package io.legado.app.ui.book.storage

import io.legado.app.R
import io.legado.app.help.storage.CacheDetail
import io.legado.app.help.storage.StorageCalculator
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

/**
 * 存储管理数据源抽象（testing.md §16：静态依赖收敛为构造注入以便 Fake）
 * Default 实现封装 StorageCalculator / appCtx 静态依赖，
 * 阻塞的清理操作统一切 IO 调度器，ViewModel 不再关心线程切换
 */
interface StorageDataProvider {

    // ==================== 缓存大小与计数 ====================

    suspend fun bookCacheSize(): Long
    suspend fun cachedBookCount(): Int
    suspend fun epubCacheSize(): Long
    suspend fun tempCacheSize(): Long
    suspend fun ttsCacheSize(): Long
    suspend fun ttsEngineCount(): Int
    suspend fun aCacheSize(): Long
    suspend fun aCacheItemCount(): Int
    suspend fun dbCacheSize(): Long
    suspend fun dbCacheItemCount(): Int
    suspend fun logCacheSize(): Long
    suspend fun webViewCacheSize(): Long
    suspend fun webViewCacheDirCount(): Int

    // ==================== 明细与清理 ====================

    suspend fun details(type: CacheType): List<CacheDetail>

    suspend fun clear(type: CacheType, detailId: String?)

    suspend fun clearAll()

    // ==================== 文案与路径 ====================

    fun cacheName(type: CacheType): String
    fun cacheDescription(type: CacheType): String
    fun bookCountBadge(count: Int): String
    fun engineCountBadge(count: Int): String
    fun itemCountBadge(count: Int): String
    fun allCacheLabel(): String
    fun loadFailedMessage(): String
    fun clearFailedMessage(): String
    fun cachePath(type: CacheType): String

