package io.legado.app.ui.rss.source.manage

import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.utils.startActivity

/**
 * 订阅源内容查询对话窗
 */
class RssSourceContentSearchDialog : BaseContentSearchDialog() {

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基础",
            "start" to "起始",
            "list" to "列表",
            "webview" to "正文"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "sourceUrl" to "源地址",
                "sourceName" to "源名称",
                "sourceGroup" to "源分组",
                "sourceComment" to "源注释",
                "searchUrl" to "搜索地址",
                "sortUrl" to "分类地址",
                "loginUrl" to "登录地址",
                "loginUi" to "登录界面",
                "loginCheckJs" to "登录检查JS",
                "header" to "请求头",
                "variableComment" to "变量说明",
                "concurrentRate" to "并发率",
                "jsLib" to "jsLib"
            ),
            "start" to listOf(
                "startHtml" to "起始页HTML",
                "startStyle" to "起始页样式",
                "startJs" to "起始页JS",
                "preloadJs" to "预加载JS"
            ),
            "list" to listOf(
                "ruleArticles" to "文章列表规则",
                "ruleNextPage" to "下一页规则",
                "ruleTitle" to "标题规则",
                "rulePubDate" to "发布日期规则",
                "ruleDescription" to "描述规则",
                "ruleImage" to "图片规则",
                "ruleLink" to "链接规则"
            ),
            "webview" to listOf(
                "ruleContent" to "正文规则",
                "style" to "正文样式",
                "injectJs" to "注入JS",
                "shouldOverrideUrlLoading" to "URL拦截",
                "contentWhitelist" to "正文白名单",
                "contentBlacklist" to "正文黑名单"
            )
        )
    }

    override fun getDialogTitle() = "订阅源内容查询"

    override fun getSearchHint() = "输入关键词搜索所有订阅源"

    override fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit) {
        // RssSource 字段较少，直接在 IO 线程加载
        val sources = if (allSources) {
            appDb.rssSourceDao.all
        } else {
            appDb.rssSourceDao.all.filter { it.enabled }
        }

        val items = mutableListOf<SourceFieldItem>()
        for (source in sources) {
            for ((tabKey, fields) in TAB_FIELDS) {
                for ((fieldKey, fieldName) in fields) {
                    val value = getFieldValue(source, fieldKey) ?: continue
                    if (value.isNotBlank()) {
                        items.add(SourceFieldItem(
                            sourceName = source.sourceName,
                            sourceUrl = source.sourceUrl,
                            tabKey = tabKey,
                            tabName = TAB_NAMES[tabKey] ?: tabKey,
                            fieldKey = fieldKey,
                            fieldName = fieldName,
                            value = value
                        ))
                    }
                }
            }
        }

        callback(items)
    }

    override fun performSearch(query: String, allItems: List<SourceFieldItem>): List<SourceFieldItem> {
        val queryLower = query.lowercase()
        val contextChars = 50
        val results = mutableListOf<SourceFieldItem>()

        for (item in allItems) {
            if (item.value.lowercase().contains(queryLower)) {
                var startIndex = 0
                val valueLower = item.value.lowercase()
                while (true) {
                    val matchIndex = valueLower.indexOf(queryLower, startIndex)
                    if (matchIndex == -1) break

                    val start = maxOf(0, matchIndex - contextChars)
                    val end = minOf(item.value.length, matchIndex + query.length + contextChars)
                    val contextText = buildString {
                        if (start > 0) append("...")
                        append(item.value.substring(start, end))
                        if (end < item.value.length) append("...")
                    }

                    results.add(item.copy(value = contextText))
                    startIndex = matchIndex + 1
                }
            }
        }

        showResults(results)
        return results
    }

    override fun navigateToEdit(sourceUrl: String) {
        startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    private fun getFieldValue(source: RssSource, fieldKey: String): String? {
        return when (fieldKey) {
            "sourceUrl" -> source.sourceUrl
            "sourceName" -> source.sourceName
            "sourceGroup" -> source.sourceGroup
            "sourceComment" -> source.sourceComment
            "searchUrl" -> source.searchUrl
            "sortUrl" -> source.sortUrl
            "loginUrl" -> source.loginUrl
            "loginUi" -> source.loginUi
            "loginCheckJs" -> source.loginCheckJs
            "header" -> source.header
            "variableComment" -> source.variableComment
            "concurrentRate" -> source.concurrentRate
            "jsLib" -> source.jsLib
            "startHtml" -> source.startHtml
            "startStyle" -> source.startStyle
            "startJs" -> source.startJs
            "preloadJs" -> source.preloadJs
            "ruleArticles" -> source.ruleArticles
            "ruleNextPage" -> source.ruleNextPage
            "ruleTitle" -> source.ruleTitle
            "rulePubDate" -> source.rulePubDate
            "ruleDescription" -> source.ruleDescription
            "ruleImage" -> source.ruleImage
            "ruleLink" -> source.ruleLink
            "ruleContent" -> source.ruleContent
            "style" -> source.style
            "injectJs" -> source.injectJs
            "shouldOverrideUrlLoading" -> source.shouldOverrideUrlLoading
            "contentWhitelist" -> source.contentWhitelist
            "contentBlacklist" -> source.contentBlacklist
            else -> null
        }
    }
}
