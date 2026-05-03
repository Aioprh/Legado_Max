package io.legado.app.ui.urlRecord

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.constant.PreferKey
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * URL记录界面ViewModel
 * 
 * 负责管理URL记录的数据状态和业务逻辑：
 * - 观察URL记录数据变化
 * - 管理域名列表用于筛选
 * - 处理搜索和筛选逻辑
 * - 执行记录清除操作
 */
class UrlRecordViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow<UrlRecordUIState>(UrlRecordUIState.Loading)
    val uiState: StateFlow<UrlRecordUIState> = _uiState.asStateFlow()

    val recordCount = MutableStateFlow(0)
    
    private val _domains = MutableStateFlow<List<String>>(emptyList())
    val domains: StateFlow<List<String>> = _domains.asStateFlow()

    private val _isRecordEnabled = MutableStateFlow(AppConfig.recordUrl)
    val isRecordEnabled: StateFlow<Boolean> = _isRecordEnabled.asStateFlow()

    private var observeCoroutine: Coroutine<Unit>? = null
    private var domainCoroutine: Coroutine<Unit>? = null
    
    var currentDomain: String? = null
        private set

    init {
        observeRecords()
        observeDomains()
    }

    /**
     * 观察域名列表变化
     * 用于动态更新筛选菜单
     */
    private fun observeDomains() {
        domainCoroutine = execute {
            appDb.urlRecordDao.flowAllDomains()
                .catch { e ->
                    e.printStackTrace()
                }
                .collect { domainList ->
                    _domains.value = domainList
                }
        }
    }

    /**
     * 观察URL记录数据
     * @param searchKey 搜索关键词，为空则不过滤
     * @param domain 域名筛选，为空则不过滤
     */
    fun observeRecords(searchKey: String? = null, domain: String? = null) {
        currentDomain = domain
        observeCoroutine?.cancel()
        observeCoroutine = execute {
            val flow = when {
                !domain.isNullOrEmpty() -> appDb.urlRecordDao.flowByDomain(domain)
                !searchKey.isNullOrEmpty() -> appDb.urlRecordDao.flowSearch(searchKey)
                else -> appDb.urlRecordDao.flowAll()
            }
            
            flow
                .onStart {
                    _uiState.value = UrlRecordUIState.Loading
                }
                .catch { e ->
                    _uiState.value = UrlRecordUIState.Error(e.message ?: "加载失败")
                }
                .collect { records ->
                    recordCount.value = records.size
                    _uiState.value = if (records.isEmpty()) {
                        UrlRecordUIState.Empty
                    } else {
                        UrlRecordUIState.Success(records)
                    }
                }
        }
    }

    /**
     * 按域名筛选记录
     * @param domain 域名，为null则显示全部
     */
    fun filterByDomain(domain: String?) {
        searchViewQuery?.let {
            observeRecords(it, domain)
        } ?: observeRecords(null, domain)
    }

    var searchViewQuery: String? = null
        private set

    /**
     * 设置搜索关键词
     * @param query 搜索关键词
     */
    fun setSearchQuery(query: String?) {
        searchViewQuery = query
        observeRecords(query, currentDomain)
    }

    /**
     * 清除所有URL记录
     * @return 删除的记录数
     */
    suspend fun clearAll(): Int {
        return withContext(Dispatchers.IO) {
            appDb.urlRecordDao.deleteAll()
        }
    }

    /**
     * 删除指定天数之前的记录
     * @param days 天数
     * @return 删除的记录数
     */
    suspend fun deleteOldRecords(days: Int): Int {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            appDb.urlRecordDao.deleteOldRecords(timestamp)
        }
    }

    /**
     * 获取指定天数之前的记录数
     * @param days 天数
     * @return 符合条件的记录数
     */
    suspend fun getOldRecordsCount(days: Int): Int {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            appDb.urlRecordDao.getOldRecordsCount(timestamp)
        }
    }

    /**
     * 设置URL记录开关
     * @param enabled 是否启用
     */
    fun setRecordUrl(enabled: Boolean) {
        AppConfig.recordUrl = enabled
        appCtx.putPrefBoolean(PreferKey.recordUrl, enabled)
        _isRecordEnabled.value = enabled
    }
}
