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
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.widget.dialog.ParagraphCommentConfig
import com.google.gson.JsonObject
import io.legado.app.utils.INITIAL_GSON
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

    /**
     * 「本书源自身即段评源」模式的摘要缓存（带时间戳）。
     * 与 [summaryCache] 分开，允许短期过期：这是「发完段评回到本章能不能立刻看到新数字」的关键。
     */
    private val selfSummaryCache = HashMap<String, CachedSummary>()

    /**
     * 自身段评源模式的摘要缓存有效期（毫秒）。只用于合并「同一次打开章节」产生的重复调用
     * （翻页/预取会让 loadContent 多次触发注入），设太大会导致发完段评回来仍是旧数字，
     * 因此在「实时」与「去重」之间取一个很小的值。
     */
    private const val SELF_SUMMARY_TTL_MS = 3_000L

    /** 带时间戳的摘要缓存项 */
    private data class CachedSummary(val result: SummaryResult, val at: Long)

    /** 正文首行是否为章节标题（与书源 jsLib 里 fqDpInject / fqDpParaOf 的判据保持一致） */
    private val TITLE_LINE_REGEX = Regex("""^\s*第\s*[0-9零一二三四五六七八九十百千万两]+\s*[章节回]""")

    private val adapters: List<ParagraphAdapter> = listOf(
        ShenmoAdapter,
        QidianFullAdapter,
        JiuJiuAdapter,
        FanqieAdapter,
        GenericAdapter
    )

    /**
     * 「联网书自动接入段评」总开关。
     * 打开时，联网书只要其书源本身自带段评能力（[FanqieAdapter.looksLikeSelfSource]）就直接注入，
     * 无需逐本去「书籍详情 → 段评设置」开启；改成 false 可退回逐本手动开。
     */
    private const val AUTO_SELF_PARAGRAPH_COMMENT = true

    /**
     * 获取某本书的段评书源 URL。
     * 每本书单独配置（book.config.paragraphComment）优先；未单独开启或未选书源时，
     * 本地书回退全局配置；联网书在 [AUTO_SELF_PARAGRAPH_COMMENT] 打开且本书源自带段评能力时
     * 返回本书源自身，否则返回 null。
     *
     * 起点直连模式（[QidianParagraphComment.BINDING_PREFIX] + 作品 ID）由用户手动搜索或粘贴
     * 起点作品链接绑定，不再按书名/作者自动匹配；未绑定时不注入段评。
     */
    fun sourceUrlFor(book: Book): String? {
        val config = book.readConfig
        return when {
            config?.paragraphComment == true ->
                config.paragraphCommentSource?.takeIf { it.isNotBlank() }
                    ?: AppConfig.localParagraphSource?.takeIf { it.isNotBlank() }
            book.isLocal && AppConfig.localParagraphComment ->
                AppConfig.localParagraphSource?.takeIf { it.isNotBlank() }
            // 【番茄自动接入】联网书：本书书源本身就是番茄段评源 → 打开即有气泡，无需逐本开。
            // 只放「自带段评能力」的源（URL 含 fqnovel.com / fanqie）过去；其余联网书仍返回 null，
            // 避免为它们白跑一遍「搜索远程书 → 拉全量目录 → 章节名匹配」的跨源流程。
            AUTO_SELF_PARAGRAPH_COMMENT && !book.isLocal && book.origin.isNotBlank() &&
                FanqieAdapter.looksLikeSelfSource(book.origin) -> book.origin
            else -> null
        }
    }

    /**
     * 若需要为本书（本地书或联网书）注入段评气泡，返回注入后的正文；否则原样返回。
     * 任何一步失败（未开启、无书源、搜索不到、无目录、无段评等）都安全回退原正文。
     */
    suspend fun injectIfNeeded(book: Book, chapter: BookChapter, content: String): String {
        val sourceUrl = sourceUrlFor(book) ?: return content

        // 起点直连模式：完全绕过 BookSource，本地书（TXT/EPUB）与联网书通用。
        // 仅在已手动绑定起点作品 ID 时注入，未绑定时 QidianParagraphComment 会原样返回。
        if (QidianParagraphComment.isBinding(sourceUrl)) {
            return QidianParagraphComment.inject(book, chapter, content, sourceUrl)
        }

        val source = appDb.bookSourceDao.getBookSource(sourceUrl)?.takeIf { it.enabled }
            ?: return content
        val adapter = adapters.firstOrNull { it.match(source) } ?: return content
        // 【番茄实时气泡】本书源自身即段评源：直接用本书的 book_id/item_id 取数，
        // 不走「搜索远程书 → 拉全量目录 → 按章节名映射」那套跨源流程（对联网书既慢又易错）。
        if (adapter.selfSource && sourceUrl.trim() == book.origin.trim()) {
            return injectSelf(book, chapter, content, source, adapter)
        }
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

    /**
     * 按「正文非空行序（1 基）」注入气泡。
     * [apiPids] 把注入用的行序映射回站点自己的段号：番茄的 para_index 是 0 基且不含章节标题，
     * 与行序正好差 1；起点系的 ParagraphId 本身就等于行序，故缺省时两者相同。
     */
    private fun injectByPosition(content: String, source: BookSource, adapter: ParagraphAdapter, bookId: String, chapterId: String, chapterUrl: String, counts: Map<Int, Int>, apiPids: Map<Int, Int> = emptyMap()): String {
        val lines = content.replace("\r\n", "\n").split("\n"); var pid = 0; val out = ArrayList<String>(lines.size)
        for (line in lines) {
            if (line.trim().isEmpty()) { out.add(line); continue }
            pid++; val count = counts[pid] ?: 0
            if (count > 0) {
                val pclick = adapter.buildPclick(source, bookId, chapterId, apiPids[pid] ?: pid, chapterUrl)
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

    // ==================== 「本书源自身即段评源」注入（番茄实时气泡） ====================

    /**
     * 本书源自身即段评源：按本书的 book_id/item_id 取本章段评摘要并注入气泡。
     *
     * 与跨源模式（本地书借别的源看段评）的区别：
     * - 不搜索远程书、不拉远程目录、不做章节名匹配，直接用本书自己的 ID；
     * - 摘要有内存缓存但带 [SELF_SUMMARY_TTL_MS] 有效期，每次打开章节都会重新取数，
     *   所以发完段评回到本章即可看到新数字，不需要手动「刷新章节」；
     * - 适配器返回站点原始段号口径（番茄：para_index，0 基、不含章节标题），
     *   这里统一换算成 [injectByPosition] 需要的「正文非空行序（1 基）」。
     */
    private suspend fun injectSelf(
        book: Book,
        chapter: BookChapter,
        content: String,
        source: BookSource,
        adapter: ParagraphAdapter
    ): String {
        val bookId = adapter.extractBookId(book.bookUrl, chapter.url)
        val chapterId = adapter.extractChapterId(chapter.url)
        if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) {
            AppLog.put("段评(本书源): 无法提取书籍/章节ID（bookId=$bookId chapterId=$chapterId）")
            return content
        }
        val cacheKey = "${source.bookSourceUrl}|${chapter.url}"
        val now = System.currentTimeMillis()
        val cached = synchronized(selfSummaryCache) { selfSummaryCache[cacheKey] }
        val summary = if (cached != null && cached.result.counts.isNotEmpty()
            && now - cached.at < SELF_SUMMARY_TTL_MS
        ) {
            cached.result
        } else {
            adapter.fetchSummaryCounts(source, bookId, chapterId, chapter.url).also {
                synchronized(selfSummaryCache) {
                    selfSummaryCache[cacheKey] = CachedSummary(it, System.currentTimeMillis())
                }
            }
        }
        if (summary.counts.isEmpty()) {
            AppLog.putReaderDebug("段评(本书源): 章节[${chapter.title}]暂无段评")
            return content
        }
        val lines = content.replace("\r\n", "\n").split("\n")
        val nonEmpty = lines.indices.filter { lines[it].isNotBlank() }
        val offset =
            if (nonEmpty.isNotEmpty() && TITLE_LINE_REGEX.containsMatchIn(lines[nonEmpty[0]].trim())) 1 else 0
        val counts = HashMap<Int, Int>()
        val apiPids = HashMap<Int, Int>()
        val paragraphs = HashMap<Int, String>()
        nonEmpty.forEachIndexed { k, lineIndex ->
            val para = k - offset
            if (para < 0) return@forEachIndexed
            paragraphs[para] = lines[lineIndex].trim()
            val count = summary.counts[para] ?: 0
            if (count > 0) {
                counts[k + 1] = count
                apiPids[k + 1] = para
            }
        }
        if (counts.isEmpty()) {
            AppLog.putReaderDebug("段评(本书源): 章节[${chapter.title}]摘要段号与正文对不上")
            return content
        }
        adapter.setChapterParagraphs(chapter.url, paragraphs)
        val injected =
            injectByPosition(content, source, adapter, bookId, chapterId, chapter.url, counts, apiPids)
        if (injected != content) {
            AppLog.putReaderDebug("段评(本书源): 章节[${chapter.title}] 已注入 ${counts.size} 个段评气泡")
        }
        return injected
    }

    // ==================== 番茄段评适配器 ====================

    /**
     * 番茄小说（api5-normal-sinfonlinec.fqnovel.com）段评适配器。
     *
     * 识别方式：按 [BookSource.bookSourceUrl] 的域名特征匹配（与 ShenmoAdapter / QidianFullAdapter
     * 的写法一致）。**不要**用「扫 ruleContent 里有没有 ugc/idea/list/v1」来判定 —— 本套书源把接口
     * 调用封装在 jsLib 的 fqIdeaReq 里，ruleContent 只有一句 `@js: fqContentDp(...)`，扫不到接口串。
     * 「当前书源本身就是段评源」的真正判据在 [injectIfNeeded] 里：段评源 URL == 本书的 origin。
     */
    private object FanqieAdapter : ParagraphAdapter {

        private const val IDEA_LIST_API =
            "https://api5-normal-sinfonlinec.fqnovel.com/reading/ugc/idea/list/v1/"

        /**
         * 「书源自带段评能力」的共用判据（只按 URL）。
         * 供 [sourceUrlFor] 的联网书自动接入分支与 [match] 复用：
         * URL 含 `fqnovel.com` / `fanqie` 即认定为番茄段评源。
         */
        fun looksLikeSelfSource(url: String): Boolean =
            url.contains("fqnovel.com", true) || url.contains("fanqie", true)

        override fun match(source: BookSource): Boolean {
            return looksLikeSelfSource(source.bookSourceUrl) ||
                source.bookSourceName.contains("番茄")
        }

        /** 支持「本书源自身即段评源」 */
        override val selfSource: Boolean get() = true

        override fun extractBookId(bookUrl: String, chapterUrl: String): String? {
            val book = fanqiePlain(bookUrl)
            val chapter = fanqiePlain(chapterUrl)
            return pickId(book, "book_id") ?: pickId(book, "bookId")
            ?: pickId(chapter, "book_id") ?: pickId(chapter, "bookId")
        }

        override fun extractChapterId(chapterUrl: String): String? {
            val plain = fanqiePlain(chapterUrl)
            return pickId(plain, "item_ids") ?: pickId(plain, "item_id") ?: pickId(plain, "itemId")
        }

        override suspend fun fetchSummaryCounts(
            source: BookSource,
            bookId: String,
            chapterId: String,
            chapterUrl: String?
        ): SummaryResult {
            val custom = source.getReviewRule()?.summaryUrl?.takeIf { it.isNotBlank() }
            val body = if (custom == null) {
                // 番茄的段评概览接口是免签的普通 GET（书源侧 fqIdeaReq 同样没有签名头），
                // 直接请求即可；带签名的那几个接口（正文/搜索/目录）走书源 JS，不在这里重复实现。
                fetchBody(
                    source,
                    "$IDEA_LIST_API?book_id=$bookId&item_id=$chapterId" +
                        "&item_version=1&query_type=1&sort=1&aid=1967"
                )
            } else {
                val rule = custom.trim()
                if (rule.startsWith("@js:", true)) {
                    // 源驱动口子：脚本在书源作用域执行（jsLib 可用）。
                    // 注意 jsLib 里的函数拿不到规则作用域的 java/source，需要桥就用参数传进去。
                    val code = rule.substring(4)
                        .replace("{bookId}", bookId)
                        .replace("{chapterId}", chapterId)
                    evalSourceJs(source, code)
                } else {
                    fetchBody(
                        source,
                        rule.replace("{bookId}", bookId).replace("{chapterId}", chapterId)
                    )
                }
            } ?: return SummaryResult()
            return parseFanqieIdea(body)
        }

        override fun buildPclick(
            source: BookSource,
            bookId: String,
            chapterId: String,
            pid: Int,
            chapterUrl: String?
        ): String {
            // pid = para_index（0 基、不含章节标题），与书源侧 fqDpInject / H5 面板口径一致，
            // 点击后走书源 jsLib 的 fqDpClick（H5 面板，含登录态、点赞/回复/删除/发段评）。
            // cB64 = 被点段落原文，供 H5 头部引用展示；base64 必须 NO_WRAP，
            // 且 pclick 里禁止出现双引号/反斜杠/大于号（会截断 <img src> 属性），字符串一律单引号。
            val text = paragraphText(chapterUrl, pid)
            val cB64 = if (text.isBlank()) "" else
                Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "fqDpClick.call(this, java, '$bookId', '$chapterId', '$pid', '', '$cB64')"
        }

        // ---- 段落原文缓存：气泡点击时 H5 面板显示「你评的是这一段」 ----

        private const val PARAGRAPH_CACHE_MAX = 8
        private val paragraphCache = LinkedHashMap<String, Map<Int, String>>()

        override fun setChapterParagraphs(chapterUrl: String?, paragraphs: Map<Int, String>) {
            val key = chapterUrl ?: return
            synchronized(paragraphCache) {
                paragraphCache[key] = paragraphs
                while (paragraphCache.size > PARAGRAPH_CACHE_MAX) {
                    val first = paragraphCache.keys.firstOrNull() ?: break
                    paragraphCache.remove(first)
                }
            }
        }

        override fun paragraphText(chapterUrl: String?, pid: Int): String {
            val key = chapterUrl ?: return ""
            return synchronized(paragraphCache) { paragraphCache[key]?.get(pid) }.orEmpty()
        }
    }

    /**
     * 番茄书源把 URL 存成 legado 的「标记」形式：`data:;base64,<b64>,{"type":"1"}`，或纯十六进制串。
     * 必须先还原出明文（如 `item_ids=123456&book_id=789`）再正则取 ID ——
     * 直接在标记串上取 ID 永远取不到，是接这套源最常见的坑。
     */
    private fun fanqiePlain(url: String): String {
        if (url.isBlank()) return ""
        Regex(""";base64,([A-Za-z0-9+/=]+)""").find(url)?.groupValues?.get(1)?.let { b64 ->
            runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val hex = url.trim()
        if (hex.isNotEmpty() && hex.length % 2 == 0 &&
            hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        ) {
            runCatching {
                String(
                    ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() },
                    Charsets.UTF_8
                )
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return url
    }

    /**
     * 在书源作用域里执行 JS：java 桥 = [AnalyzeRule]（有 ajax），原型链 = 书源 jsLib。
     * 仅给 ruleReview.summaryUrl 这类「源驱动」脚本使用；番茄默认路径直接请求，不经过这里。
     */
    private suspend fun evalSourceJs(source: BookSource, code: String): String? = runCatching {
        val rule = AnalyzeRule(null, source)
        rule.setCoroutineContext(currentCoroutineContext())
        rule.setBaseUrl(source.bookSourceUrl)
        rule.evalJS(code, null)?.toString()
    }.onFailure {
        AppLog.put("段评(番茄): 执行书源摘要脚本失败 ${it.localizedMessage}", it)
    }.getOrNull()

    /**
     * 解析番茄段评摘要：`data.idea_data` 是「段号 -> 对象」的 map，取其中 idea_count。
     * 返回的 key 是站点原始段号（番茄：para_index，0 基、不含章节标题）。
     */
    private fun parseFanqieIdea(body: String): SummaryResult = runCatching {
        val json = INITIAL_GSON.fromJson(body, JsonObject::class.java)
            ?: return@runCatching SummaryResult()
        val ideaObject = json.getAsJsonObject("data")?.getAsJsonObject("idea_data")
            ?: return@runCatching SummaryResult()
        val counts = HashMap<Int, Int>()
        ideaObject.entrySet().forEach { entry ->
            val para = entry.key.toIntOrNull() ?: return@forEach
            val count = runCatching {
                entry.value.asJsonObject.get("idea_count")?.asInt
            }.getOrNull() ?: 0
            if (para >= 0 && count > 0) counts[para] = count
        }
        SummaryResult(counts, HashMap(), emptyList())
    }.onFailure {
        AppLog.put("段评(番茄): 解析摘要失败 ${it.localizedMessage}")
    }.getOrDefault(SummaryResult())

    private interface ParagraphAdapter {
        fun match(source: BookSource): Boolean

        /** 是否支持「本书源自身即段评源」模式（联网书看自己书源的段评，跳过跨源搜索）。默认不支持。 */
        val selfSource: Boolean get() = false

        fun extractBookId(bookUrl: String, chapterUrl: String): String?
        fun extractChapterId(chapterUrl: String): String?
        suspend fun fetchSummaryCounts(source: BookSource, bookId: String, chapterId: String, chapterUrl: String? = null): SummaryResult
        fun buildPclick(source: BookSource, bookId: String, chapterId: String, pid: Int, chapterUrl: String? = null): String

        /** 注入前把「本章正文段落」（key = 站点段号）告知适配器，供气泡点击时引用原文展示。默认忽略。 */
        fun setChapterParagraphs(chapterUrl: String?, paragraphs: Map<Int, String>) {}

        /** 取被点段落原文（站点段号口径）。缺省返回空串。 */
        fun paragraphText(chapterUrl: String?, pid: Int): String = ""
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
