package io.legado.app.model.webBook

import android.util.Base64
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.widget.dialog.ParagraphCommentConfig
import io.legado.app.utils.jsonPath
import kotlinx.coroutines.currentCoroutineContext
import java.net.URLEncoder
import java.util.Random

object LocalParagraphComment {
    private val remoteBookCache = HashMap<String, Book?>()
    private val chapterMapCache = HashMap<String, Map<String, String>?>()
    private val summaryCache = HashMap<String, SummaryResult>()

    private val adapters: List<ParagraphAdapter> = listOf(
        ShenmoAdapter, FanqieFourInOneAdapter, QidianFullAdapter, JiuJiuAdapter, GenericAdapter
    )

    fun sourceUrlFor(book: Book): String? {
        val config = book.readConfig
        return when {
            config?.paragraphComment == true ->
                config.paragraphCommentSource?.takeIf { it.isNotBlank() } ?: AppConfig.localParagraphSource
            book.isLocal && AppConfig.localParagraphComment -> AppConfig.localParagraphSource
            else -> null
        }
    }

    suspend fun injectIfNeeded(book: Book, chapter: BookChapter, content: String): String {
        val sourceUrl = sourceUrlFor(book) ?: return content
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)?.takeIf { it.enabled } ?: return content
        val adapter = adapters.firstOrNull { it.match(source) } ?: return content
        val remoteBook = getRemoteBook(book, source) ?: return content
        val remoteChapterUrl = getRemoteChapterUrl(book, chapter, source, remoteBook) ?: return content
        val bookId = adapter.extractBookId(remoteBook.bookUrl, remoteChapterUrl)
        val chapterId = adapter.extractChapterId(remoteChapterUrl)
        if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) {
            AppLog.put("本地书段评: 无法从书源[${source.bookSourceName}]的URL提取书籍/章节ID")
            return content
        }
        val cacheKey = "$bookId|$chapterId|$remoteChapterUrl"
        val cached = synchronized(summaryCache) { summaryCache[cacheKey] }
        val summary = if (cached != null && cached.counts.isNotEmpty()) cached else
            adapter.fetchSummaryCounts(source, bookId, chapterId, remoteChapterUrl).also {
                if (it.counts.isNotEmpty()) synchronized(summaryCache) { summaryCache[cacheKey] = it }
            }
        if (summary.counts.isEmpty()) return content
        return injectBubbles(content, source, adapter, bookId, chapterId, remoteChapterUrl, summary)
    }

    private suspend fun getRemoteBook(book: Book, source: BookSource): Book? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        synchronized(remoteBookCache) { if (remoteBookCache.containsKey(key)) return remoteBookCache[key] }

        val precise = WebBook.preciseSearchAwait(source, book.name, book.author).getOrNull()
        val remote = precise?.takeIf { isRemoteBookMatch(it, book.name, book.author) }
            ?: fuzzySearchRemoteBook(source, book.name, book.author)

        if (remote == null) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]未严格匹配到《${book.name}》(${book.author})")
            synchronized(remoteBookCache) { remoteBookCache[key] = null }
            return null
        }
        val full = runCatching { WebBook.getBookInfoAwait(source, remote) }.getOrDefault(remote)
        if (!isRemoteBookMatch(full, book.name, book.author)) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]详情页校验失败《${book.name}》(${book.author})")
            synchronized(remoteBookCache) { remoteBookCache[key] = null }
            return null
        }
        synchronized(remoteBookCache) { remoteBookCache[key] = full }
        return full
    }

    private fun isRemoteBookMatch(remote: Book, name: String, author: String): Boolean {
        val expectedName = normalizeTitle(name)
        val actualName = normalizeTitle(remote.name)
        if (expectedName.isEmpty() || actualName != expectedName) return false
        val expectedAuthor = normalizeTitle(author)
        if (expectedAuthor.isEmpty()) return true
        val actualAuthor = normalizeTitle(remote.author)
        return actualAuthor == expectedAuthor
    }

    private suspend fun fuzzySearchRemoteBook(source: BookSource, name: String, author: String): Book? {
        val list = runCatching { WebBook.searchBookAwait(source, name) }.getOrNull() ?: return null
        if (list.isEmpty()) return null
        val n = normalizeTitle(name)
        val a = normalizeTitle(author)
        return list.asSequence()
            .map { it.toBook() }
            .firstOrNull { remote ->
                normalizeTitle(remote.name) == n && (a.isEmpty() || normalizeTitle(remote.author) == a)
            }
    }

    private suspend fun getRemoteChapterUrl(book: Book, chapter: BookChapter, source: BookSource, remoteBook: Book): String? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        val map = if (synchronized(chapterMapCache) { chapterMapCache.containsKey(key) }) {
            synchronized(chapterMapCache) { chapterMapCache[key] }
        } else fetchChapterMap(source, remoteBook).also { synchronized(chapterMapCache) { chapterMapCache[key] = it } }
        if (map == null) return null
        val raw = chapter.title
        val norm = normalizeTitle(raw)
        val core = normalizeChapterCore(raw)
        return map[raw] ?: map[raw.trim()] ?: map[norm] ?: map[CORE + core]?.takeIf { core.isNotEmpty() }
            ?: chapterNumber(raw)?.let { map[NUM + it] }
    }

    private suspend fun fetchChapterMap(source: BookSource, remoteBook: Book): Map<String, String>? {
        val full = if (remoteBook.tocUrl.isNullOrBlank()) runCatching { WebBook.getBookInfoAwait(source, remoteBook) }.getOrDefault(remoteBook) else remoteBook
        if (full.tocUrl.isNullOrBlank()) return null
        val chapters = WebBook.getChapterListAwait(source, full).getOrNull() ?: return null
        val map = LinkedHashMap<String, String>()
        val cores = HashMap<String, MutableList<String>>()
        val nums = HashMap<Int, MutableList<String>>()
        chapters.forEach {
            map[it.title] = it.url
            normalizeTitle(it.title).takeIf(String::isNotEmpty)?.let { n -> map[n] = it.url }
            normalizeChapterCore(it.title).takeIf(String::isNotEmpty)?.let { c -> cores.getOrPut(c) { ArrayList() }.add(it.url) }
            chapterNumber(it.title)?.let { n -> nums.getOrPut(n) { ArrayList() }.add(it.url) }
        }
        cores.forEach { (k, v) -> v.distinct().singleOrNull()?.let { map[CORE + k] = it } }
        nums.forEach { (k, v) -> v.distinct().singleOrNull()?.let { map[NUM + k] = it } }
        return map
    }

    private const val CORE = "__chapter_core__:"
    private const val NUM = "__chapter_number__:"

    private fun chapterNumber(title: String): Int? {
        val n = normalizeTitle(title)
        val m = Regex("""(?:^|[^0-9])(?:第)?([0-9]+)章(?:$|[^0-9])""").find(n)
            ?: Regex("""(?:^|[^a-z0-9])chapter([0-9]+)(?:$|[^a-z0-9])""", RegexOption.IGNORE_CASE).find(n)
        return m?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun normalizeChapterCore(title: String): String {
        var v = normalizeTitle(title)
        v = v.replace(Regex("""^(?:第)?[0-9]+章""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^chapter[0-9]+""", RegexOption.IGNORE_CASE), "")
        return v.trim(':', '-', '_', '·', '.', '。')
    }

    private fun normalizeTitle(title: String): String {
        val sb = StringBuilder(title.length)
        for (c in title) when {
            c == ' ' || c == '\u3000' || c == '\t' -> Unit
            c in "《》（）()【】〔〕「」『』" -> Unit
            c in '\uFF10'..'\uFF19' -> sb.append(c - 0xFEE0)
            c in '\uFF21'..'\uFF3A' -> sb.append(c - 0xFEE0)
            c in '\uFF41'..'\uFF5A' -> sb.append(c - 0xFEE0)
            c == '：' -> sb.append(':')
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private suspend fun fetchBody(source: BookSource, url: String, headers: Map<String, String>? = null): String? = runCatching {
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            baseUrl = source.bookSourceUrl,
            source = source,
            coroutineContext = currentCoroutineContext(),
            headerMapF = headers
        )
        analyzeUrl.getStrResponseAwait().body()?.trimStart('\uFEFF')
    }.getOrNull()

    private fun pickId(url: String, param: String): String? =
        Regex("""(?:[?&]|^)$param=(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)

    private fun parseCounts(body: String, path: String, pidKeys: List<String>, countKeys: List<String>): Map<Int, Int> = runCatching {
        val list = jsonPath.parse(body).read<List<Any?>>(path)
        val out = HashMap<Int, Int>()
        list.filterIsInstance<Map<*, *>>().forEach { m ->
            val pid = firstIntValue(m, pidKeys) ?: return@forEach
            val count = firstIntValue(m, countKeys) ?: 0
            if (pid > 0 && count > 0) out[pid] = count
        }
        out
    }.getOrDefault(emptyMap())

    private fun firstIntValue(map: Map<*, *>, keys: List<String>): Int? {
        for (key in keys) for ((k, v) in map) if (k?.toString()?.equals(key, true) == true && v != null)
            v.toString().toIntOrNull()?.let { return it }
        return null
    }

    private fun injectBubbles(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, summary: SummaryResult): String =
        if (summary.remoteParagraphs.isNotEmpty()) injectByTextAlign(content, source, adapter, bookId, chapterId, chapterUrl, summary)
        else injectByPosition(content, source, adapter, bookId, chapterId, chapterUrl, summary.counts)

    private fun injectByPosition(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, counts: Map<Int, Int>): String {
        val lines = content.replace("\r\n", "\n").split("\n")
        var pid = 0
        return lines.joinToString("\n") { line ->
            if (line.trim().isEmpty()) line else {
                pid++
                val count = counts[pid] ?: 0
                if (count <= 0) line else adapter.buildPclick(source, bookId, chapterId, pid, chapterUrl).takeIf { it.isNotBlank() }?.let { "$line<img src=\"dp:$count,${bubbleOption(it, count)}\">" } ?: line
            }
        }
    }

    private fun injectByTextAlign(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, summary: SummaryResult): String {
        val lines = content.replace("\r\n", "\n").split("\n")
        val local = ArrayList<String>(); val lineIndex = ArrayList<Int>()
        lines.forEachIndexed { i, line -> if (line.trim().isNotEmpty()) { local.add(line); lineIndex.add(i) } }
        val align = alignLocalToRemote(local, summary.remoteParagraphs)
        val out = lines.toMutableList()
        local.indices.forEach { i ->
            val r = align[i]
            if (r < 0) return@forEach
            val rp = r + 1; val count = summary.counts[rp] ?: 0
            if (count > 0) {
                val apiPid = summary.apiPids[rp] ?: rp
                adapter.buildPclick(source, bookId, chapterId, apiPid, chapterUrl).takeIf { it.isNotBlank() }?.let {
                    out[lineIndex[i]] += "<img src=\"dp:$count,${bubbleOption(it, count)}\">"
                }
            }
        }
        return out.joinToString("\n")
    }

    private fun alignLocalToRemote(local: List<String>, remote: List<String>): IntArray {
        val a = local.map(::normalizePara); val b = remote.map(::normalizePara)
        val result = IntArray(local.size) { -1 }; var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            if (a[i].isEmpty()) { i++; continue }
            if (b[j].isEmpty()) { j++; continue }
            if (a[i] == b[j]) { result[i] = j; i++; j++; continue }
            var joined = b[j]; var end = j
            while (end + 1 < b.size && joined.length < a[i].length * 2 + 80) {
                end++; joined += b[end]
                if (joined == a[i] || joined.contains(a[i])) break
            }
            if (joined == a[i] || joined.contains(a[i])) { result[i] = end; i++; j = end + 1; continue }
            var ljoined = a[i]; var lend = i
            while (lend + 1 < a.size && ljoined.length < b[j].length * 2 + 80) {
                lend++; ljoined += a[lend]
                if (ljoined == b[j] || ljoined.contains(b[j])) break
            }
            if (ljoined == b[j] || ljoined.contains(b[j])) { result[lend] = j; i = lend + 1; j++; continue }
            var matched = false
            for (k in j + 1 until minOf(b.size, j + 6)) {
                if (a[i].length >= 12 && (a[i] == b[k] || a[i].contains(b[k]) || b[k].contains(a[i]))) {
                    result[i] = k; i++; j = k + 1; matched = true; break
                }
            }
            if (!matched) i++
        }
        return result
    }

    private val HTML_TAG = Regex("<[^>]*>")
    private val WS = Regex("\\s+")
    private fun normalizePara(s: String): String = HTML_TAG.replace(s, "").replace(WS, "").replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "").trim()

    private fun bubbleOption(pclick: String, count: Int): String = buildString {
        append("{\\\"pclick\\\":\\\"").append(pclick).append("\\\"")
        if (count > 99) append(",\\\"displayText\\\":\\\"99+\\\"")
        append(",\\\"status\\\":\\\"normal\\\"}")
    }

    private fun buildPclickScript(listPath: String, totalPath: String, commentsUrl: String, repliesUrl: String, replyListPath: String, audioUrl: String, pageSize: Int, fields: ParagraphCommentConfig.FieldConfig, sortEnabled: Boolean = true): String = buildString {
        fun q(s: String) = if (s.isBlank()) "''" else "'$s'"
        fun fs(f: ParagraphCommentConfig.FieldConfig) =
            "{nickname:${q(f.nickname)},avatar:${q(f.avatar)},level:${q(f.level)},ip:${q(f.ip)},content:${q(f.content)},agree:${q(f.agree)},oppose:${q(f.oppose)},time:${q(f.time)},floor:${q(f.floor)},id:${q(f.id)},rootId:${q(f.rootId)},replyCount:${q(f.replyCount)},replyTo:${q(f.replyTo)}}"
        append("var cfg={listPath:'$listPath',totalPath:'$totalPath',commentsUrl:'$commentsUrl',repliesUrl:'$repliesUrl',replyListPath:'$replyListPath',")
        if (audioUrl.isNotBlank()) append("audioUrl:'$audioUrl',")
        append("pageSize:$pageSize,sortEnabled:$sortEnabled,fields:${fs(fields)},replyFields:${fs(fields)}};java.showParagraphComments(JSON.stringify(cfg));")
    }

    data class SummaryResult(
        val counts: Map<Int, Int> = emptyMap(),
        val apiPids: Map<Int, Int> = emptyMap(),
        val remoteParagraphs: List<String> = emptyList()
    )

    private interface ParagraphAdapter {
        fun match(source: BookSource): Boolean
        fun extractBookId(bookUrl: String, chapterUrl: String): String?
        fun extractChapterId(chapterUrl: String): String?
        suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult
        fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String
    }

    private object GenericAdapter : ParagraphAdapter {
        private val ENDPOINT = Regex("""https?://[^'\"\\s]*?(?:comments?|reviews?)\\.[a-z]+""", RegexOption.IGNORE_CASE)
        override fun match(source: BookSource) = true
        override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "book_id") ?: pickId(chapterUrl, "book_id")
        override fun extractChapterId(chapterUrl: String) = pickId(chapterUrl, "chapter_id")
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            val endpoint = source.getContentRule()?.content?.let { ENDPOINT.find(it)?.value?.trimEnd('?', '&') } ?: return SummaryResult()
            val body = fetchBody(source, "$endpoint?action=summary&book_id=$bookId&chapter_id=$chapterId") ?: return SummaryResult()
            val path = listOf("$.Data.Getparagraphscommentcounts.DataList", "$.Data.DataList", "$.Data.Paragraphs").firstOrNull { runCatching { jsonPath.parse(body).read<List<Any?>>(it) }.isSuccess } ?: return SummaryResult()
            return SummaryResult(parseCounts(body, path, listOf("ParagraphId", "paragraphId"), listOf("CommentCount", "commentCount")))
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String =
            source.getContentRule()?.content?.let { ENDPOINT.find(it)?.value?.trimEnd('?', '&') }?.let {
                buildPclickScript("$.Data.DataList", "$.Data.TotalCount", "$it?action=paragraph&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&type=text&page=[page]&page_size=[pageSize]", "$it?action=replies&book_id=$bookId&chapter_id=$chapterId&review_id=[reviewId]&root_review_id=[rootId]&page=1&page_size=[pageSize]", "$.Data.DataList", "", 20, ParagraphCommentConfig.FieldConfig())
            } ?: ""
    }

    private object ShenmoAdapter : ParagraphAdapter {
        private const val API = "https://shenmoxs.top"
        private const val QD = "https://m.qidian.com/majax/chapterReview/reviewSummary"
        override fun match(source: BookSource) = source.bookSourceUrl.contains("shenmoxs.top", true)
        private fun fanqie(chapterUrl: String?) = chapterUrl?.contains("item_id=", true) == true
        override fun extractBookId(bookUrl: String, chapterUrl: String) = if (fanqie(chapterUrl)) pickId(chapterUrl, "book_id") ?: pickId(bookUrl, "bookId") else pickId(bookUrl, "bookId") ?: pickId(chapterUrl, "bookId")
        override fun extractChapterId(chapterUrl: String) = if (fanqie(chapterUrl)) pickId(chapterUrl, "item_id") else pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "chapter_id")
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            if (fanqie(chapterUrl)) return parseFanqieCounts(fetchBody(source, chapterUrl ?: return SummaryResult()) ?: return SummaryResult())
            val token = runCatching { CookieStore.getKey("https://m.qidian.com", "_csrfToken") }.getOrDefault("")
            val body = fetchBody(source, "$QD?bookId=$bookId&chapterId=$chapterId&_csrfToken=$token", mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36", "Cookie" to "qd_client_id=$token; _csrfToken=$token")) ?: return SummaryResult()
            return SummaryResult(parseCounts(body, "$.data.list", listOf("paragraphId", "ParagraphId"), listOf("textCount", "TextCount", "commentCount", "CommentCount")))
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String {
            val session = runCatching { CookieStore.getCookie("https://shenmoxs.top") }.getOrDefault("").let { Regex("admin_session=([^;]*)").find(it)?.groupValues?.get(1) }?.takeIf(String::isNotBlank)?.let { "&_s=" + URLEncoder.encode(it, "UTF-8") } ?: ""
            val fq = fanqie(chapterUrl); val sp = if (fq) "&source=fanqie" else ""
            return buildPclickScript("$.data.comments", "$.data.pagination.totalCount", "$API/chapter/comments?bookId=$bookId&chapterId=$chapterId&paragraphId=$pid$sp&kind=paragraph&page=[page]&pageSize=[pageSize]$session", "$API/chapter/comment-replies?bookId=$bookId&chapterId=$chapterId&paragraphId=$pid$sp&commentId=[reviewId]&kind=paragraph&pageSize=20$session", "$.data.comments", "", 20, if (fq) ParagraphCommentConfig.FieldConfig() else ParagraphCommentConfig.FieldConfig(nickname="$.UserName", avatar="$.UserHeadIcon", level="$.ShowTag", ip="$.IpLocation", content="$.Content", agree="$.AgreeAmount", oppose="$.OpposeAmount", time="$.CreateTime", floor="$.Floor", id="$.Id", rootId="$.Id", replyCount="$.ReviewCount", replyTo="$.RelatedUser"), !fq)
        }
    }

    private object FanqieFourInOneAdapter : ParagraphAdapter {
        private const val ROOT = "http://114.66.17.2:7894"
        override fun match(source: BookSource) = source.bookSourceName.contains("番茄四合一", true) || source.bookSourceUrl.contains("114.66.17.2:7894", true)
        override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "bookid") ?: pickId(bookUrl, "book_id") ?: pickId(chapterUrl, "book_id")
        override fun extractChapterId(chapterUrl: String): String? {
            if (chapterUrl.startsWith("data:;base64,")) {
                val b64 = chapterUrl.substringAfter(";base64,").substringBefore(",")
                val decoded = runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                if (!decoded.isNullOrBlank() && decoded.all(Char::isDigit)) return decoded
            }
            return pickId(chapterUrl, "item_id") ?: pickId(chapterUrl, "chapter_id") ?: pickId(chapterUrl, "chapterId")
        }
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            val root = source.bookSourceUrl.substringBefore("##").trimEnd('/').ifBlank { ROOT }
            val body = fetchBody(source, "$root/comment.php?action=counts&book_id=$bookId&item_id=$chapterId") ?: return SummaryResult()
            val counts = HashMap<Int, Int>()
            runCatching {
                val ideas = jsonPath.parse(body).read<Map<String, Any?>>("$.data.idea_data")
                ideas.forEach { (k, v) ->
                    val c = (v as? Map<*, *>)?.entries?.firstOrNull { it.key.toString().equals("idea_count", true) }?.value?.toString()?.toIntOrNull() ?: 0
                    val idx = k.toIntOrNull() ?: return@forEach
                    if (idx >= 0 && c > 0) counts[idx + 1] = c
                }
            }
            return SummaryResult(counts)
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String {
            val root = source.bookSourceUrl.substringBefore("##").trimEnd('/').ifBlank { ROOT }
            return webHalfScreenScript("$root/comments.html?book_id=$bookId&item_id=$chapterId&para_index=${pid - 1}", "段评")
        }
    }

    private object QidianFullAdapter : ParagraphAdapter {
        private const val API = "https://pl.aadcn.cn/api/qidian_full_api.php"
        override fun match(source: BookSource) = source.bookSourceUrl.contains("m.qidian.com", true) || source.bookSourceUrl.contains("pl.aadcn.cn", true) || source.bookSourceUrl.contains("qd.aadcn.cn", true)
        override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "novelId") ?: pickId(bookUrl, "bookId") ?: pickId(bookUrl, "book_id") ?: pickId(bookUrl, "bookid")
        override fun extractChapterId(chapterUrl: String): String? {
            if (chapterUrl.startsWith("data:;base64,")) {
                val b64 = chapterUrl.substringAfter(";base64,").substringBefore(",")
                val decoded = runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                if (!decoded.isNullOrBlank() && decoded.all(Char::isDigit)) return decoded
            }
            return pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "chapter_id")
        }
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            val body = fetchBody(source, "$API?action=paragraph_summary&book_id=$bookId&chapter_id=$chapterId") ?: return SummaryResult()
            val counts = parseCounts(body, "$.data.summary", listOf("ParagraphId", "paragraphId"), listOf("CommentCount", "commentCount", "TextCount", "textCount"))
            if (counts.isEmpty()) return SummaryResult()
            val token = source.getVariable().takeIf(String::isNotBlank)
            val remote = fetchBody(source, "https://qd.aadcn.cn/novel/chap?novelId=$bookId&chapId=$chapterId", token?.let { mapOf("Authorization" to "Bearer $it") })
            val text = runCatching {
                val rc = jsonPath.parse(remote ?: "")
                sequenceOf("$.data.content", "$.data.Content", "$.data.chapter.content", "$.data.chapter.Content", "$.content", "$.Content")
                    .mapNotNull { runCatching { rc.read<String>(it) }.getOrNull() }.firstOrNull { it.isNotBlank() }
            }.getOrNull()
            return SummaryResult(counts, counts.keys.associateWith { it }, splitRemoteParagraphs(text))
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?) =
            buildPclickScript("$.data.comments", "$.data.total", "$API?action=paragraph_comments&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&page=[page]&page_size=[pageSize]", "$API?action=comment_replies&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&root_review_id=[rootId]&page=1&page_size=[pageSize]", "$.data.comments", "$API?action=paragraph_audio_comments&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&page=[page]&page_size=[pageSize]", 20, ParagraphCommentConfig.FieldConfig(nickname="$.user_info.user_name", avatar="$.user_info.user_avatar", level="$.raw.ShowTag", ip="$.raw.IpLocation", content="$.text", agree="$.digg_count", oppose="$.raw.OpposeAmount", time="$.create_timestamp", floor="$.floor", id="$.comment_id", rootId="$.raw.RootReviewId", replyCount="$.reply_count", replyTo="$.raw.RelatedUser"))
    }

    private object JiuJiuAdapter : ParagraphAdapter {
        private const val COMMENTS_ROOT = "https://serene.sunianxincue.love/api/fanqie/comments/"
        override fun match(source: BookSource) = source.bookSourceUrl.contains("sunianxincue.love", true) || source.bookSourceUrl.contains("sunianxin.cmcure.com", true) || source.bookSourceName.contains("玖玖小说", true)
        override fun extractBookId(bookUrl: String, chapterUrl: String) = trailingNumber(bookUrl) ?: pickId(bookUrl, "book_id") ?: pickId(chapterUrl, "book_id")
        override fun extractChapterId(chapterUrl: String) = pickId(chapterUrl, "item_id") ?: trailingNumber(chapterUrl)
        private fun sources(chapterUrl: String): String? = Regex("/api/content/([a-z0-9_]+)", RegexOption.IGNORE_CASE).find(chapterUrl)?.groupValues?.get(1)
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            val src = sources(chapterUrl ?: return SummaryResult()) ?: return SummaryResult()
            val body = fetchBody(source, "$COMMENTS_ROOT$src/$bookId/$chapterId") ?: return SummaryResult()
            val counts = HashMap<Int, Int>()
            runCatching {
                val list = jsonPath.parse(body).read<List<Map<String, Any?>>>("$.data.distributions")
                list.forEach { m ->
                    val idx = (m["para_index"] ?: m["paraIndex"])?.toString()?.toIntOrNull() ?: return@forEach
                    val count = (m["count"] ?: m["comment_count"] ?: m["commentCount"])?.toString()?.toIntOrNull() ?: 0
                    if (idx >= 0 && count > 0) counts[idx + 1] = count
                }
            }
            return SummaryResult(counts)
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String {
            val src = sources(chapterUrl ?: return "") ?: return ""
            return webHalfScreenScript("${COMMENTS_ROOT}index.php/ui/$src/$bookId/$chapterId/${pid - 1}", "段评")
        }
    }

    private fun trailingNumber(url: String): String? = Regex("(?:/|=)(\\d+)(?:$|[?#])").find(url)?.groupValues?.get(1)

    private fun parseFanqieCounts(body: String): SummaryResult {
        val counts = HashMap<Int, Int>()
        runCatching {
            val root = jsonPath.parse(body)
            val candidates = listOf("$.data.idea_data", "$.data.ideaData", "$.idea_data", "$.data")
            for (path in candidates) {
                val map = runCatching { root.read<Map<String, Any?>>(path) }.getOrNull() ?: continue
                map.forEach { (k, v) ->
                    val idx = k.toIntOrNull() ?: return@forEach
                    val c = (v as? Map<*, *>)?.entries?.firstOrNull { it.key.toString().equals("idea_count", true) || it.key.toString().equals("count", true) }?.value?.toString()?.toIntOrNull() ?: v.toString().toIntOrNull() ?: 0
                    if (idx >= 0 && c > 0) counts[idx + 1] = c
                }
                if (counts.isNotEmpty()) break
            }
        }
        return SummaryResult(counts)
    }

    private fun splitRemoteParagraphs(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return text.replace("\\r\\n", "\\n").split("\\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun webHalfScreenScript(url: String, title: String): String {
        val safeUrl = url.replace("\\\\", "\\\\\\\\").replace("'", "\\\\'")
        val safeTitle = title.replace("\\\\", "\\\\\\\\").replace("'", "\\\\'")
        return "try{java.showBrowser('$safeUrl',null,'window.java=java;',JSON.stringify({expandedCornersRadius:20,dismissOnTouchOutside:true,isDraggable:true,shouldDimBackground:true,backgroundDimAmount:0.5,hardwareAccelerated:true,isNestedScrollingEnabled:true,isGestureInsetBottomIgnored:true,setFitToContents:false,heightPercentage:0.75,isHideable:true}))}catch(e){java.startBrowser('$safeUrl','$safeTitle')}"
    }
}
