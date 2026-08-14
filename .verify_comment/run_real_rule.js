// 读取 qd_booksource.json 的真实 content 规则，模拟完整管线，验证段评气泡点击弹窗
const fs = require('fs');

const source = JSON.parse(fs.readFileSync('/workspace/qd_booksource.json', 'utf8'));
const contentRule = source[0].ruleContent.content;
console.log('规则前缀:', contentRule.slice(0, 80));

// ---- 模拟 evalJS 执行 @js:... 规则 ----
// 将规则里的 \\u003E (JSON中为 \\\\u003E) 正确处理
// JSON 解析后 content 字符串里是 \\u003E（两个反斜杠+u003E）
// JS 源码里 "\\u003E" 求值后为 \u003E（一个反斜杠+u003E 字面量）

// 用新的 Function 构造器执行 JS（模拟 Rhino evalJS）
// 需要提供 java / book / chapter 全局
function runRule(rule) {
  const book = { bookUrl: 'https://full.hnxianxin.cn/qd/detail.php?book_id=102895' };
  const chapter = { url: 'https://full.hnxianxin.cn/qd/content.php?book_id=102895&chapter_id=123456' };
  const java = {
    ajax: (u) => {
      if (u.includes('action=me')) return '{"request_token":"tok123"}';
      if (u.includes('action=summary')) return JSON.stringify({ Data: { Getparagraphscommentcounts: { DataList: [ { ParagraphId: 12, CommentCount: 12 } ] } } });
      if (u.includes('action=paragraph')) return JSON.stringify({ Data: { DataList: [ { Content: '这本书写得真好', UserName: '张三' } ] } });
      if (u.includes('action=chapter')) return JSON.stringify({ Data: { DataList: [ { Content: '章评内容', UserName: '李四' } ] } });
      return '{"content":"' + Array.from({length:20},(_,i)=>'第'+(i+1)+'段正文。').join('\\n') + '"}';
    },
    get: () => ({ body: () => '{"content":"vip正文"}' }),
    base64Encode: (s) => Buffer.from(s, 'utf8').toString('base64'),
  };
  const fn = new Function('java', 'book', 'chapter', 'return eval(' + JSON.stringify(rule) + ');');
  // 规则是 "@js:..." ，去掉前缀
  const jsBody = rule.replace(/^@js:/, '');
  const fn2 = new Function('java', 'book', 'chapter', 'return (function(){ ' + jsBody + '\n return value; })()');
  const result = fn2(java, book, chapter);
  return result;
}

let content = runRule(contentRule);
console.log('规则输出长度:', content.length);
console.log('输出是否含 img:', content.includes('<img'));
console.log('输出是否含 click option:', content.includes('{"click"'));

// 保存输出供检查
fs.writeFileSync('/workspace/.verify_comment/rule_output.txt', content);

// ---- 模拟 BookContent: formatKeepImg + unescapeHtml4 ----
function base64Encode(s) { return Buffer.from(s, 'utf8').toString('base64'); }
const notImgHtmlRegex = /<\/?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>/g;
const formatImagePattern = /<img[^>]*\ssrc\s*=\s*['"]([^'"{>]*\{(?:[^{}]|\{[^>]+})+})['"][^>]*>|<img[^>]*\sdata-(?:src|original|srcset)\s*=\s*['"]([^'">]+)['"][^>]*>|<img[^>]*\ssrc\s*=\s*"([^">]+)"[^>]*>|<img[^>]*\s(?:data-[^=>]*|src)=\s*['"]([^'">]*)['"][^>]*>/gi;
const paramPattern = /,(\s*)(?=\{)/;
function getAbsoluteURL(baseURL, rp) { return rp.trim().startsWith('data:') ? rp.trim() : rp; }

function formatKeepImg(html) {
  let s = html.replace(notImgHtmlRegex, '');
  s = s.replace(formatImagePattern, (m, g1, g2, g3, g4) => {
    let param = '';
    let src = g1 || g2 || g3 || g4;
    const pm = paramPattern.exec(src);
    if (pm) { param = ',' + src.substring(pm.index + pm[0].length); src = src.substring(0, pm.index); }
    return '<img src="' + getAbsoluteURL(null, src) + param + '">';
  });
  return s;
}
function unescapeHtml4(s) {
  return s.replace(/&gt;/g, '>').replace(/&lt;/g, '<').replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"').replace(/&#(39|0*39);/g, "'").replace(/&nbsp;/g, ' ');
}

content = formatKeepImg(content);
if (content.indexOf('&') > -1) content = unescapeHtml4(content);
fs.writeFileSync('/workspace/.verify_comment/after_format.txt', content);

// ---- 模拟 TextChapterLayout: imgPattern -> paramPattern -> GSON ----
const imgPattern = /<img[^>]*src="([^"]*(?:"[^>]+})?)"[^>]*>/g;
imgPattern.lastIndex = 0;
let m = imgPattern.exec(content);
console.log('imgPattern 匹配:', !!m);
if (m) {
  const src = m[1];
  console.log('--- src 前 60 字符:', src.slice(0, 60));
  const pm = paramPattern.exec(src);
  console.log('paramPattern 找到:', !!pm);
  if (pm) {
    const optionStr = src.substring(pm.index + pm[0].length);
    console.log('option 前 60 字符:', optionStr.slice(0, 60));
    // GSON 解析（JSON.parse 等效，解码 \uXXXX）
    let option, click = null;
    try { option = JSON.parse(optionStr); click = option.click; } catch (e) { console.log('JSON 解析失败:', e.message); }
    console.log('click 提取成功:', !!click);
    if (click) {
      console.log('--- click 前 100 字符:', click.slice(0, 100));
      // 检查是否含有 +bid+ 或 +cid+（bug）
      if (click.includes('+bid+') || click.includes('+cid+')) {
        console.log('BUG: click 脚本仍包含 +bid+ 或 +cid+ 变量引用');
      } else {
        console.log('OK: click 脚本已包含实际 book_id 和 chapter_id 值');
      }
      // 模拟执行 click
      let showed = null;
      const java2 = {
        ajax: (u) => JSON.stringify({ Data: { DataList: [ { Content: '写得好', UserName: '张三' } ] } }),
        showBrowser: (...a) => { showed = a[1]; },
        startBrowser: (...a) => { showed = a[2]; },
      };
      try {
        const fn3 = new Function('java', 'book', 'chapter', click);
        fn3(java2, { bookUrl: 'b' }, { url: 'c' });
        console.log('click 是否调用了 showBrowser:', !!showed);
        if (showed) console.log('弹窗 HTML 前 60 字符:', showed.slice(0, 60));
      } catch (e) {
        console.log('click 执行异常:', e.message);
      }
    }
  }
}