package io.legado.app.ui.main.explore

import android.content.Intent
import android.widget.ImageView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.AppConfig
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.exploreKinds
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.widget.components.BookBottomSheet
import io.legado.app.ui.widget.components.CoverLoader
import io.legado.app.utils.BookshelfMatcher
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DISCOVERY_AUTO_PAGES = 5
private const val MAX_DISCOVERY_BOOKS = 60

@Composable
fun DiscoveryModernScreen(
    sources: List<BookSource>,
    modifier: Modifier = Modifier,
    onOpenSource: (BookSource) -> Unit = {}
) {
    val config = remember { DiscoverySuiteStore.load() }
    var selectedSuiteId by remember { mutableStateOf(DiscoverySuiteStore.selectedSuiteId()) }
    var query by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf(0) }
    val suites = remember(config, refreshToken) { config.suites.sortedBy { it.order } }
    val selected = suites.firstOrNull { it.id == selectedSuiteId } ?: suites.firstOrNull()

    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("发现") },
                    placeholder = { Text("搜索书籍") }
                )
                TextButton(onClick = { refreshToken++ }) { Text("刷新") }
            }
            if (suites.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suites.forEach { suite ->
                        FilterChip(
                            selected = suite.id == selected?.id,
                            onClick = {
                                selectedSuiteId = suite.id
                                DiscoverySuiteStore.setSelectedSuiteId(suite.id)
                            },
                            label = { Text(suite.displayName) }
                        )
                    }
                }
            }
            if (selected == null) {
                DiscoverySuiteManager(sources, onChanged = { refreshToken++ })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selected.widgets.sortedBy { it.order }.forEach { widget ->
                        item(key = widget.id) {
                            DiscoveryWidgetCard(
                                widget = widget,
                                query = query,
                                refreshToken = refreshToken,
                                onOpen = { book ->
                                    appDb.bookDao.findBook(book.name, book.author)?.let { appDb.bookDao.update(it.copy() as Any) }
                                    val intent = Intent(this@DiscoveryModernScreen.javaClass.classLoader?.let { null }, BookInfoActivity::class.java)
                                },
                                onSearch = { book -> SearchActivity.start(it) }
                            )
                        }
                    }
                    item { DiscoverySuiteManager(sources, onChanged = { refreshToken++ }) }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryWidgetCard(
    widget: DiscoverySuiteWidget,
    query: String,
    refreshToken: Int,
    onOpen: (SearchBook) -> Unit,
    onSearch: (SearchBook) -> Unit
) {
    var selectedTargetIndex by remember(widget.id) { mutableStateOf(0) }
    var page by remember(widget.id, refreshToken) { mutableStateOf(1) }
    var hasMore by remember(widget.id, refreshToken) { mutableStateOf(true) }
    var loading by remember(widget.id, refreshToken) { mutableStateOf(false) }
    var errorText by remember(widget.id, refreshToken) { mutableStateOf<String?>(null) }
    var books by remember(widget.id, refreshToken) { mutableStateOf<List<SearchBook>>(emptyList()) }
    var randomSeed by remember(widget.id, refreshToken) { mutableStateOf(0) }
    val horizontalState = rememberLazyListState()
    val targets = widget.targets
    val safeTarget = selectedTargetIndex.coerceIn(0, (targets.size - 1).coerceAtLeast(0))
    val pagedHorizontal = widget.layout() == DiscoveryWidgetLayout.Horizontal
    val visibleLimit = if (pagedHorizontal) (widget.displayLimit * page).coerceAtMost(MAX_DISCOVERY_BOOKS) else widget.displayLimit.coerceAtMost(MAX_DISCOVERY_BOOKS)
    val selectedTarget = targets.getOrNull(safeTarget)

    LaunchedEffect(widget.id, selectedTarget?.sourceUrl, selectedTarget?.tagUrl, page, randomSeed, query, refreshToken) {
        if (selectedTarget == null || selectedTarget.sourceUrl.isBlank() || selectedTarget.tagUrl.isBlank()) {
            books = emptyList()
            return@LaunchedEffect
        }
        loading = true
        errorText = null
        val result = DiscoveryDataRepository.loadTarget(
            selectedTarget.sourceUrl,
            selectedTarget.tagUrl,
            selectedTarget.title,
            page = page,
            forceRefresh = randomSeed > 0 || refreshToken > 0
        )
        if (page == 1) books = result.books
        else books = (books + result.books).distinctBy { "${it.origin}|${it.bookUrl}" }.take(MAX_DISCOVERY_BOOKS)
        hasMore = result.books.size >= 60 && page < MAX_DISCOVERY_AUTO_PAGES
        errorText = result.error
        loading = false
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(widget.title, style = MaterialTheme.typography.titleMedium)
                Row {
                    if (widget.type == DiscoverySuiteWidgetType.RandomBooks.value) TextButton({ randomSeed++; page = 1 }) { Text("换一批") }
                    if (loading) Text("加载中…", style = MaterialTheme.typography.bodySmall) else Text("${books.size} 本", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (widget.type == DiscoverySuiteWidgetType.TagBar.value && targets.isNotEmpty()) {
                DiscoveryTagBar(targets.map { DiscoverTagItem(ExploreKind(title = it.title), it.title.ifBlank { "分类" }, DiscoverTagItem.Role.UrlTag) }, safeTarget, { index -> selectedTargetIndex = index; page = 1 })
            }
            if (widget.type == DiscoverySuiteWidgetType.RankButtons.value && targets.isNotEmpty()) {
                DiscoveryRankButtons(targets.mapIndexed { index, target -> target.title.ifBlank { "榜单 ${index + 1}" } }, safeTarget, { index -> selectedTargetIndex = index; page = 1 })
            }
            errorText?.let { Text("部分目标加载失败：$it", style = MaterialTheme.typography.bodySmall) }
            if (books.isEmpty() && !loading) Text("暂无发现内容")
            else when (widget.layout()) {
                DiscoveryWidgetLayout.Horizontal -> DiscoveryHorizontalBooks(
                    books = books.take(visibleLimit),
                    onClick = { book -> onOpen(book) },
                    onLongClick = { book -> onOpen(book) },
                    listState = horizontalState,
                    loading = loading,
                    onLoadMore = { if (hasMore && !loading) page++ }
                )
                DiscoveryWidgetLayout.Waterfall -> DiscoveryWaterfallBooks(
                    books = books.take(visibleLimit),
                    onClick = { book -> onOpen(book) },
                    modifier = Modifier.fillMaxWidth(),
                    onLongClick = { book -> onOpen(book) }
                )
                DiscoveryWidgetLayout.RankedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { books.take(visibleLimit).forEachIndexed { index, book -> DiscoveryRankedBookRow(index + 1, book) { selected -> onOpen(selected) } } }
                DiscoveryWidgetLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { books.take(visibleLimit).forEach { book -> DiscoveryBookRow(book, null) { onOpen(book) } } }
            }
            if (!loading && books.isNotEmpty() && !pagedHorizontal && widget.type != DiscoverySuiteWidgetType.RandomBooks.value && widget.type != DiscoverySuiteWidgetType.TagBar.value && widget.type != DiscoverySuiteWidgetType.RankButtons.value) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (page > 1) TextButton({ page-- }) { Text("上一页") }
                    if (hasMore) TextButton({ page++; hasMore = true }) { Text("加载下一页") }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryBookRow(book: SearchBook, rank: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AndroidView(factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, modifier = Modifier.width(56.dp).height(80.dp), update = { CoverLoader.load(it, book, AppConfig.loadCoverOnlyWifi) })
            Column(Modifier.weight(1f)) {
                Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                if (book.author.isNotBlank()) Text(book.author, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                rank?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun BookBottomSheetCompat(book: SearchBook, state: BookshelfMatcher.State, onDismiss: () -> Unit, onOpen: () -> Unit) {
    BookBottomSheet(show = true, book = book, shelfState = state, onDismiss = onDismiss, onAddToShelf = { appDb.bookDao.insert(it.toBook()) }, onShowInfo = onOpen)
}

@Composable
private fun DiscoverySuiteManager(sources: List<BookSource>, onChanged: () -> Unit) {
    var config by remember { mutableStateOf(DiscoverySuiteStore.load()) }
    var showCreate by remember { mutableStateOf(false) }
    var suiteName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("发现套件", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { showCreate = true }) { Text("新增套件") }
        }
        config.suites.sortedBy { it.order }.forEach { suite ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(suite.displayName)
                    suite.widgets.sortedBy { it.order }.forEach { widget -> Text("• ${widget.title}") }
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建发现套件") },
            text = { OutlinedTextField(suiteName, { suiteName = it }, label = { Text("名称") }) },
            confirmButton = {
                TextButton(onClick = {
                    val suite = DiscoverySuiteStore.newSuite(suiteName)
                    val next = config.copy(suites = config.suites + suite)
                    DiscoverySuiteStore.save(next)
                    config = next
                    suiteName = ""
                    showCreate = false
                    onChanged()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } }
        )
    }
}
