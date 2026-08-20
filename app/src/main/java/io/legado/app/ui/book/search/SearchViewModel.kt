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
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.help.config.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.model.webBook.SearchModel
import io.legado.app.model.webBook.SourceSearchRecord
import io.legado.app.utils.ConflateLiveData
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : BaseViewModel(application) {
    val handler = Handler(Looper.getMainLooper())
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
        // 订阅 BookshelfMatcher 的刷新信号，转发为 upAdapterLiveData
        viewModelScope.launch {
            BookshelfMatcher.refreshSignal.collect {
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        return BookshelfMatcher.isInShelf(book.bookUrl, book.name, book.author)
    }

    fun getBookShelfState(book: SearchBook): BookShelfState {
        return BookshelfMatcher.getState(book.name, book.author, book.bookUrl)
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
            // BookshelfMatcher 会通过 flowShelfKeys() 自动感知 DB 变化并刷新
        }.onError {
            AppLog.put("加入书架失败", it)
        }
    }

    /**
     * 离线缓存书籍：自动加入书架（若未加入），并启动全书缓存
     * 直接使用 SearchBook.toBook() 生成的 Book 实体，无需二次查询。
     */
    fun onCacheBook(book: SearchBook) {
        execute {
            // 1. 转换为 Book 实体（包含完整字段：bookUrl、name、author、origin 等）
            val bookEntity = book.toBook()
            // 2. 加入书架（如已存在则替换，不会报错）
            appDb.bookDao.insert(bookEntity)
            // 3. BookshelfMatcher 通过 flowShelfKeys() 自动感知 DB 变化并刷新书架状态
            upAdapterLiveData.postValue("isInBookshelf")
            // 4. 启动全书缓存（0 到 -1 表示全部章节）
            CacheBook.start(getApplication(), bookEntity, 0, -1)
            // 5. 提示成功
            context.toastOnUi(context.getString(R.string.offline_cache_start, book.name))
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchModel.close()
    }

}