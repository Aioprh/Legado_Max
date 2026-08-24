package io.legado.app.api.controller

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.app.api.ReturnData
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.EncodingDetect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * AI 生成书源辅助接口
 *
 * 移植自 DandanLLab/legadoSkill（MIT）的 smart_fetch_html 能力：
 * 由 App 内置 HTTP 服务代理抓取目标网站 HTML（浏览器直接跨域抓取会被 CORS 拦截），
 * 自动检测编码后返回给 web 端，供 AI 分析网页结构生成书源。
 *
 * 针对「前端 JS 动态渲染（SPA）」站点做了增强（这类站点静态 HTML 里没有书数据，
 * 直接把整页 HTML 喂给 LLM 会既超上下文又得不到有用结构）：
 * - 预处理 HTML：剔除 <style>、<script>、注释等噪声，显著缩小 prompt
 * - 从页面脚本中自动发现 JSON API 接口（fetch/axios/$.ajax/XMLHttpRequest 等），
 *   并转成 Legado 占位符形式
 * - 提取页面内嵌 JSON 数据（window.__INITIAL_STATE__ / __NUXT__ / __NEXT_DATA__
 *   及 type="application/json" 脚本等），一并交给 LLM 编写 JSONPath 规则
 * - 用搜索关键词调用搜索接口取回示例 JSON，再从示例中提取 book_id 调用目录接口，
 *   把真实 JSON 一并交给 LLM
 * - 支持自定义请求头/Cookie，应对站点反爬（并默认携带浏览器 UA）
 */
object AiSourceController {

    /** 单次抓取的最大字节数，防止大页面撑爆内存 */
    private const val MAX_BYTES = 1_000_000

    /** 返回给前端的最大字符数，超出截断避免 prompt 过大 */
    private const val MAX_CHARS = 200_000

    /** 接口示例响应的最大字符数，超出截断 */
    private const val MAX_API_CHARS = 12_000

    /** 内嵌 JSON 单块最大字符数 */
    private const val MAX_EMBEDDED_CHARS = 30_000

    /** 内嵌 JSON 总字符数上限 */
    private const val MAX_EMBEDDED_TOTAL = 60_000

    /** 内嵌 JSON 最多返回的块数 */
    private const val MAX_EMBEDDED_COUNT = 5

    /** 发现页探测最多生成的联系入口数（分类+子分类+榜单，避免无限膨胀） */
    private const val MAX_EXPLORE_LINKS = 400

    /** 榜单（排序）入口最多生成的条数 */
    private const val ORDER_LIMIT = 8

    /** 接口探测超时（毫秒） */
    private const val API_TIMEOUT_MS = 15_000L

