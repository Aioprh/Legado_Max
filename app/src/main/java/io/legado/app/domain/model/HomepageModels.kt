package io.legado.app.domain.model

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.legado.app.R

/** 供 Gateway 和 ViewModel 使用的不可变模块模型 */
@Immutable
@Keep
data class ModuleItem(
    val id: String = "",
    val sourceUrl: String = "",
    val moduleKey: String = "",
    val type: String = "",
    val title: String = "",
    val customTitle: String? = null,
    val customSetTitle: String? = null,
    val args: String? = null,
    val layoutConfig: String? = null,
    val url: String? = null,
    val isEnabled: Boolean = true,
    val customSetId: String? = null,
    val isUserCreated: Boolean = false,
    val sortOrder: Int = 0,
    val sourceJsonHash: String? = null,
    val syncedAt: Long = 0,
) {
    val displayTitle: String get() = customTitle ?: title
}

@Immutable
@Keep
data class CustomSetItem(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
)

/** 模块定义（来自书源 JSON 解析或用户手动添加） */
@Keep
data class ModuleDef(
    val key: String = "",
    val type: String = "",
    val title: String = "",
    val args: String? = null,
    val layoutConfig: String? = null,
    val url: String? = null,
    val sourceUrl: String = "",
) {
    val globalId: String get() = globalIdOf(sourceUrl, key)

    companion object {
        fun globalIdOf(sourceUrl: String, key: String, setId: String? = null): String {
            val targetSetId = setId ?: "src_$sourceUrl"
            return "$targetSetId::$sourceUrl::$key"
        }
    }
}

/** 首页模块类型枚举 — 定义在 Domain 层以便 UseCase 和 ViewModel 共享 */
enum class HomepageModuleType(val key: String, @StringRes val titleRes: Int) {
    Banner("banner", R.string.module_type_banner),
    Ranking("ranking", R.string.module_type_ranking),
    GridRanking("gridRanking", R.string.module_type_grid_ranking),
    Grid("grid", R.string.module_type_grid),
    Card("card", R.string.module_type_card),
    InfiniteGrid("infiniteGrid", R.string.module_type_infinite_grid),
    ButtonGroup("buttonGroup", R.string.module_type_button_group),
    SmartFilter("smartFilter", R.string.module_type_smart_filter),
    Waterfall("waterfall", R.string.module_type_waterfall),
    Unknown("", R.string.unknown_type);

    companion object {
        fun fromKey(key: String?): HomepageModuleType =
            entries.find { it.key == key } ?: Unknown
    }
}

/** 模块分类 — 决定加载与渲染的统一策略，避免各层 when(type) 分派漂移 */
enum class HomepageModuleCategory {
    /** 普通书籍列表（Banner/Card/Grid/InfiniteGrid/Waterfall） */
    BookList,
    /** 排行榜多 Tab（Ranking/GridRanking） */
    RankingTabs,
    /** 分类按钮组（ButtonGroup） */
    ButtonGroup,
    /** 智能筛选（SmartFilter） */
    SmartFilter,
}

/**
 * 首页模块规格注册表 —— 集中登记各模块类型的加载/分页/语意元数据。
 * 新增模块类型时：补一个 [HomepageModuleType] 枚举项 + 一个渲染器，
 * 并在本注册表登记其 [HomepageModuleCategory] 与分页能力即可。
 */
object HomepageModuleSpec {
    fun category(type: HomepageModuleType): HomepageModuleCategory = when (type) {
        HomepageModuleType.SmartFilter -> HomepageModuleCategory.SmartFilter
        HomepageModuleType.ButtonGroup -> HomepageModuleCategory.ButtonGroup
        HomepageModuleType.Ranking,
        HomepageModuleType.GridRanking -> HomepageModuleCategory.RankingTabs
        HomepageModuleType.Banner,
        HomepageModuleType.Card,
        HomepageModuleType.Grid,
        HomepageModuleType.InfiniteGrid,
        HomepageModuleType.Waterfall,
        HomepageModuleType.Unknown -> HomepageModuleCategory.BookList
    }

    /** 该类型是否支持"加载更多"（分页/无限流） */
    fun canLoadMore(type: HomepageModuleType): Boolean = when (type) {
        HomepageModuleType.InfiniteGrid,
        HomepageModuleType.Waterfall,
        HomepageModuleType.Ranking,
        HomepageModuleType.GridRanking -> true
        else -> false
    }

    fun isRankingTabs(type: HomepageModuleType): Boolean =
        category(type) == HomepageModuleCategory.RankingTabs

    fun isBookList(type: HomepageModuleType): Boolean =
        category(type) == HomepageModuleCategory.BookList
}

/**
 * 首页模块布局配置注册表 —— 集中登记每种类型可配置的布局选项及其默认值。
 *
 * 每种模块类型通过 `layoutConfig`（JSON，形如 `{"columns":4,"maxRows":2}`）携带布局参数；
 * ViewModel 解析后以 `layout_<字段>` 前缀存入 [io.legado.app.ui.main.homepage.HomepageModuleUi.config]。
 *
 * 用途：
 * - [HomepageScreen] 渲染时据此回填默认值并读取用户配置
 * - [AddCustomModuleDialog] 据此为对应类型生成可视化配置项
 *
 * 新增可配置项只需：在此登记 key + 默认值，并把对应渲染器改为读取 config 即可。
 */
object HomepageLayoutOptions {
    /** 布局选项描述：key 为 layoutConfig JSON 字段名，default 为默认整数值，labelRes 为界面标签 */
    data class Option(val key: String, val default: Int, val labelRes: Int)

    /** 每种类型支持的布局选项（空列表表示该类型无可视化配置项） */
    fun optionsOf(type: HomepageModuleType): List<Option> = when (type) {
        HomepageModuleType.Grid -> listOf(
            Option("columns", 3, R.string.homepage_config_columns),
            Option("maxRows", 2, R.string.homepage_config_rows)
        )
        HomepageModuleType.InfiniteGrid -> listOf(
            Option("columns", 3, R.string.homepage_config_columns)
        )
        HomepageModuleType.Waterfall -> listOf(
            Option("columns", 2, R.string.homepage_config_columns)
        )
        else -> emptyList()
    }

    /** 获取某类型指定选项的默认值；该类型不支持该选项或未登记时返回 [fallback] */
    fun defaultOf(type: HomepageModuleType, key: String, fallback: Int = 0): Int =
        optionsOf(type).firstOrNull { it.key == key }?.default ?: fallback
}

/**
 * 从模块配置 map 中读取整数布局参数。
 *
 * @param type 模块类型，决定默认值来源
 * @param key 布局字段名（例如 "columns"），对应 config 中 `layout_<key>`
 * @param fallback 当类型不支持该选项或缺失默认值时的兜底值
 * @return 用户配置值；若未配置则返回该类型的默认值；类型不支持时返回 fallback
 */
fun Map<String, String>.layoutInt(
    type: HomepageModuleType,
    key: String,
    fallback: Int = 3,
): Int {
    val configured = this["layout_$key"]?.toIntOrNull()
    if (configured != null) return configured
    return HomepageLayoutOptions.defaultOf(type, key, fallback)
}
