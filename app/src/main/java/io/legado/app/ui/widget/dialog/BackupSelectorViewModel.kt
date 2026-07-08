package io.legado.app.ui.widget.dialog

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.BackupSelectorConfig
import io.legado.app.ui.widget.components.dialog.MultiSelectGroup
import io.legado.app.ui.widget.components.dialog.MultiSelectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 备份选择器界面状态。
 *
 * 这个状态只服务于弹窗展示：加载中显示进度，加载完成后提供分组数据和当前选中项。
 */
sealed class BackupSelectorUiState {
    object Loading : BackupSelectorUiState()

    data class Content(
        val groups: List<MultiSelectGroup>,
        val selectedKeys: Set<String>
    ) : BackupSelectorUiState()
}

/**
 * 备份选择器的 ViewModel。
 *
 * 负责加载备份项详情、维护弹窗内的选择状态，并在用户确认时统一写回配置。
 */
class BackupSelectorViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow<BackupSelectorUiState>(BackupSelectorUiState.Loading)
    val uiState: StateFlow<BackupSelectorUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        // 备份概览会读取数据库数量和文件大小，必须放到 IO 线程，避免阻塞主线程。
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = buildUiState()
        }
    }
    /**
     * 处理用户选择项变化事件。
     *
     * @param key 被选择项的键值。
     * @param isSelected 如果为 true，则添加到选中项集合；否则从集合中移除。
     */

    fun onSelectionChange(key: String, isSelected: Boolean) {
        updateContent { content ->
            val selectedKeys = if (isSelected) {
                content.selectedKeys + key
            } else {
                content.selectedKeys - key
            }
            content.copy(selectedKeys = selectedKeys)
        }
    }
    /**
     * 全选所有备份项。
     */
    fun selectAll() {
        updateContent { content ->
            content.copy(
                selectedKeys = content.groups
                    .flatMap { it.items }
                    .map { it.key }
                    .toSet()
            )
        }
    }
    /**
     * 取消全选所有备份项。
     */
    fun deselectAll() {
        updateContent { content ->
            content.copy(selectedKeys = emptySet())
        }
    }

    fun saveSelection() {
        val content = _uiState.value as? BackupSelectorUiState.Content ?: return
        // 弹窗内的勾选变化先保存在内存中，只有用户确认关闭时才写回配置文件。
        BackupSelectorConfig.setSelectedKeys(content.selectedKeys)
        BackupSelectorConfig.save()
    }
    /**
     * 格式化选中项的总大小。
     *
     * @param selectedItems 选中的备份项列表。
     * @return 格式化后的总大小字符串。
     */
    fun formatTotalSize(selectedItems: List<MultiSelectItem>): String {
        return BackupInfoHelper.formatSize(selectedItems.sumOf { it.rawSize ?: 0L })
    }

    private fun updateContent(
        block: (BackupSelectorUiState.Content) -> BackupSelectorUiState.Content
    ) {
        val content = _uiState.value as? BackupSelectorUiState.Content ?: return
        _uiState.value = block(content)
    }

    private fun buildUiState(): BackupSelectorUiState.Content {
        // 在 ViewModel 中把存储层的备份定义转换成通用多选弹窗模型，
        // 避免 BackupSelectorConfig 反向依赖 UI 组件类。
        val overview = BackupInfoHelper.getBackupOverview()
        val fileInfoByName = overview.items.associateBy { it.fileName }
        val selectedKeys = BackupSelectorConfig.getSelectedKeys()
        val groups = BackupSelectorConfig.groupItems.map { (groupName, items) ->
            MultiSelectGroup(
                name = groupName,
                iconEmoji = BackupSelectorConfig.getGroupIcon(groupName),
                items = items.map { item ->
                    val fileInfo = fileInfoByName[item.overviewFileName()]
                    val countInfo = BackupInfoHelper.getItemCount(item.key)
                        .takeIf { it > 0 }
                        ?.let { "$it 个" }

                    MultiSelectItem(
                        key = item.key,
                        title = item.title,
                        subtitle = item.fileName,
                        size = fileInfo?.let { BackupInfoHelper.formatSize(it.size) },
                        rawSize = fileInfo?.size,
                        count = countInfo,
                        group = item.group,
                        iconEmoji = item.iconEmoji,
                        selected = item.key in selectedKeys
                    )
                }
            )
        }
        return BackupSelectorUiState.Content(groups, selectedKeys)
    }

    private fun BackupSelectorConfig.BackupItem.overviewFileName(): String {
        return when (key) {
            // 选择器持久化的是 "bg"，但 BackupInfoHelper 统计时使用的是展示分组 key。
            "backgroundImages" -> "backgroundImages"
            else -> fileName
        }
    }
}
