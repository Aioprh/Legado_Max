package io.legado.app.ui.main.homepage

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import io.legado.app.ui.main.homepage.manage.HomepageModuleManageSheet
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.HomepageModuleSkeleton
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.BookBottomSheet
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.utils.showHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页主屏幕 Composable
 *
 * 负责展示首页模块列表，包括：
 * - 顶部栏（标题 + 模块管理入口）
 * - 空状态提示
 * - 各类型模块的内容渲染（Banner、卡片、网格、排行、瀑布流等）
 * - 模块管理底部弹窗
 *
 * @param viewModel 首页 ViewModel，提供 UI 状态和操作方法
 * @param onBookClick 书籍点击回调，传递书籍信息用于跳转详情页
 * @param onModuleHeaderClick 模块标题点击回调，用于跳转发现页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageScreen(
    viewModel: HomepageViewModel = viewModel(),
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

    // 书籍底部弹窗状态
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

    // 构建管理操作回调
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.homepage_title), fontWeight = FontWeight.Bold) },
                actions = {
                    // 模块管理
                    IconButton(onClick = { showManageSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.homepage_module_manage)
                        )
                    }
                    // 三点菜单（切换布局、帮助等）
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.homepage_more)
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
                            // 预加载开关（仅在分源Tab模式下显示）
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
                                text = { Text(stringResource(R.string.homepage_help)) },
                                onClick = {
                                    showOverflowMenu = false
                                    (context as? AppCompatActivity)?.showHelp("homepageHelp")
                                }
                            )
                        }
                        // 布局选择子菜单
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
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
            // 分源Tab 模式：按集分组，Tab 切换展示
            SourceTabLayout(
                modules = uiState.modules,
                sets = uiState.manageState.sets,
                paddingValues = paddingValues,
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
            // 混合列表 模式：所有模块在一个列表中展示，无限类型模块排在底部
            val sortedModules = uiState.modules.sortedBy { module ->
                if (HomepageViewModel.isInfinite(module.type.key, null)) 1 else 0
            }
            // 使用 rememberLazyListState 保存滚动位置，避免 Fragment 重建时丢失
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
                        contentPadding = PaddingValues(vertical = 8.dp),
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
                // 悬浮回到顶部按钮
                ScrollToTopFab(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }

    // 模块管理弹窗
    HomepageModuleManageSheet(
        show = showManageSheet,
        onDismiss = { showManageSheet = false },
        state = uiState.manageState,
        actions = manageActions,
    )

    // 书籍底部弹窗
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

/**
 * 分源Tab 布局
 *
 * 使用管理状态中的集列表作为Tab来源，确保Tab顺序与集排序同步更新。
 * 通过 Tab 切换展示不同集的模块，适用于书源较多、希望分源浏览的场景。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceTabLayout(
    modules: List<HomepageModuleUi>,
    sets: List<HomepageSourceManageUi>,
    paddingValues: PaddingValues,
    viewModel: HomepageViewModel,
    context: android.content.Context,
    isRefreshing: Boolean,
    onRefresh: (String?) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
) {
    // 使用管理状态中的集列表作为Tab来源，确保顺序与排序同步
    // 只显示已选中且有模块的集
    val selectedSets = remember(sets) {
        sets.filter { it.isSelected && it.moduleCount > 0 }
    }
    val pagerState = rememberPagerState(pageCount = { selectedSets.size.coerceAtLeast(1) })
    var selectedTabIndex by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // 同步 pagerState.settledPage 和 selectedTabIndex
    // 使用 settledPage 而不是 currentPage，确保只有页面稳定后才触发加载
    LaunchedEffect(pagerState.settledPage) {
        selectedTabIndex = pagerState.settledPage
    }

    // 更新 ViewModel 中的当前Tab索引和集列表（用于预加载控制）
    // 使用 settledPage 确保只有页面稳定后才触发加载
    LaunchedEffect(pagerState.settledPage, selectedSets) {
        viewModel.updateCurrentTab(pagerState.settledPage, selectedSets)
    }

    // 确保 selectedTabIndex 不越界
    LaunchedEffect(selectedSets.size) {
        if (selectedTabIndex >= selectedSets.size) {
            selectedTabIndex = 0
            pagerState.scrollToPage(0)
        }
    }

    // 确保 selectedTabIndex 不越界（组合期间立即生效，防止隐藏集后索引越界崩溃）
    val safeTabIndex = if (selectedSets.isEmpty()) 0 else selectedTabIndex.coerceIn(0, selectedSets.lastIndex)

    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    // 跟踪当前页面的滚动状态，用于悬浮回到顶部按钮
    val currentPageListState = remember { mutableStateOf<LazyListState?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = (paddingValues.calculateTopPadding() - 16.dp).coerceAtLeast(0.dp),
                bottom = paddingValues.calculateBottomPadding(),
                start = paddingValues.calculateLeftPadding(layoutDirection),
                end = paddingValues.calculateRightPadding(layoutDirection),
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        if (selectedSets.isEmpty()) return@Column
        // 可滚动的 Tab 栏
        ScrollableTabRow(
            selectedTabIndex = safeTabIndex,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
            // 自定义 indicator：防止 tabPositions 与 selectedTabIndex 不同步时越界
            indicator = { tabPositions ->
                if (tabPositions.isNotEmpty() && safeTabIndex < tabPositions.size) {
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[safeTabIndex])
                    )
                }
            }
        ) {
            selectedSets.forEachIndexed { index, set ->
                Tab(
                    selected = safeTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = set.sourceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }
        // 使用 HorizontalPager 实现左右滑动切换书源集
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> selectedSets.getOrNull(index)?.sourceUrl ?: index }
        ) { pageIndex ->
            // 当前选中 Tab 对应的集
            val currentSet = selectedSets.getOrNull(pageIndex)
            // 根据集过滤模块：自定义集使用 customSetId 匹配，书源集使用 sourceUrl 匹配
            val currentModules = remember(modules, currentSet) {
                val filtered = modules.filter { module ->
                    if (currentSet?.isCustomSet == true) {
                        val setId = HomepageViewModel.customSetIdFromUrl(currentSet.sourceUrl)
                        module.customSetId == setId
                    } else {
                        // 书源集：集 URL 格式为 src_<书源URL>，模块的 customSetId 也是 src_<书源URL>
                        module.customSetId == currentSet?.sourceUrl
                    }
                }
                // 无限类型模块排在底部
                filtered.sortedBy { module ->
                    if (HomepageViewModel.isInfinite(module.type.key, null)) 1 else 0
                }
            }
            val currentSetName = currentSet?.sourceName
            // 使用 rememberSaveable 保存每个页面的滚动位置
            val listStateKey = "homepage_tab_${currentSet?.sourceUrl ?: pageIndex}"
            val listState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            // 更新当前页面的滚动状态引用，用于悬浮回到顶部按钮
            // 直接在组合期间赋值，确保页面重组时引用始终同步
            if (pagerState.settledPage == pageIndex) {
                currentPageListState.value = listState
            }
            // 直接从 ViewModel 观察刷新状态，确保每页独立接收状态变更
            // 绕过 HorizontalPager 页面缓存导致的状态传播延迟
            val pageIsRefreshing by viewModel.uiState.map { it.isRefreshing }.collectAsStateWithLifecycle(false)
            PullToRefreshBox(
                isRefreshing = pageIsRefreshing,
                onRefresh = { onRefresh(currentSetName) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
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
        // 悬浮回到顶部按钮
        currentPageListState.value?.let { state ->
            ScrollToTopFab(
                listState = state,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
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
        // 排行榜多 Tab 模式下，获取当前选中 Tab 的 exploreUrl
        val rankingTabState = module.state as? ModuleLoadState.RankingTabs
        val isRankingTabs = rankingTabState != null
        val rankingCurrentExploreUrl = rankingTabState
            ?.tabs?.getOrNull(rankingTabState.selectedIndex)?.exploreUrl
        // 箭头显示逻辑：多 Tab 时在 Tab 栏 → 标题行不显示；单 Tab 或无 Tab 时在标题行显示
        val hasArrow = ((module.exploreUrl != null || rankingCurrentExploreUrl != null)
                && module.type != HomepageModuleType.ButtonGroup
                && (!isRankingTabs || (rankingTabState?.tabs?.size ?: 0) <= 1))

        // Module header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
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
            if (hasArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.homepage_more),
                    tint = pageSecondaryTextColor(),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Module content
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

                        HomepageModuleType.Grid, HomepageModuleType.InfiniteGrid -> Column(modifier = Modifier.fillMaxWidth()) {
                            GridModule(
                                books = state.books,
                                onClick = { book, _ -> onBookClick(book) },
                                onLongClick = { book, _ -> onBookLongClick(book) },
                                maxRows = if (module.type == HomepageModuleType.InfiniteGrid) null else 2
                            )
                            // 无限网格显示加载更多
                            if (module.type == HomepageModuleType.InfiniteGrid && state.hasMore) {
                                LoadMoreFooter(
                                    isLoading = state.isLoadingMore,
                                    onClick = { viewModel.loadMoreModule(module.globalId) }
                                )
                            }
                        }

                        HomepageModuleType.Ranking -> RankingModule(
                            books = state.books,
                            onClick = { book, _ -> onBookClick(book) },
                            onLongClick = { book, _ -> onBookLongClick(book) }
                        )

                        HomepageModuleType.GridRanking -> GridRankingModule(
                            books = state.books,
                            onClick = { item -> onBookClick(item.book) },
                            onLongClick = { item -> onBookLongClick(item.book) }
                        )

                        HomepageModuleType.Waterfall -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 瀑布流布局 - 使用 Column+Row 实现两列，避免 LazyGrid 嵌套需要固定高度
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
                                // 加载更多
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
                    RankingTabsModule(
                        tabs = state.tabs,
                        selectedIndex = state.selectedIndex,
                        moduleType = module.type,
                        onTabSelected = { index ->
                            viewModel.selectRankingTab(module.globalId, index)
                        },
                        onBookClick = onBookClick,
                        onBookLongClick = onBookLongClick,
                        onArrowClick = { tab ->
                            onModuleHeaderClick(tab.title, module.sourceUrl, tab.exploreUrl)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    if (isLoading) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(R.string.homepage_loading),
                style = MaterialTheme.typography.labelMedium,
                color = pageSecondaryTextColor()
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.homepage_load_more),
                style = MaterialTheme.typography.labelMedium,
                color = pageAccentColor()
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = pageAccentColor(),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingTabsModule(
    tabs: List<RankingTabData>,
    selectedIndex: Int,
    moduleType: HomepageModuleType,
    onTabSelected: (Int) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
    onArrowClick: (RankingTabData) -> Unit,
) {
    val currentTab = tabs.getOrNull(selectedIndex) ?: return
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

        // 内容区域（按 selectedIndex 做 key，切换 Tab 时重置分页/滚动状态）
        // 注意：外层 HomepageModuleItem 已应用 padding(horizontal = 16.dp)，此处不再重复
        Box {
            androidx.compose.runtime.key(selectedIndex) {
            when {
                currentTab.books != null -> {
                    val books = currentTab.books!!
                    when (moduleType) {
                        HomepageModuleType.Ranking -> RankingModule(
                            books = books,
                            onClick = { book, _ -> onBookClick(book) },
                            onLongClick = { book, _ -> onBookLongClick(book) }
                        )
                        HomepageModuleType.GridRanking -> GridRankingModule(
                            books = books,
                            onClick = { item -> onBookClick(item.book) },
                            onLongClick = { item -> onBookLongClick(item.book) }
                        )
                        else -> RankingModule(
                            books = books,
                            onClick = { book, _ -> onBookClick(book) },
                            onLongClick = { book, _ -> onBookLongClick(book) }
                        )
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
            } // key(selectedIndex)
        }
    }
}

/**
 * 悬浮回到顶部按钮
 *
 * 当列表向下滚动超过一定距离时显示，点击后平滑滚动到列表顶部。
 * 背景和图标颜色均通过项目主题系统自适应。
 *
 * @param listState 关联的 LazyListState
 * @param modifier 额外的修饰符
 */
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
