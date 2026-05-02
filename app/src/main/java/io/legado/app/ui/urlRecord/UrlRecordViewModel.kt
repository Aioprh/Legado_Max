package io.legado.app.ui.urlRecord

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.constant.PreferKey
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import splitties.init.appCtx

class UrlRecordViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow<UrlRecordUIState>(UrlRecordUIState.Loading)
    val uiState: StateFlow<UrlRecordUIState> = _uiState.asStateFlow()

    val recordCount = MutableStateFlow(0)

    init {
        observeRecords()
    }

    private fun observeRecords() {
        execute {
            appDb.urlRecordDao.flowAll()
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

    fun clearAll() {
        execute {
            appDb.urlRecordDao.deleteAll()
        }
    }

    fun deleteOldRecords(days: Int) {
        execute {
            val timestamp = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            appDb.urlRecordDao.deleteOldRecords(timestamp)
        }
    }

    fun setRecordUrl(enabled: Boolean) {
        AppConfig.recordUrl = enabled
        appCtx.putPrefBoolean(PreferKey.recordUrl, enabled)
    }

    fun isRecordUrlEnabled(): Boolean = AppConfig.recordUrl
}
