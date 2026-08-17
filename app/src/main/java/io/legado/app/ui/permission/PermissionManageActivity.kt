package io.legado.app.ui.permission

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class PermissionManageActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        PermissionManageScreen(onBackClick = { finish() })
    }
}