    /** 抓取时的默认浏览器 UA，避免被常见反爬拦截 */
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 匹配常见前端请求里的 URL 模板（相对路径或带 `${}` 占位符）
     * 覆盖 fetch / axios / $.ajax / $.get / $.post / uni.request / XMLHttpRequest
     * 同时检测 POST 方法与请求体
     */
    private val fetchUrlPattern: Pattern = Pattern.compile(
        """fetch\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """axios\s*\.\s*(?:get|post|put|delete|request)\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """axios\s*\(\s*\{\s*url\s*:\s*[`'"]([^`'"]+)[`'"]|""" +
            """\$\s*\.\s*(?:get|post|ajax)\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """(?:uni|wx|my|Taro)\.request\s*\(\s*\{\s*url\s*:\s*[`'"]([^`'"]+)[`'"]|""" +
            """\$\s*\.\s*(?:getJSON|postJSON)\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """XMLHttpRequest[^;]*?\.\s*open\s*\(\s*['"](?:GET|POST|PUT|DELETE)['"]\s*,\s*[`'"]([^`'"]+)[`'"]""",
        Pattern.CASE_INSENSITIVE
    )

    /** 探测 POST 请求的方法与请求体模板 */
    private val postMethodPattern: Pattern = Pattern.compile(
        """(?:method|type)\s*[:=]\s*['"]POST['"]""",
        Pattern.CASE_INSENSITIVE
    )
    private val postBodyPattern: Pattern = Pattern.compile(
        """(?:body|data)\s*[:=]\s*[`'"]([^`'"]+)[`'"]""",
        Pattern.CASE_INSENSITIVE
    )

    /** 识别“固定URL模板+书籍ID 拼封面”的赋值，形如 `xxxCover = "https://cdn/.../${id}/..."` */
    private val coverTemplatePattern: Pattern = Pattern.compile(
        """\b\w*[Cc]over\w*\s*=\s*[`'"]([^`'"\s]*\$\{[^}]*\}[^`'"\s]*)[`'"]""",
        Pattern.CASE_INSENSITIVE
    )

    /** `${...}` 模板占位符 -> 供 LLM 识别的中性占位符
     *  注意：{{key}}/{{page}} 是 Legado 真实变量可保留；而本版 Legado 没有 bookId/chapterId 变量，
     *  若转成 {{bookId}} 会被 LLM 照抄进规则导致 ReferenceError，故转成 {book_id}/{chapter_id}
     *  中性占位符，配合提示词指导 LLM 用 JSONPath/正则补全真实 ID。 */
    private val templateReplacements = listOf(
        Regex("""\$\{encodeURIComponent\s*\(\s*keyword\s*\)\}""") to "{{key}}",
        Regex("""\$\{\s*keyword\s*\}""") to "{{key}}",
        Regex("""\$\{encodeURIComponent\s*\(\s*page\s*\)\}""") to "{{page}}",
        Regex("""\$\{\s*page\s*\}""") to "{{page}}",
        Regex("""\$\{encodeURIComponent\s*\(\s*bookId\s*\)\}""") to "{book_id}",
        Regex("""\$\{\s*bookId\s*\}""") to "{book_id}",
        Regex("""\$\{encodeURIComponent\s*\(\s*chapterId\s*\)\}""") to "{chapter_id}",
        Regex("""\$\{\s*chapterId\s*\}""") to "{chapter_id}",
        Regex("""\$\{encodeURIComponent\s*\(\s*categoryId\s*\)\}""") to "{category_id}",
        Regex("""\$\{\s*categoryId\s*\}""") to "{category_id}",
        Regex("""\$\{encodeURIComponent\s*\(\s*itemId\s*\)\}""") to "{item_id}",
        Regex("""\$\{\s*itemId\s*\}""") to "{item_id}",
        Regex("""\$\{encodeURIComponent\s*\(\s*tagId\s*\)\}""") to "{tag_id}",
        Regex("""\$\{\s*tagId\s*\}""") to "{tag_id}"
    )

    /** 常见内嵌 JSON 的全局变量名 */
    private val embeddedJsonKeys = listOf(
        "__INITIAL_STATE__", "__NUXT__", "__NEXT_DATA__", "__PRELOADED_STATE__",
        "__INITIAL_DATA__", "__STATE__", "__DATA__", "__data__", "__NEXT_DATA_JSON__",
        "__NUXT_STATE__", "__ASYNC_DATA__", "__ROOT_DATA__", "__SSR_DATA__", "__SSR__",
        "INITIAL_STATE", "INITIAL_DATA", "__BOOTSTRAP__", "__RUNTIME_CONFIG__",
        "serverData", "_SSR_DATA", "SERVER_DATA", "__PROLOGUE_STATE__", "appConfig"
    )

    /** 抓取结果 */
    data class HtmlContent(
        val url: String,
        val charset: String,
        val length: Int,
        val html: String,
        val loginUrl: String,
        val loginCheckUrl: String,
        val apiEndpoints: List<ApiEndpoint>,
        val sampleSearch: SampleResult,
        val sampleCatalog: SampleResult,
        val sampleExplore: SampleResult,
        val exploreLinks: List<Pair<String, String>>,
        val embeddedJson: List<String>,
        val coverTemplates: List<String>
    )

    data class ApiEndpoint(
        val type: String,
        val url: String,
        val method: String = "GET",
        var postBody: String = ""
    )

    data class SampleResult(
        val ok: Boolean,
        val url: String = "",
        val json: String = "",
        val error: String = ""
    )

    /**
     * 抓取目标网站 HTML 并自动检测编码（供 HTTP 接口与原生 AI 生成页共用）
     * @param header 自定义请求头，多行形式 "Key: Value"，可为空
     * @param cookie 自定义 Cookie，可为空
     */
    fun fetchHtmlContent(
        url: String,
        keyword: String? = null,
        header: String? = null,
        cookie: String? = null
    ): Result<HtmlContent> {
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("参数url不能为空，请填写需要分析的网站地址"))
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("url必须以http://或https://开头"))
        }
        return runCatching {
            val request = buildRequest(url, header, cookie)
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("请求失败: HTTP ${response.code}")
                }
                val body = response.body ?: throw RuntimeException("响应无内容")

                // 请求最终地址（可能经过重定向），用于解析相对接口路径
                val effectiveUrl = response.request.url.toString()

                // 限量读取，避免把超大响应整体读入内存
                val source = body.source()
                val buffer = ByteArray(MAX_BYTES)
                var total = 0
                while (total < MAX_BYTES) {
                    val read = source.read(buffer, total, MAX_BYTES - total)
                    if (read == -1) break
                    total += read
                }
                val limitedBytes = buffer.copyOf(total)
                if (limitedBytes.isEmpty()) throw RuntimeException("响应为空")

                // 自动检测编码（优先 <meta> 标签，其次 ICU 探测），再转成字符串
                val charset = runCatching {
                    EncodingDetect.getHtmlEncode(limitedBytes)
                }.getOrDefault("UTF-8")
                val html = runCatching {
                    String(limitedBytes, Charset.forName(charset))
                }.getOrElse {
                    String(limitedBytes, Charsets.UTF_8)
                }

                // 先基于原始 HTML 做接口发现与内嵌 JSON 提取（<script> 会被后续剥离）
                val endpoints = discoverApiEndpoints(html, effectiveUrl)
                val embeddedJson = extractEmbeddedJson(html)

                // 预处理：剔除 CSS、注释、脚本等噪声，显著缩小传给 LLM 的文本
                val clean = preprocessHtml(html)
                val truncated = if (clean.length > MAX_CHARS) clean.substring(0, MAX_CHARS) else clean

                val sampleSearch = if (keyword.isNullOrBlank()) {
                    SampleResult(ok = false, error = "未提供搜索关键词，跳过接口探测")
                } else {
                    fetchSearchSample(endpoints, keyword, header, cookie)
                }
                val sampleCatalog = if (sampleSearch.ok) {
                    fetchCatalogSample(endpoints, sampleSearch.json, header, cookie)
                } else {
                    SampleResult(ok = false, error = "搜索接口探测失败，跳过目录探测")
                }
                // 从首页提取分类/榜单/推荐导航链接，并抓取首个分类页真实 HTML 供 LLM 编写发现规则
                var exploreLinks = discoverExploreLinks(html, effectiveUrl)
                var sampleExplore = fetchExploreSample(exploreLinks, header, cookie)
                // SPA / JSON-API 站点：首页的“分类/榜单”多为按钮+下拉（JS 动态渲染），静态 <a> 链接
                // 很少或不全（如起点代理站首页仅能抓到 1 个）。只要发现 JSON 分类/榜单接口，就尝试从
                // 接口解析出完整分类（玄幻/仙侠/都市…），用更全的 JSON 结果覆盖 HTML 探测结果，
                // 供 LLM 编写多分类 exploreUrl；普通站无此类接口则维持 HTML 探测结果不变。
                if (endpoints.any { it.type == "explore" || it.type == "category" }) {
                    discoverJsonExplore(html, endpoints, header, cookie)?.let { (links, sample) ->
                        if (links.isNotEmpty()) {
                            exploreLinks = links
                            sampleExplore = sample
                        }
                    }
                }

                HtmlContent(
                    url, charset, truncated.length, truncated,
                    discoverLoginUrl(html, effectiveUrl),
                    discoverLoginCheckUrl(html, effectiveUrl),
                    endpoints, sampleSearch, sampleCatalog, sampleExplore, exploreLinks, embeddedJson,
                    discoverCoverTemplates(html)
                )
            }
        }
    }

    /**
     * 预处理 HTML：剔除 <style>、<script>、注释，折叠连续空行。
     * 注意：调用前需先完成接口发现与内嵌 JSON 提取，脚本内容对 LLM 无价值。
     */
    private fun preprocessHtml(html: String): String {
        var s = html.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("<!--[\\s\\S]*?-->"), "")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s
    }

    /**
     * 构建抓取请求：默认携带浏览器 UA，可追加自定义请求头与 Cookie
     */
    private fun buildRequest(url: String, header: String?, cookie: String?): Request {
        val builder = Request.Builder().url(url)
        applyHeaders(builder, header, cookie)
        return builder.build()
    }

    /** 为请求统一注入浏览器 UA、自定义请求头与 Cookie */
    private fun applyHeaders(builder: Request.Builder, header: String?, cookie: String?) {
        builder.header("User-Agent", DEFAULT_USER_AGENT)
        parseHeaderString(header).forEach { (k, v) ->
            if (k.isNotBlank()) builder.header(k.trim(), v)
        }
        if (!cookie.isNullOrBlank()) {
            builder.header("Cookie", cookie)
        }
    }

