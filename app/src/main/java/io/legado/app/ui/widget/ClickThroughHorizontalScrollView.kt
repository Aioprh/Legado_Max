package io.legado.app.ui.widget

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import kotlin.math.abs

/**
 * 卡片分类标签的横向滚动容器。
 *
 * 平台 HorizontalScrollView 会消费触摸事件，但不走 View 的点击处理流程，
 * 因此不会触发 performClick/performLongClick，标签行空白处的点击会被"吞掉"，
 * 无法穿透到外层卡片。此处在未发生横向滚动时手动补发点击/长按事件，
 * 保证整张卡片（包括标签行）都可点击。
 */
class ClickThroughHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isScrolling = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTime = SystemClock.uptimeMillis()
                isScrolling = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScrolling &&
                    (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop)
                ) {
                    isScrolling = true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (!isScrolling) {
                    if (SystemClock.uptimeMillis() - downTime >= longPressTimeout) {
                        performLongClick()
                    } else {
                        performClick()
                    }
                }
                isScrolling = false
            }
            MotionEvent.ACTION_CANCEL -> isScrolling = false
        }
        return handled
    }
}