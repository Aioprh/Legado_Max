// 模拟 AiSourceController 的示例抓取流水线（与 Kotlin 逻辑一致）
const keyword = encodeURIComponent('斗破苍穹')
const searchEndpoint = 'https://full.hnxianxin.cn/qd/search.php?keyword={{key}}&page={{page}}&page_size=20'
const catalogEndpoint = 'https://full.hnxianxin.cn/qd/catalog.php?book_id={{bookId}}'

function buildSearchUrl() {
  let url = searchEndpoint
    .replace('{{key}}', keyword)
    .replace('{{page}}', '1')
    .replace(/\{\{[^}]*\}\}/g, '')
  if (!url.includes('keyword=')) url += (url.includes('?') ? '&' : '?') + 'keyword=' + keyword
  if (!url.includes('page=')) url += (url.includes('?') ? '&' : '?') + 'page=1&page_size=20'
  return url
}

function extractBookId(json) {
  function search(el) {
    if (el && typeof el === 'object' && !Array.isArray(el)) {
      for (const [k, v] of Object.entries(el)) {
        const key = k.toLowerCase()
        if ((key === 'bookid' || key === 'book_id') && (typeof v === 'number' || typeof v === 'string')) {
          const s = String(v)
          if (s && /^\d+$/.test(s)) return s
        }
        const r = search(v)
        if (r) return r
      }
      return null
    }
    if (Array.isArray(el)) {
      for (const e of el) {
        const r = search(e)
        if (r) return r
      }
      return null
    }
    return null
  }
  return search(JSON.parse(json))
}

;(async () => {
  const searchUrl = buildSearchUrl()
  console.log('搜索示例URL:', searchUrl)
  const sr = await fetch(searchUrl)
  const text = await sr.text()
  const ok = sr.ok && (text.trimStart().startsWith('{') || text.trimStart().startsWith('['))
  console.log('搜索HTTP:', sr.status, 'JSON:', ok, '长度:', text.length)
  if (!ok) return
  const bookId = extractBookId(text)
  console.log('提取bookId:', bookId)
  if (!bookId) return
  const catalogUrl = catalogEndpoint.replace('{{bookId}}', bookId).replace(/\{\{[^}]*\}\}/g, '')
  console.log('目录示例URL:', catalogUrl)
  const cr = await fetch(catalogUrl)
  const ctext = await cr.text()
  const cok = cr.ok && (ctext.trimStart().startsWith('{') || ctext.trimStart().startsWith('['))
  console.log('目录HTTP:', cr.status, 'JSON:', cok, '长度:', ctext.length)
  if (cok) {
    const cat = JSON.parse(ctext)
    const chapters = cat.Data?.Chapters
    console.log('章节数:', Array.isArray(chapters) ? chapters.length : 'N/A')
    if (Array.isArray(chapters) && chapters[0]) {
      console.log('首个章节示例:', JSON.stringify(chapters[0]).slice(0, 200))
    }
  }
})()
