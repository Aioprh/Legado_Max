package io.legado.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemExploreShowGridBinding
import io.legado.app.databinding.ItemExploreShowWaterfallBinding
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class ExploreShowAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_WATERFALL = 2
    }

    /** 布局模式，由 Activity 控制。0=列表, 1=网格, 2=瀑布流；非列表模式均使用简化卡片 */
    var layoutMode: Int = 0
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(item: SearchBook, position: Int): Int {
        return when (layoutMode) {
            2 -> VIEW_TYPE_WATERFALL
            1 -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> ItemViewHolder(ItemExploreShowGridBinding.inflate(inflater, parent, false))
            VIEW_TYPE_WATERFALL -> ItemViewHolder(ItemExploreShowWaterfallBinding.inflate(inflater, parent, false))
            else -> super.onCreateViewHolder(parent, viewType)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(
        holder: ItemViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val binding = holder.binding
        when (binding) {
            is ItemExploreShowGridBinding -> {
                val actualPosition = position - getHeaderCount()
                if (actualPosition < 0 || actualPosition >= getActualItemCount()) return
                val item = getItem(actualPosition) ?: return
                bindGrid(holder, binding, item)
                holder.itemView.setOnClickListener {
                    getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                        callBack.showBookInfo(it)
                    }
                }
            }
            is ItemExploreShowWaterfallBinding -> {
                val actualPosition = position - getHeaderCount()
                if (actualPosition < 0 || actualPosition >= getActualItemCount()) return
                val item = getItem(actualPosition) ?: return
                bindWaterfall(holder, binding, item)
                holder.itemView.setOnClickListener {
                    getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                        callBack.showBookInfo(it)
                    }
                }
            }
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    /**
     * 网格模式：仅渲染封面和书名（最多两行）。
     * 比对上次绑定的 bookUrl，相同则跳过避免返回页面时封面闪烁。
     */
    private fun bindGrid(
        holder: ItemViewHolder,
        binding: ItemExploreShowGridBinding,
        item: SearchBook
    ) {
        val lastItemTag = holder.itemView.tag as? String
        if (lastItemTag == item.bookUrl) return
        holder.itemView.tag = item.bookUrl
        binding.ivCoverGrid.load(item, AppConfig.loadCoverOnlyWifi)
        binding.tvNameGrid.text = item.name
    }

    /**
     * 瀑布流模式：使用普通 ImageView 加载封面，依靠 adjustViewBounds 动态适配图片比例实现错落效果。
     * 比对上次绑定的 bookUrl，相同则跳过避免返回页面时封面闪烁。
     */
    private fun bindWaterfall(
        holder: ItemViewHolder,
        binding: ItemExploreShowWaterfallBinding,
        item: SearchBook
    ) {
        val lastItemTag = holder.itemView.tag as? String
        if (lastItemTag == item.bookUrl) return
        holder.itemView.tag = item.bookUrl
        binding.tvNameWaterfall.text = item.name
        val coverUrl = item.coverUrl
        if (coverUrl.isNullOrEmpty()) {
            binding.ivCoverWaterfall.setImageResource(R.drawable.image_cover_default)
            return
        }
        ImageLoader.load(context, coverUrl)
            .placeholder(R.drawable.image_cover_default)
            .centerCrop()
            .into(binding.ivCoverWaterfall)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            bind(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bindChange(binding, item, bundle)
            }
        }
    }

    private fun bind(binding: ItemSearchBinding, item: SearchBook) {
        binding.run {
            tvName.text = item.name
            tvAuthor.text = context.getString(R.string.author_show, item.author)
            ivInBookshelf.isVisible = callBack.isInBookshelf(item)
            if (item.latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text = context.getString(R.string.lasted_show, item.latestChapterTitle)
                tvLasted.visible()
            }
            tvIntroduce.text = item.trimIntro(context)
            val kinds = item.getKindList()
            if (kinds.isEmpty()) {
                llKind.gone()
            } else {
                llKind.visible()
                llKind.setLabels(kinds)
            }
            ivCover.load(
                item,
                AppConfig.loadCoverOnlyWifi
            )
        }
    }

    private fun bindChange(binding: ItemSearchBinding, item: SearchBook, bundle: Bundle) {
        binding.run {
            bundle.keySet().forEach {
                when (it) {
                    "isInBookshelf" -> ivInBookshelf.isVisible =
                        callBack.isInBookshelf(item)
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                callBack.showBookInfo(it)
            }
        }
    }

    interface CallBack {
        fun isInBookshelf(book: SearchBook): Boolean
        fun showBookInfo(book: SearchBook)
    }
}
