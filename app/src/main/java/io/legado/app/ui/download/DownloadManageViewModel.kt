package io.legado.app.ui.download

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.service.DownloadState
import io.legado.app.service.DownloadStatus
import io.legado.app.service.DownloadTask
import io.legado.app.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadTab(val label: String) {
    ALL("全部"),
    DOWNLOADING("下载中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    FAILED("失败")
}

/**
 * 下载管理ViewModel
 * 负责管理UI状态、轮询下载进度、执行下载操作
 */
class DownloadManageViewModel(application: Application) : BaseViewModel(application) {

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
                val updatedTasks = DownloadState.queryAllTaskStatus()
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
        DownloadService.cancelDownload(id)
    }

    /**
     * 重试下载
     * @param context 上下文
     * @param id 下载任务ID
     */
    fun retryDownload(context: Context, id: Long) {
        DownloadService.retryDownload(context, id)
    }

    /**
     * 清除已完成的任务
     * 包括成功和失败的任务
     */
    fun clearCompletedTasks() {
        _tasks.value.filter { 
            it.status == DownloadStatus.SUCCESSFUL || it.status == DownloadStatus.FAILED 
        }.forEach {
            DownloadState.removeTask(it.id)
        }
    }

    /**
     * 清除所有任务
     */
    fun clearAllTasks() {
        DownloadService.clearAllTasks()
    }

}
