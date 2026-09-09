package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.EnhancedPageConfig
import io.legado.app.help.config.RimcharsUiConfig
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

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")
    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val searchView: SearchView by lazy { binding.titleBar.findViewById(R.id.search_view) }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private val exploreSources = linkedMapOf<String, String>()
    private var selectedExploreSource: String? = null
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null
    private var sourceMenu: SubMenu? = null
    private var sort = BookSourceSort.Default
    private var sortAscending = true
    private var usingRimcharsStyle = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        usingRimcharsStyle = RimcharsUiConfig.discoveryEnabled
        initSearchView()
        initRecyclerView()
        initGroupData()
        initSourceData()
        applyRimcharsStyle()
        upExploreData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        sourceMenu = menu.findItem(R.id.menu_explore_source)?.subMenu
        menu.findItem(R.id.menu_explore_direct_url)?.isVisible = EnhancedPageConfig.enhancedExplorePage && !usingRimcharsStyle
        menu.findItem(R.id.menu_explore_source)?.isVisible = EnhancedPageConfig.enhancedExplorePage && !usingRimcharsStyle
        menu.findItem(R.id.menu_rimchars_discovery)?.isChecked = usingRimcharsStyle
        menu.findItem(R.id.menu_rimchars_discovery)?.isVisible = true
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu
        sortSubMenu?.findItem(R.id.menu_sort_desc)?.isChecked = !sortAscending
        sortSubMenu?.setGroupCheckable(R.id.menu_group_sort, true, true)
        upGroupsMenu()
        upSourceMenu()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        menu.findItem(R.id.menu_explore_source)?.isVisible = EnhancedPageConfig.enhancedExplorePage && !usingRimcharsStyle
        menu.findItem(R.id.menu_explore_direct_url)?.isVisible = EnhancedPageConfig.enhancedExplorePage && !usingRimcharsStyle
        menu.findItem(R.id.menu_rimchars_discovery)?.isChecked = usingRimcharsStyle
        super.onPrepareOptionsMenu(menu)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = if (EnhancedPageConfig.enhancedExplorePage) "搜索书源 / 分组 / URL" else getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initRecyclerView() {
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager
        binding.rvFind.adapter = adapter
        (binding.rvFind.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.rvFind.setItemViewCacheSize(8)
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) binding.rvFind.scrollToPosition(0)
            }
        })
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (view == null) return
        binding.rvFind.clipToPadding = false
        binding.rvFind.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        binding.rvFind.updatePadding(bottom = bottomPadding)
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED, AppDatabase.BOOK_SOURCE_TABLE_NAME)
                .conflate().distinctUntilChanged().collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    delay(500)
                }
        }
    }

    private fun initSourceData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExplore()
                .flowWithLifecycleAndDatabaseChange(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED, AppDatabase.BOOK_SOURCE_TABLE_NAME)
                .map { list -> list.distinctBy { it.bookSourceUrl }.associate { it.bookSourceUrl to it.bookSourceName } }
                .conflate().distinctUntilChanged().collect { data ->
                    exploreSources.clear()
                    exploreSources.putAll(data)
                    if (selectedExploreSource !in exploreSources.keys) selectedExploreSource = null
                    upSourceMenu()
                    upExploreData(searchView.query?.toString())
                }
        }
    }

    private fun upSourceMenu() = sourceMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_explore_source_text)
        val all = subMenu.add(R.id.menu_explore_source_text, Menu.NONE, 0, "全部发现源")
        all.isCheckable = true
        all.isChecked = selectedExploreSource == null
        exploreSources.entries.forEachIndexed { index, (url, name) ->
            val item = subMenu.add(R.id.menu_explore_source_text, Menu.NONE, index + 1, name)
            item.isCheckable = true
            item.isChecked = selectedExploreSource == url
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> appDb.bookSourceDao.flowExplore()
                searchKey.startsWith("group:") -> appDb.bookSourceDao.flowGroupExplore(searchKey.substringAfter("group:"))
                else -> appDb.bookSourceDao.flowExplore(searchKey)
            }.map { data ->
                val filtered = selectedExploreSource?.let { url -> data.filter { it.bookSourceUrl == url } } ?: data
                if (sortAscending) {
                    when (sort) {
                        BookSourceSort.Name -> filtered.sortedWith { o1, o2 -> o1.bookSourceName.cnCompare(o2.bookSourceName) }
                        BookSourceSort.Url -> filtered.sortedBy { it.bookSourceUrl }
                        BookSourceSort.Update -> filtered.sortedByDescending { it.lastUpdateTime }
                        BookSourceSort.Respond -> filtered.sortedBy { it.respondTime }
                        else -> filtered
                    }
                } else {
                    when (sort) {
                        BookSourceSort.Name -> filtered.sortedWith { o1, o2 -> o2.bookSourceName.cnCompare(o1.bookSourceName) }
                        BookSourceSort.Url -> filtered.sortedByDescending { it.bookSourceUrl }
                        BookSourceSort.Update -> filtered.sortedBy { it.lastUpdateTime }
                        BookSourceSort.Respond -> filtered.sortedByDescending { it.respondTime }
                        else -> filtered.reversed()
                    }
                }
            }.flowWithLifecycleAndDatabaseChange(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED, AppDatabase.BOOK_SOURCE_TABLE_NAME)
                .catch { AppLog.put("发现界面更新数据出错", it) }
                .conflate().flowOn(IO).collect { data ->
                    binding.tvEmptyMsg.isGone = data.isNotEmpty() || searchView.query.isNotEmpty()
                    adapter.setItems(data, diffItemCallBack)
                    binding.rvFind.post { binding.rvFind.refreshSystemScrollBar() }
                    if (usingRimcharsStyle) {
                        binding.composeRimcharsDiscovery.setContent {
                            RimcharsDiscoveryScreen(
                                sources = data,
                                groups = groups.toList(),
                                onSourceClick = { source ->
                                    val bookSource = source.getBookSource()
                                    openExplore(source.bookSourceUrl, source.bookSourceName, bookSource?.exploreUrl)
                                },
                                onSearchClick = {
                                    searchView.isIconified = false
                                    searchView.requestFocus()
                                }
                            )
                        }
                    }
                    delay(500)
                }
        }
    }

    private fun applyRimcharsStyle() {
        binding.composeRimcharsDiscovery.isVisible = usingRimcharsStyle
        binding.rvFind.isVisible = !usingRimcharsStyle
        binding.tvExploreHint.isVisible = !usingRimcharsStyle
        binding.titleBar.isVisible = !usingRimcharsStyle
        if (usingRimcharsStyle) {
            binding.composeRimcharsDiscovery.setContent {
                RimcharsDiscoveryScreen(
                    sources = emptyList(),
                    groups = groups.toList(),
                    onSourceClick = {},
                    onSearchClick = {
                        searchView.isIconified = false
                        searchView.requestFocus()
                    }
                )
            }
        }
        activity?.invalidateOptionsMenu()
    }

    override fun onResume() { super.onResume(); adapter.upResumed(true); adapter.onResume(); if (usingRimcharsStyle != RimcharsUiConfig.discoveryEnabled) { usingRimcharsStyle = RimcharsUiConfig.discoveryEnabled; applyRimcharsStyle(); upExploreData(searchView.query?.toString()) } }
    override fun onPause() { adapter.upResumed(false); searchView.clearFocus(); adapter.onPause(); super.onPause() }
    override fun onDestroyView() { adapter.onDestroy(); super.onDestroyView() }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach { subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it) }
    }

    override val scope: CoroutineScope get() = viewLifecycleOwner.lifecycleScope

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_rimchars_discovery -> {
                RimcharsUiConfig.discoveryEnabled = !RimcharsUiConfig.discoveryEnabled
                usingRimcharsStyle = RimcharsUiConfig.discoveryEnabled
                item.isChecked = usingRimcharsStyle
                applyRimcharsStyle()
                upExploreData(searchView.query?.toString())
                return
            }
            R.id.menu_explore_direct_url -> if (EnhancedPageConfig.enhancedExplorePage && !usingRimcharsStyle) showDirectUrlDialog()
            R.id.menu_sort_desc -> { sortAscending = !sortAscending; item.isChecked = !sortAscending; upExploreData(searchView.query?.toString()) }
            R.id.menu_sort_manual -> { item.isChecked = true; sort = BookSourceSort.Default; upExploreData(searchView.query?.toString()) }
            R.id.menu_sort_name -> { item.isChecked = true; sort = BookSourceSort.Name; upExploreData(searchView.query?.toString()) }
            R.id.menu_sort_url -> { item.isChecked = true; sort = BookSourceSort.Url; upExploreData(searchView.query?.toString()) }
            R.id.menu_sort_time -> { item.isChecked = true; sort = BookSourceSort.Update; upExploreData(searchView.query?.toString()) }
            R.id.menu_sort_respondTime -> { item.isChecked = true; sort = BookSourceSort.Respond; upExploreData(searchView.query?.toString()) }
        }
        if (item.groupId == R.id.menu_group_text) {
            searchView.setQuery("group:${item.title}", true)
        } else if (item.groupId == R.id.menu_explore_source_text && EnhancedPageConfig.enhancedExplorePage && !usingRimcharsStyle) {
            selectedExploreSource = if (item.order <= 0) null else exploreSources.keys.elementAtOrNull(item.order - 1)
            upSourceMenu()
            upExploreData(searchView.query?.toString())
        }
    }

    private fun showDirectUrlDialog() {
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply { editView.hint = "书源URL::发现URL" }
        alert("直接 URL 发现") {
            setMessage("格式：已安装书源URL::发现URL")
            customView { dialogBinding.root }
            okButton {
                val value = dialogBinding.editView.text?.toString()?.trim().orEmpty()
                val separator = value.indexOf("::")
                if (separator > 0 && separator < value.lastIndex) {
                    val sourceUrl = value.substring(0, separator).trim()
                    val exploreUrl = value.substring(separator + 2).trim()
                    if ((sourceUrl.startsWith("http://", true) || sourceUrl.startsWith("https://", true)) &&
                        (exploreUrl.startsWith("http://", true) || exploreUrl.startsWith("https://", true))) {
                        startActivity<ExploreShowActivity> {
                            putExtra("exploreName", "URL 发现")
                            putExtra("sourceUrl", sourceUrl)
                            putExtra("exploreUrl", exploreUrl)
                        }
                    }
                }
            }
            cancelButton()
        }
    }

    override fun scrollTo(pos: Int) { (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0) }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        adapter.clearPendingScrollToSource()
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) { startActivity<BookSourceEditActivity> { putExtra("sourceUrl", sourceUrl) } }
    override fun toTop(source: BookSourcePart) { viewModel.topSource(source) }
    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) { setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName); noButton(); yesButton { viewModel.deleteSource(source) } }
    }
    override fun searchBook(bookSource: BookSourcePart) { SearchActivity.start(requireContext(), bookSource) }
    override fun showKindQueryDialog(source: BookSourcePart) { showDialogFragment(ExploreKindQueryDialog(source.bookSourceUrl, source.bookSourceName)) }

    fun compressExplore() {
        if (!adapter.compressExplore()) {
            if (AppConfig.isEInkMode) binding.rvFind.scrollToPosition(0) else binding.rvFind.smoothScrollToPosition(0)
        }
    }
}
