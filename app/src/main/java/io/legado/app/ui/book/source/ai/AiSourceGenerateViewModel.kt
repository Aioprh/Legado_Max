package io.legado.app.ui.book.source.ai

import android.app.Application
import cn.hutool.crypto.symmetric.AES
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.api.controller.AiSourceController
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.putFloat
import io.legado.app.utils.putInt
import io.legado.app.utils.putString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI 生成书源 ViewModel
 *
 * 移植自 DandanLLab/legadoSkill（MIT）的 AI 写书源能力：
 * 抓取目标网站 HTML -> 调用 OpenAI 兼容接口让 LLM 分析网页结构 -> 生成符合 Legado 规范的书源 JSON。
 * AI 接口配置通过 [LocalConfig] 持久化到本地。
 */
class AiSourceGenerateViewModel(application: Application) : BaseViewModel(application) {

    /** AI 接口地址（OpenAI 兼容） */
    var baseUrl: String
        get() = LocalConfig.getString(KEY_BASE_URL, "https://api.deepseek.com/v1") ?: ""
        set(value) {
            LocalConfig.putString(KEY_BASE_URL, value)
        }

    /** API Key（AES 加密后存储，防止明文落盘；兼容旧版本明文数据） */
    var apiKey: String
        get() {
            val stored = LocalConfig.getString(KEY_API_KEY, "") ?: ""
            if (stored.isEmpty()) return ""
            if (!stored.startsWith(ENC_PREFIX)) return stored // 旧版本明文，直接返回
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

    /** 采样温度（部分服务/推理模型不支持该参数，会自动去掉重试） */
    var temperature: Double
        get() = LocalConfig.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE).toDouble()
        set(value) {
            LocalConfig.putFloat(KEY_TEMPERATURE, value.toFloat())
        }

    /** 喂给 LLM 的 HTML 最大字符数（与模型上下文相关，可配置） */
    var promptHtmlLimit: Int
        get() = LocalConfig.getInt(KEY_PROMPT_HTML_LIMIT, DEFAULT_HTML_LIMIT)
        set(value) {
            LocalConfig.putInt(KEY_PROMPT_HTML_LIMIT, value)
        }

    /** 自动修复最大轮次 */
    var maxFixRounds: Int
        get() = LocalConfig.getInt(KEY_MAX_ROUNDS, DEFAULT_MAX_ROUNDS)
        set(value) {
            LocalConfig.putInt(KEY_MAX_ROUNDS, value)
        }

    /** 自定义 System Prompt（用户可覆盖默认提示词） */
    var customSystemPrompt: String
        get() = LocalConfig.getString(KEY_CUSTOM_PROMPT, "") ?: ""
        set(value) {
            LocalConfig.putString(KEY_CUSTOM_PROMPT, value)
        }

    /** 获取实际生效的 System Prompt：自定义非空则用自定义，否则用默认 */
    fun getEffectiveSystemPrompt(): String =
        customSystemPrompt.ifBlank { SYSTEM_PROMPT }

    /**
     * 抓取目标网站 HTML 并自动检测编码（供本页使用）
     * @param keyword 搜索关键词（可选），用于自动探测 JSON API 并抓取搜索/目录示例
     * @param header  自定义请求头（可选），多行 "Key: Value"，用于站点反爬
     * @param cookie  自定义 Cookie（可选）
     */
    fun fetchHtml(
        url: String,
        keyword: String? = null,
        header: String? = null,
        cookie: String? = null
    ): Result<AiSourceController.HtmlContent> =
        AiSourceController.fetchHtmlContent(url, keyword, header, cookie)

    /**
     * 调用 LLM 生成书源 JSON
     * 成功返回剥离 markdown 代码块后的 JSON 文本
     * 部分 OpenAI 兼容服务/推理模型会拒绝 temperature 参数，首次失败时自动去掉该参数重试一次。
     */
    suspend fun generate(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        return generateWithMessages(baseUrl, apiKey, model, messages)
    }

    /**
     * 带完整对话历史的 LLM 调用（供多轮修复复用，避免重复发送大段 HTML 上下文）
     */
    suspend fun generateWithMessages(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Map<String, String>>
    ): String {
        return try {
            requestCompletion(baseUrl, apiKey, model, messages, withParams = true)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val code = msg.substringAfter("HTTP ", "").substringBefore(":").toIntOrNull()
            // 参数被拒可能是 HTTP 4xx，也可能是 200+{"error":...}（见 requestCompletion），统一识别后去参重试
            val paramRejected = code == 400 || code == 422 ||
                msg.contains("temperature", ignoreCase = true) ||
                msg.contains("not support", ignoreCase = true) ||
                msg.contains("unsupported", ignoreCase = true)
            if (paramRejected) {
                // 可能是 temperature 等参数不受支持，去掉后重试一次
                requestCompletion(baseUrl, apiKey, model, messages, withParams = false)
            } else {
                throw e
            }
        }
    }

