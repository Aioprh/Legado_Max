// 修复 qd_booksource.json 的段评弹窗正文显示：
// 1) HTML 特殊字符转义  2) 换行转 <br>  3) [fn=NN] 表情标记转 emoji  4) 空内容过滤
const fs = require('fs');
const file = '/workspace/qd_booksource.json';
const arr = JSON.parse(fs.readFileSync(file, 'utf8'));
const source = arr[0];
const contentRule = source.ruleContent.content;

// 旧 map 片段（JS 源码层）
const start = "r.map(function(it){var tx=it.Content||it.ImageMeaning||''";
const end = "}).join('')";
const si = contentRule.indexOf(start);
if (si < 0) { console.error('未找到旧 map 片段'); process.exit(1); }
const ei = contentRule.indexOf(end, si);
if (ei < 0) { console.error('未找到 map 结束'); process.exit(1); }
const oldSeg = contentRule.slice(si, ei + end.length);

// 新 map 片段（JS 源码层，\\u003E 表示字面 \u003E）
const newSeg = `r.map(function(it){var tx=it.Content;if(!tx)tx=it.ImageMeaning||'';if(!tx)return '';var u=(it.UserName||'书友').split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;');tx=tx.split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;').split(String.fromCharCode(10)).join('<br\\u003E');while(tx.indexOf('[fn=')>=0){var fa=tx.indexOf('[fn=');var fb=tx.indexOf(']',fa);if(fb<0)break;tx=tx.slice(0,fa)+'😀'+tx.slice(fb+1)}return '<div class=c\\u003E<div class=u\\u003E'+u+'</div\\u003E<div class=t\\u003E'+tx+'</div\\u003E</div\\u003E'}).join('')`;

const newContent = contentRule.replace(oldSeg, newSeg);
if (newContent === contentRule) { console.error('替换无变化'); process.exit(1); }

source.ruleContent.content = newContent;
fs.writeFileSync(file, JSON.stringify(arr, null, 2), 'utf8');
console.log('OK 已修复段评弹窗正文显示逻辑');
console.log('改前长度:', contentRule.length, '改后长度:', newContent.length);