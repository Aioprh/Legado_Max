const fs = require('fs');
const src = JSON.parse(fs.readFileSync('/workspace/qd_booksource.json','utf8'))[0];
const jsCode = src.ruleContent.content.replace(/^@js:/,'');
const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=1046465651' };
const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=1046465651&chapter_id=863897429' };

const realContent = JSON.stringify({Blocks:[],Content:"　　第一段正文。\r\n　　第二段正文。\r\n　　第三段正文。"});
function mockAjax(u){
  if(u.includes('action=me')) return JSON.stringify({request_token:'T'});
  if(u.includes('action=summary')) return JSON.stringify({Data:{Getparagraphscommentcounts:{DataList:[{ParagraphId:1,CommentCount:3}]}}});
  if(u.includes('action=paragraph')) return JSON.stringify({Data:{DataList:[{Content:'好',UserName:'张三'}]}});
  if(u.includes('action=chapter')) return JSON.stringify({Data:{DataList:[{Content:'章评',UserName:'王五'}]}});
  return realContent;
}
const java={ajax:mockAjax,get:(u)=>({body:()=>mockAjax(u)}),base64Encode:(s)=>Buffer.from(s,'utf8').toString('base64'),startBrowser:()=>{}};
const fn=new Function('book','chapter','java', jsCode+'\nreturn value;');
const value=fn(book,chapter,java);
console.log(value);
console.log('\n=== 检查 ===');
console.log('含"第一段正文":', value.includes('第一段正文'));
console.log('含"第二段正文":', value.includes('第二段正文'));
console.log('含乱码 \\uXXXX:', /\\u[0-9a-fA-F]{4}/.test(value));
console.log('含 img 气泡:', value.includes('<img'));
console.log('含章评:', value.includes('章评'));
console.log('UTF8 中文占比正常:', (/[\u4e00-\u9fa5]/g.test(value)));