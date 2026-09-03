package io.legado.app.ui.book.toc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.ItemChapterListBinding
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.gone
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ChapterListAdapter(context: Context, val callback: Callback) :
    DiffRecyclerAdapter<BookChapter, ItemChapterListBinding>(context) {

    val cacheFileNames = hashSetOf<String>()
    private val displayTitleMap = ConcurrentHashMap<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private var fullItems: List<BookChapter> = emptyList()
    private val collapsedVolumes = mutableSetOf<Int>()
    private var pendingVolumeToggleIndex: Int? = null

    override val diffItemCallback: DiffUtil.ItemCallback<BookChapter>
        get() = object : DiffUtil.ItemCallback<BookChapter>() {
            override fun areItemsTheSame(oldItem: BookChapter, newItem: BookChapter) = oldItem.index == newItem.index
            override fun areContentsTheSame(oldItem: BookChapter, newItem: BookChapter) = oldItem.bookUrl == newItem.bookUrl && oldItem.url == newItem.url && oldItem.isVip == newItem.isVip && oldItem.isPay == newItem.isPay && oldItem.title == newItem.title && oldItem.tag == newItem.tag && oldItem.wordCount == newItem.wordCount && oldItem.isVolume == newItem.isVolume
        }

    private var upDisplayTileJob: Coroutine<*>? = null

    override fun onCurrentListChanged() {
        super.onCurrentListChanged()
        pendingVolumeToggleIndex?.let { volumeIndex ->
            handler.post {
                val index = getItems().indexOfFirst { it.index == volumeIndex }
                if (index >= 0) notifyItemChanged(index)
            }
            pendingVolumeToggleIndex = null
        }
        callback.onListChanged()
    }

    fun setChapterItems(items: List<BookChapter>, applyCollapse: Boolean = true) {
        fullItems = items
        if (applyCollapse) {
            resetCollapsedVolumes()
            autoExpandVolumeForChapter(callback.durChapterIndex())
            applyFilter()
        } else setItems(items)
    }

    private fun resetCollapsedVolumes() {
        collapsedVolumes.clear()
        if (!AppConfig.tocCollapseVolumeName) return
        fullItems.forEachIndexed { index, item ->
            if (item.isVolume && index < fullItems.lastIndex && !fullItems[index + 1].isVolume) collapsedVolumes.add(item.index)
        }
    }

    private fun autoExpandVolumeForChapter(chapterIndex: Int) {
        var volumeIndex = -1
        for (item in fullItems) {
            if (item.isVolume) volumeIndex = item.index
            if (item.index == chapterIndex) break
        }
        if (volumeIndex >= 0) collapsedVolumes.remove(volumeIndex)
    }

    private fun applyFilter() {
        if (collapsedVolumes.isEmpty()) { setItems(fullItems); return }
        val filtered = mutableListOf<BookChapter>()
        var skipUntilNextVolume = false
        for (item in fullItems) {
            if (item.isVolume) { skipUntilNextVolume = item.index in collapsedVolumes; filtered.add(item) }
            else if (!skipUntilNextVolume) filtered.add(item)
        }
        setItems(filtered)
    }

    fun toggleVolume(volumeIndex: Int) {
        if (volumeIndex in collapsedVolumes) collapsedVolumes.remove(volumeIndex) else collapsedVolumes.add(volumeIndex)
        pendingVolumeToggleIndex = volumeIndex
        applyFilter()
    }

    fun isVolumeCollapsed(volumeIndex: Int): Boolean = volumeIndex in collapsedVolumes

    private fun volumeHasChapters(volumeIndex: Int): Boolean {
        var found = false
        for (item in fullItems) {
            if (item.index == volumeIndex) { found = true; continue }
            if (found) { if (item.isVolume) return false; return true }
        }
        return false
    }

    private fun isCurrentVolume(volumeIndex: Int): Boolean {
        val durChapterIndex = callback.durChapterIndex()
        var inVolume = false
        for (item in fullItems) {
            if (item.isVolume) inVolume = item.index == volumeIndex
            if (item.index == durChapterIndex) return inVolume
        }
        return false
    }

    fun notifyChapterChanged(chapterIndex: Int) {
        getItems().forEachIndexed { index, bookChapter -> if (bookChapter.index == chapterIndex) notifyItemChanged(index, true) }
    }

    fun clearDisplayTitle() { upDisplayTileJob?.cancel(); displayTitleMap.clear() }

    fun upDisplayTitles(startIndex: Int) {
        upDisplayTileJob?.cancel()
        upDisplayTileJob = Coroutine.async(callback.scope) {
            val book = callback.book ?: return@async
            val replaceRules = ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
            val replaceBook = book.toReplaceBook()
            val useReplace = AppConfig.tocUiUseReplace && book.getUseReplaceRule()
            val items = getItems()
            launch {
                for (i in startIndex until items.size) {
                    val item = items[i]
                    if (displayTitleMap[item.title] == null) {
                        ensureActive()
                        displayTitleMap[item.title] = item.getDisplayTitle(replaceRules, useReplace, replaceBook = replaceBook)
                        ensureActive()
                        handler.post { notifyItemChanged(i, true) }
                    }
                }
            }
            launch {
                for (i in startIndex downTo 0) {
                    val item = items[i]
                    if (displayTitleMap[item.title] == null) {
                        ensureActive()
                        displayTitleMap[item.title] = item.getDisplayTitle(replaceRules, useReplace, replaceBook = replaceBook)
                        ensureActive()
                        handler.post { notifyItemChanged(i, true) }
                    }
                }
            }
        }
    }

    private fun getDisplayTitle(chapter: BookChapter): String = displayTitleMap[chapter.title] ?: chapter.title

    override fun getViewBinding(parent: ViewGroup): ItemChapterListBinding = ItemChapterListBinding.inflate(inflater, parent, false)

    override fun convert(holder: ItemViewHolder, binding: ItemChapterListBinding, item: BookChapter, payloads: MutableList<Any>) {
        binding.run {
            val isDur = callback.durChapterIndex() == item.index
            val cached = callback.isLocalBook || item.isVolume || cacheFileNames.contains(item.getFileName())
            val isCurrentVol = item.isVolume && isCurrentVolume(item.index)
            tvChapterItem.background = when {
                isDur -> ContextCompat.getDrawable(context, R.drawable.bg_toc_item_current_chapter)
                item.isVolume && isCurrentVol -> ContextCompat.getDrawable(context, R.drawable.bg_toc_volume_current)
                item.isVolume -> ContextCompat.getDrawable(context, R.drawable.bg_toc_volume)
                else -> ContextCompat.getDrawable(context, R.drawable.bg_toc_item)
            }
            if (payloads.isEmpty()) {
                val textColor = if (isDur || isCurrentVol) context.accentColor else context.getColor(R.color.primaryText)
                tvChapterName.setTextColor(textColor)
                tvChapterName.text = getDisplayTitle(item)
                tvChapterName.isSingleLine = !AppConfig.tocShowFullChapterName
                if (item.isVolume) {
                    if (volumeHasChapters(item.index)) {
                        ivVolumeIndicator.visible()
                        ivVolumeIndicator.rotation = if (isVolumeCollapsed(item.index)) 0f else 90f
                        ivVolumeIndicator.setColorFilter(textColor)
                    } else ivVolumeIndicator.gone()
                } else ivVolumeIndicator.gone()
                if (!item.tag.isNullOrEmpty()) { tvTag.text = item.tag; tvTag.visible() } else tvTag.gone()
                if (AppConfig.tocCountWords && !item.wordCount.isNullOrEmpty() && !item.isVolume) { tvWordCount.text = item.wordCount; tvWordCount.visible() } else tvWordCount.gone()
                if (item.isVip && !item.isPay) ivLocked.visible() else ivLocked.gone()
                upHasCache(binding, isDur, cached)
            } else {
                tvChapterName.text = getDisplayTitle(item)
                tvChapterName.isSingleLine = !AppConfig.tocShowFullChapterName
                upHasCache(binding, isDur, cached)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemChapterListBinding) {
        holder.itemView.setOnClickListener { getItem(holder.layoutPosition)?.let { if (it.isVolume) toggleVolume(it.index) else callback.openChapter(it) } }
        holder.itemView.setOnLongClickListener { getItem(holder.layoutPosition)?.let { item -> context.longToastOnUi(getDisplayTitle(item)) }; true }
    }

    private fun upHasCache(binding: ItemChapterListBinding, isDur: Boolean, cached: Boolean) = binding.apply {
        ivChecked.setImageResource(R.drawable.ic_outline_cloud_24)
        ivChecked.visible(!cached)
        if (isDur) { ivChecked.setImageResource(R.drawable.ic_check); ivChecked.visible() }
    }

    interface Callback {
        val scope: CoroutineScope
        val book: Book?
        val isLocalBook: Boolean
        fun openChapter(bookChapter: BookChapter)
        fun durChapterIndex(): Int
        fun onListChanged()
    }
}
