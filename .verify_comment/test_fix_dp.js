// 验证修复后的段评 clickJs：完整链路 第一层生成 clickJs -> opt JSON -> JSON.parse -> evalJS
// 用真实段评数据(含 [fn=] 表情、换行、HTML特殊字符) 验证段评正文正确显示
const fs = require('fs');

// ---- 真实段评数据 + 构造极端数据（fn/换行/HTML特殊字符）----
const real = JSON.parse(fs.readFileSync('/workspace/.verify_comment/real_dp.json', 'utf8'));
const realRows = real.Data.DataList.slice(0, 8);
realRows.push(
  { Content: '[fn=11][fn=33]', UserName: '表情测试' },          // 纯表情
  { Content: '结束生命对鱼生来说确实普通[fn=33]', UserName: '晓风微凉v' }, // 文字+表情
  { Content: '第一行\n第二行\n第三行', UserName: '换行测试' },   // 换行
  { Content: '含 <b>标签</b> 与 & 符号 "引号"', UserName: '特殊字符' }, // HTML特殊字符
  { Content: '', UserName: '空内容' },                           // 空内容（应被过滤）
);

// ---- 第2层：第一层 JS 生成的 clickJs（含修复后的 map 逻辑）----
// String.raw 保留 \u003E 与 \n 字面，模拟 clickJs 运行时值
const clickJs = String.raw`var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id=1041604040&chapter_id=805214738&paragraph_id=1&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(err){r=[]}var h='<html\u003E<head\u003E<meta charset=utf-8\u003E<style\u003Ebody{margin:0;padding:14px;font-family:sans-serif;font-size:14px}h3{margin:6px 0}.u{font-weight:bold;color:#333}.t{color:#555;margin:4px 0}.c{margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #eee}</style\u003E</head\u003E<body\u003E<h3\u003E段评</h3\u003E'+r.map(function(it){var tx=it.Content;if(!tx)tx=it.ImageMeaning||'';if(!tx)return '';var u=(it.UserName||'书友').split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;');tx=tx.split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;').split(String.fromCharCode(10)).join('<br\u003E');while(tx.indexOf('[fn=')>=0){var fa=tx.indexOf('[fn=');var fb=tx.indexOf(']',fa);if(fb<0)break;tx=tx.slice(0,fa)+'😀'+tx.slice(fb+1)}return '<div class=c\u003E<div class=u\u003E'+u+'</div\u003E<div class=t\u003E'+tx+'</div\u003E</div\u003E'}).join('')+(r.length?'':'<p\u003E暂无段评</p\u003E')+'</body\u003E</html\u003E';java.showBrowser('',h)`;

// ---- 检查 clickJs 是否含字面反斜杠转义（会导致 JSON.parse 失败）----
console.log('clickJs 含字面 > :', clickJs.includes('>'));
console.log('clickJs 含正则 \\[ (反斜杠+[):', /\\\[/.test(clickJs));
console.log('clickJs 含 \\n 字面:', clickJs.includes('\\n'));

// ---- opt JSON ----
const opt = '{"click":"' + clickJs + '"}';
let click;
try { click = JSON.parse(opt).click; }
catch (e) { console.log('RESULT: JSON.parse FAILED:', e.message); process.exit(1); }
console.log('RESULT: JSON.parse OK, click 含字面 > :', click.includes('>'));

// ---- 模拟 evalJS 执行 click ----
const java = {
  ajax: (u) => JSON.stringify({ Data: { DataList: realRows } }),
  showBrowser: (a, h) => { html = h; },
};
let html = null;
try {
  eval(click);
  console.log('RESULT: evalJS OK, showBrowser called:', !!html);
  console.log('\n===== 段评弹窗 HTML =====');
  console.log(html);
  console.log('===== 校验 =====');
  console.log('[fn=11] 残留:', html.includes('[fn=11]'));
  console.log('[fn=33] 残留:', html.includes('[fn=33]'));
  console.log('含 emoji 😀:', html.includes('😀'));
  console.log('含 <br> :', html.includes('<br>'));
  console.log('特殊字符 &lt; 转义:', html.includes('&lt;'));
  console.log('用户名正确转义:', html.includes('青萝御庭'));
  console.log('结尾标签正确:', html.endsWith('</html>'));
} catch (e) {
  console.log('RESULT: evalJS FAILED:', e.message);
  process.exit(1);
}