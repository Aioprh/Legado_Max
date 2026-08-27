package io.legado.app.ui.book.storage

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.utils.startActivity

class StorageManageActivity : BaseComposeActivity() {

    private val viewModel: StorageManageViewModel by viewModels {
        StorageManageViewModel.Factory
    }

    @Composable
    override fun ComposeContent() {
        StorageManageScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onOpenPath = { path ->
                startActivity<FileManageActivity> {
                    putExtra(FileManageActivity.EXTRA_INITIAL_PATH, path)
                }
            }
        )
    }
}
