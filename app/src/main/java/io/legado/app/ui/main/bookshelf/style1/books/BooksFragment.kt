package io.legado.app.ui.main.bookshelf.style1.books

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBooksBinding
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.SmartTag
import io.legado.app.help.book.SmartTagConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.widget.NoScrollbarHorizontalScrollView
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class BooksFragment() : BaseFragment(R.layout.fragment_books), BaseBooksAdapter.CallBack {
    constructor(position: Int, group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        bundle.putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBooksBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()
    private var bookLayout = AppConfig.bookLayout
    private lateinit var booksAdapter: BaseBooksAdapter<*>
    private var booksFlowJob: Job? = null
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private val bookshelfMargin by lazy { AppConfig.bookshelfMargin }
    private var itemCount = 0
    private var currentTag: String? = null
    private var smartTagFilterScroll: HorizontalScrollView? = null
    private var smartTagChipGroup: LinearLayout? = null
    // 全局智能标签集合：基于全部书架书籍计算，切换分组时标签集合保持稳定，仅数字跟随当前分组
    private var globalSmartTags: List<String> = emptyList()
    private var globalTagsJob: Job? = null
    private var lastAllItems: List<io.legado.app.data.dao.BookShelfDisplay> = emptyList()

    private fun createBooksAdapter(): BaseBooksAdapter<*> = when (AppConfig.bookLayout) {
        0 -> BooksAdapterList(requireContext(), this, this, viewLifecycleOwner.lifecycle)
        1 -> BooksAdapterList2(requireContext(), this, this, viewLifecycleOwner.lifecycle)
        else -> BooksAdapterGrid(requireContext(), this)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position", 0)
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            binding.refreshLayout.isEnabled = false
        }
        initRecyclerView()
        initSmartTagFilterBar()
        initGlobalSmartTags()
        upRecyclerData()
    }

    fun performRefresh() {
        activityViewModel.upToc(booksAdapter.getItems().map { it.toMinimalBook() }, false)
    }

    private fun initSmartTagFilterBar() {
        if (smartTagFilterScroll != null) return
        val context = requireContext()
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val glassSurface = if (isNight) 0x661A1A1A else 0xB8FFFFFF.toInt()
        val glassStroke = if (isNight) 0x52FFFFFF else 0x66FFFFFF

        smartTagChipGroup = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dpToPx(), 7.dpToPx(), 12.dpToPx(), 7.dpToPx())
        }
        smartTagFilterScroll = NoScrollbarHorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = false
            setFadingEdgeLength(0)
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            clipToPadding = true
            clipChildren = true
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        22.dpToPx().toFloat()
                    )
                }
            }
            elevation = 6.dpToPx().toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22.dpToPx().toFloat()
                setColor(glassSurface)
            }
            addView(
                smartTagChipGroup,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        binding.root.addView(
            smartTagFilterScroll,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                leftMargin = 8.dpToPx()
                rightMargin = 8.dpToPx()
                topMargin = 4.dpToPx()
            }
        )
        smartTagFilterScroll?.visibility = View.GONE
    }

    /**
     * 订阅全部书架书籍，计算全局智能标签集合（标签名字全局一致）。
     * 切换分组页面时标签集合保持不变，标签后的数字由 updateSmartTagFilterBar 按当前分组统计。
     */
    private fun initGlobalSmartTags() {
        globalTagsJob?.cancel()
        globalTagsJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowAll()
                .flowWithLifecycleAndDatabaseChangeFirst(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.STARTED,
                    AppDatabase.BOOK_TABLE_NAME
                ).catch { AppLog.put("全局标签计算出错", it) }
                .flowOn(Dispatchers.Default)
                .collect { allBooks ->
                    val ctx = context ?: return@collect
                    val counts = linkedMapOf<String, Int>()
                    allBooks.forEach { book ->
                        SmartTag.names(book, ctx, Int.MAX_VALUE).forEach { tag ->
                            if (SmartTagConfig.isRuleVisible(ctx, tag)) {
                                counts[tag] = (counts[tag] ?: 0) + 1
                            }
                        }
                    }
                    globalSmartTags = counts.entries.sortedWith(
                        compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
                    ).take(12).map { it.key }
                    if (isAdded) updateSmartTagFilterBar(lastAllItems)
                }
        }
    }

    private fun createSmartTagChip(text: String, checked: Boolean, onClick: () -> Unit): TextView =
        TextView(smartTagChipGroup?.context ?: requireContext()).apply {
            val context = this.context
            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val normalSurface = if (isNight) 0x421A1A1A else 0x78FFFFFF
            val normalStroke = if (isNight) 0x4AFFFFFF else 0x66FFFFFF
            val selectedSurface = (accentColor and 0x00FFFFFF) or 0xC0000000.toInt()

            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = 34.dpToPx()
            setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18.dpToPx().toFloat()
                setColor(if (checked) selectedSurface else normalSurface)
                setStroke(1.dpToPx(), if (checked) 0x55FFFFFF else normalStroke)
            }
            setTextColor(if (checked) ContextCompat.getColor(context, R.color.white) else ContextCompat.getColor(context, R.color.secondaryText))
            contentDescription = text
            elevation = if (checked) 3.dpToPx().toFloat() else 1.dpToPx().toFloat()
        }

    private fun updateSmartTagFilterBar(items: List<io.legado.app.data.dao.BookShelfDisplay>) {
        lastAllItems = items
        val scroll = smartTagFilterScroll ?: return
        val chipGroup = smartTagChipGroup ?: return
        val context = context ?: return
        if (!SmartTagConfig.isEnabled(context)) {
            scroll.visibility = View.GONE
            binding.rvBookshelf.updatePadding(top = 0)
            return
        }
        val tags = globalSmartTags
        if (tags.isEmpty()) {
            scroll.visibility = View.GONE
            binding.rvBookshelf.updatePadding(top = 0)
            return
        }

        // 标签集合全局一致，数字按当前分组的完整书籍集合统计
        val counts = linkedMapOf<String, Int>()
        items.forEach { item ->
            SmartTag.names(item.toMinimalBook(), context, Int.MAX_VALUE).forEach { tag ->
                if (SmartTagConfig.isRuleVisible(context, tag)) counts[tag] = (counts[tag] ?: 0) + 1
            }
        }

        chipGroup.removeAllViews()
        chipGroup.addView(createSmartTagChip("全部  ${items.size}", currentTag == null) {
            filterBooksByTag(null)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = 6.dpToPx()
        })
        tags.forEach { tag ->
            chipGroup.addView(createSmartTagChip("$tag  ${counts[tag] ?: 0}", currentTag == tag) {
                filterBooksByTag(tag)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 6.dpToPx()
            })
        }

        scroll.visibility = View.VISIBLE
        scroll.doOnLayout {
            binding.rvBookshelf.updatePadding(top = it.height + 6.dpToPx())
        }
    }

    private fun initRecyclerView() {
        if (!this::booksAdapter.isInitialized) booksAdapter = createBooksAdapter()
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        binding.rvBookshelf.setHasFixedSize(true)
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        upFastScrollerBar()
        if (bookLayout >= 2) {
            binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookLayout)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksGridRecycledViewPool)
        } else if (bookLayout == 1) {
            binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksList2RecycledViewPool)
        } else {
            binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksListRecycledViewPool)
        }
        booksAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvBookshelf.adapter = booksAdapter
        while (binding.rvBookshelf.itemDecorationCount > 0) binding.rvBookshelf.removeItemDecorationAt(0)
        binding.rvBookshelf.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val marginFirst = bookshelfMargin + 24
            private val marginNormal = bookshelfMargin
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                if (position == RecyclerView.NO_POSITION) return
                if (bookLayout >= 2) {
                    val rowIndex = position / bookLayout
                    val lastRowIndex = if (itemCount > 0) (itemCount - 1) / bookLayout else 0
                    if (rowIndex == 0 && rowIndex == lastRowIndex) {
                        outRect.set(bookshelfMargin, marginFirst, bookshelfMargin, marginFirst)
                    } else when (rowIndex) {
                        0 -> outRect.set(bookshelfMargin, marginFirst, bookshelfMargin, bookshelfMargin)
                        lastRowIndex -> outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, marginFirst)
                        else -> outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, bookshelfMargin)
                    }
                } else {
                    if (position == 0 && position == itemCount - 1) outRect.set(0, marginFirst, 0, marginFirst)
                    else when (position) {
                        0 -> outRect.set(0, marginFirst, 0, marginNormal)
                        itemCount - 1 -> outRect.set(0, marginNormal, 0, marginFirst)
                        else -> outRect.set(0, marginNormal, 0, marginNormal)
                    }
                }
            }
        })
        startLastUpdateTimeJob()
    }

    private fun upFastScrollerBar() {
        val showFastScroller = AppConfig.showBookshelfFastScroller
        binding.rvBookshelf.setFastScrollEnabled(showFastScroller)
        binding.rvBookshelf.isVerticalScrollBarEnabled = !showFastScroller
        if (!showFastScroller) binding.rvBookshelf.scrollBarSize = ViewConfiguration.get(requireContext()).scaledScrollBarSize
    }

    fun updateMainBottomPadding(bottomPadding: Int) {
        if (view == null) return
        binding.rvBookshelf.clipToPadding = false
        binding.rvBookshelf.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        binding.rvBookshelf.updatePadding(bottom = bottomPadding)
        binding.rvBookshelf.refreshFastScrollerLayout()
    }

    fun upBookSort(sort: Int) {
        binding.root.post {
            arguments?.putInt("bookSort", sort)
            bookSort = sort
            upRecyclerData()
        }
    }

    fun setEnableRefresh(enable: Boolean) {
        binding.refreshLayout.isEnabled = false
    }

    fun filterBooksByTag(tag: String?) {
        // 通知父 Fragment 记录跨分组共享的选中标签，使切换分组后选中态保持跟随
        (parentFragment as? BookshelfFragment1)?.onTagSelected(tag)
        if (currentTag == tag) {
            upRecyclerData()
            return
        }
        currentTag = tag
        upRecyclerData()
    }

    /**
     * 切换分组页面时由父 Fragment 调用：同步共享选中标签。
     * 标签集合全局一致，这里同步选中态并刷新当前分组的数字。
     */
    fun applySharedTag(tag: String?) {
        if (currentTag == tag) {
            updateSmartTagFilterBar(lastAllItems)
            return
        }
        currentTag = tag
        upRecyclerData()
    }

    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        val context = requireContext()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowShelfByGroup(groupId).map { list ->
                val filtered = currentTag?.let { tag ->
                    list.filter { item ->
                        val smartMatch = SmartTagConfig.isEnabled(context) &&
                            SmartTagConfig.isRuleVisible(context, tag) &&
                            SmartTag.names(item.toMinimalBook(), context, Int.MAX_VALUE).contains(tag)
                        smartMatch || BookTagHelper.has(item.customTag, tag)
                    }
                } ?: list
                val sorted = when (bookSort) {
                    1 -> filtered.sortedByDescending { it.latestChapterTime }
                    2 -> filtered.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    3 -> filtered.sortedBy { it.order }
                    4 -> filtered.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
                    5 -> filtered.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }
                    else -> filtered
                }
                list to sorted
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch { AppLog.put("书架更新出错", it) }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { (allItems, list) ->
                    itemCount = list.size
                    binding.tvEmptyMsg.isGone = itemCount > 0
                    booksAdapter.setItems(list)
                    // 标签栏始终根据当前分组的完整书籍集合计算，不使用已经过滤后的列表。
                    // 这样点击一个标签后，其他标签不会因为当前结果集变小而消失。
                    updateSmartTagFilterBar(allItems)
                }
        }
    }

    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!AppConfig.showLastUpdateTime || bookLayout >= 2) return
        upLastUpdateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    booksAdapter.upLastUpdateTime()
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> = booksAdapter.getItems().map { it.toMinimalBook() }

    fun gotoTop() {
        if (AppConfig.isEInkMode) binding.rvBookshelf.scrollToPosition(0)
        else binding.rvBookshelf.smoothScrollToPosition(0)
    }

    fun getBooksCount(): Int = booksAdapter.itemCount

    override fun onDestroyView() {
        globalTagsJob?.cancel()
        smartTagFilterScroll = null
        smartTagChipGroup = null
        super.onDestroyView()
        binding.rvBookshelf.setItemViewCacheSize(0)
        binding.rvBookshelf.adapter = null
    }

    override fun open(book: Book) = startActivityForBook(book)

    override fun openBookInfo(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    override fun isUpdate(bookUrl: String): Boolean = activityViewModel.isUpdate(bookUrl)

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) { booksAdapter.notification(it) }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            bookLayout = AppConfig.bookLayout
            val oldItems = booksAdapter.getItems()
            val newAdapter = createBooksAdapter()
            if (newAdapter::class != booksAdapter::class) {
                booksAdapter = newAdapter
                booksAdapter.setItems(oldItems)
            }
            initRecyclerView()
            booksAdapter.notifyDataSetChanged()
            startLastUpdateTimeJob()
            upFastScrollerBar()
            updateSmartTagFilterBar(lastAllItems)
        }
    }
}