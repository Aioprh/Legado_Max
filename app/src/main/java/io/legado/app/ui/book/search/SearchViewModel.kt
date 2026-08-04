package io.legado.app.ui.book.search

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.config.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.model.webBook.SearchModel
import io.legado.app.model.webBook.SourceSearchRecord
import io.legado.app.utils.ConflateLiveData
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : BaseViewModel(application) {
    val handler = Handler(Looper.getMainLooper())
    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val upAdapterLiveData = MutableLiveData<String>()
    var searchBookLiveData = ConflateLiveData<List<SearchBook>>(1000)
    val searchScope: SearchScope = SearchScope(AppConfig.searchScope)
    var searchFinishLiveData = MutableLiveData<Boolean>()
    var isSearchLiveData = MutableLiveData<Boolean>()
    /** 书源状态记录 LiveData，驱动进度显示和诊断面板 */
    val sourceRecordsLiveData = MutableLiveData<List<SourceSearchRecord>>()
    var searchKey: String = ""
    var hasMore = true
    private var searchID = 0L
    private val searchModel = SearchModel(viewModelScope, object : SearchModel.CallBack {

        override fun getSearchScope(): SearchScope {
            return searchScope
        }

        override fun onSearchStart() {
            isSearchLiveData.postValue(true)
        }

        override fun onSearchSuccess(searchBooks: List<SearchBook>) {
            searchBookLiveData.postValue(searchBooks)
        }

        override fun onSearchProgress(completed: Int, total: Int, resultCount: Int) {
        }

        override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
            this@SearchViewModel.hasMore = hasMore
            isSearchLiveData.postValue(false)
            searchFinishLiveData.postValue(isEmpty)
        }

        override fun onSearchCancel(exception: Throwable?) {
            isSearchLiveData.postValue(false)
            exception?.let {
                context.toastOnUi(it.localizedMessage)
            }
        }

        override fun onSourceStatesChanged(records: List<SourceSearchRecord>) {
            sourceRecordsLiveData.postValue(records)
        }

    })

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
                AppLog.put("搜索界面获取书籍列表失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
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

    fun search(key: String) {
        execute {
            if ((searchKey == key) || key.isNotEmpty()) {
                searchModel.cancelSearch()
                searchID = System.currentTimeMillis()
                searchBookLiveData.postValue(emptyList())
                searchKey = key
                hasMore = true
            }
            if (searchKey.isEmpty()) {
                return@execute
            }
            searchModel.search(searchID, searchKey)
        }
    }

    fun stop() {
        searchModel.cancelSearch()
    }

    fun pause() {
        searchModel.pause()
    }

    fun resume() {
        searchModel.resume()
    }

    fun saveSearchKey(key: String) {
        execute {
            appDb.searchKeywordDao.get(key)?.let {
                it.usage += 1
                it.lastUseTime = System.currentTimeMillis()
                appDb.searchKeywordDao.update(it)
            } ?: appDb.searchKeywordDao.insert(SearchKeyword(key, 1))
        }
    }

    fun clearHistory() {
        execute {
            appDb.searchKeywordDao.deleteAll()
        }
    }

    fun deleteHistory(searchKeyword: SearchKeyword) {
        execute {
            appDb.searchKeywordDao.delete(searchKeyword)
        }
    }

    fun addToBookshelf(book: SearchBook) {
        execute {
            val bookEntity = book.toBook()
            appDb.bookDao.insert(bookEntity)
            val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
            bookshelf.add(key)
            bookshelf.add(book.bookUrl)
            upAdapterLiveData.postValue("isInBookshelf")
        }.onError {
            AppLog.put("加入书架失败", it)
        }
    }

    /**
     * 离线缓存书籍：自动加入书架（若未加入），并启动全书缓存
     */
    fun onCacheBook(book: SearchBook) {
        execute {
            // 1. 确保书籍在书架中（若已在则跳过）
            addToBookshelf(book)

            // 2. 获取 Book 实体（优先使用 bookUrl）
            val bookEntity = appDb.bookDao.getBook(book.bookUrl)
                ?: appDb.bookDao.getBook(book.name, book.author)
            if (bookEntity == null) {
                context.toastOnUi(context.getString(R.string.offline_cache_failed, book.name))
                return@execute
            }

            // 3. 启动全书缓存（从第0章到最后一章，-1 表示全部）
            CacheBook.start(getApplication(), bookEntity, 0, -1)
            context.toastOnUi(context.getString(R.string.offline_cache_start, book.name))
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchModel.close()
    }

}