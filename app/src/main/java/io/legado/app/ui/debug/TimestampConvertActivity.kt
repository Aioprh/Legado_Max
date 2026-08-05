package io.legado.app.ui.debug

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class TimestampConvertActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        TimestampConvertScreen(onBackClick = { finish() })
    }
}

@Composable
fun TimestampConvertContent(
    onBackClick: () -> Unit
) {
    TimestampConvertScreen(onBackClick = onBackClick)
}
