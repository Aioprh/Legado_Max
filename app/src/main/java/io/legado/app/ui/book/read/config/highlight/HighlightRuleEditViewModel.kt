package io.legado.app.ui.book.read.config.highlight

import android.app.Application
import io.legado.app.base.BaseViewModel

/**
 * 高亮规则编辑弹窗的状态管理层。
 *
 * 保存当前正在编辑的规则、可选分组列表和编辑页临时 UI 状态，
 * 让弹窗在重建后仍能从 ViewModel 恢复编辑上下文。
 */
class HighlightRuleEditViewModel(application: Application) : BaseViewModel(application) {

    lateinit var editingRule: HighlightRule
    var groupItems: List<String> = emptyList()
        private set
    var isRegexMode: Boolean = false

    private var initialized = false

    fun initialize(
        sourceRule: HighlightRule?,
        defaultGroup: String?,
        defaultScope: String?,
    ) {
        if (initialized) return
        editingRule = sourceRule?.copy() ?: HighlightRule(
            group = defaultGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP,
            scope = defaultScope
        )
        isRegexMode = editingRule.isRegex
        groupItems = HighlightRuleRepository.loadGroups(context)
        initialized = true
    }
}
