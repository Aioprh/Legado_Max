package io.legado.app.ui.book.source.ai

import android.app.Application
import cn.hutool.crypto.symmetric.AES
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
import kotlinx.coroutines.suspendCancellableCoroutine
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

    /**
     * 抓取目标网站 HTML 并自动检测编码（供本页使用）
     * @param keyword 搜索关键词（可选），用于自动探测 JSON API 并抓取搜索/目录示例
     * @param header  自定义请求头（可选），多行 "Key: Value"，用于站点反爬
     * @param cookie  自定义 Cookie（可选）
     * @param searchUrl 用户手动指定的搜索页/搜索接口地址（可选）。自动探测不到搜索接口时，
     *   可在此直接填写（如 https://www.example.com/search 或 /api/search?q=），将作为搜索接口优先使用
     */
    fun fetchHtml(
        url: String,
        keyword: String? = null,
        header: String? = null,
        cookie: String? = null,
        searchUrl: String? = null
    ): Result<AiSourceController.HtmlContent> =
        AiSourceController.fetchHtmlContent(url, keyword, header, cookie, searchUrl)

    /**
     * 调用 LLM 生成书源 JSON
     * 成功返回剥离 markdown 代码块后的 JSON 文本
     * 部分 OpenAI 兼容服务/推理模型会拒绝 temperature/max_tokens 参数，
     * 首次失败时自动去掉这些参数重试一次。
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
            val code = (e.message ?: "").substringAfter("HTTP ", "").substringBefore(":").toIntOrNull()
            if (code == 400 || code == 422) {
                // 可能是参数不受支持，去掉 temperature/max_tokens 后重试
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
            bodyMap["max_tokens"] = MAX_TOKENS
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
            val content = runCatching {
                val root = JsonParser.parseString(text).asJsonObject
                val message = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                // 兼容 DeepSeek-R1 等推理模型：正文可能在 reasoning_content
                message?.get("content")?.asString ?: message?.get("reasoning_content")?.asString ?: ""
            }.getOrDefault("")
            if (content.isBlank()) throw RuntimeException("模型未返回内容")
            return stripCodeFence(content)
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
        maxRounds: Int = maxFixRounds
    ): AutoFixResult {
        var round = 0
        var current = sourceJson
        val log = StringBuilder()
        // 使用对话历史代替重复发送完整 HTML 上下文
        val messages = mutableListOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        while (round < maxRounds) {
            round++
            log.appendLine("[第 $round 轮] 用真实搜索验证书源...")
            val verify = verifyByRealSearch(current, keyword)
            log.appendLine(verify.summary)
            if (verify.succeeded) {
                return AutoFixResult(ok = true, rounds = round, json = verify.fixedJson ?: current, log = log.toString())
            }
            // 组装修复提示词，只反馈错误信息，不重复发送 HTML
            val fixUserPrompt = buildString {
                appendLine("你之前生成的书源经真实搜索验证不通过，请根据以下错误信息修复规则，只输出修复后的完整书源 JSON 数组（不要任何解释）：")
                appendLine()
                appendLine("【真实验证报错】")
                appendLine(verify.summary)
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
        return AutoFixResult(ok = false, rounds = round, json = current, log = log.toString())
    }

    /**
     * 用 App 真实搜索引擎对书源做全链路验证：搜索 -> 详情 -> 目录 -> 正文。
     * 任何一环失败都返回失败与具体报错，供 LLM 修复。
     */
    private suspend fun verifyByRealSearch(jsonText: String, keyword: String): VerifyResult {
        if (keyword.isBlank()) {
            return VerifyResult(succeeded = false, summary = "未提供搜索关键词，无法用真实搜索验证")
        }
        val fixed = AiSourceValidate.parseSource(jsonText) ?: return VerifyResult(false, "生成结果无法解析为书源 JSON")
        return runCatching {
            val bookSource = GSON.fromJson(fixed, BookSource::class.java)
            if (bookSource.searchUrl.isNullOrBlank()) {
                return@runCatching VerifyResult(false, "书源缺少 searchUrl，无法搜索")
            }
            val sb = StringBuilder()

            // ① 搜索
            sb.appendLine("① 搜索")
            val list = WebBook.searchBookAwait(bookSource, keyword, 1)
            if (list.isEmpty()) {
                return@runCatching VerifyResult(
                    false,
                    sb.append("搜索返回空列表，ruleSearch.bookList 或 name 规则可能匹配不上").toString()
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
                return@runCatching VerifyResult(
                    false,
                    sb.append("详情解析失败：${it.message ?: it.javaClass.simpleName}").toString()
                )
            }
            sb.appendLine("✓ 详情解析成功，书名「${infoBook.name}」，目录地址「${infoBook.tocUrl}」")

            // ③ 目录
            sb.appendLine("③ 目录")
            val chapters = runCatching {
                WebBook.getChapterListAwait(bookSource, infoBook, runPerJs = true).getOrThrow()
            }.getOrElse {
                return@runCatching VerifyResult(
                    false,
                    sb.append("目录解析失败：${it.message ?: it.javaClass.simpleName}").toString()
                )
            }
            if (chapters.isEmpty()) {
                return@runCatching VerifyResult(
                    false,
                    sb.append("目录解析为空，ruleToc.chapterList/chapterName/chapterUrl 可能匹配不上").toString()
                )
            }
            val chapter = chapters.firstOrNull { !it.isVolume } ?: chapters.first()
            sb.appendLine("✓ 目录解析成功，共 ${chapters.size} 章，首章「${chapter.title}」")

            // ④ 正文
            sb.appendLine("④ 正文")
            val content = runCatching {
                WebBook.getContentAwait(bookSource, infoBook, chapter, needSave = false)
            }.getOrElse {
                return@runCatching VerifyResult(
                    false,
                    sb.append("正文解析失败：${it.message ?: it.javaClass.simpleName}").toString()
                )
            }
            if (content.isBlank()) {
                return@runCatching VerifyResult(
                    false,
                    sb.append("正文解析为空，ruleContent.content 可能匹配不上").toString()
                )
            }
            sb.appendLine("✓ 正文解析成功，${content.length} 字（首章「${chapter.title}」）")
            VerifyResult(true, sb.toString(), AiSourceValidate.toSourceJson(bookSource, fixed))
        }.getOrElse { e ->
            VerifyResult(false, "验证失败：${e.message ?: e.javaClass.simpleName}")
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

        /** API Key 加密后存储的前缀标记 */
        private const val ENC_PREFIX = "aes:"

        /** API Key 加密密钥（MD5 前 16 字节），与备份 AES 策略一致 */
        private val cryptoKey: ByteArray by lazy {
            MD5Utils.md5Encode("legado.ai-source.api-key").encodeToByteArray(0, 16)
        }

        private const val DEFAULT_TEMPERATURE = 0.3f
        private const val DEFAULT_HTML_LIMIT = 40_000
        private const val DEFAULT_MAX_ROUNDS = 3

        /** 生成书源 JSON 的最大 token 数，避免长书源被截断 */
        private const val MAX_TOKENS = 8192

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
    "updateTime": "章节更新时间规则（无则空字符串）",
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
  "exploreUrl": "发现地址（可选），多分类用 分类名::URL 换行分隔，如 热门::https://host/top/###...；分页参数写 page=1（App 会自动翻页）；无发现页则空字符串",
  "ruleExplore": {
    "bookList": "发现列表选择器（必填，若 exploreUrl 非空）",
    "name": "发现书名",
    "author": "发现作者",
    "coverUrl": "发现封面",
    "intro": "发现简介",
    "kind": "分类",
    "bookUrl": "详情页 URL 规则",
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
- JSONPath（返回 JSON 的接口/内嵌 JSON 数据）：bookList=$.data.records、字段 $.name、$.id，可用 {{$.id}} 拼接 URL；目录/正文接口若 URL 模板缺参，按 Legado 约定补 book_id 与 chapter_id（book_id 用 {{book.bookUrl}} 正则提取，chapter_id 用目录章节对象的 ID 字段）
- 内联 JS：{{表达式}} 嵌入 JS 片段；@get:{变量名} 读全局变量；@put:{变量名} 存全局变量
- 正则替换：规则后接 ##正则## 且必须成对，如 ".title@text##作者：##"；正则里 \d、\s 等须写双反斜杠 \\d、\\s
- 整页 JS 渲染：若页面数据完全由 JS 动态生成、HTML 里没有书籍数据，则在对应 webJs 字段写提取脚本，或用 preUpdateJs/formatJs 处理后端数据

【输出要求】
1. 严格输出 JSON，最外层必须是数组 [...]，即使只有一个书源
2. 所有规则必须基于提供的 HTML 真实分析，禁止编造不存在的选择器
3. 只输出 JSON 本身，不要添加解释文字或 markdown 代码块标记
4. 无法推断的字段填空字符串 ""
5. 若 HTML 是 JSON 数据，使用 JSONPath 语法
6. 必须输出完整书源：搜索、详情(ruleBookInfo)、目录(ruleToc)、正文(ruleContent) 为必填核心；发现(exploreUrl/ruleExplore)、登录(loginUrl)、下载(ruleBookInfo.downloadUrls) 若网站支持则填写，不支持/无法推断时填空字符串 ""，但字段名必须保留在输出结构中
7. 若提供了 JSON API 接口与示例响应，一律优先使用 JSONPath 规则并补齐上述全部字段
""".trimIndent()
    }
}
