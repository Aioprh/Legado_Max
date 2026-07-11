package io.legado.app.ui.book.toc.rule

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.utils.GSON
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment

/**
 * TXT 目录规则内容查询界面，用于按规则字段或完整 JSON 搜索目录识别规则。
 */
class TxtTocRuleContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<TxtTocRuleContentSearchViewModel>()

    private var allRules: List<TxtTocRule> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "rule" to "规则"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "enable" to "启用状态",
                "id" to "ID",
                "serialNumber" to "排序"
            ),
            "rule" to listOf(
                "rule" to "目录正则",
                "replacement" to "替换规则",
                "example" to "示例"
            )
        )
    }

    override fun getDialogTitle() = "TXT目录规则内容查询"

    override fun getSearchHint() = "输入关键词搜索TXT目录规则"

    override fun getContentSearchType() = ContentSearchType.TXT_TOC_RULE

    override fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit) {
        viewModel.loadRules(allSources) { rules ->
            allRules = rules
            cachedJsonStrings = rules.associate { it.id.toString() to GSON.toJson(it) }
            val items = mutableListOf<SourceFieldItem>()
            for (rule in rules) {
                val ruleId = rule.id.toString()
                for ((tabKey, fields) in TAB_FIELDS) {
                    for ((fieldKey, fieldName) in fields) {
                        val value = getFieldValue(rule, fieldKey) ?: continue
                        if (value.isNotBlank()) {
                            items.add(
                                SourceFieldItem(
                                    sourceName = rule.name,
                                    sourceUrl = ruleId,
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
                jsonItems = allRules.mapNotNull { rule ->
                    val ruleId = rule.id.toString()
                    val json = cachedJsonStrings[ruleId] ?: return@mapNotNull null
                    JsonSearchItem(
                        sourceName = rule.name,
                        sourceUrl = ruleId,
                        json = json
                    )
                }
            )
        }
    }

    override fun navigateToEdit(sourceUrl: String, tabKey: String?, fieldKey: String?) {
        sourceUrl.toLongOrNull()?.let {
            showDialogFragment(TxtTocRuleEditDialog(it))
        }
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        val ruleIds = sourceUrls.mapNotNull { it.toLongOrNull() }
        viewModel.exportRules(ruleIds) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(rule: TxtTocRule, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> rule.name
            "enable" -> if (rule.enable) "启用" else "禁用"
            "id" -> rule.id.toString()
            "serialNumber" -> rule.serialNumber.toString()
            "rule" -> rule.rule
            "replacement" -> rule.replacement
            "example" -> rule.example
            else -> null
        }
    }
}
