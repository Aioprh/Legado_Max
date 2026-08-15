package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry

/**
 * 兼容导入 archive_primate_beta 分支导出的应用主题包。
 *
 * 该格式的 zip 包含一个 `appearance_kit.json` 清单和多个子 zip 组件
 *（theme_day.zip, topbar_day.zip 等）。清单中的 components 列表描述
 * 了每个子 zip 的类型、日夜模式和路径。
 *
 * 导入时逐个解压子 zip，提取其中的配置和资源，转换为当前分支的
 * [ApplicationThemeManager.Config] 结构。由于当前分支可能缺少一些参数
 *（如 panelBackgroundImgPath、cardColor 等），这些多余字段在 GSON
 * 解析时会被安全忽略。
 */
internal object AppearanceKitImporter {

    @Keep
    private data class AppearanceKitPackage(
        @SerializedName("id") val id: String = "",
        @SerializedName("name") val name: String = "",
        @SerializedName("version") val version: Int = 0,
        @SerializedName("exportedAt") val exportedAt: Long = 0L,
        @SerializedName("previewPath") val previewPath: String? = null,
        @SerializedName("binding") val binding: AppearanceKitBinding? = null,
        @SerializedName("components") val components: List<AppearanceKitComponent> = emptyList()
    )

    @Keep
    private data class AppearanceKitBinding(
        @SerializedName("preset") val preset: String? = null,
        @SerializedName("dayTheme") val dayTheme: ComponentRef? = null,
        @SerializedName("nightTheme") val nightTheme: ComponentRef? = null,
        @SerializedName("dayTopBar") val dayTopBar: ComponentRef? = null,
        @SerializedName("nightTopBar") val nightTopBar: ComponentRef? = null,
        @SerializedName("dayNavigationBar") val dayNavigationBar: ComponentRef? = null,
        @SerializedName("nightNavigationBar") val nightNavigationBar: ComponentRef? = null,
        @SerializedName("dayCoverCollection") val dayCoverCollection: ComponentRef? = null,
        @SerializedName("nightCoverCollection") val nightCoverCollection: ComponentRef? = null,
        @SerializedName("floatingBottomBarHideSearch") val floatingBottomBarHideSearch: Boolean? = null
    )

    @Keep
    private data class ComponentRef(
        @SerializedName("dirName") val dirName: String = "",
        @SerializedName("name") val name: String = ""
    )

    @Keep
    private data class AppearanceKitComponent(
        @SerializedName("type") val type: String = "",
        @SerializedName("isNight") val isNight: Boolean = false,
        @SerializedName("path") val path: String = ""
    )

    /** archive_primate_beta 主题包内的 theme.json 结构 */
    @Keep
    private data class AppearanceThemePackage(
        @SerializedName("name") val name: String = "",
        @SerializedName("dirName") val dirName: String = "",
        @SerializedName("isNightTheme") val isNightTheme: Boolean = false,
        @SerializedName("updatedAt") val updatedAt: Long = 0L,
        @SerializedName("config") val config: ThemeConfig.Config? = null
    )

    /** archive_primate_beta 主题包内的 top_bar.json 结构 */
    @Keep
    private data class AppearanceTopBarConfig(
        @SerializedName("name") val name: String = "",
        @SerializedName("isNightMode") val isNightMode: Boolean = false,
        @SerializedName("style") val style: String = "default",
        @SerializedName("tagBarColor") val tagBarColor: Int? = null,
        @SerializedName("tagBarAlpha") val tagBarAlpha: Int = 100,
        @SerializedName("tagSelectedColor") val tagSelectedColor: Int? = null,
        @SerializedName("tagSelectedAlpha") val tagSelectedAlpha: Int = 100,
        @SerializedName("wallpaperPath") val wallpaperPath: String? = null,
        @SerializedName("wallpaperAlpha") val wallpaperAlpha: Int = 100,
        @SerializedName("backgroundColor") val backgroundColor: Int? = null,
        @SerializedName("cornerScale") val cornerScale: Float? = null,
        @SerializedName("expandFiltersByDefault") val expandFiltersByDefault: Boolean = false,
        @SerializedName("hideFilterToggleWhenExpanded") val hideFilterToggleWhenExpanded: Boolean = false,
        @SerializedName("showSearchInDefaultStyle") val showSearchInDefaultStyle: Boolean = false,
        @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
    )

