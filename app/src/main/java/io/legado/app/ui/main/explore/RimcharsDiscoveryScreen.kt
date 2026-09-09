package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.help.webView.WebViewPool
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

@Composable
fun RimcharsDiscoveryScreen(
    sources: List<BookSourcePart>,
    groups: List<String>,
    onSourceClick: (BookSourcePart) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var config by remember { mutableStateOf(DiscoverySuiteStore.load()) }
    var selectedId by remember { mutableStateOf(DiscoverySuiteStore.selectedSuiteId()) }
    var manage by remember { mutableStateOf(false) }

    val sourceTargets = remember(sources) {
        sources.mapNotNull { part ->
            val source = part.getBookSource() ?: return@mapNotNull null
            val url = source.exploreUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DiscoverySuiteWidgetTarget(part.bookSourceUrl, url, part.bookSourceName)
        }
    }

    LaunchedEffect(sourceTargets, config.suites) {
        if (config.suites.isEmpty()) {
            val suite = DiscoverySuiteStore.newSuite("默认发现")
            val widget = DiscoverySuiteStore.newWidget("推荐").copy(targets = sourceTargets.take(20), displayLimit = 12)
            val seeded = DiscoverySuiteConfig(listOf(suite.copy(widgets = listOf(widget))))
            DiscoverySuiteStore.save(seeded)
            DiscoverySuiteStore.setSelectedSuiteId(suite.id)
            config = seeded
            selectedId = suite.id
        } else if (selectedId.isBlank() || config.suites.none { it.id == selectedId }) {
            val id = config.suites.first().id
            selectedId = id
            DiscoverySuiteStore.setSelectedSuiteId(id)
        }
    }

    val selectedSuite = config.suites.firstOrNull { it.id == selectedId } ?: config.suites.firstOrNull()
    val suiteSignature = selectedSuite?.widgets?.joinToString("|") { it.cacheSignature() }.orEmpty()
    val booksByWidget = remember { mutableStateMapOf<String, List<SearchBook>>() }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(suiteSignature, sourceTargets) {
        booksByWidget.clear()
        val suite = selectedSuite ?: return@LaunchedEffect
        loading = true
        try {
            val result = withContext(IO) { suite.widgets.associate { it.id to loadSuiteBooks(it, sourceTargets) } }
            booksByWidget.putAll(result)
        } finally { loading = false }
    }

    val visibleSuites = config.suites.sortedBy { it.order }
    if (manage) {
        DiscoverySuiteManageDialog(config, selectedId, { manage = false }) { next ->
            config = next
            DiscoverySuiteStore.save(next)
            val id = next.suites.firstOrNull()?.id.orEmpty()
            selectedId = id
            if (id.isNotBlank()) DiscoverySuiteStore.setSelectedSuiteId(id)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("发现", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Text("发现套件", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("⌕", fontSize = 25.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onSearchClick).padding(9.dp))
                Text("⋮", fontSize = 25.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { manage = true }.padding(9.dp))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)), modifier = Modifier.fillMaxWidth().clickable(onClick = onSearchClick)) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(9.dp)); Text("搜索发现内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (visibleSuites.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleSuites, key = { it.id }) { suite ->
                        val selected = suite.id == selectedSuite?.id
                        Text(suite.displayName, modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable {
                            selectedId = suite.id; DiscoverySuiteStore.setSelectedSuiteId(suite.id)
                        }.padding(horizontal = 15.dp, vertical = 9.dp), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (loading) item { Text("正在加载发现内容…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
        if (selectedSuite == null) {
            item { Text("暂无套件，点击右上角 ⋮ 创建。", modifier = Modifier.padding(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            selectedSuite.widgets.forEach { widget ->
                item(key = widget.id) {
                    SuiteWidgetView(widget, booksByWidget[widget.id].orEmpty(), onSourceClick, sources, onRefresh = { booksByWidget.remove(widget.id) })
                }
            }
        }
    }
}

private suspend fun loadSuiteBooks(widget: DiscoverySuiteWidget, fallback: List<DiscoverySuiteWidgetTarget>): List<SearchBook> {
    val targets = widget.validTargets().ifEmpty { fallback }
    val output = mutableListOf<SearchBook>()
    for (target in targets.take(6)) {
        val source = appDb.bookSourceDao.getBookSource(target.sourceUrl) ?: continue
        val page = runCatching { WebBook.exploreBookAwait(source, target.tagUrl, 1, WebViewPool.Scope.DISCOVERY) }.getOrDefault(emptyList())
        output += page
        if (output.size >= widget.displayLimit) break
    }
    return output.distinctBy { "${it.name}|${it.author}|${it.originName}" }.take(widget.displayLimit)
}

@Composable
private fun SuiteWidgetView(widget: DiscoverySuiteWidget, books: List<SearchBook>, onSourceClick: (BookSourcePart) -> Unit, sources: List<BookSourcePart>, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(widget.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("换一批", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onRefresh).padding(7.dp))
        }
        Spacer(Modifier.height(7.dp))
        when (widget.type) {
            DiscoverySuiteWidgetType.TagBar.value -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(widget.validTargets(), key = { it.key() }) { target ->
                    Text(target.title.ifBlank { "分类" }, modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                        sources.firstOrNull { it.bookSourceUrl == target.sourceUrl }?.let(onSourceClick)
                    }.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 14.sp)
                }
            }
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(books, key = { "${it.name}|${it.originName}" }) { book ->
                    Card(Modifier.width(if (widget.type == DiscoverySuiteWidgetType.WaterfallBooks.value) 145.dp else 105.dp).clickable { SearchActivity.start(context, key = book.name) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                        Column(Modifier.padding(11.dp)) {
                            Text(book.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp)); Text(book.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (book.originName.isNotBlank()) Text(book.originName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteManageDialog(config: DiscoverySuiteConfig, selectedId: String, onDismiss: () -> Unit, onChange: (DiscoverySuiteConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val selected = config.suites.firstOrNull { it.id == selectedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现套件管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                config.suites.forEach { suite ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(suite.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { onChange(config.copy(suites = listOf(suite) + config.suites.filterNot { it.id == suite.id })) }) { Text("当前") }
                        TextButton(onClick = { if (config.suites.size > 1) onChange(config.copy(suites = config.suites.filterNot { it.id == suite.id })) }) { Text("删") }
                    }
                }
                selected?.let { Text("当前：${it.displayName} · ${it.widgets.size} 个控件", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (adding) OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("套件名称") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (adding) {
                    val suite = DiscoverySuiteStore.newSuite(name)
                    onChange(config.copy(suites = config.suites + suite)); name = ""; adding = false
                } else adding = true
            }) { Text(if (adding) "创建" else "新建套件") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
