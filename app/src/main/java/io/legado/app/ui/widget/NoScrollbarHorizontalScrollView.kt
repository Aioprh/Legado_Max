package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

/**
 * HorizontalScrollView with all platform scroll indicators disabled.
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
        setFadingEdgeLength(0)
        overScrollMode = OVER_SCROLL_NEVER
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        scrollBarSize = 0
    }
}
