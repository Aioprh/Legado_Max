package io.legado.app.ui.dict.rule

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.utils.GSON
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment

/**
 * 字典规则内容查询界面，用于按规则字段或完整 JSON 搜索字典规则。
 */
class DictRuleContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<DictRuleContentSearchViewModel>()

    private var allRules: List<DictRule> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "rule" to "规则"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "enabled" to "启用状态",
                "sortNumber" to "排序"
            ),
            "rule" to listOf(
                "urlRule" to "URL规则",
                "showRule" to "显示规则"
            )
        )
    }

    override fun getDialogTitle() = "字典规则内容查询"

    override fun getSearchHint() = "输入关键词搜索字典规则"

    override fun getContentSearchType() = ContentSearchType.DICT_RULE

    override fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit) {
        viewModel.loadRules(allSources) { rules ->
            allRules = rules
            cachedJsonStrings = rules.associate { it.name to GSON.toJson(it) }
            val items = mutableListOf<SourceFieldItem>()
            for (rule in rules) {
                val ruleName = rule.name.ifBlank { "未命名" }
                for ((tabKey, fields) in TAB_FIELDS) {
                    for ((fieldKey, fieldName) in fields) {
                        val value = getFieldValue(rule, fieldKey) ?: continue
                        if (value.isNotBlank()) {
                            items.add(
                                SourceFieldItem(
                                    sourceName = ruleName,
                                    sourceUrl = rule.name,
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
                    val json = cachedJsonStrings[rule.name] ?: return@mapNotNull null
                    JsonSearchItem(
                        sourceName = rule.name.ifBlank { "未命名" },
                        sourceUrl = rule.name,
                        json = json
                    )
                }
            )
        }
    }

    override fun navigateToEdit(sourceUrl: String) {
        showDialogFragment(DictRuleEditDialog(sourceUrl))
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        viewModel.exportRules(sourceUrls) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(rule: DictRule, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> rule.name
            "enabled" -> if (rule.enabled) "启用" else "禁用"
            "sortNumber" -> rule.sortNumber.toString()
            "urlRule" -> rule.urlRule
            "showRule" -> rule.showRule
            else -> null
        }
    }
}
