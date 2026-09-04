package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.databinding.ItemBookshelfListBinding
import io.legado.app.help.book.SmartTag
import io.legado.app.help.book.SmartTagConfig
import io.legado.app.help.book.isAudio
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bookBorderBackground
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toTimeAgo
import io.legado.app.utils.visible
import splitties.views.onLongClick

class BooksAdapterList(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfListBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfListBinding =
        ItemBookshelfListBinding.inflate(inflater, parent, false)

    override fun cancelCoverLoad(binding: ItemBookshelfListBinding) {
        binding.ivCover.cancelLoad()
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfListBinding,
        item: BookShelfDisplay,
        payloads: MutableList<Any>
    ) = binding.run {
        if (payloads.isEmpty()) {
            if (AppConfig.showBookBorder) {
                root.background = context.bookBorderBackground
                (root.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
                    4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx()
                )
            } else {
                root.background = null
                (root.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, 0)
            }
            tvName.text = item.name
            tvAuthor.text = item.author
            tvRead.text = item.durChapterTitle
            tvLast.text = item.latestChapterTitle
            ivCover.load(item, false)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
            upMoreInfo(binding, item)
            bindAudioPlayButton(holder, binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.name
                        "author" -> tvAuthor.text = item.author
                        "dur" -> tvRead.text = item.durChapterTitle
                        "last" -> tvLast.text = item.latestChapterTitle
                        "cover" -> ivCover.load(item, false, fragment, lifecycle)
                        "refresh" -> upRefresh(binding, item)
                        "lastUpdateTime" -> upLastUpdateTime(binding, item)
                        "moreInfo" -> upMoreInfo(binding, item)
                    }
                }
            }
            bindAudioPlayButton(holder, binding, item)
        }
    }

    private fun upRefresh(binding: ItemBookshelfListBinding, item: BookShelfDisplay) {
        if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
            binding.bvUnread.invisible()
            binding.rlLoading.visible()
        } else {
            binding.rlLoading.gone()
            if (AppConfig.showUnread) {
                binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
            } else binding.bvUnread.invisible()
        }
    }

    private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: BookShelfDisplay) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) binding.tvLastUpdateTime.text = time
        } else binding.tvLastUpdateTime.text = ""
    }

    private fun upMoreInfo(binding: ItemBookshelfListBinding, item: BookShelfDisplay) {
        if (AppConfig.showMoreInfoInList && AppConfig.showTagsInList) {
            binding.flexboxTags.visible()
            updateTagViews(binding.flexboxTags, item)
        } else binding.flexboxTags.gone()

        if (AppConfig.showMoreInfoInList && AppConfig.showIntroInList) {
            binding.tvIntro.visible()
            binding.tvIntro.text = item.getDisplayIntroPlainText()
            binding.tvIntro.maxLines = AppConfig.introLinesInList
        } else binding.tvIntro.gone()
    }

    private fun updateTagViews(flexboxLayout: FlexboxLayout, item: BookShelfDisplay) {
        flexboxLayout.removeAllViews()
        item.wordCount?.takeIf { it.isNotBlank() }?.let { flexboxLayout.addView(createTagView(it)) }

        val manualTags = (item.customTag ?: item.kind ?: "").splitNotBlank(",", "\n")
        manualTags.forEach { flexboxLayout.addView(createTagView(it)) }

        if (SmartTagConfig.isEnabled(context)) {
            SmartTag.names(item.toMinimalBook(), SmartTag.ruleInfos.size)
                .filter { SmartTagConfig.isRuleVisible(context, it) }
                .take(4)
                .forEach { flexboxLayout.addView(createSmartTagView(it)) }
        }
    }

    private fun createTagView(tag: String): TextView = TextView(context).apply {
        text = tag
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(context.resources.getColor(R.color.tv_text_summary, null))
        if (AppConfig.showBookBorder) setBackgroundResource(R.drawable.bg_tag)
        setPadding(8, 4, 8, 4)
        layoutParams = FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(4, 2, 4, 2) }
    }

    private fun createSmartTagView(tag: String): TextView = createTagView("✦ $tag").apply {
        alpha = 0.9f
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfListBinding) {
        holder.itemView.apply {
            setOnClickListener { getItem(holder.layoutPosition)?.let { callBack.open(it.toMinimalBook()) } }
            onLongClick { getItem(holder.layoutPosition)?.let { callBack.openBookInfo(it.toMinimalBook()) } }
        }
    }

    private fun bindAudioPlayButton(holder: ItemViewHolder, binding: ItemBookshelfListBinding, item: BookShelfDisplay) {
        val button = binding.ivAudioPlay
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
