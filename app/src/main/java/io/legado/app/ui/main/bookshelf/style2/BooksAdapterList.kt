package io.legado.app.ui.main.bookshelf.style2

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.ItemBookshelfGridGroupBinding
import io.legado.app.databinding.ItemBookshelfList2Binding
import io.legado.app.databinding.ItemBookshelfListBinding
import io.legado.app.databinding.ItemBookshelfListGroupBinding
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.lib.theme.bookBorderBackground
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toTimeAgo
import io.legado.app.utils.visible
import io.legado.app.utils.dpToPx
import splitties.views.onLongClick

@Suppress("UNUSED_PARAMETER")
class BooksAdapterList(context: Context, callBack: CallBack) :
    BaseBooksAdapter<RecyclerView.ViewHolder>(context, callBack) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            1 -> {
                if (AppConfig.folderLayout >= 2) {
                    GroupGridViewHolder(ItemBookshelfGridGroupBinding.inflate(inflater, parent, false))
                } else {
                    GroupViewHolder(ItemBookshelfListGroupBinding.inflate(inflater, parent, false))
                }
            }
            else -> {
                if (AppConfig.bookLayout == 0) { BookViewHolder(ItemBookshelfListBinding.inflate(inflater, parent, false)) }
                else { BookViewHolder2(ItemBookshelfList2Binding.inflate(inflater, parent, false)) }
            }

        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when (holder) {
            is BookViewHolder -> (getItem(position) as? Book)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }

            is BookViewHolder2 -> (getItem(position) as? Book)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }

            is GroupViewHolder -> (getItem(position) as? BookGroup)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }

            is GroupGridViewHolder -> (getItem(position) as? BookGroup)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }
        }
    }

    /**
     * 音频书籍使用书架内联播放控制，不打开完整播放器即可播放/暂停。
     * 仅对 BookType.audio 生效，普通文本/漫画/本地书籍不会出现按钮。
     */
    private fun bindAudioPlayButton(button: android.widget.ImageButton, item: Book) {
        if (!item.isAudio) {
            button.gone()
            button.setOnClickListener(null)
            button.isEnabled = false
            return
        }

        button.visible()
        button.isEnabled = true
        val isCurrent = AudioPlay.book?.bookUrl == item.bookUrl
        val isPlaying = isCurrent && AudioPlay.status == Status.PLAY && !AudioPlayService.pause
        button.setImageResource(if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp)
        button.contentDescription = if (isPlaying) "暂停" else "播放"

        button.setOnClickListener {
            val current = AudioPlay.book
            when {
                current?.bookUrl == item.bookUrl && AudioPlay.status == Status.PLAY -> {
                    AudioPlay.pause(context)
                }
                current?.bookUrl == item.bookUrl && AudioPlay.status == Status.PAUSE -> {
                    AudioPlay.resume(context)
                }
                else -> {
                    // 切换到新的音频书：复用现有 AudioPlay 播放链路，不复制播放器逻辑。
                    AudioPlay.resetData(item)
                    button.setImageResource(R.drawable.ic_pause_24dp)
                    button.contentDescription = "暂停"
                    button.postDelayed({ AudioPlay.loadOrUpPlayUrl() }, 120L)
                }
            }
            // AudioPlayService 通过 EventBus 更新全局播放状态，这里延迟刷新一次，
            // 让当前 RecyclerView item 立即跟随播放/暂停状态切换。
            button.postDelayed({ notifyItemChanged(bindingAdapterPosition.coerceAtLeast(0)) }, 220L)
            button.postDelayed({ notifyItemChanged(bindingAdapterPosition.coerceAtLeast(0)) }, 650L)
        }
    }

    inner class BookViewHolder(val binding: ItemBookshelfListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: Book, position: Int) = binding.run {
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
            flHasNew.visible()
            ivAuthor.visible()
            ivLast.visible()
            ivRead.visible()
            upRefresh(this, item)
            upLastUpdateTime(binding, item)
            upMoreInfo(binding, item)
            bindAudioPlayButton(ivAudioPlay, item)
        }

        fun onBind(item: Book, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item, position)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> tvName.text = item.name
                            "author" -> tvAuthor.text = item.author
                            "dur" -> tvRead.text = item.durChapterTitle
                            "last" -> tvLast.text = item.latestChapterTitle
                            "cover" -> ivCover.load(item, false)
                            "refresh" -> upRefresh(this, item)
                            "lastUpdateTime" -> upLastUpdateTime(binding, item)
                            "moreInfo" -> upMoreInfo(binding, item)
                        }
                    }
                }
                bindAudioPlayButton(ivAudioPlay, item)
            }
        }

        fun registerListener(item: Any) {
            binding.root.setOnClickListener { callBack.onItemClick(item) }
            binding.root.onLongClick { callBack.onItemLongClick(item) }
        }

        private fun upRefresh(binding: ItemBookshelfListBinding, item: Book) {
            if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                binding.bvUnread.invisible()
                binding.rlLoading.visible()
            } else {
                binding.rlLoading.gone()
                if (AppConfig.showUnread) {
                    binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                    binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
                } else {
                    binding.bvUnread.invisible()
                }
            }
        }

        private fun upMoreInfo(binding: ItemBookshelfListBinding, item: Book) {
            if (AppConfig.showMoreInfoInList && AppConfig.showTagsInList) {
                binding.flexboxTags.visible()
                updateTagViews(binding.flexboxTags, item)
            } else {
                binding.flexboxTags.gone()
            }
            if (AppConfig.showMoreInfoInList && AppConfig.showIntroInList) {
                binding.tvIntro.visible()
                binding.tvIntro.text = item.getDisplayIntroPlainText()
                binding.tvIntro.maxLines = AppConfig.introLinesInList
            } else {
                binding.tvIntro.gone()
            }
        }

        private fun updateTagViews(flexboxLayout: FlexboxLayout, item: Book) {
            flexboxLayout.removeAllViews()
            if (item.wordCount?.isNotBlank() == true) {
                flexboxLayout.addView(createTagView(item.wordCount!!))
            }
            val tagsText = item.customTag ?: item.kind ?: ""
            if (tagsText.isNotBlank()) {
                for (tag in tagsText.splitNotBlank(",", "\n")) {
                    flexboxLayout.addView(createTagView(tag))
                }
            }
        }

        private fun createTagView(tag: String): TextView {
            return TextView(context).apply {
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
        }

        private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: Book) {
            if (AppConfig.showLastUpdateTime && !item.isLocal) {
                val time = item.latestChapterTime.toTimeAgo()
                if (binding.tvLastUpdateTime.text != time) binding.tvLastUpdateTime.text = time
            } else {
                binding.tvLastUpdateTime.text = ""
            }
        }
    }

    inner class BookViewHolder2(val binding: ItemBookshelfList2Binding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: Book, position: Int) = binding.run {
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
            flHasNew.visible()
            ivAuthor.visible()
            ivLast.visible()
            upRefresh(this, item)
            upLastUpdateTime(binding, item)
            bindAudioPlayButton(ivAudioPlay, item)
        }

        fun onBind(item: Book, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item, position)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> tvName.text = item.name
                            "author" -> tvAuthor.text = item.author
                            "dur" -> tvRead.text = item.durChapterTitle
                            "last" -> tvLast.text = item.latestChapterTitle
                            "cover" -> ivCover.load(item, false)
                            "refresh" -> upRefresh(this, item)
                            "lastUpdateTime" -> upLastUpdateTime(binding, item)
                        }
                    }
                }
                bindAudioPlayButton(ivAudioPlay, item)
            }
        }

        fun registerListener(item: Any) {
            binding.root.setOnClickListener { callBack.onItemClick(item) }
            binding.root.onLongClick { callBack.onItemLongClick(item) }
        }

        private fun upRefresh(binding: ItemBookshelfList2Binding, item: Book) {
            if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                binding.bvUnread.invisible()
                binding.rlLoading.visible()
            } else {
                binding.rlLoading.gone()
                if (AppConfig.showUnread) {
                    binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                    binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
                } else {
                    binding.bvUnread.invisible()
                }
            }
        }

        private fun upLastUpdateTime(binding: ItemBookshelfList2Binding, item: Book) {
            if (AppConfig.showLastUpdateTime && !item.isLocal) {
                val time = item.latestChapterTime.toTimeAgo()
                if (binding.tvLastUpdateTime.text != time) binding.tvLastUpdateTime.text = time
            } else {
                binding.tvLastUpdateTime.text = ""
            }
        }
    }

    inner class GroupViewHolder(val binding: ItemBookshelfListGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun onBind(item: BookGroup, position: Int) = binding.run {
            tvName.text = item.groupName
            ivCover.load(item)
            flHasNew.gone()
            ivAuthor.gone()
            ivLast.gone()
            ivRead.gone()
            tvAuthor.gone()
            tvLast.gone()
            tvRead.gone()
        }
        fun onBind(item: BookGroup, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) onBind(item, position) else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "groupName" -> tvName.text = item.groupName
                            "cover" -> ivCover.load(item)
                        }
                    }
                }
            }
        }
        fun registerListener(item: Any) {
            binding.root.setOnClickListener { callBack.onItemClick(item) }
            binding.root.onLongClick { callBack.onItemLongClick(item) }
        }
    }

    inner class GroupGridViewHolder(val binding: ItemBookshelfGridGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun onBind(item: BookGroup, position: Int) = binding.run {
            tvName.text = item.groupName
            ivCover.load(item)
        }
        fun onBind(item: BookGroup, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) onBind(item, position) else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "groupName" -> tvName.text = item.groupName
                            "cover" -> ivCover.load(item)
                        }
                    }
                }
            }
        }
        fun registerListener(item: Any) {
            binding.root.setOnClickListener { callBack.onItemClick(item) }
            binding.root.onLongClick { callBack.onItemLongClick(item) }
        }
    }
}