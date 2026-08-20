package io.legado.app.ui.book.storage

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class StorageManageActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        StorageManageScreen(onBackClick = { finish() })
    }
}

@Composable
fun StorageManageContent(
    onBackClick: () -> Unit
) {
    StorageManageScreen(onBackClick = onBackClick)
}
