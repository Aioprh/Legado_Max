package io.legado.app.ui.book.explore

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.blockrule.BlockRule
import io.legado.app.model.blockrule.BlockRuleStore
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import io.legado.app.help.source.exploreKinds
import io.legado.app.data.entities.rule.ExploreKind


@OptIn(ExperimentalCoroutinesApi::class)
class ExploreShowViewModel(application: Application) : BaseViewModel(application) {
    companion object {
        private val pageQueryRegex = Regex("""([?&]page=)(\d+)""", RegexOption.IGNORE_CASE)
        /** 预加载缓存的最大数量，避免OOM */
        private const val MAX_PRELOAD_CACHE_SIZE = 10
    }

    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val upAdapterLiveData = MutableLiveData<String>()
    val booksData = MutableLiveData<List<SearchBook>>()
    val addBooksData = MutableLiveData<List<SearchBook>>()
    val errorLiveData = MutableLiveData<String>()
    val errorTopLiveData = MutableLiveData<String>()
    val pageLiveData = MutableLiveData<Int>()
    val addAllToShelfResult = MutableLiveData<Int>()
    /** 屏蔽规则变化后通知UI全量刷新书籍列表 */
    val blockRulesRefreshData = MutableLiveData<List<SearchBook>>()
    /** 屏蔽数量变化通知UI更新进度指示器 */
    val blockedCountData = MutableLiveData<Int>()
    /** 实际匹配到书籍的规则列表，用于"开启屏蔽规则后起效的规则"展示 */
    val matchedRulesData = MutableLiveData<List<BlockRule>>()
    val booksCount: Int get() = books.size
    /** 所有发现分类列表，用于Tab显示 */
    val exploreKindsData = MutableLiveData<List<ExploreKind>>()
    /** 预加载的分类数据缓存（分类URL -> 书籍列表） */
    private val preloadCache = ConcurrentHashMap<String, List<SearchBook>>()
    private var bookSource: BookSource? = null
    private var exploreUrl: String? = null
    private var page = 1
    private var books = linkedSetOf<SearchBook>()
    /** 原始未过滤的书籍列表，用于屏蔽规则变化时重新过滤 */
    private var allBooks = linkedSetOf<SearchBook>()
    /** 获取原始未过滤书籍列表的副本 */
    val allBooksList: List<SearchBook> get() = allBooks.toList()
    /** 当前书源URL，用于屏蔽规则过滤 */
    var currentSourceUrl: String = ""

