package io.legado.app.constant

/**
 * 事件总线常量。
 *
 * 基于 LiveEventBus 的事件标签定义，每个常量对应一个独立的事件通道。
 */
object EventBus {
    const val RECREATE = "RECREATE"
    const val NOTIFY_MAIN = "notifyMain"
    const val WEB_SERVICE = "webService"
    const val NAVIGATION_BAR_CHANGED = "navigationBarChanged"
    const val TOP_BAR_CHANGED = "topBarChanged"
    const val DEBUG_MODE_CHANGED = "debugModeChanged"

    const val UP_BOOKSHELF = "upBookToc"
    const val BOOKSHELF_REFRESH = "bookshelfRefresh"
    const val SMART_TAG_FILTER = "smartTagFilter"
    const val SMART_TAG_CHANGED = "smartTagChanged"
    const val SOURCE_CHANGED = "sourceChanged"
    const val REFRESH_BOOK_INFO = "refreshBookInfo"
    const val REFRESH_BOOK_CONTENT = "refreshBookContent"
    const val REFRESH_BOOK_TOC = "refreshBookToc"
    const val TOC_PARTIAL_LOADED = "tocPartialLoaded"
    const val TOC_LOAD_COMPLETE = "tocLoadComplete"

    const val UP_CONFIG = "upConfig"
    const val UPDATE_READ_ACTION_BAR = "updateReadActionBar"
    const val UP_SEEK_BAR = "upSeekBar"
    const val TIP_COLOR = "tipColor"
    const val UP_MANGA_CONFIG = "upMangaConfig"
    const val MEDIA_BUTTON = "mediaButton"

    const val ALOUD_STATE = "aloud_state"
    const val TTS_PROGRESS = "ttsStart"
    const val READ_ALOUD_DS = "readAloudDs"
    const val READ_ALOUD_PLAY = "readAloudPlay"
    const val SHOW_READ_MENU = "showReadMenu"

    const val AUDIO_DS = "audioDs"
    const val AUDIO_STATE = "audioState"
    const val AUDIO_SUB_TITLE = "audioSubTitle"
    const val AUDIO_PROGRESS = "audioProgress"
    const val AUDIO_BUFFER_PROGRESS = "audioBufferProgress"
    const val AUDIO_SIZE = "audioSize"
    const val AUDIO_SPEED = "audioSpeed"
    const val PLAY_MODE_CHANGED = "playModeChanged"
    const val AUDIO_QUEUE_CHANGED = "audioQueueChanged"
    const val AUDIO_ERROR = "audioError"

    const val VIDEO_SUB_TITLE = "VideoSubTitle"
    const val UP_VIDEO_INFO = "UP_VIDEO_INFO"
    const val VIDEO_CONFIG_CHANGED = "videoConfigChanged"
    const val BATTERY_CHANGED = "batteryChanged"
    const val TIME_CHANGED = "timeChanged"
    const val UP_DOWNLOAD = "upDownload"
    const val UP_DOWNLOAD_STATE = "upDownloadState"
    const val SAVE_CONTENT = "saveContent"
    const val EXPORT_BOOK = "exportBook"
    const val CHECK_SOURCE = "checkSource"
    const val CHECK_SOURCE_RESULT = "checkSourceResult"
    const val CHECK_SOURCE_DONE = "checkSourceDone"
    const val SEARCH_RESULT = "searchResult"
    const val COVER_HTML_TEMPLATE_CHANGED = "coverHtmlTemplateChanged"
}
