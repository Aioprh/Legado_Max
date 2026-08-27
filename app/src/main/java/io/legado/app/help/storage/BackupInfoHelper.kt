package io.legado.app.help.storage

import io.legado.app.data.appDb
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.BookCover
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStore
import io.legado.app.ui.book.read.websearch.SearchEngineHelper
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File

/**
 * 备份/恢复信息工具类.
 * 统计本机数据、配置规则，以及扫描已解压的备份目录。
 */
object BackupInfoHelper {

    data class BackupFileInfo(
        val fileName: String,
        val displayName: String,
        val size: Long,
        val selected: Boolean = true
    )

    data class BackupOverview(
        val items: List<BackupFileInfo>,
        val totalSize: Long,
        val selectedSize: Long
    )

    data class CategoryInfo(
        val name: String,
        val icon: String,
        val items: List<BackupFileInfo>,
        val totalSize: Long
    )

    private data class CategoryDef(
        val name: String,
        val icon: String,
        val keywords: List<String>
    )

    /**
     * 数据库条目定义：文件名、数量提供函数、每条记录的预估大小（字节）。
     */
    private data class DbItemDef(
        val fileName: String,
        val countProvider: () -> Int,
        val perItemSize: Long
    )

    private val categoryConfig = listOf(
        CategoryDef(
            "书籍相关",
            "📚",
            listOf("bookshelf", "bookmark", "bookGroup", "readRecord", "bookCache", "bookChapterCache")
        ),
        CategoryDef(
            "源相关",
            "📗",
            listOf("bookSource", "rssSource", "rssStar", "sourceSub", "runtimeSourceCache", "homepage")
        ),
        CategoryDef(
            "规则相关",
            "🔧",
            listOf("replaceRule", "txtTocRule", "dictRule", "keyboardAssist", "highlightRule", "webSearch")
        ),
        CategoryDef("语音相关", "🔊", listOf("httpTTS")),
        CategoryDef(
            "配置相关",
            "⚙️",
            listOf("config", "videoConfig", "readConfig", "shareConfig", "coverRule", "servers", "directLink")
        )
    )

    val displayNameMap = mapOf(
        "zip" to "打包备份文件",
        "unzipBackup" to "解压备份文件",
        "copyBackup" to "保存备份文件",
        "webDavBackup" to "上传 WebDAV",
        "clearBackupCache" to "清理临时文件",
        "webDavBackgroundImages" to "上传背景图片",
        "applyRestoreConfig" to "应用恢复配置",
        "themeBackgroundImages" to "主题背景图片",
        CoverGalleryRepository.backupDirName to "封面图集",
        "bookshelf.json" to "书架书籍",
        "bookmark.json" to "书签",
        "bookGroup.json" to "书籍分组",
        "bookSource.json" to "书源",
        "rssSources.json" to "订阅源",
        "rssStar.json" to "订阅收藏",
        "sourceSub.json" to "源订阅",
        "webSearchEngines.json" to "搜索引擎规则",
        "replaceRule.json" to "替换规则",
        HighlightRuleStore.backupFileName to "高亮规则",
        "readRecord.json" to "阅读记录",
        "readRecordDetail.json" to "阅读详情",
        "readRecordSession.json" to "阅读时段",
        "searchHistory.json" to "搜索历史",
        "txtTocRule.json" to "TXT 目录规则",
        "httpTTS.json" to "TTS 配置",
        "keyboardAssists.json" to "键盘辅助",
        "dictRule.json" to "词典规则",
        "servers.json" to "服务器配置",
        "runtimeSourceCache.json" to "书源运行数据",
        "book_cache" to "书籍缓存",
        "bookCacheIndex.json" to "书籍缓存索引",
        "bookCacheBooks.json" to "书籍缓存书架信息",
        "bookChapterCache.json" to "书籍章节目录",
        ReadBookConfig.configFileName to "阅读样式配置",
        ReadBookConfig.shareConfigFileName to "共享阅读配置",
        ThemeConfig.configFileName to "主题配置",
        BookCover.configFileName to "封面规则",
        DirectLinkUpload.ruleFileName to "直链上传配置",
        "backgroundImages" to "阅读背景",
        "config.xml" to "应用配置",
        "videoConfig.xml" to "视频配置",
        "homepage.json" to "首页"
    )

