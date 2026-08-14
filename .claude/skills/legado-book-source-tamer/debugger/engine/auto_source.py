"""
AutoSource - 自动生成「可用」的 Legado 书源

设计目标（针对 AI 写书源"用不了"的根因）：
1. 不凭空猜规则：先抓取真实页面，再根据真实 DOM 推导 CSS 选择器。
2. 生成后立即用 DebugEngine 走全链路验证（详情→目录→正文，搜索模式含搜索），
   有一环不通就尝试替代方案，仍不通则明确报错——保证产出的书源「当前可用」。

优化能力：
- JSON 模式 URL 推导：内嵌 JSON 只有 id(如 cool18 的 tid) 时，
  支持 --book-url-template 提供详情页 URL 模板，或用页面同名链接自动推导。
- 多候选自动回退：正文/列表识别出多个候选，验证失败自动试下一个。
- 作者/分类/字数识别：补全 author / kind / wordCount（HTML + JSON）。
- 登录/Cookie 支持：--cookie 传入会话，所有抓取都带上，并写入导出书源的 header。
- 目录分页 nextTocUrl：--toc-paging 开启后检测章节分页并写入 nextTocUrl 模板。

用法：
    auto_generate(build_from_search=True, search_url="http://site/search?q={{key}}", keyword="书名",
                  cookie="k=v", book_url_template="https://site/read?tid={{$.tid}}")
    auto_generate(build_from_search=False, book_url="http://site/book/123", source_name="站点名")
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
AUTHOR_KEYS = ['username', 'author', 'writer', 'uname', 'nickname']
KIND_KEYS = ['type', 'kind', 'cate', 'category', 'tag', 'class', 'genre']
WC_KEYS = ['size', 'wordcount', 'word_count', 'wordCount', 'len', 'length', '字数']


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
    author_rule: str = ''
    kind_rule: str = ''
    word_count_rule: str = ''
    needs_url_template: bool = False


class SiteFetcher:
    """复用 DebugEngine 的抓取、编码自动检测、charset/POST 与 Cookie 能力"""

    def __init__(self, base_url: str = 'https://example.com', cookie: str = None):
        dummy = BookSource(bookSourceUrl=base_url)
        self.engine = DebugEngine(dummy)
        if cookie:
            self.engine.session.headers['Cookie'] = cookie

    def fetch(self, url: str, options: Optional[Dict] = None) -> Tuple[str, int]:
        return self.engine._fetch_url(url, options)

    def fetch_search(self, search_url_template: str, keyword: str) -> Tuple[str, int, str]:
        self.engine.book_source.searchUrl = search_url_template
        url, options = self.engine._build_search_url(keyword)
        html, status = self.engine._fetch_url(url, options)
        return html, status, url

    def get_cookie_header(self, url: str) -> str:
        """把抓取过程中自动收集的、属于该域名的 Cookie 转成 header 字符串"""
        try:
            from urllib.parse import urlparse
            host = urlparse(url).hostname or ''
            pairs = []
            for c in self.engine.cookiejar:
                domain = c.domain.lstrip('.') if c.domain else ''
                if host and (host == domain or host.endswith('.' + domain)):
                    pairs.append(f'{c.name}={c.value}')
            return '; '.join(pairs)
        except Exception:
            return ''


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


def _detect_html_meta(item: Tag) -> Tuple[str, str, str]:
    """在条目内识别作者/分类/字数（按 class 语义，best-effort）"""
    author = kind = wc = ''
    for el in item.find_all(['span', 'em', 'p', 'td', 'div', 'a'], limit=60):
        cls = ' '.join(el.get('class') or [])
        cl = cls.lower()
        if not author and ('author' in cl or '作者' in cls):
            author = f'{_selector_for(el)}@text'
        if not kind and any(w in cl for w in ('type', 'kind', 'cate', 'categor', 'tag', 'genre')):
            kind = f'{_selector_for(el)}@text'
        if not wc and any(w in cl for w in ('size', 'word', 'count', '字数')):
            wc = f'{_selector_for(el)}@text'
        if author and kind and wc:
            break
    return author, kind, wc


def _detect_book_info_meta(html: str) -> Dict[str, str]:
    """详情页级元信息检测：扫全页找带语义 class/id 的作者/分类/字数/封面节点。
    区别于列表条目内检测（_detect_html_meta），这里针对 book_url 详情页独立区域。"""
    soup = BeautifulSoup(html, 'html.parser')
    if soup.body is None:
        return {}
    res: Dict[str, str] = {}

    def match_rule(el, keyword):
        el_id = el.get('id') or ''
        el_cls = ' '.join(el.get('class') or [])
        return keyword in (el_cls + ' ' + el_id).lower()

    def assign(rule_key, keywords):
        if rule_key in res:
            return
        for el in soup.body.find_all(['span', 'em', 'p', 'td', 'div', 'label', 'a'], limit=200):
            if match_rule(el, keywords):
                res[rule_key] = f"{_selector_for(el)}@text"
                return

    assign('author', 'author')
    assign('kind', 'type')
    assign('wordCount', 'word')
    assign('wordCount', 'size')
    # 封面
    for img in soup.body.find_all('img', limit=50):
        el_id = img.get('id') or ''
        el_cls = ' '.join(img.get('class') or [])
        if 'cover' in (el_cls + ' ' + el_id).lower() or '封面' in (img.get('alt') or ''):
            res['coverUrl'] = f"{_selector_for(img)}@src"
            break
    return res


def _detect_json_meta(keys: set) -> Tuple[str, str, str]:
    def pick(prefs):
        for k in prefs:
            if k in keys:
                return f'$.{k}'
        return ''
    return pick(AUTHOR_KEYS), pick(KIND_KEYS), pick(WC_KEYS)


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
    rep_item = None
    for it in items:
        rules = _item_rules(it)
        if rules:
            rep_item = it
            break
    if rules is None:
        return None
    name_rule, url_rule = rules
    author_rule, kind_rule, wc_rule = (_detect_html_meta(rep_item)
                                       if rep_item is not None else ('', '', ''))

    sample = []
    for it in items[:5]:
        all_links = it.find_all('a')
        cand = [a for a in all_links if _is_book_link(a)]
        if not cand:
            continue
        main = max(cand, key=lambda a: len(a.get_text(' ', strip=True)))
        sample.append({'name': main.get_text(' ', strip=True), 'href': main.get('href', '')})

    return DetectResult(selector=selector, name_rule=name_rule, url_rule=url_rule,
                        sample=sample, author_rule=author_rule, kind_rule=kind_rule,
                        word_count_rule=wc_rule)


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


def _looks_like_url(v: Any) -> bool:
    s = str(v)
    return s.startswith('http') or s.startswith('/') or '.' in s or '/' in s


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
    url_like = False
    for o in best_arr[:5]:
        t = o.get(title_key, '')
        u = o.get(url_key, '') if url_key else ''
        if t:
            sample.append({'name': str(t), 'href': str(u)})
            if u and _looks_like_url(u):
                url_like = True

    author_rule, kind_rule, wc_rule = _detect_json_meta(keys)

    var_esc = re.escape(best_var)
    url_ref = url_key if url_key else title_key
    book_list_js = (
        f"@js:var m=result.match(/(?:var|let|const|window\\.)?\\s*{var_esc}\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;/);"
        f"result=m?JSON.parse(m[1]).filter(o=>o['{title_key}']&&o['{url_ref}']):[];"
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
        author_rule=author_rule,
        kind_rule=kind_rule,
        word_count_rule=wc_rule,
        needs_url_template=not (url_key and url_like),
    )


def detect_list(html: str, min_items: int = 3) -> Optional[DetectResult]:
    r = detect_html_list(html, min_items)
    if r is not None:
        return r
    return detect_json_list(html, min_items)


def _json_url_template_from_html(html: str, sample_id: str, sample_title: str,
                                 url_key: str, base: str) -> Optional[str]:
    """JSON 只有 id 时，用页面里同名链接推导详情页 URL 模板（把 id 换成 {{$.url_key}}）"""
    if not sample_id or not sample_title:
        return None
    soup = BeautifulSoup(html, 'html.parser')
    for a in soup.find_all('a'):
        text = a.get_text(' ', strip=True)
        href = a.get('href') or ''
        if not href or href.startswith('#') or 'javascript:' in href:
            continue
        if str(sample_id) in href and (sample_title in text or text in sample_title):
            new_href = href.replace(str(sample_id), '{{$.%s}}' % url_key, 1)
            if not new_href.startswith('http'):
                new_href = urljoin(base, new_href)
            return new_href
    return None


# --------------------------------------------------------------------------- #
# 正文 / 书名识别
# --------------------------------------------------------------------------- #
def detect_content_candidates(html: str, min_len: int = 50) -> List[str]:
    """返回正文候选选择器列表（去重、按优先级+文本密度排序）"""
    soup = BeautifulSoup(html, 'html.parser')
    if soup.body is None:
        return []
    cands: List[str] = []
    seen = set()
    for sel in COMMON_CONTENT:
        try:
            for node in soup.select(sel):
                if len(node.get_text(strip=True)) >= min_len:
                    s = f"{_selector_for(node)}@html"
                    if s not in seen:
                        seen.add(s)
                        cands.append(s)
        except Exception:
            continue
    density = []
    for el in soup.body.find_all(['div', 'article', 'td', 'section']):
        txt = el.get_text(' ', strip=True)
        if len(txt) >= min_len:
            density.append((len(txt), _selector_for(el)))
    for _, s in sorted(density, key=lambda x: x[0], reverse=True):
        sc = f"{s}@html"
        if sc not in seen:
            seen.add(sc)
            cands.append(sc)
    return cands


def _pick_content(html: str, candidates: List[str], min_len: int = 50) -> Optional[str]:
    """按优先级选第一个文本长度达标的候选"""
    soup = BeautifulSoup(html, 'html.parser')
    for cand in candidates:
        sel = cand.split('@')[0]
        try:
            nodes = soup.select(sel) if sel else []
        except Exception:
            nodes = []
        for n in nodes:
            if len(n.get_text(strip=True)) >= min_len:
                return cand
    return candidates[0] if candidates else None


def detect_content(html: str, min_len: int = 30) -> Optional[str]:
    cands = detect_content_candidates(html, min_len)
    return _pick_content(html, cands, min_len)


def detect_content_blocker(html: str) -> Optional[str]:
    """章节页无静态正文时，识别常见『正文拿不到』的原因，返回可读诊断。"""
    if not html:
        return '页面为空'
    signs = []
    if '加载中' in html or 'loading' in html.lower():
        signs.append('页面提示“加载中”')
    if re.search(r'getCookie\s*\(\s*["\']getsite|setCookie\s*\(\s*["\']getsite', html):
        signs.append('检测到 getsite Cookie 反爬验证（需浏览器自动探测镜像域名）')
    if 'userverify' in html:
        signs.append('检测到 userverify 反爬跳转')
    if re.search(r'<meta[^>]+http-equiv=["\']?refresh', html, re.I):
        signs.append('检测到 meta refresh 跳转')
    if '登录' in html and '登录后才能' in html or '请登录' in html:
        signs.append('可能需要登录')
    if re.search(r'<script[^>]*>[\s\S]{0,200}?(ajax|fetch\(|XMLHttpRequest|\.getJSON|chapter)",?', html):
        signs.append('正文疑似通过 JS/AJAX 异步加载，静态 HTML 中不含正文')
    return '；'.join(signs) if signs else None


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


def detect_next_toc_url(html: str, base: str) -> Optional[str]:
    """检测目录分页链接，返回带 {{page}} 的模板（找不到分页返回 None）。
    支持 ?page=2 查询参数形式 与 /p/2.html 路径形式；避免把页面路径里的字母误当页码。"""
    soup = BeautifulSoup(html, 'html.parser')
    for a in soup.find_all('a'):
        text = a.get_text(' ', strip=True)
        href = a.get('href') or ''
        if not href or href.startswith('#') or 'javascript:' in href:
            continue
        if not any(w in text for w in ('下一页', '下页', 'next', '尾页', '末页')):
            continue
        if not href.startswith('http'):
            href = urljoin(base, href)
        # 查询参数形式：?page=2 / &pagenum=3
        m = re.search(r'[?&](?:page|pageno|pagenum|pageNum|key)=(\d+)', href, re.I)
        if m:
            return href[:m.start(1)] + '{{page}}' + href[m.end(1):]
        # 路径形式：/p/2.html 或 /page/2
        m = re.search(r'(?:/(?:p|page)/)(\d+)(?:\.html?)?', href, re.I)
        if m:
            return href[:m.start(1)] + '{{page}}' + href[m.end(1):]
    return None


# --------------------------------------------------------------------------- #
# 书源构造
# --------------------------------------------------------------------------- #
def _build_source(origin: str, search_url: Optional[str], rule_search: Optional[Dict],
                  toc_url: Optional[str], rule_toc: Dict, rule_content: Dict,
                  source_name: str, header: Optional[str] = None,
                  book_info: Optional[Dict] = None) -> Dict:
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
    if book_info:
        source['ruleBookInfo'].update({k: v for k, v in book_info.items() if v})
    if toc_url:
        source['ruleBookInfo']['tocUrl'] = toc_url
    if search_url and rule_search:
        source['searchUrl'] = search_url
        source['ruleSearch'] = {k: v for k, v in rule_search.items() if v}
    if header:
        source['header'] = header
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


def _verify_content_only(source_dict: Dict, chapter_url: str, cand: str,
                         min_len: int = 50) -> bool:
    """仅重测正文候选（不重跑搜索/目录），用于多候选回退"""
    try:
        bs = BookSource.from_dict(source_dict)
        bs.ruleContent = {'content': cand}
        engine = DebugEngine(bs)
        r = engine.test_content(chapter_url)
        return r.success and r.data is not None and len(r.data.text) >= min_len
    except Exception:
        return False


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def auto_generate(build_from_search: bool = True, search_url: str = None,
                  keyword: str = '斗破苍穹', book_url: str = None,
                  source_name: str = '', validate: bool = True,
                  cookie: str = None, book_url_template: str = None,
                  enable_toc_paging: bool = False) -> Dict:
    fetcher = SiteFetcher(cookie=cookie)
    report = {'ok': False, 'errors': [], 'steps': {}, 'bookSource': None}
    origin = None
    json_mode = False
    needs_url_template = False
    url_key = None

    rule_search = None
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
                json_mode = li.json_mode
                needs_url_template = li.needs_url_template
                url_key = li.url_key
                report['detect_search'] = {
                    'selector': li.selector[:120], 'name_rule': li.name_rule,
                    'url_rule': li.url_rule, 'json_mode': li.json_mode,
                    'author_rule': li.author_rule, 'kind_rule': li.kind_rule,
                    'word_count_rule': li.word_count_rule,
                    'needs_url_template': li.needs_url_template,
                    'sample_count': len(li.sample), 'sample': li.sample,
                }
                rule_search = {
                    'bookList': li.selector,
                    'name': li.name_rule,
                    'bookUrl': li.url_rule,
                    'author': li.author_rule,
                    'kind': li.kind_rule,
                    'wordCount': li.word_count_rule,
                }
                # 取第一条真实链接去抓详情页
                if li.sample and li.sample[0].get('href'):
                    first_href = li.sample[0]['href']
                    if li.json_mode and li.needs_url_template:
                        # id-only：用模板或同名链接推导
                        if book_url_template:
                            rule_search['bookUrl'] = book_url_template
                            book_url = book_url_template.replace('{{$.%s}}' % li.url_key,
                                                                 first_href) if li.url_key else ''
                        else:
                            tpl = _json_url_template_from_html(html, first_href,
                                                               li.sample[0]['name'],
                                                               li.url_key, origin)
                            if tpl:
                                rule_search['bookUrl'] = tpl
                                book_url = tpl.replace('{{$.%s}}' % li.url_key, first_href)
                            else:
                                report['errors'].append(
                                    'JSON 列表只有 id 无链接，请用 --book-url-template 提供详情页模板'
                                    f'（如 https://site/read?tid={{{{$.{li.url_key}}}}})')
                    else:
                        book_url = urljoin(origin, first_href)
                else:
                    report['errors'].append('识别到列表但无可用链接')
                # 报告里展示最终生效的 bookUrl（JSON+模板场景下已被替换为模板）
                if rule_search and rule_search.get('bookUrl'):
                    report['detect_search']['url_rule'] = rule_search['bookUrl']

    # ---- 详情 / 目录 / 正文 ----
    rule_toc = None
    rule_content = None
    content_candidates: List[str] = []
    chapter_url = None
    toc_page_url = None
    rule_book_info = {}

    if not book_url and not rule_search:
        report['errors'].append('未提供 search_url 或 book_url 输入')
        return report

    if book_url:
        detail_html, _ = fetcher.fetch(book_url)
        if not detail_html:
            report['errors'].append(f'详情页抓取失败: {book_url}')
        else:
            local_origin = _origin(book_url)
            # 详情页级元信息（作者/分类/字数/封面）——独立于章节列表区域
            page_meta = _detect_book_info_meta(detail_html)
            for k, v in page_meta.items():
                if v:
                    rule_book_info[k] = v
            toc_det = detect_list(detail_html, min_items=2)
            if toc_det is None:
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
                if toc_det.author_rule:
                    rule_book_info['author'] = f"{rule_book_info.get('author','')}||{toc_det.author_rule}".lstrip('||')
                if toc_det.kind_rule:
                    rule_book_info['kind'] = f"{rule_book_info.get('kind','')}||{toc_det.kind_rule}".lstrip('||')
                if toc_det.word_count_rule:
                    rule_book_info['wordCount'] = f"{rule_book_info.get('wordCount','')}||{toc_det.word_count_rule}".lstrip('||')

                # 目录分页 nextTocUrl（可选）
                if enable_toc_paging:
                    next_tpl = detect_next_toc_url(detail_html, local_origin)
                    if next_tpl:
                        rule_toc['nextTocUrl'] = next_tpl
                        report['detect_toc_next'] = next_tpl

                report['detect_toc'] = {
                    'selector': toc_det.selector[:120], 'name_rule': toc_det.name_rule,
                    'url_rule': toc_det.url_rule, 'json_mode': toc_det.json_mode,
                    'sample_count': len(toc_det.sample), 'sample': toc_det.sample,
                }
                if toc_det.sample and toc_det.sample[0].get('href'):
                    chapter_url = urljoin(local_origin, toc_det.sample[0]['href'])
                    ch_html, _ = fetcher.fetch(chapter_url)
                    if ch_html:
                        content_candidates = detect_content_candidates(ch_html)
                        content_sel = _pick_content(ch_html, content_candidates)
                        if content_sel:
                            rule_content = {'content': content_sel}
                            report['detect_content'] = {'selector': content_sel,
                                                        'candidates': len(content_candidates)}
                        else:
                            blocker = detect_content_blocker(ch_html)
                            report['errors'].append(
                                '正文容器未识别出文本' + (f'（{blocker}）' if blocker else '')
                            )
                            report['detect_content'] = {'selector': '',
                                                        'blocker': blocker or '未知（可能正文结构特殊）'}
                    else:
                        report['errors'].append(f'章节页抓取失败: {chapter_url}')

    if (not rule_toc) or (not rule_content):
        report['errors'].append('缺少目录或正文规则，无法生成可用书源')
        return report

    # 合并自动学到的会话 Cookie：抓取过程中服务端 Set-Cookie 的，一并写入书源 header
    if not source_name:
        host = urlparse(origin or book_url or '').netloc
        source_name = host if host else '自动生成'

    learned = fetcher.get_cookie_header(origin or book_url)
    combined = '; '.join(x for x in (cookie, learned) if x)
    header = json.dumps({'Cookie': combined}) if combined else None
    source_dict = _build_source(
        origin=origin or _origin(book_url),
        search_url=(search_url if build_from_search and rule_search else None),
        rule_search=rule_search,
        toc_url=toc_page_url,
        rule_toc=rule_toc,
        rule_content=rule_content,
        source_name=source_name,
        header=header,
        book_info=rule_book_info,
    )

    # ---- 验证 + 正文多候选回退 ----
    if validate:
        v = run_validation(source_dict, keyword=keyword,
                           only_toc_content=not (build_from_search and rule_search),
                           detail_url=book_url)
        if not v['ok'] and content_candidates and chapter_url:
            for cand in content_candidates[1:]:
                if _verify_content_only(source_dict, chapter_url, cand):
                    source_dict['ruleContent']['content'] = cand
                    report['detect_content']['selector'] = cand
                    v = run_validation(source_dict, keyword=keyword,
                                       only_toc_content=not (build_from_search and rule_search),
                                       detail_url=book_url)
                    if v['ok']:
                        break
        report['ok'] = v['ok']
        report['steps'] = v['steps']
        if v['error']:
            report['errors'].append(v['error'])
    else:
        report['ok'] = True

    report['bookSource'] = source_dict
    return report