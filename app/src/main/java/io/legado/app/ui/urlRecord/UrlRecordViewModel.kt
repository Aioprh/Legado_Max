package io.legado.app.ui.urlRecord

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.constant.PreferKey
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

class UrlRecordViewModel(application: Application) : BaseViewModel(application) {

    val allRecords = appDb.urlRecordDao.getAll()

    fun clearAll() {
        execute {
            appDb.urlRecordDao.deleteAll()
        }
    }

    fun deleteOldRecords(days: Int) {
        execute {
            val timestamp = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            appDb.urlRecordDao.deleteOldRecords(timestamp)
        }
    }

    fun setRecordUrl(enabled: Boolean) {
        AppConfig.recordUrl = enabled
        appCtx.putPrefBoolean(PreferKey.recordUrl, enabled)
    }

    fun isRecordUrlEnabled(): Boolean = AppConfig.recordUrl
}
