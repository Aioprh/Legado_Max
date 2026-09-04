package io.legado.app.ui.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.defaultSharedPreferences
import java.io.File

/**
 * 将底栏导航能力适配到顶栏。
 *
 * 顶栏导航采用底栏相同的五个导航项和图标配置，但使用紧凑的
 * 图标按钮布局，避免文字与状态栏发生裁切或挤压。
 */
class TopNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val prefs = context.defaultSharedPreferences
    private var basePaddingTop = 0
    private var contentBasePaddingTop = 0
    private var attached = false

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (attached) post { refresh() }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        elevation = dp(3).toFloat()
        clipChildren = false
        clipToPadding = false
        visibility = View.GONE

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            setPadding(dp(8), basePaddingTop + statusBarTop, dp(8), 0)
            post { adjustContentPadding() }
            insets
        }
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
            restoreMainNavigation()
            return
        }

        visibility = View.VISIBLE
        bringToFront()
        buildItems(entry)
        hideBottomNavigation()
        adjustContentPadding()
    }

    private fun buildItems(entry: TopBarConfig.Entry) {
        removeAllViews()

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
            addView(createItem(previewConfig, item, item.menuId == selectedId))
        }
        background = createBackground(config)
    }

    private fun createItem(
        config: NavigationBarConfig,
        item: NavigationBarConfig.NavItem,
        selected: Boolean
    ): View {
        return FrameItem(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
            foreground = null
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

    private fun createBackground(config: TopBarConfig.Config): GradientDrawable {
        val base = TopBarConfig.resolveBackgroundColor(config)
        val alpha = if (config.style == TopBarConfig.STYLE_REGULAR) {
            config.tagBarAlpha.coerceIn(0, 100)
        } else {
            92
        }
        return GradientDrawable().apply {
            setColor(TopBarConfig.withOpacity(base, alpha))
            cornerRadius = dp(16).toFloat() * TopBarConfig.resolveCornerScale(config).coerceIn(0.5f, 3f)
        }
    }

    private fun selectedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(withAlpha(ThemeStore.accentColor(context), 32))
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
