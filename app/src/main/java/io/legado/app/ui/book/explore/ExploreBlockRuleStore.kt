package io.legado.app.ui.book.explore

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.SearchBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * 屏蔽规则存储管理
 *
 * 使用 SharedPreferences + JSON 序列化存储屏蔽规则列表，
 * 提供规则的加载、保存、过滤和清洗功能。
 * 内置内存缓存避免频繁反序列化。
 */
object ExploreBlockRuleStore {

    /** 内存缓存，避免频繁读取 SharedPreferences 和反序列化 */
    @Volatile
    private var cachedRules: List<ExploreBlockRule>? = null

    /**
     * 加载所有屏蔽规则
     * 优先从缓存读取，缓存未命中时从 SharedPreferences 反序列化
     */
    fun load(context: Context): MutableList<ExploreBlockRule> {
        cachedRules?.let { return it.toMutableList() }
        val stored = context.getPrefString(PreferKey.exploreBlockRuleItems)
        if (stored.isNullOrBlank()) {
            return mutableListOf()
        }
        val rules = GSON.fromJsonArray<ExploreBlockRule>(stored).getOrNull()?.toMutableList()
        if (rules != null) {
            val sanitized = rules.map { sanitizeRule(it) }
            cachedRules = sanitized
            ExploreBlockRuleGroupStore.ensureFromRules(context, sanitized)
            return sanitized.toMutableList()
        }
        return mutableListOf()
    }

    /** 加载已启用且有匹配模式的规则 */
    fun loadEnabled(context: Context): List<ExploreBlockRule> {
        return load(context).filter { it.enabled && it.pattern.isNotBlank() }
    }

    /**
     * 保存规则列表
     * 同时更新缓存和 SharedPreferences，并同步分组信息
     */
    fun save(context: Context, rules: List<ExploreBlockRule>) {
        val normalized = rules.map { sanitizeRule(it) }
        cachedRules = normalized
        context.putPrefString(PreferKey.exploreBlockRuleItems, GSON.toJson(normalized))
        ExploreBlockRuleGroupStore.ensureFromRules(context, normalized)
    }

    /**
     * 核心过滤方法：返回被屏蔽规则过滤后的书籍列表
     * 遍历所有已启用的规则，移除匹配的书籍
     */
    fun filterBooks(context: Context, books: List<SearchBook>, sourceUrl: String): List<SearchBook> {
        val rules = loadEnabled(context)
        if (rules.isEmpty()) return books
        return books.filterNot { book ->
            rules.any { rule -> rule.matches(book) && rule.matchesScope(sourceUrl) }
        }
    }

    /**
     * 获取实际匹配到书籍的规则列表
     * 返回在指定书籍列表和书源下，至少匹配了一本书的规则
     */
    fun getMatchedRules(context: Context, books: List<SearchBook>, sourceUrl: String): List<ExploreBlockRule> {
        val rules = loadEnabled(context)
        if (rules.isEmpty() || books.isEmpty()) return emptyList()
        return rules.filter { rule ->
            rule.matchesScope(sourceUrl) && books.any { book -> rule.matches(book) }
        }
    }

    /** 清除缓存，下次加载时重新从 SharedPreferences 读取 */
    fun invalidateCache() {
        cachedRules = null
    }

    /**
     * 清洗规则数据，确保字段合法
     * 处理缺失字段、空值、越界值等情况
     */
    fun sanitizeRule(
        rule: ExploreBlockRule,
        fallbackGroup: String = ExploreBlockRuleGroupStore.DEFAULT_GROUP,
    ): ExploreBlockRule {
        val name = runCatching { rule.name }.getOrNull().orEmpty()
        val pattern = runCatching { rule.pattern }.getOrNull().orEmpty()
        val group = runCatching { rule.group }.getOrNull().orEmpty().ifBlank { fallbackGroup }
        val id = runCatching { rule.id }.getOrNull().orEmpty().ifBlank {
            "${System.currentTimeMillis()}_${name.hashCode().toUInt().toString(16)}"
        }
        val scope = runCatching { rule.scope }.getOrNull()?.takeIf { it.isNotBlank() }
        val excludeScope = runCatching { rule.excludeScope }.getOrNull()?.takeIf { it.isNotBlank() }
        return ExploreBlockRule(
            id = id,
            name = name,
            pattern = pattern,
            isRegex = runCatching { rule.isRegex }.getOrDefault(false),
            group = group,
            targetScope = runCatching { rule.targetScope }.getOrDefault(0)
                .coerceIn(0, ExploreBlockRule.SCOPE_ALL),
            enabled = runCatching { rule.enabled }.getOrDefault(true),
            scope = scope,
            excludeScope = excludeScope,
        )
    }
}
