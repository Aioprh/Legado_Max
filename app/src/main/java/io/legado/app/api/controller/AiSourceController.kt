package io.legado.app.api.controller

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.app.api.ReturnData
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.EncodingDetect
import okhttp3.Request
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
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
 * - 预处理 HTML：剔除 <style>、注释等对分析无价值的噪声，显著缩小 prompt
 * - 从页面脚本中自动发现 JSON API 接口（fetch/axios 请求），并转成 Legado 占位符形式
 * - 用搜索关键词调用搜索接口取回示例 JSON，再从示例中提取 book_id 调用目录接口，
 *   把真实 JSON 一并交给 LLM 编写 JSONPath 规则
 */
object AiSourceController {

    /** 单次抓取的最大字节数，防止大页面撑爆内存 */
    private const val MAX_BYTES = 1_000_000

    /** 返回给前端的最大字符数，超出截断避免 prompt 过大 */
    private const val MAX_CHARS = 200_000

    /** 接口示例响应的最大字符数，超出截断 */
    private const val MAX_API_CHARS = 12_000

    /** 接口探测超时（毫秒） */
    private const val API_TIMEOUT_MS = 15_000L

    /** 匹配 fetch/axios 请求里的 URL 模板（相对路径或带 `${}` 占位符） */
    private val fetchUrlPattern: Pattern = Pattern.compile(
        """fetch\s*\(\s*[`'"]([^`'"]+)[`'"]|axios\s*\.\s*(?:get|post|put|delete)\s*\(\s*[`'"]([^`'"]+)[`'"]""",
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

    /** 抓取结果 */
    data class HtmlContent(
        val url: String,
        val charset: String,
        val length: Int,
        val html: String,
        val apiEndpoints: List<ApiEndpoint>,
        val sampleSearch: SampleResult,
        val sampleCatalog: SampleResult
    )

    data class ApiEndpoint(val type: String, val url: String)

    data class SampleResult(
        val ok: Boolean,
        val url: String = "",
        val json: String = "",
        val error: String = ""
    )

    /**
     * 抓取目标网站 HTML 并自动检测编码（供 HTTP 接口与原生 AI 生成页共用）
     */
    fun fetchHtmlContent(url: String, keyword: String? = null): Result<HtmlContent> {
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("参数url不能为空，请填写需要分析的网站地址"))
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("url必须以http://或https://开头"))
        }
        return runCatching {
            val request = Request.Builder().url(url).build()
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

                // 预处理：剔除 CSS、注释等噪声，显著缩小传给 LLM 的文本
                val clean = preprocessHtml(html)
                val truncated = if (clean.length > MAX_CHARS) clean.substring(0, MAX_CHARS) else clean

                // 从脚本中发现 JSON API 接口并取搜索/目录示例响应
                val endpoints = discoverApiEndpoints(truncated, effectiveUrl)
                val sampleSearch = if (keyword.isNullOrBlank()) {
                    SampleResult(ok = false, error = "未提供搜索关键词，跳过接口探测")
                } else {
                    fetchSearchSample(endpoints, keyword)
                }
                val sampleCatalog = if (sampleSearch.ok) {
                    fetchCatalogSample(endpoints, sampleSearch.json)
                } else {
                    SampleResult(ok = false, error = "搜索接口探测失败，跳过目录探测")
                }

                HtmlContent(url, charset, truncated.length, truncated, endpoints, sampleSearch, sampleCatalog)
            }
        }
    }

    /**
     * 预处理 HTML：剔除 <style>、注释，折叠连续空行
     */
    private fun preprocessHtml(html: String): String {
        var s = html.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("<!--[\\s\\S]*?-->"), "")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s
    }

    /**
     * 从页面脚本中提取 JSON API 接口，并解析为绝对地址（相对路径基于最终页面地址）
     */
    private fun discoverApiEndpoints(html: String, baseUrl: String): List<ApiEndpoint> {
        val found = LinkedHashMap<String, String>()
        val matcher = fetchUrlPattern.matcher(html)
        while (matcher.find()) {
            val template = matcher.group(1) ?: matcher.group(2) ?: continue
            if (template.isBlank() || template.startsWith("$")) continue

            var cleaned = template
            for ((re, rep) in templateReplacements) cleaned = cleaned.replace(re, rep)
            cleaned = cleaned.replace(Regex("""\$\{[^}]*\}"""), "")
            if (cleaned.isBlank()) continue

            val type = when {
                cleaned.contains("keyword") || cleaned.contains("search") -> "search"
                cleaned.contains("catalog") -> "catalog"
                cleaned.contains("content") -> "content"
                cleaned.contains("detail") || cleaned.contains("book_id") -> "detail"
                else -> "other"
            }
            if (type == "other") continue

            val resolved = runCatching { URL(baseUrl, cleaned).toString() }.getOrDefault("")
            if (resolved.isBlank() || !resolved.startsWith("http")) continue

            // search 接口优先带 keyword 占位符的
            val existing = found[type]
            if (existing == null || (type == "search" && cleaned.contains("keyword") && !existing.contains("{{key}}"))) {
                found[type] = resolved
            }
        }
        val order = listOf("search", "detail", "catalog", "content")
        return order.mapNotNull { t -> found[t]?.let { ApiEndpoint(t, it) } }
    }

    /**
     * 调用搜索接口取回示例 JSON
     */
    private fun fetchSearchSample(endpoints: List<ApiEndpoint>, keyword: String): SampleResult {
        val search = endpoints.firstOrNull { it.type == "search" }
            ?: return SampleResult(ok = false, error = "未在页面脚本中发现搜索接口")
        val kw = URLEncoder.encode(keyword, "UTF-8")
        var url = search.url
            .replace("{{key}}", kw)
            .replace("{{page}}", "1")
            .replace(Regex("""\{\{[^}]*\}\}"""), "")
        if (!url.contains("keyword=")) {
            url = if (url.contains("?")) "$url&keyword=$kw" else "$url?keyword=$kw"
        }
        if (!url.contains("page=")) {
            url = if (url.contains("?")) "$url&page=1&page_size=20" else "$url?page=1&page_size=20"
        }
        return fetchJsonSample(url)
    }

    /**
     * 从搜索示例 JSON 中提取 book_id，调用目录接口取回章节示例 JSON
     */
    private fun fetchCatalogSample(endpoints: List<ApiEndpoint>, searchJson: String): SampleResult {
        val catalog = endpoints.firstOrNull { it.type == "catalog" }
            ?: return SampleResult(ok = false, error = "未在页面脚本中发现目录接口")
        val bookId = extractBookId(searchJson)
            ?: return SampleResult(ok = false, error = "未能从搜索示例中提取 book_id")
        val url = catalog.url
            .replace("{{bookId}}", bookId)
            .replace(Regex("""\{\{[^}]*\}\}"""), "")
        return fetchJsonSample(url)
    }

    /**
     * 抓取接口示例 JSON（限时、截断、非 JSON 判失败）
     */
    private fun fetchJsonSample(url: String): SampleResult {
        return runCatching {
            val request = Request.Builder().url(url).build()
            val client = okHttpClient.newBuilder()
                .callTimeout(API_TIMEOUT_MS)
                .readTimeout(API_TIMEOUT_MS)
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
        return fetchHtmlContent(url, keyword).fold(
            onSuccess = {
                returnData.setData(
                    mapOf(
                        "url" to it.url,
                        "charset" to it.charset,
                        "length" to it.length,
                        "html" to it.html,
                        "apiEndpoints" to it.apiEndpoints,
                        "sampleSearch" to it.sampleSearch,
                        "sampleCatalog" to it.sampleCatalog
                    )
                )
            },
            onFailure = {
                returnData.setErrorMsg("抓取失败: ${it.message ?: it.javaClass.simpleName}")
            }
        )
    }
}
