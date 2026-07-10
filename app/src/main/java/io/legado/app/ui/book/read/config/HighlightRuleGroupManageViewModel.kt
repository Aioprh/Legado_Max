package io.legado.app.ui.book.read.config

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.ui.book.read.config.highlight.HighlightRule
import io.legado.app.ui.book.read.config.highlight.HighlightRuleGroupStore
import io.legado.app.ui.book.read.config.highlight.HighlightRuleRepository

/**
 * 高亮规则分组管理弹窗的状态管理层。
 *
 * 维护分组列表和规则列表，负责新增、重命名、删除分组时的数据同步，
 * Dialog 只负责收集输入、展示菜单和刷新列表。
 */
class HighlightRuleGroupManageViewModel(application: Application) : BaseViewModel(application) {

    val groups = ArrayList<String>()
    val rules = ArrayList<HighlightRule>()

    fun loadData() {
        groups.clear()
        groups.addAll(HighlightRuleRepository.loadGroups(context))
        rules.clear()
        rules.addAll(HighlightRuleRepository.loadRules(context))
    }

    fun addGroup(name: String) {
        groups.add(name)
        HighlightRuleRepository.saveGroups(context, groups)
        loadData()
    }

    fun renameGroup(source: String, newName: String) {
        val index = groups.indexOf(source)
        if (index >= 0) groups[index] = newName
        rules.replaceAll { rule ->
            if (rule.group == source) rule.copy(group = newName) else rule
        }
        HighlightRuleRepository.saveGroups(context, groups)
        HighlightRuleRepository.saveRules(context, rules)
        loadData()
    }

    fun deleteGroup(group: String) {
        groups.remove(group)
        rules.replaceAll { rule ->
            if (rule.group == group) {
                rule.copy(group = HighlightRuleGroupStore.DEFAULT_GROUP)
            } else {
                rule
            }
        }
        HighlightRuleRepository.saveGroups(context, groups)
        HighlightRuleRepository.saveRules(context, rules)
        loadData()
    }

    fun groupCount(group: String): Int {
        return rules.count { it.group == group }
    }

    fun rulesInGroup(group: String): List<HighlightRule> {
        return rules.filter { it.group == group }
    }
}
