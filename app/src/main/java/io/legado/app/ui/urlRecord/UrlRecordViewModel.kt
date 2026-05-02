package io.legado.app.ui.urlRecord

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.constant.PreferKey
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * URL访问记录界面的ViewModel
 * 
 * 负责管理URL记录数据和配置状态。
 * 提供数据加载、清除记录、开关控制等功能。
 */
class UrlRecordViewModel(application: Application) : BaseViewModel(application) {

    // 所有URL记录列表
    val allRecords = appDb.urlRecordDao.getAll()

    /**
     * 清除所有URL记录
     */
    fun clearAll() {
        execute {
            appDb.urlRecordDao.deleteAll()
        }
    }

    /**
     * 删除指定天数之前的旧记录
     * @param days 要删除的记录天数阈值
     */
    fun deleteOldRecords(days: Int) {
        execute {
            val timestamp = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            appDb.urlRecordDao.deleteOldRecords(timestamp)
        }
    }

    /**
     * 设置URL记录开关状态
     * @param enabled 是否启用URL记录
     */
    fun setRecordUrl(enabled: Boolean) {
        AppConfig.recordUrl = enabled
        appCtx.putPrefBoolean(PreferKey.recordUrl, enabled)
    }

    /**
     * 获取URL记录开关状态
     * @return 是否启用URL记录
     */
    fun isRecordUrlEnabled(): Boolean = AppConfig.recordUrl
}
