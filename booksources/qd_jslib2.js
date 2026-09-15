const api = 'https://qd.aadcn.cn'

function getApiUrl(path, data = {}) {
  data = Object.entries(data).map(v => v.join('=')).join('&')
  let option = {
    method: "GET",
    headers: {
      'authorization': `Bearer ${this.source.getVariable() || ''}`,
    }
  }
  let url = api + path + '?' + data + ',' + JSON.stringify(option)
  return url
}

function requestApiUrl(path, data = {}) {
  let url = getApiUrl.call(this, path, data)
  try {
    let res = JSON.parse(this.java.ajax(url))
    if (res.msg && res.msg != 'success') {
      this.java.longToast(`\n操作失败，code${res.code}：\n${res.msg}`)
    }
    if (res.data) {
      return res.data
    }
  } catch (e) {
    this.java.log(`请求共享接口${path}异常：` + e.message)
  }
  return null
}

function getCsrfToken(that) {
  let { java, cookie } = that
  let bsu = "https://m.qidian.com"
  let csrf = cookie.getKey(bsu, "_csrfToken")
  let getStr = length => {
    let chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return Array(length)
      .fill("")
      .map(() => chars[Math.floor(62 * Math.random())])
      .join("")
  }
  if (!String(csrf)) {
    csrf = getStr(40)
    cookie.replaceCookie(bsu, "_csrfToken=" + csrf)
  }
  return "_csrfToken=" + csrf
}

function Map(k){
 var s = null;
 try { s = this && this.source ? this.source : null; } catch(e) {}
 try { if (!s && typeof source !== "undefined") s = source; } catch(e2) {}
 var m = s && s.getLoginInfoMap ? s.getLoginInfoMap() : null;
 if(k==null) return m;
 var v=m && (m.get ? m.get(k) : m[k]);
 if ((v === null || v === undefined || String(v) === "") && s && s.get) {
  try { v = s.get(k); } catch(e3) {}
 }
 return v==null?"":String(v);
}

function qdToggle(key, defaultValue) {
 var value = Map.call(this, key);
 if (value === "") return !!defaultValue;
 value = String(value).replace(/^\s+|\s+$/g, "").toLowerCase();
 return value === "✅" || value === "1" || value === "true" || value === "开" || value === "开启";
}

function getCfg() {
 var dp = qdToggle.call(this, "段评开关", true);
 var zm = qdToggle.call(this, "章名段评", true);
 var tl = qdToggle.call(this, "本章讨论", true);
 var zz = qdToggle.call(this, "作者评论", true);
 var rp = qdToggle.call(this, "热门评论", true);
 return { fs: qdToggle.call(this, "段评分色", false), dp: dp, zm: zm, tl: tl, zz: zz, rp: rp, pl: dp || zm || tl || zz || rp };
}

function qdLoginVal(key, def) {
 try {
  var s = null;
  try { s = this && this.source ? this.source : null; } catch(e0) {}
  try { if (!s && typeof source !== "undefined") s = source; } catch(e1) {}
  var m = s && s.getLoginInfoMap ? s.getLoginInfoMap() : null;
  var v = m && (m.get ? m.get(key) : m[key]);
  if (v !== null && v !== undefined && String(v) !== "") return String(v);
 } catch(e) {}
 return def == null ? "" : String(def);
}

function qdApiBase() {
    return "https://pl.aadcn.cn/api/qidian_full_api.php";
}
function qdApiUrl(action, params, compat) {
    params = params || {};
    params.action = action;
    if (compat) params.compat = 1;
    var qs = [];
    for (var k in params) {
        if (!params.hasOwnProperty(k)) continue;
        if (params[k] === undefined || params[k] === null || params[k] === "") continue;
        qs.push(encodeURIComponent(k) + "=" + encodeURIComponent(String(params[k])));
    }
    return qdApiBase() + "?" + qs.join("&");
}
function qdJsonParse(raw) {
    var text = String(raw || "{}").trim();
    text = text.replace(/("(?:MidpageId|MidPageId|PageId)"\s*:\s*)(\d{16,})/g, '$1"$2"');
    return JSON.parse(text || "{}");
}

function qdApi(action, params) {
    const { java } = this;
    var url = qdApiUrl.call(this, action, params || {}, false);
    var raw = "";
    try {
        raw = java && java.ajax ? java.ajax(url) : java.get(url, {}).body();
        return qdJsonParse(raw);
    } catch(e) {
        try { if (java && java.log) java.log("起点评论接口请求失败：" + e); } catch(_) {}
        return { code: -1, message: String(e), data: {}, rawText: raw };
    }
}

function qdMidpageEnabled() {
    return Map.call(this, "\u5f69\u86cb\u7ae0\u5f00\u5173") == "✅";
}

function qdMidpageChapters(bid) {
    try {
        var res = qdApi.call(this, "midpage_chapters", {book_id: bid});
        var data = res && (res.data || res.Data || {}) || {};
        var list = data.Chapters || data.chapters || data.DataList || data.MidpageChapters || data.MidPageChapters || data;
        return qdList(list);
    } catch(e) {
        return [];
    }
}

function setMidpageUrl(bid, cid, midpageId) {
    var eggKey = "midpage:" + String(bid) + ":" + String(cid) + ":" + String(midpageId || "");
    return "data:;base64," + java.base64Encode(String(eggKey)) + ",{\"type\":\"novel\",\"novelId\":\"" + String(bid) + "\",\"chapterId\":\"" + String(cid) + "\",\"qimoType\":\"midpage\",\"midpageId\":\"" + String(midpageId || "") + "\"}";
}


