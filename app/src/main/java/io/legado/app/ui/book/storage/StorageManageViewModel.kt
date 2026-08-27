package io.legado.app.ui.book.storage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.legado.app.help.storage.CacheDetail
import io.legado.app.utils.ConvertUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ### 业务层
// 2. StorageManageViewModel.kt

// - 作用 ：ViewModel，管理UI状态和业务逻辑
// - 主要功能 ：
//   - 使用 StateFlow 管理缓存列表、总大小、UI状态
//   - loadCacheInfo() - 加载所有缓存信息
//   - toggleExpand() - 展开/折叠缓存详情
//   - clearCache() - 清理单个缓存类型
//   - clearAllCache() - 一键清理所有缓存

enum class CacheType {
    BOOK_CACHE,
    EPUB_CACHE,
    TEMP_CACHE,
    TTS_CACHE,
    ACACHE_DISK,
    DB_CACHE,
    LOG_CACHE,
    WEBVIEW_CACHE
}

data class CacheItem(
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val formattedSize: String,
    val path: String? = null,
    val icon: ImageVector,
    val iconColor: Long,
    val canExpand: Boolean = false,
    val expandBadge: String? = null,
    val details: List<CacheDetail>? = null,
    val isExpanded: Boolean = false
)

sealed class StorageUiState {
    object Idle : StorageUiState()
    object Loading : StorageUiState()
    data class Clearing(val target: String) : StorageUiState()
    data class Error(val message: String) : StorageUiState()
}

/**
 * 存储管理清理确认 Dialog 状态
 * （state-events.md §4.5：Dialog 由 ViewModel 状态条件渲染）
 */
sealed interface StorageDialogState {
    /** 清理指定缓存项（可精确到某本书/条目） */
    data class ClearConfirm(val cacheType: CacheType, val detailId: String?) : StorageDialogState

    /** 一键清理全部缓存 */
    data object ClearAll : StorageDialogState
}

/**
 * 存储管理 ViewModel
 * 数据源经 [StorageDataProvider] 构造注入（默认 Default 实现），JVM 单测可直接 Fake（testing.md §16）
 */
