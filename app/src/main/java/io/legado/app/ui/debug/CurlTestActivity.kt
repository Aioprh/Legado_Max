package io.legado.app.ui.debug

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class CurlTestActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        CurlTestScreen(onBackClick = { finish() })
    }
}

@Composable
fun CurlTestContent(
    onBackClick: () -> Unit
) {
    CurlTestScreen(onBackClick = onBackClick)
}
