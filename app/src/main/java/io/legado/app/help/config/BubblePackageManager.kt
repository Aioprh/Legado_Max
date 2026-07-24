package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.putPrefString
import io.legado.app.utils.compress.ZipUtils
import splitties.init.appCtx
import java.io.File

/**
 * 段评气泡包管理器。
 *
 * 管理段评气泡的 SVG 模板、缩放比例和日夜间配色。
 * 配置以文件系统目录形式存储，每个包包含 bubble.json 配置文件。
 * 内置气泡包为只读，用户可创建自定义包或导入 zip。
 *
 * 气泡渲染由 [ParagraphBubbleRenderer] 负责，通过 SVG 模板
 * 替换 ${color} 和 ${num} 占位符生成位图。
 */
object BubblePackageManager {

    /** 备份目录名 */
    const val backupDirName = "bubblePackages"
    /** 内置气泡包目录名 */
    const val BUILTIN_DIR_NAME = "builtin_default"
    /** 默认强调色 */
    const val DEFAULT_EMPHASIS_COLOR = "#FF0000"
    /** 默认常规色 */
    const val DEFAULT_NORMAL_COLOR = "#808080"
    /** 最小缩放比例 */
    const val MIN_SIZE_SCALE = 0.5f
    /** 最大缩放比例 */
    const val MAX_SIZE_SCALE = 1.5f
    private const val packageFileName = "bubble.json"
    private const val defaultBubblePath =
        "M44 48 Q48 48 48 44 L48 20 Q48 16 44 16 L20 16 Q16 16 16 20 L16 24 S16 28 10 30 Q6 32 10 34 Q16 36 16 38 L16 44 Q16 48 20 48 Z"

    @Volatile
    private var cachedEntry: Entry? = null

    @Volatile
    private var cachedDirName: String? = null

    val rootDir: File
        get() = appCtx.externalFiles.getFile(backupDirName)

    private val tempDir: File
        get() = rootDir.getFile("temp").apply { mkdirs() }

    /**
     * 气泡配置数据类。
     *
     * @property name 配置名称
     * @property dirName 存储目录名（内置为 [BUILTIN_DIR_NAME]）
     * @property svgTemplate SVG 模板字符串，支持 ${color} 和 ${num} 占位符
     * @property sizeScale 缩放比例（[MIN_SIZE_SCALE] ~ [MAX_SIZE_SCALE]）
     * @property dayNormalColor 日间常规色
     * @property dayEmphasisColor 日间强调色
     * @property nightNormalColor 夜间常规色
     * @property nightEmphasisColor 夜间强调色
     * @property updatedAt 最后更新时间戳
     */
    @Keep
    data class Config(
        var name: String,
        var dirName: String = "",
        var svgTemplate: String = "",
        var sizeScale: Float = 1f,
        var dayNormalColor: String? = null,
        var dayEmphasisColor: String? = null,
        var nightNormalColor: String? = null,
        var nightEmphasisColor: String? = null,
        var updatedAt: Long = System.currentTimeMillis()
    )

    /** 配置条目：包含配置数据和来源信息 */
    data class Entry(
        val config: Config,
        val source: Source,
        val dirName: String,
        val localDir: File? = null
    )

    /** 配置来源类型 */
    enum class Source { BUILTIN, LOCAL }

    /** 创建内置默认配置 */
    fun builtinConfig(): Config {
        return Config(
            name = appCtx.getString(R.string.bubble_builtin_name),
            dirName = BUILTIN_DIR_NAME,
            svgTemplate = defaultSvgTemplate(),
            sizeScale = 1f,
            dayNormalColor = DEFAULT_NORMAL_COLOR,
            dayEmphasisColor = DEFAULT_EMPHASIS_COLOR,
            nightNormalColor = DEFAULT_NORMAL_COLOR,
            nightEmphasisColor = DEFAULT_EMPHASIS_COLOR,
            updatedAt = 0L
        )
    }

