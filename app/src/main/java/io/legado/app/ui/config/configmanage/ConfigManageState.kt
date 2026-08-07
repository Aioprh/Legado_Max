package io.legado.app.ui.config.configmanage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 通用日/夜 Tab 枚举，供所有配置管理页复用。
 */
enum class ConfigTab(val isNight: Boolean) {
    DAY(false),
    NIGHT(true)
}

/**
 * 通用多选状态。
 *
 * 不绑定任何具体数据类型，仅管理选中索引集合和激活状态。
 */
@Stable
data class MultiSelectState(
    val active: Boolean = false,
    val selectedIndices: Set<Int> = emptySet()
) {
    val count: Int get() = selectedIndices.size
    val isEmpty: Boolean get() = selectedIndices.isEmpty()
}

/**
 * 通用编辑弹窗状态。
 *
 * [visible] 和 [isNew] 由通用状态管理；[draft] 的具体类型由调用方自行持有。
 */
@Stable
data class EditDialogState(
    val visible: Boolean = false,
    val isNew: Boolean = true,
    val editingIndex: Int = -1
)

/**
 * 配置管理页通用状态 Holder（组合模式）。
 *
 * 封装了日/夜 Tab 切换、多选模式、编辑弹窗可见性这三组与具体数据类型无关的 UI 状态逻辑。
 * 各管理页通过 [rememberConfigManageState] 创建实例，在自己的 Screen 中组装使用，
 * 不需要继承任何基类。
 *
 * ## 职责
 * - Tab 切换（切换时自动清空多选）
 * - 多选：进入/退出/切换选中/全选/取消全选
 * - 编辑弹窗：打开/关闭/判断可见性
 *
 * ## 不负责
 * - 数据加载/增删改查（由各页面 ViewModel 自行管理）
 * - 列表内容（由各页面 Composable 自行渲染）
 */
@Stable
class ConfigManageState(initialTab: ConfigTab) {

    var tab by mutableStateOf(initialTab)
        private set

    var multiSelect by mutableStateOf(MultiSelectState())
        private set

    var editDialog by mutableStateOf(EditDialogState())
        private set

    // ── Tab ──────────────────────────────────────────────

    fun switchTab(newTab: ConfigTab) {
        if (tab == newTab) return
        tab = newTab
        // 切换 Tab 时退出多选
        multiSelect = MultiSelectState()
    }

    // ── 多选 ──────────────────────────────────────────────

    fun enterMultiSelect(index: Int) {
        multiSelect = MultiSelectState(
            active = true,
            selectedIndices = setOf(index)
        )
    }

    fun exitMultiSelect() {
        multiSelect = MultiSelectState()
    }

    fun toggleSelection(index: Int) {
        val current = multiSelect.selectedIndices
        val updated = if (index in current) current - index else current + index
        multiSelect = if (updated.isEmpty()) {
            MultiSelectState()
        } else {
            multiSelect.copy(selectedIndices = updated)
        }
    }

    /**
     * 全选/取消全选当前可见列表。
     *
     * @param visibleIndices 当前 Tab 下所有可见条目的索引集合
     */
    fun selectAllVisible(visibleIndices: List<Int>) {
        val allSelected = visibleIndices.isNotEmpty() &&
            visibleIndices.all { it in multiSelect.selectedIndices }
        multiSelect = if (allSelected) {
            MultiSelectState()
        } else {
            multiSelect.copy(selectedIndices = visibleIndices.toSet())
        }
    }

    /**
     * 判断指定索引列表是否已全部选中（用于全选按钮切换文案）。
     */
    fun isAllSelected(visibleIndices: List<Int>): Boolean {
        return visibleIndices.isNotEmpty() &&
            visibleIndices.all { it in multiSelect.selectedIndices }
    }

    // ── 编辑弹窗 ──────────────────────────────────────────

    fun openEditDialog(isNew: Boolean, editingIndex: Int = -1) {
        editDialog = EditDialogState(
            visible = true,
            isNew = isNew,
            editingIndex = editingIndex
        )
    }

    fun closeEditDialog() {
        editDialog = EditDialogState()
    }

    // ── 便捷属性 ──────────────────────────────────────────

    val isMultiSelectMode: Boolean get() = multiSelect.active
    val selectedCount: Int get() = multiSelect.count
}

/**
 * 创建并记住一个 [ConfigManageState] 实例。
 *
 * 在 Compose 中使用组合模式管理配置页通用状态的入口。
 *
 * @param initialTab 初始 Tab（日间或夜间）
 */
@Composable
fun rememberConfigManageState(initialTab: ConfigTab): ConfigManageState {
    return remember { ConfigManageState(initialTab) }
}
