package io.legado.app.ui.book.source.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON

/**
 * AI 生成书源结果校验
 *
 * 校验生成的 JSON 是否符合 Legado 书源的基本结构要求，
 * 与 web 端 SourceAiGenerate.vue 的校验逻辑保持一致。
 */
object AiSourceValidate {

    /** 校验结果：名称、是否通过、说明 */
    data class Check(val name: String, val pass: Boolean, val msg: String = "")

    /** 读取对象中字符串字段值，不存在/非字符串/空白时返回 null */
    private fun strOf(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            el.asString.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    /**
     * 校验书源 JSON 文本，返回检查项列表
     */
    fun validate(text: String): List<Check> {
        val root = runCatching { JsonParser.parseString(text) }.getOrNull()
        if (root == null || !root.isJsonArray && !root.isJsonObject) {
            return listOf(Check("JSON 格式", false, "无法解析为 JSON"))
        }
        val src = when {
            root.isJsonArray -> (root as JsonArray).firstOrNull()?.asJsonObject
            else -> root.asJsonObject
        }
        if (src == null) {
            return listOf(Check("书源结构", false, "书源对象无效"))
        }

        val list = mutableListOf<Check>()
        fun check(name: String, pass: Boolean, msg: String = "") =
            list.add(Check(name, pass, msg))

        // 基本信息
        val name = strOf(src, "bookSourceName")
        check("源名称", name != null, "bookSourceName 不能为空")
        val sourceUrl = strOf(src, "bookSourceUrl")
        check("源地址", sourceUrl != null, "bookSourceUrl 不能为空")
        if (sourceUrl != null && !sourceUrl.startsWith("http://") &&
            !sourceUrl.startsWith("https://") && !sourceUrl.startsWith("data:")
        ) {
            check("源地址格式", false, "bookSourceUrl 应以 http:// 或 https:// 开头")
        }
        val typeEl = src.get("bookSourceType")
        val typeVal = typeEl?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asJsonPrimitive?.asInt
        check(
            "源类型",
            typeVal != null && typeVal in 0..4,
            "bookSourceType 应为 0~4 的整数"
        )
        val searchUrl = strOf(src, "searchUrl")
        check("搜索地址", searchUrl != null, "searchUrl 不能为空")
        if (searchUrl != null && !searchUrl.contains("{{key}}")) {
            check("搜索关键字占位符", true, "searchUrl 未包含 {{key}}，可能无法执行关键字搜索")
        }

        // 搜索规则
        val rs = src.getAsJsonObject("ruleSearch")
        if (rs != null) {
            check("搜索列表规则", strOf(rs, "bookList") != null, "ruleSearch.bookList 不能为空")
            check("搜索书名规则", strOf(rs, "name") != null, "ruleSearch.name 不能为空")
            check("搜索详情地址规则", strOf(rs, "bookUrl") != null, "ruleSearch.bookUrl 不能为空")
        }

        // 目录规则
        val rt = src.getAsJsonObject("ruleToc")
        if (rt != null) {
            check("目录列表规则", strOf(rt, "chapterList") != null, "ruleToc.chapterList 不能为空")
            check("章节名规则", strOf(rt, "chapterName") != null, "ruleToc.chapterName 不能为空")
            check("章节地址规则", strOf(rt, "chapterUrl") != null, "ruleToc.chapterUrl 不能为空")
        }

        // 正文规则
        val rc = src.getAsJsonObject("ruleContent")
        check("正文规则", strOf(rc, "content") != null, "ruleContent.content 不能为空")

        // 发现规则联动：exploreUrl 非空时要求 ruleExplore 必备字段非空
        val exploreUrl = strOf(src, "exploreUrl")
        val re = src.getAsJsonObject("ruleExplore")
        if (exploreUrl != null && re != null) {
            check("发现列表规则", strOf(re, "bookList") != null, "exploreUrl 非空时 ruleExplore.bookList 不能为空")
            check("发现书名规则", strOf(re, "name") != null, "exploreUrl 非空时 ruleExplore.name 不能为空")
            check("发现详情地址规则", strOf(re, "bookUrl") != null, "exploreUrl 非空时 ruleExplore.bookUrl 不能为空")
        }

        // 收集所有字符串规则，做语法级检查
        val allRules = mutableListOf<String>()
        listOf(rs, src.getAsJsonObject("ruleBookInfo"), rt, rc).forEach { obj ->
            obj?.entrySet()?.forEach { (_, v) ->
                if (v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                    val s = v.asString
                    if (s.isNotBlank()) allRules.add(s)
                }
            }
        }

        // 正则成对检查（规则字符串中包含 ## 的必须成对）
        val regexRules = allRules.filter { it.contains("##") }
        val oddRules = regexRules.filter { r ->
            Regex("##").findAll(r).count() % 2 != 0
        }
        check(
            "正则成对",
            oddRules.isEmpty(),
            if (oddRules.isNotEmpty()) {
                "以下规则 ## 未成对：${oddRules.take(3).joinToString("、") { it.take(30) }}"
            } else {
                "所有 ## 正则均已成对"
            }
        )

        // 正则可编译性检查
        val invalidRegex = regexRules.mapNotNull { r ->
            val regex = r.split("##").getOrNull(1)
            if (regex.isNullOrBlank() || runCatching { Regex(regex) }.isSuccess) null else r
        }
        check(
            "正则语法",
            invalidRegex.isEmpty(),
            if (invalidRegex.isNotEmpty()) {
                "以下规则正则无法编译：${invalidRegex.take(3).joinToString("、") { it.take(30) }}"
            } else {
                "所有 ## 正则均可编译"
            }
        )

        // 花括号配对检查（{{js}} / @get:{...} / @put:{...}）
        val unbalancedBraces = allRules.filter { r ->
            r.count { it == '{' } != r.count { it == '}' }
        }
        check(
            "花括号配对",
            unbalancedBraces.isEmpty(),
            if (unbalancedBraces.isNotEmpty()) {
                "以下规则 { } 未配对：${unbalancedBraces.take(3).joinToString("、") { it.take(30) }}"
            } else {
                "所有 { } 均配对"
            }
        )

        return list
    }

    /** 从 JSON 文本中提取书源对象（供导入编辑器前结构判断） */
    fun parseSource(text: String): JsonObject? {
        val root = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return null
        val obj = when {
            root.isJsonArray -> (root as JsonArray).firstOrNull()?.asJsonObject
            root.isJsonObject -> root.asJsonObject
            else -> null
        }
        return obj?.takeIf { it.has("bookSourceUrl") }
    }

    /**
     * 静态预检：扫描书源对象所有规则字符串，找出引用了本版 Legado 中不存在的 bookId 变量
     * （Book 无 bookId 字段，直接引用会报 ReferenceError: bookId 未定义）。
     * 排除 $.bookId 等 JSONPath 属性访问（前导为 $ 或 . 时不命中）。
     * @return 违规的 (规则位置, 规则内容) 列表
     */
    fun findUnsupportedBookId(src: JsonObject): List<Pair<String, String>> {
        val hits = mutableListOf<Pair<String, String>>()
        val refPattern = Regex("""(?<![$.])\bbookId\b""")
        fun scan(sec: String, obj: JsonObject) {
            obj.entrySet().forEach { (k, v) ->
                if (v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                    val s = v.asString
                    if (refPattern.containsMatchIn(s)) hits.add("$sec.$k" to s)
                }
            }
        }
        listOf("ruleSearch", "ruleBookInfo", "ruleToc", "ruleContent", "ruleExplore").forEach { sec ->
            src.getAsJsonObject(sec)?.let { scan(sec, it) }
        }
        src.entrySet().forEach { (k, v) ->
            if (v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                val s = v.asString
                if (refPattern.containsMatchIn(s)) hits.add("top.$k" to s)
            }
        }
        return hits
    }

    /**
     * 将 BookSource 对象序列化回书源 JSON 数组文本。
     * @param original 用于保留解析出的 JsonObject 中被 BookSource 忽略的字段（如 header 等）
     */
    fun toSourceJson(source: BookSource, original: JsonObject?): String {
        val json = GSON.toJson(source)
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrElse { JsonObject() }
        // 补回 BookSource 反序列化时可能丢失的自定义字段
        original?.let { orig ->
            listOf("header", "loginUrl", "loginCheckJs", "charset").forEach { k ->
                if (!obj.has(k)) orig.get(k)?.let { obj.add(k, it) }
            }
        }
        val arr = JsonArray()
        arr.add(obj)
        return arr.toString()
    }
}
