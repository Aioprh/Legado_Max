package io.legado.app.ui.main.video

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.SourceSelectDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 独立影视首页。
 *
 * 影视内容不内置任何“动漫/电影/电视剧”等分类。
 * 只读取用户启用的 BookSourceType.video 书源，并从该书源的 exploreUrl
 * 动态解析分类。这样不同影视书源可以提供完全不同的分类结构。
 */
class VideoFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private lateinit var root: LinearLayout
    private lateinit var sourceButton: TextView
    private lateinit var categoryContainer: FlexboxLayout
    private lateinit var sourceHint: TextView
    private lateinit var featuredTitle: TextView
    private lateinit var featuredContainer: LinearLayout
    private lateinit var heroContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var historyTitle: TextView
    private lateinit var categorySectionsContainer: LinearLayout

    private var currentSource: BookSourcePart? = null
    private var currentKinds: List<ExploreKind> = emptyList()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dpToPx(), 12.dpToPx(), 18.dpToPx(), 8.dpToPx())
        }

        sourceButton = TextView(requireContext()).apply {
            textSize = 18f
            setTextColor(primaryTextColor)
            setPadding(16.dpToPx(), 10.dpToPx(), 16.dpToPx(), 10.dpToPx())
            background = requireContext().getDrawable(R.drawable.bg_popup_menu)
            isClickable = true
            setOnClickListener { chooseSource() }
        }
        header.addView(sourceButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val search = TextView(requireContext()).apply {
            text = "⌕"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(primaryTextColor)
            setPadding(10.dpToPx())
            setOnClickListener { showSearchDialog() }
        }
        header.addView(search, LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx()))

        root.addView(header)

        sourceHint = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(primaryTextColor)
            setPadding(18.dpToPx(), 4.dpToPx(), 18.dpToPx(), 6.dpToPx())
            visibility = View.GONE
        }
        root.addView(sourceHint)

        val categoryScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(14.dpToPx(), 4.dpToPx(), 14.dpToPx(), 10.dpToPx())
        }
        categoryContainer = FlexboxLayout(requireContext()).apply {
            flexWrap = com.google.android.flexbox.FlexWrap.NOWRAP
        }
        categoryScroll.addView(categoryContainer)
        root.addView(categoryScroll)

        heroContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 4.dpToPx(), 16.dpToPx(), 4.dpToPx())
        }
        root.addView(heroContainer)

        historyTitle = TextView(requireContext()).apply {
            text = "继续观看"
            textSize = 22f
            setTextColor(primaryTextColor)
            setPadding(20.dpToPx(), 14.dpToPx(), 20.dpToPx(), 8.dpToPx())
            visibility = View.GONE
        }
        root.addView(historyTitle)

        val historyScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 10.dpToPx())
        }
        historyContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        historyScroll.addView(historyContainer)
        root.addView(historyScroll)

        featuredTitle = TextView(requireContext()).apply {
            text = "推荐"
            textSize = 24f
            setTextColor(primaryTextColor)
            setPadding(20.dpToPx(), 12.dpToPx(), 20.dpToPx(), 8.dpToPx())
        }
        root.addView(featuredTitle)

        val featuredScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 12.dpToPx())
        }
        featuredContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        featuredScroll.addView(featuredContainer)
        root.addView(featuredScroll)

        val desc = TextView(requireContext()).apply {
            text = "内容、分类和目录均来自当前影视书源"
            textSize = 14f
            setTextColor(primaryTextColor)
            alpha = 0.68f
            setPadding(20.dpToPx(), 0, 20.dpToPx(), 20.dpToPx())
        }
        root.addView(desc)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        renderHistory()
        loadSources()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            renderHistory()
            loadSources()
        }
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        val history = AppConfig.videoHistory
        historyTitle.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        if (history.isEmpty()) return
        history.forEach { item ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 10.dpToPx(), 0)
                isClickable = true
                setOnClickListener {
                    startActivity<io.legado.app.ui.video.VideoDetailActivity> {
                        putExtra("name", item.name)
                        putExtra("author", item.author)
                        putExtra("bookUrl", item.bookUrl)
                        putExtra("origin", item.origin)
                        putExtra("episodeIndex", item.episodeIndex)
                    }
                }
            }
            val cover = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (!item.coverUrl.isNullOrBlank()) {
                    Glide.with(this@VideoFragment).load(item.coverUrl)
                        .placeholder(R.drawable.ic_cover_default).into(this)
                }
            }
            card.addView(cover, LinearLayout.LayoutParams(72.dpToPx(), 102.dpToPx()))
            card.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10.dpToPx(), 0, 0, 0)
                addView(TextView(context).apply {
                    text = item.name.ifBlank { "未命名" }
                    textSize = 15f
                    setTextColor(primaryTextColor)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = if (item.episodeTitle.isBlank()) "第" + (item.episodeIndex + 1) + "集" else item.episodeTitle
                    textSize = 13f
                    setTextColor(primaryTextColor)
                    alpha = .62f
                    setPadding(0, 6.dpToPx(), 0, 0)
                })
            }, LinearLayout.LayoutParams(150.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT))
            historyContainer.addView(card)
        }
    }

    private fun loadSources() {
        lifecycleScope.launch {
            val sources = withContext(IO) {
                appDb.bookSourceDao.allEnabledPart
                    .filter {
                        it.enabled &&
                            it.bookSourceType == BookSourceType.video &&
                            it.enabledExplore &&
                            it.hasExploreUrl
                    }
                    .sortedBy { it.customOrder }
            }
            if (!isAdded) return@launch
            if (sources.isEmpty()) {
                currentSource = null
                sourceButton.text = "影视书源"
                sourceHint.text = "暂无启用的影视书源，请先导入并启用视频类型书源"
                sourceHint.visibility = View.VISIBLE
                categoryContainer.removeAllViews()
                return@launch
            }
            val saved = AppConfig.videoSourceUrl
            currentSource = sources.firstOrNull { it.bookSourceUrl == saved } ?: sources.first()
            sourceHint.visibility = View.GONE
            sourceButton.text = currentSource?.getDisPlayNameGroup() ?: "影视书源"
            loadCategories(currentSource)
        }
    }

    private fun chooseSource() {
        lifecycleScope.launch {
            val sources = withContext(IO) {
                appDb.bookSourceDao.allEnabledPart
                    .filter {
                        it.enabled &&
                            it.bookSourceType == BookSourceType.video &&
                            it.enabledExplore &&
                            it.hasExploreUrl
                    }
                    .sortedBy { it.customOrder }
            }
            if (sources.isEmpty()) {
                toastOnUi("暂无可用的影视书源")
                return@launch
            }
            SourceSelectDialog.show(
                requireContext(),
                "选择影视书源",
                sources,
                currentSource?.bookSourceUrl,
                { it.getDisPlayNameGroup() },
                { listOf(it.bookSourceName, it.bookSourceGroup.orEmpty()) },
                "搜索影视书源",
                { it.bookSourceUrl },
                { selected ->
                    currentSource = selected
                    AppConfig.videoSourceUrl = selected.bookSourceUrl
                    sourceButton.text = selected.getDisPlayNameGroup()
                    loadCategories(selected)
                }
            )
        }
    }

    private fun loadCategories(part: BookSourcePart?) {
        if (part == null) return
        lifecycleScope.launch {
            val kinds = withContext(IO) {
                runCatching { part.exploreKinds() }
                    .getOrElse { emptyList() }
                    .filter { !it.url.isNullOrBlank() }
            }
            if (!isAdded || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
            currentKinds = kinds
            categorySectionsContainer.removeAllViews()
            heroContainer.removeAllViews()
            featuredContainer.removeAllViews()
            renderCategories(kinds)
            loadFeatured(part, kinds.firstOrNull())
        }
    }

    private fun renderCategories(kinds: List<ExploreKind>) {
        categoryContainer.removeAllViews()
        if (kinds.isEmpty()) {
            sourceHint.text = "当前书源没有提供可解析的影视分类"
            sourceHint.visibility = View.VISIBLE
            return
        }
        sourceHint.visibility = View.GONE
        kinds.forEachIndexed { index, kind ->
            val chip = TextView(requireContext()).apply {
                text = kind.title
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                setPadding(18.dpToPx(), 9.dpToPx(), 18.dpToPx(), 9.dpToPx())
                background = requireContext().getDrawable(
                    if (index == 0) R.drawable.bg_video_chapter_item else R.drawable.bg_popup_menu
                )
                setOnClickListener { openCategory(kind) }
            }
            categoryContainer.addView(
                chip,
                FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = 8.dpToPx()
                }
            )
        }
    }

    private fun loadFeatured(part: BookSourcePart, kind: ExploreKind?) {
        featuredContainer.removeAllViews()
        featuredTitle.text = kind?.title?.takeIf { it.isNotBlank() } ?: "推荐"
        val url = kind?.url ?: run {
            sourceHint.text = "当前影视书源没有可展示的发现内容"
            sourceHint.visibility = View.VISIBLE
            return
        }
        lifecycleScope.launch {
            val books = withContext(IO) {
                runCatching {
                    WebBook.exploreBookAwait(
                        part.getBookSource() ?: return@runCatching emptyList(),
                        url,
                        1
                    ).take(12)
                }.getOrElse { emptyList() }
            }
            if (!isAdded || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
            renderFeatured(books)
            loadCategorySections(part, kinds.drop(1).take(4))
        }
    }

    private fun loadCategorySections(part: BookSourcePart, kinds: List<ExploreKind>) {
        categorySectionsContainer.removeAllViews()
        if (kinds.isEmpty()) return
        kinds.forEach { kind ->
            val title = TextView(requireContext()).apply {
                text = kind.title
                textSize = 20f
                setTextColor(primaryTextColor)
                setPadding(20.dpToPx(), 12.dpToPx(), 20.dpToPx(), 7.dpToPx())
            }
            categorySectionsContainer.addView(title)
            val scroll = HorizontalScrollView(requireContext()).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                setPadding(16.dpToPx(), 0, 16.dpToPx(), 8.dpToPx())
            }
            val cards = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            scroll.addView(cards)
            categorySectionsContainer.addView(scroll)
            lifecycleScope.launch {
                val books = withContext(IO) {
                    runCatching {
                        val source = part.getBookSource() ?: return@runCatching emptyList<SearchBook>()
                        WebBook.exploreBookAwait(source, kind.url.orEmpty(), 1).take(10)
                    }.getOrElse { emptyList() }
                }
                if (!isAdded || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
                books.forEach { book ->
                    val card = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        isClickable = true
                        setPadding(0, 0, 8.dpToPx(), 0)
                        setOnClickListener {
                            startActivity<io.legado.app.ui.video.VideoDetailActivity> {
                                putExtra("name", book.name)
                                putExtra("author", book.author)
                                putExtra("bookUrl", book.bookUrl)
                                putExtra("origin", book.origin)
                            }
                        }
                    }
                    val cover = ImageView(requireContext()).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        if (!book.coverUrl.isNullOrBlank()) {
                            Glide.with(this@VideoFragment).load(book.coverUrl)
                                .placeholder(R.drawable.ic_cover_default).into(this)
                        }
                    }
                    card.addView(cover, LinearLayout.LayoutParams(104.dpToPx(), 150.dpToPx()))
                    card.addView(TextView(requireContext()).apply {
                        text = book.name.ifBlank { "未命名" }
                        textSize = 13f
                        setTextColor(primaryTextColor)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(2.dpToPx(), 6.dpToPx(), 2.dpToPx(), 0)
                    }, LinearLayout.LayoutParams(104.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT))
                    cards.addView(card)
                }
            }
        }
    }

    private fun renderFeatured(books: List<SearchBook>) {
        featuredContainer.removeAllViews()
        heroContainer.removeAllViews()
        if (books.isEmpty()) {
            sourceHint.text = "当前分类暂时没有解析到影视内容"
            sourceHint.visibility = View.VISIBLE
            return
        }
        sourceHint.visibility = View.GONE

        val heroBook = books.first()
        val hero = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 4.dpToPx())
            isClickable = true
            background = requireContext().getDrawable(R.drawable.bg_popup_menu)
            setOnClickListener {
                startActivity<io.legado.app.ui.video.VideoDetailActivity> {
                    putExtra("name", heroBook.name)
                    putExtra("author", heroBook.author)
                    putExtra("bookUrl", heroBook.bookUrl)
                    putExtra("origin", heroBook.origin)
                }
            }
        }
        val heroCover = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (!heroBook.coverUrl.isNullOrBlank()) {
                Glide.with(this@VideoFragment).load(heroBook.coverUrl)
                    .placeholder(R.drawable.ic_cover_default).into(this)
            }
        }
        hero.addView(heroCover, LinearLayout.LayoutParams(150.dpToPx(), 210.dpToPx()))
        hero.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            addView(TextView(context).apply {
                text = heroBook.name.ifBlank { "未命名" }
                textSize = 23f
                setTextColor(primaryTextColor)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = heroBook.author.ifBlank { "影视内容" }
                textSize = 14f
                setTextColor(primaryTextColor)
                alpha = .68f
                maxLines = 2
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            })
            addView(TextView(context).apply {
                text = "来自当前影视书源 · 点击查看详情"
                textSize = 13f
                setTextColor(primaryTextColor)
                alpha = .55f
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heroContainer.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        books.forEach { book ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                setPadding(0, 0, 8.dpToPx(), 0)
                setOnClickListener {
                    startActivity<io.legado.app.ui.video.VideoDetailActivity> {
                        putExtra("name", book.name)
                        putExtra("author", book.author)
                        putExtra("bookUrl", book.bookUrl)
                        putExtra("origin", book.origin)
                    }
                }
            }
            val cover = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_popup_menu)
                if (!book.coverUrl.isNullOrBlank()) {
                    Glide.with(this@VideoFragment).load(book.coverUrl)
                        .placeholder(R.drawable.ic_cover_default).into(this)
                }
            }
            card.addView(cover, LinearLayout.LayoutParams(116.dpToPx(), 166.dpToPx()))
            card.addView(TextView(requireContext()).apply {
                text = book.name.ifBlank { "未命名" }
                textSize = 14f
                setTextColor(primaryTextColor)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(2.dpToPx(), 7.dpToPx(), 2.dpToPx(), 0)
            }, LinearLayout.LayoutParams(116.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT))
            featuredContainer.addView(card)
        }
    }

    private fun showSearchDialog() {
        val source = currentSource ?: run {
            toastOnUi("暂无可用的影视书源")
            return
        }
        val input = EditText(requireContext()).apply {
            hint = "搜索影视名称"
            setSingleLine(true)
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 8.dpToPx())
        }
        AlertDialog.Builder(requireContext())
            .setTitle("影视搜索 · ${source.bookSourceName}")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("搜索") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    val books = withContext(IO) {
                        runCatching {
                            WebBook.searchBookAwait(
                                source.getBookSource() ?: return@runCatching emptyList(),
                                key,
                                1
                            ).take(20)
                        }.getOrElse { emptyList() }
                    }
                    if (!isAdded) return@launch
                    showSearchResults(books)
                }
            }.show()
    }

    private fun showSearchResults(books: List<SearchBook>) {
        if (books.isEmpty()) {
            Toast.makeText(requireContext(), "没有搜索到结果", Toast.LENGTH_SHORT).show()
            return
        }
        val names = books.map { it.name.ifBlank { "未命名" } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("搜索结果")
            .setItems(names) { _, which ->
                val book = books[which]
                startActivity<io.legado.app.ui.video.VideoDetailActivity> {
                    putExtra("name", book.name)
                    putExtra("author", book.author)
                    putExtra("bookUrl", book.bookUrl)
                    putExtra("origin", book.origin)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun openCategory(kind: ExploreKind) {
        val source = currentSource ?: return
        val url = kind.url ?: return
        startActivity<ExploreShowActivity> {
            putExtra("sourceUrl", source.bookSourceUrl)
            putExtra("exploreUrl", url)
            putExtra("exploreName", kind.title)
            putExtra("videoMode", true)
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (::root.isInitialized) root.setPadding(
            root.paddingLeft,
            root.paddingTop,
            root.paddingRight,
            bottomPadding
        )
    }
}
