package io.legado.app.ui.video.config

import android.content.Context
import androidx.compose.runtime.Composable
import io.legado.app.ui.widget.components.dialog.BaseComposeDialogFragment

class SettingsDialog(private val context: Context, private val callBack: CallBack? = null) :
    BaseComposeDialogFragment() {

    @Composable
    override fun DialogContent() {
        VideoSettingsContent(
            onDismiss = { dismiss() }
        )
    }

    interface CallBack {// 可扩展的回调接口
    }
}
