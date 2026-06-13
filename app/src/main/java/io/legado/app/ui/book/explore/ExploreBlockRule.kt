package io.legado.app.ui.book.explore

import io.legado.app.data.entities.SearchBook

/**
 * 发现界面屏蔽规则数据模型
 *
 * 用于屏蔽发现列表中匹配关键词或正则表达式的书籍。
 * 作用范围使用位掩码支持多选（标题/作者/标签/简介），
 * 书源作用域支持指定生效和排除的书源URL。
 *
 * 存储方式：SharedPreferences + JSON 序列化，由 [ExploreBlockRuleStore] 管理
 */
data class ExploreBlockRule(
    /** 唯一标识，默认使用时间戳 */
    var id: String = System.currentTimeMillis().toString(),
    /** 规则名称 */
    var name: String = "",
    /** 匹配模式：关键词或正则表达式 */
    var pattern: String = "",
    /** 是否启用正则匹配，false=关键词匹配 */
    var isRegex: Boolean = false,
    /** 所属分组 */
    var group: String = ExploreBlockRuleGroupStore.DEFAULT_GROUP,
    /** 作用范围（位掩码），默认0表示未选择，需至少选一个才能保存 */
    var targetScope: Int = 0,
    /** 是否启用该规则 */
    var enabled: Boolean = true,
    /** 作用的书源，书源URL分号分隔，为空则对所有书源生效 */
    var scope: String? = null,
    /** 排除的书源，书源URL分号分隔，匹配的书源不应用该规则 */
    var excludeScope: String? = null,
) {

    /** 检查是否包含指定的作用范围标志 */
    fun hasScope(flag: Int): Boolean = (targetScope and flag) != 0

    /**
     * 判断书籍是否匹配该屏蔽规则
     * 根据作用范围（位掩码）选择匹配字段，对选中字段逐一匹配
     */
    fun matches(book: SearchBook): Boolean {
        val searchTargets = mutableListOf<String>()
        if (hasScope(SCOPE_TITLE)) searchTargets.add(book.name)
        if (hasScope(SCOPE_AUTHOR)) searchTargets.add(book.author)
        if (hasScope(SCOPE_TAG)) searchTargets.add(book.kind.orEmpty())
        if (hasScope(SCOPE_INTRO)) searchTargets.add(book.intro.orEmpty())
        if (searchTargets.isEmpty()) return false
        return searchTargets.any { text ->
            if (isRegex) {
                runCatching { Regex(pattern).containsMatchIn(text) }.getOrDefault(false)
            } else {
                text.contains(pattern)
            }
        }
    }

    /**
     * 判断规则是否对指定书源生效
     * - scope 为空时，默认对所有书源生效（仍会检查 excludeScope）
     * - scope 非空时，仅对匹配书源URL的书源生效
     * - excludeScope 非空时，匹配的书源会被排除
     */
    fun matchesScope(sourceUrl: String): Boolean {
        val scopeVal = scope
        if (!scopeVal.isNullOrBlank()) {
            val items = scopeVal.split(";").map { it.trim() }.filter { it.isNotBlank() }
            if (!items.any { sourceUrl.contains(it) }) return false
        }
        val excludeVal = excludeScope
        if (!excludeVal.isNullOrBlank()) {
            val items = excludeVal.split(";").map { it.trim() }.filter { it.isNotBlank() }
            if (items.any { sourceUrl.contains(it) }) return false
        }
        return true
    }

    /** 返回作用范围摘要文本，如"标题, 作者" */
    fun scopeSummary(): String {
        val parts = mutableListOf<String>()
        if (hasScope(SCOPE_TITLE)) parts.add("标题")
        if (hasScope(SCOPE_AUTHOR)) parts.add("作者")
        if (hasScope(SCOPE_TAG)) parts.add("标签")
        if (hasScope(SCOPE_INTRO)) parts.add("简介")
        return if (parts.isEmpty()) "无" else parts.joinToString(", ")
    }

    /** 返回匹配模式标签："关键词" 或 "正则" */
    fun modeLabel(): String {
        return if (isRegex) "正则" else "关键词"
    }

    /** 复制规则并生成新的ID，用于导入时避免ID冲突 */
    fun copyWithNewId(): ExploreBlockRule {
        return copy(id = "${System.currentTimeMillis()}_${name.hashCode()}")
    }

    companion object {
        const val SCOPE_TITLE = 1    // 0b0001 作用范围：标题
        const val SCOPE_AUTHOR = 2   // 0b0010 作用范围：作者
        const val SCOPE_TAG = 4      // 0b0100 作用范围：标签
        const val SCOPE_INTRO = 8    // 0b1000 作用范围：简介
        const val SCOPE_ALL = 15     // 0b1111 作用范围：全部
    }
}
