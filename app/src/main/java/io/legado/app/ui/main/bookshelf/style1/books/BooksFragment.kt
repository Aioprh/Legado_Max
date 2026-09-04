package io.legado.app.ui.main.bookshelf.style1.books

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
    private var enableRefresh = true
    private var onlyUpdateRead = false
    private val bookshelfMargin by lazy { AppConfig.bookshelfMargin }
    private var itemCount = 0
    private var currentTag: String? = null
    private var smartTagFilterScroll: HorizontalScrollView? = null
    private var smartTagChipGroup: ChipGroup? = null

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
            enableRefresh = it.getBoolean("enableRefresh", true)
            onlyUpdateRead = it.getBoolean("onlyUpdateRead", false)
            binding.refreshLayout.isEnabled = enableRefresh
        }
        initRecyclerView()
        initSmartTagFilterBar()
        upRecyclerData()
    }

    private fun initSmartTagFilterBar() {
        if (smartTagFilterScroll != null) return
        val context = requireContext()
        // 项目主题不是 MaterialComponents，而 Chip/ChipGroup 初始化要求该主题，
        // 否则会抛 "The style on this component requires your app theme to be Theme.MaterialComponents"
        val chipContext: Context = ContextThemeWrapper(
            context.applicationContext,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight_DarkActionBar
        )
        smartTagChipGroup = ChipGroup(chipContext).apply {
            isSingleLine = true
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
        }
        smartTagFilterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            elevation = 4.dpToPx().toFloat()
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.background))
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
            ).apply { gravity = Gravity.TOP }
        )
        smartTagFilterScroll?.visibility = View.GONE
    }

    private fun updateSmartTagFilterBar(items: List<io.legado.app.data.dao.BookShelfDisplay>) {
        val scroll = smartTagFilterScroll ?: return
        val chipGroup = smartTagChipGroup ?: return
        if (!SmartTagConfig.isEnabled(requireContext())) {
            scroll.visibility = View.GONE
            binding.rvBookshelf.updatePadding(top = 0)
            return
        }
        val counts = linkedMapOf<String, Int>()
        items.forEach { item ->
            SmartTag.names(item.toMinimalBook(), SmartTag.ruleInfos.size).forEach { tag ->
                if (SmartTagConfig.isRuleVisible(requireContext(), tag)) counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        val tags = counts.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        ).take(12)
        chipGroup.removeAllViews()
        if (tags.isEmpty()) {
            scroll.visibility = View.GONE
            binding.rvBookshelf.updatePadding(top = 0)
            return
        }
        chipGroup.addView(Chip(chipGroup.context).apply {
            text = "全部 ${items.size}"
            isCheckable = true
            isChecked = currentTag == null
            setOnClickListener { filterBooksByTag(null) }
        })
        tags.forEach { entry ->
            chipGroup.addView(Chip(chipGroup.context).apply {
                text = "${entry.key} ${entry.value}"
                isCheckable = true
                isChecked = currentTag == entry.key
                setOnClickListener { filterBooksByTag(entry.key) }
            })
        }
        scroll.visibility = View.VISIBLE
        scroll.post {
            val height = scroll.height
            if (height > 0) binding.rvBookshelf.updatePadding(top = height + 4.dpToPx())
        }
    }

    private fun initRecyclerView() {
        if (!this::booksAdapter.isInitialized) booksAdapter = createBooksAdapter()
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        binding.rvBookshelf.setHasFixedSize(true)
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        upFastScrollerBar()
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(booksAdapter.getItems().map { it.toMinimalBook() }, onlyUpdateRead)
        }
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
        enableRefresh = enable
        binding.refreshLayout.isEnabled = enable
    }

    fun filterBooksByTag(tag: String?) {
        if (currentTag == tag) {
            updateSmartTagFilterBar(booksAdapter.getItems())
            return
        }
        currentTag = tag
        upRecyclerData()
    }

    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowShelfByGroup(groupId).map { list ->
                val filtered = currentTag?.let { tag ->
                    list.filter { item ->
                        val smartMatch = SmartTagConfig.isEnabled(requireContext()) &&
                            SmartTagConfig.isRuleVisible(requireContext(), tag) &&
                            SmartTag.names(item.toMinimalBook(), SmartTag.ruleInfos.size).contains(tag)
                        smartMatch || BookTagHelper.has(item.customTag, tag)
                    }
                } ?: list
                when (bookSort) {
                    1 -> filtered.sortedByDescending { it.latestChapterTime }
                    2 -> filtered.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    3 -> filtered.sortedBy { it.order }
                    4 -> filtered.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
                    5 -> filtered.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }
                    else -> filtered
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch { AppLog.put("书架更新出错", it) }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { list ->
                    itemCount = list.size
                    binding.tvEmptyMsg.isGone = itemCount > 0
                    binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
                    booksAdapter.setItems(list)
                    updateSmartTagFilterBar(list)
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
            updateSmartTagFilterBar(booksAdapter.getItems())
        }
    }
}
