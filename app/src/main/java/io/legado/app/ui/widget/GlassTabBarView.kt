package io.legado.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.dpToPx
import kotlin.math.max

/**
 * 书架顶部液态玻璃 Tab：选中项为悬浮玻璃胶囊，并支持跟随 ViewPager 滑动的指示动画。
 */
class GlassTabBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    private val tabs = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(8.dpToPx(), 5.dpToPx(), 8.dpToPx(), 5.dpToPx())
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorRect = RectF()
    private var selectedIndex = 0
    private var targetIndex = 0
    private var pageOffset = 0f
    private var tabClick: ((Int) -> Unit)? = null
    private var tabLongClick: ((Int) -> Boolean)? = null
    private var indicatorAnimator: ValueAnimator? = null

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        setBackground(GradientDrawable().apply {
            cornerRadius = 24.dpToPx().toFloat()
            setColor(ContextCompat.getColor(context, R.color.background_card))
            setStroke(1.dpToPx(), ContextCompat.getColor(context, R.color.divider))
        })
        elevation = 3.dpToPx().toFloat()
        addView(tabs, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        indicatorPaint.color = accentColor
    }

    fun setOnTabClickListener(listener: (Int) -> Unit) {
        tabClick = listener
    }

    fun setOnTabLongClickListener(listener: (Int) -> Boolean) {
        tabLongClick = listener
    }

    fun submitTabs(labels: List<String>, selected: Int = 0) {
        indicatorAnimator?.cancel()
        tabs.removeAllViews()
        labels.forEachIndexed { index, label ->
            tabs.addView(createTab(label, index))
        }
        selectedIndex = selected.coerceIn(0, max(0, labels.lastIndex))
        targetIndex = selectedIndex
        pageOffset = 0f
        updateTabStyles()
        post { updateIndicator(false) }
    }

    fun selectTab(index: Int, animate: Boolean = true) {
        if (tabs.childCount == 0) return
        val target = index.coerceIn(0, tabs.childCount - 1)
        selectedIndex = target
        targetIndex = target
        pageOffset = 0f
        updateTabStyles()
        updateIndicator(animate)
        scrollToSelected()
    }

    /** 在 ViewPager.onPageScrolled 中调用，指示胶囊会随手指平滑移动。 */
    fun onPageScrolled(position: Int, positionOffset: Float) {
        if (tabs.childCount == 0) return
        targetIndex = position.coerceIn(0, tabs.childCount - 1)
        pageOffset = positionOffset.coerceIn(0f, 1f)
        invalidate()
    }

    private fun createTab(label: String, index: Int): TextView = TextView(context).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = 38.dpToPx()
        minWidth = 58.dpToPx()
        setPadding(15.dpToPx(), 0, 15.dpToPx(), 0)
        setTextColor(primaryTextColor)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (index != selectedIndex) tabClick?.invoke(index)
        }
        setOnLongClickListener { tabLongClick?.invoke(index) ?: false }
        background = null
    }

    private fun updateTabStyles() {
        for (i in 0 until tabs.childCount) {
            val view = tabs.getChildAt(i) as? TextView ?: continue
            view.setTextColor(if (i == selectedIndex) ContextCompat.getColor(context, R.color.white) else primaryTextColor)
            view.alpha = if (i == selectedIndex) 1f else 0.78f
            view.elevation = if (i == selectedIndex) 2.dpToPx().toFloat() else 0f
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawIndicator(canvas)
        super.dispatchDraw(canvas)
    }

    private fun drawIndicator(canvas: Canvas) {
        val from = tabs.getChildAt(selectedIndex) ?: return
        val toIndex = targetIndex.coerceIn(0, max(0, tabs.childCount - 1))
        val to = tabs.getChildAt(toIndex) ?: return
        val t = if (selectedIndex == toIndex) 0f else pageOffset
        val left = from.left + (to.left - from.left) * t
        val right = from.right + (to.right - from.right) * t
        val top = from.top + 3.dpToPx()
        val bottom = from.bottom - 3.dpToPx()
        indicatorRect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        indicatorPaint.alpha = 62
        canvas.drawRoundRect(indicatorRect, 19.dpToPx().toFloat(), 19.dpToPx().toFloat(), indicatorPaint)
        indicatorPaint.alpha = 255
    }

    private fun updateIndicator(animate: Boolean) {
        if (!animate) {
            invalidate()
            return
        }
        indicatorAnimator?.cancel()
        val start = selectedIndex
        val end = targetIndex
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            addUpdateListener {
                pageOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun scrollToSelected() {
        val child = tabs.getChildAt(selectedIndex) ?: return
        smoothScrollTo(max(0, child.left - width / 2 + child.width / 2), 0)
    }
}
