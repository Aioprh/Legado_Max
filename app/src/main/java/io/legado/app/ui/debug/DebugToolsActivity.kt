package io.legado.app.ui.debug

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class DebugToolsActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        DebugToolsScreen(onBackClick = { finish() })
    }
}

@Composable
fun DebugToolsContent(
    onBackClick: () -> Unit
) {
    DebugToolsScreen(onBackClick = onBackClick)
}
