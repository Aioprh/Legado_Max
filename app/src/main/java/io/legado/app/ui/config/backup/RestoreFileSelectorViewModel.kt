package io.legado.app.ui.config.backup

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.help.storage.BackupConfig
import io.legado.app.help.storage.BackupFileValidator
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.Restore
import io.legado.app.help.storage.ValidationResult
import io.legado.app.help.storage.ValidationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RestoreFileSelectorUiState(
    val files: List<BackupInfoHelper.BackupFileInfo> = emptyList(),
    val validationResults: Map<String, ValidationResult> = emptyMap(),
    val isRestoring: Boolean = false,
    val restoreProgress: String = "",
    val restoreCurrent: Int = 0,
    val restoreTotal: Int = 0,
    val restoreError: String? = null,
    val restoreComplete: Boolean = false
)

sealed class RestoreFileSelectorEvent {
    data class Toast(val message: String) : RestoreFileSelectorEvent()
    data object Dismiss : RestoreFileSelectorEvent()
}

class RestoreFileSelectorViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RestoreFileSelectorUiState())
    val uiState: StateFlow<RestoreFileSelectorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RestoreFileSelectorEvent>()
    val events: SharedFlow<RestoreFileSelectorEvent> = _events.asSharedFlow()

    private var validationJob: Job? = null
    private var restoreJob: Job? = null

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

    fun validateFiles(backupPath: String) {
        validationJob?.cancel()
        val files = _uiState.value.files
        if (files.isEmpty()) return
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
                        state.copy(validationResults = state.validationResults + (fileName to result))
                    }
                }
            } catch (_: Exception) {
                // 验证失败不阻塞，已在 validationResults 中体现
            }
        }
    }

    /**
     * Restore.restoreSelected 的进度节点并不等于所选文件数量：
     * 阅读记录、书籍缓存会合并成一个恢复阶段，阅读配置还受忽略开关影响，
     * 同时恢复流程还会额外执行主题背景刷新和最终配置应用。
     */
    private fun getProgressTotal(selectedFiles: List<String>): Int {
        val selected = selectedFiles.toSet()
        var total = selected.size

        val readRecordCount = selected.count {
            it == "readRecord.json" ||
                it == "readRecordDetail.json" ||
                it == "readRecordSession.json"
        }
        if (readRecordCount > 1) total -= readRecordCount - 1

        val bookCacheCount = selected.count {
            it == "book_cache" ||
                it == "bookCacheIndex.json" ||
                it == "bookCacheBooks.json" ||
                it == "bookChapterCache.json"
        }
        if (bookCacheCount > 1) total -= bookCacheCount - 1

        if ("backgroundImages" in selected) total--

        val readConfigCount = selected.count {
            it == "readConfig.json" || it == "readShareConfig.json"
        }
        if (BackupConfig.ignoreReadConfig) {
            total -= readConfigCount
        } else if (readConfigCount > 0) {
            total++
        }

        total += 2
        return total.coerceAtLeast(1)
    }

    /**
     * 执行选择性恢复。
     * 进度由 Restore 的实际项目回调驱动，并与普通恢复统一显示百分比、当前/总数和项目名。
     */
    fun restoreSelected(backupPath: String, selectedFiles: List<String>) {
        restoreJob?.cancel()
        val total = getProgressTotal(selectedFiles)
        _uiState.update {
            it.copy(
                isRestoring = true,
                restoreProgress = "",
                restoreCurrent = 0,
                restoreTotal = total,
                restoreError = null,
                restoreComplete = false
            )
        }

        RestoreSelectorRestoreGuard.begin(backupPath)
        restoreJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var current = 0
                Restore.restoreSelected(context, backupPath, selectedFiles) { itemName ->
                    current = (current + 1).coerceAtMost(total)
                    _uiState.update {
                        it.copy(
                            restoreProgress = itemName,
                            restoreCurrent = current,
                            restoreTotal = total
                        )
                    }
                }
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreCurrent = total,
                        restoreTotal = total,
                        restoreComplete = true
                    )
                }
                RestoreSelectorRestoreGuard.end(backupPath)
                _events.emit(RestoreFileSelectorEvent.Dismiss)
            } catch (e: CancellationException) {
                RestoreSelectorRestoreGuard.end(backupPath)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreError = null
                    )
                }
            } catch (e: Exception) {
                RestoreSelectorRestoreGuard.end(backupPath)
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreError = e.localizedMessage ?: "恢复失败"
                    )
                }
            }
        }
    }

    /**
     * 真正取消后台恢复任务。Mutex 使用 withLock，因此取消后会自动释放锁，
     * 不会留下“界面已经关闭但后台仍占用恢复锁”的状态。
     */
    fun cancelRestore() {
        restoreJob?.cancel()
        restoreJob = null
        _uiState.update {
            it.copy(
                isRestoring = false,
                restoreError = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        validationJob?.cancel()
        restoreJob?.cancel()
    }
}
