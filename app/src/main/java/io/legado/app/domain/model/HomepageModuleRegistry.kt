package io.legado.app.domain.model

import androidx.annotation.StringRes
import io.legado.app.R

/**
 * 首页模块注册表。
 *
 * 将模块的业务能力、加载策略和默认布局集中描述，避免 ViewModel、加载器、
 * 管理器和 UI 各自维护一套类型判断。
 */
data class HomepageModuleDescriptor(
    val type: HomepageModuleType,
    val category: HomepageModuleCategory,
    val canLoadMore: Boolean = false,
    val supportsTabs: Boolean = false,
    val standalone: Boolean = false,
    val defaultColumns: Int = 0,
    val defaultRows: Int = 0,
)

object HomepageModuleRegistry {

    private val descriptors: Map<HomepageModuleType, HomepageModuleDescriptor> =
        HomepageModuleType.entries.associateWith { type ->
            when (type) {
                HomepageModuleType.Banner ->
                    HomepageModuleDescriptor(type, HomepageModuleCategory.BookList)

                HomepageModuleType.Card ->
                    HomepageModuleDescriptor(type, HomepageModuleCategory.BookList)

                HomepageModuleType.Grid ->
                    HomepageModuleDescriptor(
                        type = type,
                        category = HomepageModuleCategory.BookList,
                        defaultColumns = 3,
                        defaultRows = 2
                    )

                HomepageModuleType.InfiniteGrid ->
                    HomepageModuleDescriptor(
                        type = type,
                        category = HomepageModuleCategory.BookList,
                        canLoadMore = true,
                        defaultColumns = 3
                    )

                HomepageModuleType.Waterfall ->
                    HomepageModuleDescriptor(
                        type = type,
                        category = HomepageModuleCategory.BookList,
                        canLoadMore = true,
                        defaultColumns = 2
                    )

                HomepageModuleType.Ranking ->
                    HomepageModuleDescriptor(
                        type = type,
                        category = HomepageModuleCategory.RankingTabs,
                        canLoadMore = true,
                        supportsTabs = true
                    )

                HomepageModuleType.GridRanking ->
                    HomepageModuleDescriptor(
                        type = type,
                        category = HomepageModuleCategory.RankingTabs,
                        canLoadMore = true,
                        supportsTabs = true
                    )

                HomepageModuleType.ButtonGroup ->
                    HomepageModuleDescriptor(type, HomepageModuleCategory.ButtonGroup)

                HomepageModuleType.SmartFilter ->
                    HomepageModuleDescriptor(type, HomepageModuleCategory.SmartFilter)

                HomepageModuleType.SearchBar ->
                    HomepageModuleDescriptor(
                        type = type,
                        category = HomepageModuleCategory.Standalone,
                        standalone = true
                    )

                HomepageModuleType.Unknown ->
                    HomepageModuleDescriptor(type, HomepageModuleCategory.BookList)
            }
        }

    fun descriptor(type: HomepageModuleType): HomepageModuleDescriptor =
        descriptors[type] ?: descriptors.getValue(HomepageModuleType.Unknown)

    fun descriptor(key: String?): HomepageModuleDescriptor =
        descriptor(HomepageModuleType.fromKey(key))

    fun category(type: HomepageModuleType): HomepageModuleCategory =
        descriptor(type).category

    fun canLoadMore(type: HomepageModuleType): Boolean =
        descriptor(type).canLoadMore

    fun supportsTabs(type: HomepageModuleType): Boolean =
        descriptor(type).supportsTabs

    fun isStandalone(type: HomepageModuleType): Boolean =
        descriptor(type).standalone

    fun defaultColumns(type: HomepageModuleType): Int =
        descriptor(type).defaultColumns

    fun defaultRows(type: HomepageModuleType): Int =
        descriptor(type).defaultRows
}

/**
 * 保留原有 API，减少已有业务代码改动；新代码统一从 Registry 读取模块能力。
 */
object HomepageModuleSpec {
    fun category(type: HomepageModuleType): HomepageModuleCategory =
        HomepageModuleRegistry.category(type)

    fun canLoadMore(type: HomepageModuleType): Boolean =
        HomepageModuleRegistry.canLoadMore(type)

    fun isRankingTabs(type: HomepageModuleType): Boolean =
        HomepageModuleRegistry.supportsTabs(type)

    fun isBookList(type: HomepageModuleType): Boolean =
        HomepageModuleRegistry.category(type) == HomepageModuleCategory.BookList
}
