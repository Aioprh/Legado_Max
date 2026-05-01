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

    private fun createFastScroller() {
        if (fastScroller != null) return
        
        fastScroller = FastScrollerBuilder(this)
            .setThumbColor(scrollbarColor)
            .setTrackColor(trackColor)
            .build()
        
        val parent = parent as? ViewGroup ?: return
        if (parent.indexOfChild(fastScroller) == -1) {
            val lp = when (parent) {
                is FrameLayout -> FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = android.view.Gravity.END
                    marginEnd = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end)
                }
                else -> MarginLayoutParams(
                    MarginLayoutParams.WRAP_CONTENT,
                    MarginLayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end)
                }
            }
            parent.addView(fastScroller, lp)
        }
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
