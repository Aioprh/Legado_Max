package io.legado.app.ui.main.homepage

import android.app.Application
import android.text.Html
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.HomepageModulesRepository
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.domain.model.ModuleItem
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.help.book.isNotShelf
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.sortUrls
import io.legado.app.model.rss.Rss
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import java.util.concurrent.ConcurrentHashMap

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
        private fun parseModuleDefs(source: BookSource, json: String): List<ModuleDef> =
            GSON.fromJsonArray<ModuleDef>(json).getOrDefault(emptyList())
                .map { it.copy(sourceUrl = source.bookSourceUrl) }

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
    private val exploreBooksUseCase = ExploreBooksUseCase()
    private val saveSearchBooksUseCase = SaveSearchBooksUseCase()
    private val resolveBookShelfStateUseCase = ResolveBookShelfStateUseCase()
    private val addToBookshelfUseCase = AddToBookshelfUseCase()

    private val _effects = MutableSharedFlow<HomepageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private val loadJobs = ConcurrentHashMap<String, Job>()

    // Bookshelf tracking - use appDb.bookDao.flowAll() like ExploreShowViewModel
    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshingSetName = MutableStateFlow<String?>(null)
    private val _isManageMode = MutableStateFlow(false)
    private val _configVersion = MutableStateFlow(0L)
    private val _moduleContentStates = MutableStateFlow<Map<String, ModuleLoadState>>(emptyMap())
    private val _bookSourcesCache = MutableStateFlow<Map<String, BookSource>>(emptyMap())
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
        _moduleContentStates,
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
        _bookshelf
    ) { modules, bookshelf ->
        if (bookshelf.isEmpty()) {
            modules.map { module ->
                val state = module.state
                if (state is ModuleLoadState.Loaded) {
                    module.copy(state = state.copy(
                        books = state.books.map { item ->
                            if (item.shelfState == BookShelfState.NOT_IN_SHELF) item
                            else item.copy(shelfState = BookShelfState.NOT_IN_SHELF)
                        }
                    ))
                } else module
            }
        } else {
            val exactKeys = HashSet<Triple<String, String, String?>>(bookshelf.size)
            val nameAuthorKeys = HashSet<Pair<String, String>>(bookshelf.size)
            for (key in bookshelf) {
                exactKeys.add(Triple(key.name, key.author, key.url))
                nameAuthorKeys.add(key.name to key.author)
            }
            modules.map { module ->
                val state = module.state
                if (state is ModuleLoadState.Loaded) {
                    module.copy(state = state.copy(
                        books = state.books.map { item ->
                            val bookTriple = Triple(item.book.name, item.book.author, item.book.bookUrl)
                            val newShelfState = when {
                                bookTriple in exactKeys -> BookShelfState.IN_SHELF
                                (item.book.name to item.book.author) in nameAuthorKeys ->
                                    BookShelfState.SAME_NAME_AUTHOR
                                else -> BookShelfState.NOT_IN_SHELF
                            }
                            if (item.shelfState == newShelfState) item
                            else item.copy(shelfState = newShelfState)
                        }
                    ))
                } else module
            }
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
        viewModelScope.launch {
            appDb.bookSourceDao.flowExploreSources().collect { sources ->
                _bookSourcesCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        // 跟踪所有订阅源名称（用于管理界面显示订阅源名称而非 URL）
        viewModelScope.launch {
            appDb.rssSourceDao.flowAll().collect { sources ->
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
                    if (ui.state is ModuleLoadState.Loading && loadJobs[ui.globalId]?.isActive != true) {
                        // 只加载应该加载的模块
                        if (ui.globalId in shouldLoadIds) {
                            val module = gateway.getById(ui.globalId)
                            if (module != null) loadModule(module)
                        }
                    }
                }
            }
        }

        // 监听模块状态变化，更新刷新状态
        viewModelScope.launch {
            _moduleContentStates.collect { states ->
                // 如果正在刷新，检查是否所有模块都加载完成
                if (_isRefreshing.value) {
                    val refreshingSetName = _refreshingSetName.value
                    val allLoaded = if (refreshingSetName != null) {
                        // 只检查指定书源集的模块
                        val setModules = uiState.value.modules.filter { it.setName == refreshingSetName }
                        val setModuleIds = setModules.map { it.globalId }.toSet()
                        // 检查指定书源集的模块是否都加载完成
                        setModuleIds.all { id ->
                            val state = states[id]
                            state != null && state !is ModuleLoadState.Loading
                        }
                    } else {
                        // 检查所有模块
                        states.values.none { it is ModuleLoadState.Loading } && states.isNotEmpty()
                    }
                    if (allLoaded) {
                        _isRefreshing.value = false
                        _refreshingSetName.value = null
                    }
                }
            }
        }

        // Track bookshelf using appDb.bookDao.flowAll() like ExploreShowViewModel
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = mutableSetOf<BookShelfKey>()
                books.filterNot { it.isNotShelf }
                    .forEach {
                        keys.add(BookShelfKey(it.name, it.author, it.bookUrl))
                    }
                keys
            }.collect { keys ->
                _bookshelf.value = keys
            }
        }.onError {
            // ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
    }

    private suspend fun syncModulesFromSource(source: BookSource) {
        val json = source.homepageModules ?: return
        ensureSetForSource(source.bookSourceUrl, source.bookSourceName)
        val parsedDefs = parseModuleDefs(source, json)
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

    private fun loadModule(module: ModuleItem) {
        loadJobs[module.id]?.cancel()
        if (module.type == HomepageModuleType.ButtonGroup.key) {
            loadJobs[module.id] = viewModelScope.launch {
                kotlin.runCatching {
                    val source = appDb.bookSourceDao.getBookSource(module.sourceUrl)
                        ?: throw Exception("Source not found")
                    val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                    val selectedTitles =
                        module.args?.let { GSON.fromJsonArray<String>(it).getOrNull() }
                    if (selectedTitles.isNullOrEmpty()) allKinds.take(HOMEPAGE_MAX_BUTTON_GROUP_KINDS)
                    else selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                }.onSuccess { kinds ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Buttons(kinds)) }
                }.onFailure { e ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }
        loadJobs[module.id] = viewModelScope.launch {
            kotlin.runCatching {
                // 检查是否为订阅源模块
                val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
                if (rssSource != null) {
                    // 订阅源加载：获取文章列表
                    val sortUrl = module.url ?: rssSource.sourceUrl
                    val sortName = module.title.ifBlank { rssSource.sourceName }
                    val (articles, _) = withContext(Dispatchers.IO) {
                        Rss.getArticlesAwait(sortName, sortUrl, rssSource, page = 1)
                    }
                    // 转换为 SearchBook 以复用现有 UI
                    val books = articles.map { article ->
                        // 描述规则：去除 HTML 标签得到纯文本
                        val introText = article.description?.let {
                            Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                        }
                        SearchBook(
                            bookUrl = article.link,
                            origin = rssSource.sourceUrl,
                            originName = rssSource.sourceName,
                            name = article.title,
                            coverUrl = article.image,
                            intro = introText,
                            author = rssSource.sourceName,
                            latestChapterTitle = article.pubDate,
                        )
                    }
                    books to false
                } else {
                    // 书源加载（原有逻辑）
                    val isRanking =
                        module.type == HomepageModuleType.Ranking.key || module.type == HomepageModuleType.GridRanking.key
                    val books = if (isRanking) exploreBooksUseCase.executeForRanking(
                        module.sourceUrl,
                        module.url,
                        module.args
                    )
                    else exploreBooksUseCase.execute(module.sourceUrl, module.url, module.args).books
                    val hasMore = isInfinite(module.type, module.layoutConfig) && books.isNotEmpty()
                    books to hasMore
                }
            }.onSuccess { (books, hasMore) ->
                val shelf = _bookshelf.value
                _moduleContentStates.update {
                    it + (module.id to ModuleLoadState.Loaded(
                        books.map { book ->
                            HomepageBookItemUi(
                                book = book,
                                shelfState = resolveBookShelfStateUseCase.execute(
                                    name = book.name,
                                    author = book.author,
                                    url = book.bookUrl,
                                    shelf = shelf
                                )
                            )
                        },
                        hasMore = hasMore
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
    }

    fun loadMoreModule(globalId: String) {
        val currentState = _moduleContentStates.value[globalId] as? ModuleLoadState.Loaded ?: return
        if (currentState.isLoadingMore || !currentState.hasMore) return
        val nextPage = currentState.page + 1
        _moduleContentStates.update { it + (globalId to currentState.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            kotlin.runCatching {
                val module = gateway.getById(globalId) ?: throw Exception("Module not found")
                exploreBooksUseCase.execute(
                    module.sourceUrl,
                    module.url,
                    module.args,
                    page = nextPage
                )
            }.onSuccess { result ->
                _moduleContentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    val existingUrls = lastState.books.map { it.book.bookUrl }.toSet()
                    val shelf = _bookshelf.value
                    val deduped = result.books.filter { it.bookUrl !in existingUrls }.map { book ->
                        HomepageBookItemUi(
                            book = book,
                            shelfState = resolveBookShelfStateUseCase.execute(
                                name = book.name,
                                author = book.author,
                                url = book.bookUrl,
                                shelf = shelf
                            )
                        )
                    }
                    states + (globalId to ModuleLoadState.Loaded(
                        books = lastState.books + deduped,
                        hasMore = deduped.isNotEmpty(), isLoadingMore = false, page = nextPage
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    states + (globalId to lastState.copy(isLoadingMore = false))
                }
                _effects.tryEmit(HomepageEffect.ShowSnackbar("加载更多失败: ${e.message}"))
            }
        }
    }

    fun refreshButtonGroup(globalId: String) {
        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            loadModule(module)
        }
    }

    fun onKindUrlClick(sourceUrl: String, url: String, title: String) =
        _effects.tryEmit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, url))

    /**
     * 刷新首页模块内容（重新加载已存在的模块数据，不自动从书源同步新模块）
     * @param setName 可选的书源集名称，如果指定则只刷新该集的模块
     */
    fun onRefresh(setName: String? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshingSetName.value = setName
            loadJobs.values.forEach { it.cancel() }
            loadJobs.clear()
            // 仅重新加载已有模块的内容，不从书源自动同步
            if (setName != null) {
                // 只刷新指定书源集的模块
                val setModules = uiState.value.modules.filter { it.setName == setName }
                val setModuleIds = setModules.map { it.globalId }.toSet()
                _moduleContentStates.update { states ->
                    states.filterKeys { it !in setModuleIds }
                }
            } else {
                // 刷新所有模块
                _moduleContentStates.value = emptyMap()
            }
            // isRefreshing 由 auto-load collector 在所有模块加载完成后自动置为 false
        }
    }

    fun retryModule(globalId: String) {
        _moduleContentStates.update { it + (globalId to ModuleLoadState.Loading) }
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
        return resolveBookShelfStateUseCase.execute(
            name = book.name,
            author = book.author,
            url = book.bookUrl,
            shelf = _bookshelf.value
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
        val defs = parseModuleDefs(source, json)
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

    fun syncSourceModules(sourceUrl: String) {
        viewModelScope.launch {
            val source = _bookSourcesCache.value[sourceUrl] ?: return@launch
            syncModulesFromSource(source)
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
                    args = GSON.toJson(kindTitles),
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
    suspend fun getExploreKinds(sourceUrl: String): List<Pair<String, String>> {
        val source = _bookSourcesCache.value[sourceUrl] ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                source.exploreKinds()
            }.map { it.title to (it.url ?: "") }
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

    fun deleteModule(globalId: String) {
        viewModelScope.launch {
            gateway.delete(globalId)
            _moduleContentStates.update { it - globalId }
            loadJobs.remove(globalId)?.cancel()
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
                    _moduleContentStates.update { it - mid }
                    loadJobs.remove(mid)?.cancel()
                }
            } else {
                // 自定义集：只删除属于该集的模块
                val moduleIds = allModulesCache.value
                    .filter { it.customSetId == id }
                    .map { it.id }
                gateway.deleteCustomSet(id)
                moduleIds.forEach { mid ->
                    _moduleContentStates.update { it - mid }
                    loadJobs.remove(mid)?.cancel()
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
}
