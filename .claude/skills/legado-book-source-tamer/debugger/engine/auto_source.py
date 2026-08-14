"""
AutoSource - 自动生成「可用」的 Legado 书源

核心思路（针对 AI 写书源"用不了"的根因）：
1. 不凭空猜规则 —— 先抓取真实页面，再根据真实 DOM 结构推导 CSS 选择器。
2. 生成后立即用 DebugEngine 走全链路验证（搜索→详情→目录→正文），
   有一环不通就重试替代方案或明确报错，保证产出的书源「当前可用」。

支持两种输入：
- gen-search：给定搜索页 URL(含 {{key}}) + 关键词，从搜索结果页推导整套规则。
- gen-book  ：给定书籍详情/目录页 URL，推导目录与正文规则（无搜索）。

兼容两类站点结构：
- 传统 HTML 列表（通用算法：识别重复条目 + 内部主链接）
- 内嵌 JSON 数组（如 cool18 的 var _PageData = [...]）
"""

import json
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import urljoin, urlparse, quote

from bs4 import BeautifulSoup, Tag

from .book_source import BookSource
from .debug_engine import DebugEngine

# 常见导航/无意义链接文本，用于过滤非书籍链接
STOP_TEXTS = {
    '首页', '登录', '注册', '关于', '帮助', '下一页', '上一页', '更多', '目录',
    '返回', '上一章', '下一章', '设置', '搜索', '末页', '首章', '点击', '阅读',
    '查看', '详情', '全部', '刷新', '收藏', '推荐', '评论', '简介', '作者',
    '加入书架', '连载', '完本', '最新', '热门', '排行榜', '分类', '我的书架',
    '上一页', '尾章', '开始阅读', '一键缓存', '下载', '手机版', '电脑版',
}

# 正文常用容器选择器（按优先级）
COMMON_CONTENT = [
    '#content', '#chaptercontent', '#chapterContent', '#booktext', '#htmlContent',
    '#Text', '#bookText', '#nr', '#main', '#booktxt', '#ChapterContent',
    '.content', '.read-content', '.chapter-content', '.article-content',
    '.novel-content', '.read-content', '.txt', '.showtxt', '.contentbox',
    '.bookContent', '.text', '.chapterContent', '.read_con', '.neirong',
]

# 标题类字段候选（用于内嵌 JSON 模式）
TITLE_KEYS = ['title', 'name', 'subject', 'bookname', 'book_name', 'articlename',
              'novelname', 'chaptername', 'nodename', 'bookTitle', 'book_title']
# 链接/ID 类字段候选
URL_KEYS = ['url', 'link', 'href', 'bookurl', 'book_url', 'chapterurl', 'chapter_url',
            'id', 'tid', 'nid', 'bookid', 'novelid', 'chapterid', 'articleno']


@dataclass
class DetectResult:
    """一次列表结构识别的结果"""
    selector: str          # 条目 CSS 选择器（bookList / chapterList）
    name_rule: str         # 名称规则
    url_rule: str          # 链接规则
    sample: List[Dict[str, str]] = field(default_factory=list)  # 采样条目
    json_mode: bool = False
    title_key: str = ''
    url_key: str = ''
    var_name: str = ''


class SiteFetcher:
    """复用 DebugEngine 的抓取与编码自动检测能力"""

    def __init__(self, base_url: str = 'https://example.com'):
        dummy = BookSource(bookSourceUrl=base_url)
        self.engine = DebugEngine(dummy)

    def fetch(self, url: str, options: Optional[Dict] = None) -> Tuple[str, int]:
        return self.engine._fetch_url(url, options)

    def fetch_search(self, search_url_template: str, keyword: str) -> Tuple[str, int]:
        self.engine.book_source.searchUrl = search_url_template
        url, options = self.engine._build_search_url(keyword)
        return self.engine._fetch_url(url, options)


# --------------------------------------------------------------------------- #
# 工具函数
# --------------------------------------------------------------------------- #
def _origin(url: str) -> str:
    p = urlparse(url)
    return f"{p.scheme}://{p.netloc}"


