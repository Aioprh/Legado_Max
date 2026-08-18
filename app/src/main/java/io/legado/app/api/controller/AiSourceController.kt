package io.legado.app.api.controller

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.app.api.ReturnData
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.EncodingDetect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
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

    /** 接口探测超时（毫秒） */
    private const val API_TIMEOUT_MS = 15_000L

    /** 抓取时的默认浏览器 UA，避免被常见反爬拦截 */
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 匹配常见前端请求里的 URL 模板（相对路径或带 `${}` 占位符）
     * 覆盖 fetch / axios / $.ajax / $.get / $.post / $.getJSON / uni.request /
     * XMLHttpRequest / new Request，同时兼容 $.ajax({url:...}) 等对象形式
     * 并检测 POST 方法与请求体
     */
    private val fetchUrlPattern: Pattern = Pattern.compile(
        """fetch\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """axios\s*\.\s*(?:get|post|put|delete|request)\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """axios\s*\(\s*\{\s*(?:[^{}]*?)\burl\s*:\s*[`'"]([^`'"]+)[`'"]|""" +
            """\$\s*\.\s*(?:get|post|getJSON|ajax)\s*\(\s*[`'"]([^`'"]+)[`'"]|""" +
            """\$\s*\.\s*(?:get|post|ajax)\s*\(\s*\{\s*(?:[^{}]*?)\burl\s*:\s*[`'"]([^`'"]+)[`'"]|""" +
            """(?:uni\.request|request)\s*\(\s*\{\s*url\s*:\s*[`'"]([^`'"]+)[`'"]|""" +
            """XMLHttpRequest[^;]*?\.\s*open\s*\(\s*['"](?:GET|POST)['"]\s*,\s*[`'"]([^`'"]+)[`'"]|""" +
            """new\s+Request\s*\(\s*[`'"]([^`'"]+)[`'"]""",
        Pattern.CASE_INSENSITIVE
    )

    /** 探测 POST 请求的方法与请求体模板（字符串形式 body/data） */
    private val postMethodPattern: Pattern = Pattern.compile(
        """(?:method|type)\s*[:=]\s*['"]POST['"]""",
        Pattern.CASE_INSENSITIVE
    )
    private val postBodyPattern: Pattern = Pattern.compile(
        """(?:body|data)\s*[:=]\s*[`'"]([^`'"]+)[`'"]""",
        Pattern.CASE_INSENSITIVE
    )

    /** body/data 为 JS 对象字面量（如 data: { keyword: kw, page: 1 }），兼容 JSON.stringify({...}) 包装 */
    private val postBodyObjectPattern: Pattern = Pattern.compile(
        """(?:body|data)\s*[:=]\s*(?:JSON\s*\.\s*stringify\s*\(\s*)?\{\s*([^}]*)\}""",
        Pattern.CASE_INSENSITIVE
    )

    /** axios.post('/url', {...}) 等隐式 POST 调用（无显式 method 字段） */
    private val implicitPostPattern: Pattern = Pattern.compile(
        """(?:axios|http|https?)\s*\.\s*post\s*\(|(?:\$|jQuery)\s*\.\s*post\s*\(""",
        Pattern.CASE_INSENSITIVE
    )

    /** 裸对象请求体参数（如 axios.post('/url', { keyword: kw }) 的第二个参数） */
    private val rawObjectBodyPattern: Pattern = Pattern.compile(
        """(?:\)|['"])\s*,\s*\{\s*([^{}]*)\}""",
        Pattern.CASE_INSENSITIVE
    )

    /** 常见搜索查询参数名，用于从 URL 中识别搜索接口（即使路径不含 search） */
    private val searchParamNames = setOf(
        "wd", "q", "kw", "so", "query", "key", "word", "find",
        "keyword", "searchkey", "searchword", "search_key", "skey", "keywrod",
        "bookname", "name", "searchtext", "searchvalue", "searchname", "novelname",
        "searchs", "sousuo", "sosuo", "txtname", "articlename"
    )

    /** 拼接式写法里可能承载关键词的 JS 变量名（如 '/so/' + kw） */
    private val keywordVars = setOf(
        "keyword", "kw", "q", "wd", "name", "key", "searchkey", "searchword",
        "bookname", "word", "novelname", "searchname", "searchtext", "searchvalue",
        "keys", "sou", "so", "find", "query"
    )

    /** 探测 URL 字符串后用 `+ 变量` 拼接关键词的写法（'search?name=' + name 等） */
    private val keywordConcatPattern: Pattern = Pattern.compile(
        """\s*\+\s*(?:encodeURIComponent\s*\(\s*)?[`'"]?\s*(keyword|kw|q|wd|name|key|searchkey|searchword|bookname|word|novelname|searchname|searchtext|searchvalue|keys|so|sou|find|query)\s*[`'"]?\s*(?:\)\s*)?(?=[+\-;,&?)}])""",
        Pattern.CASE_INSENSITIVE
    )

    /** 常见详情/书籍 ID 参数名 */
    private val idParamNames = setOf(
        "id", "book_id", "bookid", "novel_id", "novelid", "book", "detail", "bookinfo"
    )

    /** 识别 URL/body 中值为空的搜索参数（来自变量拼接，如 ?keyword= + kw） */
    private val blankSearchParamPattern: Pattern = Pattern.compile(
        """([?&])(?:keyword|searchKey|search_keyword|search_key|searchkey|searchword|searchtext|searchvalue|keywrod|novelname|wd|q|kw|so|query|word|find|bookname|name|skey|key)=([^&]*)""",
        Pattern.CASE_INSENSITIVE
    )

    /** `${...}` 模板占位符 -> Legado 书源占位符 */
    private val templateReplacements = listOf(
        Regex("""\$\{encodeURIComponent\s*\(\s*keyword\s*\)\}""") to "{{key}}",
        Regex("""\$\{\s*keyword\s*\}""") to "{{key}}",
        Regex("""\$\{encodeURIComponent\s*\(\s*page\s*\)\}""") to "{{page}}",
        Regex("""\$\{\s*page\s*\}""") to "{{page}}",
        Regex("""\$\{encodeURIComponent\s*\(\s*bookId\s*\)\}""") to "{{bookId}}",
        Regex("""\$\{\s*bookId\s*\}""") to "{{bookId}}",
        Regex("""\$\{encodeURIComponent\s*\(\s*chapterId\s*\)\}""") to "{{chapterId}}",
        Regex("""\$\{\s*chapterId\s*\}""") to "{{chapterId}}"
    )

    /** 常见内嵌 JSON 的全局变量名 */
    private val embeddedJsonKeys = listOf(
        "__INITIAL_STATE__", "__NUXT__", "__NEXT_DATA__", "__PRELOADED_STATE__",
        "__INITIAL_DATA__", "__STATE__", "__DATA__", "__data__", "__NEXT_DATA_JSON__"
    )

    /** 抓取结果 */
    data class HtmlContent(
        val url: String,
        val charset: String,
        val length: Int,
        val html: String,
        val apiEndpoints: List<ApiEndpoint>,
        val sampleSearch: SampleResult,
        val sampleCatalog: SampleResult,
        val embeddedJson: List<String>
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

                HtmlContent(
                    url, charset, truncated.length, truncated,
                    endpoints, sampleSearch, sampleCatalog, embeddedJson
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
        // 无法确定类型的接口也保留，供 AI 参考（很多搜索接口路径不含 search 字样）
        val candidates = LinkedHashMap<String, ApiEndpoint>()
        val matcher = fetchUrlPattern.matcher(html)
        while (matcher.find()) {
            val template = (1..matcher.groupCount())
                .firstNotNullOfOrNull { matcher.group(it)?.takeIf { g -> g.isNotBlank() } }
                ?: continue
            if (template.startsWith("$")) continue

            var cleaned = template
            for ((re, rep) in templateReplacements) cleaned = cleaned.replace(re, rep)
            cleaned = cleaned.replace(Regex("""\$\{[^}]*\}"""), "")
            if (cleaned.isBlank()) continue

            // 处理 `'路径' + kw` 字符串拼接关键词写法（关键词未进入模板，需回填）
            val tail = html.substring(matcher.end(), minOf(html.length, matcher.end() + 200))
            val cm = keywordConcatPattern.matcher(tail)
            if (cm.lookingAt() && cm.group(1).lowercase() in keywordVars) {
                if (cleaned.endsWith("=") || cleaned.endsWith("/") ||
                    !cleaned.substringAfterLast('/').contains('.')
                ) {
                    cleaned += "{{key}}"
                }
            }

            val resolved = runCatching { URL(URL(baseUrl), cleaned).toString() }.getOrDefault("")
            if (resolved.isBlank() || !resolved.startsWith("http")) continue

            // 判断是否为 POST：显式 method/type 字段，或 axios.post/$.post 隐式写法
            val contextStart = (matcher.start() - 120).coerceAtLeast(0)
            val context = html.substring(contextStart, minOf(html.length, matcher.end() + 200))
            val matched = matcher.group()
            val isPost = implicitPostPattern.matcher(matched).find() ||
                postMethodPattern.matcher(context).find()
            val postBody = if (isPost) {
                postBodyPattern.matcher(context).run {
                    if (find()) group(1) ?: "" else ""
                }.ifEmpty {
                    // body/data 为 JS 对象字面量（如 data: { keyword: kw }）时，提取键名构造表单模板
                    postBodyObjectPattern.matcher(context).run {
                        if (find()) objectBodyToForm(group(1) ?: "") else ""
                    }
                }.ifEmpty {
                    // axios.post('/url', { keyword: kw }) 的裸对象参数
                    rawObjectBodyPattern.matcher(context).run {
                        if (find()) objectBodyToForm(group(1) ?: "") else ""
                    }
                }
            } else {
                ""
            }

            // POST 接口参数在 body 里，类型判断需结合 postBody（URL 可能无搜索字样）
            val type = guessType(resolved, postBody)

            if (type == null) {
                val existing = candidates[resolved]
                if (existing == null) {
                    candidates[resolved] = ApiEndpoint("other", resolved, if (isPost) "POST" else "GET")
                        .apply { if (isPost && postBody.isNotBlank()) this.postBody = postBody }
                }
                continue
            }

            // search 接口优先带 keyword 占位符的；detail 接口优先带 bookId 的
            val existing = found[type]
            val better = when {
                existing == null -> true
                type == "search" && cleaned.contains("keyword") && !existing.url.contains("{{key}}") -> true
                type == "detail" && cleaned.contains("bookid") && !existing.url.contains("{{bookId}}") -> true
                else -> false
            }
            if (better) {
                found[type] = ApiEndpoint(type, resolved, if (isPost) "POST" else "GET")
                    .apply { if (isPost && postBody.isNotBlank()) this.postBody = postBody }
            }
        }

        // 额外探测 <form> 表单搜索入口（老式站点）
        for (fe in discoverFormEndpoints(html, baseUrl)) {
            if (fe.type != "other" && found[fe.type] == null) {
                found[fe.type] = fe
            }
        }

        val order = listOf("search", "detail", "catalog", "content")
        return order.mapNotNull { found[it] } + candidates.values.take(8)
    }

    /**
     * 根据 URL 判断接口类型：
     * 路径含 search/keyword/find 等关键词优先；其次看查询参数名（wd/q/kw 等）；
     * 再判断 ID 类参数（book_id 等）；URL 无参数时结合 POST 请求体参数名判断。
     * 无法判断返回 null（保留为候选）。
     */
    private fun guessType(url: String, postBody: String? = null): String? {
        val u = url.lowercase()
        // URL 模板中含搜索关键词占位符 {{key}}，必然是搜索接口（模板字面量/拼接式路径均会走到这里）
        if (u.contains("{{key}}")) return "search"
        // 含书籍 ID 占位符 {{bookId}} 的视为详情接口
        if (u.contains("{{bookid}}")) return "detail"
        if (u.contains("search") || u.contains("keyword") || u.contains("searchword") ||
            u.contains("searchkey") || u.contains("find") || u.contains("sousuo") ||
            u.contains("sosuo")
        ) return "search"
        if (u.contains("catalog") || u.contains("chapterlist") || u.contains("toc")) return "catalog"
        if (u.contains("chaptercontent") || (u.contains("content") && u.contains("chapter"))) return "content"
        if (u.contains("detail") || u.contains("bookinfo")) return "detail"
        val query = u.substringAfter('?', "").substringBefore('#')
        val params = query.split('&')
            .mapNotNull { it.substringBefore('=').trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (params.any { it in searchParamNames }) return "search"
        if (params.any { it in idParamNames }) return "detail"
        // URL 无查询参数时，看 POST 请求体里的参数名（如 axios.post('/api/query', { wd: kw })）
        if (!postBody.isNullOrBlank()) {
            val bodyParams = postBody.split('&')
                .mapNotNull { it.substringBefore('=').trim().lowercase() }
                .filter { it.isNotEmpty() }
            if (bodyParams.any { it in searchParamNames }) return "search"
            if (bodyParams.any { it in idParamNames }) return "detail"
        }
        return null
    }

    /**
     * 将 JS 对象字面量形式的请求体（如 "keyword: this.keyword, page: this.page"）
     * 转换为 x-www-form-urlencoded 模板：含关键词的键填 {{key}}，其余键留空。
     */
    private fun objectBodyToForm(bodyObject: String): String {
        val keys = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*:""")
            .findAll(bodyObject)
            .map { it.groupValues[1] }
            .toList()
        if (keys.isEmpty()) return ""
        val searchKey = keys.firstOrNull { it.lowercase() in searchParamNames }
            ?: keys.firstOrNull { k ->
                listOf("kw", "q", "wd", "key", "word").any { k.lowercase().contains(it) }
            }
            ?: return ""
        return keys.joinToString("&") { k -> if (k == searchKey) "$k={{key}}" else "$k=" }
    }

    /**
     * 探测 <form> 表单搜索入口，返回候选接口。
     * 兼容 GET/POST 表单、无 action 表单（提交到当前页）。
     * 优先按输入框 name 是否为常见搜索参数名识别搜索表单（覆盖首页搜索框场景）。
     */
    private fun discoverFormEndpoints(html: String, baseUrl: String): List<ApiEndpoint> {
        val found = LinkedHashMap<String, ApiEndpoint>()
        val inputNameRegex = Regex(
            """<input\b[^>]*\bname\s*=\s*["']?([A-Za-z_][A-Za-z0-9_]*)["']?""",
            RegexOption.IGNORE_CASE
        )
        val formTagRegex = Regex("""<form\b[^>]*>""", RegexOption.IGNORE_CASE)
        for (m in formTagRegex.findAll(html)) {
            val tag = m.value
            val action = Regex("""\baction\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("javascript:") }
                ?: baseUrl
            val resolved = runCatching { URL(URL(baseUrl), action).toString() }.getOrDefault("")
            if (resolved.isBlank() || !resolved.startsWith("http")) continue

            val formBody = html.substring(m.range.last + 1, minOf(html.length, m.range.last + 1 + 800))
            val isPost = Regex("""\bmethod\s*=\s*["']post["']""", RegexOption.IGNORE_CASE)
                .containsMatchIn(tag)
            // 优先找 name 为常见搜索参数名的输入框（跳过 hidden 等干扰项），再退化为首个输入框
            val inputName = inputNameRegex.findAll(formBody)
                .map { it.groupValues[1] }
                .firstOrNull { it.lowercase() in searchParamNames }
                ?: inputNameRegex.find(formBody)?.groupValues?.get(1)

            // 首页搜索框的 action 常为首页地址或为空，按输入框 name 直接判定为搜索表单
            val isSearchForm = inputName != null && inputName.lowercase() in searchParamNames
            if (isSearchForm) {
                if (isPost) {
                    if (found["search"] == null) {
                        found["search"] = ApiEndpoint("search", resolved, "POST")
                            .apply { postBody = "$inputName={{key}}" }
                    }
                } else {
                    val sep = if (resolved.contains("?")) "&" else "?"
                    if (found["search"] == null) {
                        found["search"] = ApiEndpoint("search", "$resolved$sep$inputName={{key}}", "GET")
                    }
                }
                continue
            }

            val type = guessType(resolved) ?: continue
            if (found[type] == null) {
                found[type] = ApiEndpoint(type, resolved, if (isPost) "POST" else "GET")
                    .apply { if (isPost && inputName != null) postBody = "$inputName={{key}}" }
            }
        }
        return found.values.toList()
    }

    /**
     * 将 URL / 请求体里值为空的搜索参数回填关键词。
     * 处理前端常见 `'/search?keyword=' + kw` 变量拼接写法（抓到的参数值为空）。
     */
    private fun fillBlankSearchParams(url: String, kw: String): String {
        val m = blankSearchParamPattern.matcher(url)
        val sb = StringBuilder()
        var changed = false
        while (m.find()) {
            if (m.group(3).isBlank()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + "=" + kw))
                changed = true
            }
        }
        m.appendTail(sb)
        return if (changed) sb.toString() else url
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
            ?: return probeSearchCandidates(endpoints, keyword, header, cookie)
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
            // 变量拼接导致参数值为空（如 ?keyword= + kw）时回填关键词
            .let { fillBlankSearchParams(it, kw) }

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
                .let { fillBlankSearchParams(it, kw) }
            return fetchPostSample(url, body, header, cookie)
        }

        if (keyParam == null && !url.contains(Regex(
                """[?&](?:keyword|searchKey|search_keyword|searchkey|searchword|wd|q|kw|so|query|word|find|bookname|name|skey|key)="""
            ))) {
            url = if (url.contains("?")) "$url&keyword=$kw" else "$url?keyword=$kw"
        }
        if (pageParam == null && !url.contains(Regex("""[?&]page\d*="""))) {
            url = if (url.contains("?")) "$url&page=1" else "$url?page=1"
        }
        return fetchJsonSample(url, header, cookie)
    }

    /**
     * 未发现明确搜索接口时的兜底探测：
     * 对疑似搜索的候选接口用关键词请求，取首个返回合法 JSON 且疑似书数据的响应。
     * 只挑「带 {{key}} 占位、有 POST 请求体、或路径含 so/sou/query 等」的候选，避免大量无效请求。
     */
    private fun probeSearchCandidates(
        endpoints: List<ApiEndpoint>,
        keyword: String,
        header: String?,
        cookie: String?
    ): SampleResult {
        val kw = URLEncoder.encode(keyword, "UTF-8")
        val searchish = Regex(
            """(?:^|/)(?:so|sou|sousuo|sosuo|query|find|search|booksearch)(?:/|\?|$)""",
            RegexOption.IGNORE_CASE
        )
        val plausible = endpoints
            .filter { it.type == "other" }
            .filter { ep ->
                ep.url.contains("{{key}}") || ep.postBody.isNotBlank() ||
                    searchish.containsMatchIn(ep.url)
            }
            .take(3)
        if (plausible.isEmpty()) {
            return SampleResult(
                ok = false,
                error = "未在页面脚本中发现搜索接口（未找到疑似搜索的 JSON API / 表单）"
            )
        }
        for (ep in plausible) {
            val url = ep.url.replace("{{key}}", kw)
                .replace(Regex("""\{\{[^}]*\}\}"""), "")
            val result = if (ep.method == "POST") {
                val body = (ep.postBody.ifBlank { "keyword={{key}}" })
                    .replace("{{key}}", kw)
                    .replace(Regex("""\{\{[^}]*\}\}"""), "")
                fetchPostSample(url, body, header, cookie)
            } else {
                val u = if (url.contains("?")) "$url&keyword=$kw" else "$url?keyword=$kw"
                fetchJsonSample(u, header, cookie)
            }
            if (result.ok && isLikelyBookJson(result.json)) {
                return result
            }
        }
        return SampleResult(
            ok = false,
            error = "未在页面脚本中发现明确的搜索接口（疑似候选接口探测均失败）"
        )
    }

    /** 粗判 JSON 是否像书籍搜索结果（含书名/作者等关键字段），用于兜底探测去伪 */
    private fun isLikelyBookJson(text: String): Boolean {
        val t = text.take(6000).lowercase()
        return listOf(
            "\"name\"", "\"bookname\"", "\"book_name\"", "\"title\"", "\"book_title\"",
            "\"author\"", "\"book\"", "\"novelname\"", "\"novel_name\"", "\"novel\"",
            "\"books\"", "\"articlename\"", "\"bookname\"", "\"realname\""
        ).any { t.contains(it) }
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
            .replace("{{bookId}}", bookId)
            .replace(Regex("""\{\{[^}]*\}\}"""), "")
        return fetchJsonSample(url, header, cookie)
    }

    /**
     * 抓取接口示例 JSON（限时、截断、非 JSON 判失败）
     */
    private fun fetchJsonSample(url: String, header: String?, cookie: String?): SampleResult {
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
                        "apiEndpoints" to it.apiEndpoints,
                        "sampleSearch" to it.sampleSearch,
                        "sampleCatalog" to it.sampleCatalog,
                        "embeddedJson" to it.embeddedJson
                    )
                )
            },
            onFailure = {
                returnData.setErrorMsg("抓取失败: ${it.message ?: it.javaClass.simpleName}")
            }
        )
    }
}
