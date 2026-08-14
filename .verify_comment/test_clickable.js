// 验证"纯书源实现可点击段评气泡"方案：
// 1) 生成 data:image/svg+xml;base64 气泡图 + {click:...} 选项的 <img> 标签
// 2) 验证 imgPattern / paramPattern 能正确切分 src 与 option
// 3) 验证 option 是合法 JSON（等价 GSON 解析），click 脚本可提取
// 4) 验证 click 脚本语法正确（用 new Function 粗验）

function base64Encode(str) {
  // 模拟 java.base64Encode(str, 2) 无换行
  return Buffer.from(str, 'utf8').toString('base64');
}

// ===== 模拟生成逻辑（与书源 ruleContent 中一致）=====
const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=102895' };
const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=102895&chapter_id=654321' };
const bid = '102895';
const cid = '654321';
const paraNum = 12;
const cnt = 12;

// 构建 click 脚本：
//  - 只含单引号、不含双引号
//  - HTML 中的 '>' 用实体 &gt; 代替，保证 src 中无字面 '>'（imgPattern 的 [^>]+ 无法跨越 '>'）
//  - WebView 渲染时会把 &gt; 还原为 '>'
var clickJs = "var para='" + paraNum + "';var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id='+book.bookUrl.match(/book_id=([0-9]+)/)[1]+'&chapter_id='+chapter.url.match(/chapter_id=([0-9]+)/)[1]+'&paragraph_id='+para+'&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(e){}var h='<html&gt;<head&gt;<meta charset=utf-8&gt;<style&gt;body{margin:0;padding:14px;font-family:sans-serif;font-size:14px}h3{margin:6px 0}.u{font-weight:bold;color:#333}.t{color:#555;margin:4px 0}.c{margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #eee}</style&gt;</head&gt;<body&gt;<h3&gt;段评</h3&gt;';if(r.length){for(var i=0;i<r.length;i++){var it=r[i];var tx=it.Content||it.ImageMeaning||'';if(tx){h+='<div class=c&gt;<div class=u&gt;'+(it.UserName||'书友')+'</div&gt;<div class=t&gt;'+tx+'</div&gt;</div&gt;'}}}else{h+='<p&gt;暂无段评</p&gt;'}h+='</body&gt;</html&gt;';java.showBrowser(null,h)";

// 构建 SVG 气泡
var svg = "<svg xmlns='http://www.w3.org/2000/svg' width='60' height='24'><rect rx='12' width='60' height='24' fill='#e8f0fe' stroke='#4a6cf7' stroke-width='1'/><text x='30' y='16' font-size='12' text-anchor='middle' fill='#4a6cf7' font-family='sans-serif'>" + cnt + "</text></svg>";
var b64 = base64Encode(svg);
var opt = '{"click":"' + clickJs + '"}';
var img = '<img src="data:image/svg+xml;base64,' + b64 + ',' + opt + '" style="vertical-align:middle;margin-left:4px;">';

console.log('=== 生成的 <img> 标签 ===');
console.log(img);
console.log('\n=== 断言 ===\n');

// 1) clickJs 不应含双引号或反斜杠（避免破坏 JSON / 规则转义）
let fail = 0;
if (clickJs.includes('"')) { console.log('FAIL clickJs 含双引号'); fail++; }
if (clickJs.includes('\\')) { console.log('FAIL clickJs 含反斜杠'); fail++; }

// 2) imgPattern（Java 正则转 JS）应捕获完整 src
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;
const m = imgPattern.exec(img);
if (!m) { console.log('FAIL 未匹配 imgPattern'); fail++; }
else {
  const src = m[1];
  console.log('imgPattern 捕获 src 长度:', src.length);
  console.log('src 尾部:', JSON.stringify(src.slice(-40)));
  const pm2 = /\s*,\s*(?=\{)/.exec(src);
  console.log('pm2.index:', pm2 ? pm2.index : 'null', 'pm2[0]:', pm2 ? JSON.stringify(pm2[0]) : '');
  // 3) paramPattern 切分 src 与 option
  const paramPattern = /\s*,\s*(?=\{)/;
  const pm = paramPattern.exec(src);
  if (!pm) { console.log('FAIL 未匹配 option 分隔'); fail++; }
  else {
    const urlNoOption = src.substring(0, pm.index);
    const optionStr = src.substring(pm.index + pm[0].length); // 等价 Java urlMatcher.end()
    console.log('urlNoOption:', urlNoOption.slice(0, 60) + '...');
    console.log('optionStr 长度:', optionStr.length);
    // 4) 解析 option JSON（等价 GSON）
    let parsed;
    try { parsed = JSON.parse(optionStr); } catch (e) { parsed = null; }
    if (!parsed || typeof parsed.click !== 'string') { console.log('FAIL option 非合法 JSON 或缺 click'); fail++; }
    else {
      console.log('option.click 长度:', parsed.click.length);
      // click 应包含 showBrowser 与 paragraph_id 参数
      if (!parsed.click.includes('java.showBrowser(null,h)')) { console.log('FAIL click 缺少 showBrowser'); fail++; }
      if (!parsed.click.includes("paragraph_id='+para+'")) { console.log('FAIL click 缺少 paragraph_id 拼接'); fail++; }
      if (!parsed.click.includes("var para='" + paraNum + "'")) { console.log('FAIL click 缺少正确 paraNum'); fail++; }
      // 5) click 脚本语法粗验
      try { new Function('java','book','chapter', parsed.click); console.log('OK click 脚本可解析为函数'); }
      catch (e) { console.log('FAIL click 脚本语法错误: ' + e.message); fail++; }
    }
  }
}

// 6) base64 不应含逗号/花括号/双引号（避免混淆 paramPattern 与 imgPattern）
if (/[,{}"]/.test(b64)) { console.log('FAIL base64 含特殊字符'); fail++; }

console.log(fail === 0 ? '\n全部通过 ✅' : '\n存在 ' + fail + ' 个失败 ❌');