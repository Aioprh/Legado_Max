/**
 * Legado Compose Activity 支持类。
 *
 * 提供一系列扩展函数和 Compose 组件，用于统一处理 Compose 页面的主题初始化、
 * 系统栏设置、背景图加载等功能。
 *
 * ## 主要功能
 * - [initLegadoComposeTheme]：根据全局配置设置 XML theme（用于 View 系统兼容）
 * - [setupLegadoComposeSystemBar]：配置状态栏和导航栏
 * - [loadLegadoBackgroundDrawable]：加载主题背景图
 * - [LegadoThemeWithBackground]：包裹主题和背景的 Compose 组件
 * - [setLegadoContent]：一站式设置 Compose 内容（含主题、背景、系统栏）
 *
 * ## 使用方式
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         initLegadoComposeTheme()
 *         super.onCreate(savedInstanceState)
 *         setLegadoContent {
 *             MyComposableContent()
 *         }
 *     }
 * }
 * ```
 *
 * @see BaseComposeActivity 已封装好的 Compose Activity 基类
 */
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 初始化 Compose Activity 的 XML 主题。
 *
 * 根据 [ThemeConfig.getTheme] 的结果设置对应的 XML style（暗色/亮色），
 * 确保 View 系统组件（如 AlertDialog）也能跟随主题。
 *
 * **注意**：此方法必须在 `super.onCreate()` 之前调用。
 */
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

/**
 * 配置 Compose Activity 的系统栏（状态栏 + 导航栏）。
 *
 * - 全屏模式
 * - 设置状态栏颜色和透明度
 * - 设置导航栏颜色（支持沉浸模式）
 */
fun ComponentActivity.setupLegadoComposeSystemBar() {
    fullScreen()
    val isTransparentStatusBar = AppConfig.isTransparentStatusBar
    val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
    setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
    if (AppConfig.immNavigationBar) {
        setNavigationBarColorAuto(ThemeStore.navigationBarColor(this), transparent = true)
    } else {
        setNavigationBarColorAuto(ColorUtils.darkenColor(ThemeStore.navigationBarColor(this)))
    }
}

/**
 * 加载当前主题的背景图 Drawable。
 *
 * 根据屏幕尺寸解码适当大小的背景图，避免内存浪费。
 *
 * @return 背景图 Drawable，如果未配置或加载失败则返回 null
 */
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

/**
 * 背景图容器组件。
 *
 * 在内容层下方渲染主题背景图，支持叠加半透明遮罩层。
 * 如果没有背景图，则使用纯色背景。
 *
 * @param backgroundDrawable 背景图可绘制对象
 * @param modifier 修饰符
 * @param backgroundColor 背景颜色
 * @param overlayAlpha 遮罩层透明度，null 则根据背景亮度自动计算
 * @param content 子组件内容
 */
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

/**
 * 带背景的 Legado 主题组件。
 *
 * 组合 [LegadoTheme] 和 [LegadoBackgroundBox]，提供完整的主题 + 背景图容器。
 *
 * @param backgroundDrawable 背景图可绘制对象
 * @param overlayAlpha 遮罩层透明度
 * @param content 子组件内容
 */
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

/**
 * 一站式设置 Compose Activity 的内容。
 *
 * 封装了以下操作：
 * 1. 配置系统栏（状态栏 + 导航栏）
 * 2. 启用 Edge-to-Edge 模式
 * 3. 异步加载背景图
 * 4. 包裹 [LegadoThemeWithBackground] 主题容器
 * 5. 安装全局 UI 组件（朗读迷你栏、调试悬浮球）
 *
 * @param overlayAlpha 背景遮罩层透明度
 * @param content Compose 内容
 */
fun ComponentActivity.setLegadoContent(
    overlayAlpha: Float? = null,
    content: @Composable () -> Unit
) {
    setupLegadoComposeSystemBar()
    val bgState: MutableState<Drawable?> = mutableStateOf(null)
    enableEdgeToEdge()
    setContent {
        LegadoThemeWithBackground(
            backgroundDrawable = bgState.value,
            overlayAlpha = overlayAlpha
        ) {
            content()
        }
    }
    (this as? AppCompatActivity)?.installComposeGlobalUi()
    lifecycleScope.launch(Dispatchers.Default) {
        val drawable = loadLegadoBackgroundDrawable()
        launch(Dispatchers.Main) { bgState.value = drawable }
    }
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
