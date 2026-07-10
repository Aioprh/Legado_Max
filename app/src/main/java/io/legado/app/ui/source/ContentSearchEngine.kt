package io.legado.app.ui.source

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 内容查询通用搜索引擎，用于复用规则字段和完整 JSON 的文本匹配逻辑。
 */
object ContentSearchEngine {

    private const val DEFAULT_CONTEXT_CHARS = 50

    suspend fun searchFields(
        query: String,
        items: List<SourceFieldItem>,
        contextChars: Int = DEFAULT_CONTEXT_CHARS
    ): List<SourceFieldItem> {
        if (query.isEmpty()) return emptyList()
        val queryLower = query.lowercase()
        return items.flatMapMatches(queryLower, query.length, contextChars)
    }

    suspend fun searchJson(
        query: String,
        sourceItems: List<SourceFieldItem>,
        jsonItems: List<JsonSearchItem>,
        contextChars: Int = DEFAULT_CONTEXT_CHARS,
        fieldName: String = "JSON全文"
    ): List<SourceFieldItem> {
        if (query.isEmpty()) return emptyList()
        val queryLower = query.lowercase()
        // JSON 搜索也要受当前分类筛选影响，只查筛选后仍可见的实体。
        val sourceUrls = sourceItems.map { it.sourceUrl }.distinct().toHashSet()
        val results = mutableListOf<SourceFieldItem>()

        for (jsonItem in jsonItems) {
            currentCoroutineContext().ensureActive()
            if (jsonItem.sourceUrl !in sourceUrls) continue

            results.addAll(
                jsonItem.json.findMatchContexts(queryLower, query.length, contextChars) { contextText ->
                    SourceFieldItem(
                        sourceName = jsonItem.sourceName,
                        sourceUrl = jsonItem.sourceUrl,
                        tabKey = "json",
                        tabName = "JSON",
                        fieldKey = "json",
                        fieldName = fieldName,
                        value = contextText,
                        fullValue = jsonItem.json
                    )
                }
            )
        }

        return results
    }

    private suspend fun List<SourceFieldItem>.flatMapMatches(
        queryLower: String,
        queryLen: Int,
        contextChars: Int
    ): List<SourceFieldItem> {
        val results = mutableListOf<SourceFieldItem>()
        for (item in this) {
            currentCoroutineContext().ensureActive()
            results.addAll(
                item.value.findMatchContexts(queryLower, queryLen, contextChars) { contextText ->
                    item.copy(value = contextText)
                }
            )
        }
        return results
    }

    private suspend fun <T> String.findMatchContexts(
        queryLower: String,
        queryLen: Int,
        contextChars: Int,
        buildResult: (String) -> T
    ): List<T> {
        val sourceText = this
        val valueLower = sourceText.lowercase()
        if (!valueLower.contains(queryLower)) return emptyList()

        val results = mutableListOf<T>()
        var startIndex = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val matchIndex = valueLower.indexOf(queryLower, startIndex)
            if (matchIndex == -1) break

            val start = maxOf(0, matchIndex - contextChars)
            val end = minOf(sourceText.length, matchIndex + queryLen + contextChars)
            val contextText = buildString {
                if (start > 0) append("...")
                append(sourceText.substring(start, end))
                if (end < sourceText.length) append("...")
            }

            results.add(buildResult(contextText))
            startIndex = matchIndex + 1
        }
        return results
    }
}

/**
 * 可搜索实体的完整 JSON 内容描述。
 */
data class JsonSearchItem(
    val sourceName: String,
    val sourceUrl: String,
    val json: String
)
