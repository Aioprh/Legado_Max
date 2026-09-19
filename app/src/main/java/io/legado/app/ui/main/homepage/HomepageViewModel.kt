package io.legado.app.ui.main.homepage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourceExploreLite
import io.legado.app.data.entities.RssSourceLite
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.HomepageModulesRepository
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.HomepageModuleCategory
import io.legado.app.domain.model.HomepageModuleSpec
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.domain.model.ModuleItem
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.sortUrls
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.ui.main.explore.ExploreAdapter
import com.script.rhino.runScriptWithContext
import io.legado.app.utils.InfoMap
import io.legado.app.model.CacheBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * 首页 ViewModel
 *
 * 负责首页的数据加载、状态管理和业务逻辑，包括：
 * - 模块内容的异步加载与状态管理（加载中/成功/错误/空）
 * - 书源模块的同步与增量更新（基于 MD5 哈希的变更检测）
 * - 自定义集的创建、编辑、删除和排序
 * - 模块的启用/禁用、排序、编辑和删除
 * - 书架状态查询与书籍添加
 * - 发现页分类（ExploreKind）的获取
 *
 * 架构特点：
 * - 使用多层 combine Flow 构建响应式 UI 状态
 * - 通过 _configVersion StateFlow 触发流重算（写入后递增）
 * - 模块 ID 编码格式：setId::sourceUrl::moduleKey
 * - 集 ID 前缀：src_（书源集）、cs_（用户自定义集）
 * - 每个集仅允许一个瀑布流或无限网格模块（无限流互斥约束）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModel(application: Application) : BaseViewModel(application) {

    /**
     * 模块加载参数封装类
     * 用于 combine 操作的参数传递
     */
    private data class ModuleLoadParams(
        val modules: List<HomepageModuleUi>,
        val layout: Int,
        val preload: Int,
        val sets: List<HomepageSourceManageUi>,
        val tabIndex: Int
    )

    companion object {
        private const val CUSTOM_SET_URL_PREFIX = "custom://"
        private const val HOMEPAGE_DEFAULT_GRID_ROWS = 2
        private const val HOMEPAGE_MAX_BUTTON_GROUP_KINDS = 5

        /** 将自定义集 ID 转换为 URL 格式 */
        fun customSetUrl(id: String) = "$CUSTOM_SET_URL_PREFIX$id"
        /** 判断 URL 是否为自定义集 */
        fun isCustomSetUrl(url: String) = url.startsWith(CUSTOM_SET_URL_PREFIX)
        /** 从 URL 中提取自定义集 ID */
        fun customSetIdFromUrl(url: String): String = url.removePrefix(CUSTOM_SET_URL_PREFIX)

        /**
         * 判断模块是否为无限流类型（瀑布流或无限网格）
         * 无限流模块每个集仅允许存在一个
         */
        fun isInfinite(type: String?, layoutConfig: String?): Boolean {
            return type == HomepageModuleType.Waterfall.key
                    || type == HomepageModuleType.InfiniteGrid.key
        }

        /** 从书源的 homepageModules JSON 解析模块定义列表 */
        private fun parseModuleDefs(sourceUrl: String, json: String): List<ModuleDef> =
            GSON.fromJsonArray<ModuleDef>(json).getOrDefault(emptyList())
                .map { it.copy(sourceUrl = sourceUrl) }

        /** 计算 JSON 字符串的 MD5 哈希值，用于增量同步的变更检测 */
        private fun jsonHash(json: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(json.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** 按集分组并保持顺序（自定义集优先，书源集随后） */
        private fun List<ModuleItem>.groupBySourceOrdered(): Map<String, List<ModuleItem>> {
            val result = linkedMapOf<String, MutableList<ModuleItem>>()
            for (module in this) {
                val key = module.customSetId?.let { customSetUrl(it) } ?: module.sourceUrl
                result.getOrPut(key) { mutableListOf() }.add(module)
            }
            return result
        }
    }

    private val gateway: HomepageModulesGateway =
        HomepageModulesRepository(appDb.homepageModuleDao, appDb.homepageCustomSetDao)
    private val saveSearchBooksUseCase = SaveSearchBooksUseCase()
    private val addToBookshelfUseCase = AddToBookshelfUseCase()

    private val _effects = MutableSharedFlow<HomepageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    /**
     * 模块内容加载引擎：承载模块内容加载 / 行走Tab / 分页 / 刷新重载及专用内容状态。
     * 刷新编排（isRefreshing 由本类持有）与展示层 Flow 仍留在 ViewModel 中。
     */
    private val moduleLoader = HomepageModuleLoader(
        scope = viewModelScope,
        gateway = gateway,
        preloadModeProvider = { HomepageConfig.homepagePreload },
        emitEffect = { _effects.tryEmit(it) },
    )


    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshingSetName = MutableStateFlow<String?>(null)
    /** 刷新期间需要加载的模块 ID 集合，确保加载与完成检测使用同一份清单 */
    private val _refreshingModuleIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isManageMode = MutableStateFlow(false)
    private val _configVersion = MutableStateFlow(0L)
    private val _bookSourcesCache = MutableStateFlow<Map<String, BookSourceExploreLite>>(emptyMap())
    private val _rssSourceNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _layoutConfigCache = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    
    /** 当前选中的书源集Tab索引（用于分源Tab模式下的预加载控制） */
    private val _currentTabIndex = MutableStateFlow(0)
    /** 当前选中的书源集列表（用于分源Tab模式下的预加载控制） */
    private val _currentSets = MutableStateFlow<List<HomepageSourceManageUi>>(emptyList())

    private val localModulesFlow = gateway.flowEnabled()
    val allModulesCache = gateway.flowAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customSetsFlow = gateway.flowCustomSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 用于首页布局和管理界面的自定义集列表（同步读取最新排序）。
     *
     * 每次 _configVersion 变化时，直接从数据库重新读取自定义集列表，
     * 确保排序变更后 rawModulesFlow 和 setsFlow 能立即获取到最新的排序顺序。
     * 这解决了 customSetsFlow (Room Flow) 异步发射延迟导致界面不即时更新的问题。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val customSetsSync = _configVersion.mapLatest {
        gateway.flowCustomSets().first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val orderedModuleDefsFlow = combine(localModulesFlow, _configVersion) { modules, _ ->
        modules.groupBySourceOrdered()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val rawModulesFlow = combine(
        orderedModuleDefsFlow,
        moduleLoader.contentStates,
        _bookSourcesCache,
        customSetsSync,
        // 将 _configVersion 纳入 combine，确保 hiddenSetUrls 变化时触发重算
        combine(_layoutConfigCache, _configVersion) { cache, _ -> cache }
    ) { grouped, contentStates, sourcesCache, customSets, configCache ->
        val setNames = customSets.associate { it.id to it.name }
        val sortedSetIds = customSets.sortedBy { it.sortOrder }.map { it.id }
        val hidden = hiddenSetUrls

        sortedSetIds.flatMap { setId ->
            // 计算集 URL（与 setsFlow 中的逻辑保持一致）
            // 书源集（src_）和订阅源集（rss_）都视为源集
            val isSourceSet = setId.startsWith("src_") || setId.startsWith("rss_")
            val setUrl = if (isSourceSet) setId else customSetUrl(setId)
            // 跳过已隐藏的集
            if (setUrl in hidden) return@flatMap emptyList()
            val mods = grouped[customSetUrl(setId)] ?: emptyList()
            mods.map { module ->
                val source = sourcesCache[module.sourceUrl]
                val sourceName = source?.bookSourceName ?: module.sourceUrl
                val setName = module.customSetId?.let { setNames[it] } ?: sourceName
                val exploreUrl = module.url ?: source?.exploreUrl
                val configMap = configCache[module.id] ?: emptyMap()

                HomepageModuleUi(
                    sourceUrl = module.sourceUrl,
                    setName = setName,
                    globalId = module.id,
                    type = HomepageModuleType.fromKey(module.type),
                    title = module.displayTitle,
                    exploreUrl = exploreUrl,
                    customSetId = module.customSetId,
                    layoutConfig = module.layoutConfig,
                    state = contentStates[module.id] ?: ModuleLoadState.Loading,
                    config = configMap
                )
            }
        }
    }

    private val displayModulesFlow = combine(
        rawModulesFlow,
        BookshelfMatcher.version
    ) { modules, _ ->
        modules.map { module ->
            updateModuleShelfState(module) { item ->
                BookshelfMatcher.getState(item.book.name, item.book.author, item.book.bookUrl)
            }
        }
    }

    /**
     * 更新模块中书籍的书架状态
     *
     * 统一处理 Loaded 和 RankingTabs 两种状态，确保书架状态变化时所有书籍都能正确更新。
     *
     * @param module 首页模块 UI 数据
     * @param resolveState 根据书籍信息计算新的书架状态
     */
    private fun updateModuleShelfState(
        module: HomepageModuleUi,
        resolveState: (HomepageBookItemUi) -> BookShelfState
    ): HomepageModuleUi {
        val state = module.state
        return when (state) {
            is ModuleLoadState.Loaded -> {
                module.copy(state = state.copy(
                    books = state.books.map { item ->
                        val newShelfState = resolveState(item)
                        if (item.shelfState == newShelfState) item
                        else item.copy(shelfState = newShelfState)
                    }
                ))
            }
            is ModuleLoadState.RankingTabs -> {
                module.copy(state = state.copy(
                    tabs = state.tabs.map { tab ->
                        val books = tab.books ?: return@map tab
                        tab.copy(books = books.map { item ->
                            val newShelfState = resolveState(item)
                            if (item.shelfState == newShelfState) item
                            else item.copy(shelfState = newShelfState)
                        })
                    }
                ))
            }
            else -> module
        }
    }

    // ==================== Management Flows ====================

    private val hiddenSetUrls: Set<String>
        get() {
            val json = HomepageConfig.homepageSourceHidden
            if (json.isBlank()) return emptySet()
            return GSON.fromJsonArray<String>(json).getOrDefault(emptySet()).toSet()
        }

    private fun saveHiddenSetUrls(urls: Set<String>) {
        HomepageConfig.homepageSourceHidden = GSON.toJson(urls)
    }

    /**
     * 集列表管理界面的数据流。
     *
     * 使用 customSetsSync（同步读取）替代 customSetsFlow（异步 Room Flow），
     * 确保拖动排序后集列表能即时反映最新顺序，解决异步发射延迟问题。
     * 
     * 将 _configVersion 纳入 combine，确保 hiddenSetUrls 变化时触发重算，
     * 解决开关切换后 isSelected 状态不更新的问题。
     */
    val setsFlow = combine(customSetsSync, allModulesCache, _configVersion) { sets, modules, _ ->
        val hidden = hiddenSetUrls
        sets.map { cs ->
            // 书源集（src_ 前缀）和订阅源集（rss_ 前缀）使用原始 ID 作为 URL
            // 自定义集使用 custom:// 前缀
            val isSourceSet = cs.id.startsWith("src_") || cs.id.startsWith("rss_")
            val setUrl = if (isSourceSet) cs.id else customSetUrl(cs.id)
            val count = modules.count { it.customSetId == cs.id }
            val sourceType = when {
                cs.id.startsWith("src_") -> "book"
                cs.id.startsWith("rss_") -> "rss"
                else -> null
            }
            HomepageSourceManageUi(
                sourceUrl = setUrl,
                sourceName = cs.name,
                isSelected = setUrl !in hidden,
                moduleCount = count,
                isCustomSet = !isSourceSet,
                sourceType = sourceType,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browseSourcesFlow = combine(
        _bookSourcesCache,
        allModulesCache,
        _configVersion
    ) { sources, modules, _ ->
        // 保持 flowExploreSources() 查询的 customOrder 排序，不重新排序
        sources.values.map { source ->
            val count = modules.count { it.sourceUrl == source.bookSourceUrl }
            HomepageSourceManageUi(
                sourceUrl = source.bookSourceUrl,
                sourceName = source.bookSourceName,
                sourceGroup = source.bookSourceGroup,
                moduleCount = count,
                isCustomSet = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 首页布局模式（0: 混合列表, 1: 分源Tab），响应式跟随配置变化 */
    val layoutMode: StateFlow<Int> = _configVersion
        .map { HomepageConfig.homepageLayoutMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageConfig.homepageLayoutMode)

    /** 首页预加载模式（0: 仅当前集, 1: 当前集+相邻集），响应式跟随配置变化 */
    val preloadMode: StateFlow<Int> = _configVersion
        .map { HomepageConfig.homepagePreload }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageConfig.homepagePreload)

    val manageStateFlow = combine(
        setsFlow,
        browseSourcesFlow,
        allModulesCache,
        _bookSourcesCache,
        _rssSourceNames
    ) { sets, browseSources, modules, sources, rssNames ->
        val sourceNames = sources.values.associate { it.bookSourceUrl to it.bookSourceName } + rssNames
        val allJoined = modules.map { mod ->
            HomepageModuleManageUi(
                id = mod.id,
                sourceUrl = mod.sourceUrl,
                sourceName = sourceNames[mod.sourceUrl] ?: mod.sourceUrl,
                moduleKey = mod.moduleKey,
                title = mod.displayTitle,
                customSetTitle = mod.customSetTitle,
                customSetId = mod.customSetId,
                isVisible = mod.isEnabled,
                type = mod.type,
                url = mod.url,
                args = mod.args,
                layoutConfig = mod.layoutConfig,
                originalTitle = mod.title,
                sourceType = if (rssNames.containsKey(mod.sourceUrl)) "rss" else "book",
            )
        }
        HomepageManageUiState(
            sets = sets,
            browseSources = browseSources,
            allJoinedModules = allJoined,
            sourceNames = sourceNames,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageManageUiState())

    val uiState: StateFlow<HomepageUiState> = combine(
        displayModulesFlow,
        _isRefreshing,
        _isManageMode,
        manageStateFlow
    ) { modules, isRefreshing, isManageMode, manageState ->
        HomepageUiState(
            modules = modules,
            isRefreshing = isRefreshing,
            isManageMode = isManageMode,
            manageState = manageState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageUiState())

    init {
        // Parse and cache module layoutConfig
        viewModelScope.launch {
            localModulesFlow.collect { modules ->
                val cache = mutableMapOf<String, Map<String, String>>()
                for (module in modules) {
                    val configStr = module.layoutConfig ?: continue
                    try {
                        val json = GSON.fromJson(configStr, Map::class.java)
                        if (json != null) {
                            val map = mutableMapOf<String, String>()
                            json.forEach { (k, v) -> map["layout_$k"] = v.toString() }
                            cache[module.id] = map
                        }
                    } catch (_: Exception) {
                    }
                }
                _layoutConfigCache.value = cache
            }
        }

        // 跟踪所有启用了发现功能的书源（用于浏览书源模块列表）
        // 注意：此处仅填充缓存，不自动同步模块。模块仅在用户主动添加后才出现。
        // 使用 BookSourceExploreLite DTO 替代全量 BookSource，避免大量书源时 OOM
        viewModelScope.launch {
            appDb.bookSourceDao.flowExploreSourcesLite().collect { sources ->
                _bookSourcesCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        // 跟踪所有订阅源名称（用于管理界面显示订阅源名称而非 URL）
        // 使用 RssSourceLite DTO 替代全量 RssSource，仅加载必要字段
        viewModelScope.launch {
            appDb.rssSourceDao.flowAllLite().collect { sources ->
                _rssSourceNames.value = sources.associate { it.sourceUrl to it.sourceName }
            }
        }

        // Auto-load modules when they appear in Loading state
        // 根据预加载设置过滤要加载的模块
        // 将 _currentSets 和 _currentTabIndex 纳入 combine，确保Tab切换时触发重新加载
        viewModelScope.launch {
            combine(
                uiState.map { it.modules },
                layoutMode,
                preloadMode,
                _currentSets,
                _currentTabIndex
            ) { modules, layout, preload, sets, tabIndex ->
                ModuleLoadParams(modules, layout, preload, sets, tabIndex)
            }.collect { params ->
                // 计算应该加载的模块ID集合
                val shouldLoadIds = computeShouldLoadModuleIds(
                    params.modules, 
                    params.layout, 
                    params.preload
                )
                
                params.modules.forEach { ui ->
                    if (ui.state is ModuleLoadState.Loading && !moduleLoader.isJobActive(ui.globalId)) {
                        // 刷新期间只加载目标集的模块，正常浏览时由预加载机制控制
                        val shouldLoad = if (_isRefreshing.value) {
                            ui.globalId in _refreshingModuleIds.value
                        } else {
                            ui.globalId in shouldLoadIds
                        }
                        if (shouldLoad) {
                            val module = gateway.getById(ui.globalId)
                            if (module != null) moduleLoader.loadModule(module)
                        }
                    }
                }
            }
        }

        // 监听模块状态变化，更新刷新状态
        viewModelScope.launch {
            moduleLoader.contentStates.collect { states ->
                // 如果正在刷新，检查是否目标模块都加载完成
                if (_isRefreshing.value) {
                    val targetIds = _refreshingModuleIds.value
                    val allLoaded = if (targetIds.isNotEmpty()) {
                        // 检查刷新目标模块是否都加载完成（统一使用 _refreshingModuleIds）
                        targetIds.all { id ->
                            val state = states[id]
                            state != null && state !is ModuleLoadState.Loading
                        }
                    } else {
                        // 未指定目标时检查全部
                        states.values.none { it is ModuleLoadState.Loading } && states.isNotEmpty()
                    }
                    if (allLoaded) {
                        // 最小刷新动画时长，防止 PullToRefreshBox 动画被提前中断
                        kotlinx.coroutines.delay(400)
                        _isRefreshing.value = false
                        _refreshingSetName.value = null
                        _refreshingModuleIds.value = emptySet()
                    }
                }
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        moduleLoader.cancelAllJobs()
    }

    private suspend fun syncModulesFromSource(source: BookSource) {
        val json = source.homepageModules ?: return
        ensureSetForSource(source.bookSourceUrl, source.bookSourceName)
        val parsedDefs = parseModuleDefs(source.bookSourceUrl, json)
        val newHash = jsonHash(json)

        val existingModules = gateway.flowBySource(source.bookSourceUrl).first()
        val existingById = existingModules.associateBy { it.id }
        val parsedIds = parsedDefs.map { it.globalId }.toSet()

        val toUpsert = mutableListOf<ModuleItem>()
        for (i in parsedDefs.indices) {
            val def = parsedDefs[i]
            val existing = existingById[def.globalId]
            if (existing != null) {
                if (existing.isUserCreated) continue
                if (existing.sourceJsonHash == newHash) continue
                toUpsert.add(
                    existing.copy(
                        type = def.type, title = def.title, args = def.args, url = def.url,
                        layoutConfig = def.layoutConfig,
                        sourceJsonHash = newHash, syncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                toUpsert.add(
                    ModuleItem(
                        id = def.globalId,
                        sourceUrl = source.bookSourceUrl,
                        moduleKey = def.key,
                        type = def.type,
                        title = def.title,
                        args = def.args,
                        url = def.url,
                        layoutConfig = def.layoutConfig,
                        isEnabled = true,
                        customSetId = "src_${source.bookSourceUrl}",
                        sortOrder = i,
                        sourceJsonHash = newHash,
                        syncedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        if (toUpsert.isNotEmpty()) gateway.upsertAll(toUpsert)
        if (parsedIds.isNotEmpty()) gateway.deleteStale(source.bookSourceUrl, parsedIds.toList())
    }

    // ==================== 模块内容加载 ====================
    // 模块内容加载 / 行走Tab / 分页 / 刷新重载逻辑及其专用状态已拆分至 HomepageModuleLoader，
    // 此处仅保留对外暴露的转发入口与刷新编排。

    fun loadMoreModule(globalId: String) = moduleLoader.loadMoreModule(globalId)

    fun onKindUrlClick(sourceUrl: String, url: String, title: String) =
        _effects.tryEmit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, url))

    fun selectRankingTab(globalId: String, index: Int) =
        moduleLoader.selectRankingTab(globalId, index)

    fun loadMoreRankingTab(globalId: String, tabIndex: Int) =
        moduleLoader.loadMoreRankingTab(globalId, tabIndex)

    fun retryModule(globalId: String) = moduleLoader.retryModule(globalId)

    /**
     * 刷新首页模块内容（重新加载已存在的模块数据，不自动从书源同步新模块）
     * @param setName 可选的书源集名称，如果指定则只刷新该集的模块
     */
    fun onRefresh(setName: String? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshingSetName.value = setName
            moduleLoader.cancelAllJobs()
            // 仅重新加载已有模块的内容，不从书源自动同步
            if (setName != null) {
                // 只刷新指定书源集的模块
                val setModules = uiState.value.modules.filter { it.setName == setName }
                val setModuleIds = setModules.map { it.globalId }.toSet()
                _refreshingModuleIds.value = setModuleIds
                moduleLoader.clearContent(setModuleIds)
            } else {
                // 刷新所有模块
                _refreshingModuleIds.value = uiState.value.modules.map { it.globalId }.toSet()
                moduleLoader.clearAllContent()
            }
            // isRefreshing 由 auto-load collector 在所有模块加载完成后自动置为 false
        }
    }

    /**
     * 确保书源对应的集存在（不存在则自动创建）
     * 集 ID 格式：src_<书源URL>，集名称为书源名称
     * @return 集 ID
     */
    private suspend fun ensureSetForSource(sourceUrl: String, sourceName: String): String {
        val setId = "src_$sourceUrl"
        if (gateway.getCustomSetById(setId) == null) gateway.upsertCustomSet(
            CustomSetItem(id = setId, name = sourceName)
        )
        return setId
    }

    fun getCurrentBookShelfState(book: SearchBook): BookShelfState {
        return BookshelfMatcher.getState(
            name = book.name,
            author = book.author,
            bookUrl = book.bookUrl
        )
    }

    fun onAddToShelf(book: SearchBook) {
        execute {
            addToBookshelfUseCase.execute(book)
        }
    }

    fun onBookClick(book: SearchBook) {
        viewModelScope.launch {
            // RSS 订阅源文章不保存搜索历史（SearchBook 有 BookSource 外键约束）
            if (!appDb.rssSourceDao.has(book.origin)) {
                saveSearchBooksUseCase.save(book)
            }
            _effects.emit(
                HomepageEffect.NavigateToBookInfo(
                    book.name,
                    book.author,
                    book.bookUrl,
                    book.origin,
                    book.coverUrl
                )
            )
        }
    }

    fun onModuleHeaderClick(sourceUrl: String, exploreUrl: String?, title: String?) {
        viewModelScope.launch {
            _effects.emit(
                HomepageEffect.NavigateToExploreShow(title, sourceUrl, exploreUrl)
            )
        }
    }

    /**
     * 计算应该加载的模块ID集合
     *
     * 根据布局模式和预加载设置，确定哪些模块应该被加载：
     * - 混合列表模式（layoutMode == 0）：加载所有模块
     * - 分源Tab模式（layoutMode == 1）：
     *   - 预加载关闭（preloadMode == 0）：只加载当前书源集的模块
     *   - 预加载开启（preloadMode == 1）：加载当前书源集 + 相邻书源集的模块
     *
     * @param modules 所有模块列表
     * @param layoutMode 布局模式
     * @param preloadMode 预加载模式
     * @return 应该加载的模块ID集合
     */
    private fun computeShouldLoadModuleIds(
        modules: List<HomepageModuleUi>,
        layoutMode: Int,
        preloadMode: Int
    ): Set<String> {
        // 混合列表模式：加载所有模块
        if (layoutMode == 0) {
            return modules.map { it.globalId }.toSet()
        }
        
        // 分源Tab模式：根据预加载设置过滤
        val currentSets = _currentSets.value
        val currentTabIndex = _currentTabIndex.value
        
        if (currentSets.isEmpty()) {
            // 没有集信息时，不加载任何模块，等待 SourceTabLayout 更新后再加载
            return emptySet()
        }
        
        // 计算要加载的集索引范围
        val indicesToLoad = if (preloadMode == 1) {
            // 预加载开启：当前集 + 相邻集（前后各一个）
            val start = (currentTabIndex - 1).coerceAtLeast(0)
            val end = (currentTabIndex + 1).coerceAtMost(currentSets.lastIndex)
            (start..end).toList()
        } else {
            // 预加载关闭：仅当前集
            listOf(currentTabIndex.coerceIn(0, currentSets.lastIndex))
        }
        
        // 获取要加载的集的 sourceUrl
        val setUrlsToLoad = indicesToLoad.mapNotNull { index ->
            currentSets.getOrNull(index)?.sourceUrl
        }
        
        // 过滤出属于这些集的模块
        return modules.filter { module ->
            // 检查模块是否属于要加载的集
            // 自定义集：module.customSetId 对应 setUrl（去掉 custom:// 前缀）
            // 书源集：module.customSetId == setUrl
            setUrlsToLoad.any { setUrl ->
                if (setUrl.startsWith("custom://")) {
                    val setId = customSetIdFromUrl(setUrl)
                    module.customSetId == setId
                } else {
                    // 书源集 URL 格式为 src_<书源URL>
                    module.customSetId == setUrl
                }
            }
        }.map { it.globalId }.toSet()
    }

    // ==================== Management Methods ====================

    fun toggleManageMode() {
        _isManageMode.value = !_isManageMode.value
    }

    /** 设置首页布局模式（0: 混合列表, 1: 分源Tab） */
    fun setLayoutMode(mode: Int) {
        HomepageConfig.homepageLayoutMode = mode
        notifyConfigChanged()
    }

    /** 设置首页预加载模式（0: 仅当前集, 1: 当前集+相邻集） */
    fun setPreloadMode(mode: Int) {
        HomepageConfig.homepagePreload = mode
        notifyConfigChanged()
    }

    /**
     * 更新当前选中的书源集Tab索引和集列表（用于分源Tab模式下的预加载控制）
     *
     * @param tabIndex 当前选中的Tab索引
     * @param sets 当前显示的书源集列表（已选中且有模块的集）
     */
    fun updateCurrentTab(tabIndex: Int, sets: List<HomepageSourceManageUi>) {
        _currentTabIndex.value = tabIndex
        _currentSets.value = sets
    }

    private fun notifyConfigChanged() {
        _configVersion.update { it + 1 }
    }

    fun toggleSet(setUrl: String, visible: Boolean) {
        val hidden = hiddenSetUrls.toMutableSet()
        if (visible) hidden.remove(setUrl) else hidden.add(setUrl)
        saveHiddenSetUrls(hidden)
        notifyConfigChanged()
    }

    fun getSourceModules(sourceUrl: String, setId: String?): List<HomepageModuleManageUi> {
        val source = _bookSourcesCache.value[sourceUrl] ?: return emptyList()
        val json = source.homepageModules ?: return emptyList()
        val defs = parseModuleDefs(sourceUrl, json)
        val existing = allModulesCache.value.filter { it.sourceUrl == sourceUrl }
        val targetSetId = setId ?: "src_$sourceUrl"
        val sourceName = _bookSourcesCache.value[sourceUrl]?.bookSourceName ?: sourceUrl
        return defs.map { def ->
            val globalId = ModuleDef.globalIdOf(sourceUrl, def.key, targetSetId)
            val existingMod = existing.find { it.id == globalId }
            HomepageModuleManageUi(
                id = globalId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                moduleKey = def.key,
                title = def.title,
                customSetId = existingMod?.customSetId,
                isVisible = existingMod?.isEnabled ?: false,
                type = def.type,
                url = def.url,
                args = def.args,
                layoutConfig = def.layoutConfig,
                originalTitle = def.title,
            )
        }
    }

    /**
     * 智能分析书源并生成首页模块方案。
     *
     * 优先使用书源已经声明的 homepageModules；没有声明时，
     * 自动执行发现分类解析，并根据分类名称推断 Grid / Ranking / ButtonGroup。
     * 不直接覆盖已有用户模块，调用方可将返回结果逐项加入目标集。
     */
    suspend fun smartConfigureSource(sourceUrl: String, setId: String?): List<ModuleDef> {
        val source = _bookSourcesCache.value[sourceUrl] ?: return emptyList()
        val targetSetId = setId ?: "src_$sourceUrl"
        val existingIds = allModulesCache.value
            .filter { it.customSetId == targetSetId && it.sourceUrl == sourceUrl }
            .map { it.moduleKey }
            .toSet()

        val declared = source.homepageModules?.takeIf { it.isNotBlank() }?.let {
            runCatching { parseModuleDefs(sourceUrl, it) }.getOrDefault(emptyList())
        }.orEmpty()
        if (declared.isNotEmpty()) return declared.filter { it.key !in existingIds }

        val kinds = runCatching {
            withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(sourceUrl)?.exploreKinds() ?: emptyList()
            }
        }.getOrDefault(emptyList())
        if (kinds.isEmpty()) return emptyList()

        val interactive = kinds.filter {
            it.type == ExploreKind.Type.select ||
                it.type == ExploreKind.Type.toggle ||
                it.type == ExploreKind.Type.text ||
                (it.type == ExploreKind.Type.button && !it.action.isNullOrBlank())
        }
        val result = mutableListOf<ModuleDef>()
        if (interactive.isNotEmpty()) {
            result += ModuleDef(
                key = "smart_filter",
                type = HomepageModuleType.SmartFilter.key,
                title = "智能筛选",
                args = GSON.toJson(interactive.map { kind ->
                    mapOf(
                        "title" to kind.title,
                        "type" to kind.type,
                        "action" to (kind.action ?: ""),
                        "default" to (kind.default ?: ""),
                        "chars" to (kind.chars ?: emptyArray<String?>()).filterNotNull(),
                        "url" to (kind.url ?: ""),
                        "viewName" to (kind.viewName ?: "")
                    )
                }),
                sourceUrl = sourceUrl
            )
        }

        val rankingKinds = kinds.filter {
            it.type == ExploreKind.Type.url &&
                (it.title.contains("榜") || it.title.contains("排行") || it.title.contains("排行榜"))
        }
        if (rankingKinds.size >= 2) {
            result += ModuleDef(
                key = "smart_ranking",
                type = HomepageModuleType.Ranking.key,
                title = "热门榜单",
                args = GSON.toJson(rankingKinds.map { mapOf("t" to it.title, "u" to (it.url ?: "")) }),
                url = rankingKinds.firstOrNull()?.url ?: "",
                sourceUrl = sourceUrl
            )
        }

        val genreNames = setOf(
            "玄幻", "奇幻", "武侠", "仙侠", "都市", "现实", "历史", "军事",
            "游戏", "体育", "科幻", "悬疑", "灵异", "二次元", "短篇", "古代言情",
            "现代言情", "青春", "幻想", "职场", "轻小说"
        )
        val genreKinds = kinds.filter { it.type == ExploreKind.Type.url && it.title.trim() in genreNames }
        if (genreKinds.size >= 3) {
            result += ModuleDef(
                key = "smart_genres",
                type = HomepageModuleType.ButtonGroup.key,
                title = "热门分类",
                args = GSON.toJson(genreKinds.map { mapOf("t" to it.title, "u" to (it.url ?: "")) }),
                sourceUrl = sourceUrl
            )
        }

        val used = interactive.toSet() + rankingKinds.toSet() + genreKinds.toSet()
        kinds.filter { it !in used && it.type == ExploreKind.Type.url }
            .forEachIndexed { index, kind ->
                val safeKey = kind.title.trim().ifBlank { "分类" + index }
                    .replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
                result += ModuleDef(
                    key = "smart_${index}_$safeKey",
                    type = HomepageModuleType.Grid.key,
                    title = kind.title,
                    url = kind.url ?: "",
                    sourceUrl = sourceUrl
                )
            }
        return result.filter { it.key !in existingIds }
    }

    fun onSmartFilterChanged(globalId: String, kind: ExploreKind, value: String) {
        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            val source = withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(module.sourceUrl)
            } ?: return@launch
            val infoMap = ExploreAdapter.exploreInfoMapList[module.sourceUrl]
                ?: InfoMap(module.sourceUrl).also {
                    ExploreAdapter.exploreInfoMapList.put(module.sourceUrl, it)
                }
            infoMap[kind.title] = value

            runCatching {
                val action = kind.action?.takeIf { it.isNotBlank() } ?: return@runCatching
                withContext(Dispatchers.IO) {
                    runScriptWithContext {
                        source.evalJS(action) {
                            put("infoMap", infoMap)
                        }
                    }
                }
            }.onFailure { error ->
                _effects.tryEmit(HomepageEffect.ShowSnackbar("筛选动作执行失败: " + error.message))
            }

            runCatching { source.clearExploreKindsCache() }
            moduleLoader.loadModule(module)
            notifyConfigChanged()
        }
    }

    fun syncSourceModules(sourceUrl: String) {
        viewModelScope.launch {
            val source = _bookSourcesCache.value[sourceUrl] ?: return@launch
            val fullSource = appDb.bookSourceDao.getBookSource(source.bookSourceUrl) ?: return@launch
            syncModulesFromSource(fullSource)
            notifyConfigChanged()
        }
    }

    fun toggleModule(moduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            gateway.setEnabled(moduleId, enabled)
            notifyConfigChanged()
        }
    }

    fun joinModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
            // 确保书源集存在（自动创建以书源命名的集）
            val effectiveSetId = setId ?: run {
                val source = _bookSourcesCache.value[sourceUrl]
                ensureSetForSource(sourceUrl, source?.bookSourceName ?: sourceUrl)
            }
            val globalId = ModuleDef.globalIdOf(sourceUrl, def.key, effectiveSetId)
            val existing = gateway.getById(globalId)
            if (existing != null) {
                gateway.setEnabled(globalId, true)
                gateway.setCustomSetId(globalId, effectiveSetId)
            } else {
                gateway.upsertAll(listOf(
                    ModuleItem(
                        id = globalId,
                        sourceUrl = sourceUrl,
                        moduleKey = def.key,
                        type = def.type,
                        title = def.title,
                        args = def.args,
                        layoutConfig = def.layoutConfig,
                        url = def.url,
                        isEnabled = true,
                        customSetId = effectiveSetId,
                        isUserCreated = true,
                        sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                        syncedAt = System.currentTimeMillis()
                    )
                ))
            }
            notifyConfigChanged()
        }
    }

    fun addCustomModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
            // 确保书源集存在（自动创建以书源命名的集）
            val effectiveSetId = setId ?: run {
                val source = _bookSourcesCache.value[sourceUrl]
                ensureSetForSource(sourceUrl, source?.bookSourceName ?: sourceUrl)
            }
            val key = def.key.ifBlank { "custom_${System.currentTimeMillis()}" }
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId)
            gateway.upsertAll(listOf(
                ModuleItem(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = def.type,
                    title = def.title,
                    args = def.args,
                    layoutConfig = def.layoutConfig,
                    url = def.url,
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    isUserCreated = true,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    fun addButtonGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        kindTitles: List<String>
    ) {
        viewModelScope.launch {
            // 确保书源集存在（自动创建以书源命名的集）
            val effectiveSetId = setId ?: run {
                val source = _bookSourcesCache.value[sourceUrl]
                ensureSetForSource(sourceUrl, source?.bookSourceName ?: sourceUrl)
            }
            val key = "bg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId)
            gateway.upsertAll(listOf(
                ModuleItem(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = HomepageModuleType.ButtonGroup.key,
                    title = title,
                    args = GSON.toJson(kindTitles.map { mapOf("t" to it, "u" to "") }),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    isUserCreated = true,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    /**
     * 获取书源的发现分类列表（支持 JS 动态生成的分类）
     *
     * 使用 suspend 版本的 exploreKinds()，能够执行 @js: 或 <js> 脚本
     * 动态生成分类列表，并缓存结果。
     *
     * @param sourceUrl 书源 URL
     * @return 发现分类列表，每项为 (分类标题, 分类URL) 对
     */
    suspend fun getExploreKinds(sourceUrl: String): List<ExploreKind> {
        val source = _bookSourcesCache.value[sourceUrl] ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(sourceUrl)?.exploreKinds() ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 获取订阅源的分类列表（支持 JS 动态生成的分类）
     *
     * 使用 RssSource.sortUrls() 解析 sortUrl 字段中的分类定义，
     * 若 sortUrl 为空或无效则返回含空标题的默认条目。
     *
     * @param sourceUrl 订阅源 URL
     * @return 分类列表，每项为 (分类标题, 分类URL) 对
     */
    suspend fun getRssKinds(sourceUrl: String): List<Pair<String, String>> {
        val source = appDb.rssSourceDao.getByKey(sourceUrl) ?: return emptyList()
        return runCatching {
            source.sortUrls()
        }.getOrDefault(listOf(Pair("", sourceUrl)))
    }

    /**
     * 确保订阅源对应的集存在（不存在则自动创建）
     * 集 ID 格式：rss_<订阅源URL>，集名称为订阅源名称
     * @return 集 ID
     */
    private suspend fun ensureRssSetForSource(sourceUrl: String, sourceName: String): String {
        val setId = "rss_$sourceUrl"
        if (gateway.getCustomSetById(setId) == null) gateway.upsertCustomSet(
            CustomSetItem(id = setId, name = sourceName)
        )
        return setId
    }

    /**
     * 添加订阅源首页模块
     *
     * 与 addCustomModule 完全一致的结构，差异仅为：
     * - 使用 rss_ 前缀创建集 ID 以避免与书源集（src_）冲突
     * - 从 RssSource 表获取源名称
     *
     * @param sourceUrl 订阅源 URL
     * @param setId 目标集 ID，为 null 时自动创建
     * @param def 模块定义
     */
    fun addRssCustomModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
            // 确保订阅源集存在（自动创建以订阅源命名的集）
            val effectiveSetId = setId ?: run {
                val rssSource = appDb.rssSourceDao.getByKey(sourceUrl)
                ensureRssSetForSource(sourceUrl, rssSource?.sourceName?.ifBlank { null } ?: sourceUrl)
            }
            val key = def.key.ifBlank { "rss_${System.currentTimeMillis()}" }
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId)
            gateway.upsertAll(listOf(
                ModuleItem(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = def.type,
                    title = def.title,
                    args = def.args,
                    layoutConfig = def.layoutConfig,
                    url = def.url,
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    isUserCreated = true,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    /**
     * 从分类创建订阅源按钮组模块
     *
     * 与 addButtonGroupFromKinds 类似，但使用订阅源专用的集管理逻辑。
     *
     * @param sourceUrl 订阅源 URL
     * @param setId 目标集 ID，为 null 时自动创建 rss_ 前缀集
     * @param title 按钮组标题
     * @param kindTitles 选中的分类标题列表
     */
    fun addRssButtonGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        kindTitles: List<String>
    ) {
        viewModelScope.launch {
            // 确保订阅源集存在（自动创建以订阅源命名的集）
            val effectiveSetId = setId ?: run {
                val rssSource = appDb.rssSourceDao.getByKey(sourceUrl)
                ensureRssSetForSource(sourceUrl, rssSource?.sourceName?.ifBlank { null } ?: sourceUrl)
            }
            val key = "bg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId)
            gateway.upsertAll(listOf(
                ModuleItem(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = HomepageModuleType.ButtonGroup.key,
                    title = title,
                    args = GSON.toJson(kindTitles.map { mapOf("t" to it, "u" to "") }),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    isUserCreated = true,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    /**
     * 从分类创建书源排行榜组（Ranking / GridRanking）
     *
     * @param sourceUrl 书源 URL
     * @param setId 目标集 ID
     * @param title 排行榜组标题
     * @param categories 选中的分类列表，每项为 (标题, URL)
     * @param rankingType 排行榜类型 ranking 或 gridRanking
     */
    fun addRankingGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        categories: List<Pair<String, String>>,
        rankingType: String = HomepageModuleType.Ranking.key
    ) {
        viewModelScope.launch {
            val effectiveSetId = setId ?: run {
                val source = _bookSourcesCache.value[sourceUrl]
                ensureSetForSource(sourceUrl, source?.bookSourceName ?: sourceUrl)
            }
            val key = "rg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId)
            val args = categories.map { mapOf("t" to it.first, "u" to it.second) }
            gateway.upsertAll(listOf(
                ModuleItem(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = rankingType,
                    title = title,
                    args = GSON.toJson(args),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    isUserCreated = true,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    /**
     * 从分类创建订阅源排行榜组（Ranking / GridRanking）
     */
    fun addRssRankingGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        categories: List<Pair<String, String>>,
        rankingType: String = HomepageModuleType.Ranking.key
    ) {
        viewModelScope.launch {
            val effectiveSetId = setId ?: run {
                val rssSource = appDb.rssSourceDao.getByKey(sourceUrl)
                ensureRssSetForSource(sourceUrl, rssSource?.sourceName?.ifBlank { null } ?: sourceUrl)
            }
            val key = "rg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId)
            val args = categories.map { mapOf("t" to it.first, "u" to it.second) }
            gateway.upsertAll(listOf(
                ModuleItem(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = rankingType,
                    title = title,
                    args = GSON.toJson(args),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    isUserCreated = true,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    fun updateModule(globalId: String, def: ModuleDef) {
        viewModelScope.launch {
            val existing = gateway.getById(globalId) ?: return@launch
            val targetSetId = existing.customSetId ?: "src_${existing.sourceUrl}"
            // Check infinite module constraint
            if (isInfinite(def.type, def.layoutConfig)) {
                val hasOther = allModulesCache.value.any {
                    it.customSetId == targetSetId &&
                            it.id != globalId &&
                            isInfinite(it.type, it.layoutConfig)
                }
                if (hasOther) {
                    _effects.tryEmit(HomepageEffect.ShowSnackbar("每个集只能有一个无限流模块"))
                    return@launch
                }
            }
            gateway.upsertAll(listOf(
                existing.copy(
                    customTitle = def.title.takeIf { it != existing.title },
                    type = def.type,
                    url = def.url,
                    args = def.args,
                    layoutConfig = def.layoutConfig,
                    isUserCreated = true,
                    syncedAt = System.currentTimeMillis()
                )
            ))
            notifyConfigChanged()
        }
    }

    /**
     * 删除模块。
     *
     * - 从源集（src_/rss_）删除：同时删除所有自定义集中的副本
     * - 从自定义集删除：仅删除当前副本，不影响源集及其他自定义集中的模块
     */
    fun deleteModule(globalId: String) {
        viewModelScope.launch {
            val module = allModulesCache.value.find { it.id == globalId }
            if (module != null) {
                val isSourceModule = module.customSetId?.let {
                    it.startsWith("src_") || it.startsWith("rss_")
                } == true
                if (isSourceModule) {
                    // 从源集删除：删除所有相同 (sourceUrl, moduleKey) 的模块（含源集和所有副本）
                    gateway.deleteBySourceAndKey(module.sourceUrl, module.moduleKey)
                    // 清除所有被删模块的 content state
                    val deletedIds = allModulesCache.value
                        .filter { it.sourceUrl == module.sourceUrl && it.moduleKey == module.moduleKey }
                        .map { it.id }
                    moduleLoader.clearContent(deletedIds)
                    deletedIds.forEach { moduleLoader.cancelJob(it) }
                } else {
                    // 从自定义集删除：仅删除当前模块
                    gateway.delete(globalId)
                    moduleLoader.clearContent(globalId)
                    moduleLoader.cancelJob(globalId)
                }
            } else {
                gateway.delete(globalId)
                moduleLoader.clearContent(globalId)
                moduleLoader.cancelJob(globalId)
            }
            notifyConfigChanged()
        }
    }

    fun reorderModules(orderedIds: List<String>) {
        viewModelScope.launch {
            val orders = orderedIds.mapIndexed { index, id -> id to index }.toMap()
            gateway.batchSetSortOrders(orders)
            notifyConfigChanged()
        }
    }

    fun reorderCustomSets(orderedUrls: List<String>) {
        viewModelScope.launch {
            val orders = orderedUrls.mapIndexed { index, url ->
                customSetIdFromUrl(url) to index
            }.toMap()
            gateway.batchSetCustomSetSortOrders(orders)
            // notifyConfigChanged 触发 customSetsForLayout 重新从数据库读取最新排序
            notifyConfigChanged()
        }
    }

    fun setCustomSetTitle(moduleId: String, title: String?) {
        viewModelScope.launch {
            gateway.setCustomSetTitle(moduleId, title)
            notifyConfigChanged()
        }
    }

    fun createCustomSet(name: String) {
        viewModelScope.launch {
            gateway.createCustomSet(name)
            notifyConfigChanged()
        }
    }

    fun renameCustomSet(id: String, name: String) {
        viewModelScope.launch {
            gateway.renameCustomSet(id, name)
            notifyConfigChanged()
        }
    }

    /**
     * 删除自定义集或书源集。
     *
     * - 自定义集：删除集及其包含的模块
     * - 书源集（src_ 前缀）：删除集、书源集中的模块，以及所有自定义集中来自该书源的模块副本
     */
    fun deleteCustomSet(id: String) {
        viewModelScope.launch {
            // 判断是否为书源集（src_ 前缀）或订阅源集（rss_ 前缀）
            val isSourceSet = id.startsWith("src_") || id.startsWith("rss_")
            
            if (isSourceSet) {
                // 源集：提取源URL，删除所有来自该源的模块
                val sourceUrl = id.removePrefix("src_").removePrefix("rss_")
                val moduleIds = allModulesCache.value
                    .filter { it.sourceUrl == sourceUrl }
                    .map { it.id }
                gateway.deleteCustomSet(id)
                // 删除所有来自该书源的模块（包括书源集和自定义集中的副本）
                moduleIds.forEach { mid -> gateway.delete(mid) }
                moduleIds.forEach { mid ->
                    moduleLoader.clearContent(mid)
                    moduleLoader.cancelJob(mid)
                }
            } else {
                // 自定义集：只删除属于该集的模块
                val moduleIds = allModulesCache.value
                    .filter { it.customSetId == id }
                    .map { it.id }
                gateway.deleteCustomSet(id)
                moduleIds.forEach { mid ->
                    moduleLoader.clearContent(mid)
                    moduleLoader.cancelJob(mid)
                }
            }
            notifyConfigChanged()
        }
    }

    /**
     * 将模块分配到自定义集或从自定义集移除
     *
     * - 分配到自定义集（customSetId != null）：在目标集中创建模块副本，不删除原模块
     * - 从自定义集移除（customSetId == null）：删除自定义集中的模块副本，不影响源集模块
     *
     * @param moduleId 模块 ID
     * @param customSetId 目标集 ID，为 null 表示从自定义集移除
     */
    fun assignModuleToCustomSet(moduleId: String, customSetId: String?) {
        viewModelScope.launch {
            val existing = gateway.getById(moduleId) ?: return@launch
            if (customSetId == null) {
                // 从自有集移除：直接删除该模块（它是源集模块的副本）
                val inSourceSet = existing.customSetId?.let { it.startsWith("src_") || it.startsWith("rss_") } == true
                if (inSourceSet) {
                    // 模块在书源集中，仅禁用
                    gateway.setEnabled(moduleId, false)
                } else {
                    // 模块在自定义集中，直接删除（源集中的原始模块不受影响）
                    gateway.delete(moduleId)
                }
            } else {
                // 分配到自定义集：在目标集中创建副本，保留原模块
                val newId = ModuleDef.globalIdOf(existing.sourceUrl, existing.moduleKey, customSetId)
                // 检查目标集中是否已存在该模块
                val targetExisting = gateway.getById(newId)
                if (targetExisting != null) {
                    // 目标集中已存在，仅启用
                    gateway.setEnabled(newId, true)
                } else {
                    // 在目标集中创建新副本，不删除原模块
                    gateway.upsertAll(listOf(
                        existing.copy(
                            id = newId,
                            customSetId = customSetId,
                            isEnabled = true,
                            isUserCreated = true,
                            sortOrder = allModulesCache.value.count { it.customSetId == customSetId },
                            syncedAt = System.currentTimeMillis()
                        )
                    ))
                }
            }
            notifyConfigChanged()
        }
    }

    // ==================== 新增：离线缓存 ====================
    /**
     * 离线缓存书籍：自动加入书架（若未加入），并启动全书缓存
     */
    fun onCacheBook(book: SearchBook) {
        execute {
            // 1. 确保书籍在书架中（若已在则跳过）
            addToBookshelfUseCase.execute(book)

            // 2. 获取 Book 实体（优先使用 bookUrl）
            val bookEntity = appDb.bookDao.getBook(book.bookUrl)
                ?: appDb.bookDao.getBook(book.name, book.author)
            if (bookEntity == null) {
                _effects.tryEmit(HomepageEffect.ShowSnackbar("加入书架后未找到书籍，请稍后重试"))
                return@execute
            }

            // 3. 启动全书缓存（从第0章到最后一章，-1 表示全部）
            CacheBook.start(getApplication(), bookEntity, 0, -1)
            _effects.tryEmit(HomepageEffect.ShowSnackbar("开始离线缓存：${book.name}"))
        }
    }
}