package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.data.entities.Book
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.*
import io.legado.app.utils.compress.ZipUtils

class FileAssociationViewModel(application: Application) : BaseAssociationViewModel(application) {
    val importBookLiveData = MutableLiveData<Uri>()
    val importRedThemeLiveData = MutableLiveData<Uri>()
    val onLineImportLive = MutableLiveData<Uri>()
    val openBookLiveData = MutableLiveData<Book>()
    val notSupportedLiveData = MutableLiveData<Pair<Uri, String>>()

    fun dispatchIntent(uri: Uri) {
        execute {
            //如果是普通的url，需要根据返回的内容判断是什么
            if (uri.isContentScheme() || uri.isFileScheme()) {
                val fileDoc = FileDoc.fromUri(uri, false)
                val fileName = fileDoc.name
                // .red 文件：Reeden 阅读 App 的主题包格式（RED 头 + ZIP 数据）
                if (fileName.endsWith(".red", ignoreCase = true)) {
                    importRedThemeLiveData.postValue(fileDoc.uri)
                    return@execute
                }
                if (fileName.matches(AppPattern.archiveFileRegex)) {
                    // .zip 文件：检查是否为主题包
                    if (fileName.endsWith(".zip", ignoreCase = true) && isThemeZip(fileDoc)) {
                        importRedThemeLiveData.postValue(fileDoc.uri)
                        return@execute
                    }
                    ArchiveUtils.deCompress(fileDoc, ArchiveUtils.TEMP_PATH) {
                        it.matches(bookFileRegex) || it.endsWith(".red", ignoreCase = true)
                    }.forEach {
                        dispatch(FileDoc.fromFile(it))
                    }
                } else {
                    dispatch(fileDoc)
                }
            } else {
                onLineImportLive.postValue(uri)
            }
        }.onError {
            it.printOnDebug()
            val msg = "无法打开文件\n${it.localizedMessage}"
            errorLive.postValue(msg)
            AppLog.put(msg, it, dialogName = "文件关联")
        }
    }

    private fun dispatch(fileDoc: FileDoc) {
        kotlin.runCatching {
            if (fileDoc.openInputStream().getOrNull().isJson()) {
                importJson(fileDoc.uri)
                return
            }
        }.onFailure {
            it.printOnDebug()
            AppLog.put("尝试导入为JSON文件失败\n${it.localizedMessage}", it, dialogName = "文件关联")
        }
        if (fileDoc.name.endsWith(".red", ignoreCase = true)) {
            importRedThemeLiveData.postValue(fileDoc.uri)
            return
        }
        if (fileDoc.name.matches(bookFileRegex)) {
            importBookLiveData.postValue(fileDoc.uri)
            return
        }
        notSupportedLiveData.postValue(Pair(fileDoc.uri, fileDoc.name))
    }

    /**
     * 检测 zip 文件是否包含主题包清单文件。
     *
     * 兼容四种主题包格式：
     * - 当前分支：application_theme.json
     * - archive_primate_beta 分支：appearance_kit.json
     * - MD3-main 分支：manifest.json
     * - Reeden 阅读 App：theme.json
     */
    private fun isThemeZip(fileDoc: FileDoc): Boolean {
        return kotlin.runCatching {
            fileDoc.openInputStream().getOrThrow().use { input ->
                ZipUtils.getFilesName(input) { true }.any { name ->
                    val normalized = name.replace('\\', '/').trim('/')
                    normalized.endsWith("application_theme.json", ignoreCase = true) ||
                        normalized.equals("application_theme.json", ignoreCase = true) ||
                        normalized.endsWith("appearance_kit.json", ignoreCase = true) ||
                        normalized.equals("appearance_kit.json", ignoreCase = true) ||
                        normalized.endsWith("manifest.json", ignoreCase = true) ||
                        normalized.equals("manifest.json", ignoreCase = true) ||
                        normalized.endsWith("theme.json", ignoreCase = true) ||
                        normalized.equals("theme.json", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }

    fun importBook(uri: Uri) {
        val book = LocalBook.importFile(uri)
        openBookLiveData.postValue(book)
    }
}