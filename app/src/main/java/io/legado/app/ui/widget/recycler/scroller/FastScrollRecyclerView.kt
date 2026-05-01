package io.legado.app.ui.widget.recycler.scroller

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getCompatColor

open class FastScrollRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var fastScroller: FastScrollerView? = null
    private var fastScrollEnabled = true
    private var scrollbarColor = ColorUtils.adjustAlpha(context.accentColor, 0.5f)
    private var trackColor = 0x26000000

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (fastScrollEnabled) {
            createFastScroller()
        }
    }

    override fun onDetachedFromWindow() {
        fastScroller?.detachFromRecyclerView()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateFastScrollerLayout()
    }

    private fun createFastScroller() {
        if (fastScroller != null) return
        
        fastScroller = FastScrollerBuilder(this)
            .setThumbColor(scrollbarColor)
            .setTrackColor(trackColor)
            .build()
        
        val parent = parent as? ViewGroup ?: return
        if (parent.indexOfChild(fastScroller) == -1) {
            parent.addView(fastScroller)
        }
        updateFastScrollerLayout()
    }

    private fun updateFastScrollerLayout() {
        val scroller = fastScroller ?: return
        val parent = parent as? ViewGroup ?: return
        
        val lp = scroller.layoutParams ?: when (parent) {
            is FrameLayout -> FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            else -> ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.height = height
        
        when (lp) {
            is FrameLayout.LayoutParams -> {
                lp.topMargin = top
                lp.bottomMargin = parent.height - bottom
                lp.marginEnd = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end)
            }
            is MarginLayoutParams -> {
                lp.topMargin = top
                lp.bottomMargin = parent.height - bottom
                lp.marginEnd = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end)
            }
        }
        
        scroller.layoutParams = lp
        scroller.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    fun setFastScrollEnabled(enabled: Boolean) {
        if (fastScrollEnabled != enabled) {
            fastScrollEnabled = enabled
            if (enabled) {
                createFastScroller()
                fastScroller?.visibility = VISIBLE
            } else {
                fastScroller?.visibility = GONE
            }
        }
    }

    fun isFastScrollEnabled(): Boolean = fastScrollEnabled

    fun setHideScrollbar(hideScrollbar: Boolean) {
        setFastScrollEnabled(!hideScrollbar)
    }

    fun setTrackColor(@ColorInt color: Int) {
        trackColor = color
        fastScroller?.setTrackColor(color)
    }

    fun setHandleColor(@ColorInt color: Int) {
        scrollbarColor = ColorUtils.adjustAlpha(color, 0.5f)
        fastScroller?.setThumbColor(scrollbarColor)
    }

    fun setBubbleVisible(visible: Boolean) {
        // 不支持气泡
    }

    fun setBubbleColor(@ColorInt color: Int) {
        // 不支持气泡
    }

    fun setBubbleTextColor(@ColorInt color: Int) {
        // 不支持气泡
    }

    fun setSectionIndexer(sectionIndexer: FastScroller.SectionIndexer?) {
        // 不支持 SectionIndexer
    }

    fun setFastScrollStateChangeListener(listener: FastScrollStateChangeListener?) {
        // 不支持状态监听
    }

}
