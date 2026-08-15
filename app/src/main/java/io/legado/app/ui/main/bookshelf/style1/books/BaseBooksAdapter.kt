package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.Book

abstract class BaseBooksAdapter<VB : ViewBinding>(context: Context) :
    DiffRecyclerAdapter<BookShelfDisplay, VB>(context) {

    override val keepScrollPosition = true

    /**
     * 方案B优化：DiffUtil 比较逻辑简化
     *
     * areContentsTheSame 只比较直接影响 UI 的字段，
     * 移除了 getDisplayCover() 和 getUnreadChapterNum() 等计算型比较，
     * 改为在 getChangePayload 中按需比较底层字段。
     */
    override val diffItemCallback: DiffUtil.ItemCallback<BookShelfDisplay> =
        object : DiffUtil.ItemCallback<BookShelfDisplay>() {

            override fun areItemsTheSame(oldItem: BookShelfDisplay, newItem: BookShelfDisplay): Boolean {
                return oldItem.name == newItem.name
                        && oldItem.author == newItem.author
            }

            override fun areContentsTheSame(oldItem: BookShelfDisplay, newItem: BookShelfDisplay): Boolean {
                return when {
                    oldItem.durChapterTime != newItem.durChapterTime -> false
                    oldItem.name != newItem.name -> false
                    oldItem.author != newItem.author -> false
                    oldItem.durChapterTitle != newItem.durChapterTitle -> false
                    oldItem.latestChapterTitle != newItem.latestChapterTitle -> false
                    oldItem.lastCheckCount != newItem.lastCheckCount -> false
                    oldItem.coverUrl != newItem.coverUrl -> false
                    oldItem.customCoverUrl != newItem.customCoverUrl -> false
                    oldItem.totalChapterNum != newItem.totalChapterNum -> false
                    oldItem.durChapterIndex != newItem.durChapterIndex -> false
                    oldItem.readConfig != newItem.readConfig -> false
                    else -> true
                }
            }

            override fun getChangePayload(oldItem: BookShelfDisplay, newItem: BookShelfDisplay): Any? {
                val bundle = bundleOf()
                if (oldItem.name != newItem.name) {
                    bundle.putString("name", newItem.name)
                }
                if (oldItem.author != newItem.author) {
                    bundle.putString("author", newItem.author)
                }
                if (oldItem.durChapterTitle != newItem.durChapterTitle) {
                    bundle.putString("dur", newItem.durChapterTitle)
                }
                if (oldItem.latestChapterTitle != newItem.latestChapterTitle) {
                    bundle.putString("last", newItem.latestChapterTitle)
                }
                if (oldItem.coverUrl != newItem.coverUrl || oldItem.customCoverUrl != newItem.customCoverUrl) {
                    bundle.putString("cover", newItem.getDisplayCover())
                }
                if (oldItem.lastCheckCount != newItem.lastCheckCount
                    || oldItem.durChapterTime != newItem.durChapterTime
                    || oldItem.totalChapterNum != newItem.totalChapterNum
                    || oldItem.durChapterIndex != newItem.durChapterIndex
                    || oldItem.readConfig != newItem.readConfig
                ) {
                    bundle.putBoolean("refresh", true)
                }
                if (oldItem.latestChapterTime != newItem.latestChapterTime) {
                    bundle.putBoolean("lastUpdateTime", true)
                }
                if (bundle.isEmpty) return null
                return bundle
            }

        }

    override fun onViewRecycled(holder: ItemViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        // 方案E：回收ViewHolder时取消封面图片加载，避免不必要的内存占用
        (holder.binding as? VB)?.let { binding ->
            cancelCoverLoad(binding)
        }
    }

    /**
     * 方案E：子类实现，取消封面图片加载
     */
    protected abstract fun cancelCoverLoad(binding: VB)

    /**
     * 方案B优化：仅刷新可见范围内的 item，避免对 10000+ 项全量 notifyItemRangeChanged
     */
    fun upLastUpdateTime() {
        val rv = recyclerView ?: return
        val layoutManager = rv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return
        notifyItemRangeChanged(first, last - first + 1, bundleOf(Pair("lastUpdateTime", null)))
    }

    fun notification(bookUrl: String) {
        getItems().forEachIndexed { i, it ->
            if (it.bookUrl == bookUrl) {
                notifyItemChanged(i, bundleOf(Pair("refresh", null), Pair("lastUpdateTime", null)))
                return
            }
        }
    }

    interface CallBack {
        fun open(book: Book)
        fun openBookInfo(book: Book)
        fun isUpdate(bookUrl: String): Boolean
    }
}
