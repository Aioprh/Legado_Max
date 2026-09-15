function splitArray(input, size) {
  const output = []
  for (let i = 0; i < input.length; i += size) {
    output.push(input.slice(i, i + size))
  }
  return output
}
function write_config(key, value) {
  if (Packages.android.text.TextUtils.isEmpty(this.source.getVariable())) {
    this.source.setVariable('{}')
  }
  // this.java.log('var:' + this.source.getVariable())
  let obj = JSON.parse(this.source.getVariable())
  let arr = key.split('.')
  let temp = obj

  for (let i = 0; i < arr.length - 1; i++) {
    let currentKey = arr[i]
    if (!temp[currentKey]) {
      temp[currentKey] = {}
    }
    temp = temp[currentKey]
  }
  temp[arr[arr.length - 1]] = value
  this.source.setVariable(JSON.stringify(obj))
}
function read_config(key) {
  if (Packages.android.text.TextUtils.isEmpty(this.source.getVariable())) {
    this.source.setVariable('{}')
  }
  // this.java.log('var:' + this.source.getVariable())
  let obj = JSON.parse(this.source.getVariable())
  let arr = key.split('.')
  let res = JSON.parse(JSON.stringify(obj))
  for (let i = 0; i < arr.length; i++) {
    res = res[arr[i]] || (i < (arr.length - 1) ? {} : undefined)
    // this.java.log(JSON.stringify(res))
  }
  return res
}
function getBookId(url, page) {
  const {java} = this
  let $ = JSON.parse(url).data
  let list
  let arr
  if ($.book_shelf_info != 0 && $.book_shelf_info != undefined) {
   arr = $.book_shelf_info.map($ => $.book_id)
  } else if (let (list = $.data_list) list != 0 && $.data_list != undefined)  {
    arr = $.data_list.map($ => $.book_id_str)
  } else {
    java.toast("获取 book_id 失败，你可能需要登录！")
  }
  return arr.slice(page*100, (page+1)*100)
}

function Map(e) {
    const { java, source, cookie, cache } = this;
    var infomap = source.getLoginInfoMap();
    var map = (infomap !== null && infomap.get(e) && String(infomap.get(e)).length > 0) ? infomap.get(e) : '';
    return String(map);
}
var toneMap = {
    0: "真人发音", 1: "甜美少女", 2: "清亮青叔", 4: "成熟大叔", 5: "开朗青年",
    6: "温柔淑女", 8: "风雅青叔", 12: "清纯少女", 17: "磁性青叔", 29: "儒雅大叔",
    30: "优雅御姐", 31: "斯文青叔", 32: "知性主播", 51: "多人对话", 74: "成熟升级",
    80: "多人升级", 100: "俏皮御姐", 103: "双音灵动"
};
function reMulti(str) {
    var parts = str.split(/(\d+)/);
    var others = [];
    for (var i = 1; i < parts.length; i += 2) {
        var n = parseInt(parts[i], 10);
        if (toneMap.hasOwnProperty(n)) {
            parts[i] = "[" + n + "]" + toneMap[n];
        } else {
            others.push(n);
            parts[i] = "";
        }
    }
    var ret = parts.join("").replace(/,+/g, ",").replace(/^,|,$/g, "");
    if (others.length) ret += "\n🎼其他音色：" + others.join(",");
    return ret;
}
function reSingle(n) {
    n = parseInt(n, 10);
    return toneMap.hasOwnProperty(n)
        ? "[" + n + "]" + toneMap[n]
        : "[" + n + "]";   
}

// ==================== 站点根路径段评（适配封装接口） ====================
var FQ_WRAPPER_CONFIG = { rootUrl: 'http://114.66.17.2:7894/', clickType: 'fq', cacheTTL: 8 * 60 * 1000, switchKey: 'fqShowPara' };

function fqRootUrl() {
  var ctx = this || {};
  var ctxSource = ctx.source || null;
  var values = [ctx.rootUrl];
  try { if (ctxSource) values.push(String(ctxSource.key || '')); } catch (eKey) {}
  try { if (ctxSource && ctxSource.getKey) values.push(String(ctxSource.getKey() || '')); } catch (eGetKey) {}
  try { if (ctxSource) values.push(String(ctxSource.bookSourceUrl || '')); } catch (eUrl) {}
  values.push(FQ_WRAPPER_CONFIG.rootUrl);
  for (var i = 0; i < values.length; i++) {
    var value = String(values[i] || '').replace(/^\s+|\s+$/g, '');
    if (value.indexOf('http://') === 0 || value.indexOf('https://') === 0) {
      return value.charAt(value.length - 1) === '/' ? value : value + '/';
    }
  }
  return '';
}

