package io.legado.app.help.book

import io.legado.app.data.appDb
import io.legado.app.data.dao.ShelfKey
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 书架匹配器（全局单例）
 *
 * 统一管理书架 key 集合，替代 [HomepageViewModel]、[SearchViewModel]、[ExploreShowViewModel]
 * 各自独立订阅 [appDb.bookDao.flowAll] 的旧模式。
 *
 * 优势：
 * - 轻量查询：只加载 name/author/bookUrl 3 个字段，不加载完整 Book 实体
 * - O(1) 匹配：使用 HashSet 做精确匹配和同名同作者匹配
 * - 全局共享：3 个 ViewModel 共享同一份数据，减少 2/3 的 Room Flow 订阅
 *
 * 使用方式：
 * 1. App 启动时调用 [start]
 * 2. 查询书籍状态调用 [getState]
 * 3. View 体系通过 [refreshSignal] 订阅刷新通知
 * 4. Compose 体系通过 [version] 触发重组
 */
object BookshelfMatcher {

    /** 精确匹配集合：Triple<name, author, bookUrl> */
    private val exactKeys: MutableSet<Triple<String, String, String?>> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** 同名同作者集合：Pair<name, author> */
    private val nameAuthorKeys: MutableSet<Pair<String, String>> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** 刷新信号（供 View 体系的 upAdapterLiveData 转发使用） */
    private val _refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshSignal = _refreshSignal.asSharedFlow()

    /** 版本号（供 Compose 订阅触发重组） */
    private val _version = MutableStateFlow(0L)
    val version = _version.asStateFlow()

    private var initJob: Job? = null

    /**
     * 启动书架键订阅
     *
     * 在 [io.legado.app.App.onCreate] 中调用，全局只执行一次。
     * 通过 [appDb.bookDao.flowShelfKeys] 订阅轻量查询，
     * books 表变化时自动更新内部 HashSet。
     */
    fun start() {
        if (initJob != null) return
        initJob = CoroutineScope(Dispatchers.IO).launch {
            appDb.bookDao.flowShelfKeys().collect { keys ->
                exactKeys.clear()
                nameAuthorKeys.clear()
                keys.forEach { key: ShelfKey ->
                    exactKeys.add(Triple(key.name, key.author, key.bookUrl))
                    nameAuthorKeys.add(key.name to key.author)
                }
                _version.update { it + 1 }
                _refreshSignal.tryEmit(Unit)
            }
        }
    }

    /**
     * 查询书籍在书架中的状态（O(1) 时间复杂度）
     *
     * @param name 书名
     * @param author 作者
     * @param bookUrl 书籍 URL（Primary Key）
     * @return 书架状态：[BookShelfState.IN_SHELF]（bookUrl 精确匹配）、
     *         [BookShelfState.SAME_NAME_AUTHOR]（同名同作者但 URL 不同）、
     *         [BookShelfState.NOT_IN_SHELF]（不在书架）
     */
    fun getState(name: String, author: String, bookUrl: String?): BookShelfState {
        val triple = Triple(name, author, bookUrl)
        return when {
            triple in exactKeys -> BookShelfState.IN_SHELF
            (name to author) in nameAuthorKeys -> BookShelfState.SAME_NAME_AUTHOR
            else -> BookShelfState.NOT_IN_SHELF
        }
    }

    /**
     * 判断书籍是否在书架上（精确匹配 bookUrl）
     *
     * 供 [SearchViewModel.isInBookShelf] / [ExploreShowViewModel.isInBookShelf] 使用
     */
    fun isInShelf(bookUrl: String?, name: String, author: String): Boolean {
        if (bookUrl != null && Triple(name, author, bookUrl) in exactKeys) {
            return true
        }
        return (name to author) in nameAuthorKeys
    }
}
