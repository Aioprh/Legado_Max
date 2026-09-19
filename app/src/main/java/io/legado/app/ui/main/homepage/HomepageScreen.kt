package io.legado.app.ui.main.homepage

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextAlign
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
import io.legado.app.domain.model.layoutInt
import io.legado.app.ui.main.homepage.manage.HomepageModuleManageSheet
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.HomepageModuleSkeleton
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.SearchBarModule
import io.legado.app.ui.main.homepage.modules.SmartFilterModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.widget.components.BookBottomSheet
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 无限流（瀑布流/无限网格）每次揭示的书籍条数。窗口化渲染：仅渲染已揭示部分，
 * 避免无限流加载过多后一次渲染全部书籍导致 UI 卡顿 / 内存放大。
 */
private const val MODULE_REVEAL_STEP = 20

/**
 * 首页主屏幕 Composable
 *
 * 负责展示首页模块列表，包括：
 * - 顶部栏（标题 + 搜索 + 模块管理入口）
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
            onSmartConfigureSource = viewModel::smartConfigureSource,
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
        topBar = {
            val topBarColors = pageTopBarColors()
            Box(
                modifier = Modifier
                    .pageTopBarBackground(topBarColors)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.homepage_title),
                        fontWeight = FontWeight.Bold,
                        color = topBarColors.contentColor,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    )
                    // 搜索按钮
                    IconButton(onClick = {
                        context.startActivity<SearchActivity>()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = topBarColors.contentColor
                        )
                    }
                    // 模块管理
                    IconButton(onClick = { showManageSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.homepage_module_manage),
                            tint = topBarColors.contentColor
                        )
                    }
                    // 三点菜单（切换布局、帮助等）
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.homepage_more),
                                tint = topBarColors.contentColor
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
                            // 缓存管理
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cache_management)) },
                                onClick = {
                                    showOverflowMenu = false
                                    context.startActivity<CacheActivity>()
                                }
                            )
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
            // 分源Tab 模式：按集分组，Tab 切换展示
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
            // 混合列表模式：严格使用模块管理后的排序，不再按模块类型强制重排。
            // 这样首页管理器中的拖拽顺序就是最终展示顺序。
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
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 8.dp + with(androidx.compose.ui.platform.LocalDensity.current) { bottomPaddingPx.toDp() }
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = uiState.modules,
                            key = { it.globalId },
                            contentType = { it.type.key }
                        ) { module ->
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
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + with(androidx.compose.ui.platform.LocalDensity.current) { bottomPaddingPx.toDp() }
                        )
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
        onCacheBook = { book -> viewModel.onCacheBook(book) }
    )
}

/**
 * 分源Tab 布局
 *
 * 使用管理状态中的集列表作为Tab来源，确保Tab顺序与集排序同步更新。
 * Tab视觉与书架智能标签统一为透明液态玻璃胶囊样式，同时保留横向滑动与Pager联动。
 */
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

            // 分源 Tab：复用书架智能标签的透明玻璃胶囊视觉语言。
            val tabScrollState = rememberScrollState()
            val isNight = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val accent = pageAccentColor()
            val tabBarShape = RoundedCornerShape(20.dp)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = tabBarShape,
                color = if (isNight) {
                    Color(0x661B1B1D)
                } else {
                    Color.White.copy(alpha = 0.72f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isNight) {
                        Color.White.copy(alpha = 0.20f)
                    } else {
                        Color.White.copy(alpha = 0.60f)
                    }
                ),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tabScrollState)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedSets.forEachIndexed { index, set ->
                        val isSelected = safeTabIndex == index
                        Surface(
                            modifier = Modifier
                                .height(36.dp)
                                .clickable {
                                    selectedTabIndex = index
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                accent.copy(alpha = 0.82f)
                            } else if (isNight) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color.White.copy(alpha = 0.44f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    Color.White.copy(alpha = 0.60f)
                                } else if (isNight) {
                                    Color.White.copy(alpha = 0.20f)
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                }
                            ),
                            contentColor = if (isSelected) {
                                Color.White
                            } else {
                                pageSecondaryTextColor()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = set.sourceName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
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
                    filtered
                }
                val currentSetName = currentSet?.sourceName
                val listState = rememberSaveable(saver = LazyListState.Saver) {
                    LazyListState()
                }
                if (pagerState.settledPage == pageIndex) {
                    currentPageListState.value = listState
                }
                val pageIsRefreshing by viewModel.uiState
                    .map { it.isRefreshing }
                    .collectAsStateWithLifecycle(false)

                PullToRefreshBox(
                    isRefreshing = pageIsRefreshing,
                    onRefresh = { onRefresh(currentSetName) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 8.dp + with(androidx.compose.ui.platform.LocalDensity.current) {
                                bottomPaddingPx.toDp()
                            }
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = currentModules,
                            key = { it.globalId },
                            contentType = { it.type.key }
                        ) { module ->
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
                        bottom = 16.dp + with(androidx.compose.ui.platform.LocalDensity.current) {
                            bottomPaddingPx.toDp()
                        }
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
        // 排行榜多 Tab 模式下，获取当前选中 Tab 的 exploreUrl
        val rankingTabState = module.state as? ModuleLoadState.RankingTabs
        val isRankingTabs = rankingTabState != null
        val rankingCurrentExploreUrl = rankingTabState
            ?.tabs?.getOrNull(rankingTabState.selectedIndex)?.exploreUrl

        // 统一首页模块头：液态玻璃胶囊 + 明确的模块操作区。
        // 标题和箭头使用同一点击区域，避免文字可点、箭头又是独立点击目标造成误触。
        val canOpenExplore = module.type != HomepageModuleType.ButtonGroup &&
            module.type != HomepageModuleType.SearchBar
        val headerShape = RoundedCornerShape(14.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 1.dp)
                .then(
                    if (canOpenExplore) {
                        Modifier.clickable {
                            onModuleHeaderClick(
                                module.title,
                                module.sourceUrl,
                                rankingCurrentExploreUrl ?: module.exploreUrl
                            )
                        }
                    } else Modifier
                ),
            shape = headerShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(
                            color = pageAccentColor(),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 9.dp)
                )
                if (canOpenExplore) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.homepage_more),
                        tint = pageSecondaryTextColor(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Module content
        Box(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            val context = LocalContext.current
            // 独立组件（SearchBar）常驻渲染，不依赖加载状态
            if (module.type == HomepageModuleType.SearchBar) {
                SearchBarModule(
                    onSearch = { query ->
                        SearchActivity.start(context, query)
                    }
                )
            } else when (val state = module.state) {
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

                        HomepageModuleType.Grid, HomepageModuleType.InfiniteGrid -> {
                            // 无限网格同样采用窗口化揭示渲染，避免一次渲染全部已载书籍
                            var revealedCount by rememberSaveable(module.globalId) {
                                mutableStateOf(MODULE_REVEAL_STEP)
                            }
                            LaunchedEffect(state.books.size) {
                                if (revealedCount > state.books.size) revealedCount = state.books.size
                            }
                            // 布局参数从 registry 读取，未配置时回退到类型默认值（Grid 3 列 / 2 行，InfiniteGrid 3 列）
                            val gridColumns = module.config.layoutInt(module.type, "columns", 0)
                            val gridMaxRows = if (module.type == HomepageModuleType.InfiniteGrid) null
                            else module.config.layoutInt(module.type, "maxRows", 2).coerceIn(1, 4)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val displayBooks = if (module.type == HomepageModuleType.InfiniteGrid) {
                                    state.books.take(revealedCount)
                                } else state.books
                                GridModule(
                                    books = displayBooks,
                                    onClick = { book, _ -> onBookClick(book) },
                                    onLongClick = { book, _ -> onBookLongClick(book) },
                                    columns = gridColumns,
                                    maxRows = gridMaxRows
                                )
                                // 无限网格：先揭示本地窗口，再触发网络加载更多
                                if (module.type == HomepageModuleType.InfiniteGrid) {
                                    val hasLocalReveal = revealedCount < state.books.size
                                    if (hasLocalReveal) {
                                        LoadMoreFooter(
                                            isLoading = false,
                                            onClick = {
                                                revealedCount =
                                                    (revealedCount + MODULE_REVEAL_STEP).coerceAtMost(state.books.size)
                                            }
                                        )
                                    } else if (state.hasMore) {
                                        LoadMoreFooter(
                                            isLoading = state.isLoadingMore,
                                            onClick = {
                                                revealedCount += MODULE_REVEAL_STEP
                                                viewModel.loadMoreModule(module.globalId)
                                            }
                                        )
                                    }
                                }
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

                        HomepageModuleType.GridRanking -> GridRankingModule(
                            books = state.books,
                            onClick = { item -> onBookClick(item.book) },
                            onLongClick = { item -> onBookLongClick(item.book) },
                            onLoadMore = if (state.hasMore && !state.isLoadingMore) {
                                { viewModel.loadMoreModule(module.globalId) }
                            } else null
                        )

                        HomepageModuleType.Waterfall -> {
                            // 瀑布流布局 - 使用 Column+Row 实现两列，避免 LazyGrid 嵌套需要固定高度
                            // 窗口化揭示渲染：仅渲染「已揭示」的书籍，避免无限流加载过多后一次渲染全部导致卡顿
                            var revealedCount by rememberSaveable(module.globalId) {
                                mutableStateOf(MODULE_REVEAL_STEP)
                            }
                            // 书籍被刷新/重载变少时收敛窗口
                            LaunchedEffect(state.books.size) {
                                if (revealedCount > state.books.size) revealedCount = state.books.size
                            }
                            androidx.compose.foundation.layout.BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val displayBooks = state.books.take(revealedCount)
                                // 未配置列数时根据可用宽度自适应，避免手机/平板固定两列或过密。
                                val configuredColumns = module.config.layoutInt(module.type, "columns", 0)
                                val waterfallColumns = if (configuredColumns > 0) {
                                    configuredColumns.coerceIn(1, 6)
                                } else {
                                    (maxWidth / 170.dp).toInt().coerceIn(2, 4)
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 按列均分展示：第 i 本书放入第 (i % columns) 列
                                    (0 until waterfallColumns).forEach { col ->
                                        val colBooks = displayBooks.filterIndexed { index, _ -> index % waterfallColumns == col }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            colBooks.forEach { item ->
                                                WaterfallItem(
                                                    book = item,
                                                    onClick = { onBookClick(item.book) },
                                                    onLongClick = { onBookLongClick(item.book) }
                                                )
                                            }
                                        }
                                    }
                                }
                                }
                                // 本地还有已载入但未揭示的书籍：先揭示本地窗口
                                val hasLocalReveal = revealedCount < state.books.size
                                if (hasLocalReveal) {
                                    LoadMoreFooter(
                                        isLoading = false,
                                        onClick = {
                                            revealedCount =
                                                (revealedCount + MODULE_REVEAL_STEP).coerceAtMost(state.books.size)
                                        }
                                    )
                                } else if (state.hasMore) {
                                    // 本地全部揭示后：再触发网络加载更多
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        onClick = {
                                            revealedCount += MODULE_REVEAL_STEP
                                            viewModel.loadMoreModule(module.globalId)
                                        }
                                    )
                                }
                            }
                        }

                        HomepageModuleType.ButtonGroup -> {}
                        HomepageModuleType.SmartFilter -> {}
                        HomepageModuleType.SearchBar -> {}
                        HomepageModuleType.Unknown -> {}
                    }
                }

                is ModuleLoadState.SmartFilters -> {
                    SmartFilterModule(
                        kinds = state.kinds,
                        sourceUrl = module.sourceUrl,
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = { kind, value ->
                            viewModel.onSmartFilterChanged(module.globalId, kind, value)
                        },
                        onUrlClick = { sourceUrl, url, title ->
                            viewModel.onKindUrlClick(sourceUrl, url, title)
                        }
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // 为每个 Tab 保存当前页码（用于记忆翻页位置）
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

        // 内容区域（按 selectedIndex 做 key，切换 Tab 时重置分页/滚动状态）
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
            } // key(selectedIndex)
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