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
        return list.asSequence().map { it.toBook() }.firstOrNull { remote ->
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
        val tocUrl = AnalyzeUrl(full.tocUrl, source.bookSourceUrl, source.header, source.loginUrl)
        val body = runCatching { CookieStore.get(tocUrl.toString(), source.header).body?.string() }.getOrNull() ?: return null
        val list = runCatching { AnalyzeUrl(tocUrl.toString(), source.bookSourceUrl, source.header, source.loginUrl).analyzeRule.bookSource?.ruleToc?.chapterList }.getOrNull()
        val result = HashMap<String, String>()
        if (list.isNullOrBlank()) return result
        runCatching {
            val data = jsonPath.parse(body).read<List<Map<String, Any?>>>(list)
            data.forEach { item ->
                val title = item["title"]?.toString()?.trim().orEmpty()
                val url = item["url"]?.toString()?.trim().orEmpty()
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    result[title] = url
                    result[normalizeTitle(title)] = url
                    val core = normalizeChapterCore(title)
                    if (core.isNotEmpty()) result[CORE + core] = url
                    chapterNumber(title)?.let { result[NUM + it] = url }
                }
            }
        }
        return result
    }

    private fun injectBubbles(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String?, summary: SummaryResult): String {
        val config = ParagraphCommentConfig.getConfig()
        val template = config.bubbleTemplate
        if (template.isBlank()) return content
        val paragraphs = content.split("\n")
        return paragraphs.mapIndexed { index, paragraph ->
            val pid = index + 1
            val count = summary.counts[pid] ?: 0
            if (count <= 0 || paragraph.isBlank()) paragraph else {
                val script = adapter.buildPclick(source, bookId, chapterId, pid, chapterUrl)
                paragraph + template.replace("{{count}}", count.toString()).replace("{{click}}", script)
            }
        }.joinToString("\n")
    }

    private fun normalizeTitle(value: String?): String = value.orEmpty().replace(Regex("[\\s　]"), "").replace("《", "").replace("》", "").trim().lowercase()
    private fun normalizeChapterCore(value: String?): String = value.orEmpty().replace(Regex("[\\s　]"), "").replace(Regex("第[零〇一二三四五六七八九十百千万0-9]+[章节回卷集部篇].*"), "").trim()
    private fun chapterNumber(value: String?): String? = Regex("第(\\d+)[章节回卷]").find(value.orEmpty())?.groupValues?.getOrNull(1)

    private companion object {
        const val CORE = "core:"
        const val NUM = "num:"
    }
}

private data class SummaryResult(val counts: Map<Int, Int> = emptyMap())

private interface ParagraphAdapter {
    fun match(source: BookSource): Boolean
    fun extractBookId(bookUrl: String, chapterUrl: String): String?
    fun extractChapterId(chapterUrl: String): String?
    suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult
    fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String
}

private object ShenmoAdapter : ParagraphAdapter {
    override fun match(source: BookSource) = source.bookSourceName.contains("同人", true) || source.bookSourceName.contains("神魔", true)
    override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "bookid") ?: pickId(bookUrl, "book_id")
    override fun extractChapterId(chapterUrl: String) = pickId(chapterUrl, "chapter_id") ?: pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "item_id")
    override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?) = SummaryResult()
    override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?) = webHalfScreenScript(chapterUrl.orEmpty(), "段评")
}

private object FanqieFourInOneAdapter : ParagraphAdapter {
    private const val ROOT = "http://114.66.17.2:7894"
    override fun match(source: BookSource) = source.bookSourceName.contains("番茄四合一", true) || source.bookSourceUrl.contains("114.66.17.2:7894", true)
    override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "bookid") ?: pickId(bookUrl, "book_id")
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
    override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?) = webHalfScreenScript("${source.bookSourceUrl.substringBefore("##").trimEnd('/').ifBlank { ROOT }}/comments.html?book_id=$bookId&item_id=$chapterId&para_index=${pid - 1}", "段评")
}

private object QidianFullAdapter : ParagraphAdapter {
    override fun match(source: BookSource) = source.bookSourceName.contains("起点", true)
    override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "bookId") ?: pickId(bookUrl, "bookid")
    override fun extractChapterId(chapterUrl: String) = pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "chapter_id")
    override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?) = SummaryResult()
    override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?) = webHalfScreenScript(chapterUrl.orEmpty(), "段评")
}

private object JiuJiuAdapter : ParagraphAdapter {
    override fun match(source: BookSource) = source.bookSourceName.contains("玖玖", true)
    override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "bookid") ?: pickId(bookUrl, "book_id")
    override fun extractChapterId(chapterUrl: String) = pickId(chapterUrl, "chapter_id") ?: pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "item_id")
    override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?) = SummaryResult()
    override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?) = webHalfScreenScript(chapterUrl.orEmpty(), "段评")
}

private object GenericAdapter : ParagraphAdapter {
    override fun match(source: BookSource) = true
    override fun extractBookId(bookUrl: String, chapterUrl: String) = pickId(bookUrl, "bookid") ?: pickId(bookUrl, "book_id") ?: pickId(bookUrl, "bookId")
    override fun extractChapterId(chapterUrl: String) = pickId(chapterUrl, "chapter_id") ?: pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "item_id")
    override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?) = SummaryResult()
    override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?) = webHalfScreenScript(chapterUrl.orEmpty(), "段评")
}

private fun pickId(url: String?, key: String): String? = url?.let { Regex("(?:[?&#]|^)${Regex.escape(key)}=([^&#,}]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1) }
private suspend fun fetchBody(source: BookSource, url: String): String? = runCatching {
    val analyzed = AnalyzeUrl(url, source.bookSourceUrl, source.header, source.loginUrl)
    CookieStore.get(analyzed.toString(), source.header).body?.string()
}.getOrNull()
private fun webHalfScreenScript(url: String, title: String): String = "javascript:(function(){window.open(" + "'" + url.replace("'", "\\'") + "','" + title + "','width=100%,height=55%');})();"
