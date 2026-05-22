package io.legado.app.ui.book.source.manage

import android.app.Application
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.utils.GSON

class SourceContentSearchViewModel(application: Application) : BaseViewModel(application) {
    /**
     * 书源内容查询对话窗视图模型
     * @param application 应用上下文
     */
    /**
     * 加载所有书源并转为 JSON 字符串列表
     * @param enabledOnly 是否只加载启用的源
     * @return List of (sourceName, sourceUrl, jsonString)
     */
    fun loadSources(enabledOnly: Boolean, callback: (List<Triple<String, String, String>>) -> Unit) {
        execute {
            val sources = if (enabledOnly) {
                appDb.bookSourceDao.getAllSources().filter { it.enabled }
            } else {
                appDb.bookSourceDao.getAllSources()
            }
            sources.map { source ->
                Triple(
                    source.bookSourceName,
                    source.bookSourceUrl,
                    GSON.toJson(source)
                )
            }
        }.onSuccess {
            callback(it ?: emptyList())
        }.onError {
            callback(emptyList())
        }
    }
}
