package io.legado.app.ui.config.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
 * - 编辑弹窗（可见性、是否新建、正在编辑的会话 key）
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

    fun enterMultiSelect(key: String) {
        if (!isMultiSelectMode) {
            isMultiSelectMode = true
            multiSelect.clear()
        }
        multiSelect.add(key)
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        multiSelect.clear()
    }

    fun toggleSelection(key: String) {
        if (key in multiSelect.selectedKeys) {
            multiSelect.remove(key)
        } else {
            multiSelect.add(key)
        }
        if (multiSelect.selectedKeys.isEmpty()) {
            isMultiSelectMode = false
        }
    }

    val selectedCount: Int get() = multiSelect.selectedKeys.size

    fun isAllSelected(visibleKeys: List<String>): Boolean {
        if (visibleKeys.isEmpty()) return false
        return visibleKeys.all { it in multiSelect.selectedKeys }
    }

    fun selectAllVisible(visibleKeys: List<String>) {
        visibleKeys.forEach { multiSelect.add(it) }
    }

    // ── 编辑弹窗操作 ────────────────────────────────────────

    fun openEditDialog(isNew: Boolean, editingKey: String = "") {
        editDialog.visible = true
        editDialog.isNew = isNew
        editDialog.editingKey = editingKey
    }

    fun closeEditDialog() {
        editDialog.visible = false
    }
}

// ── 多选状态 ────────────────────────────────────────────────

@Stable
class MultiSelectState {
    val selectedKeys: SnapshotStateList<String> = emptyList<String>().toMutableStateList()

    fun add(key: String) {
        if (key !in selectedKeys) selectedKeys.add(key)
    }

    fun remove(key: String) {
        selectedKeys.remove(key)
    }

    fun clear() {
        selectedKeys.clear()
    }
}

// ── 编辑弹窗状态 ────────────────────────────────────────────

@Stable
class EditDialogState {
    var visible: Boolean by mutableStateOf(false)
    var isNew: Boolean by mutableStateOf(true)
    var editingKey: String by mutableStateOf("")
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
