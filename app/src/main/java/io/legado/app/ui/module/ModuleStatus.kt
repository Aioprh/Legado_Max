package io.legado.app.ui.module

import io.legado.app.model.CacheBook
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.CacheBookService
import io.legado.app.service.DownloadService
import io.legado.app.service.DownloadStatus
import io.legado.app.service.WebService

enum class ModuleRunStatus {
    RUNNING,
    IDLE,
    ERROR,
    DISABLED
}

data class ModuleStatusItem(
    val name: String,
    val status: ModuleRunStatus
)

object ModuleStatusProvider {

    fun snapshot(): List<ModuleStatusItem> {
        val downloadTasks = DownloadService.getAllTasks()
        return listOf(
            ModuleStatusItem(
                name = "Web 服务",
                status = if (WebService.isRun) ModuleRunStatus.RUNNING else ModuleRunStatus.IDLE
            ),
            ModuleStatusItem(
                name = "离线缓存",
                status = when {
                    CacheBook.errorDownloadMap.isNotEmpty() -> ModuleRunStatus.ERROR
                    CacheBookService.isRun || CacheBook.isRun -> ModuleRunStatus.RUNNING
                    else -> ModuleRunStatus.IDLE
                }
            ),
            ModuleStatusItem(
                name = "朗读服务",
                status = if (BaseReadAloudService.isRun) ModuleRunStatus.RUNNING else ModuleRunStatus.IDLE
            ),
            ModuleStatusItem(
                name = "音频播放",
                status = if (AudioPlayService.isRun) ModuleRunStatus.RUNNING else ModuleRunStatus.IDLE
            ),
            ModuleStatusItem(
                name = "文件下载",
                status = when {
                    downloadTasks.any { it.status == DownloadStatus.FAILED } -> ModuleRunStatus.ERROR
                    downloadTasks.any {
                        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING
                    } -> ModuleRunStatus.RUNNING
                    else -> ModuleRunStatus.IDLE
                }
            ),
            ModuleStatusItem(
                name = "书源搜索",
                status = ModuleRunStatus.IDLE
            ),
            ModuleStatusItem(
                name = "订阅模块",
                status = ModuleRunStatus.IDLE
            ),
            ModuleStatusItem(
                name = "备份同步",
                status = ModuleRunStatus.IDLE
            )
        )
    }
}
