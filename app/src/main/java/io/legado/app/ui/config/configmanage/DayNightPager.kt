package io.legado.app.ui.config.configmanage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 日/夜分页管理器（通用 Composable）。
 *
 * 封装了以下通用逻辑：
 * - 顶部 [SegmentedTabRow] 胶囊 Tab（日间/夜间）
 * - [HorizontalPager] 左右滑动切换日/夜列表，与 Tab 双向联动
 * - Tab 与 Pager 之间的同步（点击 Tab 滑动，滑动更新 Tab）
 * - 可选的摘要文本
 * - 空状态提示
 * - 多选模式下禁用滑动
 *
 * 列表内容完全由调用方通过 [dayContent] / [nightContent] 插槽提供。
 *
 * @param state           通用配置管理状态（提供 tab 和 multiSelect 信息）
 * @param onTabChange     Tab 切换回调（由调用方转发给自己的 ViewModel 或直接操作 state）
 * @param summaryText     摘要文本，为 null 时不显示
 * @param scrollEnabled   是否允许左右滑动（多选模式下应禁用）
 * @param dayContent      日间页内容
 * @param nightContent    夜间页内容
 */
@Composable
fun DayNightPager(
    state: ConfigManageState,
    onTabChange: (ConfigTab) -> Unit,
    summaryText: String? = null,
    scrollEnabled: Boolean = true,
    dayContent: @Composable () -> Unit,
    nightContent: @Composable () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = if (state.tab == ConfigTab.DAY) 0 else 1,
        pageCount = { 2 }
    )

    // Tab → Pager：点击胶囊时滑动到对应页
    LaunchedEffect(state.tab) {
        val targetPage = if (state.tab == ConfigTab.DAY) 0 else 1
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Pager → Tab：左右滑动时更新 tab
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val newTab = if (page == 0) ConfigTab.DAY else ConfigTab.NIGHT
                if (state.tab != newTab) {
                    onTabChange(newTab)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 日/夜 Tab
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
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        )

        // 摘要文本
        if (summaryText != null) {
            Text(
                text = summaryText,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // 左右滑动
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = scrollEnabled
        ) { page ->
            if (page == 0) dayContent() else nightContent()
        }
    }
}

/**
 * 通用配置列表页内容（LazyColumn + 空状态 + 底部安全间距）。
 *
 * 供 [DayNightPager] 的 [dayContent] / [nightContent] 插槽使用，
 * 避免每个管理页都重复写空状态判断和底部 Spacer。
 *
 * @param items    当前页的数据列表
 * @param empty    列表为空时显示的文本
 * @param itemKey  LazyColumn item 的 key 提取函数
 * @param itemContent 单个条目的渲染函数
 */
@Composable
fun <T> ConfigList(
    items: List<T>,
    emptyText: String = stringResource(R.string.empty),
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = items,
                key = itemKey
            ) { item ->
                itemContent(item)
            }
            item {
                // 底部安全距离
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