    //实时监听数据库对比书名作者，判断书是否在书架上
    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = arrayListOf<String>()
                books.filterNot { it.isNotShelf }
                    .forEach {
                        keys.add("${it.name}-${it.author}")
                        keys.add(it.name)
                        keys.add(it.bookUrl)
                    }
                keys
            }.catch {
                AppLog.put("发现列表界面获取书籍数据失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }
    
    /**
     * ViewModel初始化数据
     */
    fun initData(intent: Intent) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            currentSourceUrl = sourceUrl ?: ""
            exploreUrl = intent.getStringExtra("exploreUrl")
            page = parsePageFromUrl(exploreUrl)
            if (bookSource == null && sourceUrl != null) {
                bookSource = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            pageLiveData.postValue(page)
            // 加载所有发现分类（用于Tab显示）
            loadExploreKinds()
            explore()
        }
    }

    /**
     * 加载书源的所有发现分类
     */
    private suspend fun loadExploreKinds() {
        val source = bookSource
        if (source == null) {
            exploreKindsData.postValue(emptyList())
            return
        }
        withContext(IO) {
            kotlin.runCatching {
                source.exploreKinds().filter { !it.url.isNullOrBlank() }
            }.onSuccess { kinds ->
                exploreKindsData.postValue(kinds)
            }.onFailure {
                exploreKindsData.postValue(emptyList())
            }
        }
    }

    /**
     * 上滑触发的增量更新
     */
    fun explore(page: Int) {
        val source = bookSource
        val url = buildExploreUrl(page)
        if (source == null || url == null) return
        WebBook.exploreBook(viewModelScope, source, url, page)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, currentSourceUrl)
                val newBooks = linkedSetOf<SearchBook>()
                newBooks.addAll(filtered)
                newBooks.addAll(books)
                books = newBooks
                addBooksData.postValue(filtered)
                blockedCountData.postValue(allBooks.size - books.size)
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(page)
            }.onError {
                it.printOnDebug()
                errorTopLiveData.postValue(it.stackTraceStr)
            }
    }

    /**
     * 跳转到指定页码
     */
    fun skipPage(page: Int) {
        if (page > 0) {
            books.clear()
            allBooks.clear()
            this.page = page
            pageLiveData.postValue(page)
        }
    }
    /**
     * 网络请求核心逻辑
     */
    fun explore() {
        val source = bookSource
        val requestPage = page
        val url = buildExploreUrl(requestPage)
        if (source == null || url == null) return
        WebBook.exploreBook(viewModelScope, source, url, requestPage)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, currentSourceUrl)
                books.addAll(filtered)
                booksData.postValue(books.toList())
                blockedCountData.postValue(allBooks.size - books.size)
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(requestPage)
                page = requestPage + 1
            }.onError {
                it.printOnDebug()
                errorLiveData.postValue(it.stackTraceStr)
            }
    }

    private fun parsePageFromUrl(url: String?): Int {
        val pageValue = url?.let {
            // 从URL中提取页码，分页机制
            // 例如：https://www.baidu.com/explore?page=2
            pageQueryRegex.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull()
        }
        return pageValue?.takeIf { it > 0 } ?: 1
    }

    private fun buildExploreUrl(page: Int): String? {
        val safePage = page.coerceAtLeast(1)
        val currentUrl = exploreUrl ?: return null
        val updatedUrl = pageQueryRegex.replace(currentUrl) {
            "${it.groupValues[1]}$safePage"
        }
        exploreUrl = updatedUrl
        return updatedUrl
    }

    /**
     * 屏蔽规则变化后重新过滤当前书籍列表
     */
    fun applyBlockRules(sourceUrl: String) {
        currentSourceUrl = sourceUrl
        BlockRuleStore.invalidateCache()
        val matched = BlockRuleStore.getMatchedRules(getApplication(), allBooks.toList(), sourceUrl)
        val filtered = BlockRuleStore.filterBooks(getApplication(), allBooks.toList(), sourceUrl)
        books = linkedSetOf<SearchBook>().apply { addAll(filtered) }
        blockedCountData.postValue(allBooks.size - books.size)
        matchedRulesData.postValue(matched)
        blockRulesRefreshData.postValue(books.toList())
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return bookshelf.contains(key) || bookshelf.contains(bookUrl)
    }

    fun getBookShelfState(book: SearchBook): BookShelfState {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return when {
            bookshelf.contains(bookUrl) -> BookShelfState.IN_SHELF
            bookshelf.contains(key) -> BookShelfState.SAME_NAME_AUTHOR
            else -> BookShelfState.NOT_IN_SHELF
        }
    }

    fun addAllToShelf(groupId: Long) {
        execute {
            val booksToAdd = books.filterNot { isInBookShelf(it) }
            if (booksToAdd.isEmpty()) {
                addAllToShelfResult.postValue(0)
                return@execute
            }
            
            val bookEntities = booksToAdd.mapIndexed { index, searchBook ->
                searchBook.toBook().apply {
                    this.group = groupId
                    this.order = index
                }
            }
            
            appDb.bookDao.insert(*bookEntities.toTypedArray())
            
            bookEntities.forEach { book ->
                val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
                bookshelf.add(key)
                bookshelf.add(book.bookUrl)
            }
            
            addAllToShelfResult.postValue(booksToAdd.size)
        }.onError {
            AppLog.put("批量加入书架失败", it)
            errorLiveData.postValue("批量加入书架失败: ${it.localizedMessage}")
        }
    }

    fun addToShelf(book: SearchBook) {
        execute {
            val bookEntity = book.toBook()
            appDb.bookDao.insert(bookEntity)
            val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
            bookshelf.add(key)
            bookshelf.add(book.bookUrl)
            upAdapterLiveData.postValue("isInBookshelf")
        }.onError {
            AppLog.put("加入书架失败", it)
            errorLiveData.postValue("加入书架失败: ${it.localizedMessage}")
        }
    }

    /**
     * 切换到指定分类
     * 清空当前书籍列表，加载新分类的书籍
     *
     * @param newUrl 分类URL
     * @param exploreName 分类名称（可选，用于标题栏）
     * @param preload 是否预加载相邻分类
     * @param allKinds 所有分类列表（用于预加载相邻分类）
     */
    fun switchCategory(
        newUrl: String,
        exploreName: String? = null,
        preload: Boolean = false,
        allKinds: List<ExploreKind>? = null
    ) {
        execute {
            // 检查是否有预加载缓存
            val cachedData = preloadCache[newUrl]
            if (cachedData != null) {
                // 使用缓存数据
                books.clear()
                allBooks.clear()
                books.addAll(cachedData)
                allBooks.addAll(cachedData)
                booksData.postValue(cachedData)
                page = parsePageFromUrl(newUrl) + 1
                exploreUrl = newUrl
                pageLiveData.postValue(parsePageFromUrl(newUrl))
                // 清除已使用的缓存
                preloadCache.remove(newUrl)
            } else {
                // 清空当前书籍列表（不发送空列表，避免触发"没有更多数据"提示）
                books.clear()
                allBooks.clear()
                // 更新URL和页码
                page = parsePageFromUrl(newUrl)
                exploreUrl = newUrl
                pageLiveData.postValue(page)
                // 开始加载新分类的书籍
                explore()
            }

            // 预加载相邻分类
            if (preload && allKinds != null) {
                preloadAdjacentCategories(newUrl, allKinds)
            }
        }
    }

    /**
     * 预加载相邻分类的内容
     *
     * @param currentUrl 当前分类URL
     * @param allKinds 所有分类列表
     */
    private fun preloadAdjacentCategories(currentUrl: String, allKinds: List<ExploreKind>) {
        val currentIndex = allKinds.indexOfFirst { it.url == currentUrl }
        if (currentIndex < 0) return

        val source = bookSource ?: return
        val indicesToPreload = mutableListOf<Int>()

        // 预加载前一个分类
        if (currentIndex > 0) {
            indicesToPreload.add(currentIndex - 1)
        }
        // 预加载后一个分类
        if (currentIndex < allKinds.size - 1) {
            indicesToPreload.add(currentIndex + 1)
        }

        // 检查缓存大小，如果超过限制，清除旧的缓存
        if (preloadCache.size >= MAX_PRELOAD_CACHE_SIZE) {
            // 清除一半的缓存（保留当前分类相邻的）
            val urlsToKeep = mutableSetOf<String>()
            indicesToPreload.forEach { index ->
                allKinds.getOrNull(index)?.url?.let { urlsToKeep.add(it) }
            }
            urlsToKeep.add(currentUrl)
            
            preloadCache.keys.removeAll { !urlsToKeep.contains(it) }
        }

        // 异步预加载
        viewModelScope.launch(IO) {
            indicesToPreload.forEach { index ->
                val kind = allKinds[index]
                val url = kind.url ?: return@forEach
                // 检查是否已缓存
                if (preloadCache.containsKey(url)) return@forEach

                kotlin.runCatching {
                    val preloadPage = parsePageFromUrl(url)
                    val preloadUrl = buildExploreUrlFromBase(url, preloadPage)
                    if (preloadUrl != null) {
                        WebBook.exploreBookAwait(source, preloadUrl, preloadPage)
                    } else {
                        null
                    }
                }.onSuccess { searchBooks ->
                    if (searchBooks != null) {
                        val filtered = BlockRuleStore.filterBooks(
                            getApplication(),
                            searchBooks,
                            currentSourceUrl
                        )
                        preloadCache[url] = filtered
                    }
                }
            }
        }
    }

    /**
     * 从基础URL构建完整的探索URL
     */
    private fun buildExploreUrlFromBase(baseUrl: String, page: Int): String? {
        val safePage = page.coerceAtLeast(1)
        return pageQueryRegex.replace(baseUrl) {
            "${it.groupValues[1]}$safePage"
        }
    }

    /**
     * 检查是否有预加载缓存
     */
    fun hasPreloadCache(url: String): Boolean {
        return preloadCache.containsKey(url)
    }

    /**
     * 清除预加载缓存
     */
    fun clearPreloadCache() {
        preloadCache.clear()
    }

}
