package io.legado.app.ui.download

import android.os.Environment
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.legado.app.R
import io.legado.app.service.DownloadStatus
import io.legado.app.service.DownloadTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadTab(@StringRes val labelRes: Int) {
    ALL(R.string.all),
    DOWNLOADING(R.string.download_tab_downloading),
    PAUSED(R.string.download_tab_paused),
    COMPLETED(R.string.download_tab_completed),
    FAILED(R.string.download_tab_failed)
}

/**
 * 下载管理一次性 UI 事件
 * 平台操作（打开文件、剪贴板、Toast）统一由 Activity 执行，ViewModel 只做数据准备并抛事件（state-events.md §4.1）
 */
sealed interface DownloadEvent {
    data class OpenFile(val taskId: Long) : DownloadEvent
    data object OpenFolder : DownloadEvent
    data class CopyPath(val path: String) : DownloadEvent
    data class Toast(@StringRes val msgRes: Int) : DownloadEvent
}

/**
 * 下载管理ViewModel
 * 负责管理UI状态、轮询下载进度、执行下载操作
 *
 * 依赖经 [DownloadTaskSource]/[DownloadCommander]/下载目录提供器构造注入（默认 Default 实现），
 * JVM 单测可直接 Fake（testing.md §16）
 */
class DownloadManageViewModel(
    private val taskSource: DownloadTaskSource = DownloadTaskSource.Default,
    private val commander: DownloadCommander = DownloadCommander.Default,
    private val downloadsDir: () -> String = DefaultDownloadsDir
) : ViewModel() {

    // 关键事件（打开文件/文件夹、复制路径）：UNLIMITED 缓冲，事件不允许丢失（§4.1）
    private val _events = Channel<DownloadEvent>(Channel.UNLIMITED)
    val events: Flow<DownloadEvent> = _events.receiveAsFlow()

    // Toast 事件：天然允许"只留最新"，使用 CONFLATED 通道（等价容量 1 + DROP_OLDEST）；
    // 页面后台期间新 Toast 到达会丢弃旧的，丢事件语义符合预期（§4.1）
    private val _toasts = Channel<DownloadEvent.Toast>(Channel.CONFLATED)
    val toasts: Flow<DownloadEvent.Toast> = _toasts.receiveAsFlow()

    // 任务列表StateFlow，供UI订阅
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    // 当前选中的 Tab
    private val _selectedTab = MutableStateFlow(DownloadTab.ALL)
    val selectedTab: StateFlow<DownloadTab> = _selectedTab.asStateFlow()

    // 过滤后的任务列表
    val filteredTasks: StateFlow<List<DownloadTask>> = combine(
        _tasks, _selectedTab
    ) { tasks, tab ->
        when (tab) {
            DownloadTab.ALL -> tasks
            DownloadTab.DOWNLOADING -> tasks.filter {
                it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING
            }
            DownloadTab.PAUSED -> tasks.filter { it.status == DownloadStatus.PAUSED }
            DownloadTab.COMPLETED -> tasks.filter { it.status == DownloadStatus.SUCCESSFUL }
            DownloadTab.FAILED -> tasks.filter { it.status == DownloadStatus.FAILED }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectTab(tab: DownloadTab) {
        _selectedTab.value = tab
    }

    // 轮询任务Job
    private var pollJob: Job? = null

    init {
        startPolling()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    /**
     * 启动轮询任务
     * 每500ms查询一次下载状态
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val updatedTasks = taskSource.queryAllTaskStatus()
                _tasks.value = updatedTasks
                delay(500)
            }
        }
    }

    /**
     * 停止轮询任务
     */
    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * 取消下载
     * @param id 下载任务ID
     */
    fun cancelDownload(id: Long) {
        commander.cancelDownload(id)
    }

    /**
     * 重试下载
     * @param id 下载任务ID
     */
    fun retryDownload(id: Long) {
        commander.retryDownload(id)
    }

    /**
     * 清除已完成的任务
     * 包括成功和失败的任务
     */
    fun clearCompletedTasks() {
        _tasks.value.filter {
            it.status == DownloadStatus.SUCCESSFUL || it.status == DownloadStatus.FAILED
        }.forEach {
            taskSource.removeTask(it.id)
        }
    }

    /**
     * 清除所有任务
     */
    fun clearAllTasks() {
        commander.clearAllTasks()
    }

    /**
     * 打开已下载的文件
     * 只做数据准备，打开动作通过事件抛给 Activity 执行（§4.1）
     * @param id 下载任务ID
     */
    fun openFile(id: Long) {
        _events.trySend(DownloadEvent.OpenFile(id))
    }

    /**
     * 打开下载文件所在的文件夹
     * 打开动作通过事件抛给 Activity 执行（§4.1）
     */
    fun openFolder() {
        _events.trySend(DownloadEvent.OpenFolder)
    }

    /**
     * 复制文件路径到剪贴板
     * 只计算路径（数据准备），剪贴板与 Toast 由 Activity 执行（§4.1）
     * @param id 下载任务ID
     */
    fun copyPath(id: Long) {
        val task = taskSource.getTask(id) ?: return
        val filePath = "${downloadsDir()}/${task.fileName}"
        _events.trySend(DownloadEvent.CopyPath(filePath))
        _toasts.trySend(DownloadEvent.Toast(R.string.download_path_copied))
    }

    companion object {
        /** 生产环境默认下载目录（公共 Downloads 目录） */
        val DefaultDownloadsDir: () -> String = {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        }

        /** 默认工厂：生产环境使用 Default 依赖（构造参数有默认值，反射工厂需要显式构造） */
        val Factory = viewModelFactory {
            initializer { DownloadManageViewModel() }
        }
    }
}
