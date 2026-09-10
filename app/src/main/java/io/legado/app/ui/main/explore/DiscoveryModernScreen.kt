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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.CoverLoader
import io.legado.app.help.source.exploreKinds
import io.legado.app.ui.book.info.BookInfoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DISCOVERY_AUTO_PAGES = 5
private const val MAX_DISCOVERY_BOOKS = 60

@Composable
fun DiscoveryModernScreen(
    sources: List<BookSourcePart>,
    suites: List<DiscoverySuite>,
    selectedSuiteId: String,
    onSuiteSelected: (String) -> Unit,
    onOpen: (BookSourcePart, ExploreKind) -> Unit,
    onRefresh: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var manage by remember { mutableStateOf(false) }
    val selectedSuite = suites.firstOrNull { it.id == selectedSuiteId }
    val filtered = remember(sources, query) {
        if (query.isBlank()) sources else sources.filter {
            it.bookSourceName.contains(query, true) || it.bookSourceUrl.contains(query, true)
        }
    }
    if (manage) {
        DiscoverySuiteManager(suites, sources, selectedSuiteId, onSuiteSelected) { manage = false }
        return
    }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("发现", style = MaterialTheme.typography.headlineSmall)
                        Text("聚合书源发现、分类与榜单", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        TextButton({ manage = true }) { Text("管理") }
                        TextButton(onRefresh) { Text("刷新") }
                    }
                }
            }
            if (suites.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suites.sortedBy { it.order }.forEach { suite ->
                        FilterChip(suite.id == selectedSuiteId, { onSuiteSelected(suite.id) }, label = { Text(suite.displayName) })
                    }
                }
            }
            selectedSuite?.widgets?.sortedBy { it.order }?.forEach { widget ->
                item(key = widget.id) { DiscoveryWidgetCard(widget) }
            }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索书源") })
            }
            if (filtered.isEmpty()) item { Text("暂无可用发现源", Modifier.padding(24.dp)) }
            else items(filtered, key = { it.bookSourceUrl }) { source -> DiscoverySourceCard(source, onOpen) }
        }
    }
}

