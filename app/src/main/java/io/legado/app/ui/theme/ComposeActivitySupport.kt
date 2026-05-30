package io.legado.app.ui.theme

import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.config.ReadAloudActivity
import io.legado.app.ui.debuglog.DebugFloatingBallManager
import io.legado.app.ui.debuglog.DebugLogPanelDialog
import io.legado.app.ui.widget.ReadAloudMiniBarController
import io.legado.app.ui.widget.ReadAloudMiniBarHost
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.fullScreen
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.startActivity

fun ComponentActivity.initLegadoComposeTheme() {
    when (ThemeConfig.getTheme()) {
        Theme.Dark -> setTheme(R.style.AppTheme_Dark)
        Theme.Light -> setTheme(R.style.AppTheme_Light)
        else -> {
            if (ColorUtils.isColorLight(primaryColor)) {
                setTheme(R.style.AppTheme_Light)
            } else {
                setTheme(R.style.AppTheme_Dark)
            }
        }
    }
}

fun ComponentActivity.setupLegadoComposeSystemBar() {
    fullScreen()
    val isTransparentStatusBar = AppConfig.isTransparentStatusBar
    val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
    setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
    if (AppConfig.immNavigationBar) {
        setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
    } else {
        setNavigationBarColorAuto(ColorUtils.darkenColor(ThemeStore.navigationBarColor(this)))
    }
}

fun ComponentActivity.loadLegadoBackgroundDrawable(): Drawable? {
    return try {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
        }
        ThemeConfig.getBgImage(this, metrics)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun LegadoBackgroundBox(
    backgroundDrawable: Drawable?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    overlayAlpha: Float? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundDrawable != null) {
            val resolvedOverlayAlpha = overlayAlpha
                ?: if (backgroundColor.luminance() > 0.5f) 0.10f else 0.18f
            Image(
                bitmap = backgroundDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor.copy(alpha = resolvedOverlayAlpha))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            )
        }
        content()
    }
}

@Composable
fun LegadoThemeWithBackground(
    backgroundDrawable: Drawable?,
    overlayAlpha: Float? = null,
    content: @Composable () -> Unit
) {
    LegadoTheme {
        LegadoBackgroundBox(
            backgroundDrawable = backgroundDrawable,
            overlayAlpha = overlayAlpha
        ) {
            content()
        }
    }
}

fun ComponentActivity.setLegadoContent(
    overlayAlpha: Float? = null,
    content: @Composable () -> Unit
) {
    setupLegadoComposeSystemBar()
    val backgroundDrawable = loadLegadoBackgroundDrawable()
    enableEdgeToEdge()
    setContent {
        LegadoThemeWithBackground(
            backgroundDrawable = backgroundDrawable,
            overlayAlpha = overlayAlpha
        ) {
            content()
        }
    }
    (this as? AppCompatActivity)?.installComposeGlobalUi()
}

private fun AppCompatActivity.installComposeGlobalUi() {
    val controller = ComposeGlobalUiController(this)
    controller.attach()
    lifecycle.addObserver(controller)
}

private class ComposeGlobalUiController(
    private val activity: AppCompatActivity
) : DefaultLifecycleObserver {

    private var readAloudMiniBarController: ReadAloudMiniBarController? = null
    private val readAloudMiniBarHost = ComposeReadAloudMiniBarHost(activity)

    fun attach() {
        activity.findViewById<ViewGroup>(android.R.id.content)?.let { parent ->
            readAloudMiniBarController = ReadAloudMiniBarController(
                activity = activity,
                host = readAloudMiniBarHost,
                parent = parent
            )
        }
        activity.observeEvent<Int>(EventBus.ALOUD_STATE) {
            readAloudMiniBarController?.refresh()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        DebugFloatingBallManager.onActivityResumed(activity)
        readAloudMiniBarController?.refresh()
    }

    override fun onPause(owner: LifecycleOwner) {
        readAloudMiniBarController?.onPause()
        DebugFloatingBallManager.onActivityPaused(activity)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        DebugFloatingBallManager.onActivityDestroyed(activity)
        DebugLogPanelDialog.onActivityDestroyed(activity)
    }
}

private class ComposeReadAloudMiniBarHost(
    private val activity: AppCompatActivity
) : ReadAloudMiniBarHost {

    override fun showReadAloudMiniBar(): Boolean = AppConfig.readAloudFloatingUi

    override fun lockReadAloudMiniBarPosition(): Boolean = false

    override fun readAloudMiniBarBottomMarginDp(): Int = 76

    override fun defaultReadAloudMiniBarColor(): Int = 0xFF665185.toInt()

    override fun onReadAloudMiniBarClick() {
        BaseReadAloudService.activeBookUrl?.let { bookUrl ->
            activity.startActivity<ReadBookActivity> {
                putExtra("bookUrl", bookUrl)
            }
        } ?: ReadBook.book?.let { book ->
            activity.startActivity<ReadBookActivity> {
                putExtra("bookUrl", book.bookUrl)
            }
        } ?: activity.startActivity<ReadAloudActivity>()
    }

    override fun onReadAloudMiniBarLongClick(): Boolean = false
}
