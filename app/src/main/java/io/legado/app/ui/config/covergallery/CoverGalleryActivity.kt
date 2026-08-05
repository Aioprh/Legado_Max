package io.legado.app.ui.config.covergallery

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class CoverGalleryActivity : BaseComposeActivity() {

    @Composable
    override fun ComposeContent() {
        CoverGalleryScreen(onBackClick = { finish() })
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CoverGalleryActivity::class.java))
        }
    }
}
