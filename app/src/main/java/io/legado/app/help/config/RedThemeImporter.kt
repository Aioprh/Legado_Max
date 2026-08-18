package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry

/**
 * .red 主题包中的导航图标文件名 → 当前分支图标 key 的映射。
 *
 * .red 格式（来自 Reeden 阅读 App）的底栏图标文件名格式为 `{item}_{state}.png`，
 * state 可能是 `normal`（未选中态）或 `selected`（选中态）。
 * 部分主题包只提供 `selected` 状态，此时导入时用 selected 图标兜底作为 normal。
 *
 * 当前分支的图标 key 格式为 `{key}_selected` / `{key}_normal`。
 *
 * .red 导航项名 → 当前分支导航项 key：
 * - home → homepage
 * - bookshelf → bookshelf（相同）
 * - feature → discovery
 * - notes → rss
 * - settings → my
 * - statistics → 无对应（当前分支没有统计页导航项，忽略）
 */
private val RED_NAV_ICON_MAP = mapOf(
    "home" to "homepage",
    "bookshelf" to "bookshelf",
    "feature" to "discovery",
    "notes" to "rss",
    "settings" to "my"
)

/**
 * 兼容导入 .red 格式主题包。
 *
 * .red 文件来自 Reeden 阅读 App，其 ZIP 内容结构为：
 * ```
 * theme.json                          — 主题清单（颜色配置 + 资源引用 ID）
 * light/                              — 日间模式资源
 *   theme_bg.img                      — 主题背景图
 *   card_bg.img                       — 卡片背景图
 *   bookshelf_carousel/               — 书架轮播图
 * dark/                               — 夜间模式资源（可选）
 * cover_gallery/{uuid}/               — 封面图集
 *   meta.json                         — 图集元信息
 *   *.jpg                             — 封面图片
 * navbar_pack/{uuid}/                 — 底栏图标包
 *   meta.json                         — 图标包元信息
 *   {item}_normal.png                 — 各导航项的未选中状态图标（部分主题包可能缺失）
 *   {item}_selected.png               — 各导航项的选中状态图标
 * reader_schema/{uuid}/               — 阅读页配色方案（当前分支暂不使用）
 *   schema.json                       — 阅读页配置
 *   bg.img                            — 阅读页背景图
 * custom_splash/{uuid}/               — 自定义启动屏（当前分支暂不使用）
 *   meta.json                         — 启动屏元信息
 *   asset.png                         — 启动屏图片
 * ```
 *
 * 导入时将颜色配置映射为当前分支的 [ThemeConfig.Config]，
 * 提取背景图、卡片背景图、底栏图标和封面图集资源。
 */
internal object RedThemeImporter {

