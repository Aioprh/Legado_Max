package io.legado.app.ui.video.player

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import io.legado.app.R
import io.legado.app.data.entities.BookChapter

/**
 * 视频播放器内的选集抽屉。
 *
 * 这里只负责视频集数选择，不启动阅读器目录，也不跳转书籍详情/目录页面。
 */
class ChoiceEpisodeDialog(private val mContext: Context) : Dialog(
    mContext, R.style.dialog_style
) {
    private var listView: ListView? = null
    private var adapter: ArrayAdapter<BookChapter>? = null
    private var onItemClickListener: OnListItemClickListener? = null
    private var data: List<BookChapter>? = null

    interface OnListItemClickListener {
        fun onItemClick(position: Int)
        fun finishDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            attributes = attributes.apply {
                dimAmount = 0.58f
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        setCanceledOnTouchOutside(true)
    }

    override fun onStop() {
        onItemClickListener?.finishDialog()
        super.onStop()
    }

    @SuppressLint("SetTextI18n")
    fun initList(
        data: List<BookChapter>,
        onItemClickListener: OnListItemClickListener,
        initialSelection: Int = -1
    ) {
        this.onItemClickListener = onItemClickListener
        this.data = data

        val view = LayoutInflater.from(mContext)
            .inflate(R.layout.switch_episode_video_dialog, null)

        view.findViewById<TextView>(R.id.listCount).text = "选集  ·  ${data.size} 集"
        view.findViewById<TextView>(R.id.currentEpisode).text = when {
            initialSelection in data.indices -> "当前播放：${data[initialSelection].title}"
            else -> "视频专用选集"
        }

        listView = view.findViewById(R.id.switch_dialog_list)
        setContentView(view)

        adapter = SwitchVideoAdapter(
            context = mContext,
            dataList = data,
            titleProvider = { it.title },
            selectedPosition = initialSelection
        )
        listView?.adapter = adapter

        if (initialSelection in data.indices) {
            listView?.post {
                listView?.setSelectionFromTop(initialSelection, 12)
            }
        }

        listView?.onItemClickListener = OnItemClickListener()
        view.findViewById<TextView>(R.id.closeDialog).setOnClickListener {
            dismiss()
        }

        window?.apply {
            val metrics = mContext.resources.displayMetrics
            attributes = attributes.apply {
                width = (metrics.widthPixels * 0.68f).toInt().coerceAtLeast(280)
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.END
                dimAmount = 0.58f
            }
        }
    }

    private inner class OnItemClickListener : AdapterView.OnItemClickListener {
        override fun onItemClick(
            adapterView: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            onItemClickListener?.onItemClick(position)
            dismiss()
        }
    }
}
