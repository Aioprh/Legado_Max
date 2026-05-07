/**
 * URL访问记录界面Activity
 * 
 * 这是一个使用Jetpack Compose构建的Activity。
 * 与传统View系统不同，Compose使用Kotlin代码来定义UI，
 * 而不是XML布局文件。
 * 
 * Compose Activity的核心结构：
 * 1. 继承AppCompatActivity（也可以用ComponentActivity）
 * 2. 在onCreate中调用setContent { } 来设置Compose UI
 * 3. 在setContent中调用@Composable函数来构建界面
 * 
 * 本Activity的主要职责：
 * - 初始化主题和系统栏
 * - 加载背景图片
 * - 设置Compose内容
 * - 配置Material3主题颜色
 */
package io.legado.app.ui.urlRecord

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
 * URL记录Activity
 * 
 * 继承AppCompatActivity，使用Compose构建UI。
 * 
 * Compose Activity的生命周期：
 * - onCreate: 初始化主题、系统栏、背景图片，然后设置Compose内容
 * - setContent: 类似于setContentView，但用于Compose
 * - Compose会自动处理配置变更（如屏幕旋转）
 */
class UrlRecordActivity : AppCompatActivity() {

    /**
     * 背景图片Drawable
     * 
     * 在onCreate中加载，传递给Compose进行渲染。
     * 使用Drawable而不是Bitmap是为了延迟转换，节省内存。
     */
    private var bgDrawable: Drawable? = null

    /**
     * Activity创建时调用
     * 
     * 初始化顺序很重要：
     * 1. initTheme() - 必须在super.onCreate之前，设置Activity主题
     * 2. super.onCreate - 调用父类初始化
     * 3. setupSystemBar() - 设置状态栏和导航栏
     * 4. loadBackgroundImage() - 加载背景图片
     * 5. enableEdgeToEdge() - 启用边到边显示
     * 6. setContent { } - 设置Compose内容
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        // setContent 是Compose的入口点
        // 类似于View系统的setContentView(R.layout.xxx)
        // 在这个lambda中调用@Composable函数来构建UI
        setContent {
            UrlRecordContent(
                bgDrawable = bgDrawable,
                onBackClick = { finish() }
            )
        }
    }

    /**
     * 加载背景图片
     * 
     * 从ThemeConfig获取用户设置的背景图片。
     * 使用DisplayMetrics获取屏幕尺寸，用于图片缩放。
     */
    private fun loadBackgroundImage() {
        try {
            bgDrawable = ThemeConfig.getBgImage(this, windowManager.defaultDisplay.run {
                android.util.DisplayMetrics().apply { getMetrics(this) }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 初始化主题
     * 
     * 根据用户设置选择亮色或暗色主题。
     * 必须在super.onCreate之前调用，否则主题不会生效。
     * 
     * 主题选择逻辑：
     * 1. 如果用户明确选择了Dark/Light主题，使用对应主题
     * 2. 如果是跟随系统，根据primaryColor亮度判断
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
                // 跟随系统或自动判断
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    /**
     * 设置系统栏（状态栏和导航栏）
     * 
     * 包括：
     * - 全屏模式
     * - 状态栏颜色和透明度
     * - 状态栏图标颜色（亮/暗）
     * - 导航栏颜色
     */
    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        setLightStatusBar(ColorUtils.isColorLight(backgroundColor))
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

/**
 * URL记录界面的Compose内容
 * 
 * 这个函数负责：
 * 1. 从ThemeStore获取应用的主题颜色
 * 2. 根据亮度计算Material3的ColorScheme
 * 3. 用MaterialTheme包裹主界面
 * 
 * @Composable 注解表示这是一个可组合函数
 * 可组合函数是Compose的基本构建块
 * 
 * @param bgDrawable 背景图片Drawable
 * @param onBackClick 返回按钮点击回调
 */
@Composable
fun UrlRecordContent(
    bgDrawable: Drawable?,
    onBackClick: () -> Unit
) {
    // LocalContext.current 获取当前的Context
    // 类似于View系统中的context
    val context = LocalContext.current

    // ==================== 从ThemeStore获取主题颜色 ====================
    // remember 缓存计算结果，避免每次重组都重新获取
    // 这些颜色值来自应用的主题设置
    
    val primaryColor = remember { ThemeStore.primaryColor(context) }       // 主色
    val accentColor = remember { ThemeStore.accentColor(context) }        // 强调色
    val bgColor = remember { ThemeStore.backgroundColor(context) }        // 背景色
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }     // 主文字色
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) } // 次文字色

    // ==================== 计算Compose颜色 ====================
    // 将Int颜色转换为Compose的Color对象
    // 根据亮度(isLight)计算不同的颜色值
    
    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(accentColor) { Color(accentColor) }
    val secondary = remember(primaryColor) { Color(primaryColor) }
    val onBackground = remember(textPrimaryColor) { Color(textPrimaryColor) }
    val onBackgroundVariant = remember(textSecondaryColor) { Color(textSecondaryColor) }
    
    // surface颜色：用于卡片、对话框等的背景
    // 使用lerp在background和White之间插值
    val surface = remember(background, isLight) {
        lerp(background, Color.White, if (isLight) 0.04f else 0.10f)
    }
    
    // surfaceVariant：用于输入框、列表项等的背景
    val surfaceVariant = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    }
    
    // outline：用于边框、分割线
    val outline = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    }
    
