/* 段评 / 本章说 / 标题评论 / 作家说 / 本书评论 查看页。
 *
 * 所有书源共用同一套渲染。源之间的差异**不再写在这个文件里**，而是由后端下发的
 * semantics 描述（window.__CMT__.semantics，见 SEM 变量）：
 *   - paragraphIdKind: 段号语义。number1=1 基（起点）/ number0=0 基（番茄）/ opaque=不透明串（书旗）
 *   - chapterSayId / titleCommentId / authorSayId: 该视图要传的 paragraphId；"-" = 该源不支持
 *     （书旗的 titleCommentId 是个固定哨兵 __title__：它标题槽位的真实段号每章都不一样，
 *      由服务端在请求到达时翻译回实际值，前端与书源都不需要知道这件事）
 *   - pagination: "page"=页码分页（起点/番茄）/ "cursor"=游标分页（书旗，不返回总数）
 *   - emojiEndpoint: 表情映射端点；空 = 内置码表（起点的 [fn=N] → Unicode）
 *   - bookReview: 是否支持「本书评论」（书旗独有，挂进本章说弹窗的 Tab）
 *
 * 为什么这么改：改造前这些差异靠 isFanqie() 与魔法段号（0/-1/-10）硬编码，
 * 而 paragraphId 一个字段同时承载了「定位第几段」和「这是哪种视图」两种语义。
 * 番茄的 0 是正常段落、书旗的段号是 'p000' 这类不透明串，与起点的特殊值天然冲突——
 * 每加一个源就得再打一次补丁。现在请求显式带 kind，段号退化为纯定位符。
 *
 * ID 精度（关键，勿动）：
 *   起点后端 transformBigIDs 只把「≥15 位」的 *Id 字段转成字符串，所以同一条评论里
 *   Id/RootReviewId（18 位）是字符串，而 UserId/RelatedUserId（9 位）仍是数字，
 *   RefferCommentId 在主楼回复时是数字 0。番茄的 ID（19 位）上游本就是字符串。
 *   → 本文件任何 ID 一律用 String() 比较、拼接，绝不 parseInt/Number，否则大数会被截断。
 */

// 起点表情码表：[fn=N] → Unicode
var EMOJI_MAP = {
    31: '😳', 21: '😍', 11: '🙄', 32: '😎', 12: '😭', 43: '☺️', 50: '🤐', 45: '😴', 19: '😂', 37: '😡',
    47: '😱', 26: '🤪', 4: '😁', 40: '😞', 36: '🤓', 17: '😓', 24: '😫', 51: '🥴', 33: '🤭', 7: '🙂',
    49: '😪', 16: '🥵', 14: '😥', 5: '😄', 46: '🤠🚬', 29: '💪', 44: '🤬', 39: '😄❓', 18: '🤫', 13: '😵',
    28: '😣', 20: '😢', 30: '💀', 22: '🤕🔨', 34: '😄👏', 10: '👆🏻🐽', 1: '👏', 23: '😑', 8: '😏', 41: '😧',
    15: '🖕🏻', 38: '🙁', 58: '😒', 9: '😙', 60: '😮', 6: '🥺', 54: '🔪', 48: '🐷', 2: '🌹', 42: '💋',
    53: '❤️', 56: '💔', 52: '🌙', 55: '🎁', 25: '🤗', 35: '👍🏻', 3: '🤝', 59: '✌🏻️', 27: '🙏', 57: '👊🏻',
    61: '🤨', 62: '😴', 63: '👏🏻', 64: '🐲'
};

var SVG_CARET = '<svg viewBox="0 0 24 24"><path d="M7 10l5 5 5-5z"/></svg>';
var SVG_LIKE = '<svg viewBox="0 0 24 24"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>';

var PAGE_SIZE = 20;

var bookId = '';
var chapterId = '';
var paragraphId = '';
var commentSource = '';   // 书源标识；空 = 起点
var currentPage = 1;
var totalComments = 0;
var totalPages = 1;
var hasMoreComments = true;
var nextCursor = '';      // 游标分页（书旗）：下一页的游标
var isLoading = false;
var isPopupMode = false;  // 半屏弹窗：页内跳转要带上 mode=popup，否则关闭按钮消失
var chapterName = '';     // 起点上游 BookInfo.ChapterName，形如「第九十九章 咒术」
var srcEmojiMap = null;
var srcEmojiPattern = null;

// 视图类型常量（与后端 comment_kind.go 一一对应）
var KIND_PARAGRAPH = 'paragraph';
var KIND_CHAPTER_SAY = 'chapterSay';
var KIND_TITLE_COMMENT = 'titleComment';
var KIND_AUTHOR_SAY = 'authorSay';
var KIND_BOOK_REVIEW = 'bookReview';

var currentKind = KIND_PARAGRAPH;

/* 当前源的段评语义。
 * 由服务端随页面注入（不额外发请求——半屏弹窗在 WebView 里网络往往不稳，
 * 多一次串行请求就多一次「卡在加载中」）。注入缺失时用起点的历史默认兜底，
 * 保证老页面/直接访问 URL 的场景不至于白屏。 */
var SEM = {
    paragraphIdKind: 'number1',
    chapterSayId: '0',
    titleCommentId: '-1',
    authorSayId: '-10',
    pagination: 'page',
    emojiEndpoint: '',
    bookReview: false,
    chapterSayView: 'endReview'
};

function semSupports(kind) {
    if (kind === KIND_PARAGRAPH) return true;
    if (kind === KIND_CHAPTER_SAY) return SEM.chapterSayId !== '-';
    if (kind === KIND_TITLE_COMMENT) return SEM.titleCommentId !== '-';
    if (kind === KIND_AUTHOR_SAY) return SEM.authorSayId !== '-';
    if (kind === KIND_BOOK_REVIEW) return !!SEM.bookReview;
    return false;
}

