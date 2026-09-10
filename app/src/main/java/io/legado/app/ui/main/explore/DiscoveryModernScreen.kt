package io.legado.app.ui.main.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        DiscoverySuiteManager(
            suites = suites,
            sources = sources,
            selectedSuiteId = selectedSuiteId,
            onSelected = onSuiteSelected,
            onBack = { manage = false }
        )
        return
    }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("发现", style = MaterialTheme.typography.headlineSmall)
                        Text("聚合多个书源的发现分类", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        TextButton(onClick = { manage = true }) { Text("管理") }
                        TextButton(onClick = onRefresh) { Text("刷新") }
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
            if (selectedSuite != null) {
                items(selectedSuite.widgets.sortedBy { it.order }, key = { it.id }) { widget ->
                    DiscoveryWidgetCard(widget, sources, onOpen)
                }
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
private fun DiscoveryWidgetCard(widget: DiscoverySuiteWidget, sources: List<BookSourcePart>, onOpen: (BookSourcePart, ExploreKind) -> Unit) {
    var results by remember(widget.id, widget.targets) { mutableStateOf<List<DiscoveryDataRepository.TargetResult>>(emptyList()) }
    var loading by remember(widget.id, widget.targets) { mutableStateOf(true) }
    LaunchedEffect(widget.id, widget.targets) {
        loading = true
        results = DiscoveryDataRepository.loadWidget(widget)
        loading = false
    }
    val books = remember(results, widget.displayLimit) {
        results.flatMap { it.books }.distinctBy { "${it.origin}|${it.bookUrl}" }.take(widget.displayLimit.coerceIn(1, 60))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(widget.title, style = MaterialTheme.typography.titleMedium)
                if (loading) Text("加载中…", style = MaterialTheme.typography.bodySmall) else Text("${books.size} 本", style = MaterialTheme.typography.bodySmall)
            }
            when {
                loading -> Text("正在从书源获取发现内容…")
                books.isEmpty() -> Text(if (results.any { it.error != null }) "部分书源加载失败" else "暂无发现内容")
                widget.type == DiscoverySuiteWidgetType.HorizontalBooks.value || widget.type == DiscoverySuiteWidgetType.WaterfallBooks.value || widget.type == DiscoverySuiteWidgetType.RandomBooks.value -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(books, key = { "${it.origin}|${it.bookUrl}" }) { book -> DiscoveryBookCard(book) }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { books.forEach { DiscoveryBookRow(it) } }
            }
        }
    }
}

