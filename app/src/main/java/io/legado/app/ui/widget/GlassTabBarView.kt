package io.legado.app.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import com.google.android.material.tabs.TabLayout
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.dpToPx

/**
 * 书架顶部液态玻璃 TabLayout。
 * 保留 TabLayout/ViewPager 原生联动，同时把选中指示器改成悬浮玻璃胶囊。
 */
class GlassTabBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TabLayout(context, attrs) {

    private var onTabClick: ((Int) -> Unit)? = null
    private var onTabLongClick: ((Int) -> Boolean)? = null
    private var submitSelecting = false

    init {
        background = createGlassSurface()
        elevation = 3.dpToPx().toFloat()
        translationZ = 1.dpToPx().toFloat()
        tabRippleColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        tabMode = MODE_SCROLLABLE
        isTabIndicatorFullWidth = false
        setSelectedTabIndicator(createGlassIndicator())
        setSelectedTabIndicatorGravity(INDICATOR_GRAVITY_STRETCH)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            setTabIndicatorAnimationMode(INDICATOR_ANIMATION_MODE_ELASTIC)
        }
        addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: Tab) {
                styleTabs()
                if (!submitSelecting) onTabClick?.invoke(tab.position)
                post { centerTab(tab.position, true) }
            }
            override fun onTabUnselected(tab: Tab) { styleTabs() }
            override fun onTabReselected(tab: Tab) { centerTab(tab.position, true) }
        })
        post { styleTabs() }
    }

    fun setOnTabClickListener(listener: (Int) -> Unit) {
        onTabClick = listener
    }

    fun setOnTabLongClickListener(listener: (Int) -> Boolean) {
        onTabLongClick = listener
    }

    fun submitTabs(names: List<String>, selectedIndex: Int) {
        removeAllTabs()
        names.forEachIndexed { index, name ->
            val tab = newTab().setText(name)
            addTab(tab)
            tab.view?.setOnLongClickListener { onTabLongClick?.invoke(index) ?: false }
        }
        post { styleTabs() }
        val idx = selectedIndex.coerceIn(0, tabCount - 1)
        submitSelecting = true
        getTabAt(idx)?.select()
        submitSelecting = false
        post { centerTab(idx, false) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight.coerceAtLeast(44.dpToPx()))
    }

    private fun createGlassSurface(): GradientDrawable {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isNight) {
                intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(30, 255, 255, 255))
            } else {
                intArrayOf(Color.argb(128, 255, 255, 255), Color.argb(78, 255, 255, 255))
            }
        ).apply {
            cornerRadius = 22.dpToPx().toFloat()
            setStroke(1.dpToPx(), if (isNight) Color.argb(70, 255, 255, 255) else Color.argb(105, 255, 255, 255))
        }
    }

    private fun createGlassIndicator(): GradientDrawable {
        val accent = ThemeStore.accentColor(context)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(92, accent.red(), accent.green(), accent.blue()),
                Color.argb(62, accent.red(), accent.green(), accent.blue())
            )
        ).apply {
            cornerRadius = 20.dpToPx().toFloat()
            setStroke(1.dpToPx(), Color.argb(125, 255, 255, 255))
        }
    }

    private fun styleTabs() {
        for (i in 0 until tabCount) {
            getTabAt(i)?.view?.let { view ->
                view.minimumHeight = 38.dpToPx()
                view.setPadding(15.dpToPx(), 0, 15.dpToPx(), 0)
                view.alpha = if (view.isSelected) 1f else 0.70f
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
