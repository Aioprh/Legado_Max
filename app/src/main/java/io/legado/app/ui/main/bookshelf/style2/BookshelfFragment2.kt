package io.legado.app.ui.main.bookshelf.style2

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf2Binding
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 书架界面
 */
class BookshelfFragment2() : BaseBookshelfFragment(R.layout.fragment_bookshelf2),
    SearchView.OnQueryTextListener,
    BaseBooksAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf2Binding::bind)
    private var folderLayout = AppConfig.folderLayout
    private var bookLayout = AppConfig.bookLayout
    private var spanCount = 1
    private lateinit var booksAdapter: BaseBooksAdapter<*>
    private var spanSizeLookup: GridLayoutManager.SpanSizeLookup? = null
    private var bookGroups: List<BookGroup> = emptyList()
    private var booksFlowJob: Job? = null
    override var groupId = BookGroup.IdRoot
    override var books: List<Book> = emptyList()
    private var enableRefresh = true
    override var onlyUpdateRead = false
    private val bookshelfMargin by lazy { AppConfig.bookshelfMargin }
    private var itemCount = 0
    private var tagFilter: String? = null
    private var tagBar: RoundedTagBarView? = null
    private var tagSelectedIndex = -1
    private var currentTagList: List<String> = emptyList()

    // 计算最小公倍数
    private fun lcm(a: Int, b: Int): Int {
        return a * b / gcd(a, b)
    }

    // 计算最大公约数
    private fun gcd(a: Int, b: Int): Int {
        return if (b == 0) a else gcd(b, a % b)
    }

    private fun createBooksAdapter(): BaseBooksAdapter<*> {
        return if (AppConfig.bookLayout >= 2) {
            BooksAdapterGrid(requireContext(), this)
        } else {
            BooksAdapterList(requireContext(), this)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        tagBar = binding.tagBar
        tagBar?.setOnTagClickListener { index ->
            tagSelectedIndex = index
            tagBar?.setSelectedIndex(index)
            applyTagFilter()
        }
        initRecyclerView()
        initBookGroupData()
        initBooksData()
    }

    private fun initRecyclerView() {
        // 初始化适配器
        if (!this::booksAdapter.isInitialized) {
            booksAdapter = createBooksAdapter()
        }
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        binding.rvBookshelf.setHasFixedSize(true)
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        upFastScrollerBar()
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(books, onlyUpdateRead)
        }
        // 让文件夹和书籍完全独立，互不影响
        // 使用最小公倍数作为spanCount，两者可以自由选择列数
        val bookSpan = if (bookLayout >= 2) bookLayout else 1
        val folderSpan = if (folderLayout >= 2) folderLayout else 1
        val useGrid = bookSpan > 1 || folderSpan > 1
        
        // 计算最小公倍数
        spanCount = if (useGrid) {
            lcm(bookSpan, folderSpan)
        } else {
            1
        }
        
        val layoutManager = if (useGrid) {
            GridLayoutManager(context, spanCount).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (booksAdapter.getItemViewType(position) == 1) {
                            // 文件夹：folderLayout >= 2 时占 spanCount/folderSpan 列（显示为folderLayout列网格）
                            // folderLayout < 2 时占满一行（列表样式）
                            if (folderLayout >= 2) {
                                spanCount / folderSpan
                            } else {
                                spanCount // 占满一行（列表样式）
                            }
                        } else {
                            // 书籍：bookLayout >= 2 时占 spanCount/bookSpan 列（显示为bookLayout列网格）
                            // bookLayout < 2 时占满一行（列表样式）
                            if (bookLayout >= 2) {
                                spanCount / bookSpan
                            } else {
                                spanCount // 占满一行（列表样式）
                            }
                        }
                    }
                }
                this.spanSizeLookup.isSpanIndexCacheEnabled = true
                this@BookshelfFragment2.spanSizeLookup = this.spanSizeLookup
            }
        } else {
            LinearLayoutManager(context)
        }
        binding.rvBookshelf.layoutManager = layoutManager
        binding.rvBookshelf.adapter = booksAdapter
        /**
         * 采用 layoutManager?.onRestoreInstanceState(layoutState)
         * 恢复滚动位置
         * **/
        binding.rvBookshelf.itemAnimator = null
        // 清除旧的ItemDecoration，避免累积
        while (binding.rvBookshelf.itemDecorationCount > 0) {
            binding.rvBookshelf.removeItemDecorationAt(0)
        }
        binding.rvBookshelf.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val marginFirst = bookshelfMargin + 24
            private val marginNormal = bookshelfMargin
            
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                if (position == RecyclerView.NO_POSITION) return
                
                if (spanCount >= 2 && spanSizeLookup != null) {
                    // 使用spanSizeLookup获取正确的行号（组索引）
                    val rowIndex = spanSizeLookup!!.getSpanGroupIndex(position, spanCount)
                    val lastGroupIndex = if (itemCount > 0) {
                        spanSizeLookup!!.getSpanGroupIndex(itemCount - 1, spanCount)
                    } else 0
                    // 处理单行情况：既是第一行也是最后一行
                    if (rowIndex == 0 && rowIndex == lastGroupIndex) {
                        outRect.set(bookshelfMargin, marginFirst, bookshelfMargin, marginFirst)
                    } else when (rowIndex) {
                        0 -> outRect.set(bookshelfMargin, marginFirst, bookshelfMargin, bookshelfMargin)
                        lastGroupIndex -> outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, marginFirst)
                        else -> outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, bookshelfMargin)
                    }
                } else {
                    // 处理单行情况：既是第一行也是最后一行
                    if (position == 0 && position == itemCount - 1) {
                        outRect.set(0, marginFirst, 0, marginFirst)
                    } else when (position) {
                        0 -> outRect.set(0, marginFirst, 0, marginNormal)
                        itemCount - 1 -> outRect.set(0, marginNormal, 0, marginFirst)
                        else -> outRect.set(0, marginNormal, 0, marginNormal)
                    }
                }
            }
        })
    }

    private fun upFastScrollerBar() {
        val showFastScroller = AppConfig.showBookshelfFastScroller
        binding.rvBookshelf.setFastScrollEnabled(showFastScroller)
        binding.rvBookshelf.isVerticalScrollBarEnabled = !showFastScroller
        if (!showFastScroller) {
            binding.rvBookshelf.scrollBarSize =
                ViewConfiguration.get(requireContext()).scaledScrollBarSize
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (view == null) return
        binding.rvBookshelf.clipToPadding = false
        binding.rvBookshelf.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        binding.rvBookshelf.updatePadding(bottom = bottomPadding)
        binding.rvBookshelf.refreshFastScrollerLayout()
    }

    override fun upGroup(data: List<BookGroup>) {
        if (data != bookGroups) {
            bookGroups = data
            booksAdapter.updateItems(groupId)
            itemCount = getItemCount()
            binding.tvEmptyMsg.isGone = itemCount > 0
            binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
        }
    }

    override fun upSort() {
        initBooksData()
    }

    private fun initBooksData() {
        if (groupId == BookGroup.IdRoot) {
            tagBar?.visibility = View.GONE
            tagFilter = null
            if (isAdded) {
                binding.titleBar.title = getString(R.string.bookshelf)
                binding.refreshLayout.isEnabled = true
                enableRefresh = true
            }
        } else {
            bookGroups.firstOrNull {
                groupId == it.groupId
            }?.let {
                binding.titleBar.title = "${getString(R.string.bookshelf)}(${it.groupName})"
                binding.refreshLayout.isEnabled = it.enableRefresh
                enableRefresh = it.enableRefresh
                onlyUpdateRead = it.onlyUpdateRead
            }
            // 加载标签栏（标签栏完成后会自行刷新数据流，不会回调 initBooksData）
            loadTagBar()
        }
        restartBooksFlow()
    }

    /**
     * 启动/重启书籍数据流。
     * 根据当前 [groupId] 和 [tagFilter] 从数据库加载书籍并筛选。
     */
    private fun restartBooksFlow() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            // 方案A：使用轻量查询 flowShelfByGroup 替代 flowByGroup，
            // SQL 层面已过滤 notShelf 并按 durChapterTime DESC 排序
            appDb.bookDao.flowShelfByGroup(groupId).map { list ->
                //排序
                when (AppConfig.getBookSortByGroupId(groupId)) {
                    1 -> list.sortedByDescending {
                        it.latestChapterTime
                    }

                    2 -> list.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> list.sortedBy {
                        it.order
                    }

                    4 -> list.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }

                    else -> list // SQL 已按 durChapterTime DESC 排序，无需再排
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                // 方案A：将 BookShelfDisplay 转换为最小化 Book，供 style2 的 Any 类型 Adapter 使用
                val filtered = if (tagFilter == null) list else list.filter {
                    BookTagHelper.has(it.customTag, tagFilter!!)
                }
                books = filtered.map { it.toMinimalBook() }
                booksAdapter.updateItems(groupId)
                itemCount = getItemCount()
                binding.tvEmptyMsg.isGone = itemCount > 0
                binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
            }
        }
    }

    fun back(): Boolean {
        if (groupId != BookGroup.IdRoot) {
            groupId = BookGroup.IdRoot
            tagBar?.visibility = View.GONE
            tagFilter = null
            // 检查View是否存在，避免崩溃
            if (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                initBooksData()
            }
            return true
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    override fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    override fun onItemClick(item: Any) {
        when (item) {
            is Book -> startActivityForBook(item)

            is BookGroup -> {
                groupId = item.groupId
                initBooksData()
            }
        }
    }

    /**
     * 加载当前分组的二级标签栏数据。
     *
     * 标签栏关闭时清除筛选状态并刷新数据流；
     * 标签栏开启时异步加载标签，完成后设置默认筛选（全部）并刷新数据流。
     * 标签来源：当前分组中实际有书籍使用的标签 + 用户手动配置的标签，
     * 减去被隐藏的标签。只显示当前分组中有对应书籍的标签。
     * 本方法不会回调 [initBooksData]，避免循环调用。
     */
    private fun loadTagBar() {
        if (!AppConfig.showBookshelfTagBar) {
            tagBar?.visibility = View.GONE
            tagSelectedIndex = -1
            currentTagList = emptyList()
            tagFilter = null
            // 不在此处 restartBooksFlow，由调用方 initBooksData 负责
            return
        }
        val currentGroupId = groupId
        viewLifecycleOwner.lifecycleScope.launch {
            val allText = getString(R.string.bookshelf_tag_all)
            val tags = withContext(Dispatchers.IO) {
                val configured = AppConfig.bookshelfGroupTags[currentGroupId].orEmpty()
                val hidden = AppConfig.bookshelfHiddenTags[currentGroupId].orEmpty()
                val allBooks = appDb.bookDao.allTagInfos
                val groupBooks = filterBooksByGroup(allBooks, currentGroupId)
                val existing = groupBooks.flatMap { BookTagHelper.parse(it.customTag) }
                val merged = BookTagManagement.mergeTags(configured, existing)
                merged.filter { tag -> hidden.none { it.equals(tag, ignoreCase = true) } }
            }
            // 在标签列表前插入空字符串作为“全部”标签
            currentTagList = listOf("") + tags
            tagSelectedIndex = 0
            tagBar?.visibility = View.VISIBLE
            tagBar?.applyTopBarStyle(force = true)
            tagBar?.submitItems(
                currentTagList.map { RoundedTagBarView.Item(it.ifBlank { allText }) },
                0
            )
            tagBar?.setSelectedIndex(0, false)
            // 标签栏加载完成后，默认选"全部"（tagFilter=null）。
            // 仅在 tagFilter 有非空旧值时才需重启数据流，避免不必要的取消/重启导致列表闪烁。
            if (tagFilter != null) {
                tagFilter = null
                restartBooksFlow()
            }
        }
    }

    /**
     * 根据 groupId 过滤书籍，逻辑与 [io.legado.app.ui.main.bookshelf.BookshelfTagManageViewModel.booksInGroup] 一致。
     * 默认分组（负数 ID）基于 [BookType] 筛选，用户分组（正数 ID）基于 group 位掩码筛选。
     */
    private fun filterBooksByGroup(
        books: List<io.legado.app.data.dao.BookTagInfo>,
        currentGroupId: Long
    ): List<io.legado.app.data.dao.BookTagInfo> {
        return when (currentGroupId) {
            BookGroup.IdAll -> books
            BookGroup.IdLocal -> books.filter { it.type and BookType.local > 0 }
            BookGroup.IdAudio -> books.filter { it.type and BookType.audio > 0 }
            BookGroup.IdVideo -> books.filter { it.type and BookType.video > 0 }
            BookGroup.IdError -> books.filter { it.type and BookType.updateError > 0 }
            else -> {
                val userGroupMask = appDb.bookGroupDao.all
                    .filter { it.groupId > 0 }
                    .fold(0L) { acc, group -> acc or group.groupId }
                when (currentGroupId) {
                    BookGroup.IdNetNone -> books.filter {
                        it.type and BookType.audio == 0 &&
                            it.type and BookType.video == 0 &&
                            it.type and BookType.local == 0 &&
                            (it.group and userGroupMask) == 0L
                    }
                    BookGroup.IdLocalNone -> books.filter {
                        it.type and BookType.audio == 0 &&
                            it.type and BookType.video == 0 &&
                            it.type and BookType.local > 0 &&
                            (it.group and userGroupMask) == 0L
                    }
                    else -> if (currentGroupId > 0) {
                        books.filter { it.group and currentGroupId > 0 }
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }

    /**
     * 应用当前选中的标签筛选，重新加载数据流。
     * “全部”标签（索引0）传 null 表示不筛选。
     */
    private fun applyTagFilter() {
        val selectedIndex = tagSelectedIndex
        tagFilter = if (selectedIndex <= 0 || selectedIndex >= currentTagList.size) {
            null
        } else {
            currentTagList[selectedIndex]
        }
        // 只重启数据流，不调用 initBooksData，避免 loadTagBar → initBooksData → loadTagBar 循环
        restartBooksFlow()
    }

    override fun onItemLongClick(item: Any) {
        when (item) {
            is Book -> startActivity<BookInfoActivity> {
                putExtra("name", item.name)
                putExtra("author", item.author)
            }

            is BookGroup -> showDialogFragment(GroupEditDialog(item))
        }
    }

    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    fun getItemCount(): Int {
        return if (groupId == BookGroup.IdRoot) {
            bookGroups.size + books.size
        } else {
            books.size
        }
    }

    override fun getItems(): List<Any> {
        if (groupId != BookGroup.IdRoot) {
            return books
        }
        return bookGroups + books
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            // 更新布局配置
            folderLayout = AppConfig.folderLayout
            bookLayout = AppConfig.bookLayout
            // 如果布局类型改变，重新创建适配器
            val newAdapter = createBooksAdapter()
            if (newAdapter::class != booksAdapter::class) {
                booksAdapter = newAdapter
                booksAdapter.updateItems(groupId)
            }
            // 重新初始化RecyclerView以应用新的布局
            initRecyclerView()
            booksAdapter.notifyDataSetChanged()
            upFastScrollerBar()
            // 刷新标签栏（开关状态可能变化）
            if (groupId != BookGroup.IdRoot) {
                loadTagBar()
                // 标签栏从开变关时 loadTagBar 清除了 tagFilter，
                // 需要重启数据流以应用无筛选状态
                restartBooksFlow()
            }
        }
        // 顶栏配置变更时，同步刷新二级标签栏样式
        observeEvent<Boolean>(EventBus.TOP_BAR_CHANGED) { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                tagBar?.applyTopBarStyle(force = true)
            }
        }
    }
}