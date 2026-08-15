package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import androidx.annotation.Keep
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.R
import io.legado.app.utils.compress.ZipUtils
import splitties.init.appCtx
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.util.UUID
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

/**
 * 应用主题管理器。
 *
 * 将主题、顶栏、底栏、封面图集四个维度的配置组合为一个
 * 「应用主题」整体方案，支持一键切换、导出/导入 zip 包。
 *
 * 导出的 zip 包含 application_theme.json 清单文件和所有
 * 关联资源（背景图、壁纸、图标、封面图片）。
 * 导入时会自动解压资源、创建对应的子配置项。
 *
 * 配置存储在 filesDir/applicationThemes.json 中，单文件最大 5MB。
 */
object ApplicationThemeManager {

    private const val fileName = "applicationThemes.json"
    private const val currentIdKey = "currentApplicationThemeId"
    private const val maxConfigBytes = 5L * 1024 * 1024
    private const val maxManifestBytes = 2L * 1024 * 1024
    private const val maxAssetBytes = 64L * 1024 * 1024
    private const val maxCoverImages = 500
    private val filePath = FileUtils.getPath(appCtx.filesDir, fileName)

    /**
     * 应用主题配置数据类。
     *
     * 将日间/夜间两套子配置组合在一起。
     *
     * @property id 唯一标识符
     * @property name 主题名称
     * @property dayTheme 日间主题配置（[ThemeConfig.Config]），null 表示不设置
     * @property nightTheme 夜间主题配置，null 表示不设置
     * @property dayTopBarDir 日间顶栏配置包目录名
     * @property nightTopBarDir 夜间顶栏配置包目录名
     * @property dayBottomBarId 日间底栏配置 ID
     * @property nightBottomBarId 夜间底栏配置 ID
     * @property dayCoverGroupId 日间封面图集组 ID
     * @property nightCoverGroupId 夜间封面图集组 ID
     * @property updatedAt 最后更新时间戳
     */
    @Keep
    data class Config(
        val id: String = UUID.randomUUID().toString(),
        var name: String = "",
        var dayTheme: ThemeConfig.Config? = null,
        var nightTheme: ThemeConfig.Config? = null,
        var dayTopBarDir: String = TopBarConfig.DEFAULT_DIR_NAME,
        var nightTopBarDir: String = TopBarConfig.DEFAULT_DIR_NAME,
        var dayBottomBarId: String? = null,
        var nightBottomBarId: String? = null,
        var dayCoverGroupId: Long? = null,
        var nightCoverGroupId: Long? = null,
        var updatedAt: Long = System.currentTimeMillis()
    )

    /** zip 包内部数据结构，包含清单和各子配置的打包数据 */
    @Keep
    private data class PackageData(
        val version: Int = 1,
        val config: Config,
        val dayTopBar: TopBarConfig.Config? = null,
        val nightTopBar: TopBarConfig.Config? = null,
        val dayBottomBar: NavigationBarConfig? = null,
        val nightBottomBar: NavigationBarConfig? = null,
        val dayCover: CoverPayload? = null,
        val nightCover: CoverPayload? = null
    )

    /** zip 包内封面图集的载荷数据 */
    @Keep
    private data class CoverPayload(
        val name: String,
        val images: List<String>
    )

    /** 导入应用主题时的可选创建范围，日夜间可独立控制 */
    @Keep
    data class ImportOptions(
        val importDayTheme: Boolean = true,
        val importNightTheme: Boolean = true,
        val importDayTopBar: Boolean = true,
        val importNightTopBar: Boolean = true,
        val importDayBottomBar: Boolean = true,
        val importNightBottomBar: Boolean = true,
        val importDayCover: Boolean = true,
        val importNightCover: Boolean = true
    )

    /** 将导入选项持久化到 SharedPreferences */
    fun saveImportOptions(context: Context, options: ImportOptions) {
        context.putPrefBoolean(PreferKey.appThemeImportDayTheme, options.importDayTheme)
        context.putPrefBoolean(PreferKey.appThemeImportNightTheme, options.importNightTheme)
        context.putPrefBoolean(PreferKey.appThemeImportDayTopBar, options.importDayTopBar)
        context.putPrefBoolean(PreferKey.appThemeImportNightTopBar, options.importNightTopBar)
        context.putPrefBoolean(PreferKey.appThemeImportDayBottomBar, options.importDayBottomBar)
        context.putPrefBoolean(PreferKey.appThemeImportNightBottomBar, options.importNightBottomBar)
        context.putPrefBoolean(PreferKey.appThemeImportDayCover, options.importDayCover)
        context.putPrefBoolean(PreferKey.appThemeImportNightCover, options.importNightCover)
    }

