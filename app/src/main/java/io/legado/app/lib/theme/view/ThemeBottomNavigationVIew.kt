package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.databinding.ViewNavigationBadgeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.lib.theme.elevation

class ThemeBottomNavigationVIew(context: Context, attrs: AttributeSet) :
    BottomNavigationView(context, attrs) {

    init {
        val transparentNavBar = context.transparentNavBar
        val bgColor = context.bottomBackground
        if (transparentNavBar) {
            setBackgroundColor(Color.TRANSPARENT)
        } else {
            setBackgroundColor(bgColor)
            elevation = context.elevation
        }
        val colorStateList = createThemeColorStateList()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
        if (AppConfig.isEInkMode || transparentNavBar) {
            isItemHorizontalTranslationEnabled = false
            itemBackground = Color.TRANSPARENT.toDrawable()
        }

        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }

    fun createThemeColorStateList(bgColor: Int = context.bottomBackground): ColorStateList {
        val textIsDark = ColorUtils.isColorLight(bgColor)
        val textColor = context.getSecondaryTextColor(textIsDark)
        return Selector.colorBuild()
            .setDefaultColor(textColor)
            .setSelectedColor(ThemeStore.accentColor(context))
            .create()
    }

    fun restoreThemeIconTint(bgColor: Int = context.bottomBackground) {
        val colorStateList = createThemeColorStateList(bgColor)
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
    }

    /**
     * 应用底栏导航栏配置（NavigationBarConfig）。
     *
     * 根据当前激活的配置项设置底栏背景色/透明度、图标和文字颜色。
     * E-Ink 模式下跳过，保持 E-Ink 专用背景。
     */
    fun applyNavigationBarConfig() {
        if (AppConfig.isEInkMode) return
        val config = NavigationBarConfig.activeConfig(context, AppConfig.isNightTheme)
        val baseColor = context.bottomBackground
        val bgColor = NavigationBarConfig.resolveBottomColor(baseColor, config)
        if (context.transparentNavBar) {
            setBackgroundColor(Color.TRANSPARENT)
        } else {
            setBackgroundColor(bgColor)
            elevation = context.elevation
        }
        restoreThemeIconTint(bgColor)
    }

    fun addBadgeView(index: Int): BadgeView {
        //获取底部菜单view
        val menuView = getChildAt(0) as ViewGroup
        //获取第index个itemView
        val itemView = menuView.getChildAt(index) as ViewGroup
        val badgeBinding = ViewNavigationBadgeBinding.inflate(LayoutInflater.from(context))
        itemView.addView(badgeBinding.root)
        return badgeBinding.viewBadge
    }

}
