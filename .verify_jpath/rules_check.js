// 极简 JSONPath 校验（数组字段访问自动映射，语义对齐 Jayway）
function jpath(obj, path) {
  const toks = path.split('.').filter(Boolean).map(t => {
    const arr = t.match(/^(\w+)?\[(.+)\]$/)
    return arr ? { key: arr[1], sel: arr[2] } : { key: t, sel: null }
  })
  let cur = obj
  for (const tok of toks) {
    if (tok.sel === '*') {
      const list = tok.key ? (Array.isArray(cur) ? cur.flatMap(e => e?.[tok.key] ?? []) : (cur?.[tok.key] ?? [])) : (Array.isArray(cur) ? cur : [cur])
      cur = list.flat()
    } else if (tok.sel && tok.sel.startsWith('?(')) {
      const m = tok.sel.slice(2, -1).match(/@\.(\w+)\s*>\s*(-?\d+)/)
      const k = m[1], v = Number(m[2])
      const src = tok.key ? (cur?.[tok.key] || []) : cur
      cur = (Array.isArray(src) ? src : []).filter(e => Number(e?.[k] || 0) > v)
    } else if (tok.key) {
      cur = Array.isArray(cur) ? cur.map(e => e?.[tok.key]).flat() : cur?.[tok.key]
    }
  }
  return cur
}
const base = 'https://full.hnxianxin.cn/qd/'
;(async () => {
  const s = await (await fetch(base + 'search.php?keyword=' + encodeURIComponent('斗破苍穹') + '&page=1&page_size=20')).json()
  const books = jpath(s, '$.Data.CardList[*].Body[*].ItemData')
  console.log('bookList count:', books.length)
  const b0 = books[0]
  console.log('name:', b0.BookName, '| author:', b0.AuthorName, '| kind:', b0.CategoryName, '| words:', b0.WordsCount)
  const bookUrl = 'https://full.hnxianxin.cn/qd/detail.php?book_id=' + b0.BookId
  console.log('bookUrl:', bookUrl)
  const bid = bookUrl.match(/book_id=(\d+)/)[1]
  console.log('extracted book_id:', bid)

  const c = await (await fetch(base + 'catalog.php?book_id=' + bid)).json()
  const chapters = jpath(c, '$.Data.Chapters[?(@.C>0)]')
  console.log('chapterList count (C>0):', chapters.length)
  const ch0 = chapters[0]
  console.log('chapter name:', ch0.N, '| id:', ch0.C)
  console.log('chapterUrl:', `https://full.hnxianxin.cn/qd/content.php?book_id=${bid}&chapter_id=${ch0.C}`)

  const con = await (await fetch(base + 'content.php?book_id=' + bid + '&chapter_id=' + ch0.C, {headers:{'User-Agent':'Mozilla/5.0'}})).json()
  console.log('content len:', (con.Content||'').length)

  const d = await (await fetch(base + 'detail.php?book_id=' + bid)).json()
  console.log('detail name:', d.Data.BaseBookInfo.BookName, '| author:', d.Data.AuthorInfo.Author, '| intro len:', (d.Data.BaseBookInfo.Description||'').length)
})().catch(e => console.error('ERR', e.message))
