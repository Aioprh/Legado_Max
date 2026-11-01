package io.legado.app.ui.main.my

/**
 * 「我的」页可点击的入口。
 *
 * UI 层只负责发出语义化的入口标识，具体的 Activity 跳转与参数拼装由
 * [MyFragment] 统一处理，避免 Compose 组件直接依赖业务 Activity。
 */
internal enum class MyEntry {
    BOOK_SOURCE,
    TXT_TOC_RULE,
    REPLACE_RULE,
    DICT_RULE,
    BOOKMARK,
    READ_RECORD,
    URL_RECORD,
    FILE_MANAGE,
    STORAGE_MANAGE,
    DOWNLOAD_MANAGE,
    PRECISE_MANAGE,
    DEBUG_TOOLS,
    THEME_SETTING,
    BACKUP_RESTORE,
    OTHER_SETTING,
    ABOUT,
    EXIT
}
