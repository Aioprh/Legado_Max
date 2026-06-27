/**
 * 首页 UI 契约定义
 *
 * 文件作用：定义首页模块的 UI 状态模型和管理操作契约。
 * 主要功能：
 * - 定义首页书籍、模块、整体页面的 UI 状态数据类
 * - 定义模块加载状态（加载中、已加载、按钮组、错误）
 * - 定义管理模式的源、模块管理状态
 * - 定义管理操作的回调集合
 */
package io.legado.app.ui.main.homepage

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef

/**
 * 首页书籍项 UI 数据
 *
 * @property book 搜索书籍数据
 * @property shelfState 书架状态，默认不在书架中
 */
@Stable
data class HomepageBookItemUi(
    val book: SearchBook,
    val shelfState: BookShelfState = BookShelfState.NOT_IN_SHELF,
)

/**
 * 首页整体 UI 状态
 *
 * @property modules 模块列表
 * @property isManageMode 是否处于管理模式
 * @property isRefreshing 是否正在刷新
 * @property manageState 管理模式状态
 */
@Stable
data class HomepageUiState(
    val modules: List<HomepageModuleUi> = emptyList(),
    val isManageMode: Boolean = false,
    val isRefreshing: Boolean = false,
    val manageState: HomepageManageUiState = HomepageManageUiState(),
)

/**
 * 首页模块 UI 数据
 *
 * @property sourceUrl 书源 URL
 * @property setName 书源集合名称
 * @property globalId 模块全局唯一标识
 * @property type 模块类型
 * @property title 模块标题
 * @property exploreUrl 探索 URL
 * @property customSetId 自定义集合 ID
 * @property layoutConfig 布局配置
 * @property state 模块加载状态
 * @property config 模块配置键值对
 */
@Stable
data class HomepageModuleUi(
    val sourceUrl: String,
    val setName: String,
    val globalId: String,
    val type: HomepageModuleType,
    val title: String,
    val exploreUrl: String? = null,
    val customSetId: String? = null,
    val layoutConfig: String? = null,
    val state: ModuleLoadState = ModuleLoadState.Loading,
    val config: Map<String, String> = emptyMap()
)

/**
 * 模块加载状态密封接口
 *
 * 用于表示模块的不同加载状态，包含加载中、已加载、按钮组和错误四种状态。
 */
@Stable
sealed interface ModuleLoadState {
    /** 加载中状态 */
    @Stable
    data object Loading : ModuleLoadState

    /**
     * 已加载状态
     *
     * @property books 已加载的书籍列表
     * @property hasMore 是否还有更多数据
     * @property isLoadingMore 是否正在加载更多
     * @property page 当前页码
     */
    @Stable
    data class Loaded(
        val books: List<HomepageBookItemUi>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val page: Int = 1
    ) : ModuleLoadState

    /**
     * 按钮组状态
     *
     * @property kinds 探索分类列表
     */
    @Stable
    data class Buttons(val kinds: List<ExploreKind>) : ModuleLoadState

    /**
     * 排行榜多 Tab 状态（支持 Ranking / GridRanking 多分类）
     *
     * @property tabs 各分类 Tab 的数据
     * @property selectedIndex 当前选中的 Tab 索引
     */
    @Stable
    data class RankingTabs(
        val tabs: List<RankingTabData>,
        val selectedIndex: Int = 0
    ) : ModuleLoadState

    /**
     * 错误状态
     *
     * @property message 错误信息
     */
    @Stable
    data class Error(val message: String) : ModuleLoadState
}

/**
 * 排行榜 Tab 数据 — 多分类排行榜中每个 Tab 的独立状态
 *
 * @property title 分类标题（Tab 标签文字）
 * @property exploreUrl 分类探索 URL（点击箭头跳转目标）
 * @property books 该分类下的书籍列表，null 表示尚未加载
 * @property errorMessage 加载错误，非 null 表示加载失败
 */
@Stable
data class RankingTabData(
    val title: String,
    val exploreUrl: String?,
    val books: List<HomepageBookItemUi>? = null,
    val errorMessage: String? = null,
)

// ==================== 管理模式 UI 状态 ====================

/**
 * 首页管理模式整体状态
 *
 * @property sets 书源集合列表
 * @property browseSources 浏览书源列表
 * @property allJoinedModules 所有已加入的模块列表
 * @property sourceNames 书源 URL 到名称的映射
 */
@Stable
data class HomepageManageUiState(
    val sets: List<HomepageSourceManageUi> = emptyList(),
    val browseSources: List<HomepageSourceManageUi> = emptyList(),
    val allJoinedModules: List<HomepageModuleManageUi> = emptyList(),
    val sourceNames: Map<String, String> = emptyMap(),
)

