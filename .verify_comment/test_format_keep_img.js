// 验证 HtmlFormatter.formatKeepImg 对采用 \u003E 的 img src 是否安全
// formatImagePattern 4 个分支；getAbsoluteURL 对 data: 原样返回

function base64Encode(str) { return Buffer.from(str, 'utf8').toString('base64'); }

// 与 HtmlFormatter.kt 一致的 Java 正则（转 JS）
const notImgHtmlRegex = /<\/?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>/g;
const formatImagePattern = /<img[^>]*\ssrc\s*=\s*['"]([^'"{>]*\{(?:[^{}]|\{[^}>]+\})+\})['"][^>]*>|<img[^>]*\sdata-(?:src|original|srcset)\s*=\s*['"]([^'">]+)['"][^>]*>|<img[^>]*\ssrc\s*=\s*"([^">]+)"[^>]*>|<img[^>]*\s(?:data-[^=>]*|src)=\s*['"]([^'">]*)['"][^>]*>/gi;
const paramPattern = /,(\s*)(?=\{)/;

function getAbsoluteURL(baseURL, relativePath) {
  const rp = relativePath.trim();
  if (rp.startsWith('data:')) return rp; // isDataUrl
  return rp;
}

// format() 中的 notImgHtmlRegex 移除
function formatKeepImg(html) {
  let keepImgHtml = html.replace(notImgHtmlRegex, '');
  // 重建 img
  keepImgHtml = keepImgHtml.replace(formatImagePattern, (m, g1, g2, g3, g4) => {
    let param = '';
    let src = g1 || g2 || g3 || g4;
    const pm = paramPattern.exec(src);
    if (pm) {
      param = ',' + src.substring(pm.index + pm[0].length);
      src = src.substring(0, pm.index);
    }
    return '<img src="' + getAbsoluteURL(baseURL, src) + param + '">';
  });
  return keepImgHtml;
}

const cnt = 12;
const clickJs = "var para='12';var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id='+book.bookUrl.match(/book_id=([0-9]+)/)[1]+'&chapter_id='+chapter.url.match(/chapter_id=([0-9]+)/)[1]+'&paragraph_id='+para+'&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(e){}var h='<html\\u003E<head\\u003E<meta charset=utf-8\\u003E<style\\u003Ebody{margin:0;padding:14px}.u{font-weight:bold}.t{color:#555}.c{margin-bottom:12px}</style\\u003E</head\\u003E<body\\u003E<h3\\u003E段评</h3\\u003E';if(r.length){for(var i=0;i<r.length;i++){var it=r[i];var tx=it.Content||'';if(tx){h+='<div class=c\\u003E<div class=u\\u003E'+(it.UserName||'书友')+'</div\\u003E<div class=t\\u003E'+tx+'</div\\u003E</div\\u003E'}}}else{h+='<p\\u003E暂无段评</p\\u003E'}h+='</body\\u003E</html\\u003E';java.showBrowser(null,h)";
const opt = '{"click":"' + clickJs + '"}';
const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='60' height='24'><rect rx='12' width='60' height='24' fill='#e8f0fe' stroke='#4a6cf7'/><text x='30' y='16' font-size='12' text-anchor='middle' fill='#4a6cf7'>" + cnt + "</text></svg>";
const b64 = base64Encode(svg);
const img = '<img src="data:image/svg+xml;base64,' + b64 + ',' + opt + '" style="x">';
const content = '第12段正文。' + img;

const baseURL = 'https://full.hnxianxin.cn/qd/content.php?book_id=1&chapter_id=2';
const out = formatKeepImg(content);

console.log('--- formatKeepImg output ---');
const idx = out.indexOf('<img');
console.log('img tag after formatKeepImg:', out.substring(idx));

// 再跑 imgPattern 捕获
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+\})?)"[^>]*>/g;
imgPattern.lastIndex = 0;
const m = imgPattern.exec(out);
console.log('\nsrc captured endsWith }: ', m ? m[1].endsWith('}') : 'NO MATCH');
console.log('src has literal > : ', m ? m[1].includes('>') : 'n/a');
if (m && m[1].endsWith('}') && !m[1].includes('>')) {
  console.log('\nRESULT: formatKeepImg + imgPattern 均安全，src 完整保留, click 可解析');
} else {
  console.log('\nRESULT: FAILED');
}