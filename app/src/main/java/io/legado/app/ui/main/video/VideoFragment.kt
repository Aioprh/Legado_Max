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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.SourceSelectDialog
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
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

    // 书源加载与内容加载各自的 Job；切换书源或页面时取消，避免旧请求回填与内存浪费
    private var sourcesJob: Job? = null
    private var categoriesJob: Job? = null
    private var featuredJob: Job? = null
    private val sectionJobs = mutableListOf<Job>()
    private var sourcesLoaded = false

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 用 ScrollView 包裹内容，保证影视首页内容超出屏幕时可以上下滚动
        val scroll = ScrollView(requireContext()).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 顶部让出状态栏高度，避免内容被状态栏遮挡
            applyStatusBarPadding()
        }
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

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
            text = getString(R.string.video_continue_watching)
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
            text = getString(R.string.video_recommend)
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

        categorySectionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(categorySectionsContainer)

        val desc = TextView(requireContext()).apply {
            text = getString(R.string.video_footer_desc)
            textSize = 14f
            setTextColor(primaryTextColor)
            alpha = 0.68f
            setPadding(20.dpToPx(), 0, 20.dpToPx(), 20.dpToPx())
        }
        root.addView(desc)

        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        // 兜底：部分设备（低版本 Android / 鸿蒙）不派发 WindowInsets，
        // 此时 applyStatusBarPadding 拿到的高度为 0，这里主动补一次状态栏高度
        view.post {
            val ctx = context ?: return@post
            if (view.paddingTop == 0) {
                view.setPadding(view.paddingLeft, ctx.statusBarHeight, view.paddingRight, view.paddingBottom)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (view == null) return
        // 历史记录每次回到页面都刷新；书源与发现内容只在首次进入时加载一次，
        // 避免 onViewCreated 与 onResume 重复触发以及来回切 Tab 造成的无谓网络请求
        renderHistory()
        if (!sourcesLoaded) {
            sourcesLoaded = true
            loadSources()
        }
    }

    override fun onDestroyView() {
        sourcesJob?.cancel()
        cancelContentLoads()
        super.onDestroyView()
    }

    private fun cancelContentLoads() {
        categoriesJob?.cancel()
        categoriesJob = null
        featuredJob?.cancel()
        featuredJob = null
        sectionJobs.forEach { it.cancel() }
        sectionJobs.clear()
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
                        .placeholder(R.drawable.image_cover_default).into(this)
                }
            }
            card.addView(cover, LinearLayout.LayoutParams(72.dpToPx(), 102.dpToPx()))
            card.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10.dpToPx(), 0, 0, 0)
                addView(TextView(context).apply {
                    text = item.name.ifBlank { getString(R.string.video_unnamed) }
                    textSize = 15f
                    setTextColor(primaryTextColor)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = if (item.episodeTitle.isBlank()) {
                        getString(R.string.video_episode_n, item.episodeIndex + 1)
                    } else {
                        item.episodeTitle
                    }
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
        sourcesJob?.cancel()
        sourcesJob = lifecycleScope.launch {
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
            // isAdded 只代表已附着，视图可能尚未创建或已销毁，直接触碰 lateinit 视图字段会崩
            if (!isAdded || view == null) return@launch
            if (sources.isEmpty()) {
                currentSource = null
                sourceButton.text = getString(R.string.video_source_default)
                sourceHint.text = getString(R.string.video_no_source_hint)
                sourceHint.visibility = View.VISIBLE
                categoryContainer.removeAllViews()
                return@launch
            }
            val saved = AppConfig.videoSourceUrl
            currentSource = sources.firstOrNull { it.bookSourceUrl == saved } ?: sources.first()
            sourceHint.visibility = View.GONE
            sourceButton.text = currentSource?.getDisPlayNameGroup() ?: getString(R.string.video_source_default)
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
            // 查询期间页面可能已 detach，需与 loadSources 保持一致做视图防护
            if (!isAdded || view == null) return@launch
            if (sources.isEmpty()) {
                toastOnUi(R.string.video_no_source_available)
                return@launch
            }
            SourceSelectDialog.show(
                requireContext(),
                getString(R.string.video_choose_source_title),
                sources,
                currentSource?.bookSourceUrl,
                { it.getDisPlayNameGroup() },
                { listOf(it.bookSourceName, it.bookSourceGroup.orEmpty()) },
                getString(R.string.video_search_source_hint),
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
        cancelContentLoads()
        categoriesJob = lifecycleScope.launch {
            val kinds = withContext(IO) {
                runCatching { part.exploreKinds() }
                    .onFailure { AppLog.put("影视分类解析失败(sourceUrl=${part.bookSourceUrl})", it) }
                    .getOrElse { emptyList() }
                    .filter { !it.url.isNullOrBlank() }
            }
            if (!isAdded || view == null || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
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
            sourceHint.text = getString(R.string.video_no_kinds_hint)
            sourceHint.visibility = View.VISIBLE
            return
        }
        sourceHint.visibility = View.GONE
        kinds.forEach { kind ->
            val chip = TextView(requireContext()).apply {
                text = kind.title
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                setPadding(18.dpToPx(), 9.dpToPx(), 18.dpToPx(), 9.dpToPx())
                // 所有分类样式保持一致；此前硬编码仅第 0 项使用深色底，导致只有第一个分类看起来像被选中
                background = requireContext().getDrawable(R.drawable.bg_popup_menu)
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
        featuredTitle.text = kind?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.video_recommend)
        val url = kind?.url ?: run {
            sourceHint.text = getString(R.string.video_no_featured_hint)
            sourceHint.visibility = View.VISIBLE
            return
        }
        featuredJob?.cancel()
        featuredJob = lifecycleScope.launch {
            val books = withContext(IO) {
                runCatching {
                    WebBook.exploreBookAwait(
                        part.getBookSource() ?: return@runCatching emptyList(),
                        url,
                        1
                    ).take(12)
                }.onFailure { AppLog.put("影视发现内容加载失败(url=$url)", it) }
                    .getOrElse { emptyList() }
            }
            if (!isAdded || view == null || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
            renderFeatured(books)
            loadCategorySections(part, currentKinds.drop(1).take(4))
        }
    }

    private fun loadCategorySections(part: BookSourcePart, kinds: List<ExploreKind>) {
        sectionJobs.forEach { it.cancel() }
        sectionJobs.clear()
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
            sectionJobs += lifecycleScope.launch {
                val books = withContext(IO) {
                    runCatching {
                        val source = part.getBookSource() ?: return@runCatching emptyList<SearchBook>()
                        WebBook.exploreBookAwait(source, kind.url.orEmpty(), 1).take(10)
                    }.onFailure { AppLog.put("影视分类列表加载失败(url=${kind.url})", it) }
                        .getOrElse { emptyList() }
                }
                if (!isAdded || view == null || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
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
                                .placeholder(R.drawable.image_cover_default).into(this)
                        }
                    }
                    card.addView(cover, LinearLayout.LayoutParams(104.dpToPx(), 150.dpToPx()))
                    card.addView(TextView(requireContext()).apply {
                        text = book.name.ifBlank { getString(R.string.video_unnamed) }
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
            sourceHint.text = getString(R.string.video_category_empty_hint)
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
                    .placeholder(R.drawable.image_cover_default).into(this)
            }
        }
        hero.addView(heroCover, LinearLayout.LayoutParams(150.dpToPx(), 210.dpToPx()))
        hero.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            addView(TextView(context).apply {
                text = heroBook.name.ifBlank { getString(R.string.video_unnamed) }
                textSize = 23f
                setTextColor(primaryTextColor)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = heroBook.author.ifBlank { getString(R.string.video_content_default) }
                textSize = 14f
                setTextColor(primaryTextColor)
                alpha = .68f
                maxLines = 2
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            })
            addView(TextView(context).apply {
                text = getString(R.string.video_from_source_hint)
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
                        .placeholder(R.drawable.image_cover_default).into(this)
                }
            }
            card.addView(cover, LinearLayout.LayoutParams(116.dpToPx(), 166.dpToPx()))
            card.addView(TextView(requireContext()).apply {
                text = book.name.ifBlank { getString(R.string.video_unnamed) }
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
            toastOnUi(R.string.video_no_source_available)
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.video_search_input_hint)
            setSingleLine(true)
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 8.dpToPx())
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.video_search_title, source.bookSourceName))
            .setView(input)
            .setNegativeButton(R.string.video_cancel, null)
            .setPositiveButton(R.string.video_search) { _, _ ->
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
                        }.onFailure { AppLog.put("影视搜索失败(key=$key)", it) }
                            .getOrElse { emptyList() }
                    }
                    if (!isAdded || view == null) return@launch
                    showSearchResults(books)
                }
            }.show()
    }

    private fun showSearchResults(books: List<SearchBook>) {
        if (books.isEmpty()) {
            Toast.makeText(requireContext(), R.string.video_search_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val names = books.map { it.name.ifBlank { getString(R.string.video_unnamed) } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.video_search_result_title)
            .setItems(names) { _, which ->
                val book = books[which]
                startActivity<io.legado.app.ui.video.VideoDetailActivity> {
                    putExtra("name", book.name)
                    putExtra("author", book.author)
                    putExtra("bookUrl", book.bookUrl)
                    putExtra("origin", book.origin)
                }
            }
            .setNegativeButton(R.string.video_close, null)
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
