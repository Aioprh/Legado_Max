package io.legado.app.ui.video.config

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import io.legado.app.base.BaseDialogFragment
import io.legado.app.ui.theme.LegadoTheme

class SettingsDialog(private val context: Context, private val callBack: CallBack? = null) :
    BaseDialogFragment(0) {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    VideoSettingsContent(
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // Compose内容已在onCreateView中设置，无需额外初始化
    }

    interface CallBack {
        // 可扩展的回调接口
    }
}