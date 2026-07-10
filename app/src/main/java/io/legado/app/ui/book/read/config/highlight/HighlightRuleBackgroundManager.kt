package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import android.graphics.Bitmap
import io.legado.app.ui.book.read.page.entities.TextLine
import java.io.File

/**
 * 高亮规则背景图的文件管理器。
 *
 * 负责把外部图片迁移到应用内部目录、从备份目录恢复图片、
 * 收集正在使用的背景图文件，并清理未被规则引用的内部图片。
 */
object HighlightRuleBackgroundManager {

    fun migrateToInternal(context: Context, rule: HighlightRule): HighlightRule {
        val path = rule.bgImage ?: return rule
        if (path.startsWith("assets://")) return rule
        if (path.startsWith(context.filesDir.absolutePath)) return rule
        val migrated = TextLine.copyBgImageToInternal(context, path) ?: return rule
        if (migrated == path) return rule
        return rule.copy(bgImage = migrated)
    }

    fun restoreFromBackup(
        context: Context,
        backupRootPath: String?,
        bgImage: String?,
    ): String? {
        val path = bgImage ?: return null
        if (path.isBlank() || path.startsWith("assets://")) return path
        val rootPath = backupRootPath ?: return path
        val backupFile = File(rootPath, "${HighlightRuleStore.backupBgDirName}${File.separator}${File(path).name}")
            .takeIf { it.exists() && it.isFile }
            ?: return path
        val dir = File(context.filesDir, "bg_images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val targetFile = File(dir, backupFile.name)
        if (!targetFile.exists() || targetFile.length() != backupFile.length()) {
            backupFile.copyTo(targetFile, overwrite = true)
        }
        return targetFile.absolutePath
    }

    fun getUsedFiles(context: Context, rules: List<HighlightRule>): List<File> {
        return rules
            .mapNotNull { it.bgImage }
            .asSequence()
            .filter { it.isNotBlank() && !it.startsWith("assets://") }
            .map(::File)
            .filter { it.exists() && it.isFile }
            .distinctBy { it.absolutePath }
            .toList()
    }

    fun cleanupUnused(context: Context, rules: List<HighlightRule>) {
        val usedPaths = rules.mapNotNull { it.bgImage }.toSet()
        TextLine.cleanupUnusedBgImages(context, usedPaths)
    }

    fun copyToInternal(context: Context, sourcePath: String): String? {
        return TextLine.copyBgImageToInternal(context, sourcePath)
    }

    fun getBitmap(path: String): Bitmap? {
        return TextLine.getBgBitmap(path)
    }
}