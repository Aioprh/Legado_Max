package io.legado.app.ui.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.data.entities.SearchBook

/**
 * 兼容旧调用点使用单参数 onRankedLoadMore 的重载。
 * 新实现仍由 DiscoverySuiteHomeScreen 的双参数版本负责真正加载。
 */
@Composable
fun DiscoverySuiteHomeScreen(
    uiState: DiscoverySuiteHomeUiState,
    onSearchClick: () -> Unit,
    onSuiteClick: () -> Unit,
    onSuiteSelect: (DiscoverySuite) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onTagClick: (DiscoverySuiteWidgetTarget) -> Unit,
    onRefreshWidget: (DiscoverySuiteWidget) -> Unit,
    onHorizontalLoadMore: (DiscoverySuiteWidget) -> Unit,
    onRankedLoadMore: (DiscoverySuiteWidget) -> Unit,
    modifier: Modifier = Modifier
) {
    DiscoverySuiteHomeScreen(
        uiState = uiState,
        onSearchClick = onSearchClick,
        onSuiteClick = onSuiteClick,
        onSuiteSelect = onSuiteSelect,
        onBookClick = onBookClick,
        onTagClick = onTagClick,
        onRefreshWidget = onRefreshWidget,
        onHorizontalLoadMore = onHorizontalLoadMore,
        onRankedLoadMore = { widget, _ -> onRankedLoadMore(widget) },
        modifier = modifier
    )
}
