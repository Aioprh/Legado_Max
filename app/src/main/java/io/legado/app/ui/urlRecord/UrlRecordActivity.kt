package io.legado.app.ui.urlRecord

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint
import io.legado.app.di.AppVersionInfo
import io.legado.app.ui.theme.LegadoThemeWithBackground
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent
import javax.inject.Inject

@AndroidEntryPoint
class UrlRecordActivity : AppCompatActivity() {

    @Inject
    lateinit var appVersionInfo: AppVersionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        // 示例：打印注入的应用版本信息
        android.util.Log.d("HiltDemo", "AppVersion: ${appVersionInfo.displayVersion}, channel: ${appVersionInfo.channel}")
        setLegadoContent {
            UrlRecordScreen(onBackClick = { finish() })
        }
    }
}

@Composable
fun UrlRecordContent(
    onBackClick: () -> Unit
) {
    LegadoThemeWithBackground(backgroundDrawable = null) {
        UrlRecordScreen(onBackClick = onBackClick)
    }
}
