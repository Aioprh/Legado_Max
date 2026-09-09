package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import com.google.android.material.tabs.TabLayout
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.dpToPx

/**
 * 书架顶部液态玻璃 TabLayout。
 *
 * 书架当前采用页面左右滑动切换分组，标签筛选栏负责展示当前页面的筛选项。
 * 为避免 ViewPager 横向滑动过程中顶部出现第二套分组标签，这个控件默认不参与显示。
 * 保留完整 TabLayout 实现，便于已有调用代码安全运行，也避免影响选项卡状态同步。
 */
class GlassTabBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TabLayout(context, attrs) {

    private var onTabClick: ((Int) -> Unit)? = null
    private var onTabLongClick: ((Int) -> Boolean)? = null
    private var submitSelecting = false

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
        post { updateTabMode() }

        // 书架分组通过 ViewPager 左右滑动切换；顶部只保留当前书架的标签筛选栏。
        // 隐藏这个分组 Tab，避免切换页面时出现“全部/听书”等第二套标签。
        visibility = View.GONE
    }

    /**
     * 根据所有 tab 的总宽度自适应切换模式：
     * - 能在胶囊内完整放下 → MODE_FIXED，tab 均匀填满，避免右侧留空白
     * - 放不下 → MODE_SCROLLABLE，允许左右滑动
     */
    private fun updateTabMode() {
        if (tabCount == 0 || width == 0) return
        val totalWidth = (0 until tabCount).sumOf { getTabAt(it)?.view?.width ?: 0 }
        val targetMode = if (totalWidth <= width) MODE_FIXED else MODE_SCROLLABLE
        if (tabMode != targetMode) {
            tabMode = targetMode
            post {
                styleTabs()
            }
        }
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
        if (tabCount == 0) return
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

    private fun createGlassIndicator(): GradientDrawable {
        val accent = ThemeStore.accentColor(context)
        return GradientDrawable().apply {
            cornerRadius = 20.dpToPx().toFloat()
            setColor(Color.argb(70, accent.red(), accent.green(), accent.blue()))
            setStroke(1.dpToPx(), Color.argb(105, 255, 255, 255))
        }
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
