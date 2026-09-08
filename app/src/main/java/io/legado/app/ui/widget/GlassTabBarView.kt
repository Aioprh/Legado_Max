package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
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
    private val clipPath = Path()

    init {
        background = GradientDrawable().apply {
            cornerRadius = 24.dpToPx().toFloat()
            setColor(Color.argb(34, 255, 255, 255))
            setStroke(1.dpToPx(), Color.argb(46, 255, 255, 255))
        }
        elevation = 3.dpToPx().toFloat()
        // 内容裁剪到大胶囊圆角内：硬件加速下 clipToOutline 依赖 outline 快照，
        // 需在 onSizeChanged 里 invalidateOutline() 刷新，滑动再远也不画出圆角。
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 24.dpToPx().toFloat())
            }
        }
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
            // TabLayout 自带点击选中，这里只补充长按回调
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        if (w > 0 && h > 0) {
            val radius = 24.dpToPx().toFloat().coerceAtMost(h / 2f)
            clipPath.addRoundRect(
                0f,
                0f,
                w.toFloat(),
                h.toFloat(),
                radius,
                radius,
                Path.Direction.CW
            )
        }
        // 刷新 RenderNode outline 快照，clipToOutline 的圆角裁剪才生效
        invalidateOutline()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            return
        }
        canvas.save()
        if (!clipPath.isEmpty) canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restore()
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
