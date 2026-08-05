package io.legado.app.help.di

/**
 * 应用版本信息（注入示例）
 */
data class AppVersionInfo(
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val debug: Boolean,
    val channel: String
) {
    /** 格式化的版本描述 */
    val displayVersion: String
        get() = "$appName $versionName ($versionCode)${if (debug) " [DEBUG]" else ""}"
}