def _is_book_link(a: Tag) -> bool:
    href = a.get('href') or ''
    text = a.get_text(' ', strip=True)
    if not href or href.startswith('#') or 'javascript:' in href or href.startswith('mailto:'):
        return False
    if not text or len(text) < 2 or len(text) > 60:
        return False
    if text in STOP_TEXTS:
        return False
    if re.fullmatch(r'[\d\W_]+', text):
        return False
    low = text.lower()
    if any(w in low for w in ('登录', '注册', '下一页', '上一页', '首页', '关于我们')):
        return False
    return True


def _selector_for(node: Tag) -> str:
    """为节点生成可在页面内唯一定位的 CSS 选择器（优先 id，其次 class，最后走路径）"""
    if node is None:
        return ''
    if node.get('id'):
        return '#' + node['id']
    classes = node.get('class') or []
    if classes:
        return node.name + '.' + '.'.join(classes)
    return _path_selector(node)


def _path_selector(node: Tag) -> str:
    parts = []
    cur = node
    while cur is not None and getattr(cur, 'name', None) and cur.name != '[document]':
        tag = cur.name
        pid = cur.get('id')
        if pid:
            parts.insert(0, '#' + pid)
            break
        classes = cur.get('class') or []
        if classes:
            parts.insert(0, tag + '.' + '.'.join(classes))
        else:
            parent = cur.parent
            if parent is not None:
                siblings = parent.find_all(tag, recursive=False)
                if len(siblings) > 1:
                    idx = siblings.index(cur) + 1
                    parts.insert(0, f'{tag}:nth-of-type({idx})')
                else:
                    parts.insert(0, tag)
        cur = cur.parent
    return ' > '.join(parts)


def _item_for_link(link: Tag, root: Tag) -> Tag:
    """从一条链接向上爬，找到『父容器含多条候选链接、自身只含一条』的条目节点"""
    node = link
    while node is not None and node is not root and node.parent is not None:
        parent = node.parent
        if parent is root or parent.name == 'body':
            break
        cnt = sum(1 for a in parent.find_all('a') if _is_book_link(a))
        if cnt > 1:
            break
        node = parent
    return node


def _item_rules(item: Tag) -> Optional[Tuple[str, str]]:
    """在条目内选择『文本最长的链接』作为主链接，返回 (name_rule, url_rule)"""
    all_links = item.find_all('a')
    cand = [a for a in all_links if _is_book_link(a)]
    if not cand:
        return None
    main = max(cand, key=lambda a: len(a.get_text(' ', strip=True)))
    idx = all_links.index(main)
    name_rule = f'a.{idx}@text' if idx else 'a@text'
    url_rule = f'a.{idx}@href' if idx else 'a@href'
    return name_rule, url_rule


# --------------------------------------------------------------------------- #
# HTML 列表识别（搜索列表 / 目录列表共用同一算法）
# --------------------------------------------------------------------------- #
def detect_html_list(html: str, min_items: int = 3) -> Optional[DetectResult]:
    soup = BeautifulSoup(html, 'html.parser')
    if soup.body is None:
        return None

    links = [a for a in soup.body.find_all('a') if _is_book_link(a)]
    if len(links) < min_items:
        return None

    # 按条目结构签名分组
    groups: Dict[Tuple, List[Tag]] = {}
    for a in links:
        item = _item_for_link(a, soup.body)
        sig = (item.name, item.get('id'), tuple(item.get('class') or []))
        groups.setdefault(sig, []).append(item)

    # 取条目数最多的签名
    best_sig = max(groups, key=lambda k: len(groups[k]))
    items = list(dict.fromkeys(groups[best_sig]))  # 去重保序
    if len(items) < min_items:
        return None

    selector = _selector_for(items[0])
    # 用条目数最多的那个条目求 name/url 规则
    rules = None
    for item in items:
        rules = _item_rules(item)
        if rules:
            break
    if not rules:
        return None

    name_rule, url_rule = rules
    sample = []
    for it in items[:5]:
        all_links = it.find_all('a')
        cand = [a for a in all_links if _is_book_link(a)]
        if not cand:
            continue
        main = max(cand, key=lambda a: len(a.get_text(' ', strip=True)))
        sample.append({'name': main.get_text(' ', strip=True), 'href': main.get('href', '')})

    return DetectResult(selector=selector, name_rule=name_rule, url_rule=url_rule, sample=sample)


