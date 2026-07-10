package io.legado.app.ui.replace

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.utils.GSON
import io.legado.app.utils.share

/**
 * 替换净化规则内容查询界面，用于按规则字段或完整 JSON 搜索替换规则。
 */
class ReplaceRuleContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<ReplaceRuleContentSearchViewModel>()

    private var allRules: List<ReplaceRule> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "replace" to "替换",
            "scope" to "作用范围",
            "execute" to "执行"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "group" to "分组",
                "isEnabled" to "启用状态",
                "id" to "ID",
                "order" to "排序"
            ),
            "replace" to listOf(
                "pattern" to "替换内容",
                "replacement" to "替换为",
                "isRegex" to "正则"
            ),
            "scope" to listOf(
                "scopeTitle" to "作用于标题",
                "scopeContent" to "作用于正文",
                "scope" to "作用范围",
                "excludeScope" to "排除范围"
            ),
            "execute" to listOf(
                "timeoutMillisecond" to "超时时间"
            )
        )
    }

    override fun getDialogTitle() = "替换净化规则内容查询"

    override fun getSearchHint() = "输入关键词搜索替换净化规则"

    override fun getContentSearchType() = ContentSearchType.REPLACE_RULE

    override fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit) {
        viewModel.loadRules(allSources) { rules ->
            allRules = rules
            cachedJsonStrings = rules.associate { it.id.toString() to GSON.toJson(it) }
            val items = mutableListOf<SourceFieldItem>()
            for (rule in rules) {
                val ruleId = rule.id.toString()
                val ruleName = rule.getDisplayNameGroup().ifBlank { "未命名($ruleId)" }
                for ((tabKey, fields) in TAB_FIELDS) {
                    for ((fieldKey, fieldName) in fields) {
                        val value = getFieldValue(rule, fieldKey) ?: continue
                        if (value.isNotBlank()) {
                            items.add(
                                SourceFieldItem(
                                    sourceName = ruleName,
                                    sourceUrl = ruleId,
                                    tabKey = tabKey,
                                    tabName = TAB_NAMES[tabKey] ?: tabKey,
                                    fieldKey = fieldKey,
                                    fieldName = fieldName,
                                    value = value,
                                    sourceGroup = rule.group
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
                        sourceName = rule.getDisplayNameGroup().ifBlank { "未命名($ruleId)" },
                        sourceUrl = ruleId,
                        json = json
                    )
                }
            )
        }
    }

    override fun navigateToEdit(sourceUrl: String) {
        sourceUrl.toLongOrNull()?.let {
            startActivity(ReplaceEditActivity.startIntent(requireContext(), it))
        }
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        val ruleIds = sourceUrls.mapNotNull { it.toLongOrNull() }
        viewModel.exportRules(ruleIds) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(rule: ReplaceRule, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> rule.name
            "group" -> rule.group
            "isEnabled" -> if (rule.isEnabled) "启用" else "禁用"
            "id" -> rule.id.toString()
            "order" -> rule.order.toString()
            "pattern" -> rule.pattern
            "replacement" -> rule.replacement
            "isRegex" -> if (rule.isRegex) "正则" else "文本"
            "scopeTitle" -> if (rule.scopeTitle) "是" else "否"
            "scopeContent" -> if (rule.scopeContent) "是" else "否"
            "scope" -> rule.scope
            "excludeScope" -> rule.excludeScope
            "timeoutMillisecond" -> rule.timeoutMillisecond.toString()
            else -> null
        }
    }
}
