package io.legado.app.ui.book.explore

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.blockrule.BlockRuleStore
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Fragment级别的ViewModel，管理单个分类的书籍数据
 * 参考 RssArticlesViewModel 的实现
 */
class ExploreShowFragmentViewModel(application: Application) : BaseViewModel(application) {
    companion object {
        private const val MAX_CACHE_SIZE = 10
        private const val MAX_BOOKS_PER_CATEGORY = 300 // 单个分类最大累积书籍数量，防止OOM
        private val pageQueryRegex = Regex("""([?&]page=)(\d+)""", RegexOption.IGNORE_CASE)

        /** 共享分类数据缓存（LRU，最多 MAX_CACHE_SIZE 条），key = exploreUrl */
        private val preloadCache = object : LinkedHashMap<String, List<SearchBook>>(
            MAX_CACHE_SIZE, 0.75f, true
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchBook>>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }

        @Synchronized
        fun getCachedBooks(exploreUrl: String): List<SearchBook>? = preloadCache[exploreUrl]

        @Synchronized
        fun cacheBooks(exploreUrl: String, books: List<SearchBook>) {
            // 缓存时限制单个分类的最大书籍数量，防止预加载缓存占用过多内存
            val limitedBooks = if (books.size > MAX_BOOKS_PER_CATEGORY) {
                books.take(MAX_BOOKS_PER_CATEGORY)
            } else {
                books
            }
            preloadCache[exploreUrl] = limitedBooks
        }

        @Synchronized
        fun clearDataCache() {
            preloadCache.clear()
        }
    }

    val booksData = MutableLiveData<List<SearchBook>>()
    val addBooksData = MutableLiveData<List<SearchBook>>()
    val errorLiveData = MutableLiveData<String>()
    val errorTopLiveData = MutableLiveData<String>()
    val loadFinallyLiveData = MutableLiveData<Boolean>()
    val pageLiveData = MutableLiveData<Int>()

    var isLoading = true
    var order = System.currentTimeMillis()
    private var nextPageUrl: String? = null
    private var initialExploreUrl: String = ""
    /** 分类的唯一标识 URL（不含页码参数，稳定不变） */
    val stableExploreUrl: String get() = initialExploreUrl
    var exploreKindName: String = ""
    var exploreUrl: String = ""
    var page = 1

    private var books = linkedSetOf<SearchBook>()
    private var allBooks = linkedSetOf<SearchBook>()
    private var bookSource: BookSource? = null
    var sourceUrl: String = ""

    /**
     * 当书籍数据累积超过限制时，清理旧数据防止OOM
     * 保留最近添加的书籍，避免无限累积
     */
    private fun trimBooksIfNeeded() {
        if (allBooks.size > MAX_BOOKS_PER_CATEGORY) {
            // 将 LinkedHashSet 转为列表，移除最旧的书籍（LinkedHashSet按插入顺序）
            val allBooksList = allBooks.toList()
            val booksList = books.toList()
            
            // 计算需要保留的数量
            val keepCount = MAX_BOOKS_PER_CATEGORY
            val dropCount = allBooksList.size - keepCount
            
            // 重新构建 Set，只保留最近的书籍
            allBooks = linkedSetOf<SearchBook>().apply {
                addAll(allBooksList.drop(dropCount))
            }
            
            // 同步更新 filtered books
            books = linkedSetOf<SearchBook>().apply {
                addAll(booksList.drop(dropCount))
            }
        }
    }

    fun init(bundle: Bundle?, source: BookSource?) {
        bundle?.let {
            exploreKindName = it.getString("exploreKindName") ?: ""
            exploreUrl = it.getString("exploreUrl") ?: ""
            initialExploreUrl = exploreUrl
            sourceUrl = it.getString("sourceUrl") ?: ""
        }
        bookSource = source
        page = parsePageFromUrl(exploreUrl)
        pageLiveData.value = page
    }

