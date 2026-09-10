package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 发现套件首页 ViewModel。
 *
 * 负责从 [DiscoverySuiteStore] 加载选中套件，并驱动每个 widget 的书籍抓取：
 * - RandomBooks / BookList / WaterfallBooks：跨目标分页抓取、去重、随机取样
 * - HorizontalBooks：首目标分页抓取 + 触底加载更多
 * - RankedList：每个目标独立抓取 + 分页加载更多
 * - TagBar / RankButtons：纯按钮类，无需抓取
 *
 * 抓取结果仅保存在进程内（[DiscoverySuiteMemoryCache]），并借助
 * [DiscoveryCachePolicy] 压缩裁剪，防止超过 SQLite 单行上限 —— 这是对
 * 参考项目「套件快照磁盘缓存（CacheDao）」的能力裁剪，见移植说明。
 */
class DiscoverySuiteHomeViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(DiscoverySuiteHomeUiState())
    val uiState: StateFlow<DiscoverySuiteHomeUiState> get() = _uiState

    private val loadJobs = HashMap<String, Job>()
    private val horizontalPaging = HashMap<String, SuitePaging>()
    private val rankedPaging = HashMap<String, SuitePaging>()

    /** 一次性初始化拉取首屏。 */
    fun refreshConfig() {
        if (_uiState.value.selectedSuiteId.isNotBlank()) return
        reloadFromStore()
    }

    /** 页面返回后重新同步存储（可能已在管理页被改动）。 */
    fun reloadFromStore() {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        horizontalPaging.clear()
        rankedPaging.clear()
        val config = DiscoverySuiteStore.load()
        val selectedId = DiscoverySuiteStore.selectedSuiteId()
            .takeIf { id -> config.suites.any { it.id == id } }
            ?: config.suites.firstOrNull()?.id.orEmpty()
        if (selectedId != DiscoverySuiteStore.selectedSuiteId()) {
            DiscoverySuiteStore.setSelectedSuiteId(selectedId)
        }
        val suite = config.suites.firstOrNull { it.id == selectedId }
        _uiState.value = _uiState.value.copy(
            suites = config.suites,
            selectedSuiteId = selectedId,
            selectedSuite = suite,
            loadingWidgetIds = emptySet()
        )
        applySnapshotOrFetch(suite)
    }

    fun selectSuite(suite: DiscoverySuite) {
        DiscoverySuiteStore.setSelectedSuiteId(suite.id)
        reloadFromStore()
        _uiState.value = _uiState.value.copy(scrollToTopSignal = _uiState.value.scrollToTopSignal + 1)
    }

    fun scrollToTop() {
        _uiState.value = _uiState.value.copy(scrollToTopSignal = _uiState.value.scrollToTopSignal + 1)
    }

    fun refreshWidget(widget: DiscoverySuiteWidget) {
        if (!widget.needsFetch()) return
        launchWidgetLoad(widget, force = true) {
            loadAllBooksForWidget(widget)
        }
    }

    fun loadMoreHorizontal(widget: DiscoverySuiteWidget) {
        if (widget.type != DiscoverySuiteWidgetType.HorizontalBooks.value) return
        val target = widget.validRandomTargets().firstOrNull() ?: return
        val current = _uiState.value.widgetBooks[widget.id].orEmpty()
        if (current.isEmpty()) return
        launchWidgetLoad(widget, force = false) {
            val paging = horizontalPaging.getOrPut(widget.id) { SuitePaging() }
            val next = paging.nextPage
            val source = appDb.bookSourceDao.getBookSource(target.sourceUrl)
            val more = source?.let { fetchPage(it, target.tagUrl, next) }.orEmpty()
            paging.nextPage = next + 1
            val merged = (current + more)
                .distinctBy { it.suiteDeckKey() }
                .take(HORIZONTAL_MAX_RENDER)
            merged to emptyMap()
        }
    }

    fun loadMoreRanked(widget: DiscoverySuiteWidget, target: DiscoverySuiteWidgetTarget) {
        val ranked = _uiState.value.rankedWidgetBooks[widget.id].orEmpty().toMutableMap()
        val current = ranked[target.deckKey()].orEmpty()
        if (current.isEmpty()) return
        launchWidgetLoad(widget, force = false) {
            val paging = rankedPaging.getOrPut("${widget.id}\n${target.deckKey()}") { SuitePaging() }
            val next = paging.nextPage
            val source = appDb.bookSourceDao.getBookSource(target.sourceUrl)
            val more = source?.let { fetchPage(it, target.tagUrl, next) }.orEmpty()
            paging.nextPage = next + 1
            ranked[target.deckKey()] = (current + more).distinctBy { it.suiteDeckKey() }
            saveWidgetSnapshot(widget, ranked.values.flatten(), ranked)
            emptyList<SearchBook>() to ranked
        }
    }

    private fun applySnapshotOrFetch(suite: DiscoverySuite?) {
        val state = _uiState.value
        if (suite == null || suite.widgets.isEmpty()) {
            _uiState.value = state.copy(widgetBooks = emptyMap(), rankedWidgetBooks = emptyMap())
            return
        }
        val cached = DiscoverySuiteMemoryCache.get(suite.id)
        if (cached?.signature == suite.cacheSignature()) {
            _uiState.value = state.copy(
                widgetBooks = cached.widgetBooks,
                rankedWidgetBooks = cached.rankedWidgetBooks
            )
            return
        }
        suite.widgets.forEach { widget ->
            val books = emptyList<SearchBook>()
            _uiState.value = _uiState.value.copy(
                widgetBooks = _uiState.value.widgetBooks + (widget.id to books),
                rankedWidgetBooks = _uiState.value.rankedWidgetBooks - widget.id
            )
            if (widget.needsFetch()) {
                loadWidgetAsync(widget)
            }
        }
    }

    private fun loadWidgetAsync(widget: DiscoverySuiteWidget) {
        launchWidgetLoad(widget, force = false) {
            loadAllBooksForWidget(widget)
        }
    }

    private suspend fun loadAllBooksForWidget(
        widget: DiscoverySuiteWidget
    ): Pair<List<SearchBook>, Map<String, List<SearchBook>>> {
        if (!widget.needsFetch()) return emptyList<SearchBook>() to emptyMap()
        return if (widget.type == DiscoverySuiteWidgetType.RankedList.value) {
            val ranked = loadRankedList(widget)
            val flat = ranked.values.flatten()
            saveWidgetSnapshot(widget, flat, ranked)
            emptyList<SearchBook>() to ranked
        } else {
            val books = loadBookWidget(widget)
            saveWidgetSnapshot(widget, books, null)
            books to emptyMap()
        }
    }

    private fun launchWidgetLoad(
        widget: DiscoverySuiteWidget,
        force: Boolean,
        load: suspend () -> Pair<List<SearchBook>, Map<String, List<SearchBook>>>
    ) {
        if (!force && _uiState.value.loadingWidgetIds.contains(widget.id)) return
        loadJobs.remove(widget.id)?.cancel()
        _uiState.value = _uiState.value.copy(
            loadingWidgetIds = _uiState.value.loadingWidgetIds + widget.id
        )
        loadJobs[widget.id] = viewModelScope.launch {
            val (books, ranked) = try {
                withContext(Dispatchers.IO) { load() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.put("套件发现控件加载失败: ${e.message}", e)
                emptyList<SearchBook>() to emptyMap()
            }
            setWidgetBooks(widget, books, ranked)
            loadJobs.remove(widget.id)
        }
    }

    private fun setWidgetBooks(
        widget: DiscoverySuiteWidget,
        books: List<SearchBook>,
        ranked: Map<String, List<SearchBook>>
    ) {
        val s = _uiState.value
        _uiState.value = s.copy(
            widgetBooks = s.widgetBooks + (widget.id to books),
            rankedWidgetBooks = if (ranked.isEmpty()) {
                s.rankedWidgetBooks - widget.id
            } else {
                s.rankedWidgetBooks + (widget.id to ranked)
            },
            loadingWidgetIds = s.loadingWidgetIds - widget.id
        )
    }

    private suspend fun loadBookWidget(widget: DiscoverySuiteWidget): List<SearchBook> {
        val targets = widget.validRandomTargets()
        if (targets.isEmpty()) return emptyList()
        val needs = when (widget.type) {
            DiscoverySuiteWidgetType.WaterfallBooks.value -> widget.displayLimit.coerceIn(8, 40)
            else -> widget.displayLimit.coerceIn(4, 60)
        }
        val seen = LinkedHashSet<SearchBook>()
        val seenKeys = HashSet<String>()
        val targetsCycle = ArrayList(targets)
        var targetIndex = Random(System.nanoTime()).nextInt(targetsCycle.size)
        var attempts = 0
        while (seen.size < needs && attempts < targetsCycle.size * RANDOM_MAX_PAGE) {
            val page = (attempts / targetsCycle.size) + 1
            val target = targetsCycle[targetIndex % targetsCycle.size]
            targetIndex++
            attempts++
            val source = appDb.bookSourceDao.getBookSource(target.sourceUrl) ?: continue
            fetchPage(source, target.tagUrl, page).forEach { book ->
                if (seenKeys.add(book.suiteDeckKey())) {
                    seen.add(book)
                }
            }
            if (seen.size >= needs) break
        }
        return seen.toList().take(needs)
    }

    private suspend fun loadRankedList(widget: DiscoverySuiteWidget): Map<String, List<SearchBook>> {
        val result = linkedMapOf<String, List<SearchBook>>()
        widget.validRandomTargets().take(RANKED_TARGET_LIMIT).forEach { target ->
            val source = appDb.bookSourceDao.getBookSource(target.sourceUrl)
            val books = source?.let { fetchPage(it, target.tagUrl, 1) }
                .orEmpty()
                .distinctBy { it.suiteDeckKey() }
            result[target.deckKey()] = books
        }
        return result
    }

    private suspend fun fetchPage(source: BookSource, tagUrl: String, page: Int): List<SearchBook> {
        return try {
            WebBook.exploreBookAwait(source, tagUrl, page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLog.put("套件发现加载分类失败: ${source.bookSourceUrl} ${e.message}", e)
            emptyList()
        }
    }

    private fun saveWidgetSnapshot(
        widget: DiscoverySuiteWidget,
        books: List<SearchBook>,
        ranked: Map<String, List<SearchBook>>?
    ) {
        val suite = _uiState.value.selectedSuite ?: return
        val cur = DiscoverySuiteMemoryCache.get(suite.id)
        val widgetBooks = (cur?.widgetBooks ?: emptyMap()) + (widget.id to books)
        val rankedBooks = (cur?.rankedWidgetBooks ?: emptyMap()).let { base ->
            if (ranked == null) base - widget.id else base + (widget.id to ranked)
        }
        DiscoverySuiteMemoryCache.put(
            suite.id,
            DiscoverySuiteSnapshot(suite.cacheSignature(), widgetBooks, rankedBooks)
        )
    }
}

/** 进程内套件快照（未经压缩的数据结构）。 */
private data class DiscoverySuiteSnapshot(
    val signature: String,
    val widgetBooks: Map<String, List<SearchBook>>,
    val rankedWidgetBooks: Map<String, Map<String, List<SearchBook>>>
)

/**
 * 进程内套件快照缓存：借助 [DiscoveryCachePolicy] 压缩裁剪后保存，
 * 避免整页 widget 数据无界增长。仅做首屏加载优化，丢失无碍。
 */
private object DiscoverySuiteMemoryCache {
    private val snapshotBySuite = HashMap<String, DiscoverySuiteCompact>()

    fun get(suiteId: String): DiscoverySuiteSnapshot? {
        val compact = snapshotBySuite[suiteId] ?: return null
        if (!DiscoveryCachePolicy.canRead(compact.jsonByteCount)) return null
        return compact.snapshot
    }

    fun put(suiteId: String, snapshot: DiscoverySuiteSnapshot) {
        val compacted = snapshot.compact()
        val json = DiscoveryCachePolicy.toBoundedJson(compacted) ?: return
        val byteCount = DiscoveryCachePolicy.utf8ByteCount(
            json,
            DiscoveryCachePolicy.MAX_SQLITE_VALUE_BYTES
        )
        if (!DiscoveryCachePolicy.canRead(byteCount)) return
        snapshotBySuite[suiteId] = DiscoverySuiteCompact(compacted, byteCount)
    }
}

private data class DiscoverySuiteCompact(
    val snapshot: DiscoverySuiteSnapshot,
    val jsonByteCount: Long
)

private fun DiscoverySuiteSnapshot.compact(): DiscoverySuiteSnapshot {
    return DiscoverySuiteSnapshot(
        signature = signature,
        widgetBooks = widgetBooks.mapValues { entry ->
            entry.value.mapNotNull(DiscoveryCachePolicy::compact)
        }.filterValues { it.isNotEmpty() },
        rankedWidgetBooks = rankedWidgetBooks.mapValues { entry ->
            entry.value.mapValues { inner ->
                inner.value.mapNotNull(DiscoveryCachePolicy::compact)
            }.filterValues { it.isNotEmpty() }
        }.filterValues { it.isNotEmpty() }
    )
}

private class SuitePaging {
    var nextPage: Int = 2
}

private const val RANDOM_MAX_PAGE = 3
private const val RANKED_TARGET_LIMIT = 9
private const val HORIZONTAL_MAX_RENDER = 72