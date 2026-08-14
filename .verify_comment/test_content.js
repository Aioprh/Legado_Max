const fs = require('fs');
const src = JSON.parse(fs.readFileSync('/workspace/qd_booksource.json','utf8'))[0];
const jsCode = src.ruleContent.content.replace(/^@js:/,'');

const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=1046465651' };
const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=1046465651&chapter_id=863897429' };

// 使用 curl 抓到的真实接口返回
const realContent = JSON.stringify({Blocks:[],Content:"　　“最近新余这边出了两起了。\r\n　　“大伯准备搬进内城了？”林辉出声。\r\n　　林红珍微微点头。"});

function mockAjax(u){
  if(u.includes('action=me')) return JSON.stringify({request_token:'T'});
  if(u.includes('action=summary')) return JSON.stringify({Data:{Getparagraphscommentcounts:{DataList:[]}}});
  if(u.includes('action=paragraph')) return JSON.stringify({Data:{DataList:[]}});
  if(u.includes('action=chapter')) return JSON.stringify({Data:{DataList:[]}});
  return realContent;
}
const java = {
  ajax: mockAjax,
  get:(u)=>({body:()=>mockAjax(u)}),
  base64Encode:(s)=>Buffer.from(s,'utf8').toString('base64'),
  startBrowser:()=>{}
};

const fn = new Function('book','chapter','java', jsCode + '\nreturn value;');
const value = fn(book, chapter, java);
console.log('=== 原始规则输出 value ===');
console.log(JSON.stringify(value));
console.log('\n=== 打印显示 ===');
console.log(value);
console.log('\n=== 是否含 \\r :', value.includes('\r'));
console.log('=== 是否含 \\u 转义序列:', /\\u[0-9a-fA-F]{4}/.test(value));