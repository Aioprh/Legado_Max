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
import io.legado.app.help.glide.CoverLoader
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class ExploreShowAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_WATERFALL = 2
        private const val SPACING_RATIO = 0.05f
    }

    var layoutMode: Int = 0
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var columnCount: Int = 2

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
                bindWaterfall(binding, item)
                holder.itemView.setOnClickListener {
                    getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                        callBack.showBookInfo(it)
                    }
                }
            }
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindGrid(
        holder: ItemViewHolder,
        binding: ItemExploreShowGridBinding,
        item: SearchBook
    ) {
        binding.ivInBookshelfGrid.isVisible = callBack.isInBookshelf(item)
        val tagKey = "${item.bookUrl}_$columnCount"
        val lastItemTag = holder.itemView.tag as? String
        if (lastItemTag == tagKey) return
        holder.itemView.tag = tagKey
        val spacing = calcColumnSpacing()
        val halfSpacing = spacing / 2
        holder.itemView.setPadding(halfSpacing, halfSpacing, halfSpacing, halfSpacing)
        val contentWidth = context.resources.displayMetrics.widthPixels / columnCount - spacing
        binding.ivCoverGrid.load(item, AppConfig.loadCoverOnlyWifi, overrideWidth = contentWidth, overrideHeight = contentWidth * 4 / 3)
        binding.tvNameGrid.text = item.name
    }

    private fun calcColumnSpacing(): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val itemWidth = screenWidth / columnCount.coerceAtLeast(1)
        return (itemWidth * SPACING_RATIO).toInt().coerceIn(2, 80)
    }

    private fun bindWaterfall(
        binding: ItemExploreShowWaterfallBinding,
        item: SearchBook
    ) {
        binding.ivInBookshelfWaterfall.isVisible = callBack.isInBookshelf(item)
        binding.tvNameWaterfall.text = item.name
        binding.tvAuthorWaterfall.text = context.getString(R.string.author_show, item.author)

        val kinds = item.getKindList()
        if (kinds.isEmpty()) {
            binding.llKindWaterfall.gone()
        } else {
            binding.llKindWaterfall.visible()
            binding.llKindWaterfall.setLabels(kinds)
        }

        if (item.latestChapterTitle.isNullOrEmpty()) {
            binding.tvLastedWaterfall.gone()
        } else {
            binding.tvLastedWaterfall.text = context.getString(R.string.lasted_show, item.latestChapterTitle)
            binding.tvLastedWaterfall.visible()
        }

        binding.tvIntroduceWaterfall.text = item.trimIntro(context)
        // 根据卡片实际宽度动态计算简介最大行数（基于密度比例）
        binding.tvIntroduceWaterfall.maxLines = if (columnCount <= 3) {
            10
        } else {
            // 以360dp宽度为基准，计算相对比例
            val density = context.resources.displayMetrics.density
            val screenWidthPx = context.resources.displayMetrics.widthPixels
            val spacing = calcColumnSpacing()
            val itemWidthDp = (screenWidthPx / columnCount - spacing) / density
            // 基准宽度360dp时显示4行，按比例调整
            val baseWidthDp = 360f
            maxOf(1, (itemWidthDp / baseWidthDp * 4).toInt().coerceIn(1, 6))
        }

        val imageView = binding.ivCoverWaterfall
        val tagKey = "${item.bookUrl}_${item.origin}_$columnCount"
        val lastTag = imageView.tag as? String
        if (lastTag == tagKey) return
        imageView.tag = tagKey
        imageView.adjustViewBounds = true
        val lp = imageView.layoutParams
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        imageView.layoutParams = lp

        val spacing = calcColumnSpacing()
        val halfSpacing = spacing / 2
        (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.setMargins(halfSpacing, halfSpacing, halfSpacing, halfSpacing)
            binding.root.layoutParams = it
        }
        val contentWidth = context.resources.displayMetrics.widthPixels / columnCount - spacing

        // 使用 CoverLoader 加载封面，支持封面设置，保持自由图片比例
        CoverLoader.load(
            imageView,
            item,
            AppConfig.loadCoverOnlyWifi,
            overrideWidth = contentWidth,
            overrideHeight = contentWidth * 4 / 3,
            fixedRatio = false
        )
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
