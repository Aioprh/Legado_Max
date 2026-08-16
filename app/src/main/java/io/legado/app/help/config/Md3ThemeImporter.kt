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
import io.legado.app.utils.EncoderUtils
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * MD3 导航图标字段名 → 当前分支图标 key 的映射。
 *
 * MD3-main 导出时 assets Map 中的 key 格式为 "navigation.{item}.{state?}"，
 * 例如 "navigation.home"、"navigation.home.selected"。
 * 同时兼容旧格式中 assets 使用字段名（如 "navIconHome"）作为 key 的情况。
 * Triple(dataField, assetsKey, localIconKey)
 */
private val NAV_ICON_MAP = listOf(
    Triple("navIconHome", "navigation.home", "homepage_normal"),
    Triple("navIconHomeSelected", "navigation.home.selected", "homepage_selected"),
    Triple("navIconBookshelf", "navigation.bookshelf", "bookshelf_normal"),
    Triple("navIconBookshelfSelected", "navigation.bookshelf.selected", "bookshelf_selected"),
    Triple("navIconExplore", "navigation.explore", "discovery_normal"),
    Triple("navIconExploreSelected", "navigation.explore.selected", "discovery_selected"),
    Triple("navIconRss", "navigation.rss", "rss_normal"),
    Triple("navIconRssSelected", "navigation.rss.selected", "rss_selected"),
    Triple("navIconMy", "navigation.my", "my_normal"),
    Triple("navIconMySelected", "navigation.my.selected", "my_selected")
)

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
        // 主题名：优先使用 manifest 中的 name 字段；
        // MD3 导出时如果未传入 themeName，该字段可能为 null。
        // 回退到 zip 文件名（去掉扩展名），最后才使用默认值。
        val themeName = manifest.name?.takeIf { it.isNotBlank() }
            ?: runCatching {
                val zipName = zip.name.substringAfterLast(File.separator)
                    .substringBeforeLast('.')
                // 排除纯数字（时间戳）、manifest、以及 "import_数字" 格式的临时文件名
                zipName.takeIf {
                    it.isNotBlank() &&
                        it != "manifest" &&
                        !it.all(Char::isDigit) &&
                        !it.matches(Regex("import_\\d+"))
                }
            }.getOrNull()
            ?: "MD3主题"

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
            // MD3 的 toPortableConfig() 会将 bgImageLight 置为 null，
            // 因此不能依赖 data.bgImageLight 判断是否有背景图。
            // 正确做法：直接从 manifest.assets 中查找 "background.light" 条目。
            val dayBgAssetPath = manifest.assets["background.light"]
                ?: manifest.assets["bgImageLight"]
                ?: data.bgImageLight
            if (!dayBgAssetPath.isNullOrBlank()) {
                val extracted = ApplicationThemeManager.extractAsset(zip, temp, dayBgAssetPath)
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
                // isPureBlack 对应当前分支的透明导航栏（纯黑模式下底栏背景透明）
                transparentNavBar = data.isPureBlack,
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
            // 同日间背景图，直接从 manifest.assets 中查找 "background.dark" 条目。
            val nightBgAssetPath = manifest.assets["background.dark"]
                ?: manifest.assets["bgImageDark"]
                ?: data.bgImageDark
            if (!nightBgAssetPath.isNullOrBlank()) {
                val extracted = ApplicationThemeManager.extractAsset(zip, temp, nightBgAssetPath)
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
                // isPureBlack 对应当前分支的透明导航栏（纯黑模式下底栏背景透明）
                transparentNavBar = data.isPureBlack,
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

        // 1. 从 coverAlbums 中导入（新格式：图片以文件形式存储在 zip 中）
        //    MD3 的 exportCoverAlbums 方法中，同一个 album 可能同时包含 lightImages 和 darkImages。
        //    如果 lightImages 非空，先导入为日间封面图集；
        //    如果 darkImages 非空且 lightImages 为空，导入为夜间封面图集；
        //    如果两者都非空，分别导入。
        for (album in manifest.coverAlbums) {
            val albumName = album.name.ifBlank { themeName }
            val repository = CoverGalleryRepository()

            // 导入日间封面图（lightImages）
            if (album.lightImages.isNotEmpty() && importDayCover) {
                val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == albumName }
                val groupId = existingGroup?.group?.id ?: repository.addGroup(albumName)
                val files = album.lightImages.mapNotNull { img -> ApplicationThemeManager.extractAsset(zip, temp, img.path) }
                require(files.size <= ApplicationThemeManager.maxCoverImages) {
                    appCtx.getString(R.string.app_theme_too_many_cover_images)
                }
                if (files.isNotEmpty()) repository.addImageFiles(appCtx, groupId, files)
                dayCoverGroupId = groupId
            }

            // 导入夜间封面图（darkImages）
            if (album.darkImages.isNotEmpty() && importNightCover) {
                val nightAlbumName = if (album.lightImages.isEmpty()) albumName else "$albumName (夜间)"
                val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == nightAlbumName }
                val groupId = existingGroup?.group?.id ?: repository.addGroup(nightAlbumName)
                val files = album.darkImages.mapNotNull { img -> ApplicationThemeManager.extractAsset(zip, temp, img.path) }
                require(files.size <= ApplicationThemeManager.maxCoverImages) {
                    appCtx.getString(R.string.app_theme_too_many_cover_images)
                }
                if (files.isNotEmpty()) repository.addImageFiles(appCtx, groupId, files)
                nightCoverGroupId = groupId
            }
        }

        // 2. 如果 coverAlbums 未提供封面图，尝试从 assets 中导入旧格式的封面图（Base64 编码）
        //    旧格式中 coverDefaultImage/coverDefaultImageDark 字段包含逗号分隔的本地路径，
        //    对应的 Base64 数据存储在 assets 中，key 为 coverDefaultImage 或 coverDefaultImage_0 等。
        if (dayCoverGroupId == null && importDayCover) {
            val dayCoverFiles = extractLegacyCoverImages(manifest.assets, "coverDefaultImage", temp)
            if (dayCoverFiles.isNotEmpty()) {
                val albumName = themeName
                val repository = CoverGalleryRepository()
                val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == albumName }
                val groupId = existingGroup?.group?.id ?: repository.addGroup(albumName)
                require(dayCoverFiles.size <= ApplicationThemeManager.maxCoverImages) {
                    appCtx.getString(R.string.app_theme_too_many_cover_images)
                }
                repository.addImageFiles(appCtx, groupId, dayCoverFiles)
                dayCoverGroupId = groupId
            }
        }
        if (nightCoverGroupId == null && importNightCover) {
            val nightCoverFiles = extractLegacyCoverImages(manifest.assets, "coverDefaultImageDark", temp)
            if (nightCoverFiles.isNotEmpty()) {
                val albumName = themeName
                val repository = CoverGalleryRepository()
                val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == albumName }
                val groupId = existingGroup?.group?.id ?: repository.addGroup(albumName)
                require(nightCoverFiles.size <= ApplicationThemeManager.maxCoverImages) {
                    appCtx.getString(R.string.app_theme_too_many_cover_images)
                }
                repository.addImageFiles(appCtx, groupId, nightCoverFiles)
                nightCoverGroupId = groupId
            }
        }

        // 如果有日间封面专辑但夜间没有，尝试用同一个专辑
        if (dayCoverGroupId != null && nightCoverGroupId == null && importNightCover) {
            nightCoverGroupId = dayCoverGroupId
        }
        if (nightCoverGroupId != null && dayCoverGroupId == null && importDayCover) {
            dayCoverGroupId = nightCoverGroupId
        }

        // 导入顶栏配置（topBarOpacity 对应 wallpaperAlpha）
        val importDayTopBar = options?.importDayTopBar ?: true
        val importNightTopBar = options?.importNightTopBar ?: true
        var dayTopBarDir = TopBarConfig.DEFAULT_DIR_NAME
        var nightTopBarDir = TopBarConfig.DEFAULT_DIR_NAME

        if (importDayTopBar) {
            val topBarConfig = TopBarConfig.Config(
                name = themeName,
                isNightMode = false,
                wallpaperAlpha = data.topBarOpacity,
                backgroundColor = data.labelContainerColor,
                updatedAt = System.currentTimeMillis()
            )
            val existingEntry = TopBarConfig.loadEntries(appCtx, false)
                .firstOrNull { it.config.name == themeName.trim() }
            dayTopBarDir = TopBarConfig.addOrUpdate(topBarConfig, oldEntry = existingEntry).dirName
        }
        if (importNightTopBar) {
            val topBarConfig = TopBarConfig.Config(
                name = themeName,
                isNightMode = true,
                wallpaperAlpha = data.topBarOpacity,
                backgroundColor = data.labelContainerColorNight,
                updatedAt = System.currentTimeMillis()
            )
            val existingEntry = TopBarConfig.loadEntries(appCtx, true)
                .firstOrNull { it.config.name == themeName.trim() }
            nightTopBarDir = TopBarConfig.addOrUpdate(topBarConfig, oldEntry = existingEntry).dirName
        }

        // 导入底栏配置（bottomBarOpacity 对应 opacity，useFloatingBottomBar 对应 layoutMode）
        val importDayBottomBar = options?.importDayBottomBar ?: true
        val importNightBottomBar = options?.importNightBottomBar ?: true
        var dayBottomBarId: String? = null
        var nightBottomBarId: String? = null

        // 提取导航图标资源
        // MD3 的 toPortableConfig() 会将 navIconHome 等字段清空为 ""，
        // 因此 extractNavIcons 内部优先从 manifest.assets 中查找。
        val navIcons = extractNavIcons(data, manifest.assets, zip, temp)

        // MD3 enableBlur → effectMode: 有模糊效果时使用 glass，否则 solid
        val md3EffectMode = if (data.enableBlur) NavigationBarConfig.EFFECT_GLASS else NavigationBarConfig.EFFECT_SOLID

        if (importDayBottomBar) {
            val navConfig = NavigationBarConfig(
                id = UUID.randomUUID().toString(),
                name = themeName,
                isNight = false,
                isBuiltin = false,
                layoutMode = if (data.useFloatingBottomBar) NavigationBarConfig.LAYOUT_FLOATING else NavigationBarConfig.LAYOUT_STANDARD,
                effectMode = md3EffectMode,
                opacity = data.bottomBarOpacity,
                icons = navIcons
            )
            val existing = NavigationBarConfig.loadConfigs(appCtx)
            val existingIndex = existing.indexOfFirst { it.isNight == false && it.name == themeName.trim() && !it.isBuiltin }
            val id = if (existingIndex >= 0) {
                existing[existingIndex].id
            } else {
                navConfig.id
            }
            val finalConfig = navConfig.copy(id = id)
            if (existingIndex >= 0) existing[existingIndex] = finalConfig else existing.add(finalConfig)
            NavigationBarConfig.saveConfigs(appCtx, existing)
            dayBottomBarId = id
        }
        if (importNightBottomBar) {
            val navConfig = NavigationBarConfig(
                id = UUID.randomUUID().toString(),
                name = themeName,
                isNight = true,
                isBuiltin = false,
                layoutMode = if (data.useFloatingBottomBar) NavigationBarConfig.LAYOUT_FLOATING else NavigationBarConfig.LAYOUT_STANDARD,
                effectMode = md3EffectMode,
                opacity = data.bottomBarOpacity,
                icons = navIcons
            )
            val existing = NavigationBarConfig.loadConfigs(appCtx)
            val existingIndex = existing.indexOfFirst { it.isNight == true && it.name == themeName.trim() && !it.isBuiltin }
            val id = if (existingIndex >= 0) {
                existing[existingIndex].id
            } else {
                navConfig.id
            }
            val finalConfig = navConfig.copy(id = id)
            if (existingIndex >= 0) existing[existingIndex] = finalConfig else existing.add(finalConfig)
            NavigationBarConfig.saveConfigs(appCtx, existing)
            nightBottomBarId = id
        }

        val config = ApplicationThemeManager.Config(
            id = UUID.randomUUID().toString(),
            name = themeName,
            dayTheme = dayTheme,
            nightTheme = nightTheme,
            dayTopBarDir = dayTopBarDir,
            nightTopBarDir = nightTopBarDir,
            dayBottomBarId = dayBottomBarId,
            nightBottomBarId = nightBottomBarId,
            dayCoverGroupId = dayCoverGroupId,
            nightCoverGroupId = nightCoverGroupId
        )
        return ApplicationThemeManager.addImported(
            ApplicationThemeManager.stripComponents(config, options)
        )
    }

    /**
     * 提取 MD3 格式的导航图标并转换为当前分支的图标映射。
     *
     * MD3-main 导出时导航图标的存储方式有三种可能：
     * 1. 新格式（ThemePackageManager 导出 zip）：assets Map 中的 key 为
     *    "navigation.home" 等，value 为 zip 内的文件路径（如 "assets/navigation/home.png"），
     *    可直接用 zip.getEntry(path) 提取。
     * 2. 旧格式（ThemeImportExport 导出 json）：assets Map 中的 key 为
     *    "navIconHome" 等，value 为 Base64 编码的文件内容。
     * 3. 混合格式：assets Map 为空，但 Md3ThemeExportData 中的字段
     *    （navIconHome 等）存储了本地文件路径（此时无法从 zip 中提取）。
     *
     * MD3 的 toPortableConfig() 会将 navIconHome 等字段清空为 ""，
     * 因此新格式下必须从 manifest.assets 中查找。
     */
    private fun extractNavIcons(
        data: Md3ThemeExportData,
        assets: Map<String, String>,
        zip: ZipFile,
        temp: File
    ): Map<String, String> {
        val iconDir = appCtx.externalFiles
            .getFile("navigationBarIcons", UUID.randomUUID().toString()).apply { mkdirs() }
        val result = mutableMapOf<String, String>()
        val jsonObj = GSON.toJsonTree(data).asJsonObject
        for ((md3Field, assetKey, iconKey) in NAV_ICON_MAP) {
            // 优先从 assets 映射中查找（新格式用 "navigation.home" 等 key，
            // 旧格式用 "navIconHome" 等 key）
            val assetValue = assets[assetKey] ?: assets[md3Field]
            if (assetValue != null && assetValue.isNotBlank()) {
                // 判断是 Base64 还是文件路径：
                // - 文件路径包含 / 或 \ 字符
                // - Base64 字符串不含路径分隔符
                val extracted: File? = if (assetValue.contains("/") || assetValue.contains("\\")) {
                    // 文件路径，从 zip 中提取
                    ApplicationThemeManager.extractAsset(zip, temp, assetValue)
                } else {
                    // 可能是 Base64 编码，也可能是纯文件名（无路径分隔符）
                    // 先尝试当 Base64 解码，失败则当 zip 内文件名查找
                    try {
                        val bytes = EncoderUtils.base64DecodeToByteArray(assetValue)
                        val target = iconDir.getFile("$iconKey.png")
                        FileOutputStream(target).use { it.write(bytes) }
                        target
                    } catch (e: Exception) {
                        // Base64 解码失败，尝试当 zip 内路径查找
                        ApplicationThemeManager.extractAsset(zip, temp, assetValue)
                    }
                }
                if (extracted != null && extracted.isFile) {
                    val target = iconDir.getFile("$iconKey.${extracted.extension.ifBlank { "png" }}")
                    if (extracted != target) extracted.copyTo(target, overwrite = true)
                    result[iconKey] = target.absolutePath
                }
                continue
            }
            // 回退到 Md3ThemeExportData 中的字段值（新格式下为 ""，旧格式下可能是本地文件路径）
            val path = jsonObj.get(md3Field)?.asString
            if (path.isNullOrBlank()) continue
            // 尝试从 zip 中提取（仅当 path 是 zip 内路径时有效）
            val extracted = ApplicationThemeManager.extractAsset(zip, temp, path)
            if (extracted != null && extracted.isFile) {
                val target = iconDir.getFile("$iconKey.${extracted.extension.ifBlank { "png" }}")
                extracted.copyTo(target, overwrite = true)
                result[iconKey] = target.absolutePath
            }
        }
        return result
    }

    /**
     * 从旧格式 assets 中提取封面图集图片（Base64 编码）。
     *
     * MD3 旧格式中，coverDefaultImage 字段包含逗号分隔的本地文件路径，
     * 对应的 Base64 数据存储在 assets Map 中，key 为：
     * - 单个图片：coverDefaultImage / coverDefaultImageDark
     * - 多个图片：coverDefaultImage_0, coverDefaultImage_1, ... / coverDefaultImageDark_0, ...
     */
    private fun extractLegacyCoverImages(
        assets: Map<String, String>,
        keyPrefix: String,
        temp: File
    ): List<File> {
        val result = mutableListOf<File>()
        // 尝试单个 key
        val single = assets[keyPrefix]
        if (single != null && single.isNotBlank()) {
            val file = decodeBase64ToFile(single, temp)
            if (file != null) result.add(file)
        }
        // 尝试多个 key（coverDefaultImage_0, coverDefaultImage_1, ...）
        if (result.isEmpty()) {
            var index = 0
            while (true) {
                val key = "${keyPrefix}_$index"
                val base64 = assets[key] ?: break
                if (base64.isBlank()) break
                val file = decodeBase64ToFile(base64, temp)
                if (file != null) result.add(file)
                index++
            }
        }
        return result
    }

    /** 将 Base64 字符串解码并写入临时文件 */
    private fun decodeBase64ToFile(base64: String, temp: File): File? {
        return try {
            val bytes = EncoderUtils.base64DecodeToByteArray(base64)
            val target = temp.resolve("${UUID.randomUUID()}.jpg")
            FileOutputStream(target).use { it.write(bytes) }
            target
        } catch (e: Exception) {
            null
        }
    }

    /** 将 Int 颜色值转换为 #RRGGBB 格式的十六进制字符串 */
    private fun colorToHex(color: Int): String {
        val rgb = color and 0xFFFFFF
        return String.format("#%06X", rgb)
    }
}
