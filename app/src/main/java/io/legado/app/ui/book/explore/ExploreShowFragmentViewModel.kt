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
        private val pageQueryRegex = Regex("""([?&]page=)(\d+)""", RegexOption.IGNORE_CASE)
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
    var exploreKindName: String = ""
    var exploreUrl: String = ""
    var page = 1

    private var books = linkedSetOf<SearchBook>()
    private var allBooks = linkedSetOf<SearchBook>()
    private var bookSource: BookSource? = null
    var sourceUrl: String = ""

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
        
        val source = bookSource ?: return
        val url = buildExploreUrl(page)
        
        WebBook.exploreBook(viewModelScope, source, url, page)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, sourceUrl)
                books.addAll(filtered)
                booksData.postValue(books.toList())
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(page)
                page = page + 1
                val hasMore = searchBooks.isNotEmpty()
                loadFinallyLiveData.postValue(hasMore)
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
     */
    fun applyBlockRules() {
        val filtered = BlockRuleStore.filterBooks(getApplication(), allBooks.toList(), sourceUrl)
        books = linkedSetOf<SearchBook>().apply { addAll(filtered) }
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