// 全链路验证（含 formatKeepImg）：采用"无嵌套大括号 + \u003E"的扁平 click
// 目标：formatKeepImg / unescapeHtml4 / imgPattern / paramPattern+GSON / evalJS 全部通过

function base64Encode(str) { return Buffer.from(str, 'utf8').toString('base64'); }

const notImgHtmlRegex = /<\/?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>/g;
const formatImagePattern = /<img[^>]*\ssrc\s*=\s*['"]([^'"{>]*\{(?:[^{}]|\{[^}>]+\})+\})['"][^>]*>|<img[^>]*\sdata-(?:src|original|srcset)\s*=\s*['"]([^'">]+)['"][^>]*>|<img[^>]*\ssrc\s*=\s*"([^">]+)"[^>]*>|<img[^>]*\s(?:data-[^=>]*|src)=\s*['"]([^'">]*)['"][^>]*>/gi;
const paramPattern = /,(\s*)(?=\{)/;
function unescapeHtml4(s){return s.replace(/&gt;/g,'>').replace(/&lt;/g,'<').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&nbsp;/g,' ');}
function getAbsoluteURL(baseURL,rp){return rp.trim().startsWith('data:')?rp.trim():rp;}
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;

function formatKeepImg(html){
  let s = html.replace(notImgHtmlRegex,'');
  s = s.replace(formatImagePattern,(m,g1,g2,g3,g4)=>{
    let param=''; let src=g1||g2||g3||g4;
    const pm=paramPattern.exec(src);
    if(pm){param=','+src.substring(pm.index+pm[0].length); src=src.substring(0,pm.index);}
    return '<img src="'+getAbsoluteURL(null,src)+param+'">';
  });
  return s;
}

const cnt=12, bid='102895', cid='654321', para='12';
// 扁平 click：仅 try / catch(err){r=[]} / function(it){...} 单层大括号；HTML 的 > 用 \u003E；无字面 > 与 "；用单引号
const clickJs = "var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id="+bid+"&chapter_id="+cid+"&paragraph_id="+para+"&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(err){r=[]}var h='<html\\u003E<head\\u003E<meta charset=utf-8\\u003E<style\\u003Ebody{margin:0;padding:14px;font-family:sans-serif;font-size:14px}h3{margin:6px 0}.u{font-weight:bold;color:#333}.t{color:#555;margin:4px 0}.c{margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #eee}</style\\u003E</head\\u003E<body\\u003E<h3\\u003E段评</h3\\u003E'+r.map(function(it){var tx=it.Content||it.ImageMeaning||'';return tx?('<div class=c\\u003E<div class=u\\u003E'+(it.UserName||'书友')+'</div\\u003E<div class=t\\u003E'+tx+'</div\\u003E</div\\u003E'):''}).join('')+(r.length?'':'<p\\u003E暂无段评</p\\u003E')+'</body\\u003E</html\\u003E';java.showBrowser(null,h)";

console.log('clickJs nested braces? (should be false):', (clickJs.match(/\{/g)||[]).length > 3 || (clickJs.match(/\}/g)||[]).length > 3 + 2);
console.log('clickJs literal > (should be false):', clickJs.includes('>'));
console.log('clickJs literal " (should be false):', clickJs.includes('"'));
console.log('braces count {:', (clickJs.match(/\{/g)||[]).length, '} :', (clickJs.match(/\}/g)||[]).length);

const opt = '{"click":"'+clickJs+'"}';
const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='60' height='24'><rect rx='12' width='60' height='24' fill='#e8f0fe' stroke='#4a6cf7'/><text x='30' y='16' font-size='12' text-anchor='middle' fill='#4a6cf7'>"+cnt+"</text></svg>";
const b64 = base64Encode(svg);
const img = '<img src="data:image/svg+xml;base64,'+b64+','+opt+'" style="x">';
let content = '第12段正文。'+img;

// 1) formatKeepImg
content = formatKeepImg(content);
const i1 = content.indexOf('<img');
console.log('\n--- 1) formatKeepImg ---');
console.log('img:', content.substring(i1, i1+120));

// 2) unescapeHtml4
content = unescapeHtml4(content);
console.log('\n--- 2) unescapeHtml4 ---');
console.log('src has literal > after unescape (should be false in raw):', /<img[^>]*src="[^"]*>/.test(content));

// 3) imgPattern
imgPattern.lastIndex=0; const m=imgPattern.exec(content);
if(!m){console.log('\nRESULT: imgPattern FAILED');process.exit(1);}
const src=m[1];
console.log('\n--- 3) imgPattern ---');
console.log('src endsWith }: ', src.endsWith('}'));
console.log('src has literal >:', src.includes('>'));

// 4) paramPattern + JSON.parse(GSON)
const pm=paramPattern.exec(src);
const optionStr=src.substring(pm.index+pm[0].length);
let click; try{click=JSON.parse(optionStr).click;}catch(e){console.log('\nRESULT: JSON parse FAILED', e.message);process.exit(1);}
console.log('\n--- 4) GSON ---');
console.log('click has literal > (should be true):', click.includes('>'));
console.log('click has java.showBrowser:', click.includes('java.showBrowser'));

// 5) evalJS
let showed=null;
const java={ajax:()=>JSON.stringify({Data:{DataList:[{Content:'写得好',UserName:'张三'}]}}),showBrowser:(...a)=>{showed=a[1];}};
try{
  eval(click);
  console.log('\n--- 5) evalJS ---');
  console.log('showBrowser called:', !!showed);
  console.log('HTML:', showed);
  console.log('\nRESULT: FULL SUCCESS');
}catch(e){console.log('\nRESULT: evalJS FAILED:',e.message);process.exit(1);}