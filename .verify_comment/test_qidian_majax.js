// 校验起点 majax 段评列表接口返回体在 ParagraphCommentDialog 的解析逻辑下能否正确取出字段。
// 用法: node test_qidian_majax.js <reviewList响应json文件>
const fs = require('fs');
const file = process.argv[2];
if (!file) { console.log('need json file'); process.exit(2); }
const body = JSON.parse(fs.readFileSync(file, 'utf8'));

// ---- 复刻 ParagraphCommentDialog 关键逻辑 ----
function findKey(map, key) {
  if (map[key] !== undefined && map[key] !== null) return map[key];
  const lower = key.toLowerCase();
  for (const k of Object.keys(map)) if (k.toLowerCase() === lower) return map[k];
  return null;
}
function tokenize(path) {
  const tokens = []; let cur = ''; let inBracket = false;
  for (const c of path) {
    if (c === '[') { inBracket = true; cur += c; }
    else if (c === ']') { inBracket = false; cur += c; }
    else if (c === '.' && !inBracket) { if (cur) { tokens.push(cur); cur = ''; } }
    else cur += c;
  }
  if (cur) tokens.push(cur); return tokens;
}
function resolvePath(map, path) {
  let cur = map;
  for (const tok of tokenize(path)) {
    if (tok === '$') continue;
    if (cur && typeof cur === 'object' && !Array.isArray(cur)) cur = findKey(cur, tok);
    else if (Array.isArray(cur)) {
      const idx = parseInt(tok.replace('[', '').replace(']', ''), 10);
      cur = Number.isInteger(idx) && idx >= 0 && idx < cur.length ? cur[idx] : null;
    } else return null;
    if (cur === null || cur === undefined) return null;
  }
  return cur;
}
function readStr(map, primary, defaults) {
  const paths = primary ? [primary, ...defaults.filter(d => d !== primary)] : defaults;
  for (const p of paths) {
    const v = resolvePath(map, p); if (v === null || v === undefined) continue;
    const s = String(v).trim(); if (s && s !== 'null') return s;
  }
  return '';
}
function readLong(map, primary, defaults) {
  const paths = primary ? [primary, ...defaults.filter(d => d !== primary)] : defaults;
  for (const p of paths) {
    const v = resolvePath(map, p); if (v === null || v === undefined) continue;
    if (typeof v === 'number') return v;
    const n = parseInt(String(v), 10); if (!isNaN(n)) return n;
  }
  return 0;
}

// Dialog 里的兜底候选（节选与本次相关的）
const D = {
  IDS: ["$.Id","$.CommentId","$.ReviewId","$.Cid","$.comment_id","$.review_id","$.cid","$.id"],
  NICK: ["$.NickName","$.UserName","$.Name","$.Uname","$.user_name","$.nickname","$.comment_user.user_name","$.comment_user.nickname","$.user.nickname","$.user.name","$.user_info.user_name"],
  AVATAR: ["$.UserHeadIcon","$.UserPhoto","$.Avatar","$.HeadIcon","$.Photo","$.user_avatar","$.avatar","$.comment_user.user_avatar","$.comment_user.avatar","$.user.avatar","$.user.avatar_url","$.user_info.user_avatar"],
  IP: ["$.IpLocation","$.IpAddress","$.Ip","$.Location","$.Region","$.ip_location","$.ip_address"],
  CONTENT: ["$.Content","$.Text","$.Msg","$.ImageMeaning","$.text","$.content","$.body","$.comment_content","$.comment_text","$.review_content"],
  AGREE: ["$.AgreeAmount","$.AgreeCount","$.LikeCount","$.LikeAmount","$.Up","$.digg_count","$.like_count","$.comment_like_count","$.agree_count"],
  TIME: ["$.CreateTime","$.Time","$.CreatedAt","$.CreateDate","$.create_timestamp","$.timestamp","$.created_at","$.create_time","$.post_time","$.createTime","$.pub_time"],
  REPLYCNT: ["$.ReviewCount","$.RootReviewReplyCount","$.ReplyCount","$.ReplyNum","$.SubCount","$.reply_count","$.comment_count","$.replyCount","$.child_count"]
};
function isGod(map) {
  const es = findKey(map, 'EssenceStatus');
  if (typeof es === 'boolean') return es;
  return false;
}
function readInlineReplies(map) {
  const v = findKey(map, 'Replies') || findKey(map, 'replyList'); // findKey 命中 null 时用 ||
  if (!Array.isArray(v)) return [];
  return v.map(m => ({
    nickname: readStr(m, '', D.NICK), content: String(findKey(m, 'Content') ?? ''),
    time: readLong(m, '', D.TIME), replyTo: String(findKey(m, 'QuoteNickName') ?? '')
  })).filter(r => r.content);
}

// ---- 断言 ----
const data = body.data || {};
console.log('code =', body.code, '| total =', data.total, '| list.length =', (data.list || []).length);
if (body.code !== 0) { console.log('RESULT: FAIL code!=0'); process.exit(1); }
const list = data.list || [];
if (!list.length) { console.log('RESULT: FAIL 空列表'); process.exit(1); }

let ok = true;
for (const [i, m] of list.slice(0, 3).entries()) {
  const item = {
    id: readStr(m, '', D.IDS), nickname: readStr(m, '', D.NICK), avatar: readStr(m, '', D.AVATAR),
    ip: readStr(m, '', D.IP), content: readStr(m, '', D.CONTENT), agree: readLong(m, '', D.AGREE),
    time: readLong(m, '', D.TIME), replyCount: readLong(m, '', D.REPLYCNT), isGod: isGod(m),
    replies: readInlineReplies(m)
  };
  console.log(`#${i}`, JSON.stringify({ id: item.id, nickname: item.nickname, ip: item.ip, agree: item.agree, time: item.time, replyCount: item.replyCount, isGod: item.isGod, replies: item.replies.length, content: item.content.slice(0, 20) }));
  if (!item.id || !item.nickname || !item.content || item.time <= 0) ok = false;
}
// 内嵌回复至少第一条要有
const anyReplies = list.slice(0, 3).some(m => readInlineReplies(m).length > 0);
console.log('内嵌回复可解析:', anyReplies);

console.log(ok ? '\nRESULT: PASS' : '\nRESULT: FAIL');
process.exit(ok ? 0 : 1);