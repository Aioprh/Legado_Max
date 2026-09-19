package io.legado.app.ui.main.homepage

import io.legado.app.data.entities.SearchBook
import io.legado.app.utils.GSON

data class HomepageSourceQuery(
    val sourceUrl: String,
    val url: String? = null,
    val title: String? = null,
    val args: String? = null,
    val weight: Int = 0,
    val enabled: Boolean = true,
)

data class HomepageAggregateConfig(
    val queries: List<HomepageSourceQuery>,
    val limit: Int = 0,
    val parallelism: Int = 4,
)

object HomepageAggregation {

    @Suppress("UNCHECKED_CAST")
    fun parse(args: String?): HomepageAggregateConfig {
        if (args.isNullOrBlank() || !args.trimStart().startsWith("{")) {
            return HomepageAggregateConfig(emptyList())
        }
        return runCatching {
            val root = GSON.fromJson(args, Map::class.java) as? Map<String, Any?>
                ?: return@runCatching HomepageAggregateConfig(emptyList())
            val rawSources = root["sources"] as? List<*>
                ?: return@runCatching HomepageAggregateConfig(emptyList())
            val queries = rawSources.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val sourceUrl = (map["sourceUrl"] ?: map["source"])?.toString()?.trim().orEmpty()
                if (sourceUrl.isBlank()) return@mapNotNull null
                HomepageSourceQuery(
                    sourceUrl = sourceUrl,
                    url = map["url"]?.toString()?.takeIf { it.isNotBlank() },
                    title = map["title"]?.toString()?.takeIf { it.isNotBlank() },
                    args = map["args"]?.toString()?.takeIf { it.isNotBlank() },
                    weight = map["weight"]?.toString()?.toIntOrNull() ?: 0,
                    enabled = map["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true,
                )
            }
                .filter { it.enabled }
                .sortedByDescending { it.weight }
            val limit = (root["limit"]?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val parallelism = (root["parallelism"]?.toString()?.toIntOrNull() ?: 4).coerceIn(1, 8)
            HomepageAggregateConfig(queries, limit, parallelism)
        }.getOrDefault(HomepageAggregateConfig(emptyList()))
    }

    /**
     * 聚合结果按源优先级稳定合并，并做两级去重：
     * 1. URL 去重；
     * 2. 名称 + 作者归一化去重，避免不同源同一本书重复占位。
     */
    fun merge(groups: List<List<SearchBook>>, limit: Int = 0): List<SearchBook> {
        val seenUrls = HashSet<String>()
        val seenBooks = HashSet<String>()
        val merged = ArrayList<SearchBook>()
        groups.asSequence().flatten().forEach { book ->
            val url = book.bookUrl.trim()
            val urlKey = url.lowercase()
            val bookKey = normalize(book.name) + "::" + normalize(book.author)
            if ((urlKey.isBlank() || seenUrls.add(urlKey)) && seenBooks.add(bookKey)) {
                merged += book
            }
        }
        return if (limit > 0) merged.take(limit) else merged
    }

    private fun normalize(value: String?): String =
        value.orEmpty()
            .lowercase()
            .replace(Regex("[\\s\\p{Punct}\\p{IsPunctuation}]+"), "")

    @Suppress("UNCHECKED_CAST")
    fun cacheTtlMillis(layoutConfig: String?): Long {
        if (layoutConfig.isNullOrBlank()) return 120_000L
        return runCatching {
            val map = GSON.fromJson(layoutConfig, Map::class.java) as? Map<*, *>
                ?: return@runCatching 120_000L
            val seconds = map["cacheSeconds"]?.toString()?.toLongOrNull() ?: 120L
            seconds.coerceIn(0L, 86_400L) * 1000L
        }.getOrDefault(120_000L)
    }
}