    fun getDisplayName(fileName: String): String {
        return displayNameMap[fileName] ?: fileName
    }

    private val selectorFileAliases = mapOf(
        ReadBookConfig.shareConfigFileName to "readShareConfig.json",
        DirectLinkUpload.ruleFileName to "directLinkRule.json",
        BookCover.configFileName to "coverRule.json",
        CoverGalleryRepository.backupDirName to CoverGalleryRepository.backupDirName
    )

    fun getBackupOverview(): BackupOverview {
        return buildOverview(::isFileSelectedForBackup)
    }

    fun getRestoreOverview(): BackupOverview {
        return buildOverview(::isFileEnabledForRestore)
    }

    private fun buildOverview(isSelected: (String) -> Boolean): BackupOverview {
        val items = mutableListOf<BackupFileInfo>()
        var totalSize = 0L
        var selectedSize = 0L

        fun addItem(fileName: String, size: Long) {
            val selected = isSelected(fileName)
            totalSize += size
            if (selected) selectedSize += size
            items.add(
                BackupFileInfo(
                    fileName = fileName,
                    displayName = displayNameMap[fileName] ?: fileName,
                    size = size,
                    selected = selected
                )
            )
        }

        // 每条记录序列化为 JSON 后的预估大小（字节）。
        // 这些值基于实际数据分析，比统一的 200L 更接近真实值。
        val dbItems = listOf(
            DbItemDef("bookshelf.json", { appDb.bookDao.allBookCount }, 600L),
            DbItemDef("bookmark.json", { appDb.bookmarkDao.count }, 300L),
            DbItemDef("bookGroup.json", { appDb.bookGroupDao.count }, 200L),
            DbItemDef("bookSource.json", { appDb.bookSourceDao.allCount() }, 800L),
            DbItemDef("rssSources.json", { appDb.rssSourceDao.size }, 500L),
            DbItemDef("rssStar.json", { appDb.rssStarDao.count }, 300L),
            DbItemDef("sourceSub.json", { appDb.ruleSubDao.count }, 200L),
            DbItemDef("webSearchEngines.json", { SearchEngineHelper.loadSearchEngines(appCtx).size }, 200L),
            DbItemDef("replaceRule.json", { appDb.replaceRuleDao.count }, 500L),
            DbItemDef("readRecord.json", { appDb.readRecordDao.count }, 200L),
            DbItemDef("readRecordDetail.json", { appDb.readRecordDao.getDetailsCount() }, 300L),
            DbItemDef("readRecordSession.json", { appDb.readRecordDao.getSessionsCount() }, 250L),
            DbItemDef("searchHistory.json", { appDb.searchKeywordDao.count }, 100L),
            DbItemDef("txtTocRule.json", { appDb.txtTocRuleDao.count }, 500L),
            DbItemDef("httpTTS.json", { appDb.httpTTSDao.count }, 500L),
            DbItemDef("keyboardAssists.json", { appDb.keyboardAssistsDao.count }, 300L),
            DbItemDef("dictRule.json", { appDb.dictRuleDao.count }, 500L),
            DbItemDef("servers.json", { appDb.serverDao.count }, 300L),
            DbItemDef("homepage.json", { appDb.homepageModuleDao.count + appDb.homepageCustomSetDao.count }, 300L)
        )
        dbItems.forEach { (fileName, countProvider, perItemSize) ->
            addItem(fileName, countProvider() * perItemSize)
        }

        val runtimeCacheCount = appDb.cacheDao.getRuntimeSourceCacheCount(System.currentTimeMillis())
        addItem("runtimeSourceCache.json", runtimeCacheCount * 800L)

        val highlightRuleSize = GSON.toJson(HighlightRuleStore.backupData(appCtx)).length.toLong()
        addItem(HighlightRuleStore.backupFileName, highlightRuleSize)

        addBookCacheItems(::addItem)
        addConfigItems(::addItem)
        addBackgroundItems(::addItem)
        addCoverGalleryItem(::addItem)

        return BackupOverview(items, totalSize, selectedSize)
    }

