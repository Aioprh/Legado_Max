// 模拟书源上下文
const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=102895' };
const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=102895&chapter_id=654321' };
const java = {
  ajax: (u) => {
    if (u.includes('action=me')) return '{"request_token":"tok123"}';
    if (u.includes('action=summary')) {
      return JSON.stringify({ Data: { Getparagraphscommentcounts: { DataList: [
        { ParagraphId: 1, CommentCount: 3 },
        { ParagraphId: 3, CommentCount: 5 },
      ] } } });
    }
    if (u.includes('action=chapter')) {
      return JSON.stringify({ Data: { DataList: [
        { UserName: '书友A', Content: '好文！' },
        { UserName: '书友B', Content: '追更', ImageMeaning: '' },
      ] } });
    }
    if (u.includes('action=paragraph')) {
      return JSON.stringify({ Data: { DataList: [
        { UserName: '张三', Content: '精彩' },
        { UserName: '李四', Content: '顶一下' },
      ] } });
    }
    return '{}';
  },
  get: () => ({ body: () => '{}' }),
  showBrowser: (...a) => { console.log('showBrowser called, args[1] length=', (a[1]||'').length); },
};

// 正文（两段，模拟站点 \r\n 分段）
let VALUE = '第一段内容。\r\n第二段内容。\r\n第三段内容。\r\n第四段内容。';

// ===== 下面是被测的正文规则 JS（核心部分）=====
var SHOW_DUANPING=true;var SHOW_ZHANGPING=true;
var bid='';try{bid=book.bookUrl.match(/book_id=(\d+)/)[1]}catch(e){}
var cid='';try{cid=chapter.url.match(/chapter_id=(\d+)/)[1]}catch(e){}
var token='';try{token=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/auth.php?action=me')).request_token||'';}catch(e){}
var url='https://full.hnxianxin.cn/qd/content.php?book_id='+bid+'&chapter_id='+cid;
var value='';
try{var o=JSON.parse(java.ajax(url));var c=o.content||o.Content||'';if(c.length>0){value=c.replace(/\r\n/g,'\n')}}catch(e){}
if(value.length===0){try{var headers={'User-Agent':'Mozilla/5.0','X-Content-Token':token};var o2=JSON.parse(java.get(url+'&vip=1',headers).body());var c2=(o2.content||o2.Content||'').replace(/\r\n/g,'\n');if(c2.length>0){value=c2}else if(o2.detail){value='【'+o2.detail+'，请在书源内点登录后重试】'}else{value=JSON.stringify(o2)}}catch(e){value='【正文获取失败：'+e+'】'}}
if(value.length>0&&SHOW_DUANPING){try{
  var s=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=summary&book_id='+bid+'&chapter_id='+cid));
  var rows=(s.Data&&s.Data.Getparagraphscommentcounts&&s.Data.Getparagraphscommentcounts.DataList)||[];
  if(rows.length){
    var paras=value.split('\n');
    rows.forEach(function(r){
      var idx=Number(r.ParagraphId)-1;
      var cnt=Number(r.CommentCount||r.TextCount||0);
      if(idx>=0&&idx<paras.length&&cnt>0){
        var paraNum=idx+1;
        var body="var para='"+paraNum+"';var r=[];try{var d=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=paragraph&book_id='+book.bookUrl.match(/book_id=(\d+)/)[1]+'&chapter_id='+chapter.url.match(/chapter_id=(\d+)/)[1]+'&paragraph_id='+para+'&type=text&page=1&page_size=20'));r=(d.Data&&d.Data.DataList)||[]}catch(e){}var h='<html><head><meta charset=utf-8><style>body{margin:0;padding:14px;font-family:sans-serif} h3{margin:6px 0} .u{font-weight:bold} .t{color:#555;margin:4px 0} .c{margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #eee}</style></head><body><h3>段评</h3>';if(r.length){for(var i=0;i<r.length;i++){var it=r[i];var tx=it.Content||it.ImageMeaning||'';if(tx){h+='<div class=c><div class=u>'+(it.UserName||'书友')+'</div><div class=t>'+tx+'</div></div>'}}}else{h+='<p style=color:#888>暂无段评</p>'}h+='</body></html>';java.showBrowser(null,h)";
        var opt={click:body,status:'emphasis'};
        var src='<img src="dp:'+cnt+','+JSON.stringify(opt)+'">';
        paras[idx]=paras[idx]+src;
      }
    });
    value=paras.join('\n');
  }
}catch(e){}}
if(value.length>0&&SHOW_ZHANGPING){try{
  var cm=JSON.parse(java.ajax('https://full.hnxianxin.cn/qd/comments.php?action=chapter&book_id='+bid+'&chapter_id='+cid+'&page=1&page_size=20'));
  var items=(cm.Data&&(cm.Data.DataList||[]))||[];
  if(items.length){value+='\n\n【章评】\n';items.forEach(function(it){var txt=it.Content||it.ImageMeaning||'';if(txt){value+=(it.UserName||'书友')+'：'+txt+'\n'}})}
}catch(e){}}
value;

// ===== 输出验证 =====
console.log('===== 最终正文 =====');
console.log(value);
console.log('===== 校验 =====');
const dpMatch = value.match(/dp:(\d+),(\{[^>]*?\})/g);
console.log('dp气泡数量:', (dpMatch||[]).length);
if (dpMatch) {
  dpMatch.forEach(m => {
    const j = JSON.parse(m.substring(m.indexOf(',')+1));
    console.log('dp src:', m);
    console.log('  status:', j.status);
    console.log('  click含java.showBrowser:', j.click.includes('java.showBrowser(null,h)'));
    console.log('  click含paragraph_id:', j.click.includes("paragraph_id='+para+"));
  });
}
console.log('章评追加:', value.includes('【章评】'));
console.log('章评用户名:', value.includes('书友A'));