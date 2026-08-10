package io.legado.app.ui.config.theme.manage

import io.legado.app.help.config.ThemeConfig

/** 主题列表条目。key 只在本次管理会话内标识条目，不依赖可漂移的列表位置。 */
data class ThemeItem(
    val key: String,
    val config: ThemeConfig.Config
)

/** 一次性事件（Toast / Snackbar / 跳转）。 */
sealed class ThemeEvent {
    /** 通过资源 ID 展示提示。 */
    data class Toast(val resId: Int) : ThemeEvent()

    /** 通过文本内容展示提示。 */
    data class ToastMsg(val msg: String) : ThemeEvent()

    /** 主题应用完成后的反馈事件。 */
    data class Applied(val themeName: String) : ThemeEvent()

    /** 剪贴板主题导入成功。 */
    data object ImportSuccess : ThemeEvent()

    /** 剪贴板没有可导入内容。 */
    data object ImportEmpty : ThemeEvent()

    /** 剪贴板内容解析失败。 */
    data object ImportFailed : ThemeEvent()

    /** 请求 Activity 展示删除确认框。 */
    data object DeleteConfirm : ThemeEvent()

    /** 请求外部分享主题 JSON。 */
    data class ShareJson(val json: String) : ThemeEvent()
}