/* 存量兼容：老书源不带 kind，按该源声明的特殊值从 paragraphId 反推。
 * 新书源一律显式传 kind；等存量书源自然淘汰后这个函数可整体删除。 */
function inferKind(para) {
    var p = String(para == null ? '' : para);
    if (SEM.chapterSayId !== '-' && p === SEM.chapterSayId) return KIND_CHAPTER_SAY;
    if (SEM.titleCommentId !== '-' && p === SEM.titleCommentId) return KIND_TITLE_COMMENT;
    if (SEM.authorSayId !== '-' && p === SEM.authorSayId) return KIND_AUTHOR_SAY;
    return KIND_PARAGRAPH;
}

function isChapterSay() { return currentKind === KIND_CHAPTER_SAY; }
function isTitleComment() { return currentKind === KIND_TITLE_COMMENT; }
function isAuthorSay() { return currentKind === KIND_AUTHOR_SAY; }
function isBookReview() { return currentKind === KIND_BOOK_REVIEW; }
function isCursorPaging() { return SEM.pagination === 'cursor'; }

// ===== 工具 =====

function $(id) { return document.getElementById(id); }

function escapeHtml(text) {
    var d = document.createElement('div');
    d.textContent = (text == null ? '' : text);
    return d.innerHTML;
}

