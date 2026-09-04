package io.legado.app.ui.main.bookshelf.style2

import android.content.Context
import android.os.Parcelable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.dpToPx

abstract class BaseBooksAdapter<VH : RecyclerView.ViewHolder>(
    val context: Context,
    val callBack: CallBack
) : RecyclerView.Adapter<VH>() {
    private val layoutStates = mutableMapOf<Long, Parcelable?>()
    private var currentGroupId: Long? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    protected val inflater: LayoutInflater = LayoutInflater.from(context)
    private var audioChildAttachListener: RecyclerView.OnChildAttachStateChangeListener? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        layoutManager = recyclerView.layoutManager
        audioChildAttachListener = object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val position = recyclerView.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) bindAudioPlayButton(view, position)
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        }.also { recyclerView.addOnChildAttachStateChangeListener(it) }
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val position = recyclerView.getChildAdapterPosition(child)
            if (position != RecyclerView.NO_POSITION) bindAudioPlayButton(child, position)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        audioChildAttachListener?.let { recyclerView.removeOnChildAttachStateChangeListener(it) }
        audioChildAttachListener = null
        layoutManager = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    private val diffItemCallback = object : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Book && newItem is Book -> {
                    oldItem.name == newItem.name
                            && oldItem.author == newItem.author
                }

                oldItem is BookGroup && newItem is BookGroup -> {
                    oldItem.groupId == newItem.groupId
                }

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Book && newItem is Book -> {
                    oldItem.durChapterTime == newItem.durChapterTime &&
                            oldItem.name == newItem.name &&
                            oldItem.author == newItem.author &&
                            oldItem.durChapterTitle == newItem.durChapterTitle &&
                            oldItem.latestChapterTitle == newItem.latestChapterTitle &&
                            oldItem.lastCheckCount == newItem.lastCheckCount &&
                            oldItem.coverUrl == newItem.coverUrl &&
                            oldItem.customCoverUrl == newItem.customCoverUrl &&
                            oldItem.totalChapterNum == newItem.totalChapterNum &&
                            oldItem.durChapterIndex == newItem.durChapterIndex &&
                            oldItem.readConfig == newItem.readConfig
                }

                oldItem is BookGroup && newItem is BookGroup -> {
                    oldItem.groupName == newItem.groupName &&
                            oldItem.cover == newItem.cover &&
                            oldItem.enableRefresh == newItem.enableRefresh &&
                            oldItem.onlyUpdateRead == newItem.onlyUpdateRead
                }

                else -> false
            }
        }

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
            val bundle = bundleOf()
            when {
                oldItem is Book && newItem is Book -> {
                    if (oldItem.name != newItem.name) bundle.putString("name", newItem.name)
                    if (oldItem.author != newItem.author) bundle.putString("author", newItem.author)
                    if (oldItem.durChapterTitle != newItem.durChapterTitle) bundle.putString("dur", newItem.durChapterTitle)
                    if (oldItem.latestChapterTitle != newItem.latestChapterTitle) bundle.putString("last", newItem.latestChapterTitle)
                    if (oldItem.coverUrl != newItem.coverUrl || oldItem.customCoverUrl != newItem.customCoverUrl) {
                        bundle.putString("cover", newItem.getDisplayCover())
                    }
                    if (oldItem.lastCheckCount != newItem.lastCheckCount
                        || oldItem.durChapterTime != newItem.durChapterTime
                        || oldItem.totalChapterNum != newItem.totalChapterNum
                        || oldItem.durChapterIndex != newItem.durChapterIndex
                        || oldItem.readConfig != newItem.readConfig
                    ) bundle.putBoolean("refresh", true)
                }

                oldItem is BookGroup && newItem is BookGroup -> {
                    if (oldItem.groupName != newItem.groupName) bundle.putString("groupName", newItem.groupName)
                    if (oldItem.cover != newItem.cover) bundle.putString("cover", newItem.cover)
                    if (oldItem.enableRefresh != newItem.enableRefresh || oldItem.onlyUpdateRead != newItem.onlyUpdateRead) {
                        bundle.putBoolean("unviewable", true)
                    }
                }
            }
            if (bundle.isEmpty) return null
            return bundle
        }
    }

    private val asyncListDiffer by lazy {
        AsyncListDiffer(this, diffItemCallback).apply {
            addListListener { _, _ ->
                currentGroupId?.let {
                    layoutManager?.onRestoreInstanceState(layoutStates[it])
                    layoutStates[it] = null
                }
            }
        }
    }

    fun updateItems(groupId: Long) {
        currentGroupId?.let {
            layoutStates[it] = layoutManager?.onSaveInstanceState()
        }
        currentGroupId = groupId
        asyncListDiffer.submitList(callBack.getItems())
    }

    fun notification(bookUrl: String) {
        for (i in 0 until itemCount) {
            getItem(i).let {
                if (it is Book && it.bookUrl == bookUrl) {
                    notifyItemChanged(i, bundleOf(Pair("refresh", null)))
                    return
                }
            }
        }
    }

    fun getItems() = asyncListDiffer.currentList

    fun getItem(position: Int) = getItems().getOrNull(position)

    override fun getItemCount(): Int = getItems().size

    override fun getItemViewType(position: Int): Int {
        if (getItem(position) is BookGroup) return 1
        return 0
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {}

    /**
     * 为音频书架项安装独立的播放/暂停按钮。
     * 按钮位于卡片右侧中部，不影响整行点击打开书籍的行为。
     */
    private fun bindAudioPlayButton(view: View, position: Int) {
        val book = getItem(position) as? Book
        val isAudioBook = book != null && (book.type and BookType.audio) != 0
        val root = view as? ViewGroup ?: return
        val button = root.findViewWithTag<AppCompatImageButton>(AUDIO_BUTTON_TAG)

        if (!isAudioBook) {
            button?.visibility = View.GONE
            return
        }

        val playButton = button ?: AppCompatImageButton(context).apply {
            tag = AUDIO_BUTTON_TAG
            contentDescription = "播放或暂停音频"
            isClickable = true
            isFocusable = true
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(9.dpToPx(), 9.dpToPx(), 9.dpToPx(), 9.dpToPx())
            val selectable = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectable, true)
            if (selectable.resourceId != 0) setBackgroundResource(selectable.resourceId)
            root.addView(this, createAudioButtonLayoutParams(root))
            setOnClickListener {
                val current = AudioPlay.book?.bookUrl == book?.bookUrl
                if (book == null) return@setOnClickListener
                if (current && AudioPlayService.isRun) {
                    if (AudioPlayService.pause || AudioPlay.status != Status.PLAY) {
                        AudioPlay.resume(context)
                    } else {
                        AudioPlay.pause(context)
                    }
                } else {
                    AudioPlay.resetData(book)
                    AudioPlay.loadOrUpPlayUrl()
                }
                postDelayed({ refreshVisibleAudioButtons(root.parent as? RecyclerView) }, 220L)
            }
        }

        playButton.visibility = View.VISIBLE
        updateAudioPlayButtonIcon(playButton, book)
    }

    private fun createAudioButtonLayoutParams(root: ViewGroup): ViewGroup.LayoutParams {
        if (root is ConstraintLayout) {
            return ConstraintLayout.LayoutParams(44.dpToPx(), 44.dpToPx()).apply {
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginEnd = 6.dpToPx()
            }
        }
        return androidx.recyclerview.widget.RecyclerView.LayoutParams(44.dpToPx(), 44.dpToPx())
    }

    private fun updateAudioPlayButtonIcon(button: AppCompatImageButton, book: Book) {
        val playing = AudioPlay.book?.bookUrl == book.bookUrl &&
                AudioPlay.status == Status.PLAY &&
                !AudioPlayService.pause
        button.setImageResource(if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp)
        ContextCompat.getColor(context, R.color.tv_text_summary).let { button.setColorFilter(it) }
    }

    private fun refreshVisibleAudioButtons(recyclerView: RecyclerView?) {
        if (recyclerView == null) return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val position = recyclerView.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val book = getItem(position) as? Book ?: continue
            val button = child.findViewWithTag<AppCompatImageButton>(AUDIO_BUTTON_TAG)
            if ((book.type and BookType.audio) != 0 && button != null) {
                button.visibility = View.VISIBLE
                updateAudioPlayButtonIcon(button, book)
            }
        }
    }

    /**
     * 方案E：回收 ViewHolder 时取消封面图片加载
     */
    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.itemView.findViewById<io.legado.app.ui.widget.image.CoverImageView?>(R.id.iv_cover)
            ?.cancelLoad()
    }

    interface CallBack {
        fun onItemClick(item: Any)
        fun onItemLongClick(item: Any)
        fun isUpdate(bookUrl: String): Boolean
        fun getItems(): List<Any>
    }

    companion object {
        private const val AUDIO_BUTTON_TAG = "legado_audio_play_button"
    }
}
