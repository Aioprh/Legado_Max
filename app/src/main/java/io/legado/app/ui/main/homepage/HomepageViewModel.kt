package io.legado.app.ui.main.homepage

import android.app.Application
import android.text.Html
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourceExploreLite
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssSourceLite
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModel(application: Application) : BaseViewModel(application) {

    private data class ModuleLoadParams(
        val modules: List<HomepageModuleUi>,
        val layout: Int,
        val preload: Int,
        val sets: List<HomepageSourceManageUi>,
        val tabIndex: Int
    )

    companion object {
        private const val CUSTOM_SET_URL_PREFIX = "custom://"

        fun customSetUrl(id: String) = "$CUSTOM_SET_URL_PREFIX$id"
        fun isCustomSetUrl(url: String) = url.startsWith(CUSTOM_SET_URL_PREFIX)
        fun customSetIdFromUrl(url: String): String = url.removePrefix(CUSTOM_SET_URL_PREFIX)

        fun isInfinite(type: String?, layoutConfig: String?): Boolean {
            return type == HomepageModuleType.Waterfall.key ||
                    type == HomepageModuleType.InfiniteGrid.key
        }

        private fun parseModuleDefs(sourceUrl: String, json: String): List<ModuleDef> =
            GSON.fromJsonArray<ModuleDef>(json).getOrDefault(emptyList())
                .map { it.copy(sourceUrl = sourceUrl) }

        private fun jsonHash(json: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(json.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

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

    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshingSetName = MutableStateFlow<String?>(null)
    private val _refreshingModuleIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isManageMode = MutableStateFlow(false)
    private val _configVersion = MutableStateFlow(0L)
    private val _moduleContentStates = MutableStateFlow<Map<String, ModuleLoadState>>(emptyMap())
    private val _bookSourcesCache = MutableStateFlow<Map<String, BookSourceExploreLite>>(emptyMap())
    private val _rssSourceNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _layoutConfigCache = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    private val _currentTabIndex = MutableStateFlow(0)
    private val _currentSets = MutableStateFlow<List<HomepageSourceManageUi>>(emptyList())

    private val localModulesFlow = gateway.flowEnabled()
    val allModulesCache = gateway.flowAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customSetsFlow = gateway.flowCustomSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        combine(_layoutConfigCache, _configVersion) { cache, _ -> cache }
    ) { grouped, contentStates, sourcesCache, customSets, configCache ->
        val setNames = customSets.associate { it.id to it.name }
        val sortedSetIds = customSets.sortedBy { it.sortOrder }.map { it.id }
        val hidden = hiddenSetUrls

        sortedSetIds.flatMap { setId ->
            val isSourceSet = setId.startsWith("src_") || setId.startsWith("rss_")
            val setUrl = if (isSourceSet) setId else customSetUrl(setId)
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
                updateModuleShelfState(module) { _ -> BookShelfState.NOT_IN_SHELF }
            }
        } else {
            val exactKeys = HashSet<Triple<String, String, String?>>(bookshelf.size)
            val nameAuthorKeys = HashSet<Pair<String, String>>(bookshelf.size)
            for (key in bookshelf) {
                exactKeys.add(Triple(key.name, key.author, key.url))
                nameAuthorKeys.add(key.name to key.author)
            }
            modules.map { module ->
                updateModuleShelfState(module) { item ->
                    val bookTriple = Triple(item.book.name, item.book.author, item.book.bookUrl)
                    when {
                        bookTriple in exactKeys -> BookShelfState.IN_SHELF
                        (item.book.name to item.book.author) in nameAuthorKeys ->
                            BookShelfState.SAME_NAME_AUTHOR
                        else -> BookShelfState.NOT_IN_SHELF
                    }
                }
            }
        }
    }

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

    val setsFlow = combine(customSetsSync, allModulesCache, _configVersion) { sets, modules, _ ->
        val hidden = hiddenSetUrls
        sets.map { cs ->
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

    val layoutMode: StateFlow<Int> = _configVersion
        .map { HomepageConfig.homepageLayoutMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageConfig.homepageLayoutMode)

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

        viewModelScope.launch {
            appDb.bookSourceDao.flowExploreSourcesLite().collect { sources ->
                _bookSourcesCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        viewModelScope.launch {
            appDb.rssSourceDao.flowAllLite().collect { sources ->
                _rssSourceNames.value = sources.associate { it.sourceUrl to it.sourceName }
            }
        }

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
                val shouldLoadIds = computeShouldLoadModuleIds(
                    params.modules,
                    params.layout,
                    params.preload
                )

                params.modules.forEach { ui ->
                    if (ui.state is ModuleLoadState.Loading && loadJobs[ui.globalId]?.isActive != true) {
                        val shouldLoad = if (_isRefreshing.value) {
                            ui.globalId in _refreshingModuleIds.value
                        } else {
                            ui.globalId in shouldLoadIds
                        }
                        if (shouldLoad) {
                            val module = gateway.getById(ui.globalId)
                            if (module != null) loadModule(module)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            _moduleContentStates.collect { states ->
                if (_isRefreshing.value) {
                    val targetIds = _refreshingModuleIds.value
                    val allLoaded = if (targetIds.isNotEmpty()) {
                        targetIds.all { id ->
                            val state = states[id]
                            state != null && state !is ModuleLoadState.Loading
                        }
                    } else {
                        states.values.none { it is ModuleLoadState.Loading } && states.isNotEmpty()
                    }
                    if (allLoaded) {
                        kotlinx.coroutines.delay(400)
                        _isRefreshing.value = false
                        _refreshingSetName.value = null
                        _refreshingModuleIds.value = emptySet()
                    }
                }
            }
        }

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
                    val selectedTitles = parseKindTitlesFromArgs(module.args)
                    if (selectedTitles.isNullOrEmpty()) {
                        emptyList<ExploreKind>()
                    } else {
                        val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
                        if (rssSource != null) {
                            val allKinds = rssSource.sortUrls().map { (title, url) ->
                                ExploreKind(title = title, url = url)
                            }
                            selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                        } else {
                            val source = appDb.bookSourceDao.getBookSource(module.sourceUrl)
                                ?: throw Exception("Source not found")
                            val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                            selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                        }
                    }
                }.onSuccess { kinds ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Buttons(kinds)) }
                }.onFailure { e ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }

        val isRanking = module.type == HomepageModuleType.Ranking.key || module.type == HomepageModuleType.GridRanking.key
        val rankingCategoryPairs = if (isRanking) parseRankingCategories(module.args) else null

        if (rankingCategoryPairs != null && rankingCategoryPairs.size >= 2) {
            val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
            val initialTabs = rankingCategoryPairs.map { (title, url) ->
                RankingTabData(
                    title = title,
                    exploreUrl = url.ifBlank { null },
                    page = 1,
                    hasMore = true,
                    isLoadingMore = false
                )
            }
            _moduleContentStates.update { it + (module.id to ModuleLoadState.RankingTabs(initialTabs)) }
            if (rankingCategoryPairs.isNotEmpty()) {
                val (title, url) = rankingCategoryPairs[0]
                loadRankingTab(module.id, module.sourceUrl, rssSource, 0, title, url, page = 1)
            }
            return
        }

        loadJobs[module.id] = viewModelScope.launch {
            kotlin.runCatching {
                val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
                if (rssSource != null) {
                    val sortUrl = module.url ?: rssSource.sourceUrl
                    val sortName = module.title.ifBlank { rssSource.sourceName }
                    val (articles, _) = withContext(Dispatchers.IO) {
                        Rss.getArticlesAwait(sortName, sortUrl, rssSource, page = 1)
                    }
                    val books = articles.map { article ->
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
                    val effectiveUrl = if (isRanking) {
                        parseRankingCategories(module.args)?.firstOrNull()?.second?.ifBlank { null }
                            ?: module.url
                    } else {
                        module.url
                    }
                    val result = exploreBooksUseCase.execute(
                        sourceUrl = module.sourceUrl,
                        moduleUrl = effectiveUrl,
                        args = module.args,
                        page = 1
                    )
                    result.books to result.hasMore
                }
            }.onSuccess { (books, hasMore) ->
                val shelf = _bookshelf.value
                _moduleContentStates.update {
                    it + (module.id to ModuleLoadState.Loaded(
                        books = books.map { book ->
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
                        hasMore = hasMore,
                        page = 1,
                        isLoadingMore = false
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
                val isRanking = module.type == HomepageModuleType.Ranking.key ||
                        module.type == HomepageModuleType.GridRanking.key
                val effectiveUrl = if (isRanking) {
                    parseRankingCategories(module.args)?.firstOrNull()?.second?.ifBlank { null }
                        ?: module.url
                } else {
                    module.url
                }
                exploreBooksUseCase.execute(
                    sourceUrl = module.sourceUrl,
                    moduleUrl = effectiveUrl,
                    args = module.args,
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
                    val finalHasMore = if (deduped.isEmpty()) false else result.hasMore
                    states + (globalId to ModuleLoadState.Loaded(
                        books = lastState.books + deduped,
                        hasMore = finalHasMore,
                        isLoadingMore = false,
                        page = nextPage
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

    // ==================== 多分类 Tab 加载 ====================
    private fun loadRankingTab(
        moduleId: String,
        sourceUrl: String,
        rssSource: RssSource?,
        index: Int,
        title: String,
        url: String,
        page: Int = 1
    ) {
        val jobKey = "${moduleId}_tab_$index"
        // 取消之前的加载任务
        loadJobs[jobKey]?.cancel()
        loadJobs[jobKey] = viewModelScope.launch {
            kotlin.runCatching {
                val books = if (rssSource != null) {
                    val (articles, _) = withContext(Dispatchers.IO) {
                        Rss.getArticlesAwait(title.ifBlank { rssSource.sourceName }, url, rssSource, page = page)
                    }
                    articles.map { article ->
                        SearchBook(
                            bookUrl = article.link,
                            origin = rssSource.sourceUrl,
                            originName = rssSource.sourceName,
                            name = article.title,
                            coverUrl = article.image,
                            intro = article.description?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() },
                            author = rssSource.sourceName,
                            latestChapterTitle = article.pubDate,
                        )
                    }
                } else {
                    val result = exploreBooksUseCase.execute(
                        sourceUrl = sourceUrl,
                        moduleUrl = url.ifBlank { null },
                        args = null,
                        page = page
                    )
                    result.books
                }
                val shelf = _bookshelf.value
                books.map { book ->
                    HomepageBookItemUi(
                        book = book,
                        shelfState = resolveBookShelfStateUseCase.execute(
                            name = book.name, author = book.author, url = book.bookUrl, shelf = shelf
                        )
                    )
                }
            }.onSuccess { bookItems ->
                _moduleContentStates.update { states ->
                    val current = states[moduleId] as? ModuleLoadState.RankingTabs ?: return@update states
                    val updatedTabs = current.tabs.toMutableList()
                    val oldTab = updatedTabs[index]
                    val existingUrls = oldTab.books?.map { it.book.bookUrl }?.toSet() ?: emptySet()
                    val deduped = bookItems.filter { it.book.bookUrl !in existingUrls }
                    val newBooks = if (oldTab.books != null) oldTab.books + deduped else bookItems
                    // ★★★ 关键修复：根据返回数据是否为空判断 hasMore ★★★
                    val hasMore = if (bookItems.isEmpty()) false else true
                    updatedTabs[index] = oldTab.copy(
                        books = newBooks,
                        page = page,
                        hasMore = hasMore,
                        isLoadingMore = false,
                        errorMessage = null
                    )
                    states + (moduleId to current.copy(tabs = updatedTabs))
                }
            }.onFailure { e ->
                _moduleContentStates.update { states ->
                    val current = states[moduleId] as? ModuleLoadState.RankingTabs ?: return@update states
                    val updatedTabs = current.tabs.toMutableList()
                    updatedTabs[index] = updatedTabs[index].copy(
                        errorMessage = e.stackTraceStr,
                        isLoadingMore = false
                    )
                    states + (moduleId to current.copy(tabs = updatedTabs))
                }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(jobKey) } }
    }

    // ★★★ 修正后的 loadMoreRankingTab（支持重试 + 刷新状态处理）★★★
    fun loadMoreRankingTab(globalId: String, tabIndex: Int) {
        // ★★★ 如果正在刷新，等待刷新完成后再执行 ★★★
        if (_isRefreshing.value) {
            viewModelScope.launch {
                // 等待刷新结束
                _isRefreshing.collect { refreshing ->
                    if (!refreshing) {
                        loadMoreRankingTab(globalId, tabIndex)
                    }
                }
            }
            return
        }

        val state = _moduleContentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return
        val tab = state.tabs.getOrNull(tabIndex) ?: return

        if (tab.isLoadingMore) return

        val nextPage = tab.page + 1

        // ★★★ 重试逻辑：即使 hasMore=false，如果已有书籍且不是空列表，允许重试 ★★★
        val effectiveHasMore = if (!tab.hasMore && tab.books != null && tab.books!!.isNotEmpty()) {
            true
        } else {
            tab.hasMore
        }
        if (!effectiveHasMore) return

        // 更新状态
        _moduleContentStates.update { states ->
            val current = states[globalId] as? ModuleLoadState.RankingTabs ?: return@update states
            val updatedTabs = current.tabs.toMutableList()
            updatedTabs[tabIndex] = updatedTabs[tabIndex].copy(
                isLoadingMore = true,
                hasMore = true,  // 重置 hasMore，允许新请求
                errorMessage = null
            )
            states + (globalId to current.copy(tabs = updatedTabs))
        }

        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
            loadRankingTab(
                moduleId = globalId,
                sourceUrl = module.sourceUrl,
                rssSource = rssSource,
                index = tabIndex,
                title = tab.title,
                url = tab.exploreUrl ?: "",
                page = nextPage
            )
        }
    }

    // ==================== 其他函数 ====================
    fun refreshButtonGroup(globalId: String) {
        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            loadModule(module)
        }
    }

    fun onKindUrlClick(sourceUrl: String, url: String, title: String) =
        _effects.tryEmit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, url))

    fun selectRankingTab(globalId: String, index: Int) {
        val prevState = _moduleContentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return
        _moduleContentStates.update { states ->
            val current = states[globalId] as? ModuleLoadState.RankingTabs ?: return@update states
            states + (globalId to current.copy(selectedIndex = index))
        }
        val tab = prevState.tabs.getOrNull(index) ?: return

        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
            val state = _moduleContentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return@launch
            val currentTab = state.tabs.getOrNull(index) ?: return@launch
            if (currentTab.books == null && currentTab.errorMessage == null) {
                val tabJobKey = "${globalId}_tab_$index"
                if (loadJobs[tabJobKey]?.isActive != true) {
                    loadRankingTab(globalId, module.sourceUrl, rssSource, index, currentTab.title, currentTab.exploreUrl ?: "", page = 1)
                }
            }
            if (preloadMode.value == 1) {
                listOf(index - 1, index + 1).forEach { adjacentIndex ->
                    val adjacentTab = state.tabs.getOrNull(adjacentIndex) ?: return@forEach
                    if (adjacentTab.books == null && adjacentTab.errorMessage == null) {
                        val adjJobKey = "${globalId}_tab_$adjacentIndex"
                        if (loadJobs[adjJobKey]?.isActive != true) {
                            loadRankingTab(globalId, module.sourceUrl, rssSource, adjacentIndex, adjacentTab.title, adjacentTab.exploreUrl ?: "", page = 1)
                        }
                    }
                }
            }
        }
    }

    fun onRefresh(setName: String? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshingSetName.value = setName
            loadJobs.values.forEach { it.cancel() }
            loadJobs.clear()
            if (setName != null) {
                val setModules = uiState.value.modules.filter { it.setName == setName }
                val setModuleIds = setModules.map { it.globalId }.toSet()
                _refreshingModuleIds.value = setModuleIds
                _moduleContentStates.update { states ->
                    states.filterKeys { it !in setModuleIds }
                }
            } else {
                _refreshingModuleIds.value = uiState.value.modules.map { it.globalId }.toSet()
                _moduleContentStates.value = emptyMap()
            }
        }
    }

    fun retryModule(globalId: String) {
        _moduleContentStates.update { it + (globalId to ModuleLoadState.Loading) }
    }

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

    private fun computeShouldLoadModuleIds(
        modules: List<HomepageModuleUi>,
        layoutMode: Int,
        preloadMode: Int
    ): Set<String> {
        if (layoutMode == 0) {
            return modules.map { it.globalId }.toSet()
        }

        val currentSets = _currentSets.value
        val currentTabIndex = _currentTabIndex.value

        if (currentSets.isEmpty()) {
            return emptySet()
        }

        val indicesToLoad = if (preloadMode == 1) {
            val start = (currentTabIndex - 1).coerceAtLeast(0)
            val end = (currentTabIndex + 1).coerceAtMost(currentSets.lastIndex)
            (start..end).toList()
        } else {
            listOf(currentTabIndex.coerceIn(0, currentSets.lastIndex))
        }

        val setUrlsToLoad = indicesToLoad.mapNotNull { index ->
            currentSets.getOrNull(index)?.sourceUrl
        }

        return modules.filter { module ->
            setUrlsToLoad.any { setUrl ->
                if (setUrl.startsWith("custom://")) {
                    val setId = customSetIdFromUrl(setUrl)
                    module.customSetId == setId
                } else {
                    module.customSetId == setUrl
                }
            }
        }.map { it.globalId }.toSet()
    }

    // ==================== Management Methods ====================

    fun toggleManageMode() {
        _isManageMode.value = !_isManageMode.value
    }

    fun setLayoutMode(mode: Int) {
        HomepageConfig.homepageLayoutMode = mode
        notifyConfigChanged()
    }

    fun setPreloadMode(mode: Int) {
        HomepageConfig.homepagePreload = mode
        notifyConfigChanged()
    }

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

    suspend fun getExploreKinds(sourceUrl: String): List<ExploreKind> {
        val source = _bookSourcesCache.value[sourceUrl] ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(sourceUrl)?.exploreKinds() ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getRssKinds(sourceUrl: String): List<Pair<String, String>> {
        val source = appDb.rssSourceDao.getByKey(sourceUrl) ?: return emptyList()
        return runCatching {
            source.sortUrls()
        }.getOrDefault(listOf(Pair("", sourceUrl)))
    }

    private suspend fun ensureRssSetForSource(sourceUrl: String, sourceName: String): String {
        val setId = "rss_$sourceUrl"
        if (gateway.getCustomSetById(setId) == null) gateway.upsertCustomSet(
            CustomSetItem(id = setId, name = sourceName)
        )
        return setId
    }

    fun addRssCustomModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
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

    fun addRssButtonGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        kindTitles: List<String>
    ) {
        viewModelScope.launch {
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

    private fun parseRankingCategories(args: String?): List<Pair<String, String>>? {
        if (args.isNullOrBlank()) return null
        return try {
            val list = GSON.fromJsonArray<Map<String, String>>(args).getOrNull() ?: return null
            val result = list.mapNotNull { map ->
                val t = map["t"] ?: return@mapNotNull null
                val u = map["u"] ?: ""
                Pair(t, u)
            }
            if (result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseKindTitlesFromArgs(args: String?): List<String>? {
        if (args.isNullOrBlank()) return null
        try {
            val list = GSON.fromJsonArray<Map<String, String>>(args).getOrNull()
            if (list != null && list.isNotEmpty()) {
                return list.mapNotNull { it["t"] }
            }
        } catch (_: Exception) { }
        return try {
            GSON.fromJsonArray<String>(args).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun updateModule(globalId: String, def: ModuleDef) {
        viewModelScope.launch {
            val existing = gateway.getById(globalId) ?: return@launch
            val targetSetId = existing.customSetId ?: "src_${existing.sourceUrl}"
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
            val module = allModulesCache.value.find { it.id == globalId }
            if (module != null) {
                val isSourceModule = module.customSetId?.let {
                    it.startsWith("src_") || it.startsWith("rss_")
                } == true
                if (isSourceModule) {
                    gateway.deleteBySourceAndKey(module.sourceUrl, module.moduleKey)
                    val deletedIds = allModulesCache.value
                        .filter { it.sourceUrl == module.sourceUrl && it.moduleKey == module.moduleKey }
                        .map { it.id }
                    _moduleContentStates.update { states ->
                        var result = states
                        deletedIds.forEach { result = result - it }
                        result
                    }
                    deletedIds.forEach { loadJobs.remove(it)?.cancel() }
                } else {
                    gateway.delete(globalId)
                    _moduleContentStates.update { it - globalId }
                    loadJobs.remove(globalId)?.cancel()
                }
            } else {
                gateway.delete(globalId)
                _moduleContentStates.update { it - globalId }
                loadJobs.remove(globalId)?.cancel()
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

    fun deleteCustomSet(id: String) {
        viewModelScope.launch {
            val isSourceSet = id.startsWith("src_") || id.startsWith("rss_")
            if (isSourceSet) {
                val sourceUrl = id.removePrefix("src_").removePrefix("rss_")
                val moduleIds = allModulesCache.value
                    .filter { it.sourceUrl == sourceUrl }
                    .map { it.id }
                gateway.deleteCustomSet(id)
                moduleIds.forEach { mid -> gateway.delete(mid) }
                moduleIds.forEach { mid ->
                    _moduleContentStates.update { it - mid }
                    loadJobs.remove(mid)?.cancel()
                }
            } else {
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

    fun assignModuleToCustomSet(moduleId: String, customSetId: String?) {
        viewModelScope.launch {
            val existing = gateway.getById(moduleId) ?: return@launch
            if (customSetId == null) {
                val inSourceSet = existing.customSetId?.let { it.startsWith("src_") || it.startsWith("rss_") } == true
                if (inSourceSet) {
                    gateway.setEnabled(moduleId, false)
                } else {
                    gateway.delete(moduleId)
                }
            } else {
                val newId = ModuleDef.globalIdOf(existing.sourceUrl, existing.moduleKey, customSetId)
                val targetExisting = gateway.getById(newId)
                if (targetExisting != null) {
                    gateway.setEnabled(newId, true)
                } else {
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