    fun builtinEntry(): Entry {
        return Entry(
            config = builtinConfig(),
            source = Source.BUILTIN,
            dirName = BUILTIN_DIR_NAME
        )
    }

    /** 加载所有气泡包条目（内置 + 本地），按更新时间排序 */
    fun loadEntries(): List<Entry> {
        val local = rootDir.listFiles()
            ?.filter { it.isDirectory && it.name != BUILTIN_DIR_NAME }
            ?.mapNotNull(::readEntry)
            .orEmpty()
            .sortedWith(compareByDescending<Entry> { it.config.updatedAt }.thenBy { it.config.name })
        return listOf(builtinEntry()) + local
    }

    /** 获取当前激活的气泡包目录名 */
    fun activeDirName(): String {
        return appCtx.getPrefString(PreferKey.paragraphBubblePackage, BUILTIN_DIR_NAME)
            ?.ifBlank { BUILTIN_DIR_NAME }
            ?: BUILTIN_DIR_NAME
    }

    /** 应用指定气泡包：设置激活 key 并清除缓存 */
    fun apply(entry: Entry) {
        appCtx.putPrefString(PreferKey.paragraphBubblePackage, entry.dirName)
        invalidateCurrentEntry()
    }

    /**
     * 获取当前激活的配置条目，带内存缓存。
     * 缓存会在 [invalidateCurrentEntry] 调用时失效。
     */
    fun currentEntry(): Entry {
        val dirName = activeDirName()
        cachedEntry?.takeIf { cachedDirName == dirName }?.let { return it }
        val entry = if (dirName == BUILTIN_DIR_NAME) {
            builtinEntry()
        } else {
            readEntry(localDir(dirName)) ?: builtinEntry()
        }
        cachedDirName = dirName
        cachedEntry = entry
        return entry
    }

    /** 清除内存缓存，下次读取时重新从磁盘加载 */
    fun invalidateCurrentEntry() {
        cachedEntry = null
        cachedDirName = null
    }

    /** 新增或更新配置。若为已有本地包则更新，否则创建新目录 */
    fun addOrUpdate(config: Config, oldEntry: Entry? = null): Entry {
        val normalized = normalizeConfig(config)
        val editableOldEntry = oldEntry?.takeIf {
            it.dirName.isNotBlank() && it.dirName != BUILTIN_DIR_NAME && it.source != Source.BUILTIN
        }
        val name = normalized.name.trim().ifBlank { appCtx.getString(R.string.bubble_default_name) }
        val dirName = editableOldEntry?.dirName ?: uniqueDirName(
            normalized.dirName.ifBlank { name.normalizeFileName() }
                .ifBlank { "bubble_${System.currentTimeMillis()}" }
        )
        val dir = localDir(dirName).apply { mkdirs() }
        val next = normalized.copy(
            name = name,
            dirName = dirName,
            updatedAt = System.currentTimeMillis()
        )
        File(dir, packageFileName).writeText(GSON.toJson(next))
        invalidateCurrentEntry()
        return Entry(next, Source.LOCAL, dirName, dir)
    }

    /** 删除本地气泡包，若删除的是当前激活配置则重置为内置 */
    fun deleteLocal(entry: Entry) {
        if (entry.source == Source.BUILTIN || entry.dirName == BUILTIN_DIR_NAME) return
        FileUtils.delete(entry.localDir ?: localDir(entry.dirName), deleteRootDir = true)
        if (activeDirName() == entry.dirName) {
            appCtx.putPrefString(PreferKey.paragraphBubblePackage, BUILTIN_DIR_NAME)
        }
        invalidateCurrentEntry()
    }

