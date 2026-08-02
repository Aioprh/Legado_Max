package io.legado.app.ui.main.homepage

/**
 * 排行榜 Tab 数据
 * @param title Tab 标题
 * @param exploreUrl 探索 URL（可为空）
 * @param books 当前已加载的书籍列表
 * @param page 当前页码（从1开始）
 * @param hasMore 是否还有更多数据
 * @param isLoadingMore 是否正在加载更多
 * @param errorMessage 错误信息（有错误时显示）
 */
data class RankingTabData(
    val title: String,
    val exploreUrl: String?,
    val books: List<HomepageBookItemUi>? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null
)