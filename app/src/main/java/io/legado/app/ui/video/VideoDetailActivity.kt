package io.legado.app.ui.video

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.constant.SourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoDetailActivity : AppCompatActivity() {
    private val viewModel by viewModels<BookInfoViewModel>()
    private lateinit var content: LinearLayout
    private var book: Book? = null
    private var chapters: List<BookChapter> = emptyList()
    private var pendingResumeEpisode = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor); setPadding(16.dpToPx(),0,16.dpToPx(),100.dpToPx()) }
        scroll.addView(content); setContentView(scroll)
        content.addView(TextView(this).apply { text = "‹  影视详情"; textSize = 20f; setTextColor(primaryTextColor); gravity = Gravity.CENTER_VERTICAL; setPadding(4.dpToPx(),18.dpToPx(),4.dpToPx(),14.dpToPx()); setOnClickListener { finish() } })
        viewModel.bookData.observe(this) { book = it; renderBook(it) }
        viewModel.chapterListData.observe(this) {
            chapters = it
            renderEpisodes(it)
            if (it.isNotEmpty() && pendingResumeEpisode >= 0) {
                val index = pendingResumeEpisode.coerceIn(0, it.lastIndex)
                pendingResumeEpisode = -1
                playEpisode(index)
            }
        }
        pendingResumeEpisode = intent.getIntExtra("episodeIndex", -1)
        viewModel.initData(intent)
    }

    private fun renderBook(book: Book) {
        while (content.childCount > 1) content.removeViewAt(1)
        val hero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,8.dpToPx(),0,16.dpToPx()) }
        val cover = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        hero.addView(cover, LinearLayout.LayoutParams(128.dpToPx(),184.dpToPx()))
        val coverUrl = book.customCoverUrl?.takeIf { it.isNotBlank() } ?: book.coverUrl
        if (!coverUrl.isNullOrBlank()) Glide.with(this).load(coverUrl).placeholder(R.drawable.ic_cover_default).into(cover)
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dpToPx(),0,0,0) }
        info.addView(TextView(this).apply { text = book.name; textSize = 25f; setTextColor(primaryTextColor) })
        info.addView(TextView(this).apply { text = "作者：" + book.author.ifBlank { "暂无" }; textSize = 15f; setTextColor(primaryTextColor); alpha=.7f; setPadding(0,8.dpToPx(),0,4.dpToPx()) })
        info.addView(TextView(this).apply { text = "来源：" + book.originName.ifBlank { book.origin }; textSize=14f; setTextColor(primaryTextColor); alpha=.65f })
        hero.addView(info, LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); content.addView(hero)
        content.addView(TextView(this).apply { text=book.intro?.trim().orEmpty().ifBlank { "暂无简介" }; textSize=15f; setTextColor(primaryTextColor); setPadding(14.dpToPx(),14.dpToPx(),14.dpToPx(),14.dpToPx()); setBackgroundResource(R.drawable.bg_popup_menu) })
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                text = "▶ 立即播放"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                background = roundedBackground(true)
                setOnClickListener { playCurrent(0) }
            }, LinearLayout.LayoutParams(0, 52.dpToPx(), 1f).apply { rightMargin = 6.dpToPx() })
            addView(TextView(context).apply {
                text = "刷新目录"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                background = roundedBackground(false)
                setOnClickListener {
                    viewModel.initData(intent)
                    toastOnUi("正在刷新影视目录")
                }
            }, LinearLayout.LayoutParams(0, 52.dpToPx(), 1f).apply { leftMargin = 6.dpToPx() })
        })
        content.addView(TextView(this).apply { text="选集"; textSize=21f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(primaryTextColor); setPadding(4.dpToPx(),18.dpToPx(),4.dpToPx(),8.dpToPx()) })
    }

    private fun renderEpisodes(items: List<BookChapter>) {
        while (content.childCount > 0 && content.getChildAt(content.childCount-1).tag == "episode") content.removeViewAt(content.childCount-1)
        if (items.isEmpty()) { content.addView(TextView(this).apply { text="目录加载中或当前书源没有可用剧集"; setTextColor(primaryTextColor); alpha=.65f; tag="episode"; setPadding(4.dpToPx(),12.dpToPx(),4.dpToPx(),12.dpToPx()) }); return }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "episode" }
        items.forEachIndexed { index, chapter ->
            if (index % 2 == 0) {
                grid.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    tag = "episode_row"
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val row = grid.getChildAt(grid.childCount - 1) as LinearLayout
            row.addView(TextView(this).apply {
                text = (index + 1).toString() + ". " + chapter.title
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(primaryTextColor)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(14.dpToPx(), 0, 8.dpToPx(), 0)
                background = roundedBackground(false)
                setOnClickListener { playEpisode(index) }
            }, LinearLayout.LayoutParams(0, 46.dpToPx(), 1f).apply {
                if (index % 2 == 0) rightMargin = 4.dpToPx() else leftMargin = 4.dpToPx()
            })
        }
        content.addView(grid)
    }

    private fun roundedBackground(primary: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 18.dpToPx().toFloat()
        setColor(if (primary) io.legado.app.lib.theme.accentColor else backgroundColor)
        setStroke(1.dpToPx(), Color.argb(if (primary) 45 else 25, Color.red(primaryTextColor), Color.green(primaryTextColor), Color.blue(primaryTextColor)))
    }

    private fun playEpisode(index: Int) { playCurrent(index) }
    private fun playCurrent(index: Int = 0) {
        val b=book ?: return
        if (chapters.isEmpty()) { toastOnUi("目录尚未加载完成"); return }
        lifecycleScope.launch(IO) {
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
            AppConfig.saveVideoHistory(
                name = b.name,
                author = b.author,
                bookUrl = b.bookUrl,
                origin = b.origin,
                coverUrl = b.getDisplayCover(),
                episodeIndex = index.coerceIn(0, chapters.lastIndex),
                episodeTitle = chapters[index.coerceIn(0, chapters.lastIndex)].title
            )
            withContext(Dispatchers.Main) { startActivity<VideoPlayerActivity> { putExtra("isNew",true); putExtra("sourceKey",b.origin); putExtra("sourceType",SourceType.book); putExtra("bookUrl",b.bookUrl); putExtra("episodeIndex",index) } }
        }
    }
}