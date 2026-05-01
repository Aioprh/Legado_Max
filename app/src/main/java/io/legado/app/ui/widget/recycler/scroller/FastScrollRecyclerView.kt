package io.legado.app.ui.widget.recycler.scroller

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorInt
import io.legado.app.R

open class FastScrollRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DraggableScrollBarRecyclerView(context, attrs, defStyleAttr) {

    fun setHideScrollbar(hideScrollbar: Boolean) {
        setCustomScrollbarEnabled(!hideScrollbar)
    }

    fun setTrackColor(@ColorInt color: Int) {
        // 轨道颜色暂不支持
    }

    fun setHandleColor(@ColorInt color: Int) {
        setScrollbarColor(color)
    }

    fun setBubbleVisible(visible: Boolean) {
        // 不再支持气泡显示
    }

    fun setBubbleColor(@ColorInt color: Int) {
        // 不再支持气泡
    }

    fun setBubbleTextColor(@ColorInt color: Int) {
        // 不再支持气泡
    }

    fun setSectionIndexer(sectionIndexer: FastScroller.SectionIndexer?) {
        // 不再支持 SectionIndexer
    }

    fun setFastScrollStateChangeListener(listener: FastScrollStateChangeListener?) {
        // 不再支持状态监听
    }

}
