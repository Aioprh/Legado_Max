package io.legado.app.model.blockrule

import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.SearchBook
import io.legado.app.utils.RegexCache
import java.util.UUID

/**
 * 表示一条已解析完毕的屏蔽规则，将原始 [BlockRule] 中与匹配无关的字段剥离，
 * 并将 scope 字符串预解析为列表，避免每次匹配时重复 split/trim/filter。
 *
 * 供 [BlockRuleStore] 在过滤流程中构建并缓存，所有过滤方法直接使用此对象。
 */
class CompiledBlockRule(
    val id: String,
    val pattern: String,
    val isRegex: Boolean,
    val targetScope: Int,
    val rssTargetScope: Int,
    val scopeItems: List<String>,      // 预解析的书源作用域列表，空列表=对所有书源生效
    val rssScopeItems: List<String>,   // 预解析的订阅源作用域列表，空列表=对所有订阅源生效
) {
    /** 缓存的正则表达式，仅在 isRegex 且 pattern 非空时编译 */
    private val regex: Regex? =
        if (isRegex && pattern.isNotBlank()) RegexCache.getOrCompile(pattern) else null

    /** 检查书源作用范围是否包含指定的标志 */
    fun hasScope(flag: Int): Boolean = (targetScope and flag) != 0

    /** 检查订阅源作用范围是否包含指定的标志 */
    fun hasRssScope(flag: Int): Boolean = (rssTargetScope and flag) != 0

    /**
     * 判断规则是否对指定书源生效
     * scopeItems 为空时对所有书源生效，否则检查 sourceUrl 是否包含任一 scope 项
     */
    fun matchesScope(sourceUrl: String): Boolean {
        if (scopeItems.isEmpty()) return true
        return scopeItems.any { sourceUrl.contains(it) }
    }

    /**
     * 判断规则是否对指定订阅源生效
     * rssScopeItems 为空时对所有订阅源生效，否则检查 sourceUrl 是否包含任一 scope 项
     */
    fun matchesRssScope(sourceUrl: String): Boolean {
        if (rssScopeItems.isEmpty()) return true
        return rssScopeItems.any { sourceUrl.contains(it) }
    }

    /**
     * 对单个文本执行关键词或正则匹配
     * 正则匹配使用 runCatching 防止非法正则导致崩溃
     */
    private fun matchText(text: String): Boolean {
        val r = regex ?: return text.contains(pattern)
        return runCatching { r.containsMatchIn(text) }.getOrDefault(false)
    }

    /**
     * 判断书籍是否匹配该屏蔽规则
     * 根据书源作用范围（位掩码）选择匹配字段，仅当 targetScope != 0 时生效
     * 使用短路条件判断，避免 MutableList 分配
     */
    fun matches(book: SearchBook): Boolean {
        if (targetScope == 0 || pattern.isBlank()) return false
        if (hasScope(BlockRule.SCOPE_TITLE) && matchText(book.name)) return true
        if (hasScope(BlockRule.SCOPE_AUTHOR) && matchText(book.author)) return true
        if (hasScope(BlockRule.SCOPE_KIND) && matchText(book.kind.orEmpty())) return true
        if (hasScope(BlockRule.SCOPE_INTRO) && matchText(book.intro.orEmpty())) return true
        if (hasScope(BlockRule.SCOPE_WORD_COUNT) && matchText(book.wordCount.orEmpty())) return true
        return false
    }

    /**
     * 判断RSS文章是否匹配该屏蔽规则
     * 根据订阅源作用范围（位掩码）选择匹配字段，仅当 rssTargetScope != 0 时生效
     * 使用短路条件判断，避免 MutableList 分配
     */
    fun matchesRssArticle(article: RssArticle): Boolean {
        if (rssTargetScope == 0 || pattern.isBlank()) return false
        if (hasRssScope(BlockRule.SCOPE_RSS_TITLE) && matchText(article.title)) return true
        if (hasRssScope(BlockRule.SCOPE_RSS_TIME) && matchText(article.pubDate.orEmpty())) return true
        return false
    }

    /** 关联回原始 BlockRule 的 ID，用于匹配结果报告 */
    fun toBlockRuleId(): String = id
}

/**
 * 屏蔽规则数据模型
 *
 * 用于屏蔽发现、搜索、订阅列表中匹配关键词或正则表达式的内容。
 * 书源作用范围（targetScope）和订阅源作用范围（rssTargetScope）各自独立控制，
 * 支持仅作用于书源、仅作用于订阅源，或同时作用于两者。
 *
 * 存储方式：SharedPreferences + JSON 序列化，由 [BlockRuleStore] 管理
 */
