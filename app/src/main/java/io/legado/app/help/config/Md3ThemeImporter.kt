package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import splitties.init.appCtx
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry

/**
 * 兼容导入 MD3-main 分支导出的主题包。
 *
 * 该格式的 zip 包含一个 `manifest.json` 清单，其中 config 是
 * [Md3ThemeExportData] 结构（扁平的参数列表），assets 是资源文件路径映射，
 * coverAlbums 和 coverSelection 描述封面图集。
 *
 * 导入时需要将 ThemeExportData 的颜色参数映射为当前分支的
 * [ThemeConfig.Config]，从 zip 中提取背景图等资源。
 * 由于当前分支可能缺少一些参数（如 containerOpacity、enableBlur 等），
 * 这些字段在映射时会被安全忽略。
 */
internal object Md3ThemeImporter {

    @Keep
    private data class Md3ThemeManifest(
        @SerializedName("formatVersion") val formatVersion: Int = 1,
        @SerializedName("name") val name: String? = null,
        @SerializedName("config") val config: Md3ThemeExportData = Md3ThemeExportData(),
        @SerializedName("assets") val assets: Map<String, String> = emptyMap(),
        @SerializedName("coverAlbums") val coverAlbums: List<Md3CoverAlbum> = emptyList(),
        @SerializedName("coverSelection") val coverSelection: Md3CoverSelection = Md3CoverSelection()
    )

    @Keep
    @Suppress("unused")
    private data class Md3ThemeExportData(
        @SerializedName("appTheme") val appTheme: String = "0",
        @SerializedName("themeMode") val themeMode: String = "0",
        @SerializedName("isPureBlack") val isPureBlack: Boolean = false,
        @SerializedName("cPrimary") val cPrimary: Int = 0,
        @SerializedName("cNPrimary") val cNPrimary: Int = 0,
        @SerializedName("themeColor") val themeColor: Int = 0,
        @SerializedName("secondaryThemeColor") val secondaryThemeColor: Int = 0,
        @SerializedName("primaryTextColor") val primaryTextColor: Int = 0,
        @SerializedName("secondaryTextColor") val secondaryTextColor: Int = 0,
        @SerializedName("themeBackgroundColor") val themeBackgroundColor: Int = 0,
        @SerializedName("labelContainerColor") val labelContainerColor: Int = 0,
        @SerializedName("themeColorNight") val themeColorNight: Int = 0,
        @SerializedName("secondaryThemeColorNight") val secondaryThemeColorNight: Int = 0,
        @SerializedName("primaryTextColorNight") val primaryTextColorNight: Int = 0,
        @SerializedName("secondaryTextColorNight") val secondaryTextColorNight: Int = 0,
        @SerializedName("themeBackgroundColorNight") val themeBackgroundColorNight: Int = 0,
        @SerializedName("labelContainerColorNight") val labelContainerColorNight: Int = 0,
        @SerializedName("bgImageLight") val bgImageLight: String? = null,
        @SerializedName("bgImageDark") val bgImageDark: String? = null,
        @SerializedName("bgImageBlurring") val bgImageBlurring: Int = 0,
        @SerializedName("bgImageNBlurring") val bgImageNBlurring: Int = 0,
        @SerializedName("navIconHome") val navIconHome: String = "",
        @SerializedName("navIconBookshelf") val navIconBookshelf: String = "",
        @SerializedName("navIconExplore") val navIconExplore: String = "",
        @SerializedName("navIconRss") val navIconRss: String = "",
        @SerializedName("navIconMy") val navIconMy: String = "",
        @SerializedName("navIconHomeSelected") val navIconHomeSelected: String = "",
        @SerializedName("navIconBookshelfSelected") val navIconBookshelfSelected: String = "",
        @SerializedName("navIconExploreSelected") val navIconExploreSelected: String = "",
        @SerializedName("navIconRssSelected") val navIconRssSelected: String = "",
        @SerializedName("navIconMySelected") val navIconMySelected: String = "",
        @SerializedName("appFontPath") val appFontPath: String? = null,
        @SerializedName("coverDefaultImage") val coverDefaultImage: String = "",
        @SerializedName("coverDefaultImageDark") val coverDefaultImageDark: String = "",
        @SerializedName("coverTextColor") val coverTextColor: Int = -16777216,
        @SerializedName("coverShadowColor") val coverShadowColor: Int = -16777216,
        @SerializedName("coverShowName") val coverShowName: Boolean = true,
        @SerializedName("coverShowAuthor") val coverShowAuthor: Boolean = true,
        @SerializedName("coverTextColorN") val coverTextColorN: Int = -1,
        @SerializedName("coverShadowColorN") val coverShadowColorN: Int = -1,
        @SerializedName("coverShowNameN") val coverShowNameN: Boolean = true,
        @SerializedName("coverShowAuthorN") val coverShowAuthorN: Boolean = true,
        @SerializedName("coverShowShadow") val coverShowShadow: Boolean = false,
        @SerializedName("coverShowStroke") val coverShowStroke: Boolean = true,
        @SerializedName("coverDefaultColor") val coverDefaultColor: Boolean = true,
        @SerializedName("coverLoadOnlyWifi") val coverLoadOnlyWifi: Boolean = false,
        @SerializedName("coverUseDefault") val coverUseDefault: Boolean = false,
        @SerializedName("coverInfoOrientation") val coverInfoOrientation: String = "0",
        @SerializedName("showHome") val showHome: Boolean = true,
        @SerializedName("showDiscovery") val showDiscovery: Boolean = true,
        @SerializedName("showRss") val showRss: Boolean = true,
        @SerializedName("showStatusBar") val showStatusBar: Boolean = true,
        @SerializedName("showBottomView") val showBottomView: Boolean = true,
        @SerializedName("useFloatingBottomBar") val useFloatingBottomBar: Boolean = false,
        @SerializedName("tabletInterface") val tabletInterface: String = "auto",
        @SerializedName("labelVisibilityMode") val labelVisibilityMode: String = "auto",
        @SerializedName("defaultHomePage") val defaultHomePage: String = "bookshelf",
        @SerializedName("mainNavigationOrder") val mainNavigationOrder: String = "home,bookshelf,explore,rss,my",
        @SerializedName("topBarOpacity") val topBarOpacity: Int = 100,
        @SerializedName("bottomBarOpacity") val bottomBarOpacity: Int = 100,
        @SerializedName("fontScale") val fontScale: Int = 10,
        @SerializedName("containerOpacity") val containerOpacity: Int = 100,
        @SerializedName("enableBlur") val enableBlur: Boolean = false,
        @SerializedName("enableProgressiveBlur") val enableProgressiveBlur: Boolean = false,
        @SerializedName("topBarBlurRadius") val topBarBlurRadius: Int = 24,
        @SerializedName("bottomBarBlurRadius") val bottomBarBlurRadius: Int = 8,
        @SerializedName("topBarBlurAlpha") val topBarBlurAlpha: Int = 73,
        @SerializedName("bottomBarBlurAlpha") val bottomBarBlurAlpha: Int = 40,
        @SerializedName("assets") val assets: Map<String, String>? = null
    )

