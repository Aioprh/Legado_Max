package io.legado.app.ui.config.backup

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.help.storage.BackupFileValidator
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.Restore
import io.legado.app.help.storage.ValidationResult
import io.legado.app.help.storage.ValidationState
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
    val validationResults: Map<String, ValidationResult> = emptyMap(),
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
 * - 触发文件格式验证
 * - 管理用户选择状态
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

            _uiState.update { it.copy(files = files) }
        }
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
                        state.copy(
                            validationResults = state.validationResults + (fileName to result)
                        )
                    }
                }
            } catch (e: Exception) {
                // 验证失败不阻塞，已在 validationResults 中体现
            }
        }
    }

    /**
     * 执行选择性恢复。
     */
    fun restoreSelected(backupPath: String, selectedFiles: List<String>) {
        restoreJob?.cancel()
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