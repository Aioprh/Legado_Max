package io.legado.app.ui.config.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import kotlinx.coroutines.launch

/**
 * 日/夜分页管理器。
 *
 * 封装了 Tab 行 + HorizontalPager 的双向联动逻辑，以及摘要文本、空状态。
 * 各管理页只需传入 [dayContent] / [nightContent] 即可复用。
 *
 * @param state 通用状态 Holder（[ConfigManageState]）
 * @param onTabChange Tab 切换回调
 * @param summaryText 摘要文本资源 ID（显示在 Tab 下方）
 * @param scrollEnabled 是否允许多选模式下禁用滚动
 * @param contentPadding 来自 Scaffold 的内边距，避免内容被 TopAppBar 遮挡
 * @param dayContent 日间模式下的内容
 * @param nightContent 夜间模式下的内容
 */
@Composable
fun DayNightPager(
    state: ConfigManageState,
    onTabChange: (ConfigTab) -> Unit,
    summaryText: String? = null,
    scrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    dayContent: @Composable () -> Unit,
    nightContent: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = if (state.tab == ConfigTab.NIGHT) 1 else 0,
        pageCount = { 2 }
    )

    // Pager → Tab 联动
    LaunchedEffect(pagerState.currentPage) {
        val newTab = if (pagerState.currentPage == 0) ConfigTab.DAY else ConfigTab.NIGHT
        if (state.tab != newTab) {
            state.tab = newTab
        }
    }

    // Tab → Pager 联动
    LaunchedEffect(state.tab) {
        val targetPage = if (state.tab == ConfigTab.NIGHT) 1 else 0
        if (pagerState.currentPage != targetPage) {
            scope.launch {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        // Tab 行
        SegmentedTabRow(
            tabs = ConfigTab.entries,
            selected = state.tab,
            onTabClick = onTabChange,
            labelText = { tab ->
                when (tab) {
                    ConfigTab.DAY -> stringResource(R.string.day)
                    ConfigTab.NIGHT -> stringResource(R.string.night)
                }
            },
            iconContent = { tab ->
                Icon(
                    imageVector = when (tab) {
                        ConfigTab.DAY -> Icons.Default.LightMode
                        ConfigTab.NIGHT -> Icons.Default.DarkMode
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        // 摘要文本
        if (summaryText != null) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Pager
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            userScrollEnabled = scrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 0) dayContent() else nightContent()
        }
    }
}

/**
 * 通用配置列表（含空状态）。
 *
 * @param items 数据列表
 * @param itemKey 列表项稳定 key
 * @param itemContent 列表项 Composable
 */
@Composable
fun <T> ConfigList(
    items: List<T>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    itemKey: ((T) -> Any)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    emptyText: String = stringResource(R.string.empty),
    itemContent: @Composable (T) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement
        ) {
            items(
                items = items,
                key = itemKey
            ) { item ->
                itemContent(item)
            }
        }
    }
}