    // pagePrimary：页面主色，暗色模式下稍微提亮
    val pagePrimary = remember(primary, isLight) {
        if (isLight) primary else lerp(primary, Color.White, 0.20f)
    }
    
    // pageOnBackgroundVariant：页面次文字色
    val pageOnBackgroundVariant = remember(onBackgroundVariant, onBackground, isLight) {
        if (isLight) onBackgroundVariant else lerp(onBackgroundVariant, onBackground, 0.32f)
    }
    
    // pageSurfaceVariant：页面表面变体色
    val pageSurfaceVariant = remember(surfaceVariant, onBackground, isLight) {
        if (isLight) surfaceVariant else lerp(surfaceVariant, onBackground, 0.08f)
    }

    // ==================== 创建Material3 ColorScheme ====================
    // ColorScheme定义了Material3主题的所有颜色
    // 根据isLight选择lightColorScheme或darkColorScheme
    
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
            // 亮色主题
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
                // onPrimary是主色上的文字颜色
                // 如果主色是亮色，文字用黑色；否则用白色
                onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFE53935),  // 错误色：红色
                onError = Color.White
            )
        } else {
            // 暗色主题
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
                onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFFF5252),  // 暗色主题的错误色稍亮
                onError = Color.Black
            )
        }
    }

    // ==================== 应用主题并渲染界面 ====================
    // MaterialTheme是Compose的主题容器
    // 它会为所有子组件提供colorScheme、typography、shapes
    MaterialTheme(colorScheme = colorScheme) {
        // 渲染背景和主界面
        BoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            // UrlRecordScreen是主界面组件
            UrlRecordScreen(onBackClick = onBackClick)
        }
    }
}

/**
 * 带背景的Box容器
 * 
 * 这个组件负责：
 * 1. 渲染背景图片（如果有）
 * 2. 添加半透明遮罩层
 * 3. 渲染内容
 * 
 * @param bgDrawable 背景图片Drawable，可以为null
 * @param bgColor 背景颜色
 * @param content 子内容，是一个@Composable lambda
 */
@Composable
fun BoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    // Box是叠加布局，子元素可以叠加显示
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            // 有背景图片时：
            // 1. 计算遮罩透明度（亮色主题更透明，暗色主题更不透明）
            val overlayAlpha = if (bgColor.luminance() > 0.5f) 0.22f else 0.40f
            
            // 2. 渲染背景图片
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,  // 装饰性图片，不需要无障碍描述
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop  // 裁剪以填满屏幕
            )
            
            // 3. 添加半透明遮罩层，让内容更清晰
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = overlayAlpha))
            )
        } else {
            // 没有背景图片时，直接使用背景色
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        // 渲染子内容（主界面）
        // content()调用传入的@Composable lambda
        content()
    }
}