    /** 从 SharedPreferences 读取持久化的导入选项，默认全部为 true */
    fun getImportOptions(context: Context): ImportOptions {
        return ImportOptions(
            importDayTheme = context.getPrefBoolean(PreferKey.appThemeImportDayTheme, true),
            importNightTheme = context.getPrefBoolean(PreferKey.appThemeImportNightTheme, true),
            importDayTopBar = context.getPrefBoolean(PreferKey.appThemeImportDayTopBar, true),
            importNightTopBar = context.getPrefBoolean(PreferKey.appThemeImportNightTopBar, true),
            importDayBottomBar = context.getPrefBoolean(PreferKey.appThemeImportDayBottomBar, true),
            importNightBottomBar = context.getPrefBoolean(PreferKey.appThemeImportNightBottomBar, true),
            importDayCover = context.getPrefBoolean(PreferKey.appThemeImportDayCover, true),
            importNightCover = context.getPrefBoolean(PreferKey.appThemeImportNightCover, true)
        )
    }

    /** 从文件加载所有应用主题配置，自动校验大小和格式 */
    fun load(): MutableList<Config> {
        val file = File(filePath)
        if (!file.isFile) return mutableListOf()
        require(file.length() <= maxConfigBytes) { appCtx.getString(R.string.app_theme_config_too_large) }
        val parsed = GSON.fromJsonArray<Config>(file.readText()).getOrElse {
            throw IllegalStateException(appCtx.getString(R.string.app_theme_config_corrupted), it)
        }
        return parsed.map { sanitize(it) }.toMutableList()
    }

    /** 获取当前激活的应用主题 ID */
    fun currentId(context: Context): String = context.getPrefString(currentIdKey).orEmpty()

    /** 按 ID 查找配置 */
    fun find(id: String): Config? = load().firstOrNull { it.id == id }

    /** 导出当前应用主题为 zip 文件（含所有关联资源） */
    fun exportCurrent(context: Context): File {
        val current = (load().firstOrNull { isCurrent(context, it) }
            ?: captureCurrent(context, appCtx.getString(io.legado.app.R.string.application_theme_manage)))
            .let { config ->
                // 从 SharedPreferences 刷新背景图路径，确保导出的是当前实际使用的背景图。
                // 用户可能通过「主题设置」偏好页面修改了背景图，而已保存的应用主题配置中
                // 的 backgroundImgPath 可能仍是旧值。
                config.copy(
                    dayTheme = config.dayTheme?.copy(
                        backgroundImgPath = context.getPrefString(PreferKey.bgImage)
                    ),
                    nightTheme = config.nightTheme?.copy(
                        backgroundImgPath = context.getPrefString(PreferKey.bgImageN)
                    )
                )
            }
        validateForApply(context, current)
        return exportConfig(context, current)
    }

