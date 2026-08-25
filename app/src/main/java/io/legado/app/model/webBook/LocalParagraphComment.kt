package io.legado.app.model.webBook

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.jsonPath
import kotlinx.coroutines.currentCoroutineContext

/**
 * 本地书段评：为本地书籍接入其他书源的段评。
 *
 * 当“本地书段评”开关开启且已选择“段评书源”时，读取本地书正文后：
 * 1. 用书名/作者在段评书源中精确搜索远程书，得到远程 bookUrl（含 book_id）；
 * 2. 拉取远程书详情与目录，按章节标题把本地章节映射到远程章节 URL（含 chapter_id）；
 * 3. 从书源正文规则里提取段评接口地址，请求该章的段评摘要，得到各段评论数；
 * 4. 对有评论的段落后方注入 dp: 气泡，点击后经 pclick 打开原生段评弹窗。
 *
 * 本地段落号按“非空行序号”近似远程段落号（与书源内 ruleContent 的取段逻辑一致），
 * 因此要求本地书与远程书的换行/分段落基本一致，才能准确定位。
 */
object LocalParagraphComment {

    /** 段评接口地址（comments.php / reviews.php 等），从书源正文规则中提取 */
    private val COMMENTS_ENDPOINT_REGEX =
        Regex("""https?://[^'"\s]*?(?:comments?|reviews?)\.[a-z]+""", RegexOption.IGNORE_CASE)

    /** 段评摘要接口返回的评论数列表候选路径（兼容不同站点命名） */
    private val SUMMARY_LIST_PATHS = listOf(
        "$.Data.Getparagraphscommentcounts.DataList",
        "$.Data.DataList",
        "$.Data.Paragraphs"
    )

    /** 本地书 bookUrl|sourceUrl -> 远程书（null 表示搜索失败，避免反复搜索） */
    private val remoteBookCache = HashMap<String, Book?>()

    /** 本地书 bookUrl|sourceUrl -> 本地章节标题 -> 远程章节 URL（null 表示拉取失败） */
    private val chapterMapCache = HashMap<String, Map<String, String>?>()

