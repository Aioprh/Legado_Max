package io.legado.app.ui.main.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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
    val filtered = remember(sources, query) {
        if (query.isBlank()) sources else sources.filter {
            it.bookSourceName.contains(query, true) || it.bookSourceUrl.contains(query, true)
        }
    }
    val selectedSuite = suites.firstOrNull { it.id == selectedSuiteId }

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
                        Text("浏览、搜索并组合书源发现内容", style = MaterialTheme.typography.bodySmall)
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
                        FilterChip(
                            selected = suite.id == selectedSuiteId,
                            onClick = { onSuiteSelected(suite.id) },
                            label = { Text(suite.displayName) }
                        )
                    }
                }
            }
            if (selectedSuite != null && selectedSuite.widgets.isNotEmpty()) {
                items(selectedSuite.widgets.sortedBy { it.order }, key = { it.id }) { widget ->
                    DiscoveryWidgetCard(widget, sources, onOpen)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索书源") }
                )
            }
            if (filtered.isEmpty()) item { Text("暂无可用发现源", Modifier.padding(24.dp)) }
            items(filtered, key = { it.bookSourceUrl }) { source -> DiscoverySourceCard(source, onOpen) }
        }
    }
}

@Composable
private fun DiscoverySuiteManager(
    suites: List<DiscoverySuite>,
    sources: List<BookSourcePart>,
    selectedSuiteId: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var config by remember(suites) { mutableStateOf(DiscoverySuiteConfig(suites)) }
    var editingSuiteId by remember { mutableStateOf(selectedSuiteId.ifBlank { config.suites.firstOrNull()?.id.orEmpty() }) }
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
        val id = editingSuiteId.ifBlank { next.suites.firstOrNull()?.id.orEmpty() }
        if (id.isNotBlank()) { editingSuiteId = id; onSelected(id) }
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
                    config.suites.sortedBy { it.order }.forEach { suite ->
                        FilterChip(suite.id == editingSuiteId, { editingSuiteId = suite.id; onSelected(suite.id) }, label = { Text(suite.displayName) })
                    }
                    AssistChip(onClick = { editName = ""; editAlias = ""; showSuiteDialog = true }, label = { Text("＋ 新建套件") })
                }
            }
            if (current == null) {
                item { Text("还没有发现套件，先创建一个。", Modifier.padding(20.dp)) }
            } else {
                item {
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(current.name, style = MaterialTheme.typography.titleMedium)
                                    if (current.alias.isNotBlank()) Text(current.alias, style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                    TextButton(onClick = { editName = current.name; editAlias = current.alias; showSuiteDialog = true }) { Text("编辑") }
                                    TextButton(onClick = {
                                        val rest = config.suites.filterNot { it.id == current.id }
                                        save(DiscoverySuiteConfig(rest))
                                        editingSuiteId = rest.firstOrNull()?.id.orEmpty()
                                    }) { Text("删除") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showWidgetDialog = true }) { Text("添加组件") }
                                TextButton(onClick = {
                                    val i = config.suites.indexOfFirst { it.id == current.id }
                                    if (i > 0) {
                                        val list = config.suites.toMutableList(); val x = list.removeAt(i); list.add(i - 1, x); save(DiscoverySuiteConfig(list))
                                    }
                                }) { Text("套件上移") }
                                TextButton(onClick = {
                                    val i = config.suites.indexOfFirst { it.id == current.id }
                                    if (i >= 0 && i < config.suites.lastIndex) {
                                        val list = config.suites.toMutableList(); val x = list.removeAt(i); list.add(i + 1, x); save(DiscoverySuiteConfig(list))
                                    }
                                }) { Text("套件下移") }
                            }
                        }
                    }
                }
                items(current.widgets.sortedBy { it.order }, key = { it.id }) { widget ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(widget.title, style = MaterialTheme.typography.titleMedium)
                                    Text(widget.type, style = MaterialTheme.typography.bodySmall)
                                    Text("目标 ${widget.targets.size} 个 · 最多 ${widget.displayLimit} 本", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    val list = current.widgets.filterNot { it.id == widget.id }
                                    save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(widgets = list) else it }))
                                }) { Text("删除") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    val i = current.widgets.indexOfFirst { it.id == widget.id }
                                    if (i > 0) { val list = current.widgets.toMutableList(); val x = list.removeAt(i); list.add(i - 1, x); save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(widgets = list) else it })) }
                                }) { Text("上移") }
                                TextButton(onClick = {
                                    val i = current.widgets.indexOfFirst { it.id == widget.id }
                                    if (i >= 0 && i < current.widgets.lastIndex) { val list = current.widgets.toMutableList(); val x = list.removeAt(i); list.add(i + 1, x); save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(widgets = list) else it })) }
                                }) { Text("下移") }
                                TextButton(onClick = { targetWidgetId = widget.id; targetSourceUrl = ""; targetKindUrl = "" }) { Text("添加目标") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSuiteDialog) {
        AlertDialog(
            onDismissRequest = { showSuiteDialog = false },
            title = { Text("${if (config.suites.any { it.id == editingSuiteId }) "编辑" else "新建"}发现套件") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(editName, { editName = it }, singleLine = true, label = { Text("名称") })
                OutlinedTextField(editAlias, { editAlias = it }, singleLine = true, label = { Text("显示别名（可选）") })
            } },
            confirmButton = { TextButton(onClick = {
                if (editingSuiteId.isNotBlank() && config.suites.any { it.id == editingSuiteId }) {
                    save(DiscoverySuiteConfig(config.suites.map { if (it.id == editingSuiteId) it.copy(name = editName, alias = editAlias) else it }))
                } else {
                    val s = DiscoverySuiteStore.newSuite(editName)
                    save(DiscoverySuiteConfig(config.suites + s.copy(alias = editAlias)))
                    editingSuiteId = s.id
                }
                showSuiteDialog = false
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showSuiteDialog = false }) { Text("取消") } }
        )
    }

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
                val w = DiscoverySuiteStore.newWidget(title, type)
                save(DiscoverySuiteConfig(config.suites.map { if (it.id == current.id) it.copy(widgets = it.widgets + w) else it }))
                showWidgetDialog = false
            }) { Text("添加") } },
            dismissButton = { TextButton(onClick = { showWidgetDialog = false }) { Text("取消") } }
        )
    }

    targetWidgetId?.let { wid ->
        val widget = current?.widgets?.firstOrNull { it.id == wid }
        if (widget != null) {
            LaunchedEffect(targetSourceUrl) {
                targetKinds = if (targetSourceUrl.isBlank()) emptyList() else withContext(Dispatchers.IO) {
                    appDb.bookSourceDao.getBookSource(targetSourceUrl)?.let { exploreKinds(it) }.orEmpty()
                }
            }
            AlertDialog(
                onDismissRequest = { targetWidgetId = null },
                title = { Text("添加发现目标") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var expandedSource by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expandedSource, { expandedSource = !expandedSource }) {
                        OutlinedTextField(targetSourceUrl, {}, readOnly = true, label = { Text("书源") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expandedSource, { expandedSource = false }) {
                            sources.forEach { s -> DropdownMenuItem({ Text(s.bookSourceName) }, { targetSourceUrl = s.bookSourceUrl; targetKindUrl = ""; expandedSource = false }) }
                        }
                    }
                    if (targetKinds.isNotEmpty()) {
                        var expandedKind by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expandedKind, { expandedKind = !expandedKind }) {
                            OutlinedTextField(targetKinds.firstOrNull { it.url == targetKindUrl }?.title.orEmpty(), {}, readOnly = true, label = { Text("发现分类") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expandedKind, { expandedKind = false }) {
                                targetKinds.filter { it.url?.startsWith("http", true) == true }.forEach { k -> DropdownMenuItem({ Text(k.title) }, { targetKindUrl = k.url.orEmpty(); expandedKind = false }) }
                            }
                        }
                    }
                } },
                confirmButton = { TextButton(enabled = targetSourceUrl.isNotBlank() && targetKindUrl.isNotBlank(), onClick = {
                    val kindTitle = targetKinds.firstOrNull { it.url == targetKindUrl }?.title.orEmpty()
                    val target = DiscoverySuiteWidgetTarget(targetSourceUrl, targetKindUrl, kindTitle)
                    save(DiscoverySuiteConfig(config.suites.map { suite -> if (suite.id == current.id) suite.copy(widgets = suite.widgets.map { if (it.id == wid) it.copy(targets = (it.targets + target).distinctBy { t -> "${t.sourceUrl}|${t.tagUrl}" }) else it }) else suite }))
                    targetWidgetId = null
                }) { Text("添加") } },
                dismissButton = { TextButton(onClick = { targetWidgetId = null }) { Text("取消") } }
            )
        }
    }
}