    private fun addBookCacheItems(addItem: (String, Long) -> Unit) {
        val selectedBooks = BookCacheSelectorConfig.getSelectedBooks()
        val cacheDir = File(BookHelp.cachePath)
        var bookCacheSize = 0L
        var chapterCount = 0
        if (cacheDir.exists()) {
            selectedBooks.forEach { book ->
                val bookFolder = File(cacheDir, book.getFolderName())
                if (bookFolder.exists()) {
                    bookCacheSize += bookFolder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    chapterCount += appDb.bookChapterDao.getChapterCount(book.bookUrl)
                }
            }
        }
        addItem("book_cache", bookCacheSize)
        addItem("bookChapterCache.json", chapterCount * 400L)
        addItem("bookCacheIndex.json", selectedBooks.size * 300L)
        addItem("bookCacheBooks.json", selectedBooks.size * 600L)
    }

    private fun addConfigItems(addItem: (String, Long) -> Unit) {
        listOf(
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml",
            "videoConfig.xml"
        ).forEach { fileName ->
            val file = File(appCtx.filesDir, fileName)
            addItem(fileName, if (file.exists()) file.length() else 0L)
        }

        val directLinkConfig = DirectLinkUpload.getConfig()
        val directLinkSize = directLinkConfig?.let { GSON.toJson(it).length.toLong() } ?: 0L
        addItem(DirectLinkUpload.ruleFileName, directLinkSize)
    }

    private fun addBackgroundItems(addItem: (String, Long) -> Unit) {
        val totalBgSize = Backup.getBackgroundImageFiles()
            .distinctBy { it.absolutePath }
            .sumOf { it.length() }
        addItem("backgroundImages", totalBgSize)
    }

    private fun addCoverGalleryItem(addItem: (String, Long) -> Unit) {
        val imageSize = appDb.coverGalleryDao.allImages.asSequence()
            .map { File(it.path) }
            .filter { it.exists() && it.isFile }
            .distinctBy { it.absolutePath }
            .sumOf { it.length() }
        val estimatedDataSize = appDb.coverGalleryDao.allGroups.size * 100L
        addItem(CoverGalleryRepository.backupDirName, imageSize + estimatedDataSize)
    }

    private fun isFileSelectedForBackup(fileName: String): Boolean {
        return when (fileName) {
            "book_cache",
            "bookChapterCache.json",
            "bookCacheBooks.json",
            "bookCacheIndex.json" -> BackupSelectorConfig.isSelected("bookCache")

            "backgroundImages" -> BackupSelectorConfig.isSelected("backgroundImages")
            else -> {
                val key = BackupSelectorConfig.allItems.find {
                    it.fileName == fileName || it.fileName == selectorFileAliases[fileName]
                }?.key ?: return true
                BackupSelectorConfig.isSelected(key)
            }
        }
    }

    private fun isFileEnabledForRestore(fileName: String): Boolean {
        return when (fileName) {
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            "backgroundImages" -> !BackupConfig.ignoreReadConfig

            "book_cache",
            "bookChapterCache.json",
            "bookCacheBooks.json",
            "bookCacheIndex.json" -> !BackupConfig.ignoreBookCache

            else -> true
        }
    }

