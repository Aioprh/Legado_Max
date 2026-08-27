package io.legado.app.ui.download

import io.legado.app.service.DownloadService
import io.legado.app.service.DownloadState
import io.legado.app.service.DownloadTask
import splitties.init.appCtx

/**
 * 下载任务状态数据源抽象（testing.md §16：静态依赖收敛为构造注入以便 Fake）
 */
interface DownloadTaskSource {

    fun queryAllTaskStatus(): List<DownloadTask>

    fun getTask(id: Long): DownloadTask?

    fun removeTask(id: Long)

    companion object Default : DownloadTaskSource {

        override fun queryAllTaskStatus(): List<DownloadTask> =
            DownloadState.queryAllTaskStatus()

        override fun getTask(id: Long): DownloadTask? = DownloadState.getTask(id)

        override fun removeTask(id: Long) = DownloadState.removeTask(id)
    }
}

/**
 * 下载操作指令抽象（平台动作由 Default 实现转发到 DownloadService）
 */
interface DownloadCommander {

    fun cancelDownload(id: Long)

    fun retryDownload(id: Long)

    fun clearAllTasks()

    companion object Default : DownloadCommander {

        override fun cancelDownload(id: Long) = DownloadService.cancelDownload(id)

        override fun retryDownload(id: Long) = DownloadService.retryDownload(appCtx, id)

        override fun clearAllTasks() = DownloadService.clearAllTasks()
    }
}