    /** 导出指定应用主题为 zip 文件（含所有关联资源） */
    fun exportConfig(context: Context, config: Config): File {
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = config.name.normalizeFileName().ifBlank { "application_theme" }
        return dir.resolve("$exportName.zip").apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                val packagedConfig = config.copy(
                    dayTheme = packageTheme(zip, config.dayTheme, "themes/day"),
                    nightTheme = packageTheme(zip, config.nightTheme, "themes/night")
                )
                val data = PackageData(
                    config = packagedConfig,
                    dayTopBar = packageTopBar(zip, context, false, config.dayTopBarDir, "topbar/day"),
                    nightTopBar = packageTopBar(zip, context, true, config.nightTopBarDir, "topbar/night"),
                    dayBottomBar = packageBottomBar(zip, context, false, config.dayBottomBarId, "bottombar/day"),
                    nightBottomBar = packageBottomBar(zip, context, true, config.nightBottomBarId, "bottombar/night"),
                    dayCover = packageCover(zip, config.dayCoverGroupId, "covers/day"),
                    nightCover = packageCover(zip, config.nightCoverGroupId, "covers/night")
                )
                zip.putNextEntry(ZipEntry("application_theme.json"))
                zip.write(GSON.toJson(data).toByteArray())
                zip.closeEntry()
            }
        }
    }

    /**
     * 导入应用主题文件。
     * 支持 zip（含资源）和纯 json（仅配置）两种格式。
     * 自动处理名称冲突，添加数字后缀。
     * @param options 导入选项，控制是否创建各子配置；null 表示全部创建
     */
    suspend fun importFile(file: File, options: ImportOptions? = null): Config {
        val isZip = file.inputStream().use { input ->
            input.read() == 'P'.code && input.read() == 'K'.code
        }
        if (isZip) return importZip(file, options)
        require(file.length() <= maxManifestBytes) { appCtx.getString(R.string.app_theme_file_too_large) }
        val imported = sanitize(
            GSON.fromJson(file.readText(), Config::class.java)
                ?: throw IllegalArgumentException("Invalid application theme")
        )
        return addImported(stripComponents(imported, options))
    }

    private suspend fun importZip(file: File, options: ImportOptions?): Config {
        val temp = appCtx.cacheDir.resolve("applicationThemeImport/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipFile(file).use { zip ->
                // 检测 zip 包内的清单文件类型，兼容三种格式
                val manifestEntry = zip.getEntry("application_theme.json")
                val appearanceKitEntry = zip.entries().asSequence()
                    .firstOrNull { !it.isDirectory && (it.name == "appearance_kit.json" || it.name.endsWith("/appearance_kit.json")) }
                val md3ManifestEntry = zip.getEntry("manifest.json")

                when {
                    // 当前分支格式
                    manifestEntry != null -> return importNativeFormat(zip, temp, manifestEntry, options)
                    // archive_primate_beta 分支格式
                    appearanceKitEntry != null -> return importAppearanceKitFormat(zip, temp, appearanceKitEntry, options)
                    // MD3-main 分支格式
                    md3ManifestEntry != null -> return importMd3Format(zip, temp, md3ManifestEntry, options)
                    else -> throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_manifest))
                }
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    /** 导入当前分支的原生格式（application_theme.json） */
    private suspend fun importNativeFormat(
        zip: ZipFile,
        temp: File,
        manifestEntry: ZipEntry,
        options: ImportOptions?
    ): Config {
        require(manifestEntry.size in 0..maxManifestBytes) { appCtx.getString(R.string.app_theme_manifest_too_large) }
        val data = zip.getInputStream(manifestEntry).bufferedReader().use {
            GSON.fromJson(it, PackageData::class.java)
        } ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))
        require(data.version == 1) { appCtx.getString(R.string.app_theme_unsupported_version) }
        validatePackage(zip, data, options)
        val source = sanitize(data.config)
        val importDayTheme = options?.importDayTheme ?: true
        val importNightTheme = options?.importNightTheme ?: true
        val importDayTopBar = options?.importDayTopBar ?: true
        val importNightTopBar = options?.importNightTopBar ?: true
        val importDayBottomBar = options?.importDayBottomBar ?: true
        val importNightBottomBar = options?.importNightBottomBar ?: true
        val importDayCover = options?.importDayCover ?: true
        val importNightCover = options?.importNightCover ?: true
        val dayTheme = restoreThemeAsset(zip, temp, source.dayTheme, false, registerTheme = importDayTheme)
        val nightTheme = restoreThemeAsset(zip, temp, source.nightTheme, true, registerTheme = importNightTheme)
        val dayTop = if (importDayTopBar) restoreTopBar(zip, temp, false, source.dayTopBarDir, data.dayTopBar) else TopBarConfig.DEFAULT_DIR_NAME
        val nightTop = if (importNightTopBar) restoreTopBar(zip, temp, true, source.nightTopBarDir, data.nightTopBar) else TopBarConfig.DEFAULT_DIR_NAME
        val dayBottom = if (importDayBottomBar) restoreBottomBar(zip, temp, false, data.dayBottomBar) else null
        val nightBottom = if (importNightBottomBar) restoreBottomBar(zip, temp, true, data.nightBottomBar) else null
        val dayCover = if (importDayCover) restoreCover(zip, temp, data.dayCover) else null
        val nightCover = if (importNightCover) restoreCover(zip, temp, data.nightCover) else null
        return addImported(
            source.copy(
                dayTheme = dayTheme,
                nightTheme = nightTheme,
                dayTopBarDir = dayTop,
                nightTopBarDir = nightTop,
                dayBottomBarId = dayBottom,
                nightBottomBarId = nightBottom,
                dayCoverGroupId = dayCover,
                nightCoverGroupId = nightCover
            )
        )
    }

    /**
     * 导入 archive_primate_beta 分支导出的应用主题包（appearance_kit.json）。
     *
     * 该格式的 zip 包含一个 appearance_kit.json 清单和多个子 zip 组件
     * （theme_day.zip, topbar_day.zip 等）。清单中的 components 列表描述
     * 了每个子 zip 的类型、日夜模式和路径。
     *
     * 导入时逐个解压子 zip，提取其中的配置和资源，转换为当前分支的
     * Config 结构。由于当前分支可能缺少一些参数（如 panelBackgroundImgPath、
     * cardColor 等），这些多余字段在 GSON 解析时会被安全忽略。
     */
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

    private suspend fun importAppearanceKitFormat(
        zip: ZipFile,
        temp: File,
        manifestEntry: ZipEntry,
        options: ImportOptions?
    ): Config {
        require(manifestEntry.size in 0..maxManifestBytes) { appCtx.getString(R.string.app_theme_manifest_too_large) }
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

        // 逐个处理组件
        val componentTemp = temp.resolve("components").apply { mkdirs() }
        for (component in kitPackage.components) {
            val componentFile = extractAsset(zip, componentTemp, component.path) ?: continue
            when (component.type.uppercase()) {
                "THEME" -> {
                    val shouldImport = if (component.isNight) options?.importNightTheme ?: true
                    else options?.importDayTheme ?: true
                    if (!shouldImport) continue
                    // 解压子 zip 获取 theme.json
                    val subTemp = componentTemp.resolve("theme_${component.isNight}").apply { mkdirs() }
                    try {
                        ZipUtils.unZipToPath(componentFile, subTemp)
                        val themeJsonFile = subTemp.walkTopDown().firstOrNull { it.name == "theme.json" }
                        if (themeJsonFile != null) {
                            val pkg = GSON.fromJsonObject<AppearanceThemePackage>(themeJsonFile.readText()).getOrNull()
                            val config = pkg?.config
                            if (config != null) {
                                // 提取背景图资源
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
                                // 提取壁纸
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
                                // 提取图标资源
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
                                    wallpaperPath = null,
                                    sidebarBackgroundPath = source.sidebarBackgroundPath,
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
                        // 尝试找到封面图集清单
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
                            require(imageFiles.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
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

        val config = Config(
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
        return addImported(stripComponents(config, options))
    }

    /**
     * 导入 MD3-main 分支导出的主题包（manifest.json）。
     *
     * 该格式的 zip 包含一个 manifest.json 清单，其中 config 是
     * ThemeExportData 结构（扁平的参数列表），assets 是资源文件路径映射，
     * coverAlbums 和 coverSelection 描述封面图集。
     *
     * 导入时需要将 ThemeExportData 的颜色参数映射为当前分支的
     * ThemeConfig.Config，从 zip 中提取背景图等资源。
     * 由于当前分支可能缺少一些参数（如 containerOpacity、enableBlur 等），
     * 这些字段在映射时会被安全忽略。
     */
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

    private suspend fun importMd3Format(
        zip: ZipFile,
        temp: File,
        manifestEntry: ZipEntry,
        options: ImportOptions?
    ): Config {
        require(manifestEntry.size in 0..maxManifestBytes) { appCtx.getString(R.string.app_theme_manifest_too_large) }
        val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(manifestJson).asJsonObject
        require(root.has("formatVersion") && root.has("config")) {
            appCtx.getString(R.string.app_theme_invalid_format)
        }
        val manifest = GSON.fromJson(manifestJson, Md3ThemeManifest::class.java)
            ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))

        val data = manifest.config
        val themeName = manifest.name?.ifBlank { null } ?: "MD3主题"

        // 将 MD3 的颜色参数映射为当前分支的 ThemeConfig.Config
        val importDayTheme = options?.importDayTheme ?: true
        val importNightTheme = options?.importNightTheme ?: true

        // 日间主题
        var dayTheme: ThemeConfig.Config? = null
        if (importDayTheme) {
            val dayPrimary = colorToHex(data.themeColor)
            val dayAccent = colorToHex(data.secondaryThemeColor)
            val dayBg = colorToHex(data.themeBackgroundColor)
            val dayBottomBg = colorToHex(data.labelContainerColor)
            // 提取日间背景图
            var dayBgImgPath: String? = null
            val dayBgAssetPath = data.bgImageLight
            if (!dayBgAssetPath.isNullOrBlank()) {
                val assetEntry = manifest.assets.entries.firstOrNull { it.key == "background.light" }?.value
                    ?: dayBgAssetPath
                val extracted = extractAsset(zip, temp, assetEntry)
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
            // 提取夜间背景图
            var nightBgImgPath: String? = null
            val nightBgAssetPath = data.bgImageDark
            if (!nightBgAssetPath.isNullOrBlank()) {
                val assetEntry = manifest.assets.entries.firstOrNull { it.key == "background.dark" }?.value
                    ?: nightBgAssetPath
                val extracted = extractAsset(zip, temp, assetEntry)
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

            // 提取封面图片
            val images = if (isNightAlbum) album.darkImages else album.lightImages
            val files = images.mapNotNull { img -> extractAsset(zip, temp, img.path) }
            require(files.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
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

        val config = Config(
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
        return addImported(stripComponents(config, options))
    }

    /** 将 Int 颜色值转换为 #RRGGBB 格式的十六进制字符串 */
    private fun colorToHex(color: Int): String {
        val rgb = color and 0xFFFFFF
        return String.format("#%06X", rgb)
    }

    /**
     * 按导入选项剥离未选中的组件，将对应字段置为默认值。
     *
     * 主题组件存储的是内联数据（颜色 + 背景图路径），而非引用 ID，
     * 因此不剥离背景图路径——无论是否勾选「主题」，应用主题配置中
     * 都保留完整的主题数据，以便预览和应用时能正确显示背景图。
     * 「主题」勾选仅控制是否在主题管理列表中注册新条目（见 restoreThemeAsset）。
     */
    private fun stripComponents(config: Config, options: ImportOptions?): Config {
        if (options == null) return config
        return config.copy(
            dayTopBarDir = if (options.importDayTopBar) config.dayTopBarDir else TopBarConfig.DEFAULT_DIR_NAME,
            nightTopBarDir = if (options.importNightTopBar) config.nightTopBarDir else TopBarConfig.DEFAULT_DIR_NAME,
            dayBottomBarId = if (options.importDayBottomBar) config.dayBottomBarId else null,
            nightBottomBarId = if (options.importNightBottomBar) config.nightBottomBarId else null,
            dayCoverGroupId = if (options.importDayCover) config.dayCoverGroupId else null,
            nightCoverGroupId = if (options.importNightCover) config.nightCoverGroupId else null
        )
    }

    private fun addImported(imported: Config): Config {
        val items = load()
        val baseName = imported.name.trim().ifBlank { appCtx.getString(io.legado.app.R.string.application_theme_manage) }
        // 同名配置直接覆盖（复用原有 ID），避免反复追加“名称 2”
        val existingIndex = items.indexOfFirst { it.name == baseName }
        val next = if (existingIndex >= 0) {
            imported.copy(id = items[existingIndex].id, name = baseName, updatedAt = System.currentTimeMillis())
        } else {
            imported.copy(id = UUID.randomUUID().toString(), name = baseName, updatedAt = System.currentTimeMillis())
        }
        if (existingIndex >= 0) items[existingIndex] = next else items.add(next)
        save(items)
        return next
    }

    /** 判断指定配置是否与当前系统状态完全匹配 */
    fun isCurrent(context: Context, config: Config): Boolean {
        return currentId(context) == config.id &&
            (config.dayTheme == null || context.getPrefString(PreferKey.dThemeName).orEmpty() == config.dayTheme?.themeName) &&
            (config.nightTheme == null || context.getPrefString(PreferKey.dNThemeName).orEmpty() == config.nightTheme?.themeName) &&
            (config.dayTopBarDir.isBlank() || TopBarConfig.activeDirName(false) == config.dayTopBarDir) &&
            (config.nightTopBarDir.isBlank() || TopBarConfig.activeDirName(true) == config.nightTopBarDir) &&
            (config.dayBottomBarId == null || NavigationBarConfig.activeConfig(context, false).id == config.dayBottomBarId) &&
            (config.nightBottomBarId == null || NavigationBarConfig.activeConfig(context, true).id == config.nightBottomBarId) &&
            (config.dayCoverGroupId == null || selectedCoverGroupId(context, false) == config.dayCoverGroupId) &&
            (config.nightCoverGroupId == null || selectedCoverGroupId(context, true) == config.nightCoverGroupId)
    }

    /** 从当前系统状态快照生成一个新配置 */
    fun captureCurrent(context: Context, name: String, id: String? = null): Config {
        val dayThemeName = context.getPrefString(PreferKey.dThemeName).orEmpty()
        val nightThemeName = context.getPrefString(PreferKey.dNThemeName).orEmpty()
        val dayTheme = ThemeConfig.configList.firstOrNull {
            !it.isNightTheme && it.themeName == dayThemeName
        }?.copy()
        val nightTheme = ThemeConfig.configList.firstOrNull {
            it.isNightTheme && it.themeName == nightThemeName
        }?.copy()
        // 使用 SharedPreferences 中的实时值覆盖 backgroundImgPath。
        // 用户可能通过「主题设置」偏好页面直接修改了背景图（写入 PreferKey.bgImage / bgImageN），
        // 而该修改不会同步更新 ThemeConfig.configList 中的模板配置，
        // 因此必须从 SharedPreferences 读取当前实际使用的背景图路径。
        dayTheme?.backgroundImgPath = context.getPrefString(PreferKey.bgImage)
        nightTheme?.backgroundImgPath = context.getPrefString(PreferKey.bgImageN)
        return Config(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            dayTheme = dayTheme,
            nightTheme = nightTheme,
            dayTopBarDir = TopBarConfig.activeDirName(false),
            nightTopBarDir = TopBarConfig.activeDirName(true),
            dayBottomBarId = NavigationBarConfig.activeConfig(context, false).id,
            nightBottomBarId = NavigationBarConfig.activeConfig(context, true).id,
            dayCoverGroupId = selectedCoverGroupId(context, false),
            nightCoverGroupId = selectedCoverGroupId(context, true)
        )
    }

    /** 新增配置（名称不能重复） */
    fun add(config: Config) {
        val items = load()
        require(config.name.isNotBlank())
        require(items.none { it.name == config.name })
        items.add(config)
        save(items)
    }

    /** 替换配置（同 ID 覆盖，名称不能与其他配置重复） */
    fun replace(config: Config) {
        val items = load()
        require(config.name.isNotBlank())
        require(items.none { it.id != config.id && it.name == config.name })
        val index = items.indexOfFirst { it.id == config.id }
        if (index >= 0) items[index] = config else items.add(config)
        save(items)
    }

    /** 重命名配置 */
    fun rename(id: String, name: String) {
        val items = load()
        val nextName = name.trim()
        require(nextName.isNotBlank())
        require(items.none { it.id != id && it.name == nextName })
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(name = nextName, updatedAt = System.currentTimeMillis())
            save(items)
        }
    }

    /** 删除配置，若删除的是当前配置则清除激活标记 */
    fun delete(context: Context, id: String) {
        save(load().filterNot { it.id == id })
        if (currentId(context) == id) context.putPrefString(currentIdKey, "")
    }

    /**
     * 应用主题配置。
     * 依次应用主题颜色、顶栏、底栏、封面图集，
     * 然后发送事件总线通知 UI 刷新。
     */
    fun apply(context: Context, config: Config) {
        validateForApply(context, config)
        val wasNight = AppConfig.isNightTheme
        config.dayTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = false), applyNow = false) }
        config.nightTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = true), applyNow = false) }

        applyTopBar(context, false, config.dayTopBarDir)
        applyTopBar(context, true, config.nightTopBarDir)
        applyBottomBar(context, false, config.dayBottomBarId)
        applyBottomBar(context, true, config.nightBottomBarId)

        val coverRepository = CoverGalleryRepository()
        config.dayCoverGroupId?.let { coverRepository.setSelectedGroup(false, it) }
        config.nightCoverGroupId?.let { coverRepository.setSelectedGroup(true, it) }

        if (config.dayTheme != null && context.getPrefString(PreferKey.dThemeName) != config.dayTheme?.themeName) {
            throw IllegalStateException(appCtx.getString(R.string.app_theme_day_apply_failed))
        }
        if (config.nightTheme != null && context.getPrefString(PreferKey.dNThemeName) != config.nightTheme?.themeName) {
            throw IllegalStateException(appCtx.getString(R.string.app_theme_night_apply_failed))
        }

        AppConfig.isNightTheme = wasNight
        ThemeConfig.applyDayNight(context)
        context.putPrefString(currentIdKey, config.id)
        postEvent(EventBus.TOP_BAR_CHANGED, wasNight)
        postEvent(EventBus.NAVIGATION_BAR_CHANGED, wasNight)
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    /** 生成配置摘要文本，用于列表展示 */
    fun summary(context: Context, config: Config): String {
        val dayTheme = config.dayTheme?.themeName ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val nightTheme = config.nightTheme?.themeName ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val dayTop = topBarName(context, false, config.dayTopBarDir)
        val nightTop = topBarName(context, true, config.nightTopBarDir)
        val dayBottom = bottomBarName(context, false, config.dayBottomBarId)
        val nightBottom = bottomBarName(context, true, config.nightBottomBarId)
        val covers = CoverGalleryRepository()
        val dayCover = covers.getGroupName(config.dayCoverGroupId) ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val nightCover = covers.getGroupName(config.nightCoverGroupId) ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        return appCtx.getString(R.string.app_theme_summary_format, dayTheme, dayTop, dayBottom, dayCover, nightTheme, nightTop, nightBottom, nightCover)
    }

    /** 安全保存配置列表：先写临时文件再原子替换，并备份原文件 */
    private fun save(items: List<Config>) {
        val target = FileUtils.createFileIfNotExist(filePath)
        val temp = File("$filePath.tmp")
        temp.writeText(GSON.toJson(items))
        if (target.exists()) target.copyTo(File("$filePath.bak"), overwrite = true)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun applyTopBar(context: Context, isNight: Boolean, dirName: String) {
        TopBarConfig.loadEntries(context, isNight).firstOrNull { it.dirName == dirName }
            ?.let(TopBarConfig::apply)
    }

    private fun applyBottomBar(context: Context, isNight: Boolean, id: String?) {
        NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id }
            ?.let { NavigationBarConfig.setActiveId(context, isNight, it.id) }
    }

    private fun topBarName(context: Context, isNight: Boolean, dirName: String): String {
        return TopBarConfig.loadEntries(context, isNight)
            .firstOrNull { it.dirName == dirName }?.config?.name
            ?: context.getString(io.legado.app.R.string.application_theme_not_set)
    }

    private fun bottomBarName(context: Context, isNight: Boolean, id: String?): String {
        return NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id }?.name
            ?: context.getString(io.legado.app.R.string.application_theme_not_set)
    }

    private fun selectedCoverGroupId(context: Context, isNight: Boolean): Long? {
        return context.getPrefString(
            if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        )?.toLongOrNull()
    }

    private fun packageTheme(zip: ZipOutputStream, theme: ThemeConfig.Config?, prefix: String): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath ?: return theme.copy()
        if (path.startsWith("http", true)) return theme.copy()
        val source = File(path).takeIf { it.isFile } ?: appCtx.externalFiles
            .getFile(if (theme.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage)
            .getFile(path)
            .takeIf { it.isFile }
        val entry = source?.let { addZipFile(zip, it, "$prefix/background") }
        return theme.copy(backgroundImgPath = entry)
    }

    private fun packageTopBar(
        zip: ZipOutputStream,
        context: Context,
        isNight: Boolean,
        dirName: String,
        prefix: String
    ): TopBarConfig.Config? {
        if (dirName.isBlank() || dirName == TopBarConfig.DEFAULT_DIR_NAME) return null
        val entry = TopBarConfig.loadEntries(context, isNight).firstOrNull { it.dirName == dirName } ?: return null
        val wallpaper = entry.config.wallpaperPath?.let { path ->
            val file = File(path).takeIf { it.isFile }
                ?: entry.localDir?.resolve(path)?.takeIf { it.isFile }
            file?.let { addZipFile(zip, it, "$prefix/wallpaper") }
        }
        return entry.config.copy(wallpaperPath = wallpaper)
    }

    private fun packageBottomBar(
        zip: ZipOutputStream,
        context: Context,
        isNight: Boolean,
        id: String?,
        prefix: String
    ): NavigationBarConfig? {
        val config = NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id } ?: return null
        val icons = config.icons.mapNotNull { (key, path) ->
            File(path).takeIf { it.isFile }?.let { key to addZipFile(zip, it, "$prefix/$key") }
        }.toMap()
        return config.copy(icons = icons)
    }

    private fun packageCover(zip: ZipOutputStream, groupId: Long?, prefix: String): CoverPayload? {
        val group = CoverGalleryRepository().allGroupsWithImages()
            .firstOrNull { it.group.id == groupId } ?: return null
        val images = group.images.mapIndexedNotNull { index, image ->
            File(image.path).takeIf { it.isFile }?.let { addZipFile(zip, it, "$prefix/$index") }
        }
        return CoverPayload(group.group.name, images)
    }

    private fun addZipFile(zip: ZipOutputStream, file: File, basePath: String): String {
        val extension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val path = "$basePath$extension"
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
        return path
    }

    private suspend fun restoreThemeAsset(
        zip: ZipFile,
        temp: File,
        theme: ThemeConfig.Config?,
        isNight: Boolean,
        registerTheme: Boolean = true
    ): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath
        var restored = theme.copy(isNightTheme = isNight)
        if (path != null && !path.startsWith("http", true)) {
            val extracted = extractAsset(zip, temp, path)
            if (extracted != null) {
                val dir = appCtx.externalFiles.getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage).apply { mkdirs() }
                val target = dir.getFile("application_theme_${UUID.randomUUID()}.${extracted.extension.ifBlank { "jpg" }}")
                extracted.copyTo(target, overwrite = true)
                restored = restored.copy(backgroundImgPath = target.absolutePath)
            } else {
                restored = restored.copy(backgroundImgPath = null)
            }
        }
        if (registerTheme) {
            // 将导入的主题添加到 ThemeConfig.configList，使其在主题管理列表中可见
            // ThemeConfig.addConfig 已支持同名覆盖，无需 uniqueName
            ThemeConfig.addConfig(restored)
        }
        return restored
    }

    private fun restoreTopBar(
        zip: ZipFile,
        temp: File,
        isNight: Boolean,
        originalDir: String,
        packaged: TopBarConfig.Config?
    ): String {
        if (originalDir.isBlank()) return ""
        if (originalDir == TopBarConfig.DEFAULT_DIR_NAME) return TopBarConfig.DEFAULT_DIR_NAME
        val source = packaged ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_top_bar))
        val wallpaper = source.wallpaperPath?.let { extractAsset(zip, temp, it)?.absolutePath }
        // 同名顶栏直接复用已有条目进行更新，避免追加“名称 2”
        val existingEntry = TopBarConfig.loadEntries(appCtx, isNight)
            .firstOrNull { it.config.name == source.name.trim() }
        return TopBarConfig.addOrUpdate(
            source.copy(isNightMode = isNight, wallpaperPath = wallpaper),
            oldEntry = existingEntry
        ).dirName
    }

    private fun restoreBottomBar(
        zip: ZipFile,
        temp: File,
        isNight: Boolean,
        packaged: NavigationBarConfig?
    ): String? {
        packaged ?: return null
        if (packaged.isBuiltin) {
            return NavigationBarConfig.loadConfigs(appCtx)
                .firstOrNull { it.isNight == isNight && it.isBuiltin }?.id
        }
        val existing = NavigationBarConfig.loadConfigs(appCtx)
        // 同名底栏直接覆盖已有配置，避免追加"名称 2"
        val existingIndex = existing.indexOfFirst { it.isNight == isNight && it.name == packaged.name.trim() && !it.isBuiltin }
        val id = if (existingIndex >= 0) existing[existingIndex].id else UUID.randomUUID().toString()
        val iconDir = appCtx.externalFiles.getFile("navigationBarIcons", id).apply { mkdirs() }
        val icons = packaged.icons.mapNotNull { (key, path) ->
            extractAsset(zip, temp, path)?.let { source ->
                val target = iconDir.getFile("${key}.${source.extension.ifBlank { "png" }}")
                source.copyTo(target, overwrite = true)
                key to target.absolutePath
            }
        }.toMap()
        val next = packaged.copy(id = id, name = packaged.name.trim(), isNight = isNight, isBuiltin = false, icons = icons)
        if (existingIndex >= 0) existing[existingIndex] = next else existing.add(next)
        NavigationBarConfig.saveConfigs(appCtx, existing)
        return id
    }

    private suspend fun restoreCover(zip: ZipFile, temp: File, payload: CoverPayload?): Long? {
        payload ?: return null
        val repository = CoverGalleryRepository()
        val baseName = payload.name.trim().ifBlank { appCtx.getString(R.string.app_theme_component_default_name) }
        // 同名封面图集直接复用已有图集，避免管理列表堆积重复组件
        val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == baseName }
        val groupId = existingGroup?.group?.id ?: repository.addGroup(baseName)
        val files = payload.images.mapNotNull { extractAsset(zip, temp, it) }
        if (files.isNotEmpty()) repository.addImageFiles(appCtx, groupId, files)
        return groupId
    }

    private fun extractAsset(zip: ZipFile, temp: File, path: String): File? {
        val entry = zip.getEntry(path) ?: return null
        require(!entry.isDirectory) { appCtx.getString(R.string.app_theme_invalid_asset) }
        require(entry.size in 0..maxAssetBytes) { appCtx.getString(R.string.app_theme_asset_too_large) }
        val target = temp.resolve("${UUID.randomUUID()}.${path.substringAfterLast('.', "bin")}")
        zip.getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun uniqueName(base: String, used: Set<String>): String {
        val normalized = base.trim().ifBlank { appCtx.getString(R.string.app_theme_component_default_name) }
        if (normalized !in used) return normalized
        var index = 2
        while ("$normalized $index" in used) index++
        return "$normalized $index"
    }

    private fun validatePackage(zip: ZipFile, data: PackageData, options: ImportOptions? = null) {
        val importDayTopBar = options?.importDayTopBar ?: true
        val importNightTopBar = options?.importNightTopBar ?: true
        val importDayBottomBar = options?.importDayBottomBar ?: true
        val importNightBottomBar = options?.importNightBottomBar ?: true
        val importDayCover = options?.importDayCover ?: true
        val importNightCover = options?.importNightCover ?: true
        val assetPaths = buildList {
            // 主题背景图始终需要校验：无论是否勾选「主题」，
            // 背景图资源都会从 zip 中提取以供预览和应用使用
            listOfNotNull(data.config.dayTheme, data.config.nightTheme).forEach { theme ->
                theme.backgroundImgPath?.takeUnless { it.startsWith("http", true) }?.let(::add)
            }
            if (importDayTopBar) {
                data.dayTopBar?.wallpaperPath?.let(::add)
            }
            if (importNightTopBar) {
                data.nightTopBar?.wallpaperPath?.let(::add)
            }
            if (importDayBottomBar) {
                data.dayBottomBar?.icons?.values?.let(::addAll)
            }
            if (importNightBottomBar) {
                data.nightBottomBar?.icons?.values?.let(::addAll)
            }
            if (importDayCover) {
                data.dayCover?.let { cover ->
                    require(cover.images.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
                    addAll(cover.images)
                }
            }
            if (importNightCover) {
                data.nightCover?.let { cover ->
                    require(cover.images.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
                    addAll(cover.images)
                }
            }
        }
        require(assetPaths.distinct().size == assetPaths.size) { appCtx.getString(R.string.app_theme_duplicate_assets) }
        assetPaths.forEach { path ->
            val entry = zip.getEntry(path) ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_asset, path))
            require(!entry.isDirectory && entry.size in 0..maxAssetBytes) { appCtx.getString(R.string.app_theme_invalid_asset_path, path) }
        }
    }

    /** 校验配置是否可以应用：颜色格式、背景图存在性、子配置是否存在 */
    private fun validateForApply(context: Context, config: Config) {
        listOfNotNull(config.dayTheme, config.nightTheme).forEach { theme ->
            runCatching {
                Color.parseColor(theme.primaryColor)
                Color.parseColor(theme.accentColor)
                Color.parseColor(theme.backgroundColor)
                Color.parseColor(theme.bottomBackground)
            }.getOrElse { throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_color, theme.themeName), it) }
            theme.backgroundImgPath?.takeIf { File(it).isAbsolute }?.let { path ->
                require(File(path).isFile) { appCtx.getString(R.string.app_theme_bg_image_missing, theme.themeName) }
            }
        }
        if (config.dayTopBarDir.isNotBlank()) {
            require(TopBarConfig.loadEntries(context, false).any { it.dirName == config.dayTopBarDir }) { appCtx.getString(R.string.app_theme_day_top_bar_missing) }
        }
        if (config.nightTopBarDir.isNotBlank()) {
            require(TopBarConfig.loadEntries(context, true).any { it.dirName == config.nightTopBarDir }) { appCtx.getString(R.string.app_theme_night_top_bar_missing) }
        }
        config.dayBottomBarId?.let { id ->
            require(NavigationBarConfig.loadConfigs(context).any { !it.isNight && it.id == id }) { appCtx.getString(R.string.app_theme_day_nav_bar_missing) }
        }
        config.nightBottomBarId?.let { id ->
            require(NavigationBarConfig.loadConfigs(context).any { it.isNight && it.id == id }) { appCtx.getString(R.string.app_theme_night_nav_bar_missing) }
        }
        val groupIds = CoverGalleryRepository().allGroupsWithImages().map { it.group.id }.toSet()
        config.dayCoverGroupId?.let { require(it in groupIds) { appCtx.getString(R.string.app_theme_day_cover_missing) } }
        config.nightCoverGroupId?.let { require(it in groupIds) { appCtx.getString(R.string.app_theme_night_cover_missing) } }
    }

    /** 清洗并验证导入的配置数据，确保所有字段合法 */
    private fun sanitize(source: Config): Config {
        val id = runCatching { source.id }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val name = runCatching { source.name }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_name_invalid))
        return Config(
            id = id,
            name = name,
            dayTheme = runCatching { source.dayTheme }.getOrNull(),
            nightTheme = runCatching { source.nightTheme }.getOrNull(),
            dayTopBarDir = runCatching { source.dayTopBarDir }.getOrNull().orEmpty(),
            nightTopBarDir = runCatching { source.nightTopBarDir }.getOrNull().orEmpty(),
            dayBottomBarId = runCatching { source.dayBottomBarId }.getOrNull(),
            nightBottomBarId = runCatching { source.nightBottomBarId }.getOrNull(),
            dayCoverGroupId = runCatching { source.dayCoverGroupId }.getOrNull(),
            nightCoverGroupId = runCatching { source.nightCoverGroupId }.getOrNull(),
            updatedAt = runCatching { source.updatedAt }.getOrDefault(System.currentTimeMillis())
        )
    }
}
