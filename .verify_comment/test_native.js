// 验证"纯书源(不改软件)原生点击"方案在 stock Legado 的 imgPattern/paramPattern 下能否正确切分
// 目标：<img src="data:image/svg+xml;base64,<b64>,{"click":"<脚本>"}" style="..."> 点击执行脚本

// AppPattern.imgPattern (Java 正则)
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;
// AnalyzeUrl.paramPattern
const paramPattern = /,(\s*)(?=\{)/;

function base64Encode(str) {
  return Buffer.from(str, 'utf8').toString('base64');
}

// 模拟书源 clickJs（全部单引号、HTML 用 &gt; 实体、无字面 >、无双引号）
const paraNum = 12;
const cnt = 12;
const clickJs = "var para='" + paraNum + "';var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id='+book.bookUrl.match(/book_id=([0-9]+)/)[1]+'&chapter_id='+chapter.url.match(/chapter_id=([0-9]+)/)[1]+'&paragraph_id='+para+'&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(e){}var h='<html&gt;<head&gt;<meta charset=utf-8&gt;...java.showBrowser(null,h)";

const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='60' height='24'><rect rx='12' width='60' height='24' fill='#e8f0fe' stroke='#4a6cf7' stroke-width='1'/><text x='30' y='16' font-size='12' text-anchor='middle' fill='#4a6cf7' font-family='sans-serif'>" + cnt + "</text></svg>";
const b64 = base64Encode(svg);
const opt = '{"click":"' + clickJs + '"}';
const img = '<img src="data:image/svg+xml;base64,' + b64 + ',' + opt + '" style="vertical-align:middle;margin-left:4px;">';

console.log('IMG len:', img.length);
console.log('IMG:', img);

imgPattern.lastIndex = 0;
const m = imgPattern.exec(img);
if (!m) { console.log('RESULT: imgPattern FAILED'); process.exit(1); }
const src = m[1];
console.log('\n--- imgPattern group1 captured ---');
console.log('src startsWith data:', src.startsWith('data:image/svg+xml;base64,'));
console.log('src endsWith }:', src.endsWith('}'));

// paramPattern 切分
const pm = paramPattern.exec(src);
if (!pm) { console.log('RESULT: paramPattern FAILED'); process.exit(1); }
const urlPart = src.substring(0, pm.index);
const optionStr = src.substring(pm.index + pm[0].length);
console.log('\n--- paramPattern split ---');
console.log('urlPart:', urlPart);
console.log('optionStr:', optionStr);

// 解析 option JSON
let option;
try { option = JSON.parse(optionStr); } catch(e){ console.log('JSON parse FAILED:', e.message); process.exit(1); }
console.log('\n--- option ---');
console.log('keys:', Object.keys(option));
const click = option['click'];
console.log('click length:', click ? click.length : 0);
console.log('click has &gt;:', click.includes('&gt;'));
console.log('click has java.showBrowser:', click.includes('java.showBrowser'));
console.log('click has double quote?', click.includes('"'));

console.log('\nRESULT: SUCCESS - 原生 img + option.click 可被正确提取');