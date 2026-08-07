package io.legado.app.ui.debug

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class HttpDebugActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        HttpDebugScreen(onBackClick = { finish() })
    }
}

@Composable
fun HttpDebugContent(
    onBackClick: () -> Unit
) {
    HttpDebugScreen(onBackClick = onBackClick)
}
