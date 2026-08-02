package io.legado.app.ui.main.homepage

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.main.homepage.manage.HomepageModuleManageSheet
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.HomepageModuleSkeleton
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.ui.widget.components.BookBottomSheet
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageScreen(
    viewModel: HomepageViewModel = viewModel(),
    bottomPaddingPx: Int = 0,
    onBookClick: (name: String?, author: String?, bookUrl: String, origin: String?, coverPath: String?) -> Unit,
    onModuleHeaderClick: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showManageSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val preloadMode by viewModel.preloadMode.collectAsStateWithLifecycle()

    var showBookSheet by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<SearchBook?>(null) }
    var selectedBookShelfState by remember { mutableStateOf(BookShelfState.NOT_IN_SHELF) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomepageEffect.NavigateToBookInfo ->
                    onBookClick(effect.name, effect.author, effect.bookUrl, effect.origin, effect.coverPath)

                is HomepageEffect.NavigateToExploreShow ->
                    onModuleHeaderClick(effect.title, effect.sourceUrl, effect.exploreUrl)

                is HomepageEffect.ShowSnackbar -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val manageActions = remember(viewModel) {
        HomepageManageActions(
            onToggleSet = viewModel::toggleSet,
            onGetSourceModules = viewModel::getSourceModules,
            onSyncSourceModules = viewModel::syncSourceModules,
            onToggleModule = viewModel::toggleModule,
            onJoinModule = viewModel::joinModule,
            onAddCustomModule = viewModel::addCustomModule,
            onAddButtonGroupFromKinds = viewModel::addButtonGroupFromKinds,
            onGetExploreKinds = viewModel::getExploreKinds,
            onGetRssKinds = viewModel::getRssKinds,
            onAddRssCustomModule = viewModel::addRssCustomModule,
            onAddRssButtonGroupFromKinds = viewModel::addRssButtonGroupFromKinds,
            onAddRankingGroupFromKinds = viewModel::addRankingGroupFromKinds,
            onAddRssRankingGroupFromKinds = viewModel::addRssRankingGroupFromKinds,
            onUpdateModule = viewModel::updateModule,
            onDeleteModule = viewModel::deleteModule,
            onReorderModules = viewModel::reorderModules,
            onReorderSets = viewModel::reorderCustomSets,
            onSetCustomSetTitle = viewModel::setCustomSetTitle,
            onCreateCustomSet = viewModel::createCustomSet,
            onRenameCustomSet = viewModel::renameCustomSet,
            onDeleteCustomSet = viewModel::deleteCustomSet,
            onAssignModuleToCustomSet = viewModel::assignModuleToCustomSet,
        )
    }

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // ★★★ 顶部栏改为毛玻璃半透明风格 ★★★
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.homepage_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    IconButton(onClick = { showManageSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.homepage_module_manage),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.homepage_more),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.homepage_switch_layout)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showLayoutMenu = true
                                }
                            )
                            if (layoutMode == 1) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.homepage_preload),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        viewModel.setPreloadMode(if (preloadMode == 0) 1 else 0)
                                        showOverflowMenu = false
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = if (preloadMode == 1) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = if (preloadMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log)) },
                                onClick = {
                                    showOverflowMenu = false
                                    (context as? AppCompatActivity)?.showDialogFragment<AppLogDialog>()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.homepage_help)) },
                                onClick = {
                                    showOverflowMenu = false
                                    (context as? AppCompatActivity)?.showHelp("homepageHelp")
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.homepage_layout_mixed)) },
                                onClick = {
                                    viewModel.setLayoutMode(0)
                                    showLayoutMenu = false
                                },
                                leadingIcon = {
                                    if (layoutMode == 0) Icon(Icons.Default.Dashboard, null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.homepage_layout_source_tab)) },
                                onClick = {
                                    viewModel.setLayoutMode(1)
                                    showLayoutMenu = false
                                },
                                leadingIcon = {
                                    if (layoutMode == 1) Icon(Icons.Default.ViewModule, null)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.modules.isEmpty() && !uiState.isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.homepage_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = pageSecondaryTextColor()
                    )
                    Text(
                        text = stringResource(R.string.homepage_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor().copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (layoutMode == 1) {
            SourceTabLayout(
                modules = uiState.modules,
                sets = uiState.manageState.sets,
                paddingValues = paddingValues,
                bottomPaddingPx = bottomPaddingPx,
                viewModel = viewModel,
                context = context,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onRefresh,
                onBookLongClick = { book ->
                    selectedBook = book
                    selectedBookShelfState = viewModel.getCurrentBookShelfState(book)
                    showBookSheet = true
                },
            )
        } else {
            val sortedModules = uiState.modules.sortedBy { module ->
                if (HomepageViewModel.isInfinite(module.type.key, null)) 1 else 0
            }
            val listState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.onRefresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 8.dp + with(androidx.compose.ui.platform.LocalDensity.current) { bottomPaddingPx.toDp() }
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(sortedModules, key = { it.globalId }) { module ->
                            HomepageModuleItem(
                                module = module,
                                viewModel = viewModel,
                                onBookClick = { book ->
                                    viewModel.onBookClick(book)
                                },
                                onBookLongClick = { book ->
                                    selectedBook = book
                                    selectedBookShelfState = viewModel.getCurrentBookShelfState(book)
                                    showBookSheet = true
                                },
                                onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                                    viewModel.onModuleHeaderClick(sourceUrl, exploreUrl, title)
                                }
                            )
                        }
                    }
                }
                ScrollToTopFab(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + with(androidx.compose.ui.platform.LocalDensity.current) { bottomPaddingPx.toDp() }
                        )
                )
            }
        }
    }

    HomepageModuleManageSheet(
        show = showManageSheet,
        onDismiss = { showManageSheet = false },
        state = uiState.manageState,
        actions = manageActions,
    )

    val isRssArticle = remember(selectedBook) {
        selectedBook?.let { book ->
            appDb.rssSourceDao.has(book.origin)
        } ?: false
    }
    BookBottomSheet(
        show = showBookSheet,
        book = selectedBook,
        shelfState = selectedBookShelfState,
        onDismiss = { showBookSheet = false },
        onAddToShelf = { book -> viewModel.onAddToShelf(book) },
        onShowInfo = { book ->
            viewModel.onBookClick(book)
        },
        isRssArticle = isRssArticle,
        onAddToFavorites = if (isRssArticle) {
            { book ->
                kotlinx.coroutines.MainScope().launch {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        appDb.rssStarDao.insert(RssStar(
                            origin = book.origin,
                            title = book.name,
                            link = book.bookUrl,
                            description = book.intro,
                            image = book.coverUrl,
                            pubDate = book.latestChapterTitle,
                        ))
                    }
                    Toast.makeText(context, R.string.added_to_favorites, Toast.LENGTH_SHORT).show()
                }
            }
        } else null,
        onViewContent = if (isRssArticle) {
            { book ->
                ReadRssActivity.start(
                    context,
                    false,
                    book.origin,
                    book.name,
                    book.bookUrl
                )
            }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceTabLayout(
    modules: List<HomepageModuleUi>,
    sets: List<HomepageSourceManageUi>,
    paddingValues: PaddingValues,
    bottomPaddingPx: Int,
    viewModel: HomepageViewModel,
    context: android.content.Context,
    isRefreshing: Boolean,
    onRefresh: (String?) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
) {
    val selectedSets = remember(sets) {
        sets.filter { it.isSelected && it.moduleCount > 0 }
    }
    val pagerState = rememberPagerState(pageCount = { selectedSets.size.coerceAtLeast(1) })
    var selectedTabIndex by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.settledPage) {
        selectedTabIndex = pagerState.settledPage
    }

    LaunchedEffect(pagerState.settledPage, selectedSets) {
        viewModel.updateCurrentTab(pagerState.settledPage, selectedSets)
    }

    LaunchedEffect(selectedSets.size) {
        if (selectedTabIndex >= selectedSets.size) {
            selectedTabIndex = 0
            pagerState.scrollToPage(0)
        }
    }

    val safeTabIndex = if (selectedSets.isEmpty()) 0 else selectedTabIndex.coerceIn(0, selectedSets.lastIndex)

    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val currentPageListState = remember { mutableStateOf<LazyListState?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
                start = paddingValues.calculateLeftPadding(layoutDirection),
                end = paddingValues.calculateRightPadding(layoutDirection),
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedSets.isEmpty()) return@Column
            val tabScrollState = rememberScrollState()
            val accent = pageAccentColor()
            val secondaryColor = pageSecondaryTextColor()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tabScrollState)
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                selectedSets.forEachIndexed { index, set ->
                    val isSelected = safeTabIndex == index
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                selectedTabIndex = index
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = set.sourceName,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) accent else secondaryColor
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isSelected) accent else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> selectedSets.getOrNull(index)?.sourceUrl ?: index }
            ) { pageIndex ->
                val currentSet = selectedSets.getOrNull(pageIndex)
                val currentModules = remember(modules, currentSet) {
                    val filtered = modules.filter { module ->
                        if (currentSet?.isCustomSet == true) {
                            val setId = HomepageViewModel.customSetIdFromUrl(currentSet.sourceUrl)
                            module.customSetId == setId
                        } else {
                            module.customSetId == currentSet?.sourceUrl
                        }
                    }
                    filtered.sortedBy { module ->
                        if (HomepageViewModel.isInfinite(module.type.key, null)) 1 else 0
                    }
                }
                val currentSetName = currentSet?.sourceName
                val listState = rememberSaveable(saver = LazyListState.Saver) {
                    LazyListState()
                }
                if (pagerState.settledPage == pageIndex) {
                    currentPageListState.value = listState
                }
                val pageIsRefreshing by viewModel.uiState.map { it.isRefreshing }.collectAsStateWithLifecycle(false)
                PullToRefreshBox(
                    isRefreshing = pageIsRefreshing,
                    onRefresh = { onRefresh(currentSetName) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 8.dp + with(androidx.compose.ui.platform.LocalDensity.current) { bottomPaddingPx.toDp() }
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(currentModules, key = { it.globalId }) { module ->
                            HomepageModuleItem(
                                module = module,
                                viewModel = viewModel,
                                onBookClick = { book ->
                                    viewModel.onBookClick(book)
                                },
                                onBookLongClick = onBookLongClick,
                                onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                                    viewModel.onModuleHeaderClick(sourceUrl, exploreUrl, title)
                                }
                            )
                        }
                    }
                }
            }
        }
        currentPageListState.value?.let { state ->
            ScrollToTopFab(
                listState = state,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = 16.dp + with(androidx.compose.ui.platform.LocalDensity.current) { bottomPaddingPx.toDp() }
                    )
            )
        }
    }
}

