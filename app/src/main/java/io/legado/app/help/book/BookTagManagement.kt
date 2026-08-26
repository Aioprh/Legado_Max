package io.legado.app.help.book

import java.util.Locale

/**
 * 标签合并、可复用计算、增删逻辑。
 *
 * 标签管理的核心业务逻辑，不持有任何 Android 平台依赖，可独立单元测试。
 */
object BookTagManagement {

    /**
     * 合并两组标签，去重后保持首次出现的顺序。
     *
     * @param configured 配置过的标签（优先级高，排前面）
     * @param existing 现有书籍中实际使用的标签
     * @return 合并并去重后的列表
     */
    fun mergeTags(configured: List<String>, existing: List<String>): List<String> {
        val merged = linkedMapOf<String, String>()
        (configured + existing).forEach { rawTag ->
            val tag = rawTag.trim()
            if (tag.isNotEmpty()) {
                merged.putIfAbsent(tag.lowercase(Locale.ROOT), tag)
            }
        }
        return merged.values.toList()
    }

    /**
     * 计算可复用的标签：所有标签中排除当前分组已有的标签。
     *
     * @param current 当前分组已有的标签
     * @param all 所有分组的全部标签
     * @return 可复用的标签列表
     */
    fun reusableTags(current: List<String>, all: List<String>): List<String> {
        val currentKeys = current.asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        return mergeTags(emptyList(), all).filterNot {
            it.lowercase(Locale.ROOT) in currentKeys
        }
    }

    /**
     * 标签变更操作结果。
     *
     * @param customTag 变更后的标签字符串，为 null 表示清除所有标签
     */
    data class TagWrite(val customTag: String?)

    /**
     * 计算标签增删后的新值。
     *
     * @param customTag 当前标签字符串
     * @param tag 要操作的标签名
     * @param selected true=添加, false=移除
     * @return null 表示无需更新数据库；非 null 表示需要写入 [TagWrite.customTag]
     */
    fun updateTag(customTag: String?, tag: String, selected: Boolean): TagWrite? {
        val tags = BookTagHelper.parse(customTag).toMutableList()
        val hasTag = tags.any { it.equals(tag, ignoreCase = true) }
        if (hasTag == selected) return null
        if (selected) {
            tags.add(tag)
        } else {
            tags.removeAll { it.equals(tag, ignoreCase = true) }
        }
        return TagWrite(BookTagHelper.join(tags))
    }
}
