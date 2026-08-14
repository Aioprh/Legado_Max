const fs = require('fs')
const html = fs.readFileSync('/workspace/qd.html', 'utf8')
const cleaned = html
  .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
  .replace(/<!--[\s\S]*?-->/g, '')
const baseUrl = 'https://full.hnxianxin.cn/qd/'
const fetchRe = /fetch\s*\(\s*[`'"]([^`'"]+)[`'"]/gi
const found = {}
let m
while ((m = fetchRe.exec(cleaned))) {
  let template = m[1]
  if (!template || template.startsWith('$')) continue
  let c = template
    .replace(/\$\{encodeURIComponent\s*\(\s*keyword\s*\)\}/g, '{{key}}')
    .replace(/\$\{\s*keyword\s*\}/g, '{{key}}')
    .replace(/\$\{encodeURIComponent\s*\(\s*page\s*\)\}/g, '{{page}}')
    .replace(/\$\{\s*page\s*\}/g, '{{page}}')
    .replace(/\$\{encodeURIComponent\s*\(\s*bookId\s*\)\}/g, '{{bookId}}')
    .replace(/\$\{\s*bookId\s*\}/g, '{{bookId}}')
    .replace(/\$\{encodeURIComponent\s*\(\s*chapterId\s*\)\}/g, '{{chapterId}}')
    .replace(/\$\{\s*chapterId\s*\}/g, '{{chapterId}}')
    .replace(/\$\{[^}]*\}/g, '')
  if (!c) continue
  let type = 'other'
  if (c.includes('keyword') || c.includes('search')) type = 'search'
  else if (c.includes('catalog')) type = 'catalog'
  else if (c.includes('content')) type = 'content'
  else if (c.includes('detail') || c.includes('book_id')) type = 'detail'
  if (type === 'other') continue
  let url
  try { url = new URL(c, baseUrl).toString() } catch { continue }
  if (!url.startsWith('http')) continue
  const existing = found[type]
  if (existing == null) found[type] = url
  else if (type === 'search' && c.includes('keyword') && !existing.includes('{{key}}')) found[type] = url
}
for (const t of ['search', 'detail', 'catalog', 'content']) {
  if (found[t]) console.log(t + ': ' + found[t])
}
