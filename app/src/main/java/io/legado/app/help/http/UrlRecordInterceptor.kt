package io.legado.app.help.http

import io.legado.app.data.appDb
import io.legado.app.data.entities.UrlRecord
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * URL访问记录拦截器
 * 
 * OkHttp网络请求拦截器，用于记录所有通过OkHttpClient发起的HTTP请求。
 * 记录内容包括：请求URL、方法、响应状态码、耗时、错误信息等。
 * 
 * 功能特点：
 * - 通过 AppConfig.recordUrl 控制是否启用记录
 * - 使用协程异步写入数据库，不阻塞主请求
 * - 支持记录POST请求体（限制1000字符以内）
 * - 自动提取请求来源信息（通过X-Source-Name/X-Source-Url请求头）
 * 
 * 使用方式：
 * 在 OkHttpClient 构建时添加此拦截器：
 * ```
 * OkHttpClient.Builder()
 *     .addInterceptor(UrlRecordInterceptor)
 *     .build()
 * ```
 */
object UrlRecordInterceptor : Interceptor {

    // IO协程作用域，用于异步写入数据库
    private val scope = CoroutineScope(Dispatchers.IO)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // 检查是否启用URL记录功能
        if (!AppConfig.recordUrl) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()
        var response: Response? = null
        var errorMsg: String? = null
        var responseCode = 0

        try {
            // 执行实际请求
            response = chain.proceed(request)
            responseCode = response.code
            return response
        } catch (e: Exception) {
            // 记录错误信息
            errorMsg = e.message
            throw e
        } finally {
            // 计算请求耗时
            val duration = System.currentTimeMillis() - startTime
            
            // 提取请求信息
            val url = request.url.toString()
            val domain = request.url.host
            
            // 从请求头获取来源信息（书源名/URL）
            val sourceName = request.header("X-Source-Name")
            val sourceUrl = request.header("X-Source-Url")
            
            // 对于POST请求，记录请求体内容（限制1000字符）
            val requestBody = if (request.method.equals("POST", ignoreCase = true)) {
                request.body?.let { body ->
                    try {
                        val buffer = okio.Buffer()
                        body.writeTo(buffer)
                        buffer.readUtf8().takeIf { it.length <= 1000 }
                    } catch (e: Exception) {
                        null
                    }
                }
            } else null

            // 构建记录对象
            val record = UrlRecord(
                url = url,
                domain = domain,
                method = request.method,
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                timestamp = startTime,
                responseCode = responseCode,
                duration = duration,
                requestBody = requestBody,
                errorMsg = errorMsg
            )

            // 异步写入数据库，避免阻塞请求
            scope.launch {
                try {
                    appDb.urlRecordDao.insert(record)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
