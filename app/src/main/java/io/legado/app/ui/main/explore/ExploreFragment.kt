package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.source.manage.BookSourceSort
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.theme.LegadoTheme

class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore), MainFragmentInterface, ExploreAdapter.CallBack {
    constructor(position: Int) : this() { arguments = Bundle().apply { putInt("position", position) } }
    override val position: Int? get() = arguments?.getInt("position")
    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val searchView: SearchView by lazy { binding.titleBar.findViewById(R.id.search_view) }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null
    private var sort = BookSourceSort.Default
    private var sortAscending = true
    private val suiteHomeViewModel by viewModels<DiscoverySuiteHomeViewModel>()
    private val suiteManageViewModel by viewModels<DiscoverySuiteManageViewModel>()
    private var suiteComposeView: ComposeView? = null
    private var modernComposeView: ComposeView? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        when {
            AppConfig.enableModernDiscovery -> showModernDiscovery()
            AppConfig.enableDiscoverySuite -> showDiscoverySuite()
            else -> { initSearchView(); initRecyclerView(); initGroupData(); upExploreData() }
        }
    }

    private fun addFullScreenComposeView(): ComposeView = ComposeView(requireContext()).also { compose ->
        binding.root.addView(compose, ConstraintLayout.LayoutParams(-1, -1).apply {
            topToTop = ConstraintSet.PARENT_ID; bottomToBottom = ConstraintSet.PARENT_ID
            startToStart = ConstraintSet.PARENT_ID; endToEnd = ConstraintSet.PARENT_ID
        })
    }

    private fun showModernDiscovery() {
        binding.titleBar.isGone = true; binding.tvExploreHint.isGone = true
        binding.rvFind.isGone = true; binding.tvEmptyMsg.isGone = true
        if (modernComposeView != null) return
        modernComposeView = addFullScreenComposeView().also { compose ->
            compose.setContent {
                LegadoTheme {
                    ModernDiscoveryScreen(
                        lifecycle = viewLifecycleOwner.lifecycle,
                        onSearch = { SearchActivity.start(requireContext(), null) },
                        onExploreSource = { source ->
                            source.getBookSource()?.exploreUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                openExplore(source.bookSourceUrl, source.bookSourceName, url)
                            } ?: context?.toastOnUi("该书源没有发现规则")
                        }
                    )
                }
            }
        }
    }

    private fun showDiscoverySuite() {
        binding.titleBar.isGone = true; binding.tvExploreHint.isGone = true; binding.rvFind.isGone = true
        if (suiteComposeView != null) return
        suiteComposeView = addFullScreenComposeView().also { compose ->
            compose.setContent {
                LegadoTheme {
                    val suiteState by suiteHomeViewModel.uiState.collectAsState()
                    var showManage by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) { suiteHomeViewModel.refreshConfig() }
                    if (showManage) DiscoverySuiteManageScreen(suiteManageViewModel) { showManage = false; suiteHomeViewModel.reloadFromStore() }
                    else DiscoverySuiteHomeScreen(
                        uiState = suiteState,
                        onSearchClick = { SearchActivity.start(requireContext(), null) },
                        onSuiteClick = { suiteManageViewModel.reload(); showManage = true },
                        onSuiteSelect = { suiteHomeViewModel.selectSuite(it) },
                        onBookClick = { openSearchBook(it) },
                        onTagClick = { target -> target.tagUrl.takeIf { it.isNotBlank() }?.let { openExplore(target.sourceUrl, target.title, it) } },
                        onRefreshWidget = { suiteHomeViewModel.refreshWidget(it) },
                        onHorizontalLoadMore = { suiteHomeViewModel.loadMoreHorizontal(it) },
                        onRankedLoadMore = { suiteHomeViewModel.loadMoreRanked(it) }
                    )
                }
            }
        }
    }

    private fun openSearchBook(book: SearchBook) = startActivity<BookInfoActivity> {
        putExtra("name", book.name); putExtra("author", book.author); putExtra("bookUrl", book.bookUrl); putExtra("origin", book.origin)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        if (AppConfig.enableModernDiscovery || AppConfig.enableDiscoverySuite) return
        menuInflater.inflate(R.menu.main_explore, menu); groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        menu.findItem(R.id.action_sort).subMenu?.apply { findItem(R.id.menu_sort_desc)?.isChecked = !sortAscending; setGroupCheckable(R.id.menu_group_sort, true, true) }
        upGroupsMenu()
    }
    override fun onPrepareOptionsMenu(menu: Menu) {
        if (AppConfig.enableModernDiscovery || AppConfig.enableDiscoverySuite) return
        menu.findItem(R.id.action_sort).subMenu!!.apply { findItem(R.id.menu_sort_desc).isChecked = !sortAscending; setGroupCheckable(R.id.menu_group_sort, true, true) }
        super.onPrepareOptionsMenu(menu)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor); searchView.isSubmitButtonEnabled = true; searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean { upExploreData(newText); return false }
        })
    }
    private fun initRecyclerView() {
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0); binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager; binding.rvFind.adapter = adapter
        (binding.rvFind.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false; binding.rvFind.setItemViewCacheSize(8)
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() { override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { if (positionStart == 0) binding.rvFind.scrollToPosition(0) } })
    }
    override fun updateMainBottomPadding(bottomPadding: Int) { if (view == null) return; binding.rvFind.clipToPadding = false; binding.rvFind.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY; binding.rvFind.updatePadding(bottom = bottomPadding) }
    private fun initGroupData() { viewLifecycleOwner.lifecycleScope.launch { appDb.bookSourceDao.flowExploreGroups().flowWithLifecycleAndDatabaseChange(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED, AppDatabase.BOOK_SOURCE_TABLE_NAME).conflate().distinctUntilChanged().collect { groups.clear(); groups.addAll(it); upGroupsMenu(); delay(500) } } }
    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel(); exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when { searchKey.isNullOrBlank() -> appDb.bookSourceDao.flowExplore(); searchKey.startsWith("group:") -> appDb.bookSourceDao.flowGroupExplore(searchKey.substringAfter("group:")); else -> appDb.bookSourceDao.flowExplore(searchKey) }
                .map { data -> if (sortAscending) when (sort) { BookSourceSort.Name -> data.sortedWith { a,b -> a.bookSourceName.cnCompare(b.bookSourceName) }; BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }; BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }; BookSourceSort.Respond -> data.sortedBy { it.respondTime }; else -> data } else when (sort) { BookSourceSort.Name -> data.sortedWith { a,b -> b.bookSourceName.cnCompare(a.bookSourceName) }; BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }; BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }; BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }; else -> data.reversed() } }
                .flowWithLifecycleAndDatabaseChange(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED, AppDatabase.BOOK_SOURCE_TABLE_NAME).catch { AppLog.put("发现界面更新数据出错", it) }.conflate().flowOn(IO).collect { data -> binding.tvEmptyMsg.isGone = data.isNotEmpty() || searchView.query.isNotEmpty(); adapter.setItems(data, diffItemCallBack); binding.rvFind.post { binding.rvFind.refreshSystemScrollBar() }; delay(500) }
        }
    }
    override fun onResume() { super.onResume(); if (!AppConfig.enableModernDiscovery && !AppConfig.enableDiscoverySuite) { adapter.upResumed(true); adapter.onResume() } }
    override fun onPause() { if (!AppConfig.enableModernDiscovery && !AppConfig.enableDiscoverySuite) { adapter.upResumed(false); searchView.clearFocus(); adapter.onPause() }; super.onPause() }
    override fun onDestroyView() { modernComposeView?.let { (it.parent as? ViewGroup)?.removeView(it) }; modernComposeView = null; suiteComposeView?.let { (it.parent as? ViewGroup)?.removeView(it) }; suiteComposeView = null; adapter.onDestroy(); super.onDestroyView() }
    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu -> subMenu.removeGroup(R.id.menu_group_text); groups.forEach { subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it) } }
    override val scope: CoroutineScope get() = viewLifecycleOwner.lifecycleScope
    override fun onCompatOptionsItemSelected(item: MenuItem) { super.onCompatOptionsItemSelected(item); if (AppConfig.enableModernDiscovery || AppConfig.enableDiscoverySuite) return; when (item.itemId) { R.id.menu_sort_desc -> { sortAscending = !sortAscending; item.isChecked = !sortAscending; upExploreData(searchView.query?.toString()) }; R.id.menu_sort_manual -> { item.isChecked=true; sort=BookSourceSort.Default; upExploreData(searchView.query?.toString()) }; R.id.menu_sort_name -> { item.isChecked=true; sort=BookSourceSort.Name; upExploreData(searchView.query?.toString()) }; R.id.menu_sort_url -> { item.isChecked=true; sort=BookSourceSort.Url; upExploreData(searchView.query?.toString()) }; R.id.menu_sort_time -> { item.isChecked=true; sort=BookSourceSort.Update; upExploreData(searchView.query?.toString()) }; R.id.menu_sort_respondTime -> { item.isChecked=true; sort=BookSourceSort.Respond; upExploreData(searchView.query?.toString()) } }; if (item.groupId == R.id.menu_group_text) searchView.setQuery("group:${item.title}", true) }
    override fun scrollTo(pos: Int) { (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0) }
    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) { if (exploreUrl.isNullOrBlank()) return; adapter.clearPendingScrollToSource(); startActivity<ExploreShowActivity> { putExtra("exploreName", title); putExtra("sourceUrl", sourceUrl); putExtra("exploreUrl", exploreUrl) } }
    override fun editSource(sourceUrl: String) = startActivity<BookSourceEditActivity> { putExtra("sourceUrl", sourceUrl) }
    override fun toTop(source: BookSourcePart) = viewModel.topSource(source)
    override fun deleteSource(source: BookSourcePart) { alert(R.string.draw) { setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName); noButton(); yesButton { viewModel.deleteSource(source) } } }
    override fun searchBook(bookSource: BookSourcePart) = SearchActivity.start(requireContext(), bookSource)
    override fun showKindQueryDialog(source: BookSourcePart) = showDialogFragment(ExploreKindQueryDialog(source.bookSourceUrl, source.bookSourceName))
    fun compressExplore() { if (!adapter.compressExplore()) { if (AppConfig.isEInkMode) binding.rvFind.scrollToPosition(0) else binding.rvFind.smoothScrollToPosition(0) } }
}
