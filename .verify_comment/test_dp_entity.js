// 复现 QidianParagraphComment 注入的 dp: 气泡在「当前」解析逻辑下 click 丢失的问题，
// 并验证修复（unescapeJsonOption 增加 HTML 实体解码）后能恢复 pclick。

const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;

// ---------- 复刻 QidianParagraphComment.appendBubble ----------
function appendBubble(paragraph, bookId, chapterId, paragraphId, count) {
  const pclick = "java.showQidianParagraphComments('" + bookId + "','" + chapterId + "'," + paragraphId + ");";
  const option = '{&quot;pclick&quot;:&quot;' + pclick + '&quot;,&quot;status&quot;:&quot;normal&quot;,&quot;displayText&quot;:&quot;' + count + '&quot;}';
  return paragraph + '<img src="dp:' + count + ',' + option + '">';
}

// ---------- 现状 unescapeJsonOption（仅处理反斜杠转义）----------
function unescapeJsonOptionCurrent(raw) {
  return raw.replace(/\\"/g, '"').replace(/\\\\/g, '\\');
}

// ---------- 修复后 unescapeJsonOption（增加 HTML 实体解码）----------
function unescapeJsonOptionFixed(raw) {
  return raw
    .replace(/&quot;/gi, '"')
    .replace(/&#34;/g, '"')
    .replace(/&#x22;/gi, '"')
    .replace(/&apos;/gi, "'")
    .replace(/&#39;/g, "'")
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\');
}

// ---------- 复刻 TextChapterLayout.tryParseForcedBubbleSrcWithClick(dp:) ----------
function parseParagraphBubble(src, unescape) {
  const payload = src.substring('dp:'.length).trim();
  const optionIndex = payload.indexOf(',{');
  const count = optionIndex >= 0 ? payload.substring(0, optionIndex) : payload;
  let option = {};
  if (optionIndex >= 0) {
    const optionStr = payload.substring(optionIndex + 1);
    try { option = JSON.parse(optionStr); } catch (e) { option = {}; }
    if (!option || Object.keys(option).length === 0) {
      try { option = JSON.parse(unescape(optionStr)); } catch (e) { option = {}; }
    }
  }
  const pclick = option.pclick && option.pclick.trim() ? option.pclick : null;
  const click = option.click && option.click.trim() ? option.click : null;
  const clickScript = pclick || click || null;
  // 展示文本回退到 count（与 Kotlin 中 FORCED_BUBBLE_DISPLAY_PARAM_REGEX 不匹配、最终用 count.trim() 一致）
  const displayText = option.displayText || option.num || count.trim();
  return { count, displayText, clickScript };
}

const img = appendBubble('正文段落。', '1010868264', '402733549', 12, 12);
console.log('注入 img:', img);

imgPattern.lastIndex = 0;
const m = imgPattern.exec(img);
const src = m[1];
console.log('imgPattern 捕获 src:', src);
console.log('捕获是否含字面双引号:', /"/.test(src));

const before = parseParagraphBubble(src, unescapeJsonOptionCurrent);
const after = parseParagraphBubble(src, unescapeJsonOptionFixed);

console.log('\n[现状]   displayText =', before.displayText, '| clickScript =', before.clickScript);
console.log('[修复后] displayText =', after.displayText, '| clickScript =', after.clickScript);

const okBefore = before.clickScript !== null;
const okAfter = after.clickScript !== null && after.clickScript.includes("showQidianParagraphComments('1010868264','402733549',12)");

console.log('\n现状能否拿到 click :', okBefore, '（应为 false = 复现 bug）');
console.log('修复后能否拿到 click:', okAfter, '（应为 true）');

// 6) 全链路：修复后的 click -> 拼 bubble:// URL -> ContentTextView 取回 -> ReadBookActivity 正则
function buildBubbleUrl(displayText, status, clickScript) {
  const e = encodeURIComponent;
  const clickQuery = clickScript ? '&click=' + e(clickScript) : '';
  return 'bubble://paragraph?displayText=' + e(displayText) + '&num=' + e(displayText) + '&status=' + e(status) + clickQuery;
}
function clickScriptFromUrl(url) {
  const q = url.substring(url.indexOf('?') + 1);
  const kv = q.split('&').find((p) => p.split('=')[0].toLowerCase() === 'click');
  return kv ? decodeURIComponent(kv.substring(kv.indexOf('=') + 1)) : '';
}

const bubbleUrl = buildBubbleUrl(after.displayText, 'normal', after.clickScript);
const recoveredClick = clickScriptFromUrl(bubbleUrl);
const rx = /showQidianParagraphComments\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*(\d+)\s*\)/i;
const mm = recoveredClick.match(rx);
console.log('\n全链路 bubble URL:', bubbleUrl);
console.log('取回的 click   :', recoveredClick);
console.log('正则解析 book/chapter/para:', mm ? [mm[1], mm[2], mm[3]].join(' / ') : 'FAIL');

const okChain = !!mm && mm[1] === '1010868264' && mm[2] === '402733549' && mm[3] === '12';

if (!okBefore && okAfter && okChain) {
  console.log('\nRESULT: PASS —— 已复现“气泡显示但 click 丢失”，修复后全链路可点击');
  process.exit(0);
} else {
  console.log('\nRESULT: FAIL');
  process.exit(1);
}