@Composable
private fun HomepageModuleItem(
    module: HomepageModuleUi,
    viewModel: HomepageViewModel,
    onBookClick: (SearchBook) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
    onModuleHeaderClick: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val rankingTabState = module.state as? ModuleLoadState.RankingTabs
        val isRankingTabs = rankingTabState != null
        val rankingCurrentExploreUrl = rankingTabState
            ?.tabs?.getOrNull(rankingTabState.selectedIndex)?.exploreUrl

        // ★★★ 模块标题（已去除箭头）★★★
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .then(
                    if (module.type != HomepageModuleType.ButtonGroup) {
                        Modifier.clickable {
                            onModuleHeaderClick(
                                module.title,
                                module.sourceUrl,
                                rankingCurrentExploreUrl ?: module.exploreUrl
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = module.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            when (val state = module.state) {
                is ModuleLoadState.Loading -> {
                    HomepageModuleSkeleton(type = module.type)
                }

                is ModuleLoadState.Error -> {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.homepage_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.homepage_click_retry),
                                style = MaterialTheme.typography.labelMedium,
                                color = pageAccentColor(),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { viewModel.retryModule(module.globalId) }
                            )
                        }
                    }
                }

                is ModuleLoadState.Loaded -> {
                    when (module.type) {
                        HomepageModuleType.Banner -> BannerModule(
                            books = state.books,
                            onClick = { book, _ -> onBookClick(book) },
                            onLongClick = { book, _ -> onBookLongClick(book) }
                        )

                        HomepageModuleType.Card -> CardModule(
                            books = state.books,
                            onClick = { book, _ -> onBookClick(book) },
                            onLongClick = { book, _ -> onBookLongClick(book) }
                        )

                        HomepageModuleType.Grid, HomepageModuleType.InfiniteGrid -> Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GridModule(
                                books = state.books,
                                onClick = { book, _ -> onBookClick(book) },
                                onLongClick = { book, _ -> onBookLongClick(book) },
                                maxRows = if (module.type == HomepageModuleType.InfiniteGrid) null else 2
                            )
                            if (module.type == HomepageModuleType.InfiniteGrid && state.hasMore) {
                                LoadMoreFooter(
                                    isLoading = state.isLoadingMore,
                                    onClick = { viewModel.loadMoreModule(module.globalId) }
                                )
                            }
                        }

                        HomepageModuleType.Ranking -> AutoLoadMoreContainer(
                            enabled = state.hasMore,
                            isLoading = state.isLoadingMore,
                            onLoadMore = { viewModel.loadMoreModule(module.globalId) }
                        ) {
                            RankingModule(
                                books = state.books,
                                onClick = { book, _ -> onBookClick(book) },
                                onLongClick = { book, _ -> onBookLongClick(book) }
                            )
                        }

                        HomepageModuleType.GridRanking -> {
                            // ★★★ 单分类网格排行榜：去除加载更多按钮 ★★★
                            GridRankingModule(
                                books = state.books,
                                onClick = { item -> onBookClick(item.book) },
                                onLongClick = { item -> onBookLongClick(item.book) }
                            )
                        }

                        HomepageModuleType.Waterfall -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val displayBooks = state.books
                                val leftColumn = displayBooks.filterIndexed { index, _ -> index % 2 == 0 }
                                val rightColumn = displayBooks.filterIndexed { index, _ -> index % 2 == 1 }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        leftColumn.forEach { item ->
                                            WaterfallItem(
                                                book = item,
                                                onClick = { onBookClick(item.book) },
                                                onLongClick = { onBookLongClick(item.book) }
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rightColumn.forEach { item ->
                                            WaterfallItem(
                                                book = item,
                                                onClick = { onBookClick(item.book) },
                                                onLongClick = { onBookLongClick(item.book) }
                                            )
                                        }
                                    }
                                }
                                if (state.hasMore) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        onClick = { viewModel.loadMoreModule(module.globalId) }
                                    )
                                }
                            }
                        }

                        HomepageModuleType.ButtonGroup -> {}
                        HomepageModuleType.Unknown -> {}
                    }
                }

                is ModuleLoadState.Buttons -> {
                    ButtonGroupModule(
                        kinds = state.kinds,
                        sourceUrl = module.sourceUrl,
                        onKindClick = { sourceUrl, url, kindTitle ->
                            viewModel.onKindUrlClick(sourceUrl, url, kindTitle)
                        }
                    )
                }

                is ModuleLoadState.RankingTabs -> {
                    // ★★★ 多分类Tab ★★★
                    RankingTabsModule(
                        tabs = state.tabs,
                        selectedIndex = state.selectedIndex,
                        moduleType = module.type,
                        globalId = module.globalId,
                        onTabSelected = { index ->
                            viewModel.selectRankingTab(module.globalId, index)
                        },
                        onBookClick = onBookClick,
                        onBookLongClick = onBookLongClick,
                        onArrowClick = { tab ->
                            onModuleHeaderClick(tab.title, module.sourceUrl, tab.exploreUrl)
                        },
                        onLoadMore = { tabIndex ->
                            viewModel.loadMoreRankingTab(module.globalId, tabIndex)
                        }
                    )
                }
            }
        }
    }
}