    /**
     * 解析多行请求头字符串，每行 "Key: Value" 或 "Key=Value"
     */
    private fun parseHeaderString(header: String?): List<Pair<String, String>> {
        if (header.isNullOrBlank()) return emptyList()
        return header.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isBlank() || t.startsWith("#") || t.startsWith("//")) return@mapNotNull null
            val idx = t.indexOf(':').takeIf { it > 0 }
                ?: t.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
            val k = t.substring(0, idx).trim()
            val v = t.substring(idx + 1).trim()
            if (k.isBlank()) null else k to v
        }
    }

    /**
     * 从页面脚本中提取 JSON API 接口，并解析为绝对地址（相对路径基于最终页面地址）。
     * 同时判断请求方法（GET/POST）与 POST 请求体模板。
     */
    private fun discoverApiEndpoints(html: String, baseUrl: String): List<ApiEndpoint> {
        val found = LinkedHashMap<String, ApiEndpoint>()
        val matcher = fetchUrlPattern.matcher(html)
        while (matcher.find()) {
            val template = (1..6)
                .firstNotNullOfOrNull { matcher.group(it)?.takeIf { g -> g.isNotBlank() } }
                ?: continue
            // 解析开头 JS 变量前缀（如 `${api}?action=...`），这类模板此前会被整体跳过，
            // 导致 SPA 站点常见的 ranking/config/list 等分类接口无法被发现
            var cleaned = resolveVarPrefix(template, html)
            for ((re, rep) in templateReplacements) cleaned = cleaned.replace(re, rep)
            cleaned = cleaned.replace(Regex("""\$\{[^}]*\}"""), "")
            if (cleaned.isBlank()) continue

            val type = when {
                cleaned.contains("keyword") || cleaned.contains("search") -> "search"
                cleaned.contains("catalog") || cleaned.contains("chapterlist") -> "catalog"
                cleaned.contains("content") || cleaned.contains("chapter_id") ||
                    cleaned.contains("chapterid") -> "content"
                cleaned.contains("detail") || cleaned.contains("book_id") ||
                    cleaned.contains("bookid") -> "detail"
                // 注意：config 接口 URL 往往也带 "ranking/rank" 字样，须先于 explore 判断，否则会被误判为榜单
                cleaned.contains("config") -> "category"
                // JSON-API / SPA 站点：把“榜单/分类/发现”类接口保留下来，供发现页规则探测
                cleaned.contains("square") || cleaned.contains("action=list") ||
                    cleaned.contains("ranking") || cleaned.contains("rank") ||
                    cleaned.contains("category") -> "explore"
                else -> "other"
            }
            if (type == "other") continue

            val resolved = runCatching { URL(URL(baseUrl), cleaned).toString() }.getOrDefault("")
            if (resolved.isBlank() || !resolved.startsWith("http")) continue

            // 判断是否为 POST：取匹配处前后片段，检测 method/type:"POST" 与 body/data
            val contextStart = (matcher.start() - 120).coerceAtLeast(0)
            val context = html.substring(contextStart, minOf(html.length, matcher.end() + 200))
            val isPost = postMethodPattern.matcher(context).find()
            val postBody = if (isPost) {
                postBodyPattern.matcher(context).run {
                    if (find()) group(1) ?: "" else ""
                }
            } else {
                ""
            }

            // search 接口优先带 keyword 占位符的；detail 接口优先带 {book_id} 占位符的
            val existing = found[type]
            val better = when {
                existing == null -> true
                type == "search" && cleaned.contains("keyword") && !existing.url.contains("{{key}}") -> true
                type == "detail" && cleaned.contains("book_id") && !existing.url.contains("{book_id}") -> true
                else -> false
            }
            if (better) {
                found[type] = ApiEndpoint(type, resolved, if (isPost) "POST" else "GET")
                    .apply { if (isPost && postBody.isNotBlank()) this.postBody = postBody }
            }
        }
        val order = listOf("search", "detail", "catalog", "content", "category", "explore")
        return order.mapNotNull { found[it] }
    }

    /**
     * 调用搜索接口取回示例 JSON。
     * 优先识别接口模板中真实的关键字/分页参数名，避免盲目追加错误参数。
     * 接口为 POST 时使用 POST 请求（尽量沿用页面脚本里发现的请求体模板）。
     */
    private fun fetchSearchSample(
        endpoints: List<ApiEndpoint>,
        keyword: String,
        header: String?,
        cookie: String?
    ): SampleResult {
        val search = endpoints.firstOrNull { it.type == "search" }
            ?: return SampleResult(ok = false, error = "未在页面脚本中发现搜索接口")
        val kw = URLEncoder.encode(keyword, "UTF-8")

        // 从模板中识别真实参数名（如 ?q=、?keyword=、?page=、?pn=）
        val keyParam = Regex("""([A-Za-z_][A-Za-z0-9_]*)=\{\{key\}\}""")
            .find(search.url)?.groupValues?.get(1)
        val pageParam = Regex("""([A-Za-z_][A-Za-z0-9_]*)=\{\{page\}\}""")
            .find(search.url)?.groupValues?.get(1)

        var url = search.url
            .replace("{{key}}", kw)
            .replace("{{page}}", "1")
            .replace(Regex("""\{\{[^}]*\}\}"""), "")

        if (search.method == "POST") {
            // POST 探测：优先使用页面脚本里发现的请求体模板，否则用 keyword=<key>
            val bodyTemplate = search.postBody.ifBlank {
                val kp = keyParam ?: "keyword"
                "$kp={{key}}"
            }
            val body = bodyTemplate
                .replace("{{key}}", kw)
                .replace("{{page}}", "1")
                .replace(Regex("""\{\{[^}]*\}\}"""), "")
            return fetchPostSample(url, body, header, cookie)
        }

        if (keyParam == null && !url.contains(Regex("""[?&](?:keyword|searchKey|search_keyword|q|key)="""))) {
            url = if (url.contains("?")) "$url&keyword=$kw" else "$url?keyword=$kw"
        }
        if (pageParam == null && !url.contains(Regex("""[?&]page\d*="""))) {
            url = if (url.contains("?")) "$url&page=1" else "$url?page=1"
        }
        return fetchJsonSample(url, header, cookie)
    }

    /**
     * 从搜索示例 JSON 中提取 book_id，调用目录接口取回章节示例 JSON
     */
    private fun fetchCatalogSample(
        endpoints: List<ApiEndpoint>,
        searchJson: String,
        header: String?,
        cookie: String?
    ): SampleResult {
        val catalog = endpoints.firstOrNull { it.type == "catalog" }
            ?: return SampleResult(ok = false, error = "未在页面脚本中发现目录接口")
        val bookId = extractBookId(searchJson)
            ?: return SampleResult(ok = false, error = "未能从搜索示例中提取 book_id")
        val url = catalog.url
            .replace("{book_id}", bookId)
            .replace(Regex("""\{\{[^}]*\}\}"""), "")
        return fetchJsonSample(url, header, cookie)
    }

    /**
     * 抓取接口示例 JSON（限时、非 JSON 判失败）
     * @param raw 为 true 时不截断，返回完整 JSON 供程序解析（如分类 config 接口）。
     *        默认截断到 MAX_API_CHARS，仅用于喂给 LLM 的示例；截断后的 JSON 不合法，不能用于程序解析。
     */
    private fun fetchJsonSample(
        url: String,
        header: String?,
        cookie: String?,
        raw: Boolean = false
    ): SampleResult {
        return runCatching {
            val request = buildRequest(url, header, cookie)
            val client = okHttpClient.newBuilder()
                .callTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    SampleResult(ok = false, url = url, error = "HTTP ${response.code}")
                } else {
                    val text = response.body?.string()?.trim().orEmpty()
                    if (text.isEmpty() || (!text.startsWith("{") && !text.startsWith("["))) {
                        SampleResult(ok = false, url = url, error = "响应非 JSON 或为空")
                    } else {
                        val result = if (text.length > MAX_API_CHARS && !raw) {
                            text.substring(0, MAX_API_CHARS) + "\n...(已截断)"
                        } else {
                            text
                        }
                        SampleResult(ok = true, url = url, json = result)
                    }
                }
            }
        }.getOrElse {
            SampleResult(ok = false, url = url, error = it.message ?: it.javaClass.simpleName)
        }
    }

    /**
     * 以 POST 表单方式抓取接口示例 JSON（用于 POST 搜索接口探测）
     */
    private fun fetchPostSample(
        url: String,
        body: String,
        header: String?,
        cookie: String?
    ): SampleResult {
        return runCatching {
            val form = body.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val builder = Request.Builder().url(url).post(form)
            applyHeaders(builder, header, cookie)
            val client = okHttpClient.newBuilder()
                .callTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    SampleResult(ok = false, url = url, error = "POST HTTP ${response.code}")
                } else {
                    val text = response.body?.string()?.trim().orEmpty()
                    if (text.isEmpty() || (!text.startsWith("{") && !text.startsWith("["))) {
                        SampleResult(ok = false, url = url, error = "POST 响应非 JSON 或为空")
                    } else {
                        val truncated = if (text.length > MAX_API_CHARS) {
                            text.substring(0, MAX_API_CHARS) + "\n...(已截断)"
                        } else {
                            text
                        }
                        SampleResult(ok = true, url = url, json = truncated)
                    }
                }
            }
        }.getOrElse {
            SampleResult(ok = false, url = url, error = "POST 失败: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    /**
     * 提取页面内嵌 JSON 数据（SSR 站点常见）：
     * - <script type="application/json"> 内容即为 JSON
     * - window.__INITIAL_STATE__ / __NUXT__ / __NEXT_DATA__ 等全局变量
     * 取其中最大且可解析的若干块，按大小降序返回。
     */
    private fun extractEmbeddedJson(html: String): List<String> {
        val candidates = LinkedHashSet<String>()
        val scriptBlocks = Regex("<script[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
            .findAll(html)

        for (match in scriptBlocks) {
            val tag = match.value
            val content = match.groupValues[1]
            // type="application/json" / "application/ld+json" 的脚本块
            if (Regex("""type\s*=\s*["']application/(?:json|ld\+json)["']""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(tag)
            ) {
                val t = content.trim()
                if (t.isValidJson()) candidates.add(t)
                continue
            }
            // 形如 window.__XXX__ = {...} 或 var __XXX__ = {...} 的全局变量
            for (key in embeddedJsonKeys) {
                val m = Regex("""(?:window\s*\.\s*)?(?:var|let|const)?\s*$key\s*=\s*""", RegexOption.IGNORE_CASE)
                    .find(content) ?: continue
                val start = content.indexOfAny(charArrayOf('{', '['), m.range.last + 1)
                if (start < 0) continue
                val value = extractBalanced(content, start) ?: continue
                if (value.isValidJson()) candidates.add(value)
            }
        }

        val result = mutableListOf<String>()
        var used = 0
        for (json in candidates
            .sortedByDescending { it.length }
            .take(MAX_EMBEDDED_COUNT)
            .map {
                if (it.length > MAX_EMBEDDED_CHARS) {
                    it.substring(0, MAX_EMBEDDED_CHARS) + "\n...(已截断)"
                } else {
                    it
                }
            }
        ) {
            if (used + json.length > MAX_EMBEDDED_TOTAL) break
            result.add(json)
            used += json.length
        }
        return result
    }

    private fun String.isValidJson(): Boolean {
        return runCatching { JsonParser.parseString(this) }.isSuccess
    }

    /**
     * 从 start 位置提取配平的 {} / [] 片段（跳过字符串与转义）
     */
    private fun extractBalanced(s: String, start: Int): String? {
        val open = s[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return s.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    /**
     * 递归查找 JSON 中第一个名为 bookId / book_id 的数字字段
     */
    private fun extractBookId(json: String): String? {
        return runCatching {
            fun search(el: JsonElement): String? {
                return when {
                    el.isJsonObject -> {
                        val obj = el.asJsonObject
                        for ((k, v) in obj.entrySet()) {
                            val key = k.lowercase()
                            if ((key == "bookid" || key == "book_id") && v.isJsonPrimitive) {
                                val s = v.asString
                                if (s.isNotBlank() && s.all { it.isDigit() }) return s
                            }
                            search(v)?.let { return it }
                        }
                        null
                    }
                    el.isJsonArray -> {
                        for (e in el.asJsonArray) search(e)?.let { return it }
                        null
                    }
                    else -> null
                }
            }
            search(JsonParser.parseString(json))
        }.getOrNull()
    }

    /**
     * 首页导航中常见分类/榜单/推荐的链接文本关键词
     */
    private val exploreTextKeywords = listOf(
        "分类", "书库", "排行", "榜单", "推荐", "精选", "完本", "最新", "热门", "免费",
        "玄幻", "都市", "武侠", "科幻", "言情", "历史", "竞技", "悬疑",
        "全本", "连载", "新书", "上架", "月票", "点击", "收藏", "订阅", "人气",
        "总榜", "周榜", "月榜", "日榜", "排行榜", "热度", "精品", "高分",
        "男生", "女生", "男频", "女频", "出版", "漫画", "轻小说",
        "全部", "原创", "站点", "限免", "VIP", "专题",
        "现代", "古言", "穿越", "重生", "奇幻", "灵异", "军事", "游戏", "体育",
        "同人", "仙侠", "科幻", "悬疑", "探案", "甜宠", "爽文", "种田", "书单",
        "标签", "Tag", "短篇", "包月", "追书", "飙升", "新书榜", "收藏榜", "战力"
    )

    /** 首页导航中常见分类/榜单/推荐链接的 URL 关键词 */
    private val exploreHrefKeywords = listOf(
        "category", "class", "fenlei", "rank", "top", "ranklist", "booklist",
        "store", "complete", "wanben", "recommend", "sort", "genre", "list",
        "hot", "new", "popular", "best", "all", "books", "type", "tags",
        "update", "serial", "full", "over", "end", "latest", "finish",
        "male", "female", "boy", "girl", "channel", "section", "index",
        "shuku", "bookstore", "plate", "tpl", "nav", "menu", "cate", "sub"
    )

    /** 榜单/排行榜类入口名称关键词（人气最高、推荐榜、月票榜…），生成发现页时放到分类之上 */
    private val exploreRankKeywords = listOf(
        "排行", "榜单", "人气", "推荐", "月票", "点击", "收藏", "热榜", "好评", "畅销",
        "新书", "飙升", "热度", "战力", "周榜", "月榜", "日榜", "总榜",
        "top", "rank", "hot", "best", "popular"
    )

    /**
     * 情节/风格/筛选类标签名称关键词（爽文、甜宠、穿越、状态、字数、免费…）：
     * 不是真正的书籍分类，也不是排行榜入口，生成发现页时直接剔除。
     */
    private val exploreDropKeywords = listOf(
        "爽文", "爽感", "甜宠", "宠文", "甜文", "甜爽", "种田", "穿越", "重生", "快穿",
        "系统", "赘婿", "战神", "神医", "兵王", "马甲", "双洁", "双c", "虐文", "团宠",
        "龙傲天", "标签", "情节", "剧情", "风格", "治愈", "虐心", "轻松",
        "状态", "字数", "连载", "完结", "全本", "上架", "免费", "付费", "限免", "更新时间"
    )

    /** 发现页入口（名称, URL）分类：榜单 / 书籍分类 / 需剔除的情节标签 */
    enum class ExploreNameType { RANK, CATEGORY, DROP }

    /**
     * 判断一个分类/榜单入口名称归为哪一类。
     * 优先判榜单纯属（人气、推荐、点击…），其次剔除情节/风格/筛选类标签（爽文、甜宠…）；
     * 其余视为真正的书籍分类。
     */
    fun classifyExplore(name: String): ExploreNameType {
        val n = name.trim()
        if (n.isBlank()) return ExploreNameType.DROP
        val low = n.lowercase()
        if (exploreRankKeywords.any { low.contains(it) }) return ExploreNameType.RANK
        if (exploreDropKeywords.any { low.contains(it) }) return ExploreNameType.DROP
        return ExploreNameType.CATEGORY
    }

    /**
     * 从页面脚本中识别“用固定 URL 模板 + 书籍ID 拼封面”的封面模板，
     * 供 LLM 编写 ruleExplore / ruleBookInfo / ruleSearch 的 coverUrl。
     * 这类站点（多为起点系/书库站）的列表/分类接口往往不含封面字段，只有 BookId，
     * 封面需要按模板拼接，否则发现页/搜索列表加载不出封面。
     *
     * 识别特征：形如 `xxxCover = "https://cdn/.../${id}/..."` 的赋值，
     * 模板含一个 `${...}` 书籍/图片 ID 插值。命中后把插值统一归一化为 {ID} 占位符并去重。
     */
    private fun discoverCoverTemplates(html: String): List<String> {
        val found = LinkedHashSet<String>()
        val matcher = coverTemplatePattern.matcher(html)
        while (matcher.find()) {
            var t = matcher.group(1)
            // 归一化插值：${encodeURIComponent(x)} / ${x} -> {ID}
            t = t.replace(
                Regex("""\$\{(?:encodeURIComponent\s*\(\s*)?[A-Za-z0-9_.$]+(?:\s*\))?\}"""),
                "{ID}"
            )
            if (t.contains("{ID}") && (t.startsWith("http://") || t.startsWith("https://"))) {
                found.add(t)
                if (found.size >= 3) break
            }
        }
        return found.toList()
    }

    /**
     * 从首页 HTML 中提取分类/榜单/推荐导航链接（书名/分类名, 绝对 URL）。
     * 供 LLM 编写 exploreUrl/ruleExplore 发现规则使用。
     */
    private fun discoverExploreLinks(html: String, baseUrl: String): List<Pair<String, String>> {
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return emptyList()
        val home = baseUrl.lowercase().trimEnd('/')
        val homePage = home + "/"
        val found = LinkedHashMap<String, String>()
        for (a in doc.select("a[href]")) {
            val text = a.text().trim()
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            // 分类名一般较短；过滤首页/登录/注册等无关链接
            if (text.isBlank() || text.length > 20) continue
            if (text == "首页" || text.contains("登录") || text.contains("注册") || text.contains("加入")) continue
            if (href.isBlank() || href.startsWith("javascript:") || href == "#" || href.startsWith("mailto:")) continue
            val hrefLower = href.lowercase()
            if (hrefLower == home || hrefLower == homePage) continue
            // 已经抓过的原页（首页 HTML 可能同时作为分类 URL）不再重复
            if (found.containsKey(text)) continue
            val isExplore = exploreTextKeywords.any { text.contains(it) } ||
                exploreHrefKeywords.any { hrefLower.contains(it) }
            if (!isExplore) continue
            // 情节/风格/筛选类标签（爽文、甜宠、状态、免费…）不是真正的分类/榜单，剔除
            if (classifyExplore(text) == ExploreNameType.DROP) continue
            found[text] = href
            if (found.size >= 8) break
        }
        return found.toList()
    }

    /**
     * 抓取首个分类/榜单页的真实 HTML（预处理后截断），供 LLM 分析发现页结构。
     */
    private fun fetchExploreSample(
        exploreLinks: List<Pair<String, String>>,
        header: String?,
        cookie: String?
    ): SampleResult {
        val first = exploreLinks.firstOrNull { it.second.isNotBlank() }
            ?: return SampleResult(ok = false, error = "未在首页中发现分类/榜单导航链接")
        return runCatching {
            val request = buildRequest(first.second, header, cookie)
            val client = okHttpClient.newBuilder()
                .callTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    SampleResult(ok = false, url = first.second, error = "HTTP ${response.code}")
                } else {
                    val text = response.body?.string()?.trim().orEmpty()
                    if (text.isEmpty()) {
                        SampleResult(ok = false, url = first.second, error = "响应为空")
                    } else {
                        val clean = preprocessHtml(text)
                        val truncated = if (clean.length > MAX_API_CHARS) {
                            clean.substring(0, MAX_API_CHARS) + "\n...(已截断)"
                        } else {
                            clean
                        }
                        SampleResult(ok = true, url = first.second, json = truncated)
                    }
                }
            }
        }.getOrElse {
            SampleResult(ok = false, url = first.second, error = it.message ?: it.javaClass.simpleName)
        }
    }

    /**
     * 解析接口模板开头的 JS 变量前缀，如 `${api}?action=...`。
     * 常见 SPA 会用 `const api = 'xxx.php'` 之类变量再拼接，此时模板带 `${}` 前缀，
     * 旧逻辑直接跳过，导致这类站点的分类/榜单接口即使出现在脚本里也无法被发现。
     * 这里把开头连续几个 `${var}` 替换成 html 中 `(const|let|var) var = '...'` 的字面值；
     * 找不到字面值时原样返回（后续空/无关内容仍会被过滤）。
     */
    private fun resolveVarPrefix(template: String, html: String): String {
        var t = template
        var guard = 0
        while (guard++ < 8) {
            val m = Regex("""^\$\{([A-Za-z_$][A-Za-z0-9_$]*)\}""").find(t) ?: break
            val name = m.groupValues[1]
            val literal = Regex(
                """(?<![A-Za-z0-9_$])(?:const|let|var)\s+$name\s*=\s*[`'"]\s*([^`'"]+)\s*[`'"]"""
            ).find(html)?.groupValues?.get(1)
            if (literal.isNullOrEmpty() || literal.contains("$")) break
            t = literal + t.removeRange(m.range)
        }
        return t
    }

    /**
     * 给接口 URL 中空着的站点参数填入默认站点值，保证探测返回有数据。
     * 如 `${state.homeSite}` 这类模板被剥离后 URL 形如 `...&site=&page=1`，
     * 从脚本 `homeSite: 18` / `data-home-site="18"` 取值填入，避免空参导致接口返回空。
     */
    private fun injectDefaultSite(url: String, html: String): String {
        val site = Regex("""homeSite\s*:\s*(\d+)""").find(html)?.groupValues?.get(1)
            ?: Regex("""data-home-site\s*=\s*"(\d+)""").find(html)?.groupValues?.get(1)
            ?: return url
        // 仅替换空参名本身，不吞掉其后的 "&"，避免拼出 "site_id=18&&order=…" 双分隔符
        return url.replace(Regex("""([?&](?:site_id|site)=)""")) { m ->
            m.groupValues[1] + site
        }
    }

    /**
     * SPA / JSON-API 站点回退：首页无静态分类链接时，从脚本发现的 JSON 接口探测分类/榜单。
     * - 从分类 config 接口返回解析出分类名，据此生成 exploreLinks（分类名 -> 榜单列表 URL）
     * - 抓首个分类的榜单列表真实 JSON 作为 sampleExplore，供 LLM 编写 JSONPath 发现规则
     * 全程 best-effort：解析/请求失败即返回 null，不打断主流程。
     */
    private fun discoverJsonExplore(
        html: String,
        endpoints: List<ApiEndpoint>,
        header: String?,
        cookie: String?
    ): Pair<List<Pair<String, String>>, SampleResult>? {
        val explore = endpoints.firstOrNull { it.type == "explore" }
        val config = endpoints.firstOrNull { it.type == "category" }

        // 先尝试从分类 config 接口解析分类并生成榜单列表 URL
        val links = mutableListOf<Pair<String, String>>()
        if (config != null) {
            runCatching {
                val cfgUrl = injectDefaultSite(config.url, html)
                // 分类 config JSON 往往超过 MAX_API_CHARS，需完整抓取用于解析，不能截断成非法 JSON
                val cfgJson = fetchJsonSample(cfgUrl, header, cookie, raw = true)
                if (cfgJson.ok) links += parseCategoryLinks(cfgJson.json, cfgUrl)
            }
        }

        // 拿一个能真实返回书籍的榜单列表 JSON 作为发现页样例
        val sample = if (links.isNotEmpty()) {
            runCatching { fetchJsonSample(links.first().second.replace("{{page}}", "1"), header, cookie) }
                .getOrNull() ?: SampleResult(ok = false, url = links.first().second, error = "榜单接口探测失败")
        } else {
            val url = explore?.url?.takeIf { it.isNotBlank() } ?: return null
            runCatching { fetchJsonSample(injectDefaultSite(url, html), header, cookie) }
                .getOrNull() ?: SampleResult(ok = false, url = url, error = "榜单接口探测失败")
        }

        // 无法解析出分类时至少保留一个通用榜单入口，避免“未探测到分类导航”
        if (links.isEmpty() && explore?.url?.isNotBlank() == true) {
            links.add("站内榜单" to injectDefaultSite(explore.url, html))
        }
        return links to sample
    }

    /**
     * 从分类 config 返回的 JSON 中提取分类。
     * 兼容两类常见结构：
     * 1. 【Extvalue 分层】（起点系/搜索类站点，如 qd）：
     *    `FiltrLines[].FilterUnions[]`，每个主分类是带 `Id`+`Name`+`Extvalue[]`（子分类）的对象，
     *    且其 `Extvalue[]` 含 `Id==0` 的“全部XXX”占位，据此与“状态/字数/付费/标签”等其它
     *    筛选组区分（这些组通常不带 Id==0 占位或 Id 很大）。
     * 2. 【SubTag 标记】（旧形态）：顶层若干 `{Name, Id, SubTag}` 对象，SubTag 非空即主分类。
     * 同时从 config JSON 的 Orders 数组取首个排序（如“人气最高”Id=11），拼进榜单 URL。
     * 根据 config 地址推导每个子分类对应的榜单列表 URL（主分类 category_id，子分类 subcategory_id）。
     */
    private fun parseCategoryLinks(json: String, configUrl: String): List<Pair<String, String>> {
        // 榜单（排序）入口如“人气最高”“月票榜”，来自 config 的 Orders，放到分类之上
        val rankEntries = LinkedHashMap<String, String>()
        val result = LinkedHashMap<String, String>()
        val order = parseDefaultOrder(json)
        runCatching {
            collectOrderEntries(json).forEach { (name, id) ->
                if (classifyExplore(name) == ExploreNameType.RANK) {
                    rankEntries.putIfAbsent(name, buildRankUrl(configUrl, id))
                }
            }
            fun walk(el: JsonElement) {
                val obj = if (el.isJsonObject) el.asJsonObject else null
                val arr = if (el.isJsonArray) el.asJsonArray else null
                // Extvalue 分层形态：主分类对象带 Extvalue 数组，且含 Id==0 的“全部”占位
                if (obj != null) {
                    val ext = obj.get("Extvalue") ?: obj.get("extvalue")
                    val parentId = obj.get("Id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    val parentName = obj.get("Name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    if (ext != null && ext.isJsonArray && parentId.toIntOrNull()?.let { it > 0 } == true && parentName.isNotBlank()) {
                        val hasAllPlaceholder = ext.asJsonArray.any { item ->
                            item.isJsonObject &&
                                item.asJsonObject.get("Id")?.takeIf { it.isJsonPrimitive }?.asString == "0"
                        }
                        if (hasAllPlaceholder) {
                            // 情节/状态/字数等筛选标签组不是真正的书籍分类，整组剔除
                            if (classifyExplore(parentName) == ExploreNameType.DROP) return
                            // 主分类入口
                            result.putIfAbsent(
                                parentName,
                                buildCategorySubUrl(configUrl, parentId, "", order)
                            )
                            // 子分类入口（剔除情节标签类子项，如 玄幻→穿越）
                            for (sub in ext.asJsonArray) {
                                if (!sub.isJsonObject) continue
                                val so = sub.asJsonObject
                                val subId = so.get("Id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                                val subName = so.get("Name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                                if (subId.toIntOrNull()?.let { it > 0 } == true && subName.isNotBlank() &&
                                    classifyExplore(subName) != ExploreNameType.DROP
                                ) {
                                    result.putIfAbsent(
                                        "${parentName}·${subName}",
                                        buildCategorySubUrl(configUrl, parentId, subId, order)
                                    )
                                }
                            }
                            return
                        }
                    }
                    // 旧形态 fallback：对象含 SubTag 且非空
                    val subTag = obj.get("SubTag")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    val id = obj.get("Id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    val name = obj.get("Name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    if (subTag.isNotBlank() && id.isNotBlank() && id != "0" &&
                        id.all { it.isDigit() } && name.isNotBlank() &&
                        classifyExplore(name) != ExploreNameType.DROP
                    ) {
                        result.putIfAbsent(name, buildCategoryListUrl(configUrl, id, order))
                        return
                    }
                }
                if (arr != null) {
                    for (item in arr) walk(item)
                } else if (obj != null) {
                    obj.entrySet().forEach { (_, v) ->
                        if (v.isJsonArray || v.isJsonObject) walk(v)
                    }
                }
            }
            walk(JsonParser.parseString(json))
        }
        // 榜单在前，分类在后
        val ordered = LinkedHashMap<String, String>()
        rankEntries.forEach { (n, u) -> ordered.putIfAbsent(n, u) }
        result.forEach { (n, u) -> ordered.putIfAbsent(n, u) }
        val list = ordered.entries.toList().map { it.key to it.value }
        return list.take(MAX_EXPLORE_LINKS)
    }

    /**
     * 收集 config JSON 中 Orders 数组的全部排序项（如“人气最高”“月票榜”），返回 (名称, Id)。
     */
    private fun collectOrderEntries(json: String): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        fun walk(el: JsonElement) {
            when {
                el.isJsonArray -> el.asJsonArray.forEach { walk(it) }
                el.isJsonObject -> {
                    val o = el.asJsonObject
                    o.get("Orders")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { entry ->
                        if (entry.isJsonObject) {
                            val id = entry.asJsonObject.get("Id")
                                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                            val name = entry.asJsonObject.get("Name")
                                ?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                            if (id.isNotBlank() && id.all { it.isDigit() } && name.isNotBlank()) {
                                out.putIfAbsent(name, id)
                            }
                        }
                    }
                    o.entrySet().forEach { (_, v) ->
                        if (v.isJsonArray || v.isJsonObject) walk(v)
                    }
                }
            }
        }
        walk(JsonParser.parseString(json))
        return out.entries.toList().take(ORDER_LIMIT)
    }

    /**
     * 由分类 config 地址推导“全局榜单”地址（不带 category_id），排序用 Order 的 Id。
     */
    private fun buildRankUrl(configUrl: String, order: String): String {
        var url = configUrl.substringBefore("#").trimEnd('&')
        if (Regex("""action=config""", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
            url = url.replace(Regex("""action=config""", RegexOption.IGNORE_CASE), "action=ranking")
        }
        val sep = if (url.contains("?")) "&" else "?"
        return buildString {
            append(url).append(sep)
            append("order=").append(order)
            append("&page={{page}}&page_size=20")
        }
    }

    /**
     * 从 config JSON 中提取默认榜单排序 Id：取 Orders 数组首个（站点默认排序），取不到用 "0"。
     */
    private fun parseDefaultOrder(json: String): String {
        return runCatching {
            fun findOrders(el: JsonElement): String? {
                when {
                    el.isJsonArray -> el.asJsonArray.forEach { findOrders(it)?.let { r -> return r } }
                    el.isJsonObject -> {
                        val o = el.asJsonObject
                        o.get("Orders")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { entry ->
                            if (entry.isJsonObject) {
                                val id = entry.asJsonObject.get("Id")
                                    ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                                if (id.isNotBlank() && id.all { it.isDigit() }) return id
                            }
                        }
                        o.entrySet().forEach { (_, v) ->
                            if (v.isJsonArray || v.isJsonObject) findOrders(v)?.let { r -> return r }
                        }
                    }
                }
                return null
            }
            findOrders(JsonParser.parseString(json)) ?: "0"
        }.getOrDefault("0")
    }

    /**
     * 由分类 config 地址推导榜单列表地址（分页用 {{page}}，App 自动替换翻页）。
     * config 形如 `ranking.php?action=config&site_id=11`，榜单列表为
     * `ranking.php?action=ranking&site_id=11&order={order}&page={{page}}&page_size=20&category_id={id}`。
     * 非 `action=config` 形态则原样追加 category_id 查询参数，尽力而为。
     */
    private fun buildCategoryListUrl(configUrl: String, categoryId: String, order: String = "0"): String {
        return buildCategorySubUrl(configUrl, categoryId, "", order)
    }

    /**
     * 由分类 config 地址推导榜单列表地址，子分类（subId 非空）追加 subcategory_id 参数。
     */
    private fun buildCategorySubUrl(
        configUrl: String,
        categoryId: String,
        subId: String,
        order: String = "0"
    ): String {
        var url = configUrl.substringBefore("#").trimEnd('&')
        if (Regex("""action=config""", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
            url = url.replace(Regex("""action=config""", RegexOption.IGNORE_CASE), "action=ranking")
        }
        val sep = if (url.contains("?")) "&" else "?"
        return buildString {
            append(url).append(sep)
            append("order=").append(order)
            append("&page={{page}}&page_size=20")
            append("&category_id=").append(categoryId)
            if (subId.isNotBlank()) append("&subcategory_id=").append(subId)
        }
    }

    /**
     * 探测站点是否为「可登录」站点，并尽量提取登录入口地址。
     * 探测顺序：
     * 0. 弹窗登录站点（登录框由 JS 弹出、无独立登录页）：识别页面支持的
     *    `?auth=login` / `?action=login` 参数，返回 baseUrl+参数（WebView 打开即自动弹登录框）
     * 1. 首页导航里的网页登录链接（<a> 文本含“登录”或 href 含 login/signin）
     * 2. 脚本中出现的绝对登录地址（https://… 含 login/signin/logout）
     * 3. 相对登录接口（*.php 含 auth/login/signin，或含 action=login / 登录表单特征）
     * 探测不到则返回空串。
     * 说明：登录接口地址（如 auth.php）不一定能直接当 WebView 登录页使用，这里会排除
     * 纯 API 接口（验证码/状态检测等，如 auth.php?action=captcha、action=me），避免把
     * 返回 JSON/图片的接口当作登录页填进 loginUrl；但可据此提示 LLM 补上 loginUrl/loginCheckJs。
     */
    private fun discoverLoginUrl(html: String, baseUrl: String): String {
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return ""
        // 0) 弹窗登录站点：页面 JS 判断 auth/action 参数==login 时自动弹出登录框
        //    （常见于 SPA / 纯前端弹窗登录，无独立登录页，如 qd 代理站）
        val popupLogin = Regex(
            """(?:['"]auth['"]\s*\)\s*===?\s*['"]login['"]|[\?&]auth\s*=\s*login|[\?&]action\s*=\s*login)""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(html)
        if (popupLogin) {
            val sep = if (baseUrl.contains("?")) "&" else "?"
            val base = baseUrl.substringBefore('#').trimEnd('?', '&')
            val candidate = base + sep + "auth=login"
            if (candidate.startsWith("http")) return candidate
        }
        // 1) 导航里的网页登录链接
        for (a in doc.select("a[href]")) {
            val text = a.text().trim()
            val href = a.absUrl("href").ifBlank { a.attr("href").trim() }
            if (href.isBlank() || href.startsWith("javascript:") || href == "#") continue
            val low = href.lowercase()
            if ((text.contains("登录") || low.contains("login") || low.contains("signin")) &&
                !isLoginApiUrl(href)
            ) {
                return href
            }
        }
        // 2) 脚本里的绝对登录地址
        Regex("""['"`](https?://[^'"`\s]*?(?:login|signin|logout)[^'"`\s]*)['"`]""", RegexOption.IGNORE_CASE)
            .find(html)?.let { m ->
                val raw = m.groupValues[1].substringBeforeLast("'")
                if (raw.isNotBlank() && !isLoginApiUrl(raw)) return raw
            }
        // 3) 相对登录接口 / 登录表单特征
        val hasLoginForm = Regex(
            """(?:login|signin|auth)\.php|action\s*[:=]\s*['"]?login|loginForm|loginPassword|loginCaptcha|loginEmail""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(html)
        if (hasLoginForm) {
            Regex("""(\S*(?:auth|login|signin)\S*\.php(?:\?[^'"`\s]*)?)""", RegexOption.IGNORE_CASE)
                .find(html)?.let { m ->
                    val raw = m.groupValues[1]
                    if (!isLoginApiUrl(raw)) {
                        val abs = runCatching { java.net.URL(java.net.URL(baseUrl), raw).toString() }
                            .getOrDefault("")
                        if (abs.isNotEmpty() && abs.startsWith("http")) return abs
                    }
                }
        }
        return ""
    }

    /**
     * 判断一个地址是否是「纯 API 接口」而非可当登录页使用的网页地址。
     * 返回 true 表示应排除：验证码/状态检测/会话信息等接口，WebView 打开只会得到
     * JSON 或图片，无法完成登录。含 `action=login`/`auth=login` 的登录动作不算 API。
     */
    private fun isLoginApiUrl(url: String): Boolean {
        val low = url.lowercase()
        if (low.contains("auth=login") || low.contains("action=login") || low.contains("action=signin")) {
            return false
        }
        return low.contains("action=captcha") ||
            low.contains("action=me") ||
            low.contains("action=check") ||
            low.contains("action=session") ||
            low.contains("action=profile") ||
            low.contains("action=info") ||
            low.contains("action=logout") ||
            (low.contains(".php") && low.contains("action="))
    }

    /**
     * 探测站点的「登录状态检测」接口，供生成书源的 loginCheckJs 使用。
     * 常见形态：auth.php?action=me、/api/user/info、profile、session、action=check 等，
     * 返回体里 user/登录用户信息为 null ↔ 未登录、非空 ↔ 已登录。
     * 探测顺序：
     * 1. auth/login/user.php 接口且脚本里出现 me / info / session / profile / check 动作
     * 2. 脚本中出现的绝对 http 地址含 me/session/profile/userinfo/account
     * 探测不到返回空串（此时 loginCheckJs 留空即可）。
     */
    private fun discoverLoginCheckUrl(html: String, baseUrl: String): String {
        // 1) auth/login/user.php 类接口 + 登录态动作
        val hasAuthPhp = Regex("""(?:auth|login|user)\.php""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        val hasCheckAction = Regex(
            """['"\s]action\s*=\s*['"]?(?:me|info|session|profile|check)""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(html) ||
            Regex("""(?:request|authRequest)\s*\(\s*['"]me['"]""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        if (hasAuthPhp && hasCheckAction) {
            Regex("""(\S*(?:auth|login|user)\S*\.php[^'"`\s]*)""", RegexOption.IGNORE_CASE)
                .find(html)?.let { m ->
                    val abs = runCatching { java.net.URL(java.net.URL(baseUrl), m.groupValues[1]).toString() }
                        .getOrDefault("")
                    if (abs.isNotEmpty() && abs.startsWith("http")) {
                        return if (abs.contains("action=")) {
                            abs.replace(Regex("""action=[^&\s""]*"""), "action=me")
                        } else {
                            abs.trimEnd('?', '&') + "?action=me"
                        }
                    }
                }
        }
        // 2) 绝对登录态接口
        Regex("""['"`](https?://[^'"`\s]*(?:me|session|profile|userinfo|account)[^'"`\s]*)['"`]""", RegexOption.IGNORE_CASE)
            .find(html)?.let { m -> if (m.groupValues[1].isNotBlank()) return m.groupValues[1] }
        return ""
    }

    fun fetchHtml(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val url = parameters["url"]?.firstOrNull()?.trim()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请填写需要分析的网站地址")
        }
        val keyword = parameters["keyword"]?.firstOrNull()?.trim()
        val header = parameters["header"]?.firstOrNull()
        val cookie = parameters["cookie"]?.firstOrNull()
        return fetchHtmlContent(url, keyword, header, cookie).fold(
            onSuccess = {
                returnData.setData(
                    mapOf(
                        "url" to it.url,
                        "charset" to it.charset,
                        "length" to it.length,
                        "html" to it.html,
                        "loginUrl" to it.loginUrl,
                        "loginCheckUrl" to it.loginCheckUrl,
                        "apiEndpoints" to it.apiEndpoints,
                        "sampleSearch" to it.sampleSearch,
                        "sampleCatalog" to it.sampleCatalog,
                        "sampleExplore" to it.sampleExplore,
                        "exploreLinks" to it.exploreLinks,
                        "embeddedJson" to it.embeddedJson,
                        "coverTemplates" to it.coverTemplates
                    )
                )
            },
            onFailure = {
                returnData.setErrorMsg("抓取失败: ${it.message ?: it.javaClass.simpleName}")
            }
        )
    }
}
