package io.legado.app.ui.book.source.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON

/**
 * AI 生成书源结果校验。
 *
 * 除 JSON/字段级校验外，这里加入了 legadoSkill 中最有价值的规则语义预检：
 * JSONPath/JS 混用、非法 Legado 变量、危险 CSS、URL 空参数、分页/正文链路等。
 * 这些检查不会替代 App 真正执行书源；它们的作用是尽早阻止明显错误进入自动修复链。
 */
object AiSourceValidate {

    data class Check(val name: String, val pass: Boolean, val msg: String = "")

    private fun strOf(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            el.asString.takeIf { it.isNotBlank() }
        } else null
    }

    fun validate(text: String): List<Check> {
        val root = runCatching { JsonParser.parseString(text) }.getOrNull()
        if (root == null || (!root.isJsonArray && !root.isJsonObject)) {
            return listOf(Check("JSON 格式", false, "无法解析为 JSON"))
        }
        val src = when {
            root.isJsonArray -> (root as JsonArray).firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            else -> root.asJsonObject
        }
        if (src == null) return listOf(Check("书源结构", false, "书源对象无效"))

        val list = mutableListOf<Check>()
        fun check(name: String, pass: Boolean, msg: String = "") = list.add(Check(name, pass, msg))

        // 基本信息
        val name = strOf(src, "bookSourceName")
        check("源名称", name != null, "bookSourceName 不能为空")
        val sourceUrl = strOf(src, "bookSourceUrl")
        check("源地址", sourceUrl != null, "bookSourceUrl 不能为空")
        if (sourceUrl != null) {
            check(
                "源地址格式",
                sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://") || sourceUrl.startsWith("data:"),
                "bookSourceUrl 应以 http://、https:// 或 data: 开头"
            )
        }
        val typeVal = src.get("bookSourceType")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asJsonPrimitive?.asInt
        check("源类型", typeVal != null && typeVal in 0..4, "bookSourceType 应为 0~4 的整数")

        val searchUrl = strOf(src, "searchUrl")
        check("搜索地址", searchUrl != null, "searchUrl 不能为空")
        if (searchUrl != null) {
            check("搜索关键字占位符", searchUrl.contains("{{key}}"), "searchUrl 未包含 {{key}}，可能无法执行关键字搜索")
            check("搜索地址无空参数", !hasEmptyUrlParameter(searchUrl), "searchUrl 含空参数，请确认 ID/关键词占位符是否正确")
        }

        // 核心区块必须存在，不能只校验“如果存在”。
        val rs = src.getAsJsonObject("ruleSearch")
        val rb = src.getAsJsonObject("ruleBookInfo")
        val rt = src.getAsJsonObject("ruleToc")
        val rc = src.getAsJsonObject("ruleContent")
        check("搜索规则区块", rs != null, "缺少 ruleSearch")
        check("详情规则区块", rb != null, "缺少 ruleBookInfo")
        check("目录规则区块", rt != null, "缺少 ruleToc")
        check("正文规则区块", rc != null, "缺少 ruleContent")

        // 搜索规则
        check("搜索列表规则", strOf(rs, "bookList") != null, "ruleSearch.bookList 不能为空")
        check("搜索书名规则", strOf(rs, "name") != null, "ruleSearch.name 不能为空")
        check("搜索详情地址规则", strOf(rs, "bookUrl") != null, "ruleSearch.bookUrl 不能为空")

        // 详情规则
        check("详情书名规则", strOf(rb, "name") != null, "ruleBookInfo.name 不能为空")
        check("详情目录地址规则", strOf(rb, "tocUrl") != null, "ruleBookInfo.tocUrl 不能为空")

        // 目录规则
        check("目录列表规则", strOf(rt, "chapterList") != null, "ruleToc.chapterList 不能为空")
        check("章节名规则", strOf(rt, "chapterName") != null, "ruleToc.chapterName 不能为空")
        check("章节地址规则", strOf(rt, "chapterUrl") != null, "ruleToc.chapterUrl 不能为空")

        // 正文规则
        check("正文规则", strOf(rc, "content") != null, "ruleContent.content 不能为空")

        // 发现规则联动
        val exploreUrl = strOf(src, "exploreUrl")
        val re = src.getAsJsonObject("ruleExplore")
        if (exploreUrl != null) {
            check("发现规则区块", re != null, "exploreUrl 非空但缺少 ruleExplore")
            check("发现列表规则", strOf(re, "bookList") != null, "exploreUrl 非空时 ruleExplore.bookList 不能为空")
            check("发现书名规则", strOf(re, "name") != null, "exploreUrl 非空时 ruleExplore.name 不能为空")
            check("发现详情地址规则", strOf(re, "bookUrl") != null, "exploreUrl 非空时 ruleExplore.bookUrl 不能为空")
            check("发现地址无空参数", !hasEmptyUrlParameter(exploreUrl), "exploreUrl 含空参数，请检查分类/榜单 URL")
        }

        // 收集所有字符串规则。
        val allRules = mutableListOf<Pair<String, String>>()
        listOf(
            "ruleSearch" to rs,
            "ruleBookInfo" to rb,
            "ruleToc" to rt,
            "ruleContent" to rc,
            "ruleExplore" to re
        ).forEach { (section, obj) ->
            obj?.entrySet()?.forEach { (key, value) ->
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    val s = value.asString
                    if (s.isNotBlank()) allRules.add("$section.$key" to s)
                }
            }
        }
        src.entrySet().forEach { (key, value) ->
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) {
                allRules.add("top.$key" to value.asString)
            }
        }

        val ruleText = allRules.joinToString("\n") { it.second }

        // 正则 ## 成对 + 可编译
        val regexRules = allRules.filter { it.second.contains("##") }
        val oddRules = regexRules.filter { Regex("##").findAll(it.second).count() % 2 != 0 }
        check(
            "正则成对",
            oddRules.isEmpty(),
            if (oddRules.isNotEmpty()) "以下规则 ## 未成对：${oddRules.take(3).joinToString("、") { it.first }}" else "所有 ## 正则均已成对"
        )
        val invalidRegex = regexRules.mapNotNull { (where, rule) ->
            val parts = rule.split("##")
            if (parts.size < 2 || parts[1].isBlank() || runCatching { Regex(parts[1]) }.isSuccess) null else where to rule
        }
        check(
            "正则语法",
            invalidRegex.isEmpty(),
            if (invalidRegex.isNotEmpty()) "以下规则正则无法编译：${invalidRegex.take(3).joinToString("、") { it.first }}" else "所有 ## 正则均可编译"
        )

        // 花括号配对
        val unbalancedBraces = allRules.filter { (_, r) -> r.count { it == '{' } != r.count { it == '}' } }
        check(
            "花括号配对",
            unbalancedBraces.isEmpty(),
            if (unbalancedBraces.isNotEmpty()) "以下规则 { } 未配对：${unbalancedBraces.take(3).joinToString("、") { it.first }}" else "所有 { } 均配对"
        )

        // legadoSkill 高价值语义规则
        val unsupportedIds = findUnsupportedBookId(src)
        check(
            "Legado 变量",
            unsupportedIds.isEmpty(),
            if (unsupportedIds.isNotEmpty()) {
                "发现不存在的 bookId/chapterId 变量：${unsupportedIds.take(3).joinToString("、") { it.first }}；请从 book.bookUrl/chapter.url 或 JSONPath 获取 ID"
            } else "未发现裸 bookId/chapterId"
        )

        val unsafeMatch = Regex("""\.match\s*\([^)]*\)\s*\[""", RegexOption.DOT_MATCHES_ALL)
        val unsafeMatchRules = allRules.filter { unsafeMatch.containsMatchIn(it.second) }
        check(
            "match 取下标安全",
            unsafeMatchRules.isEmpty(),
            if (unsafeMatchRules.isNotEmpty()) {
                "${unsafeMatchRules.take(3).joinToString("、") { it.first }} 使用了 match()[n]；请改成 (xxx.match(/re/)||[])[n]||''"
            } else "所有规则未直接对 match() 结果取下标"
        )

        val forbiddenSelectors = listOf(":contains(", ":first-child", ":last-child")
        val selectorHits = forbiddenSelectors.filter { ruleText.contains(it, ignoreCase = true) }
        check(
            "选择器稳定性",
            selectorHits.isEmpty(),
            if (selectorHits.isNotEmpty()) "发现高风险选择器 ${selectorHits.joinToString("、")}；优先使用稳定 class/id/属性选择器" else "未发现高风险选择器"
        )

        val jsonParseThis = allRules.filter { it.second.contains("@js:JSON.parse(this)", ignoreCase = true) }
        check(
            "JSON 列表规则",
            jsonParseThis.isEmpty(),
            if (jsonParseThis.isNotEmpty()) "${jsonParseThis.take(3).joinToString("、") { it.first }} 不能用 @js:JSON.parse(this) 解析整段响应，请改为 JSONPath" else "未发现 JSON.parse(this) 高风险写法"
        )

        val emptyUrlRules = allRules.filter { hasEmptyUrlParameter(it.second) }
        check(
            "URL 参数完整",
            emptyUrlRules.isEmpty(),
            if (emptyUrlRules.isNotEmpty()) "以下规则可能产生空 URL 参数：${emptyUrlRules.take(3).joinToString("、") { it.first }}" else "未发现明显空 URL 参数"
        )

        // 目录/正文链路的静态信号：如果 chapterUrl 明显只是当前目录 URL，不应直接当正文 URL。
        val chapterUrl = strOf(rt, "chapterUrl").orEmpty()
        val tocUrl = strOf(rb, "tocUrl").orEmpty()
        if (chapterUrl.isNotBlank() && tocUrl.isNotBlank() && normalizeRule(chapterUrl) == normalizeRule(tocUrl)) {
            check("目录-正文链路", false, "ruleToc.chapterUrl 与 ruleBookInfo.tocUrl 完全相同，疑似把目录 URL 当正文 URL")
        } else {
            check("目录-正文链路", true, "chapterUrl 与 tocUrl 未发现静态重复")
        }

        // 提示哪些知识规则最相关，便于 UI 日志/后续自动修复层消费。
        val relevant = AiSourceKnowledge.relevantRepairHints(text)
        check(
            "知识规则命中",
            true,
            if (relevant.isEmpty()) "未命中额外高风险规则" else "命中：${relevant.joinToString("、") { it.id }}"
        )

        return list
    }

    private fun normalizeRule(value: String): String =
        value.trim().replace("{{book.tocUrl}}", "").replace("{{book.bookUrl}}", "")

    private fun hasEmptyUrlParameter(value: String): Boolean {
        // 只拦截明显的 query 空值，如 book_id=、id=&、?token=；不拦截 JS 模板中的普通等号。
        return Regex("[?&][A-Za-z0-9_.-]+=(?:&|$)").containsMatchIn(value)
    }

    fun parseSource(text: String): JsonObject? {
        val root = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return null
        val obj = when {
            root.isJsonArray -> (root as JsonArray).firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            root.isJsonObject -> root.asJsonObject
            else -> null
        }
        return obj?.takeIf { it.has("bookSourceUrl") }
    }

    /** 扫描当前版本 Legado 不存在的 bookId/chapterId 变量。 */
    fun findUnsupportedBookId(src: JsonObject): List<Pair<String, String>> {
        val hits = mutableListOf<Pair<String, String>>()
        val refPattern = Regex("""(?<![$.])\b(?:bookId|chapterId)\b""")
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

    /** 从 JSON 文本提取书源对象并重新序列化为标准数组格式。 */
    fun toSourceJson(source: BookSource, original: JsonObject?): String {
        val json = GSON.toJson(source)
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrElse { JsonObject() }
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
