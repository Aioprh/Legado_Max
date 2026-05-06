package io.legado.app.ui.upload

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

class DirectLinkUploadActivity : AppCompatActivity() {

    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        setContent {
            DirectLinkUploadContent(
                bgDrawable = bgDrawable,
                onBackClick = { finish() }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initTheme() {
        ThemeConfig.applyTheme(this)
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }
            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        val bgColor = ThemeStore.backgroundColor(this)
        val isNightTheme = AppConfig.isNightTheme
        val isLight = !isNightTheme && ColorUtils.isColorLight(bgColor)
        setLightStatusBar(isLight)
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

@Composable
fun DirectLinkUploadContent(
    bgDrawable: Drawable?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val isNightTheme = AppConfig.isNightTheme
    val primaryColorValue = ThemeStore.primaryColor(context)
    val accentColor = ThemeStore.accentColor(context)
    val bgColor = ThemeStore.backgroundColor(context)
    val textPrimaryColor = ThemeStore.textColorPrimary(context)
    val textSecondaryColor = ThemeStore.textColorSecondary(context)

    val isLight = !isNightTheme && ColorUtils.isColorLight(bgColor)
    val background = Color(bgColor)
    val primary = Color(accentColor)  // 使用强调色作为主色调
    val secondary = Color(primaryColorValue)  // 使用主色调作为次要颜色
    val onBackground = Color(textPrimaryColor)
    val onBackgroundVariant = Color(textSecondaryColor)
    
    val surface = lerp(background, if (isLight) Color.White else Color.Black, if (isLight) 0.04f else 0.10f)
    val surfaceVariant = lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    val outline = lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    val onSurfaceVariant = lerp(onBackground, if (isLight) Color.Black else Color.White, if (isLight) 0.2f else 0.2f)

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = secondary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            secondaryContainer = surfaceVariant,
            tertiaryContainer = surfaceVariant,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.75f),
            onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
            onSecondary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
            onBackground = onBackground,
            onSurface = onBackground,
            onSurfaceVariant = onSurfaceVariant,
            error = Color(0xFFE53935),
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = secondary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            secondaryContainer = surfaceVariant,
            tertiaryContainer = surfaceVariant,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.8f),
            onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
            onSecondary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
            onBackground = onBackground,
            onSurface = onBackground,
            onSurfaceVariant = onSurfaceVariant,
            error = Color(0xFFFF5252),
            onError = Color.Black
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        DirectLinkUploadBoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            DirectLinkUploadScreen(onBackClick = onBackClick)
        }
    }
}

@Composable
fun DirectLinkUploadBoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        content()
    }
}
