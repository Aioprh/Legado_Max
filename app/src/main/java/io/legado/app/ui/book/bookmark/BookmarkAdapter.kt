package io.legado.app.ui.book.bookmark

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ItemBookmarkBinding
import io.legado.app.utils.gone
import splitties.views.onClick
import splitties.views.onLongClick

class BookmarkAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<Bookmark, ItemBookmarkBinding>(context) {

    private val collapsedGroups = HashSet<String>()
    private var allItems: List<Bookmark> = emptyList()

    override fun getViewBinding(parent: ViewGroup): ItemBookmarkBinding {
        return ItemBookmarkBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookmarkBinding,
        item: Bookmark,
        payloads: MutableList<Any>
    ) {
        val collapsed = collapsedGroups.contains(getGroupKey(item))
        val density = binding.root.context.resources.displayMetrics.density
        val padding = (8 * density).toInt()
        
        if (collapsed) {
            binding.root.setPadding(0, 0, 0, 0)
            binding.tvChapterName.gone(true)
            binding.tvBookText.gone(true)
            binding.tvContent.gone(true)
        } else {
            binding.root.setPadding(padding, padding, padding, padding)
            binding.tvChapterName.text = item.chapterName
            binding.tvChapterName.gone(false)
            binding.tvBookText.gone(item.bookText.isEmpty())
            binding.tvBookText.text = item.bookText
            binding.tvContent.gone(item.content.isEmpty())
            binding.tvContent.text = item.content
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookmarkBinding) {
        binding.root.onClick {
            getItemByLayoutPosition(holder.layoutPosition)?.let {
                callback.onItemClick(it, holder.layoutPosition)
            }
        }
        binding.root.onLongClick {
            getItemByLayoutPosition(holder.layoutPosition)?.let {
                callback.onItemLongClick(it, holder.layoutPosition)
            } ?: false
        }
    }

    fun getHeaderText(position: Int): String {
        return with(getItem(position)) {
            "${this?.bookName ?: ""}(${this?.bookAuthor ?: ""})"
        }
    }

    fun isItemHeader(position: Int): Boolean {
        if (position == 0) return true
        val lastItem = getItem(position - 1)
        val curItem = getItem(position)
        return !(lastItem?.bookName == curItem?.bookName
                && lastItem?.bookAuthor == curItem?.bookAuthor)
    }

    fun getGroupKey(bookmark: Bookmark): String {
        return "${bookmark.bookName}_${bookmark.bookAuthor}"
    }

    fun isGroupCollapsed(position: Int): Boolean {
        val item = getItem(position) ?: return false
        return collapsedGroups.contains(getGroupKey(item))
    }

    fun toggleGroup(position: Int): Boolean {
        val item = getItem(position) ?: return false
        val groupKey = getGroupKey(item)
        return if (collapsedGroups.contains(groupKey)) {
            collapsedGroups.remove(groupKey)
            true
        } else {
            collapsedGroups.add(groupKey)
            true
        }
    }

    fun setItemsWithCollapse(items: List<Bookmark>) {
        allItems = items
        val filteredItems = mutableListOf<Bookmark>()
        var currentGroupKey: String? = null
        var addedHeaderForGroup = false
        
        for (item in items) {
            val groupKey = getGroupKey(item)
            if (groupKey != currentGroupKey) {
                currentGroupKey = groupKey
                addedHeaderForGroup = false
            }
            if (collapsedGroups.contains(groupKey)) {
                if (!addedHeaderForGroup) {
                    filteredItems.add(item)
                    addedHeaderForGroup = true
                }
            } else {
                filteredItems.add(item)
            }
        }
        setItems(filteredItems)
    }

    fun getGroupPosition(position: Int): Int {
        var groupCount = 0
        for (i in 0..position) {
            if (isItemHeader(i)) {
                groupCount++
            }
        }
        return groupCount - 1
    }

    interface Callback {

        fun onItemClick(bookmark: Bookmark, position: Int)
        fun onItemLongClick(bookmark: Bookmark, position: Int): Boolean

    }

}