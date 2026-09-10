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
    val filtered = remember(sources, query) { if (query.isBlank()) sources else sources.filter { it.bookSourceName.contains(query, true) || it.bookSourceUrl.contains(query, true) } }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("发现", style = MaterialTheme.typography.headlineSmall); Text("浏览、搜索并组合书源发现内容", style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onRefresh) { Text("刷新") }
                }
            }
            if (suites.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suites.sortedBy { it.order }.forEach { suite -> FilterChip(suite.id == selectedSuiteId, { onSuiteSelected(suite.id) }, label = { Text(suite.displayName) }) }
                }
            }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索书源") }) }
            if (filtered.isEmpty()) item { Text("暂无可用发现源", Modifier.padding(24.dp)) }
            items(filtered, key = { it.bookSourceUrl }) { source -> DiscoverySourceCard(source, onOpen) }
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
                Button(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "分类") }
            }
            if (expanded) {
                LaunchedEffect(source.bookSourceUrl) { kinds = runCatching { appDb.bookSourceDao.getBookSource(source.bookSourceUrl)?.let { exploreKinds(it) }.orEmpty() }.getOrDefault(emptyList()) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.filter { it.url?.startsWith("http", true) == true }.forEach { kind -> FilterChip(false, { onOpen(source, kind) }, label = { Text(kind.title) }) }
                }
                if (kinds.isEmpty()) Text("该书源暂无可用分类", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
