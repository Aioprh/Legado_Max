package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.main.homepage.DiscoverSourceUi
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.card.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 发现聚合模块。
 *
 * 从单纯的“书源卡片网格”升级为发现中心：
 * 1. 顶部按书源快速筛选；
 * 2. 每个书源独立展示发现分类；
 * 3. 分类默认窗口化展示，支持展开全部分类；
 * 4. 书源与分类均保持独立点击入口，不改变原有导航协议。
 *
 * 这样既保留首页模块化体系，又具备 Discovery Suite 的基础交互骨架。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverHubModule(
    sources: List<DiscoverSourceUi>,
    columns: Int,
    showDesc: Boolean,
    showTime: Boolean,
    onKindClick: (sourceUrl: String, url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) {
        Text(
            text = stringResource(R.string.homepage_discover_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = pageSecondaryTextColor()
        )
        return
    }

    val availableSources = remember(sources) {
        sources.filter { it.kinds.any { kind -> kind.isNavigable() } }
    }
    var selectedSourceUrl by remember { mutableStateOf<String?>(null) }
    val expandedSources = remember { mutableStateOf<Set<String>>(emptySet()) }

    val visibleSources = remember(sources, selectedSourceUrl) {
        if (selectedSourceUrl == null) sources
        else sources.filter { it.sourceUrl == selectedSourceUrl }
    }

    val displayColumns = if (selectedSourceUrl == null) {
        columns.coerceIn(1, 3)
    } else {
        1
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Discovery Suite 的源选择器：只负责过滤，不重新请求网络。
        SourceFilterBar(
            sources = availableSources,
            selectedSourceUrl = selectedSourceUrl,
            onSelect = { url ->
                selectedSourceUrl = if (selectedSourceUrl == url) null else url
            }
        )

        visibleSources.chunked(displayColumns).forEach { rowSources ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSources.forEach { source ->
                    DiscoverSourceCard(
                        source = source,
                        showDesc = showDesc,
                        showTime = showTime,
                        expanded = expandedSources.value.contains(source.sourceUrl),
                        onToggleExpanded = {
                            expandedSources.value =
                                if (expandedSources.value.contains(source.sourceUrl)) {
                                    expandedSources.value - source.sourceUrl
                                } else {
                                    expandedSources.value + source.sourceUrl
                                }
                        },
                        onKindClick = onKindClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(displayColumns - rowSources.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SourceFilterBar(
    sources: List<DiscoverSourceUi>,
    selectedSourceUrl: String?,
    onSelect: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            title = "全部",
            selected = selectedSourceUrl == null,
            onClick = {
                if (selectedSourceUrl != null) onSelect(selectedSourceUrl)
            }
        )
        sources.forEach { source ->
            FilterChip(
                title = source.name,
                selected = selectedSourceUrl == source.sourceUrl,
                onClick = { onSelect(source.sourceUrl) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            pageAccentColor().copy(alpha = 0.16f)
        } else {
            pageSecondaryTextColor().copy(alpha = 0.08f)
        },
        contentColor = if (selected) pageAccentColor() else pageSecondaryTextColor(),
        onClick = onClick,
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverSourceCard(
    source: DiscoverSourceUi,
    showDesc: Boolean,
    showTime: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onKindClick: (sourceUrl: String, url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navKinds = remember(source) {
        source.kinds.filter { it.isNavigable() }
    }
    val previewLimit = 8
    val displayKinds = if (expanded) navKinds else navKinds.take(previewLimit)
    val hiddenCount = (navKinds.size - displayKinds.size).coerceAtLeast(0)

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (showDesc && !source.group.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = source.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showTime) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatUpdateTime(source.updateTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = pageSecondaryTextColor().copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (navKinds.isEmpty()) {
                Text(
                    text = stringResource(R.string.homepage_discover_no_kinds),
                    style = MaterialTheme.typography.labelSmall,
                    color = pageSecondaryTextColor().copy(alpha = 0.6f),
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    displayKinds.forEach { kind ->
                        KindChip(
                            title = kind.title,
                            onClick = {
                                onKindClick(source.sourceUrl, kind.url ?: "", kind.title)
                            }
                        )
                    }
                }

                if (hiddenCount > 0 || expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pageSecondaryTextColor().copy(alpha = 0.08f),
                        contentColor = pageSecondaryTextColor(),
                        onClick = onToggleExpanded,
                    ) {
                        Text(
                            text = if (expanded) {
                                "收起分类"
                            } else {
                                "更多分类 $hiddenCount"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = pageAccentColor().copy(alpha = 0.10f),
        contentColor = pageAccentColor(),
        onClick = onClick,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** 仅对可导航的分类（含 URL 的 url 类型）交互。 */
private fun ExploreKind.isNavigable(): Boolean =
    type == ExploreKind.Type.url && !url.isNullOrBlank()

private fun formatUpdateTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
