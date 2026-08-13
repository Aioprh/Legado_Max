const base = 'https://full.hnxianxin.cn/qd/'
async function j(url) { const r = await fetch(base + url); return {status:r.status, json: await r.json()} }
;(async () => {
  const s = await j('search.php?keyword=' + encodeURIComponent('斗破苍穹') + '&page=1&page_size=20')
  console.log('== SEARCH status', s.status, 'Result', s.json.Result)
  const card = s.json.Data?.CardList?.[0]
  const book = card?.Body?.[0]?.ItemData
  console.log('first book keys:', book ? Object.keys(book) : null)
  console.log('first book:', book ? JSON.stringify({BookId:book.BookId,BookName:book.BookName,Author:book.Author,AuthorName:book.AuthorName,BookCover:book.BookCover,CategoryName:book.CategoryName,Description:(book.Description||'').slice(0,80)}):null)
  if (!book) return
  const d = await j('detail.php?book_id=' + encodeURIComponent(book.BookId))
  console.log('== DETAIL status', d.status, 'Result', d.json.Result, 'Data keys:', d.json.Data ? Object.keys(d.json.Data) : null)
  const bi = d.json.Data?.BaseBookInfo
  console.log('BaseBookInfo keys:', bi ? Object.keys(bi).slice(0,30) : null)
  console.log('AuthorInfo:', d.json.Data?.AuthorInfo ? JSON.stringify(d.json.Data.AuthorInfo).slice(0,150) : null)
  const c = await j('catalog.php?book_id=' + encodeURIComponent(book.BookId))
  console.log('== CATALOG status', c.status, 'Result', c.json.Result, 'Data keys:', c.json.Data ? Object.keys(c.json.Data) : null)
  const ch = c.json.Data?.Chapters
  console.log('chapters len:', Array.isArray(ch) ? ch.length : 'N/A')
  console.log('first chapter:', ch && ch[0] ? JSON.stringify(ch[0]).slice(0,250) : null)
  if (ch && ch[0]) {
    const con = await j('content.php?book_id=' + encodeURIComponent(book.BookId) + '&chapter_id=' + encodeURIComponent(ch[0].C))
    console.log('== CONTENT status', con.status, 'Result', con.json.Result, 'Content len:', (con.json.Content||con.json.content||con.json.Data?.Content||'').length)
    console.log('content head:', (con.json.Content||con.json.content||con.json.Data?.Content||'').replace(/\s+/g,' ').slice(0,150))
  }
})().catch(e => console.error('ERR', e.message))