function fqWrapperSwitchOn(ctx, key) {
  try {
    var value = String((((ctx || {}).source) || {}).get(key) || '');
    return value !== '0' && value.indexOf('关闭') < 0;
  } catch (e) {
    return true;
  }
}

function fqWrapperParagraphEnabled(ctx) {
  return fqWrapperSwitchOn(ctx, FQ_WRAPPER_CONFIG.switchKey);
}

function fqWrapperIdeaSummary(ctx, bookId, itemId) {
  var bridge = (ctx || {}).java || null;
  var store = (ctx || {}).cache || null;
  var root = fqRootUrl.call(ctx);
  if (!bridge || !root || !bookId || !itemId) return {};
  var key = 'fqWrapperIdea:' + bookId + ':' + itemId;
  try {
    var cached = store ? store.getFromMemory(key) : '';
    if (cached) {
      var hit = JSON.parse(cached);
      if (hit && Date.now() - Number(hit.t || 0) < FQ_WRAPPER_CONFIG.cacheTTL) return hit.d || {};
    }
  } catch (eCache) {}
  try {
    var url = root + 'comment.php?action=counts&book_id=' + encodeURIComponent(String(bookId))
      + '&item_id=' + encodeURIComponent(String(itemId));
    var rootJson = JSON.parse(bridge.ajax(url));
    var ideas = (rootJson && rootJson.data && rootJson.data.idea_data) || {};
    var out = {};
    for (var k in ideas) {
      if (!Object.prototype.hasOwnProperty.call(ideas, k)) continue;
      var count = Number(ideas[k] && ideas[k].idea_count);
      if (count > 0) out[String(k)] = { count: count, hot: ideas[k].hot_tag === true };
    }
    try { if (store) store.putMemory(key, JSON.stringify({ t: Date.now(), d: out })); } catch (ePut) {}
    return out;
  } catch (e) {
    try { bridge.log('wrapper paragraph counts failed: ' + e); } catch (eLog) {}
    return {};
  }
}

function fqWrapperCleanText(text) {
  return String(text || '')
    .replace(/\uE000[^\uE001]*\uE001/g, '')
    .replace(/<img\b[\s\S]*?>/ig, '[图片]')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/^\s+|\s+$/g, '');
}

