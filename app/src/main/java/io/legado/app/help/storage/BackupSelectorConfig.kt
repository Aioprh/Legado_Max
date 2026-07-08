package io.legado.app.help.storage

import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx

/**
 * 备份选择器配置。
 *
 * 这个文件只维护可备份项定义和用户选择结果的读写，不依赖具体 UI 组件。
 */
@Suppress("ConstPropertyName")
object BackupSelectorConfig {

    private val configPath = FileUtils.getPath(appCtx.filesDir, "backupSelector.json")

    data class BackupItem(
        val key: String,
        val fileName: String,
        val title: String,
        val group: String,
        val iconEmoji: String? = null // Emoji图标
    )

    val allItems = listOf(
        BackupItem("coverGallery", CoverGalleryRepository.backupDirName, "封面图集", "配置", "🖼️"),
        BackupItem("bookshelf", "bookshelf.json", "书架", "数据库", "📚"),
        BackupItem("bookChapter", "bookChapter.json", "章节目录", "数据库", "📖"),
        BackupItem("bookmark", "bookmark.json", "书签", "数据库", "🔖"),
        BackupItem("bookGroup", "bookGroup.json", "书籍分组", "数据库", "📁"),
        BackupItem("bookSource", "bookSource.json", "书源", "数据库", "📗"),
        BackupItem("rssSources", "rssSources.json", "订阅源", "数据库", "📰"),
        BackupItem("rssStar", "rssStar.json", "订阅收藏", "数据库", "⭐"),
        BackupItem("sourceSub", "sourceSub.json", "源订阅链接", "数据库", "🔗"),
        BackupItem("webSearchEngines", "webSearchEngines.json", "搜索引擎规则", "配置", "🔍"),
        BackupItem("replaceRule", "replaceRule.json", "替换规则", "数据库", "🔧"),
        BackupItem("highlightRule", "highlightRule.json", "高亮规则", "配置", "✨"),
        BackupItem("readRecord", "readRecord.json", "阅读记录", "数据库", "📊"),
        BackupItem("readRecordDetail", "readRecordDetail.json", "阅读记录详情", "数据库", "📝"),
        BackupItem("searchHistory", "searchHistory.json", "搜索历史", "数据库", "🔍"),
        BackupItem("txtTocRule", "txtTocRule.json", "TXT目录规则", "数据库", "📋"),
        BackupItem("httpTTS", "httpTTS.json", "TTS配置", "数据库", "🔊"),
        BackupItem("keyboardAssists", "keyboardAssists.json", "键盘辅助", "数据库", "⌨️"),
        BackupItem("dictRule", "dictRule.json", "词典规则", "数据库", "📖"),
        BackupItem("servers", "servers.json", "服务器配置", "数据库", "🖥️"),
        BackupItem("runtimeSourceCache", "runtimeSourceCache.json", "书源运行数据", "数据库", "⚡"),
        BackupItem("readConfig", "readConfig.json", "阅读样式配置", "配置", "🎨"),
        BackupItem("readShareConfig", "readShareConfig.json", "阅读分享配置", "配置", "📤"),
        BackupItem("themeConfig", "themeConfig.json", "主题配置", "配置", "🎨"),
        BackupItem("coverRule", "coverRule.json", "封面规则", "配置", "🖼️"),
        BackupItem("directLinkRule", "directLinkRule.json", "直链规则", "配置", "🔗"),
        BackupItem("homepage", "homepage.json", "首页", "数据库", "🏠"),
        BackupItem("appConfig", "config.xml", "应用配置", "配置", "⚙️"),
        BackupItem("videoConfig", "videoConfig.xml", "视频配置", "配置", "🎬"),
        BackupItem("backgroundImages", "bg", "背景图片", "其他", "🌄"),
        BackupItem("bookCache", "book_cache", "书籍缓存", "其他", "💾")
    )

    val groups = allItems.map { it.group }.distinct()

    val groupItems: Map<String, List<BackupItem>> = allItems.groupBy { it.group }

    private var selectedMap: MutableMap<String, Boolean> = load()

    private fun load(): MutableMap<String, Boolean> {
        val map = HashMap<String, Boolean>()
        val file = FileUtils.createFileIfNotExist(configPath)
        if (file.exists() && file.length() > 0) {
            val json = file.readText()
            GSON.fromJsonObject<Map<String, Boolean>>(json).getOrNull()?.let {
                map.putAll(it)
            }
        }
        return map
    }

    fun isSelected(key: String): Boolean {
        return selectedMap[key] ?: true
    }

    fun getSelectedKeys(): Set<String> {
        return allItems
            .filter { isSelected(it.key) }
            .map { it.key }
            .toSet()
    }

    fun setSelected(key: String, selected: Boolean) {
        selectedMap[key] = selected
    }

    // 供选择器弹窗在确认时一次性提交内存中的完整选择结果。
    fun setSelectedKeys(keys: Set<String>) {
        allItems.forEach { selectedMap[it.key] = it.key in keys }
    }

    fun selectAll() {
        allItems.forEach { selectedMap[it.key] = true }
    }

    fun deselectAll() {
        allItems.forEach { selectedMap[it.key] = false }
    }

    fun getSelectedFileNames(): List<String> {
        return allItems.filter { isSelected(it.key) }.map { it.fileName }
    }

    fun isAllSelected(): Boolean {
        return allItems.all { isSelected(it.key) }
    }

    fun isNoneSelected(): Boolean {
        return allItems.none { isSelected(it.key) }
    }

    fun save() {
        val json = GSON.toJson(selectedMap.toMap())
        FileUtils.createFileIfNotExist(configPath).writeText(json)
    }

    /**
     * 获取分组的 Emoji 图标
     */
    fun getGroupIcon(group: String): String? {
        return when (group) {
            "数据库" -> "📊"
            "配置" -> "⚙️"
            "其他" -> "📦"
            else -> null
        }
    }
}
