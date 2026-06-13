package io.legado.app.ui.book.explore

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * 屏蔽规则分组存储管理
 *
 * 使用 SharedPreferences + JSON 序列化存储分组列表，
 * 确保默认分组始终存在，并在规则变化时同步分组信息。
 */
object ExploreBlockRuleGroupStore {

    /** 默认分组名称 */
    const val DEFAULT_GROUP = "默认分组"

    /**
     * 加载分组列表
     * 确保默认分组始终存在且位于首位
     */
    fun load(context: Context): MutableList<String> {
        val stored = context.getPrefString(PreferKey.exploreBlockRuleGroups)
        val groups = GSON.fromJsonArray<String>(stored).getOrNull()?.toMutableList()
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.toMutableList()
            ?: mutableListOf()
        if (groups.isEmpty()) {
            groups.add(DEFAULT_GROUP)
            save(context, groups)
        }
        if (!groups.contains(DEFAULT_GROUP)) {
            groups.add(0, DEFAULT_GROUP)
            save(context, groups)
        }
        return groups
    }

    /** 保存分组列表，去除空项和重复项，确保至少有默认分组 */
    fun save(context: Context, groups: List<String>) {
        val normalized = groups.filter { it.isNotBlank() }.distinct().ifEmpty { listOf(DEFAULT_GROUP) }
        context.putPrefString(PreferKey.exploreBlockRuleGroups, GSON.toJson(normalized))
    }

    /**
     * 根据规则列表同步分组信息
     * 将规则中引用的分组合并到现有分组列表中
     */
    fun ensureFromRules(context: Context, rules: List<ExploreBlockRule>) {
        val groups = load(context)
        val merged = LinkedHashSet<String>()
        merged.addAll(groups)
        rules.mapTo(merged) { it.group.ifBlank { DEFAULT_GROUP } }
        save(context, merged.toList())
    }
}