    /**
     * 若需要为本地书注入段评气泡，返回注入后的正文；否则原样返回。
     * 任何一步失败（未开启、无书源、搜索不到、无目录、无段评等）都安全回退原正文。
     */
    suspend fun injectIfNeeded(book: Book, chapter: BookChapter, content: String): String {
        if (!AppConfig.localParagraphComment) return content
        if (!book.isLocal) return content
        val source = AppConfig.localParagraphSource
            ?.let { appDb.bookSourceDao.getBookSource(it) }
            ?.takeIf { it.enabled }
            ?: return content
        val remoteBook = getRemoteBook(book, source) ?: return content
        val remoteChapterUrl = getRemoteChapterUrl(book, chapter, source, remoteBook)
            ?: return content
        val bookId = remoteBook.bookUrl.split("book_id=").getOrNull(1)?.substringBefore("&").orEmpty()
        val chapterId = remoteChapterUrl.split("chapter_id=").getOrNull(1)?.substringBefore("&").orEmpty()
        if (bookId.isBlank() || chapterId.isBlank()) {
            AppLog.put("本地书段评: 无法从书源 URL 提取 book_id/chapter_id")
            return content
        }
        val endpoint = extractCommentsEndpoint(source)
        if (endpoint.isNullOrBlank()) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]正文规则中未找到段评接口")
            return content
        }
        val summaryBody = fetchBody(
            source,
            "$endpoint?action=summary&book_id=$bookId&chapter_id=$chapterId"
        )
        if (summaryBody.isNullOrBlank()) {
            AppLog.put("本地书段评: 段评摘要接口请求失败（$endpoint）")
            return content
        }
        val counts = parseSummaryCounts(summaryBody)
        if (counts.isEmpty()) {
            AppLog.putReaderDebug("本地书段评: 章节[${chapter.title}]暂无段评")
            return content
        }
        val injected = injectBubbles(content, bookId, chapterId, endpoint, counts)
        if (injected != content) {
            AppLog.putReaderDebug("本地书段评: 章节[${chapter.title}] 已注入 ${counts.size} 个段评气泡")
        }
        return injected
    }

    /** 在段评书源中精确搜索本地书，得到远程书（含 bookUrl、tocUrl） */
    private suspend fun getRemoteBook(book: Book, source: BookSource): Book? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        synchronized(remoteBookCache) {
            if (remoteBookCache.containsKey(key)) {
                return remoteBookCache[key]
            }
        }
        val remote = WebBook.preciseSearchAwait(source, book.name, book.author).getOrNull()
        if (remote == null) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]未搜索到《${book.name}》(${book.author})")
            synchronized(remoteBookCache) { remoteBookCache[key] = null }
            return null
        }
        // 拉取详情以补全 tocUrl，后续取目录用
        val full = runCatching { WebBook.getBookInfoAwait(source, remote) }.getOrDefault(remote)
        synchronized(remoteBookCache) { remoteBookCache[key] = full }
        return full
    }

    /** 把本地章节标题映射到远程章节 URL（一次拉取整书目录并缓存） */
    private suspend fun getRemoteChapterUrl(
        book: Book,
        chapter: BookChapter,
        source: BookSource,
        remoteBook: Book
    ): String? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        synchronized(chapterMapCache) {
            if (!chapterMapCache.containsKey(key)) {
                chapterMapCache[key] = fetchChapterMap(source, remoteBook)
            }
        }
        val map = chapterMapCache[key] ?: return null
        // 依次尝试：精确标题 -> 去空白标题 -> 全角/半角归一化标题 -> 按章节序号兜底
        return map[chapter.title]
            ?: map[chapter.title.trim()]
            ?: map[normalizeTitle(chapter.title)]
            ?: map.entries.elementAtOrNull(chapter.index)?.value
    }

    private suspend fun fetchChapterMap(source: BookSource, remoteBook: Book): Map<String, String>? {
        val full = if (remoteBook.tocUrl.isNullOrBlank()) {
            runCatching { WebBook.getBookInfoAwait(source, remoteBook) }.getOrDefault(remoteBook)
        } else {
            remoteBook
        }
        if (full.tocUrl.isNullOrBlank()) {
            AppLog.put("本地书段评: 书源[${source.bookSourceName}]目录地址为空")
            return null
        }
        val chapters = WebBook.getChapterListAwait(source, full).getOrNull()
            ?: run {
                AppLog.put("本地书段评: 拉取目录失败（${full.tocUrl}）")
                return null
            }
        val map = LinkedHashMap<String, String>()
        chapters.forEach { map[it.title] = it.url }
        return map
    }

    /** 章节标题归一化：全角数字/字母转半角并去掉空白，用于本地/远程章节标题模糊匹配 */
    private fun normalizeTitle(title: String): String {
        val sb = StringBuilder(title.length)
        for (c in title) {
            when {
                c == ' ' || c == '\u3000' || c == '\t' -> Unit
                c in '\uFF10'..'\uFF19' -> sb.append(c - 0xFEE0) // 全角数字
                c in '\uFF21'..'\uFF3A' -> sb.append(c - 0xFEE0) // 全角大写字母
                c in '\uFF41'..'\uFF5A' -> sb.append(c - 0xFEE0) // 全角小写字母
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 从书源正文规则（ruleContent.content）中提取段评接口地址 */
    private fun extractCommentsEndpoint(source: BookSource): String? {
        val content = source.getContentRule()?.content ?: return null
        return COMMENTS_ENDPOINT_REGEX.find(content)?.value?.trimEnd('?', '&')
    }

    /** 用书源自带请求头/Cookie 请求指定地址，返回响应正文 */
    private suspend fun fetchBody(source: BookSource, url: String): String? {
        return runCatching {
            val analyzeUrl = AnalyzeUrl(
                mUrl = url,
                baseUrl = source.bookSourceUrl,
                source = source,
                coroutineContext = currentCoroutineContext()
            )
            analyzeUrl.getStrResponseAwait().body()
        }.getOrNull()
    }

    /** 解析段评摘要：段落号 -> 评论数 */
    private fun parseSummaryCounts(body: String): Map<Int, Int> {
        return runCatching {
            val rc = jsonPath.parse(body)
            val list = SUMMARY_LIST_PATHS.firstNotNullOfOrNull { path ->
                runCatching { rc.read<List<Any?>>(path) }.getOrNull()
            } ?: return@runCatching emptyMap()
            val result = HashMap<Int, Int>()
            list.mapNotNull { it as? Map<*, *> }.forEach { map ->
                val pid = (map["ParagraphId"] ?: map["paragraphId"])
                    ?.toString()?.toIntOrNull() ?: return@forEach
                val count = (map["CommentCount"] ?: map["commentCount"])
                    ?.toString()?.toIntOrNull() ?: 0
                result[pid] = count
            }
            result
        }.getOrDefault(emptyMap())
    }

    /**
     * 对有评论的段落后方注入 dp: 气泡。
     * 生成格式与书源 ruleContent 一致：`<img src="dp:<count>,{\"pclick\":\"<脚本>\",\"status\":\"normal\"}">`。
     */
    private fun injectBubbles(
        content: String,
        bookId: String,
        chapterId: String,
        endpoint: String,
        counts: Map<Int, Int>
    ): String {
        val lines = content.replace("\r\n", "\n").split("\n")
        var pid = 0
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            if (line.trim().isEmpty()) {
                out.add(line)
                continue
            }
            pid++
            val count = counts[pid] ?: 0
            if (count > 0) {
                val pclick = buildPclick(bookId, chapterId, endpoint, pid)
                val option = "{\\\"pclick\\\":\\\"$pclick\\\",\\\"status\\\":\\\"normal\\\"}"
                out.add("$line<img src=\"dp:$count,$option\">")
            } else {
                out.add(line)
            }
        }
        return out.joinToString("\n")
    }

    /** 生成点击气泡时执行的 pclick 脚本：调用 java.showParagraphComments 打开原生段评弹窗 */
    private fun buildPclick(bookId: String, chapterId: String, endpoint: String, pid: Int): String {
        return buildString {
            append("var B='").append(bookId).append("';")
            append("var C='").append(chapterId).append("';")
            append("var cfg={listPath:'$.Data.DataList',totalPath:'$.Data.TotalCount',")
            append("commentsUrl:'").append(endpoint)
            append("?action=paragraph&book_id='+B+'&chapter_id='+C+'&paragraph_id=").append(pid)
            append("&type=text&page=[page]&page_size=[pageSize]',")
            append("repliesUrl:'").append(endpoint)
            append("?action=replies&book_id='+B+'&chapter_id='+C+'&review_id=[reviewId]&root_review_id=[rootId]&page=1&page_size=[pageSize]',")
            append("pageSize:20};")
            append("java.showParagraphComments(JSON.stringify(cfg));")
        }
    }
}
