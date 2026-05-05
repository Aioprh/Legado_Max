/**
 * 正则表达式测试工具 Activity
 * 
 * 采用 Activity + Screen 架构模式：
 * - Activity 负责生命周期管理、主题初始化、系统栏配置等容器工作
 * - Screen 负责所有 UI 渲染和用户交互
 * 
 * 支持从外部传入初始参数：
 * - pattern: 初始正则表达式
 * - replacement: 初始替换文本
 * - isRegex: 是否使用正则模式
 */
package io.legado.app.ui.debug

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 正则表达式测试 Activity
 * 
 * 作为 Compose UI 的容器，负责：
 * - 主题初始化和切换
 * - 系统状态栏和导航栏配置
 * - 背景图片加载
 * - 接收并传递外部参数
 */
class RegexTestActivity : AppCompatActivity() {

    companion object {
        /**
         * 创建启动 Intent
         * 
         * @param context 上下文
         * @param pattern 初始正则表达式，默认为空
         * @param replacement 初始替换文本，默认为空
         * @param isRegex 是否使用正则模式，默认为 true
         * @return 启动 RegexTestActivity 的 Intent
         */
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

    // 背景图片 Drawable，用于自定义主题背景
    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 初始化主题（必须在 super.onCreate 之前调用）
        initTheme()
        super.onCreate(savedInstanceState)
        // 配置系统栏（状态栏、导航栏）
        setupSystemBar()
        // 加载背景图片
        loadBackgroundImage()
        // 启用边到边显示
        enableEdgeToEdge()
        
        // 从 Intent 中获取外部传入的参数
        val pattern = intent.getStringExtra("pattern") ?: ""
        val replacement = intent.getStringExtra("replacement") ?: ""
        val isRegex = intent.getBooleanExtra("isRegex", true)
        
        // 设置 Compose 内容
        setContent {
            RegexTestContent(
                bgDrawable = bgDrawable,
                onBackClick = { finish() },
                initialPattern = pattern,
                initialReplacement = replacement,
                initialIsRegex = isRegex
            )
        }
    }

    /**
     * 加载背景图片
     * 
     * 根据屏幕尺寸从主题配置中获取背景图片，
     * 兼容 Android 11(Android R) 及以上版本的新 API
     */
    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android R+ 使用 WindowMetrics API
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                // Android R 以下使用旧 API
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 初始化主题
     * 
     * 根据主题配置设置对应的 AppTheme，
     * 支持深色、浅色、跟随主色调三种模式
     */
    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }
            else -> {
                // 跟随主色调判断深浅色
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    /**
     * 配置系统栏
     * 
     * 设置状态栏和导航栏的颜色、透明度等属性，
     * 支持透明状态栏和沉浸式导航栏
     */
    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        setLightStatusBar(ColorUtils.isColorLight(backgroundColor))
        // 根据配置设置导航栏
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

/**
 * 正则测试内容 Composable
 * 
 * 负责构建 Material3 主题并渲染 RegexTestScreen，
 * 包含完整的颜色方案配置
 * 
 * @param bgDrawable 背景图片 Drawable
 * @param onBackClick 返回按钮点击回调
 * @param initialPattern 初始正则表达式
 * @param initialReplacement 初始替换文本
 * @param initialIsRegex 初始是否使用正则模式
 */
@Composable
fun RegexTestContent(
    bgDrawable: Drawable?,
    onBackClick: () -> Unit,
    initialPattern: String = "",
    initialReplacement: String = "",
    initialIsRegex: Boolean = true
) {
    val context = LocalContext.current

    // 从 ThemeStore 获取主题颜色值
    val primaryColorValue = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) }

    // 判断当前是否为浅色主题
    val isLight = ColorUtils.isColorLight(bgColor)
    
    // 将 Int 颜色值转换为 Compose Color
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(primaryColorValue) { Color(primaryColorValue) }
    val secondary = remember(accentColor) { Color(accentColor) }
    val onBackground = remember(textPrimaryColor) { Color(textPrimaryColor) }
    val onBackgroundVariant = remember(textSecondaryColor) { Color(textSecondaryColor) }
    
    // 计算表面颜色（卡片背景）
    val surface = remember(background, isLight) {
        lerp(background, Color.White, if (isLight) 0.04f else 0.10f)
    }
    
    // 计算表面变体颜色（输入框背景等）
    val surfaceVariant = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    }
    
    // 计算轮廓颜色（边框、分割线）
    val outline = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    }
    
    // 计算页面主色调（深色模式下略微提亮）
    val pagePrimary = remember(primary, isLight) {
        if (isLight) primary else lerp(primary, Color.White, 0.20f)
    }
    
    // 计算页面次要文字颜色
    val pageOnBackgroundVariant = remember(onBackgroundVariant, onBackground, isLight) {
        if (isLight) onBackgroundVariant else lerp(onBackgroundVariant, onBackground, 0.32f)
    }
    
    // 计算页面表面变体颜色
    val pageSurfaceVariant = remember(surfaceVariant, onBackground, isLight) {
        if (isLight) surfaceVariant else lerp(surfaceVariant, onBackground, 0.08f)
    }

    // 构建 Material3 颜色方案
    val colorScheme = remember(
        isLight,
        pagePrimary,
        secondary,
        background,
        onBackground,
        pageOnBackgroundVariant,
        surface,
        pageSurfaceVariant,
        outline
    ) {
        if (isLight) {
            // 浅色主题颜色方案
            lightColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.75f),
                onPrimary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFE53935),
                onError = Color.White
            )
        } else {
            // 深色主题颜色方案
            darkColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.8f),
                onPrimary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFFF5252),
                onError = Color.Black
            )
        }
    }

    // 应用 Material3 主题并渲染内容
    MaterialTheme(colorScheme = colorScheme) {
        RegexTestBoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            RegexTestScreen(
                onBackClick = onBackClick,
                initialPattern = initialPattern,
                initialReplacement = initialReplacement,
                initialIsRegex = initialIsRegex
            )
        }
    }
}

/**
 * 带背景的容器 Composable
 * 
 * 支持显示自定义背景图片，并在其上叠加半透明遮罩，
 * 确保前景内容清晰可见
 * 
 * @param bgDrawable 背景图片 Drawable，为 null 时显示纯色背景
 * @param bgColor 背景颜色
 * @param content 前景内容
 */
@Composable
fun RegexTestBoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            // 有背景图片时：图片 + 半透明遮罩
            // 根据背景亮度计算遮罩透明度
            val overlayAlpha = if (bgColor.luminance() > 0.5f) 0.22f else 0.40f
            
            // 渲染背景图片
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 叠加半透明遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = overlayAlpha))
            )
        } else {
            // 无背景图片时：纯色背景
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        // 渲染前景内容
        content()
    }
}
