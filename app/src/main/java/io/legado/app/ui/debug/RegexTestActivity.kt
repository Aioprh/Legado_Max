package io.legado.app.ui.debug

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

class RegexTestActivity : AppCompatActivity() {

    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        setContent {
            RegexTestContent(
                bgDrawable = bgDrawable,
                onBackClick = { finish() }
            )
        }
    }

    private fun loadBackgroundImage() {
        try {
            bgDrawable = ThemeConfig.getBgImage(this, windowManager.defaultDisplay.run {
                android.util.DisplayMetrics().apply { getMetrics(this) }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        setLightStatusBar(ColorUtils.isColorLight(backgroundColor))
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

@Composable
fun RegexTestContent(
    bgDrawable: Drawable?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val primaryColorValue = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) }

    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(primaryColorValue) { Color(primaryColorValue) }
    val secondary = remember(accentColor) { Color(accentColor) }
    val onBackground = remember(textPrimaryColor) { Color(textPrimaryColor) }
    val onBackgroundVariant = remember(textSecondaryColor) { Color(textSecondaryColor) }
    
    val surface = remember(background, isLight) {
        lerp(background, Color.White, if (isLight) 0.04f else 0.10f)
    }
    
    val surfaceVariant = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    }
    
    val outline = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    }
    
    val pagePrimary = remember(primary, isLight) {
        if (isLight) primary else lerp(primary, Color.White, 0.20f)
    }
    
    val pageOnBackgroundVariant = remember(onBackgroundVariant, onBackground, isLight) {
        if (isLight) onBackgroundVariant else lerp(onBackgroundVariant, onBackground, 0.32f)
    }
    
    val pageSurfaceVariant = remember(surfaceVariant, onBackground, isLight) {
        if (isLight) surfaceVariant else lerp(surfaceVariant, onBackground, 0.08f)
    }

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

    MaterialTheme(colorScheme = colorScheme) {
        RegexTestBoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            RegexTestScreen(onBackClick = onBackClick)
        }
    }
}

@Composable
fun RegexTestBoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            val overlayAlpha = if (bgColor.luminance() > 0.5f) 0.22f else 0.40f
            
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = overlayAlpha))
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        content()
    }
}
