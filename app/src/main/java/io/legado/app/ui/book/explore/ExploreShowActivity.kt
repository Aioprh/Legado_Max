package io.legado.app.ui.book.explore

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ActivityExploreShowBinding
import io.legado.app.databinding.ViewLoadMoreBinding
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.LoadMoreView
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 发现列表
 */
class ExploreShowActivity : VMBaseActivity<ActivityExploreShowBinding, ExploreShowViewModel>(),
    ExploreShowAdapter.CallBack, GroupSelectDialog.CallBack {

    companion object {
        private const val REQUEST_CODE_ADD_ALL_TO_SHELF = 1001
        /** 加载下一页的冷却间隔（毫秒），滚动过快时隔 2 秒再请求 */
        private const val LOAD_COOLDOWN_MS = 2000L
        const val LAYOUT_LIST = 0
        const val LAYOUT_GRID = 1
        const val LAYOUT_WATERFALL = 2
    }

    override val binding by viewBinding(ActivityExploreShowBinding::inflate)
    override val viewModel by viewModels<ExploreShowViewModel>()

    private val adapter by lazy { ExploreShowAdapter(this, this) }
    private val loadMoreView by lazy { LoadMoreView(this) }
    private val loadMoreViewTop by lazy { LoadMoreView(this) }
    private var oldPage = -1
    private var isClearAll = false
    private var menuPage: MenuItem? = null
    private var menuSwitchLayout: MenuItem? = null
    private var menuSelectColumn: MenuItem? = null

    /** 上次发起加载下一页的时间戳，用于 2 秒冷却限制 */
    private var lastLoadTime = 0L

    /** 冷却期延迟重试的 Handler */
    private val handler = Handler(Looper.getMainLooper())

    /** 是否已有延迟重试排队中 */
    private var loadRetryScheduled = false

    /** 网格模式列数，持久化到 PreferKey.exploreShowColumn，默认 1 */
    private var columnCount: Int
        get() = getPrefInt(PreferKey.exploreShowColumn, 1)
        set(value) = putPrefInt(PreferKey.exploreShowColumn, value)

    /**
     * 布局模式，由"切换布局"菜单轮换，持久化到 PreferKey.exploreGridMode
     * 0=列表, 1=网格, 2=瀑布流；兼容旧版 Boolean 值（false→0, true→1）
     */
    private var layoutMode: Int
        get() {
            return try {
                getPrefInt(PreferKey.exploreGridMode, 0)
            } catch (_: ClassCastException) {
                if (getPrefBoolean(PreferKey.exploreGridMode, false)) LAYOUT_GRID else LAYOUT_LIST
            }
        }
        set(value) = putPrefInt(PreferKey.exploreGridMode, value)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("exploreName")
        initRecyclerView()
        viewModel.booksData.observe(this) { upData(it) }
        viewModel.addBooksData.observe(this) { upDataTop(it) }
        viewModel.initData(intent)
        viewModel.errorLiveData.observe(this) {
            loadMoreView.error(it)
        }
        viewModel.errorTopLiveData.observe(this) {
            loadMoreViewTop.error(it)
        }
        viewModel.upAdapterLiveData.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, bundleOf(it to null))
        }
        viewModel.pageLiveData.observe(this) {
            menuPage?.title = getString(R.string.menu_page, it)
        }
        viewModel.addAllToShelfResult.observe(this) { count ->
            if (count == 0) {
                toastOnUi(R.string.all_books_in_shelf)
            } else {
                toastOnUi(getString(R.string.add_books_success, count))
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explore_show, menu)
        menuPage = menu.findItem(R.id.menu_page)
        menuSwitchLayout = menu.findItem(R.id.menu_switch_layout)
        menuSelectColumn = menu.findItem(R.id.menu_select_column)
        if (layoutMode != LAYOUT_LIST) {
            menuSelectColumn?.isVisible = true
            updateColumnMenuTitle()
            adapter.layoutMode = layoutMode
            adapter.columnCount = columnCount
            applyLayoutManager(columnCount)
        }
        updateSwitchLayoutTitle()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        updateSwitchLayoutTitle()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_page -> {
                val page = viewModel.pageLiveData.value ?: 1
                NumberPickerDialog(this)
                    .setTitle(getString(R.string.change_page))
                    .setMaxValue(999)
                    .setMinValue(1)
                    .setValue(page)
                    .show {
                        if (page != it) {
                            if (oldPage == -1 && it != 1) {
                                adapter.addHeaderView {
                                    ViewLoadMoreBinding.bind(loadMoreViewTop)
                                }
                            } else if (it != 1) {
                                val layoutParams = loadMoreViewTop.layoutParams
                                if (layoutParams?.height == 0) {
                                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    loadMoreViewTop.layoutParams = layoutParams
                                }
                            }
                            oldPage = it
                            viewModel.skipPage(it)
                            isClearAll = true
                            adapter.clearItems()
                            if (!loadMoreView.hasMore) {
                                scrollToBottom(true)
                            }
                        }
                    }
            }
            R.id.menu_add_all_to_shelf -> {
                showDialogFragment(GroupSelectDialog(0, REQUEST_CODE_ADD_ALL_TO_SHELF))
            }
            R.id.menu_switch_layout -> {
                handleSwitchLayout()
            }
            R.id.menu_select_column -> {
                handleSelectColumn()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 切换布局：列表 → 网格 → 瀑布流 三轮换
     * 网格和瀑布流模式下显示选择分列菜单图标和简化卡片，列表模式下隐藏菜单图表恢复完整信息
     */
    private fun handleSwitchLayout() {
        layoutMode = (layoutMode + 1) % 3
        when (layoutMode) {
            LAYOUT_LIST -> {
                menuSelectColumn?.isVisible = false
                adapter.layoutMode = LAYOUT_LIST
                applyLayoutManager(1)
            }
            LAYOUT_GRID, LAYOUT_WATERFALL -> {
                menuSelectColumn?.isVisible = true
                if (columnCount < 1 || columnCount > 10) {
                    columnCount = 2
                }
                updateColumnMenuTitle()
                adapter.layoutMode = layoutMode
                adapter.columnCount = columnCount
                applyLayoutManager(columnCount)
            }
        }
        updateSwitchLayoutTitle()
    }

    /**
     * 弹出 NumberPickerDialog 选择列数（1-10），确认后更新布局和标题栏图标
     */
    private fun handleSelectColumn() {
        NumberPickerDialog(this)
            .setTitle(getString(R.string.select_column_count))
            .setMaxValue(10)
            .setMinValue(1)
            .setValue(columnCount)
            .show { selectedCount ->
                columnCount = selectedCount
                updateColumnMenuTitle()
                adapter.columnCount = selectedCount
                applyLayoutManager(selectedCount)
            }
    }

    /**
     * 根据列数和当前布局模式设置 RecyclerView 的 LayoutManager
     * 列表：LinearLayoutManager；网格：GridLayoutManager；瀑布流：StaggeredGridLayoutManager
     */
    private fun applyLayoutManager(count: Int) {
        binding.recyclerView.layoutManager = when {
            layoutMode == LAYOUT_LIST || count <= 1 -> {
                binding.recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
                LinearLayoutManager(this)
            }
            layoutMode == LAYOUT_WATERFALL -> {
                binding.recyclerView.itemAnimator = null
                StaggeredGridLayoutManager(count, StaggeredGridLayoutManager.VERTICAL)
            }
            else -> {
                binding.recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
                GridLayoutManager(this, count)
            }
        }
    }

    /**
     * 更新标题栏中选择分列菜单项的标题为当前列数值
     */
    private fun updateColumnMenuTitle() {
        menuSelectColumn?.title = columnCount.toString()
    }

    /**
     * 更新切换布局菜单标题为当前模式名称
     */
    private fun updateSwitchLayoutTitle() {
        val modeName = when (layoutMode) {
            LAYOUT_GRID -> getString(R.string.switch_layout_grid)
            LAYOUT_WATERFALL -> getString(R.string.switch_layout_waterfall)
            else -> getString(R.string.switch_layout_list)
        }
        menuSwitchLayout?.title = "${getString(R.string.switch_layout)}(当前:$modeName)"
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        adapter.addFooterView {
            ViewLoadMoreBinding.bind(loadMoreView)
        }
        loadMoreView.startLoad()
        loadMoreView.setOnClickListener {
            if (!loadMoreView.isLoading) {
                scrollToBottom(true)
            }
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) {
                    scrollToBottom()
                } else if (!recyclerView.canScrollVertically(-1) && dy < 0) {
                    scrollToTop()
                }
            }
        })
    }

    /**
     * 滚动到底部加载更多，列数 >3 时内置 2 秒冷却限制。
     * 冷却期内延迟重试，避免停在底部无法触发 onScrolled 导致加载卡死。
     */
    private fun scrollToBottom(forceLoad: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (layoutMode != LAYOUT_LIST && columnCount > 3 && now - lastLoadTime < LOAD_COOLDOWN_MS) {
            scheduleLoadRetry(LOAD_COOLDOWN_MS - (now - lastLoadTime))
            return
        }
        if ((loadMoreView.hasMore && !loadMoreView.isLoading && !loadMoreViewTop.isLoading) || forceLoad) {
            loadRetryScheduled = false
            lastLoadTime = now
            loadMoreView.hasMore()
            viewModel.explore()
        }
    }

    /**
     * 冷却期内延迟重试加载下一页，仅排队一次
     */
    private fun scheduleLoadRetry(delayMs: Long) {
        if (loadRetryScheduled) return
        loadRetryScheduled = true
        handler.postDelayed({
            loadRetryScheduled = false
            scrollToBottom()
        }, delayMs)
    }

    /**
     * 上滑加载上一页
     */
    private fun scrollToTop(forceLoad: Boolean = false) {
        if ((oldPage > 1 && !loadMoreView.isLoading && !loadMoreViewTop.isLoading) || forceLoad) {
            loadMoreViewTop.hasMore()
            oldPage--
            viewModel.explore(oldPage)
        }
    }

    private fun upData(books: List<SearchBook>) {
        loadMoreView.stopLoad()
        if (books.isEmpty() && adapter.isEmpty()) {
            loadMoreView.noMore(getString(R.string.empty))
        } else if (adapter.getActualItemCount() == books.size) {
            loadMoreView.noMore()
        } else {
            // 增量追加新数据，避免 setItems 的 notifyDataSetChanged 导致已渲染封面闪烁
            val oldCount = adapter.getActualItemCount()
            if (oldCount == 0) {
                adapter.setItems(books)
            } else {
                val newItems = books.subList(oldCount, books.size)
                adapter.addItems(newItems)
            }
            if (isClearAll) {
                val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(1, 0)
                isClearAll = false
            }
        }
    }

    private fun upDataTop(books: List<SearchBook>) {
        loadMoreViewTop.stopLoad()
        adapter.addItems(0, books)
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() <= 1) {
            layoutManager.scrollToPositionWithOffset(books.size, 0)
        }
        if (oldPage <= 1) {
            val layoutParams = loadMoreViewTop.layoutParams
            if (layoutParams != null) {
                layoutParams.height = 0
                loadMoreViewTop.layoutParams = layoutParams
            }
        }
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        return viewModel.isInBookShelf(book)
    }

    override fun showBookInfo(book: SearchBook) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        if (requestCode == REQUEST_CODE_ADD_ALL_TO_SHELF) {
            toastOnUi(getString(R.string.adding_books, viewModel.booksCount))
            viewModel.addAllToShelf(groupId)
        }
    }
}