// 属性值转义（src/title/alt 等），防属性逃逸
function escAttr(t) {
    return String(t == null ? '' : t)
        .replace(/&/g, '&amp;').replace(/'/g, '&#39;').replace(/"/g, '&quot;')
        .replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// 内联事件里的单引号 JS 字符串字面量
function escJsStr(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/\\/g, '\\\\').replace(/'/g, "\\'")
        .replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\r?\n/g, ' ');
}

// URL 白名单：只放 http/https 与根路径，其余（javascript: 等）丢弃
function safeUrl(u) {
    var s = String(u == null ? '' : u).trim();
    if (s === '') return '';
    if (/^https?:\/\//i.test(s) || s.charAt(0) === '/') return escAttr(s);
    return '';
}

// "YYYY-MM-DD HH:mm:ss" 在 iOS/Safari 下 new Date 得到 Invalid Date，退回把空格换成 T 再试
function parseDate(s) {
    var d = new Date(s);
    if (isNaN(d.getTime()) && typeof s === 'string') d = new Date(s.replace(' ', 'T'));
    return d;
}

function formatTime(date) {
    if (!date || isNaN(date.getTime())) return '';
    var sec = Math.round((Date.now() - date.getTime()) / 1000);
    if (sec < 60) return '刚刚';
    var min = Math.round(sec / 60);
    if (min < 60) return min + ' 分钟前';
    var h = Math.round(min / 60);
    if (h < 24) return h + ' 小时前';
    var day = Math.round(h / 24);
    if (day < 30) return day + ' 天前';
    return date.getFullYear() + '/' + (date.getMonth() + 1) + '/' + date.getDate();
}

function formatNumber(num) {
    // 上游可能给字符串，强制转数字（也避免原样拼进 innerHTML）
    var n = Number(num) || 0;
    if (n >= 10000) return (n / 10000).toFixed(1) + ' 万';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
    return String(n);
}

/* 表情替换。
 * 起点：内置码表 [fn=N] → Unicode，无需网络。
 * 番茄 / 书旗：[占位符] → <img>，映射由各源的 emojiEndpoint 下发。 */
function replaceEmojis(text) {
    if (!text) return '';
    var out = String(text).replace(/\[fn=(\d+)\]/g, function (m, n) { return EMOJI_MAP[n] || m; });
    if (srcEmojiPattern) {
        out = out.replace(srcEmojiPattern, function (m) {
            var u = safeUrl(srcEmojiMap[m]);
            return u ? '<img src="' + u + '" class="fq-emoji" alt="' + escAttr(m) + '" loading="lazy">' : m;
        });
    }
    return out;
}

/* 拉该源的表情映射（占位符 → 图片 URL）。
 * 端点由 semantics 给出，所以新增源不用改这里；没有端点的源直接跳过。
 *
 * 注意占位符要按长度降序拼进正则：短占位符是长占位符的前缀时（如 [s] 与 [sad]），
 * 先匹配短的会把 [sad] 拆成 [s]+'ad]'。书旗的 54 个表情里就存在这种前缀关系。 */
function loadSourceEmoji() {
    var ep = SEM.emojiEndpoint || '';
    if (!ep || srcEmojiMap !== null) return Promise.resolve();
    return fetch(ep)
        .then(function (r) { return r.ok ? r.json() : {}; })
        .then(function (m) {
            // 番茄直接返回映射对象；书旗包在 {code,data} 里
            var map = (m && m.data && typeof m.data === 'object') ? m.data : (m || {});
            srcEmojiMap = map;
            var keys = Object.keys(map).sort(function (a, b) {
                return b.length - a.length || (a < b ? -1 : 1);
            });
            srcEmojiPattern = keys.length
                ? new RegExp(keys.map(function (e) { return e.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }).join('|'), 'g')
                : null;
        })
        .catch(function () { srcEmojiMap = {}; srcEmojiPattern = null; });
}

function apiBase() {
    // 后台「小说管理」里内嵌本页时走 /admin/api（不受前台段评权限闸门约束）
    return window.location.pathname.indexOf('/admin') >= 0 ? '/admin/api' : '/chapter';
}

function sourceQuery() { return commentSource ? '&source=' + encodeURIComponent(commentSource) : ''; }

function showError(msgHtml, isHtml) {
    var el = $('errorAlert');
    if (isHtml) el.innerHTML = msgHtml; else el.textContent = msgHtml;
    el.style.display = 'block';
}
function hideError() { $('errorAlert').style.display = 'none'; }
function showLoginPrompt() {
    showError('查看评论需要先登录。<a href="/user" target="_blank">前往登录</a>，登录后刷新本页。', true);
}

// ===== 标题 / 引用 =====

function commentLabel() {
    if (isChapterSay()) return '本章说';
    if (isTitleComment()) return '标题评论';
    if (isAuthorSay()) return '作家说';
    if (isBookReview()) return '本书评论';
    return '段评';
}

function setHeader(title, sub) {
    $('pageTitle').textContent = title;
    $('pageSub').textContent = sub || '';
}

function showQuote(text) {
    if (!text) return;
    $('paragraphText').textContent = text;
    $('paragraphQuote').style.display = 'block';
}

// ===== 入口 =====

document.addEventListener('DOMContentLoaded', function () {
    // 半屏弹窗（java.showBrowser(url, html)）里 window.location 往往拿不到 query，
    // 服务端已把参数写进 window.__CMT__，优先用它，其次回退 URL。
    var injected = (window.__CMT__ && typeof window.__CMT__ === 'object') ? window.__CMT__ : {};
    var q = new URLSearchParams(window.location.search);

    // ID 全部按字符串保存（番茄 19 位、起点 18 位，转数字会被截断）
    bookId = String(injected.bookId || q.get('bookId') || '');
    chapterId = String(injected.chapterId || q.get('chapterId') || '');
    paragraphId = String(injected.paragraphId || q.get('paragraphId') || '');
    commentSource = injected.source || q.get('source') || '';
    var quoteText = injected.text || q.get('text') || '';
    var mode = injected.mode || q.get('mode') || '';
    // 章节名：起点在段评响应里带回来（见下面的 d.chapterName），书旗的上游不给，
    // 由书源在标题气泡链接里传。两条路都填进同一个变量，「标题评论」的引用区只认它。
    chapterName = String(injected.chapterName || q.get('chapterName') || '');

    // 段评语义：服务端注入优先。缺失时保留文件顶部的起点默认值。
    if (injected.semantics && typeof injected.semantics === 'object') {
        for (var sk in injected.semantics) {
            if (Object.prototype.hasOwnProperty.call(injected.semantics, sk)) {
                SEM[sk] = injected.semantics[sk];
            }
        }
    }

    // 鉴权透传：弹窗在独立 WebView 里，fetch 未必带 admin_session cookie（表单登录只进 okhttp 罐）。
    // ct = 段评只读令牌（与登录会话解耦，看段评不会把阅读登录挤掉，首选）；_s = 旧的会话透传。
    var ct = injected.ct || q.get('ct') || '';
    var sess = injected._s || q.get('_s') || '';
    if ((ct || sess) && !window.__cmtFetchPatched) {
        window.__cmtFetchPatched = true;
        var origFetch = window.fetch;
        window.fetch = function (u, opt) {
            try {
                if (typeof u === 'string' && u.charAt(0) === '/') {
                    if (ct && u.indexOf('ct=') < 0) u += (u.indexOf('?') >= 0 ? '&' : '?') + 'ct=' + encodeURIComponent(ct);
                    if (sess && u.indexOf('_s=') < 0) u += (u.indexOf('?') >= 0 ? '&' : '?') + '_s=' + encodeURIComponent(sess);
                }
            } catch (e) { /* 忽略 */ }
            return origFetch.call(this, u, opt);
        };
    }

    // 视图类型：显式 kind 优先；缺失时按该源的语义从 paragraphId 反推（存量书源）。
    var kindParam = String(injected.kind || q.get('kind') || '');
    if (kindParam && semSupports(kindParam)) {
        currentKind = kindParam;
    } else {
        // /chapterComments 路径且没带 paragraphId 时等价于本章说
        if (!paragraphId && /chapterComments/i.test(window.location.pathname) && semSupports(KIND_CHAPTER_SAY)) {
            currentKind = KIND_CHAPTER_SAY;
            paragraphId = SEM.chapterSayId;
        } else {
            currentKind = inferKind(paragraphId);
        }
    }

    if (mode === 'popup') {
        isPopupMode = true;
        document.body.classList.add('popup-mode');
    }

    // 该源不支持这种视图：明确告知，而不是发一个注定失败的请求。
    // （原先的表现是空列表，用户分不清「没有评论」和「这个源没这功能」。）
    if (kindParam && !semSupports(kindParam)) {
        $('mainContent').style.display = 'block';
        setHeader(commentLabel(), '');
        showError('当前书源不支持「' + commentLabel() + '」。');
        return;
    }

    // 本章说与本书评论不需要 paragraphId（书旗的本章说 paragraphId 本就是空串）
    var needsPara = (currentKind === KIND_PARAGRAPH);
    if (!bookId || !chapterId || (needsPara && !paragraphId)) {
        $('paramPrompt').style.display = 'block';
        return;
    }

    $('mainContent').style.display = 'block';
    setHeader(commentLabel(), '');

    // 表情映射先加载，避免首屏评论里的占位符先以原文闪现再被替换
    var start = loadSourceEmoji();

    // 「加载更多」「重新加载」「滚动到底自动加载」三个交互，凡是走 loadComments
    // 的视图都要绑。
    //
    // 曾经这三行写在函数末尾，而本章说（list 形态）与作家说都在中途 return，
    // 于是书旗的本章说/本书评论页拿到的是一个「按钮画出来了但没有任何监听」的壳：
    // 首屏正常，点「加载更多」毫无反应，滚到底也不会自动加载，出错后点「重新加载」
    // 同样没反应。而且不报错——按钮就在那儿，只是没人听。
    // 起点的 endReview 是章评/段评双分页聚合，自己管刷新与分页，不能绑这套。
    function bindListLoaders() {
        $('loadMoreBtn').addEventListener('click', function () {
            if (!isLoading && hasMoreComments) loadComments(currentPage + 1, true);
        });
        $('refresh-btn').addEventListener('click', function () {
            $('commentList').innerHTML = '';
            $('skeletonLoading').style.display = 'block';
            loadComments(1, false);
        });
        window.addEventListener('scroll', onScroll);
    }

    // 本章说的渲染形态由源声明：
    //   endReview = 章评 + 段评双分页（起点的聚合端点）
    //   list      = 普通评论列表（书旗；若支持本书评论会多一个 Tab）
    if (isChapterSay()) {
        if (SEM.chapterSayView === 'endReview') {
            start.then(function () { loadEndReview(); });
        } else {
            bindListLoaders();
            start.then(function () { setupChapterSayTabs(); loadComments(1, false); });
        }
        return;
    }

    // 作家说 = 一张作家说卡片 + 一个评论列表，两者都要加载。
    //
    // 曾经这里只调 loadAuthorSay() 就 return，于是评论列表从未被请求过：
    // 骨架屏只在 loadComments 的 finally 里隐藏，不调它就永久停在灰条状态，
    // 表现为「作家说正文显示出来了，下面的评论一直转圈」。
    // loadAuthorSay 只负责把卡片插到引用区之前，不碰骨架也不碰列表，两者互不干扰。
    if (isAuthorSay()) {
        bindListLoaders(); // 作家说评论也会超过一页，「加载更多/重新加载/滚动到底」必须绑上
        start.then(function () { loadAuthorSay(); loadComments(1, false); });
        return;
    }
    // 引用区：段落原文优先由接口回填；标题评论没有原文，用章节名（见 loadComments）
    if (quoteText && !isTitleComment() && !isBookReview()) {
        showQuote(quoteText);
    }

    start.then(function () { loadComments(1, false); });
    bindListLoaders();
});

function onScroll() {
    if (isLoading || !hasMoreComments) return;
    var top = document.documentElement.scrollTop || document.body.scrollTop;
    var height = document.documentElement.scrollHeight || document.body.scrollHeight;
    var view = document.documentElement.clientHeight || window.innerHeight;
    if (top + view >= height - 120) loadComments(currentPage + 1, true);
}

function goToComments() {
    var b = $('inputBookId').value.trim();
    var c = $('inputChapterId').value.trim();
    var p = $('inputParagraphId').value.trim();
    if (!b || !c || !p) { showError('请填写书籍 ID、章节 ID 与段落号'); return; }
    window.location.href = '?bookId=' + encodeURIComponent(b) +
        '&chapterId=' + encodeURIComponent(c) +
        '&paragraphId=' + encodeURIComponent(p) +
        '&kind=' + encodeURIComponent(KIND_PARAGRAPH) + sourceQuery();
}

function closeCommentPopup() {
    // 阅读器/轻悦时光的关闭桥；都没有时退回后退
    try { if (window.flutterBridge && window.flutterBridge.close) { window.flutterBridge.close(); return; } } catch (e) {}
    try { if (window.android && window.android.close) { window.android.close(); return; } } catch (e) {}
    try { window.close(); } catch (e) {}
    try { history.back(); } catch (e) {}
}

// ===== 段评 / 标题评论 / 作家说评论列表 =====

function loadComments(page, append) {
    if (isLoading) return;
    isLoading = true;
    if (append) {
        $('loadMoreBtn').disabled = true;
        $('loadMoreBtn').textContent = '加载中…';
    } else {
        // 首屏/刷新：重置游标，否则刷新会从上次的位置接着加载
        nextCursor = '';
    }
    hideError();
    $('refresh-btn').style.display = 'none';

    var url = apiBase() + '/comments?bookId=' + encodeURIComponent(bookId) +
        '&chapterId=' + encodeURIComponent(chapterId) +
        '&paragraphId=' + encodeURIComponent(paragraphId) +
        '&kind=' + encodeURIComponent(currentKind) +
        '&pageSize=' + PAGE_SIZE + sourceQuery();
    // 游标分页（书旗）：翻页靠 cursor，page 参数对它无意义。
    // 首页（page<=1 或不追加）必须重置游标，否则刷新会从上次的位置继续。
    if (isCursorPaging()) {
        if (append && nextCursor) url += '&cursor=' + encodeURIComponent(nextCursor);
    } else {
        url += '&page=' + page;
    }

    fetch(url)
        .then(function (r) {
            if (r.status === 401 || r.status === 403) {
                var e = new Error('请先登录'); e.needLogin = true; throw e;
            }
            if (!r.ok) throw new Error('网络异常');
            return r.json();
        })
        .then(function (res) {
            if (res.code !== 0) throw new Error(res.message || '获取评论失败');
            var d = res.data || {};
            var pg = d.pagination || {};
            var list = d.comments || [];

            currentPage = Number(pg.currentPage) || page;
            totalComments = Number(pg.totalCount) || 0;
            totalPages = Number(pg.totalPages) || 1;
            // hasNext 是统一字段名，hasNextPage 是起点/番茄的历史字段名，两者都认
            hasMoreComments = !!(pg.hasNextPage || pg.hasNext);
            var prevCursor = nextCursor;
            nextCursor = String(pg.nextCursor || '');
            if (isCursorPaging()) {
                // 没给下一页游标 = 到底了（书旗不返回总数，只能靠这个判定）
                if (!nextCursor) hasMoreComments = false;
                // 游标没前进却仍说有下一页：上游异常。继续请求会无限拉同一页，
                // 表现为「滚动到底后重复内容刷不完」，必须在这里截断。
                else if (append && nextCursor === prevCursor) hasMoreComments = false;
            }
            if (d.chapterName) chapterName = String(d.chapterName);

            // 引用区：标题评论没有段落原文，改用章节名（上游已含「第N章」前缀）
            if (isTitleComment()) {
                if (chapterName) showQuote(chapterName);
            } else if (d.paragraphContent) {
                showQuote(d.paragraphContent);
            }

            setHeader(commentLabel(), totalComments > 0 ? totalComments + ' 条' : '');

            if (!append) $('commentList').innerHTML = '';
            if (!list.length && !append) {
                $('commentList').innerHTML = '<div class="no-comments">还没有' + commentLabel() + '</div>';
            } else {
                renderComments(list, $('commentList'));
            }
            updateLoadMore();
        })
        .catch(function (e) {
            if (e && e.needLogin) showLoginPrompt();
            else showError('获取' + commentLabel() + '失败：' + e.message);
            $('refresh-btn').style.display = 'inline-flex';
        })
        .finally(function () {
            $('skeletonLoading').style.display = 'none';
            $('loadMoreBtn').disabled = false;
            isLoading = false;
        });
}

function updateLoadMore() {
    var btn = $('loadMoreBtn');
    var hint = $('paginationInfo');
    if (hasMoreComments) {
        btn.style.display = 'inline-block';
        btn.textContent = '加载更多';
    } else {
        btn.style.display = 'none';
    }
    // 游标分页的源拿不到总页数，显示「第 N / 1 页」会是错的；此时只显示已加载条数。
    if (isCursorPaging()) {
        var n = $('commentList').querySelectorAll('.comment-item').length;
        hint.textContent = n > 0 ? ('已加载 ' + n + ' 条') : '';
    } else {
        hint.textContent = totalPages > 1 ? ('第 ' + currentPage + ' / ' + totalPages + ' 页') : '';
    }
}

// 被回复者昵称：起点用 RelatedUser，番茄由后端归一化成 ReplyToUser。
// 主楼回复时两边都为空——此时被回复者就是主楼作者，不必重复标注。
function replyTarget(reply) {
    var n = reply.RelatedUser || reply.ReplyToUser || '';
    return String(n || '').trim();
}

function avatarHtml(name, icon, cls) {
    var initial = String(name || '用').charAt(0);
    var u = safeUrl(icon);
    var inner = u
        ? '<img src="' + u + '" alt="" onerror="this.parentNode.textContent=\'' + escJsStr(initial) + '\'">'
        : escapeHtml(initial);
    return '<div class="' + cls + '">' + inner + '</div>';
}

function titlesHtml(list) {
    if (!list || !Array.isArray(list)) return '';
    var out = '';
    list.forEach(function (t) {
        var img = t && t.TitleImage ? String(t.TitleImage).trim() : '';
        if (!img) return;
        var u = safeUrl(img);
        if (!u) return;
        out += '<img src="' + u + '" class="title-img" alt="' + escAttr(t.TitleName || '') +
            '" title="' + escAttr(t.TitleName || '') + '" onerror="this.style.display=\'none\'">';
    });
    return out ? '<div class="user-titles">' + out + '</div>' : '';
}

/* 评论图片字段候选（大小写敏感直查；番茄/起点系常见命名尽量都覆盖）。
 * 番茄段评此前只读 ImageDetail 单字段，图片放其它字段/多图时全部丢失，
 * 这里改成与起点页 commentImages 一致的多字段 + 递归提取。 */
var COMMENT_IMG_FIELDS = [
    'ImageDetail', 'PreImage', 'ImageUrl', 'Images', 'ImageList', 'ImgUrl', 'Imgs',
    'Image', 'CommentImg', 'CommentImage', 'Photo', 'PicUrl',
    'image_url', 'img_url', 'image_list', 'pic_list', 'reply_img_url'
];

/* 递归收集图片地址：
 * null/数组/嵌套对象(取所有值)/JSON 字符串(数组或对象串)/普通字符串。
 * 去重并限制数量，避免恶意/超长列表拖垮渲染。 */
function collectCommentImages(value, out, limit) {
    if (out.length >= limit) return out;
    if (value == null) return out;
    if (Array.isArray(value)) {
        for (var i = 0; i < value.length; i++) {
            collectCommentImages(value[i], out, limit);
            if (out.length >= limit) break;
        }
        return out;
    }
    if (typeof value === 'string') {
        var t = value.trim();
        if (!t) return out;
        if (t.charAt(0) === '{' || t.charAt(0) === '[') {
            try { collectCommentImages(JSON.parse(t), out, limit); } catch (e) {}
            return out;
        }
        if (out.indexOf(t) >= 0) return out;
        var u = safeUrl(t);
        if (u) out.push(u);
        return out;
    }
    if (typeof value === 'object') {
        for (var k in value) {
            if (Object.prototype.hasOwnProperty.call(value, k)) {
                collectCommentImages(value[k], out, limit);
                if (out.length >= limit) break;
            }
        }
    }
    return out;
}

function extractCommentImages(item) {
    var found = [];
    for (var i = 0; i < COMMENT_IMG_FIELDS.length; i++) {
        var v = item == null ? null : item[COMMENT_IMG_FIELDS[i]];
        if (v == null) continue;
        collectCommentImages(v, found, 6);
        if (found.length >= 6) break;
    }
    return found;
}

function mediaHtml(item, imgClass) {
    var out = '';
    var au = safeUrl(item.AudioUrl);
    if (au) out += '<audio src="' + au + '" class="comment-audio" controls preload="none"></audio>';
    var cls = imgClass || 'comment-image';
    var imgs = extractCommentImages(item);
    for (var i = 0; i < imgs.length; i++) {
        out += '<img src="' + imgs[i] + '" class="' + cls + '" alt="评论图片" loading="lazy" onerror="this.style.display=\'none\'">';
    }
    return out;
}

// 楼中楼一条。只显示时间/属地/赞数——回复的踩数与回复数上游基本恒为 0，
// 挂一排 0 只会变成视觉噪音。
function renderReplyHtml(reply) {
    if (!reply.UserName || reply.UserName === '匿名用户') return '';
    var to = replyTarget(reply);
    var like = Number(reply.AgreeAmount) || 0;
    var time = formatTime(parseDate(reply.CreateTime));
    var meta = [];
    if (time) meta.push(escapeHtml(time));
    if (reply.IpLocation) meta.push(escapeHtml(reply.IpLocation));

    return '<div class="nested-comment">' +
        avatarHtml(reply.UserName, reply.UserHeadIcon, 'nested-avatar') +
        '<div class="nested-body">' +
            '<div class="nested-head">' +
                '<span class="nested-name">' + escapeHtml(reply.UserName) + '</span>' +
                (to ? '<span class="nested-to">回复 <b>' + escapeHtml(to) + '</b></span>' : '') +
                titlesHtml(reply.TitleInfoList) +
            '</div>' +
            '<div class="nested-text">' + replaceEmojis(escapeHtml(reply.Content || '')) + mediaHtml(reply) + '</div>' +
            '<div class="nested-meta">' +
                (meta.length ? '<span>' + meta.join(' · ') + '</span>' : '') +
                (like > 0 ? '<span class="meta-like">' + SVG_LIKE + formatNumber(like) + '</span>' : '') +
            '</div>' +
        '</div>' +
    '</div>';
}

function renderComments(comments, container) {
    comments.forEach(function (c) {
        if (!c.UserName || c.UserName === '匿名用户') return;

        var el = document.createElement('div');
        el.className = 'comment-item';
        // ID 原样存字符串（18/19 位大数，dataset 存数字会丢精度）
        if (c.Id != null) el.dataset.reviewId = String(c.Id);

        var frame = safeUrl(c.FrameUrl);
        var like = Number(c.AgreeAmount) || 0;
        var time = formatTime(parseDate(c.CreateTime));
        var meta = [];
        if (time) meta.push(escapeHtml(time));
        if (c.IpLocation) meta.push(escapeHtml(c.IpLocation));
        var floor = Number(c.Floor) || 0;

        var html = '<div class="user-avatar-container">' +
                (frame ? '<img src="' + frame + '" class="avatar-frame" alt="" onerror="this.style.display=\'none\'">' : '') +
                avatarHtml(c.UserName, c.UserHeadIcon, 'user-avatar') +
            '</div>' +
            '<div class="comment-content">' +
                '<div class="comment-header">' +
                    '<span class="user-name">' + escapeHtml(c.UserName) + '</span>' +
                    titlesHtml(c.TitleInfoList) +
                    (floor > 0 ? '<span class="comment-floor">' + floor + ' 楼</span>' : '') +
                '</div>' +
                '<div class="comment-text">' + replaceEmojis(escapeHtml(c.Content || '')) + mediaHtml(c) + '</div>' +
                '<div class="comment-meta">' +
                    (meta.length ? '<span>' + meta.join(' · ') + '</span>' : '') +
                    (like > 0 ? '<span class="meta-like">' + SVG_LIKE + formatNumber(like) + '</span>' : '') +
                '</div>';

        var replies = Array.isArray(c.Replies) ? c.Replies : [];
        // 上游给的是「回复总数」，而列表里内联的往往只是前几条预览。
        // 两者不等时要提供「加载更多回复」，否则剩下的回复用户永远看不到
        // ——而界面上却写着「N 条回复」，点开只有 3 条。
        var replyTotal = Number(c.ReviewCount) || replies.length;
        if (replies.length) {
            var inner = '';
            replies.forEach(function (r) { inner += renderReplyHtml(r); });
            if (inner) {
                var moreBtn = replyTotal > replies.length
                    ? '<div class="reply-more" onclick="loadMoreReplies(this)">查看全部 ' + replyTotal + ' 条回复 ›</div>'
                    : '';
                html += '<div class="nested-comments" data-review-id="' + escAttr(String(c.Id == null ? '' : c.Id)) +
                        '" data-reply-total="' + replyTotal + '">' +
                    '<div class="reply-toggle" onclick="toggleReplies(this)">' +
                        '<span>' + replyTotal + ' 条回复</span>' + SVG_CARET +
                    '</div>' +
                    '<div class="replies-container" style="display:none">' + inner + moreBtn + '</div>' +
                '</div>';
            }
        }

        html += '</div>';
        el.innerHTML = html;
        container.appendChild(el);
    });
}

function toggleReplies(el) {
    var box = el.nextElementSibling;
    if (!box) return;
    var open = box.style.display !== 'none';
    box.style.display = open ? 'none' : 'block';
    el.classList.toggle('open', !open);
    var wrap = el.parentNode;
    var total = Number(wrap && wrap.dataset ? wrap.dataset.replyTotal : 0) ||
        box.querySelectorAll('.nested-comment').length;
    el.querySelector('span').textContent = open ? (total + ' 条回复') : '收起回复';
}

/* 加载某条主楼的完整回复（楼中楼）。
 * 起点与书旗都有 /chapter/comment-replies；带 kind 与游标参数，与主列表同一套口径。 */
function loadMoreReplies(btn) {
    var wrap = btn.closest ? btn.closest('.nested-comments') : null;
    if (!wrap || !wrap.dataset) return;
    var rid = wrap.dataset.reviewId || '';
    if (!rid || wrap.dataset.loading === '1') return;
    wrap.dataset.loading = '1';
    var original = btn.textContent;
    btn.textContent = '加载中…';

    var url = apiBase() + '/comment-replies?bookId=' + encodeURIComponent(bookId) +
        '&chapterId=' + encodeURIComponent(chapterId) +
        '&paragraphId=' + encodeURIComponent(paragraphId) +
        '&commentId=' + encodeURIComponent(rid) +
        '&kind=' + encodeURIComponent(currentKind) +
        '&pageSize=' + PAGE_SIZE +
        (wrap.dataset.cursor ? '&cursor=' + encodeURIComponent(wrap.dataset.cursor) : '') +
        sourceQuery();

    fetch(url)
        .then(function (r) { return r.ok ? r.json() : { code: -1 }; })
        .then(function (res) {
            if (res.code !== 0) throw new Error(res.message || '加载失败');
            // 起点直接返回数组；书旗包在 data.comments 里
            var d = res.data;
            var list = Array.isArray(d) ? d : ((d && d.comments) || []);
            var pg = (d && d.pagination) || {};
            var box = wrap.querySelector('.replies-container');
            if (!box) return;
            // 重建整段回复区：接口返回的是完整列表，直接替换比去重合并更不容易出错
            var inner = '';
            list.forEach(function (r) { inner += renderReplyHtml(r); });
            var cursor = String(pg.nextCursor || '');
            var hasMore = !!(pg.hasNext || pg.hasNextPage) && !!cursor;
            wrap.dataset.cursor = cursor;
            box.innerHTML = inner + (hasMore
                ? '<div class="reply-more" onclick="loadMoreReplies(this)">继续加载回复 ›</div>'
                : '');
            var tg = wrap.querySelector('.reply-toggle span');
            if (tg && !hasMore) tg.textContent = '收起回复';
        })
        .catch(function (e) {
            btn.textContent = '加载失败，点击重试';
            btn.title = (e && e.message) || '';
        })
        .finally(function () {
            wrap.dataset.loading = '0';
            if (btn.textContent === '加载中…') btn.textContent = original;
        });
}

// ===== 作家说（paragraphId=-10，仅起点）=====

function loadAuthorSay() {
    fetch(apiBase() + '/activity?bookId=' + encodeURIComponent(bookId) + '&chapterId=' + encodeURIComponent(chapterId))
        .then(function (r) { return r.ok ? r.json() : { code: -1 }; })
        .then(function (res) {
            if (res.code !== 0 || !res.data) return;
            var d = res.data;
            if (!d.authorSay || !String(d.authorSay).trim()) return;
            var card = document.createElement('div');
            card.className = 'author-say-card';
            card.id = 'authorSayCard';
            var av = safeUrl(d.authorAvatar);
            card.innerHTML =
                '<div class="author-say-head">' +
                    (av ? '<img src="' + av + '" class="author-say-avatar" alt="" onerror="this.style.display=\'none\'">' : '') +
                    '<div class="author-say-name">' + escapeHtml(d.authorName || '作者') + '</div>' +
                    '<span class="author-say-tag">作家说</span>' +
                '</div>' +
                '<div class="author-say-text">' + replaceEmojis(escapeHtml(d.authorSay)).replace(/\n/g, '<br>') + '</div>';
            var quote = $('paragraphQuote');
            quote.parentNode.insertBefore(card, quote);
        })
        .catch(function () {});
}

// ===== 本章说（paragraphId=0，仅起点）：章评 + 段评双分页 =====

function loadEndReview() {
    var eb = encodeURIComponent(bookId), ec = encodeURIComponent(chapterId);

    // 先绑刷新，首屏失败时按钮也可用
    $('refresh-btn').onclick = loadEndReview;

    $('skeletonLoading').style.display = 'block';
    $('commentList').innerHTML = '';
    $('loadMoreBtn').style.display = 'none';
    $('paginationInfo').textContent = '';
    var old = $('authorSayCard');
    if (old) old.remove();
    hideError();
    $('refresh-btn').style.display = 'none';

    Promise.all([
        fetch(apiBase() + '/end-review?bookId=' + eb + '&chapterId=' + ec).then(function (r) {
            if (r.status === 401 || r.status === 403) { var e = new Error('请先登录'); e.needLogin = true; throw e; }
            if (!r.ok) throw new Error('网络异常');
            return r.json();
        }),
        fetch(apiBase() + '/activity?bookId=' + eb + '&chapterId=' + ec)
            .then(function (r) { return r.ok ? r.json() : { code: -1 }; })
            .catch(function () { return { code: -1 }; })
    ]).then(function (rs) {
        var res = rs[0], act = rs[1];
        if (res.code !== 0) throw new Error(res.message || '获取失败');
        var d = res.data || {};
        var chapterComments = d.ChapterDataList || [];
        var segments = d.SegmentDataList || [];

        $('skeletonLoading').style.display = 'none';

        // 本章说条数以 end-review 的 ChapterReviewTotalCount 为准：
        // /activity 的 chapterSayCount 取自聚合值会偏大。
        var total = (typeof d.ChapterReviewTotalCount === 'number' && d.ChapterReviewTotalCount >= 0)
            ? d.ChapterReviewTotalCount
            : ((act.code === 0 && act.data && act.data.chapterSayCount) || chapterComments.length);

        setHeader('本章说', total + ' 条章评 · ' + segments.length + ' 段有段评');

        var box = $('commentList');
        var tabs = document.createElement('div');
        tabs.className = 'chapter-say-tabs';
        tabs.innerHTML =
            '<span id="tabChapter" class="chapter-say-tab active" onclick="switchEndReviewTab(\'chapter\')">章评 ' + chapterComments.length + '</span>' +
            '<span id="tabSegment" class="chapter-say-tab" onclick="switchEndReviewTab(\'segment\')">段评 ' + segments.length + ' 段</span>';
        box.appendChild(tabs);

        var chapterDiv = document.createElement('div');
        chapterDiv.id = 'chapterReviewTab';
        if (!chapterComments.length) chapterDiv.innerHTML = '<div class="no-comments">还没有章评</div>';
        else renderComments(chapterComments, chapterDiv);
        box.appendChild(chapterDiv);

        var segDiv = document.createElement('div');
        segDiv.id = 'segmentReviewTab';
        segDiv.style.display = 'none';
        if (!segments.length) segDiv.innerHTML = '<div class="no-comments">本章还没有段评</div>';
        else renderSegments(segments, segDiv);
        box.appendChild(segDiv);
    }).catch(function (e) {
        $('skeletonLoading').style.display = 'none';
        if (e && e.needLogin) showLoginPrompt();
        else showError('获取本章说失败：' + e.message);
        $('refresh-btn').style.display = 'inline-flex';
    });
}

/* 列表型本章说的 Tab 栏（书旗）。
 *
 * 书旗独有「本书评论」，它和本章说是两个不同的上游接口，但对用户来说是
 * 「同一本书的讨论」的两个层级——放在同一个弹窗里切换，比让用户回到书籍页再找入口自然。
 * 只有源声明了 bookReview 才渲染这个 Tab，其它源看不到它。 */
function setupChapterSayTabs() {
    if (!semSupports(KIND_BOOK_REVIEW)) return;
    var box = $('commentList');
    if (!box || $('cmtKindTabs')) return;
    var tabs = document.createElement('div');
    tabs.id = 'cmtKindTabs';
    tabs.className = 'chapter-say-tabs';
    tabs.innerHTML =
        '<span id="tabKindChapterSay" class="chapter-say-tab active" onclick="switchCommentKind(\'' + KIND_CHAPTER_SAY + '\')">本章说</span>' +
        '<span id="tabKindBookReview" class="chapter-say-tab" onclick="switchCommentKind(\'' + KIND_BOOK_REVIEW + '\')">本书评论</span>';
    box.parentNode.insertBefore(tabs, box);
}

/* 切换视图（本章说 ↔ 本书评论）。
 * 切换要把分页状态整体重置——游标不清会让新视图从上一个视图的位置接着加载。 */
function switchCommentKind(kind) {
    if (kind === currentKind || isLoading) return;
    if (!semSupports(kind)) return;
    currentKind = kind;
    currentPage = 1;
    nextCursor = '';
    hasMoreComments = true;
    totalComments = 0;
    totalPages = 1;

    var a = $('tabKindChapterSay'), b = $('tabKindBookReview');
    if (a) a.classList.toggle('active', kind === KIND_CHAPTER_SAY);
    if (b) b.classList.toggle('active', kind === KIND_BOOK_REVIEW);

    setHeader(commentLabel(), '');
    $('commentList').innerHTML = '';
    $('paginationInfo').textContent = '';
    $('skeletonLoading').style.display = 'block';
    hideError();
    loadComments(1, false);
}

function switchEndReviewTab(which) {
    var c = $('chapterReviewTab'), s = $('segmentReviewTab');
    var bc = $('tabChapter'), bs = $('tabSegment');
    if (!c || !s) return;
    var toChapter = which === 'chapter';
    c.style.display = toChapter ? '' : 'none';
    s.style.display = toChapter ? 'none' : '';
    bc.classList.toggle('active', toChapter);
    bs.classList.toggle('active', !toChapter);
}

function renderSegments(segments, container) {
    segments.forEach(function (seg) {
        // 段号是小整数（-1=标题），可安全取整；书籍/章节 ID 仍按字符串透传
        var paraId = parseInt(seg.ParagraphsId, 10);
        if (isNaN(paraId)) paraId = 0;
        var count = Number(seg.ReviewCount) || 0;
        var label = paraId === -1 ? '标题' : (seg.IsImgSegment ? '插图' : '第 ' + paraId + ' 段');
        var jump = 'openParagraphComments(\'' + escJsStr(bookId) + '\',\'' + escJsStr(chapterId) + '\',\'' + paraId + '\')';

        var div = document.createElement('div');
        div.className = 'segment-group';

        var html = '<div class="segment-header" onclick="' + jump + '">' +
                '<div class="segment-header-top">' +
                    '<span class="segment-label">' + label + '</span>' +
                    '<span class="segment-count">' + count + ' 条 ›</span>' +
                '</div>' +
                '<div class="segment-quote">' + escapeHtml(seg.QuoteContent || '') + '</div>' +
            '</div>';

        var list = seg.DataList || [];
        if (list.length) {
            html += '<div class="segment-preview">';
            list.slice(0, 3).forEach(function (sc) {
                var like = Number(sc.AgreeAmount) || 0;
                var av = safeUrl(sc.UserHeadIcon);
                html += '<div class="segment-preview-item">' +
                    '<div class="segment-preview-head">' +
                        (av ? '<img src="' + av + '" class="segment-preview-avatar" alt="" onerror="this.style.display=\'none\'">' : '') +
                        '<span class="segment-preview-name">' + escapeHtml(sc.UserName || '书友') + '</span>' +
                        (like > 0 ? '<span class="segment-preview-like">' + SVG_LIKE + formatNumber(like) + '</span>' : '') +
                    '</div>' +
                    '<div class="segment-preview-content">' + replaceEmojis(escapeHtml(sc.Content || '')) + '</div>' +
                '</div>';
            });
            if (count > list.slice(0, 3).length) {
                html += '<div class="segment-preview-more" onclick="' + jump + '">查看全部 ' + count + ' 条 ›</div>';
            }
            html += '</div>';
        }

        div.innerHTML = html;
        container.appendChild(div);
    });
}

// 段落号按字符串传，避免大数被 JS 数字化（书旗的段号是 'p000' 这类不透明串，
// 更不能当数字处理）。显式带 kind=paragraph，不再靠段号值让后端去猜视图类型。
function openParagraphComments(bid, cid, paraId) {
    var popup = isPopupMode || window.location.search.indexOf('mode=popup') >= 0;
    window.location.href = '/comments?bookId=' + encodeURIComponent(bid) +
        '&chapterId=' + encodeURIComponent(cid) +
        '&paragraphId=' + encodeURIComponent(paraId) +
        '&kind=' + encodeURIComponent(KIND_PARAGRAPH) +
        (popup ? '&mode=popup' : '') + sourceQuery();
}