    /** theme.json 中的主题颜色配置 */
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
        @SerializedName("navbarPackId") val navbarPackId: String = "",
        @SerializedName("readerColorSchemaId") val readerColorSchemaId: String = "",
        @SerializedName("customSplashId") val customSplashId: String = "",
        @SerializedName("cardShadow") val cardShadow: Int? = null,
        @SerializedName("cardBackgroundBlur") val cardBackgroundBlur: Float? = null,
        @SerializedName("cardBorder") val cardBorder: Boolean = false,
        @SerializedName("searchFieldBorder") val searchFieldBorder: Boolean = false,
        @SerializedName("searchFieldBackgroundColor") val searchFieldBackgroundColor: String = "",
        @SerializedName("switchBorder") val switchBorder: Boolean = false,
        @SerializedName("tabBorder") val tabBorder: Boolean = false,
        @SerializedName("tabBackgroundColor") val tabBackgroundColor: String = "",
        @SerializedName("shelfColor") val shelfColor: String = "",
        @SerializedName("bookshelfCarouselImageUrls") val bookshelfCarouselImageUrls: List<String>? = null,
        @SerializedName("themeEffectType") val themeEffectType: String = "",
        @SerializedName("description") val description: String = ""
    )

    /** theme.json 的顶层结构 */
    @Keep
    private data class RedThemeV4(
        @SerializedName("name") val name: String = "",
        @SerializedName("light") val light: RedThemeColors? = null,
        @SerializedName("dark") val dark: RedThemeColors? = null
    )

    /** GZIP 类 .red 文件的顶层结构 */
    @Keep
    private data class RedThemePackage(
        @SerializedName("version") val version: Int = 1,
        @SerializedName("type") val type: String = "",
        @SerializedName("data") val data: List<RedThemeItem> = emptyList()
    )

    /** GZIP 类 .red 文件中的单个主题项 */
    @Keep
    private data class RedThemeItem(
        @SerializedName("name") val name: String = "",
        @SerializedName("light") val light: RedThemeColors? = null,
        @SerializedName("dark") val dark: RedThemeColors? = null,
        @SerializedName("lightBackgroundImage") val lightBackgroundImage: String = "",
        @SerializedName("darkBackgroundImage") val darkBackgroundImage: String = ""
    )

    /** 封面图集 / 底栏图标包的 meta.json 通用结构 */
    @Keep
    private data class RedNameMeta(
        @SerializedName("name") val name: String = ""
    )

    /**
     * 导入 GZIP 类 .red 格式的主题包。
     *
     * GZIP 类 .red 文件解压后得到 JSON，结构为 [RedThemePackage]：
     * `{ version, type: "theme", data: [{ name, light, dark, lightBackgroundImage, darkBackgroundImage }] }`。
     *
     * 与 ZIP 类格式不同，GZIP 类格式只有颜色配置和内联背景图（base64 编码），
     * 没有底栏图标、封面图集等资源文件。
     *
     * @param json 解压后的 JSON 文本
     * @param options 导入选项
     * @return 导入后的 Config
     */
    suspend fun importGzipJson(json: String, options: ApplicationThemeManager.ImportOptions?): ApplicationThemeManager.Config {
        val redPackage = GSON.fromJsonObject<RedThemePackage>(json).getOrThrow()
        if (redPackage.type != "theme" || redPackage.data.isEmpty()) {
            throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))
        }

        // 取第一个主题项（GZIP 格式通常只有一个）
        val item = redPackage.data.first()
        val themeName = item.name.ifBlank { "RED 主题" }

        val importDayTheme = options?.importDayTheme ?: true
        val importNightTheme = options?.importNightTheme ?: true

        var dayTheme: ThemeConfig.Config? = null
        var nightTheme: ThemeConfig.Config? = null

        if (importDayTheme && item.light != null) {
            dayTheme = convertGzipTheme(item.light, themeName, isNight = false, inlineBg = item.lightBackgroundImage)
            ThemeConfig.addConfig(dayTheme)
        }
        if (importNightTheme && item.dark != null) {
            nightTheme = convertGzipTheme(item.dark, themeName, isNight = true, inlineBg = item.darkBackgroundImage)
            ThemeConfig.addConfig(nightTheme)
        }

        val config = ApplicationThemeManager.Config(
            id = UUID.randomUUID().toString(),
            name = themeName,
            dayTheme = dayTheme,
            nightTheme = nightTheme,
            dayTopBarDir = TopBarConfig.DEFAULT_DIR_NAME,
            nightTopBarDir = TopBarConfig.DEFAULT_DIR_NAME
        )
        return ApplicationThemeManager.addImported(
            ApplicationThemeManager.stripComponents(config, options)
        )
    }

    /**
     * 将 GZIP 类 .red 的颜色配置转换为 [ThemeConfig.Config]。
     *
     * 背景图可能是 base64 编码的内联图片，需要解码后保存到文件。
     */
    private fun convertGzipTheme(
        colors: RedThemeColors,
        name: String,
        isNight: Boolean,
        inlineBg: String
    ): ThemeConfig.Config {
        val backgroundImgPath = inlineBg.takeIf { it.isNotBlank() }?.let { encoded ->
            try {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                val dir = appCtx.externalFiles
                    .getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage)
                    .apply { mkdirs() }
                val target = dir.getFile("red_theme_${UUID.randomUUID()}.jpg")
                target.writeBytes(bytes)
                target.absolutePath
            } catch (e: Exception) {
                null
            }
        }

        return ThemeConfig.Config(
            themeName = name.trim().ifBlank { "RED 主题" },
            isNightTheme = isNight,
            primaryColor = colors.primaryColor.ifBlank { if (isNight) "#FFFFFFFF" else "#FF4A775C" },
            accentColor = colors.accentColor.ifBlank { colors.primaryColor.ifBlank { "#FF4A775C" } },
            backgroundColor = colors.backgroundColor.ifBlank { if (isNight) "#FF000000" else "#FFF5F9F2" },
            bottomBackground = colors.cardColor.ifBlank { colors.mutedColor.ifBlank { if (isNight) "#FF000000" else "#FFFCFDFA" } },
            transparentNavBar = false,
            backgroundImgPath = backgroundImgPath,
            backgroundImgBlur = 0
        )
    }

    /**
     * 导入 .red 格式的主题包。
     *
     * 调用前需已通过 [RedAssetPackage.zipPayload] 剥离 RED 头部，
     * 得到标准 ZIP 文件。本方法负责解压 ZIP 并解析其中的 theme.json。
     *
     * @param zip 已打开的 ZipFile（内容为 .red 剥离头部后的 ZIP）
     * @param temp 临时解压目录
     * @param themeJsonEntry theme.json 的 ZipEntry
     * @param options 导入选项
     * @return 导入后的 Config
     */
    suspend fun import(
        zip: ZipFile,
        temp: File,
        themeJsonEntry: ZipEntry,
        options: ApplicationThemeManager.ImportOptions?
    ): ApplicationThemeManager.Config {
        require(themeJsonEntry.size in 0..ApplicationThemeManager.maxManifestBytes) {
            appCtx.getString(R.string.app_theme_manifest_too_large)
        }

        // 读取 theme.json
        val themeJson = zip.getInputStream(themeJsonEntry).bufferedReader().use { it.readText() }
        val redTheme = GSON.fromJsonObject<RedThemeV4>(themeJson).getOrThrow()
        val themeName = redTheme.name.ifBlank { "RED 主题" }

        // 解压所有文件到临时目录
        val unzipDir = temp.resolve("red_content_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val target = File(unzipDir, entry.name)
                val canonicalTarget = unzipDir.canonicalPath
                val canonicalChild = target.canonicalPath
                if (!canonicalChild.startsWith(canonicalTarget)) {
                    throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }

            val importDayTheme = options?.importDayTheme ?: true
            val importNightTheme = options?.importNightTheme ?: true
            val importDayBottomBar = options?.importDayBottomBar ?: true
            val importNightBottomBar = options?.importNightBottomBar ?: true
            val importDayCover = options?.importDayCover ?: true
            val importNightCover = options?.importNightCover ?: true

            // 导入日间/夜间主题
            var dayTheme: ThemeConfig.Config? = null
            var nightTheme: ThemeConfig.Config? = null
            if (importDayTheme && redTheme.light != null) {
                dayTheme = convertTheme(redTheme.light, themeName, isNight = false, root = unzipDir)
                ThemeConfig.addConfig(dayTheme)
            }
            if (importNightTheme && redTheme.dark != null) {
                nightTheme = convertTheme(redTheme.dark, themeName, isNight = true, root = unzipDir)
                ThemeConfig.addConfig(nightTheme)
            }

            // 导入底栏图标包
            var dayBottomBarId: String? = null
            var nightBottomBarId: String? = null
            if (importDayBottomBar || importNightBottomBar) {
                val dayNavBar = importNavBarPack(unzipDir, themeName, redTheme.light, redTheme.dark, isNight = false)
                val nightNavBar = importNavBarPack(unzipDir, themeName, redTheme.dark, redTheme.light, isNight = true)
                if (dayNavBar != null && importDayBottomBar) dayBottomBarId = dayNavBar
                if (nightNavBar != null && importNightBottomBar) nightBottomBarId = nightNavBar
                // 如果只有一套底栏图标，复制到另一套
                if (dayBottomBarId == null && nightBottomBarId != null && importDayBottomBar) {
                    dayBottomBarId = nightBottomBarId
                }
                if (nightBottomBarId == null && dayBottomBarId != null && importNightBottomBar) {
                    nightBottomBarId = dayBottomBarId
                }
            }

            // 导入封面图集
            var dayCoverGroupId: Long? = null
            var nightCoverGroupId: Long? = null
            if (importDayCover) {
                dayCoverGroupId = importCoverGallery(unzipDir, redTheme.light, themeName, isNight = false)
            }
            if (importNightCover) {
                nightCoverGroupId = importCoverGallery(unzipDir, redTheme.dark, themeName, isNight = true)
            }
            // 如果只有一套封面图集，共用
            if (dayCoverGroupId == null && nightCoverGroupId != null && importDayCover) {
                dayCoverGroupId = nightCoverGroupId
            }
            if (nightCoverGroupId == null && dayCoverGroupId != null && importNightCover) {
                nightCoverGroupId = dayCoverGroupId
            }

            // 顶栏配置（.red 没有顶栏配置，使用默认）
            val dayTopBarDir = TopBarConfig.DEFAULT_DIR_NAME
            val nightTopBarDir = TopBarConfig.DEFAULT_DIR_NAME

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
        } finally {
            unzipDir.deleteRecursively()
        }
    }

    /**
     * 将 .red 的颜色配置转换为当前分支的 [ThemeConfig.Config]。
     *
     * .red 格式使用 `#AARRGGBB` 颜色格式，与当前分支一致。
     * 背景图从 `light/theme_bg.img` 或 `dark/theme_bg.img` 中提取。
     */
    private fun convertTheme(
        colors: RedThemeColors,
        name: String,
        isNight: Boolean,
        root: File
    ): ThemeConfig.Config {
        val modeDir = if (isNight) "dark" else "light"

        // 提取主题背景图
        val backgroundImgPath = File(root, "$modeDir/theme_bg.img")
            .takeIf { it.isFile }
            ?.let { bgFile ->
                val dir = appCtx.externalFiles
                    .getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage)
                    .apply { mkdirs() }
                val target = dir.getFile("red_theme_${UUID.randomUUID()}.${bgFile.extension.ifBlank { "jpg" }}")
                bgFile.copyTo(target, overwrite = true)
                target.absolutePath
            }

        return ThemeConfig.Config(
            themeName = name.trim().ifBlank { "RED 主题" },
            isNightTheme = isNight,
            primaryColor = colors.primaryColor.ifBlank { if (isNight) "#FFFFFFFF" else "#FF4A775C" },
            accentColor = colors.accentColor.ifBlank { colors.primaryColor.ifBlank { "#FF4A775C" } },
            backgroundColor = colors.backgroundColor.ifBlank { if (isNight) "#FF000000" else "#FFF5F9F2" },
            bottomBackground = colors.cardColor.ifBlank { colors.mutedColor.ifBlank { if (isNight) "#FF000000" else "#FFFCFDFA" } },
            transparentNavBar = false,
            backgroundImgPath = backgroundImgPath,
            backgroundImgBlur = 0
        )
    }

    /**
     * 导入 .red 格式的底栏图标包。
     *
     * .red 的底栏图标存储在 `navbar_pack/{uuid}/` 目录中，
     * 文件名格式为 `{item}_{state}.png`（如 `home_selected.png`、`home_normal.png`）。
     *
     * 支持两种情况：
     * - 主题包含 normal 和 selected 两种状态的图标：分别导入，保留原始状态。
     * - 主题只含 selected 状态：用 selected 图标兜底作为 normal 图标
     *   （当前分支要求至少有 normal 状态才能正常显示）。
     */
    private fun importNavBarPack(
        root: File,
        fallbackName: String,
        colors: RedThemeColors?,
        fallbackColors: RedThemeColors?,
        isNight: Boolean
    ): String? {
        val navColors = colors?.takeIf { it.navbarPackId.isNotBlank() }
            ?: fallbackColors?.takeIf { it.navbarPackId.isNotBlank() }
            ?: return null
        val navbarRoot = File(root, "navbar_pack").takeIf { it.isDirectory } ?: return null
        val packId = navColors.navbarPackId
        // 查找图标目录：优先用 ID 匹配，否则取第一个子目录或直接在 navbarRoot 下
        val sourceDir = File(navbarRoot, packId).takeIf { it.isDirectory }
            ?: navbarRoot.listFiles()?.firstOrNull { it.isDirectory }
            ?: navbarRoot.takeIf { it.listFiles()?.any { f -> f.isFile } == true }
            ?: return null

        val iconDir = appCtx.externalFiles
            .getFile("navigationBarIcons", UUID.randomUUID().toString())
            .apply { mkdirs() }

        val icons = mutableMapOf<String, String>()
        // 第一遍：遍历目录中的所有图片文件，按原状态导入
        sourceDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val fileName = file.nameWithoutExtension.lowercase()
            val extension = file.extension.ifBlank { "png" }
            // 解析图标名：{item}_{state}
            val parts = fileName.split("_")
            if (parts.size < 2) return@forEach
            val redItemKey = parts[0]
            val state = parts.getOrElse(1) { "" }
            // 映射到当前分支的导航项 key
            val localItemKey = RED_NAV_ICON_MAP[redItemKey] ?: return@forEach
            // 只处理 normal 和 selected 两种状态
            if (state != "normal" && state != "selected") return@forEach

            val targetFile = iconDir.getFile("${localItemKey}_$state.$extension")
            file.copyTo(targetFile, overwrite = true)
            icons["${localItemKey}_$state"] = targetFile.absolutePath
        }

        // 第二遍：对缺少 normal 状态的导航项，用 selected 图标兜底
        RED_NAV_ICON_MAP.values.forEach { localItemKey ->
            val normalKey = "${localItemKey}_normal"
            if (normalKey !in icons) {
                val selectedKey = "${localItemKey}_selected"
                val selectedPath = icons[selectedKey]
                if (selectedPath != null) {
                    val selectedFile = File(selectedPath)
                    val normalTarget = iconDir.getFile("$normalKey.${selectedFile.extension.ifBlank { "png" }}")
                    selectedFile.copyTo(normalTarget, overwrite = true)
                    icons[normalKey] = normalTarget.absolutePath
                }
            }
        }

        if (icons.isEmpty()) return null

        // 读取 meta.json 获取名称
        val metaName = File(sourceDir, "meta.json").takeIf { it.isFile }
            ?.let { GSON.fromJsonObject<RedNameMeta>(it.readText()).getOrNull()?.name }
            ?: fallbackName

        val navConfig = NavigationBarConfig(
            id = UUID.randomUUID().toString(),
            name = metaName.ifBlank { fallbackName },
            isNight = isNight,
            isBuiltin = false,
            layoutMode = NavigationBarConfig.LAYOUT_FLOATING,
            effectMode = NavigationBarConfig.EFFECT_GLASS,
            opacity = 76,
            icons = icons
        )

        // 同名覆盖
        val existing = NavigationBarConfig.loadConfigs(appCtx)
        val existingIndex = existing.indexOfFirst {
            it.isNight == isNight && it.name == navConfig.name.trim() && !it.isBuiltin
        }
        val id = if (existingIndex >= 0) existing[existingIndex].id else navConfig.id
        val finalConfig = navConfig.copy(id = id)
        if (existingIndex >= 0) existing[existingIndex] = finalConfig else existing.add(finalConfig)
        NavigationBarConfig.saveConfigs(appCtx, existing)
        return id
    }

    /**
     * 导入 .red 格式的封面图集。
     *
     * .red 的封面图集存储在 `cover_gallery/{uuid}/` 目录中，
     * 包含一个 `meta.json` 和若干图片文件（`.jpg` 等）。
     */
    private suspend fun importCoverGallery(
        root: File,
        colors: RedThemeColors?,
        fallbackName: String,
        isNight: Boolean
    ): Long? {
        val galleryId = colors?.coverGalleryId?.takeIf { it.isNotBlank() } ?: return null
        val sourceDir = File(root, "cover_gallery/$galleryId").takeIf { it.isDirectory } ?: return null

        // 读取 meta.json 获取图集名称
        val galleryName = File(sourceDir, "meta.json").takeIf { it.isFile }
            ?.let { GSON.fromJsonObject<RedNameMeta>(it.readText()).getOrNull()?.name }
            ?: fallbackName

        // 收集所有图片文件
        val imageFiles = sourceDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".json") }
            .toList()

        if (imageFiles.isEmpty()) return null

        require(imageFiles.size <= ApplicationThemeManager.maxCoverImages) {
            appCtx.getString(R.string.app_theme_too_many_cover_images)
        }

        val repository = CoverGalleryRepository()
        val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == galleryName }
        val groupId = existingGroup?.group?.id ?: repository.addGroup(galleryName)
        repository.addImageFiles(appCtx, groupId, imageFiles)
        return groupId
    }
}
