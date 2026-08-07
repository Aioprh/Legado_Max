package io.legado.app.ui.config.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * 配置管理通用 UI 状态 Holder（与具体数据类型解耦）。
 *
 * 封装主题/顶栏/底栏等管理界面共有的交互状态：
 * - 日/夜 Tab 切换
 * - 多选模式（进入/退出、选中集合、全选/取消全选）
 * - 编辑弹窗（可见性、是否新建、正在编辑的原始索引）
 *
 * 通过 [rememberConfigManageState] 在 Composable 中创建。
 */
@Stable
class ConfigManageState(initialTab: ConfigTab) {

    // ── Tab 状态 ────────────────────────────────────────────
    var tab: ConfigTab by mutableStateOf(initialTab)

    // ── 多选模式 ────────────────────────────────────────────
    var isMultiSelectMode: Boolean by mutableStateOf(false)
    val multiSelect = MultiSelectState()

    // ── 编辑弹窗 ────────────────────────────────────────────
    val editDialog = EditDialogState()

    // ── Tab 操作 ────────────────────────────────────────────

    fun switchTab(newTab: ConfigTab) {
        if (tab == newTab) return
        tab = newTab
        if (isMultiSelectMode) {
            exitMultiSelect()
        }
    }

    // ── 多选操作 ────────────────────────────────────────────

    fun enterMultiSelect(index: Int) {
        if (!isMultiSelectMode) {
            isMultiSelectMode = true
            multiSelect.clear()
        }
        multiSelect.add(index)
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        multiSelect.clear()
    }

    fun toggleSelection(index: Int) {
        if (index in multiSelect.selectedIndices) {
            multiSelect.remove(index)
        } else {
            multiSelect.add(index)
        }
        if (multiSelect.selectedIndices.isEmpty()) {
            isMultiSelectMode = false
        }
    }

    val selectedCount: Int get() = multiSelect.selectedIndices.size

    fun isAllSelected(visibleIndices: List<Int>): Boolean {
        if (visibleIndices.isEmpty()) return false
        return visibleIndices.all { it in multiSelect.selectedIndices }
    }

    fun selectAllVisible(visibleIndices: List<Int>) {
        visibleIndices.forEach { multiSelect.add(it) }
    }

    // ── 编辑弹窗操作 ────────────────────────────────────────

    fun openEditDialog(isNew: Boolean, editingIndex: Int = -1) {
        editDialog.visible = true
        editDialog.isNew = isNew
        editDialog.editingIndex = editingIndex
    }

    fun closeEditDialog() {
        editDialog.visible = false
    }
}

// ── 多选状态 ────────────────────────────────────────────────

@Stable
class MultiSelectState {
    val selectedIndices: SnapshotStateList<Int> = emptyList<Int>().toMutableStateList()

    fun add(index: Int) {
        if (index !in selectedIndices) {
            selectedIndices.add(index)
        }
    }

    fun remove(index: Int) {
        selectedIndices.remove(index)
    }

    fun clear() {
        selectedIndices.clear()
    }
}

// ── 编辑弹窗状态 ────────────────────────────────────────────

@Stable
class EditDialogState {
    var visible: Boolean by mutableStateOf(false)
    var isNew: Boolean by mutableStateOf(true)
    var editingIndex: Int by mutableIntStateOf(-1)
}

// ── 通用数据类 ──────────────────────────────────────────────

/**
 * 日间/夜间枚举，作为 DayNightPager、SegmentedTabRow 等组件的泛型实参。
 */
enum class ConfigTab(val isNight: Boolean) {
    DAY(false),
    NIGHT(true)
}

// ── Composable 入口 ─────────────────────────────────────────

@Composable
fun rememberConfigManageState(initialTab: ConfigTab = ConfigTab.DAY): ConfigManageState {
    return remember { ConfigManageState(initialTab) }
}
