package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.defaultSharedPreferences
import java.io.File

/**
 * 将底栏的导航能力适配到顶部。
 *
 * 顶栏配置启用 navigationEnabled 后，本控件接管首页/书架/发现/RSS/我的导航，
 * 复用底栏的菜单 ID、页面切换逻辑以及图标资源，同时允许顶栏配置覆盖图标。
 */
class TopNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val prefs = context.defaultSharedPreferences
    private var baseTopPadding = 0
    private var contentBasePaddingTop = 0
    private var insetsTop = 0
    private var attached = false

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (attached) post { refresh() }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        elevation = dp(2).toFloat()
        visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            insetsTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            setPadding(dp(8), baseTopPadding + insetsTop, dp(8), 0)
            post { adjustContentPadding() }
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        baseTopPadding = paddingTop
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

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && attached) post { refresh() }
    }

    private fun refresh() {
        val night = isNightMode()
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
        val selectedId = findBottomNavigation()?.selectedItemId ?: NavigationBarConfig.items.first().menuId

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
        val cell = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            background = if (selected) selectedBackground() else null
            setOnClickListener {
                findBottomNavigation()?.selectedItemId = item.menuId
                post { refresh() }
            }
        }
        val icon = ImageView(context).apply {
            layoutParams = LayoutParams(dp(26), dp(26))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageDrawable(NavigationBarConfig.previewDrawable(context, config, item, selected))
            contentDescription = context.getString(item.titleRes)
        }
        val title = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1)
            }
            text = context.getString(item.titleRes)
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(
                if (selected) ThemeStore.accentColor(context)
                else ContextCompat.getColor(context, R.color.secondaryText)
            )
            maxLines = 1
        }
        cell.addView(icon)
        cell.addView(title)
        return cell
    }

    private fun createBackground(config: TopBarConfig.Config): GradientDrawable {
        val base = TopBarConfig.resolveBackgroundColor(config)
        val alpha = if (config.style == TopBarConfig.STYLE_REGULAR) config.tagBarAlpha else 92
        return GradientDrawable().apply {
            setColor(TopBarConfig.withOpacity(base, alpha))
            cornerRadius = dp(14).toFloat() * TopBarConfig.resolveCornerScale(config).coerceAtLeast(0.5f)
        }
    }

    private fun selectedBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(ThemeStore.accentColor(context) and 0x20FFFFFF)
            cornerRadius = dp(12).toFloat()
        }
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
        val top = contentBasePaddingTop + height
        container.setPadding(container.paddingLeft, top, container.paddingRight, container.paddingBottom)
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

    private fun isNightMode(): Boolean =
        runCatching { io.legado.app.help.config.AppConfig.isNightTheme }.getOrDefault(false)

    private fun selectedBackgroundColor(): Int = Color.argb(32, 255, 255, 255)

    private fun dp(value: Int): Int = (value * density).toInt()
}
