package io.legado.app.help.book

import java.util.Locale

/**
 * 标签字符串解析/拼接/查询工具。
 *
 * 标签存储在 [io.legado.app.data.entities.Book.customTag] 字段中，
 * 以逗号分隔的纯文本形式持久化。本类负责在内存表示（[List<String>]）与存储格式之间转换。
 */
object BookTagHelper {

    private val splitter = Regex("[,，;；、|/\\s]+")

    /**
     * 将存储格式的标签字符串解析为去重后的列表。
     *
     * @param raw 原始字符串，可为 null 或空白
     * @return 去重后的标签列表，大小写不敏感去重但保留原始大小写
     */
    fun parse(raw: String?): List<String> {
        return raw.orEmpty()
            .split(splitter)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }

    /**
     * 将标签列表拼接为存储格式。
     *
     * @param tags 标签集合
     * @return 逗号分隔的字符串，全部为空时返回 null
     */
    fun join(tags: Collection<String>): String? {
        return tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .joinToString(",")
            .ifBlank { null }
    }

    /**
     * 判断标签字符串中是否包含指定标签（大小写不敏感）。
     *
     * @param raw 原始标签字符串
     * @param tag 要查找的标签
     * @return 是否包含
     */
    fun has(raw: String?, tag: String): Boolean {
        return parse(raw).any { it.equals(tag.trim(), ignoreCase = true) }
    }
}
