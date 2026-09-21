package io.legado.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemExploreShowGridBinding
import io.legado.app.databinding.ItemExploreShowWaterfallBinding
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.CoverLoader
import io.legado.app.ui.widget.image.CircleImageView
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class ExploreShowAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_WATERFALL = 2
        private const val SPACING_DP = 10
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
                holder.itemView.setOnLongClickListener {
                    getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                        callBack.onBookLongClick(it)
                    }
                    true
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
                holder.itemView.setOnLongClickListener {
                    getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                        callBack.onBookLongClick(it)
                    }
                    true
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
        // 新版发现页不显示书架状态角标，避免封面右上角出现白色方块。
        binding.ivInBookshelfGrid.isVisible = false
        binding.ivInBookshelfDotGrid.isVisible = false
        val tagKey = "${item.bookUrl}_$columnCount"
        val lastItemTag = holder.itemView.tag as? String
        if (lastItemTag == tagKey) return
        holder.itemView.tag = tagKey
        val density = context.resources.displayMetrics.density
        val itemMargin = (8 * density).toInt()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val contentWidth = (screenWidth / columnCount - itemMargin).coerceAtLeast(1)
        binding.ivCoverGrid.load(item, AppConfig.loadCoverOnlyWifi, overrideWidth = contentWidth, overrideHeight = contentWidth)
        binding.tvNameGrid.text = item.name
    }

    private fun calcColumnSpacing(): Int {
        val density = context.resources.displayMetrics.density
        return (SPACING_DP * density).toInt().coerceAtLeast(1)
    }

    private fun bindWaterfall(
        binding: ItemExploreShowWaterfallBinding,
        item: SearchBook
    ) {
        // 新版发现页不显示书架状态角标，避免封面右上角出现白色方块。
        binding.ivInBookshelfWaterfall.isVisible = false
        binding.ivInBookshelfDotWaterfall.isVisible = false
        binding.tvNameWaterfall.text = item.name
        binding.tvAuthorWaterfall.text = item.author

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
            binding.tvLastedWaterfall.text = item.latestChapterTitle
            binding.tvLastedWaterfall.visible()
        }

        binding.tvIntroduceWaterfall.text = item.intro?.trim().orEmpty()

        // 统一瀑布流卡片高度，避免简介长短不同造成三列卡片参差不齐。
        val density = context.resources.displayMetrics.density
        val spacing = calcColumnSpacing()
        val contentWidth = (context.resources.displayMetrics.widthPixels / columnCount - spacing).coerceAtLeast(1)
        val contentAreaHeight = (210 * density).toInt()
        binding.root.layoutParams = binding.root.layoutParams.apply {
            height = contentWidth + contentAreaHeight
        }

        // 固定简介可见行数，超出部分省略，确保所有卡片保持相同高度。
        binding.tvIntroduceWaterfall.maxLines = when {
            columnCount <= 2 -> 7
            columnCount == 3 -> 5
            else -> 4
        }

        val imageView = binding.ivCoverWaterfall
        val tagKey = "${item.bookUrl}_${item.origin}_$columnCount"
        val lastTag = imageView.tag as? String
        if (lastTag == tagKey) return
        imageView.tag = tagKey
        val halfSpacing = spacing / 2

        imageView.adjustViewBounds = false
        val lp = imageView.layoutParams
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = contentWidth
        imageView.layoutParams = lp

        (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.setMargins(halfSpacing, halfSpacing, halfSpacing, halfSpacing)
            binding.root.layoutParams = it
        }

        // 使用 CoverLoader 加载封面，支持封面设置，保持自由图片比例
        CoverLoader.load(
            imageView,
            item,
            AppConfig.loadCoverOnlyWifi,
            overrideWidth = contentWidth,
            overrideHeight = contentWidth,
            fixedRatio = true
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
            val shelfState = callBack.getBookShelfState(item)
            ivInBookshelf.setShelfState(shelfState)
            ivInBookshelfDot.setShelfStateDot(shelfState)
            tvName.text = item.name
            tvAuthor.text = context.getString(R.string.author_show, item.author)
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
                    "isInBookshelf" -> {
                        val shelfState = callBack.getBookShelfState(item)
                        ivInBookshelf.setShelfState(shelfState)
                        ivInBookshelfDot.setShelfStateDot(shelfState)
                    }
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
        holder.itemView.setOnLongClickListener {
            getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                callBack.onBookLongClick(it)
            }
            true
        }
    }

    interface CallBack {
        fun getBookShelfState(book: SearchBook): BookShelfState
        fun showBookInfo(book: SearchBook)
        fun onBookLongClick(book: SearchBook)
    }
}

/**
 * 根据书架状态设置 ImageView 的图标和可见性（新版样式）：
 * - IN_SHELF: 显示 Check 图标（已加入书架）
 * - SAME_NAME_AUTHOR: 显示 Shuffle 图标（同名同作者）
 * - NOT_IN_SHELF: 隐藏
 * 根据配置决定是否显示
 */
internal fun ImageView.setShelfState(state: BookShelfState) {
    // 新版样式（图标）仅在配置为0时显示
    if (AppConfig.bookshelfIconStyle == 0) {
        when (state) {
            BookShelfState.IN_SHELF -> {
                setImageResource(R.drawable.ic_check)
                isVisible = true
            }
            BookShelfState.SAME_NAME_AUTHOR -> {
                setImageResource(R.drawable.ic_shuffle)
                isVisible = true
            }
            else -> {
                isVisible = false
            }
        }
    } else {
        isVisible = false
    }
}

/**
 * 根据书架状态设置 CircleImageView 的可见性（经典样式）：
 * - IN_SHELF: 显示小绿点
 * - SAME_NAME_AUTHOR: 显示小绿点
 * - NOT_IN_SHELF: 隐藏
 * 根据配置决定是否显示
 */
internal fun CircleImageView.setShelfStateDot(state: BookShelfState) {
    // 经典样式（小绿点）仅在配置为1时显示
    if (AppConfig.bookshelfIconStyle == 1) {
        when (state) {
            BookShelfState.IN_SHELF, BookShelfState.SAME_NAME_AUTHOR -> {
                isVisible = true
            }
            else -> {
                isVisible = false
            }
        }
    } else {
        isVisible = false
    }
}