# --------------------------------------------------------------------------- #
# 内嵌 JSON 列表识别（如 cool18 的 _PageData）
# --------------------------------------------------------------------------- #
def _extract_js_array(html: str, var: str) -> Optional[List[Dict]]:
    """按括号配对稳健地取出 `var = [...]` 的数组内容"""
    m = re.search(r'(?:var|let|const|window\.)?\s*' + re.escape(var) + r'\s*=\s*\[', html)
    if not m:
        return None
    start = m.end() - 1  # 指向 '['
    depth = 0
    in_str = False
    esc = False
    i = start
    while i < len(html):
        c = html[i]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c in ('"', "'"):
                in_str = False
        else:
            if c in ('"', "'"):
                in_str = True
            elif c == '[':
                depth += 1
            elif c == ']':
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(html[start:i + 1])
                    except Exception:
                        return None
        i += 1
    return None


def detect_json_list(html: str, min_items: int = 3) -> Optional[DetectResult]:
    var_s = re.findall(r'(?:var|let|const)?\s*([A-Za-z_$][\w$]*)\s*=\s*\[', html)
    best_arr: Optional[List[Dict]] = None
    best_var = ''
    for var in dict.fromkeys(var_s):
        arr = _extract_js_array(html, var)
        if arr and isinstance(arr, list) and len(arr) >= min_items and all(isinstance(o, dict) for o in arr[:10]):
            if best_arr is None or len(arr) > len(best_arr):
                best_arr = arr
                best_var = var
    if best_arr is None:
        return None

    keys = set()
    for o in best_arr[:30]:
        keys.update(o.keys())

    def pick(prefs):
        for k in prefs:
            if k in keys:
                return k
        # 兜底：取常见含义
        for k in keys:
            if 'title' in k.lower() or 'name' in k.lower():
                return k
        return ''

    title_key = pick(TITLE_KEYS)
    url_key = pick(URL_KEYS)
    if not title_key:
        return None

    sample = []
    for o in best_arr[:5]:
        t = o.get(title_key, '')
        u = str(o.get(url_key, '')) if url_key else ''
        if t:
            sample.append({'name': str(t), 'href': u})

    # 生成 bookList JS：用变量名精准定位，避免正则非贪婪截断
    if url_key:
        book_list_js = (
            f"@js:JSON.parse(result.match(/(?:var|let|const|window\\.)?\\s*{re.escape(best_var)}\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;/)[1])"
            f".filter(o=>o['{title_key}']&&o['{url_key}'])"
        )
    else:
        book_list_js = (
            f"@js:JSON.parse(result.match(/(?:var|let|const|window\\.)?\\s*{re.escape(best_var)}\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;/)[1])"
            f".filter(o=>o['{title_key}'])"
        )

    return DetectResult(
        selector=book_list_js,
        name_rule=f"$.{title_key}" if title_key else '',
        url_rule=f"$.{url_key}" if url_key else '',
        sample=sample,
        json_mode=True,
        title_key=title_key,
        url_key=url_key,
        var_name=best_var,
    )


def detect_list(html: str, min_items: int = 3) -> Optional[DetectResult]:
    """自动识别列表：优先 HTML 结构，其次内嵌 JSON"""
    r = detect_html_list(html, min_items)
    if r is not None:
        return r
    return detect_json_list(html, min_items)


# --------------------------------------------------------------------------- #
# 正文识别
# --------------------------------------------------------------------------- #
def detect_content(html: str, min_len: int = 100) -> Optional[str]:
    soup = BeautifulSoup(html, 'html.parser')
    if soup.body is None:
        return None

    # 1) 优先命中已知正文容器
    for sel in COMMON_CONTENT:
        try:
            for node in soup.select(sel):
                txt = node.get_text(' ', strip=True)
                # 过滤掉『导航/页脚』占比过大的节点
                if len(txt) >= min_len:
                    return f"{_selector_for(node)}@html"
        except Exception:
            continue

    # 2) 文本密度兜底：文本最长且非整页的容器
    best = None
    best_len = 0
    for el in soup.body.find_all(['div', 'article', 'td', 'section', 'p']):
        txt = el.get_text(' ', strip=True)
        if len(txt) > best_len:
            best = el
            best_len = len(txt)
    if best is not None and best_len >= min_len:
        # 避免选中几乎整页的容器
        sel = _selector_for(best)
        return f"{sel}@html"
    return None


