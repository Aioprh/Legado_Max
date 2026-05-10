package io.legado.app.ui.debuglog.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import io.legado.app.model.debug.FlowLogItem
import io.legado.app.model.debug.FlowStage
import io.legado.app.model.debug.SourceSubCategory
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import splitties.init.appCtx

/**
 * 调试日志 ViewModel
 */
class DebugLogViewModel(application: Application) : BaseViewModel(application) {

    data class UiState(
        val logs: List<DebugEvent> = emptyList(),
        val flowLogs: List<FlowLogItem> = emptyList(),
        val selectedLog: DebugEvent? = null,
        val isLoading: Boolean = false,
        val isEmpty: Boolean = false,
        val isPaused: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    private var _allLogs = listOf<DebugEvent>()

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(DebugCategory.ALL)
    val selectedCategory: StateFlow<DebugCategory> = _selectedCategory.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow<SourceSubCategory?>(null)
    val selectedSubCategory: StateFlow<SourceSubCategory?> = _selectedSubCategory.asStateFlow()

    private val _selectedFlowStage = MutableStateFlow<FlowStage?>(null)
    val selectedFlowStage: StateFlow<FlowStage?> = _selectedFlowStage.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery: StateFlow<String?> = _searchQuery.asStateFlow()

    val filteredLogs = combine(_uiState, _selectedCategory, _selectedSubCategory, _searchQuery) { uiState, category, subCategory, query ->
        var result = uiState.logs

        if (category != DebugCategory.ALL) {
            result = result.filter { 
                when (category) {
                    DebugCategory.SOURCE -> {
                        it.category == DebugCategory.SOURCE || it.category == DebugCategory.RULE
                    }
                    else -> it.category == category
                }
            }
        }

        if (subCategory != null && category == DebugCategory.SOURCE) {
            result = result.filter { it.subCategory == subCategory }
        }

        query?.let { q ->
            if (q.isNotBlank()) {
                result = result.filter { log ->
                    log.message.contains(q, ignoreCase = true) ||
                    log.detail?.contains(q, ignoreCase = true) == true ||
                    log.url?.contains(q, ignoreCase = true) == true ||
                    log.sourceName?.contains(q, ignoreCase = true) == true
                }
            }
        }

        result
    }.stateIn(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredFlowLogs = combine(_uiState, _selectedFlowStage, _searchQuery) { uiState, stage, query ->
        var result = uiState.flowLogs

        if (stage != null) {
            result = result.filter { it.stage == stage }
        }

        query?.let { q ->
            if (q.isNotBlank()) {
                result = result.filter { log ->
                    log.message.contains(q, ignoreCase = true) ||
                    log.detail?.contains(q, ignoreCase = true) == true ||
                    log.url?.contains(q, ignoreCase = true) == true ||
                    log.sourceName?.contains(q, ignoreCase = true) == true ||
                    log.rule?.contains(q, ignoreCase = true) == true ||
                    log.result?.contains(q, ignoreCase = true) == true
                }
            }
        }

        result
    }.stateIn(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _singleEvents = MutableSharedFlow<String>()
    val singleEvents: SharedFlow<String> = _singleEvents.asSharedFlow()

    init {
        loadHistoryLogs()
        subscribeToEventFlow()
        subscribeToFlowLogs()
        // 初始化时刷新一次流程日志，确保显示最新数据
        refreshFlowLogs()
    }

    private fun subscribeToFlowLogs() {
        FlowLogRecorder.logs
            .onEach { logs ->
                _uiState.value = _uiState.value.copy(flowLogs = logs)
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * 刷新流程日志
     * 手动刷新流程日志列表，确保显示最新数据
     */
    fun refreshFlowLogs() {
        val currentLogs = FlowLogRecorder.getCurrentLogs()
        _uiState.value = _uiState.value.copy(flowLogs = currentLogs)
    }

    fun selectCategory(category: DebugCategory) {
        _selectedCategory.value = category
        if (category != DebugCategory.SOURCE) {
            _selectedSubCategory.value = null
        }
    }

    fun selectSubCategory(subCategory: SourceSubCategory?) {
        _selectedSubCategory.value = subCategory
    }

    fun selectFlowStage(stage: FlowStage?) {
        _selectedFlowStage.value = stage
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    fun selectLog(log: DebugEvent) {
        _uiState.value = _uiState.value.copy(selectedLog = log)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedLog = null)
    }

    fun togglePause() {
        val newPauseState = !_isPaused.value
        _isPaused.value = newPauseState
        _uiState.value = _uiState.value.copy(isPaused = newPauseState)

        if (newPauseState) {
            showToast("已暂停采集")
        } else {
            showToast("已继续采集")
        }
    }

    fun clearLogs() {
        execute {
            DebugEventCenter.clear()
            _allLogs = emptyList()
            _uiState.value = UiState(
                logs = emptyList(),
                isEmpty = true,
                isPaused = _isPaused.value
            )
        }.onSuccess {
            showToast("已清空所有日志")
        }.onError { e ->
            e.printStackTrace()
            showToast("清空失败：${e.message}")
        }
    }

    fun clearSearch() {
        _searchQuery.value = null
    }

    fun copyLogDetail(log: DebugEvent) {
        val text = buildString {
            appendLine("[${log.level.displayName}] ${log.category.displayName}")
            appendLine("时间: ${formatTime(log.time)}")
            appendLine("消息: ${log.message}")
            log.url?.let { appendLine("URL: $it") }
            log.method?.let { appendLine("方法: $it") }
            log.statusCode?.let { appendLine("状态码: $it") }
            log.duration?.let { appendLine("耗时: ${it}ms") }
            log.sourceName?.let { appendLine("书源: $it") }
            log.detail?.let { appendLine("\n详情:\n$it") }
            log.throwable?.let { appendLine("\n异常:\n${it.stackTraceToString()}") }
        }

        copyToClipboard(text)
        showToast("已复制到剪贴板")
    }

    fun exportFilteredLogs(): String {
        val logs = run {
            val current = _uiState.value
            var result = current.logs

            if (_selectedCategory.value != DebugCategory.ALL) {
                result = result.filter { it.category == _selectedCategory.value }
            }

            _searchQuery.value?.let { query ->
                if (!query.isBlank()) {
                    result = result.filter { it.message.contains(query, ignoreCase = true) }
                }
            }

            result
        }

        return buildString {
            appendLine("=== 调试日志导出 ===")
            appendLine("导出时间: ${formatTime(System.currentTimeMillis())}")
            appendLine("总条数: ${logs.size}\n")

            logs.forEachIndexed { index, event ->
                appendLine("--- [${index + 1}] ---")
                appendLine("[${event.level.displayName}] [${event.category.displayName}]")
                appendLine("时间: ${formatTime(event.time)}")
                appendLine("消息: ${event.message}")

                event.url?.let { appendLine("URL: $it") }
                event.method?.let { appendLine("方法: $it") }
                event.statusCode?.let { appendLine("状态码: $it") }
                event.duration?.let { appendLine("耗时: ${it}ms") }
                event.sourceName?.let { appendLine("书源: $it") }

                event.detail?.let { appendLine("\n详情:\n$it") }
                event.throwable?.let { appendLine("\n异常:\n${it.stackTraceToString()}") }

                appendLine()
            }
        }
    }

    fun exportAllLogs(): String {
        return DebugEventCenter.exportToText()
    }

    private fun loadHistoryLogs() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        execute {
            _allLogs = DebugEventCenter.getRecentLogs(DebugEventCenter.MAX_EVENTS)

            _uiState.value = UiState(
                logs = _allLogs,
                isEmpty = _allLogs.isEmpty(),
                isLoading = false,
                isPaused = _isPaused.value
            )
        }.onError { e ->
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(isLoading = false, isEmpty = true)
            showToast("加载历史日志失败：${e.message}")
        }
    }

    private fun subscribeToEventFlow() {
        DebugEventCenter.eventFlow
            .filter { !_isPaused.value }
            .debounce(100)
            .mapLatest { event ->
                val updatedLogs = mutableListOf(event)
                updatedLogs.addAll(_allLogs)

                if (updatedLogs.size > DebugEventCenter.MAX_EVENTS) {
                    updatedLogs.removeAt(updatedLogs.lastIndex)
                }

                _allLogs = updatedLogs.toList()

                _uiState.value.copy(
                    logs = _allLogs,
                    isEmpty = false,
                    isPaused = _isPaused.value
                )
            }
            .catch { e ->
                e.printStackTrace()
                showToast("接收事件异常：${e.message}")
            }
            .launchIn(viewModelScope)
    }

    private fun showToast(message: String) {
        appCtx.toastOnUi(message)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Debug Log", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    companion object {
        const val MAX_DISPLAY_LOGS = DebugEventCenter.MAX_EVENTS
    }
}
