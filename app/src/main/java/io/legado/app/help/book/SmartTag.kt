package io.legado.app.help.book

import android.content.Context
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book

/**
 * Max 智能标签引擎：基于书籍已有元数据生成虚拟标签。
 * 标签不会写入 books 表，因此无需数据库迁移，也不会覆盖用户手动设置的 customTag。
 */
object SmartTag {
    data class Result(val name: String, val score: Int = 0, val reason: String? = null)
    data class RuleInfo(val name: String, val score: Int, val reason: String, val custom: Boolean = false, val id: String? = null)

    private data class Rule(val name: String, val score: Int, val reason: String, val match: (Book) -> Boolean)

    private fun hasType(book: Book, type: Int): Boolean = (book.type and type) != 0

    private val rules = listOf(
        Rule("有声", 100, "书籍类型为音频") { hasType(it, BookType.audio) },
        Rule("漫画", 100, "书籍类型为图片/漫画") { hasType(it, BookType.image) },
        Rule("视频", 100, "书籍类型为视频") { hasType(it, BookType.video) },
        Rule("本地", 90, "本地书籍") { hasType(it, BookType.local) },
        Rule("网络书", 60, "来自网络书源") { !hasType(it, BookType.local) },
        Rule("更新异常", 95, "最近一次更新失败") { hasType(it, BookType.updateError) },
        Rule("已读完", 100, "已阅读到最后一章") { it.totalChapterNum > 0 && it.durChapterIndex >= it.totalChapterNum - 1 },
        Rule("在读", 80, "存在阅读进度且尚未读完") { it.totalChapterNum > 0 && it.durChapterIndex > 0 && it.durChapterIndex < it.totalChapterNum - 1 },
        Rule("未开始", 70, "尚未产生章节阅读进度") { it.totalChapterNum > 0 && it.durChapterIndex <= 0 && it.durChapterPos <= 0 },
        Rule("超长篇", 80, "章节数达到 1000 章以上") { it.totalChapterNum >= 1000 },
        Rule("长篇", 70, "章节数达到 500 章以上") { it.totalChapterNum in 500..999 },
        Rule("中长篇", 60, "章节数达到 200 章以上") { it.totalChapterNum in 200..499 },
        Rule("短篇", 50, "章节数少于 50 章") { it.totalChapterNum in 1..49 },
        Rule("有更新", 85, "最近一次检查发现新章节") { it.lastCheckCount > 0 },
        Rule("不可更新", 75, "已关闭自动更新") { !it.canUpdate }
    )

    val ruleInfos: List<RuleInfo>
        get() = rules.map { RuleInfo(it.name, it.score, it.reason) }

    fun allRuleInfos(context: Context): List<RuleInfo> =
        ruleInfos + SmartTagConfig.customRules(context).map {
            RuleInfo(it.name, 90, "自定义规则：${it.field} ${it.operator} ${it.value}", true, it.id)
        }

    fun evaluate(book: Book, maxTags: Int = 6): List<Result> = evaluate(book, null, maxTags)

    fun evaluate(book: Book, context: Context?, maxTags: Int = 6): List<Result> {
        if (maxTags <= 0) return emptyList()
        val result = rules.asSequence()
            .filter { context == null || SmartTagConfig.isRuleVisible(context, it.name) }
            .filter { it.match(book) }
            .map { Result(it.name, it.score, it.reason) }
            .toMutableList()
        if (context != null) {
            SmartTagConfig.customRules(context).asSequence()
                .filter { it.enabled && SmartTagConfig.isRuleVisible(context, it.name) }
                .filter { matchesCustom(book, it) }
                .map { Result(it.name, 90, "自定义规则") }
                .forEach(result::add)
        }
        return result.sortedWith(compareByDescending<Result> { it.score }.thenBy { it.name })
            .distinctBy { it.name }.take(maxTags)
    }

    fun names(book: Book, maxTags: Int = 6): List<String> = evaluate(book, maxTags).map { it.name }
    fun names(book: Book, context: Context, maxTags: Int = 6): List<String> = evaluate(book, context, maxTags).map { it.name }

    private fun matchesCustom(book: Book, rule: SmartTagConfig.CustomRule): Boolean {
        val raw = when (rule.field.lowercase()) {
            "name" -> book.name
            "author" -> book.author
            "origin" -> book.origin
            "originname", "source" -> book.originName
            "kind", "category" -> book.kind.orEmpty()
            "wordcount" -> book.wordCount.orEmpty()
            "chapters", "totalchapternum" -> book.totalChapterNum.toString()
            "progress" -> if (book.totalChapterNum > 0) ((book.durChapterIndex + 1) * 100f / book.totalChapterNum).toInt().toString() else "0"
            "unread" -> book.getUnreadChapterNum().toString()
            "updates", "lastcheckcount" -> book.lastCheckCount.toString()
            "canupdate" -> book.canUpdate.toString()
            else -> return false
        }
        return when (rule.operator.lowercase()) {
            "contains" -> raw.contains(rule.value, true)
            "not_contains" -> !raw.contains(rule.value, true)
            "equals", "=" -> raw.equals(rule.value, true)
            "not_equals", "!=" -> !raw.equals(rule.value, true)
            ">", ">=", "<", "<=" -> compareNumber(raw, rule.value, rule.operator)
            "starts_with" -> raw.startsWith(rule.value, true)
            "ends_with" -> raw.endsWith(rule.value, true)
            else -> false
        }
    }

    private fun compareNumber(left: String, right: String, operator: String): Boolean {
        val a = left.toDoubleOrNull() ?: return false
        val b = right.toDoubleOrNull() ?: return false
        return when (operator) {
            ">" -> a > b
            ">=" -> a >= b
            "<" -> a < b
            "<=" -> a <= b
            else -> false
        }
    }
}

fun Book.getSmartTags(maxTags: Int = 6): List<String> = SmartTag.names(this, maxTags)
