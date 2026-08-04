package io.legado.app.ui.debug

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class EncodeToolsActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        EncodeToolsScreen(onBackClick = { finish() })
    }
}

@Composable
fun EncodeToolsContent(
    onBackClick: () -> Unit
) {
    EncodeToolsScreen(onBackClick = onBackClick)
}
