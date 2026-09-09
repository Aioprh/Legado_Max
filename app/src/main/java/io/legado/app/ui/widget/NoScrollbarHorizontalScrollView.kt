package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.graphics.Canvas
import android.widget.HorizontalScrollView

/**
 * HorizontalScrollView without any platform scrollbar drawing.
 * Used by bookshelf smart-tag filter bar so the rounded glass container
 * never shows a stray horizontal white scrollbar/line.
 */
class NoScrollbarHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    init {
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        overScrollMode = OVER_SCROLL_NEVER
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        scrollBarSize = 0
    }

    override fun onDrawScrollBars(canvas: Canvas) {
        // Intentionally empty: the tag bar is a decorative horizontal scroller.
    }
}
