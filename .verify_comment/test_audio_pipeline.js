// 验证：起点语音段评接口 -> 合并进 majax 列表 -> 弹窗 parseComments 默认字段路径解析。
// 复刻 ParagraphCommentDialog 的 DEFAULT_* 取值顺序，确认语音条目能解析出可播放的 audioUrl。
const {API,hd,g}=require('./qd_signed.js');

// ParagraphCommentDialog.DEFAULT_* （按顺序，取第一个非空且非"null"的值）
const D={
 id:['$.Id','$.CommentId','$.ReviewId','$.Cid','$.comment_id','$.review_id','$.cid','$.id'],
 nickname:['$.NickName','$.UserName','$.Name','$.Uname','$.user_name','$.nickname','$.user_info.user_name'],
 avatar:['$.UserHeadIcon','$.UserPhoto','$.Avatar','$.HeadIcon','$.Photo','$.user_avatar','$.avatar','$.user_info.user_avatar'],
 ip:['$.IpLocation','$.IpAddress','$.Ip','$.Location','$.Region'],
 content:['$.Content','$.Text','$.Msg','$.ImageMeaning','$.text','$.content','$.body'],
 agree:['$.AgreeAmount','$.AgreeCount','$.LikeCount','$.LikeAmount','$.Up','$.digg_count'],
 time:['$.CreateTime','$.Time','$.CreatedAt','$.CreateDate','$.create_timestamp','$.timestamp'],
 floor:['$.Floor','$.FloorNum','$.FloorNumber','$.floor'],
 reply:['$.ReviewCount','$.RootReviewReplyCount','$.ReplyCount','$.ReplyNum','$.SubCount','$.reply_count'],
};
const AUDIO_FIELDS=['AudioUrl','VoiceUrl','Audio','Voice','SoundUrl','AudioRoleId','AudioTime','HotAudioStatus','audio_url','voice_url'];
// 与弹窗 resolvePath 一致：去掉开头 "$."，再按 "." 逐层取值
const read=(m,paths)=>{for(const p of paths){const seg=p.replace(/^\$\.?/,'').split('.');let v=m;for(const s of seg){if(v==null)break;v=v[s];}if(v!==undefined&&v!==null){const s=String(v).trim();if(s!==''&&s!=='null')return s;}}return '';};
const readAudio=m=>{for(const k of AUDIO_FIELDS){const v=m[k];if(v===undefined||v===null)continue;const s=String(v).trim();if(s===''||s==='null'||s==='0')continue;return s.startsWith('http')?s:'voice';}return '';};

async function audioOf(bookId,chapterId,pid){
 const p=`bookId=${bookId}&chapterId=${chapterId}&paragraphId=${pid}&pg=1&pz=100&roleId=0`;
 const r=await g(API+'v1/chapterreview/getparagraphsaudiocomments?'+p.split('&').sort().join('&'),hd(p));
 const j=JSON.parse(r.d);
 return {http:r.c,Result:j.Result,total:j.Data.TotalCount,list:j.Data.DataList||[]};
}

(async()=>{
 for(const [bk,ch,pid] of [['1041637443','805945430',1],['1010868264','402733549',1]]){
  const a=await audioOf(bk,ch,pid);
  // 与 Kotlin 一致：只保留带 AudioUrl 的条目，避免与 majax 文字段评重复
  const raw=a.list;
  a.list=raw.filter(x=>String(x.AudioUrl||'').startsWith('http'));
  console.log(`\n===== book ${bk} / chapter ${ch} / pid ${pid} =====`);
  console.log(`http=${a.http} Result=${a.Result} AudioCount=${a.total} 接口返回=${raw.length} 过滤后=${a.list.length} ${a.list.length==a.total?'(与AudioCount一致 ✓)':'(与AudioCount不一致 ✗)'}`);
  let playable=0;
  a.list.forEach((m,i)=>{
   const item={id:read(m,D.id),nickname:read(m,D.nickname),avatar:read(m,D.avatar),ip:read(m,D.ip),
    content:read(m,D.content),audio:readAudio(m),agree:read(m,D.agree),time:read(m,D.time),
    floor:read(m,D.floor),reply:read(m,D.reply)};
   item.audioUrl=item.audio.startsWith('http')?item.audio:'';
   const keep=item.content!==''||item.audio!=='';
   if(item.audioUrl)playable++;
   if(i<2)console.log(`  [${i}] 保留=${keep} 昵称=${item.nickname} 角色=${m.AudioRoleInfo&&m.AudioRoleInfo.AudioRoleName||''} 时长=${m.AudioTime}s 赞=${item.agree} 回复=${item.reply} 播放地址=${item.audioUrl.slice(0,60)}...`);
  });
  const roleSet=[...new Set(a.list.map(x=>x.AudioRoleInfo&&x.AudioRoleInfo.AudioRoleName).filter(Boolean))];
  console.log(`  可播放=${playable}/${a.list.length}  角色分布=${JSON.stringify(roleSet)}`);
  console.log(`  结论: ${playable>0?'PASS 语音段评可解析出可播放条目':'FAIL 无可播放条目'}`);
 }
})();