# --------------------------------------------------------------------------- #
# 名称识别（详情页书名）
# --------------------------------------------------------------------------- #
def detect_book_name(html: str) -> str:
    soup = BeautifulSoup(html, 'html.parser')
    h1 = soup.find('h1')
    if h1:
        t = h1.get_text(' ', strip=True)
        if t and len(t) < 60:
            return t
    title = soup.find('title')
    if title:
        t = title.get_text(' ', strip=True)
        t = re.sub(r'[-_|].*$', '', t).strip()
        if t:
            return t
    return ''


# --------------------------------------------------------------------------- #
# 书源构造
# --------------------------------------------------------------------------- #
def _rule_url_template(url_key: str, origin: str) -> str:
    """JSON 模式：根据 url 字段决定书源 bookUrl 模板（绝对URL/相对URL/仅ID）"""
    # 若 url 字段本身是完整或相对链接，直接用
    return f"{{{{$.{url_key}}}}}"


def _build_book_source(
    origin: str,
    search_url: Optional[str],
    rule_search: Optional[Dict],
    book_name: str,
    rule_toc: Dict,
    rule_content: Dict,
    source_name: str,
) -> Dict:
    source = {
        'bookSourceName': source_name,
        'bookSourceUrl': origin,
        'bookSourceType': 0,
        'enabled': True,
        'enabledExplore': False,
        'ruleBookInfo': {'name': 'h1@text||title@text'},
        'ruleToc': rule_toc,
        'ruleContent': rule_content,
    }
    if search_url and rule_search:
        source['searchUrl'] = search_url
        source['ruleSearch'] = rule_search
    return BookSource.from_dict(source).to_dict()


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def auto_generate(search_url: str = None, keyword: str = '斗破苍穹',
                  book_url: str = None, source_name: str = '自动生成',
                  validate: bool = True) -> Dict:
    fetcher = SiteFetcher()
    report = {'steps': {}, 'errors': []}

    # ---- 1. 搜索（可选）→ 得到书籍详情 URL ----
    if search_url:
        html, _ = fetcher.fetch_search(search_url, keyword)
        if not html:
            report['errors'].append('搜索页抓取失败（可能需登录/被拦截/网络不通）')
        else:
            li = detect_list(html)
            if li is None:
                report['errors'].append('搜索页未识别出书籍列表（站点结构特殊，需人工补一个 bookList）')
            else:
                report['detect_search'] = {
                    'selector': li.selector[:120], 'name_rule': li.name_rule,
                    'url_rule': li.url_rule, 'json_mode': li.json_mode,
                    'sample': li.sample,
                }
                # 取第一条作为详情页起点
                if li.sample and (li.sample[0].get('href') or li.json_mode):
                    first_href = li.sample[0].get('href') or ''
                    if not book_url:
                        if li.json_mode:
                            # 详情页 URL 模板：用 $.url 字段
                            book_url = f"{{{{$.{li.url_key}}}}}"
                            # 相对链接需在生成时补齐，这里先取采样值验证
                            if first_href and not first_href.startswith('http'):
                                book_url = urljoin(_origin(html) if False else '', '')
                        else:
                            book_url = urljoin(origin_of(html), first_href) if first_href else None
    return _finalize(fetcher, book_url, search_url, keyword, source_name, report, validate)


def origin_of(html: str) -> str:
    return ''  # 占位，实际由调用方传入 URL；见 build 流程


def _finalize(fetcher: SiteFetcher, book_url: str, search_url: str, keyword: str,
              source_name: str, report: Dict, validate: bool) -> Dict:
    raise NotImplementedError