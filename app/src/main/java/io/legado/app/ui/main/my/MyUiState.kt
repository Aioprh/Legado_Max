package io.legado.app.ui.main.my

/**
 * 顶部信息卡展示的本地阅读统计。
 */
internal data class MyReadingStats(
    val bookCount: Int = 0,
    val totalReadMillis: Long = 0L,
    val readDays: Int = 0,
    val bookmarkCount: Int = 0
)

/**
 * 「我的」页完整 UI 状态。
 *
 * 统计值来自 Room 的 Flow，设置项来自 AppConfig/WebService，
 * 两者都由 [MyViewModel] 合并后推给 [MyScreen]。
 */
internal data class MyUiState(
    val stats: MyReadingStats = MyReadingStats(),
    val themeMode: Int = 0,
    val webServiceRunning: Boolean = false,
    val webServiceAddress: String = "",
    val showDebugTools: Boolean = false,
    val versionName: String = ""
)