function fqWrapperNormalizeTitle(text) {
  return fqWrapperCleanText(text)
    .replace(/[\s\u00A0，。！？；：、·…“”‘’《》〈〉「」『』【】\[\]()（）{}｛｝< > , . ! ? ; : '"_-]/g, '')
    .replace(/^(章节名|章节标题|标题)/, '');
}

function fqWrapperTitleCore(text) {
  return String(text || '')
    .replace(/^第[0-9零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,12}[章节卷回]/, '')
    .replace(/^[0-9]{1,4}[.、-]/, '');
}

function fqWrapperIsTitle(text, chapter) {
  var norm = fqWrapperNormalizeTitle(text);
  if (!norm || norm.length > 60) return false;
  var title = fqWrapperNormalizeTitle((chapter || {}).title || (chapter || {}).name || (chapter || {}).chapterName || '');
  if (title && (norm === title || title.indexOf(norm) >= 0 || norm.indexOf(title) >= 0)) return true;
  var normCore = fqWrapperTitleCore(norm);
  var titleCore = fqWrapperTitleCore(title);
  if (normCore && titleCore && normCore.length >= 2 && titleCore.length >= 2 &&
      (normCore.indexOf(titleCore) >= 0 || titleCore.indexOf(normCore) >= 0)) return true;
  return /^(第[0-9零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,12}[章节卷回]|序章|楔子|引子|尾声|后记|番外)/.test(norm);
}

function fqWrapperExtractIdx(text) {
  var match = String(text || '').match(/<p\b[^>]*\bidx\s*=\s*"?(\d+)/i);
  return match ? match[1] : null;
}

function fqWrapperIsImageLine(text) {
  return /<img\b/i.test(String(text || '')) || /\uE000[^\uE001]*\uE001/.test(String(text || ''));
}

function fqWrapperMakeBubble(ctx, count, bookId, itemId, paraIndex, hot) {
  var bridge = (ctx || {}).java || null;
  if (!bridge || !bridge.base64Encode) return '';
  var display = Number(count) > 99 ? '99+' : String(Number(count) || 0);
  var key = String(paraIndex || '0');
  var font = 'system-ui,-apple-system,Arial,sans-serif';
  var svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="5 14 45 36" width="180" height="144">'
    + '<path d="M44 48 Q48 48 48 44 L48 20 Q48 16 44 16 L20 16 Q16 16 16 20 L16 24 S16 28 10 30 Q6 32 10 34 Q16 36 16 38 L16 44 Q16 48 20 48 Z" fill="none" stroke="#666666" stroke-width="0.7"/>'
    + '<text x="32" y="36.5" text-anchor="middle" font-family="' + font + '" font-weight="600" font-size="11" fill="#666666">' + display + '</text></svg>';
  var root = fqRootUrl.call(ctx);
  var options = {
    style: 'TEXT',
    type: FQ_WRAPPER_CONFIG.clickType,
    status: hot ? 'emphasis' : 'normal',
    click: 'showCmt(java,' + JSON.stringify(root) + ',' + JSON.stringify(String(bookId)) + ',' + JSON.stringify(String(itemId)) + ',' + JSON.stringify(key) + ')',
    marker: 'fqWrapper:' + bookId + ':' + itemId + ':' + key
  };
  return '<img src="data:' + 'image/svg+xml;base64,' + bridge.base64Encode(svg) + ',' + JSON.stringify(options) + '">';
}

function showCmt(bridge, rootUrl, bookId, itemId, paraIndex) {
  if (!bridge || !rootUrl) return;
  var root = String(rootUrl).charAt(String(rootUrl).length - 1) === '/' ? String(rootUrl) : String(rootUrl) + '/';
  var mode = paraIndex === 'chapter' || paraIndex === 'author' ? String(paraIndex) : '';
  var url = root + 'comments.html?book_id=' + encodeURIComponent(String(bookId || ''))
    + '&item_id=' + encodeURIComponent(String(itemId || ''))
    + (mode ? '&mode=' + encodeURIComponent(mode) : '&para_index=' + encodeURIComponent(String(paraIndex || '0')));
  bridge.startBrowser(url, mode === 'chapter' ? '章评' : mode === 'author' ? '作家说' : '段评');
}

function showChapterCmt(bridge, rootUrl, bookId, itemId) {
  showCmt(bridge, rootUrl, bookId, itemId, 'chapter');
}

function showAuthorCmt(bridge, rootUrl, bookId, itemId) {
  showCmt(bridge, rootUrl, bookId, itemId, 'author');
}

function fqWrapperXmlEsc(text) {
  return String(text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fqWrapperClip(text, max) {
  text = String(text || '').replace(/\s+/g, ' ');
  return text.length > max ? text.slice(0, max) + '…' : text;
}

function fqWrapperReadTail(ctx, bookId, itemId, needAuthor, needChapter) {
  var bridge = (ctx || {}).java || null;
  var store = (ctx || {}).cache || null;
  var root = fqRootUrl.call(ctx);
  if (!bridge || !root) return { author: null, cc: null };
  needAuthor = needAuthor !== false;
  needChapter = needChapter !== false;
  var key = 'fqWrapperTail:' + bookId + ':' + itemId + ':' + (needAuthor ? '1' : '0') + ':' + (needChapter ? '1' : '0');
  try {
    var hit = JSON.parse(String(store ? store.getFromMemory(key) : '') || '');
    if (hit && hit.d && Date.now() - Number(hit.t || 0) < FQ_WRAPPER_CONFIG.cacheTTL) return hit.d;
  } catch (eCache) {}
  try {
    var url = root + 'comment.php?action=tail&book_id=' + encodeURIComponent(String(bookId)) + '&item_id=' + encodeURIComponent(String(itemId));
    var rootJson = JSON.parse(bridge.ajax(url));
    var data = (rootJson && rootJson.data) || {};
    var out = { author: null, cc: null };
    var a = data.author || null;
    if (needAuthor && a && a.tid) {
      out.author = {
        tid: String(a.tid), text: String(a.text || ''), cover: String(a.cover || ''),
        name: String(a.name || '作者'), digg: Number(a.digg || 0), cnt: Number(a.count || 0)
      };
    }
    var c = data.chapter || data.cc || null;
    if (needChapter && c) {
      var rawPreviews = c.previews || c.pv || [];
      var previews = [];
      for (var i = 0; i < rawPreviews.length && previews.length < 2; i++) {
        var item = rawPreviews[i] || {};
        var text = String(item.text || item.t || '').replace(/\s+/g, ' ');
        if (!text) continue;
        previews.push({ n: String(item.name || item.n || '书友'), t: text, d: Number(item.likes || item.d || 0) });
      }
      var count = Number(c.count || c.n || 0);
      if (!count) count = rawPreviews.length;
      if (count > 0) out.cc = { n: count, pv: previews };
    }
    try { if (store) store.putMemory(key, JSON.stringify({ t: Date.now(), d: out })); } catch (ePut) {}
    return out;
  } catch (e) {
    try { bridge.log('wrapper tail failed: ' + e); } catch (eLog) {}
    return { author: null, cc: null };
  }
}

function fqWrapperSvgEsc(text) {
  return String(text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

function fqWrapperSvgClip(text, max) {
  text = String(text || '');
  if (text.length <= max) return text;
  text = text.slice(0, max);
  var code = text.charCodeAt(text.length - 1);
  if (code >= 0xd800 && code <= 0xdbff) text = text.slice(0, -1);
  return text + '…';
}

function fqWrapperSvgWrap(text, maxCols, maxLines) {
  var plainText = String(text || '').replace(/\r/g, '').replace(/\s+/g, ' ');
  var lines = [], line = '', width = 0;
  for (var i = 0; i < plainText.length; i++) {
    var char = plainText.charAt(i);
    var code = plainText.charCodeAt(i);
    if (code >= 0xd800 && code <= 0xdbff && i + 1 < plainText.length) { char += plainText.charAt(++i); }
    var charWidth = code < 0x2e80 ? 0.55 : 1;
    if (width + charWidth > maxCols && line) {
      lines.push(line); line = ''; width = 0;
      if (lines.length >= maxLines) break;
    }
    line += char; width += charWidth;
  }
  if (line || !lines.length) lines.push(line);
  if (lines.length > maxLines) {
    lines = lines.slice(0, maxLines);
    lines[maxLines - 1] = fqWrapperSvgClip(lines[maxLines - 1], Math.max(1, lines[maxLines - 1].length - 1)) + '…';
  }
  return lines;
}

function fqWrapperCardImage(ctx, inner, width, height, radius, click, marker) {
  var bridge = (ctx || {}).java || null;
  if (!bridge || !bridge.base64Encode) return '';
  var svg = '<svg width="' + width + '" height="' + height + '" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">'
    + '<rect x="2" y="2" width="' + (width - 4) + '" height="' + (height - 4) + '" fill="rgba(255,255,255,0.25)" rx="' + radius + '" stroke="#888" stroke-width="1"/>'
    + inner + '</svg>';
  var options = { style: 'FULL', click: click, marker: marker };
  return '<img src="data:' + 'image/svg+xml;base64,' + bridge.base64Encode(svg) + ',' + JSON.stringify(options) + '">';
}

function fqWrapperAuthorCard(ctx, author, bookId, itemId) {
  var width = 1080, left = 60, right = 60, fontSize = 38, lineHeight = 56, spacing = 40;
  var inner = '', y = 0;
  var tagX = width - 110;
  inner += '<path d="M ' + (tagX + 26) + ' 0 C ' + (tagX - 36) + ' 0 ' + (tagX - 60) + ' 1 ' + (tagX - 114) + ' -32 C ' + (tagX - 84) + ' -12 ' + (tagX - 68) + ' 6 ' + (tagX - 66) + ' 22 C ' + (tagX - 64) + ' 44 ' + (tagX - 54) + ' 56 ' + (tagX - 34) + ' 56 L ' + (tagX + 110) + ' 56 L ' + (tagX + 110) + ' 26 Q ' + (tagX + 110) + ' 0 ' + (tagX + 84) + ' 0 Z" fill="rgba(74,116,228,0.90)"/>'
    + '<text x="' + (tagX + 28) + '" y="40" font-size="34" fill="#fff" font-weight="bold" text-anchor="middle" font-family="Segoe UI,Arial,sans-serif">作家说</text>'
    + '<text x="' + left + '" y="70" font-family="Segoe UI,Arial,sans-serif" font-size="44" fill="#000">' + fqWrapperSvgEsc(fqWrapperSvgClip(author.name || '作者', 16)) + '</text>';
  y = 150;
  var lines = fqWrapperSvgWrap(author.text, Math.floor((width - left - right) / fontSize), 8);
  if (!lines.length) lines = ['点击查看作者说'];
  for (var i = 0; i < lines.length; i++) {
    inner += '<text x="' + left + '" y="' + y + '" font-family="Segoe UI,Arial,sans-serif" font-size="' + fontSize + '" fill="#333">' + fqWrapperSvgEsc(lines[i]) + '</text>';
    y += lineHeight;
  }
  y += spacing + 10;
  var root = JSON.stringify(fqRootUrl.call(ctx));
  return fqWrapperCardImage(ctx, inner, width, y, 60,
    'showAuthorCmt(java,' + root + ',' + JSON.stringify(String(bookId)) + ',' + JSON.stringify(String(itemId)) + ',' + JSON.stringify(String(author.tid)) + ')',
    'fqWrapperTail:' + bookId + ':' + itemId + ':author');
}

function fqWrapperChapterCard(ctx, chapter, bookId, itemId) {
  var width = 1080, left = 60, right = 60, nameSize = 42, textSize = 42, lineHeight = 60, titleHeight = 120, spacing = 35, iconSize = 50;
  var inner = '', y = 0;
  inner += '<text x="' + left + '" y="75" font-size="44" font-family="Segoe UI,Arial,sans-serif" fill="#000">本章说</text>'
    + '<text x="' + (width - right) + '" y="75" font-size="36" text-anchor="end" font-family="Segoe UI,Arial,sans-serif" fill="#000">' + (Number(chapter.n) || 0) + '条评论 ❯</text>';
  y += titleHeight;
  var previews = chapter.pv || [];
  var budget = 4;
  for (var j = 0; j < previews.length; j++) {
    var preview = previews[j] || {};
    var likes = Number(preview.d || 0);
    var maxLines = Math.max(1, Math.ceil(budget / Math.max(1, previews.length - j)));
    var lines = fqWrapperSvgWrap(preview.t || '', Math.floor((width - left - right) / textSize), maxLines);
    budget -= lines.length;
    var likeSize = 38, pad = 18, baseY = y + 40;
    var likeWidth = iconSize + String(likes).length * likeSize * 0.65 + pad * 2;
    var boxX = width - right - likeWidth, boxY = baseY - likeSize * 0.75 - pad / 2, boxH = likeSize + pad;
    inner += '<rect x="' + boxX + '" y="' + boxY + '" width="' + likeWidth + '" height="' + boxH + '" rx="25" fill="rgba(200,200,200,0.35)"/>'
      + '<text x="' + left + '" y="' + baseY + '" font-weight="bold" font-size="' + nameSize + '" font-family="Segoe UI,Arial,sans-serif" fill="#000">' + fqWrapperSvgEsc(fqWrapperSvgClip(preview.n || '书友', 14)) + '</text>'
      + '<text x="' + (boxX + pad + iconSize) + '" y="' + (boxY + boxH / 2 + likeSize * 0.35) + '" font-size="' + likeSize + '" font-family="Segoe UI,Arial,sans-serif" fill="#000">' + likes + '</text>';
    var textY = baseY + nameSize + spacing;
    for (var k = 0; k < lines.length; k++) {
      inner += '<text x="' + left + '" y="' + textY + '" font-size="' + textSize + '" font-family="Segoe UI,Arial,sans-serif" fill="#000">' + fqWrapperSvgEsc(lines[k]) + '</text>';
      textY += lineHeight;
    }
    y += nameSize + lineHeight * lines.length + spacing * 2.5;
  }
  y = Math.max(y, titleHeight + 20);
  var root = JSON.stringify(fqRootUrl.call(ctx));
  return fqWrapperCardImage(ctx, inner, width, y, 59,
    'showChapterCmt(java,' + root + ',' + JSON.stringify(String(bookId)) + ',' + JSON.stringify(String(itemId)) + ')',
    'fqWrapperTail:' + bookId + ':' + itemId + ':chapter');
}

function fqWrapperAppendTail(ctx, bookId, itemId, out) {
  var showAuthor = fqWrapperSwitchOn(ctx, 'fqShowAuthor');
  var showChapter = fqWrapperSwitchOn(ctx, 'fqShowChapter');
  if (!showAuthor && !showChapter) return;
  if (String((ctx || {}).result || '').indexOf('fqWrapperTail:' + bookId + ':' + itemId) >= 0) return;
  if (out.join('\n').indexOf('fqWrapperTail:' + bookId + ':' + itemId) >= 0) return;
  try {
    var tail = fqWrapperReadTail(ctx, bookId, itemId, showAuthor, showChapter) || {};
    if (showAuthor && tail.author && tail.author.tid) out.push(fqWrapperAuthorCard(ctx, tail.author, bookId, itemId));
    if (showChapter && tail.cc && Number(tail.cc.n) > 0) out.push(fqWrapperChapterCard(ctx, tail.cc, bookId, itemId));
    try { ((ctx || {}).java || {}).log('wrapper tail: author=' + (tail.author ? tail.author.cnt : 'none') + ' chapter=' + (tail.cc ? tail.cc.n : 0)); } catch (eLog) {}
  } catch (error) {
    try { ((ctx || {}).java || {}).log('wrapper tail card failed: ' + error); } catch (eLog) {}
  }
}

function fqProcessContent(rawContent) {
  var ctx = this || {};
  var bridge = ctx.java || null;
  var book = ctx.book || null;
  var chapter = ctx.chapter || null;
  if (!bridge) return String(rawContent || '');
  var bookId = String((book && book.getVariable && book.getVariable('book_id')) || '');
  if (!bookId && book && book.bookUrl) {
    var bookMatch = String(book.bookUrl).match(/bookid=(\d+)/);
    if (!bookMatch) bookMatch = String(book.bookUrl).match(/book_id=(\d+)/);
    if (bookMatch) bookId = bookMatch[1];
  }
  var itemId = String(ctx.itemId || (chapter && chapter.tag) || '');
  if (!itemId && chapter && chapter.url && bridge.base64Decode) {
    var chapterMatch = String(chapter.url).match(/base64,([A-Za-z0-9+\/=]+)/);
    if (chapterMatch) itemId = String(bridge.base64Decode(chapterMatch[1]));
  }
  var content = String(rawContent || '');
  if (!bookId || !itemId) return content;
  var summary = fqWrapperParagraphEnabled(ctx) ? fqWrapperIdeaSummary(ctx, bookId, itemId) : {};
  var keys = [];
  for (var name in summary) if (Object.prototype.hasOwnProperty.call(summary, name)) keys.push(name);
  if (!keys.length && !fqWrapperSwitchOn(ctx, 'fqShowChapter') && !fqWrapperSwitchOn(ctx, 'fqShowAuthor')) return content;

  var lines = content
    .replace(/\r/g, '')
    .replace(/<\/div>/ig, '\n')
    .replace(/<article\b[^>]*>/ig, '\n')
    .replace(/<p\b[^>]*>/ig, '\n')
    .replace(/<\/p>/ig, '\n')
    .split('\n');
  var out = [], textIndex = 0, imageIndex = 0, firstText = true;
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    if (!String(line).replace(/\s/g, '')) { out.push(line); continue; }
    if (!fqWrapperCleanText(line)) { out.push(line); continue; }
    if (firstText && fqWrapperIsTitle(line, chapter)) { firstText = false; out.push(line); continue; }
    firstText = false;
    if (fqWrapperIsImageLine(line)) {
      var imageKey = String(20000 + imageIndex++);
      var imageIdea = summary[imageKey];
      if (imageIdea && imageIdea.count > 0 && line.indexOf('fqWrapper:' + bookId + ':' + itemId + ':' + imageKey) < 0) {
        line += fqWrapperMakeBubble(ctx, imageIdea.count, bookId, itemId, imageKey, imageIdea.hot);
      }
      out.push(line);
      continue;
    }
    var idx = fqWrapperExtractIdx(line);
    var textKey = idx != null ? String(idx) : String(textIndex);
    var idea = summary[textKey];
    if (idea && idea.count > 0 && line.indexOf('fqWrapper:' + bookId + ':' + itemId + ':' + textKey) < 0) {
      line += fqWrapperMakeBubble(ctx, idea.count, bookId, itemId, textKey, idea.hot);
    }
    textIndex++;
    out.push(line);
  }
  fqWrapperAppendTail(ctx, bookId, itemId, out);
  try { bridge.log('wrapper paragraph bubbles=' + keys.length + ' paras=' + textIndex); } catch (eLog) {}
  return out.join('\n');
}