package io.legado.app.ui.main.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.RssSource

@Composable
fun RimcharsRssScreen(
    sources: List<RssSource>,
    groups: List<String>,
    onSourceClick: (RssSource) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val filtered = remember(sources, selectedGroup) {
        selectedGroup?.let { group -> sources.filter { it.sourceGroup?.contains(group, true) == true } } ?: sources
    }
    val visibleGroups = remember(groups) { groups.filter { it.isNotBlank() }.distinct().take(24) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("订阅", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (selectedGroup == null) "管理并浏览已启用订阅源" else "正在浏览：$selectedGroup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onSearchClick),
                    contentAlignment = Alignment.Center
                ) { Text("⌕", style = MaterialTheme.typography.titleLarge) }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSearchClick),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            ) {
                Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("搜索订阅源 / 内容 / 分组", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (visibleGroups.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("分组", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RimcharsRssChip("全部", selectedGroup == null) { selectedGroup = null }
                        visibleGroups.forEach { group -> RimcharsRssChip(group, selectedGroup == group) { selectedGroup = group } }
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("订阅源", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${filtered.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (filtered.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无订阅源", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text("请启用订阅源或添加新的 RSS 地址", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(filtered, key = { it.sourceUrl }) { source ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSourceClick(source) },
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(source.sourceName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(source.sourceName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (source.singleUrl) "URL 订阅" else "RSS / Atom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(source.sourceUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    source.sourceComment?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    source.sourceGroup?.takeIf { it.isNotBlank() }?.let { RimcharsRssMetaChip(it) }
                }
            }
        }
    }
}

@Composable
private fun RimcharsRssChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1
    )
}

@Composable
private fun RimcharsRssMetaChip(text: String) {
    Text(
        text = text,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
