package io.legado.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isInvisible
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
import io.legado.app.utils.splitNotBlank
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
                // 分类标签的横向滑动容器会拦截空白区域的点击，转发给整张卡片
                binding.hsvKindGrid.setOnClickListener { holder.itemView.performClick() }
                binding.hsvKindGrid.setOnLongClickListener { holder.itemView.performLongClick() }
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
                // 分类标签的横向滑动容器会拦截空白区域的点击，转发给整张卡片
                binding.hsvKindWaterfall.setOnClickListener { holder.itemView.performClick() }
                binding.hsvKindWaterfall.setOnLongClickListener { holder.itemView.performLongClick() }
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

        binding.tvNameGrid.text = item.name
        binding.tvAuthorGrid.text = item.author

        // 分类标签（不含字数）
        val kindTags = item.kind?.splitNotBlank(",", "\n").orEmpty()
        binding.llKindGrid.setLabels(kindTags.toList())
        // 无标签时保留占位，避免同列卡片信息区高矮不一
        binding.hsvKindGrid.isInvisible = kindTags.isEmpty()

        // 字数，放在分类标签下方
        val wordCount = item.wordCount?.takeIf { it.isNotBlank() }
        if (wordCount.isNullOrBlank()) {
            binding.tvWordCountGrid.gone()
        } else {
            binding.tvWordCountGrid.visible()
            binding.tvWordCountGrid.text = wordCount
        }

        binding.tvIntroduceGrid.text = item.intro?.trim().orEmpty()

        val spacing = calcColumnSpacing()
        val contentWidth = (context.resources.displayMetrics.widthPixels / columnCount - spacing).coerceAtLeast(1)
        val halfSpacing = spacing / 2

        // 卡片高度由内容自然撑开：书名固定两行、标签行保留占位，同列卡片保持等高，
        // 同时避免固定高度在信息较少时留下大片空白。
        binding.root.layoutParams = binding.root.layoutParams.apply {
            if (this is ViewGroup.MarginLayoutParams) {
                setMargins(halfSpacing, halfSpacing, halfSpacing, halfSpacing)
            }
        }

        // 防重用错乱：相同数据跳过重复加载
        val tagKey = "${item.bookUrl}_${item.origin}_$columnCount"
        val lastTag = holder.itemView.tag as? String
        if (lastTag == tagKey) return
        holder.itemView.tag = tagKey

        binding.ivCoverGrid.load(item, AppConfig.loadCoverOnlyWifi, overrideWidth = contentWidth, overrideHeight = contentWidth)
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

        // 分类标签（不含字数）
        val kindTags = item.kind?.splitNotBlank(",", "\n").orEmpty()
        binding.llKindWaterfall.textSize = 10.5f
        binding.llKindWaterfall.setLabels(kindTags.toList())
        // 无标签时保留占位，避免同列卡片信息区高矮不一
        binding.hsvKindWaterfall.isInvisible = kindTags.isEmpty()

        // 字数，放在分类标签下方
        val wordCount = item.wordCount?.takeIf { it.isNotBlank() }
        if (wordCount.isNullOrBlank()) {
            binding.tvWordCountWaterfall.gone()
        } else {
            binding.tvWordCountWaterfall.visible()
            binding.tvWordCountWaterfall.text = wordCount
        }

        // 三列卡片空间有限，只保留核心信息（书名 / 作者 / 分类），章节与简介不展示
        binding.tvLastedWaterfall.gone()
        binding.tvIntroduceWaterfall.gone()

        val spacing = calcColumnSpacing()
        val contentWidth = (context.resources.displayMetrics.widthPixels / columnCount - spacing).coerceAtLeast(1)
        val halfSpacing = spacing / 2

        // 高度由内容自然撑开：封面 1:1、书名固定两行、标签行保留占位，同列卡片保持等高
        binding.root.layoutParams = binding.root.layoutParams.apply {
            if (this is ViewGroup.MarginLayoutParams) {
                setMargins(halfSpacing, halfSpacing, halfSpacing, halfSpacing)
            }
        }

        // 防重用错乱：相同数据跳过重复加载
        val imageView = binding.ivCoverWaterfall
        val tagKey = "${item.bookUrl}_${item.origin}_$columnCount"
        val lastTag = imageView.tag as? String
        if (lastTag == tagKey) return
        imageView.tag = tagKey

        imageView.adjustViewBounds = false

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
            // 分类标签（不含字数）
            val kindTags = item.kind?.splitNotBlank(",", "\n").orEmpty()
            if (kindTags.isEmpty()) {
                hsvKind.gone()
            } else {
                hsvKind.visible()
                llKind.setLabels(kindTags.toList())
            }
            // 字数，放在分类标签下方
            val wordCount = item.wordCount?.takeIf { it.isNotBlank() }
            if (wordCount.isNullOrBlank()) {
                tvWordCount.gone()
            } else {
                tvWordCount.visible()
                tvWordCount.text = wordCount
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
        // 分类标签的横向滑动容器会拦截空白区域的点击，转发给整张卡片
        binding.hsvKind.setOnClickListener { holder.itemView.performClick() }
        binding.hsvKind.setOnLongClickListener { holder.itemView.performLongClick() }
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