// ★★★ 加载更多按钮（仅用于无限网格、瀑布流、普通排行榜，网格排行榜已去除）★★★
@Composable
private fun LoadMoreFooter(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    if (isLoading) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(6.dp),
                strokeWidth = 0.5.dp,
                color = pageAccentColor()
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.homepage_loading),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = pageSecondaryTextColor()
            )
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(6.dp),
            color = pageAccentColor().copy(alpha = 0.08f),
            border = BorderStroke(0.5.dp, pageAccentColor().copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.homepage_load_more),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = pageAccentColor()
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = pageAccentColor(),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ★★★ 最初版本样式 RankingTabsModule（保留页码记忆和加载更多）★★★
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RankingTabsModule(
    tabs: List<RankingTabData>,
    selectedIndex: Int,
    moduleType: HomepageModuleType,
    globalId: String,
    onTabSelected: (Int) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
    onArrowClick: (RankingTabData) -> Unit,
    onLoadMore: (Int) -> Unit,
) {
    val currentTab = tabs.getOrNull(selectedIndex) ?: return

    // ★★★ 为每个 Tab 保存当前页码（用于记忆翻页位置）★★★
    val pageStates = remember { mutableStateMapOf<String, Int>() }
    val currentPage = pageStates.getOrPut(currentTab.title) { 0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 多个 Tab 时显示 Tab 栏 + 固定箭头
        if (tabs.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val accent = pageAccentColor()
                        Surface(
                            color = if (selectedIndex == index)
                                accent.copy(alpha = 0.12f)
                            else Color.Transparent,
                            contentColor = if (selectedIndex == index)
                                accent
                            else pageSecondaryTextColor(),
                            shape = RoundedCornerShape(8.dp),
                            border = if (selectedIndex == index) null
                            else BorderStroke(1.dp, pageSecondaryTextColor().copy(alpha = 0.2f)),
                            onClick = { onTabSelected(index) }
                        ) {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                // 固定位置箭头
                if (currentTab.exploreUrl != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.homepage_more),
                        tint = pageSecondaryTextColor(),
                        modifier = Modifier
                            .padding(start = 4.dp, end = 8.dp)
                            .size(18.dp)
                            .clickable { onArrowClick(currentTab) }
                    )
                }
            }
        }

        // 内容区域
        Box {
            androidx.compose.runtime.key(selectedIndex) {
                when {
                    currentTab.books != null -> {
                        val books = currentTab.books!!
                        when (moduleType) {
                            HomepageModuleType.Ranking -> {
                                RankingModule(
                                    books = books,
                                    onClick = { book, _ -> onBookClick(book) },
                                    onLongClick = { book, _ -> onBookLongClick(book) }
                                )
                            }
                            HomepageModuleType.GridRanking -> {
                                // ★★★ 多分类网格排行榜：去除手动加载按钮，仅保留自动加载 ★★★
                                GridRankingModule(
                                    books = books,
                                    onClick = { item -> onBookClick(item.book) },
                                    onLongClick = { item -> onBookLongClick(item.book) },
                                    onLoadMore = if (currentTab.hasMore && !currentTab.isLoadingMore) {
                                        { onLoadMore(selectedIndex) }
                                    } else null,
                                    initialPage = currentPage,
                                    onPageChanged = { newPage ->
                                        pageStates[currentTab.title] = newPage
                                    }
                                )
                            }
                            else -> {
                                RankingModule(
                                    books = books,
                                    onClick = { book, _ -> onBookClick(book) },
                                    onLongClick = { book, _ -> onBookLongClick(book) }
                                )
                            }
                        }
                    }
                    currentTab.errorMessage != null -> {
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.homepage_load_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        // 加载中
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollToTopFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val visible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(),
        exit = scaleOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = pageCardElevatedContainerColor(),
            contentColor = pageAccentColor(),
            shadowElevation = 4.dp,
            onClick = {
                scope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = pageAccentColor(),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AutoLoadMoreContainer(
    enabled: Boolean,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    var triggered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            if (!enabled || isLoading) return@onGloballyPositioned
            val bottom = coords.positionInWindow().y + coords.size.height
            if (bottom >= view.height.toFloat() - threshold) {
                if (!triggered) {
                    triggered = true
                    onLoadMore()
                }
            } else {
                triggered = false
            }
        }
    ) {
        content()
    }
}