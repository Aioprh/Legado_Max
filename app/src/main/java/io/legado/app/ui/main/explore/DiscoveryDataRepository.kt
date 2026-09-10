package io.legado.app.ui.main.explore

import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 现代发现页的数据层。
 *
 * 直接复用 Max 现有的 WebBook.exploreBookAwait()，因此发现规则、JS、请求、解析
 * 和书源兼容性仍由原有核心负责；现代 UI 只负责组合和展示结果。
 */
object DiscoveryDataRepository {
    private val cache = DiscoveryResultCache<String, List<SearchBook>>(
        DiscoveryCachePolicy(
            maxEntries = 80,
            maxBooksPerEntry = 60,
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
        val key = buildKey(sourceUrl, tagUrl, page)
        if (!forceRefresh) {
            cache.get(key)?.let {
                return@withContext TargetResult(sourceUrl, title, it, page, fromCache = true)
            }
        }

        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            ?: return@withContext TargetResult(
                sourceUrl, title, emptyList(), page, error = "书源不存在或已被删除"
            )

        runCatching {
            WebBook.exploreBookAwait(source, tagUrl, page)
                .take(60)
        }.onSuccess { books ->
            cache.put(key, books)
        }.fold(
            onSuccess = { books ->
                TargetResult(sourceUrl, title, books, page)
            },
            onFailure = { throwable ->
                TargetResult(
                    sourceUrl,
                    title,
                    emptyList(),
                    page,
                    error = throwable.message ?: throwable.javaClass.simpleName
                )
            }
        )
    }

    suspend fun loadWidget(
        widget: DiscoverySuiteWidget,
        forceRefresh: Boolean = false
    ): List<TargetResult> = withContext(Dispatchers.IO) {
        widget.targets.map { target ->
            loadTarget(
                sourceUrl = target.sourceUrl,
                tagUrl = target.tagUrl,
                title = target.title,
                page = 1,
                forceRefresh = forceRefresh
            )
        }
    }

    fun clear() = cache.clear()

    private fun buildKey(sourceUrl: String, tagUrl: String, page: Int): String =
        "$sourceUrl|$tagUrl|${page.coerceAtLeast(1)}"
}
