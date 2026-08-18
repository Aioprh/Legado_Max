package io.legado.app.ui.book.source.ai

import android.app.Application
import com.google.gson.JsonParser
import io.legado.app.api.controller.AiSourceController
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.putFloat
import io.legado.app.utils.putInt
import io.legado.app.utils.putString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

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

    /** API Key */
    var apiKey: String
        get() = LocalConfig.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            LocalConfig.putString(KEY_API_KEY, value)
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
        return try {
            requestCompletion(baseUrl, apiKey, model, systemPrompt, userPrompt, withParams = true)
        } catch (e: Exception) {
            val code = (e.message ?: "").substringAfter("HTTP ", "").substringBefore(":").toIntOrNull()
            if (code == 400 || code == 422) {
                // 可能是参数不受支持，去掉 temperature/max_tokens 后重试
                requestCompletion(baseUrl, apiKey, model, systemPrompt, userPrompt, withParams = false)
            } else {
                throw e
            }
        }
    }

    private suspend fun requestCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        withParams: Boolean
    ): String {
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
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
        aiHttpClient.newCall(request).execute().use { response ->
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
        while (round < maxRounds) {
            round++
            log.appendLine("[第 $round 轮] 用真实搜索验证书源...")
            val verify = verifyByRealSearch(current, keyword)
            log.appendLine(verify.summary)
            if (verify.succeeded) {
                return AutoFixResult(ok = true, rounds = round, json = verify.fixedJson ?: current, log = log.toString())
            }
            // 组装修复提示词，把真实报错反馈给 LLM
            val fixPrompt = buildString {
                appendLine("你之前生成的书源经真实搜索验证不通过，请根据以下错误信息修复规则，只输出修复后的完整书源 JSON 数组（不要任何解释）：")
                appendLine()
                appendLine("【真实验证报错】")
                appendLine(verify.summary)
                appendLine()
                appendLine("【上一次生成的书源 JSON】")
                appendLine(current)
                appendLine()
                appendLine("【原始网页/接口上下文】")
                appendLine(userPrompt)
            }
            val fixed = runCatching {
                generate(baseUrl, apiKey, model, systemPrompt, fixPrompt)
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

【规则语法（Default 语法优先）】
- 提取类型：@text 取文本、@html 取 HTML、@href 取链接、@src 取图片、@textNode、@ownText
- Default 语法：class.booklist@tag.li 或 .booklist li@tag.a；简单 CSS 选择器不要加 @css 前缀
- 复杂 CSS：@css:.detail p:nth-child(2)@text
- XPath：//div[@id='content']、//h3/a/text()、//img/@src
- JSONPath（返回 JSON 的网站）：bookList=$.data.records、字段 $.name、$.id，可用 {{$.id}} 拼接 URL；正文/目录接口若 URL 模板缺参，按 Legado 约定补 book_id 与 chapter_id（book_id 用 {{book.bookUrl}} 正则提取，chapter_id 用目录章节对象的 ID 字段）
- 元素直接取属性：@@text、@@href（取当前选中元素自身的文本/链接，用于列表项）
- 内联 JS：{{表达式}} 可嵌入 JS 片段；@get:{变量名} 读取全局变量；@put:{变量名} 存入全局变量
- 正则：规则后接 ##正则## 且必须成对，如 ".title@text##作者：##"；注意 \d、\s 等需写双反斜杠
- 整页 JS 渲染：若页面数据完全由 JS 动态生成、HTML 里没有书籍数据，则在对应 webJs 字段写提取脚本，或用 preUpdateJs/formatJs 处理后端数据
- 注意转义：正则里的 \d、\s 等需写双反斜杠

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
