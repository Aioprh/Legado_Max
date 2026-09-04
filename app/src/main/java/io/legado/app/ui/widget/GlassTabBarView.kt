package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import com.google.android.material.tabs.TabLayout
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.dpToPx

/**
 * 书架顶部液态玻璃 TabLayout。
 * 保留 TabLayout/ViewPager 原生联动，同时把选中指示器改成悬浮玻璃胶囊。
 */
class GlassTabBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TabLayout(context, attrs) {

    init {
        background = GradientDrawable().apply {
            cornerRadius = 24.dpToPx().toFloat()
            setColor(Color.argb(34, 255, 255, 255))
            setStroke(1.dpToPx(), Color.argb(46, 255, 255, 255))
        }
        elevation = 3.dpToPx().toFloat()
        tabRippleColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        tabMode = MODE_SCROLLABLE
        isTabIndicatorFullWidth = false
        setSelectedTabIndicator(createGlassIndicator())
        setSelectedTabIndicatorGravity(INDICATOR_GRAVITY_STRETCH)
        setTabIndicatorAnimationMode(INDICATOR_ANIMATION_MODE_ELASTIC)
        addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: Tab) {
                styleTabs()
                post { centerTab(tab.position, true) }
            }
            override fun onTabUnselected(tab: Tab) { styleTabs() }
            override fun onTabReselected(tab: Tab) { centerTab(tab.position, true) }
        })
        post { styleTabs() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight.coerceAtLeast(44.dpToPx()))
    }

    private fun createGlassIndicator(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 20.dpToPx().toFloat()
        setColor(Color.argb(70, accentColor.red(), accentColor.green(), accentColor.blue()))
        setStroke(1.dpToPx(), Color.argb(105, 255, 255, 255))
    }

    private fun styleTabs() {
        for (i in 0 until tabCount) {
            getTabAt(i)?.view?.let { view ->
                view.minimumHeight = 38.dpToPx()
                view.setPadding(15.dpToPx(), 0, 15.dpToPx(), 0)
                view.alpha = if (view.isSelected) 1f else 0.72f
                view.elevation = if (view.isSelected) 2.dpToPx().toFloat() else 0f
            }
        }
    }

    private fun centerTab(position: Int, animate: Boolean) {
        val tabView = getTabAt(position)?.view ?: return
        val target = tabView.left - (width - tabView.width) / 2
        if (animate) smoothScrollTo(target.coerceAtLeast(0), 0) else scrollTo(target.coerceAtLeast(0), 0)
    }

    private fun Int.red(): Int = this shr 16 and 0xff
    private fun Int.green(): Int = this shr 8 and 0xff
    private fun Int.blue(): Int = this and 0xff
}