@Composable
private fun DiscoveryWidgetCard(widget: DiscoverySuiteWidget) {
    val context = LocalContext.current
    var page by remember(widget.id) { mutableStateOf(1) }
    var selectedTarget by remember(widget.id, widget.targets) { mutableStateOf(0) }
    var results by remember(widget.id, widget.targets, selectedTarget) { mutableStateOf<List<DiscoveryDataRepository.TargetResult>>(emptyList()) }
    var loading by remember(widget.id, widget.targets, selectedTarget) { mutableStateOf(true) }
    var hasMore by remember(widget.id, widget.targets, selectedTarget) { mutableStateOf(true) }
    var errorText by remember(widget.id, widget.targets, selectedTarget) { mutableStateOf<String?>(null) }
    var randomSeed by remember(widget.id) { mutableStateOf(0) }
    var selectedBook by remember(widget.id) { mutableStateOf<SearchBook?>(null) }
    val horizontalState = rememberLazyListState()
    val targets = remember(widget.targets) {
        widget.targets.filter { it.sourceUrl.isNotBlank() && it.tagUrl.isNotBlank() }.distinctBy { "${it.sourceUrl}|${it.tagUrl}" }
    }
    val safeTarget = selectedTarget.coerceIn(0, (targets.size - 1).coerceAtLeast(0))
    val targetWidget = remember(widget, safeTarget, targets) {
        if (widget.type == DiscoverySuiteWidgetType.TagBar.value || widget.type == DiscoverySuiteWidgetType.RankButtons.value)
            widget.copy(targets = targets.getOrNull(safeTarget)?.let { listOf(it) }.orEmpty())
        else widget.copy(targets = targets)
    }
    fun resetPaging() {
        page = 1
        results = emptyList()
        hasMore = true
        errorText = null
    }
    fun selectTarget(index: Int) {
        selectedTarget = index.coerceIn(0, (targets.size - 1).coerceAtLeast(0))
        resetPaging()
    }
    fun open(book: SearchBook) {
        context.startActivity(Intent(context, BookInfoActivity::class.java).apply {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
            putExtra("origin", book.origin)
        })
    }
    LaunchedEffect(widget.id, widget.targets, selectedTarget, page, randomSeed) {
        if (targetWidget.targets.isEmpty()) {
            loading = false
            results = emptyList()
            hasMore = false
            return@LaunchedEffect
        }
        loading = true
        val previousBookCount = results.flatMap { it.books }.distinctBy { "${it.origin}|${it.bookUrl}" }.size
        val next = DiscoveryDataRepository.loadWidget(targetWidget, page, forceRefresh = randomSeed > 0 && page == 1)
        val merged = if (page == 1 || randomSeed > 0) next else results + next
        results = merged
        errorText = next.firstOrNull { it.error != null }?.error
        val currentBookCount = merged.flatMap { it.books }.distinctBy { "${it.origin}|${it.bookUrl}" }.size
        val returnedFullPage = next.any { it.books.size >= MAX_DISCOVERY_BOOKS }
        hasMore = currentBookCount > previousBookCount && returnedFullPage && page < MAX_DISCOVERY_AUTO_PAGES
        loading = false
    }
    val pagedHorizontal = widget.layout() == DiscoveryWidgetLayout.Horizontal && widget.type != DiscoverySuiteWidgetType.RandomBooks.value
    val visibleLimit = if (pagedHorizontal) {
        (widget.displayLimit.coerceIn(1, MAX_DISCOVERY_BOOKS) * page).coerceAtMost(MAX_DISCOVERY_BOOKS)
    } else widget.displayLimit.coerceIn(1, MAX_DISCOVERY_BOOKS)
    val books = remember(results, visibleLimit, randomSeed, widget.type) {
        val all = results.flatMap { it.books }.distinctBy { "${it.origin}|${it.bookUrl}" }
        if (widget.type == DiscoverySuiteWidgetType.RandomBooks.value)
            all.shuffled(kotlin.random.Random(randomSeed.toLong() + widget.id.hashCode())).take(widget.displayLimit.coerceIn(1, MAX_DISCOVERY_BOOKS))
        else all.take(visibleLimit)
    }
    val canAutoLoadMore = pagedHorizontal && hasMore && page < MAX_DISCOVERY_AUTO_PAGES
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(widget.title, style = MaterialTheme.typography.titleMedium)
                Row {
                    if (widget.type == DiscoverySuiteWidgetType.RandomBooks.value) TextButton({ randomSeed++; resetPaging() }) { Text("换一批") }
                    if (loading) Text("加载中…", style = MaterialTheme.typography.bodySmall) else Text("${books.size} 本", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (widget.type == DiscoverySuiteWidgetType.TagBar.value && targets.isNotEmpty()) {
                DiscoveryTagBar(targets.map { DiscoverTagItem(ExploreKind(title = it.title), it.title.ifBlank { "分类" }, DiscoverTagItem.Role.UrlTag) }, safeTarget, ::selectTarget)
            }
            if (widget.type == DiscoverySuiteWidgetType.RankButtons.value && targets.isNotEmpty()) {
                DiscoveryRankButtons(targets.mapIndexed { index, target -> target.title.ifBlank { "榜单 ${index + 1}" } }, safeTarget, ::selectTarget)
            }
            errorText?.let { Text("部分目标加载失败：$it", style = MaterialTheme.typography.bodySmall) }
            if (books.isEmpty() && !loading) Text("暂无发现内容")
            else when (widget.layout()) {
                DiscoveryWidgetLayout.Horizontal -> DiscoveryHorizontalBooks(books = books, onClick = { selectedBook = it }, onLongClick = { selectedBook = it }, listState = horizontalState, loading = loading, onLoadMore = { if (canAutoLoadMore && !loading) page++ })
                DiscoveryWidgetLayout.Waterfall -> DiscoveryWaterfallBooks(books = books, onClick = { selectedBook = it }, onLongClick = { selectedBook = it })
                DiscoveryWidgetLayout.RankedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { books.forEachIndexed { index, book -> DiscoveryRankedBookRow(index + 1, book) { selectedBook = it } } }
                DiscoveryWidgetLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { books.forEach { book -> DiscoveryBookRow(book, null) { selectedBook = book } } }
            }
            if (!loading && books.isNotEmpty() && !pagedHorizontal && widget.type != DiscoverySuiteWidgetType.RandomBooks.value && widget.type != DiscoverySuiteWidgetType.TagBar.value && widget.type != DiscoverySuiteWidgetType.RankButtons.value) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (page > 1) TextButton({ page-- }) { Text("上一页") }
                    TextButton({ page++; hasMore = true }) { Text("加载下一页") }
                }
            }
        }
    }
    selectedBook?.let { book ->
        val state = BookshelfMatcher.getState(book.name, book.author, book.bookUrl)
        BookBottomSheetCompat(book, state, onDismiss = { selectedBook = null }, onOpen = { open(book) })
    }
}