    fun loadBooks(targetPage: Int = 1) {
        isLoading = true
        page = targetPage.coerceAtLeast(1)
        order = System.currentTimeMillis()
        nextPageUrl = null

        // 第一页优先使用缓存，避免重复网络请求
        if (targetPage == 1) {
            getCachedBooks(initialExploreUrl)?.let { cached ->
                allBooks.addAll(cached)
                trimBooksIfNeeded() // 添加后检查并清理
                val filtered = BlockRuleStore.filterBooks(getApplication(), cached, sourceUrl)
                books.addAll(filtered)
                booksData.postValue(books.toList())
                appDb.searchBookDao.insert(*cached.toTypedArray())
                pageLiveData.postValue(page)
                loadFinallyLiveData.postValue(cached.isNotEmpty())
                isLoading = false
                return
            }
        }

        val source = bookSource ?: return
        val url = buildExploreUrl(page)

        WebBook.exploreBook(viewModelScope, source, url, page)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                trimBooksIfNeeded() // 添加后检查并清理
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, sourceUrl)
                books.addAll(filtered)
                booksData.postValue(books.toList())
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                // 发送当前加载的页数给UI显示（参考订阅源RssArticlesViewModel的实现）
                pageLiveData.postValue(page)
                val hasMore = searchBooks.isNotEmpty()
                loadFinallyLiveData.postValue(hasMore)
                if (targetPage == 1 && searchBooks.isNotEmpty()) {
                    cacheBooks(initialExploreUrl, searchBooks)
                }
                isLoading = false
            }.onError {
                loadFinallyLiveData.postValue(false)
                it.printOnDebug()
                errorLiveData.postValue(it.stackTraceStr)
                isLoading = false
            }
    }

    fun loadMore() {
        isLoading = true
        page++
        val source = bookSource ?: return
        val url = buildExploreUrl(page)

        WebBook.exploreBook(viewModelScope, source, url, page)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                trimBooksIfNeeded() // 添加后检查并清理
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, sourceUrl)
                books.addAll(filtered)
                booksData.postValue(books.toList())
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(page)
                loadFinallyLiveData.postValue(searchBooks.isNotEmpty())
                isLoading = false
            }.onError {
                loadFinallyLiveData.postValue(false)
                it.printOnDebug()
                errorLiveData.postValue(it.stackTraceStr)
                isLoading = false
            }
    }

    fun loadPrevious() {
        if (page <= 1) return
        isLoading = true
        val prevPage = page - 1
        val source = bookSource ?: return
        val url = buildExploreUrl(prevPage)

        WebBook.exploreBook(viewModelScope, source, url, prevPage)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                trimBooksIfNeeded() // 添加后检查并清理
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, sourceUrl)
                val newBooks = linkedSetOf<SearchBook>()
                newBooks.addAll(filtered)
                newBooks.addAll(books)
                books = newBooks
                addBooksData.postValue(filtered)
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(prevPage)
                page = prevPage
                loadFinallyLiveData.postValue(searchBooks.isNotEmpty())
                isLoading = false
            }.onError {
                loadFinallyLiveData.postValue(false)
                it.printOnDebug()
                errorTopLiveData.postValue(it.stackTraceStr)
                isLoading = false
            }
    }

    fun skipPage(targetPage: Int) {
        page = targetPage.coerceAtLeast(1)
        nextPageUrl = null
        books.clear()
        allBooks.clear()
        pageLiveData.postValue(page)
    }

    fun clearBooks() {
        books.clear()
        allBooks.clear()
        booksData.postValue(emptyList())
    }

    fun getBooksCount(): Int = books.size

    /** 获取当前分类的书籍列表（用于全部加入书架） */
    fun getSearchBooks(): List<SearchBook> = books.toList()

    private fun parsePageFromUrl(url: String?): Int {
        val pageValue = url?.let {
            pageQueryRegex.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull()
        }
        return pageValue?.takeIf { it > 0 } ?: 1
    }

    private fun buildExploreUrl(page: Int): String {
        val safePage = page.coerceAtLeast(1)
        val currentUrl = exploreUrl
        val updatedUrl = pageQueryRegex.replace(currentUrl) {
            "${it.groupValues[1]}$safePage"
        }
        exploreUrl = updatedUrl
        return updatedUrl
    }

    /**
     * 屏蔽规则变化后重新过滤当前书籍列表
     * 优化：直接在 allBooks 的 iterator 上过滤，避免创建临时列表
     */
    fun applyBlockRules() {
        // 获取已启用且作用域匹配的规则
        val rules = BlockRuleStore.loadEnabled(getApplication())
            .filter { it.matchesScope(sourceUrl) }

        if (rules.isEmpty()) {
            // 无规则时，直接恢复 allBooks 到 books
            books = linkedSetOf<SearchBook>().apply { addAll(allBooks) }
        } else {
            // 在 allBooks 的 iterator 上过滤，减少临时对象创建
            books = linkedSetOf<SearchBook>().apply {
                allBooks.forEach { book ->
                    if (!rules.any { rule -> rule.matches(book) }) {
                        add(book)
                    }
                }
            }
        }

        booksData.postValue(books.toList())
    }

    /**
     * 获取原始未过滤书籍列表
     */
    fun getAllBooks(): List<SearchBook> = allBooks.toList()

    /**
     * 获取当前被屏蔽的书籍数量
     */
    fun getBlockedCount(): Int = allBooks.size - books.size
}