package io.legado.app.ui.book.cache

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.help.audio.AudioDownloadManager
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.sendValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.collections.set

class CacheViewModel(application: Application) : BaseViewModel(application) {
    val upAdapterLiveData = MutableLiveData<String>()

    private var loadChapterCoroutine: Coroutine<Unit>? = null
    // 缓存每本书已缓存的章节URL集合
    val cacheChapters = hashMapOf<String, HashSet<String>>()
    // 缓存每本书的缓存文件大小
    val cacheSizes = hashMapOf<String, Long>()
    private val bookRepository = BookRepository()

    // 用于检测是否是相同的书籍列表，避免重复加载
    private var lastLoadedBooksKey: String? = null
    // 防止并发加载的标志
    private var isLoading = false

    /**
     * 加载文字缓存和音频缓存统计。
     * 音频缓存使用独立二进制目录，但由这里统一提供给离线缓存 UI。
     */
    fun loadCacheFiles(books: List<Book>, force: Boolean = false) {
        if (isLoading) return

        val booksKey = books.map { it.bookUrl }.sorted().joinToString(",")
        if (!force && booksKey == lastLoadedBooksKey) return

        loadChapterCoroutine?.cancel()
        loadChapterCoroutine = execute {
            isLoading = true
            try {
                val newBooks = books.filter { !it.isLocal && (force || !cacheChapters.contains(it.bookUrl)) }
                if (newBooks.isEmpty()) {
                    lastLoadedBooksKey = booksKey
                    return@execute
                }

                newBooks.map { book ->
                    async(Dispatchers.IO) {
                        try {
                            if (book.isAudio) {
                                val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                                if (chapters.isNotEmpty()) {
                                    book.totalChapterNum = chapters.size
                                }
                                val stats = AudioDownloadManager.getCacheStats(book)
                                val chapterCaches = HashSet<String>(stats.count)
                                repeat(stats.count) { index ->
                                    chapterCaches.add("audio_cache_$index")
                                }
                                Triple(book.bookUrl, chapterCaches, stats.size)
                            } else {
                                val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                                val cacheNames = BookHelp.getCacheFiles(
                                    setOf(book.getFolderName())
                                )[book.getFolderName()] ?: hashSetOf()
                                val chapterCaches = hashSetOf<String>()
                                if (cacheNames.isNotEmpty()) {
                                    book.totalChapterNum = chapters.size
                                    chapters.forEach { chapter ->
                                        if (cacheNames.contains(chapter.getFileName()) || chapter.isVolume) {
                                            chapterCaches.add(chapter.url)
                                        }
                                    }
                                }
                                val cacheSize = File(BookHelp.cachePath, book.getFolderName())
                                    .takeIf { it.exists() }
                                    ?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                                Triple(book.bookUrl, chapterCaches, cacheSize)
                            }
                        } catch (e: Exception) {
                            Triple(book.bookUrl, hashSetOf<String>(), 0L)
                        }
                    }
                }.awaitAll().forEach { (bookUrl, chapterCaches, cacheSize) ->
                    cacheChapters[bookUrl] = chapterCaches
                    cacheSizes[bookUrl] = cacheSize
                    upAdapterLiveData.sendValue(bookUrl)
                }

                lastLoadedBooksKey = booksKey
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun getBookCover(bookName: String, bookAuthor: String): String? {
        return bookRepository.getBookCoverByNameAndAuthor(bookName, bookAuthor)
    }

    fun clearCache(bookUrl: String) {
        cacheChapters[bookUrl] = hashSetOf()
        cacheSizes[bookUrl] = 0L
        lastLoadedBooksKey = null
    }

    fun clearAllCache() {
        cacheChapters.clear()
        cacheSizes.clear()
        lastLoadedBooksKey = null
    }

}