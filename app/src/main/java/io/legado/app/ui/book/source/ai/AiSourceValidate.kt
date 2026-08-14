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

        check("源名称", strOf(src, "bookSourceName") != null, "bookSourceName 不能为空")
        check("源地址", strOf(src, "bookSourceUrl") != null, "bookSourceUrl 不能为空")
        val typeEl = src.get("bookSourceType")
        check(
            "源类型",
            typeEl != null && typeEl.isJsonPrimitive && typeEl.asJsonPrimitive.isNumber,
            "bookSourceType 应为数字(0/1/2/3/4)"
        )
        check("搜索地址", strOf(src, "searchUrl") != null, "searchUrl 不能为空")

        val rs = src.getAsJsonObject("ruleSearch")
        if (rs != null) {
            check("搜索列表规则", strOf(rs, "bookList") != null, "ruleSearch.bookList 不能为空")
            check("搜索书名规则", strOf(rs, "name") != null, "ruleSearch.name 不能为空")
            check("搜索详情地址规则", strOf(rs, "bookUrl") != null, "ruleSearch.bookUrl 不能为空")
        }

        val rt = src.getAsJsonObject("ruleToc")
        if (rt != null) {
            check("目录列表规则", strOf(rt, "chapterList") != null, "ruleToc.chapterList 不能为空")
            check("章节名规则", strOf(rt, "chapterName") != null, "ruleToc.chapterName 不能为空")
            check("章节地址规则", strOf(rt, "chapterUrl") != null, "ruleToc.chapterUrl 不能为空")
        }

        val rc = src.getAsJsonObject("ruleContent")
        check("正文规则", strOf(rc, "content") != null, "ruleContent.content 不能为空")

        // 正则成对检查（规则字符串中包含 ## 的必须成对）
        val rules = mutableListOf<String>()
        listOf(rs, src.getAsJsonObject("ruleBookInfo"), rt, rc).forEach { obj ->
            obj?.entrySet()?.forEach { (_, v) ->
                if (v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                    val s = v.asString
                    if (s.contains("##")) rules.add(s)
                }
            }
        }
        val oddRules = rules.filter { r ->
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
