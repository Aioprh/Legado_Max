function isFq(u) { u = String(u || ''); return /source=fanqie/.test(u) || /\/fanqie\//.test(u); }

function fqPick(u, re, d) { var m = String(u || '').match(re); return m ? m[1] : d; }

function jparse(r) { try { return JSON.parse(r) || {}; } catch (e) { return {}; } }
function fqEnsureEnv(result) {
    var java = this.java, self = this;
    function checkEnv() {
        try { new Packages.io.legato.kazusa.utils.TimeoutCancellationException(''); return true; }
        catch (e) { return (typeof self.source.loginUi == 'function') ? false : true; }
    }
    try { java.put('bbpd', checkEnv() ? '改版' : '原版'); } catch (e) { java.put('bbpd', '原版'); }
    var devType = '安卓';
    try { java.deviceID(); devType = '苹果'; java.put('dev', 'ios'); }
    catch (e) { try { java.androidId(); java.put('dev', 'android'); } catch (e2) { java.put('dev', 'android'); } }
    try { java.qread(); java.put('dev', 'android-轻阅读'); } catch (e) {}
    try { java.put('bbSvg', String(jparse(result).bubbleSvg || '')); } catch (e) {}
    try { var _bs=jparse(result); java.put('bbStyle', String((_bs.bubbleStyle!=null?_bs.bubbleStyle:(_bs.data&&_bs.data.bubbleStyle))||'1')); } catch (e) {}
    return devType;
}

function fqGetComments(content, list, bid, cid) {
    var java = this.java, cache = this.cache, self = this;
    try {
        if (!list || !list.length) return content;
        var separator = '\n';
        if (/<p>/.test(content)) { content = content.replace('<p>', '').replace('\n', ''); separator = '<p>'; }
        else { content = content.replace(/\r/g, '').replace(/\n$/, ''); }
        var comcont = content.split(separator);
        for (var k = 0; k < list.length; k++) {
            var x = list[k], idx = Number(x.paragraphId) - 1;
            var pIndex = (typeof x.paraIndex !== 'undefined') ? x.paraIndex : x.paragraphId;
            if (idx >= 0 && idx < comcont.length && comcont[idx]) {
                cache.putMemory('fqreader-' + bid + '-' + cid + '-' + pIndex + '-text', comcont[idx]);

                var svg = createSvg.call({ source: self.source, java: java, cache: cache }, x.textCount, bid, cid, pIndex);

                svg = svg.split('"androidshowCmt(').join('"fqAndroidShowCmt(').split('"showCmt(').join('"fqShowCmt(');

                svg = svg.split('(' + bid + ', ' + cid + ', ').join("('" + bid + "','" + cid + "', ");
                comcont[idx] += '<img src="' + svg + '">';
            }
        }
        return comcont.join('\n');
    } catch (e) { java.log('fqGetComments: ' + e); return content; }
}

function fqGetCommentsios(content, list, bid, cid) {
    var java = this.java, qt = String(this.source.bookSourceUrl);
    try {
        if (!list || !list.length) return content;
        var cleaned = (content || '').replace(/\r/g, '').trim();
        var raw = cleaned.split('\n');
        for (var i = 0; i < raw.length; i++) raw[i] = raw[i].trim();
        var paragraphs = [null];
        for (var i = 0; i < raw.length; i++) paragraphs[i + 1] = raw[i];
        for (var k = 0; k < list.length; k++) {
            var x = list[k], pid = Number(x.paragraphId), cnt = Math.min(x.textCount || 0, 99);
            var pIndex = (typeof x.paraIndex !== 'undefined') ? x.paraIndex : pid;
            if (pid > 0 && pid < paragraphs.length && cnt > 0) {
                var orig = paragraphs[pid] || '';
                var title = (orig.substring(0, 30) + (orig.length > 30 ? '...' : '')).replace(/'/g, "\\'").replace(/"/g, '\\"');

                var url = qt + '/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=' + pIndex + '&source=fanqie&text=' + encodeURIComponent(title) + this.appSession();
                paragraphs[pid] = orig + '<comment count="' + cnt + '" onClick="java.showReadingBrowser(\'' + url + '\', \'' + title + '\')" />';
            }
        }
        var res = [];
        for (var m = 1; m < paragraphs.length; m++) { res.push('<span rs-native>' + (paragraphs[m] || '') + '</span>'); }
        return res.join('\n');
    } catch (e) { java.log('fqGetCommentsios: ' + e); return content; }
}

function fqShowCmt(bid, cid, para, date, dev, bbpd) {
    var qt = String(this.source.bookSourceUrl), java = this.java, cache = this.cache;
    var mname = 'fqreader-' + bid + '-' + cid + '-' + para;
    try {
        var title = cache.getFromMemory(mname + '-text') || '番茄段评';
        var url = qt + '/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=' + para + '&source=fanqie&text=' + encodeURIComponent(title) + this.appSession();
        try { java.showBrowser(url + '&mode=popup', java.ajax(url + '&mode=popup')); } catch (e2) { java.startBrowser(url, title); }
    } catch (e) { java.log('fqShowCmt: ' + e); }
}
function fqAndroidShowCmt(bid, cid, para, date, dev, bbpd) {
    var qt = String(this.source.bookSourceUrl), java = this.java, cache = this.cache;
    var mname = 'fqreader-' + bid + '-' + cid + '-' + para;
    var load = (cache.getFromMemory(mname) || '-').split('-');
    if (load[0] != 1 || load[1] != date) { cache.putMemory(mname, '1-' + date); return; }
    try {
        var title = cache.getFromMemory(mname + '-text') || '番茄段评';
        var url = qt + '/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=' + para + '&source=fanqie&text=' + encodeURIComponent(title) + this.appSession();
        try { java.showBrowser(url + '&mode=popup', java.ajax(url + '&mode=popup')); } catch (e2) { java.startBrowser(url, title); }
    } catch (e) { java.log('fqAndroidShowCmt: ' + e); }
}

function fqExploreItems(body, srv) {
    var out = [];
    function isArr(o) { return Object.prototype.toString.call(o) === '[object JavaList]' || (o && o.constructor === Array); }
    function push(b) {
        if (!b || !(b.book_id || b.book_name)) return;
        out.push({
            name: b.book_name || b.title || '', author: b.author || '',
            cover: b.thumb_url || b.cover_url || b.audio_thumb_uri || '', intro: b.abstract || '',
            kind: [b.category, b.sub_title].filter(Boolean).join('\n'), last: b.last_chapter_title || '', words: b.word_number || '',
            bookUrl: srv + '/detail?source=fanqie&bookId=' + (b.book_id || '') + '&format=normalized&min=1'
        });
    }
    function walk(node) {
        if (!node || typeof node !== 'object') return;

        if (isArr(node.book_info)) for (var i = 0; i < node.book_info.length; i++) push(node.book_info[i]);
        if (isArr(node.book_data)) for (var i = 0; i < node.book_data.length; i++) push(node.book_data[i]);
        if (isArr(node.video_data)) for (var i = 0; i < node.video_data.length; i++) push(node.video_data[i]);
        if (node.book_id || node.book_name) push(node);
        if (isArr(node.cell_data)) for (var i = 0; i < node.cell_data.length; i++) walk(node.cell_data[i]);
        if (isArr(node.data)) for (var i = 0; i < node.data.length; i++) walk(node.data[i]);
        if (isArr(node.item_list)) for (var i = 0; i < node.item_list.length; i++) walk(node.item_list[i]);
        if (isArr(node.item_data_list)) for (var i = 0; i < node.item_data_list.length; i++) walk(node.item_data_list[i]);
        if (isArr(node)) for (var i = 0; i < node.length; i++) walk(node[i]);
    }
    try { var j = JSON.parse(body); walk(j.data != null ? j.data : j); } catch (e) { this.java.log('fqExploreItems: ' + e); }
    return out;
}

function fusionExplore(result, baseUrl) {
    var srv = String(this.source.bookSourceUrl).match(/^https?:\/\/[^\/]+/)[0];

    if (isFq(baseUrl)) return JSON.stringify(fqExploreItems.call(this, result, srv));

    var out = [];
    try {
        var j = JSON.parse(result); var list = (j.data && (j.data.records || j.data.list)) || [];
        for (var i = 0; i < list.length; i++) {
            var b = list[i];
            out.push({
                name: b.bName || '', author: b.bAuth || '',
                cover: 'https://bookcover.yuewen.com/qdbimg/349573/' + (b.bid || '') + '/600',
                intro: b.desc || '', kind: [b.cat, b.subCat, b.state].filter(Boolean).join(' '),
                last: b.lastVUCname || '', words: b.cnt || '',
                bookUrl: srv + '/detail?bookId=' + (b.bid || '') + '&format=normalized&min=1'
            });
        }
    } catch (e) { this.java.log('fusionExplore qd: ' + e); }
    return JSON.stringify(out);
}

function generateCsrfToken() { var ck = String(this.cookie.getCookie('https://m.qidian.com') || ''); if (!/_csrfToken=/.test(ck)) { ck = String(this.cookie.getCookie('https://qidian.com') || ''); } var m = ck.match(/_csrfToken=([^;]*)/); return m ? m[1] : ''; }
function qdtoken() { var ck = String(this.cookie.getCookie('https://m.qidian.com') || ''); if (!ck) { ck = String(this.cookie.getCookie('https://qidian.com') || ''); } return ck; }
function qdCommentToken() { try { var s = this.source, j = this.java; var t = String(s.get('qd_ct') || ''); if (t) return t; var r = JSON.parse(j.ajax(String(s.bookSourceUrl) + '/comment-token')); if (r && r.code === 0 && r.token) { t = String(r.token); try { s.put('qd_ct', t); } catch (e) {} return t; } } catch (e) {} return ''; }
function appSession() { try { var t = qdCommentToken.call(this); if (t) return '&ct=' + encodeURIComponent(t); } catch (e) {} try { var ck = String(this.cookie.getCookie(String(this.source.bookSourceUrl)) || ''); var m = ck.match(/admin_session=([^;]*)/); return m ? '&_s=' + encodeURIComponent(m[1]) : ''; } catch (e) { return ''; } }
function refreshQdToken() { try { qdEnsureToken.call(this); } catch (e) {} }
function qdReviewFetch(bid, cid) {
    var java = this.java, self = this;
    try { qdEnsureToken.call(this); } catch (e) {}
    var base = 'https://m.qidian.com/majax/chapterReview/reviewSummary?bookId=' + bid + '&chapterId=' + cid;
    function once() { try { return java.ajax(base + '&_csrfToken=' + self.generateCsrfToken() + ',{"headers":{"Cookie":"' + self.qdtoken() + '"}}'); } catch (e) { return ''; } }
    function ok(r) { if (!r) return false; try { var j = JSON.parse(r); return !!(j && (j.code === 0 || (j.data && j.data.list))); } catch (e) { return false; } }
    var r = once();
    if (!ok(r)) { self.refreshQdToken(); r = once(); }
    return r;
}
function getCommentsios(content, bid, cid) {
    var qt = String(this.source.bookSourceUrl);
    var java = this.java, cache = this.cache;
    try {
        var comments = this.qdReviewFetch(bid, cid);
        var commentList = [];
        if (comments) { try { var commentData = JSON.parse(comments); commentList = commentData?.data?.list || []; } catch (e) {} }
        try { java.put('qd_title_svg', ''); for (var _tt = 0; _tt < commentList.length; _tt++) { if (Number(commentList[_tt].paragraphId) === -1 && (commentList[_tt].textCount || 0) > 0) { java.put('qd_title_svg', createSvg.call({ source: this.source, java: java, cache: cache }, commentList[_tt].textCount, bid, cid, -1)); break; } } } catch (e) {}
        var cleanedContent = (content || '').replace(/\r/g, '').trim();
        var rawParagraphs = cleanedContent.split('\n');
        for (var i = 0; i < rawParagraphs.length; i++) { rawParagraphs[i] = rawParagraphs[i].trim(); }
        var isImgLine = []; var textToRaw = [null];
        for (var i = 0; i < rawParagraphs.length; i++) { var im = /^<img\s+src="https?:/.test(rawParagraphs[i]); isImgLine[i] = im; if (!im) textToRaw.push(i); }
        for (var j = 0; j < commentList.length; j++) {
            var comment = commentList[j];
            var targetPId = Number(comment.paragraphId);
            var limitedTextCount = Math.min(comment.textCount || 0, 99);
            var ri = textToRaw[targetPId];
            if (ri != null && limitedTextCount > 0) {
                var originalText = rawParagraphs[ri] || '';
                var paraTitle = originalText.substring(0, 30) + (originalText.length > 30 ? '...' : '');
                paraTitle = paraTitle.replace(/'/g, "\\'").replace(/"/g, '\\"');
                var commentUrl = qt + '/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=' + targetPId + this.appSession();
                var commentTag = '<comment count="' + limitedTextCount + '" onClick="java.showReadingBrowser(\'' + commentUrl + '\', \'' + paraTitle + '\')" />';
                rawParagraphs[ri] = originalText + commentTag;
            }
        }
        var result = [];
        for (var k = 0; k < rawParagraphs.length; k++) { var p = rawParagraphs[k]; if (isImgLine[k]) { result.push(p); } else { result.push('<span rs-native>' + (p || '') + '</span>'); } }
        return result.join('\n');
    } catch (e) { java.log(e); return content; }
}
function getComments(content, bid, cid) {
    var qt = String(this.source.bookSourceUrl);
    var java = this.java, cache = this.cache;
    try {
        var separator = '\n';
        if (/<p>/.test(content)) { content = content.replace('<p>','').replace('\n', ''); separator = '<p>'; } else { content = content.replace(/\r/g, '').replace(/\n$/, ''); }
        var comcont = content.split(separator);
        var _ti = []; for (var _ci = 0; _ci < comcont.length; _ci++) { if (!/^\s*<img\s+src="https?:/.test(comcont[_ci])) _ti.push(_ci); }
        var comments = this.qdReviewFetch(bid, cid);
        var commentList = [];
        if (comments) { try { var commentData = JSON.parse(comments); commentList = commentData?.data?.list || []; } catch (e) {} }
        if (commentList.length == 0) { return content; }
        try { java.put('qd_title_svg', ''); for (var _tt = 0; _tt < commentList.length; _tt++) { if (Number(commentList[_tt].paragraphId) === -1 && (commentList[_tt].textCount || 0) > 0) { java.put('qd_title_svg', createSvg.call({ source: this.source, java: java, cache: cache }, commentList[_tt].textCount, bid, cid, -1)); break; } } } catch (e) {}
        commentList.forEach(function(x) {
            var _pi = _ti[x.paragraphId - 1];
            if (_pi != null && comcont[_pi] != null) {
                cache.putMemory('qdreader-' + bid + '-' + cid + '-' + x.paragraphId + '-text', comcont[_pi]);
                comcont[_pi] += '<img src="' + createSvg.call({source: this.source, java: java, cache: cache}, x.textCount, bid, cid, x.paragraphId) + '">';
            }
        }.bind(this));
        return comcont.join('\n');
    } catch (e) { java.log(e); return content; }
}
function createSvg(number, bid, cid, para) {
    var java = this.java, cache = this.cache;
    var qpyscolor = '#A9A9A9', qpwzcolor = '#A9A9A9';
    try { var c1 = this.source.getLoginInfoMap().get('气泡颜色'); if (c1 && c1 != '') qpyscolor = c1; } catch(e) {}
    try { var c2 = this.source.getLoginInfoMap().get('气泡文字'); if (c2 && c2 != '') qpwzcolor = c2; } catch(e) {}
    var displayText = number > 99 ? '99' : number;
    var dev = java.get('dev');
    var bbpd = java.get('bbpd');
    var bbSvg = String(java.get('bbSvg') || '');
    var svg = '';
    if (bbSvg) { svg = bbSvg.split('{n}').join(displayText).split('{c}').join(qpyscolor).split('{t}').join(qpwzcolor); }
    else if (dev == 'ios') { svg = '<svg t="1760002253572" class="icon" viewBox="-300 300 1500 1500" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="1490" width="850" height="850"><g transform="rotate(90 512 512) scale(1.3 1.3) translate(380, -200)"><path d="M512 938.666667c-27.733333 0-55.466667-12.8-72.533333-34.133334l-46.933334-55.466666c-14.933333-17.066667-34.133333-25.6-57.6-25.6h-110.933333c-76.8 0-138.666667-61.866667-138.666667-138.666667V224C85.333333 147.2 147.2 85.333333 224 85.333333h573.866667c76.8 0 138.666667 61.866667 138.666666 138.666667v460.8c0 76.8-61.866667 138.666667-138.666666 138.666667h-108.8c-21.333333 0-42.666667 8.533333-55.466667 25.6l-49.066667 55.466666c-17.066667 21.333333-44.8 34.133333-72.533333 34.133334zM224 149.333333C183.466667 149.333333 149.333333 183.466667 149.333333 224v460.8c0 40.533333 34.133333 74.666667 74.666667 74.666667h110.933333c40.533333 0 78.933333 17.066667 104.533334 49.066666l46.933333 55.466667c6.4 6.4 14.933333 10.666667 25.6 10.666667 8.533333 0 19.2-4.266667 23.466667-10.666667l49.066666-55.466667c25.6-29.866667 64-46.933333 104.533334-46.933333h108.8c40.533333 0 74.666667-34.133333 74.666666-74.666667V224C874.666667 183.466667 840.533333 149.333333 800 149.333333h-576z" fill="' + qpyscolor + '" p-id="1530"></path></g><text x="700" y="1300" font-family="Arial, sans-serif" text-anchor="middle" dominant-baseline="middle" font-size="450" fill="' + qpwzcolor + '">' + displayText + '</text></svg>'; }
    else if (dev == 'android-轻阅读') { svg = '<svg t="1760002253572" class="icon" viewBox="-150 0 1224 1224" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="1490" width="800" height="800"><g transform="rotate(90 512 512) scale(1 1.2) translate(150, 0)"><path d="M512 938.666667c-27.733333 0-55.466667-12.8-72.533333-34.133334l-46.933334-55.466666c-14.933333-17.066667-34.133333-25.6-57.6-25.6h-110.933333c-76.8 0-138.666667-61.866667-138.666667-138.666667V224C85.333333 147.2 147.2 85.333333 224 85.333333h573.866667c76.8 0 138.666667 61.866667 138.666666 138.666667v460.8c0 76.8-61.866667 138.666667-138.666666 138.666667h-108.8c-21.333333 0-42.666667 8.533333-55.466667 25.6l-49.066667 55.466666c-17.066667 21.333333-44.8 34.133333-72.533333 34.133334zM224 149.333333C183.466667 149.333333 149.333333 183.466667 149.333333 224v460.8c0 40.533333 34.133333 74.666667 74.666667 74.666667h110.933333c40.533333 0 78.933333 17.066667 104.533334 49.066666l46.933333 55.466667c6.4 6.4 14.933333 10.666667 25.6 10.666667 8.533333 0 19.2-4.266667 23.466667-10.666667l49.066666-55.466667c25.6-29.866667 64-46.933333 104.533334-46.933333h108.8c40.533333 0 74.666667-34.133333 74.666666-74.666667V224C874.666667 183.466667 840.533333 149.333333 800 149.333333h-576z" fill="' + qpyscolor + '" p-id="1530"></path></g><text x="480" y="780" font-family="Arial, sans-serif" text-anchor="middle" dominant-baseline="middle" font-size="340" fill="' + qpwzcolor + '">' + displayText + '</text></svg>'; }
    else { svg = '<svg t="1760002253572" class="icon" viewBox="-150 0 1224 1224" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="1490" width="800" height="800"><g transform="rotate(90 512 512) scale(1 1.2)"><path d="M512 938.666667c-27.733333 0-55.466667-12.8-72.533333-34.133334l-46.933334-55.466666c-14.933333-17.066667-34.133333-25.6-57.6-25.6h-110.933333c-76.8 0-138.666667-61.866667-138.666667-138.666667V224C85.333333 147.2 147.2 85.333333 224 85.333333h573.866667c76.8 0 138.666667 61.866667 138.666666 138.666667v460.8c0 76.8-61.866667 138.666667-138.666666 138.666667h-108.8c-21.333333 0-42.666667 8.533333-55.466667 25.6l-49.066667 55.466666c-17.066667 21.333333-44.8 34.133333-72.533333 34.133334zM224 149.333333C183.466667 149.333333 149.333333 183.466667 149.333333 224v460.8c0 40.533333 34.133333 74.666667 74.666667 74.666667h110.933333c40.533333 0 78.933333 17.066667 104.533334 49.066666l46.933333 55.466667c6.4 6.4 14.933333 10.666667 25.6 10.666667 8.533333 0 19.2-4.266667 23.466667-10.666667l49.066666-55.466667c25.6-29.866667 64-46.933333 104.533334-46.933333h108.8c40.533333 0 74.666667-34.133333 74.666666-74.666667V224C874.666667 183.466667 840.533333 149.333333 800 149.333333h-576z" fill="' + qpyscolor + '" p-id="1530"></path></g><text x="460" y="620" font-family="Arial, sans-serif" text-anchor="middle" dominant-baseline="middle" font-size="400" fill="' + qpwzcolor + '">' + displayText + '</text></svg>'; }
    var encodedSvg = java.base64Encode(svg);
    var date = Date.now().toString();
    var pdsb = '';
    if (dev == 'android-轻阅读' || dev == 'ios' || bbpd == '改版') { pdsb = 'showCmt'; } else { pdsb = 'androidshowCmt'; }
    if (bbpd == '改版') { return 'data:image/svg+xml;base64,' + encodedSvg + ',{"style":"text","type":"qd","click":"' + pdsb + '(' + bid + ', ' + cid + ', ' + para + ', ' + date + ',\'' + dev + '\',\'' + bbpd + '\')"}';}
    else { return 'data:image/svg+xml;base64,' + encodedSvg + ',{"style":"text","type":"qd","js":"' + pdsb + '(' + bid + ', ' + cid + ', ' + para + ', ' + date + ',\'' + dev + '\',\'' + bbpd + '\')"}';}
}
function showCmt(bid, cid, para, date, dev, bbpd) {
    var qt = String(this.source.bookSourceUrl);
    var java = this.java, cache = this.cache;
    var mname = 'qdreader-' + bid + '-' + cid + '-' + para;
    try { var title = cache.getFromMemory(mname + '-text') ?? '起点段评'; var url = qt + '/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=' + para + '&text=' + encodeURIComponent(title) + this.appSession(); try { java.showBrowser(url + '&mode=popup', java.ajax(url + '&mode=popup')); } catch (e2) { java.startBrowser(url, title); } } catch (error) { java.log('获取或处理评论失败: ' + error); }
}
function androidshowCmt(bid, cid, para, date, dev, bbpd) {
    var qt = String(this.source.bookSourceUrl);
    var java = this.java, cache = this.cache;
    var mname = 'qdreader-' + bid + '-' + cid + '-' + para;
    var load = (cache.getFromMemory(mname) ?? '-').split('-');
    if (load[0] != 1 || load[1] != date) { cache.putMemory(mname, '1-' + date); return; }
    try { var title = cache.getFromMemory(mname + '-text') ?? '起点段评'; var url = qt + '/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=' + para + '&text=' + encodeURIComponent(title) + this.appSession(); try { java.showBrowser(url + '&mode=popup', java.ajax(url + '&mode=popup')); } catch (e2) { java.startBrowser(url, title); } } catch (error) { java.log('获取或处理评论失败: ' + error); }
}
function godEsc(s){return String(s==null?'':s).replace(/[<>&"']/g,function(ch){return({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;',"'":'&#39;'})[ch];});}
function createGodSvg(godItem, bid, cid, paraId){
    var java=this.java;
    if(!godItem||!godItem.Content)return '';
    var dev=java.get('dev'), bbpd=java.get('bbpd');
    var w=1000,padX=40,padY=24,badgeW=96,badgeH=46,badgeFont=26,font=38,lh=60;
    var raw=String(godItem.Content||'').replace(/\r/g,'').replace(/\n/g,' ').trim();
    var lines=[],cur='',cw=0,first=true;
    var maxW1=w-padX*2-badgeW-24, maxW2=w-padX*2;
    for(var i=0;i<raw.length;i++){
        var ch=raw.charAt(i), chW=(raw.charCodeAt(i)>255)?font:(font*0.55), tmax=first?maxW1:maxW2;
        if(cw+chW>tmax&&cur.length>0){lines.push(cur);cur=ch;cw=chW;first=false;}
        else{cur+=ch;cw+=chW;}
    }
    if(cur.length>0)lines.push(cur);
    var totalH=padY*2+lines.length*lh;
    var svg='<svg width="'+w+'" height="'+totalH+'" viewBox="0 0 '+w+' '+totalH+'" xmlns="http://www.w3.org/2000/svg"><rect width="100%" height="100%" fill="rgba(255,255,255,0.55)" rx="30"/>';
    var flcY=padY+lh/2, badgeY=flcY-badgeH/2, btY=flcY+badgeFont*0.35;
    svg+='<rect x="'+padX+'" y="'+badgeY+'" width="'+badgeW+'" height="'+badgeH+'" rx="'+(badgeH/2)+'" fill="#F06260"/>';
    svg+='<text x="'+(padX+badgeW/2)+'" y="'+btY+'" font-size="'+badgeFont+'" fill="#FFFFFF" text-anchor="middle" font-weight="bold">热评</text>';
    for(var li=0;li<lines.length;li++){
        var cx=(li===0)?(padX+badgeW+20):padX, cy=padY+lh/2+li*lh+font*0.35;
        svg+='<text x="'+cx+'" y="'+cy+'" font-size="'+font+'" fill="#333333">'+godEsc(lines[li])+'</text>';
    }
    svg+='</svg>';
    var date=Date.now().toString();
    var pdsb=(dev=='android-轻阅读'||dev=='ios'||bbpd=='改版')?'showCmt':'androidshowCmt';
    var handler=(bbpd=='改版')?'click':'js';
    return '<img src="data:image/svg+xml;base64,'+java.base64Encode(svg)+',{"style":"FULL","type":"qd","'+handler+'":"'+pdsb+'('+bid+', '+cid+', '+paraId+', '+date+',\''+dev+'\',\''+bbpd+'\')"}">';
}
function getGodComments(content, bid, cid){
    var java=this.java;
    try{
        var url=String(this.source.bookSourceUrl)+'/chapter/god-comments?bookId='+bid+'&chapterId='+cid+this.appSession();
        var resp=java.ajax(url);
        var data=null;
        if(resp){try{var j=JSON.parse(resp);if(j&&j.code===0)data=j.data;}catch(e){}}
        if(!data)return content;
        var lines=content.split('\n');
        var out=[];
        var pid=0;
        for(var i=0;i<lines.length;i++){
            out.push(lines[i]);
            if(/^\s*<img\s+src="https?:/.test(lines[i]))continue;
            pid++;
            var god=data[pid];
            if(god&&god.Content){
                var img=createGodSvg.call({java:java,source:this.source,cache:this.cache},god,bid,cid,pid);
                if(img){

                    if(/^<span rs-native>/.test(lines[i])){out.push('<span rs-native>'+img+'</span>');}
                    else{out.push(img);}
                }
            }
        }
        return out.join('\n');
    }catch(e){java.log(e);return content;}
}

function qdEnsureToken(){
    var source=this.source, cookie=this.cookie;
    var tok='';
    try{ tok=String(source.get('qd_csrf')||''); }catch(e){}
    if(!tok){
        tok=(Date.now().toString(36)+Math.random().toString(36).slice(2,10));
        try{ source.put('qd_csrf', tok); }catch(e){}
    }
    try{ cookie.replaceCookie('https://m.qidian.com', '_csrfToken='+tok); }catch(e){}
    return tok;
}

function qdWrap(s, per, max) {
    var L = [], ln = ''; s = String(s || '');
    for (var i = 0; i < s.length; i++) { var c = s[i]; if (c == '\n') { if (ln) L.push(ln); ln = ''; continue; } ln += c; if (ln.length >= per) { L.push(ln); ln = ''; } }
    if (ln) L.push(ln);
    if (max && L.length > max) { L = L.slice(0, max); L[max - 1] = L[max - 1].substring(0, per - 1) + '\u2026'; }
    return L;
}
function qdHeart(x, y, scale, fill) {
    return '<g transform="translate(' + x + ',' + y + ') scale(' + scale + ')"><path d="M560.6 410.36l243.84-14.2a133.76 133.76 0 0 1 128.72 170.16l-36.72 129.8-2.56 9.08c-47.32 168.84-125.6 258.32-236.4 258.32H221.04A141.08 141.08 0 0 1 80 822.4v-290.92C80 459.2 127.68 416.12 211.12 392a197.28 197.28 0 0 0 123.56-119.2l55.64-150.96a101.04 101.04 0 0 1 192.64 9.72c19.84 76.92 9.36 166.32-30.28 267.92a8 8 0 0 0 7.92 10.88z" fill="' + fill + '"/></g>';
}
function qdCardImg(svg, bid, cid, para) {
    var java = this.java;
    var dev = java.get('dev'), bbpd = java.get('bbpd');
    var date = Date.now().toString();
    var pdsb = (dev == 'android-\u8f7b\u9605\u8bfb' || dev == 'ios' || bbpd == '\u6539\u7248') ? 'showCmt' : 'androidshowCmt';
    var handler = (bbpd == '\u6539\u7248') ? 'click' : 'js';
    return '<img src="data:image/svg+xml;base64,' + java.base64Encode(svg) + ',{"style":"FULL","type":"qd","' + handler + '":"' + pdsb + '(' + bid + ', ' + cid + ', ' + para + ', ' + date + ',\'' + dev + '\',\'' + bbpd + '\')"}">';
}
function getSayData(bid, cid) {
    var java = this.java, qt = String(this.source.bookSourceUrl);
    var total = 0, seen = {}, list = [];
    function add(u, a, c, img) { u = String(u || ''); if (!u) return; var k = u + '|' + String(c || '').slice(0, 20); if (seen[k]) return; seen[k] = 1; list.push({ UserName: u, AgreeAmount: a || 0, Content: c || '', ImageDetail: img || '' }); }
    try { var jc = JSON.parse(java.ajax(qt + '/chapter/comments?bookId=' + bid + '&chapterId=' + cid + '&paragraphId=0' + this.appSession())); var dc = (jc && jc.data) || {}; total = (dc.pagination && dc.pagination.totalCount) || ((dc.comments || []).length) || 0; var cc = dc.comments || []; for (var i = 0; i < cc.length; i++) add(cc[i].UserName, cc[i].AgreeAmount, cc[i].Content, cc[i].ImageDetail); } catch (e) {}
    try { var ja = JSON.parse(java.ajax(qt + '/chapter/activity?bookId=' + bid + '&chapterId=' + cid + this.appSession())); var hc = (ja && ja.data && ja.data.hotComments) || []; for (var h = 0; h < hc.length; h++) add(hc[h].userName, hc[h].agreeAmount, hc[h].content, ''); } catch (e) {}
    return { total: total, list: list };
}
function getAuthorSay(bid, cid) {
    var java = this.java;
    try {
        var url = String(this.source.bookSourceUrl) + '/chapter/activity?bookId=' + bid + '&chapterId=' + cid + this.appSession();
        var j = JSON.parse(java.ajax(url)); var d = (j && j.data) || {};
        return { name: d.authorName || '', say: String(d.authorSay || '').trim() };
    } catch (e) { return { name: '', say: '' }; }
}
function createAuthorCard(author, bid, cid) {
    var padX = 44, y = 40, AMBER = '#D98A3D', TC = '#333333', MUTED = '#8A8A8A', bw = 164, bh = 56, bf = 34, font = 38, lh = 56;
    var body = '<rect x="' + padX + '" y="' + y + '" width="' + bw + '" height="' + bh + '" rx="' + (bh / 2) + '" fill="' + AMBER + '"/>'
        + '<text x="' + (padX + bw / 2) + '" y="' + (y + bh / 2 + bf * 0.35) + '" font-size="' + bf + '" fill="#FFF" text-anchor="middle" font-weight="bold">\u4f5c\u5bb6\u8bf4</text>'
        + '<text x="' + (padX + bw + 20) + '" y="' + (y + bh / 2 + 12) + '" font-size="36" fill="' + AMBER + '" font-weight="bold">' + godEsc(author.name || '\u4f5c\u8005') + '</text>';
    y += bh + 22;
    var lines = qdWrap(godEsc(String(author.say || '')), 26, 10);
    for (var i = 0; i < lines.length; i++) { body += '<text x="' + padX + '" y="' + (y + 38) + '" font-size="' + font + '" fill="' + TC + '">' + lines[i] + '</text>'; y += lh; }
    y += 10;
    body += '<text x="' + (1000 - padX) + '" y="' + (y + 34) + '" font-size="34" fill="' + AMBER + '" text-anchor="end" font-weight="bold">\u67e5\u770b\u4f5c\u5bb6\u8bf4\u8bc4\u8bba \u3009</text>';
    y += 34 + 36;
    try { this.cache.putMemory('qdreader-' + bid + '-' + cid + '--10-text', '\u4f5c\u5bb6\u8bf4'); } catch (e) {}
    var svg = '<svg width="1000" height="' + y + '" xmlns="http://www.w3.org/2000/svg"><rect width="100%" height="100%" rx="30" fill="rgba(252,246,236,0.62)"/>' + body + '</svg>';
    return qdCardImg.call(this, svg, bid, cid, -10);
}
function createSayCard(total, list, bid, cid) {
    var padX = 44, y = 40, GREEN = '#5E8A6A', TC = '#333333', MUTED = '#8A8A8A', bw = 164, bh = 56, bf = 34, font = 38;
    var body = '<rect x="' + padX + '" y="' + y + '" width="' + bw + '" height="' + bh + '" rx="' + (bh / 2) + '" fill="' + GREEN + '"/>'
        + '<text x="' + (padX + bw / 2) + '" y="' + (y + bh / 2 + bf * 0.35) + '" font-size="' + bf + '" fill="#FFF" text-anchor="middle" font-weight="bold">\u672c\u7ae0\u8bf4</text>'
        + '<text x="' + (1000 - padX) + '" y="' + (y + bh / 2 + 12) + '" font-size="34" fill="' + GREEN + '" text-anchor="end" font-weight="bold">' + total + ' \u6761\u8bc4\u8bba \u3009</text>';
    y += bh + 22;
    (list || []).slice().sort(function (a, b) { return (b.AgreeAmount || 0) - (a.AgreeAmount || 0); }).slice(0, 3).forEach(function (it) {
        var uname = godEsc(it.UserName || '\u533f\u540d'), like = it.AgreeAmount || 0, numStr = String(like);
        var _raw = String(it.Content || ''); if (it.ImageDetail) { _raw = _raw ? (_raw + ' [图片]') : '[图片]'; } var show = qdWrap(godEsc(_raw), 20, 4);
        body += '<text x="' + padX + '" y="' + (y + 38) + '" font-size="36" fill="' + GREEN + '" font-weight="bold">' + uname + '</text>';
        var heartX = (1000 - padX) - numStr.length * 20 - 48;
        body += qdHeart(heartX, y + 8, 0.038, MUTED);
        body += '<text x="' + (1000 - padX) + '" y="' + (y + 42) + '" font-size="32" fill="' + MUTED + '" text-anchor="end" font-weight="bold">' + numStr + '</text>';
        y += 56;
        for (var si = 0; si < show.length; si++) { body += '<text x="' + padX + '" y="' + (y + 36) + '" font-size="' + font + '" fill="' + TC + '">' + show[si] + '</text>'; y += 52; }
        y += 26;
    });
    y += 12;
    var svg = '<svg width="1000" height="' + y + '" xmlns="http://www.w3.org/2000/svg"><rect width="100%" height="100%" rx="30" fill="rgba(244,249,245,0.62)"/>' + body + '</svg>';
    return qdCardImg.call(this, svg, bid, cid, 0);
}
function getTitleBubble(bid, cid) {
    var java = this.java;
    try {
        var comments = this.qdReviewFetch(bid, cid);
        var list = []; if (comments) { try { list = (JSON.parse(comments).data || {}).list || []; } catch (e) {} }
        for (var i = 0; i < list.length; i++) {
            if (Number(list[i].paragraphId) === -1 && list[i].textCount > 0) {
                try { this.cache.putMemory('qdreader-' + bid + '-' + cid + '--1-text', '\u6807\u9898\u8bc4\u8bba'); } catch (e) {}
                return '<img src="' + createSvg.call({ source: this.source, java: java, cache: this.cache }, list[i].textCount, bid, cid, -1) + '">';
            }
        }
    } catch (e) {}
    return '';
}