data class BlockRule(
    /** 唯一标识，使用 UUID 确保唯一性 */
    var id: String = UUID.randomUUID().toString(),
    /** 规则名称 */
    var name: String = "",
    /** 匹配模式：关键词或正则表达式 */
    var pattern: String = "",
    /** 是否启用正则匹配，false=关键词匹配 */
    var isRegex: Boolean = false,
    /** 所属分组 */
    var group: String = BlockRuleGroupStore.DEFAULT_GROUP,
    /** 书源作用范围（位掩码），0=不作用于书源 */
    var targetScope: Int = 0,
    /** 订阅源作用范围（位掩码），0=不作用于订阅源 */
    var rssTargetScope: Int = 0,
    /** 是否启用该规则 */
    var enabled: Boolean = true,
    /** 作用的书源，书源URL分号分隔，为空则对所有书源生效 */
    var scope: String? = null,
    /** 作用的订阅源，订阅源URL分号分隔，为空则对所有订阅源生效 */
    var rssScope: String? = null,
) {

    /** 检查书源作用范围是否包含指定的标志 */
    fun hasScope(flag: Int): Boolean = (targetScope and flag) != 0

    /** 检查订阅源作用范围是否包含指定的标志 */
    fun hasRssScope(flag: Int): Boolean = (rssTargetScope and flag) != 0

    /**
     * 将此规则编译为 [CompiledBlockRule]，预解析 scope 字符串为列表，避免后续重复 split/trim/filter。
     * 调用方应缓存编译结果（如 BlockRuleStore 中缓存），不要在每次匹配时调用。
     */
    fun compile(): CompiledBlockRule {
        val scopeItems = scope
            ?.takeIf { it.isNotBlank() }
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val rssScopeItems = rssScope
            ?.takeIf { it.isNotBlank() }
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return CompiledBlockRule(
            id = id,
            pattern = pattern,
            isRegex = isRegex,
            targetScope = targetScope,
            rssTargetScope = rssTargetScope,
            scopeItems = scopeItems,
            rssScopeItems = rssScopeItems,
        )
    }

    /** 返回作用范围摘要文本，分别显示书源和订阅源的作用范围 */
    fun scopeSummary(): String {
        val bookParts = mutableListOf<String>()
        if (hasScope(SCOPE_TITLE)) bookParts.add("标题")
        if (hasScope(SCOPE_AUTHOR)) bookParts.add("作者")
        if (hasScope(SCOPE_KIND)) bookParts.add("分类")
        if (hasScope(SCOPE_INTRO)) bookParts.add("简介")
        if (hasScope(SCOPE_WORD_COUNT)) bookParts.add("字数")

        val rssParts = mutableListOf<String>()
        if (hasRssScope(SCOPE_RSS_TITLE)) rssParts.add("标题")
        if (hasRssScope(SCOPE_RSS_TIME)) rssParts.add("时间")

        val result = mutableListOf<String>()
        if (bookParts.isNotEmpty()) result.add("书源: ${bookParts.joinToString(", ")}")
        if (rssParts.isNotEmpty()) result.add("订阅源: ${rssParts.joinToString(", ")}")
        return if (result.isEmpty()) "无" else result.joinToString(" | ")
    }

    /** 返回匹配模式标签："关键词" 或 "正则" */
    fun modeLabel(): String {
        return if (isRegex) "正则" else "关键词"
    }

    /** 复制规则并生成新的ID，用于导入时避免ID冲突 */
    fun copyWithNewId(): BlockRule {
        return copy(id = UUID.randomUUID().toString())
    }

    companion object {
        // 书源作用范围位标志（用于 targetScope 字段）
        const val SCOPE_TITLE = 1       // 0b00001 标题
        const val SCOPE_AUTHOR = 2      // 0b00010 作者
        const val SCOPE_KIND = 4         // 0b00100 分类
        const val SCOPE_INTRO = 8       // 0b01000 简介
        const val SCOPE_WORD_COUNT = 32 // 0b100000 字数
        const val SCOPE_BOOK_ALL = 47   // 0b101111 书源全部（标题|作者|分类|简介|字数）

        // 订阅源作用范围位标志（用于 rssTargetScope 字段）
        const val SCOPE_RSS_TITLE = 1   // 0b0001 标题
        const val SCOPE_RSS_TIME = 4    // 0b0100 时间
        const val SCOPE_RSS_ALL = 5     // 0b0101 订阅源全部（标题+时间）

        // 旧版常量（仅用于数据迁移）
        @Deprecated("订阅源简介无实际内容，已废弃")
        const val SCOPE_RSS_INTRO = 2           // 旧版订阅源简介位（已废弃）
        const val SCOPE_RSS_TIME_LEGACY = 16   // 旧版订阅源时间位（在 targetScope 中）
        const val SCOPE_ALL = 31                // 旧版全部位（兼容旧数据）
    }
}