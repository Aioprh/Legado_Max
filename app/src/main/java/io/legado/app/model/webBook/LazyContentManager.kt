package io.legado.app.model.webBook

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class PageContent(
    val content: String,
    val nextUrl: String?
)

class LazyContentManager(
    private val scope: CoroutineScope,
    private val bookSource: BookSource,
    private val book: Book,
    private val bookChapter: BookChapter,
    private val baseUrl: String,
    private val redirectUrl: String,
    private val initialBody: String,
    private val nextChapterUrl: String?,
    private val nextContentUrlRule: String,
    private val contentRule: String,
    private val webJs: String?
) {
    private val pages = ConcurrentHashMap<Int, PageContent>()
    private val loadingPages = ConcurrentHashMap<Int, AtomicBoolean>()
    
    val totalPages: AtomicInteger = AtomicInteger(-1)
    val currentIndex: AtomicInteger = AtomicInteger(0)
    
    private var prefetchJob: Job? = null
    
    val contentChannel = Channel<PageContent>(Channel.UNLIMITED)
    
    val isCompleted: AtomicBoolean = AtomicBoolean(false)
    
    fun getPage(index: Int): PageContent? {
        return pages[index]
    }
    
    fun isPageLoaded(index: Int): Boolean {
        return pages.containsKey(index)
    }
    
    fun isPageLoading(index: Int): Boolean {
        return loadingPages[index]?.get() == true
    }
    
    fun getAllLoadedContent(): String {
        val sortedPages = pages.keys.sorted()
        return sortedPages.mapNotNull { pages[it]?.content }.joinToString("\n")
    }
    
    suspend fun loadInitialPage(): PageContent {
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(initialBody, baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        
        val content = analyzeRule.getString(contentRule, unescape = false)
        val nextUrl = if (nextContentUrlRule.isNotBlank()) {
            analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
        } else null
        
        val pageContent = PageContent(content, nextUrl)
        pages[0] = pageContent
        currentIndex.set(0)
        contentChannel.trySend(pageContent)
        
        return pageContent
    }
    
    fun prefetchNextPage() {
        val currentIdx = currentIndex.get()
        val nextIdx = currentIdx + 1
        
        if (isCompleted.get()) return
        if (isPageLoaded(nextIdx)) return
        if (isPageLoading(nextIdx)) return
        
        loadingPages[nextIdx] = AtomicBoolean(true)
        
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            try {
                val currentPage = pages[currentIdx] ?: return@launch
                val nextUrl = currentPage.nextUrl
                
                if (nextUrl.isNullOrBlank()) {
                    isCompleted.set(true)
                    totalPages.set(currentIdx + 1)
                    return@launch
                }
                
                if (!nextChapterUrl.isNullOrEmpty() &&
                    NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl) ==
                    NetworkUtils.getAbsoluteURL(redirectUrl, nextChapterUrl)
                ) {
                    isCompleted.set(true)
                    totalPages.set(currentIdx + 1)
                    return@launch
                }
                
                ensureActive()
                
                val analyzeUrl = AnalyzeUrl(
                    mUrl = nextUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = kotlin.coroutines.coroutineContext
                )
                val res = analyzeUrl.getStrResponseAwait(jsStr = webJs)
                
                res.body?.let { body ->
                    val analyzeRule = AnalyzeRule(book, bookSource)
                    analyzeRule.setContent(body, nextUrl)
                    analyzeRule.setRedirectUrl(res.url)
                    
                    val content = analyzeRule.getString(contentRule, unescape = false)
                    val nextNextUrl = if (nextContentUrlRule.isNotBlank()) {
                        analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
                    } else null
                    
                    val pageContent = PageContent(content, nextNextUrl)
                    pages[nextIdx] = pageContent
                    contentChannel.trySend(pageContent)
                }
            } catch (e: Exception) {
                AppLog.put("预加载下一页失败: ${e.localizedMessage}", e)
            } finally {
                loadingPages[nextIdx]?.set(false)
            }
        }
    }
    
    suspend fun loadPage(index: Int): PageContent? {
        if (index < 0) return null
        
        pages[index]?.let { return it }
        
        while (isPageLoading(index)) {
            kotlinx.coroutines.delay(50)
        }
        
        pages[index]?.let { return it }
        
        if (index == 0) {
            return loadInitialPage()
        }
        
        loadingPages[index] = AtomicBoolean(true)
        
        return try {
            var prevPage = pages[index - 1]
            if (prevPage == null) {
                prevPage = loadPage(index - 1) ?: return null
            }
            
            val nextUrl = prevPage.nextUrl
            if (nextUrl.isNullOrBlank()) {
                isCompleted.set(true)
                totalPages.set(index)
                return null
            }
            
            if (!nextChapterUrl.isNullOrEmpty() &&
                NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl) ==
                NetworkUtils.getAbsoluteURL(redirectUrl, nextChapterUrl)
            ) {
                isCompleted.set(true)
                totalPages.set(index)
                return null
            }
            
            val analyzeUrl = AnalyzeUrl(
                mUrl = nextUrl,
                source = bookSource,
                ruleData = book,
                coroutineContext = kotlin.coroutines.coroutineContext
            )
            val res = analyzeUrl.getStrResponseAwait(jsStr = webJs)
            
            res.body?.let { body ->
                val analyzeRule = AnalyzeRule(book, bookSource)
                analyzeRule.setContent(body, nextUrl)
                analyzeRule.setRedirectUrl(res.url)
                
                val content = analyzeRule.getString(contentRule, unescape = false)
                val nextNextUrl = if (nextContentUrlRule.isNotBlank()) {
                    analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
                } else null
                
                val pageContent = PageContent(content, nextNextUrl)
                pages[index] = pageContent
                contentChannel.trySend(pageContent)
                pageContent
            }
        } catch (e: Exception) {
            AppLog.put("加载第${index}页失败: ${e.localizedMessage}", e)
            null
        } finally {
            loadingPages[index]?.set(false)
        }
    }
    
    fun setCurrentIndex(index: Int) {
        currentIndex.set(index)
    }
    
    fun cancel() {
        prefetchJob?.cancel()
        contentChannel.close()
    }
    
    fun hasMorePages(): Boolean {
        if (isCompleted.get()) return false
        val currentIdx = currentIndex.get()
        val currentPage = pages[currentIdx] ?: return false
        return !currentPage.nextUrl.isNullOrBlank()
    }
}