    companion object Default : StorageDataProvider {

        override suspend fun bookCacheSize(): Long = StorageCalculator.calculateBookCacheSize()
        override suspend fun cachedBookCount(): Int = StorageCalculator.countCachedBooks()
        override suspend fun epubCacheSize(): Long = StorageCalculator.calculateEpubCacheSize()
        override suspend fun tempCacheSize(): Long = StorageCalculator.calculateTempCacheSize()
        override suspend fun ttsCacheSize(): Long = StorageCalculator.calculateTtsCacheSize()
        override suspend fun ttsEngineCount(): Int = StorageCalculator.countTtsEngines()
        override suspend fun aCacheSize(): Long = StorageCalculator.calculateACacheSize()
        override suspend fun aCacheItemCount(): Int = StorageCalculator.countACacheItems()
        override suspend fun dbCacheSize(): Long = StorageCalculator.calculateDbCacheSize()
        override suspend fun dbCacheItemCount(): Int = StorageCalculator.countDbCacheItems()
        override suspend fun logCacheSize(): Long = StorageCalculator.calculateLogCacheSize()
        override suspend fun webViewCacheSize(): Long = StorageCalculator.calculateWebViewCacheSize()
        override suspend fun webViewCacheDirCount(): Int = StorageCalculator.countWebViewCacheDirs()

        override suspend fun details(type: CacheType): List<CacheDetail> = when (type) {
            CacheType.BOOK_CACHE -> StorageCalculator.calculateBookCacheDetails()
            CacheType.TTS_CACHE -> StorageCalculator.calculateTtsCacheDetails()
            CacheType.ACACHE_DISK -> StorageCalculator.calculateACacheDetailsAccurate()
            CacheType.DB_CACHE -> StorageCalculator.calculateDbCacheDetailsAccurate()
            CacheType.WEBVIEW_CACHE -> StorageCalculator.calculateWebViewCacheDetails()
            else -> emptyList()
        }

        override suspend fun clear(type: CacheType, detailId: String?) {
            when (type) {
                CacheType.BOOK_CACHE -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearBookCache(detailId)
                }
                CacheType.EPUB_CACHE -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearEpubCache()
                }
                CacheType.TEMP_CACHE -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearTempCache()
                }
                CacheType.TTS_CACHE -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearTtsCache(detailId)
                }
                CacheType.ACACHE_DISK -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearACacheAccurate(detailId)
                }
                CacheType.DB_CACHE -> StorageCalculator.clearDbCacheByPrefix(detailId)
                CacheType.LOG_CACHE -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearLogCache()
                }
                CacheType.WEBVIEW_CACHE -> withContext(Dispatchers.IO) {
                    StorageCalculator.clearWebViewCache(detailId)
                }
            }
        }

        override suspend fun clearAll() {
            withContext(Dispatchers.IO) {
                StorageCalculator.clearBookCache()
                StorageCalculator.clearEpubCache()
                StorageCalculator.clearTempCache()
                StorageCalculator.clearTtsCache()
                StorageCalculator.clearACache()
                StorageCalculator.clearLogCache()
                StorageCalculator.clearWebViewCache()
            }
            StorageCalculator.clearDbCache()
        }

        override fun cacheName(type: CacheType): String = when (type) {
            CacheType.BOOK_CACHE -> appCtx.getString(R.string.storage_cache_book_name)
            CacheType.EPUB_CACHE -> appCtx.getString(R.string.storage_cache_epub_name)
            CacheType.TEMP_CACHE -> appCtx.getString(R.string.storage_cache_temp_name)
            CacheType.TTS_CACHE -> appCtx.getString(R.string.storage_cache_tts_name)
            CacheType.ACACHE_DISK -> appCtx.getString(R.string.storage_cache_acache_name)
            CacheType.DB_CACHE -> appCtx.getString(R.string.storage_cache_db_name)
            CacheType.LOG_CACHE -> appCtx.getString(R.string.storage_cache_log_name)
            CacheType.WEBVIEW_CACHE -> appCtx.getString(R.string.storage_cache_webview_name)
        }

        override fun cacheDescription(type: CacheType): String = when (type) {
            CacheType.BOOK_CACHE -> appCtx.getString(R.string.storage_cache_book_desc)
            CacheType.EPUB_CACHE -> appCtx.getString(R.string.storage_cache_epub_desc)
            CacheType.TEMP_CACHE -> appCtx.getString(R.string.storage_cache_temp_desc)
            CacheType.TTS_CACHE -> appCtx.getString(R.string.storage_cache_tts_desc)
            CacheType.ACACHE_DISK -> appCtx.getString(R.string.storage_cache_acache_desc)
            CacheType.DB_CACHE -> appCtx.getString(R.string.storage_cache_db_desc)
            CacheType.LOG_CACHE -> appCtx.getString(R.string.storage_cache_log_desc)
            CacheType.WEBVIEW_CACHE -> appCtx.getString(R.string.storage_cache_webview_desc)
        }

        override fun bookCountBadge(count: Int): String =
            appCtx.getString(R.string.storage_cache_count_books, count)

        override fun engineCountBadge(count: Int): String =
            appCtx.getString(R.string.storage_cache_count_engines, count)

        override fun itemCountBadge(count: Int): String =
            appCtx.getString(R.string.storage_cache_count_items, count)

        override fun allCacheLabel(): String = appCtx.getString(R.string.storage_cache_all)

        override fun loadFailedMessage(): String = appCtx.getString(R.string.storage_load_failed)

        override fun clearFailedMessage(): String = appCtx.getString(R.string.storage_clear_failed)

        override fun cachePath(type: CacheType): String = when (type) {
            CacheType.BOOK_CACHE -> appCtx.externalFiles.getFile("book_cache").absolutePath
            CacheType.EPUB_CACHE -> appCtx.externalFiles.getFile("epub").absolutePath
            CacheType.TEMP_CACHE -> appCtx.externalCache.absolutePath
            CacheType.TTS_CACHE -> appCtx.cacheDir.getFile("httpTTS").absolutePath
            CacheType.ACACHE_DISK -> File(appCtx.cacheDir, "ACache").absolutePath
            CacheType.DB_CACHE -> appCtx.getDatabasePath("legado.db").absolutePath
            CacheType.WEBVIEW_CACHE -> listOf(
                appCtx.getDir("webview", android.content.Context.MODE_PRIVATE).absolutePath,
                appCtx.getDir("hws_webview", android.content.Context.MODE_PRIVATE).absolutePath
            ).joinToString("\n")
            CacheType.LOG_CACHE -> appCtx.externalCache.getFile("log").absolutePath
        }
    }
}
