package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.R
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.databinding.ItemBookshelfGrid2Binding
import io.legado.app.databinding.ItemBookshelfGridBinding
import io.legado.app.help.book.isAudio
import io.legado.app.help.config.AppConfig
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import splitties.views.onLongClick

class BooksAdapterGrid(context: Context, private val callBack: CallBack) :
    BaseBooksAdapter<ViewBinding>(context) {
    private val showBookname = AppConfig.showBookname
    override fun getViewBinding(parent: ViewGroup): ViewBinding {
        return when (showBookname) {
            2 -> ItemBookshelfGrid2Binding.inflate(inflater, parent, false)
            else -> ItemBookshelfGridBinding.inflate(inflater, parent, false)
        }
    }

    /**
     * 方案E：取消封面图片加载
     */
    override fun cancelCoverLoad(binding: ViewBinding) {
        when (binding) {
            is ItemBookshelfGridBinding -> binding.ivCover.cancelLoad()
            is ItemBookshelfGrid2Binding -> binding.ivCover.cancelLoad()
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ViewBinding,
        item: BookShelfDisplay,
        payloads: MutableList<Any>
    ) {
        when (binding) {
            is ItemBookshelfGridBinding -> binding.run {
                if (payloads.isEmpty()) {
                    if (showBookname == 0) {
                        tvName.visible()
                        tvName.text = item.name
                    } else {
                        tvName.gone()
                    }
                    ivCover.load(item, false)
                    upRefresh(binding, item)
                    bindAudioPlayButton(holder, binding, item)
                } else {
                    for (i in payloads.indices) {
                        val bundle = payloads[i] as Bundle
                        bundle.keySet().forEach {
                            when (it) {
                                "name" -> tvName.text = item.name
                                "cover" -> ivCover.load(item, false)
                                "refresh" -> upRefresh(binding, item)
                            }
                        }
                    }
                    bindAudioPlayButton(holder, binding, item)
                }
            }
            is ItemBookshelfGrid2Binding -> binding.run {
                if (payloads.isEmpty()) {
                    tvName.text = item.name
                    ivCover.load(item, false)
                    upRefresh(binding, item)
                    bindAudioPlayButton(holder, binding, item)
                } else {
                    for (i in payloads.indices) {
                        val bundle = payloads[i] as Bundle
                        bundle.keySet().forEach {
                            when (it) {
                                "name" -> tvName.text = item.name
                                "cover" -> ivCover.load(item, false)
                                "refresh" -> upRefresh(binding, item)
                            }
                        }
                    }
                    bindAudioPlayButton(holder, binding, item)
                }
            }
        }

    }

    private fun upRefresh(binding: ViewBinding, item: BookShelfDisplay) {
        when (binding) {
            is ItemBookshelfGridBinding -> binding.run {
                if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                    bvUnread.invisible()
                    rlLoading.visible()
                } else {
                    rlLoading.inVisible()
                    if (AppConfig.showUnread) {
                        bvUnread.setBadgeCount(item.getUnreadChapterNum())
                        bvUnread.setHighlight(item.lastCheckCount > 0)
                    } else {
                        bvUnread.invisible()
                    }
                }
            }
            is ItemBookshelfGrid2Binding -> binding.run {
                if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                    bvUnread.invisible()
                    rlLoading.visible()
                } else {
                    rlLoading.inVisible()
                    if (AppConfig.showUnread) {
                        bvUnread.setBadgeCount(item.getUnreadChapterNum())
                        bvUnread.setHighlight(item.lastCheckCount > 0)
                    } else {
                        bvUnread.invisible()
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ViewBinding) {
        holder.itemView.apply {
            setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it.toMinimalBook())
                }
            }

            onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it.toMinimalBook())
                }
            }
        }
    }

    /** 音频书籍专用的书架内联播放/暂停控制（Grid 布局）。 */
    private fun bindAudioPlayButton(
        holder: ItemViewHolder,
        binding: ItemBookshelfGridBinding,
        item: BookShelfDisplay
    ) = bindAudioPlayButtonImpl(holder, binding.ivAudioPlay, item)

    private fun bindAudioPlayButton(
        holder: ItemViewHolder,
        binding: ItemBookshelfGrid2Binding,
        item: BookShelfDisplay
    ) = bindAudioPlayButtonImpl(holder, binding.ivAudioPlay, item)

    private fun bindAudioPlayButtonImpl(
        holder: ItemViewHolder,
        button: androidx.appcompat.widget.AppCompatImageButton,
        item: BookShelfDisplay
    ) {
        if (!item.isAudio) {
            button.gone()
            button.setOnClickListener(null)
            button.isEnabled = false
            return
        }
        button.visible()
        button.isEnabled = true
        val isCurrent = AudioPlay.book?.bookUrl == item.bookUrl
        val isPlaying = isCurrent && AudioPlayService.isRun && !AudioPlayService.pause
        button.setImageResource(if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp)
        button.contentDescription = if (isPlaying) "暂停" else "播放"
        button.setOnClickListener {
            when {
                AudioPlay.book?.bookUrl == item.bookUrl && AudioPlayService.isRun -> {
                    if (AudioPlayService.pause) AudioPlay.resume(context) else AudioPlay.pause(context)
                }
                else -> {
                    AudioPlay.resetData(item.toMinimalBook())
                    button.setImageResource(R.drawable.ic_pause_24dp)
                    button.contentDescription = "暂停"
                    button.postDelayed({ AudioPlay.loadOrUpPlayUrl() }, 120L)
                }
            }
            refreshAudioButtonLater(holder, button)
        }
    }

    private fun refreshAudioButtonLater(holder: ItemViewHolder, button: androidx.appcompat.widget.AppCompatImageButton) {
        button.postDelayed({
            val itemView = button.parent as? View ?: return@postDelayed
            val recyclerView = itemView.parent as? RecyclerView ?: return@postDelayed
            val position = recyclerView.getChildAdapterPosition(holder.itemView)
            if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
        }, 300L)
    }
}
