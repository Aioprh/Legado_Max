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

/**
 * 段评注入：为本地书或联网书接入其他书源的段评。
 *
 * 当“本地书段评”开关开启且已选择“段评书源”（本地书），或某本书单独配置了“本书段评书源”时，
 * 读取正文后：
 * 1. 用书名/作者在段评书源中精确搜索远程书，得到远程 bookUrl（含书籍 ID）；
 * 2. 拉取远程书详情与目录，按章节标题把本书章节映射到远程章节 URL（含章节 ID）；
 * 3. 按书源类型选择适配器，请求该章的段评摘要，得到各段评论数；
 * 4. 对有评论的段落后方注入 dp: 气泡，点击后经 pclick 打开原生段评弹窗。
 *
 * 段落号按“非空行序号”近似远程段落号（与书源内 ruleContent 的取段逻辑一致），
 * 因此要求本书与远程书的换行/分段落基本一致，才能准确定位。
 *
 * 不同站点的段评接口差异很大，这里用 [ParagraphAdapter] 按书源 URL 分发：
 * - 起点系镜像站（comments.php）   —— 通用/默认适配器
 * - 神魔小说（shenmoxs.top）        —— 摘要走 m.qidian.com，评论走源站 /chapter/comments
 * - 起点限免（同人小说网）           —— 走 pl.aadcn.cn/api/qidian_full_api.php
 */
object LocalParagraphComment {

    /** 本地书 bookUrl|sourceUrl -> 远程书（null 表示搜索失败，避免反复搜索） */
    private val remoteBookCache = HashMap<String, Book?>()

    /** 本地书 bookUrl|sourceUrl -> 本地章节标题 -> 远程章节 URL（null 表示拉取失败） */
    private val chapterMapCache = HashMap<String, Map<String, String>?>()

    /** 远程章节 URL -> 段评摘要（空摘要=该章无段评）。避免重复阅读同一章节时反复请求 */
    private val summaryCache = HashMap<String, SummaryResult>()

    private val adapters: List<ParagraphAdapter> = listOf(
        ShenmoAdapter,
        QidianFullAdapter,
        JiuJiuAdapter,
        GenericAdapter
    )

    /**
     * 获取某本书的段评书源 URL。
     * 每本书单独配置（book.config.paragraphComment）优先；未单独开启或未选书源时，
     * 仅本地书回退全局配置。联网书必须每本书单独配置，避免与书源自身的段评冲突。
     */
    fun sourceUrlFor(book: Book): String? {
        val config = book.readConfig
        return when {
            config?.paragraphComment == true ->
                config.paragraphCommentSource?.takeIf { it.isNotBlank() } ?: AppConfig.localParagraphSource
            book.isLocal && AppConfig.localParagraphComment -> AppConfig.localParagraphSource
            else -> null
        }
    }

