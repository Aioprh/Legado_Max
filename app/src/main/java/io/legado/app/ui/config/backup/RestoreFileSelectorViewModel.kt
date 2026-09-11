package io.legado.app.ui.config.backup

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.help.config.BackupConfig
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
import java.io.File

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
     * 计算选择性恢复真正会产生的进度节点。
     * readRecord、book_cache 等内部是一个恢复阶段，而不是按所选文件逐项增加。
     */
    private fun getProgressTotal(backupPath: String, selectedFiles: List<String>): Int {
        val selected = selectedFiles.toSet()
        val progressFiles = setOf(
            "bookshelf.json", "bookmark.json", "bookGroup.json", "bookSource.json",
            "rssSources.json", "rssStar.json", "sourceSub.json", "webSearchEngines.json",
            "homepage.json", "replaceRule.json", "highlightRule.json", "searchHistory.json",
            "txtTocRule.json", "httpTTS.json", "dictRule.json", "keyboardAssists.json",
            "servers.json", "directLinkRule.json", "theme.json", "coverRule.json",
            "videoConfig.xml", "runtimeSourceCache.json"
        )
        var total = selected.count { it in progressFiles }

        if (selected.any { it == "readRecord.json" || it == "readRecordDetail.json" || it == "readRecordSession.json" }) {
            total++
        }
        if (selected.any { it == "book_cache" || it == "bookCacheIndex.json" || it == "bookCacheBooks.json" || it == "bookChapterCache.json" }) {
            total++
        }
        if (!BackupConfig.ignoreReadConfig && selected.any { it == "readConfig.json" || it == "readShareConfig.json" }) {
            if (File(backupPath, "backgroundImages").exists()) total++
            if (selected.contains("readConfig.json")) total++
            if (selected.contains("readShareConfig.json")) total++
        }
        // 选择性恢复流程始终会刷新主题背景和最终阅读配置。
        if (File(backupPath, "themeBackgroundImages").exists()) total++
        total++ // applyRestoreConfig
        return total.coerceAtLeast(1)
    }

    /**
     * 执行选择性恢复。
     * 进度由 Restore 的实际项目回调驱动，按真实恢复阶段统计，避免把关联文件重复计数。
     */
    fun restoreSelected(backupPath: String, selectedFiles: List<String>) {
        restoreJob?.cancel()
        val total = getProgressTotal(backupPath, selectedFiles)
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
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreCurrent = total,
                        restoreTotal = total,
                        restoreComplete = true
                    )
                }
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
