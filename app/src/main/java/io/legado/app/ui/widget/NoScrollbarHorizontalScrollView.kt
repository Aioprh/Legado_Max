package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.HorizontalScrollView

/**
 * HorizontalScrollView with platform scroll indicators and foreground effects disabled.
 * Used by the bookshelf smart-tag filter bar.
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
        isVerticalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        overScrollMode = OVER_SCROLL_NEVER
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        scrollBarSize = 0
    }

    override fun onDrawForeground(canvas: Canvas) {
        // Intentionally omit the platform foreground pass. HorizontalScrollView
        // can draw scroll indicators/fading effects here even when scrollbars
        // are disabled; the tag bar must remain visually clean while scrolling.
    }
}
