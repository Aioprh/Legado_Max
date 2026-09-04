package io.legado.app.help.book

import android.content.Context
import io.legado.app.data.entities.Book

/** Smart Tag 2.0 的组合筛选：AND / OR / NOT。 */
object SmartTagFilter {
    enum class Mode { ALL, ANY }

    data class Query(
        val include: Set<String> = emptySet(),
        val exclude: Set<String> = emptySet(),
        val mode: Mode = Mode.ALL
    ) {
        val isEmpty: Boolean get() = include.isEmpty() && exclude.isEmpty()
    }

    fun matches(book: Book, context: Context, query: Query, maxTags: Int = 12): Boolean {
        if (query.isEmpty) return true
        val tags = SmartTag.names(book, context, maxTags).toSet()
        val included = when (query.mode) {
            Mode.ALL -> query.include.all(tags::contains)
            Mode.ANY -> query.include.isEmpty() || query.include.any(tags::contains)
        }
        return included && query.exclude.none(tags::contains)
    }

    fun filter(books: List<Book>, context: Context, query: Query, maxTags: Int = 12): List<Book> =
        if (query.isEmpty) books else books.filter { matches(it, context, query, maxTags) }

    fun describe(query: Query): String {
        if (query.isEmpty) return "全部"
        val include = query.include.joinToString(if (query.mode == Mode.ALL) " + " else " / ")
        val exclude = query.exclude.joinToString("、")
        return buildString {
            append(include)
            if (exclude.isNotEmpty()) append(" · 排除：").append(exclude)
        }
    }
}
