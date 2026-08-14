// 复现 AppPattern.imgPattern（Java 正则转 JS）检查 dp: 协议 src 能否被正确捕获
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;

// 模拟书源 JS 构建 body（点击脚本）。规则：
//  - 不用 \d，改用 [0-9]（避免反斜杠）
//  - 字符串只用单引号（避免双引号）
//  - HTML 里的 > 写成 \u003E（在书源字符串字面量里是 \\u003E，得到 \u003E）
function makeBody(paraNum) {
  return "var para='" + paraNum + "';var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id='+book.bookUrl.match(/book_id=([0-9]+)/)[1]+'&chapter_id='+chapter.url.match(/chapter_id=([0-9]+)/)[1]+'&paragraph_id='+para+'&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(e){}var h='<html\u003E<head\u003E<meta charset=utf-8\u003E<style\u003Ebody{margin:0;padding:14px;font-family:sans-serif;font-size:14px}h3{margin:6px 0}.u{font-weight:bold;color:#333}.t{color:#555;margin:4px 0}.c{margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #eee}</style\u003E</head\u003E<body\u003E<h3\u003E段评</h3\u003E';if(r.length){for(var i=0;i<r.length;i++){var it=r[i];var tx=it.Content||it.ImageMeaning||'';if(tx){h+='<div class=c\u003E<div class=u\u003E'+(it.UserName||'书友')+'</div\u003E<div class=t\u003E'+tx+'</div\u003E</div\u003E'}}}else{h+='<p\u003E暂无段评</p\u003E'}h+='</body\u003E</html\u003E';java.showBrowser(null,h)";
}

// 手动构建 option JSON（不用 JSON.stringify，避免把 \u003E 转义成 \\u003E）
function makeOptionJson(body) {
  return '{"click":"' + body + '","status":"emphasis"}';
}

const cnt = 12;
const body = makeBody(1);
const optJson = makeOptionJson(body);
const src = 'dp:' + cnt + ',' + optJson;
const img = '<img src="' + src + '">';

console.log('img     :', img.slice(0, 120) + '...');
console.log('含反斜杠:', body.includes('\\'));
console.log('含双引号:', body.includes('"'));
console.log('含原始>:', body.includes('>'));

// 1) imgPattern 捕获
const m = imgPattern.exec(img);
if (!m) {
  console.log('FAIL: imgPattern 未匹配');
  process.exit(1);
}
const captured = m[1];
console.log('\n捕获src :', captured.slice(0, 120) + '...');
console.log('捕获完整 :', captured === src);

// 2) 模拟 parseParagraphBubble：paramPattern 按 ,{ 切分
const prefix = 'dp:';
const payload = captured.substring(prefix.length).trim();
const optionIndex = payload.indexOf(',{');
const count = optionIndex >= 0 ? payload.substring(0, optionIndex) : payload;
const optionStr = optionIndex >= 0 ? payload.substring(optionIndex + 1) : null;
console.log('\ncount   :', count);

// 3) 模拟 GSON 解析 option（JSON.parse 会像 GSON 一样解码 \u003E -> >）
const opt = JSON.parse(optionStr);
console.log('status  :', opt.status);
console.log('click 开头:', opt.click.slice(0, 60));
console.log('click 含 >:', opt.click.includes('>'));
console.log('click 含 java.showBrowser:', opt.click.includes('java.showBrowser(null,h)'));
console.log('click 长度:', opt.click.length);

// 4) 校验 evalJS 后 HTML 字符串能还原（模拟 Rhino 解析 \u003E -> >）
// 提取 HTML 字符串字面量并手动解码 \u003E，验证无残留
const htmlLiteral = opt.click.match(/var h='([\s\S]*?)';/)[1];
const decoded = htmlLiteral.split('\\u003E').join('>');
console.log('\nHTML解码后是否含 <html>:', decoded.includes('<html>'));
console.log('HTML解码后是否含 </html>:', decoded.includes('</html>'));

// 5) 校验 imgPattern 是否会把带 > 的脚本搞坏（对比：若直接把 > 写进 src）
const badBody = 'var h=' + "'<html><head></head><body>测试</body></html>';java.showBrowser(null,h)";
const badSrc = 'dp:' + cnt + ',{"click":"' + badBody + '","status":"emphasis"}';
const badImg = '<img src="' + badSrc + '">';
imgPattern.lastIndex = 0;
const badM = imgPattern.exec(badImg);
console.log('\n[对照] 直接含>的src是否被截断:', badM ? (badM[1].includes('"status"') ? '完整?' : '截断! ' + badM[1].slice(0, 60)) : '未匹配');