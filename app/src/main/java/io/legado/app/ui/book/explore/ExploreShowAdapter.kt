package io.legado.app.ui.book.explore

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.ViewGroup
import androidx.collection.LruCache
import androidx.core.view.isVisible
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemExploreShowGridBinding
import io.legado.app.databinding.ItemExploreShowWaterfallBinding
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class ExploreShowAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_WATERFALL = 2
        private val waterfallAspectCache = LruCache<String, Float>(399)
    }

    var layoutMode: Int = 0
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var columnCount: Int = 2
        set(value) {
            if (field != value) {
                field = value
                cardWidth = 0
            }
        }
    private var cardWidth = 0

    override fun getItemViewType(item: SearchBook, position: Int): Int {
        return when (layoutMode) {
            2 -> VIEW_TYPE_WATERFALL
            1 -> VIEW_TYPE_GRID
            else -> VIEW_TYPE_LIST
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        if (cardWidth == 0 && columnCount > 1) {
            cardWidth = (parent.width - (columnCount + 1) * 8) / columnCount
        }
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        if (cardWidth == 0 && columnCount > 1) {
            cardWidth = (parent.width - (columnCount + 1) * 8) / columnCount
        }
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

    private fun bindWaterfall(
        holder: ItemViewHolder,
        binding: ItemExploreShowWaterfallBinding,
        item: SearchBook
    ) {
        val lastTag = holder.itemView.tag as? String
        val isRebind = lastTag == item.bookUrl
        holder.itemView.tag = item.bookUrl

        if (!isRebind) {
            binding.tvNameWaterfall.text = item.name
        }

        val coverUrl = item.coverUrl
        val imageView = binding.ivCoverWaterfall
        imageView.adjustViewBounds = true
        val lp = imageView.layoutParams
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT

        if (coverUrl.isNullOrEmpty()) {
            lp.height = cardWidth * 4 / 3
            imageView.layoutParams = lp
            imageView.setImageResource(R.drawable.image_cover_default)
            return
        }

        val cachedRatio = waterfallAspectCache[coverUrl]
        if (cachedRatio != null) {
            lp.height = (cardWidth * cachedRatio).toInt()
            imageView.layoutParams = lp
            if (!isRebind) {
                loadImage(coverUrl, item.origin, imageView)
            }
        } else if (!isRebind) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            imageView.layoutParams = lp
            loadImage(coverUrl, item.origin, imageView) { resource ->
                val w = resource.intrinsicWidth
                val h = resource.intrinsicHeight
                if (w > 0 && h > 0) {
                    waterfallAspectCache.put(coverUrl, h.toFloat() / w.toFloat())
                    notifyItemChanged(holder.bindingAdapterPosition)
                }
            }
        }
    }

    private fun loadImage(
        url: String,
        origin: String,
        imageView: android.widget.ImageView,
        onReady: ((Drawable) -> Unit)? = null
    ) {
        val options = RequestOptions()
        if (origin.isNotEmpty()) {
            options.set(OkHttpModelLoader.sourceOriginOption, origin)
        }
        var builder = ImageLoader.load(context, url)
            .apply(options)
            .placeholder(R.drawable.image_cover_default)
        if (onReady != null) {
            builder = builder.addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    onReady(resource)
                    return false
                }
            })
        }
        builder.centerCrop().into(imageView)
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
