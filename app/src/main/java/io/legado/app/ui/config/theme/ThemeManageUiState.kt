package io.legado.app.ui.config.theme

import io.legado.app.help.config.ThemeConfig

/**
 * 主题列表条目（携带原始 configList 索引，供 delConfig / toTopConfigs 使用）
 */
data class ThemeItem(
    val config: ThemeConfig.Config,
    val originalIndex: Int
)

/**
 * 多选状态
 */
data class MultiSelectState(
    val active: Boolean = false,
    val selectedOriginalIndices: Set<Int> = emptySet()
) {
    val count: Int get() = selectedOriginalIndices.size
    val isEmpty: Boolean get() = selectedOriginalIndices.isEmpty()
}

/**
 * 主题编辑弹窗状态
 */
data class EditDialogState(
    val visible: Boolean = false,
    val isNew: Boolean = true,
    val editingIndex: Int = -1,
    val draft: ThemeConfig.Config? = null
)

/**
 * 顶部 Tab：日间 / 夜间
 */
enum class ThemeTab(val isNight: Boolean) {
    DAY(false),
    NIGHT(true)
}

/**
 * "更多" 菜单事件：复制 / 分享 / 导入 / 导出 / 帮助
 */
sealed class ThemeAction {
    data class CopyShare(val configs: List<ThemeConfig.Config>) : ThemeAction()
    data object ImportFromClipboard : ThemeAction()
    data class ExportToFile(val configs: List<ThemeConfig.Config>) : ThemeAction()
    data object ShowHelp : ThemeAction()
}

/**
 * 一次性事件（Toast / Snackbar / 跳转）
 */
sealed class ThemeEvent {
    data class Toast(val resId: Int) : ThemeEvent()
    data class ToastMsg(val msg: String) : ThemeEvent()
    data class Applied(val themeName: String) : ThemeEvent()
    data object ImportSuccess : ThemeEvent()
    data object ImportEmpty : ThemeEvent()
    data object ImportFailed : ThemeEvent()
    data object DeleteConfirm : ThemeEvent()
    data object Recreate : ThemeEvent()
    data class ShareJson(val json: String) : ThemeEvent()
}

/**
 * 主题管理页整体 UiState
 */
data class ThemeManageUiState(
    val tab: ThemeTab = ThemeTab.DAY,
    val allItems: List<ThemeItem> = emptyList(),
    val visibleItems: List<ThemeItem> = emptyList(),
    val multiSelect: MultiSelectState = MultiSelectState(),
    val editDialog: EditDialogState = EditDialogState()
) {
    val isMultiSelectMode: Boolean get() = multiSelect.active
    val selectedCount: Int get() = multiSelect.count
    val allVisibleSelected: Boolean
        get() = visibleItems.isNotEmpty() && visibleItems.all { it.originalIndex in multiSelect.selectedOriginalIndices }
}
