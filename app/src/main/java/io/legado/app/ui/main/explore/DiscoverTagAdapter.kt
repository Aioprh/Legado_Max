package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 现代发现页统一的标签模型。
 *
 * 保留 ExploreKind，同时区分普通分类、全局选择、开关和脚本按钮，
 * 后续各发现组件可以共享同一套交互语义。
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
        ScriptUrl,
    }

    val isButton: Boolean
        get() = role == Role.ActionButton || role == Role.ScriptUrl || role == Role.Toggle
}
