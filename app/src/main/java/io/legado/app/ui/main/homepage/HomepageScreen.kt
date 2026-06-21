package io.legado.app.ui.main.homepage

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.manage.HomepageModuleManageSheet
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.HomepageModuleSkeleton
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.utils.showHelp
import kotlinx.coroutines.flow.collectLatest

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
                title = { Text("首页", fontWeight = FontWeight.Bold) },
                actions = {
                    // 模块管理
                    IconButton(onClick = { showManageSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "模块管理"
                        )
                    }
                    // 三点菜单（切换布局、帮助等）
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("切换布局") },
                                onClick = {
                                    showOverflowMenu = false
                                    showLayoutMenu = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("帮助") },
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
                                text = { Text("混合列表") },
                                onClick = {
                                    viewModel.setLayoutMode(0)
                                    showLayoutMenu = false
                                },
                                leadingIcon = {
                                    if (layoutMode == 0) Icon(Icons.Default.Dashboard, null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分源Tab") },
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
                        text = "暂无首页内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = pageSecondaryTextColor()
                    )
                    Text(
                        text = "请点击右上角设置按钮配置首页模块",
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
                paddingValues = paddingValues,
                viewModel = viewModel,
                context = context,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onRefresh,
            )
        } else {
            // 混合列表 模式：所有模块在一个列表中展示，无限类型模块排在底部
            val sortedModules = uiState.modules.sortedBy { module ->
                if (HomepageViewModel.isInfinite(module.type.key, null)) 1 else 0
            }
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onRefresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
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
                                viewModel.onAddToShelf(book)
                                Toast.makeText(context, "已加入书架: ${book.name}", Toast.LENGTH_SHORT).show()
                            },
                            onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                                viewModel.onModuleHeaderClick(sourceUrl, exploreUrl, title)
                            }
                        )
                    }
                }
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
}

/**
 * 分源Tab 布局
 *
 * 将模块按集（setName）分组，通过 Tab 切换展示不同集的模块。
 * 适用于书源较多、希望分源浏览的场景。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceTabLayout(
    modules: List<HomepageModuleUi>,
    paddingValues: PaddingValues,
    viewModel: HomepageViewModel,
    context: android.content.Context,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    // 按集名称分组，保持原始顺序
    val groupedModules = remember(modules) {
        modules.groupBy { it.setName }
    }
    val setNames = remember(groupedModules) { groupedModules.keys.toList() }
    var selectedTabIndex by remember { mutableStateOf(0) }

    // 确保 selectedTabIndex 不越界
    LaunchedEffect(setNames.size) {
        if (selectedTabIndex >= setNames.size) {
            selectedTabIndex = 0
        }
    }

    // 确保 selectedTabIndex 不越界（组合期间立即生效，防止隐藏集后索引越界崩溃）
    val safeTabIndex = if (setNames.isEmpty()) 0 else selectedTabIndex.coerceIn(0, setNames.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (setNames.isEmpty()) return@Column
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
            setNames.forEachIndexed { index, setName ->
                Tab(
                    selected = safeTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = setName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }
        // 当前选中 Tab 的模块列表，无限类型模块排在底部
        val currentModules = (groupedModules[setNames[safeTabIndex]] ?: emptyList()).sortedBy { module ->
            if (HomepageViewModel.isInfinite(module.type.key, null)) 1 else 0
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
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
                        onBookLongClick = { book ->
                            viewModel.onAddToShelf(book)
                            Toast.makeText(context, "已加入书架: ${book.name}", Toast.LENGTH_SHORT).show()
                        },
                        onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                            viewModel.onModuleHeaderClick(sourceUrl, exploreUrl, title)
                        }
                    )
                }
            }
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
        // Module header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable {
                    onModuleHeaderClick(module.title, module.sourceUrl, module.exploreUrl)
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = module.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (module.exploreUrl != null && module.type != HomepageModuleType.ButtonGroup) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "更多",
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
                                text = "加载失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "点击重试",
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
                        onKindClick = { sourceUrl, url ->
                            viewModel.onKindUrlClick(sourceUrl, url, module.title)
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
                text = "加载中...",
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
                text = "加载更多",
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
