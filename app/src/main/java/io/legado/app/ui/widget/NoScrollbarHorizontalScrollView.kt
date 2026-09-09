package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.HorizontalScrollView

/**
 * HorizontalScrollView used by the bookshelf smart-tag filter bar.
 *
 * The tag bar is an overlay surface. It must keep horizontal scrolling while
 * drawing no platform scroll indicators, fading effects, or elevation shadow.
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
        stateListAnimator = null
        translationZ = 0f
    }

    /**
     * Do not allow the bookshelf tag bar to acquire a Material/platform shadow.
     * A translucent rounded surface plus an elevation shadow can appear as a
     * thin white horizontal line on some Android renderers.
     */
    override fun setElevation(elevation: Float) {
        super.setElevation(0f)
    }

    override fun setTranslationZ(translationZ: Float) {
        super.setTranslationZ(0f)
    }

    override fun onDrawForeground(canvas: Canvas) {
        // Deliberately omit the platform foreground pass so scroll indicators
        // and fading/edge effects cannot paint a line over the tag surface.
    }
}
