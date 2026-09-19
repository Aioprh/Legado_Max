package io.legado.app.ui.main.homepage

import android.text.Html
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.model.HomepageModuleCategory
import io.legado.app.domain.model.HomepageModuleSpec
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleItem
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.sortUrls
import io.legado.app.model.rss.Rss
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 首页模块内容加载引擎（从 HomepageViewModel 拆分而出的独立状态与逻辑）。
 *
 * 职责：
 * - 承载每个模块的内容加载状态 [contentStates]（加载中/成功/按钮组/排行Tab/错误）
 * - 执行模块内容加载与分页：普通列表、无限流加载更多、排行榜多 Tab 懒加载
 * - 处理刷新时的任务取消与内容清理（刷新编排仍由 [HomepageViewModel] 负责）
 *
 * 依赖注入：
 * - [scope] 协程作用域（通常传入 ViewModel）用于启动加载任务
 * - [gateway] 模块数据网关，用于读取模块定义
 * - [preloadModeProvider] 惰性读取"预加载相邻 Tab"配置，避免加载器直接耦合全局配置
 * - [emitEffect] 副作用回调（导航 / Snackbar），解耦需向上抛出的 UI 事件
 *
 * 该引擎不感知 UI 布局模式 / 预加载 Tab 过滤 / 书架状态等展示层逻辑，
 * 这些仍留在 [HomepageViewModel] 的响应式 Flow 编排中。
 */
