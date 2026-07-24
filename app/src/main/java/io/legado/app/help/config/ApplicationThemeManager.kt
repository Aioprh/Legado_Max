package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.annotation.Keep
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.R
import splitties.init.appCtx
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
        val current = load().firstOrNull { isCurrent(context, it) }
            ?: captureCurrent(context, appCtx.getString(io.legado.app.R.string.application_theme_manage))
        validateForApply(context, current)
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = current.name.normalizeFileName().ifBlank { "application_theme" }
        return dir.resolve("$exportName.zip").apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                val packagedConfig = current.copy(
                    dayTheme = packageTheme(zip, current.dayTheme, "themes/day"),
                    nightTheme = packageTheme(zip, current.nightTheme, "themes/night")
                )
                val data = PackageData(
                    config = packagedConfig,
                    dayTopBar = packageTopBar(zip, context, false, current.dayTopBarDir, "topbar/day"),
                    nightTopBar = packageTopBar(zip, context, true, current.nightTopBarDir, "topbar/night"),
                    dayBottomBar = packageBottomBar(zip, context, false, current.dayBottomBarId, "bottombar/day"),
                    nightBottomBar = packageBottomBar(zip, context, true, current.nightBottomBarId, "bottombar/night"),
                    dayCover = packageCover(zip, current.dayCoverGroupId, "covers/day"),
                    nightCover = packageCover(zip, current.nightCoverGroupId, "covers/night")
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
     */
    suspend fun importFile(file: File): Config {
        val isZip = file.inputStream().use { input ->
            input.read() == 'P'.code && input.read() == 'K'.code
        }
        if (isZip) return importZip(file)
        require(file.length() <= maxManifestBytes) { appCtx.getString(R.string.app_theme_file_too_large) }
        val imported = sanitize(
            GSON.fromJson(file.readText(), Config::class.java)
                ?: throw IllegalArgumentException("Invalid application theme")
        )
        val items = load()
        val baseName = imported.name.trim().ifBlank {
            appCtx.getString(io.legado.app.R.string.application_theme_manage)
        }
        var name = baseName
        var suffix = 2
        while (items.any { it.name == name }) {
            name = "$baseName $suffix"
            suffix++
        }
        val next = imported.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            updatedAt = System.currentTimeMillis()
        )
        items.add(next)
        save(items)
        return next
    }

    private suspend fun importZip(file: File): Config {
        val temp = appCtx.cacheDir.resolve("applicationThemeImport/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipFile(file).use { zip ->
                val manifest = zip.getEntry("application_theme.json")
                    ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_manifest))
                require(manifest.size in 0..maxManifestBytes) { appCtx.getString(R.string.app_theme_manifest_too_large) }
                val data = zip.getInputStream(manifest).bufferedReader().use {
                    GSON.fromJson(it, PackageData::class.java)
                } ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))
                require(data.version == 1) { appCtx.getString(R.string.app_theme_unsupported_version) }
                validatePackage(zip, data)
                val source = sanitize(data.config)
                val dayTheme = restoreThemeAsset(zip, temp, source.dayTheme, false)
                val nightTheme = restoreThemeAsset(zip, temp, source.nightTheme, true)
                val dayTop = restoreTopBar(zip, temp, false, source.dayTopBarDir, data.dayTopBar)
                val nightTop = restoreTopBar(zip, temp, true, source.nightTopBarDir, data.nightTopBar)
                val dayBottom = restoreBottomBar(zip, temp, false, data.dayBottomBar)
                val nightBottom = restoreBottomBar(zip, temp, true, data.nightBottomBar)
                val dayCover = restoreCover(zip, temp, data.dayCover)
                val nightCover = restoreCover(zip, temp, data.nightCover)
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
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun addImported(imported: Config): Config {
        val items = load()
        val baseName = imported.name.trim().ifBlank { appCtx.getString(io.legado.app.R.string.application_theme_manage) }
        var name = baseName
        var suffix = 2
        while (items.any { it.name == name }) name = "$baseName ${suffix++}"
        val next = imported.copy(id = UUID.randomUUID().toString(), name = name, updatedAt = System.currentTimeMillis())
        items.add(next)
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
        return Config(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            dayTheme = ThemeConfig.configList.firstOrNull {
                !it.isNightTheme && it.themeName == dayThemeName
            }?.copy(),
            nightTheme = ThemeConfig.configList.firstOrNull {
                it.isNightTheme && it.themeName == nightThemeName
            }?.copy(),
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

    private fun restoreThemeAsset(
        zip: ZipFile,
        temp: File,
        theme: ThemeConfig.Config?,
        isNight: Boolean
    ): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath ?: return theme.copy(isNightTheme = isNight)
        if (path.startsWith("http", true)) return theme.copy(isNightTheme = isNight)
        val extracted = extractAsset(zip, temp, path) ?: return theme.copy(isNightTheme = isNight, backgroundImgPath = null)
        val dir = appCtx.externalFiles.getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage).apply { mkdirs() }
        val target = dir.getFile("application_theme_${UUID.randomUUID()}.${extracted.extension.ifBlank { "jpg" }}")
        extracted.copyTo(target, overwrite = true)
        return theme.copy(isNightTheme = isNight, backgroundImgPath = target.absolutePath)
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
        val usedNames = TopBarConfig.loadEntries(appCtx, isNight).map { it.config.name }.toSet()
        val name = uniqueName(source.name, usedNames)
        return TopBarConfig.addOrUpdate(
            source.copy(name = name, isNightMode = isNight, wallpaperPath = wallpaper)
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
        val id = UUID.randomUUID().toString()
        val iconDir = appCtx.externalFiles.getFile("navigationBarIcons", id).apply { mkdirs() }
        val icons = packaged.icons.mapNotNull { (key, path) ->
            extractAsset(zip, temp, path)?.let { source ->
                val target = iconDir.getFile("${key}.${source.extension.ifBlank { "png" }}")
                source.copyTo(target, overwrite = true)
                key to target.absolutePath
            }
        }.toMap()
        val existing = NavigationBarConfig.loadConfigs(appCtx)
        val name = uniqueName(packaged.name, existing.filter { it.isNight == isNight }.map { it.name }.toSet())
        val next = packaged.copy(id = id, name = name, isNight = isNight, isBuiltin = false, icons = icons)
        existing.add(next)
        NavigationBarConfig.saveConfigs(appCtx, existing)
        return id
    }

    private suspend fun restoreCover(zip: ZipFile, temp: File, payload: CoverPayload?): Long? {
        payload ?: return null
        val repository = CoverGalleryRepository()
        val usedNames = repository.allGroupsWithImages().map { it.group.name }.toSet()
        val groupId = repository.addGroup(uniqueName(payload.name, usedNames))
        val uris = payload.images.mapNotNull { extractAsset(zip, temp, it) }.map(Uri::fromFile)
        if (uris.isNotEmpty()) repository.addImages(appCtx, groupId, uris)
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

    private fun validatePackage(zip: ZipFile, data: PackageData) {
        val assetPaths = buildList {
            listOfNotNull(data.config.dayTheme, data.config.nightTheme).forEach { theme ->
                theme.backgroundImgPath?.takeUnless { it.startsWith("http", true) }?.let(::add)
            }
            listOfNotNull(data.dayTopBar, data.nightTopBar).forEach { topBar ->
                topBar.wallpaperPath?.let(::add)
            }
            listOfNotNull(data.dayBottomBar, data.nightBottomBar).forEach { bottomBar ->
                addAll(bottomBar.icons.values)
            }
            listOfNotNull(data.dayCover, data.nightCover).forEach { cover ->
                require(cover.images.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
                addAll(cover.images)
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
