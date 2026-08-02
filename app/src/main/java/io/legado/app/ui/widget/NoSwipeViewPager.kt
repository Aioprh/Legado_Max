package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class NoSwipeViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    var pagingEnabled = true

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return if (pagingEnabled) {
            super.onInterceptTouchEvent(ev)
        } else {
            false   // 不拦截任何触摸，内容区域的子 View（RecyclerView 等）可正常滚动/点击
        }
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        return if (pagingEnabled) {
            super.onTouchEvent(ev)
        } else {
            false   // 不消费触摸，完全禁用滑动切换
        }
    }
}