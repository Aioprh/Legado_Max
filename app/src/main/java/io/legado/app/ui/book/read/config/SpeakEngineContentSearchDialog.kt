package io.legado.app.ui.book.read.config

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.utils.GSON
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment

/**
 * 朗读引擎内容查询界面，用于按规则字段或完整 JSON 搜索 HTTP TTS 配置。
 */
class SpeakEngineContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<SpeakEngineContentSearchViewModel>()

    private var allEngines: List<HttpTTS> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "request" to "请求",
            "login" to "登录",
            "script" to "脚本"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "id" to "ID",
                "lastUpdateTime" to "更新时间"
            ),
            "request" to listOf(
                "url" to "URL",
                "contentType" to "Content-Type",
                "header" to "请求头",
                "concurrentRate" to "并发率",
                "enabledCookieJar" to "CookieJar"
            ),
            "login" to listOf(
                "loginUrl" to "登录URL",
                "loginUi" to "登录UI",
                "loginCheckJs" to "登录检查JS"
            ),
            "script" to listOf(
                "jsLib" to "JS库"
            )
        )
    }

    override fun getDialogTitle() = "朗读引擎规则内容查询"

    override fun getSearchHint() = "输入关键词搜索朗读引擎规则"

    override fun getContentSearchType() = ContentSearchType.SPEAK_ENGINE

    override fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit) {
        viewModel.loadEngines { engines ->
            allEngines = engines
            cachedJsonStrings = engines.associate { it.id.toString() to GSON.toJson(it) }
            val items = mutableListOf<SourceFieldItem>()
            for (engine in engines) {
                val engineId = engine.id.toString()
                val engineName = engine.name.ifBlank { "未命名($engineId)" }
                for ((tabKey, fields) in TAB_FIELDS) {
                    for ((fieldKey, fieldName) in fields) {
                        val value = getFieldValue(engine, fieldKey) ?: continue
                        if (value.isNotBlank()) {
                            items.add(
                                SourceFieldItem(
                                    sourceName = engineName,
                                    sourceUrl = engineId,
                                    tabKey = tabKey,
                                    tabName = TAB_NAMES[tabKey] ?: tabKey,
                                    fieldKey = fieldKey,
                                    fieldName = fieldName,
                                    value = value
                                )
                            )
                        }
                    }
                }
            }
            callback(items)
        }
    }

    override suspend fun performSearch(
        query: String,
        allItems: List<SourceFieldItem>
    ): List<SourceFieldItem> {
        return if (searchByRuleField) {
            ContentSearchEngine.searchFields(query, allItems)
        } else {
            ContentSearchEngine.searchJson(
                query = query,
                sourceItems = allItems,
                jsonItems = allEngines.mapNotNull { engine ->
                    val engineId = engine.id.toString()
                    val json = cachedJsonStrings[engineId] ?: return@mapNotNull null
                    JsonSearchItem(
                        sourceName = engine.name.ifBlank { "未命名($engineId)" },
                        sourceUrl = engineId,
                        json = json
                    )
                }
            )
        }
    }

    override fun navigateToEdit(sourceUrl: String) {
        sourceUrl.toLongOrNull()?.let {
            showDialogFragment(HttpTtsEditDialog(it))
        }
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        val engineIds = sourceUrls.mapNotNull { it.toLongOrNull() }
        viewModel.exportEngines(engineIds) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(engine: HttpTTS, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> engine.name
            "id" -> engine.id.toString()
            "lastUpdateTime" -> engine.lastUpdateTime.toString()
            "url" -> engine.url
            "contentType" -> engine.contentType
            "header" -> engine.header
            "concurrentRate" -> engine.concurrentRate
            "enabledCookieJar" -> engine.enabledCookieJar?.let { if (it) "启用" else "禁用" }
            "loginUrl" -> engine.loginUrl
            "loginUi" -> engine.loginUi
            "loginCheckJs" -> engine.loginCheckJs
            "jsLib" -> engine.jsLib
            else -> null
        }
    }
}
