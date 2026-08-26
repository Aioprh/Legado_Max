package io.legado.app.ui.book.storage

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.utils.startActivity

class StorageManageActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        StorageManageScreen(
            onBackClick = { finish() },
            onOpenPath = { path ->
                startActivity<FileManageActivity> {
                    putExtra(FileManageActivity.EXTRA_INITIAL_PATH, path)
                }
            }
        )
    }
}

@Composable
fun StorageManageContent(
    onBackClick: () -> Unit,
    onOpenPath: (String) -> Unit = {}
) {
    StorageManageScreen(onBackClick = onBackClick, onOpenPath = onOpenPath)
}
