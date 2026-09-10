package io.legado.app.ui.main.explore

import io.legado.app.data.entities.SearchBook

/**
 * 发现套件首页 UI 状态。
 *
 * 承载选中套件、套件列表，以及按 widget 聚合的书籍数据。
 */
data class DiscoverySuiteHomeUiState(
    val suites: List<DiscoverySuite> = emptyList(),
    val selectedSuiteId: String = "",
    val selectedSuite: DiscoverySuite? = null,
    val widgetBooks: Map<String, List<SearchBook>> = emptyMap(),
    val rankedWidgetBooks: Map<String, Map<String, List<SearchBook>>> = emptyMap(),
    val loadingWidgetIds: Set<String> = emptySet(),
    val scrollToTopSignal: Int = 0
) {
    val selectedSuiteName: String
        get() = selectedSuite?.displayName.orEmpty()
}