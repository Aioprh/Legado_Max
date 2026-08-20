package io.legado.app.ui.config

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppLog
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.book.read.ShareNoteImageRenderer
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.readBytes
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 摘录分享模板管理 Activity。
 *
 * 在"主题设置 -> 其他设置"中入口进入，支持：
 * - 查看模板列表（内置 + 本地）与实时预览
 * - 应用模板、切换分享样式（配色/字体）
 * - 编辑本地模板（HTML 源码）、复制新建、导入/导出（HTML/ZIP）
 * - 删除本地模板
 */
class ShareNoteTemplateManageActivity : BaseComposeActivity() {

    private val entriesState = mutableStateOf<List<ShareNoteTemplateManager.Entry>>(emptyList())
    private val activeDirNameState = mutableStateOf(ShareNoteTemplateManager.activeDirName())
    private val previewFilesState = mutableStateOf<Map<String, File>>(emptyMap())
    private val shareStyleState = mutableStateOf(ShareNoteTemplateManager.currentStyle())
    private var editingEntry: ShareNoteTemplateManager.Entry? = null
    private var loadTemplatesJob: Job? = null
    private val previewJobs = mutableListOf<Job>()
    private var previewBatch = 0

    private val importTemplate = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            lifecycleScope.launch {
                kotlin.runCatching {
                    withContext(Dispatchers.IO) {
                        val name = uri.lastPathSegment.orEmpty().lowercase()
                        if (name.endsWith(".zip")) {
                            val temp = File(cacheDir, "share_note_template_import.zip")
                            temp.writeBytes(uri.readBytes(this@ShareNoteTemplateManageActivity))
                            ShareNoteTemplateManager.importZip(temp)
                        } else {
                            ShareNoteTemplateManager.importHtml(uri.readText(this@ShareNoteTemplateManageActivity))
                        }
                    }
                }.onSuccess {
                    toastOnUi(R.string.success)
                    loadTemplates()
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: getString(R.string.wrong_format))
                }
            }
        }
    }

    private val exportTemplate = registerForActivityResult(HandleFileContract()) { result ->
        if (result.uri != null) toastOnUi(R.string.export_success)
    }

    private val editTemplateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringExtra("text") ?: return@registerForActivityResult
            lifecycleScope.launch {
                kotlin.runCatching {
                    withContext(Dispatchers.IO) {
                        ShareNoteTemplateManager.addOrUpdate(text, editingEntry)
                    }
                }.onSuccess {
                    editingEntry = null
                    toastOnUi(R.string.success)
                    loadTemplates()
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: getString(R.string.wrong_format))
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        loadTemplates()
    }

    override fun onResume() {
        super.onResume()
        loadTemplates()
    }

    override fun onDestroy() {
        loadTemplatesJob?.cancel()
        cancelPreviewJobs()
        super.onDestroy()
    }

    @Composable
    override fun ComposeContent() {
        ShareNoteTemplateManageScreen(
            entries = entriesState.value,
            activeDirName = activeDirNameState.value,
            shareStyle = shareStyleState.value,
            previewFiles = previewFilesState.value,
            onBackClick = { finish() },
            args = ShareNoteTemplateManageArgs(
                onApply = ::applyTemplate,
                onStyleChange = ::updateShareStyle,
                onEdit = ::editTemplate,
                onMoreActions = ::templateActions,
                onAddClick = ::showAddActions
            )
        )
    }

    private fun loadTemplates() {
        loadTemplatesJob?.cancel()
        cancelPreviewJobs()
        loadTemplatesJob = lifecycleScope.launch {
            val entries = try {
                withContext(Dispatchers.IO) { ShareNoteTemplateManager.loadEntries() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put("Share note template load failed\n${e.localizedMessage}", e)
                toastOnUi(getString(R.string.theme_package_load_failed, e.localizedMessage ?: getString(R.string.error)))
                return@launch
            }
            entriesState.value = entries
            activeDirNameState.value = ShareNoteTemplateManager.activeDirName()
            refreshPreviews(entries)
        }
    }

    private fun refreshPreviews(
        entries: List<ShareNoteTemplateManager.Entry>,
        force: Boolean = false
    ) {
        cancelPreviewJobs()
        val currentDirs = entries.mapTo(hashSetOf()) { it.dirName }
        previewFilesState.value = previewFilesState.value.filterKeys { it in currentDirs }
        val batch = previewBatch
        val style = shareStyleState.value
        entries.forEach { entry ->
            val job = lifecycleScope.launch {
                val file = try {
                    ShareNoteImageRenderer.renderPreview(
                        context = this@ShareNoteTemplateManageActivity,
                        entry = entry,
                        force = force,
                        style = style
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.put("Share note template preview failed: ${entry.dirName}\n${e.localizedMessage}", e)
                    null
                }
                file?.let {
                    if (batch == previewBatch) {
                        previewFilesState.value = previewFilesState.value + (entry.dirName to it)
                    }
                }
            }
            previewJobs += job
        }
    }

    private fun cancelPreviewJobs() {
        previewBatch += 1
        previewJobs.forEach { it.cancel() }
        previewJobs.clear()
    }

    private fun applyTemplate(entry: ShareNoteTemplateManager.Entry) {
        ShareNoteTemplateManager.apply(entry)
        activeDirNameState.value = entry.dirName
        toastOnUi(R.string.success)
    }

    private fun updateShareStyle(style: ShareNoteTemplateManager.ShareStyle) {
        if (style == shareStyleState.value) return
        ShareNoteTemplateManager.saveStyle(style)
        shareStyleState.value = ShareNoteTemplateManager.currentStyle()
        previewFilesState.value = emptyMap()
        refreshPreviews(entriesState.value, force = true)
    }

    private fun showAddActions() {
        selector(
            getString(R.string.share_note_add_template),
            listOf(
                getString(R.string.share_note_add_copy_builtin),
                getString(R.string.share_note_import_html),
                getString(R.string.share_note_import_zip)
            )
        ) { _, index ->
            when (index) {
                0 -> copyTemplate(ShareNoteTemplateManager.builtinEntry(), editAfterCopy = true)
                1 -> importTemplate.launch {
                    mode = HandleFileContract.FILE
                    title = getString(R.string.share_note_import_html)
                    allowExtensions = arrayOf("html", "htm")
                }
                2 -> importTemplate.launch {
                    mode = HandleFileContract.FILE
                    title = getString(R.string.share_note_import_zip)
                    allowExtensions = arrayOf("zip")
                }
            }
        }
    }

    private fun templateActions(entry: ShareNoteTemplateManager.Entry): List<ShareNoteMenuAction> {
        return buildList {
            add(ShareNoteMenuAction(getString(R.string.share_note_action_preview)) { openPreview(entry) })
            add(ShareNoteMenuAction(getString(R.string.share_note_action_copy)) { copyTemplate(entry, editAfterCopy = true) })
            add(ShareNoteMenuAction(getString(R.string.share_note_export_html)) { exportHtml(entry) })
            add(ShareNoteMenuAction(getString(R.string.share_note_export_zip)) { exportZip(entry) })
            if (entry.source == ShareNoteTemplateManager.Source.LOCAL) {
                add(ShareNoteMenuAction(getString(R.string.delete), danger = true) { confirmDelete(entry) })
            }
        }
    }

    private fun openPreview(entry: ShareNoteTemplateManager.Entry) {
        lifecycleScope.launch {
            val file = try {
                ShareNoteImageRenderer.renderPreview(
                    context = this@ShareNoteTemplateManageActivity,
                    entry = entry,
                    force = true,
                    style = shareStyleState.value
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put("Share note template manual preview failed: ${entry.dirName}\n${e.localizedMessage}", e, true)
                null
            }
            if (file == null) {
                toastOnUi(R.string.error)
            } else {
                previewFilesState.value = previewFilesState.value + (entry.dirName to file)
                toastOnUi(R.string.success)
            }
        }
    }

    private fun copyTemplate(entry: ShareNoteTemplateManager.Entry, editAfterCopy: Boolean) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { ShareNoteTemplateManager.copyToLocal(entry) }
            }.onSuccess {
                loadTemplates()
                if (editAfterCopy) editTemplate(it)
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    private fun editTemplate(entry: ShareNoteTemplateManager.Entry) {
        val editable = if (entry.source == ShareNoteTemplateManager.Source.BUILTIN) {
            copyTemplate(entry, editAfterCopy = true)
            return
        } else {
            entry
        }
        editingEntry = editable
        editTemplateLauncher.launch(Intent(this, CodeEditActivity::class.java).apply {
            putExtra("title", editable.meta.name)
            putExtra("text", ShareNoteTemplateManager.readTemplateHtml(editable))
            putExtra("languageName", "text.html.basic")
        })
    }

    private fun exportHtml(entry: ShareNoteTemplateManager.Entry) {
        exportTemplate.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "${safeFileName(entry.meta.name)}.html",
                ShareNoteTemplateManager.exportHtmlBytes(entry),
                "text/html"
            )
        }
    }

    private fun exportZip(entry: ShareNoteTemplateManager.Entry) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { ShareNoteTemplateManager.exportZip(entry) }
            }.onSuccess { file ->
                exportTemplate.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "${safeFileName(entry.meta.name)}.zip",
                        file,
                        "application/zip"
                    )
                }
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.share_note_export_failed))
            }
        }
    }

    private fun confirmDelete(entry: ShareNoteTemplateManager.Entry) {
        alert(
            title = getString(R.string.delete),
            message = entry.meta.name
        ) {
            okButton {
                ShareNoteTemplateManager.deleteLocal(entry)
                loadTemplates()
            }
            noButton()
        }
    }

    private fun safeFileName(name: String): String {
        return name.trim().ifBlank { "share_note_template" }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}
