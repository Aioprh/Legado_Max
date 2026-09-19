package io.legado.app.ui.main.homepage

import io.legado.app.data.entities.SearchBook
import io.legado.app.utils.GSON

data class HomepageSourceQuery(
    val sourceUrl: String,
    val url: String? = null,
    val title: String? = null,
    val args: String? = null,
)

data class HomepageAggregateConfig(
    val queries: List<HomepageSourceQuery>,
    val limit: Int = 0,
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
                )
            }
            val limit = (root["limit"]?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            HomepageAggregateConfig(queries, limit)
        }.getOrDefault(HomepageAggregateConfig(emptyList()))
    }

    fun merge(groups: List<List<SearchBook>>, limit: Int = 0): List<SearchBook> {
        val seen = HashSet<String>()
        val merged = groups.asSequence()
            .flatten()
            .filter { seen.add(it.bookUrl.ifBlank { it.name + "::" + it.author }) }
        return if (limit > 0) merged.take(limit).toList() else merged.toList()
    }

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