    @Keep
    private data class Md3CoverAlbum(
        @SerializedName("ref") val ref: String = "",
        @SerializedName("name") val name: String = "",
        @SerializedName("lightImages") val lightImages: List<Md3CoverImage> = emptyList(),
        @SerializedName("darkImages") val darkImages: List<Md3CoverImage> = emptyList()
    )

    @Keep
    private data class Md3CoverImage(
        @SerializedName("path") val path: String = ""
    )

    @Keep
    private data class Md3CoverSelection(
        @SerializedName("albumRef") val albumRef: String? = null
    )

    /**
     * 导入 MD3-main 格式的主题包。
     *
     * @param zip 已打开的 ZipFile
     * @param temp 临时解压目录
     * @param manifestEntry 清单 ZipEntry（manifest.json）
     * @param options 导入选项
     * @return 导入后的 Config
     */
    suspend fun import(
        zip: ZipFile,
        temp: File,
        manifestEntry: ZipEntry,
        options: ApplicationThemeManager.ImportOptions?
    ): ApplicationThemeManager.Config {
        require(manifestEntry.size in 0..ApplicationThemeManager.maxManifestBytes) {
            appCtx.getString(R.string.app_theme_manifest_too_large)
        }
        val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(manifestJson).asJsonObject
        require(root.has("formatVersion") && root.has("config")) {
            appCtx.getString(R.string.app_theme_invalid_format)
        }
        val manifest = GSON.fromJson(manifestJson, Md3ThemeManifest::class.java)
            ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))

        val data = manifest.config
        val themeName = manifest.name?.ifBlank { null } ?: "MD3主题"

        val importDayTheme = options?.importDayTheme ?: true
        val importNightTheme = options?.importNightTheme ?: true

        // 日间主题
        var dayTheme: ThemeConfig.Config? = null
        if (importDayTheme) {
            val dayPrimary = colorToHex(data.themeColor)
            val dayAccent = colorToHex(data.secondaryThemeColor)
            val dayBg = colorToHex(data.themeBackgroundColor)
            val dayBottomBg = colorToHex(data.labelContainerColor)
            var dayBgImgPath: String? = null
            val dayBgAssetPath = data.bgImageLight
            if (!dayBgAssetPath.isNullOrBlank()) {
                val assetEntry = manifest.assets.entries.firstOrNull { it.key == "background.light" }?.value
                    ?: dayBgAssetPath
                val extracted = ApplicationThemeManager.extractAsset(zip, temp, assetEntry)
                if (extracted != null) {
                    val dir = appCtx.externalFiles.getFile(PreferKey.bgImage).apply { mkdirs() }
                    val target = dir.getFile("application_theme_${UUID.randomUUID()}.${extracted.extension.ifBlank { "jpg" }}")
                    extracted.copyTo(target, overwrite = true)
                    dayBgImgPath = target.absolutePath
                }
            }
            dayTheme = ThemeConfig.Config(
                themeName = themeName,
                isNightTheme = false,
                primaryColor = dayPrimary,
                accentColor = dayAccent,
                backgroundColor = dayBg,
                bottomBackground = dayBottomBg,
                transparentNavBar = false,
                backgroundImgPath = dayBgImgPath,
                backgroundImgBlur = data.bgImageBlurring
            )
            ThemeConfig.addConfig(dayTheme)
        }

        // 夜间主题
        var nightTheme: ThemeConfig.Config? = null
        if (importNightTheme) {
            val nightPrimary = colorToHex(data.themeColorNight)
            val nightAccent = colorToHex(data.secondaryThemeColorNight)
            val nightBg = colorToHex(data.themeBackgroundColorNight)
            val nightBottomBg = colorToHex(data.labelContainerColorNight)
            var nightBgImgPath: String? = null
            val nightBgAssetPath = data.bgImageDark
            if (!nightBgAssetPath.isNullOrBlank()) {
                val assetEntry = manifest.assets.entries.firstOrNull { it.key == "background.dark" }?.value
                    ?: nightBgAssetPath
                val extracted = ApplicationThemeManager.extractAsset(zip, temp, assetEntry)
                if (extracted != null) {
                    val dir = appCtx.externalFiles.getFile(PreferKey.bgImageN).apply { mkdirs() }
                    val target = dir.getFile("application_theme_${UUID.randomUUID()}.${extracted.extension.ifBlank { "jpg" }}")
                    extracted.copyTo(target, overwrite = true)
                    nightBgImgPath = target.absolutePath
                }
            }
            nightTheme = ThemeConfig.Config(
                themeName = themeName,
                isNightTheme = true,
                primaryColor = nightPrimary,
                accentColor = nightAccent,
                backgroundColor = nightBg,
                bottomBackground = nightBottomBg,
                transparentNavBar = false,
                backgroundImgPath = nightBgImgPath,
                backgroundImgBlur = data.bgImageNBlurring
            )
            ThemeConfig.addConfig(nightTheme)
        }

        // 导入封面图集
        val importDayCover = options?.importDayCover ?: true
        val importNightCover = options?.importNightCover ?: true
        var dayCoverGroupId: Long? = null
        var nightCoverGroupId: Long? = null

        for (album in manifest.coverAlbums) {
            val isNightAlbum = album.darkImages.isNotEmpty() && album.lightImages.isEmpty()
            val shouldImport = if (isNightAlbum) importNightCover else importDayCover
            if (!shouldImport) continue

            val albumName = album.name.ifBlank { themeName }
            val repository = CoverGalleryRepository()
            val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == albumName }
            val groupId = existingGroup?.group?.id ?: repository.addGroup(albumName)

            val images = if (isNightAlbum) album.darkImages else album.lightImages
            val files = images.mapNotNull { img -> ApplicationThemeManager.extractAsset(zip, temp, img.path) }
            require(files.size <= ApplicationThemeManager.maxCoverImages) {
                appCtx.getString(R.string.app_theme_too_many_cover_images)
            }
            if (files.isNotEmpty()) repository.addImageFiles(appCtx, groupId, files)

            if (isNightAlbum) nightCoverGroupId = groupId else dayCoverGroupId = groupId
        }

        // 如果有日间封面专辑但夜间没有，尝试用同一个专辑
        if (dayCoverGroupId != null && nightCoverGroupId == null && importNightCover) {
            nightCoverGroupId = dayCoverGroupId
        }
        if (nightCoverGroupId != null && dayCoverGroupId == null && importDayCover) {
            dayCoverGroupId = nightCoverGroupId
        }

        val config = ApplicationThemeManager.Config(
            id = UUID.randomUUID().toString(),
            name = themeName,
            dayTheme = dayTheme,
            nightTheme = nightTheme,
            dayTopBarDir = TopBarConfig.DEFAULT_DIR_NAME,
            nightTopBarDir = TopBarConfig.DEFAULT_DIR_NAME,
            dayBottomBarId = null,
            nightBottomBarId = null,
            dayCoverGroupId = dayCoverGroupId,
            nightCoverGroupId = nightCoverGroupId
        )
        return ApplicationThemeManager.addImported(
            ApplicationThemeManager.stripComponents(config, options)
        )
    }

    /** 将 Int 颜色值转换为 #RRGGBB 格式的十六进制字符串 */
    private fun colorToHex(color: Int): String {
        val rgb = color and 0xFFFFFF
        return String.format("#%06X", rgb)
    }
}