@Composable
private fun DiscoveryWidgetCard(widget: DiscoverySuiteWidget, sources: List<BookSourcePart>, onOpen: (BookSourcePart, ExploreKind) -> Unit) {
    var targets by remember(widget.id, sources) { mutableStateOf<List<Pair<BookSourcePart, ExploreKind>>>(emptyList()) }
    var loading by remember(widget.id, sources) { mutableStateOf(true) }
    LaunchedEffect(widget.id, widget.targets, widget.sourceUrls, widget.tagUrls) {
        loading = true
        targets = withContext(Dispatchers.IO) {
            val sourceByUrl = sources.associateBy { it.bookSourceUrl }
            val configured = widget.targets.mapNotNull { target ->
                val source = sourceByUrl[target.sourceUrl] ?: return@mapNotNull null
                val entity = appDb.bookSourceDao.getBookSource(source.bookSourceUrl) ?: return@mapNotNull null
                val kind = exploreKinds(entity).firstOrNull { it.url == target.tagUrl } ?: exploreKinds(entity).firstOrNull { it.title == target.title }
                kind?.let { source to it }
            }
            configured.distinctBy { "${it.first.bookSourceUrl}|${it.second.url}" }.take(widget.displayLimit.coerceIn(1, 60))
        }
        loading = false
    }
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(widget.title, style = MaterialTheme.typography.titleMedium); Text(widget.type, style = MaterialTheme.typography.labelSmall) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (targets.isEmpty()) Text("暂未配置发现目标", style = MaterialTheme.typography.bodySmall)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { targets.forEach { (source, kind) -> AssistChip({ onOpen(source, kind) }, label = { Text(kind.title.ifBlank { source.bookSourceName }) }) } }
        }
    }
}

@Composable
private fun DiscoverySourceCard(source: BookSourcePart, onOpen: (BookSourcePart, ExploreKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var kinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(source.bookSourceName, style = MaterialTheme.typography.titleMedium); Text(source.bookSourceUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "分类") }
            }
            if (expanded) {
                LaunchedEffect(source.bookSourceUrl) { kinds = withContext(Dispatchers.IO) { runCatching { appDb.bookSourceDao.getBookSource(source.bookSourceUrl)?.let { exploreKinds(it) }.orEmpty() }.getOrDefault(emptyList()) } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { kinds.filter { it.url?.startsWith("http", true) == true }.forEach { kind -> FilterChip(false, { onOpen(source, kind) }, label = { Text(kind.title) }) } }
                if (kinds.isEmpty()) Text("该书源暂无可用分类", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
