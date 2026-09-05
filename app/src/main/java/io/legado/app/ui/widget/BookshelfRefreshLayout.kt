package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        if (view is RecyclerView) {
            return recyclerCanScrollUp(view)
        }
        if (view.canScrollVertically(-1)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                // 只考虑当前可见页的子树：ViewPager 会预加载当前页之外的离屏分组，
                // 若任一离屏分组的列表不在顶部，会把整页下拉刷新误拦。
                if (!child.getGlobalVisibleRect(Rect())) continue
                if (descendantCanScrollUp(child)) return true
            }
        }
        return false
    }

    /**
     * 判断列表是否真正处于“已离开顶部”的状态。
     * 列表顶部预留 contentPadding（例如智能标签栏预占的高度）时，
     * View.canScrollVertically(-1) 在列表已到顶时仍会返回 true，
     * 导致下拉刷新被误拦。这里改为依据真实的首项位置判断：
     * 仅当首项位置 > 0，或首项内容被顶出容器上缘时，才视为可继续上滑。
     */
    private fun recyclerCanScrollUp(rv: RecyclerView): Boolean {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return rv.canScrollVertically(-1)
        if (lm.findFirstVisibleItemPosition() > 0) return true
        val firstChild = lm.getChildAt(0) ?: return false
        return firstChild.top < rv.paddingTop
    }
}