package io.legado.app.ui.debug

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class PingTestActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        PingTestScreen(onBackClick = { finish() })
    }
}

@Composable
fun PingTestContent(
    onBackClick: () -> Unit
) {
    PingTestScreen(onBackClick = onBackClick)
}