    /** archive_primate_beta 主题包内的 navigation_bar.json 结构 */
    @Keep
    private data class AppearanceNavBarConfig(
        @SerializedName("name") val name: String = "",
        @SerializedName("isNightMode") val isNightMode: Boolean = false,
        @SerializedName("layoutMode") val layoutMode: String = "floating",
        @SerializedName("sidebarGravity") val sidebarGravity: String = "start",
        @SerializedName("effectMode") val effectMode: String = "glass",
        @SerializedName("opacity") val opacity: Int = 72,
        @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis(),
        @SerializedName("sidebarBackgroundPath") val sidebarBackgroundPath: String? = null,
        @SerializedName("wallpaperPath") val wallpaperPath: String? = null,
        @SerializedName("borderColor") val borderColor: Int? = null,
        @SerializedName("borderAlpha") val borderAlpha: Int = 100,
        @SerializedName("hideSearchInFloatingStyle") val hideSearchInFloatingStyle: Boolean = false,
        @SerializedName("icons") val icons: Map<String, String> = emptyMap()
    )

    /** archive_primate_beta 封面图集 zip 内的清单 */
    @Keep
    private data class AppearanceCoverCollection(
        @SerializedName("name") val name: String = "",
        @SerializedName("images") val images: List<String> = emptyList()
    )