@Composable
private fun DiscoveryBookCard(book: SearchBook) {
    Card(Modifier.width(150.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.name.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(book.author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            book.kind?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            Text(book.originName.ifBlank { book.origin }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun DiscoveryBookRow(book: SearchBook) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(book.name.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall)
        Text(listOf(book.author, book.kind.orEmpty(), book.latestChapterTitle.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

@Composable
private fun DiscoverySourceCard(source: BookSourcePart, onOpen: (BookSourcePart, ExploreKind) -> Unit) {
    var kinds by remember(source.bookSourceUrl) { mutableStateOf<List<ExploreKind>>(emptyList()) }
    LaunchedEffect(source.bookSourceUrl) {
        kinds = withContext(Dispatchers.IO) {
            appDb.bookSourceDao.getBookSource(source.bookSourceUrl)?.let { exploreKinds(it) }.orEmpty().filter { !it.url.isNullOrBlank() }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(source.bookSourceName, style = MaterialTheme.typography.titleMedium)
            Text(source.bookSourceUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.take(12).forEach { kind -> FilterChip(false, { onOpen(source, kind) }, label = { Text(kind.title) }) }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteManager(suites: List<DiscoverySuite>, sources: List<BookSourcePart>, selectedSuiteId: String, onSelected: (String) -> Unit, onBack: () -> Unit) {
    var config by remember(suites) { mutableStateOf(DiscoverySuiteConfig(suites)) }
    var editingSuiteId by remember { mutableStateOf(selectedSuiteId.ifBlank { suites.firstOrNull()?.id.orEmpty() }) }
    var creatingSuite by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editAlias by remember { mutableStateOf("") }
    var showSuiteDialog by remember { mutableStateOf(false) }
    var showWidgetDialog by remember { mutableStateOf(false) }
    var targetWidgetId by remember { mutableStateOf<String?>(null) }
    var targetSourceUrl by remember { mutableStateOf("") }
    var targetKindUrl by remember { mutableStateOf("") }
    var targetKinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }

    fun save(next: DiscoverySuiteConfig) {
        config = next
        DiscoverySuiteStore.save(next)
        if (editingSuiteId.isNotBlank()) onSelected(editingSuiteId)
    }
    val current = config.suites.firstOrNull { it.id == editingSuiteId }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("发现套件", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.suites.sortedBy { it.order }.forEach { suite -> FilterChip(suite.id == editingSuiteId, { editingSuiteId = suite.id; onSelected(suite.id) }, label = { Text(suite.displayName) }) }
                    FilterChip(false, {
                        creatingSuite = true; editName = ""; editAlias = ""; showSuiteDialog = true
                    }, label = { Text("＋ 新建套件") })
                }
            }
            if (current == null) item { Text("还没有发现套件，请先创建。", Modifier.padding(20.dp)) }
            else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(current.name, style = MaterialTheme.typography.titleMedium)
                            if (current.alias.isNotBlank()) Text(current.alias, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { showWidgetDialog = true }) { Text("添加组件") }
                                TextButton(onClick = { creatingSuite = false; editName = current.name; editAlias = current.alias; showSuiteDialog = true }) { Text("编辑") }
                                TextButton(onClick = {
                                    val rest = config.suites.filterNot { it.id == current.id }
                                    editingSuiteId = rest.firstOrNull()?.id.orEmpty(); save(DiscoverySuiteConfig(rest))
                                }) { Text("删除") }
                            }
                        }
                    }
                }
                items(current.widgets.sortedBy { it.order }, key = { it.id }) { widget ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(widget.title, style = MaterialTheme.typography.titleMedium)
                            Text("${widget.type} · ${widget.targets.size} 个目标 · ${widget.displayLimit} 本")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { targetWidgetId = widget.id; targetSourceUrl = ""; targetKindUrl = "" }) { Text("添加目标") }
                                TextButton(onClick = {
                                    val rest = current.widgets.filterNot { it.id == widget.id }
                                    save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(widgets = rest) else it }))
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSuiteDialog) AlertDialog(
        onDismissRequest = { showSuiteDialog = false },
        title = { Text(if (creatingSuite) "新建发现套件" else "编辑发现套件") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(editName, { editName = it }, singleLine = true, label = { Text("名称") })
            OutlinedTextField(editAlias, { editAlias = it }, singleLine = true, label = { Text("显示别名") })
        } },
        confirmButton = { TextButton(onClick = {
            if (creatingSuite) {
                val created = DiscoverySuiteStore.newSuite(editName).copy(alias = editAlias)
                editingSuiteId = created.id; save(DiscoverySuiteConfig(config.suites + created))
            } else if (current != null) {
                save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(name = editName, alias = editAlias) else it }))
            }
            showSuiteDialog = false
        }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showSuiteDialog = false }) { Text("取消") } }
    )

    if (showWidgetDialog && current != null) {
        var title by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(DiscoverySuiteWidgetType.RandomBooks.value) }
        AlertDialog(
            onDismissRequest = { showWidgetDialog = false },
            title = { Text("添加发现组件") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("标题") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiscoverySuiteWidgetType.entries.forEach { t -> FilterChip(type == t.value, { type = t.value }, label = { Text(t.value) }) }
                }
            } },
            confirmButton = { TextButton(onClick = {
                val widget = DiscoverySuiteStore.newWidget(title, type)
                save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(widgets = it.widgets + widget) else it }))
                showWidgetDialog = false
            }) { Text("添加") } },
            dismissButton = { TextButton(onClick = { showWidgetDialog = false }) { Text("取消") } }
        )
    }

    targetWidgetId?.let { widgetId ->
        val widget = current?.widgets?.firstOrNull { it.id == widgetId }
        if (widget != null) {
            LaunchedEffect(targetSourceUrl) {
                targetKinds = if (targetSourceUrl.isBlank()) emptyList() else withContext(Dispatchers.IO) {
                    appDb.bookSourceDao.getBookSource(targetSourceUrl)?.let { exploreKinds(it) }.orEmpty().filter { !it.url.isNullOrBlank() }
                }
            }
            AlertDialog(
                onDismissRequest = { targetWidgetId = null },
                title = { Text("添加发现目标") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { source -> TextButton(onClick = { targetSourceUrl = source.bookSourceUrl; targetKindUrl = "" }) { Text(source.bookSourceName) } }
                    if (targetSourceUrl.isNotBlank()) {
                        Text("已选书源", style = MaterialTheme.typography.labelMedium)
                        targetKinds.forEach { kind -> TextButton(onClick = { targetKindUrl = kind.url.orEmpty() }) { Text(kind.title) } }
                    }
                } },
                confirmButton = { TextButton(enabled = targetSourceUrl.isNotBlank() && targetKindUrl.isNotBlank(), onClick = {
                    val title = targetKinds.firstOrNull { it.url == targetKindUrl }?.title.orEmpty()
                    val target = DiscoverySuiteWidgetTarget(targetSourceUrl, targetKindUrl, title)
                    save(DiscoverySuiteConfig(config.suites.map { suite -> if (suite.id == current.id) suite.copy(widgets = suite.widgets.map { w -> if (w.id == widgetId) w.copy(targets = (w.targets + target).distinctBy { "${it.sourceUrl}|${it.tagUrl}" }) else w }) else suite }))
                    targetWidgetId = null
                }) { Text("添加") } },
                dismissButton = { TextButton(onClick = { targetWidgetId = null }) { Text("取消") } }
            )
        }
    }
}
