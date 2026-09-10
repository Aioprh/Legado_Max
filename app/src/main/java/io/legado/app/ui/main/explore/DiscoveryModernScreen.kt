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
import io.legado.app.model.webBook.WebBook
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
        DiscoverySuiteManagePanel(
            suites = suites,
            selectedSuiteId = selectedSuiteId,
            onSuiteSelected = onSuiteSelected,
            onClose = { manage = false }
        )
        return
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("搜索书源") }
                )
                TextButton(onClick = onRefresh) { Text("刷新") }
                TextButton(onClick = { manage = true }) { Text("管理") }
            }
            if (suites.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suites.forEach { suite ->
                        FilterChip(
                            selected = suite.id == selectedSuiteId,
                            onClick = { onSuiteSelected(suite.id) },
                            label = { Text(suite.name) }
                        )
                    }
                }
            }
            if (selectedSuite == null) {
                Text("暂无发现方案", style = MaterialTheme.typography.bodyMedium)
            } else {
                DiscoverySuiteContent(
                    suite = selectedSuite,
                    sources = filtered,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun DiscoverySuiteManagePanel(
    suites: List<DiscoverySuite>,
    selectedSuiteId: String,
    onSuiteSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("发现方案", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text("完成") }
            }
            suites.forEach { suite ->
                FilterChip(
                    selected = suite.id == selectedSuiteId,
                    onClick = { onSuiteSelected(suite.id) },
                    label = { Text(suite.name) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DiscoverySuiteContent(
    suite: DiscoverySuite,
    sources: List<BookSourcePart>,
    onOpen: (BookSourcePart, ExploreKind) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        suite.widgets.forEach { widget ->
            DiscoverySuiteWidgetContent(widget, sources, onOpen)
        }
    }
}

@Composable
private fun DiscoverySuiteWidgetContent(
    widget: DiscoverySuiteWidget,
    sources: List<BookSourcePart>,
    onOpen: (BookSourcePart, ExploreKind) -> Unit
) {
    var targets by remember(widget.id, sources) {
        mutableStateOf(emptyList<Pair<BookSourcePart, ExploreKind>>())
    }
    LaunchedEffect(widget.id, sources) {
        targets = withContext(Dispatchers.IO) {
            sources.flatMap { source ->
                val bookSource = source.getBookSource() ?: return@flatMap emptyList()
                bookSource.exploreKinds().map { kind -> source to kind }
            }.filter { (source, _) ->
                widget.sourceIds.isEmpty() || source.bookSourceUrl in widget.sourceIds
            }.filter { (_, kind) -> !kind.url.isNullOrBlank() }.take(20)
        }
    }
    var selectedTarget by remember(widget.id) { mutableStateOf(0) }
    var selectedBook by remember { mutableStateOf<SearchBook?>(null) }
    var page by remember(widget.id) { mutableStateOf(1) }
    var randomSeed by remember(widget.id) { mutableStateOf(0) }
    var loading by remember(widget.id) { mutableStateOf(false) }
    var errorText by remember(widget.id) { mutableStateOf<String?>(null) }
    var books by remember(widget.id) { mutableStateOf<List<SearchBook>>(emptyList()) }
    val horizontalState = rememberLazyListState()
    val safeTarget = if (targets.isEmpty()) 0 else selectedTarget.coerceIn(0, targets.lastIndex)
    val target = targets.getOrNull(safeTarget)

    LaunchedEffect(widget.id, target, page, randomSeed) {
        if (target == null) {
            books = emptyList()
            return@LaunchedEffect
        }
        val source = target.first.getBookSource()
        val url = target.second.url
        if (source == null || url.isNullOrBlank()) {
            books = emptyList()
            errorText = "书源或发现地址无效"
            return@LaunchedEffect
        }
        loading = true
        errorText = null
        val result = runCatching {
            withContext(Dispatchers.IO) {
                WebBook.exploreBookAwait(source, url, page)
            }
        }
        result.onSuccess { loaded ->
            books = if (page == 1) {
                loaded.take(MAX_DISCOVERY_BOOKS)
            } else {
                (books + loaded)
                    .distinctBy { "${it.origin}|${it.bookUrl}" }
                    .take(MAX_DISCOVERY_BOOKS)
            }
        }.onFailure { errorText = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(selectedBook) {
        selectedBook?.let { book ->
            val context = LocalContext.current
            context.startActivity(Intent(context, BookInfoActivity::class.java).apply {
                putExtra("bookUrl", book.bookUrl)
                putExtra("origin", book.origin)
            })
            selectedBook = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(widget.title.ifBlank { "发现" }, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (widget.type == DiscoverySuiteWidgetType.RandomBooks.value) TextButton({ randomSeed++; page = 1 }) { Text("换一批") }
                if (loading) Text("加载中…", style = MaterialTheme.typography.bodySmall) else Text("${books.size} 本", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (widget.type == DiscoverySuiteWidgetType.TagBar.value && targets.isNotEmpty()) {
            DiscoveryTagBar(targets.map { DiscoverTagItem(ExploreKind(title = it.second.title), it.second.title.ifBlank { "分类" }, DiscoverTagItem.Role.UrlTag) }, safeTarget, { index -> selectedTarget = index; page = 1 })
        }
        if (widget.type == DiscoverySuiteWidgetType.RankButtons.value && targets.isNotEmpty()) {
            DiscoveryRankButtons(targets.mapIndexed { index, targetItem -> targetItem.second.title.ifBlank { "榜单 ${index + 1}" } }, safeTarget, { index -> selectedTarget = index; page = 1 })
        }
        errorText?.let { Text("部分目标加载失败：$it", style = MaterialTheme.typography.bodySmall) }
        if (books.isEmpty() && !loading) Text("暂无发现内容")
        else when (widget.layout()) {
            DiscoveryWidgetLayout.Horizontal -> DiscoveryHorizontalBooks(books = books, onClick = { book -> selectedBook = book }, onLongClick = { book -> selectedBook = book }, listState = horizontalState, loading = loading, onLoadMore = { if (page < MAX_DISCOVERY_AUTO_PAGES && !loading) page++ })
            DiscoveryWidgetLayout.Waterfall -> DiscoveryModernWaterfallBooks(books = books, onClick = { book -> selectedBook = book }, onLongClick = { book -> selectedBook = book })
            DiscoveryWidgetLayout.RankedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { books.forEachIndexed { index, book -> DiscoveryRankedBookRow(index + 1, book) { selectedBook = it } } }
            DiscoveryWidgetLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { books.forEach { book -> DiscoveryBookRow(book, null) { selectedBook = book } } }
        }
        if (!loading && books.isNotEmpty() && widget.layout() != DiscoveryWidgetLayout.Horizontal && widget.type != DiscoverySuiteWidgetType.RandomBooks.value && widget.type != DiscoverySuiteWidgetType.TagBar.value && widget.type != DiscoverySuiteWidgetType.RankButtons.value) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page > 1) TextButton({ page-- }) { Text("上一页") }
                TextButton({ page++ }) { Text("加载下一页") }
            }
        }
    }
}

@Composable
private fun DiscoveryBookRow(book: SearchBook, shelfState: BookShelfState?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                modifier = Modifier.width(70.dp).height(96.dp),
                update = { view -> CoverLoader.load(view, book, AppConfig.loadCoverOnlyWifi) }
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                if (book.author.isNotBlank()) Text(book.author, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                book.kind?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun DiscoveryBookPreview(book: SearchBook, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.name.ifBlank { "未命名" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("作者：${book.author.ifBlank { "未知" }}")
                book.kind?.takeIf { it.isNotBlank() }?.let { Text("分类：$it") }
                book.latestChapterTitle?.takeIf { it.isNotBlank() }?.let { Text("最新：$it") }
                book.intro?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 5) }
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text("打开详情") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
