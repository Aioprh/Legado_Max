package io.legado.app.help.book

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookChapter
import java.io.File

/**
 * 目录刷新时的章节缓存迁移器
 *
 * 章节缓存文件名由章节序号和标题MD5组成(BookChapter.getFileName),
 * 书源调整目录导致章节标题或序号变化后, 新目录按新文件名找不到旧缓存,
 * 表现为刷新目录后已缓存章节被重新下载
 * 以章节url为章节唯一标识, 将旧缓存文件重命名为新文件名
 * 渐进式目录加载会多次发射中间结果(标题未经formatJs处理, 序号也未最终确定),
 * 因此记住每个章节当前使用的缓存文件名, 多次调用migrate接力完成迁移
 */
class ChapterCacheMigrator(
    folderPath: String,
    oldChapters: List<BookChapter>
) {

    private val folder = File(folderPath)
    private val currentNameMap = HashMap<String, String>(oldChapters.size)

    init {
        oldChapters.forEach {
            currentNameMap[it.url] = it.getFileName()
        }
    }

    fun migrate(chapters: List<BookChapter>) {
        if (currentNameMap.isEmpty()) return
        var migratedCount = 0
        kotlin.runCatching {
            chapters.forEach { chapter ->
                val oldName = currentNameMap[chapter.url] ?: return@forEach
                val newName = chapter.getFileName()
                if (oldName == newName) return@forEach
                if (renameFile(oldName, newName)) {
                    currentNameMap[chapter.url] = newName
                    migratedCount++
                } else if (!File(folder, oldName).exists()) {
                    //旧文件不存在(未缓存过或在中间阶段按新名下载), 记录当前名以便后续接力迁移
                    currentNameMap[chapter.url] = newName
                }
            }
        }.onFailure {
            AppLog.put("迁移章节缓存失败", it)
        }
        if (migratedCount > 0) {
            AppLog.putReaderDebug("目录刷新: 已按章节地址迁移${migratedCount}个章节的缓存文件")
        }
    }

    private fun renameFile(oldName: String, newName: String): Boolean {
        val oldFile = File(folder, oldName)
        if (!oldFile.exists()) return false
        val newFile = File(folder, newName)
        if (newFile.exists()) return false
        if (!oldFile.renameTo(newFile)) return false
        //同步迁移去除重复标题的标记文件(.nr后缀)
        val oldNrFile = File(folder, oldName.substringBeforeLast('.') + NR_SUFFIX)
        if (oldNrFile.exists()) {
            val newNrFile = File(folder, newName.substringBeforeLast('.') + NR_SUFFIX)
            if (!newNrFile.exists()) {
                oldNrFile.renameTo(newNrFile)
            }
        }
        return true
    }

    companion object {
        private const val NR_SUFFIX = ".nr"
    }

}
