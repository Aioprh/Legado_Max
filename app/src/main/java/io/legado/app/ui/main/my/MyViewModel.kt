package io.legado.app.ui.main.my

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.service.WebService
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「我的」页 ViewModel。
 *
 * - 本地阅读统计：书籍数 / 累计阅读时长 / 阅读天数 / 书签数，全部来自 Room Flow，实时刷新
 * - 设置项快照：主题模式、Web 服务状态、调试模式开关
 */
internal class MyViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    private var statsJob: Job? = null

    /**
     * 刷新不需要数据库订阅的设置项状态。
     * 页面每次可见、Web 服务状态变化、调试模式切换后调用。
     */
    fun refreshPreferences() {
        _uiState.update {
            it.copy(
                themeMode = AppConfig.themeMode?.toIntOrNull() ?: 0,
                webServiceRunning = WebService.isRun,
                webServiceAddress = WebService.hostAddress,
                showDebugTools = AppConfig.debugMode,
                versionName = AppConst.appInfo.versionName
            )
        }
    }

    /**
     * 开关切换后立即回显，等服务启动/停止的 [io.legado.app.constant.EventBus.WEB_SERVICE]
     * 事件回来再由 [refreshPreferences] 校正真实状态。
     */
    fun optimisticWebServiceState(running: Boolean) {
        _uiState.update { it.copy(webServiceRunning = running) }
    }

    /**
     * 订阅本地阅读统计。重复调用是安全的，只用一份订阅。
     */
    fun observeStats() {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            combine(
                appDb.bookDao.flowBookCount(),
                appDb.readRecordDao.getCalculatedTotalReadTime(),
                appDb.readRecordDao.getReadDaysFlow(),
                appDb.bookmarkDao.flowAll()
            ) { bookCount, totalReadTime, readDays, bookmarks ->
                MyReadingStats(
                    bookCount = bookCount,
                    totalReadMillis = totalReadTime,
                    readDays = readDays,
                    bookmarkCount = bookmarks.size
                )
            }.catch {
                LogUtils.e(TAG, "load reading stats failed: ${it.message}")
            }.collect { stats ->
                _uiState.update { it.copy(stats = stats) }
            }
        }
    }

    private companion object {
        const val TAG = "MyViewModel"
    }
}
