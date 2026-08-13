package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.EncodingDetect
import okhttp3.Request
import java.nio.charset.Charset

/**
 * AI 生成书源辅助接口
 *
 * 移植自 DandanLLab/legadoSkill（MIT）的 smart_fetch_html 能力：
 * 由 App 内置 HTTP 服务代理抓取目标网站 HTML（浏览器直接跨域抓取会被 CORS 拦截），
 * 自动检测编码后返回给 web 端，供 AI 分析网页结构生成书源。
 */
object AiSourceController {

    /** 单次抓取的最大字节数，防止大页面撑爆内存 */
    private const val MAX_BYTES = 1_000_000

    /** 返回给前端的最大字符数，超出截断避免 prompt 过大 */
    private const val MAX_CHARS = 200_000

    /** 抓取结果 */
    data class HtmlContent(
        val url: String,
        val charset: String,
        val length: Int,
        val html: String
    )

    /**
     * 抓取目标网站 HTML 并自动检测编码（供 HTTP 接口与原生 AI 生成页共用）
     */
    fun fetchHtmlContent(url: String): Result<HtmlContent> {
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

                // 限量读取，避免把超大响应整体读入内存
                // okio 的 read(ByteArray, Int, Int) 返回 Int（-1 表示流结束）
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
                val truncated = if (html.length > MAX_CHARS) html.substring(0, MAX_CHARS) else html

                HtmlContent(url, charset, html.length, truncated)
            }
        }
    }

    fun fetchHtml(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val url = parameters["url"]?.firstOrNull()?.trim()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请填写需要分析的网站地址")
        }
        return fetchHtmlContent(url).fold(
            onSuccess = {
                returnData.setData(
                    mapOf(
                        "url" to it.url,
                        "charset" to it.charset,
                        "length" to it.length,
                        "html" to it.html
                    )
                )
            },
            onFailure = {
                returnData.setErrorMsg("抓取失败: ${it.message ?: it.javaClass.simpleName}")
            }
        )
    }
}
