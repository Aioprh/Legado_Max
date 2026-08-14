const base = 'https://full.hnxianxin.cn/qd/'
;(async () => {
  const r = await fetch(base + 'catalog.php?book_id=1209977')
  const j = await r.json()
  const real = (j.Data?.Chapters||[]).find(ch => Number(ch.C) > 0 && ch.N)
  console.log('real chapter:', JSON.stringify(real).slice(0,250))
  const cr = await fetch(base + 'content.php?book_id=1209977&chapter_id=' + encodeURIComponent(real.C), {headers:{'User-Agent':'Mozilla/5.0'}})
  const cj = await cr.json()
  console.log('CONTENT status', cr.status, 'keys:', Object.keys(cj).slice(0,20))
  const content = cj.Content || cj.content || cj.Data?.Content || ''
  console.log('content len:', content.length)
  console.log('content head:', content.replace(/\s+/g,' ').slice(0,200))
  console.log('raw head:', JSON.stringify(cj).slice(0,300))
})().catch(e => console.error('ERR', e.message))