    /**
     * 若需要为本书（本地书或联网书）注入段评气泡，返回注入后的正文；否则原样返回。
     * 任何一步失败（未开启、无书源、搜索不到、无目录、无段评等）都安全回退原正文。
     */
    suspend fun injectIfNeeded(book: Book, chapter: BookChapter, content: String): String {
        val sourceUrl = sourceUrlFor(book) ?: return content
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)?.takeIf { it.enabled }
            ?: return content
        val adapter = adapters.firstOrNull { it.match(source) } ?: return content
        val remoteBook = getRemoteBook(book, source) ?: return content
        val remoteChapterUrl = getRemoteChapterUrl(book, chapter, source, remoteBook)
            ?: return content
        val bookId = adapter.extractBookId(remoteBook.bookUrl, remoteChapterUrl)
        val chapterId = adapter.extractChapterId(remoteChapterUrl)
        if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) {
            AppLog.put("本地书段评: 无法从书源[${source.bookSourceName}]的URL提取书籍/章节ID")
            return content
        }
        // 摘要按远程章节 URL 缓存，重复阅读同一章节不再请求。
        // 注意：仅缓存“非空”摘要。首次打开时远程章节段评数据（review_list）可能尚未就绪或瞬时拉取失败，
        // 若把空摘要也缓存会导致该章整个会话段评气泡都不显示；不缓存后下次打开会重新拉取自愈。
        val cached = synchronized(summaryCache) { summaryCache[remoteChapterUrl] }
        val summary = if (cached != null && cached.counts.isNotEmpty()) {
            cached
        } else {
            adapter.fetchSummaryCounts(source, bookId, chapterId, remoteChapterUrl).also { fetched ->
                if (fetched.counts.isNotEmpty()) {
                    synchronized(summaryCache) { summaryCache[remoteChapterUrl] = fetched }
                }
            }
        }
        if (summary.counts.isEmpty()) {
            AppLog.putReaderDebug("本地书段评: 章节[${chapter.title}]暂无段评")
            return content
        }
        val injected = injectBubbles(
            content, source, adapter, bookId, chapterId, remoteChapterUrl, summary
        )
        if (injected != content) {
            AppLog.putReaderDebug("本地书段评: 章节[${chapter.title}] 已注入 ${summary.counts.size} 个段评气泡")
        }
        return injected
    }

    /** 在段评书源中搜索本地书，得到远程书（含 bookUrl、tocUrl）。精确匹配失败时降级模糊匹配。 */
    private suspend fun getRemoteBook(book: Book, source: BookSource): Book? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        synchronized(remoteBookCache) {
            if (remoteBookCache.containsKey(key)) {
                return remoteBookCache[key]
            }
        }
        val remote = WebBook.preciseSearchAwait(source, book.name, book.author).getOrNull()
            ?: fuzzySearchRemoteBook(source, book.name, book.author)
        if (remote == null) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]未搜索到《${book.name}》(${book.author})${loginHint(source)}")
            synchronized(remoteBookCache) { remoteBookCache[key] = null }
            return null
        }
        val full = runCatching { WebBook.getBookInfoAwait(source, remote) }.getOrDefault(remote)
        synchronized(remoteBookCache) { remoteBookCache[key] = full }
        return full
    }

    private suspend fun fuzzySearchRemoteBook(source: BookSource, name: String, author: String): Book? {
        val list = runCatching { WebBook.searchBookAwait(source, name) }.getOrNull() ?: return null
        if (list.isEmpty()) return null
        AppLog.putReaderDebug("本地书段评: 模糊搜索《$name》返回 ${list.size} 条（书源${source.bookSourceName}）")
        val n = normalizeTitle(name)
        val a = normalizeTitle(author)
        list.firstOrNull { normalizeTitle(it.name) == n }?.toBook()?.let { return it }
        if (a.isNotEmpty()) list.firstOrNull { normalizeTitle(it.author) == a }?.toBook()?.let { return it }
        list.firstOrNull {
            val bn = normalizeTitle(it.name)
            bn.isNotEmpty() && (bn.contains(n) || n.contains(bn))
        }?.toBook()?.let { return it }
        return list.first().toBook()
    }

    private fun loginHint(source: BookSource): String {
        if (!source.bookSourceUrl.contains("shenmoxs.top", ignoreCase = true)) return ""
        val ck = runCatching { CookieStore.getCookie("https://shenmoxs.top") }.getOrDefault("")
        return if (Regex("admin_session=[^;]+").containsMatchIn(ck)) ""
        else "（神魔小说书源未登录，搜索被拒；请先到书源登录页登录）"
    }

    private suspend fun getRemoteChapterUrl(
        book: Book,
        chapter: BookChapter,
        source: BookSource,
        remoteBook: Book
    ): String? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        val cached = synchronized(chapterMapCache) { chapterMapCache.containsKey(key) }
        val map = if (cached) synchronized(chapterMapCache) { chapterMapCache[key] }
        else fetchChapterMap(source, remoteBook).also { synchronized(chapterMapCache) { chapterMapCache[key] = it } }
        if (map == null) return null
        val rawTitle = chapter.title
        val normalized = normalizeTitle(rawTitle)
        val core = normalizeChapterCore(rawTitle)
        return map[rawTitle]
            ?: map[rawTitle.trim()]
            ?: map[normalized]
            ?: map[CHAPTER_CORE_PREFIX + core]?.takeIf { core.isNotEmpty() }
            ?: chapterNumber(rawTitle)?.let { map[CHAPTER_NUMBER_PREFIX + it] }
    }

    private suspend fun fetchChapterMap(source: BookSource, remoteBook: Book): Map<String, String>? {
        val full = if (remoteBook.tocUrl.isNullOrBlank()) {
            runCatching { WebBook.getBookInfoAwait(source, remoteBook) }.getOrDefault(remoteBook)
        } else remoteBook
        if (full.tocUrl.isNullOrBlank()) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]目录地址为空")
            return null
        }
        val chapters = WebBook.getChapterListAwait(source, full).getOrNull() ?: run {
            AppLog.put("本地书段评: 拉取目录失败（${full.tocUrl}）")
            return null
        }
        val map = LinkedHashMap<String, String>()
        val coreCandidates = HashMap<String, MutableList<String>>()
        val numberCandidates = HashMap<Int, MutableList<String>>()
        chapters.forEach { chapterEntry ->
            val title = chapterEntry.title
            val normalized = normalizeTitle(title)
            map[title] = chapterEntry.url
            if (normalized.isNotEmpty()) map[normalized] = chapterEntry.url
            val core = normalizeChapterCore(title)
            if (core.isNotEmpty()) coreCandidates.getOrPut(core) { ArrayList() }.add(chapterEntry.url)
            chapterNumber(title)?.let { number -> numberCandidates.getOrPut(number) { ArrayList() }.add(chapterEntry.url) }
        }
        coreCandidates.forEach { (core, urls) ->
            val distinct = urls.distinct()
            if (distinct.size == 1) map[CHAPTER_CORE_PREFIX + core] = distinct[0]
        }
        numberCandidates.forEach { (number, urls) ->
            val distinct = urls.distinct()
            if (distinct.size == 1) map[CHAPTER_NUMBER_PREFIX + number] = distinct[0]
        }
        return map
    }

    private const val CHAPTER_CORE_PREFIX = "__chapter_core__:"
    private const val CHAPTER_NUMBER_PREFIX = "__chapter_number__:"

    private fun chapterNumber(title: String): Int? {
        val normalized = normalizeTitle(title)
        val match = Regex("""(?:^|[^0-9])(?:第)?([0-9]+)章(?:$|[^0-9])""").find(normalized)
            ?: Regex("""(?:^|[^a-z0-9])chapter([0-9]+)(?:$|[^a-z0-9])""", RegexOption.IGNORE_CASE).find(normalized)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun normalizeChapterCore(title: String): String {
        var value = normalizeTitle(title)
        value = value.replace(Regex("""^(?:第)?[0-9]+章""", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("""^chapter[0-9]+""", RegexOption.IGNORE_CASE), "")
        return value.trim(':', '-', '_', '·', '.', '。')
    }

    private fun normalizeTitle(title: String): String {
        val sb = StringBuilder(title.length)
        for (c in title) {
            when {
                c == ' ' || c == '\u3000' || c == '\t' -> Unit
                c in "《》（）()【】〔〕「」『』" -> Unit
                c in '\uFF10'..'\uFF19' -> sb.append(c - 0xFEE0)
                c in '\uFF21'..'\uFF3A' -> sb.append(c - 0xFEE0)
                c in '\uFF41'..'\uFF5A' -> sb.append(c - 0xFEE0)
                c == '：' -> sb.append(':')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private suspend fun fetchBody(
        source: BookSource,
        url: String,
        headerMapF: Map<String, String>? = null
    ): String? = runCatching {
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            baseUrl = source.bookSourceUrl,
            source = source,
            coroutineContext = currentCoroutineContext(),
            headerMapF = headerMapF
        )
        analyzeUrl.getStrResponseAwait().body()?.trimStart('\uFEFF')
    }.getOrNull()

    private fun pickId(url: String, param: String): String? =
        Regex("""(?:[?&]|^)$param=(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)

    private fun parseCounts(
        body: String,
        listPath: String,
        pidKeys: List<String>,
        countKeys: List<String>
    ): Map<Int, Int> = runCatching {
        val rc = jsonPath.parse(body)
        val list = runCatching { rc.read<List<Any?>>(listPath) }.getOrNull() ?: return@runCatching emptyMap()
        val result = HashMap<Int, Int>()
        list.mapNotNull { it as? Map<*, *> }.forEach { map ->
            val pid = firstIntValue(map, pidKeys) ?: return@forEach
            if (pid <= 0) return@forEach
            result[pid] = firstIntValue(map, countKeys) ?: 0
        }
        result
    }.getOrDefault(emptyMap())

    private fun firstIntValue(map: Map<*, *>, keys: List<String>): Int? {
        for (key in keys) for ((k, v) in map) if (k != null && k.toString().equals(key, true) && v != null) {
            v.toString().toIntOrNull()?.let { return it }
            break
        }
        return null
    }

    private fun parseFanqieCounts(body: String): SummaryResult = runCatching {
        val rc = jsonPath.parse(body)
        val content = runCatching { rc.read<String>("$.content") }.getOrNull()
            ?: runCatching { rc.read<String>("$.data.content") }.getOrNull()
        val list = runCatching { rc.read<List<Any?>>("$.review_list") }.getOrNull()
            ?: runCatching { rc.read<List<Any?>>("$.data.review_list") }.getOrNull()
            ?: return@runCatching SummaryResult()
        val rawParagraphs = if (content == null) emptyList() else if (content.contains("<p>", true)) {
            content.replace("\r\n", "\n").replace("\r", "\n").replaceFirst("<p>", "", true).split(Regex("<p>", RegexOption.IGNORE_CASE))
        } else content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val remoteParagraphs = rawParagraphs.map { it.trim() }.filter { it.isNotEmpty() }
        val lineToPara = HashMap<Int, Int>(); var para = 0
        rawParagraphs.forEachIndexed { i, line -> if (line.trim().isNotEmpty()) { para++; lineToPara[i + 1] = para } }
        val counts = HashMap<Int, Int>(); val apiPids = HashMap<Int, Int>()
        list.mapNotNull { it as? Map<*, *> }.forEach { map ->
            val rawLine = firstIntValue(map, listOf("paragraphId", "ParagraphId")) ?: return@forEach
            val paraNum = lineToPara[rawLine] ?: rawLine
            if (paraNum <= 0) return@forEach
            val count = firstIntValue(map, listOf("textCount", "TextCount", "commentCount", "CommentCount")) ?: 0
            val apiPid = firstIntValue(map, listOf("paraIndex", "ParaIndex")) ?: rawLine
            counts[paraNum] = count; apiPids[paraNum] = apiPid
        }
        SummaryResult(counts, apiPids, remoteParagraphs)
    }.getOrDefault(SummaryResult())

    private fun injectBubbles(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, summary: SummaryResult): String =
        if (summary.remoteParagraphs.isNotEmpty()) injectByTextAlign(content, source, adapter, bookId, chapterId, chapterUrl, summary)
        else injectByPosition(content, source, adapter, bookId, chapterId, chapterUrl, summary.counts)

    private fun bubbleOption(pclick: String, count: Int): String = buildString {
        append("{\\\"pclick\\\":\\\"").append(pclick).append("\\\"")
        if (count > 99) append(",\\\"displayText\\\":\\\"99+\\\"")
        append(",\\\"status\\\":\\\"normal\\\"}")
    }

    private fun injectByPosition(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, counts: Map<Int, Int>): String {
        val lines = content.replace("\r\n", "\n").split("\n"); var pid = 0; val out = ArrayList<String>(lines.size)
        for (line in lines) {
            if (line.trim().isEmpty()) { out.add(line); continue }
            pid++; val count = counts[pid] ?: 0
            if (count > 0) {
                val pclick = adapter.buildPclick(source, bookId, chapterId, pid, chapterUrl)
                if (pclick.isNotBlank()) out.add("$line<img src=\"dp:$count,${bubbleOption(pclick, count)}\">") else out.add(line)
            } else out.add(line)
        }
        return out.joinToString("\n")
    }

    private fun injectByTextAlign(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, summary: SummaryResult): String {
        val lines = content.replace("\r\n", "\n").split("\n"); val localParas = ArrayList<String>(); val lineIndexOfPara = ArrayList<Int>()
        lines.forEachIndexed { i, line -> if (line.trim().isNotEmpty()) { localParas.add(line); lineIndexOfPara.add(i) } }
        val align = alignLocalToRemote(localParas, summary.remoteParagraphs); val out = lines.toMutableList()
        for (pi in localParas.indices) {
            val ri = align[pi]; if (ri < 0) continue
            val remotePara = ri + 1; val count = summary.counts[remotePara] ?: 0
            if (count > 0) {
                val apiPid = summary.apiPids[remotePara] ?: remotePara
                val pclick = adapter.buildPclick(source, bookId, chapterId, apiPid, chapterUrl)
                if (pclick.isNotBlank()) out[lineIndexOfPara[pi]] = "${out[lineIndexOfPara[pi]]}<img src=\"dp:$count,${bubbleOption(pclick, count)}\">"
            }
        }
        return out.joinToString("\n")
    }

    private fun alignLocalToRemote(local: List<String>, remote: List<String>): IntArray {
        val nl = local.map(::normalizePara); val nr = remote.map(::normalizePara); val result = IntArray(local.size) { -1 }; var li = 0; var ri = 0
        while (li < nl.size && ri < nr.size) {
            if (nl[li].isEmpty()) { li++; continue }; if (nr[ri].isEmpty()) { ri++; continue }
            if (nl[li] == nr[ri]) { result[li] = ri; li++; ri++; continue }
            var remoteJoined = nr[ri]; var rEnd = ri; var foundLocalInRemote = false
            while (rEnd + 1 < nr.size && remoteJoined.length < nl[li].length * 2 + 80) {
                if (remoteJoined == nl[li] || remoteJoined.contains(nl[li])) { foundLocalInRemote = true; break }
                rEnd++; remoteJoined += nr[rEnd]
                if (remoteJoined == nl[li] || remoteJoined.contains(nl[li])) { foundLocalInRemote = true; break }
            }
            if (foundLocalInRemote) { result[li] = rEnd; li++; ri = rEnd + 1; continue }
            var localJoined = nl[li]; var lEnd = li; var foundRemoteInLocal = false
            while (lEnd + 1 < nl.size && localJoined.length < nr[ri].length * 2 + 80) {
                if (localJoined == nr[ri] || localJoined.contains(nr[ri])) { foundRemoteInLocal = true; break }
                lEnd++; localJoined += nl[lEnd]
                if (localJoined == nr[ri] || localJoined.contains(nr[ri])) { foundRemoteInLocal = true; break }
            }
            if (foundRemoteInLocal) { result[lEnd] = ri; li = lEnd + 1; ri++; continue }
            var matched = false; val maxLookAhead = minOf(nr.size, ri + 6)
            for (k in ri + 1 until maxLookAhead) if (nl[li].length >= 12 && (nr[k] == nl[li] || nr[k].contains(nl[li]) || nl[li].contains(nr[k]))) { result[li] = k; li++; ri = k + 1; matched = true; break }
            if (!matched) li++
        }
        return result
    }

    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private fun normalizePara(s: String): String = HTML_TAG_REGEX.replace(s, "").replace(WHITESPACE_REGEX, "").replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "").trim()

    private fun buildPclickScript(listPath: String, totalPath: String, commentsUrl: String, repliesUrl: String, replyListPath: String, audioUrl: String, pageSize: Int, fields: ParagraphCommentConfig.FieldConfig, replyFields: ParagraphCommentConfig.FieldConfig = fields, sortEnabled: Boolean = true): String = buildString {
        append("var cfg={listPath:'").append(listPath).append("',totalPath:'").append(totalPath).append("',commentsUrl:'").append(commentsUrl).append("',repliesUrl:'").append(repliesUrl).append("',replyListPath:'").append(replyListPath).append("',")
        if (audioUrl.isNotBlank()) append("audioUrl:'").append(audioUrl).append("',")
        append("pageSize:").append(pageSize).append(",sortEnabled:").append(sortEnabled).append(",fields:").append(fieldsScript(fields)).append(",replyFields:").append(fieldsScript(replyFields)).append("};java.showParagraphComments(JSON.stringify(cfg));")
    }

    private fun fieldsScript(f: ParagraphCommentConfig.FieldConfig): String = buildString {
        fun q(s: String) = if (s.isBlank()) "''" else "'$s'"
        append("{nickname:").append(q(f.nickname)).append(",avatar:").append(q(f.avatar)).append(",level:").append(q(f.level)).append(",ip:").append(q(f.ip)).append(",content:").append(q(f.content)).append(",agree:").append(q(f.agree)).append(",oppose:").append(q(f.oppose)).append(",time:").append(q(f.time)).append(",floor:").append(q(f.floor)).append(",id:").append(q(f.id)).append(",rootId:").append(q(f.rootId)).append(",replyCount:").append(q(f.replyCount)).append(",replyTo:").append(q(f.replyTo)).append("}")
    }

    data class SummaryResult(val counts: Map<Int, Int> = emptyMap(), val apiPids: Map<Int, Int> = emptyMap(), val remoteParagraphs: List<String> = emptyList())

    private interface ParagraphAdapter {
        fun match(source: BookSource): Boolean
        fun extractBookId(bookUrl: String, chapterUrl: String): String?
        fun extractChapterId(chapterUrl: String): String?
        suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String? = null): SummaryResult
        fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String? = null): String
    }

    private object GenericAdapter : ParagraphAdapter {
        private val COMMENTS_ENDPOINT_REGEX = Regex("""https?://[^'"\\s]*?(?:comments?|reviews?)\\.[a-z]+""", RegexOption.IGNORE_CASE)
        private val SUMMARY_LIST_PATHS = listOf("$.Data.Getparagraphscommentcounts.DataList", "$.Data.DataList", "$.Data.Paragraphs")
        override fun match(source: BookSource): Boolean = true
        override fun extractBookId(bookUrl: String, chapterUrl: String): String? = pickId(bookUrl, "book_id") ?: pickId(chapterUrl, "book_id")
        override fun extractChapterId(chapterUrl: String): String? = pickId(chapterUrl, "chapter_id")
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            val endpoint = extractCommentsEndpoint(source) ?: return SummaryResult()
            val body = fetchBody(source, "$endpoint?action=summary&book_id=$bookId&chapter_id=$chapterId") ?: return SummaryResult()
            val listPath = SUMMARY_LIST_PATHS.firstOrNull { runCatching { jsonPath.parse(body).read<List<Any?>>(it) }.getOrNull() != null } ?: return SummaryResult()
            return SummaryResult(parseCounts(body, listPath, listOf("ParagraphId", "paragraphId"), listOf("CommentCount", "commentCount")))
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String {
            val endpoint = extractCommentsEndpoint(source) ?: return ""
            return buildPclickScript("$.Data.DataList", "$.Data.TotalCount", "$endpoint?action=paragraph&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&type=text&page=[page]&page_size=[pageSize]", "$endpoint?action=replies&book_id=$bookId&chapter_id=$chapterId&review_id=[reviewId]&root_review_id=[rootId]&page=1&page_size=[pageSize]", "$.Data.DataList", "", 20, ParagraphCommentConfig.FieldConfig())
        }
        private fun extractCommentsEndpoint(source: BookSource): String? = source.getContentRule()?.content?.let { COMMENTS_ENDPOINT_REGEX.find(it)?.value?.trimEnd('?', '&') }
    }

    private object ShenmoAdapter : ParagraphAdapter {
        private const val API = "https://shenmoxs.top"
        private const val QD_SUMMARY = "https://m.qidian.com/majax/chapterReview/reviewSummary"
        override fun match(source: BookSource): Boolean = source.bookSourceUrl.contains("shenmoxs.top", true)
        private fun isFanqie(bookUrl: String, chapterUrl: String?): Boolean = bookUrl.contains("source=fanqie", true) || chapterUrl?.contains("item_id=", true) == true
        override fun extractBookId(bookUrl: String, chapterUrl: String): String? = if (isFanqie(bookUrl, chapterUrl)) pickId(chapterUrl, "book_id") ?: pickId(bookUrl, "bookId") else pickId(bookUrl, "bookId") ?: pickId(chapterUrl, "bookId")
        override fun extractChapterId(chapterUrl: String): String? = if (chapterUrl.contains("item_id=", true)) pickId(chapterUrl, "item_id") else pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "chapter_id")
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            if (chapterUrl != null && chapterUrl.contains("item_id=", true)) return parseFanqieCounts(fetchBody(source, chapterUrl) ?: return SummaryResult())
            val token = qidianToken(); val body = fetchBody(source, "$QD_SUMMARY?bookId=$bookId&chapterId=$chapterId&_csrfToken=$token", mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36", "Cookie" to "qd_client_id=$token; _csrfToken=$token")) ?: return SummaryResult()
            return SummaryResult(parseCounts(body, "$.data.list", listOf("paragraphId", "ParagraphId"), listOf("textCount", "TextCount", "commentCount", "CommentCount")))
        }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String {
            val session = adminSession(); val fanqie = chapterUrl?.contains("item_id=", true) == true; val sourceParam = if (fanqie) "&source=fanqie" else ""
            return buildPclickScript("$.data.comments", "$.data.pagination.totalCount", "$API/chapter/comments?bookId=$bookId&chapterId=$chapterId&paragraphId=$pid$sourceParam&kind=paragraph&page=[page]&pageSize=[pageSize]$session", "$API/chapter/comment-replies?bookId=$bookId&chapterId=$chapterId&paragraphId=$pid$sourceParam&commentId=[reviewId]&kind=paragraph&pageSize=20$session", "$.data.comments", "", 20, if (fanqie) ParagraphCommentConfig.FieldConfig() else ParagraphCommentConfig.FieldConfig(nickname="$.UserName", avatar="$.UserHeadIcon", level="$.ShowTag", ip="$.IpLocation", content="$.Content", agree="$.AgreeAmount", oppose="$.OpposeAmount", time="$.CreateTime", floor="$.Floor", id="$.Id", rootId="$.Id", replyCount="$.ReviewCount", replyTo="$.RelatedUser"), sortEnabled=!fanqie)
        }
        private fun qidianToken(): String { val existing = runCatching { CookieStore.getKey("https://m.qidian.com", "_csrfToken") }.getOrDefault(""); if (existing.isNotBlank()) return existing; val chars="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; val random=Random(); return (1..40).map { chars[random.nextInt(chars.length)] }.joinToString("") }
        private fun adminSession(): String { val ck=runCatching { CookieStore.getCookie("https://shenmoxs.top") }.getOrDefault(""); val v=Regex("admin_session=([^;]*)").find(ck)?.groupValues?.get(1) ?: return ""; return if (v.isBlank()) "" else "&_s="+runCatching { URLEncoder.encode(v,"UTF-8") }.getOrDefault(v) }
    }

    private object QidianFullAdapter : ParagraphAdapter {
        private const val API = "https://pl.aadcn.cn/api/qidian_full_api.php"
        override fun match(source: BookSource): Boolean = source.bookSourceUrl.contains("m.qidian.com", true) || source.bookSourceUrl.contains("pl.aadcn.cn", true) || source.bookSourceUrl.contains("qd.aadcn.cn", true)
        override fun extractBookId(bookUrl: String, chapterUrl: String): String? = pickId(bookUrl, "novelId") ?: pickId(bookUrl, "bookId") ?: pickId(bookUrl, "book_id")
        override fun extractChapterId(chapterUrl: String): String? {
            if (chapterUrl.startsWith("data:;base64,")) { val b64=chapterUrl.substringAfter(";base64,").substringBefore(","); val decoded=runCatching { String(Base64.decode(b64,Base64.DEFAULT),Charsets.UTF_8) }.getOrNull(); if (decoded!=null && decoded.isNotEmpty() && decoded.all { it.isDigit() }) return decoded; return null }
            return pickId(chapterUrl,"chapterId") ?: pickId(chapterUrl,"chapter_id") ?: Regex("\\\"chapterId\\\"\\s*:\\s*\\\"(\\d+)\\\"").find(chapterUrl)?.groupValues?.get(1)
        }
        override suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String?): SummaryResult {
            val body=fetchBody(source,"$API?action=paragraph_summary&book_id=$bookId&chapter_id=$chapterId") ?: return SummaryResult()
            val counts=parseCounts(body,"$.data.summary",listOf("ParagraphId","paragraphId"),listOf("CommentCount","commentCount","TextCount","textCount")); if(counts.isEmpty()) return SummaryResult()
            val remoteParagraphs=splitRemoteParagraphs(fetchRemoteChapterContent(source,bookId,chapterId))
            return SummaryResult(counts,counts.keys.associateWith { it },remoteParagraphs)
        }
        private suspend fun fetchRemoteChapterContent(source: BookSource, bookId: String, chapterId: String): String? {
            val token=source.getVariable().trim(); val headers=if(token.isNotEmpty()) mapOf("Authorization" to "Bearer $token") else null
            val body=fetchBody(source,"https://qd.aadcn.cn/novel/chap?novelId=$bookId&chapId=$chapterId",headers) ?: return null
            return runCatching { val rc=jsonPath.parse(body); sequenceOf("$.data.content","$.data.Content","$.data.chapter.content","$.data.chapter.Content","$.content","$.Content").mapNotNull { path -> runCatching { rc.read<String>(path) }.getOrNull() }.firstOrNull { it.isNotBlank() } }.getOrNull()
        }
        private fun splitRemoteParagraphs(content:String?):List<String>{ if(content.isNullOrBlank()) return emptyList(); val normalized=content.replace("\r\n","\n").replace("\r","\n"); val raw=if(Regex("<p\\b",RegexOption.IGNORE_CASE).containsMatchIn(normalized)){normalized.replace(Regex("<p\\b[^>]*>",RegexOption.IGNORE_CASE),"\n").replace(Regex("</p>",RegexOption.IGNORE_CASE),"\n").split("\n")}else normalized.split("\n"); return raw.map{it.trim()}.filter{it.isNotEmpty()} }
        override fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String?): String = buildPclickScript("$.data.comments","$.data.total","$API?action=paragraph_comments&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&page=[page]&page_size=[pageSize]","$API?action=comment_replies&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&root_review_id=[rootId]&page=1&page_size=[pageSize]","$.data.comments","$API?action=paragraph_audio_comments&book_id=$bookId&chapter_id=$chapterId&paragraph_id=$pid&page=[page]&page_size=[pageSize]",20,ParagraphCommentConfig.FieldConfig(nickname="$.user_info.user_name",avatar="$.user_info.user_avatar",level="$.raw.ShowTag",ip="$.raw.IpLocation",content="$.text",agree="$.digg_count",oppose="$.raw.OpposeAmount",time="$.create_timestamp",floor="$.floor",id="$.comment_id",rootId="$.raw.RootReviewId",replyCount="$.reply_count",replyTo="$.raw.RelatedUser"))
    }

    private object JiuJiuAdapter : ParagraphAdapter {
        private const val COMMENTS_ROOT="/api/fanqie/comments/"
        override fun match(source: BookSource):Boolean=source.bookSourceUrl.contains("sunianxincue.love",true)
        override fun extractBookId(bookUrl:String,chapterUrl:String):String?=trailingNumber(bookUrl) ?: pickId(chapterUrl,"book_id")
        override fun extractChapterId(chapterUrl:String):String?=pickId(chapterUrl,"item_id") ?: trailingNumber(chapterUrl)
        private fun sources(chapterUrl:String?):String=chapterUrl?.let{Regex("""/api/content/([a-z]+)/""",RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)} ?: "fanqie"
        private fun trailingNumber(u:String):String?=Regex("""/(\\d+)(?=[/?]|$)""").find(u)?.groupValues?.get(1)
        override suspend fun fetchSummaryCounts(source:BookSource,bookId:String,chapterId:String,chapterUrl:String?):SummaryResult{ val url=source.bookSourceUrl.trimEnd('/')+COMMENTS_ROOT+sources(chapterUrl)+"/$bookId/$chapterId"; val body=fetchBody(source,url) ?: return SummaryResult(); val counts=HashMap<Int,Int>(); runCatching{ val list=jsonPath.parse(body).read<List<Any?>>("$.data.distributions"); list.filterIsInstance<Map<*,*>>().forEach{d->val idx=d["para_index"]?.toString()?.toIntOrNull() ?: return@forEach; val count=d["count"]?.toString()?.toIntOrNull() ?: return@forEach; if(idx>=0&&count>0) counts[idx+1]=(counts[idx+1]?:0)+count } }; return SummaryResult(counts)}
        override fun buildPclick(source:BookSource,bookId:String,chapterId:String,pid:Int,chapterUrl:String?):String{ val url=source.bookSourceUrl.trimEnd('/')+COMMENTS_ROOT+"index.php/ui/"+sources(chapterUrl)+"/$bookId/$chapterId/${pid-1}"; return "java.openUrl('$url');" }
    }
}
