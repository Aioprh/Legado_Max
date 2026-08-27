package io.legado.app.ui.download

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.service.DownloadState
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch

class DownloadManageActivity : AppCompatActivity() {

    private val viewModel: DownloadManageViewModel by viewModels {
        DownloadManageViewModel.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        collectEvents()
        setLegadoContent {
            DownloadManageScreen(viewModel = viewModel, onBackClick = { finish() })
        }
    }

    /**
     * 收集 ViewModel 一次性事件并执行平台操作（§4.4）
     * 绑定 STARTED 生命周期：页面不可见时停止收集，UNLIMITED 通道中的关键事件可见后补发，
     * CONFLATED 通道中的后台 Toast 自然丢弃
     */
    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.events.collect(::handleEvent) }
                launch { viewModel.toasts.collect { toastOnUi(it.msgRes) } }
            }
        }
    }

    private fun handleEvent(event: DownloadEvent) {
        when (event) {
            is DownloadEvent.OpenFile -> openFile(event.taskId)
            is DownloadEvent.OpenFolder -> openFolder()
            is DownloadEvent.CopyPath -> copyPathToClipboard(event.path)
            is DownloadEvent.Toast -> toastOnUi(event.msgRes)
        }
    }

    private fun openFile(taskId: Long) {
        val task = DownloadState.getTask(taskId) ?: return
        kotlin.runCatching {
            val downloadManager =
                getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.getUriForDownloadedFile(taskId)?.let { uri ->
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(task.fileName.substringAfterLast(".", "")) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            }
        }.onFailure {
            it.printStackTrace()
            toastOnUi(R.string.download_open_file_failed)
        }
    }

    private fun openFolder() {
        kotlin.runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "resource/folder"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.onFailure {
            // 降级：打开系统下载管理器
            kotlin.runCatching {
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                startActivity(intent)
            }.onFailure {
                toastOnUi(R.string.download_open_folder_failed)
            }
        }
    }

    private fun copyPathToClipboard(path: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("file path", path))
    }
}