    fun categorizeItems(items: List<BackupFileInfo>, onlySelected: Boolean = false): List<CategoryInfo> {
        val filteredItems = if (onlySelected) items.filter { it.selected } else items
        val result = mutableListOf<CategoryInfo>()
        val assigned = mutableSetOf<String>()

        for (cfg in categoryConfig) {
            val matched = filteredItems.filter { item ->
                cfg.keywords.any { kw ->
                    item.fileName.lowercase().contains(kw.lowercase())
                } && !assigned.contains(item.fileName)
            }
            if (matched.isNotEmpty()) {
                matched.forEach { assigned.add(it.fileName) }
                result.add(
                    CategoryInfo(
                        name = cfg.name,
                        icon = cfg.icon,
                        items = matched,
                        totalSize = matched.sumOf { it.size }
                    )
                )
            }
        }

        val themeItems = filteredItems.filter {
            !assigned.contains(it.fileName) &&
                (it.fileName == "backgroundImages" || it.fileName == ThemeConfig.configFileName)
        }
        if (themeItems.isNotEmpty()) {
            result.add(
                CategoryInfo(
                    name = "主题",
                    icon = "🎨",
                    items = themeItems,
                    totalSize = themeItems.sumOf { it.size }
                )
            )
            themeItems.forEach { assigned.add(it.fileName) }
        }

        val remaining = filteredItems.filter { !assigned.contains(it.fileName) }
        if (remaining.isNotEmpty()) {
            result.add(
                CategoryInfo(
                    name = "其他",
                    icon = "📦",
                    items = remaining,
                    totalSize = remaining.sumOf { it.size }
                )
            )
        }

        return result
    }

    fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024))
        }
    }

    /**
     * 扫描已解压的备份目录，返回文件列表。
     *
     * 筛选 .json / .xml 文件以及子目录，计算每个条目的大小。
     * 与 [getBackupOverview] 不同：这个方法解析的是 ZIP 解压后的实际目录，
     * 而非本地数据库/配置。
     *
     * @param path 已解压的备份目录路径
     * @return 文件信息列表，空目录返回空列表
     */
    fun scanRestoreDirectory(path: String): List<BackupFileInfo> {
        return File(path).listFiles()
            ?.filter { entry ->
                entry.isFile && (entry.name.endsWith(".json") || entry.name.endsWith(".xml"))
                        || entry.isDirectory
            }
            ?.map { entry ->
                val size = if (entry.isDirectory) {
                    entry.walkTopDown()
                        .filter { f -> f.isFile }
                        .sumOf { f -> f.length() }
                } else {
                    entry.length()
                }
                BackupFileInfo(
                    fileName = entry.name,
                    displayName = displayNameMap[entry.name] ?: entry.name,
                    size = size,
                    selected = true
                )
            } ?: emptyList()
    }

    /**
     * 获取指定备份项的数据数量
     */
    fun getItemCount(key: String): Int {
        return when (key) {
            "bookshelf" -> appDb.bookDao.allBookCount
            "bookmark" -> appDb.bookmarkDao.count
            "bookGroup" -> appDb.bookGroupDao.count
            "bookSource" -> appDb.bookSourceDao.allCount()
            "rssSources" -> appDb.rssSourceDao.size
            "rssStar" -> appDb.rssStarDao.count
            "sourceSub" -> appDb.ruleSubDao.count
            "webSearchEngines" -> SearchEngineHelper.loadSearchEngines(appCtx).size
            "replaceRule" -> appDb.replaceRuleDao.count
            "readRecord" -> appDb.readRecordDao.count
            "readRecordDetail" -> appDb.readRecordDao.getDetailsCount()
            "readRecordSession" -> appDb.readRecordDao.getSessionsCount()
            "searchHistory" -> appDb.searchKeywordDao.count
            "txtTocRule" -> appDb.txtTocRuleDao.count
            "httpTTS" -> appDb.httpTTSDao.count
            "keyboardAssists" -> appDb.keyboardAssistsDao.count
            "dictRule" -> appDb.dictRuleDao.count
            "servers" -> appDb.serverDao.count
            "homepage" -> appDb.homepageModuleDao.count + appDb.homepageCustomSetDao.count
            "runtimeSourceCache" -> appDb.cacheDao.getRuntimeSourceCacheCount(System.currentTimeMillis())
            else -> 0
        }
    }
}
