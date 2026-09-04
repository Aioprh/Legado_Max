package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.DevicePerformanceUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.dpToPx
import java.io.File

/**
 * 顶栏导航。
 *
 * 顶栏导航与底栏共用同一套导航配置，并且共用底栏的材质体系：
 * 实色 / 磨砂 / 液态玻璃、透明度、边框颜色以及自定义图标。
 * 这样切换导航位置后，视觉效果不会出现“底栏有玻璃、顶栏只有一块普通色块”的割裂感。
 */
class TopNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val prefs = context.defaultSharedPreferences
    private var basePaddingTop = 0
    private var contentBasePaddingTop = 0
    private var attached = false

    private val glassView = StableLiquidGlassView(context)
    private val shellOverlay = View(context)
    private val navigationRow = LinearLayout(context)

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (attached) post { refresh() }
    }

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        elevation = dp(3).toFloat()

        glassView.apply {
            id = R.id.top_navigation_glass_view
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
        }
        addView(glassView, LayoutParams(MATCH_PARENT, MATCH_PARENT))

        shellOverlay.apply {
            id = R.id.top_navigation_shell_overlay
            isClickable = false
            isFocusable = false
        }
        addView(shellOverlay, LayoutParams(MATCH_PARENT, MATCH_PARENT))

        navigationRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            clipChildren = false
            clipToPadding = false
        }
        addView(navigationRow, LayoutParams(MATCH_PARENT, MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            setPadding(0, basePaddingTop + statusBarTop, 0, 0)
            post { adjustContentPadding() }
            insets
        }
        visibility = View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        basePaddingTop = paddingTop
        contentBasePaddingTop = findContentContainer()?.paddingTop ?: 0
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        post { refresh() }
    }

    override fun onDetachedFromWindow() {
        attached = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        glassView.release()
        restoreMainNavigation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) post { adjustContentPadding() }
    }

    private fun refresh() {
        val night = AppConfig.isNightTheme
        val entry = TopBarConfig.currentEntry(context, night)
        val config = entry.config

        if (!config.navigationEnabled) {
            visibility = View.GONE
            glassView.release()
            restoreMainNavigation()
            return
        }

        visibility = View.VISIBLE
        bringToFront()
        buildItems(entry)
        applyMaterial(config)
        hideBottomNavigation()
        adjustContentPadding()
    }

    private fun buildItems(entry: TopBarConfig.Entry) {
        navigationRow.removeAllViews()

        val config = entry.config
        val bottomConfig = NavigationBarConfig.activeConfig(context, config.isNightMode)
        val mergedIcons = bottomConfig.icons.toMutableMap()
        config.navigationIcons.forEach { (key, path) ->
            val file = File(path)
            mergedIcons[key] = if (file.isAbsolute) file.absolutePath
            else File(entry.localDir ?: File("."), path).absolutePath
        }
        val previewConfig = bottomConfig.copy(icons = mergedIcons)
        val selectedId = findBottomNavigation()?.selectedItemId
            ?: NavigationBarConfig.items.first().menuId

        NavigationBarConfig.items.forEach { item ->
            navigationRow.addView(createItem(previewConfig, item, item.menuId == selectedId))
        }
    }

    private fun createItem(
        config: NavigationBarConfig,
        item: NavigationBarConfig.NavItem,
        selected: Boolean
    ): View {
        return FrameItem(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
            background = if (selected) selectedBackground() else null

            val icon = ImageView(context).apply {
                layoutParams = LayoutParams(dp(30), dp(30))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageDrawable(
                    NavigationBarConfig.previewDrawable(
                        context,
                        config,
                        item,
                        selected
                    )
                )
                contentDescription = context.getString(item.titleRes)
            }
            addView(icon)

            setOnClickListener {
                findBottomNavigation()?.selectedItemId = item.menuId
                post { refresh() }
            }
        }
    }

    private class FrameItem(context: Context) : androidx.appcompat.widget.AppCompatFrameLayout(context) {
        init {
            gravity = Gravity.CENTER
            setPadding(6, 0, 6, 0)
        }
    }

    /**
     * 直接复用底栏的效果模式和参数思路。
     * 液态玻璃：实时采样 + 折射；磨砂：更强模糊 + 更低色散；实色：静态背景。
     */
    private fun applyMaterial(config: TopBarConfig.Config) {
        val navConfig = NavigationBarConfig.activeConfig(context, config.isNightMode)
        val baseColor = resolveBaseColor(navConfig)
        val opacity = navConfig.opacity.coerceIn(0, 100) / 100f
        val standard = navConfig.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val wantsLiquid = !standard && navConfig.effectMode != NavigationBarConfig.EFFECT_SOLID
        val liquid = wantsLiquid && DevicePerformanceUtils.supportsRealtimeGlass

        val shellRadius = dp(22)
        background = Color.TRANSPARENT.toDrawable()
        clipChildren = false

        if (liquid) {
            glassView.visibility = View.VISIBLE
            glassView.bind(findContentContainer())
            glassView.beginBatchUpdate()
            glassView.setCornerRadius(shellRadius.toFloat())
            val frosted = navConfig.effectMode == NavigationBarConfig.EFFECT_FROSTED
            glassView.setRefractionHeight(if (frosted) dp(10).toFloat() else dp(14 + (opacity * 10).toInt()).toFloat())
            glassView.setRefractionOffset(if (frosted) dp(30).toFloat() else dp(42 + (opacity * 18).toInt()).toFloat())
            glassView.setBlurRadius(if (frosted) 22f + opacity * 20f else 8f + opacity * 14f)
            glassView.setDispersion(if (frosted) 0.06f else 0.24f + opacity * 0.24f)
            glassView.setTintAlpha(if (frosted) 0.012f + opacity * 0.268f else 0.004f + opacity * 0.156f)
            glassView.setTintColorRed(Color.red(baseColor) / 255f)
            glassView.setTintColorGreen(Color.green(baseColor) / 255f)
            glassView.setTintColorBlue(Color.blue(baseColor) / 255f)
            glassView.endBatchUpdate()
            shellOverlay.background = createGlassOverlay(navConfig, baseColor, shellRadius)
        } else {
            glassView.release()
            glassView.visibility = View.GONE
            shellOverlay.background = createStaticBackground(navConfig, baseColor, shellRadius)
        }

        elevation = if (opacity <= 0f) 0f else dp(8).toFloat()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val shadowAlpha = (opacity * 255).toInt().coerceIn(0, 255)
            outlineSpotShadowColor = Color.argb(shadowAlpha, 0, 0, 0)
            outlineAmbientShadowColor = Color.argb(shadowAlpha, 0, 0, 0)
        }
    }

    private fun resolveBaseColor(config: NavigationBarConfig): Int {
        return if (config.isBuiltin) {
            io.legado.app.lib.theme.bottomBackground
        } else if (AppConfig.isNightTheme) {
            io.legado.app.utils.getPrefInt(
                io.legado.app.constant.PreferKey.cNBBackground,
                io.legado.app.utils.getCompatColor(R.color.default_night_bottom_background)
            )
        } else {
            io.legado.app.utils.getPrefInt(
                io.legado.app.constant.PreferKey.cBBackground,
                io.legado.app.utils.getCompatColor(R.color.default_bottom_background)
            )
        }
    }

    private fun createGlassOverlay(config: NavigationBarConfig, baseColor: Int, radius: Int): Drawable {
        val alpha = (config.opacity.coerceIn(0, 100) * 0.22f).toInt().coerceIn(0, 255)
        return GradientDrawable().apply {
            setColor(ColorUtils.withAlpha(baseColor, alpha))
            cornerRadius = radius.toFloat()
            config.borderColor?.let { border ->
                val borderAlpha = (Color.alpha(border) * config.borderAlpha.coerceIn(0, 100) / 100f).toInt()
                if (borderAlpha > 0) setStroke(dp(1), ColorUtils.withAlpha(border, borderAlpha))
            }
        }
    }

    private fun createStaticBackground(config: NavigationBarConfig, baseColor: Int, radius: Int): Drawable {
        val alpha = (config.opacity.coerceIn(0, 100) * 0.92f).toInt().coerceIn(0, 255)
        return GradientDrawable().apply {
            setColor(ColorUtils.withAlpha(baseColor, alpha))
            cornerRadius = radius.toFloat()
            config.borderColor?.let { border ->
                val borderAlpha = (Color.alpha(border) * config.borderAlpha.coerceIn(0, 100) / 100f).toInt()
                if (borderAlpha > 0) setStroke(dp(1), ColorUtils.withAlpha(border, borderAlpha))
            }
        }
    }

    private fun selectedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(withAlpha(ThemeStore.accentColor(context), 40))
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
    }

    private fun hideBottomNavigation() {
        findBottomNavigationContainer()?.visibility = View.GONE
    }

    private fun restoreMainNavigation() {
        findBottomNavigationContainer()?.visibility = View.VISIBLE
        findContentContainer()?.let { container ->
            container.setPadding(
                container.paddingLeft,
                contentBasePaddingTop,
                container.paddingRight,
                container.paddingBottom
            )
        }
    }

    private fun adjustContentPadding() {
        if (visibility != View.VISIBLE) return
        val container = findContentContainer() ?: return
        val requiredTop = contentBasePaddingTop + height
        if (container.paddingTop != requiredTop) {
            container.setPadding(
                container.paddingLeft,
                requiredTop,
                container.paddingRight,
                container.paddingBottom
            )
        }
    }

    private fun findBottomNavigation(): com.google.android.material.bottomnavigation.BottomNavigationView? {
        return rootView.findViewById(R.id.bottom_navigation_view)
    }

    private fun findBottomNavigationContainer(): ViewGroup? {
        return rootView.findViewById(R.id.bottom_navigation_glass)
    }

    private fun findContentContainer(): ViewGroup? {
        return rootView.findViewById(R.id.content_container)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
