package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.Lifecycle

@Composable
fun ModernDiscoveryScreen(
    lifecycle: Lifecycle,
    onExploreSource: (BookSourcePart) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sources by remember { mutableStateOf<List<BookSourcePart>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        appDb.bookSourceDao.flowExplore()
            .flowWithLifecycleAndDatabaseChange(lifecycle, Lifecycle.State.STARTED, "book_sources")
            .distinctUntilChanged()
            .collect { sources = it }
    }

    val groups = remember(sources) {
        sources.flatMap { it.bookSourceGroup?.split(",") ?: emptyList() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
    val filtered = remember(sources, selectedGroup) {
        selectedGroup?.let { group ->
            sources.filter { it.bookSourceGroup?.split(",")?.any { g -> g.trim() == group } == true }
        } ?: sources
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("发现", style = MaterialTheme.typography.headlineSmall)
                Text("浏览可用书源，快速进入分类发现", fontSize = 13.sp)
            }
            AssistChip(onClick = onSearch, label = { Text("搜索") })
        }

        if (groups.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { selectedGroup = null },
                    label = { Text("全部") }
                )
                groups.forEach { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = if (selectedGroup == group) null else group },
                        label = { Text(group) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            filtered.forEach { source ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onExploreSource(source) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(4.dp).height(42.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(source.bookSourceName, fontSize = 16.sp)
                            Text(
                                source.bookSourceGroup?.takeIf { it.isNotBlank() } ?: "未分组",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(if (source.hasExploreUrl) "发现 ›" else "无发现", fontSize = 12.sp)
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无可用发现书源", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