function fetchMidpageContent(bid, cid, midpageId) {
    var html = "";
    var runtimeJava = null;
    try { runtimeJava = this && this.java ? this.java : java; } catch(e0) {}
    try {
        var res = qdApi.call(this, "midpage_pageinfo", {book_id: bid, chapter_id: cid, needAdv: 0});
        var data = res && (res.data || res.Data || {}) || {};
        var midList = qdList(data.MidPageList || data.MidpageList || data.midPageList || data.Pages || data);
        if (midpageId) {
            var filtered = [];
            for (var fi = 0; fi < midList.length; fi++) {
                var item = midList[fi] || {};
                var cfg = item.MidPageConfig || item.MidpageConfig || {};
                var pid = cfg.PageId || cfg.MidPageId || cfg.MidpageId || item.PageId || item.MidPageId || item.MidpageId || "";
                if (String(pid) === String(midpageId)) filtered.push(item);
            }
            if (filtered.length) midList = filtered;
        }

        function pickText(o) {
            if (!o) return "";
            var value = o.Text || o.text || o.Title || o.title || o.Desc || o.desc || o.Content || o.content || "";
            return String(value || "")
                .replace(/^\s*彩蛋章(?:[\s·：:—-]+)?/, "")
                .replace(/^\s+|\s+$/g, "");
        }

        function pickVideoUrl(o) {
            if (!o) return "";
            var direct = o.VideoUrl || o.videoUrl || o.VideoURL || o.videoURL || o.PlayUrl || o.playUrl || "";
            if (direct) return String(direct);

            var actionUrl = String(o.ActionUrl || o.actionUrl || "");
            if (!actionUrl) return "";

            var jsonMatch = actionUrl.match(/["']playUrl["']\s*:\s*["']([^"']+)["']/i);
            if (jsonMatch && jsonMatch[1]) return jsonMatch[1].replace(/\\\//g, "/");

            var queryMatch = actionUrl.match(/[?&]playUrl=([^&#]+)/i);
            if (queryMatch && queryMatch[1]) {
                try { return decodeURIComponent(queryMatch[1]); } catch(e1) { return queryMatch[1]; }
            }

            var queryPos = actionUrl.indexOf("query=");
            if (queryPos >= 0) {
                try {
                    var query = actionUrl.substring(queryPos + 6);
                    var parsed = JSON.parse(decodeURIComponent(query));
                    return String(parsed.playUrl || parsed.PlayUrl || parsed.videoUrl || parsed.VideoUrl || "");
                } catch(e2) {}
            }
            return "";
        }

        function mediaPlayAction(url, title, style) {
            var mediaUrl = String(url || "");
            var mediaTitle = String(title || "彩蛋章");
            var safeUrl = mediaUrl.replace(/\\/g, "\\\\").replace(/'/g, "\\'").replace(/[\r\n]+/g, "");
            var safeTitle = mediaTitle.replace(/\\/g, "\\\\").replace(/'/g, "\\'").replace(/[\r\n]+/g, "");
            return {
                style: style || "TEXT",
                type: "qd",
                click: "playMidpageMedia('" + safeUrl + "','" + safeTitle + "')"
            };
        }

        function mediaImage(url, cover, title) {
            var mediaUrl = String(url || "");
            var coverUrl = String(cover || "");
            if (!mediaUrl) return "";
            if (!coverUrl) return "<usehtml><p>" + qdEsc(mediaUrl) + "</p></usehtml>\n";
            return '<img src="' + coverUrl.replace(/"/g, "&quot;") + ',' + JSON.stringify(mediaPlayAction(mediaUrl, title, "FULL")) + '">\n';
        }

        function mediaButton(url, label, title) {
            if (!runtimeJava || !runtimeJava.base64Encode) return "";
            var mediaUrl = String(url || "");
            if (!mediaUrl) return "";
            var buttonText = String(label || "点击播放");
            var svg = '<svg viewBox="0 0 1080 240" xmlns="http://www.w3.org/2000/svg" width="1080" height="240">' +
                '<rect x="4" y="4" width="1072" height="232" rx="28" fill="#e34d3f"/>' +
                '<path d="M82 56 L82 184 L184 120 Z" fill="#ffffff"/>' +
                '<text x="238" y="145" font-family="Arial, sans-serif" font-size="68" font-weight="600" fill="#ffffff">' + qdEsc(buttonText) + '</text>' +
                '</svg>';
            var clickObj = mediaPlayAction(mediaUrl, title, "FULL");
            return '<img src="data:image/svg+xml;base64,' + runtimeJava.base64Encode(svg) + ',' + JSON.stringify(clickObj) + '">\n';
        }

        for (var i = 0; i < midList.length; i++) {
            var mid = midList[i] || {};
            var widgets = qdList(mid.Widgets || mid.WidgetList || mid.widgets || mid.Items || mid.items);
            for (var j = 0; j < widgets.length; j++) {
                var w = widgets[j] || {};
                var d = w.Data || w.data || w;
                if (!d) continue;

                var widgetCfg = w.WidgetConfig || w.widgetConfig || {};
                var widgetType = Number(widgetCfg.Type || widgetCfg.type || 0);
                var widgetName = String(widgetCfg.Name || widgetCfg.name || "").toLowerCase();
                var isVideo = widgetType === 4 || widgetType === 20 || widgetName.indexOf("video_widget") >= 0 || widgetName.indexOf("video_card_widget") >= 0;
                var isAudio = widgetType === 21 || widgetName.indexOf("audio_card_widget") >= 0;
                var isImage = widgetType === 22 || widgetName.indexOf("image_card_widget") >= 0;

                var text = pickText(d);
                if (text) html += "<usehtml><p>" + qdEsc(text) + "</p></usehtml>\n";

                if (isVideo) {
                    var video = pickVideoUrl(d);
                    var videoCover = d.VideoCoverUrl || d.videoCoverUrl || d.CoverUrl || d.coverUrl || d.ImgUrl || d.imgUrl || "";
                    if (video) html += mediaImage(video, videoCover, "彩蛋视频") + mediaButton(video, "点击播放视频", "彩蛋视频");
                    else if (videoCover) html += "<img src='" + String(videoCover).replace(/'/g, "\\'") + ",{\"style\":\"FULL\"}'>\n";
                    continue;
                }

                if (isAudio) {
                    var audio = d.AudioUrl || d.audioUrl || d.AudioURL || d.audioURL || "";
                    var audioCover = d.AudioCoverUrl || d.audioCoverUrl || d.CoverUrl || d.coverUrl || d.ImgUrl || d.imgUrl || "";
                    if (audio) html += mediaImage(audio, audioCover, "彩蛋音频") + mediaButton(audio, "点击播放音频", "彩蛋音频");
                    else if (audioCover) html += "<img src='" + String(audioCover).replace(/'/g, "\\'") + ",{\"style\":\"FULL\"}'>\n";
                    continue;
                }

                var img = d.ImgUrl || d.imgUrl || d.ImageUrl || d.imageUrl || "";
                if ((isImage || img) && img) {
                    html += "<img src='" + String(img).replace(/'/g, "\\'") + ",{\"style\":\"FULL\"}'>\n";
                }
            }
        }
    } catch(e) {
        html = "获取彩蛋内容失败：" + qdEsc(e.message || e);
    }
    return html || "该彩蛋内容为空。";
}

function playMidpageMedia(url, title) {
    var j = null;
    try { j = this && this.java ? this.java : null; } catch(e) {}
    if (!j) { try { j = java; } catch(e2) {} }
    if (!j) return;

    var mediaUrl = String(url || "");
    var mediaTitle = String(title || "彩蛋章");
    var requestUrl = mediaUrl + ',{"headers":{"User-Agent":"Mozilla/5.0","Referer":"https://www.qidian.com/"}}';
    try {
        j.openVideoPlayer(requestUrl, mediaTitle);
    } catch(e3) {
        try {
            j.openUrl(mediaUrl);
        } catch(e4) {}
    }
}

function videoh(url) {
    var requestUrl = String(url || "") + ',{"headers":{"User-Agent":"Mozilla/5.0","Referer":"https://www.qidian.com/"}}';
    return playMidpageMedia.call(this, requestUrl, "彩蛋章");
}

var _qdSummaryCacheKey = "";
var _qdSummaryCacheValue = null;
function qdCommentCache() {
    try {
        if (this && this.cache) return this.cache;
    } catch(e0) {}
    try {
        if (typeof cache !== "undefined") return cache;
    } catch(e1) {}
    return null;
}
function qdGetParagraphSummary(bid, cid) {
    var key = String(bid || "") + ":" + String(cid || "");
    var memoryKey = "qdreader-paragraph-summary:" + key;
    var memory = qdCommentCache.call(this);

    if (memory && memory.getFromMemory) {
        try {
            var cachedRaw = memory.getFromMemory(memoryKey);
            if (cachedRaw !== null && cachedRaw !== undefined && String(cachedRaw) !== "") {
                var cached = typeof cachedRaw === "string" ? JSON.parse(cachedRaw) : JSON.parse(String(cachedRaw));
                _qdSummaryCacheKey = key;
                _qdSummaryCacheValue = cached;
                return cached;
            }
        } catch(e2) {}
    }
    if (key && key === _qdSummaryCacheKey && _qdSummaryCacheValue !== null) {
        return _qdSummaryCacheValue;
    }

    var value = qdApi.call(this, "paragraph_summary", {
        book_id: bid,
        chapter_id: cid
    });
    if (value && Number(value.code || 0) === 0) {
        _qdSummaryCacheKey = key;
        _qdSummaryCacheValue = value;
        if (memory && memory.putMemory) {
            try {
                var latestKeyName = "qdreader-paragraph-summary:latest";
                var oldKey = memory.getFromMemory ? memory.getFromMemory(latestKeyName) : null;
                if (oldKey && String(oldKey) !== memoryKey && memory.deleteMemory) {
                    memory.deleteMemory(String(oldKey));
                }
                memory.putMemory(memoryKey, JSON.stringify(value));
                memory.putMemory(latestKeyName, memoryKey);
            } catch(e3) {}
        }
    }
    return value || { code: -1, data: {} };
}

function qdList(v) {
    if (!v) return [];
    if (Array.isArray(v)) return v;
    if (v.size && v.get) {
        var arr = [];
        for (var i = 0; i < v.size(); i++) arr.push(v.get(i));
        return arr;
    }
    return [];
}
function qdEsc(s) {
    return String(s == null ? "" : s)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
function qdColor(count, hot) {
    var c1 = qdLoginVal.call(this, "◎ 段评颜色", "#666666");
    var c2 = qdLoginVal.call(this, "◎ 段评分色", "#e23b3b");
    var c3 = qdLoginVal.call(this, "◎ 热评颜色", "#f06060");
    var n1 = parseInt(qdLoginVal.call(this, "◎ 分色数值", "45"));
    var cfg = getCfg.call(this);
    if (!cfg.fs) return c1;
    if (hot) return c3;
    return Number(count) >= n1 ? c2 : c1;
}
function qdMakeBubble(number, bid, cid, para, hot, type) {
    const { java } = this;
    var n = Number(number) || 0;
    if (n <= 0) return "";
    var displayText = n > 999 ? "999" : String(n);
    var color = qdColor.call(this, n, hot);
    var font = "Arial";
    var size = qdLoginVal.call(this, "◎ 大小", "text");
    var style = qdLoginVal.call(this, "◎ 模板", "起点");
    var tpl = qdLoginVal.call(this, style.replace("样式", "◎ 气泡"), "");
    var svg = (style !== "起点" && tpl.trim())
        ? tpl.replace(/\$\{qpyscolor\}/g, color).replace(/\$\{qpwzcolor\}/g, color).replace(/\$\{font\}/g, font).replace(/\$\{displayText\}/g, displayText)
        : '<svg viewBox="5 14 45 36" xmlns="http://www.w3.org/2000/svg" width="180" height="144">' +
        '<path d="M44 48 Q48 48 48 44 L48 20 Q48 16 44 16 L20 16 Q16 16 16 20 L16 24 S16 28 10 30 Q6 32 10 34 Q16 36 16 38 L16 44 Q16 48 20 48 Z" fill="none" stroke="' + color + '" stroke-width="0.7"/>' +
        '<text x="32" y="38" text-anchor="middle" font-family="' + font + '" font-weight="600" font-size="18" fill="' + color + '" dominant-baseline="middle">' + displayText + '</text>' +
        '</svg>';
    var clickObj = { style: size, type: "qd", click: "showCmt('" + bid + "','" + cid + "','" + para + "'," + Date.now() + ",'" + (type || "dp") + "')" };
    return '<img src="data:image/svg+xml;base64,' + java.base64Encode(svg) + ',' + JSON.stringify(clickObj) + '">';
}

const EMOJI_MAP = { 1:"👏",2:"🌹",3:"🤝",4:"😁",5:"😄",6:"🥺",7:"🙂",8:"😏",9:"😙",10:"👆🏻🐽",11:"🙄",12:"😭",13:"😵",14:"😥",15:"🖕🏻",16:"🥵",17:"😓",18:"🤫",19:"😂",20:"😢",21:"😍",22:"🤕🔨",23:"😑",24:"😫",25:"🤗",26:"🤪",27:"🙏",28:"😣",29:"💪",30:"💀",31:"😳",32:"😎",33:"🤭",34:"😄👏",35:"👍🏻",36:"🤓",37:"😡",38:"🙁",39:"😄❓",40:"😞",41:"😧",42:"💋",43:"☺️",44:"🤬",45:"😴",46:"🤠🚬",47:"😱",48:"🐷",49:"😪",50:"🤐",51:"🥴",52:"🌙",53:"❤️",54:"🔪",55:"🎁",56:"💔",57:"👊🏻",58:"😒",59:"✌🏻️",60:"😮",61:"🤨",62:"😴",63:"👏🏻",64:"🐲",65:"⭐",66:"🌧️",67:"🍉",68:"🍵",69:"🔥",70:"💯" };

function qdCreateGodHtml(c, bid, cid, para) {
    const { java } = this;
    var text = qdEsc(qdCommentText(c)).replace(/https?:\/\/[^\s]+/g, "").replace(/\[fn=(\d+)\]/g, (match, num) => EMOJI_MAP[num] || '').trim();
    if (!text) return "";
    var display = text.length > 18 ? text.substring(0, 18) + "⋯" : text;
    var width = 1000, boxHeight = 100, marginBottom = 20, padding = 32, tagWidth = 140, tagHeight = 56;
    var canvasHeight = boxHeight + marginBottom;
    var centerY = boxHeight / 2 + 14;
    var font = "Segoe UI, Arial, Helvetica, sans-serif";
    var svg = '<svg width="' + width + '" height="' + canvasHeight + '" xmlns="http://www.w3.org/2000/svg">' +
        '<rect x="2" y="0" width="' + (width - 4) + '" height="' + boxHeight + '" fill="rgba(255,255,255,0.25)" rx="35" stroke="#888" stroke-width="1"/>' +
        '<rect x="' + padding + '" y="' + ((boxHeight - tagHeight) / 2) + '" width="' + tagWidth + '" height="' + tagHeight + '" rx="20" fill="#FF4444"/>' +
        '<text x="' + (padding + tagWidth / 2) + '" y="' + centerY + '" font-size="32" font-family="' + font + '" fill="#FFF" text-anchor="middle" font-weight="bold">热评</text>' +
        '<text x="' + (padding + tagWidth + 24) + '" y="' + centerY + '" font-size="38" font-family="' + font + '" fill="#000" font-weight="bold">' + display + '</text>' +
        '</svg>';
    var clickObj = { style: "FULL", click: "showCmt('" + bid + "','" + cid + "','" + para + "'," + Date.now() + ",'dp')" };
    return '<img src="data:image/svg+xml;base64,' + java.base64Encode(svg) + ',' + JSON.stringify(clickObj) + '">';
}

function qdCommentUser(c) {
    var u = c.user_info || c.UserInfo || c.User || {};
    return {
        name: u.user_name || u.UserName || u.NickName || "匿名用户",
        avatar: u.user_avatar || u.UserHeadIcon || u.Avatar || ""
    };
}

function qdCommentText(c) {
    var t = c.text || c.ReviewContent || c.Content || c.content || c.Body || c.Subject || "";
    if (typeof t == "object") t = t.Text || t.text || JSON.stringify(t);
    return String(t || "");
}

function qdCommentTime(c) {
    var ts = Number(c.create_timestamp || c.CreateTime || c.CreateTimestamp || 0);
    if (!ts) return "";
    if (String(ts).length <= 10) ts *= 1000;
    try { return java.timeFormatUTC(ts, "yyyy-MM-dd HH:mm", 28800000); } catch(e) { return ""; }
}

function qdCreateChapterCmtHtml(list, total, bid, cid) {
    const { java } = this;
    list = qdList(list).filter(function(item) {
        return qdCommentText(item).trim() !== "";
    }).slice(0, 2);
    if (!list.length && !total) return "";
    var svg = "", y = 0;
    var w = 1080, lp = 60, rp = 60, ufs = 42, cfs = 42, lh = 60, th = 120, sp = 35, gr = 60;
    var font = "Segoe UI, Arial, Helvetica, sans-serif";
    svg += '<text x="' + lp + '" y="' + (y + 75) + '" font-size="44" font-family="' + font + '" fill="#000">本章说</text>' +
        '<text x="' + (w - rp) + '" y="' + (y + 75) + '" font-size="36" text-anchor="end" font-family="' + font + '" fill="#000">' + (total || list.length || 0) + '条评论 ❯</text>';
    y += th;
    for (var j = 0; j < list.length; j++) {
        var c = list[j] || {};
        var u = qdCommentUser(c);
        var like = c.digg_count || c.LikeCount || c.DiggCount || c.AgreeAmount || 0;
        var txt = qdEsc(qdCommentText(c).replace(/https?:\/\/[^\s]+/g, "").replace(/qdd\.gg\/[^\s]+/g, "").replace(/\[fn=(\d+)\]/g, (match, num) => EMOJI_MAP[num] || '')).trim();
        if (!txt) continue;
        var max = Math.floor((w - lp - rp) / cfs);
        var txtLines = [], s = txt;
        while (s) { txtLines.push(s.slice(0, max)); s = s.slice(max); }
        if (txtLines.length > 4) {
            txtLines = txtLines.slice(0, 4);
            txtLines[3] = txtLines[3].slice(0, max - 1) + "…";
        }
        var baseY = y + 40;
        svg += '<text x="' + lp + '" y="' + baseY + '" font-weight="bold" font-size="' + ufs + '" font-family="' + font + '" fill="#000">' + qdEsc(u.name) + '</text>';
        var capsuleWidth = 140;
        var capsuleHeight = 56;
        var capsuleRx = 28;
        var capsuleX = w - rp - capsuleWidth;
        var capsuleY = baseY - capsuleHeight/2 + 8;
        svg += '<rect x="' + capsuleX + '" y="' + capsuleY + '" width="' + capsuleWidth + '" height="' + capsuleHeight + '" rx="' + capsuleRx + '" fill="rgba(0,0,0,0.06)" stroke="#b0b0b0" stroke-width="1"/>';
        var centerY = capsuleY + capsuleHeight / 2;
        var iconSize = 28;
        var iconX = capsuleX + 16;
        var iconY = centerY - iconSize / 2;
        svg += '<svg x="' + iconX + '" y="' + iconY + '" width="' + iconSize + '" height="' + iconSize + '" viewBox="0 0 24 24" fill="none" stroke="#333" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">' +
            '<path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/>' +
            '</svg>';
        var textX = iconX + iconSize + 6;
        var textY = centerY + 8;
        svg += '<text x="' + textX + '" y="' + textY + '" font-size="28" font-family="' + font + '" fill="#333">' + like + '</text>';
        var ly = baseY + ufs + sp;
        for (var k = 0; k < txtLines.length; k++) {
            svg += '<text x="' + lp + '" y="' + ly + '" font-size="' + cfs + '" font-family="' + font + '" fill="#000">' + txtLines[k] + '</text>';
            ly += lh;
        }
        y += ufs + lh * txtLines.length + sp * 2.5;
    }
    var finalSvg = '<svg width="' + w + '" height="' + y + '" xmlns="http://www.w3.org/2000/svg">' +
        '<rect x="2" y="2" width="' + (w - 4) + '" height="' + (y - 4) + '" fill="rgba(255,255,255,0.25)" rx="' + (gr - 1) + '" stroke="#888" stroke-width="1"/>' +
        svg +
        '</svg>';
    var clickObj = { style: "FULL", click: "showCmt('" + bid + "','" + cid + "','-1'," + Date.now() + ",'zp')" };
    return '<img src="data:image/svg+xml;base64,' + java.base64Encode(finalSvg) + ',' + JSON.stringify(clickObj) + '">';
}

function qdAuthorSayText(authorSay) {
    if (authorSay == null) return "";
    var obj = authorSay;
    if (typeof obj === "string") {
        var rawText = obj.trim();
        if (!rawText) return "";
        try {
            var parsed = JSON.parse(rawText);
            if (parsed && typeof parsed === "object") obj = parsed;
            else return rawText;
        } catch(e) {
            return rawText;
        }
    }
    if (!obj || typeof obj !== "object") return String(obj || "");

    var value =
        obj.AuthorComments ||
        obj.AuthorComment ||
        obj.AuthorSay ||
        obj.author_say ||
        obj.authorSay ||
        (obj.AskMonthTicket && (obj.AskMonthTicket.AuthorExpectText || obj.AskMonthTicket.author_expect_text)) ||
        (obj.ask_month_ticket && (obj.ask_month_ticket.AuthorExpectText || obj.ask_month_ticket.author_expect_text)) ||
        obj.ChapterAuthorSay ||
        obj.chapterAuthorSay ||
        obj.Content ||
        obj.Text ||
        obj.content ||
        obj.text ||
        obj.Comment ||
        obj.comment ||
        obj.Say ||
        obj.say ||
        obj.AuthorWords ||
        obj.authorWords ||
        obj.WriterSay ||
        obj.writerSay ||
        "";

    if (value === obj) return "";
    if (value && typeof value === "object") return qdAuthorSayText(value);
    return String(value || "");
}

function qdCreateAuthorSayHtml(authorSay, bid, cid) {
    const { java } = this;
    if (!authorSay) return "";

    var obj = authorSay;
    var content = "";

    try {
        if (typeof obj === "string") {
            try {
                var parsed = JSON.parse(obj);
                if (parsed && typeof parsed === "object") obj = parsed;
                else content = obj;
            } catch(e0) {
                content = obj;
            }
        }

if (!content && obj && typeof obj === "object") {
    content =
        obj.AuthorComments ||
        obj.AuthorComment ||
        obj.AuthorSay ||
        obj.author_say ||
        obj.authorSay ||
        (obj.AskMonthTicket && obj.AskMonthTicket.AuthorExpectText) ||
        obj.ChapterAuthorSay ||
        obj.chapterAuthorSay ||
        obj.Content ||
        obj.Text ||
        obj.content ||
        obj.text ||
        obj.Comment ||
        obj.comment ||
        obj.Say ||
        obj.say ||
        obj.AuthorWords ||
        obj.authorWords ||
        obj.WriterSay ||
        obj.writerSay ||
        "";
}

        if (typeof content === "object") {
            content =
                content.Text ||
                content.Content ||
                content.text ||
                content.content ||
                content.Value ||
                content.value ||
                JSON.stringify(content);
        }
    } catch(e) {}

    content = String(content || "")
        .replace(/https?:\/\/[^\s]+/g, "")
        .replace(/\[fn=(\d+)\]/g, function(match, num) {
            return EMOJI_MAP[num] || "";
        })
        .trim();

    content = qdEsc(content);
    if (!content) return "";

    var ask = {};
    var authorName = "";

    try {
        if (obj && typeof obj === "object") {
            ask = obj.AskMonthTicket || obj.ask_month_ticket || {};
            authorName = qdEsc(
                obj.AuthorName ||
                obj.author_name ||
                obj.NickName ||
                obj.nickname ||
                obj.Author ||
                obj.author ||
                ""
            );
        }
    } catch(e2) {}

    var svg = "", y = 0;
    var w = 1080, lp = 60, rp = 60;
    var cfs = 38, lh = 56, sp = 40, gr = 60;
    var font = "Segoe UI, Arial, Helvetica, sans-serif";

    var tagX = w - 110, tagY2 = 0, tagWidth = 110;
    var tagPath =
        "M " + (tagX + 26) + " " + tagY2 +
        " C " + (tagX - 36) + " " + tagY2 +
        " " + (tagX - 60) + " " + (tagY2 + 1) +
        " " + (tagX - 114) + " " + (tagY2 - 32) +
        " C " + (tagX - 84) + " " + (tagY2 - 12) +
        " " + (tagX - 68) + " " + (tagY2 + 6) +
        " " + (tagX - 66) + " " + (tagY2 + 22) +
        " C " + (tagX - 64) + " " + (tagY2 + 44) +
        " " + (tagX - 54) + " " + (tagY2 + 56) +
        " " + (tagX - 34) + " " + (tagY2 + 56) +
        " L " + (tagX + tagWidth) + " " + (tagY2 + 56) +
        " L " + (tagX + tagWidth) + " " + (tagY2 + 26) +
        " Q " + (tagX + tagWidth) + " " + tagY2 +
        " " + (tagX + tagWidth - 26) + " " + tagY2 + " Z";

    svg += '<path d="' + tagPath + '" fill="rgba(74,116,228,0.90)"/>';
    svg += '<text x="' + (tagX + 28) + '" y="40" font-size="34" fill="#fff" font-weight="bold" text-anchor="middle" font-family="system-ui, sans-serif">作者说</text>';

    if (authorName) {
        svg += '<text x="' + lp + '" y="' + (y + 70) + '" font-family="' + font + '" font-size="44" fill="#000">' + authorName + '</text>';
    }

    y += 150;

    var max = Math.floor((w - lp - rp) / cfs);
    var txtLines = [], s = content;

    while (s) {
        txtLines.push(s.slice(0, max));
        s = s.slice(max);
    }

    for (var i = 0; i < txtLines.length; i++) {
        svg += '<text x="' + lp + '" y="' + y + '" font-family="' + font + '" font-size="' + cfs + '" fill="#333">' + txtLines[i] + '</text>';
        y += lh;
    }

    y += sp;

    var expect = "";
    try {
        expect = qdEsc(ask.AuthorExpectText || ask.author_expect_text || "");
    } catch(e3) {}

    if (authorName && expect) {
        svg += '<text x="' + lp + '" y="' + y + '" font-family="' + font + '" font-size="38"><tspan fill="#000" font-weight="bold">' + authorName + '：</tspan><tspan fill="#000">' + expect + '</tspan></text>';
        y += lh;
    }

    y += 10;

    var finalSvg =
        '<svg width="' + w + '" height="' + y + '" xmlns="http://www.w3.org/2000/svg">' +
        '<rect x="2" y="2" width="' + (w - 4) + '" height="' + (y - 4) + '" fill="rgba(255,255,255,0.25)" rx="' + gr + '" stroke="#888" stroke-width="1"/>' +
        svg +
        '</svg>';

    var clickObj = {
        style: "FULL",
        click: "showCmt('" + bid + "','" + cid + "','-10'," + Date.now() + ",'zp')"
    };

    return '<img src="data:image/svg+xml;base64,' + java.base64Encode(finalSvg) + ',' + JSON.stringify(clickObj) + '">';
}

function getComments(content, bid, cid, type, chapData) {
    let { java, source } = this;

    var cfg;
    try { cfg = getCfg.call(this); } catch(eCfg) { cfg = { dp:false, zm:false, tl:false, zz:false, rp:false, pl:false }; }
    if (!cfg.pl) { try { source.put(cid, ""); } catch(eAllCommentsOff) {} return content; }

    try {
        var summary = [];
        var hotReviews = [];
        if (cfg.dp || cfg.rp || cfg.zm) {
            var summaryRes = qdGetParagraphSummary.call(this, bid, cid);
            var data = summaryRes.data || summaryRes.Data || {};
            summary = qdList(data.summary || data.Summary);
            hotReviews = qdList(data.reviews || data.Reviews);
        }

        // Normalize paragraph wrappers and CRLF before attaching inline comment images.
        var normalizedContent = String(content == null ? "" : content).replace(/\r/g, "");
        if (/<p\b/i.test(normalizedContent)) {
            normalizedContent = normalizedContent.replace(/<p\b[^>]*>/gi, "").replace(/<\/p>/gi, "\n");
        }
        var lines = normalizedContent.split("\n");
        var paraMap = {};
        var chapterCount = 0;
        if (!cfg.zm) { try { source.put(cid, ""); } catch(eTitleOff) {} }

        // 正文图片不计入文字段落，避免段评气泡错位。
        var textLineIndexes = [];
        for (var li = 0; li < lines.length; li++) {
            var trimLine = lines[li].trim();
            if (trimLine && !(/^<\s*img\s/i.test(trimLine)) && trimLine.indexOf("data:image/") === -1) {
                textLineIndexes.push(li);
            }
        }

        for (var i = 0; i < summary.length; i++) {
            var item = summary[i] || {};
            var pid = Number(
                item.ParagraphId || item.paragraphId || item.paragraph_id ||
                item.ParaId || item.paraId || 0
            );
            var count = Number(
                item.CommentCount || item.commentCount || item.TextCount ||
                item.textCount || item.Count || item.count || 0
            );
            var hot = Number(item.HasHotComment || item.hasHotComment || 0) !== 0;
            if (pid > 0 && count > 0) {
                var idx = textLineIndexes[pid - 1];
                if (idx !== undefined && idx >= 0 && idx < lines.length) {
                    paraMap[pid] = idx;
                    if (cfg.dp) lines[idx] += qdMakeBubble.call(this, count, bid, cid, pid, hot, "dp");
                }
            } else if (pid === -1 && count > 0) {
                chapterCount = count;
                if (cfg.zm) {
                    try { source.put(cid, createSvg.call(this, count, bid, cid, -1, false, true, "dp")); } catch(eTitle) {}
                }
            }
        }

        if (cfg.rp) {
            for (var g = 0; g < hotReviews.length; g++) {
                var gc = hotReviews[g] || {};
                var gpid = Number(gc.paragraph_id || gc.ParagraphId || gc.paragraphId || 0);
                var gi = paraMap[gpid];
                if (gi === undefined) gi = textLineIndexes[gpid - 1];
                if (gi !== undefined && gi >= 0 && gi < lines.length) {
                    lines[gi] += "\n" + qdCreateGodHtml.call(this, gc, bid, cid, gpid);
                }
            }
        }

        var tail = "";
        if (cfg.zz) {
            var authorSay = null;
            try {
                var cd = chapData || {};
                authorSay =
                    cd.author_say || cd.authorSay || cd.AuthorSay || cd.Author_say ||
                    cd.author_comment || cd.authorComment || cd.AuthorComment || cd.AuthorComments ||
                    cd.chapter_author_say || cd.chapterAuthorSay || cd.ChapterAuthorSay ||
                    cd.authorWords || cd.AuthorWords || cd.writerSay || cd.WriterSay || cd.say || cd.Say || null;
                if (!qdAuthorSayText(authorSay).trim()) authorSay = null;
            } catch(eA) { authorSay = null; }

            if (!authorSay) {
                try {
                    var act = qdApi.call(this, "chapter_activity", {
                        book_id: bid,
                        chapter_id: cid
                    });
                    var actData = act.data || act.Data || {};
                    authorSay =
                        actData.author_say || actData.authorSay || actData.AuthorSay || actData.Author_say ||
                        actData.author_comment || actData.authorComment || actData.AuthorComment || actData.AuthorComments ||
                        actData.chapter_author_say || actData.chapterAuthorSay || actData.ChapterAuthorSay ||
                        actData.authorWords || actData.AuthorWords || actData.writerSay || actData.WriterSay ||
                        actData.say || actData.Say || null;
                    if (!qdAuthorSayText(authorSay).trim()) authorSay = null;
                } catch(eB) { authorSay = null; }
            }
            if (authorSay) tail += qdCreateAuthorSayHtml.call(this, authorSay, bid, cid);
        }

        if (cfg.tl || cfg.zm) {
            var cc = qdApi.call(this, "chapter_comments", {
                book_id: bid,
                chapter_id: cid,
                page: 1,
                page_size: 20
            });
            var ccData = cc.data || cc.Data || {};
            var ccTotal = Number(ccData.total || ccData.Total || 0);
            if (!chapterCount && ccTotal > 0 && cfg.zm) {
                try { source.put(cid, createSvg.call(this, ccTotal, bid, cid, -1, false, true, "dp")); } catch(eTitleFallback) {}
            }
            if (cfg.tl) tail += qdCreateChapterCmtHtml.call(this, ccData.comments || ccData.Comments || [], ccTotal, bid, cid);
        }

        return lines.join("\n") + (tail ? "\n" + tail : "");
    } catch (e) {
        try { java.log("qidian_api错误: " + e); } catch(_) {}
        return content;
    }
}

function createSvg(number, bid, cid, para, hot, isTitle, fs) {
    const { java } = this;
    try {
        var n = Number(number) || 0;
        if (n <= 0) return "";

        var displayText = n > 999 ? "999" : String(n);

        var size = "TEXT";
        try {
            var s = qdLoginVal.call(this, "◎ 大小", "TEXT");
            if (s) size = String(s);
        } catch(e) {}

        var color = qdColor.call(this, n, hot);
        var font = "Arial";
        var style = qdLoginVal.call(this, "◎ 模板", "起点");
        var tpl = qdLoginVal.call(this, String(style).replace("样式", "◎ 气泡"), "");

        var svg = (style !== "起点" && String(tpl).trim())
            ? String(tpl)
                .replace(/\$\{qpyscolor\}/g, color)
                .replace(/\$\{qpwzcolor\}/g, color)
                .replace(/\$\{font\}/g, font)
                .replace(/\$\{displayText\}/g, displayText)
            : '<svg viewBox="5 14 45 36" xmlns="http://www.w3.org/2000/svg" width="180" height="144">' +
                '<path d="M44 48 Q48 48 48 44 L48 20 Q48 16 44 16 L20 16 Q16 16 16 20 L16 24 S16 28 10 30 Q6 32 10 34 Q16 36 16 38 L16 44 Q16 48 20 48 Z" fill="none" stroke="' + color + '" stroke-width="0.7"/>' +
                '<text x="32" y="38" text-anchor="middle" font-family="' + font + '" font-weight="600" font-size="18" fill="' + color + '" dominant-baseline="middle">' + displayText + '</text>' +
                '</svg>';

        // fs 用来区分打开方式：
        // dp = 段评，包括章名段评 paragraph_id = -1
        // zp = 本章说 / 章评
        var cmtType = fs || "dp";

        var click =
            "showCmt('" + String(bid).replace(/'/g, "\\'") +
            "','" + String(cid).replace(/'/g, "\\'") +
            "','" + String(para).replace(/'/g, "\\'") +
            "'," + Date.now() +
            ",'" + String(cmtType).replace(/'/g, "\\'") + "')";

        return "data:image/svg+xml;base64," + java.base64Encode(svg) + "," + JSON.stringify({
            style: size,
            type: "qd",
            click: click
        });
    } catch(e) {
        try { java.log("createSvg错误：" + e); } catch(_) {}
        return "";
    }
}
var QD_PARAGRAPH_CONFIG = { browser: { isHideable: true, expandedCornersRadius: 12, skipCollapsed: true, hardwareAccelerated: true, heightPercentage: 0.8 }, clickType: 'qd' };
var _qdJ = null; var _qdC = null; var _qdS = null;




function getBuiltInCommentHtml() {
 return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>段评 · 起点</title>
  <style>
  /* 本地轻量样式兼容层 */
    *{box-sizing:border-box}
    .hidden{display:none!important}.block{display:block}.flex{display:flex}.inline-flex{display:inline-flex}
    .flex-1{flex:1 1 0%}.flex-shrink-0{flex-shrink:0}.flex-wrap{flex-wrap:wrap}.items-center{align-items:center}.justify-center{justify-content:center}.justify-between{justify-content:space-between}
    .relative{position:relative}.absolute{position:absolute}.fixed{position:fixed}.inset-0{inset:0}.z-10{z-index:10}.z-50{z-index:50}.overflow-hidden{overflow:hidden}
    .min-h-screen{min-height:100vh}.w-4{width:1rem}.h-4{height:1rem}.w-8{width:2rem}.h-8{height:2rem}.w-12{width:3rem}.h-12{height:3rem}.w-full{width:100%}.max-w-4xl{max-width:56rem}.max-w-md{max-width:28rem}.min-w-0{min-width:0}
    .mx-auto{margin-left:auto;margin-right:auto}.mt-0\.5{margin-top:.125rem}.mt-1{margin-top:.25rem}.mt-2{margin-top:.5rem}.mt-3{margin-top:.75rem}.mt-6{margin-top:1.5rem}.mb-4{margin-bottom:1rem}.mb-6{margin-bottom:1.5rem}.mr-1{margin-right:.25rem}.mr-2{margin-right:.5rem}.ml-2{margin-left:.5rem}
    .p-3{padding:.75rem}.px-3{padding-left:.75rem;padding-right:.75rem}.py-2{padding-top:.5rem;padding-bottom:.5rem}.py-4{padding-top:1rem;padding-bottom:1rem}.py-8{padding-top:2rem;padding-bottom:2rem}.pt-4{padding-top:1rem}.pb-6{padding-bottom:1.5rem}
    .space-y-2>*+*{margin-top:.5rem}.space-y-3>*+*{margin-top:.75rem}.gap-1{gap:.25rem}.gap-2{gap:.5rem}
    .rounded-full{border-radius:9999px}.rounded-md{border-radius:.375rem}.rounded-lg{border-radius:.5rem}
    .border-b{border-bottom-width:1px;border-bottom-style:solid}.border-t{border-top-width:1px;border-top-style:solid}.border-gray-100{border-color:#f3f4f6}
    .bg-gray-50{background-color:var(--bg-primary,#f9fafb)}.bg-red-100{background-color:var(--error-bg,#fef2f2)}.bg-black\/90{background-color:rgba(0,0,0,.9)}
    .text-center{text-align:center}.text-xs{font-size:.75rem}.text-sm{font-size:.875rem}.text-base{font-size:1rem}.font-bold{font-weight:700}.font-medium{font-weight:500}
    .text-gray-800{color:var(--text-primary,#1f2937)}.text-gray-700{color:var(--text-secondary,#374151)}.text-gray-500{color:var(--text-tertiary,#6b7280)}.text-gray-400{color:#9ca3af}.text-red-700{color:var(--error-text,#b91c1c)}
    .leading-relaxed{line-height:1.625}.leading-tight{line-height:1.25}.truncate{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.object-cover{object-fit:cover}.object-contain{object-fit:contain}.cursor-pointer{cursor:pointer}.transition-colors{transition:color .2s,background-color .2s}.pointer-events-none{pointer-events:none}
    .max-w-\[60vw\]{max-width:60vw}.w-auto{width:auto}
    .bg-white{background:#fff}.border{border-width:1px;border-style:solid}.shadow-sm{box-shadow:0 1px 3px rgba(15,23,42,.08)}.shadow-md{box-shadow:0 6px 14px rgba(15,23,42,.10)}
    .p-4{padding:1rem}.px-4{padding-left:1rem;padding-right:1rem}.py-1{padding-top:.25rem;padding-bottom:.25rem}.pt-1{padding-top:.25rem}.mb-1{margin-bottom:.25rem}.mb-2{margin-bottom:.5rem}.mb-3{margin-bottom:.75rem}.mt-4{margin-top:1rem}.mx-3{margin-left:.75rem;margin-right:.75rem}
    .max-w-sm{max-width:24rem}.max-w-\[60vw\]{max-width:60vw}.text-blue-500{color:#3b82f6}.text-gray-600{color:#4b5563}.text-gray-900{color:#111827}
    .text-\[clamp\(1\.25rem\,3vw\,1\.5rem\)\]{font-size:clamp(1.25rem,3vw,1.5rem)}.text-lg{font-size:1.125rem}.font-semibold{font-weight:600}
    .transition-shadow{transition:box-shadow .2s ease}.hover\:shadow-md:hover{box-shadow:0 6px 14px rgba(15,23,42,.10)}
    /* ========================================== */
    /* ?? ????????? (????????)     */
    /* ========================================== */
    #commentList > div > .flex-1 > div:first-child .font-medium { font-size: 16px !important; }
    #commentList > div > .flex-1 > .mt-1 > p { font-size: 16px !important; line-height: 1.6 !important; }
    .reply-item .font-medium { font-size: 14px !important; }
    .reply-item > .flex-1 > .mt-1:not(.flex) { font-size: 14px !important; line-height: 1.5 !important; }
    :root {
        --badge-slot-h: 1.5em !important;
        --bg-primary: #f9fafb;
        --text-primary: #1f2937; --text-secondary: #1f2937; --text-tertiary: #9ca3af;
        --border-color: #f3f4f6; --error-bg: #fef2f2; --error-text: #dc2626; --like-svg-color: rgb(0,0,0); --badge-gap: 0.25rem;
    }
    img.badge-img { height: var(--badge-slot-h) !important; max-height: 24px !important; }
    /* ========================================== */
    /* ? ????????????????????????????? ? */
    body:not(.is-beautified) { padding-top: 56px; }
    body:not(.is-beautified) #commentCount { display: none !important; }
    /* ?????????????? */
    body:not(.is-beautified) #title {
        display: block !important;
        background: #ffffff;
        padding: 16px 16px 16px 22px;
        border-radius: 10px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.04);
        position: relative;
        margin-top: 8px;
        margin-bottom: 12px;
        font-size: 16px !important;
        font-weight: 700 !important;
        color: #1f2937;
        text-indent: 0 !important;
        line-height: 1.6 !important;
        border: 1px solid #f3f4f6;
    }
    body:not(.is-beautified) #title::before {
        content: ''; position: absolute; left: 0; top: 16px; bottom: 16px; width: 4px; background: #9ca3af; border-radius: 0 3px 3px 0;
    }
    body:not(.is-beautified) #stickyWrapper {
        position: fixed; top: 0; left: 0; right: 0;
        background-color: var(--bg-primary);
        z-index: 999; padding: 12px 16px 0 16px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.05); box-sizing: border-box;
    }
    /* ???????????????? */
    @media (prefers-color-scheme: dark) {
        body:not(.is-beautified) #stickyWrapper { background-color: #121212; border-bottom: 1px solid #2c2c2e; box-shadow: none; }
        body:not(.is-beautified) #title { background: #1c1c1e; color: #e5e7eb; border-color: #2c2c2e; }
        body:not(.is-beautified) #title::before { background: #4b5563; }
    }
    body:not(.is-beautified) #tabContainer, body:not(.is-beautified) #roleTabContainer {
        width: 100%; max-width: 56rem; padding-left: 0.75rem; padding-right: 0.75rem; box-sizing: border-box; margin: 0 auto;
    }
    .tab-bar { display: flex; gap: 1.5rem; font-size: 15px; color: #6b7280; padding-bottom: 6px; border-bottom: none; align-items: baseline; }
    .tab-item { cursor: pointer; position: relative; display: flex; align-items: baseline; gap: 4px; transition: all 0.2s; }
    .tab-item.active { color: #111827; font-weight: 800; font-size: 18px; }
    .tab-item.active::after { content: ''; position: absolute; bottom: -6px; left: 50%; transform: translateX(-50%); width: 16px; height: 3px; background-color: #ef4444; border-radius: 2px; }
    .tab-num { font-size: 12px; color: #9ca3af; font-weight: 500; transition: color 0.2s; }
    .tab-item.active .tab-num { color: #111827; font-weight: bold; }
    /* ???????? */
    .role-tab-bar { display: flex; gap: 10px; overflow-x: auto; margin-top: 4px; padding-bottom: 8px; white-space: nowrap; border-bottom: 1px solid transparent; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
    .role-tab-bar::-webkit-scrollbar { display: none; }
    .role-tab-item { padding: 4px 14px; font-size: 13px; background: #f3f4f6; border-radius: 999px; cursor: pointer; color: #4b5563; border: 1px solid transparent; transition: all 0.2s; }
    .role-tab-item.active { background: #fef2f2 !important; color: #ef4444 !important; border-color: #fca5a5 !important; font-weight: bold !important; }
    /* ========================================== */
    /* ??????????????                 */
    audio::-webkit-media-controls-panel { background-color: #f5f5f5; border-radius: 0.5rem; }
    audio::-webkit-media-controls-play-button { background-color: #ef4444; border-radius: 50%; }
    audio::-webkit-media-controls-play-button:hover { background-color: #dc2626; }
    body { background-color: var(--bg-primary); transition: background-color .3s ease; }
    .bg-gray-50 { background-color: var(--bg-primary); } .text-gray-800 { color: var(--text-primary); } .text-gray-700 { color: var(--text-secondary); } .text-gray-500 { color: var(--text-tertiary); } .text-gray-400 { color: var(--text-tertiary); } .border-gray-100 { border-color: var(--border-color); } .bg-red-100 { background-color: var(--error-bg); } .text-red-700 { color: var(--error-text); } .text-blue-500 { color: #3b82f6; } svg path { fill: var(--like-svg-color); }
    .badge-wrap { display: inline-flex; align-items: center; gap: var(--badge-gap); margin-left: 0.25rem; height: var(--badge-slot-h); flex: 0 0 auto; line-height: 1; }
    .badge-slot { display: inline-flex; align-items: center; height: var(--badge-slot-h); flex: 0 0 auto; }
    .badge-img { height: 100%; width: auto; object-fit: contain; display: none; }
    #fullscreenImg { transform-origin: center center; cursor: grab; }
    .god-stamp-header { padding-right: 45px; }
    .comment-card .god-stamp-header { padding-right: 24px !important; }
/* ?????? */
.reply-toggle, .reply-more:not(.hidden), #loadMore:not(.hidden) { display: inline-flex; align-items: center; justify-content: center; min-height: 28px; margin-top: 9px; padding: 0 14px; color: #475569 !important; background: #f1f5f9 !important; border: none; border-radius: 14px; font-size: 13px !important; cursor: pointer; font-weight: 500; transition: background 0.2s ease; text-decoration: none !important; }
.reply-toggle:active, .reply-more:not(.hidden):active, #loadMore:not(.hidden):active { background: #e2e8f0 !important; }
#loadMore { width: fit-content; margin-left: auto; margin-right: auto; min-width: 128px; }
/* ????????? */
.replies-container { margin: 2px 0 0 0; width: 100%; padding: 0; background: transparent; border-radius: 8px; box-sizing: border-box; opacity: 0; transform: translateY(-3px); transition: opacity .18s ease, transform .18s ease, margin .18s ease; will-change: opacity, transform; contain: content; }
.replies-container.expanded { margin: 12px 0 0 -56px; width: calc(100% + 56px); padding: 12px 10px 12px 56px; background: var(--reply-panel-bg, #f8fafc); opacity: 1; transform: translateY(0); }
html.qd-dark-preload, html.qd-dark-preload body { background:#121212 !important; color:#e5e7eb !important; }
html.qd-dark-preload { --bg-primary:#121212; --text-primary:#e5e7eb; --text-secondary:#dddddd; --text-tertiary:#9ca3af; --border-color:#2c2c2e; --reply-panel-bg:#18181a; --error-bg:#2b1618; --error-text:#fca5a5; }
html.qd-dark-preload .replies-container.expanded, body.qd-dark-preload .replies-container.expanded { background:#18181a !important; }
html.qd-dark-preload .reply-loading, body.qd-dark-preload .reply-loading { background:#18181a !important; color:#9ca3af !important; }
@media (prefers-color-scheme: dark) {
  :root { --bg-primary:#121212; --text-primary:#e5e7eb; --text-secondary:#dddddd; --text-tertiary:#9ca3af; --border-color:#2c2c2e; --reply-panel-bg:#18181a; --error-bg:#2b1618; --error-text:#fca5a5; }
  html, body { background:#121212 !important; color:#e5e7eb !important; }
  .replies-container.expanded { background:#18181a !important; }
  .reply-loading { background:#18181a !important; color:#9ca3af !important; }
  .reply-toggle, .reply-more:not(.hidden), #loadMore:not(.hidden) { background:#2c2c2e !important; color:#9ca3af !important; }
}
@keyframes replyFadeIn { 0% { opacity: 0; transform: translateY(-4px); } 100% { opacity: 1; transform: translateY(0); } }
.reply-item { animation: replyFadeIn .18s ease forwards; opacity: 0; }
.reply-item:nth-child(n) { animation-delay: 0s; }
.reply-loading { display:flex; align-items:center; justify-content:center; min-height:32px; padding:8px 10px; border-radius:8px; background:var(--reply-panel-bg,#f8fafc); color:var(--text-tertiary,#9ca3af); }
.end-card { margin: 12px 0 16px 0; padding: 16px 18px; background: #ffffff; border-radius: 12px; color: #94a3b8; font-size: 13px; text-align: center; box-shadow: 0 2px 12px rgba(0,0,0,0.03); border: 1px solid #f1f5f9; }
/* ???????????? */
body:not(.is-beautified) #commentList > div { background: #ffffff; border-radius: 10px; margin-bottom: 8px; padding: 14px 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.03); border: 1px solid #f3f4f6; }
body:not(.is-beautified) #commentList > div:last-child { margin-bottom: 0; }
/* ???????????? */
.comment-img { margin-top: 8px !important; border-radius: 8px; object-fit: cover; max-width: 160px; width: 160px; height: 160px; display: block; }
  body:not(.is-beautified) .comment-img { width: 140px !important; height: 140px !important; max-width: 140px !important; }
  .reply-item .comment-img { width: 100px !important; height: 100px !important; max-width: 100px !important; }
  img.badge-img { height: var(--badge-slot-h) !important; max-height: 24px !important; }
  .avatar { border-radius: 50% !important; object-fit: cover; }
  .reply-avatar { border-radius: 50% !important; object-fit: cover; }
audio { margin-top: 8px; width: 100%; max-width: 28rem; }
/* ???????? */
.comment-content-wrapper > p { margin: 0 0 4px 0; }
/* ??????????? */
.badge-wrap { margin-left: 4px; }
/* ???????? */
#loadMore { display: flex; align-items: center; justify-content: center; margin: 10px auto; min-width: 120px; }

.loading-inline{display:flex;align-items:center;justify-content:center;gap:7px;width:fit-content;margin:20px auto;padding:8px 12px;color:#89827a;font-size:13px;line-height:1}
.loading-spinner{width:16px;height:16px;border:2px solid rgba(137,130,122,.25);border-top-color:#e58a18;border-radius:50%;animation:qd-spin .78s linear infinite;flex:0 0 auto}
.loading-text{color:#89827a;font-size:13px;line-height:1}
@keyframes qd-spin{to{transform:rotate(360deg)}}
  </style>
</head>
<body class="bg-gray-50 min-h-screen">
  <div id="stickyWrapper">
    <div id="tabContainer" class="tab-bar hidden">
      <div class="tab-item active" data-type="all"><span>全部</span><span class="tab-num" id="num-all"></span></div>
      <div class="tab-item" data-type="image" style="display:none;"><span>配图</span><span class="tab-num" id="num-img"></span></div>
      <div class="tab-item" data-type="audio" style="display:none;"><span>配音</span><span class="tab-num" id="num-audio"></span></div>
    </div>
    <div id="roleTabContainer" class="role-tab-bar hidden"></div>
  </div>
  <div class="max-w-4xl mx-auto px-3 pb-6">
    <h1 id="title" class="text-[clamp(1.25rem,3vw,1.5rem)] font-bold text-gray-800 mb-6 mt-6" style="text-indent:2em; display:none;">加载中...</h1>
    <div id="commentCount" class="text-gray-500 text-sm mb-4" style="display:none !important;"></div>
    <div id="errorAlert" class="hidden bg-red-100 text-red-700 p-3 rounded-lg mt-2 mb-4"></div>
    <div id="commentList" class="space-y-2 hidden mt-2"></div>
    <div id="loadMore" class="hidden">????</div>
    <div id="loading" class="text-center text-gray-500 py-4 mt-2">加载中...</div>
  </div>
  <div id="fullscreenOverlay" class="fixed inset-0 bg-black/90 flex justify-center items-center z-50 hidden">
    <img id="fullscreenImg" src="" alt="全屏图片">
  </div>
<script>
(function() {
  if (typeof java === 'undefined') { window.java = {}; }
  if (typeof cache === 'undefined') { window.cache = { _store: {}, get: function(k) { return this._store[k] }, put: function(k, v) { this._store[k] = v } }; }
  function debugEnvCheck(){}
  function getUrlParams() {
    if (window.qdBid) return { bookId: decodeURIComponent(window.qdBid || ''), chapterId: decodeURIComponent(window.qdCid || ''), paragraphId: decodeURIComponent(window.qdPara || '') };
    const params = new URLSearchParams(window.location.search);
    return { bookId: params.get('bookId') || params.get('book_id'), chapterId: params.get('chapterId') || params.get('chapter_id'), paragraphId: params.get('paragraphId') || params.get('paragraph_id') };
  }
const { bookId, chapterId, paragraphId } = getUrlParams();

const openType = String(window.qdCmtType || 'dp').toLowerCase();
const isAuthorSay = openType === 'author' || paragraphId === '-10';

// -1 + dp 是章名段评；-1 + zp 是本章说；-10 是作者说。
const hasParagraph =
  paragraphId &&
  paragraphId !== '' &&
  !isAuthorSay &&
  !(paragraphId === '-1' && openType === 'zp');

const commentTypeText = isAuthorSay ? '作者说' : (hasParagraph ? '段评' : '章评');
  document.title = commentTypeText + '页';
  let commentPage = 1; let isCommentEnd = false; let isLoading = false; let hasAutoLoadedSecondPage = false;
  let currentTab = 'all'; let currentRoleId = '0';
  let totalAll = 0, totalImg = 0, totalAudio = 0;
  let isLazyLoaded = false;
  let audioRolesCached = null; 
  const errorAlert = document.getElementById('errorAlert'); const loading = document.getElementById('loading'); const loadMore = document.getElementById('loadMore'); const commentCountElement = document.getElementById('commentCount'); const commentList = document.getElementById('commentList'); const fullscreenOverlay = document.getElementById('fullscreenOverlay'); const fullscreenImg = document.getElementById('fullscreenImg'); const titleElement = document.getElementById('title');
  titleElement.textContent = commentTypeText;
      if(hasParagraph) document.getElementById('tabContainer').classList.remove('hidden');
  if (window.ResizeObserver) {
      new ResizeObserver(() => {
          if (!document.body.classList.contains('is-beautified')) {
              const wrap = document.getElementById('stickyWrapper');
              if (wrap) document.body.style.paddingTop = wrap.offsetHeight + 'px';
          }
      }).observe(document.getElementById('stickyWrapper'));
  }
  // ✨：配合隐形支撑，完美解决清空列表导致高度坍塌，以及智能判断吸顶状态
  function resetScrollForTabs() {
      var sticky = document.getElementById('stickyWrapper');
      // 计算真正的吸顶状态
      if (sticky && document.body.classList.contains('is-beautified')) {
          var offset = 50; // topBar 高度
          var rect = sticky.getBoundingClientRect();
          if (rect.top <= offset + 5) {
              // 此时已经处于吸顶状态，计算并强制锁定在原文下方的临界点，绝不露出原文
              var prev = sticky.previousElementSibling;
              if (prev) {
                  var targetY = window.scrollY + prev.getBoundingClientRect().bottom - offset;
                  window.scrollTo(0, targetY);
              }
          } else {
              // 没有吸附时，正常滚到最顶端
              window.scrollTo(0, 0);
          }
      } else {
          window.scrollTo(0, 0); // 保持原有逻辑
      }
  }
  document.querySelectorAll('.tab-item').forEach(tab => {
    tab.addEventListener('click', () => {
      if (currentTab === tab.dataset.type) return; 
      document.querySelectorAll('.tab-item').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      currentTab = tab.dataset.type;
      if(currentTab === 'audio') {
          currentRoleId = '0'; 
          document.getElementById('roleTabContainer').classList.remove('hidden');
          if (audioRolesCached) renderRoleTabs(audioRolesCached); 
      } else {
          document.getElementById('roleTabContainer').classList.add('hidden');
      }
      commentPage = 1; isCommentEnd = false; window._seenCmtIds = {}; window._actualLoadedCount = 0;
      resetScrollForTabs(); // 拦截清空前的高度并修正滚动
      document.getElementById('commentList').innerHTML = '';
      loadComments();
    });
  });
  function renderRoleTabs(roles) {
    const rContainer = document.getElementById('roleTabContainer');
    if(!roles || roles.length === 0) { rContainer.classList.add('hidden'); return; }
    rContainer.innerHTML = '<div class="role-tab-item' + (currentRoleId === '0' ? ' active' : '') + '" data-role-id="0">全部配音</div>';
    roles.forEach(role => {
      rContainer.innerHTML += '<div class="role-tab-item' + (currentRoleId === String(role.AudioRoleId) ? ' active' : '') + '" data-role-id="' + role.AudioRoleId + '">' + (role.AudioRoleName || '未知') + '</div>';
    });
    rContainer.querySelectorAll('.role-tab-item').forEach(item => {
      item.addEventListener('click', () => {
        if (currentRoleId === item.dataset.roleId) return; 
        rContainer.querySelectorAll('.role-tab-item').forEach(t => t.classList.remove('active'));
        item.classList.add('active');
        currentRoleId = item.dataset.roleId;
        commentPage = 1; isCommentEnd = false; window._seenCmtIds = {}; window._actualLoadedCount = 0;
        resetScrollForTabs(); // 拦截清空前的高度并修正滚动
        document.getElementById('commentList').innerHTML = '';
        loadComments();
      });
    });
    rContainer.classList.remove('hidden');
  }
  function lazyLoadCountAfterPageRender() {
    if (!hasParagraph || isLazyLoaded) return;
    isLazyLoaded = true;
    setTimeout(function() {
      try {
        fetchTabCount('paragraph_image_comments', 'image');
        fetchTabCount('paragraph_audio_comments', 'audio');
      } catch(e) {}
    }, 350);
  }
  function fetchTabCount(action, tabType) {
    var apiBase = window.qdApiBase || 'https://pl.aadcn.cn/api/qidian_full_api.php';
    var url = apiBase + '?action=' + action +
      '&book_id=' + encodeURIComponent(bookId) +
      '&chapter_id=' + encodeURIComponent(chapterId) +
      '&paragraph_id=' + encodeURIComponent(paragraphId || '') +
      '&page=1&page_size=20';
    var data = safeParseBigInt(httpGet(url));
    if (!data || data.code !== 0) return;
    var remote = data.data || {};
    var count = Number(remote.total || 0);
    if (!count) {
      var list = remote.comments || [];
      count = list.length || 0;
    }
    if (tabType === 'image') {
      totalImg = count;
      var tabImg = document.querySelector('.tab-item[data-type="image"]');
      var numImg = document.getElementById('num-img');
      if (tabImg && count > 0) tabImg.style.display = 'flex';
      if (numImg && count > 0) numImg.textContent = count;
    } else if (tabType === 'audio') {
      totalAudio = count;
      var tabAudio = document.querySelector('.tab-item[data-type="audio"]');
      var numAudio = document.getElementById('num-audio');
      if (tabAudio && count > 0) tabAudio.style.display = 'flex';
      if (numAudio && count > 0) numAudio.textContent = count;
      var roles = remote.audio_roles || (remote.raw && (remote.raw.AudioRole || remote.raw.AudioRoles)) || [];
      if (roles && roles.length) audioRolesCached = roles;
    }
  }
  function ensureMediaTabsFromList(list) {
    if (!hasParagraph || !list || !list.length) return;
    var imgCount = 0, audioCount = 0;
    for (var i = 0; i < list.length; i++) {
      var c = adaptComment(list[i]);
      if (c.imageUrl) imgCount++;
      if (c.audioUrl) audioCount++;
    }
    if (imgCount > 0) {
      var tabImg = document.querySelector('.tab-item[data-type="image"]');
      var numImg = document.getElementById('num-img');
      if (tabImg) tabImg.style.display = 'flex';
      if (numImg && !numImg.textContent) numImg.textContent = totalImg || imgCount;
    }
    if (audioCount > 0) {
      var tabAudio = document.querySelector('.tab-item[data-type="audio"]');
      var numAudio = document.getElementById('num-audio');
      if (tabAudio) tabAudio.style.display = 'flex';
      if (numAudio && !numAudio.textContent) numAudio.textContent = totalAudio || audioCount;
    }
  }
  function updateLoadMore() {
    if (!loadMore) return;
    if (!isLoading && !isCommentEnd) { loadMore.textContent = '加载更多'; loadMore.classList.remove('hidden'); }
    else { loadMore.classList.add('hidden'); }
  }
  function updateContextTitle(validComments) {
    if (!titleElement) return;
    if (isAuthorSay) { titleElement.textContent = '作者说'; return; }
    if (hasParagraph && validComments && validComments.length > 0) {
      var first = validComments[0] || {};
      var raw = first.reffer_content || first.RefferContent || first.ReferContent || first.referContent || first.refferContent || first.ParagraphContent || first.paragraphContent || (first.raw && (first.raw.RefferContent || first.raw.ReferContent || first.raw.reffer_content || first.raw.ParagraphContent)) || '';
      if (raw) { var paraText = String(raw).replace(/[?\s]/g, ''); if (paraText.length > 50) paraText = paraText.slice(0, 50) + '?'; titleElement.textContent = paraText; return; }
      titleElement.textContent = '??'; return;
    }
    var dispTitle = String(window.chapterName || '??').split(',{"style"')[0];
    titleElement.textContent = (window.bookName ? '《' + window.bookName + '》' : '') + dispTitle;
  }
  function setCountText(total, loaded) {
    if (!commentCountElement) return;
    total = Number(total || 0);
    loaded = Number(loaded || 0);
    if (total > 0 && loaded > total) total = loaded;
    var shown;
    if (total === 0 && loaded === 0) {
        shown = '暂无' + commentTypeText;
    } else {
        var displayTotal = Math.max(total, loaded);
        shown = displayTotal + '条' + commentTypeText + '，已加载' + loaded + '条';
    }
    commentCountElement.textContent = shown;
    commentCountElement.style.display = 'block';
    if (typeof window._updateTopBar === 'function') {
        window._updateTopBar(shown);
    }
}
  if (loadMore) loadMore.addEventListener('click', function() { if (!isLoading && !isCommentEnd) loadComments(); });

  function getLikeSvg() { const fillColor = 'rgb(0,0,0)'; return '<svg class="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><g clip-rule="evenodd" fill="' + fillColor + '" fill-rule="evenodd"><path d="m11.6934 5.05855c.6491-.9737 1.7419-1.55855 2.9122-1.55855h.2139c.9269 0 1.632.8323 1.4796 1.7466l-.6118 3.6712c-.0508.30477.1842.5822.4932.5822h2.3799c1.5776 0 2.7608 1.4433 2.4514 2.9903l-1.2 6c-.2337 1.1686-1.2597 2.0097-2.4514 2.0097h-11.3604c-1.38071 0-2.5-1.1193-2.5-2.5v-8c0-.27614.22386-.5.5-.5h3.92963c.50153 0 .96988-.25065 1.24808-.66795zm2.9122-.55855c-.8359 0-1.6165-.41775-2.0802 1.11325l-2.5156 3.7735c-.46371.69545-1.24428 1.11325-2.08017 1.11325h-2.92963c-.27614 0-.5.2239-.5.5v7c0 .8284.67157 1.5 1.5 1.5h11.3604c.715 0 1.3306-.5047 1.4709-1.2058l1.2-6c.1856-.9282-.5243-1.7942-1.4709-1.7942h-3.5604c-.147 0-.2865-.0647-.3815-.1768-.095-.1122-.1359-.2604-.1117-.4054l.8059-4.8356c.0508-.30477.1842-.5822.4932-.5822z"/><path d="m8 20c-.27614 0-.5-.2239-.5-9c0-.2761.22386-.5.5-.5.27614 0 .5.2239.5.5v9c0 .2761-.22386.5-.5.5z"/></g></svg>'; }
  /* ⚠️ 核心修复：添加双重转义，确保在浏览器里正确生效！ ⚠️ */
  const EMOJI_REG = /\\[fn=(\\d+)\\]/g;
  const EMOJI_MAP = { 1:"👏",2:"🌹",3:"🤝",4:"😁",5:"😄",6:"🥺",7:"🙂",8:"😏",9:"😙",10:"👆🏻🐽",11:"🙄",12:"😭",13:"😵",14:"😥",15:"🖕🏻",16:"🥵",17:"😓",18:"🤫",19:"😂",20:"😢",21:"😍",22:"🤕🔨",23:"😑",24:"😫",25:"🤗",26:"🤪",27:"🙏",28:"😣",29:"💪",30:"💀",31:"😳",32:"😎",33:"🤭",34:"😄👏",35:"👍🏻",36:"🤓",37:"😡",38:"🙁",39:"😄❓",40:"😞",41:"😧",42:"💋",43:"☺️",44:"🤬",45:"😴",46:"🤠🚬",47:"😱",48:"🐷",49:"😪",50:"🤐",51:"🥴",52:"🌙",53:"❤️",54:"🔪",55:"🎁",56:"💔",57:"👊🏻",58:"😒",59:"✌🏻️",60:"😮",61:"🤨",62:"😴",63:"👏🏻",64:"🐲",65:"⭐",66:"🌧️",67:"🍉",68:"🍵",69:"🔥",70:"💯" };
  function formatEmoji(str) { return str ? str.replace(EMOJI_REG, (m, n) => EMOJI_MAP[n] || '💬') : ''; }
  function escapeCommentHtml(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
  function formatCommentHtml(str) {
    return escapeCommentHtml(formatEmoji(String(str == null ? '' : str)))
      .replace(/\\\\\\\\n|\\\\n|\\r\\n|\\r|\\n/g, '<br>');
  }
  function buildMetaText(createTime, floor, ipAddress) {
      let floorStr = floor ? ' · 第' + floor + '楼' : '';
      let ipStr = ipAddress ? ' · ' + ipAddress : '';
      let fullDateStr = createTime || '';
      let dateOnlyStr = fullDateStr.split(' ')[0] || fullDateStr;
      let metaSuffix = floorStr + ipStr;
      let testStr = fullDateStr + metaSuffix;
      let visualLen = 0;
      for (let i = 0; i < testStr.length; i++) {
          visualLen += testStr.charCodeAt(i) > 255 ? 2 : 1;
      }
      return visualLen > 28 ? (dateOnlyStr + metaSuffix) : testStr;
  }
  function truncateUsername(username) { return username || '匿名'; }
  function createBadgeSlot(titleObj) {
      const slot = document.createElement('span');
      slot.className = 'badge-slot';
      if (titleObj.type === 'img') {
          const img = document.createElement('img');
          img.className = 'badge-img';
          img.src = titleObj.val;
          img.referrerPolicy = 'no-referrer';
          img.onload = function() { img.style.display = 'block'; };
          img.onerror = function() { slot.remove(); };
          slot.appendChild(img);
      } else if (titleObj.type === 'text') {
          const txtWrap = document.createElement('span'); 
          const innerTxt = document.createElement('span'); 
          const colorMap = {
              1: { bg: 'linear-gradient(180deg, #e0e7ff 0%, #c7d2fe 100%)', textBg: 'linear-gradient(180deg, #4338ca 0%, #312e81 100%)', shadow: 'rgba(67, 56, 202, 0.15)' },
              2: { bg: 'linear-gradient(180deg, #ffe4e6 0%, #fecdd3 100%)', textBg: 'linear-gradient(180deg, #e11d48 0%, #be123c 100%)', shadow: 'rgba(225, 29, 72, 0.15)' },
              3: { bg: 'linear-gradient(180deg, #f3e8ff 0%, #e9d8fd 100%)', textBg: 'linear-gradient(180deg, #9955dd 0%, #803bb8 100%)', shadow: 'rgba(153, 85, 221, 0.15)' },
              9: { bg: 'linear-gradient(180deg, #f1f5f9 0%, #e2e8f0 100%)', textBg: 'linear-gradient(180deg, #475569 0%, #334155 100%)', shadow: 'rgba(71, 85, 105, 0.15)' }
          };
          const allowedKeys = [1, 2, 3, 9];
          const randomKey = allowedKeys[Math.floor(Math.random() * allowedKeys.length)];
          const style = colorMap[randomKey];
          txtWrap.style.cssText = 'display: inline-flex; align-items: center; justify-content: center; padding: 0.2rem 0.35rem; border-radius: 4px; margin-right: 2px; background: ' + style.bg + '; box-shadow: 0 1px 3px ' + style.shadow + '; box-sizing: border-box;';
          innerTxt.style.cssText = 'font-size: 0.65rem; line-height: 1; font-weight: 600; white-space: nowrap; background: ' + style.textBg + '; -webkit-background-clip: text; background-clip: text; color: transparent; text-shadow: 0 0 8px ' + style.shadow + ';';
          innerTxt.textContent = titleObj.val;
          txtWrap.appendChild(innerTxt);
          slot.appendChild(txtWrap);
      }
      return slot;
  }
  let scrollX = 0, scrollY = 0;
  function showError(message) { scrollX = window.scrollX; scrollY = window.scrollY; isLoading = true; loading.classList.add('hidden'); commentList.classList.add('hidden'); errorAlert.classList.remove('hidden'); refreshError(message, 3); }
  function refreshError(msg, leftSeconds) { if (leftSeconds <= 0) { errorAlert.textContent = ''; errorAlert.classList.add('hidden'); commentList.classList.remove('hidden'); window.scrollTo(scrollX, scrollY); isLoading = false; return; } errorAlert.textContent = msg + "        " + leftSeconds + "s后自动返回……"; setTimeout(() => refreshError(msg, leftSeconds - 1), 1000); }
    function initFullscreenImage() {
    function closePreview(pushBack){var p=document.getElementById("preview");if(!p||p.style.display!=="flex")return false;p.style.display="none";document.body.style.overflow="";var img=p.getElementsByTagName("img")[0];if(img)img.style.transform="scale(1) translate(0px, 0px)";if(pushBack&&location.hash==="#qdPreview"){history.back()}return true}
    window.addEventListener("hashchange",function(){if(location.hash!=="#qdPreview")closePreview(false)});
    window.addEventListener("popstate",function(){if(location.hash!=="#qdPreview")closePreview(false)});
    function showPreview(src,idx){
      var urls=Array.isArray(src)?src:[src];
      var cur=Math.max(0,Math.min(Number(idx||0),urls.length-1));
      if(!urls.length||!urls[cur])return;
      var p=document.getElementById("preview"),img;
      if(!p){
        p=document.createElement("div");p.id="preview";p.className="preview";p.style.cssText="position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,0.92);display:none;align-items:center;justify-content:center;overflow:hidden;touch-action:none;overscroll-behavior:none;user-select:none;-webkit-user-select:none;-webkit-touch-callout:none;";
        img=document.createElement("img");img.style.cssText="position:absolute;max-width:95vw;max-height:95vh;object-fit:contain;transition:transform 0.15s ease;transform:translate(0px,0px) scale(1);will-change:transform;";
        var imgNext=document.createElement("img");imgNext.style.cssText="position:absolute;max-width:95vw;max-height:95vh;object-fit:contain;transition:transform 0.15s ease;transform:translate(100vw,0) scale(1);will-change:transform;display:none;";
        var indicator=document.createElement("div");indicator.style.cssText="position:absolute;left:18px;bottom:16px;padding:3px 9px;border-radius:999px;background:rgba(0,0,0,0.45);color:#fff;font-size:12px;line-height:18px;z-index:2;";
        p.appendChild(imgNext);p.appendChild(img);p.appendChild(indicator);document.body.appendChild(p);
        var urlsState=[],curIndex=0,scale=1,startX=0,startY=0,moveX=0,moveY=0,baseX=0,baseY=0,overX=0,overY=0,isDragging=false,startDist=0,startScale=1,swipeTarget=-1,swipeDir=0;
        function setIndicator(){indicator.textContent=urlsState.length>1?(curIndex+1)+" / "+urlsState.length:"";indicator.style.display=urlsState.length>1?"block":"none"}
        function setImg(){img.src=urlsState[curIndex]||"";setIndicator()}
        function update(){img.style.transform="translate("+moveX+"px,"+moveY+"px) scale("+scale+")"}
        function reset(){scale=1;moveX=0;moveY=0;baseX=0;baseY=0;overX=0;overY=0;swipeTarget=-1;swipeDir=0;img.style.transition="transform 0.15s ease";imgNext.style.transition="transform 0.15s ease";imgNext.style.display="none";p.style.background="rgba(0,0,0,0.92)";update()}
        function dist(ts){var dx=ts[0].clientX-ts[1].clientX,dy=ts[0].clientY-ts[1].clientY;return Math.sqrt(dx*dx+dy*dy)}
        function limitPan(v,max){return Math.max(-max,Math.min(max,v))}
        function panLimitX(){return Math.max(0,(scale-1)*window.innerWidth*0.45)}
        function panLimitY(){return Math.max(0,(scale-1)*window.innerHeight*0.45)}
        function prepSwipe(dx){if(scale!==1||urlsState.length<=1)return;var dir=dx<0?1:-1,target=(curIndex+dir+urlsState.length)%urlsState.length;if(swipeTarget!==target){swipeTarget=target;swipeDir=dir;imgNext.src=urlsState[target];imgNext.style.display="block";imgNext.style.transition="none"}imgNext.style.transform="translate("+(dx+dir*window.innerWidth)+"px,0) scale(1)"}
        p.onclick=function(e){if(e.target===p){closePreview(true);reset()}};
        img.onclick=function(e){e.stopPropagation()};
        img.ondblclick=function(e){e.stopPropagation();scale=scale>1?1:2.5;moveX=0;moveY=0;baseX=0;baseY=0;update()};
        p.addEventListener("touchstart",function(e){if(p.style.display!=="flex")return;e.stopPropagation();img.style.transition="none";imgNext.style.transition="none";if(e.touches.length===2){isDragging=false;startDist=dist(e.touches);startScale=scale;return}if(e.touches.length!==1)return;isDragging=true;startX=e.touches[0].clientX;startY=e.touches[0].clientY;baseX=moveX;baseY=moveY;overX=0;overY=0;if(scale===1){moveX=0;moveY=0;swipeTarget=-1;swipeDir=0;imgNext.style.display="none"}},{passive:true});
        p.addEventListener("touchmove",function(e){if(p.style.display!=="flex")return;e.stopPropagation();if(e.cancelable)e.preventDefault();if(e.touches.length===2&&startDist){scale=Math.min(4,Math.max(1,startScale*(dist(e.touches)/startDist)));if(scale===1){moveX=0;moveY=0;baseX=0;baseY=0}update();return}if(!isDragging||e.touches.length!==1)return;var dx=e.touches[0].clientX-startX,dy=e.touches[0].clientY-startY;if(scale>1){var maxX=panLimitX(),maxY=panLimitY(),rawX=baseX+dx,rawY=baseY+dy;moveX=limitPan(rawX,maxX);moveY=limitPan(rawY,maxY);overX=rawX-moveX;overY=rawY-moveY;if(Math.abs(overX)>Math.abs(overY)*1.2)prepSwipe(overX);else imgNext.style.display="none";p.style.background="rgba(0,0,0,"+(0.92-Math.min(0.55,Math.abs(overY)/360))+")";update();return}moveX=dx;moveY=dy;if(Math.abs(dx)>Math.abs(dy)*1.2)prepSwipe(dx);else imgNext.style.display="none";update();p.style.background="rgba(0,0,0,"+(0.92-Math.min(0.55,Math.abs(moveY)/360))+")"},{passive:false});
        p.addEventListener("touchend",function(e){if(e)e.stopPropagation();img.style.transition="transform 0.18s ease";imgNext.style.transition="transform 0.18s ease";if(e&&e.touches&&e.touches.length===0)startDist=0;if(!isDragging){if(scale<=1)reset();return}isDragging=false;var ax=Math.abs(moveX),ay=Math.abs(moveY),w=window.innerWidth;if(scale>1){var ox=Math.abs(overX),oy=Math.abs(overY);if(oy>90&&oy>ox*1.2){closePreview(true);reset();return}if(swipeTarget>=0&&ox>70&&ox>oy*1.2){var zdir=swipeDir,zw=window.innerWidth;img.style.transform="translate("+(-zdir*zw)+"px,0) scale(1)";imgNext.style.transition="transform 0.18s ease";imgNext.style.transform="translate(0,0) scale(1)";setTimeout(function(){curIndex=swipeTarget;setImg();reset()},190);return}}if(scale===1&&ay>90&&ay>ax*1.2){closePreview(true);reset();return}if(scale===1&&swipeTarget>=0&&ax>70&&ax>ay*1.2){var dir=swipeDir;img.style.transform="translate("+(-dir*w)+"px,0) scale(1)";imgNext.style.transform="translate(0,0) scale(1)";setTimeout(function(){curIndex=swipeTarget;setImg();moveX=0;moveY=0;img.style.transition="none";img.style.transform="translate(0,0) scale(1)";imgNext.style.display="none";swipeTarget=-1;swipeDir=0},190);return}if(scale===1){moveX=0;moveY=0;imgNext.style.display="none"}else{baseX=moveX;baseY=moveY;overX=0;overY=0;imgNext.style.display="none"}p.style.background="rgba(0,0,0,0.92)";update()},{passive:true});
        p._setPreview=function(list,index){urlsState=list||[];curIndex=Math.max(0,Math.min(Number(index||0),urlsState.length-1));reset();setImg()}
      }else{img=p.getElementsByTagName("img")[1]||p.getElementsByTagName("img")[0]}
      p._setPreview(urls,cur);p.style.display="flex";document.body.style.overflow="hidden";
      try{if(location.hash!=="#qdPreview")location.hash="qdPreview"}catch(e){}
    }
    document.addEventListener('click', (e) => {
      const target = e.target;
      if (target.classList && target.classList.contains('post-img')) {
          let parent = target.closest('.post-img-list');
          if (parent) {
              let imgs = Array.from(parent.querySelectorAll('.post-img'));
              let urls = imgs.map(img => img.src);
              let idx = imgs.indexOf(target);
              showPreview(urls, idx);
          } else {
              showPreview([target.src], 0);
          }
      } else if (target.classList && (target.classList.contains('avatar') || target.classList.contains('comment-img'))) {
          showPreview([target.src], 0);
      }
    });
  }
  /* ⚠️ 核心修复：防止引号解析失效导致的数据解析崩溃 ⚠️ */
  function safeParseBigInt(jsonStr) { 
      if (typeof jsonStr !== 'string') return jsonStr; 
      if (jsonStr.indexOf('java.') === 0 || jsonStr.indexOf('{') === -1) {
          return { Result: -1, Message: "网络波动导致获取失败，请重试" };
      }
      try {
          jsonStr = jsonStr.replace(/("rootReviewId" *: *)([0-9]{16,})/g, '$1"$2"'); 
          jsonStr = jsonStr.replace(/("([A-Za-z_]*Id|[A-Za-z_]*ID|[A-Za-z_]*IdList)" *: *)([0-9]{16,})/g, '$1"$3"'); 
          return JSON.parse(String(jsonStr).trim()); 
      } catch (e) {
          return { Result: -1, Message: "数据解析异常" };
      }
  }
  function qdTextOf(v) {
    if (v == null) return '';
    if (typeof v === 'string') {
      var s = v;
      try {
        var p = JSON.parse(s);
        if (Array.isArray(p) || (p && typeof p === 'object')) return qdTextOf(p);
      } catch(e) {}
      return s;
    }
    if (Array.isArray(v)) return v.map(function(a){ return qdTextOf(a); }).join('');
    if (typeof v === 'object') {
      return qdTextOf(v.Text || v.text || v.Content || v.content || v.Body || v.body || v.Value || v.value || v.Txt || v.txt || v.Items || v.items || v.List || v.list || '');
    }
    return String(v || '');
  }
  function qdPick(obj, keys, def) {
    obj = obj || {};
    for (var i = 0; i < keys.length; i++) {
      var v = obj[keys[i]];
      if (v !== undefined && v !== null && v !== '') return v;
    }
    return def == null ? '' : def;
  }
  function qdImageOf(v) {
    var list = qdImageList(v);
    return list.length ? list[0] : '';
  }
  function qdImageList(v) {
    if (!v) return [];
    if (typeof v === 'string') {
      try {
        var p = JSON.parse(v);
        if (p && typeof p === 'object') return qdImageList(p);
      } catch(e) {}
      return v ? [v] : [];
    }
    if (Array.isArray(v)) {
      var out = [];
      v.forEach(function(item) { out = out.concat(qdImageList(item)); });
      return out.filter(function(u, idx, arr) { return !!u && arr.indexOf(u) === idx; });
    }
    if (typeof v === 'object') {
      var direct = v.Url || v.url || v.ImageUrl || v.imageUrl || v.ThumbUrl || v.thumbUrl || v.image_url || '';
      if (direct) return [direct];
      return qdImageList(v.ImageDetail || v.ImageList || v.Images || v.PicList || v.List || v.list || v.Items || v.items || '');
    }
    return [];
  }
  function qdAudioOf(v) {
    if (!v) return '';
    if (typeof v === 'string') {
      try {
        var p = JSON.parse(v);
        if (p && typeof p === 'object') return qdAudioOf(p);
      } catch(e) {}
      return v;
    }
    if (Array.isArray(v)) {
      for (var i = 0; i < v.length; i++) {
        var u = qdAudioOf(v[i]);
        if (u) return u;
      }
      return '';
    }
    if (typeof v === 'object') {
      return v.Url || v.url || v.AudioUrl || v.audioUrl || v.audio_url || v.VoiceUrl || v.voiceUrl || v.Src || v.src || qdAudioOf(v.Audio || v.audio || v.Voice || v.voice || '');
    }
    return '';
  }
  function adaptComment(item) {
    item = item || {};
    var raw = item.raw || item;
    var user = item.user_info || raw.UserInfo || raw.User || raw.user || {};
    var content = qdTextOf(item.text || item.content || raw.Content || raw.content || raw.ReviewContent || raw.Body || raw.PostBody || raw.PostContent || raw.Subject || raw.Title);
    var ts = Number(item.create_timestamp || raw.CreateTime || raw.CreateTimestamp || raw.PostTime || raw.UpdateTime || 0);
    if (ts && String(ts).length <= 10) ts *= 1000;
    let createTime = ts ? new Date(ts) : null;
    let timeStr = createTime ? (createTime.getFullYear() + "-" + (createTime.getMonth() + 1).toString().padStart(2, '0') + "-" + createTime.getDate().toString().padStart(2, '0') + " " + createTime.getHours().toString().padStart(2, '0') + ":" + createTime.getMinutes().toString().padStart(2, '0')) : (raw.PostDate || raw.CreateTimeStr || '');
    return {
      reviewId: String(item.comment_id || raw.Id || raw.CommentId || raw.ReviewId || raw.PostId || raw.PostID || ''),
      nickname: user.user_name || user.UserName || user.NickName || user.nickname || raw.UserName || raw.NickName || '匿名',
      avatarUrl: user.user_avatar || user.UserHeadIcon || user.Avatar || user.avatar || raw.UserHeadIcon || raw.Avatar || '',
      content: String(content || ''),
      createTime: timeStr,
      ipAddress: raw.IpLocation || raw.IPLocation || raw.Address || '',
      likeCount: item.digg_count || raw.AgreeAmount || raw.LikeCount || raw.DiggCount || raw.StarCount || raw.PraiseCount || 0,
      replyCount: item.reply_count || raw.ReviewCount || raw.ReplyCount || raw.PostCount || raw.CommentCount || 0,
      titles: (function() {
        var ts = [];
        var tl = raw.TitleInfoList || item.TitleInfoList || [];
        if (Array.isArray(tl)) {
          for (var i = 0; i < tl.length; i++) {
            var t = tl[i] || {};
            if (t.TitleImage) ts.push({ type: 'img', val: t.TitleImage });
            else if (t.TitleName) ts.push({ type: 'text', val: t.TitleName, titleType: t.TitleType || 0 });
          }
        }
        return ts;
      })(),
      floor: raw.Floor || raw.FloorNo || 0,
      imageUrl: qdImageOf(item.image_url || raw.image_url || raw.ImageDetail || raw.ImageList || raw.Images || raw.PicList),
      audioUrl: qdAudioOf(item.audio_url || raw.audio_url || raw.AudioUrl || raw.audioUrl || raw.Audio || raw.VoiceUrl || raw.voiceUrl),
      frameUrl: item.frame_url || raw.FrameUrl || raw.UserHeadFrame || '',
      replyToName: raw.RelatedUser || raw.ReplyUserName || '',
      replyToUserId: raw.RelatedUserId ? String(raw.RelatedUserId) : '',
      isGod: raw.EssenceType === 2 || raw.IsEssence === true
    };
  }
  function renderComment(comment) {
    const commentItem = document.createElement('div'); commentItem.className = 'flex py-2 border-b border-gray-100 leading-relaxed text-base relative overflow-hidden'; commentItem.setAttribute('data-review-id', comment.reviewId);
    const avatarContainer = document.createElement('div'); avatarContainer.className = 'relative w-12 h-12 flex items-center justify-center flex-shrink-0';
    if (comment.frameUrl) { const frameImg = document.createElement('img'); frameImg.className = 'absolute inset-0 w-full h-full object-contain'; frameImg.src = comment.frameUrl; frameImg.alt = '头像框'; frameImg.referrerPolicy = 'no-referrer'; frameImg.onerror = () => frameImg.classList.add('hidden'); avatarContainer.appendChild(frameImg); }
    /* 头像加入懒加载与异步解码 */
    const avatar = document.createElement('img'); avatar.className = 'avatar w-8 h-8 rounded-full object-cover cursor-pointer relative z-10 flex-shrink-0'; avatar.src = comment.avatarUrl || 'https://qidian.gtimg.com/qd/images/ico/default_user.0.2.png'; avatar.alt = comment.nickname || '用户头像'; avatar.referrerPolicy = 'no-referrer'; avatar.loading = 'lazy'; avatar.decoding = 'async'; avatarContainer.appendChild(avatar); commentItem.appendChild(avatarContainer);
    const commentContent = document.createElement('div'); commentContent.className = 'flex-1 min-w-0 z-10 relative'; commentItem.appendChild(commentContent);
    const commentHeader = document.createElement('div'); commentHeader.className = 'flex flex-nowrap items-center';
    const userInfo = document.createElement('div'); 
    userInfo.style.cssText = 'display: flex; align-items: center; min-width: 0; flex: 1;';
    const nickname = document.createElement('span'); 
    nickname.className = 'font-medium text-gray-800 text-base'; 
    nickname.style.cssText = 'display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 0 1 auto; min-width: 0; margin-right: 4px;'; 
    nickname.textContent = truncateUsername(comment.nickname); 
    nickname.title = comment.nickname || '匿名'; 
    userInfo.appendChild(nickname);
    const badgeWrap = document.createElement('span'); 
    badgeWrap.className = 'badge-wrap';
    (comment.titles || []).slice(0, 3).forEach(function(titleUrl) {
        badgeWrap.appendChild(createBadgeSlot(titleUrl));
    });
    userInfo.appendChild(badgeWrap);
    commentHeader.appendChild(userInfo); commentContent.appendChild(commentHeader);
    const commentBody = document.createElement('div'); commentBody.className = 'mt-1 relative z-10';
    const content = document.createElement('p'); content.className = 'text-gray-700 leading-relaxed'; 
    /* ⚠️ 核心修复：确保能够正确处理换行而不被转义吞噬 ⚠️ */
    content.innerHTML = formatCommentHtml(comment.content); commentBody.appendChild(content);
    /* ==== 主图切除过渡动画，保留原比例，增加防塌陷占位背景 ==== */
    if (comment.imageUrl) { 
        const img = document.createElement('img'); 
        img.className = 'comment-img mt-2 rounded-md max-w-[60vw] w-auto cursor-pointer'; 
        img.style.cssText = 'min-height: 120px; background-color: #f3f4f6;';
        img.src = comment.imageUrl; 
        img.alt = '评论图片'; 
        img.referrerPolicy = 'no-referrer'; 
        img.loading = 'lazy';
        img.decoding = 'async';
        commentBody.appendChild(img); 
    }
    if (comment.audioUrl) { const audio = document.createElement('audio'); audio.className = 'mt-2 w-full max-w-md'; audio.src = comment.audioUrl; audio.controls = true; commentBody.appendChild(audio); }
    commentContent.appendChild(commentBody);
    const commentMeta = document.createElement('div'); commentMeta.className = 'mt-2 flex justify-between items-center text-sm relative z-10';
    const metaInfo = document.createElement('div'); 
    metaInfo.className = 'truncate flex-1 mr-2'; 
    metaInfo.textContent = buildMetaText(comment.createTime, comment.floor, comment.ipAddress);
    const likeBtn = document.createElement('div'); likeBtn.className = 'flex items-center text-gray-500 transition-colors flex-shrink-0'; likeBtn.innerHTML = getLikeSvg() + " " + comment.likeCount;
    commentMeta.appendChild(metaInfo); commentMeta.appendChild(likeBtn); commentContent.appendChild(commentMeta);
    const replyWrapper = document.createElement('div'); replyWrapper.className = 'mt-2 relative z-10'; commentContent.appendChild(replyWrapper);
    if (comment.replyCount > 0) { const replyToggle = document.createElement('div'); replyToggle.className = 'reply-toggle'; replyToggle.textContent = "展开" + comment.replyCount + "条回复"; replyToggle.addEventListener('click', () => { loadReplies(comment, replyWrapper, replyToggle); }); replyWrapper.appendChild(replyToggle); }
    const repliesContainer = document.createElement('div'); repliesContainer.className = 'replies-container mt-3 space-y-3 hidden'; replyWrapper.appendChild(repliesContainer);
    const replyMore = document.createElement('div'); replyMore.className = 'reply-more hidden'; replyWrapper.appendChild(replyMore);
    if (comment.isGod) {
        var godStamp = document.createElement('div');
        godStamp.className = 'absolute pointer-events-none z-50';
        godStamp.style.cssText = 'top: 0px; right: 2px; opacity: 0.9;'; 
        var svgStr = '<svg width="42" height="42" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">' +
            '<g transform="rotate(15 50 50)">' +
            '<circle cx="50" cy="50" r="44" stroke="#B89047" stroke-width="2" fill="none" />' +
            '<circle cx="50" cy="50" r="38" stroke="#B89047" stroke-width="1.5" stroke-dasharray="4 3" fill="none" />' +
            '<text x="50" y="55" font-family="STXingkai, 华文行楷, cursive, serif" font-size="24" font-weight="bold" fill="#B89047" text-anchor="middle" letter-spacing="1">神评论</text>' +
            '<text x="50" y="72" font-family="sans-serif" font-size="8" fill="#B89047" letter-spacing="1" font-weight="bold" text-anchor="middle">QIDIANDUSHU</text>' +
            '</g></svg>';
        godStamp.innerHTML = svgStr;
        commentItem.appendChild(godStamp);
        if (typeof commentHeader !== 'undefined') {
            commentHeader.classList.add('god-stamp-header');
        }
    }
    return commentItem;
  }
  function renderComments(comments) { 
      if (!comments || comments.length === 0) { 
          if (commentPage === 1) { commentList.innerHTML = '<div class="text-center text-gray-500 py-8">暂无评论</div>'; } 
          return; 
      } 
      comments.forEach(function(comment) { commentList.appendChild(renderComment(comment)); }); 
  }
  function httpGet(url) {
    if (window.java && typeof window.java.ajax === 'function') {
      return window.java.ajax(url);
    }
    try {
      if (window.java && typeof window.java.get === 'function') {
        var res = window.java.get(url, {});
        if (res && typeof res.body === 'function') return res.body();
        if (res != null) return String(res);
      }
    } catch(e) {}
    throw new Error("当前阅读 WebView 不支持 java.ajax/java.get");
  }
  function buildAuthorSayComments(remoteData) {
    remoteData = remoteData || {};
    var obj = remoteData.author_say || remoteData.authorSay || remoteData.AuthorSay || remoteData.Author_say || remoteData;
    if (typeof obj === 'string') {
      try {
        var parsed = JSON.parse(obj);
        if (parsed && typeof parsed === 'object') obj = parsed;
      } catch(e) {}
    }
    if (!obj || typeof obj !== 'object') obj = { AuthorComments: String(obj || '') };

    var value = qdPick(obj, [
      'AuthorComments', 'AuthorComment', 'AuthorSay', 'author_say', 'authorSay',
      'ChapterAuthorSay', 'chapterAuthorSay', 'Content', 'Text', 'content', 'text',
      'Comment', 'comment', 'Say', 'say', 'AuthorWords', 'authorWords', 'WriterSay', 'writerSay'
    ], '');
    var content = qdTextOf(value).trim();
    if (!content) {
      var ask = obj.AskMonthTicket || obj.ask_month_ticket || {};
      content = qdTextOf(qdPick(ask, ['AuthorExpectText', 'author_expect_text'], '')).trim();
    }
    if (!content) return [];

    var authorName = qdPick(obj, ['AuthorName', 'author_name', 'NickName', 'nickname', 'UserName', 'Author', 'author'], '作者');
    var avatar = qdPick(obj, ['HeadImageUrl', 'UserHeadIcon', 'Avatar', 'avatar', 'AuthorAvatar', 'author_avatar'], '');
    var createTime = qdPick(obj, ['CreateTime', 'CreateTimestamp', 'UpdateTime', 'PostTime'], 0);
    return [{
      comment_id: 'author-' + String(chapterId || ''),
      text: content,
      create_timestamp: createTime,
      digg_count: 0,
      reply_count: 0,
      user_info: { user_name: authorName, user_avatar: avatar },
      raw: { UserName: authorName, UserHeadIcon: avatar, CreateTime: createTime }
    }];
  }
  async function loadComments() {
    if (isLoading || isCommentEnd) return;
    isLoading = true; loading.classList.remove('hidden'); updateLoadMore();
    try {
      debugEnvCheck();
      if (!bookId || !chapterId) { throw new Error('参数错误：缺少书籍ID(bookId)或章节ID(chapterId)'); }
      if (currentTab !== 'all' && !hasParagraph) { currentTab = 'all'; }
      var apiAction = isAuthorSay ? 'chapter_activity' : (hasParagraph ? 'paragraph_comments' : 'chapter_comments');
      if (hasParagraph && currentTab === 'image') apiAction = 'paragraph_image_comments';
      if (hasParagraph && currentTab === 'audio') apiAction = 'paragraph_audio_comments';
      var apiBase = window.qdApiBase || 'https://pl.aadcn.cn/api/qidian_full_api.php';
      var apiUrl = apiBase + '?action=' + apiAction +
        '&book_id=' + encodeURIComponent(bookId) +
        '&chapter_id=' + encodeURIComponent(chapterId) +
        (hasParagraph ? '&paragraph_id=' + encodeURIComponent(paragraphId || '') : '') +
        (currentTab === 'audio' ? '&role_id=' + encodeURIComponent(currentRoleId || '0') : '') +
        '&page=' + commentPage + '&page_size=20';
      var response = httpGet(apiUrl);
      var data = safeParseBigInt(response);
      if (!data || typeof data !== 'object') { throw new Error('返回数据格式异常'); }
      if (data.code !== 0) { throw new Error(data.message || '接口错误'); }
      var remoteData = data.data || {};
      var rawList = isAuthorSay ? buildAuthorSayComments(remoteData) : (remoteData.comments || []);
      if (isAuthorSay) remoteData.total = rawList.length;
      ensureMediaTabsFromList(rawList);
      if (commentPage === 1) {
        var tabTotal = Number(remoteData.total || rawList.length || 0);
        if (currentTab === 'all') {
          totalAll = tabTotal;
          var nAll = document.getElementById('num-all');
          if (nAll) nAll.textContent = totalAll;
          lazyLoadCountAfterPageRender();
        } else if (currentTab === 'image') {
          totalImg = tabTotal;
          var nImg = document.getElementById('num-img');
          var tImg = document.querySelector('.tab-item[data-type="image"]');
          if (tImg) tImg.style.display = 'flex';
          if (nImg) nImg.textContent = totalImg;
        } else if (currentTab === 'audio') {
          totalAudio = tabTotal;
          var nAudio = document.getElementById('num-audio');
          var tAudio = document.querySelector('.tab-item[data-type="audio"]');
          if (tAudio) tAudio.style.display = 'flex';
          if (nAudio) nAudio.textContent = totalAudio;
          var roles = remoteData.audio_roles || (remoteData.raw && (remoteData.raw.AudioRole || remoteData.raw.AudioRoles)) || [];
          if (roles && roles.length) { audioRolesCached = roles; renderRoleTabs(audioRolesCached); }
          else if (audioRolesCached && audioRolesCached.length) { renderRoleTabs(audioRolesCached); }
        }
      }
      if (commentPage === 1) {
        window._seenCmtIds = {};
        window._actualLoadedCount = 0;
      }
      window._seenCmtIds = window._seenCmtIds || {};
      const validComments = rawList.filter(function(item) {
        var id = item.comment_id || (item.raw && (item.raw.Id || item.raw.CommentId || item.raw.ReviewId)) || (item.create_timestamp + '_' + (item.text || ''));
        if (window._seenCmtIds[id]) return false;
        window._seenCmtIds[id] = true;
        return true;
      });
      var pageSize = 20;
      window._actualLoadedCount += validComments.length;
      var activeTotal = currentTab === 'image' ? totalImg : (currentTab === 'audio' ? totalAudio : totalAll);
      if (!activeTotal) activeTotal = Number(remoteData.total || remoteData.TotalCount || remoteData.ReviewTotalCount || remoteData.ChapterReviewCount || 0);
      isCommentEnd = rawList.length < pageSize || (activeTotal > 0 && window._actualLoadedCount >= activeTotal);
      loading.classList.add('hidden');
      if (commentPage === 1) {
        commentList.classList.remove('hidden');
        var cCount = document.getElementById('commentCount');
        if (cCount) cCount.classList.remove('hidden');
      }
      if (commentPage === 1) updateContextTitle(validComments);
      if (validComments.length === 0 && commentPage === 1) {
        commentList.innerHTML = '<div class="text-center text-gray-500 py-8">暂无评论</div>';
      } else {
        validComments.map(function(i) { return adaptComment(i); }).forEach(function(c) {
          commentList.appendChild(renderComment(c));
        });
      }
      setCountText(activeTotal, window._actualLoadedCount);
      if (!isCommentEnd) {
        commentPage++;
      } else if (!commentList.querySelector('.end-card')) {
        const endDiv = document.createElement('div');
        endDiv.className = 'end-card';
        endDiv.textContent = '已经到底了~';
        commentList.appendChild(endDiv);
      }
    } catch (error) {
      loading.classList.add('hidden');
      showError(error.message || String(error));
    } finally {
      isLoading = false;
      updateLoadMore();
    }
  }
  function renderReply(reply) {
    const replyItem = document.createElement('div'); replyItem.className = 'reply-item flex pt-4 border-t border-gray-100';
    /* 头像加入懒加载与异步解码 */
    const avatar = document.createElement('img'); avatar.className = 'avatar w-8 h-8 rounded-full object-cover cursor-pointer flex-shrink-0'; avatar.src = reply.avatarUrl || 'https://qidian.gtimg.com/qd/images/ico/default_user.0.2.png'; avatar.alt = reply.nickname || '用户头像'; avatar.referrerPolicy = 'no-referrer'; avatar.loading = 'lazy'; avatar.decoding = 'async'; replyItem.appendChild(avatar);
    const replyContent = document.createElement('div'); replyContent.className = 'flex-1 min-w-0 ml-2'; replyItem.appendChild(replyContent);
    const replyHeader = document.createElement('div'); replyHeader.className = 'flex flex-nowrap items-center';
    const replyLeft = document.createElement('div'); 
    replyLeft.style.cssText = 'display: flex; align-items: center; min-width: 0; flex: 1;';
    const nickname = document.createElement('span'); 
    nickname.className = 'font-medium text-gray-800 text-sm'; 
    nickname.style.cssText = 'display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 0 1 auto; min-width: 0; margin-right: 4px;'; 
    nickname.textContent = truncateUsername(reply.nickname); 
    nickname.title = reply.nickname || '匿名'; 
    replyLeft.appendChild(nickname);
    const badgeWrap = document.createElement('span'); 
    badgeWrap.className = 'badge-wrap';
    (reply.titles || []).slice(0, 3).forEach(function(titleUrl) {
        badgeWrap.appendChild(createBadgeSlot(titleUrl));
    });
    replyLeft.appendChild(badgeWrap); 
    replyHeader.appendChild(replyLeft);
    replyContent.appendChild(replyHeader);
    if (reply.replyToName) { const replyTo = document.createElement('div'); replyTo.className = 'text-gray-500 text-xs leading-tight mt-0.5'; replyTo.textContent = "回复@" + truncateUsername(reply.replyToName); replyTo.title = reply.replyToName; replyContent.appendChild(replyTo); }
    const replyBody = document.createElement('div'); replyBody.className = 'mt-1 text-sm'; 
    /* ⚠️ 核心修复：同样解决楼中楼的换行符吞噬问题 ⚠️ */
    replyBody.innerHTML = formatCommentHtml(reply.content || '');
    /* ==== 楼中楼图切除过渡动画，保留原比例，增加防塌陷占位背景 ==== */
    if (reply.imageUrl) { 
        const img = document.createElement('img'); 
        img.className = 'comment-img mt-1 rounded-md max-w-[60vw] w-auto cursor-pointer'; 
        img.style.cssText = 'min-height: 80px; background-color: #f3f4f6;';
        img.src = reply.imageUrl; 
        img.alt = '回复图片'; 
        img.referrerPolicy = 'no-referrer'; 
        img.loading = 'lazy';
        img.decoding = 'async';
        replyBody.appendChild(img); 
    }
    if (reply.audioUrl) { const audio = document.createElement('audio'); audio.className = 'mt-1 w-full max-w-sm'; audio.src = reply.audioUrl; audio.controls = true; replyBody.appendChild(audio); }
    replyContent.appendChild(replyBody);
    const replyMeta = document.createElement('div'); replyMeta.className = 'mt-1 flex justify-between items-center text-sm text-gray-500';
    const metaInfo = document.createElement('div'); 
    metaInfo.className = 'truncate flex-1 mr-2'; 
    metaInfo.textContent = buildMetaText(reply.createTime, reply.floor, reply.ipAddress);
    const likeBtn = document.createElement('div'); likeBtn.className = 'flex items-center flex-shrink-0'; likeBtn.innerHTML = " " + getLikeSvg() + " " + (reply.likeCount || 0);
    replyMeta.appendChild(metaInfo); replyMeta.appendChild(likeBtn); replyContent.appendChild(replyMeta);
    return replyItem;
  }
  async function loadReplies(comment, replyWrapper, toggleEl) {
    const reviewId = comment.reviewId;
    const repliesContainer = replyWrapper.querySelector('.replies-container');
    const replyMore = replyWrapper.querySelector('.reply-more');
    let replyPage = 1;
    let loadedCount = 0;
    const totalReplies = comment.replyCount || 0;
    if (!reviewId) { toggleEl.textContent = '回复ID缺失'; return; }
    toggleEl.classList.add('hidden');
    repliesContainer.classList.remove('hidden');
    repliesContainer.classList.add('expanded');
    repliesContainer.innerHTML = '';
    const loadingEl = document.createElement('div');
    loadingEl.className = 'reply-loading text-gray-500 text-sm mt-2';
    loadingEl.textContent = '加载中...';
    repliesContainer.appendChild(loadingEl);
    function replyUrl(page) {
      var apiBase = window.qdApiBase || 'https://pl.aadcn.cn/api/qidian_full_api.php';
      return apiBase + '?action=comment_replies' +
        '&book_id=' + encodeURIComponent(bookId) +
        '&chapter_id=' + encodeURIComponent(chapterId) +
        (hasParagraph ? '&paragraph_id=' + encodeURIComponent(paragraphId || '') : '') +
        '&root_review_id=' + encodeURIComponent(reviewId) +
        '&page=' + page + '&page_size=20';
    }
    function addEndMark() {
      const endMark = document.createElement('div');
      endMark.className = 'flex items-center text-xs mt-3 mb-1 w-full';
      endMark.style.color = '#9ca3af';
      endMark.innerHTML = '<div class="flex-1 border-t" style="border-color: currentColor; opacity: 0.2;"></div><div class="mx-3" style="opacity: 0.9;">已加载全部回复</div><div class="flex-1 border-t" style="border-color: currentColor; opacity: 0.2;"></div>';
      repliesContainer.appendChild(endMark);
    }
    async function fetchAndRender(page, append) {
      var data = safeParseBigInt(httpGet(replyUrl(page)));
      if (!data || typeof data !== 'object') throw new Error('回复返回数据格式异常');
      if (data.code !== 0) throw new Error(data.message || '获取回复失败');
      var rawList = (data.data && data.data.comments) || [];
      var valid = rawList.map(function(i) { return adaptComment(i); });
      if (!append) repliesContainer.innerHTML = '';
      valid.forEach(function(r) { repliesContainer.appendChild(renderReply(r)); });
      loadedCount += valid.length;
      return valid.length;
    }
    try {
      var firstCount = await fetchAndRender(replyPage, false);
      if (loadingEl.parentNode) loadingEl.parentNode.removeChild(loadingEl);
      repliesContainer.classList.remove('hidden');
      repliesContainer.classList.add('expanded');
      var isReplyEnd = (loadedCount >= totalReplies) || firstCount < 20;
      if (!isReplyEnd) {
        replyMore.textContent = '展开更多回复';
        replyMore.classList.remove('hidden');
        replyMore.onclick = async function() {
          if (isReplyEnd) return;
          replyMore.classList.add('hidden');
          const moreLoading = document.createElement('div');
          moreLoading.className = 'reply-loading text-gray-500 text-sm mt-2';
          moreLoading.textContent = '加载中...';
          repliesContainer.appendChild(moreLoading);
          try {
            replyPage++;
            var moreCount = await fetchAndRender(replyPage, true);
            if (moreLoading.parentNode) moreLoading.parentNode.removeChild(moreLoading);
            isReplyEnd = (loadedCount >= totalReplies) || moreCount < 20;
            if (!isReplyEnd) replyMore.classList.remove('hidden');
            else addEndMark();
          } catch(err) {
            if (moreLoading.parentNode) moreLoading.parentNode.removeChild(moreLoading);
            showError(err.message || String(err));
          }
        };
      } else if (firstCount > 0) {
        addEndMark();
      }
    } catch(error) {
      if (loadingEl.parentNode) loadingEl.parentNode.removeChild(loadingEl);
      showError(error.message || String(error));
      toggleEl.classList.remove('hidden');
    }
  }
  function initInfiniteScroll() { let ticking = false; const PRELOAD_THRESHOLD = 600; window.addEventListener('scroll', () => { if (!ticking) { window.requestAnimationFrame(() => { const scrollY = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0; const windowHeight = window.innerHeight || document.documentElement.clientHeight || 0; const documentHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, document.documentElement.offsetHeight); const remaining = documentHeight - (scrollY + windowHeight); if (remaining <= PRELOAD_THRESHOLD && !isLoading && !isCommentEnd) { loadComments(); } ticking = false; }); ticking = true; } }); }
  function init() { if (!bookId || !chapterId) { showError('参数错误：缺少书籍ID(bookId)或章节ID(chapterId)'); return; } initFullscreenImage(); initInfiniteScroll(); setTimeout(function() { loadComments(); }, 200); }
  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();
</script>
</body>
</html>`;
}
// ================= 新增：内置美化评论 HTML  =================







function getBuiltInBeautifyHtml() {
 return `<script>(function(){function applyBeautify(){if(window._hasBeautified)return;var list=document.getElementById('commentList');if(!list)return;window._hasBeautified=true;document.body.classList.add('is-beautified');var style=document.createElement('style');style.textContent="*{-webkit-tap-highlight-color:transparent!important;outline:none!important}.max-w-4xl{background:transparent!important;box-shadow:none!important;padding:0!important;margin-top:0!important;width:100%!important;box-sizing:border-box!important;overflow:visible!important}.comment-card .flex.py-2.border-b{display:flex!important;gap:12px!important;position:relative!important;background:#ffffff!important;padding:18px!important;border-radius:12px!important;box-shadow:0 1px 6px rgba(0,0,0,0.03)!important;margin-bottom:12px!important;border:none!important;cursor:pointer!important}img.avatar{width:40px!important;height:40px!important;flex-shrink:0!important;border-radius:50%!important;border:1px solid rgba(0,0,0,0.05)!important;background:#f0f0f0!important;object-fit:cover!important}.comment-card .font-medium{color:#000!important;font-weight:bold!important;display:block!important;overflow:hidden!important;text-overflow:ellipsis!important;white-space:nowrap!important}.comment-card .flex.py-2.border-b>.flex-1>div:first-child .font-medium{font-size:15px!important}.comment-card .flex.py-2.border-b>.flex-1>.mt-1:not(.flex){font-size:16px!important;line-height:1.6!important;margin-top:4px!important}.comment-card .comment-img{width:160px!important;height:160px!important;max-width:160px!important;border-radius:8px!important;object-fit:cover!important}.replies-container{background:#f8fafc!important;padding:8px!important;border-radius:8px!important;margin-top:12px!important;margin-left:-60px!important;width:calc(100% + 60px)!important}.reply-item{display:flex!important;gap:8px!important;margin-bottom:2px!important;padding:8px 8px 8px 6px!important;border-bottom:none!important;align-items:flex-start!important;border-radius:6px!important}.reply-item img.avatar{width:30px!important;height:30px!important;margin-top:2px!important}body.dark-mode{--bg-primary:#121212!important;background-color:#121212!important}body.dark-mode .comment-card .flex.py-2.border-b{background:#1c1c1e!important;color:#e5e7eb!important;border:1px solid #2c2c2e!important}body.dark-mode .replies-container{background:#18181a!important}";document.head.appendChild(style)}var checkTimer=setInterval(function(){if(document.querySelector('.max-w-4xl')||document.getElementById('commentList')){applyBeautify();clearInterval(checkTimer)}},50);setTimeout(function(){clearInterval(checkTimer);applyBeautify()},3000)})();<\/script>`;
}












function getBuiltInDarkHtml() {
 var html = [
  "<script>",
  "/*  核心：把深色CSS像钢钉一样打进 <head>，绝不会被App的异步刷新删掉！ */",
  "(function() {",
  "  var darkStyle = document.createElement('style');",
  "  darkStyle.id = 'immortal-dark-css';",
  "  darkStyle.textContent = `",
        "    /* 强制根部黑底 */",
        "    html, body { background-color: #121212 !important; }",
        "    :root { ",
        "      --x-bg:#121212; --x-card:#1c1c1e; --x-border:#2c2c2e; --x-text:#e5e7eb; --x-subtext:#9ca3af; ",
        "      --x-top-bg:rgba(28,28,30,0.98); --x-top-border:#2c2c2e; --x-replies-bg:#18181a; ",
        "      --x-btn-bg:#2c2c2e; --x-tab-def:#9ca3af; --x-tab-act:#e5e7eb; --x-role-bg:#2c2c2e; ",
        "    }",
        "",
        "    /* 暴力接管所有可能被 App 异步插入的原生白色类名，只要它敢出现，物理层面瞬间变黑 */",
        "    [style*='background-color: rgb(255, 255, 255)'], [style*='background-color: #fff'], [style*='background: white'] { background-color: transparent !important; color: var(--x-text) !important; }",
        "    .bg-white, [class*='bg-white'] { background-color: var(--x-card) !important; border-color: var(--x-border) !important; color: var(--x-text) !important; }",
        "    .bg-gray-50, [class*='bg-gray-50'] { background-color: var(--x-bg) !important; border-color: var(--x-border) !important; color: var(--x-text) !important; }",
        "    .text-gray-800, [class*='text-gray-8'] { color: var(--x-text) !important; }",
        "    .text-gray-700, [class*='text-gray-7'] { color: #ddd !important; }",
        "    .text-gray-500, .text-gray-400, .text-xs, .reply-loading, [class*='text-gray-5'], [class*='text-gray-4'] { color: var(--x-subtext) !important; }",
        "    .border-gray-100 { border-color: var(--x-border) !important; }",
        "",
        "    /* ================= 原版美化的 CSS 排版 ================= */",
        "    html, body, #beautify-wrap { background-color: var(--x-bg) !important; color: var(--x-text) !important; }",
        "    .comment-card .flex.py-2.border-b, .end-card { background-color: var(--x-card) !important; border-color: var(--x-border) !important; }",
        "    .replies-container { background-color: var(--x-replies-bg) !important; }",
        "    .reply-toggle, .reply-more:not(.hidden) { background-color: var(--x-btn-bg) !important; color: var(--x-subtext) !important; }",
        "    .tab-bar, .tab-num { color: var(--x-tab-def) !important; }",
        "    .tab-item.active, .tab-item.active .tab-num { color: var(--x-tab-act) !important; font-weight: 800 !important; }",
        "    .tab-item.active::after { background-color: #f87171 !important; }",
        "    .role-tab-item { background: var(--x-role-bg) !important; color: var(--x-subtext) !important; border-color: transparent !important; }",
        "    .role-tab-item.active { background: rgba(239, 68, 68, 0.15) !important; color: #f87171 !important; border-color: rgba(248, 113, 113, 0.3) !important; font-weight: bold !important; }",
        "    .flex.items-center.flex-shrink-0 svg path, .comment-action svg path { fill: var(--x-text) !important; }",
        "",
        "    * { -webkit-tap-highlight-color: transparent !important; outline: none !important; }",
        "    #themeToggle, #commentCount { display: none !important; }",
        "    .max-w-4xl { background: transparent !important; box-shadow: none !important; padding: 0 !important; margin-top: 0 !important; width: 100% !important; box-sizing: border-box !important; overflow: visible !important; }",
        "",
        "    /*  原文框透明化：不管 App 异步刷新多少次塞进什么框，它永远没有背景色，彻底杜绝白块  */",
        "    .content-card { position: relative !important; background: transparent !important; padding: 18px 20px 18px 24px !important; border-radius: 0 !important; box-shadow: none !important; margin-bottom: 0px !important; margin-top: 0px !important; display: flex !important; align-items: center !important; min-height: 60px !important; width: 100% !important; box-sizing: border-box !important; border: none !important; }",
        "    .content-card::before { content: '' !important; position: absolute !important; left: 0 !important; top: 18px !important; bottom: 18px !important; width: 4px !important; background: #4b5563 !important; border-radius: 0 4px 4px 0 !important; }",
        "    .content-card > *, #title, #title * { margin: 0 !important; font-size: 16px !important; font-weight: 800 !important; text-align: left !important; text-indent: 0 !important; color: var(--x-text) !important; line-height: 1.6 !important; letter-spacing: 0.5px !important; word-break: break-word !important; display: block !important; background: transparent !important; border: none !important; box-shadow: none !important; }",
        "    #title::before, #title *::before { display: none !important; }",
        "",
        "    body.is-beautified .tab-bar { padding-bottom: 0px !important; }",
        "    body.is-beautified .role-tab-bar { margin-top: 4px !important; padding-bottom: 2px !important; }",
        "    .comment-card { background: transparent !important; padding: 0 !important; border-radius: 0 !important; box-shadow: none !important; margin: 0 !important; width: 100% !important; box-sizing: border-box !important; min-height: 100vh !important; }",
        "    .comment-card .flex.py-2.border-b { display: flex !important; gap: 12px !important; position: relative !important; background: var(--x-card) !important; padding: 18px !important; border-radius: 12px !important; box-shadow: 0 1px 6px rgba(0,0,0,0.03) !important; margin-bottom: 12px !important; border: 1px solid var(--x-border) !important; cursor: pointer !important; }",
        "    .comment-card .flex.py-2.border-b > .flex-1, .reply-item > .flex-1 { flex: 1 !important; min-width: 0 !important; }",
        "    img.avatar { width: 40px !important; height: 40px !important; flex-shrink: 0 !important; border-radius: 50% !important; box-shadow: none !important; border: 1px solid var(--x-border) !important; background: #2c2c2e !important; object-fit: cover !important; }",
        "    .comment-card .font-medium, .reply-item .font-medium { display: block !important; overflow: hidden !important; text-overflow: ellipsis !important; white-space: nowrap !important; }",
        "    .comment-card .flex.py-2.border-b > .flex-1 > div:first-child .font-medium { font-size: 15px !important; }",
        "    .comment-card .flex.py-2.border-b > .flex-1 > .mt-1:not(.flex) { font-size: 16px !important; line-height: 1.6 !important; margin-top: 4px !important; }",
        "    .reply-item img.avatar { width: 30px !important; height: 30px !important; margin-top: 2px !important; border: 1px solid var(--x-border) !important; }",
        "    .reply-item .font-medium { font-size: 14px !important; }",
        "    .reply-item > .flex-1 > .mt-1:not(.flex) { font-size: 15px !important; line-height: 1.5 !important; margin-top: 4px !important; }",
        "    :root { --badge-slot-h: 1.5em !important; }",
        "    img.badge-img { height: var(--badge-slot-h) !important; max-height: 24px !important; }",
        "    .badge-slot > span { background: #2c2c2e !important; box-shadow: none !important; border: 1px solid #4b5563 !important; }",
        "    .badge-slot > span > span { background: none !important; color: #9ca3af !important; -webkit-text-fill-color: #9ca3af !important; text-shadow: none !important; }",
        "    .replies-container { background: var(--x-replies-bg) !important; padding: 8px !important; border-radius: 8px !important; margin-top: 12px !important; margin-left: -60px !important; width: calc(100% + 60px) !important; border-left: none !important; position: relative !important; box-sizing: border-box !important; }",
        "    .reply-item { display: flex !important; gap: 8px !important; margin-bottom: 2px !important; padding: 8px 8px 8px 6px !important; border-bottom: none !important; align-items: flex-start !important; border-radius: 6px !important; }",
        "  `;",
  "  if(document.head) document.head.appendChild(darkStyle);",
  "  else document.documentElement.appendChild(darkStyle);",
  "})();",
  "</script>",
  "<script>",
  "function applyDarkBeautify() {",
  "  var parent = document.querySelector('.max-w-4xl');",
  "  if (!parent) return;",
  "  document.body.classList.add('is-beautified');",
  "  function createElementWithStyle(tag,style,content){",
  "     var el=document.createElement(tag); if(content)el.innerHTML=content;",
  "     for(var k in style)el.style[k]=style[k]; return el;",
  "  }",
  "  var wrapContainer = document.getElementById('beautify-wrap');",
  "  if (!wrapContainer) {",
  "      wrapContainer = createElementWithStyle('div',{width:'100%',maxWidth:'96%',margin:'0 auto',background:'transparent',minHeight:'100vh',borderRadius:'16px',boxShadow:'none',padding:'60px 8px 30px 8px',boxSizing:'border-box',position:'relative',zIndex:1});",
  "      wrapContainer.id = 'beautify-wrap';",
  "      var children=Array.from(document.body.children);",
  "      for(var i=0;i<children.length;i++){",
  "        var el=children[i];",
  "        if(el.tagName!=='SCRIPT'&&el.tagName!=='STYLE'&&el.id!=='fullscreenOverlay') wrapContainer.appendChild(el);",
  "      }",
  "      document.body.appendChild(wrapContainer);",
  "      document.body.style.backgroundColor='#121212';",
  "      document.body.style.overflowX='hidden';",
  "      document.body.style.paddingTop='0';",
  "  }",
  "  var title = document.getElementById('title');",
  "  var list = document.getElementById('commentList');",
  "  var stickyWrapper = document.getElementById('stickyWrapper');",
  "  var count = document.getElementById('commentCount');",
  "  var err = document.getElementById('errorAlert');",
  "  var loading = document.getElementById('loading');",
  "  // 如果原代码已经乖乖待在我们建好的黑框里，就不重复处理",
  "  if (title && title.parentElement && title.parentElement.classList.contains('content-card')) { return; }",
  "",
  "  var contentCard = createElementWithStyle('div'); ",
  "  contentCard.className = 'content-card';",
  "  if (title) { title.style.display = 'block'; contentCard.appendChild(title); }",
  "  if (count) contentCard.appendChild(count);",
  "  var commentCard = createElementWithStyle('div'); commentCard.className = 'comment-card';",
  "  if (err) commentCard.appendChild(err);",
  "  if (list) commentCard.appendChild(list);",
  "  if (loading) commentCard.appendChild(loading);",
  "  ",
  "  while (parent.firstChild) parent.removeChild(parent.firstChild);",
  "  parent.style.overflow = 'visible';",
  "  parent.appendChild(contentCard);",
  "  if (stickyWrapper) {",
  "      stickyWrapper.style.position = 'sticky';",
  "      stickyWrapper.style.top = '50px';",
  "      stickyWrapper.style.zIndex = '90';",
  "      stickyWrapper.style.padding = '6px 12px 0 12px';",
  "      stickyWrapper.style.margin = '0 -4px 6px -4px';",
  "      stickyWrapper.style.backgroundColor = '#121212';",
  "      parent.appendChild(stickyWrapper);",
  "  }",
  "  parent.appendChild(commentCard);",
  "",
  "  var topBar = document.getElementById('dark-top-bar');",
  "  if (!topBar) {",
  "      topBar = createElementWithStyle('div', {",
  "        position:'fixed', top:'0', left:'0', width:'100%', height:'50px',",
  "        display:'flex', alignItems:'center', justifyContent:'space-between',",
  "        padding:'0 18px', boxSizing:'border-box', zIndex:9999,",
  "        background:'rgba(28,28,30,0.98)', borderBottom:'1px solid #2c2c2e'",
  "      });",
  "      topBar.className = 'top-bar';",
  "      topBar.id = 'dark-top-bar';",
  "      var cmtCountTop = createElementWithStyle('span', {fontSize:'13px', color:'#9ca3af', fontWeight:'500'}, '💬 加载中...');",
  "      cmtCountTop.id = 'cmtCountTop';",
  "      var titleEl = createElementWithStyle('span', {fontSize:'16px', fontWeight:600, color:'#e5e7eb'}, '◎ 段评详情');",
  "      titleEl.id = 'titleEl';",
  "      var topRightWrapper = createElementWithStyle('div', {display:'flex', alignItems:'center', gap:'10px'});",
  "      topRightWrapper.appendChild(titleEl); topBar.appendChild(cmtCountTop); topBar.appendChild(topRightWrapper);",
  "      document.body.appendChild(topBar);",
  "      function syncCommentCount() {",
  "        var e = document.getElementById('commentCount');",
  "        if (!e || !e.textContent.trim()) return;",
  "        cmtCountTop.textContent = '💬 ' + e.textContent.trim();",
  "      }",
  "      syncCommentCount();",
  "      var syncTimer = null;",
  "      function debouncedSync() {",
  "        if (syncTimer) clearTimeout(syncTimer);",
  "        syncTimer = setTimeout(syncCommentCount, 300);",
  "      }",
  "      if (typeof MutationObserver !== 'undefined') {",
  "        new MutationObserver(debouncedSync).observe(document.body, { characterData: true, childList: true, subtree: true });",
  "      }",
  "      window._updateTopBar = function(txt) { if (cmtCountTop) cmtCountTop.textContent = '💬 ' + txt; };",
  "  }",
  "}",
  "",
  "applyDarkBeautify();",
  "/*  核心二：永不罢工的 DOM 擒拿手！只要App敢异步重新注入带有白底框的 #title，立刻触发重新排版将它按回去！ */",
  "if (typeof MutationObserver !== 'undefined') {",
  "    var domObserver = new MutationObserver(function(mutations) {",
  "        for(var i=0; i<mutations.length; i++) {",
  "            if (mutations[i].addedNodes.length > 0) applyDarkBeautify();",
  "        }",
  "    });",
  "    domObserver.observe(document.body, { childList: true, subtree: true });",
  "}",
  "</script>"
 ];
 return html.join("\n");
}
function getConfig() {
    var _map = this.source ? this.source.getLoginInfoMap() : (_qdS ? _qdS.getLoginInfoMap() : null);
    var v = _map && (_map.get ? _map.get("◎ 半屏高度") : _map["◎ 半屏高度"]);
    var ratio = 0.8;
    if (v != null && String(v).trim() !== "") {
        var parsed = Number(v);
        if (!isNaN(parsed)) {
            if (parsed > 1 && parsed <= 100) {
                ratio = parsed / 100;
            } else if (parsed > 0 && parsed <= 1) {
                ratio = parsed;
            }
        }
    }
    return {
        state: 4,
        isHideable: true,
        heightPercentage: ratio,
        expandedCornersRadius: 20,
        dismissOnTouchOutside: true,
        widthPercentage: 1.0,
        maxWidth: -1,
        isGestureInsetBottomIgnored: true,
        skipCollapsed: true,
        hardwareAccelerated: true,
        webViewInitialScale: 100
    };
}



function showCmt(bid, cid, para, time, cmtType) {
    var j = null;
    var s = null;
    var c = null;

    try { j = this && this.java ? this.java : null; } catch(e) {}
    try { s = this && this.source ? this.source : null; } catch(e) {}
    try { c = this && this.cache ? this.cache : null; } catch(e) {}

    if (!j && typeof java !== "undefined") j = java;
    if (!c && typeof cache !== "undefined") c = cache;
    if (!s && typeof source !== "undefined") s = source;

    _qdJ = j;
    _qdC = c;
    _qdS = s;

    if (!j) return;

cmtType = cmtType || "dp";

var url = "https://druidv6.if.qidian.com/paragraph-comment?bookId=" +
    encodeURIComponent(bid) +
    "&chapterId=" + encodeURIComponent(cid) +
    "&paragraphId=" + encodeURIComponent(para || "") +
    "&type=" + encodeURIComponent(cmtType);

    var html = getBuiltInCommentHtml();

    var dpTheme = "";
    try {
        var m = s && s.getLoginInfoMap ? s.getLoginInfoMap() : null;
        var v = m && (m.get ? m.get("◎ 段评美化") : m["◎ 段评美化"]);
        if (v) dpTheme = String(v);
    } catch(e) {}

    if (dpTheme.indexOf("美化") !== -1) {
        html += getBuiltInBeautifyHtml();
    } else if (dpTheme.indexOf("深色") !== -1) {
        var darkPrimer = "<script>document.documentElement.classList.add('qd-dark-preload');document.addEventListener('DOMContentLoaded',function(){document.body.classList.add('qd-dark-preload');});</script><style>:root{--bg-primary:#121212;--text-primary:#e5e7eb;--text-secondary:#dddddd;--text-tertiary:#9ca3af;--border-color:#2c2c2e;--reply-panel-bg:#18181a;}html,body{background:#121212!important;color:#e5e7eb!important;}*{color:#e5e7eb!important;}</style>";
        html = darkPrimer + html + getBuiltInDarkHtml();
    }

    var bName = "";
    var cName = "";
    try {
        bName = this.book && this.book.name ? String(this.book.name) : "";
        cName = this.chapter && this.chapter.title ? String(this.chapter.title) : "";
    } catch(e) {}

    bName = bName.replace(/(['"\\])/g, "\\$1").replace(/\n|\r/g, "");
    cName = cName.replace(/(['"\\])/g, "\\$1").replace(/\n|\r/g, "");

    var apiBase = qdApiBase();

var preloadJs =
    "window.java=java;" +
    "window.cache=cache;" +
    "window.qdApiBase='" + apiBase + "';" +
    "window.bookName='" + bName + "';" +
    "window.chapterName='" + cName + "';" +
    "window.qdBid='" + encodeURIComponent(bid) + "';" +
    "window.qdCid='" + encodeURIComponent(cid) + "';" +
    "window.qdPara='" + encodeURIComponent(para || "") + "';" +
    "window.qdCmtType='" + String(cmtType || "dp").replace(/(['\"\\])/g, "\\$1") + "';";

j.showBrowser(url, html, preloadJs, JSON.stringify(getConfig.call(this)));
}