    /** 从 zip 文件导入气泡包，自动生成唯一目录名 */
    fun importZip(zipFile: File): Entry {
        val unzipDir = tempDir.getFile("import_${System.currentTimeMillis()}").apply {
            if (exists()) FileUtils.delete(this, deleteRootDir = true)
            mkdirs()
        }
        return try {
            ZipUtils.unZipToPath(zipFile, unzipDir)
            val packageFile = unzipDir.walkTopDown().firstOrNull { it.isFile && it.name == packageFileName }
                ?: throw IllegalArgumentException(appCtx.getString(R.string.bubble_config_not_found))
            val config = normalizeConfig(GSON.fromJsonObject<Config>(packageFile.readText()).getOrThrow())
            val dirName = uniqueDirName(
                config.dirName.ifBlank { config.name.normalizeFileName() }
                    .ifBlank { "bubble_${System.currentTimeMillis()}" }
            )
            val targetDir = localDir(dirName)
            if (targetDir.exists()) FileUtils.delete(targetDir, deleteRootDir = true)
            targetDir.mkdirs()
            packageFile.parentFile?.copyRecursively(targetDir, overwrite = true)
            val finalConfig = config.copy(
                dirName = dirName,
                updatedAt = System.currentTimeMillis()
            )
            File(targetDir, packageFileName).writeText(GSON.toJson(finalConfig))
            invalidateCurrentEntry()
            Entry(finalConfig, Source.LOCAL, dirName, localDir = targetDir)
        } finally {
            FileUtils.delete(unzipDir, deleteRootDir = true)
        }
    }

    private fun readEntry(dir: File): Entry? {
        val file = File(dir, packageFileName)
        if (!file.isFile) return null
        return GSON.fromJsonObject<Config>(file.readText()).getOrNull()
            ?.let(::normalizeConfig)
            ?.copy(dirName = dir.name)
            ?.let { Entry(it, Source.LOCAL, dir.name, dir) }
    }

    /** 规范化配置：限制缩放范围、填充默认值、验证颜色格式 */
    private fun normalizeConfig(config: Config): Config {
        val size = config.sizeScale.takeIf { it.isFinite() } ?: 1f
        return config.copy(
            name = config.name.trim().ifBlank { appCtx.getString(R.string.bubble_default_name) },
            svgTemplate = config.svgTemplate.ifBlank { defaultSvgTemplate() },
            sizeScale = size.coerceIn(MIN_SIZE_SCALE, MAX_SIZE_SCALE),
            dayNormalColor = normalizeColor(config.dayNormalColor, DEFAULT_NORMAL_COLOR),
            dayEmphasisColor = normalizeColor(config.dayEmphasisColor, DEFAULT_EMPHASIS_COLOR),
            nightNormalColor = normalizeColor(config.nightNormalColor, DEFAULT_NORMAL_COLOR),
            nightEmphasisColor = normalizeColor(config.nightEmphasisColor, DEFAULT_EMPHASIS_COLOR)
        )
    }

    private fun normalizeColor(value: String?, fallback: String): String {
        val normalized = value?.trim().orEmpty().ifBlank { fallback }
            .let { if (it.startsWith("#")) it else "#$it" }
        return runCatching {
            android.graphics.Color.parseColor(normalized)
            normalized
        }.getOrDefault(fallback)
    }

    private fun localDir(dirName: String): File {
        return rootDir.getFile(dirName)
    }

    private fun uniqueDirName(preferred: String): String {
        val clean = preferred.normalizeFileName().ifBlank { "bubble_${System.currentTimeMillis()}" }
        var candidate = clean
        var index = 1
        while (localDir(candidate).exists()) {
            candidate = "${clean}_$index"
            index++
        }
        return candidate
    }

    /** 生成默认 SVG 模板，包含气泡路径和数字文本 */
    private fun defaultSvgTemplate(): String {
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
              <path d="$defaultBubblePath" fill="none" stroke="${'$'}{color}" stroke-width="3.2" stroke-linejoin="round" stroke-linecap="round"/>
              <text x="32" y="32" dy=".35em" text-anchor="middle" font-family="sans-serif" font-size="15" font-weight="600" fill="${'$'}{color}">${'$'}{num}</text>
            </svg>
        """.trimIndent()
    }
}
