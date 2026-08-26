package io.legado.app.ui.config.backup

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.help.storage.BackupFileValidator
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.Restore
import io.legado.app.help.storage.ValidationResult
import io.legado.app.help.storage.ValidationState
import io.legado.app.ui.widget.components.dialog.MultiSelectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 恢复文件选择器的 UI 状态。
 */
data class RestoreFileSelectorUiState(
    val files: List<BackupInfoHelper.BackupFileInfo> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val validationResults: Map<String, ValidationResult> = emptyMap(),
    val detailResult: ValidationResult? = null,
    val isRestoring: Boolean = false,
    val restoreProgress: String = "",
    val restoreError: String? = null,
    val restoreComplete: Boolean = false
)

/**
 * 一次性事件，如 toast / dismiss。
 */
sealed class RestoreFileSelectorEvent {
    data class Toast(val message: String) : RestoreFileSelectorEvent()
    data object Dismiss : RestoreFileSelectorEvent()
}

/**
 * 恢复文件选择器 ViewModel。
 *
 * 负责：
 * - 从已解压的备份目录扫描文件列表
 * - 维护用户选择状态（默认全选）
 * - 触发文件格式验证与详情弹窗状态
 * - 执行选择性恢复
 *
 * 不持有 Context 引用（除 Application），验证和恢复操作通过 IO 线程执行。
 */
class RestoreFileSelectorViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RestoreFileSelectorUiState())
    val uiState: StateFlow<RestoreFileSelectorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RestoreFileSelectorEvent>()
    val events: SharedFlow<RestoreFileSelectorEvent> = _events.asSharedFlow()

    private var validationJob: Job? = null
    private var restoreJob: Job? = null

    /**
     * 扫描备份目录，加载文件列表。
     */
    fun loadFiles(backupPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = BackupInfoHelper.scanRestoreDirectory(backupPath)

            if (files.isEmpty()) {
                _events.emit(RestoreFileSelectorEvent.Toast("备份文件为空"))
                _events.emit(RestoreFileSelectorEvent.Dismiss)
                return@launch
            }

            _uiState.update {
                // 默认全选，保持原有行为；弹窗内的勾选状态统一收进 UiState
                it.copy(
                    files = files,
                    selectedKeys = files.map { file -> file.fileName }.toSet()
                )
            }
        }
    }

    /**
     * 处理用户选择项变化事件。
     */
    fun onSelectionChange(key: String, isSelected: Boolean) {
        _uiState.update { state ->
            val selectedKeys = if (isSelected) {
                state.selectedKeys + key
            } else {
                state.selectedKeys - key
            }
            state.copy(selectedKeys = selectedKeys)
        }
    }

    /**
     * 全选所有备份文件。
     */
    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedKeys = state.files.map { it.fileName }.toSet())
        }
    }

    /**
     * 取消全选所有备份文件。
     */
    fun deselectAll() {
        _uiState.update { it.copy(selectedKeys = emptySet()) }
    }

    /**
     * 格式化选中项的总大小。
     */
    fun formatTotalSize(selectedItems: List<MultiSelectItem>): String {
        return BackupInfoHelper.formatSize(selectedItems.sumOf { it.rawSize ?: 0L })
    }

    /**
     * 显示指定文件的验证详情弹窗。
     *
     * 优先按文件名取结果；取不到时回退到当前唯一一条结果，
     * 避免验证器未回填文件名的历史数据导致点击无响应。
     */
    fun showValidationDetail(fileName: String) {
        val results = _uiState.value.validationResults
        val result = results[fileName] ?: results.values.singleOrNull() ?: return
        _uiState.update { it.copy(detailResult = result) }
    }

    /**
     * 关闭验证详情弹窗。
     */
    fun hideValidationDetail() {
        _uiState.update { it.copy(detailResult = null) }
    }

    /**
     * 触发文件格式验证。
     */
    fun validateFiles(backupPath: String) {
        validationJob?.cancel()
        val files = _uiState.value.files
        if (files.isEmpty()) return

        // 标记所有文件为"验证中"
        _uiState.update { state ->
            state.copy(
                validationResults = files.associate {
                    it.fileName to ValidationResult(
                        state = ValidationState.VALIDATING,
                        fileName = it.fileName
                    )
                }
            )
        }

        validationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                BackupFileValidator.validateFiles(
                    backupPath,
                    files.map { it.fileName }
                ) { fileName, result ->
                    _uiState.update { state ->
                        // 验证器部分结果不带文件名(如结构校验)，统一回填为实际文件名，
                        // 保证列表行状态映射与详情弹窗标题都能取到值
                        val normalized = result.copy(fileName = fileName)
                        state.copy(
                            validationResults = state.validationResults + (fileName to normalized)
                        )
                    }
                }
            } catch (e: Exception) {
                // 验证失败不阻塞，已在 validationResults 中体现
            }
        }
    }

    /**
     * 执行选择性恢复。未选中任何文件时提示用户且不启动恢复。
     */
    fun restoreSelected(backupPath: String) {
        restoreJob?.cancel()
        val selectedFiles = _uiState.value.selectedKeys.toList()
        if (selectedFiles.isEmpty()) {
            viewModelScope.launch {
                _events.emit(
                    RestoreFileSelectorEvent.Toast(context.getString(R.string.fvd_select_at_least_one))
                )
            }
            return
        }
        _uiState.update { it.copy(isRestoring = true, restoreProgress = "", restoreError = null) }

        restoreJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Restore.restoreSelected(
                    context,
                    backupPath,
                    selectedFiles
                ) { itemName ->
                    _uiState.update {
                        it.copy(restoreProgress = itemName)
                    }
                }
                _uiState.update { it.copy(isRestoring = false, restoreComplete = true) }
                _events.emit(RestoreFileSelectorEvent.Dismiss)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreError = e.localizedMessage ?: "恢复失败"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        validationJob?.cancel()
        restoreJob?.cancel()
    }
}