    private suspend fun requestCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Map<String, String>>,
        withParams: Boolean
    ): String {
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val bodyMap = mutableMapOf<String, Any>(
            "model" to model.ifBlank { "gpt-4o-mini" },
            "messages" to messages
        )
        if (withParams) {
            bodyMap["temperature"] = temperature
        }
        val body = GSON.toJson(bodyMap)
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
            // 即使 HTTP 200，部分网关/模型不支持某参数时也会返回 {"error": ...}，需转成异常以触发去参重试
            val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            root?.get("error")?.takeIf { it.isJsonObject }?.let { err ->
                throw RuntimeException("HTTP 200: ${err.asJsonObject.get("message")?.asString ?: text.take(200)}")
            }
            // 兼容 content 为字符串 / 内容块数组（[{type,text}]）等情况
            val message = root?.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
            val content = message?.let { extractMessageContent(it) } ?: ""
            if (content.isBlank()) {
                // 推理模型（deepseek-reasoner / deepseek-v4-flash 等）未生成最终正文：
                // 正常响应中 content 应为书源 JSON，若为空且思考在 reasoning_content，
                // 说明模型思考后未产出结果，给出明确提示而非把思维链当结果。
                val reasoning = message?.get("reasoning_content")
                    ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (reasoning.isNotBlank()) {
                    throw RuntimeException("模型未返回内容: 推理模型未生成最终正文（思考后 content 为空），请重试或更换模型")
                }
                throw RuntimeException("模型未返回内容: ${text.take(200)}")
            }
            return stripCodeFence(content)
        }
    }

    /**
     * 从 choices[0].message 提取正文：
     * content 为字符串直接取；content 为内容块数组（[{type,text}]）时拼接 text；
     * content 为 null/缺失/空串时返回空（由调用方判断：若同时存在 reasoning_content，
     * 说明是推理模型未生成最终正文，应给出提示而非使用思维链）。
     */
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

    /**
     * 抓取指定 URL 的真实响应体（截断），供自动修复反馈使用。
     * 验证失败的步骤只回传「解析为空/失败」时，模型看不到接口实际结构只能盲猜；
     * 附带真实返回（JSON/HTML）后，模型才能写出正确的选择器 / JSONPath。
     * @return 形如 "真实返回（JSON，前 N 字符）：\n---\n{snippet}\n---" 的片段，抓取失败返回 null
     */
    private suspend fun fetchStepSample(url: String, maxChars: Int = 8000): String? {
        if (url.isBlank() || !url.startsWith("http://") && !url.startsWith("https://")) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string().orEmpty()
                    if (text.isBlank()) return@use null
                    val truncated = if (text.length > maxChars) {
                        text.substring(0, maxChars) + "\n...(已截断)"
                    } else {
                        text
                    }
                    val trim = text.trimStart()
                    val type = when {
                        trim.startsWith("{") || trim.startsWith("[") -> "JSON"
                        trim.startsWith("<") -> "HTML"
                        else -> "文本"
                    }
                    "真实返回（$type，前 ${truncated.length} 字符）：\n----------\n$truncated\n----------"
                }
            }.getOrNull()
        }
    }

    /**
     * 以挂起方式执行 HTTP 请求，协程取消时同步取消底层 OkHttp 调用
     */
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

    /** 自动修复一轮：用真实搜索验证 -> 若有误则反馈 LLM 修复 */
    suspend fun autoFix(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        sourceJson: String,
        keyword: String,
        exploreLinks: List<Pair<String, String>> = emptyList(),
        maxRounds: Int = maxFixRounds
    ): AutoFixResult {
        var round = 0
        var current = sourceJson
        val log = StringBuilder()
        // 动态收敛判定：记录上一轮失败指纹，连续两轮相同（LLM 修不到点子）即提前停止
        var lastFingerprint: String? = null
        var stopReason = "达到最大轮次（$maxRounds）仍未能通过"
        // 使用对话历史代替重复发送完整 HTML 上下文
        val messages = mutableListOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        while (round < maxRounds) {
            round++
            log.appendLine("[第 $round 轮] 用真实搜索验证书源...")
            val verify = verifyByRealSearch(current, keyword, exploreLinks)
            log.appendLine(verify.summary)
            if (verify.succeeded) {
                return AutoFixResult(ok = true, rounds = round, json = verify.fixedJson ?: current, log = log.toString())
            }
            val fingerprint = failureStage(verify.summary)
            // 连续两轮同一阶段失败 → 不再收敛，提前结束
            if (lastFingerprint == fingerprint && round >= 2) {
                stopReason = "连续两轮失败于同一环节（$fingerprint），问题未收敛，提前停止（已用 $round 轮）"
                log.appendLine(stopReason)
                return AutoFixResult(ok = false, rounds = round, json = current, log = log.toString())
            }
            lastFingerprint = fingerprint
            // 组装修复提示词，只反馈错误信息，不重复发送 HTML
            val fixUserPrompt = buildString {
                appendLine("你之前生成的书源经真实搜索验证不通过，请根据以下错误信息修复规则，只输出修复后的完整书源 JSON 数组（不要任何解释）：")
                appendLine()
                appendLine("【真实验证报错】")
                appendLine(verify.summary)
                appendLine()
                appendLine("【本版 Legado 规则注意事项（必须遵守，否则会再次失败）】")
                appendLine("1. 本 App 的 JS 规则中【没有】bookId 变量（Book 无 bookId 字段），在规则 JS 中直接写 bookId（包括 {{bookId}}）都会报 ReferenceError: bookId 未定义，请勿使用。同样也没有 chapterId 变量，禁止使用 {{chapterId}}。")
                appendLine("   正确做法：ruleSearch.bookUrl 必须返回含 ID 的完整详情 URL（如 https://host/detail/123.html），详情(ruleBookInfo)/目录(ruleToc) 直接基于该 URL 解析；若 ID 只存在于搜索结果 JSON/字段中，用 JSONPath/正则直接在 bookUrl 规则里拼出完整 URL。")
                appendLine("2. searchUrl 必须包含 {{key}} 占位符，否则无法搜索。")
                appendLine("3. 若站点是 JSON API / SPA（返回 JSON 而非 HTML），所有规则一律用 JSONPath：bookList=$.xxx、字段=$.xxx，不要用 HTML 选择器。")
                appendLine("4. 核心规则字段（ruleSearch.bookList/name/bookUrl、ruleBookInfo.name/tocUrl、ruleToc.chapterList/chapterName/chapterUrl、ruleContent.content）必须有值且非空。")
                appendLine("5. 若解析出的 URL（详情/目录/正文）中出现形如 book_id= 的空参数，说明参数取值为空，必须改用 {{book.bookUrl}} 提取 ID 或用 JSONPath/正则补全，禁止输出带空参数的 URL。")
                appendLine("6. 若报错中附带了某接口的「真实返回」片段，必须依据该真实结构重写规则：返回 JSON 用 JSONPath（注意完整层级，勿漏中间层），返回 HTML 用 CSS 选择器。")
                appendLine("7. 若目录返回 JSON 且章节对象无完整 URL、只有内容 ID 字段（如 C/Cid/ChapterId/ContentId 等），chapterUrl 必须用 @js: 规则拼出完整正文 URL。示例：目录返回 {\"Data\":{\"Chapters\":[{\"N\":\"第一章\",\"C\":123}]}} 时：")
                appendLine("   chapterList = $.Data.Chapters")
                appendLine("   chapterName = $.Data.Chapters.*.N")
                appendLine("   chapterUrl  = @js:'https://host/content.php?book_id='+book.bookUrl.match(/book_id=(\\d+)/)[1]+'&chapter_id={{$.C}}'")
                appendLine("8. loginCheckJs 是纯 JS 字段，直接写 JS 代码、不要加 @js:/<js> 前缀（否则每次搜索/详情被 evalJS 执行时会报“在语句前面缺少 ;”）；用 java.ajax 请求登录态接口并按返回体判断，可写成 (()=>{try{return /\"user\"/.test(java.ajax('https://host/auth.php?action=me'));}catch(e){return false;}})()，并尽量在末尾返回 result 透传响应。")
                appendLine()
                appendLine("【上一次生成的书源 JSON】")
                appendLine(current)
            }
            messages.add(mapOf("role" to "assistant", "content" to current))
            messages.add(mapOf("role" to "user", "content" to fixUserPrompt))
            val fixed = runCatching {
                generateWithMessages(baseUrl, apiKey, model, messages)
            }.getOrElse { e ->
                return AutoFixResult(ok = false, rounds = round, json = current, log = log.appendLine("AI 修复调用失败: ${e.message}").toString())
            }
            current = fixed
        }
        log.appendLine(stopReason)
        return AutoFixResult(ok = false, rounds = round, json = current, log = log.toString())
    }

    /**
     * 从验证失败摘要中提取「失败阶段指纹」，用于判断连续轮次是否仍在收敛。
     * 命中同一阶段（搜索/详情/目录/正文/发现）即视为同一类问题；
     * 连续两轮指纹相同说明 LLM 没修到点子上，动态提前停止，避免空转到固定上限。
     */
    private fun failureStage(summary: String): String {
        return when {
            summary.contains("搜索规则执行异常") -> "search_exc"
            summary.contains("搜索返回空列表") -> "search_empty"
            summary.contains("搜索") && summary.contains("bookList") -> "search"
            summary.contains("详情解析失败") || summary.contains("详情") -> "bookinfo"
            summary.contains("目录地址为空") -> "toc_empty_url"
            summary.contains("目录地址存在空参数") -> "toc_bad_url"
            summary.contains("目录解析失败") || summary.contains("目录解析为空") -> "toc"
            summary.contains("正文解析失败") || summary.contains("正文解析为空") -> "content"
            summary.contains("发现页解析失败") || summary.contains("发现页解析为空") ||
                summary.contains("缺少发现页规则") -> "explore"
            summary.contains("无法解析为书源 JSON") -> "json"
            summary.contains("验证失败") -> "verify"
            else -> "other"
        }
    }

    /**
     * 用 App 真实搜索引擎对书源做全链路验证：搜索 -> 详情 -> 目录 -> 正文 -> 发现页。
     * 任何一环失败都返回失败与具体报错，供 LLM 修复。
     * @param exploreLinks 首页探测到的分类/榜单/推荐导航链接（名称, URL），用于判断站点是否有发现页
     */
    private suspend fun verifyByRealSearch(
        jsonText: String,
        keyword: String,
        exploreLinks: List<Pair<String, String>> = emptyList()
    ): VerifyResult {
        if (keyword.isBlank()) {
            return VerifyResult(succeeded = false, summary = "未提供搜索关键词，无法用真实搜索验证")
        }
        val fixed = AiSourceValidate.parseSource(jsonText) ?: return VerifyResult(false, "生成结果无法解析为书源 JSON")
        // 静态预检：本版 Legado 没有 bookId 变量，直接引用会报 ReferenceError，尽早拦截以节省修复轮次
        val unsupportedBookId = AiSourceValidate.findUnsupportedBookId(fixed)
        if (unsupportedBookId.isNotEmpty()) {
            val detail = unsupportedBookId.joinToString("；") { (where, rule) ->
                "$where = ${rule.take(60)}"
            }
            return VerifyResult(
                false,
                "规则中引用了本版 Legado 不存在的 bookId/chapterId 变量（会报 ReferenceError: bookId 未定义）：$detail。请用 ruleSearch.bookUrl 返回含 ID 的完整详情 URL，再用 JSONPath/正则拼出详情/目录/正文 URL，禁止使用 bookId/chapterId。"
            )
        }
        return runCatching {
            val bookSource = GSON.fromJson(fixed, BookSource::class.java)
            // 验证核心解析链（搜索/详情/目录/正文/发现）期间临时关闭登录相关 JS：
            // loginCheckJs 会在每次搜索/详情时被 evalJS 直接执行，若书写不当（误加 @js: 前缀、
            // 或返回布尔值/引用了不存在的 http 对象）会抛 Rhino/强转异常，干扰对解析规则的验证。
            // 登录态属于"能否读到内容"的辅助能力，这里放行，避免其成为自动修复的收敛瓶颈。
            bookSource.loginCheckJs = null
            bookSource.loginUi = null
            if (bookSource.searchUrl.isNullOrBlank()) {
                return@runCatching VerifyResult(false, "书源缺少 searchUrl，无法搜索")
            }
            val sb = StringBuilder()

            // ① 搜索
            sb.appendLine("① 搜索")
            val list = try {
                WebBook.searchBookAwait(bookSource, keyword, 1)
            } catch (e: Exception) {
                // 列表规则本身抛异常（最常见：bookList 顶层用 @js:JSON.parse(this) 报 Unexpected token）：
                // 附上真实返回与明确指引，避免 LLM 反复生成同类错误写法
                val searchUrl = (bookSource.searchUrl ?: "")
                    .replace("{{key}}", keyword)
                    .replace("{{page}}", "1")
                val sample = fetchStepSample(searchUrl)
                return@runCatching VerifyResult(
                    false,
                    sb.append("搜索规则执行异常：${e.message ?: e.javaClass.simpleName}")
                        .append("\n【重要】若响应为 JSON 却在 ruleSearch.bookList 顶层用了 @js:JSON.parse(this)，会报 Unexpected token 错误：本版 Legado 在列表规则的 @js: 中 this 是 JS 作用域对象而非响应文本，JSON.parse(this) 必然失败。返回 JSON 时请直接用 JSONPath 写 bookList（如 $.data.list、$.Data.CardList[*].Body[*].ItemData），字段用 $.field；返回 HTML 才用 CSS 选择器。")
                        .append(if (sample != null) "\n$sample" else "\n（搜索接口「$searchUrl」响应抓取失败，请检查 searchUrl 是否正确）")
                        .toString()
                )
            }
            if (list.isEmpty()) {
                val searchUrl = (bookSource.searchUrl ?: "")
                    .replace("{{key}}", keyword)
                    .replace(Regex("""\{\{[^}]*\}\}"""), "")
                val searchSample = fetchStepSample(searchUrl)
                return@runCatching VerifyResult(
                    false,
                    sb.append("搜索返回空列表，ruleSearch.bookList 或 name 规则匹配不上（响应为 JSON 时 bookList 请用 JSONPath 如 $.data.list；返回 HTML 用 CSS 选择器；禁止在 bookList 顶层写 @js:JSON.parse(this)）")
                        .append(if (searchSample != null) "\n$searchSample" else "\n（搜索接口「$searchUrl」响应抓取失败，请检查 searchUrl 是否正确）")
                        .toString()
                )
            }
            val first = list.first()
            sb.appendLine("✓ 搜索成功，返回 ${list.size} 条，首条书名「${first.name}」")
            val book = first.toBook()

            // ② 详情页
            sb.appendLine("② 详情页")
            val infoBook = runCatching {
                WebBook.getBookInfoAwait(bookSource, book, canReName = false)
            }.getOrElse {
                val detailSample = fetchStepSample(book.bookUrl)
                return@runCatching VerifyResult(
                    false,
                    sb.append("详情解析失败：${it.message ?: it.javaClass.simpleName}")
                        .append(if (detailSample != null) "\n$detailSample" else "\n（详情接口「${book.bookUrl}」响应抓取失败，请检查 ruleSearch.bookUrl 是否正确）")
                        .append("\n请根据详情接口的真实返回结构重写 ruleBookInfo 相关规则：JSON 用 JSONPath（注意完整层级），HTML 用 CSS 选择器。")
                        .toString()
                )
            }
            sb.appendLine("✓ 详情解析成功，书名「${infoBook.name}」，目录地址「${infoBook.tocUrl}」")

            // ③ 目录
            sb.appendLine("③ 目录")
            if (infoBook.tocUrl.isNullOrBlank()) {
                return@runCatching VerifyResult(
                    false,
                    sb.append("目录地址为空，ruleBookInfo.tocUrl 可能未配置或解析为空，请补全目录 URL").toString()
                )
            }
            // 目录地址带空参数（如 book_id= 结尾）说明 ID 未拼入，提前拦截给出明确指引
            if (Regex("""[?&][^=]+=(?:&|$)""").containsMatchIn(infoBook.tocUrl)) {
                return@runCatching VerifyResult(
                    false,
                    sb.append("目录地址存在空参数「${infoBook.tocUrl}」，说明书籍 ID 未正确拼入 URL。请用 ruleSearch.bookUrl 返回含 ID 的完整详情 URL，再从该 URL 中提取 ID 拼出目录/正文 URL，禁止输出带空参数的 URL。").toString()
                )
            }
            val chapters = runCatching {
                WebBook.getChapterListAwait(bookSource, infoBook, runPerJs = true).getOrThrow()
            }.getOrElse {
                val tocSample = fetchStepSample(infoBook.tocUrl)
                return@runCatching VerifyResult(
                    false,
                    sb.append("目录解析失败：${it.message ?: it.javaClass.simpleName}")
                        .append(if (tocSample != null) "\n$tocSample" else "\n（目录接口「${infoBook.tocUrl}」响应抓取失败，请检查目录 URL 是否正确）")
                        .toString()
                )
            }
            if (chapters.isEmpty()) {
                val tocSample = fetchStepSample(infoBook.tocUrl)
                return@runCatching VerifyResult(
                    false,
                    sb.append("目录解析为空，ruleToc.chapterList/chapterName/chapterUrl 匹配不上")
                        .append(if (tocSample != null) "\n$tocSample" else "\n（目录接口「${infoBook.tocUrl}」响应抓取失败，请检查目录 URL 是否正确）")
                        .append("\n请根据目录接口的真实返回结构重写 ruleToc：返回 JSON 用 JSONPath（注意完整层级，如 $.Data.Chapters，勿漏中间层），返回 HTML 用 CSS 选择器。")
                        .append("\n若章节对象无完整 URL、只有内容 ID 字段（如 C/Cid/ChapterId/ContentId 等），chapterUrl 必须用 @js: 规则基于 book.bookUrl 中的 book_id 与章节内容 ID（如 {{$.C}}）拼出完整正文 URL。")
                        .toString()
                )
            }
            val chapter = chapters.firstOrNull { !it.isVolume } ?: chapters.first()
            sb.appendLine("✓ 目录解析成功，共 ${chapters.size} 章，首章「${chapter.title}」")

            // ④ 正文
            sb.appendLine("④ 正文")
            val content = runCatching {
                WebBook.getContentAwait(bookSource, infoBook, chapter, needSave = false)
            }.getOrElse {
                val contentSample = fetchStepSample(chapter.url)
                return@runCatching VerifyResult(
                    false,
                    sb.append("正文解析失败：${it.message ?: it.javaClass.simpleName}")
                        .append(if (contentSample != null) "\n$contentSample" else "\n（正文接口「${chapter.url}」响应抓取失败，请检查 ruleToc.chapterUrl 是否正确）")
                        .append("\n请根据正文接口的真实返回结构重写 ruleContent.content：JSON 用 JSONPath（如 $.Content），HTML 用 CSS 选择器。")
                        .toString()
                )
            }
            if (content.isBlank()) {
                val contentSample = fetchStepSample(chapter.url)
                return@runCatching VerifyResult(
                    false,
                    sb.append("正文解析为空，ruleContent.content 可能匹配不上")
                        .append(if (contentSample != null) "\n$contentSample" else "\n（正文接口「${chapter.url}」响应抓取失败，请检查 ruleToc.chapterUrl 是否正确）")
                        .append("\n请根据正文接口的真实返回结构重写 ruleContent.content：JSON 用 JSONPath（如 $.Content），HTML 用 CSS 选择器。")
                        .toString()
                )
            }
            sb.appendLine("✓ 正文解析成功，${content.length} 字（首章「${chapter.title}」）")

            // ⑤ 发现页
            sb.appendLine("⑤ 发现页")
            val exploreKinds = parseExploreKinds(bookSource.exploreUrl)
            if (exploreKinds.isEmpty()) {
                if (exploreLinks.isNotEmpty()) {
                    return@runCatching VerifyResult(
                        false,
                        sb.append("书源缺少发现页规则（exploreUrl/ruleExplore 为空），但首页已探测到分类/榜单/推荐导航：")
                            .append(exploreLinks.joinToString("；") { "${it.first}::${it.second}" })
                            .append("\n请补全 exploreUrl（分类名::URL 每行一个，如 热门::https://host/top/）与 ruleExplore（bookList/name/bookUrl 必填），并按分类页真实结构编写 JSONPath/CSS 选择器。")
                            .toString()
                    )
                }
                sb.appendLine("✓ 站点未探测到分类/榜单导航，跳过发现页")
            } else {
                val firstKind = exploreKinds.first()
                val firstUrl = firstKind.second
                if (firstUrl.isNullOrBlank() || firstUrl == firstKind.first) {
                    return@runCatching VerifyResult(
                        false,
                        sb.append("exploreUrl 存在但无法解析出分类 URL（如「分类名::」缺少地址），请补全完整分类 URL").toString()
                    )
                }
                val exploreBooks = runCatching {
                    WebBook.exploreBookAwait(bookSource, firstUrl, 1)
                }.getOrElse {
                    val exploreSample = fetchStepSample(firstUrl)
                    return@runCatching VerifyResult(
                        false,
                        sb.append("发现页解析失败：${it.message ?: it.javaClass.simpleName}")
                            .append(if (exploreSample != null) "\n$exploreSample" else "\n（发现接口「$firstUrl」响应抓取失败，请检查 exploreUrl 是否正确）")
                            .append("\n请根据发现页真实返回结构重写 ruleExplore：JSON 用 JSONPath（注意完整层级），HTML 用 CSS 选择器。")
                            .toString()
                    )
                }
                if (exploreBooks.isEmpty()) {
                    val exploreSample = fetchStepSample(firstUrl)
                    return@runCatching VerifyResult(
                        false,
                        sb.append("发现页解析为空，ruleExplore.bookList/name/bookUrl 匹配不上")
                            .append(if (exploreSample != null) "\n$exploreSample" else "\n（发现接口「$firstUrl」响应抓取失败，请检查 exploreUrl 是否正确）")
                            .append("\n请根据发现页真实返回结构重写 ruleExplore：JSON 用 JSONPath（注意完整层级），HTML 用 CSS 选择器。")
                            .toString()
                    )
                }
                sb.appendLine("✓ 发现页解析成功，分类「${firstKind.first}」返回 ${exploreBooks.size} 条，首条书名「${exploreBooks.first().name}」")
            }

            VerifyResult(true, sb.toString(), AiSourceValidate.toSourceJson(bookSource, fixed))
        }.getOrElse { e ->
            VerifyResult(false, "验证失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 解析 exploreUrl 为分类列表（分类名, URL）。
     * 与 BookSource.exploreKinds() 的分割规则一致（(&&|\n) 分隔，每项 名称::地址）。
     * @js:/<js> 动态分类无法离线拆分，整体视为一条交给 WebBook 处理。
     */
    private fun parseExploreKinds(exploreUrl: String?): List<Pair<String, String>> {
        if (exploreUrl.isNullOrBlank()) return emptyList()
        if (exploreUrl.startsWith("@js:", true) || exploreUrl.startsWith("<js>", true)) {
            return listOf(exploreUrl to exploreUrl)
        }
        return exploreUrl.split(Regex("(&&|\n)+")).mapNotNull { kindStr ->
            val kindCfg = kindStr.split("::")
            val name = kindCfg.getOrNull(0)?.trim() ?: return@mapNotNull null
            val url = kindCfg.getOrNull(1)?.trim()
            if (name.isBlank() && url.isNullOrBlank()) null else name to (url ?: "")
        }
    }

    data class AutoFixResult(
        val ok: Boolean,
        val rounds: Int,
        val json: String,
        val log: String
    )

    private data class VerifyResult(
        val succeeded: Boolean,
        val summary: String,
        val fixedJson: String? = null
    )

    companion object {
        /** AI API 调用专用 HTTP 客户端，使用 5 分钟超时避免大模型响应慢而失败 */
        private val aiHttpClient: OkHttpClient by lazy {
            okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)   // 5 分钟，大模型逐字生成可能较慢
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)   // 5 分钟总超时
                .build()
        }

        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_TEMPERATURE = "ai_temperature"
        private const val KEY_PROMPT_HTML_LIMIT = "ai_prompt_html_limit"
        private const val KEY_MAX_ROUNDS = "ai_max_fix_rounds"
        private const val KEY_CUSTOM_PROMPT = "ai_custom_prompt"

        /** API Key 加密后存储的前缀标记 */
        private const val ENC_PREFIX = "aes:"

        /** API Key 加密密钥（MD5 前 16 字节），与备份 AES 策略一致 */
        private val cryptoKey: ByteArray by lazy {
            MD5Utils.md5Encode("legado.ai-source.api-key").encodeToByteArray(0, 16)
        }

        private const val DEFAULT_TEMPERATURE = 0.3f
        private const val DEFAULT_HTML_LIMIT = 40_000
        private const val DEFAULT_MAX_ROUNDS = 5

        /** 剥离 markdown 代码块，提取 JSON 片段 */
        fun stripCodeFence(text: String): String {
            val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
            if (fence != null) return fence.groupValues[1].trim()
            val start = text.indexOfFirst { it == '[' || it == '{' }
            val end = maxOf(text.lastIndexOf('}'), text.lastIndexOf(']'))
            if (start > -1 && end > start) return text.substring(start, end + 1)
            return text.trim()
        }

        /**
         * AI 生成书源系统提示词
         * 提炼自 .claude/skills/legado-book-source-tamer/（DandanLLab/legadoSkill，MIT）
         */
        val SYSTEM_PROMPT: String = """
你是"Legado书源驯兽师"，精通 Legado（阅读）App 书源 JSON 开发的专家。你的任务是分析用户提供的网站 HTML，生成符合 Legado 规范的完整书源 JSON。

【书源 JSON 字段结构】
{
  "bookSourceName": "书源名称（必填）",
  "bookSourceUrl": "网站首页地址（必填，http/https）",
  "bookSourceGroup": "分组名（可选）",
  "bookSourceType": 0,
  "bookSourceComment": "说明（可选）",
  "loginUrl": "登录地址（可选），站点有登录页则填其 URL，无则空字符串",
  "loginUi": "登录界面规则（可选，无则空字符串）",
  "loginCheckJs": "登录检测 JS（可选），返回 true 表示已登录，无则空字符串",
  "header": "请求头（可选），按行写 Key: Value，如 User-Agent: Mozilla/5.0；无反爬要求则空字符串",
  "jsLib": "公共 JS 库（可选），多段用换行分隔，无则空字符串",
  "enabledCookieJar": true,
  "coverDecodeJs": "封面解密 JS（可选），无则空字符串",
  "bookUrlPattern": "详情页 URL 正则（可选），用于从详情 URL 提取关键参数，无则空字符串",
  "searchUrl": "搜索地址（必填），搜索关键字用 {{key}} 占位，如 /search?q={{key}}；POST 请求写成 /search,{"method":"POST","body":"keyword={{key}}","charset":"gbk"}",
  "ruleSearch": {
    "bookList": "书籍列表选择器（必填）",
    "name": "书名规则（必填）",
    "author": "作者规则",
    "coverUrl": "封面规则",
    "intro": "简介规则",
    "kind": "分类规则",
    "wordCount": "字数规则",
    "lastChapter": "最新章节规则",
    "bookUrl": "详情页 URL 规则（必填）",
    "checkKeyWord": "校验关键字规则（可选）：某些站点需要先用固定关键字探测数据格式，无则空字符串"
  },
  "ruleBookInfo": {
    "init": "详情页初始化 JS（可选）：详情数据需 JS 处理时用，无则空字符串",
    "name": "详情页书名",
    "author": "详情页作者",
    "coverUrl": "详情页封面",
    "intro": "详情页简介",
    "kind": "分类",
    "lastChapter": "最新章节",
    "wordCount": "详情页字数",
    "canReName": true,
    "tocUrl": "目录页 URL（与详情页不同时填写）",
    "downloadUrls": "整本下载地址规则（可选），如 https://host/download.php?id={{$.id}}，无则空字符串"
  },
  "ruleToc": {
    "chapterList": "章节列表选择器（必填）",
    "chapterName": "章节名规则（必填）",
    "chapterUrl": "章节 URL 规则（必填）",
    "isVip": "VIP 标记规则（返回 1 表示 VIP，0 表示免费，如 JSONPath $.vip）",
    "isPay": "付费标记规则（同上，无则空字符串）",
    "updateTime": "章节更新时间规则（目录中每章常带有时间戳/日期，务必解析为该字段，不要漏掉）：返回 JSON 时用 JSONPath 指向日期字段（如 $.UpdateStatus），返回 HTML 时用 CSS/JSoup 指向日期节点；若为时间戳需用 @js: 转成日期并格式化为 yyyy-MM-dd HH:mm，如 @js:new Date({{$.updateTime}}*1000).toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).replace(/\\//g,'-')；若该章确实无时间字段才填空字符串",
    "nextTocUrl": "下一页目录 URL",
    "preUpdateJs": "目录前置 JS（可选）：目录页为 JS 渲染或需要先取 token/登录态时用，无则空字符串",
    "formatJs": "章节名格式化 JS（可选）：如 章节标题需要去掉多余前缀，无则空字符串",
    "isVolume": "是否为卷规则（可选，如 chapterList 命中卷节点返回 1，普通章节返回 0；无分卷则空字符串）"
  },
  "ruleContent": {
    "content": "正文规则（必填）",
    "nextContentUrl": "下一章 URL",
    "title": "正文标题规则（可选，无则空字符串）",
    "webJs": "需 JS 渲染时注入的脚本",
    "sourceRegex": "正文源码正则（可选）：正文源码里有 base64 或加密内容时用，无则空字符串",
    "subContent": "次级正文规则（可选）：正文含分页或特殊结构时用，无则空字符串",
    "replaceRegex": "正文净化正则，如 ##<script[\\s\\S]*?</script>|请收藏.*##",
    "imageDecode": "图片解密 JS（可选），无则空字符串",
    "payAction": "付费章节处理规则（可选），无则空字符串",
    "callBackJs": "正文回调 JS（可选），无则空字符串"
  },
  "exploreUrl": "发现地址（可选，但站点有分类/榜单/推荐导航时必须填写），多分类用 分类名::URL 换行分隔，如 热门::https://host/top/\n玄幻::https://host/sort/1/###...；分页参数写 page=1（App 会自动翻页）；无发现页则空字符串",
  "ruleExplore": {
    "bookList": "发现列表选择器（必填，若 exploreUrl 非空）",
    "name": "发现书名（必填，若 exploreUrl 非空）",
    "author": "发现作者",
    "coverUrl": "发现封面",
    "intro": "发现简介",
    "kind": "分类",
    "bookUrl": "详情页 URL 规则（必填，若 exploreUrl 非空）",
    "wordCount": "字数"
  },
  "exploreScreen": "发现筛选规则（可选，无则空字符串）",
  "ruleReview": "段评规则（可选，无则空字符串）",
  "homepageModules": "首页模块（可选，无则空字符串）",
  "eventListener": "",
  "customButton": ""
}

【字段命名（重要，必须使用本版 Legado 命名）】
- 详情规则用 ruleBookInfo（不是 ruleDetail）
- 目录规则用 ruleToc（不是 ruleCatalog），章节名用 chapterName（不是 name）
- 搜索简介用 intro（不是 detail）
- 目录 VIP/付费标记用 isVip/isPay（不是 vipFlag/payFlag）
- 目录前置/格式化用 preUpdateJs/formatJs（不是 beforeUpdateJs/formatChapterName）

【规则语法（严格按本版 Legado 实际解析语义）】
- 规则默认走 JSoup 解析器（Default 模式）。列表/字段规则统一写法：class.xxx@tag.li@text
  - 选择步骤：class.X 按 class 取、tag.X 按标签取、id.X 按 id 取、text.X 按包含文本取、children 取子元素
  - 索引筛选：tag.li.0 取第 1 个、tag.li.-1 取最后一个、tag.li.1:3 取第 2~4 个、tag.li[-1:0] 反序、tag.li!0 排除第 1 个
  - 多个选择步骤用 @ 串联，例如 class.booklist@tag.li@tag.a@href；最后一个 @ 后面必须是提取类型
- 提取类型（放在最后一个 @ 之后）：text 取文本、textNodes 取文本节点、ownText 取自身文本（不含子元素）、html 取 HTML、all 取整个 outerHTML；其余任意属性名如 href/src/id 表示取该属性值
- 纯 CSS 选择器加 @CSS: 前缀：@CSS:.detail p:nth-child(2)@text
- 多个候选规则合并：&& 顺序执行并合并所有结果；|| 取第一个非空结果即止；%% 交错合并
- XPath：以 / 开头自动识别，如 //div[@id='content']、//h3/a/text()、//img/@src
- JSONPath（返回 JSON 的接口/内嵌 JSON 数据）：bookList=$.data.records、字段 $.name、$.id，可用 {{$.id}} 拼接 URL；目录/正文接口若 URL 模板缺参，用 {{book.bookUrl}} / {{book.tocUrl}} 等已有 book 对象属性拼接，或在 bookUrl 规则中用 JSONPath/正则拼出完整 URL
- 内联 JS：{{表达式}} 嵌入 JS 片段；@get:{变量名} 读全局变量；@put:{变量名} 存全局变量
- 【重要·JSON 列表规则】响应为 JSON 时，bookList/chapterList 等列表规则与字段规则一律用 JSONPath（如 $.data.list、$.Data.CardList[*].Body[*].ItemData、$.name），禁止写 @js:JSON.parse(this)：本版 Legado 在列表规则的 @js: 里 this 是 JS 作用域对象（"[object global]"）而非响应文本，JSON.parse(this) 必报 Unexpected token。需要基于 JSON 跑复杂逻辑时，请先确保该字段值来自 JSON 元素而非对整段响应用 JSON.parse(this)。
- 【重要】本 App 的 JS 规则中没有 bookId/chapterId 变量（Book 无 bookId 字段），禁止使用 {{bookId}}、{{chapterId}} 或 JS 中的 bookId，否则报 ReferenceError。ID 只能从搜索结果 JSON/字段里取（JSONPath {{$.xxx}} 拼进 URL），或直接用 {{book.bookUrl}} 提取；可用正则 ##...## 从 bookUrl 中抠出 ID。
- 若目录返回 JSON 且章节对象无完整 URL、只有内容 ID 字段（如 C/Cid/ChapterId/ContentId 等），chapterUrl 必须用 @js: 拼出完整正文 URL，例如 @js:'https://host/content.php?book_id='+book.bookUrl.match(/book_id=(\d+)/)[1]+'&chapter_id={{$.C}}'（{{$.C}} 表示取当前章节元素的 C 字段值）。
- 正则替换：规则后接 ##正则## 且必须成对，如 ".title@text##作者：##"；正则里 \d、\s 等须写双反斜杠 \\d、\\s
- 【登录】若用户提示词标明站点可登录并给出登录入口（loginUrl/登录接口），则书源 JSON 里 loginUrl 必须填写，loginUrl 填可当网页登录页使用的地址（网页登录页，或带 ?auth=login/?action=login 会自动弹出登录框的地址），禁止把纯 API 接口（auth.php?action=captcha / action=me 等返回 JSON 或图片的地址）填进 loginUrl；loginUi 用于表达无验证码的登录表单（账号/密码字段按接口字段提交），若站点有图片验证码/复杂表单导致 loginUi 无法表达，则 loginUi 留空字符串，让 App 用内置 WebView 打开 loginUrl 手动登录，并提醒用户：验证码为一次性且站点对高频操作有限流，提交前先看清验证码图片、答错会刷新换题、避免反复提交触发“验证码请求无效”；有登录态检测接口（如 auth.php?action=me）时才写 loginCheckJs。注意：loginCheckJs 是纯 JS 字段，直接写 JS 代码、不要加 @js: 或 <js> 前缀（否则每次搜索/详情被 evalJS 执行时会报“在语句前面缺少 ;”），用 java.ajax 请求检测接口、返回体含 user/登录字段即视为已登录返回 true，并尽量在末尾返回 result 透传响应；站点无登录态检测接口则 loginCheckJs 留空；无法从提示词/HTML 确认的一律留空字符串，禁止编造。
- 整页 JS 渲染：若页面数据完全由 JS 动态生成、HTML 里没有书籍数据，则在对应 webJs 字段写提取脚本，或用 preUpdateJs/formatJs 处理后端数据

【输出要求】
1. 严格输出 JSON，最外层必须是数组 [...]，即使只有一个书源
2. 所有规则必须基于提供的 HTML 真实分析，禁止编造不存在的选择器
3. 只输出 JSON 本身，不要添加解释文字或 markdown 代码块标记
4. 无法推断的字段填空字符串 ""
5. 若 HTML 是 JSON 数据，使用 JSONPath 语法
6. 必须输出完整书源：搜索、详情(ruleBookInfo)、目录(ruleToc)、正文(ruleContent) 为必填核心；发现(exploreUrl/ruleExplore) 必须分析目标站点是否有分类/榜单/推荐导航——用户提示词中会列出从首页导航中**自动探测**到的分类链接（如「玄幻::https://...」「热门::https://...」）以及首个分类页的真实 HTML，你必须基于这些链接编写 exploreUrl（"分类名::URL" 每行一个，如 "玄幻::https://host/sort/1/\n都市::https://host/sort/2/"）与 ruleExplore（bookList/name/bookUrl 必填），如果探测到的链接确实都是分类/榜单，则必须生成 exploreUrl/ruleExplore，不要留空；若用户提示词明确说「未探测到分类导航」才填空字符串；登录(loginUrl)、下载(ruleBookInfo.downloadUrls) 若网站支持则填写，不支持/无法推断时填空字符串 ""，但字段名必须保留在输出结构中
7. 若提供了 JSON API 接口与示例响应，一律优先使用 JSONPath 规则并补齐上述全部字段
8. 目录里的数字优先匹配章节更新时间 updateTime（若该章带时间戳/日期），不要把时间数字误当章节名或字数字段；周围同时有"字数/章节号"字段时才用 wordCount
""".trimIndent()
    }
}
