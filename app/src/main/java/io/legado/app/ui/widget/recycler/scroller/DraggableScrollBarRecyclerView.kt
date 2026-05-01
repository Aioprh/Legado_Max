package io.legado.app.ui.widget.recycler.scroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import kotlin.math.max

open class DraggableScrollBarRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var isDraggingScrollbar = false
    private var lastTouchY = 0f
    private var scrollbarWidth = 0
    private var scrollbarMinHeight = 0
    private var scrollbarRadius = 0f
    private val scrollbarRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    protected var customScrollbarEnabled = false
        private set
    private var scrollbarColor = context.accentColor

    init {
        scrollbarWidth = resources.getDimensionPixelSize(R.dimen.fastscroll_handle_width)
        scrollbarMinHeight = resources.getDimensionPixelSize(R.dimen.fast_scroll_min_thumb_height)
        scrollbarRadius = resources.getDimension(R.dimen.fastscroll_handle_radius)
        paint.color = scrollbarColor
    }

    fun setCustomScrollbarEnabled(enabled: Boolean) {
        customScrollbarEnabled = enabled
        if (enabled) {
            isVerticalScrollBarEnabled = false
        }
        invalidate()
    }

    fun setFastScrollEnabled(enabled: Boolean) {
        setCustomScrollbarEnabled(enabled)
    }

    fun isFastScrollEnabled(): Boolean = customScrollbarEnabled

    fun setScrollbarColor(color: Int) {
        scrollbarColor = color
        paint.color = color
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (customScrollbarEnabled) {
            drawCustomScrollbar(c)
        }
    }

    private fun drawCustomScrollbar(c: Canvas) {
        calculateScrollbarBounds()
        if (scrollbarRect.isEmpty) return

        c.drawRoundRect(scrollbarRect, scrollbarRadius, scrollbarRadius, paint)
    }

    private fun calculateScrollbarBounds() {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val offset = computeVerticalScrollOffset()

        if (range <= 0 || extent <= 0 || extent >= range) {
            scrollbarRect.setEmpty()
            return
        }

        val viewHeight = height - paddingTop - paddingBottom
        val thumbHeight = max(scrollbarMinHeight, (extent * viewHeight / range.toFloat()).toInt())
        val scrollRange = range - extent

        val thumbTop = if (scrollRange > 0) {
            paddingTop + (offset * (viewHeight - thumbHeight) / scrollRange.toFloat())
        } else {
            paddingTop.toFloat()
        }

        val left = (width - paddingRight - scrollbarWidth).toFloat()
        val right = (width - paddingRight).toFloat()

        scrollbarRect.set(left, thumbTop, right, thumbTop + thumbHeight)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN && isTouchInScrollbar(e.x, e.y)) {
            isDraggingScrollbar = true
            lastTouchY = e.y
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchInScrollbar(e.x, e.y)) {
                    isDraggingScrollbar = true
                    lastTouchY = e.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingScrollbar) {
                    val deltaY = e.y - lastTouchY
                    lastTouchY = e.y
                    scrollByDelta(deltaY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingScrollbar) {
                    isDraggingScrollbar = false
                    return true
                }
            }
        }
        return super.onTouchEvent(e)
    }

    private fun isTouchInScrollbar(x: Float, y: Float): Boolean {
        if (!customScrollbarEnabled && !isVerticalScrollBarEnabled) return false

        calculateScrollbarBounds()
        if (scrollbarRect.isEmpty) return false

        val touchPadding = scrollbarWidth * 2
        return x >= scrollbarRect.left - touchPadding &&
               x <= width.toFloat() &&
               y >= scrollbarRect.top - touchPadding &&
               y <= scrollbarRect.bottom + touchPadding
    }

    private fun scrollByDelta(deltaY: Float) {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        if (range <= 0 || extent <= 0) return

        val viewHeight = height - paddingTop - paddingBottom
        val scrollRange = range - extent
        if (scrollRange <= 0) return

        val scrollDelta = (deltaY / viewHeight * scrollRange).toInt()
        val currentOffset = computeVerticalScrollOffset()
        val targetOffset = (currentOffset + scrollDelta).coerceIn(0, scrollRange)

        scrollToPositionByProportion(targetOffset.toFloat() / scrollRange)
    }

    private fun scrollToPositionByProportion(proportion: Float) {
        val layoutManager = layoutManager ?: return
        val adapter = adapter ?: return

        val itemCount = adapter.itemCount
        if (itemCount == 0) return

        val targetPosition = (proportion * itemCount).toInt().coerceIn(0, itemCount - 1)
        
        if (layoutManager is LinearLayoutManager) {
            layoutManager.scrollToPositionWithOffset(targetPosition, 0)
        } else {
            layoutManager.scrollToPosition(targetPosition)
        }
    }
}