class HomepageModuleLoader(
    private val scope: CoroutineScope,
    private val gateway: HomepageModulesGateway,
    private val preloadModeProvider: () -> Int,
    private val emitEffect: (HomepageEffect) -> Unit,
) {

    private val exploreBooksUseCase = ExploreBooksUseCase()

    /** 各加载任务的 Job 表，key 为模块 ID 或 "模块ID_tab_序号" */
    private val loadJobs = ConcurrentHashMap<String, Job>()

    private val _contentStates = MutableStateFlow<Map<String, ModuleLoadState>>(emptyMap())
    val contentStates: StateFlow<Map<String, ModuleLoadState>> = _contentStates.asStateFlow()

    /** 指定 ID 是否有正在执行的加载任务 */
    fun isJobActive(id: String): Boolean = loadJobs[id]?.isActive == true

    /** 取消并清空所有加载任务（刷新前 / ViewModel 销毁时） */
    fun cancelAllJobs() {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
    }

    /** 取消并移除指定模块的加载任务 */
    fun cancelJob(id: String) {
        loadJobs.remove(id)?.cancel()
    }

    /** 清空所有模块内容状态（整页刷新时置空，交由自动加载重新拉取） */
    fun clearAllContent() {
        _contentStates.value = emptyMap()
    }

    /** 清空单个模块的内容状态 */
    fun clearContent(id: String) {
        _contentStates.update { it - id }
    }

    /** 清空一批模块的内容状态（按源集删除 / 关闭时使用） */
    fun clearContent(ids: Collection<String>) {
        _contentStates.update { states -> states.filterKeys { it !in ids } }
    }

    /** 将模块置为 Loading，等待自动加载重新拉取（失败重试） */
    fun retryModule(globalId: String) {
        _contentStates.update { it + (globalId to ModuleLoadState.Loading) }
    }

    /**
     * 加载单个模块的内容。根据模块类型分发到不同的加载策略：
     * - SmartFilter：解析书源发现分类中的交互控件
     * - ButtonGroup：解析 args 中选中的分类标题
     * - Ranking/GridRanking 且多分类：初始化排行榜 Tab 并加载首个分类
     * - 其余：普通书籍列表 / RSS 文章加载
     */
    fun loadModule(module: ModuleItem) {
        loadJobs[module.id]?.cancel()
        val moduleType = HomepageModuleType.fromKey(module.type)
        val moduleCategory = HomepageModuleSpec.category(moduleType)
        // 独立组件（SearchBar）：无内容加载。置为空 Loaded 状态，
        // 避免保持 Loading 导致自动加载反复触发与刷新完成检测一直挂起。
        if (moduleCategory == HomepageModuleCategory.Standalone) {
            _contentStates.update {
                it + (module.id to ModuleLoadState.Loaded(emptyList()))
            }
            return
        }
        if (moduleCategory == HomepageModuleCategory.SmartFilter) {
            loadJobs[module.id] = scope.launch {
                runCatching {
                    val source = withContext(Dispatchers.IO) {
                        appDb.bookSourceDao.getBookSource(module.sourceUrl)
                    } ?: throw Exception("Source not found")
                    withContext(Dispatchers.IO) { source.exploreKinds() }
                }.onSuccess { kinds ->
                    _contentStates.update {
                        it + (module.id to ModuleLoadState.SmartFilters(kinds))
                    }
                }.onFailure { error ->
                    _contentStates.update {
                        it + (module.id to ModuleLoadState.Error(error.stackTraceStr))
                    }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }
        if (moduleCategory == HomepageModuleCategory.ButtonGroup) {
            loadJobs[module.id] = scope.launch {
                kotlin.runCatching {
                    // 从 args 提取分类标题（兼容新旧两种格式）
                    val selectedTitles = parseKindTitlesFromArgs(module.args)
                    if (selectedTitles.isNullOrEmpty()) {
                        emptyList<ExploreKind>()
                    } else {
                        // 检查是否为订阅源
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
                    _contentStates.update { it + (module.id to ModuleLoadState.Buttons(kinds)) }
                }.onFailure { e ->
                    _contentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }
        // 排行榜多分类模式：args 包含多个 {t:标题, u:URL} 对象
        val isRanking = HomepageModuleSpec.isRankingTabs(moduleType)
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
            _contentStates.update { it + (module.id to ModuleLoadState.RankingTabs(initialTabs)) }
            if (rankingCategoryPairs.isNotEmpty()) {
                val (title, url) = rankingCategoryPairs[0]
                loadRankingTab(module.id, module.sourceUrl, rssSource, 0, title, url, page = 1)
            }
            return
        }
        loadJobs[module.id] = scope.launch {
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
                    val books = rssArticlesToSearchBooks(rssSource, articles)
                    books to false
                } else {
                    // 书源加载（原有逻辑）
                    val effectiveUrl = if (isRanking) {
                        rankingCategoryPairs?.firstOrNull()?.second?.ifBlank { null }
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
                _contentStates.update {
                    it + (module.id to ModuleLoadState.Loaded(
                        books = books.map { book ->
                            HomepageBookItemUi(
                                book = book,
                                shelfState = BookshelfMatcher.getState(
                                    book.name, book.author, book.bookUrl
                                )
                            )
                        },
                        hasMore = hasMore,
                        page = 1,
                        isLoadingMore = false
                    ))
                }
            }.onFailure { e ->
                _contentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
    }

    /** 无限流模块加载更多（分页追加，去重） */
    fun loadMoreModule(globalId: String) {
        val currentState = _contentStates.value[globalId] as? ModuleLoadState.Loaded ?: return
        if (currentState.isLoadingMore || !currentState.hasMore) return
        val nextPage = currentState.page + 1
        _contentStates.update { it + (globalId to currentState.copy(isLoadingMore = true)) }
        scope.launch {
            kotlin.runCatching {
                val module = gateway.getById(globalId) ?: throw Exception("Module not found")
                val isRanking = HomepageModuleSpec.isRankingTabs(HomepageModuleType.fromKey(module.type))
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
                _contentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    val existingUrls = lastState.books.map { it.book.bookUrl }.toSet()
                    val deduped = result.books.filter { it.bookUrl !in existingUrls }.map { book ->
                        HomepageBookItemUi(
                            book = book,
                            shelfState = BookshelfMatcher.getState(
                                book.name, book.author, book.bookUrl
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
                _contentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    states + (globalId to lastState.copy(isLoadingMore = false))
                }
                emitEffect(HomepageEffect.ShowSnackbar("加载更多失败: ${e.message}"))
            }
        }
    }

    /** 重新加载按钮组模块（修正分类偏好后调用） */
    fun refreshButtonGroup(globalId: String) {
        scope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            loadModule(module)
        }
    }

    /**
     * 切换排行榜 Tab 时按需加载当前选中分类的内容（懒加载优化）。
     * 仅加载选中的分类 Tab。若预加载开启（preloadModeProvider()==1），
     * 还会预先加载相邻分类 Tab 的内容以提升切换体验。
     */
    fun selectRankingTab(globalId: String, index: Int) {
        // 更新 selectedIndex
        val prevState = _contentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return
        _contentStates.update { states ->
            val current = states[globalId] as? ModuleLoadState.RankingTabs ?: return@update states
            states + (globalId to current.copy(selectedIndex = index))
        }
        // 按需加载：只加载当前选中的 Tab
        val tab = prevState.tabs.getOrNull(index) ?: return

        scope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            val rssSource = appDb.rssSourceDao.getByKey(module.sourceUrl)
            // 重新获取最新状态（可能已被 refresh 清空）
            val state = _contentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return@launch
            val currentTab = state.tabs.getOrNull(index) ?: return@launch
            // 加载当前 Tab（如果尚未加载）
            if (currentTab.books == null && currentTab.errorMessage == null) {
                val tabJobKey = "${globalId}_tab_$index"
                if (loadJobs[tabJobKey]?.isActive != true) {
                    loadRankingTab(globalId, module.sourceUrl, rssSource, index, currentTab.title, currentTab.exploreUrl ?: "", page = 1)
                }
            }
            // 预加载相邻 Tab（预加载开启时）
            if (preloadModeProvider() == 1) {
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
        loadJobs[jobKey] = scope.launch {
            kotlin.runCatching {
                val books = if (rssSource != null) {
                    val (articles, _) = withContext(Dispatchers.IO) {
                        Rss.getArticlesAwait(title.ifBlank { rssSource.sourceName }, url, rssSource, page = page)
                    }
                    rssArticlesToSearchBooks(rssSource, articles)
                } else {
                    val result = exploreBooksUseCase.execute(
                        sourceUrl = sourceUrl,
                        moduleUrl = url.ifBlank { null },
                        args = null,
                        page = page
                    )
                    result.books
                }
                books.map { book ->
                    HomepageBookItemUi(
                        book = book,
                        shelfState = BookshelfMatcher.getState(
                            book.name, book.author, book.bookUrl
                        )
                    )
                }
            }.onSuccess { bookItems ->
                _contentStates.update { states ->
                    val current = states[moduleId] as? ModuleLoadState.RankingTabs ?: return@update states
                    val updatedTabs = current.tabs.toMutableList()
                    val oldTab = updatedTabs[index]
                    val existingUrls = oldTab.books?.map { it.book.bookUrl }?.toSet() ?: emptySet()
                    val deduped = bookItems.filter { it.book.bookUrl !in existingUrls }
                    val newBooks = if (oldTab.books != null) oldTab.books + deduped else bookItems
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
                _contentStates.update { states ->
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

    /** 排行榜 Tab 加载更多（分页追加，带重试逻辑） */
    fun loadMoreRankingTab(globalId: String, tabIndex: Int) {
        val state = _contentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return
        val tab = state.tabs.getOrNull(tabIndex) ?: return

        if (tab.isLoadingMore) return

        val nextPage = tab.page + 1

        // 重试逻辑：即使 hasMore=false，如果已有书籍且不是空列表，允许重试
        val effectiveHasMore = if (!tab.hasMore && tab.books != null && tab.books.isNotEmpty()) {
            true
        } else {
            tab.hasMore
        }
        if (!effectiveHasMore) return

        // 更新状态
        _contentStates.update { states ->
            val current = states[globalId] as? ModuleLoadState.RankingTabs ?: return@update states
            val updatedTabs = current.tabs.toMutableList()
            updatedTabs[tabIndex] = updatedTabs[tabIndex].copy(
                isLoadingMore = true,
                hasMore = true,
                errorMessage = null
            )
            states + (globalId to current.copy(tabs = updatedTabs))
        }

        scope.launch {
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

    // ==================== 内部辅助 ====================

    /** 订阅源文章 → SearchBook：去除 HTML 标签得到纯文本简介，复用现有书籍 UI */
    private fun rssArticlesToSearchBooks(
        rssSource: RssSource,
        articles: List<RssArticle>
    ): List<SearchBook> {
        return articles.map { article ->
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
                latestChapterTitle = article.pubDate
            )
        }
    }

    /**
     * 解析排行榜模块 args 中的多分类数据
     * @return 解析成功返回 (标题, URL) 列表；否则返回 null
     */
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

    /**
     * 兼容新旧两种 args 格式提取分类标题
     * 新: [{"t":"title1","u":"url1"},...]  旧: ["title1","title2"]
     */
    private fun parseKindTitlesFromArgs(args: String?): List<String>? {
        if (args.isNullOrBlank()) return null
        // 先尝试新格式 [{t, u}]
        try {
            val list = GSON.fromJsonArray<Map<String, String>>(args).getOrNull()
            if (list != null && list.isNotEmpty()) {
                return list.mapNotNull { it["t"] }
            }
        } catch (_: Exception) { }
        // 回退旧格式 ["title1","title2"]
        return try {
            GSON.fromJsonArray<String>(args).getOrNull()
        } catch (_: Exception) {
            null
        }
    }
}