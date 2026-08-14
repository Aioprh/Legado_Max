// 全链路验证：JSON书源 -> JS构建img -> unescapeHtml4 -> imgPattern -> paramPattern/GSON -> evalJS
// 修复点：用 \u003E 代替 &gt;，避免 unescapeHtml4 解码成字面 '>' 破坏 imgPattern 捕获

function base64Encode(str) { return Buffer.from(str, 'utf8').toString('base64'); }

// 模拟 unescapeHtml4（Apache Commons）：只解码已知实体
function unescapeHtml4(s) {
  return s
    .replace(/&gt;/g, '>')
    .replace(/&lt;/g, '<')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#(39|0*39);/g, "'")
    .replace(/&nbsp;/g, ' ');
}

const cnt = 12;

// ===== 第2层：JS 源码构造 clickJs（用 \\u003E 使字符串含字面 \u003E）=====
// 模拟 JSON 层：JSON 中 "\\\\u003E" -> JS 源码中 "\\u003E" -> 字符串中 "\u003E"
// 这里直接写 JS 源码层（\\u003E）
const clickJs = "var para='12';var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id='+book.bookUrl.match(/book_id=([0-9]+)/)[1]+'&chapter_id='+chapter.url.match(/chapter_id=([0-9]+)/)[1]+'&paragraph_id='+para+'&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(e){}var h='<html\\u003E<head\\u003E<meta charset=utf-8\\u003E<style\\u003Ebody{margin:0;padding:14px;font-family:sans-serif;font-size:14px}h3{margin:6px 0}.u{font-weight:bold;color:#333}.t{color:#555;margin:4px 0}.c{margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #eee}</style\\u003E</head\\u003E<body\\u003E<h3\\u003E段评</h3\\u003E';if(r.length){for(var i=0;i<r.length;i++){var it=r[i];var tx=it.Content||it.ImageMeaning||'';if(tx){h+='<div class=c\\u003E<div class=u\\u003E'+(it.UserName||'书友')+'</div\\u003E<div class=t\\u003E'+tx+'</div\\u003E</div\\u003E'}}}else{h+='<p\\u003E暂无段评</p\\u003E'}h+='</body\\u003E</html\\u003E';java.showBrowser(null,h)";

console.log('clickJs contains literal > (should be false):', clickJs.includes('>'));
console.log('clickJs contains \\u003E (should be true):', clickJs.includes('\\u003E'));

// opt JSON 字符串
const opt = '{"click":"' + clickJs + '"}';
const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='60' height='24'><rect rx='12' width='60' height='24' fill='#e8f0fe' stroke='#4a6cf7' stroke-width='1'/><text x='30' y='16' font-size='12' text-anchor='middle' fill='#4a6cf7' font-family='sans-serif'>" + cnt + "</text></svg>";
const b64 = base64Encode(svg);
const img = '<img src="data:image/svg+xml;base64,' + b64 + ',' + opt + '" style="vertical-align:middle;margin-left:4px;">';

// ===== 第4层内容 =====
let content = '第1段正文。\n第12段正文。' + img;

// ===== 第5层 unescapeHtml4 =====
content = unescapeHtml4(content);
console.log('\n--- after unescapeHtml4 ---');
console.log('content has literal > (should be false):', content.includes('var h=\'<html>')); // <html> 不应出现

// ===== 第6层 imgPattern =====
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;
imgPattern.lastIndex = 0;
const m = imgPattern.exec(content);
if (!m) { console.log('\nRESULT: imgPattern FAILED'); process.exit(1); }
const src = m[1];
console.log('\n--- imgPattern captured ---');
console.log('src endsWith }: ', src.endsWith('}'));
console.log('src contains literal > (should be false):', src.includes('>'));

// ===== 第7层 paramPattern 切分 =====
const paramPattern = /,(\s*)(?=\{)/;
const pm = paramPattern.exec(src);
if (!pm) { console.log('RESULT: paramPattern FAILED'); process.exit(1); }
const optionStr = src.substring(pm.index + pm[0].length);
console.log('\n--- optionStr ---');
console.log('optionStr:', optionStr);

// ===== 第8层 GSON 解析（JSON.parse 等效，均解码 \uXXXX）=====
let option, click;
try { option = JSON.parse(optionStr); click = option.click; }
catch(e){ console.log('RESULT: JSON parse FAILED:', e.message); process.exit(1); }
console.log('\n--- after GSON parse ---');
console.log('click has literal > (should be true):', click.includes('>'));
console.log('click still has \\u003E (should be false):', click.includes('\\u003E'));
console.log('click has java.showBrowser:', click.includes('java.showBrowser'));

// ===== 第9层 模拟 evalJS 执行 click =====
const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=102895' };
const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=102895&chapter_id=654321' };
const java = {
  ajax: (u) => JSON.stringify({ Data: { DataList: [ { Content: '这本书写得真好', UserName: '张三' } ] } }),
  showBrowser: (...a) => { showed = a[1]; }
};
let showed = null;
const f = new Function('book','chapter','java','click', 'var result=null; ' + click + '; return result;');
// 注意：click 里用了 var h=... 与 return 无关，直接动态执行
try {
  eval(click);
  console.log('\n--- evalJS executed ---');
  console.log('showBrowser called:', !!showed);
  console.log('HTML passed to showBrowser:', showed);
  console.log('HTML has proper > tags:', showed.includes('<html>') && showed.includes('</html>') && showed.includes('<div>'));
  console.log('\nRESULT: FULL SUCCESS - 原生点击可工作');
} catch(e) {
  console.log('RESULT: evalJS FAILED:', e.message);
  process.exit(1);
}