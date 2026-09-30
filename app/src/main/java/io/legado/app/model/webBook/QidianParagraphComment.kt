package io.legado.app.model.webBook

import android.util.Base64
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.GSON
import io.legado.app.utils.jsonPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 起点直连段评适配器。
 * 不依赖 BookSource，也不依赖已经关闭的同人源。
 */
object QidianParagraphComment {

    const val BINDING_PREFIX = "qidian://"

    private const val API_HOST = "https://druidv6.if.qidian.com/argus/api/"
    private const val CATALOG_URL = "https://m.qidian.com/book/%s/catalog/"

    private val client = OkHttpClient.Builder().build()
    private val catalogCache = HashMap<String, Map<String, String>>()
    private val summaryCache = HashMap<String, Map<Int, Int>>()

    fun isBinding(value: String?): Boolean = value?.startsWith(BINDING_PREFIX) == true

    data class SearchResult(val bookId: String, val name: String, val author: String = "")

    /** 从普通起点书籍链接中提取作品 ID。 */ 
    fun extractBookId(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.matches(Regex("\\d+"))) return text

        val decoded = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
        val patterns = listOf(
            Regex(
                "(?:qidian\\.com|book\\.qidian\\.com|m\\.qidian\\.com)[^\\d]{0,80}(?:info|book)[^\\d]{0,20}(\\d{5,})",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "/(?:info|book)/(\\d{5,})(?:/|[?#]|$)",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "[?&](?:bookId|bookid|bid)=(\\d{5,})",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """["']?(?:bookId|bookid|book_id|bid)["']?\s*[:=]\s*["']?(\d{5,})""",
                RegexOption.IGNORE_CASE
            )
        )
        return patterns.asSequence()
            .mapNotNull { it.find(decoded)?.groupValues?.getOrNull(1) }
            .firstOrNull()
    }

    /**
     * 解析起点 H5 分享作品链接。
     *
     * share-link?id=... 中的数字是分享链接 ID，不是作品 bookId。
     * 按分享链接转换工具的实际流程：
     *   1. GET share-link；
     *   2. 跟随 HTTP 30x；
     *   3. 优先从最终落地 URL / 重定向 Location 提取 bookId；
     *   4. 最后才从 HTML 中提取 bookId。
     */
    suspend fun resolveBookId(value: String?): String? = withContext(Dispatchers.IO) {
        // 普通 bookId / 书籍 URL 直接处理，避免无意义的网络请求。
        extractBookId(value)?.let { return@withContext it }

        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return@withContext null

        val shareId = Regex(
            """https?://h5\.if\.qidian\.com/h5/share-link(?:\?|%3F)id=(\d{5,})""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1) ?: return@withContext null

        val shareUrl = "https://h5.if.qidian.com/h5/share-link?id=" + shareId

        runCatching {
            val request = Request.Builder()
                .url(shareUrl)
                .header("User-Agent", WEB_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json")
                .header("Referer", "https://h5.if.qidian.com/")
                .build()

            // OkHttp 默认跟随重定向；response.request.url 即最终落地 URL。
            client.newCall(request).execute().use { response ->
                val candidates = ArrayList<String>()

                // 最终 URL
                candidates += response.request.url.toString()

                // 兼容没有被 OkHttp 自动消费掉的 Location
                response.header("Location")?.let(candidates::add)

                // 兼容 302 -> 301 -> 最终页面的多级重定向链。
                var previous = response.priorResponse
                while (previous != null) {
                    candidates += previous.request.url.toString()
                    previous.header("Location")?.let(candidates::add)
                    previous = previous.priorResponse
                }

                // 与 Python 工具一致：重定向 URL 都没有 bookId 时，再检查响应 HTML。
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    candidates += body
                    val doc = Jsoup.parse(body)

                    doc.select("meta[content],a[href],link[href]").forEach { element ->
                        element.attr("content")
                            .takeIf { it.isNotBlank() }
                            ?.let(candidates::add)
                        element.attr("href")
                            .takeIf { it.isNotBlank() }
                            ?.let(candidates::add)
                    }

                    doc.select("script").forEach { script ->
                        script.data()
                            .takeIf { it.isNotBlank() }
                            ?.let(candidates::add)
                    }
                }

                candidates.asSequence()
                    .mapNotNull { extractBookId(it) }
                    .firstOrNull()
            }
        }.getOrNull()
    }
    suspend fun searchBooks(keyword: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val urls = listOf(
            "https://www.qidian.com/so/" + java.net.URLEncoder.encode(q, "UTF-8"),
            "https://m.qidian.com/search?kw=" + java.net.URLEncoder.encode(q, "UTF-8")
        )
        for (url in urls) {
            val body = httpGet(url) ?: continue
            val results = parseSearchResults(body)
            if (results.isNotEmpty()) return@withContext results.take(20)
        }
        emptyList()
    }

    private fun parseSearchResults(body: String): List<SearchResult> {
        val doc = Jsoup.parse(body)
        val result = LinkedHashMap<String, SearchResult>()
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")
            val id = Regex("/(?:info|book)/(\\d{5,})(?:/|[?#]|$)").find(href)?.groupValues?.get(1)
                ?: extractBookId(href) ?: return@forEach
            val box = link.parents().firstOrNull { parent ->
                parent.select("a[href]").size <= 8 && parent.text().length in 2..500
            } ?: link
            val name = (link.attr("title").ifBlank { link.text() }).trim()
            if (name.isBlank() || name.length > 120) return@forEach
            val author = Regex("(?:作者|作\\\\者)\\s*[:：]?\\s*([^\\s|·/]{1,40})")
                .find(box.text())?.groupValues?.getOrNull(1).orEmpty()
            result.putIfAbsent(id, SearchResult(id, name, author))
        }
        return result.values.toList()
    }


    fun bookId(binding: String?): String? {
        if (!isBinding(binding)) return null
        val id = binding!!.removePrefix(BINDING_PREFIX).trim()
        return id.takeIf { it.matches(Regex("\\d+")) }
    }

    suspend fun inject(book: Book, chapter: BookChapter, content: String): String {
        val id = bookId(book.readConfig?.paragraphCommentSource) ?: return content
        val chapterId = resolveChapterId(id, chapter) ?: run {
            AppLog.putReaderDebug("本地书段评: 起点目录未匹配章节《" + chapter.title + "》")
            return content
        }

        val key = id + "|" + chapterId
        val counts = synchronized(summaryCache) { summaryCache[key] }
            ?: fetchSummary(id, chapterId).also {
                if (it.isNotEmpty()) synchronized(summaryCache) { summaryCache[key] = it }
            }

        if (counts.isEmpty()) return content
        return injectBubbles(content, id, chapterId, counts)
    }

    suspend fun testBinding(bookId: String): Boolean = fetchCatalog(bookId).isNotEmpty()

    private suspend fun resolveChapterId(bookId: String, chapter: BookChapter): String? {
        val map = synchronized(catalogCache) { catalogCache[bookId] }
            ?: fetchCatalog(bookId).also {
                if (it.isNotEmpty()) synchronized(catalogCache) { catalogCache[bookId] = it }
            }
        if (map.isEmpty()) return null

        val title = normalizeTitle(chapter.title)
        val core = normalizeChapterCore(chapter.title)
        return map[title]
            ?: map["core:" + core]?.takeIf { core.isNotEmpty() }
            ?: chapterNumber(chapter.title)?.let { map["number:" + it] }
            ?: map.entries.elementAtOrNull(chapter.index)?.value
    }

    private suspend fun fetchCatalog(bookId: String): Map<String, String> = withContext(Dispatchers.IO) {
        val body = httpGet(CATALOG_URL.format(bookId)) ?: return@withContext emptyMap()
        runCatching {
            val element = Jsoup.parse(body).selectFirst("#vite-plugin-ssr_pageContext")
                ?: return@runCatching emptyMap<String, String>()
            val pageContext = element.data().ifBlank { element.html() }
            val chapters = jsonPath.parse(pageContext)
                .read<List<Any?>>("$.pageContext.pageProps.pageData.vs[*].cs[*]")

            val map = LinkedHashMap<String, String>()
            val cores = HashMap<String, MutableList<String>>()
            val numbers = HashMap<Int, MutableList<String>>()

            chapters.forEach { raw ->
                val item = raw as? Map<*, *> ?: return@forEach
                val title = firstString(item, "cN", "chapterName", "name", "title") ?: return@forEach
                val id = firstString(item, "id", "chapterId", "chapter_id") ?: return@forEach
                if (!id.matches(Regex("\\d+"))) return@forEach

                val normalized = normalizeTitle(title)
                if (normalized.isNotEmpty()) map[normalized] = id

                val core = normalizeChapterCore(title)
                if (core.isNotEmpty()) cores.getOrPut(core) { ArrayList() }.add(id)
                chapterNumber(title)?.let { numbers.getOrPut(it) { ArrayList() }.add(id) }
            }

            cores.forEach { (key, ids) -> ids.distinct().singleOrNull()?.let { map["core:" + key] = it } }
            numbers.forEach { (key, ids) -> ids.distinct().singleOrNull()?.let { map["number:" + key] = it } }
            map
        }.getOrElse {
            AppLog.put("本地书段评: 起点目录解析失败《" + bookId + "》", it)
            emptyMap()
        }
    }

    private suspend fun fetchSummary(bookId: String, chapterId: String): Map<Int, Int> = withContext(Dispatchers.IO) {
        val params = "bookId=" + urlEncode(bookId) +
            "&chapterId=" + urlEncode(chapterId) +
            "&useImei=0&strategy=3"
        val response = signedGet("v1/chapterreview/getchapterrepagesummary", params)
            ?: return@withContext emptyMap()

        runCatching {
            val list = jsonPath.parse(response)
                .read<List<Any?>>("$.Data.Getparagraphscommentcounts.DataList")
            val result = HashMap<Int, Int>()
            list.forEach { raw ->
                val item = raw as? Map<*, *> ?: return@forEach
                val pid = firstInt(item, "ParagraphId", "paragraphId") ?: return@forEach
                if (pid <= 0) return@forEach
                val count = firstInt(item, "CommentCount", "TextCount", "commentCount", "textCount") ?: 0
                if (count > 0) result[pid] = count
            }
            result
        }.getOrElse {
            AppLog.put("本地书段评: 起点段评汇总解析失败《" + bookId + "/" + chapterId + "》", it)
            emptyMap()
        }
    }

    private fun injectBubbles(
        content: String,
        bookId: String,
        chapterId: String,
        counts: Map<Int, Int>
    ): String {
        if (content.contains("dp:") && content.contains("pclick")) return content

        if (Regex("<p\\b", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            val regex = Regex("(?is)<p\\b[^>]*>.*?</p>")
            var pid = 0
            return regex.replace(content) { match ->
                val text = match.value
                if (text.replace(Regex("<[^>]+>"), "").trim().isEmpty()) return@replace text
                pid++
                appendBubble(text, bookId, chapterId, pid, counts[pid] ?: 0)
            }
        }

        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var pid = 0
        return lines.joinToString("\n") { line ->
            if (line.trim().isEmpty()) line
            else {
                pid++
                appendBubble(line, bookId, chapterId, pid, counts[pid] ?: 0)
            }
        }
    }

    private fun appendBubble(
        paragraph: String,
        bookId: String,
        chapterId: String,
        paragraphId: Int,
        count: Int
    ): String {
        if (count <= 0 || paragraph.contains("dp:")) return paragraph

        val pclick = "java.showQidianParagraphComments('" + bookId + "','" + chapterId + "'," + paragraphId + ");"
        // dp: 是阅读器内部段评协议；src 属性和 JSON 不能带反斜杠转义，
        // 否则阅读器的 imgPattern 会提前截断 src，导致整个 img 标签泄漏到正文。
        val option = """{&quot;pclick&quot;:&quot;$pclick&quot;,&quot;status&quot;:&quot;normal&quot;,&quot;displayText&quot;:&quot;$count&quot;}"""
        val bubble = """<img src="dp:$count,$option">"""
        return if (paragraph.contains("</p>", true)) {
            paragraph.replaceFirst(Regex("</p>", RegexOption.IGNORE_CASE), bubble + "</p>")
        } else paragraph + bubble
    }

    private fun firstString(map: Map<*, *>, vararg keys: String): String? {
        for (key in keys) {
            map.entries.firstOrNull {
                it.key?.toString()?.equals(key, true) == true
            }?.value?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun firstInt(map: Map<*, *>, vararg keys: String): Int? =
        firstString(map, *keys)?.toIntOrNull()


    /**
     * 段落评论列表（起点 App 签名接口 v2）。
     *
     * 起点移动网页 majax 接口（m.qidian.com/majax/chapterReview/reviewList）对图片评论
     * 只返回 hasImage 标记、不下发图片 URL，导致弹窗里图片无法显示；
     * 改走 App 签名接口 v2/chapterreview/getparagraphscomments（type=0）：
     * 文字/图片/语音评论同列表返回，图片评论直接带 ImageDetail/PreImage/ImgInfo，
     * 字段与 ParagraphCommentDialog 的默认解析路径（Id/UserName/UserHeadIcon/AgreeAmount/...）完全对齐。
     *
     * 返回与弹窗约定一致的结构 {"code":0,"data":{"list":[...],"total":N}}，
     * 由 [io.legado.app.ui.widget.dialog.ParagraphCommentDialog] 按 $.data.list / $.data.total 解析。
     */
    fun fetchParagraphReviews(bookId: String, chapterId: String, paragraphId: Int, page: Int, pageSize: Int): String? {
        if (paragraphId <= 0) return null
        val params = "bookId=" + urlEncode(bookId) +
            "&chapterId=" + urlEncode(chapterId) +
            "&paragraphId=" + paragraphId +
            "&pg=" + page +
            "&pz=" + pageSize +
            "&type=0&anchorId=0&from=0"
        val response = signedGet("v2/chapterreview/getparagraphscomments", params) ?: return null
        return runCatching {
            val json = jsonPath.parse(response)
            val list = json.read<List<Any?>>("$.Data.DataList").orEmpty()
            val total = runCatching { (json.read<Any>("$.Data.TotalCount") as? Number)?.toLong() }
                .getOrNull() ?: -1L
            GSON.toJson(
                mapOf(
                    "code" to 0,
                    "data" to mapOf(
                        "list" to list,
                        "total" to total
                    )
                )
            )
        }.getOrElse {
            AppLog.put("起点段评列表解析失败: " + response.take(200), it)
            null
        }
    }

    private fun httpGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()

    private fun signedGet(apiPath: String, params: String): String? = runCatching {
        val device = device()
        val time = System.currentTimeMillis().toString()
        val sorted = params.split("&").sorted().joinToString("&")

        val signRaw = "Rv1rPTnczce|" + time + "|0|" + device.qimei +
            "||||" + md5(sorted.lowercase()) + "|f189adc92b816b3e9da29ea304d4a7e4"
        val sign = tripleDesBase64(signRaw, "{1dYgqE)h9,R)hKqEcv4]k[h", "01234567")

        val infoRaw = device.qimei + "|7.9.378|1080|1184|1000009|10|1|" +
            device.model + "|1436|1000009|4|0|" + time + "|1|" + device.qimei + "|||||0"
        val info = tripleDesBase64(
            infoRaw,
            "0821CAAD409B84020821CAAD",
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
        )

        val request = Request.Builder()
            .url(API_HOST + apiPath + "?" + sorted)
            .header("QDSign", sign)
            .header("QDInfo", info)
            .header("tstamp", time)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()

    private data class Device(val qimei: String, val model: String)

    private fun device(): Device {
        val prefs = splitties.init.appCtx.getSharedPreferences("qidian_paragraph_comment", 0)
        val saved = prefs.getString("qimei_v2", null)
        if (!saved.isNullOrBlank()) {
            return Device(saved, prefs.getString("model", "PFJM10") ?: "PFJM10")
        }

        val brands = arrayOf("realme", "OPPO", "Xiaomi", "vivo", "HUAWEI", "samsung", "Google", "HONOR")
        val models = arrayOf("RMX3366", "PHY120", "24030PN60C", "V2324A", "ALN-AL10", "SM-S9280", "Pixel 4 XL", "MAA-AN10")
        val index = kotlin.random.Random.nextInt(brands.size)
        val model = models[index]
        val brand = brands[index]
        val now = java.text.SimpleDateFormat("yyyyMMddHHmmssSSS", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT+8")
        }.format(java.util.Date())
        val hex = "0123456789abcdef"
        val digits = "0123456789"
        val mac = buildString {
            repeat(6) {
                if (it > 0) append(":")
                append(hex[kotlin.random.Random.nextInt(hex.length)])
                append(hex[kotlin.random.Random.nextInt(hex.length)])
            }
            repeat(6) { append(digits[kotlin.random.Random.nextInt(digits.length)]) }
        }
        val qimei = now + mac

        prefs.edit()
            .putString("qimei_v2", qimei)
            .putString("model", model)
            .putString("brand", brand)
            .apply()
        return Device(qimei, model)
    }

    private fun tripleDesBase64(raw: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "DESede"),
            IvParameterSpec(iv.toByteArray(StandardCharsets.ISO_8859_1))
        )
        return Base64.encodeToString(
            cipher.doFinal(raw.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun normalizeTitle(title: String): String {
        val sb = StringBuilder(title.length)
        title.forEach { c ->
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

    private fun normalizeChapterCore(title: String): String {
        var value = normalizeTitle(title)
        value = value.replace(Regex("""^(?:第)?[0-9]+章""", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("""^chapter[0-9]+""", RegexOption.IGNORE_CASE), "")
        return value.trim(':', '-', '_', '·', '.', '。')
    }

    private fun chapterNumber(title: String): Int? {
        val normalized = normalizeTitle(title)
        return Regex("""(?:^|[^0-9])(?:第)?([0-9]+)章(?:$|[^0-9])""")
            .find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?:^|[^a-z0-9])chapter([0-9]+)(?:$|[^a-z0-9])""", RegexOption.IGNORE_CASE)
                .find(normalized)?.groupValues?.get(1)?.toIntOrNull()
    }

    private const val USER_AGENT =
        "Mozilla/mobile QDReaderAndroid/7.9.378/1436/1000009/Android"

    /** m.qidian.com/majax 为网页接口，需要浏览器 UA */
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
