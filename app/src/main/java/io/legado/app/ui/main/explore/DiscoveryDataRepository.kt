package io.legado.app.ui.main.explore

import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 现代发现页的数据层。
 *
 * 发现规则、JS、网络请求和解析全部复用 Max 原有 WebBook 能力；这里负责
 * 多目标并发、分页、去重、失败隔离和短期缓存。
 */
object DiscoveryDataRepository {
    private const val MAX_PAGE_SIZE = 60
    private const val MAX_TARGETS = 50

    private val cache = DiscoveryResultCache<String, List<SearchBook>>(
        DiscoveryCachePolicy(
            maxEntries = 80,
            maxBooksPerEntry = MAX_PAGE_SIZE,
            ttlMillis = 2 * 60 * 1000L,
            staleWhileRevalidate = true
        )
    )

    data class TargetResult(
        val sourceUrl: String,
        val title: String,
        val books: List<SearchBook>,
        val page: Int,
        val fromCache: Boolean = false,
        val error: String? = null
    )

    suspend fun loadTarget(
        sourceUrl: String,
        tagUrl: String,
        title: String,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): TargetResult = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val key = buildKey(sourceUrl, tagUrl, safePage)
        if (!forceRefresh) {
            cache.get(key)?.let {
                return@withContext TargetResult(sourceUrl, title, it, safePage, fromCache = true)
            }
        }

        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            ?: return@withContext TargetResult(
                sourceUrl, title, emptyList(), safePage, error = "书源不存在或已被删除"
            )

        runCatching {
            WebBook.exploreBookAwait(source, tagUrl, safePage)
                .take(MAX_PAGE_SIZE)
        }.onSuccess { books ->
            cache.put(key, books)
        }.fold(
            onSuccess = { books ->
                TargetResult(sourceUrl, title, books, safePage)
            },
            onFailure = { throwable ->
                TargetResult(
                    sourceUrl,
                    title,
                    emptyList(),
                    safePage,
                    error = throwable.message?.take(180) ?: throwable.javaClass.simpleName
                )
            }
        )
    }

    suspend fun loadWidget(
        widget: DiscoverySuiteWidget,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): List<TargetResult> = coroutineScope {
        widget.targets
            .asSequence()
            .filter { it.sourceUrl.isNotBlank() && it.tagUrl.isNotBlank() }
            .distinctBy { "${it.sourceUrl}|${it.tagUrl}" }
            .take(MAX_TARGETS)
            .map { target ->
                async(Dispatchers.IO) {
                    loadTarget(
                        sourceUrl = target.sourceUrl,
                        tagUrl = target.tagUrl,
                        title = target.title,
                        page = page,
                        forceRefresh = forceRefresh
                    )
                }
            }
            .toList()
            .awaitAll()
    }

    /** 聚合多个目标并按“书源 + 书籍 URL”去重，单个书源失败不会影响其他目标。 */
    suspend fun loadWidgetBooks(
        widget: DiscoverySuiteWidget,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): List<SearchBook> = loadWidget(widget, page, forceRefresh)
        .flatMap { it.books }
        .distinctBy { "${it.origin}|${it.bookUrl}" }
        .take(widget.displayLimit.coerceIn(1, MAX_PAGE_SIZE))

    fun clear() = cache.clear()

    private fun buildKey(sourceUrl: String, tagUrl: String, page: Int): String =
        "$sourceUrl|$tagUrl|${page.coerceAtLeast(1)}"
}
