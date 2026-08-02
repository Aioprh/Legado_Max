package io.legado.app.domain.usecase

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExploreBooksUseCase {

    suspend fun execute(
        sourceUrl: String,
        moduleUrl: String?,
        args: String?,
        page: Int = 1,
        key: String? = null,
    ): ExploreResult = withContext(Dispatchers.IO) {
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            ?: throw SourceNotFound(sourceUrl)
        val url = resolveUrl(source, moduleUrl, key)
        val books = WebBook.exploreBookAwait(source, url, page)
        // 每次只取 1 页（约 20 本），有数据则认为可能还有下一页
        ExploreResult(url, books, hasMore = books.isNotEmpty())
    }

    /**
     * 排行榜类模块加载：每次只请求 1 页（约 20 本）。
     * 注意：返回类型保持 List<SearchBook>，与 loadRankingTab 的用法一致。
     */
    suspend fun executeForRanking(
        sourceUrl: String,
        moduleUrl: String?,
        args: String?,
        page: Int = 1,
    ): List<SearchBook> = withContext(Dispatchers.IO) {
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            ?: throw SourceNotFound(sourceUrl)
        val url = resolveUrl(source, moduleUrl, null)
        WebBook.exploreBookAwait(source, url, page)
    }

    private fun resolveUrl(
        source: BookSource,
        moduleUrl: String?,
        key: String?,
    ): String {
        return moduleUrl
            ?: source.exploreUrl
            ?: throw NoExploreUrl(source.bookSourceUrl)
    }

    data class ExploreResult(
        val resolvedUrl: String,
        val books: List<SearchBook>,
        val hasMore: Boolean = false,
    )

    class SourceNotFound(url: String) : Exception("Source not found: ${url.take(60)}")
    class NoExploreUrl(url: String) : Exception("No explore URL for source: ${url.take(60)}")
}