@Composable
private fun DiscoveryBookRow(book: SearchBook, rank: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                modifier = Modifier.width(56.dp).height(76.dp),
                update = { view -> CoverLoader.load(view, book, AppConfig.loadCoverOnlyWifi, overrideWidth = 168, overrideHeight = 228) }
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((rank?.plus("  ") ?: "") + book.name.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(shelfLabel(book), style = MaterialTheme.typography.labelSmall)
                }
                Text(listOf(book.author, book.kind.orEmpty(), book.latestChapterTitle.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(book.originName.ifBlank { book.origin }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

private fun shelfLabel(book: SearchBook): String = when (BookshelfMatcher.getState(book.name, book.author, book.bookUrl)) {
    BookShelfState.IN_SHELF -> "✓ 已在书架"
    BookShelfState.SAME_NAME_AUTHOR -> "! 同名书籍"
    BookShelfState.NOT_IN_SHELF -> ""
}

@Composable
private fun BookBottomSheetCompat(book: SearchBook, state: BookShelfState, onDismiss: () -> Unit, onOpen: () -> Unit) {
    io.legado.app.ui.widget.components.BookBottomSheet(show = true, book = book, shelfState = state, onDismiss = onDismiss, onAddToShelf = { appDb.bookDao.insert(it.toBook()) }, onShowInfo = { onOpen() })
}

@Composable
private fun DiscoverySourceCard(source: BookSourcePart, onOpen: (BookSourcePart, ExploreKind) -> Unit) {
    var kinds by remember(source.bookSourceUrl) { mutableStateOf<List<ExploreKind>>(emptyList()) }
    LaunchedEffect(source.bookSourceUrl) {
        kinds = withContext(Dispatchers.IO) { appDb.bookSourceDao.getBookSource(source.bookSourceUrl)?.let { exploreKinds(it) }.orEmpty().filter { !it.url.isNullOrBlank() } }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(source.bookSourceName, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.take(16).forEach { kind -> FilterChip(false, { onOpen(source, kind) }, label = { Text(kind.title) }) }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteManager(suites: List<DiscoverySuite>, sources: List<BookSourcePart>, selectedSuiteId: String, onSelected: (String) -> Unit, onBack: () -> Unit) {
    var config by remember(suites) { mutableStateOf(DiscoverySuiteConfig(suites)) }
    var editingId by remember { mutableStateOf(selectedSuiteId.ifBlank { suites.firstOrNull()?.id.orEmpty() }) }
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var suiteDialog by remember { mutableStateOf(false) }
    var widgetDialog by remember { mutableStateOf(false) }
    var targetWidgetId by remember { mutableStateOf<String?>(null) }
    var sourceUrl by remember { mutableStateOf("") }
    var tagUrl by remember { mutableStateOf("") }
    var kinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }
    fun save(next: DiscoverySuiteConfig) { config = next; DiscoverySuiteStore.save(next); if (editingId.isNotBlank()) onSelected(editingId) }
    val current = config.suites.firstOrNull { it.id == editingId }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("发现套件", style = MaterialTheme.typography.headlineSmall); TextButton(onBack) { Text("返回") } } }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                config.suites.sortedBy { it.order }.forEach { suite -> FilterChip(suite.id == editingId, { editingId = suite.id; onSelected(suite.id) }, label = { Text(suite.displayName) }) }
                FilterChip(false, { creating = true; name = ""; alias = ""; suiteDialog = true }, label = { Text("＋ 新建") })
            } }
            current?.let { suite ->
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(suite.displayName, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ widgetDialog = true }) { Text("添加组件") }
                        TextButton({ creating = false; name = suite.name; alias = suite.alias; suiteDialog = true }) { Text("编辑") }
                        TextButton({ val rest = config.suites.filterNot { it.id == suite.id }; editingId = rest.firstOrNull()?.id.orEmpty(); save(DiscoverySuiteConfig(rest)) }) { Text("删除") }
                    }
                } } }
                items(suite.widgets.sortedBy { it.order }, key = { it.id }) { widget -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(widget.title, style = MaterialTheme.typography.titleMedium)
                    Text("${widget.type} · ${widget.targets.size} 个目标 · ${widget.displayLimit} 本")
                    Row {
                        TextButton({ targetWidgetId = widget.id; sourceUrl = ""; tagUrl = "" }) { Text("添加目标") }
                        TextButton({ save(DiscoverySuiteConfig(config.suites.map { s -> if (s.id == suite.id) s.copy(widgets = s.widgets.filterNot { it.id == widget.id }) else s })) }) { Text("删除") }
                    }
                } } }
            } ?: item { Text("暂无套件，请新建。", Modifier.padding(20.dp)) }
        }
    }
    if (suiteDialog) AlertDialog(onDismissRequest = { suiteDialog = false }, title = { Text(if (creating) "新建发现套件" else "编辑发现套件") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("名称") })
        OutlinedTextField(alias, { alias = it }, singleLine = true, label = { Text("显示别名") })
    } }, confirmButton = { TextButton({
        if (creating) { val s = DiscoverySuiteStore.newSuite(name).copy(alias = alias); editingId = s.id; save(DiscoverySuiteConfig(config.suites + s)) }
        else current?.let { c -> save(DiscoverySuiteConfig(config.suites.map { s -> if (s.id == c.id) s.copy(name = name, alias = alias) else s })) }
        suiteDialog = false
    }) { Text("保存") } }, dismissButton = { TextButton({ suiteDialog = false }) { Text("取消") } })
    if (widgetDialog && current != null) {
        var title by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(DiscoverySuiteWidgetType.RandomBooks.value) }
        AlertDialog(onDismissRequest = { widgetDialog = false }, title = { Text("添加发现组件") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("标题") })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { DiscoverySuiteWidgetType.entries.forEach { t -> FilterChip(type == t.value, { type = t.value }, label = { Text(t.value) }) } }
        } }, confirmButton = { TextButton({ val w = DiscoverySuiteStore.newWidget(title, type); save(DiscoverySuiteConfig(config.suites.map { s -> if (s.id == current.id) s.copy(widgets = s.widgets + w) else s })); widgetDialog = false }) { Text("添加") } }, dismissButton = { TextButton({ widgetDialog = false }) { Text("取消") } })
    }
    targetWidgetId?.let { id ->
        val widget = current?.widgets?.firstOrNull { it.id == id }
        if (widget != null) {
            LaunchedEffect(sourceUrl) { kinds = if (sourceUrl.isBlank()) emptyList() else withContext(Dispatchers.IO) { appDb.bookSourceDao.getBookSource(sourceUrl)?.let { exploreKinds(it) }.orEmpty().filter { !it.url.isNullOrBlank() } } }
            AlertDialog(onDismissRequest = { targetWidgetId = null }, title = { Text("添加发现目标") }, text = { Column(Modifier.horizontalScroll(rememberScrollState())) {
                if (sourceUrl.isBlank()) sources.forEach { s -> TextButton({ sourceUrl = s.bookSourceUrl }) { Text(s.bookSourceName) } }
                else kinds.forEach { k -> TextButton({ tagUrl = k.url.orEmpty() }) { Text(k.title) } }
            } }, confirmButton = { TextButton(enabled = sourceUrl.isNotBlank() && tagUrl.isNotBlank(), onClick = {
                val title = kinds.firstOrNull { it.url == tagUrl }?.title.orEmpty()
                val target = DiscoverySuiteWidgetTarget(sourceUrl, tagUrl, title)
                save(DiscoverySuiteConfig(config.suites.map { s -> if (s.id == current.id) s.copy(widgets = s.widgets.map { w -> if (w.id == id) w.copy(targets = (w.targets + target).distinctBy { "${it.sourceUrl}|${it.tagUrl}" }) else w }) else s }))
                targetWidgetId = null
            }) { Text("添加") } }, dismissButton = { TextButton({ targetWidgetId = null }) { Text("取消") } })
        }
    }
}
