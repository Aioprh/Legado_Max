package io.legado.app.ui.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class RegexTestActivity : BaseComposeActivity() {

    private var pattern: String = ""
    private var replacement: String = ""
    private var isRegex: Boolean = true

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        pattern = intent.getStringExtra("pattern") ?: ""
        replacement = intent.getStringExtra("replacement") ?: ""
        isRegex = intent.getBooleanExtra("isRegex", true)
    }

    @Composable
    override fun ComposeContent() {
        RegexTestScreen(
            onBackClick = { finish() },
            initialPattern = pattern,
            initialReplacement = replacement,
            initialIsRegex = isRegex
        )
    }

    companion object {
        fun startIntent(
            context: Context,
            pattern: String = "",
            replacement: String = "",
            isRegex: Boolean = true
        ): Intent {
            return Intent(context, RegexTestActivity::class.java).apply {
                putExtra("pattern", pattern)
                putExtra("replacement", replacement)
                putExtra("isRegex", isRegex)
            }
        }
    }
}

@Composable
fun RegexTestContent(
    onBackClick: () -> Unit,
    initialPattern: String = "",
    initialReplacement: String = "",
    initialIsRegex: Boolean = true
) {
    RegexTestScreen(
        onBackClick = onBackClick,
        initialPattern = initialPattern,
        initialReplacement = initialReplacement,
        initialIsRegex = initialIsRegex
    )
}
