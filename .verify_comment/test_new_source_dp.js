// 用用户提供的起点书源 ruleContent 实际模拟：
// 1) 执行 content @js: 规则生成正文（含 dp: 段评气泡）
// 2) 模拟 imgPattern 提取 src
// 3) 模拟 parseParagraphBubble（GSON）解析 option，验证 pclick 能否提取
const fs = require('fs');

const source = JSON.parse(fs.readFileSync('/workspace/qd_booksource_new.json', 'utf8'));
// 用户提供的书源：用实际粘贴的 content 规则
const contentRule = source.ruleContent.content;
console.log('规则前缀:', contentRule.slice(0, 60));

function runContentRule(rule) {
  const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=102895' };
  const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=102895&chapter_id=123456' };
  const java = {
    ajax: (u) => {
      if (u.includes('action=me')) return '{"request_token":"tok123","user":{"name":"u"}}';
      if (u.includes('action=summary')) return JSON.stringify({ Data: { Getparagraphscommentcounts: { DataList: [ { ParagraphId: 12, CommentCount: 12 } ] } } });
      if (u.includes('action=paragraph')) return JSON.stringify({ Data: { DataList: [ { Content: '这本书写得真好', UserName: '张三', Floor: 3, AgreeAmount: 5, CreateTime: 1700000000000 } ] } });
      if (u.includes('content.php')) return JSON.stringify({ Content: Array.from({length:20},(_,i)=>'第'+(i+1)+'段正文。').join('\n') });
      return '{}';
    },
    get: (u, h) => ({ body: () => JSON.stringify({ Content: 'vip正文\n' }) }),
  };
  const jsBody = rule.replace(/^@js:/, '');
  const fn = new Function('java', 'book', 'chapter', 'return (' + jsBody + ');');
  return fn(java, book, chapter);
}

let content;
try {
  content = runContentRule(contentRule);
} catch (e) {
  console.log('规则执行异常:', e.message);
  process.exit(1);
}
console.log('规则输出长度:', content.length);
console.log('含 <img:', content.includes('<img'));

// === 模拟 BookContent 管线：formatKeepImg + unescapeHtml4 ===
const notImgHtmlRegex = /<\/?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>/g;
const formatImagePattern = /<img[^>]*\ssrc\s*=\s*['"]([^'"{>]*\{(?:[^{}]|\{[^>]+})+})['"][^>]*>|<img[^>]*\sdata-(?:src|original|srcset)\s*=\s*['"]([^'">]+)['"][^>]*>|<img[^>]*\ssrc\s*=\s*"([^">]+)"[^>]*>|<img[^>]*\s(?:data-[^=>]*|src)=\s*['"]([^'">]*)['"][^>]*>/gi;
function unescapeHtml4(s) {
  return s.replace(/&gt;/g, '>').replace(/&lt;/g, '<').replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"').replace(/&#(39|0*39);/g, "'").replace(/&nbsp;/g, ' ');
}
content = content.replace(notImgHtmlRegex, '');
content = content.replace(formatImagePattern, (m, g1, g2, g3, g4) => {
  let param = '';
  let src = g1 || g2 || g3 || g4;
  const pm = /,(\s*)(?=\{)/.exec(src);
  if (pm) { param = ',' + src.substring(pm.index + pm[0].length); src = src.substring(0, pm.index); }
  return '<img src="' + src + param + '">';
});
if (content.indexOf('&') > -1) content = unescapeHtml4(content);
console.log('管线处理后含 <img:', content.includes('<img'));

// 提取第一个 img src
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;
imgPattern.lastIndex = 0;
let m = imgPattern.exec(content);
if (!m) { console.log('FAIL: imgPattern 未匹配（管线后）'); process.exit(1); }
const src = m[1];
console.log('\n=== imgPattern 捕获的 src（管线后）===');
console.log('src 长度:', src.length);
console.log('src 末尾 50 字符:', JSON.stringify(src.slice(-50)));
console.log('src 以 } 结尾:', src.endsWith('}'));
console.log('src 含 >:', src.includes('>'));

// 模拟 parseParagraphBubble
const payload = src.substring('dp:'.length).trim();
const optionIndex = payload.indexOf(',{');
console.log('\n=== parseParagraphBubble ===');
console.log('optionIndex:', optionIndex);
if (optionIndex < 0) { console.log('FAIL: 未找到 ,{'); process.exit(1); }
const count = payload.substring(0, optionIndex);
const optionStr = payload.substring(optionIndex + 1);
console.log('count:', count);
console.log('optionStr 长度:', optionStr.length);
console.log('optionStr 末尾 40:', JSON.stringify(optionStr.slice(-40)));

// 模拟 GSON.fromJsonObject（Gson 会解码 \" -> "）
let option = null;
try {
  // 需要先把 HTML 层反斜杠转义还原为 JSON 转义？
  // Gson 直接解析含 \" 的 JSON 字符串（Java 字符串里 \" 已是字面反斜杠+引号）
  // 这里 optionStr 是 JS 字符串，含字面反斜杠 \"
  option = JSON.parse(optionStr);
  console.log('GSON 解析成功, keys:', Object.keys(option));
} catch (e) {
  console.log('GSON 解析失败:', e.message);
  // 尝试兜底提取
  const re = /"([A-Za-z_][A-Za-z0-9_]*)":("(?:[^"\\]|\\.)*")/g;
  let mm, fallback = {};
  while ((mm = re.exec(optionStr))) fallback[mm[1]] = JSON.parse(mm[2]);
  option = fallback;
  console.log('兜底提取 keys:', Object.keys(option));
}
const pclick = option['pclick'] || option['click'];
console.log('pclick 提取:', !!pclick, '长度:', pclick ? pclick.length : 0);
if (pclick) {
  console.log('pclick 开头 60:', pclick.slice(0, 60));
  console.log('pclick 含 >:', pclick.includes('>'));
  console.log('pclick 含双引号:', pclick.includes('"'));
  console.log('pclick 含 showBrowser:', pclick.includes('java.showBrowser'));
  // 语法粗验
  try { new Function('java','book','chapter', pclick); console.log('pclick 语法 OK'); }
  catch (e) { console.log('pclick 语法错误:', e.message); }
  // 模拟点击执行：mock java.ajax 返回段评，执行 pclick，看 showBrowser 是否被调用
  let showed = null;
  const java2 = {
    ajax: (u) => JSON.stringify({ Data: { DataList: [ { Content: '这本书写得真好', UserName: '张三', Floor: 3, AgreeAmount: 5, CreateTime: 1700000000000 }, { Content: '第二句评论', UserName: '李四', Floor: 4, AgreeAmount: 2, CreateTime: 1700000001000 } ] } }),
    showBrowser: (...a) => { showed = a[1]; },
  };
  try {
    new Function('java','book','chapter', pclick)(java2, { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=102895' }, { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=102895&chapter_id=123456' });
    console.log('\n=== 点击执行 ===');
    console.log('showBrowser 被调用:', !!showed);
    if (showed) {
      console.log('弹窗内容(前80):', JSON.stringify(showed.slice(0, 80)));
      console.log('弹窗内容含换行:', showed.includes(String.fromCharCode(10)));
    }
  } catch (e) {
    console.log('点击执行异常:', e.message);
  }
}