class StorageManageViewModel(
    private val dataProvider: StorageDataProvider = StorageDataProvider.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow<StorageUiState>(StorageUiState.Loading)
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    private val _cacheItems = MutableStateFlow<List<CacheItem>>(emptyList())
    val cacheItems: StateFlow<List<CacheItem>> = _cacheItems.asStateFlow()

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize.asStateFlow()

    /** 清理确认 Dialog 状态（§4.5） */
    private val _dialog = MutableStateFlow<StorageDialogState?>(null)
    val dialog: StateFlow<StorageDialogState?> = _dialog.asStateFlow()

    /** 请求清理指定缓存项：弹出确认 Dialog */
    fun requestClear(cacheType: CacheType, detailId: String? = null) {
        _dialog.value = StorageDialogState.ClearConfirm(cacheType, detailId)
    }

    /** 请求一键清理：弹出确认 Dialog */
    fun requestClearAll() {
        _dialog.value = StorageDialogState.ClearAll
    }

    /** 关闭当前 Dialog */
    fun dismissDialog() {
        _dialog.value = null
    }

    init {
        loadCacheInfo()
    }

    /**
     * 加载所有缓存信息
     * 使用async并行计算各类缓存大小，显著提升加载速度
     * 原来串行执行需要等待每个计算完成，现在所有计算同时进行
     */
    fun loadCacheInfo() {
        viewModelScope.launch {
            _uiState.value = StorageUiState.Loading
            try {
                // 并行启动所有缓存计算任务（数据源自带调度切换）
                val items = coroutineScope {
                    val bookSize = async { dataProvider.bookCacheSize() }
                    val bookCount = async { dataProvider.cachedBookCount() }
                    val epubSize = async { dataProvider.epubCacheSize() }
                    val tempSize = async { dataProvider.tempCacheSize() }
                    val ttsSize = async { dataProvider.ttsCacheSize() }
                    val ttsCount = async { dataProvider.ttsEngineCount() }
                    val aCacheSize = async { dataProvider.aCacheSize() }
                    val aCacheCount = async { dataProvider.aCacheItemCount() }
                    val dbSize = async { dataProvider.dbCacheSize() }
                    val dbCacheCount = async { dataProvider.dbCacheItemCount() }
                    val logSize = async { dataProvider.logCacheSize() }
                    val webViewSize = async { dataProvider.webViewCacheSize() }
                    val webViewCount = async { dataProvider.webViewCacheDirCount() }

                    mutableListOf(
                        createCacheItem(
                            CacheType.BOOK_CACHE, bookSize.await(), true,
                            dataProvider.bookCountBadge(bookCount.await())
                        ),
                        createCacheItem(CacheType.EPUB_CACHE, epubSize.await(), false, null),
                        createCacheItem(CacheType.TEMP_CACHE, tempSize.await(), false, null),
                        createCacheItem(
                            CacheType.TTS_CACHE, ttsSize.await(), true,
                            dataProvider.engineCountBadge(ttsCount.await())
                        ),
                        createCacheItem(
                            CacheType.ACACHE_DISK, aCacheSize.await(), true,
                            dataProvider.itemCountBadge(aCacheCount.await())
                        ),
                        createCacheItem(
                            CacheType.DB_CACHE, dbSize.await(), true,
                            dataProvider.itemCountBadge(dbCacheCount.await())
                        ),
                        createCacheItem(
                            CacheType.WEBVIEW_CACHE, webViewSize.await(), true,
                            dataProvider.itemCountBadge(webViewCount.await())
                        ),
                        createCacheItem(CacheType.LOG_CACHE, logSize.await(), false, null)
                    )
                }

                _cacheItems.value = items
                _totalSize.value = items.sumOf { it.size }
                _uiState.value = StorageUiState.Idle
            } catch (e: Exception) {
                _uiState.value = StorageUiState.Error(e.message ?: dataProvider.loadFailedMessage())
            }
        }
    }

    fun toggleExpand(cacheType: CacheType) {
        viewModelScope.launch {
            val currentItems = _cacheItems.value.toMutableList()
            val index = currentItems.indexOfFirst { it.id == cacheType.name }
            if (index == -1) return@launch

            val item = currentItems[index]
            if (item.isExpanded) {
                currentItems[index] = item.copy(isExpanded = false)
            } else {
                val details = dataProvider.details(cacheType)
                currentItems[index] = item.copy(
                    isExpanded = true,
                    details = details
                )
            }
            _cacheItems.value = currentItems
        }
    }

    fun clearCache(cacheType: CacheType, detailId: String? = null) {
        viewModelScope.launch {
            val target = detailId ?: dataProvider.cacheName(cacheType)
            _uiState.value = StorageUiState.Clearing(target)
            try {
                dataProvider.clear(cacheType, detailId)
                loadCacheInfo()
            } catch (e: Exception) {
                _uiState.value = StorageUiState.Error(e.message ?: dataProvider.clearFailedMessage())
            }
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            _uiState.value = StorageUiState.Clearing(dataProvider.allCacheLabel())
            try {
                dataProvider.clearAll()
                loadCacheInfo()
            } catch (e: Exception) {
                _uiState.value = StorageUiState.Error(e.message ?: dataProvider.clearFailedMessage())
            }
        }
    }

    private fun createCacheItem(
        type: CacheType,
        size: Long,
        canExpand: Boolean,
        expandBadge: String?
    ): CacheItem {
        return CacheItem(
            id = type.name,
            name = dataProvider.cacheName(type),
            description = dataProvider.cacheDescription(type),
            size = size,
            formattedSize = ConvertUtils.formatFileSize(size),
            path = dataProvider.cachePath(type),
            icon = getCacheIcon(type),
            iconColor = getCacheIconColor(type),
            canExpand = canExpand,
            expandBadge = expandBadge
        )
    }

    fun getCacheName(type: CacheType): String = dataProvider.cacheName(type)

    private fun getCacheIcon(type: CacheType): ImageVector {
        return when (type) {
            CacheType.BOOK_CACHE -> Icons.Filled.Book
            CacheType.EPUB_CACHE -> Icons.Filled.Description
            CacheType.TEMP_CACHE -> Icons.Filled.Folder
            CacheType.TTS_CACHE -> Icons.Filled.Settings
            CacheType.ACACHE_DISK -> Icons.Filled.Save
            CacheType.DB_CACHE -> Icons.Filled.List
            CacheType.LOG_CACHE -> Icons.Filled.Info
            CacheType.WEBVIEW_CACHE -> Icons.Filled.Description
        }
    }

    fun getCacheIconColor(type: CacheType): Long {
        return when (type) {
            CacheType.BOOK_CACHE -> 0xFF3B82F6
            CacheType.EPUB_CACHE -> 0xFF8B5CF6
            CacheType.TEMP_CACHE -> 0xFFF59E0B
            CacheType.TTS_CACHE -> 0xFFEC4899
            CacheType.ACACHE_DISK -> 0xFF10B981
            CacheType.DB_CACHE -> 0xFF6366F1
            CacheType.LOG_CACHE -> 0xFF64748B
            CacheType.WEBVIEW_CACHE -> 0xFF0EA5E9
        }
    }

    companion object {
        /** 默认工厂：生产环境使用 Default 数据源（构造参数有默认值，反射工厂需要显式构造） */
        val Factory = viewModelFactory {
            initializer { StorageManageViewModel() }
        }
    }
}