/**
 * 首页书源管理 UI 数据
 *
 * @property sourceUrl 书源 URL
 * @property sourceName 书源名称
 * @property sourceGroup 书源分组
 * @property isSelected 是否被选中
 * @property moduleCount 模块数量
 * @property isCustomSet 是否为自定义集合
 */
@Stable
data class HomepageSourceManageUi(
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String? = null,
    val isSelected: Boolean = false,
    val moduleCount: Int = 0,
    val isCustomSet: Boolean = false,
    /** 集类型标识：null=自定义集, "book"=书源集, "rss"=订阅源集 */
    val sourceType: String? = null,
)

/**
 * 首页模块管理 UI 数据
 *
 * @property id 模块标识
 * @property sourceUrl 书源 URL
 * @property sourceName 书源名称
 * @property moduleKey 模块键
 * @property title 模块标题
 * @property customSetTitle 自定义集合标题
 * @property customSetId 自定义集合 ID
 * @property isVisible 是否可见
 * @property type 模块类型
 * @property url 模块 URL
 * @property args 模块参数
 * @property layoutConfig 布局配置
 * @property originalTitle 原始标题
 */
@Stable
data class HomepageModuleManageUi(
    val id: String = "",
    val sourceUrl: String = "",
    val sourceName: String = "",
    val moduleKey: String = "",
    val title: String = "",
    val customSetTitle: String? = null,
    val customSetId: String? = null,
    val isVisible: Boolean = true,
    val type: String = "",
    val url: String? = null,
    val args: String? = null,
    val layoutConfig: String? = null,
    val originalTitle: String = "",
    /** 模块来源类型："book"=书源模块, "rss"=订阅源模块 */
    val sourceType: String = "book",
)

/**
 * 管理操作回调集合
 *
 * 集中定义首页管理模式下的所有操作回调，便于统一传递和管理。
 *
 * @property onToggleSet 切换书源集合选中状态
 * @property onGetSourceModules 获取书源模块列表
 * @property onSyncSourceModules 同步书源模块
 * @property onToggleModule 切换模块可见状态
 * @property onJoinModule 加入模块
 * @property onAddCustomModule 添加自定义模块
 * @property onAddButtonGroupFromKinds 从分类创建按钮组模块
 * @property onAddRssButtonGroupFromKinds 从RSS分类创建按钮组模块
 * @property onGetExploreKinds 获取探索分类列表
 * @property onUpdateModule 更新模块
 * @property onDeleteModule 删除模块
 * @property onReorderModules 模块重排序
 * @property onReorderSets 集合重排序
 * @property onSetCustomSetTitle 设置自定义集合标题
 * @property onCreateCustomSet 创建自定义集合
 * @property onRenameCustomSet 重命名自定义集合
 * @property onDeleteCustomSet 删除自定义集合
 * @property onAssignModuleToCustomSet 分配模块到自定义集合
 */
@Stable
data class HomepageManageActions(
    val onToggleSet: (String, Boolean) -> Unit,
    val onGetSourceModules: (String, String?) -> List<HomepageModuleManageUi>,
    val onSyncSourceModules: (String) -> Unit,
    val onToggleModule: (String, Boolean) -> Unit,
    val onJoinModule: (String, String?, ModuleDef) -> Unit,
    val onAddCustomModule: (String, String?, ModuleDef) -> Unit,
    val onAddButtonGroupFromKinds: (String, String?, String, List<String>) -> Unit,
    val onGetExploreKinds: suspend (String) -> List<ExploreKind>,
    val onGetRssKinds: suspend (String) -> List<Pair<String, String>>,
    val onAddRssCustomModule: (String, String?, ModuleDef) -> Unit,
    val onAddRssButtonGroupFromKinds: (String, String?, String, List<String>) -> Unit,
    val onAddRankingGroupFromKinds: (String, String?, String, List<Pair<String, String>>, String) -> Unit,
    val onAddRssRankingGroupFromKinds: (String, String?, String, List<Pair<String, String>>, String) -> Unit,
    val onUpdateModule: (String, ModuleDef) -> Unit,
    val onDeleteModule: (String) -> Unit,
    val onReorderModules: (List<String>) -> Unit,
    val onReorderSets: (List<String>) -> Unit,
    val onSetCustomSetTitle: (String, String?) -> Unit,
    val onCreateCustomSet: (String) -> Unit,
    val onRenameCustomSet: (String, String) -> Unit,
    val onDeleteCustomSet: (String) -> Unit,
    val onAssignModuleToCustomSet: (String, String?) -> Unit,
)
