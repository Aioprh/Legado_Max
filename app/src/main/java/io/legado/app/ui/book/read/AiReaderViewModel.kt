package io.legado.app.ui.book.read

import android.app.Application
import cn.hutool.crypto.symmetric.AES
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.putString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI 阅读助手 ViewModel
 *
 * 复用「AI 生成书源」已持久化的 AI 接口配置（baseUrl / apiKey / model），
 * 在阅读页提供：
 * - 选中文字解释 / 问答（带当前章节上下文）
 * - 当前章节总结
 * 调用 OpenAI 兼容的 /chat/completions 接口，返回 Markdown/纯文本结果。
 */
class AiReaderViewModel(application: Application) : BaseViewModel(application) {

    /** AI 接口地址（OpenAI 兼容），复用书源生成配置 */
    var baseUrl: String
        get() = LocalConfig.getString(KEY_BASE_URL, "https://api.deepseek.com/v1") ?: ""
        set(value) {
            LocalConfig.putString(KEY_BASE_URL, value)
        }

    /** API Key（AES 加密后存储，复用书源生成配置） */
    var apiKey: String
        get() {
            val stored = LocalConfig.getString(KEY_API_KEY, "") ?: ""
            if (stored.isEmpty()) return ""
            if (!stored.startsWith(ENC_PREFIX)) return stored // 旧版本明文
            return runCatching {
                AES(cryptoKey).decryptStr(stored.removePrefix(ENC_PREFIX))
            }.getOrElse { "" }
        }
        set(value) {
            val encrypted = runCatching {
                ENC_PREFIX + AES(cryptoKey).encryptBase64(value)
            }.getOrElse { value }
            LocalConfig.putString(KEY_API_KEY, encrypted)
        }

    /** 模型名 */
    var model: String
        get() = LocalConfig.getString(KEY_MODEL, "deepseek-chat") ?: ""
        set(value) {
            LocalConfig.putString(KEY_MODEL, value)
        }

    /**
     * 调用 AI 生成结果
     * @param title 书名+章节名，用于上下文
     * @param context 章节正文（截断）
     * @param task 用户请求，如选中的文字或"总结本章"
     * @param mode 任务类型：explain / summary
     */
    suspend fun aiChat(
        title: String,
        context: String,
        task: String,
        mode: String
    ): String = withContext(Dispatchers.IO) {
        val system = when (mode) {
            "explain" -> EXPLAIN_SYSTEM_PROMPT
            else -> SUMMARY_SYSTEM_PROMPT
        }
        val user = buildString {
            appendLine("【书名/章节】$title")
            if (context.isNotBlank()) {
                appendLine("【当前章节正文】")
                appendLine("----------")
                appendLine(context)
                appendLine("----------")
            }
            when (mode) {
                "explain" -> {
                    appendLine("【需要解析的文字】")
                    appendLine(task)
                }
                else -> appendLine("【要求】请根据以上章节正文，总结本章要点。")
            }
        }
        val messages = listOf(
            mapOf("role" to "system", "content" to system),
            mapOf("role" to "user", "content" to user)
        )
        requestCompletion(messages)
    }

    private suspend fun requestCompletion(
        messages: List<Map<String, String>>
    ): String {
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 AI API Key，请先在「AI 生成书源」的接口配置中填写")
        }
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val body = GSON.toJson(
            mapOf(
                "model" to model.ifBlank { "deepseek-chat" },
                "messages" to messages
            )
        )
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        aiHttpClient.newCall(request).await().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${text.take(200)}")
            }
            val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            root?.get("error")?.takeIf { it.isJsonObject }?.let { err ->
                throw RuntimeException(err.asJsonObject.get("message")?.asString ?: text.take(200))
            }
            val message = root?.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
            val content = message?.let { extractMessageContent(it) } ?: ""
            if (content.isBlank()) {
                throw RuntimeException("模型未返回内容，请重试或更换模型")
            }
            content.trim()
        }
    }

    /** 提取 choices[0].message.content（兼容字符串与内容块数组） */
    private fun extractMessageContent(message: JsonObject): String {
        val content = message.get("content")
        return when {
            content == null || content.isJsonNull -> ""
            content.isJsonPrimitive -> content.asString
            content.isJsonArray ->
                content.asJsonArray.joinToString("") { el ->
                    if (el.isJsonObject) el.asJsonObject.get("text")?.asString ?: "" else ""
                }
            else -> ""
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isCancelled) {
                    response.close()
                    return
                }
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        /** AI API 调用专用 HTTP 客户端，5 分钟超时 */
        private val aiHttpClient: okhttp3.OkHttpClient by lazy {
            okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .build()
        }

        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private const val ENC_PREFIX = "aes:"

        private val cryptoKey: ByteArray by lazy {
            MD5Utils.md5Encode("legado.ai-source.api-key").encodeToByteArray(0, 16)
        }

        /** 文字解析/问答系统提示词 */
        private const val EXPLAIN_SYSTEM_PROMPT = """
你是阅读 AI 助手，精通文学与小说内容分析。用户会提供选自某本书的章节正文和一句需要解析的话。
请针对用户给出的文字，结合章节上下文，给出清晰、准确、有深度的解释：
- 若是生僻词/古语/专业术语，解释其含义并在小说语境下的含义
- 若是人物对话/行为，分析其深层含义、人物性格与剧情作用
- 若是典故/背景，说明典故出处及在文中的作用
请用简洁的中文作答，条理清晰，必要时分点说明，不要编造不存在的剧情。
""".trimIndent()

        /** 章节总结系统提示词 */
        private const val SUMMARY_SYSTEM_PROMPT = """
你是阅读 AI 助手，精通小说内容总结。用户会提供某本书的一个章节正文。
请总结本章内容，输出：
1. 本章情节概要（2-4 句话）
2. 关键人物与动态
3. 本章埋下的伏笔/悬念（如有）
4. 本章金句或精彩描写摘录（1-2 句，如有）
请用简洁的中文作答，条理清晰，忠实原文，不要编造不存在的剧情。
""".trimIndent()
    }
}