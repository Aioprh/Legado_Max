package io.legado.app.help.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import java.time.Instant

data class AppReleaseInfo(
    val appVariant: AppVariant,
    val createdAt: Long,
    val note: String,
    val name: String,
    val downloadUrl: String,
    val assetUrl: String
) {
    val versionName: String by lazy {
        val withoutApk = name.removeSuffix(".apk")
        withoutApk.split("_").getOrNull(2) ?: ""
    }
}

enum class AppVariant {
    OFFICIAL,
    BETA_LEGACY,    // 兼容版 - io.legado.app - 可覆盖原版
    BETA_COEXIST,   // 共存版 - io.legado.app.yuedu.a - 测试版的共存包
    BETA_RELEASE,   // 测试版 - io.legado.app.yuedu - 原包名测试版
    UNKNOWN;

    fun isBeta(): Boolean {
        return this == BETA_RELEASE || this == BETA_LEGACY || this == BETA_COEXIST
    }

}

@Keep
data class GithubRelease(
    val assets: List<Asset>?,
    val body: String,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        return assets
            .filter { it.isValid }
            .map { it.assetToAppReleaseInfo(isPreRelease, body) }
    }
}
@Keep
data class GiteeRelease(
    val assets: List<GiteeAsset>?,
    val body: String,
    @SerializedName("prerelease")
    val prerelease: Boolean,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        return assets
            .filter { it.isValid }
            .map { it.assetToAppReleaseInfo(prerelease, body) }
    }
}

@Keep
data class Asset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("content_type")
    val contentType: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("download_count")
    val downloadCount: Int,
    val id: Int,
    val name: String,
    val state: String,
    val url: String
) {
    val isValid: Boolean
        get() = (contentType == "application/vnd.android.package-archive") && (state == "uploaded")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String): AppReleaseInfo {
        val instant = Instant.parse(createdAt)
        val timestamp: Long = instant.toEpochMilli()

        val appVariant = when {
            preRelease && name.contains("releaseS") -> AppVariant.BETA_COEXIST
            preRelease && name.contains("legacy") -> AppVariant.BETA_LEGACY
            preRelease && name.contains("release") -> AppVariant.BETA_RELEASE
            else -> AppVariant.OFFICIAL
        }

        return AppReleaseInfo(appVariant, timestamp, note, name, apkUrl, url)
    }
}

@Keep
data class GiteeAsset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("name")
    val name: String
) {
    val isValid: Boolean
        get() = apkUrl.contains(".apk")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String): AppReleaseInfo {
        val appVariant = when {
            name.contains("正式版") || name.contains("releaseS") -> AppVariant.BETA_COEXIST
            name.contains("兼容版") || name.contains("legacy") -> AppVariant.BETA_LEGACY
            name.contains("测试版") || name.contains("release") -> AppVariant.BETA_RELEASE
            else -> AppVariant.OFFICIAL
        }

        return AppReleaseInfo(appVariant, 0, note, name, apkUrl, "")
    }
}
