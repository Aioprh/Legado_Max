package io.legado.app.ui.main.explore

import io.legado.app.data.entities.SearchBook

/**
 * 发现套件共享的辅助扩展函数。
 *
 * 复刻自 Rimchars/legado 的发现套件逻辑，供首页 ViewModel/Screen 与套件管理页共用。
 * 口径与参考项目保持一致（见各函数注释）。
 */

/** 稳定的目标 key：sourceUrl + tagUrl。 */
fun DiscoverySuiteWidgetTarget.deckKey(): String {
    return "$sourceUrl\n$tagUrl"
}

/** 有效（sourceUrl、tagUrl 均非空）的目标列表。 */
fun DiscoverySuiteWidget.validRandomTargets(): List<DiscoverySuiteWidgetTarget> {
    return targets.filter { it.sourceUrl.isNotBlank() && it.tagUrl.isNotBlank() }
}

/**
 * 稳定的列表 item key，口径与加载侧 distinctBy { suiteDeckKey() } 完全一致：
 * bookUrl 非空用 origin|bookUrl，否则退回 origin|name|author，避免空 bookUrl 撞 key。
 */
fun SearchBook.suiteDeckKey(): String {
    return when {
        bookUrl.isNotBlank() -> "$origin|$bookUrl"
        author.isNotBlank() -> "$origin|$name|$author"
        else -> "$origin|$name"
    }
}

/** 刷屏用的稳定 key（含封面，用于 Compose remember 触发封面包换）。 */
fun SearchBook.displayKey(): String {
    return "$origin|$bookUrl|${coverUrl.orEmpty()}"
}

/** 纯按钮类 widget（标签条 / 排行按钮），无需抓取书籍。 */
fun DiscoverySuiteWidget.isSuiteButtonOnlyWidget(): Boolean {
    return type == DiscoverySuiteWidgetType.TagBar.value ||
        type == DiscoverySuiteWidgetType.RankButtons.value
}

/** 是否需要抓取书籍。 */
fun DiscoverySuiteWidget.needsFetch(): Boolean {
    return !isSuiteButtonOnlyWidget()
}

/** 套件快照签名：任一 widget 的顺序/配置变化都会使签名改变。 */
fun DiscoverySuite.cacheSignature(): String {
    return widgets.joinToString(separator = "\u001D") { widget ->
        "${widget.order}\u001C${widget.cacheSignature()}"
    }
}

/** widget 快照签名（供进程内缓存判断是否过期）。 */
fun DiscoverySuiteWidget.cacheSignature(): String {
    return listOf(
        id,
        type,
        displayLimit.toString(),
        validRandomTargets().joinToString(separator = "\u001C") { it.deckKey() }
    ).joinToString(separator = "\u001D")
}

/** 展示层书名标题：空标题或默认占位标题时按类型回退。 */
fun DiscoverySuiteWidget.displayTitle(): String {
    val title = title.trim()
    return when {
        title.isNotBlank() -> title
        type == DiscoverySuiteWidgetType.TagBar.value -> DiscoverySuiteWidgetType.TagBar.titleResHint
        type == DiscoverySuiteWidgetType.HorizontalBooks.value -> DiscoverySuiteWidgetType.HorizontalBooks.titleResHint
        type == DiscoverySuiteWidgetType.RankedList.value -> DiscoverySuiteWidgetType.RankedList.titleResHint
        type == DiscoverySuiteWidgetType.WaterfallBooks.value -> DiscoverySuiteWidgetType.WaterfallBooks.titleResHint
        else -> DiscoverySuiteWidgetType.RandomBooks.titleResHint
    }
}

private val DiscoverySuiteWidgetType.titleResHint: String
    get() = when (this) {
        DiscoverySuiteWidgetType.TagBar -> "Tags"
        DiscoverySuiteWidgetType.HorizontalBooks -> "Horizontal books"
        DiscoverySuiteWidgetType.RankedList -> "Ranking list"
        DiscoverySuiteWidgetType.WaterfallBooks -> "Waterfall books"
        else -> "Books"
    }

private const val WIDGET_DISPLAY_LIMIT_RANDOM = 36
private const val WIDGET_DISPLAY_LIMIT_HORIZONTAL = 72
private const val WIDGET_DISPLAY_LIMIT_WATERFALL = 24
private const val WIDGET_DISPLAY_LIMIT_RANKED_TOTAL = 72

/** 进程内快照允许保留的书籍数上限（按类型裁剪）。 */
fun DiscoverySuiteWidget.snapshotBookLimit(): Int {
    return when (type) {
        DiscoverySuiteWidgetType.HorizontalBooks.value -> WIDGET_DISPLAY_LIMIT_HORIZONTAL
        DiscoverySuiteWidgetType.WaterfallBooks.value -> WIDGET_DISPLAY_LIMIT_WATERFALL
        DiscoverySuiteWidgetType.RankedList.value -> WIDGET_DISPLAY_LIMIT_RANKED_TOTAL
        else -> WIDGET_DISPLAY_LIMIT_RANDOM
    }
}