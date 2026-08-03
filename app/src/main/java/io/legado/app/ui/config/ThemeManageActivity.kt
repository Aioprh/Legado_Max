package io.legado.app.ui.config

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import io.legado.app.R
import io.legado.app.ui.config.theme.ThemeManageScreen
import io.legado.app.ui.config.theme.ThemeManageViewModel
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi

class ThemeManageActivity : AppCompatActivity() {

    private lateinit var viewModel: ThemeManageViewModel

    private val selectImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            RealPathUtil.getPath(this, uri)?.let { path ->
                viewModel.onBackgroundImageSelected(path)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ThemeManageViewModel::class.java]
        setLegadoContent {
            ThemeManageScreen(
                viewModel = viewModel,
                onBackClick = { finish() },
                onImportFromClipboard = { toastOnUi(R.string.import_success) },
                onImportEmpty = { toastOnUi(R.string.clipboard_empty) },
                onImportFailed = { toastOnUi(R.string.import_failed) },
                onSelectImage = { selectImage.launch { mode = HandleFileContract.IMAGE } },
                onShareJson = { json -> share(json) },
                onRecreate = { recreate() },
                onDeleteConfirm = {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.delete)
                        .setMessage(R.string.sure_del)
                        .setPositiveButton(R.string.yes) { _, _ -> viewModel.executeDeleteSelected() }
                        .setNegativeButton(R.string.no, null)
                        .show()
                },
                onToast = { toastOnUi(it) },
                onToastMsg = { toastOnUi(it) }
            )
        }
    }
}