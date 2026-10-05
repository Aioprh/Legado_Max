package io.legado.app.ui.video

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor); setPadding(16.dpToPx(),0,16.dpToPx(),100.dpToPx()) }
        scroll.addView(content); setContentView(scroll)
        content.addView(TextView(this).apply { text = "‹  影视详情"; textSize = 20f; setTextColor(primaryTextColor); gravity = Gravity.CENTER_VERTICAL; setPadding(4.dpToPx(),18.dpToPx(),4.dpToPx(),14.dpToPx()); setOnClickListener { finish() } })
        viewModel.bookData.observe(this) { book = it; renderBook(it) }
        viewModel.chapterListData.observe(this) { chapters = it; renderEpisodes(it) }
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
        content.addView(TextView(this).apply { text="立即播放"; textSize=17f; gravity=Gravity.CENTER; setTextColor(primaryTextColor); setPadding(0,14.dpToPx(),0,14.dpToPx()); setBackgroundResource(R.drawable.bg_popup_menu); setOnClickListener { playCurrent() } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,52.dpToPx()).apply { topMargin=12.dpToPx(); bottomMargin=10.dpToPx() })
        content.addView(TextView(this).apply { text="选集"; textSize=21f; setTextColor(primaryTextColor); setPadding(4.dpToPx(),16.dpToPx(),4.dpToPx(),8.dpToPx()) })
    }

    private fun renderEpisodes(items: List<BookChapter>) {
        while (content.childCount > 0 && content.getChildAt(content.childCount-1).tag == "episode") content.removeViewAt(content.childCount-1)
        if (items.isEmpty()) { content.addView(TextView(this).apply { text="目录加载中或当前书源没有可用剧集"; setTextColor(primaryTextColor); alpha=.65f; tag="episode"; setPadding(4.dpToPx(),12.dpToPx(),4.dpToPx(),12.dpToPx()) }); return }
        items.forEachIndexed { index, chapter -> content.addView(TextView(this).apply { text=(index+1).toString()+". "+chapter.title; textSize=15f; setTextColor(primaryTextColor); gravity=Gravity.CENTER_VERTICAL; setPadding(16.dpToPx(),14.dpToPx(),16.dpToPx(),14.dpToPx()); setBackgroundResource(R.drawable.bg_popup_menu); tag="episode"; setOnClickListener { playEpisode(index) } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=6.dpToPx() }) }
    }

    private fun playEpisode(index: Int) { playCurrent() }
    private fun playCurrent() {
        val b=book ?: return
        if (chapters.isEmpty()) { toastOnUi("目录尚未加载完成"); return }
        lifecycleScope.launch(IO) {
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
            withContext(Dispatchers.Main) { startActivity<VideoPlayerActivity> { putExtra("isNew",true); putExtra("sourceKey",b.origin); putExtra("sourceType",SourceType.book); putExtra("bookUrl",b.bookUrl) } }
        }
    }
}