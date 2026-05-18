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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadManageViewModel(application: Application) : BaseViewModel(application) {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

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

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun cancelDownload(id: Long) {
        DownloadService.cancelDownload(id)
    }

    fun retryDownload(context: Context, id: Long) {
        DownloadService.retryDownload(context, id)
    }

    fun clearCompletedTasks() {
        _tasks.value.filter { 
            it.status == DownloadStatus.SUCCESSFUL || it.status == DownloadStatus.FAILED 
        }.forEach {
            DownloadState.removeTask(it.id)
        }
    }

    fun clearAllTasks() {
        DownloadService.clearAllTasks()
    }

    fun getActiveCount(): Int = _tasks.value.count { 
        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING 
    }

    fun getCompletedCount(): Int = _tasks.value.count { 
        it.status == DownloadStatus.SUCCESSFUL 
    }

    fun getFailedCount(): Int = _tasks.value.count { 
        it.status == DownloadStatus.FAILED 
    }
}
