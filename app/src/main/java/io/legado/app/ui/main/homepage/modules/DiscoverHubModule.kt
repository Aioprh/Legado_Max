package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * 发现聚合（DiscoverHub）渲染器 —— 将"首页模块"提升到 legado 发现功能的聚合总览：
 * 一块卡片内集中展示所有启用发现的书源及其可导航分类，点击分类直达该书源发现页。
 *
 * @param sources 聚合的书源列表
 * @param columns 卡片列数（用户可在模块配置中调整，默认 2）
 * @param showDesc 是否显示分组描述
 * @param showTime 是否显示更新时间
 * @param onKindClick 点击分类回调（sourceUrl, url, title）
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
    val cols = columns.coerceIn(1, 4)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sources.chunked(cols).forEach { rowSources ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSources.forEach { source ->
                    DiscoverSourceCard(
                        source = source,
                        showDesc = showDesc,
                        showTime = showTime,
                        onKindClick = onKindClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(cols - rowSources.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverSourceCard(
    source: DiscoverSourceUi,
    showDesc: Boolean,
    showTime: Boolean,
    onKindClick: (sourceUrl: String, url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
            val navKinds = source.kinds.filter { it.isNavigable() }
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
                    navKinds.take(8).forEach { kind ->
                        KindChip(
                            title = kind.title,
                            onClick = {
                                onKindClick(source.sourceUrl, kind.url ?: "", kind.title)
                            }
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

/** 仅对可导航的分类（含 URL 的 url 类型）交互 */
private fun ExploreKind.isNavigable(): Boolean =
    type == ExploreKind.Type.url && !url.isNullOrBlank()

private fun formatUpdateTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}