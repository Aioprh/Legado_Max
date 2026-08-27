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
        // 注意：仅缓存"非空"摘要。首次打开时远程章节段评数据（review_list）可能尚未就绪或瞬时拉取失败，
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
        // 拉取详情以补全 tocUrl，后续取目录用
        val full = runCatching { WebBook.getBookInfoAwait(source, remote) }.getOrDefault(remote)
        synchronized(remoteBookCache) { remoteBookCache[key] = full }
        return full
    }

    /**
     * 精确匹配（书名+作者完全相等）失败时的降级搜索：
     * 重新按书名搜索一次，用归一化后的书名/作者做模糊匹配。
     */
    private suspend fun fuzzySearchRemoteBook(source: BookSource, name: String, author: String): Book? {
        val list = runCatching { WebBook.searchBookAwait(source, name) }.getOrNull()
            ?: return null
        if (list.isEmpty()) return null
        AppLog.putReaderDebug("本地书段评: 模糊搜索《$name》返回 ${list.size} 条（书源${source.bookSourceName}）")
        val n = normalizeTitle(name)
        val a = normalizeTitle(author)
        // 1) 书名归一化相等
        list.firstOrNull { normalizeTitle(it.name) == n }?.toBook()?.let { return it }
        // 2) 作者归一化相等
        if (a.isNotEmpty()) {
            list.firstOrNull { normalizeTitle(it.author) == a }?.toBook()?.let { return it }
        }
        // 3) 书名互相包含（忽略标点/空白）
        list.firstOrNull {
            val bn = normalizeTitle(it.name)
            bn.isNotEmpty() && (bn.contains(n) || n.contains(bn))
        }?.toBook()?.let { return it }
        // 4) 兜底取第一条
        return list.first().toBook()
    }

    /** 神魔小说等需登录的后端：未带登录会话时提示登录，便于区分“没登录”与“真没这本书” */
    private fun loginHint(source: BookSource): String {
        if (!source.bookSourceUrl.contains("shenmoxs.top", ignoreCase = true)) return ""
        val ck = runCatching { CookieStore.getCookie("https://shenmoxs.top") }.getOrDefault("")
        return if (Regex("admin_session=[^;]+").containsMatchIn(ck)) {
            ""
        } else {
            "（神魔小说书源未登录，搜索被拒；请先到书源登录页登录）"
        }
    }

    /** 把本地章节标题映射到远程章节 URL（一次拉取整书目录并缓存） */
    private suspend fun getRemoteChapterUrl(
        book: Book,
        chapter: BookChapter,
        source: BookSource,
        remoteBook: Book
    ): String? {
        val key = "${book.bookUrl}|${source.bookSourceUrl}"
        val cached = synchronized(chapterMapCache) { chapterMapCache.containsKey(key) }
        val map = if (cached) {
            synchronized(chapterMapCache) { chapterMapCache[key] }
        } else {
            // 网络拉取目录在锁外执行，避免挂起点进入临界区
            fetchChapterMap(source, remoteBook).also {
                synchronized(chapterMapCache) { chapterMapCache[key] = it }
            }
        }
        if (map == null) return null
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

    /** 章节/书名归一化：去空白、去书名号等成对标点、全角数字/字母转半角、全角冒号转半角，用于模糊匹配 */
    private fun normalizeTitle(title: String): String {
        val sb = StringBuilder(title.length)
        for (c in title) {
            when {
                c == ' ' || c == '\u3000' || c == '\t' -> Unit
                c in "《》（）()【】〔〕「」『』" -> Unit
                c in '\uFF10'..'\uFF19' -> sb.append(c - 0xFEE0) // 全角数字
                c in '\uFF21'..'\uFF3A' -> sb.append(c - 0xFEE0) // 全角大写字母
                c in '\uFF41'..'\uFF5A' -> sb.append(c - 0xFEE0) // 全角小写字母
                c == '：' -> sb.append(':')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 用书源自带请求头/Cookie 请求指定地址，返回响应正文；可额外传入请求头 */
    private suspend fun fetchBody(
        source: BookSource,
        url: String,
        headerMapF: Map<String, String>? = null
    ): String? {
        return runCatching {
            val analyzeUrl = AnalyzeUrl(
                mUrl = url,
                baseUrl = source.bookSourceUrl,
                source = source,
                coroutineContext = currentCoroutineContext(),
                headerMapF = headerMapF
            )
            // 去除响应体开头的 UTF-8 BOM：部分接口（如 pl.aadcn.cn）返回带 BOM 的 JSON，
            // 直接交给 json-smart 解析会导致路径读取返回 null（静默变空，无异常抛出）。
            analyzeUrl.getStrResponseAwait().body()?.trimStart('\uFEFF')
        }.getOrNull()
    }

    /** 从 URL 中提取 `xxx=<数字>` 的 ID（兼容 ? 与 & 分隔） */
    private fun pickId(url: String, param: String): String? {
        return Regex("""(?:[?&]|^)$param=(\d+)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(1)
    }

    /** 解析段评摘要：段落号 -> 评论数。pidKeys/countKeys 按大小写不敏感匹配 */
    private fun parseCounts(
        body: String,
        listPath: String,
        pidKeys: List<String>,
        countKeys: List<String>
    ): Map<Int, Int> {
        return runCatching {
            val rc = jsonPath.parse(body)
            val list = runCatching { rc.read<List<Any?>>(listPath) }.getOrNull()
                ?: return@runCatching emptyMap()
            val result = HashMap<Int, Int>()
            list.mapNotNull { it as? Map<*, *> }.forEach { map ->
                val pid = firstIntValue(map, pidKeys) ?: return@forEach
                if (pid <= 0) return@forEach
                val count = firstIntValue(map, countKeys) ?: 0
                result[pid] = count
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun firstIntValue(map: Map<*, *>, keys: List<String>): Int? {
        for (key in keys) {
            for ((k, v) in map) {
                if (k != null && k.toString().equals(key, ignoreCase = true) && v != null) {
                    v.toString().toIntOrNull()?.let { return it }
                    break
                }
            }
        }
        return null
    }

    /**
     * 解析番茄段评摘要：review_list 里的 paragraphId 是"原始行号"（含空行，1基），
     * 而本地段评注入按"非空段落号"计数（与书源 jsLib 的 fqGetComments 一致）。
     * 这里结合章节正文把原始行号换算成非空段落号，避免正文带空行时气泡错位；
     * 同时带出远程非空段落（供跨书源文本对齐）与各段请求接口用的 paragraphId
     * （paraIndex 优先，缺省取原始行号，与书源 jsLib 的 pIndex 一致）。
     * 取不到正文映射时退回原始行号（退化为 [parseCounts] 行为）。
     */
    private fun parseFanqieCounts(body: String): SummaryResult {
        return runCatching {
            val rc = jsonPath.parse(body)
            val content = runCatching { rc.read<String>("$.content") }.getOrNull()
                ?: runCatching { rc.read<String>("$.data.content") }.getOrNull()
            val list = runCatching { rc.read<List<Any?>>("$.review_list") }.getOrNull()
                ?: runCatching { rc.read<List<Any?>>("$.data.review_list") }.getOrNull()
                ?: return@runCatching SummaryResult()
            // 与书源 jsLib fqGetComments 一致：含 <p> 时按 <p> 分段，否则按 \n 分段。
            // 注意必须先去掉首个 <p> 再 split，否则 "<p>a</p><p>b</p>".split("<p>") 会得到
            // ["", "a</p>", "b</p>"] 前导空串，导致 paragraphId>=2 的段落号整体错位（评论数挂错段）。
            val rawParagraphs = if (content == null) {
                emptyList()
            } else if (content.contains("<p>", ignoreCase = true)) {
                content.replace("\r\n", "\n").replace("\r", "\n")
                    .replaceFirst("<p>", "", ignoreCase = true)
                    .split(Regex("<p>", RegexOption.IGNORE_CASE))
            } else {
                content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
            }
            val remoteParagraphs = rawParagraphs.map { it.trim() }.filter { it.isNotEmpty() }
            // 原始行号(1基) -> 非空段落号(1基)
            val lineToPara = HashMap<Int, Int>()
            var para = 0
            rawParagraphs.forEachIndexed { i, line ->
                if (line.trim().isNotEmpty()) {
                    para++
                    lineToPara[i + 1] = para
                }
            }
            val counts = HashMap<Int, Int>()
            val apiPids = HashMap<Int, Int>()
            list.mapNotNull { it as? Map<*, *> }.forEach { map ->
                val rawLine = firstIntValue(map, listOf("paragraphId", "ParagraphId"))
                    ?: return@forEach
                val paraNum = lineToPara[rawLine] ?: rawLine
                if (paraNum <= 0) return@forEach
                val count = firstIntValue(
                    map, listOf("textCount", "TextCount", "commentCount", "CommentCount")
                ) ?: 0
                val apiPid = firstIntValue(map, listOf("paraIndex", "ParaIndex")) ?: rawLine
                counts[paraNum] = count
                apiPids[paraNum] = apiPid
            }
            SummaryResult(counts, apiPids, remoteParagraphs)
        }.getOrDefault(SummaryResult())
    }

    /**
     * 对有评论的段落后方注入 dp: 气泡。
     * 生成格式与书源 ruleContent 一致：`<img src="dp:<count>,{\"pclick\":\"<脚本>\",\"status\":\"normal\"}">`。
     * 提供了远程正文段落时走文本对齐定位，否则按非空段落序号定位。
     */
    private fun injectBubbles(
        content: String,
        source: BookSource,
        adapter: ParagraphAdapter,
        bookId: String,
        chapterId: String,
        chapterUrl: String,
        summary: SummaryResult
    ): String {
        return if (summary.remoteParagraphs.isNotEmpty()) {
            injectByTextAlign(content, source, adapter, bookId, chapterId, chapterUrl, summary)
        } else {
            injectByPosition(content, source, adapter, bookId, chapterId, chapterUrl, summary.counts)
        }
    }

    /** 构造气泡 option JSON：pclick 固定；count>99 时附加 displayText=99+，使气泡上限显示“99+” */
    private fun bubbleOption(pclick: String, count: Int): String {
        return buildString {
            append("{\\\"pclick\\\":\\\"").append(pclick).append("\\\"")
            if (count > 99) append(",\\\"displayText\\\":\\\"99+\\\"")
            append(",\\\"status\\\":\\\"normal\\\"}")
        }
    }

    /** 按非空段落序号定位注入（起点等段落结构与远程基本一致的情况） */
    private fun injectByPosition(
        content: String,
        source: BookSource,
        adapter: ParagraphAdapter,
        bookId: String,
        chapterId: String,
        chapterUrl: String,
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
                val pclick = adapter.buildPclick(source, bookId, chapterId, pid, chapterUrl)
                if (pclick.isNotBlank()) {
                    val option = bubbleOption(pclick, count)
                    out.add("$line<img src=\"dp:$count,$option\">")
                } else {
                    out.add(line)
                }
            } else {
                out.add(line)
            }
        }
        return out.joinToString("\n")
    }

    /**
     * 文本对齐注入（跨书源，如其他书源的书接入神魔番茄段评）：
     * 本地章节与远程章节分段不一定一致，先把远程正文段落与本地段落按文字匹配，
     * 再把段评数挂到正确的本地段落后方。paragraphId 传远程段落对应的接口编号。
     */
    private fun injectByTextAlign(
        content: String,
        source: BookSource,
        adapter: ParagraphAdapter,
        bookId: String,
        chapterId: String,
        chapterUrl: String,
        summary: SummaryResult
    ): String {
        val lines = content.replace("\r\n", "\n").split("\n")
        val localParas = ArrayList<String>()
        val lineIndexOfPara = ArrayList<Int>()
        lines.forEachIndexed { i, line ->
            if (line.trim().isNotEmpty()) {
                localParas.add(line)
                lineIndexOfPara.add(i)
            }
        }
        val align = alignLocalToRemote(localParas, summary.remoteParagraphs)
        val out = lines.toMutableList()
        for (pi in localParas.indices) {
            val ri = align[pi]
            if (ri < 0) continue
            val remotePara = ri + 1 // 远程非空段落号(1基)
            val count = summary.counts[remotePara] ?: 0
            if (count > 0) {
                val apiPid = summary.apiPids[remotePara] ?: remotePara
                val pclick = adapter.buildPclick(source, bookId, chapterId, apiPid, chapterUrl)
                if (pclick.isNotBlank()) {
                    val option = bubbleOption(pclick, count)
                    val lineIndex = lineIndexOfPara[pi]
                    out[lineIndex] = "${out[lineIndex]}<img src=\"dp:$count,$option\">"
                }
            }
        }
        return out.joinToString("\n")
    }

    /** 文本对齐：把本地非空段落映射到远程非空段落序号（-1=本地多出/未匹配）。按顺序约束匹配。 */
    private fun alignLocalToRemote(local: List<String>, remote: List<String>): IntArray {
        // 预归一化：避免每轮匹配都重复做正则替换（O(n*m) 场景下是主要性能开销）
        val normLocal = local.map { normalizePara(it) }
        val normRemote = remote.map { normalizePara(it) }
        val result = IntArray(local.size) { -1 }
        var j = 0
        normLocal.indices.forEach { i ->
            val nl = normLocal[i]
            if (nl.isEmpty()) return@forEach
            var found = -1
            for (k in j until normRemote.size) {
                val nr = normRemote[k]
                if (nr.isNotEmpty() && (nr == nl || nr.contains(nl) || nl.contains(nr))) {
                    found = k
                    break
                }
            }
            if (found >= 0) {
                result[i] = found
                j = found + 1
            }
        }
        return result
    }

    /** 段落文本归一化用的正则（预编译，避免每次匹配重新编译） */
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /** 段落文本归一化：去 HTML 标签、空白，用于跨书源比对 */
    private fun normalizePara(s: String): String {
        return HTML_TAG_REGEX.replace(s, "").replace(WHITESPACE_REGEX, "").trim()
    }

    /**
     * 生成点击气泡时执行的 pclick 脚本：java.showParagraphComments(JSON.stringify(cfg))。
     * bookId/chapterId 已内联进各 URL，脚本不依赖外部变量，可独立执行。
     */
    private fun buildPclickScript(
        listPath: String,
        totalPath: String,
        commentsUrl: String,
        repliesUrl: String,
        replyListPath: String,
        audioUrl: String,
        pageSize: Int,
        fields: ParagraphCommentConfig.FieldConfig,
        replyFields: ParagraphCommentConfig.FieldConfig = fields,
        sortEnabled: Boolean = true
    ): String {
        return buildString {
            append("var cfg={")
            append("listPath:'").append(listPath).append("',")
            append("totalPath:'").append(totalPath).append("',")
            append("commentsUrl:'").append(commentsUrl).append("',")
            append("repliesUrl:'").append(repliesUrl).append("',")
            append("replyListPath:'").append(replyListPath).append("',")
            if (audioUrl.isNotBlank()) append("audioUrl:'").append(audioUrl).append("',")
            append("pageSize:").append(pageSize).append(",")
            append("sortEnabled:").append(sortEnabled).append(",")
            append("fields:").append(fieldsScript(fields)).append(",")
            append("replyFields:").append(fieldsScript(replyFields))
            append("};")
            append("java.showParagraphComments(JSON.stringify(cfg));")
        }
    }

    private fun fieldsScript(f: ParagraphCommentConfig.FieldConfig): String {
        fun q(s: String) = if (s.isBlank()) "''" else "'$s'"
        return buildString {
            append("{nickname:").append(q(f.nickname)).append(",")
            append("avatar:").append(q(f.avatar)).append(",")
            append("level:").append(q(f.level)).append(",")
            append("ip:").append(q(f.ip)).append(",")
            append("content:").append(q(f.content)).append(",")
            append("agree:").append(q(f.agree)).append(",")
            append("oppose:").append(q(f.oppose)).append(",")
            append("time:").append(q(f.time)).append(",")
            append("floor:").append(q(f.floor)).append(",")
            append("id:").append(q(f.id)).append(",")
            append("rootId:").append(q(f.rootId)).append(",")
            append("replyCount:").append(q(f.replyCount)).append(",")
            append("replyTo:").append(q(f.replyTo)).append("}")
        }
    }

    /**
     * 段评摘要结果：
     * - [counts]：远程"非空段落号"(1基) -> 评论数
     * - [apiPids]：远程非空段落号 -> 请求段评接口用的 paragraphId（缺省用段落号本身）
     * - [remoteParagraphs]：远程正文非空段落，用于跨书源文本对齐定位（为空则退回按段落序号定位）
     */
    data class SummaryResult(
        val counts: Map<Int, Int> = emptyMap(),
        val apiPids: Map<Int, Int> = emptyMap(),
        val remoteParagraphs: List<String> = emptyList()
    )

    /** 段评书源适配器：负责提取 ID、拉取段评摘要、生成点击配置 */
    private interface ParagraphAdapter {
        fun match(source: BookSource): Boolean
        fun extractBookId(bookUrl: String, chapterUrl: String): String?
        fun extractChapterId(chapterUrl: String): String?

        /**
         * 拉取各段评论数。部分站点（如神魔小说的番茄段评）段计数随章节正文一起返回，
         * 需要 chapterUrl 才能取到，故额外传入远程章节 URL（无该需求的可忽略）。
         */
        suspend fun fetchSummaryCounts(
            source: BookSource,
            bookId: String,
            chapterId: String,
            chapterUrl: String? = null
        ): SummaryResult

        fun buildPclick(
            source: BookSource,
            bookId: String,
            chapterId: String,
            pid: Int,
            chapterUrl: String? = null
        ): String
    }

    // ---------- 通用/默认适配器（起点系镜像站 comments.php） ----------

    private object GenericAdapter : ParagraphAdapter {
        private val COMMENTS_ENDPOINT_REGEX =
            Regex("""https?://[^'"\s]*?(?:comments?|reviews?)\.[a-z]+""", RegexOption.IGNORE_CASE)
        private val SUMMARY_LIST_PATHS = listOf(
            "$.Data.Getparagraphscommentcounts.DataList",
            "$.Data.DataList",
            "$.Data.Paragraphs"
        )

        override fun match(source: BookSource): Boolean = true

        override fun extractBookId(bookUrl: String, chapterUrl: String): String? =
            pickId(bookUrl, "book_id") ?: pickId(chapterUrl, "book_id")

        override fun extractChapterId(chapterUrl: String): String? = pickId(chapterUrl, "chapter_id")

        override suspend fun fetchSummaryCounts(
            source: BookSource,
            bookId: String,
            chapterId: String,
            chapterUrl: String?
        ): SummaryResult {
            val endpoint = extractCommentsEndpoint(source) ?: return SummaryResult()
            val body = fetchBody(
                source,
                "$endpoint?action=summary&book_id=$bookId&chapter_id=$chapterId"
            ) ?: return SummaryResult()
            val listPath = SUMMARY_LIST_PATHS.firstOrNull {
                runCatching { jsonPath.parse(body).read<List<Any?>>(it) }.getOrNull() != null
            } ?: return SummaryResult()
            return SummaryResult(
                parseCounts(
                    body, listPath,
                    pidKeys = listOf("ParagraphId", "paragraphId"),
                    countKeys = listOf("CommentCount", "commentCount")
                )
            )
        }

        override fun buildPclick(
            source: BookSource,
            bookId: String,
            chapterId: String,
            pid: Int,
            chapterUrl: String?
        ): String {
            val endpoint = extractCommentsEndpoint(source) ?: return ""
            return buildPclickScript(
                listPath = "$.Data.DataList",
                totalPath = "$.Data.TotalCount",
                commentsUrl = "$endpoint?action=paragraph&book_id=$bookId&chapter_id=$chapterId" +
                    "&paragraph_id=$pid&type=text&page=[page]&page_size=[pageSize]",
                repliesUrl = "$endpoint?action=replies&book_id=$bookId&chapter_id=$chapterId" +
                    "&review_id=[reviewId]&root_review_id=[rootId]&page=1&page_size=[pageSize]",
                replyListPath = "$.Data.DataList",
                audioUrl = "",
                pageSize = 20,
                fields = ParagraphCommentConfig.FieldConfig()
            )
        }

        /** 从书源正文规则（ruleContent.content）中提取段评接口地址 */
        private fun extractCommentsEndpoint(source: BookSource): String? {
            val content = source.getContentRule()?.content ?: return null
            return COMMENTS_ENDPOINT_REGEX.find(content)?.value?.trimEnd('?', '&')
        }
    }

    // ---------- 神魔小说（shenmoxs.top，起点/番茄段评） ----------

    private object ShenmoAdapter : ParagraphAdapter {
        private const val API = "https://shenmoxs.top"
        private const val QD_SUMMARY = "https://m.qidian.com/majax/chapterReview/reviewSummary"

        override fun match(source: BookSource): Boolean =
            source.bookSourceUrl.contains("shenmoxs.top", ignoreCase = true)

        /** 判断是否为番茄书：番茄书详情 URL 带 source=fanqie，章节正文 URL 带 item_id */
        private fun isFanqie(bookUrl: String, chapterUrl: String?): Boolean =
            bookUrl.contains("source=fanqie", ignoreCase = true) ||
                chapterUrl?.contains("item_id=", ignoreCase = true) == true

        override fun extractBookId(bookUrl: String, chapterUrl: String): String? =
            if (isFanqie(bookUrl, chapterUrl)) {
                // 番茄：正文 URL 里的 book_id 即番茄 bookId
                pickId(chapterUrl, "book_id") ?: pickId(bookUrl, "bookId")
            } else {
                pickId(bookUrl, "bookId") ?: pickId(chapterUrl, "bookId")
            }

        override fun extractChapterId(chapterUrl: String): String? =
            if (chapterUrl.contains("item_id=", ignoreCase = true)) {
                pickId(chapterUrl, "item_id")
            } else {
                pickId(chapterUrl, "chapterId") ?: pickId(chapterUrl, "chapter_id")
            }

        override suspend fun fetchSummaryCounts(
            source: BookSource,
            bookId: String,
            chapterId: String,
            chapterUrl: String?
        ): SummaryResult {
            if (chapterUrl != null && chapterUrl.contains("item_id=", ignoreCase = true)) {
                // 番茄段评：段计数随章节正文（review_list）一起返回；
                // 用 parseFanqieCounts 把原始行号换算成非空段落号，并带出远程段落做文本对齐
                val body = fetchBody(source, chapterUrl) ?: return SummaryResult()
                return parseFanqieCounts(body)
            }
            val token = qidianToken()
            val url = "$QD_SUMMARY?bookId=$bookId&chapterId=$chapterId&_csrfToken=$token"
            // headerMapF 会整体替换书源请求头，因此需同时带上 UA
            val body = fetchBody(
                source, url,
                headerMapF = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                    "Cookie" to "qd_client_id=$token; _csrfToken=$token"
                )
            ) ?: return SummaryResult()
            return SummaryResult(
                parseCounts(
                    body, "$.data.list",
                    pidKeys = listOf("paragraphId", "ParagraphId"),
                    countKeys = listOf("textCount", "TextCount", "commentCount", "CommentCount")
                )
            )
        }

        override fun buildPclick(
            source: BookSource,
            bookId: String,
            chapterId: String,
            pid: Int,
            chapterUrl: String?
        ): String {
            val session = adminSession()
            val fanqie = chapterUrl?.contains("item_id=", ignoreCase = true) == true
            val sourceParam = if (fanqie) "&source=fanqie" else ""
            return buildPclickScript(
                listPath = "$.data.comments",
                totalPath = "$.data.pagination.totalCount",
                commentsUrl = "$API/chapter/comments?bookId=$bookId&chapterId=$chapterId" +
                    "&paragraphId=$pid$sourceParam&kind=paragraph&page=[page]&pageSize=[pageSize]$session",
                repliesUrl = "$API/chapter/comment-replies?bookId=$bookId&chapterId=$chapterId" +
                    "&paragraphId=$pid$sourceParam&commentId=[reviewId]&kind=paragraph&pageSize=20$session",
                replyListPath = "$.data.comments",
                audioUrl = "",
                pageSize = 20,
                // 番茄段评接口不按时间/回复数排序，仅保留实时模式；
                // 起点段评保留全部排序（最新/实时/回复最多）
                sortEnabled = !fanqie,
                // 番茄评论字段与起点不同（小写 snake_case），交给弹窗 DEFAULT_* 兜底解析
                fields = if (fanqie) {
                    ParagraphCommentConfig.FieldConfig()
                } else {
                    ParagraphCommentConfig.FieldConfig(
                        nickname = "$.UserName",
                        avatar = "$.UserHeadIcon",
                        level = "$.ShowTag",
                        ip = "$.IpLocation",
                        content = "$.Content",
                        agree = "$.AgreeAmount",
                        oppose = "$.OpposeAmount",
                        time = "$.CreateTime",
                        floor = "$.Floor",
                        id = "$.Id",
                        rootId = "$.Id",
                        replyCount = "$.ReviewCount",
                        replyTo = "$.RelatedUser"
                    )
                }
            )
        }

        /** 取 m.qidian.com 的 csrf 令牌；缺省生成一个随机令牌（与书源内 qdEnsureToken 一致） */
        private fun qidianToken(): String {
            val existing = runCatching {
                CookieStore.getKey("https://m.qidian.com", "_csrfToken")
            }.getOrDefault("")
            if (existing.isNotBlank()) return existing
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val random = Random()
            return (1..40).map { chars[random.nextInt(chars.length)] }.joinToString("")
        }

        /** 取源站登录会话（admin_session），拼成 `&_s=` 参数（与书源内 appSession 一致） */
        private fun adminSession(): String {
            val ck = runCatching { CookieStore.getCookie("https://shenmoxs.top") }.getOrDefault("")
            val m = Regex("admin_session=([^;]*)").find(ck)
            val v = m?.groupValues?.get(1) ?: return ""
            if (v.isBlank()) return ""
            return "&_s=" + runCatching { URLEncoder.encode(v, "UTF-8") }.getOrDefault(v)
        }
    }

    // ---------- 起点限免（同人小说网，pl.aadcn.cn） ----------

    private object QidianFullAdapter : ParagraphAdapter {
        private const val API = "https://pl.aadcn.cn/api/qidian_full_api.php"

        override fun match(source: BookSource): Boolean =
            source.bookSourceUrl.contains("m.qidian.com", ignoreCase = true) ||
                source.bookSourceUrl.contains("pl.aadcn.cn", ignoreCase = true) ||
                source.bookSourceUrl.contains("qd.aadcn.cn", ignoreCase = true)

        override fun extractBookId(bookUrl: String, chapterUrl: String): String? =
            pickId(bookUrl, "novelId")
                ?: pickId(bookUrl, "bookId")
                ?: pickId(bookUrl, "book_id")

        override fun extractChapterId(chapterUrl: String): String? {
            if (chapterUrl.startsWith("data:;base64,")) {
                val b64 = chapterUrl.substringAfter(";base64,").substringBefore(",")
                val decoded = runCatching {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                }.getOrNull()
                // 只接受纯数字章节 ID（跳过彩蛋/分卷等 data URL）
                if (decoded != null && decoded.isNotEmpty() && decoded.all { it.isDigit() }) {
                    return decoded
                }
                return null
            }
            return pickId(chapterUrl, "chapterId")
                ?: pickId(chapterUrl, "chapter_id")
                ?: Regex("\"chapterId\"\\s*:\\s*\"(\\d+)\"").find(chapterUrl)?.groupValues?.get(1)
        }

        override suspend fun fetchSummaryCounts(
            source: BookSource,
            bookId: String,
            chapterId: String,
            chapterUrl: String?
        ): SummaryResult {
            val body = fetchBody(
                source,
                "$API?action=paragraph_summary&book_id=$bookId&chapter_id=$chapterId"
            ) ?: return SummaryResult()
            return SummaryResult(
                parseCounts(
                    body, "$.data.summary",
                    pidKeys = listOf("ParagraphId", "paragraphId"),
                    countKeys = listOf("CommentCount", "commentCount", "TextCount", "textCount")
                )
            )
        }

        override fun buildPclick(
            source: BookSource,
            bookId: String,
            chapterId: String,
            pid: Int,
            chapterUrl: String?
        ): String {
            return buildPclickScript(
                listPath = "$.data.comments",
                totalPath = "$.data.total",
                commentsUrl = "$API?action=paragraph_comments&book_id=$bookId&chapter_id=$chapterId" +
                    "&paragraph_id=$pid&page=[page]&page_size=[pageSize]",
                repliesUrl = "$API?action=comment_replies&book_id=$bookId&chapter_id=$chapterId" +
                    "&paragraph_id=$pid&root_review_id=[rootId]&page=1&page_size=[pageSize]",
                replyListPath = "$.data.comments",
                audioUrl = "$API?action=paragraph_audio_comments&book_id=$bookId&chapter_id=$chapterId" +
                    "&paragraph_id=$pid&page=[page]&page_size=[pageSize]",
                pageSize = 20,
                fields = ParagraphCommentConfig.FieldConfig(
                    nickname = "$.user_info.user_name",
                    avatar = "$.user_info.user_avatar",
                    level = "$.raw.ShowTag",
                    ip = "$.raw.IpLocation",
                    content = "$.text",
                    agree = "$.digg_count",
                    oppose = "$.raw.OpposeAmount",
                    time = "$.create_timestamp",
                    floor = "$.floor",
                    id = "$.comment_id",
                    rootId = "$.raw.RootReviewId",
                    replyCount = "$.reply_count",
                    replyTo = "$.raw.RelatedUser"
                )
            )
        }
    }

    /**
     * 玖玖小说（番茄镜像，sunianxincue.love 系）：段评是"网页方案"。
     * 摘要走 distUrl（/api/fanqie/comments/{sources}/{book_id}/{item_id}，返回 distributions 列表），
     * 点击气泡用内嵌浏览器打开玖玖的段评网页（index.php/ui/...，含图片与发评论）。
     * para_index 为"非空正文段落号"（0基），与项目"1基非空段落号"相差 1。
     */
    private object JiuJiuAdapter : ParagraphAdapter {
        private const val COMMENTS_ROOT = "/api/fanqie/comments/"

        override fun match(source: BookSource): Boolean =
            source.bookSourceUrl.contains("sunianxincue.love", ignoreCase = true)

        /** book_id：玖玖详情/章节 URL 末尾数字 */
        override fun extractBookId(bookUrl: String, chapterUrl: String): String? =
            trailingNumber(bookUrl) ?: pickId(chapterUrl, "book_id")

        /** item_id：玖玖章节 URL 末尾数字 */
        override fun extractChapterId(chapterUrl: String): String? =
            pickId(chapterUrl, "item_id") ?: trailingNumber(chapterUrl)

        /** 从章节 URL /api/content/{sources}/... 提取站点标识，缺省 fanqie */
        private fun sources(chapterUrl: String?): String =
            chapterUrl?.let {
                Regex("""/api/content/([a-z]+)/""", RegexOption.IGNORE_CASE)
                    .find(it)?.groupValues?.get(1)
            } ?: "fanqie"

        private fun trailingNumber(u: String): String? =
            Regex("""/(\d+)(?=[/?]|$)""").find(u)?.groupValues?.get(1)

        override suspend fun fetchSummaryCounts(
            source: BookSource,
            bookId: String,
            chapterId: String,
            chapterUrl: String?
        ): SummaryResult {
            val url = source.bookSourceUrl.trimEnd('/') + COMMENTS_ROOT +
                sources(chapterUrl) + "/$bookId/$chapterId"
            val body = fetchBody(source, url) ?: return SummaryResult()
            val counts = HashMap<Int, Int>()
            runCatching {
                val list = jsonPath.parse(body).read<List<Any?>>("$.data.distributions")
                list.filterIsInstance<Map<*, *>>().forEach { d ->
                    val idx = d["para_index"]?.toString()?.toIntOrNull() ?: return@forEach
                    val count = d["count"]?.toString()?.toIntOrNull() ?: return@forEach
                    if (idx < 0 || count <= 0) return@forEach
                    counts[idx + 1] = (counts[idx + 1] ?: 0) + count
                }
            }
            return SummaryResult(counts)
        }

        override fun buildPclick(
            source: BookSource,
            bookId: String,
            chapterId: String,
            pid: Int,
            chapterUrl: String?
        ): String {
            val url = source.bookSourceUrl.trimEnd('/') + COMMENTS_ROOT +
                "index.php/ui/" + sources(chapterUrl) + "/$bookId/$chapterId/${pid - 1}"
            return "java.openUrl('$url');"
        }
    }
}
