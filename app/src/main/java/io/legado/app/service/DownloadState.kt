package io.legado.app.service

import android.app.DownloadManager
import io.legado.app.model.Download
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.systemservices.downloadManager

data class DownloadTask(
    val id: Long,
    val url: String,
    val fileName: String,
    val notificationId: Int,
    val startTime: Long,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val totalSize: Int = 0,
    val downloadedSize: Int = 0
)

enum class DownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED
}

object DownloadState {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val taskMap = mutableMapOf<Long, DownloadTask>()

    fun addTask(id: Long, url: String, fileName: String, notificationId: Int) {
        val task = DownloadTask(
            id = id,
            url = url,
            fileName = fileName,
            notificationId = notificationId,
            startTime = System.currentTimeMillis()
        )
        taskMap[id] = task
        updateFlow()
    }

    fun updateTask(
        id: Long,
        status: DownloadStatus,
        progress: Int = 0,
        totalSize: Int = 0,
        downloadedSize: Int = 0
    ) {
        taskMap[id]?.let { existing ->
            taskMap[id] = existing.copy(
                status = status,
                progress = progress,
                totalSize = totalSize,
                downloadedSize = downloadedSize
            )
            updateFlow()
        }
    }

    fun removeTask(id: Long) {
        taskMap.remove(id)
        updateFlow()
    }

    fun getTask(id: Long): DownloadTask? = taskMap[id]

    fun getAllTasks(): List<DownloadTask> = taskMap.values.toList()

    fun hasTask(url: String): Boolean = taskMap.values.any { it.url == url }

    fun clear() {
        taskMap.clear()
        updateFlow()
    }

    private fun updateFlow() {
        _tasks.value = taskMap.values.toList().sortedByDescending { it.startTime }
    }

    fun cancelDownload(id: Long) {
        downloadManager.remove(id)
        removeTask(id)
    }

    fun retryDownload(context: android.content.Context, id: Long) {
        val task = taskMap[id] ?: return
        downloadManager.remove(id)
        removeTask(id)
        Download.start(context, task.url, task.fileName)
    }

    fun queryAllTaskStatus(): List<DownloadTask> {
        if (taskMap.isEmpty()) return emptyList()
        
        val ids = taskMap.keys.toLongArray()
        val query = DownloadManager.Query().setFilterById(*ids)
        
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return emptyList()
            
            val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val progressIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val fileSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            
            do {
                val taskId = cursor.getLong(idIndex)
                val downloaded = cursor.getInt(progressIndex)
                val total = cursor.getInt(fileSizeIndex)
                val progress = if (total > 0) (downloaded * 100 / total) else 0
                val status = when (cursor.getInt(statusIndex)) {
                    DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                    DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
                    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    else -> DownloadStatus.PENDING
                }
                updateTask(taskId, status, progress, total, downloaded)
            } while (cursor.moveToNext())
        }
        
        return getAllTasks()
    }
}
