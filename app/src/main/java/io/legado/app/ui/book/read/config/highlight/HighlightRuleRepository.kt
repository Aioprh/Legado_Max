package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * 高亮规则的数据仓库入口。
 *
 * 为 UI、ViewModel 和阅读排版层提供规则、分组、当前分组的统一访问接口，
 * 隔离底层 Store、SharedPreferences 和 JSON 编码细节。
 */
object HighlightRuleRepository {

    fun loadRules(context: Context): MutableList<HighlightRule> {
        return HighlightRuleStore.load(context)
    }

    fun loadEnabledRules(context: Context): List<HighlightRule> {
        return HighlightRuleStore.loadEnabled(context)
    }

    fun saveRules(context: Context, rules: List<HighlightRule>) {
        HighlightRuleStore.save(context, rules)
    }

    fun resetRules(context: Context): MutableList<HighlightRule> {
        return HighlightRuleStore.reset(context)
    }

    fun loadGroups(context: Context): MutableList<String> {
        return HighlightRuleGroupStore.load(context)
    }

    fun saveGroups(context: Context, groups: List<String>) {
        HighlightRuleGroupStore.save(context, groups)
    }

    fun saveCurrentGroup(context: Context, group: String?) {
        context.putPrefString(PreferKey.highlightRuleCurrentGroup, group.orEmpty())
    }

    fun loadCurrentGroup(context: Context): String? {
        val saved = context.getPrefString(PreferKey.highlightRuleCurrentGroup)
        if (saved.isNullOrBlank()) return null
        return saved.takeIf { loadGroups(context).contains(it) }
    }

    fun sanitizeRule(
        rule: HighlightRule,
        fallbackGroup: String = HighlightRuleGroupStore.DEFAULT_GROUP,
    ): HighlightRule {
        return HighlightRuleStore.sanitizeRule(rule, fallbackGroup)
    }

    fun encodeRules(rules: List<HighlightRule>): String {
        return GSON.toJson(rules)
    }
}