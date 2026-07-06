package io.legado.app.ui.book.explore

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
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
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.LoadMoreView
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.blockrule.BlockRuleConfigDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.BookBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.lib.theme.accentColor
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

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
    private var menuShowCategoryTab: MenuItem? = null
    private var menuPreload: MenuItem? = null
    /** 当前书源 URL，用于按书源隔离布局配置 */
    private val sourceUrl: String by lazy { intent.getStringExtra("sourceUrl") ?: "" }
    /** 是否显示屏蔽进度指示器 */
    private var showBlockProgress: Boolean
        get() = getPrefBoolean(PreferKey.blockRuleShowProgress, false)
        set(value) = putPrefBoolean(PreferKey.blockRuleShowProgress, value)
    /** 当前被屏蔽的书籍数量，用于进度指示器 */
    private var blockedCount by mutableIntStateOf(0)
    /** 屏蔽进度悬浮芯片 ComposeView */
    private var blockProgressComposeView: ComposeView? = null
    /** 上次发起加载下一页的时间戳，用于 2 秒冷却限制 */
    private var lastLoadTime = 0L

    /** 书籍底部弹窗状态 */
    private var showBookSheet by mutableStateOf(false)
    private var selectedBook by mutableStateOf<SearchBook?>(null)
    private var selectedBookShelfState by mutableStateOf(BookShelfState.NOT_IN_SHELF)
    /** 书籍弹窗 ComposeView */
    private var bookSheetComposeView: ComposeView? = null

    /** 冷却期延迟重试的 Handler */
    private val handler = Handler(Looper.getMainLooper())

    /** 是否已有延迟重试排队中 */
    private var loadRetryScheduled = false

    /** 网格模式列数，按书源持久化，默认 2 */
    private var columnCountGrid: Int
        get() = getPrefInt("${PreferKey.exploreShowColumn}_${sourceUrl}", 2)
        set(value) = putPrefInt("${PreferKey.exploreShowColumn}_${sourceUrl}", value)

    /** 瀑布流模式列数，按书源持久化，默认 2 */
    private var columnCountWaterfall: Int
        get() = getPrefInt("${PreferKey.exploreShowColumnWaterfall}_${sourceUrl}", 2)
        set(value) = putPrefInt("${PreferKey.exploreShowColumnWaterfall}_${sourceUrl}", value)

    /** 是否显示分类Tab，按书源持久化 */
    private var showCategoryTab: Boolean
        get() = getPrefBoolean("${PreferKey.exploreShowCategoryTab}_${sourceUrl}", false)
        set(value) = putPrefBoolean("${PreferKey.exploreShowCategoryTab}_${sourceUrl}", value)

    /** 预加载模式，按书源持久化：0=仅当前分类，1=当前分类+相邻分类 */
    private var preloadMode: Int
        get() = getPrefInt("${PreferKey.exploreShowPreload}_${sourceUrl}", 0)
        set(value) = putPrefInt("${PreferKey.exploreShowPreload}_${sourceUrl}", value)

    /** 分类Tab列表 */
    private val exploreKinds = mutableListOf<ExploreKind>()
    private val tabRows = mutableListOf<LinearLayout>()
    private val tabScrollViews = mutableListOf<HorizontalScrollView>()
    private var maxTagsPerRow = 10
    private val orientation by lazy { resources.configuration.orientation }
    /** 当前选中的分类索引 */
    private var currentCategoryIndex by mutableIntStateOf(0)
    /** 手势检测器，用于左右滑动切换分类 */
    private lateinit var gestureDetector: GestureDetector
    /** 最小滑动距离阈值（dp） */
    private val minSwipeDistance = 100
    /** 每个分类的滚动位置缓存（分类URL -> 滚动位置和偏移量） */
    private data class ScrollState(val position: Int, val offset: Int)
    private val scrollPositionCache = mutableMapOf<String, ScrollState>()

    /**
     * 布局模式，由"切换布局"菜单轮换，按书源持久化
     * 0=列表, 1=网格, 2=瀑布流
     */
    private var layoutMode: Int
        get() = getPrefInt("${PreferKey.exploreGridMode}_${sourceUrl}", 0)
        set(value) = putPrefInt("${PreferKey.exploreGridMode}_${sourceUrl}", value)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("exploreName")
        initRecyclerView()
        // 初始化手势检测器
        initGestureDetector()
        viewModel.booksData.observe(this) { upData(it) }
        viewModel.addBooksData.observe(this) { upDataTop(it) }
        viewModel.blockRulesRefreshData.observe(this) { refreshDataAfterBlock(it) }
        viewModel.blockedCountData.observe(this) { count ->
            blockedCount = count
            updateBlockProgressChip()
        }
        // 观察分类列表变化，更新Tab显示
        viewModel.exploreKindsData.observe(this) { kinds ->
            exploreKinds.clear()
            exploreKinds.addAll(kinds)
            if (showCategoryTab && exploreKinds.isNotEmpty()) {
                setupMultiLineTabs()
                binding.tabsContainer.visible()
            } else {
                binding.tabsContainer.gone()
            }
        }
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
        menuShowCategoryTab = menu.findItem(R.id.menu_show_category_tab)
        menuPreload = menu.findItem(R.id.menu_preload)
        if (layoutMode != LAYOUT_LIST) {
            menuSelectColumn?.isVisible = true
            val count = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
            updateColumnMenuTitle()
            adapter.layoutMode = layoutMode
            adapter.columnCount = count
            applyLayoutManager(count)
        }
        updateSwitchLayoutTitle()
        // 初始化分类Tab和预加载菜单状态
        menuShowCategoryTab?.isChecked = showCategoryTab
        menuPreload?.isVisible = showCategoryTab
        menuPreload?.isChecked = preloadMode == 1
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
            R.id.menu_block_rule -> {
                showBlockRuleConfig()
            }
            R.id.menu_select_column -> {
                handleSelectColumn()
            }
            R.id.menu_show_category_tab -> {
                showCategoryTab = !showCategoryTab
                item.isChecked = showCategoryTab
                menuPreload?.isVisible = showCategoryTab
                if (showCategoryTab && exploreKinds.isNotEmpty()) {
                    setupMultiLineTabs()
                    binding.tabsContainer.visible()
                } else {
                    binding.tabsContainer.gone()
                }
            }
            R.id.menu_preload -> {
                preloadMode = if (preloadMode == 0) 1 else 0
                item.isChecked = preloadMode == 1
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 打开屏蔽规则配置弹窗
     */
    private fun showBlockRuleConfig() {
        val dialog = BlockRuleConfigDialog()
        dialog.sourceUrl = sourceUrl
        dialog.allBooks = viewModel.allBooksList
        dialog.onRulesChanged = {
            viewModel.applyBlockRules(sourceUrl)
        }
        dialog.onShowProgressChanged = {
            showBlockProgress = it
            updateBlockProgressChip()
        }
        dialog.show(supportFragmentManager, "exploreBlockRuleConfig")
    }

    /**
     * 更新屏蔽进度悬浮芯片的显示状态
     * 芯片位于列表上方右侧，点击可打开屏蔽规则配置
     */
    private fun updateBlockProgressChip() {
        val contentView = binding.contentView
        if (showBlockProgress && blockedCount > 0) {
            if (blockProgressComposeView == null) {
                blockProgressComposeView = ComposeView(this).also { composeView ->
                    composeView.setContent {
                        LegadoTheme {
                            Surface(
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shadowElevation = 4.dp,
                                onClick = { showBlockRuleConfig() }
                            ) {
                                Text(
                                    text = getString(R.string.explore_block_rule_progress_text, blockedCount),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    val params = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    }
                    contentView.addView(composeView, params)
                }
            }
        } else {
            blockProgressComposeView?.let {
                contentView.removeView(it)
                blockProgressComposeView = null
            }
        }
    }

    /**
     * 更新书籍底部弹窗的显示状态
     */
    private fun updateBookSheetView() {
        val contentView = binding.contentView
        if (showBookSheet) {
            if (bookSheetComposeView == null) {
                bookSheetComposeView = ComposeView(this).also { composeView ->
                    composeView.setContent {
                        LegadoTheme {
                            BookBottomSheet(
                                show = showBookSheet,
                                book = selectedBook,
                                shelfState = selectedBookShelfState,
                                onDismiss = {
                                    showBookSheet = false
                                    updateBookSheetView()
                                },
                                onAddToShelf = { book ->
                                    viewModel.addToShelf(book)
                                },
                                onShowInfo = { book ->
                                    startActivity<BookInfoActivity> {
                                        putExtra("name", book.name)
                                        putExtra("author", book.author)
                                        putExtra("bookUrl", book.bookUrl)
                                    }
                                }
                            )
                        }
                    }
                    val params = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    contentView.addView(composeView, params)
                }
            }
        } else {
            bookSheetComposeView?.let {
                contentView.removeView(it)
                bookSheetComposeView = null
            }
        }
    }

    /**
     * 切换布局：列表 → 网格 → 瀑布流 三轮换
     * 网格模式下显示选择分列菜单图标和简化卡片（仅封面+书名）；
     * 瀑布流模式下显示分列菜单图标和完整信息卡片（封面+书名+作者+分类+最新章节+简介）；
     * 列表模式下隐藏菜单图标恢复完整信息
     */
    private fun handleSwitchLayout() {
        val savedPosition = findFirstVisibleItemPosition()
        layoutMode = (layoutMode + 1) % 3
        when (layoutMode) {
            LAYOUT_LIST -> {
                menuSelectColumn?.isVisible = false
                adapter.layoutMode = LAYOUT_LIST
                applyLayoutManager(1)
            }
            LAYOUT_GRID -> {
                menuSelectColumn?.isVisible = true
                if (columnCountGrid < 1 || columnCountGrid > 10) {
                    columnCountGrid = 2
                }
                updateColumnMenuTitle()
                adapter.layoutMode = layoutMode
                adapter.columnCount = columnCountGrid
                applyLayoutManager(columnCountGrid)
            }
            LAYOUT_WATERFALL -> {
                menuSelectColumn?.isVisible = true
                if (columnCountWaterfall < 1 || columnCountWaterfall > 10) {
                    columnCountWaterfall = 2
                }
                updateColumnMenuTitle()
                adapter.layoutMode = layoutMode
                adapter.columnCount = columnCountWaterfall
                applyLayoutManager(columnCountWaterfall)
            }
        }
        restoreScrollPosition(savedPosition)
        updateSwitchLayoutTitle()
    }

    /**
     * 弹出 NumberPickerDialog 选择列数（1-10），确认后更新布局和标题栏图标
     * 当前为网格模式时设置网格列数，瀑布流模式时设置瀑布流列数
     */
    private fun handleSelectColumn() {
        val currentCount = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
        val savedPosition = findFirstVisibleItemPosition()
        NumberPickerDialog(this)
            .setTitle(getString(R.string.select_column_count))
            .setMaxValue(10)
            .setMinValue(1)
            .setValue(currentCount)
            .show { selectedCount ->
                if (layoutMode == LAYOUT_WATERFALL) {
                    columnCountWaterfall = selectedCount
                } else {
                    columnCountGrid = selectedCount
                }
                updateColumnMenuTitle()
                adapter.columnCount = selectedCount
                applyLayoutManager(selectedCount)
                restoreScrollPosition(savedPosition)
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
     * 获取当前 LayoutManager 中第一个可见项的位置（用于切换布局/列数后恢复）
     */
    private fun findFirstVisibleItemPosition(): Int {
        val layoutManager = binding.recyclerView.layoutManager ?: return 0
        return when (layoutManager) {
            is StaggeredGridLayoutManager -> {
                val positions = IntArray(layoutManager.spanCount)
                layoutManager.findFirstVisibleItemPositions(positions)
                positions.minOrNull() ?: 0
            }
            is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
            else -> 0
        }
    }

    /**
     * 切换布局/列数后将 RecyclerView 滚动到之前的位置
     * 只恢复有效位置，避免越界
     */
    private fun restoreScrollPosition(position: Int) {
        if (position < 0) return
        val layoutManager = binding.recyclerView.layoutManager ?: return
        if (position >= adapter.itemCount) return
        layoutManager.scrollToPosition(position)
    }

    /**
     * 更新标题栏中选择分列菜单项的标题为当前布局模式对应的列数值
     */
    private fun updateColumnMenuTitle() {
        val count = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
        menuSelectColumn?.title = count.toString()
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
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                // 在滚动停止时保存位置，确保保存最终位置
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    saveCurrentScrollPosition()
                }
            }
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // 检查是否滚动到底部，触发加载更多
                if (!recyclerView.canScrollVertically(1)) {
                    scrollToBottom()
                } else if (!recyclerView.canScrollVertically(-1) && dy < 0) {
                    scrollToTop()
                }
            }
        })
    }

    /**
     * 保存当前分类的滚动位置（包含位置和偏移量）
     */
    private fun saveCurrentScrollPosition() {
        if (!showCategoryTab || exploreKinds.isEmpty()) return
        val currentKind = exploreKinds.getOrNull(currentCategoryIndex)
        if (currentKind != null && currentKind.url != null) {
            val scrollState = getCurrentScrollState()
            scrollPositionCache[currentKind.url] = scrollState
        }
    }

    /**
     * 获取当前滚动状态（位置和偏移量）
     */
    private fun getCurrentScrollState(): ScrollState {
        val layoutManager = binding.recyclerView.layoutManager ?: return ScrollState(0, 0)
        return when (layoutManager) {
            is StaggeredGridLayoutManager -> {
                val positions = IntArray(layoutManager.spanCount)
                layoutManager.findFirstVisibleItemPositions(positions)
                val position = positions.minOrNull() ?: 0
                // 获取第一个可见项的视图以计算偏移量
                val firstView = layoutManager.findViewByPosition(position)
                val offset = if (firstView != null) {
                    // 计算视图顶部相对于RecyclerView顶部的偏移量
                    firstView.top - binding.recyclerView.paddingTop
                } else 0
                ScrollState(position, offset)
            }
            is LinearLayoutManager -> {
                val position = layoutManager.findFirstVisibleItemPosition()
                // 获取第一个可见项的视图以计算偏移量
                val firstView = layoutManager.findViewByPosition(position)
                val offset = if (firstView != null) {
                    // 计算视图顶部相对于RecyclerView顶部的偏移量
                    firstView.top - binding.recyclerView.paddingTop
                } else 0
                ScrollState(position, offset)
            }
            else -> ScrollState(0, 0)
        }
    }

    /**
     * 恢复指定分类的滚动位置（使用位置和偏移量精确恢复）
     */
    private fun restoreScrollPosition(categoryUrl: String?) {
        if (categoryUrl == null) {
            binding.recyclerView.scrollToPosition(0)
            return
        }
        val savedState = scrollPositionCache[categoryUrl]
        if (savedState != null && savedState.position > 0 && savedState.position < adapter.itemCount) {
            val layoutManager = binding.recyclerView.layoutManager
            // 使用 scrollToPositionWithOffset 精确恢复位置
            when (layoutManager) {
                is StaggeredGridLayoutManager -> {
                    layoutManager.scrollToPositionWithOffset(savedState.position, savedState.offset)
                }
                is LinearLayoutManager -> {
                    layoutManager.scrollToPositionWithOffset(savedState.position, savedState.offset)
                }
                else -> {
                    binding.recyclerView.scrollToPosition(savedState.position)
                }
            }
        } else {
            binding.recyclerView.scrollToPosition(0)
        }
    }

    /**
     * 初始化手势检测器，用于左右滑动切换分类
     */
    private fun initGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // 只有在显示分类Tab时才支持手势切换
                if (!showCategoryTab || exploreKinds.isEmpty()) return false
                
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                
                // 判断是否为水平滑动（水平距离大于垂直距离）
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > minSwipeDistance.dpToPx()) {
                    // 判断滑动方向
                    if (dx > 0) {
                        // 向右滑动，切换到前一个分类
                        switchToPreviousCategory()
                        return true
                    } else {
                        // 向左滑动，切换到后一个分类
                        switchToNextCategory()
                        return true
                    }
                }
                return false
            }
        })
        
        // 为RecyclerView添加触摸拦截
        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // 将触摸事件传递给手势检测器
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    /**
     * 切换到前一个分类
     */
    private fun switchToPreviousCategory() {
        if (currentCategoryIndex > 0) {
            // 保存当前分类的滚动位置
            saveCurrentScrollPosition()
            val newIndex = currentCategoryIndex - 1
            val kind = exploreKinds[newIndex]
            val url = kind.url ?: return
            // 模拟Tab点击
            currentCategoryIndex = newIndex
            updateTabSelection(newIndex)
            adapter.clearItems()
            loadMoreView.hasMore()
            loadMoreView.startLoad()
            viewModel.switchCategory(
                newUrl = url,
                exploreName = kind.title,
                preload = preloadMode == 1,
                allKinds = exploreKinds
            )
            binding.titleBar.title = kind.title
        }
    }

    /**
     * 切换到后一个分类
     */
    private fun switchToNextCategory() {
        if (currentCategoryIndex < exploreKinds.size - 1) {
            // 保存当前分类的滚动位置
            saveCurrentScrollPosition()
            val newIndex = currentCategoryIndex + 1
            val kind = exploreKinds[newIndex]
            val url = kind.url ?: return
            // 模拟Tab点击
            currentCategoryIndex = newIndex
            updateTabSelection(newIndex)
            adapter.clearItems()
            loadMoreView.hasMore()
            loadMoreView.startLoad()
            viewModel.switchCategory(
                newUrl = url,
                exploreName = kind.title,
                preload = preloadMode == 1,
                allKinds = exploreKinds
            )
            binding.titleBar.title = kind.title
        }
    }

    /**
     * 滚动到底部加载更多，非列表模式下列数 >3 时内置 2 秒冷却限制。
     * 冷却期内延迟重试，避免停在底部无法触发 onScrolled 导致加载卡死。
     */
    private fun scrollToBottom(forceLoad: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val currentCount = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
        if (layoutMode != LAYOUT_LIST && currentCount > 3 && now - lastLoadTime < LOAD_COOLDOWN_MS) {
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
                // 数据加载完成后，延迟恢复滚动位置（等待布局完成）
                if (showCategoryTab && exploreKinds.isNotEmpty()) {
                    val currentKind = exploreKinds.getOrNull(currentCategoryIndex)
                    binding.recyclerView.post {
                        restoreScrollPosition(currentKind?.url)
                    }
                }
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

    /**
     * 屏蔽规则变化后全量刷新列表，避免 subList 越界
     */
    private fun refreshDataAfterBlock(books: List<SearchBook>) {
        loadMoreView.stopLoad()
        if (books.isEmpty()) {
            loadMoreView.noMore(getString(R.string.empty))
        } else {
            adapter.setItems(books)
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

    override fun getBookShelfState(book: SearchBook): BookShelfState {
        return viewModel.getBookShelfState(book)
    }

    override fun showBookInfo(book: SearchBook) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }

    override fun onBookLongClick(book: SearchBook) {
        selectedBook = book
        selectedBookShelfState = viewModel.getBookShelfState(book)
        showBookSheet = true
        updateBookSheetView()
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        if (requestCode == REQUEST_CODE_ADD_ALL_TO_SHELF) {
            toastOnUi(getString(R.string.adding_books, viewModel.booksCount))
            viewModel.addAllToShelf(groupId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清除所有缓存，避免内存泄漏
        scrollPositionCache.clear()
        viewModel.clearPreloadCache()
    }

    /**
     * 设置多行Tab布局（参考订阅源界面的实现）
     * 最多3行，横屏最多2行，采用订阅源界面的视觉样式
     */
    private fun setupMultiLineTabs() {
        val tabsContainer = binding.tabsContainer
        tabsContainer.removeAllViews()
        tabRows.clear()
        tabScrollViews.clear()
        if (exploreKinds.isEmpty()) {
            tabsContainer.gone()
            return
        }
        // 动态计算每行标签数量，最多3行
        var rowCount = when {
            exploreKinds.size <= 10 -> 1
            exploreKinds.size <= 20 -> 2
            else -> 3
        }
        if (rowCount > 1 && orientation == Configuration.ORIENTATION_LANDSCAPE) rowCount-- // 横屏最多2行
        maxTagsPerRow = (exploreKinds.size + rowCount - 1) / rowCount
        exploreKinds.chunked(maxTagsPerRow).forEachIndexed { rowIndex, rowItems ->
            // 创建横向滚动容器
            val scrollView = HorizontalScrollView(this).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    // 只有非最后一行才有底部间距
                    bottomMargin = if (rowIndex < rowCount - 1) 2.dpToPx() else 0
                }
                tabScrollViews.add(this)
            }
            // 创建行容器
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            // 添加标签到行
            rowItems.forEachIndexed { indexInRow, kind ->
                val globalIndex = rowIndex * maxTagsPerRow + indexInRow
                val tabView = createTabView(kind.title, globalIndex, kind.url ?: "")
                rowLayout.addView(tabView)
            }
            scrollView.addView(rowLayout)
            tabsContainer.addView(scrollView)
            tabRows.add(rowLayout)
        }
        // 初始选中状态：找到当前URL对应的分类索引
        val currentUrl = intent.getStringExtra("exploreUrl") ?: ""
        currentCategoryIndex = exploreKinds.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
        updateTabSelection(currentCategoryIndex)
    }

    /**
     * 创建单个Tab视图
     */
    private fun createTabView(title: String, position: Int, url: String): TextView {
        return TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 14f
            background = createTabBackground(accentColor, context)
            setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
            tag = position
            setTextColor(context.getCompatColor(R.color.primaryText))
            // 宽度自适应内容
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 6.dpToPx()
            }
            setOnClickListener {
                setTextColor(context.getCompatColor(R.color.secondaryText)) // 点击变色
                if (position != currentCategoryIndex) {
                    // 保存当前分类的滚动位置
                    saveCurrentScrollPosition()
                    currentCategoryIndex = position
                    updateTabSelection(position)
                    // 切换分类：清空当前列表，重新加载新分类数据
                    adapter.clearItems()
                    // 重置加载状态，确保自动加载功能正常
                    loadMoreView.hasMore()
                    loadMoreView.startLoad()
                    // 传入预加载参数和所有分类列表
                    viewModel.switchCategory(
                        newUrl = url,
                        exploreName = title,
                        preload = preloadMode == 1,
                        allKinds = exploreKinds
                    )
                    binding.titleBar.title = title
                }
            }
        }
    }

    /**
     * 创建Tab背景（参考订阅源界面的视觉样式）
     */
    private fun createTabBackground(accentColor: Int, context: Context): android.graphics.drawable.Drawable {
        val radius = 16f.dpToPx()
        val strokeWidth = 1f.dpToPx()

        val selectedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(strokeWidth.toInt(), accentColor)
        }

        val defaultDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), selectedDrawable)
            addState(intArrayOf(), defaultDrawable)
        }
    }

    /**
     * 更新选中状态
     */
    private fun updateTabSelection(position: Int) {
        if (!isDestroyed && !isFinishing) {
            tabRows.forEachIndexed { rowIndex, row ->
                for (i in 0 until row.childCount) {
                    val tabIndex = rowIndex * maxTagsPerRow + i
                    val tabView = row.getChildAt(i) as? TextView
                    tabView?.isSelected = tabIndex == position
                }
            }
            // 确保选中标签在视图内
            ensureTabVisible(position)
        }
    }

    /**
     * 确保选中标签在视图内可见
     */
    private fun ensureTabVisible(position: Int) {
        if (position < 0 || position >= exploreKinds.size) return
        val rowIndex = position / maxTagsPerRow
        if (rowIndex >= tabScrollViews.size) return
        val scrollView = tabScrollViews[rowIndex]
        val rowLayout = tabRows[rowIndex]
        val indexInRow = position % maxTagsPerRow
        if (indexInRow >= rowLayout.childCount) return

        val tabView = rowLayout.getChildAt(indexInRow)
        scrollView.post {
            val tabLeft = tabView.left
            val tabRight = tabView.right
            val scrollViewWidth = scrollView.width
            val padding = 12.dpToPx()
            when {
                tabLeft - padding < scrollView.scrollX ->
                    scrollView.smoothScrollTo(tabLeft - padding, 0)
                tabRight + padding > scrollView.scrollX + scrollViewWidth ->
                    scrollView.smoothScrollTo(tabRight - scrollViewWidth + padding, 0)
            }
        }
    }
}
