package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.normalizeFileName
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 多格式主题导出器。
 *
 * 将当前分支的 [ApplicationThemeManager.Config] 转换为其他分支的格式并打包为 zip：
 * - [exportArchive]：archive_primate_beta 分支格式（appearance_kit.json + 子 zip 组件）
 * - [exportMd3]：MD3-main 分支格式（manifest.json + assets）
 * - [exportRed]：Reeden 阅读 App 格式（theme.json + 资源，带 RED 头）
 *
 * 导出时进行字段映射和数据结构转换，
 * 确保目标分支能正确识别和导入。
 */
internal object ThemeExporter {

    // ─── 通用工具 ──────────────────────────────────────────────────

    /** 简单计数器，用于生成唯一资源路径 */
    private class Counter {
        private var value = 0
        fun next(): Int = value++
    }

    /** 将 #AARRGGBB 或 #RRGGBB 格式颜色字符串转换为 Int */
    private fun hexToInt(hex: String?): Int {
        if (hex.isNullOrBlank()) return 0
        return runCatching {
            val h = hex.removePrefix("#")
            if (h.length == 8) h.toLong(16).toInt()
            else if (h.length == 6) (0xFF000000L or h.toLong(16)).toInt()
            else 0
        }.getOrDefault(0)
    }

    /** 将目录内容打包为 zip 文件 */
    private fun zipDirectory(dir: File, zipFile: File) {
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** 将文件添加到 zip 中 */
    private fun addFileToZip(zip: ZipOutputStream, file: File, path: String) {
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** 将主题背景图打包到 zip 中，返回 zip 内路径 */
    private fun packageAssetToZip(
        zip: ZipOutputStream,
        theme: ThemeConfig.Config,
        isNight: Boolean,
        basePath: String,
        counter: Counter,
        assetKey: String,
        assets: MutableMap<String, String>
    ): String? {
        val path = theme.backgroundImgPath ?: return null
        if (path.startsWith("http", true)) return null
        val source = File(path).takeIf { it.isFile }
            ?: appCtx.externalFiles
                .getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage)
                .getFile(path)
                .takeIf { it.isFile }
        if (source == null || !source.isFile) return null
        val ext = source.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val assetPath = "$basePath${counter.next()}$ext"
        addFileToZip(zip, source, assetPath)
        assets[assetKey] = assetPath
        return assetPath
    }

    // ─── archive_primate_beta 格式导出 ─────────────────────────────

    @Keep
    private data class AppearanceKitPackage(
        @SerializedName("id") val id: String = "",
        @SerializedName("name") val name: String = "",
        @SerializedName("version") val version: Int = 1,
        @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
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

    @Keep
    private data class AppearanceThemePackage(
        @SerializedName("name") val name: String = "",
        @SerializedName("dirName") val dirName: String = "",
        @SerializedName("isNightTheme") val isNightTheme: Boolean = false,
        @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis(),
        @SerializedName("config") val config: ThemeConfig.Config? = null
    )

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

    @Keep
    private data class AppearanceCoverCollection(
        @SerializedName("name") val name: String = "",
        @SerializedName("images") val images: List<String> = emptyList()
    )

    /**
     * 导出为 archive_primate_beta 分支格式。
     *
     * 生成一个 zip 包含：
     * - appearance_kit.json：清单文件，描述所有组件
     * - theme_day.zip / theme_night.zip：主题组件（内含 theme.json + 背景图）
     * - topbar_day.zip / topbar_night.zip：顶栏组件（内含 top_bar.json + 壁纸）
     * - navbar_day.zip / navbar_night.zip：底栏组件（内含 navigation.json + 图标）
     * - cover_day.zip / cover_night.zip：封面图集组件（内含 meta.json + 图片）
     */
    fun exportArchive(context: android.content.Context, config: ApplicationThemeManager.Config): File {
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = config.name.normalizeFileName().ifBlank { "application_theme" }
        val zipFile = dir.resolve("${exportName}_archive.zip")

        // 当前分支底栏图标 key → archive 分支图标 key 的映射（反向映射）
        val navIconReverseMap = mapOf(
            "homepage_normal" to "search_normal",
            "homepage_selected" to "search_selected"
        )

        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            val components = mutableListOf<AppearanceKitComponent>()
            val tempDir = appCtx.cacheDir.resolve("archiveExport_${System.currentTimeMillis()}").apply { mkdirs() }

            try {
                // 主题组件
                config.dayTheme?.let { theme ->
                    val path = createThemeSubZip(tempDir, "theme_day", theme)
                    if (path != null) {
                        addFileToZip(zip, path, "theme_day.zip")
                        components.add(AppearanceKitComponent("THEME", false, "theme_day.zip"))
                    }
                }
                config.nightTheme?.let { theme ->
                    val path = createThemeSubZip(tempDir, "theme_night", theme)
                    if (path != null) {
                        addFileToZip(zip, path, "theme_night.zip")
                        components.add(AppearanceKitComponent("THEME", true, "theme_night.zip"))
                    }
                }

                // 顶栏组件
                if (config.dayTopBarDir.isNotBlank() && config.dayTopBarDir != TopBarConfig.DEFAULT_DIR_NAME) {
                    val entry = TopBarConfig.loadEntries(context, false).firstOrNull { it.dirName == config.dayTopBarDir }
                    if (entry != null) {
                        val path = createTopBarSubZip(tempDir, "topbar_day", entry, false)
                        if (path != null) {
                            addFileToZip(zip, path, "topbar_day.zip")
                            components.add(AppearanceKitComponent("TOP_BAR", false, "topbar_day.zip"))
                        }
                    }
                }
                if (config.nightTopBarDir.isNotBlank() && config.nightTopBarDir != TopBarConfig.DEFAULT_DIR_NAME) {
                    val entry = TopBarConfig.loadEntries(context, true).firstOrNull { it.dirName == config.nightTopBarDir }
                    if (entry != null) {
                        val path = createTopBarSubZip(tempDir, "topbar_night", entry, true)
                        if (path != null) {
                            addFileToZip(zip, path, "topbar_night.zip")
                            components.add(AppearanceKitComponent("TOP_BAR", true, "topbar_night.zip"))
                        }
                    }
                }

                // 底栏组件
                config.dayBottomBarId?.let { id ->
                    val navConfig = NavigationBarConfig.loadConfigs(context)
                        .firstOrNull { it.isNight == false && it.id == id }
                    if (navConfig != null) {
                        val path = createNavBarSubZip(tempDir, "navbar_day", navConfig, navIconReverseMap)
                        if (path != null) {
                            addFileToZip(zip, path, "navbar_day.zip")
                            components.add(AppearanceKitComponent("NAVIGATION_BAR", false, "navbar_day.zip"))
                        }
                    }
                }
                config.nightBottomBarId?.let { id ->
                    val navConfig = NavigationBarConfig.loadConfigs(context)
                        .firstOrNull { it.isNight == true && it.id == id }
                    if (navConfig != null) {
                        val path = createNavBarSubZip(tempDir, "navbar_night", navConfig, navIconReverseMap)
                        if (path != null) {
                            addFileToZip(zip, path, "navbar_night.zip")
                            components.add(AppearanceKitComponent("NAVIGATION_BAR", true, "navbar_night.zip"))
                        }
                    }
                }

                // 封面图集组件
                config.dayCoverGroupId?.let { groupId ->
                    val path = createCoverSubZip(tempDir, "cover_day", groupId)
                    if (path != null) {
                        addFileToZip(zip, path, "cover_day.zip")
                        components.add(AppearanceKitComponent("COVER_COLLECTION", false, "cover_day.zip"))
                    }
                }
                config.nightCoverGroupId?.let { groupId ->
                    val path = createCoverSubZip(tempDir, "cover_night", groupId)
                    if (path != null) {
                        addFileToZip(zip, path, "cover_night.zip")
                        components.add(AppearanceKitComponent("COVER_COLLECTION", true, "cover_night.zip"))
                    }
                }
            } finally {
                tempDir.deleteRecursively()
            }

            // 写入清单文件
            val kitPackage = AppearanceKitPackage(
                id = config.id,
                name = config.name,
                version = 1,
                exportedAt = System.currentTimeMillis(),
                components = components
            )
            zip.putNextEntry(ZipEntry("appearance_kit.json"))
            zip.write(GSON.toJson(kitPackage).toByteArray())
            zip.closeEntry()
        }
        return zipFile
    }

    /** 创建主题子 zip（包含 theme.json + 背景图） */
    private fun createThemeSubZip(tempDir: File, name: String, theme: ThemeConfig.Config): File? {
        val subDir = tempDir.resolve(name).apply { mkdirs() }
        val themePkg = AppearanceThemePackage(
            name = theme.themeName,
            dirName = name,
            isNightTheme = theme.isNightTheme,
            updatedAt = System.currentTimeMillis(),
            config = theme.copy()
        )
        File(subDir, "theme.json").writeText(GSON.toJson(themePkg))

        // 写入背景图
        theme.backgroundImgPath?.let { path ->
            if (!path.startsWith("http", true)) {
                val src = File(path).takeIf { it.isFile }
                    ?: appCtx.externalFiles
                        .getFile(if (theme.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage)
                        .getFile(path)
                        .takeIf { it.isFile }
                if (src != null && src.isFile) {
                    val target = File(subDir, src.name)
                    src.copyTo(target, overwrite = true)
                }
            }
        }

        val zipFile = tempDir.resolve("$name.zip")
        zipDirectory(subDir, zipFile)
        return zipFile.takeIf { it.isFile }
    }

    /** 创建顶栏子 zip（包含 top_bar.json + 壁纸） */
    private fun createTopBarSubZip(tempDir: File, name: String, entry: TopBarConfig.Entry, isNight: Boolean): File? {
        val subDir = tempDir.resolve(name).apply { mkdirs() }
        val topBarConfig = AppearanceTopBarConfig(
            name = entry.config.name,
            isNightMode = isNight,
            style = entry.config.style,
            tagBarColor = entry.config.tagBarColor,
            tagBarAlpha = entry.config.tagBarAlpha,
            tagSelectedColor = entry.config.tagSelectedColor,
            tagSelectedAlpha = entry.config.tagSelectedAlpha,
            wallpaperAlpha = entry.config.wallpaperAlpha,
            backgroundColor = entry.config.backgroundColor,
            cornerScale = entry.config.cornerScale,
            expandFiltersByDefault = entry.config.expandFiltersByDefault,
            updatedAt = System.currentTimeMillis()
        )
        // 写入壁纸图片
        val wallpaperPath = entry.config.wallpaperPath?.let { path ->
            val file = File(path).takeIf { it.isFile }
                ?: entry.localDir?.resolve(path)?.takeIf { it.isFile }
            file?.let { src ->
                val target = File(subDir, src.name)
                src.copyTo(target, overwrite = true)
                target.name
            }
        }
        val finalConfig = topBarConfig.copy(wallpaperPath = wallpaperPath)
        File(subDir, "top_bar.json").writeText(GSON.toJson(finalConfig))

        val zipFile = tempDir.resolve("$name.zip")
        zipDirectory(subDir, zipFile)
        return zipFile.takeIf { it.isFile }
    }

    /** 创建底栏子 zip（包含 navigation.json + 图标文件） */
    private fun createNavBarSubZip(
        tempDir: File,
        name: String,
        navConfig: NavigationBarConfig,
        iconReverseMap: Map<String, String>
    ): File? {
        val subDir = tempDir.resolve(name).apply { mkdirs() }

        // 复制图标文件，将当前分支的 key 映射回 archive 的 key
        val archiveIcons = mutableMapOf<String, String>()
        navConfig.icons.forEach { (key, path) ->
            val src = File(path).takeIf { it.isFile } ?: return@forEach
            val archiveKey = iconReverseMap[key] ?: key
            val target = File(subDir, "$archiveKey.${src.extension.ifBlank { "png" }}")
            src.copyTo(target, overwrite = true)
            archiveIcons[archiveKey] = target.name
        }

        val appearanceNav = AppearanceNavBarConfig(
            name = navConfig.name,
            isNightMode = navConfig.isNight,
            layoutMode = navConfig.layoutMode,
            sidebarGravity = navConfig.sidebarGravity,
            effectMode = navConfig.effectMode,
            opacity = navConfig.opacity,
            borderColor = navConfig.borderColor,
            borderAlpha = navConfig.borderAlpha,
            hideSearchInFloatingStyle = false,
            icons = archiveIcons
        )
        File(subDir, "navigation.json").writeText(GSON.toJson(appearanceNav))

        val zipFile = tempDir.resolve("$name.zip")
        zipDirectory(subDir, zipFile)
        return zipFile.takeIf { it.isFile }
    }

    /** 创建封面图集子 zip（包含 meta.json + 图片文件） */
    private fun createCoverSubZip(tempDir: File, name: String, groupId: Long): File? {
        val repository = CoverGalleryRepository()
        val group = repository.allGroupsWithImages().firstOrNull { it.group.id == groupId } ?: return null
        val subDir = tempDir.resolve(name).apply { mkdirs() }

        val coverCollection = AppearanceCoverCollection(name = group.group.name)
        File(subDir, "meta.json").writeText(GSON.toJson(coverCollection))

        group.images.forEachIndexed { index, image ->
            File(image.path).takeIf { it.isFile }?.let { src ->
                val ext = src.extension.ifBlank { "jpg" }
                val target = File(subDir, "cover_$index.$ext")
                src.copyTo(target, overwrite = true)
            }
        }

        val zipFile = tempDir.resolve("$name.zip")
        zipDirectory(subDir, zipFile)
        return zipFile.takeIf { it.isFile }
    }

    // ─── MD3-main 格式导出 ───────────────────────────────────────

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
        @SerializedName("useFloatingBottomBar") val useFloatingBottomBar: Boolean = false,
        @SerializedName("topBarOpacity") val topBarOpacity: Int = 100,
        @SerializedName("bottomBarOpacity") val bottomBarOpacity: Int = 100,
        @SerializedName("enableBlur") val enableBlur: Boolean = false,
        @SerializedName("coverShowName") val coverShowName: Boolean = true,
        @SerializedName("coverShowAuthor") val coverShowAuthor: Boolean = true,
        @SerializedName("coverShowShadow") val coverShowShadow: Boolean = false,
        @SerializedName("coverShowStroke") val coverShowStroke: Boolean = true,
        @SerializedName("coverDefaultColor") val coverDefaultColor: Boolean = true
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

    /** 当前分支底栏图标 key → MD3 图标 assets key 的映射 */
    private val MD3_NAV_ICON_MAP = listOf(
        "homepage_normal" to "navigation.home",
        "homepage_selected" to "navigation.home.selected",
        "bookshelf_normal" to "navigation.bookshelf",
        "bookshelf_selected" to "navigation.bookshelf.selected",
        "discovery_normal" to "navigation.explore",
        "discovery_selected" to "navigation.explore.selected",
        "rss_normal" to "navigation.rss",
        "rss_selected" to "navigation.rss.selected",
        "my_normal" to "navigation.my",
        "my_selected" to "navigation.my.selected"
    )

    /**
     * 导出为 MD3-main 分支格式。
     *
     * 生成一个 zip 包含：
     * - manifest.json：清单文件（config + assets + coverAlbums）
     * - assets/ 目录：背景图、导航图标等资源文件
     */
    fun exportMd3(context: android.content.Context, config: ApplicationThemeManager.Config): File {
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = config.name.normalizeFileName().ifBlank { "application_theme" }
        val zipFile = dir.resolve("${exportName}_md3.zip")

        val assets = mutableMapOf<String, String>()
        val assetIndex = Counter()

        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            // 背景图
            var bgImageLight: String? = null
            var bgImageDark: String? = null
            config.dayTheme?.let { theme ->
                bgImageLight = packageAssetToZip(zip, theme, false, "assets/background", assetIndex, "background.light", assets)
            }
            config.nightTheme?.let { theme ->
                bgImageDark = packageAssetToZip(zip, theme, true, "assets/background", assetIndex, "background.dark", assets)
            }

            // 颜色映射
            val themeColor = hexToInt(config.dayTheme?.primaryColor)
            val secondaryThemeColor = hexToInt(config.dayTheme?.accentColor)
            val themeBackgroundColor = hexToInt(config.dayTheme?.backgroundColor)
            val labelContainerColor = hexToInt(config.dayTheme?.bottomBackground)
            val themeColorNight = hexToInt(config.nightTheme?.primaryColor)
            val secondaryThemeColorNight = hexToInt(config.nightTheme?.accentColor)
            val themeBackgroundColorNight = hexToInt(config.nightTheme?.backgroundColor)
            val labelContainerColorNight = hexToInt(config.nightTheme?.bottomBackground)

            // 底栏配置
            val dayNavConfig = config.dayBottomBarId?.let { id ->
                NavigationBarConfig.loadConfigs(context).firstOrNull { it.isNight == false && it.id == id }
            }
            val nightNavConfig = config.nightBottomBarId?.let { id ->
                NavigationBarConfig.loadConfigs(context).firstOrNull { it.isNight == true && it.id == id }
            }

            // 导航图标
            dayNavConfig?.icons?.forEach { (key, path) ->
                val assetKey = MD3_NAV_ICON_MAP.firstOrNull { it.first == key }?.second
                if (assetKey != null) {
                    val src = File(path).takeIf { it.isFile }
                    if (src != null) {
                        val assetPath = "assets/navigation/${assetKey.substringAfter(".")}.${src.extension.ifBlank { "png" }}"
                        addFileToZip(zip, src, assetPath)
                        assets[assetKey] = assetPath
                    }
                }
            }
            // 夜间图标也写入（如果不同）
            nightNavConfig?.icons?.forEach { (key, path) ->
                val assetKey = MD3_NAV_ICON_MAP.firstOrNull { it.first == key }?.second
                if (assetKey != null && assetKey !in assets) {
                    val src = File(path).takeIf { it.isFile }
                    if (src != null) {
                        val assetPath = "assets/navigation/${assetKey.substringAfter(".")}.${src.extension.ifBlank { "png" }}"
                        addFileToZip(zip, src, assetPath)
                        assets[assetKey] = assetPath
                    }
                }
            }

            // 封面图集
            val coverAlbums = mutableListOf<Md3CoverAlbum>()
            config.dayCoverGroupId?.let { groupId ->
                val repo = CoverGalleryRepository()
                val group = repo.allGroupsWithImages().firstOrNull { it.group.id == groupId }
                if (group != null) {
                    val albumRef = "album_day"
                    val lightImages = group.images.mapIndexed { i, img ->
                        val src = File(img.path).takeIf { it.isFile }
                        if (src == null) return@mapIndexed Md3CoverImage("")
                        val assetPath = "assets/covers/day_$i.${src.extension.ifBlank { "jpg" }}"
                        addFileToZip(zip, src, assetPath)
                        Md3CoverImage(assetPath)
                    }.filter { it.path.isNotEmpty() }
                    coverAlbums.add(Md3CoverAlbum(albumRef, group.group.name, lightImages, emptyList()))
                }
            }
            config.nightCoverGroupId?.let { groupId ->
                val repo = CoverGalleryRepository()
                val group = repo.allGroupsWithImages().firstOrNull { it.group.id == groupId }
                if (group != null) {
                    val albumRef = "album_night"
                    val darkImages = group.images.mapIndexed { i, img ->
                        val src = File(img.path).takeIf { it.isFile }
                        if (src == null) return@mapIndexed Md3CoverImage("")
                        val assetPath = "assets/covers/night_$i.${src.extension.ifBlank { "jpg" }}"
                        addFileToZip(zip, src, assetPath)
                        Md3CoverImage(assetPath)
                    }.filter { it.path.isNotEmpty() }
                    coverAlbums.add(Md3CoverAlbum(albumRef, group.group.name, emptyList(), darkImages))
                }
            }

            // 构建 MD3 配置
            val md3Config = Md3ThemeExportData(
                appTheme = "0",
                themeMode = "0",
                isPureBlack = config.dayTheme?.transparentNavBar ?: false,
                cPrimary = themeColor,
                cNPrimary = themeColorNight,
                themeColor = themeColor,
                secondaryThemeColor = secondaryThemeColor,
                primaryTextColor = 0,
                secondaryTextColor = 0,
                themeBackgroundColor = themeBackgroundColor,
                labelContainerColor = labelContainerColor,
                themeColorNight = themeColorNight,
                secondaryThemeColorNight = secondaryThemeColorNight,
                primaryTextColorNight = 0,
                secondaryTextColorNight = 0,
                themeBackgroundColorNight = themeBackgroundColorNight,
                labelContainerColorNight = labelContainerColorNight,
                bgImageLight = bgImageLight,
                bgImageDark = bgImageDark,
                bgImageBlurring = config.dayTheme?.backgroundImgBlur ?: 0,
                bgImageNBlurring = config.nightTheme?.backgroundImgBlur ?: 0,
                useFloatingBottomBar = dayNavConfig?.layoutMode == NavigationBarConfig.LAYOUT_FLOATING,
                topBarOpacity = 100,
                bottomBarOpacity = dayNavConfig?.opacity ?: 100,
                enableBlur = dayNavConfig?.effectMode == NavigationBarConfig.EFFECT_GLASS
            )

            val manifest = Md3ThemeManifest(
                formatVersion = 1,
                name = config.name,
                config = md3Config,
                assets = assets,
                coverAlbums = coverAlbums,
                coverSelection = Md3CoverSelection(coverAlbums.firstOrNull()?.ref)
            )

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(GSON.toJson(manifest).toByteArray())
            zip.closeEntry()
        }
        return zipFile
    }

    // ─── .red 格式导出 ────────────────────────────────────────────

    @Keep
    private data class RedThemeColors(
        @SerializedName("primaryColor") val primaryColor: String = "",
        @SerializedName("backgroundColor") val backgroundColor: String = "",
        @SerializedName("foregroundColor") val foregroundColor: String = "",
        @SerializedName("mutedForegroundColor") val mutedForegroundColor: String = "",
        @SerializedName("cardColor") val cardColor: String = "",
        @SerializedName("cardForegroundColor") val cardForegroundColor: String = "",
        @SerializedName("cardBackgroundImage") val cardBackgroundImage: String = "",
        @SerializedName("popoverColor") val popoverColor: String = "",
        @SerializedName("dialogBackgroundColor") val dialogBackgroundColor: String = "",
        @SerializedName("mutedColor") val mutedColor: String = "",
        @SerializedName("borderColor") val borderColor: String = "",
        @SerializedName("dividerColor") val dividerColor: String = "",
        @SerializedName("inputBackgroundColor") val inputBackgroundColor: String = "",
        @SerializedName("inputBorderColor") val inputBorderColor: String = "",
        @SerializedName("accentColor") val accentColor: String = "",
        @SerializedName("backgroundImage") val backgroundImage: String = "",
        @SerializedName("backgroundImageFit") val backgroundImageFit: String = "",
        @SerializedName("backgroundImageOpacity") val backgroundImageOpacity: Float? = null,
        @SerializedName("coverGalleryId") val coverGalleryId: String = "",
        @SerializedName("navbarPackId") val navbarPackId: String = ""
    )

    @Keep
    private data class RedThemeV4(
        @SerializedName("name") val name: String = "",
        @SerializedName("light") val light: RedThemeColors? = null,
        @SerializedName("dark") val dark: RedThemeColors? = null
    )

    @Keep
    private data class RedNameMeta(
        @SerializedName("name") val name: String = ""
    )

    /** 当前分支底栏图标 key 前缀 → .red 导航项名 */
    private val RED_NAV_ICON_MAP = mapOf(
        "homepage" to "home",
        "bookshelf" to "bookshelf",
        "discovery" to "feature",
        "rss" to "notes",
        "my" to "settings"
    )

    /** RED 文件魔法头 */
    private val RED_PREFIX = byteArrayOf('R'.code.toByte(), 'E'.code.toByte(), 'D'.code.toByte())
    /** RED04 子格式版本标识 */
    private val RED_VERSION_ZIP = 0x04.toByte()

    /**
     * 导出为 Reeden 阅读 App 的 .red 格式。
     *
     * 生成一个 .red 文件（RED 头 + ZIP 数据），ZIP 内容包含：
     * - theme.json：主题颜色清单
     * - light/theme_bg.img：日间背景图
     * - dark/theme_bg.img：夜间背景图
     * - navbar_pack/{uuid}/：底栏图标包
     * - cover_gallery/{uuid}/：封面图集
     */
    fun exportRed(context: android.content.Context, config: ApplicationThemeManager.Config): File {
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = config.name.normalizeFileName().ifBlank { "application_theme" }
        val redFile = dir.resolve("${exportName}.red")

        // 先创建标准 ZIP，再添加 RED 头
        val tempZip = dir.resolve("${exportName}_temp.zip")

        ZipOutputStream(tempZip.outputStream().buffered()).use { zip ->
            val dayNavbarPackId = UUID.randomUUID().toString()
            val nightNavbarPackId = UUID.randomUUID().toString()
            val dayCoverGalleryId = UUID.randomUUID().toString()
            val nightCoverGalleryId = UUID.randomUUID().toString()

            // 日间背景图
            var dayBgPath: String? = null
            config.dayTheme?.let { theme ->
                dayBgPath = theme.backgroundImgPath?.takeIf { !it.startsWith("http", true) }?.let { path ->
                    val src = File(path).takeIf { it.isFile }
                        ?: appCtx.externalFiles.getFile(PreferKey.bgImage).getFile(path).takeIf { it.isFile }
                    if (src != null && src.isFile) {
                        zip.putNextEntry(ZipEntry("light/theme_bg.img"))
                        src.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                        "light/theme_bg.img"
                    } else null
                }
            }

            // 夜间背景图
            var nightBgPath: String? = null
            config.nightTheme?.let { theme ->
                nightBgPath = theme.backgroundImgPath?.takeIf { !it.startsWith("http", true) }?.let { path ->
                    val src = File(path).takeIf { it.isFile }
                        ?: appCtx.externalFiles.getFile(PreferKey.bgImageN).getFile(path).takeIf { it.isFile }
                    if (src != null && src.isFile) {
                        zip.putNextEntry(ZipEntry("dark/theme_bg.img"))
                        src.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                        "dark/theme_bg.img"
                    } else null
                }
            }

            // 底栏图标包
            config.dayBottomBarId?.let { id ->
                val navConfig = NavigationBarConfig.loadConfigs(context)
                    .firstOrNull { it.isNight == false && it.id == id }
                if (navConfig != null) {
                    writeRedNavBarPack(zip, navConfig, dayNavbarPackId)
                }
            }
            config.nightBottomBarId?.let { id ->
                val navConfig = NavigationBarConfig.loadConfigs(context)
                    .firstOrNull { it.isNight == true && it.id == id }
                if (navConfig != null) {
                    writeRedNavBarPack(zip, navConfig, nightNavbarPackId)
                }
            }

            // 封面图集
            config.dayCoverGroupId?.let { groupId ->
                val repo = CoverGalleryRepository()
                val group = repo.allGroupsWithImages().firstOrNull { it.group.id == groupId }
                if (group != null) {
                    writeRedCoverGallery(zip, group, dayCoverGalleryId)
                }
            }
                        config.nightCoverGroupId?.let { groupId ->
                val repo = CoverGalleryRepository()
                val group = repo.allGroupsWithImages().firstOrNull { it.group.id == groupId }
                if (group != null) {
                    writeRedCoverGallery(zip, group, nightCoverGalleryId)
                }
            }

            // 主题颜色
            val lightColors = config.dayTheme?.let { theme ->
                buildRedColors(theme, isNight = false, dayNavbarPackId, dayCoverGalleryId, dayBgPath)
            }
            val darkColors = config.nightTheme?.let { theme ->
                buildRedColors(theme, isNight = true, nightNavbarPackId, nightCoverGalleryId, nightBgPath)
            }

            val redTheme = RedThemeV4(
                name = config.name,
                light = lightColors,
                dark = darkColors
            )

            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write(GSON.toJson(redTheme).toByteArray())
            zip.closeEntry()
        }

        // 将标准 ZIP 包装为 .red 格式（RED 头 + ZIP 数据）
        FileOutputStream(redFile).use { output ->
            output.write(RED_PREFIX)
            output.write(RED_VERSION_ZIP.toInt())
            tempZip.inputStream().buffered().use { it.copyTo(output) }
        }
        tempZip.delete()

        return redFile
    }

    /**
     * 从当前分支的 [ThemeConfig.Config] 构建 .red 格式所需的完整颜色配置。
     *
     * 当前分支只有 5 个颜色字段，但 Reeden 应用依赖完整的颜色体系。
     * 此方法根据已有颜色推导出 foregroundColor、borderColor 等字段，
     * 避免导出空字符串导致 Reeden 将文字/图标渲染为透明。
     */
    private fun buildRedColors(
        theme: ThemeConfig.Config,
        isNight: Boolean,
        navbarPackId: String,
        coverGalleryId: String,
        backgroundImage: String?
    ): RedThemeColors {
        val primary = theme.primaryColor.ifBlank { if (isNight) "#FFFFFFFF" else "#FF4A775C" }
        val bg = theme.backgroundColor.ifBlank { if (isNight) "#FF000000" else "#FFF5F9F2" }
        val card = theme.bottomBackground.ifBlank { if (isNight) "#FF1A1A1A" else "#FFFCFDFA" }
        val accent = theme.accentColor.ifBlank { primary }

        // 前景文字颜色：日间深色，夜间白色
        val foreground = if (isNight) "#FFFFFFFF" else "#FF1A1A1A"
        // 次要文字颜色
        val mutedForeground = if (isNight) "#FFAAAAAA" else "#FF999999"
        // 边框/分割线颜色
        val border = if (isNight) "#33FFFFFF" else "#33000000"
        // 弹出层/对话框背景
        val popover = card
        // 输入框背景
        val inputBg = if (isNight) "#FF2A2A2A" else "#FFF0F0F0"

        return RedThemeColors(
            primaryColor = primary,
            backgroundColor = bg,
            foregroundColor = foreground,
            mutedForegroundColor = mutedForeground,
            cardColor = card,
            cardForegroundColor = foreground,
            cardBackgroundImage = "",
            popoverColor = popover,
            dialogBackgroundColor = popover,
            mutedColor = card,
            borderColor = border,
            dividerColor = border,
            inputBackgroundColor = inputBg,
            inputBorderColor = border,
            accentColor = accent,
            backgroundImage = backgroundImage ?: "",
            backgroundImageFit = "cover",
            backgroundImageOpacity = 1.0f,
            coverGalleryId = coverGalleryId,
            navbarPackId = navbarPackId
        )
    }

    /** 将底栏图标包写入 .red zip */
    private fun writeRedNavBarPack(zip: ZipOutputStream, navConfig: NavigationBarConfig, packId: String) {
        val baseDir = "navbar_pack/$packId"
        // 写入 meta.json
        zip.putNextEntry(ZipEntry("$baseDir/meta.json"))
        zip.write(GSON.toJson(RedNameMeta(navConfig.name)).toByteArray())
        zip.closeEntry()

        // 写入图标文件
        navConfig.icons.forEach { (key, path) ->
            val src = File(path).takeIf { it.isFile } ?: return@forEach
            // 从 key 中提取导航项名和状态（如 "homepage_normal" → "homepage", "normal"）
            val parts = key.split("_")
            if (parts.size < 2) return@forEach
            val itemKey = parts[0]
            val state = parts[1]
            val redItemName = RED_NAV_ICON_MAP[itemKey] ?: return@forEach
            val ext = src.extension.ifBlank { "png" }
            val entryPath = "$baseDir/${redItemName}_$state.$ext"
            zip.putNextEntry(ZipEntry(entryPath))
            src.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** 将封面图集写入 .red zip */
    private fun writeRedCoverGallery(
        zip: ZipOutputStream,
        group: io.legado.app.data.entities.CoverGalleryGroupWithImages,
        galleryId: String
    ) {
        val baseDir = "cover_gallery/$galleryId"
        // 写入 meta.json
        zip.putNextEntry(ZipEntry("$baseDir/meta.json"))
        zip.write(GSON.toJson(RedNameMeta(group.group.name)).toByteArray())
        zip.closeEntry()

        // 写入图片文件
        group.images.forEachIndexed { index, image ->
            File(image.path).takeIf { it.isFile }?.let { src ->
                val ext = src.extension.ifBlank { "jpg" }
                zip.putNextEntry(ZipEntry("$baseDir/cover_$index.$ext"))
                src.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}