package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * 书架顶层下拉刷新布局。
 * 原生 SwipeRefreshLayout 只能识别直接子 View 是否已到顶部，
 * 这里包装 ViewPager，向下递归查找当前可见的 RecyclerView，
 * 避免列表滚动到非顶部时仍触发下拉刷新。
 */
class BookshelfRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    override fun canChildScrollUp(): Boolean {
        if (childCount == 0) return false
        return descendantCanScrollUp(getChildAt(0))
    }

    private fun descendantCanScrollUp(view: View): Boolean {
        if (view.canScrollVertically(-1)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (descendantCanScrollUp(view.getChildAt(i))) return true
            }
        }
        return false
    }
}