    /**
     * 导入 archive_primate_beta 格式的应用主题包。
     *
     * @param zip 已打开的 ZipFile
     * @param temp 临时解压目录
     * @param manifestEntry 清单 ZipEntry（appearance_kit.json）
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
        val kitPackage = zip.getInputStream(manifestEntry).bufferedReader().use {
            GSON.fromJson(it, AppearanceKitPackage::class.java)
        } ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))

        val kitName = kitPackage.name.ifBlank { appCtx.getString(R.string.application_theme_manage) }
        var dayTheme: ThemeConfig.Config? = null
        var nightTheme: ThemeConfig.Config? = null
        var dayTopBarDir = TopBarConfig.DEFAULT_DIR_NAME
        var nightTopBarDir = TopBarConfig.DEFAULT_DIR_NAME
        var dayBottomBarId: String? = null
        var nightBottomBarId: String? = null
        var dayCoverGroupId: Long? = null
        var nightCoverGroupId: Long? = null

        val componentTemp = temp.resolve("components").apply { mkdirs() }
        for (component in kitPackage.components) {
            val componentFile = ApplicationThemeManager.extractAsset(zip, componentTemp, component.path) ?: continue
            when (component.type.uppercase()) {
                "THEME" -> {
                    val shouldImport = if (component.isNight) options?.importNightTheme ?: true
                    else options?.importDayTheme ?: true
                    if (!shouldImport) continue
                    val subTemp = componentTemp.resolve("theme_${component.isNight}").apply { mkdirs() }
                    try {
                        ZipUtils.unZipToPath(componentFile, subTemp)
                        val themeJsonFile = subTemp.walkTopDown().firstOrNull { it.name == "theme.json" }
                        if (themeJsonFile != null) {
                            val pkg = GSON.fromJsonObject<AppearanceThemePackage>(themeJsonFile.readText()).getOrNull()
                            val config = pkg?.config
                            if (config != null) {
                                val bgPath = config.backgroundImgPath
                                var restoredConfig = config.copy(isNightTheme = component.isNight)
                                if (bgPath != null && !bgPath.startsWith("http", true)) {
                                    val bgFile = subTemp.walkTopDown().firstOrNull { it.isFile && it.name == bgPath.substringAfterLast(File.separator) }
                                        ?: subTemp.walkTopDown().firstOrNull { it.isFile && !it.name.endsWith(".json") }
                                    if (bgFile != null && bgFile.isFile) {
                                        val dir = appCtx.externalFiles
                                            .getFile(if (component.isNight) PreferKey.bgImageN else PreferKey.bgImage)
                                            .apply { mkdirs() }
                                        val target = dir.getFile("application_theme_${UUID.randomUUID()}.${bgFile.extension.ifBlank { "jpg" }}")
                                        bgFile.copyTo(target, overwrite = true)
                                        restoredConfig = restoredConfig.copy(backgroundImgPath = target.absolutePath)
                                    } else {
                                        restoredConfig = restoredConfig.copy(backgroundImgPath = null)
                                    }
                                }
                                ThemeConfig.addConfig(restoredConfig)
                                if (component.isNight) nightTheme = restoredConfig else dayTheme = restoredConfig
                            }
                        }
                    } finally {
                        subTemp.deleteRecursively()
                    }
                }
                "TOP_BAR" -> {
                    val shouldImport = if (component.isNight) options?.importNightTopBar ?: true
                    else options?.importDayTopBar ?: true
                    if (!shouldImport) continue
                    val subTemp = componentTemp.resolve("topbar_${component.isNight}").apply { mkdirs() }
                    try {
                        ZipUtils.unZipToPath(componentFile, subTemp)
                        val topBarJsonFile = subTemp.walkTopDown().firstOrNull { it.name == "top_bar.json" }
                        if (topBarJsonFile != null) {
                            val source = GSON.fromJsonObject<AppearanceTopBarConfig>(topBarJsonFile.readText()).getOrNull()
                            if (source != null) {
                                val wallpaperPath = source.wallpaperPath?.let { wpPath ->
                                    val wpFile = subTemp.walkTopDown().firstOrNull { it.isFile && it.name == wpPath.substringAfterLast(File.separator) }
                                        ?: subTemp.walkTopDown().firstOrNull { it.isFile && !it.name.endsWith(".json") }
                                    wpFile?.absolutePath
                                }
                                val config = TopBarConfig.Config(
                                    name = source.name,
                                    isNightMode = component.isNight,
                                    style = source.style,
                                    tagBarColor = source.tagBarColor,
                                    tagBarAlpha = source.tagBarAlpha,
                                    tagSelectedColor = source.tagSelectedColor,
                                    tagSelectedAlpha = source.tagSelectedAlpha,
                                    wallpaperPath = wallpaperPath,
                                    wallpaperAlpha = source.wallpaperAlpha,
                                    backgroundColor = source.backgroundColor,
                                    cornerScale = source.cornerScale,
                                    expandFiltersByDefault = source.expandFiltersByDefault,
                                    updatedAt = System.currentTimeMillis()
                                )
                                val existingEntry = TopBarConfig.loadEntries(appCtx, component.isNight)
                                    .firstOrNull { it.config.name == source.name.trim() }
                                val dirName = TopBarConfig.addOrUpdate(
                                    config,
                                    oldEntry = existingEntry
                                ).dirName
                                if (component.isNight) nightTopBarDir = dirName else dayTopBarDir = dirName
                            }
                        }
                    } finally {
                        subTemp.deleteRecursively()
                    }
                }
                "NAVIGATION_BAR" -> {
                    val shouldImport = if (component.isNight) options?.importNightBottomBar ?: true
                    else options?.importDayBottomBar ?: true
                    if (!shouldImport) continue
                    val subTemp = componentTemp.resolve("navbar_${component.isNight}").apply { mkdirs() }
                    try {
                        ZipUtils.unZipToPath(componentFile, subTemp)
                        val navBarJsonFile = subTemp.walkTopDown().firstOrNull { it.name == "navigation_bar.json" }
                        if (navBarJsonFile != null) {
                            val source = GSON.fromJsonObject<AppearanceNavBarConfig>(navBarJsonFile.readText()).getOrNull()
                            if (source != null) {
                                val iconDir = appCtx.externalFiles
                                    .getFile("navigationBarIcons", UUID.randomUUID().toString()).apply { mkdirs() }
                                val icons = source.icons.mapNotNull { (key, path) ->
                                    val iconFile = subTemp.walkTopDown().firstOrNull { it.isFile && it.name == path.substringAfterLast(File.separator) }
                                        ?: subTemp.walkTopDown().firstOrNull { it.isFile && !it.name.endsWith(".json") && it.name.startsWith(key) }
                                    if (iconFile != null && iconFile.isFile) {
                                        val target = iconDir.getFile("${key}.${iconFile.extension.ifBlank { "png" }}")
                                        iconFile.copyTo(target, overwrite = true)
                                        key to target.absolutePath
                                    } else null
                                }.toMap()
                                val navConfig = NavigationBarConfig(
                                    id = UUID.randomUUID().toString(),
                                    name = source.name,
                                    isNight = component.isNight,
                                    isBuiltin = false,
                                    layoutMode = source.layoutMode,
                                    effectMode = source.effectMode,
                                    opacity = source.opacity,
                                    borderColor = source.borderColor,
                                    borderAlpha = source.borderAlpha,
                                    wallpaperPath = source.wallpaperPath?.let { wpPath ->
                                        subTemp.walkTopDown().firstOrNull { it.isFile && it.name == wpPath.substringAfterLast(File.separator) }
                                            ?.let { wpFile ->
                                                val wpDir = appCtx.externalFiles
                                                    .getFile("navigationBarWallpapers", UUID.randomUUID().toString()).apply { mkdirs() }
                                                val wpTarget = wpDir.getFile("wp_${UUID.randomUUID()}.${wpFile.extension.ifBlank { "png" }}")
                                                wpFile.copyTo(wpTarget, overwrite = true)
                                                wpTarget.absolutePath
                                            }
                                    },
                                    sidebarBackgroundPath = source.sidebarBackgroundPath?.let { sbPath ->
                                        subTemp.walkTopDown().firstOrNull { it.isFile && it.name == sbPath.substringAfterLast(File.separator) }
                                            ?.let { sbFile ->
                                                val sbDir = appCtx.externalFiles
                                                    .getFile("navigationBarSidebars", UUID.randomUUID().toString()).apply { mkdirs() }
                                                val sbTarget = sbDir.getFile("sb_${UUID.randomUUID()}.${sbFile.extension.ifBlank { "png" }}")
                                                sbFile.copyTo(sbTarget, overwrite = true)
                                                sbTarget.absolutePath
                                            }
                                    },
                                    sidebarGravity = source.sidebarGravity,
                                    icons = icons
                                )
                                val existing = NavigationBarConfig.loadConfigs(appCtx)
                                val existingIndex = existing.indexOfFirst { it.isNight == component.isNight && it.name == source.name.trim() && !it.isBuiltin }
                                val id = if (existingIndex >= 0) {
                                    existing[existingIndex].id
                                } else {
                                    navConfig.id
                                }
                                val finalConfig = navConfig.copy(id = id)
                                if (existingIndex >= 0) existing[existingIndex] = finalConfig else existing.add(finalConfig)
                                NavigationBarConfig.saveConfigs(appCtx, existing)
                                if (component.isNight) nightBottomBarId = id else dayBottomBarId = id
                            }
                        }
                    } finally {
                        subTemp.deleteRecursively()
                    }
                }
                "COVER_COLLECTION" -> {
                    val shouldImport = if (component.isNight) options?.importNightCover ?: true
                    else options?.importDayCover ?: true
                    if (!shouldImport) continue
                    val subTemp = componentTemp.resolve("cover_${component.isNight}").apply { mkdirs() }
                    try {
                        ZipUtils.unZipToPath(componentFile, subTemp)
                        val coverJsonFile = subTemp.walkTopDown().firstOrNull { it.isFile && (it.name == "collection.json" || it.name == "cover_collection.json") }
                        val coverName: String
                        val imageFiles: List<File>
                        if (coverJsonFile != null) {
                            val collection = GSON.fromJsonObject<AppearanceCoverCollection>(coverJsonFile.readText()).getOrNull()
                            coverName = collection?.name?.ifBlank { kitName } ?: kitName
                            imageFiles = collection?.images?.mapNotNull { imgPath ->
                                subTemp.walkTopDown().firstOrNull { it.isFile && it.name == imgPath.substringAfterLast(File.separator) }
                            } ?: emptyList()
                        } else {
                            coverName = kitName
                            imageFiles = subTemp.walkTopDown().filter { it.isFile && !it.name.endsWith(".json") }.toList()
                        }
                        if (imageFiles.isNotEmpty()) {
                            val repository = CoverGalleryRepository()
                            val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == coverName }
                            val groupId = existingGroup?.group?.id ?: repository.addGroup(coverName)
                            require(imageFiles.size <= ApplicationThemeManager.maxCoverImages) {
                                appCtx.getString(R.string.app_theme_too_many_cover_images)
                            }
                            repository.addImageFiles(appCtx, groupId, imageFiles)
                            if (component.isNight) nightCoverGroupId = groupId else dayCoverGroupId = groupId
                        }
                    } finally {
                        subTemp.deleteRecursively()
                    }
                }
            }
        }

        // 如果没有从组件中获取到主题，尝试从 binding 中的引用名推断
        val binding = kitPackage.binding
        if (dayTheme == null && binding?.dayTheme != null) {
            val existing = ThemeConfig.configList.firstOrNull { !it.isNightTheme && it.themeName == binding.dayTheme.name }
            if (existing != null) dayTheme = existing.copy()
        }
        if (nightTheme == null && binding?.nightTheme != null) {
            val existing = ThemeConfig.configList.firstOrNull { it.isNightTheme && it.themeName == binding.nightTheme.name }
            if (existing != null) nightTheme = existing.copy()
        }

        val config = ApplicationThemeManager.Config(
            id = UUID.randomUUID().toString(),
            name = kitName,
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
}
