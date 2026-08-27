package io.legado.app.ui.file

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.ui.theme.LegadoThemeWithBackground
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent
import io.legado.app.utils.openFileUri
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch

class FileManageActivity : AppCompatActivity() {

    // 根目录为应用外部存储目录的父目录；构造注入便于 JVM 单测替换（testing.md §16）
    private val viewModel: FileManageViewModel by viewModels {
        viewModelFactory {
            initializer { FileManageViewModel(getExternalFilesDir(null)?.parentFile) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        val initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH)
        collectEvents()
        setLegadoContent {
            FileManageScreen(
                viewModel = viewModel,
                initialPath = initialPath,
                onBackClick = { finish() }
            )
        }
    }

    /** 收集 ViewModel 一次性事件并执行平台操作（§4.4） */
    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
when (event) {
                        is FileManageEvent.OpenFile -> openFile(event)
                        is FileManageEvent.OpenWithChooser -> openWithChooser(event)
                        is FileManageEvent.Toast -> toastOnUi(event.message)
                    }
                    }
                }
                launch {
                    viewModel.toasts.collect { toastOnUi(it.message) }
                }
            }
        }
    }

    /** 使用 FileProvider 生成 URI 并调用系统打开（平台操作，§4.1） */
    private fun openFile(event: FileManageEvent.OpenFile) {
        try {
            val uri = FileProvider.getUriForFile(this, AppConst.authority, event.file)
            openFileUri(uri)
        } catch (e: Exception) {
            toastOnUi(e.localizedMessage)
        }
    }

    /** 用选择器打开目录，让用户选哪个应用/文件管理器打开（平台操作，§4.1） */
    private fun openWithChooser(event: FileManageEvent.OpenWithChooser) {
        val dir = event.dir ?: return
        try {
            val uri = FileProvider.getUriForFile(this, AppConst.authority, dir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            toastOnUi(e.stackTraceStr)
            e.printOnDebug()
        }
    }

    companion object {
        const val EXTRA_INITIAL_PATH = "initialPath"
    }
}

@Composable
fun FileManageContent(
    viewModel: FileManageViewModel,
    initialPath: String? = null,
    onBackClick: () -> Unit
) {
    LegadoThemeWithBackground(backgroundDrawable = null) {
        FileManageScreen(
            viewModel = viewModel,
            initialPath = initialPath,
            onBackClick = onBackClick
        )
    }
}
