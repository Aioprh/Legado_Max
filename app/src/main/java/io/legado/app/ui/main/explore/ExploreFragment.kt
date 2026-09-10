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
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.enableModernDiscovery
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceSort
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.theme.LegadoTheme
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
            topToTop = ConstraintSet.PARENT_ID
            bottomToBottom = ConstraintSet.PARENT_ID
            startToStart = ConstraintSet.PARENT_ID
            endToEnd = ConstraintSet.PARENT_ID
        })
    }

    private fun showModernDiscovery() {
        binding.titleBar.isGone = true
        binding.tvExploreHint.isGone = true
        binding.rvFind.isGone = true
        binding.tvEmptyMsg.isGone = true
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
        binding.titleBar.isGone = true
        binding.tvExploreHint.isGone = true
        binding.rvFind.isGone = true
        binding.tvEmptyMsg.isGone = true
        if (suiteComposeView != null) return
        suiteComposeView = addFullScreenComposeView().also { compose ->
            compose.setContent {
                LegadoTheme {
                    DiscoverySuiteHomeScreen(
                        homeViewModel = suiteHomeViewModel,
                        manageViewModel = suiteManageViewModel,
                        onSearch = { SearchActivity.start(requireContext(), null) },
                        onBookClick = ::showBookInfo,
                        onOpenExplore = ::openExplore
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppConfig.enableModernDiscovery || AppConfig.enableDiscoverySuite) return
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        if (!AppConfig.enableModernDiscovery && !AppConfig.enableDiscoverySuite) {
            adapter.clearPendingScrollToSource()
        }
        super.onPause()
    }

    override fun onDestroyView() {
        modernComposeView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        suiteComposeView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        modernComposeView = null
        suiteComposeView = null
        exploreFlowJob?.cancel()
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: android.view.MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (AppConfig.enableModernDiscovery || AppConfig.enableDiscoverySuite) return
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (AppConfig.enableModernDiscovery || AppConfig.enableDiscoverySuite) return false
        return super.onOptionsItemSelected(item)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        adapter.clearPendingScrollToSource()
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }
}
