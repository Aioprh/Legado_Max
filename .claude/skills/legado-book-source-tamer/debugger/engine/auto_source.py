"""
AutoSource - 自动生成「可用」的 Legado 书源

设计目标（针对 AI 写书源"用不了"的根因）：
1. 不凭空猜规则：先抓取真实页面，再根据真实 DOM 推导 CSS 选择器。
2. 生成后立即用 DebugEngine 走全链路验证（详情→目录→正文，搜索模式含搜索），
   有一环不通就尝试替代方案，仍不通则明确报错——保证产出的书源「当前可用」。

用法（由 CLI 或其他代码调用）：
    auto_generate(build_from_search=True, search_url="http://site/search?q={{key}}", keyword="书名")
    auto_generate(build_from_search=False, book_url="http://site/book/123", source_name="站点名")

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
    '#Text', '#bookText', '#nr', '#main', '#booktxt',
    '.content', '.read-content', '.chapter-content', '.article-content',
    '.novel-content', '.txt', '.showtxt', '.contentbox', '.bookContent',
    '.text', '.chapterContent', '.read_con', '.neirong', '.chapter_text',
]

TITLE_KEYS = ['title', 'name', 'subject', 'bookname', 'book_name', 'articlename',
              'novelname', 'chaptername', 'nodename', 'bookTitle', 'book_title']
URL_KEYS = ['url', 'link', 'href', 'bookurl', 'book_url', 'chapterurl', 'chapter_url',
            'id', 'tid', 'nid', 'bookid', 'novelid', 'chapterid', 'articleno']


@dataclass
class DetectResult:
    selector: str
    name_rule: str
    url_rule: str
    sample: List[Dict[str, str]] = field(default_factory=list)
    json_mode: bool = False
    title_key: str = ''
    url_key: str = ''
    var_name: str = ''


class SiteFetcher:
    """复用 DebugEngine 的抓取、编码自动检测、charset/POST 处理能力"""

    def __init__(self, base_url: str = 'https://example.com'):
        dummy = BookSource(bookSourceUrl=base_url)
        self.engine = DebugEngine(dummy)

    def fetch(self, url: str, options: Optional[Dict] = None) -> Tuple[str, int]:
        return self.engine._fetch_url(url, options)

    def fetch_search(self, search_url_template: str, keyword: str) -> Tuple[str, int, str]:
        self.engine.book_source.searchUrl = search_url_template
        url, options = self.engine._build_search_url(keyword)
        html, status = self.engine._fetch_url(url, options)
        return html, status, url


# --------------------------------------------------------------------------- #
# 工具
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
    if any(w in low for w in ('下一页', '上一页', '首页', '关于我们', '登录', '注册')):
        return False
    return True


def _selector_for(node: Tag) -> str:
    """节点 CSS 选择器：优先 id，其次 class，最后 nth-of-type 路径"""
    if node is None:
        return ''
    pid = node.get('id')
    if pid:
        return '#' + pid
    cls = node.get('class') or []
    if cls:
        return node.name + '.' + '.'.join(cls)
    parts = []
    cur = node
    while cur is not None and getattr(cur, 'name', None) and cur.name != '[document]':
        tag = cur.name
        cid = cur.get('id')
        if cid:
            parts.insert(0, '#' + cid)
            break
        ccls = cur.get('class') or []
        if ccls:
            parts.insert(0, tag + '.' + '.'.join(ccls))
        else:
            parent = cur.parent
            if parent is not None:
                sibs = parent.find_all(tag, recursive=False)
                if len(sibs) > 1:
                    parts.insert(0, f'{tag}:nth-of-type({sibs.index(cur) + 1})')
                else:
                    parts.insert(0, tag)
        cur = cur.parent
    return ' > '.join(parts)


def _item_for_link(link: Tag, root: Tag) -> Tag:
    """从一条链接向上爬：找到『父容器含多条候选链接、自身只含一条』的条目节点"""
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


def _item_selector(item: Tag, soup: BeautifulSoup) -> str:
    """条目选择器：优先自身 id/class；否则用『最近带电标志的祖先 + 子标签』，保证匹配全部条目"""
    if item.get('id'):
        return '#' + item['id']
    cls = item.get('class') or []
    if cls:
        return item.name + '.' + '.'.join(cls)
    anc = item.parent
    while anc is not None and anc is not soup and anc.name != 'body':
        aid = anc.get('id')
        acls = anc.get('class') or []
        if aid or acls:
            sel = '#' + aid if aid else anc.name + '.' + '.'.join(acls)
            return f"{sel} {item.name}"
        anc = anc.parent
    return item.name


def _item_rules(item: Tag) -> Optional[Tuple[str, str]]:
    """条目内选『文本最长的链接』为主链接，返回 (name_rule, url_rule)。索引按全部 a 计算以对齐 Legado"""
    all_links = item.find_all('a')
    cand = [a for a in all_links if _is_book_link(a)]
    if not cand:
        return None
    main = max(cand, key=lambda a: len(a.get_text(' ', strip=True)))
    idx = all_links.index(main)
    return (f'a.{idx}@text' if idx else 'a@text',
            f'a.{idx}@href' if idx else 'a@href')


# --------------------------------------------------------------------------- #
# HTML 列表识别（搜索列表 / 目录列表共用）
# --------------------------------------------------------------------------- #
def detect_html_list(html: str, min_items: int = 3) -> Optional[DetectResult]:
    soup = BeautifulSoup(html, 'html.parser')
    if soup.body is None:
        return None
    links = [a for a in soup.body.find_all('a') if _is_book_link(a)]
    if len(links) < min_items:
        return None

    groups: Dict[Tuple, List[Tag]] = {}
    for a in links:
        item = _item_for_link(a, soup.body)
        sig = (item.name, item.get('id'), tuple(item.get('class') or []))
        groups.setdefault(sig, []).append(item)

    best_sig = max(groups, key=lambda k: len(groups[k]))
    items = list(dict.fromkeys(groups[best_sig]))
    if len(items) < min_items:
        return None

    selector = _item_selector(items[0], soup)
    rules = None
    for it in items:
        rules = _item_rules(it)
        if rules:
            break
    if rules is None:
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
def _extract_js_array(html: str, var: str) -> Optional[List[Any]]:
    m = re.search(r'(?:var|let|const|window\.)?\s*' + re.escape(var) + r'\s*=\s*\[', html)
    if not m:
        return None
    start = m.end() - 1
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
    best_arr = None
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
        u = o.get(url_key, '') if url_key else ''
        if t:
            sample.append({'name': str(t), 'href': str(u)})

    # 用变量名精准定位数组，避免正则非贪婪截断；再用括号配对兜底
    var_esc = re.escape(best_var)
    book_list_js = (
        f"@js:(function(){{var m=result.match(/(?:var|let|const|window\\.)?\\s*{var_esc}\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;/);"
        f"return m?JSON.parse(m[1])"
        f".filter(o=>o['{title_key}']&&o['{url_key if url_key else title_key}']):[]}})()"
    )

    return DetectResult(
        selector=book_list_js,
        name_rule=f"$.{title_key}",
        url_rule=f"{{{{$.{url_key}}}}}" if url_key else '',
        sample=sample,
        json_mode=True,
        title_key=title_key,
        url_key=url_key,
        var_name=best_var,
    )


def detect_list(html: str, min_items: int = 3) -> Optional[DetectResult]:
    r = detect_html_list(html, min_items)
    if r is not None:
        return r
    return detect_json_list(html, min_items)


# --------------------------------------------------------------------------- #
# 正文 / 书名识别
# --------------------------------------------------------------------------- #
def detect_content(html: str, min_len: int = 30) -> Optional[str]:
    soup = BeautifulSoup(html, 'html.parser')
    if soup.body is None:
        return None
    for sel in COMMON_CONTENT:
        try:
            for node in soup.select(sel):
                txt = node.get_text(' ', strip=True)
                if len(txt) >= min_len:
                    return f"{_selector_for(node)}@html"
        except Exception:
            continue
    best, best_len = None, 0
    for el in soup.body.find_all(['div', 'article', 'td', 'section', 'p']):
        txt = el.get_text(' ', strip=True)
        if len(txt) > best_len:
            best, best_len = el, len(txt)
    if best is not None and best_len >= min_len:
        return f"{_selector_for(best)}@html"
    return None


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
        t = re.sub(r'[-_|·].*$', '', t).strip()
        if t:
            return t
    return ''


def find_toc_link(html: str, base: str = '') -> Optional[str]:
    """详情页无章节时，找『目录』链接"""
    soup = BeautifulSoup(html, 'html.parser')
    for a in soup.find_all('a'):
        text = a.get_text(' ', strip=True)
        if any(w in text for w in ('目录', '章节目录', '全部章节', '章节列表', '卷')):
            href = a.get('href') or ''
            if href and not href.startswith('#') and 'javascript:' not in href:
                return href if href.startswith('http') else urljoin(base, href)
    return None


# --------------------------------------------------------------------------- #
# 书源构造
# --------------------------------------------------------------------------- #
def _build_source(origin: str, search_url: Optional[str], rule_search: Optional[Dict],
                  toc_url: Optional[str], rule_toc: Dict, rule_content: Dict,
                  source_name: str) -> Dict:
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
    if toc_url:
        source['ruleBookInfo']['tocUrl'] = toc_url
    if search_url and rule_search:
        source['searchUrl'] = search_url
        source['ruleSearch'] = rule_search
    return BookSource.from_dict(source).to_dict()


# --------------------------------------------------------------------------- #
# 验证
# --------------------------------------------------------------------------- #
def run_validation(source_dict: Dict, keyword: str = '斗破苍穹',
                   only_toc_content: bool = False, detail_url: str = None) -> Dict:
    try:
        bs = BookSource.from_dict(source_dict)
    except Exception as e:
        return {'ok': False, 'error': f'书源无法解析: {e}'}
    engine = DebugEngine(bs)
    steps = {}
    overall = True

    if only_toc_content:
        # 无搜索：验证详情/目录/正文
        toc_url = detail_url or bs.bookSourceUrl
        info = engine.test_book_info(source_dict.get('ruleBookInfo', {}).get('tocUrl') or toc_url)
        steps['book_info'] = {'ok': info.success, 'msg': info.message}
        overall = overall and info.success

        real_toc = source_dict.get('ruleBookInfo', {}).get('tocUrl') or toc_url
        toc = engine.test_toc(real_toc)
        steps['toc'] = {'ok': toc.success and bool(toc.data), 'msg': toc.message,
                        'count': len(toc.data) if toc.data else 0}
        overall = overall and bool(toc.data)

        if toc.data:
            ch = engine.test_content(toc.data[0].url)
            steps['content'] = {'ok': ch.success and len(ch.data.text) > 0 if ch.data else False,
                                'msg': ch.message,
                                'len': len(ch.data.text) if ch.data else 0}
            overall = overall and steps['content']['ok']
    else:
        res = engine.run_full_test(keyword)
        for name, r in res.get('tests', {}).items():
            steps[name] = {'ok': r.get('success', False), 'msg': r.get('message', ''),
                           'err': r.get('error')}
        overall = res.get('overall_success', False)

    return {'ok': overall, 'steps': steps, 'error': ''}


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def auto_generate(build_from_search: bool = True, search_url: str = None,
                  keyword: str = '斗破苍穹', book_url: str = None,
                  source_name: str = '', validate: bool = True) -> Dict:
    fetcher = SiteFetcher()
    report = {'ok': False, 'errors': [], 'steps': {}, 'bookSource': None}
    origin = None

    # ---- 1. 搜索（可选） → 得到书籍详情 URL 与搜索规则 ----
    rule_search = None
    search_url_out = search_url
    if build_from_search and search_url:
        html, status, built_url = fetcher.fetch_search(search_url, keyword)
        origin = _origin(built_url)
        if not html:
            report['errors'].append(f'搜索页抓取失败(HTTP {status})，可能需登录/被拦截/网络不通')
        else:
            li = detect_list(html)
            if li is None:
                report['errors'].append('搜索页未识别出书籍列表，请检查搜索 URL 是否返回列表页')
            else:
                report['detect_search'] = {
                    'selector': li.selector[:120], 'name_rule': li.name_rule,
                    'url_rule': li.url_rule, 'json_mode': li.json_mode,
                    'sample_count': len(li.sample), 'sample': li.sample,
                }
                rule_search = {
                    'bookList': li.selector,
                    'name': li.name_rule,
                    'bookUrl': li.url_rule,
                }
                # 取第一条真实链接去抓详情页
                if li.sample and li.sample[0].get('href'):
                    book_url = urljoin(origin, li.sample[0]['href'])
                else:
                    report['errors'].append('识别到列表但无可用链接')

    # ---- 2. 书籍详情 / 目录 ----
    rule_toc = None
    rule_content = None
    toc_page_url = None
    if not book_url and not rule_search:
        report['errors'].append('未提供 search_url 或 book_url 输入')
        return report

    if book_url:
        detail_html, dest = fetcher.fetch(book_url)
        if not detail_html:
            report['errors'].append(f'详情页抓取失败: {book_url}')
        else:
            local_origin = _origin(book_url)
            toc_det = detect_list(detail_html, min_items=2)
            if toc_det is None:
                # 详情页没有章节 → 尝试找目录页
                toc_link = find_toc_link(detail_html, local_origin)
                if toc_link:
                    toc_html, _ = fetcher.fetch(toc_link)
                    if toc_html:
                        toc_det = detect_list(toc_html, min_items=2)
                        toc_page_url = toc_link
            if toc_det is None:
                report['errors'].append('目录页未识别出章节列表')
            else:
                rule_toc = {
                    'chapterList': toc_det.selector,
                    'chapterName': toc_det.name_rule,
                    'chapterUrl': toc_det.url_rule,
                }
                report['detect_toc'] = {
                    'selector': toc_det.selector[:120], 'name_rule': toc_det.name_rule,
                    'url_rule': toc_det.url_rule, 'json_mode': toc_det.json_mode,
                    'sample_count': len(toc_det.sample), 'sample': toc_det.sample,
                }
                # 取第一章抓正文
                if toc_det.sample and toc_det.sample[0].get('href'):
                    chapter_url = urljoin(_origin(book_url), toc_det.sample[0]['href'])
                    ch_html, _ = fetcher.fetch(chapter_url)
                    if ch_html:
                        content_sel = detect_content(ch_html)
                        if content_sel:
                            rule_content = {'content': content_sel}
                            report['detect_content'] = {'selector': content_sel}
                        else:
                            report['errors'].append('正文容器未识别出文本')
                    else:
                        report['errors'].append(f'章节页抓取失败: {chapter_url}')

    # ---- 3. 组装 ----
    if (not rule_toc) or (not rule_content):
        report['errors'].append('缺少目录或正文规则，无法生成可用书源')
        return report

    if not source_name:
        host = urlparse(origin or book_url or '').netloc
        source_name = host if host else '自动生成'

    source_dict = _build_source(
        origin=origin or _origin(book_url),
        search_url=(search_url if build_from_search and rule_search else None),
        rule_search=rule_search,
        toc_url=toc_page_url,
        rule_toc=rule_toc,
        rule_content=rule_content,
        source_name=source_name,
    )

    # ---- 4. 验证 ----
    if validate:
        v = run_validation(source_dict, keyword=keyword,
                           only_toc_content=not (build_from_search and rule_search),
                           detail_url=book_url)
        report['ok'] = v['ok']
        report['steps'] = v['steps']
        if v['error']:
            report['errors'].append(v['error'])
    else:
        report['ok'] = True

    report['bookSource'] = source_dict
    return report