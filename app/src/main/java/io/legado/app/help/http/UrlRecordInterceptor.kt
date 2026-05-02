package io.legado.app.help.http

import io.legado.app.data.appDb
import io.legado.app.data.entities.UrlRecord
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object UrlRecordInterceptor : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!AppConfig.recordUrl) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()
        var response: Response? = null
        var errorMsg: String? = null
        var responseCode = 0

        try {
            response = chain.proceed(request)
            responseCode = response.code
            return response
        } catch (e: Exception) {
            errorMsg = e.message
            throw e
        } finally {
            val duration = System.currentTimeMillis() - startTime
            
            val url = request.url.toString()
            val domain = request.url.host
            
            val sourceName = request.header("X-Source-Name")
            val sourceUrl = request.header("X-Source-Url")
            
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

            scope.launch {
                try {
                    appDb.urlRecordDao.insert(record)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancelAll() {
        scope.cancel()
    }
}
