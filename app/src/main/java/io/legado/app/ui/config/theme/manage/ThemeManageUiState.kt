package io.legado.app.ui.config.theme.manage

import io.legado.app.help.config.ThemeConfig

/**
 * 主题列表条目（携带原始 configList 索引，供 delConfig / toTopConfigs 使用）
 */
data class ThemeItem(
    val config: ThemeConfig.Config,
    val originalIndex: Int
)

/**
 * 一次性事件（Toast / Snackbar / 跳转）
 */
sealed class ThemeEvent {
    data class Toast(val resId: Int) : ThemeEvent()
    data class ToastMsg(val msg: String) : ThemeEvent()
    data class Applied(val themeName: String) : ThemeEvent()
    data object ImportSuccess : ThemeEvent()
    data object ImportEmpty : ThemeEvent()
    data object ImportFailed : ThemeEvent()
    data object DeleteConfirm : ThemeEvent()
    data class ShareJson(val json: String) : ThemeEvent()
}
