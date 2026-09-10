package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 复刻自 Rimchars/legado 的发现标签项模型。
 */
data class DiscoverTagItem(
    val kind: ExploreKind,
    val text: String,
    val role: Role,
    val group: String? = null,
) {
    enum class Role {
        UrlTag,
        GlobalSelect,
        Toggle,
        ActionButton,
        ScriptUrl
    }

    val isButton: Boolean
        get() = role == Role.ActionButton || role == Role.ScriptUrl || role == Role.Toggle
}