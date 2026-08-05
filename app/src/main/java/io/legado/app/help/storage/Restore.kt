package io.legado.app.help.storage

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.*
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.help.*
import io.legado.app.help.book.*
import io.legado.app.help.config.*
import io.legado.app.model.BookCover
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.model.localBook.LocalBook
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStore
import io.legado.app.ui.book.read.websearch.SearchEngine
import io.legado.app.ui.book.read.websearch.SearchEngineHelper
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.*
import io.legado.app.utils.compress.ZipUtils
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileReader

object Restore {
    private const val runtimeSourceCacheFileName = "runtimeSourceCache.json"
    private const val bookCacheFolderName = "book_cache"
    private const val bookCacheIndexFileName = "bookCacheIndex.json"
    private const val bookCacheBooksFileName = "bookCacheBooks.json"

    private val mutex = Mutex()
    private const val TAG = "Restore"

    private val themeRestorePrefKeys = arrayOf(
        PreferKey.dThemeName, PreferKey.dNThemeName,
        PreferKey.cPrimary, PreferKey.cAccent,
        PreferKey.cBackground, PreferKey.cBBackground,
        PreferKey.bgImage, PreferKey.bgImageBlurring,
        PreferKey.tNavBar,
        PreferKey.cNPrimary, PreferKey.cNAccent,
        PreferKey.cNBackground, PreferKey.cNBBackground,
        PreferKey.bgImageN, PreferKey.bgImageNBlurring,
        PreferKey.tNavBarN
    )

    // ======================== 公开入口 ========================

    suspend fun restore(context: Context, uri: Uri, onProgress: ((String) -> Unit)? = null) {
        LogUtils.d(TAG, "开始恢复备份 uri:$uri")
        runCatching {
            onProgress?.invoke(BackupInfoHelper.getDisplayName("unzipBackup"))
            FileUtils.delete(Backup.backupPath)
            if (uri.isContentScheme()) {
                DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                    ZipUtils.unZipToPath(it, Backup.backupPath)
                }
            } else {
                ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
            }
        }.onFailure {
            AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            return
        }
        runCatching {
            restoreLocked(Backup.backupPath, onProgress)
            LocalConfig.lastBackup = System.currentTimeMillis()
            LocalConfig.lastRestore = System.currentTimeMillis()
        }.onFailure {
            appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
            AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
        }
    }

    suspend fun restoreLocked(path: String, onProgress: ((String) -> Unit)? = null) {
        mutex.withLock { restoreFull(path, onProgress) }
    }

    suspend fun restoreSelected(
        context: Context,
        path: String,
        selectedFiles: List<String>,
        onProgress: ((String) -> Unit)? = null
    ) {
        LogUtils.d(TAG, "开始选择性恢复备份 path:$path, files:${selectedFiles.joinToString()}")
        mutex.withLock {
            try {
                restoreSelectedFiles(path, selectedFiles, onProgress)
                LocalConfig.lastBackup = System.currentTimeMillis()
                LocalConfig.lastRestore = System.currentTimeMillis()
            } catch (e: Exception) {
                appCtx.toastOnUi("恢复备份出错\n${e.localizedMessage}")
                AppLog.put("选择性恢复备份出错\n${e.localizedMessage}", e)
            }
        }
    }

    // ======================== 完整恢复 ========================

    private suspend fun restoreFull(path: String, onProgress: ((String) -> Unit)? = null) {
        val aes = BackupAES()
        fun progress(fileName: String) {
            onProgress?.invoke(BackupInfoHelper.getDisplayName(fileName))
        }

        progress("bookshelf.json")
        appDb.bookDao.deleteAll()
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { it.upType() }
            it.filter { it.isLocal }.forEach { it.coverUrl = LocalBook.getCoverPath(it) }
            val ignoreLocal = BackupConfig.ignoreLocalBook
            appDb.bookDao.insert(*it.filterNot { ignoreLocal && it.isLocal }.toTypedArray())
        }

        progress("bookmark.json")
        appDb.bookmarkDao.deleteAll()
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }

        progress("bookGroup.json")
        appDb.bookGroupDao.deleteAll()
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }

        progress("bookSource.json")
        appDb.bookSourceDao.deleteAll()
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            File(path, "bookSource.json").takeIf { it.exists() }?.readText()?.let {
                ImportOldData.importOldSource(it)
            }
        }

        progress("rssSources.json")
        appDb.rssSourceDao.deleteAll()
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }

        progress("rssStar.json")
        appDb.rssStarDao.deleteAll()
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }

        progress("sourceSub.json")
        appDb.ruleSubDao.deleteAll()
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }

        progress("webSearchEngines.json")
        File(path, "webSearchEngines.json").takeIf { it.exists() }?.readText()?.let {
            GSON.fromJsonArray<SearchEngine>(it).getOrNull()?.let { engines ->
                SearchEngineHelper.saveSearchEngines(appCtx, engines)
            }
        }

        progress("homepage.json")
        File(path, "homepage.json").takeIf { it.exists() }?.readText()?.let { json ->
            GSON.fromJsonObject<Map<String, JsonElement>>(json).getOrNull()?.let { obj ->
                appDb.homepageModuleDao.deleteAll()
                (obj["modules"] as? JsonArray)?.let {
                    GSON.fromJsonArray<HomepageModule>(it.toString()).getOrNull()?.let { modules ->
                        appDb.homepageModuleDao.upsertAll(modules)
                    }
                }
                appDb.homepageCustomSetDao.deleteAll()
                (obj["customSets"] as? JsonArray)?.let {
                    GSON.fromJsonArray<HomepageCustomSet>(it.toString()).getOrNull()?.forEach { set ->
                        appDb.homepageCustomSetDao.upsert(set)
                    }
                }
            }
        }

        progress("replaceRule.json")
        appDb.replaceRuleDao.deleteAll()
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }

        progress(HighlightRuleStore.backupFileName)
        File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                HighlightRuleStore.restoreBackupData(appCtx, it, path)
            }
        }

        progress("searchHistory.json")
        appDb.searchKeywordDao.deleteAll()
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }

        progress("txtTocRule.json")
        appDb.txtTocRuleDao.deleteAll()
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }

        progress("httpTTS.json")
        appDb.httpTTSDao.deleteAll()
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }

        progress("dictRule.json")
        appDb.dictRuleDao.deleteAll()
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }

        progress("keyboardAssists.json")
        appDb.keyboardAssistsDao.deleteAll()
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }

        progress(CoverGalleryRepository.backupDirName)
        restoreCoverGallery(path)

        progress("readRecord.json")
        val records = fileToListT<ReadRecord>(path, "readRecord.json").orEmpty()
        val details = fileToListT<ReadRecordDetail>(path, "readRecordDetail.json").orEmpty()
        val sessions = fileToListT<ReadRecordSession>(path, "readRecordSession.json").orEmpty()
        if (records.isNotEmpty() || details.isNotEmpty() || sessions.isNotEmpty()) {
            val bookAuthorMap = appDb.bookDao.all.mapNotNull { it.author.trim().ifBlank { null }?.let { it.name to it } }.toMap()
            appDb.withTransaction {
                appDb.readRecordDao.clear()
                appDb.readRecordDao.clearDetails()
                appDb.readRecordDao.clearSessions()
                ReadRecordRepository(appDb.readRecordDao).apply {
                    importRecords(records, details, sessions)
                    repairRecords { bookAuthorMap[it] }
                }
            }
            appCtx.putPrefInt(PreferKey.readRecordRepairVersion, ReadRecordRepository.CURRENT_REPAIR_VERSION)
        }

        progress("servers.json")
        appDb.serverDao.deleteAll()
        File(path, "servers.json").takeIf { it.exists() }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) json = aes.decryptStr(json)
            GSON.fromJsonArray<Server>(json).getOrNull()?.let { appDb.serverDao.insert(*it.toTypedArray()) }
        }

        progress(DirectLinkUpload.ruleFileName)
        DirectLinkUpload.delConfig()
        File(path, DirectLinkUpload.ruleFileName).takeIf { it.exists() }?.runCatching {
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, readText())
        }

        progress(ThemeConfig.configFileName)
        ThemeConfig.replaceConfigs(emptyList())
        File(path, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
            val configs = GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrNull()
            FileUtils.delete(ThemeConfig.configFilePath)
            copyTo(File(ThemeConfig.configFilePath))
            ThemeConfig.replaceConfigs(configs)
        }

        progress(BookCover.configFileName)
        BookCover.delCoverRule()
        File(path, BookCover.configFileName).takeIf { it.exists() }?.runCatching {
            BookCover.saveCoverRule(readText())
            CoverImageView.clearAllCache()
        }

        if (!BackupConfig.ignoreReadConfig) {
            progress("backgroundImages")
            restoreReadConfigBackgrounds(path)
            progress(ReadBookConfig.configFileName)
            File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }
            progress(ReadBookConfig.shareConfigFileName)
            File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }
        }
        fixReadConfigBackgroundPaths()

        progress("config.xml")
        val allowHighlight = !File(path, HighlightRuleStore.backupFileName).exists()
        readBackupPrefs(path, "config")?.let { map ->
            clearThemeRestorePrefs()
            appCtx.defaultSharedPreferences.edit().apply {
                map.forEach { (key, value) ->
                    if (BackupConfig.keyIsNotIgnore(key, allowHighlight) || key in themeRestorePrefKeys) {
                        when (key) {
                            PreferKey.webDavPassword -> {
                                runCatching { aes.decryptStr(value.toString()) }.getOrNull()?.let {
                                    putString(key, it)
                                } ?: run {
                                    if (appCtx.getPrefString(PreferKey.webDavPassword).isNullOrBlank()) {
                                        putString(key, value.toString())
                                    }
                                }
                            }
                            else -> when (value) {
                                is Int -> putInt(key, value)
                                is Boolean -> putBoolean(key, value)
                                is Long -> putLong(key, value)
                                is Float -> putFloat(key, value)
                                is String -> putString(key, value)
                            }
                        }
                    }
                }
                apply()
            }
        }
        if (allowHighlight) HighlightRuleStore.clearCache()

        progress("themeBackgroundImages")
        restoreThemeBackgrounds(path, clearExisting = true)
        fixThemeBackgroundPaths()
        fixThemeConfigBackgroundPaths()

        progress("videoConfig.xml")
        readBackupPrefs(path, "videoConfig")?.let { map ->
            appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                clear()
                map.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
        }

        progress(runtimeSourceCacheFileName)
        restoreRuntimeSourceCaches(path)

        progress(bookCacheFolderName)
        restoreBookCache(path)

        progress("applyRestoreConfig")
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    // ======================== 选择性恢复 ========================

    private suspend fun restoreSelectedFiles(
        path: String,
        selectedFiles: List<String>,
        onProgress: ((String) -> Unit)? = null
    ) {
        val aes = BackupAES()
        val selectedSet = selectedFiles.toSet()
        fun progress(fileName: String) {
            onProgress?.invoke(BackupInfoHelper.getDisplayName(fileName))
        }

        if ("bookshelf.json" in selectedSet) {
            progress("bookshelf.json")
            appDb.bookDao.deleteAll()
            fileToListT<Book>(path, "bookshelf.json")?.let {
                it.forEach { it.upType() }
                it.filter { it.isLocal }.forEach { it.coverUrl = LocalBook.getCoverPath(it) }
                val ignoreLocal = BackupConfig.ignoreLocalBook
                appDb.bookDao.insert(*it.filterNot { ignoreLocal && it.isLocal }.toTypedArray())
            }
        }

        if ("bookmark.json" in selectedSet) {
            progress("bookmark.json")
            appDb.bookmarkDao.deleteAll()
            fileToListT<Bookmark>(path, "bookmark.json")?.let {
                appDb.bookmarkDao.insert(*it.toTypedArray())
            }
        }

        if ("bookGroup.json" in selectedSet) {
            progress("bookGroup.json")
            appDb.bookGroupDao.deleteAll()
            fileToListT<BookGroup>(path, "bookGroup.json")?.let {
                appDb.bookGroupDao.insert(*it.toTypedArray())
            }
        }

        if ("bookSource.json" in selectedSet) {
            progress("bookSource.json")
            appDb.bookSourceDao.deleteAll()
            fileToListT<BookSource>(path, "bookSource.json")?.let {
                appDb.bookSourceDao.insert(*it.toTypedArray())
            } ?: run {
                File(path, "bookSource.json").takeIf { it.exists() }?.readText()?.let {
                    ImportOldData.importOldSource(it)
                }
            }
        }

        if ("rssSources.json" in selectedSet) {
            progress("rssSources.json")
            appDb.rssSourceDao.deleteAll()
            fileToListT<RssSource>(path, "rssSources.json")?.let {
                appDb.rssSourceDao.insert(*it.toTypedArray())
            }
        }

        if ("rssStar.json" in selectedSet) {
            progress("rssStar.json")
            appDb.rssStarDao.deleteAll()
            fileToListT<RssStar>(path, "rssStar.json")?.let {
                appDb.rssStarDao.insert(*it.toTypedArray())
            }
        }

        if ("sourceSub.json" in selectedSet) {
            progress("sourceSub.json")
            appDb.ruleSubDao.deleteAll()
            fileToListT<RuleSub>(path, "sourceSub.json")?.let {
                appDb.ruleSubDao.insert(*it.toTypedArray())
            }
        }

        if ("webSearchEngines.json" in selectedSet) {
            progress("webSearchEngines.json")
            File(path, "webSearchEngines.json").takeIf { it.exists() }?.readText()?.let {
                GSON.fromJsonArray<SearchEngine>(it).getOrNull()?.let { engines ->
                    SearchEngineHelper.saveSearchEngines(appCtx, engines)
                }
            }
        }

        if ("homepage.json" in selectedSet) {
            progress("homepage.json")
            File(path, "homepage.json").takeIf { it.exists() }?.readText()?.let { json ->
                GSON.fromJsonObject<Map<String, JsonElement>>(json).getOrNull()?.let { obj ->
                    appDb.homepageModuleDao.deleteAll()
                    (obj["modules"] as? JsonArray)?.let {
                        GSON.fromJsonArray<HomepageModule>(it.toString()).getOrNull()?.let { modules ->
                            appDb.homepageModuleDao.upsertAll(modules)
                        }
                    }
                    appDb.homepageCustomSetDao.deleteAll()
                    (obj["customSets"] as? JsonArray)?.let {
                        GSON.fromJsonArray<HomepageCustomSet>(it.toString()).getOrNull()?.forEach { set ->
                            appDb.homepageCustomSetDao.upsert(set)
                        }
                    }
                }
            }
        }

        if ("replaceRule.json" in selectedSet) {
            progress("replaceRule.json")
            appDb.replaceRuleDao.deleteAll()
            fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
                appDb.replaceRuleDao.insert(*it.toTypedArray())
            }
        }

        if (HighlightRuleStore.backupFileName in selectedSet) {
            progress(HighlightRuleStore.backupFileName)
            File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
                GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                    HighlightRuleStore.restoreBackupData(appCtx, it, path)
                }
            }
        }

        if ("searchHistory.json" in selectedSet) {
            progress("searchHistory.json")
            appDb.searchKeywordDao.deleteAll()
            fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
                appDb.searchKeywordDao.insert(*it.toTypedArray())
            }
        }

        if ("txtTocRule.json" in selectedSet) {
            progress("txtTocRule.json")
            appDb.txtTocRuleDao.deleteAll()
            fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
                appDb.txtTocRuleDao.insert(*it.toTypedArray())
            }
        }

        if ("httpTTS.json" in selectedSet) {
            progress("httpTTS.json")
            appDb.httpTTSDao.deleteAll()
            fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
                appDb.httpTTSDao.insert(*it.toTypedArray())
            }
        }

        if ("dictRule.json" in selectedSet) {
            progress("dictRule.json")
            appDb.dictRuleDao.deleteAll()
            fileToListT<DictRule>(path, "dictRule.json")?.let {
                appDb.dictRuleDao.insert(*it.toTypedArray())
            }
        }

        if ("keyboardAssists.json" in selectedSet) {
            progress("keyboardAssists.json")
            appDb.keyboardAssistsDao.deleteAll()
            fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
                appDb.keyboardAssistsDao.insert(*it.toTypedArray())
            }
        }

        if (CoverGalleryRepository.backupDirName in selectedSet) {
            progress(CoverGalleryRepository.backupDirName)
            restoreCoverGallery(path)
        }

        if ("readRecord.json" in selectedSet || "readRecordDetail.json" in selectedSet || "readRecordSession.json" in selectedSet) {
            progress("readRecord.json")
            val records = if ("readRecord.json" in selectedSet) fileToListT<ReadRecord>(path, "readRecord.json").orEmpty() else emptyList()
            val details = if ("readRecordDetail.json" in selectedSet) fileToListT<ReadRecordDetail>(path, "readRecordDetail.json").orEmpty() else emptyList()
            val sessions = if ("readRecordSession.json" in selectedSet) fileToListT<ReadRecordSession>(path, "readRecordSession.json").orEmpty() else emptyList()
            if (records.isNotEmpty() || details.isNotEmpty() || sessions.isNotEmpty()) {
                val bookAuthorMap = appDb.bookDao.all.mapNotNull { it.author.trim().ifBlank { null }?.let { it.name to it } }.toMap()
                appDb.withTransaction {
                    appDb.readRecordDao.clear()
                    appDb.readRecordDao.clearDetails()
                    appDb.readRecordDao.clearSessions()
                    ReadRecordRepository(appDb.readRecordDao).apply {
                        importRecords(records, details, sessions)
                        repairRecords { bookAuthorMap[it] }
                    }
                }
                appCtx.putPrefInt(PreferKey.readRecordRepairVersion, ReadRecordRepository.CURRENT_REPAIR_VERSION)
            }
        }

        if ("servers.json" in selectedSet) {
            progress("servers.json")
            appDb.serverDao.deleteAll()
            File(path, "servers.json").takeIf { it.exists() }?.runCatching {
                var json = readText()
                if (!json.isJsonArray()) json = aes.decryptStr(json)
                GSON.fromJsonArray<Server>(json).getOrNull()?.let { appDb.serverDao.insert(*it.toTypedArray()) }
            }
        }

        if (DirectLinkUpload.ruleFileName in selectedSet) {
            progress(DirectLinkUpload.ruleFileName)
            DirectLinkUpload.delConfig()
            File(path, DirectLinkUpload.ruleFileName).takeIf { it.exists() }?.runCatching {
                ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, readText())
            }
        }

        if (ThemeConfig.configFileName in selectedSet) {
            progress(ThemeConfig.configFileName)
            ThemeConfig.replaceConfigs(emptyList())
            File(path, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
                val configs = GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrNull()
                FileUtils.delete(ThemeConfig.configFilePath)
                copyTo(File(ThemeConfig.configFilePath))
                ThemeConfig.replaceConfigs(configs)
            }
        }

        if (BookCover.configFileName in selectedSet) {
            progress(BookCover.configFileName)
            BookCover.delCoverRule()
            File(path, BookCover.configFileName).takeIf { it.exists() }?.runCatching {
                BookCover.saveCoverRule(readText())
                CoverImageView.clearAllCache()
            }
        }

        if (!BackupConfig.ignoreReadConfig && (ReadBookConfig.configFileName in selectedSet || ReadBookConfig.shareConfigFileName in selectedSet)) {
            progress("backgroundImages")
            restoreReadConfigBackgrounds(path)
            if (ReadBookConfig.configFileName in selectedSet) {
                progress(ReadBookConfig.configFileName)
                File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
                    FileUtils.delete(ReadBookConfig.configFilePath)
                    copyTo(File(ReadBookConfig.configFilePath))
                    ReadBookConfig.initConfigs()
                }
            }
            if (ReadBookConfig.shareConfigFileName in selectedSet) {
                progress(ReadBookConfig.shareConfigFileName)
                File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
                    FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                    copyTo(File(ReadBookConfig.shareConfigFilePath))
                    ReadBookConfig.initShareConfig()
                }
            }
        }
        fixReadConfigBackgroundPaths()

        if ("config.xml" in selectedSet) {
            progress("config.xml")
            val allowHighlight = !File(path, HighlightRuleStore.backupFileName).exists()
            readBackupPrefs(path, "config")?.let { map ->
                clearThemeRestorePrefs()
                appCtx.defaultSharedPreferences.edit().apply {
                    map.forEach { (key, value) ->
                        if (BackupConfig.keyIsNotIgnore(key, allowHighlight) || key in themeRestorePrefKeys) {
                            when (key) {
                                PreferKey.webDavPassword -> {
                                    runCatching { aes.decryptStr(value.toString()) }.getOrNull()?.let {
                                        putString(key, it)
                                    } ?: run {
                                        if (appCtx.getPrefString(PreferKey.webDavPassword).isNullOrBlank()) {
                                            putString(key, value.toString())
                                        }
                                    }
                                }
                                else -> when (value) {
                                    is Int -> putInt(key, value)
                                    is Boolean -> putBoolean(key, value)
                                    is Long -> putLong(key, value)
                                    is Float -> putFloat(key, value)
                                    is String -> putString(key, value)
                                }
                            }
                        }
                    }
                    apply()
                }
            }
            if (allowHighlight) HighlightRuleStore.clearCache()
        }

        progress("themeBackgroundImages")
        restoreThemeBackgrounds(
            backupPath = path,
            clearExisting = "config.xml" in selectedSet || ThemeConfig.configFileName in selectedSet
        )
        fixThemeBackgroundPaths()
        fixThemeConfigBackgroundPaths()

        if ("videoConfig.xml" in selectedSet) {
            progress("videoConfig.xml")
            readBackupPrefs(path, "videoConfig")?.let { map ->
                appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                    clear()
                    map.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                        }
                    }
                    apply()
                }
            }
        }

        if (runtimeSourceCacheFileName in selectedSet) {
            progress(runtimeSourceCacheFileName)
            restoreRuntimeSourceCaches(path)
        }

        if (bookCacheFolderName in selectedSet ||
            bookCacheIndexFileName in selectedSet ||
            bookCacheBooksFileName in selectedSet ||
            "bookChapterCache.json" in selectedSet) {
            progress(bookCacheFolderName)
            restoreBookCache(path)
        }

        progress("applyRestoreConfig")
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    // ======================== 辅助方法 ========================

    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        val file = File(path, fileName)
        return if (file.exists()) {
            FileInputStream(file).use {
                GSON.fromJsonArray<T>(it).getOrThrow()
            }
        } else null
    }

    private fun restoreRuntimeSourceCaches(path: String) {
        val file = File(path, runtimeSourceCacheFileName)
        if (!file.exists()) return
        fileToListT<Cache>(path, runtimeSourceCacheFileName)?.let {
            appDb.cacheDao.deleteAllRuntimeSourceCaches()
            AppCacheManager.clearSourceVariables()
            if (it.isNotEmpty()) appDb.cacheDao.insert(*it.toTypedArray())
        }
    }

    private suspend fun restoreCoverGallery(path: String) {
        val galleryDir = File(path, CoverGalleryRepository.backupDirName)
        if (!galleryDir.exists() || !galleryDir.isDirectory) return
        val oldGroupIds = appDb.coverGalleryDao.allGroups.map { it.id }

        appDb.coverGalleryDao.deleteAllImages()
        appDb.coverGalleryDao.deleteAllGroups()
        appDb.cacheDao.deleteRuntimeSourceCachesByPrefix(CoverGalleryRepository.randomSeedKeyPrefix)
        oldGroupIds.forEach {
            CacheManager.deleteMemory(CoverGalleryRepository.randomSeedKeyPrefix + it)
        }

        val targetDir = appCtx.externalFiles.getFile("covers").createFolderIfNotExist()
        val usedImageNames = hashSetOf<String>()
        galleryDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEachIndexed { groupIndex, groupDir ->
                val groupId = appDb.coverGalleryDao.insertGroup(
                    CoverGalleryGroup(name = groupDir.name, order = groupIndex)
                )
                val images = groupDir.listFiles()
                    ?.filter { it.isFile && it.isCoverGalleryImageFile() }
                    ?.sortedBy { it.name }
                    ?.mapIndexed { imageIndex, imageFile ->
                        val targetFile = File(
                            targetDir,
                            uniqueCoverGalleryImageName(imageFile.name, usedImageNames)
                        )
                        imageFile.copyTo(targetFile, overwrite = true)
                        CoverGalleryImage(groupId = groupId, path = targetFile.absolutePath, order = imageIndex)
                    }
                    .orEmpty()
                if (images.isNotEmpty()) {
                    appDb.coverGalleryDao.insertImages(*images.toTypedArray())
                }
            }

        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    private fun File.isCoverGalleryImageFile() = extension.lowercase() in setOf("jpg","jpeg","png","webp","gif","bmp","heic","heif")

    private fun uniqueCoverGalleryImageName(fileName: String, used: MutableSet<String>): String {
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var candidate = fileName
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = if (ext.isBlank()) "$base-$suffix" else "$base-$suffix.$ext"
            suffix++
        }
        return candidate
    }

    private fun readBackupPrefs(path: String, fileName: String): Map<String, Any>? {
        val file = File(path, "$fileName.xml")
        if (!file.exists()) return null
        return runCatching {
            val map = linkedMapOf<String, Any>()
            file.inputStream().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "utf-8")
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val name = parser.getAttributeValue(null, "name")
                        if (!name.isNullOrBlank()) {
                            when (parser.name) {
                                "string" -> map[name] = parser.nextText()
                                "int" -> parser.getAttributeValue(null, "value")?.toIntOrNull()?.let { map[name] = it }
                                "long" -> parser.getAttributeValue(null, "value")?.toLongOrNull()?.let { map[name] = it }
                                "float" -> parser.getAttributeValue(null, "value")?.toFloatOrNull()?.let { map[name] = it }
                                "boolean" -> parser.getAttributeValue(null, "value")?.toBooleanStrictOrNull()?.let { map[name] = it }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            map
        }.onFailure { AppLog.put("$fileName.xml读取出错\n${it.localizedMessage}", it) }.getOrNull()
    }

    // -------- 阅读背景相关 --------
    private fun restoreReadConfigBackgrounds(path: String) {
        val bgNames = linkedSetOf<String>()
        File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { collectBgNames(it, bgNames) }
        File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.let { collectBgNames(it, bgNames) }
        clearReadConfigBackgrounds()
        if (bgNames.isEmpty()) return
        val bgDir = appCtx.externalFiles.getFile("bg").apply { if (!exists()) mkdirs() }
        bgNames.forEach { bgName ->
            val backupFile = File(path, "bg${File.separator}$bgName").takeIf { it.exists() && it.isFile }
                ?: File(path, bgName).takeIf { it.exists() && it.isFile }
            backupFile?.copyTo(File(bgDir, bgName), overwrite = true)
        }
    }

    private fun collectBgNames(config: ReadBookConfig.Config, bgNames: MutableSet<String>) {
        if (config.bgType == 2) bgNames.add(File(config.bgStr).name)
        if (config.bgTypeNight == 2) bgNames.add(File(config.bgStrNight).name)
        if (config.bgTypeEInk == 2) bgNames.add(File(config.bgStrEInk).name)
    }

    private fun clearReadConfigBackgrounds() {
        val bgDir = appCtx.externalFiles.getFile("bg")
        FileUtils.delete(bgDir)
        bgDir.mkdirs()
    }

    private fun clearThemeBackgrounds() {
        listOf(PreferKey.bgImage, PreferKey.bgImageN).forEach { prefKey ->
            val bgDir = appCtx.externalFiles.getFile(prefKey)
            FileUtils.delete(bgDir)
            bgDir.mkdirs()
        }
    }

    private fun fixReadConfigBackgroundPaths() {
        var updated = false
        ReadBookConfig.configList.forEach { if (fixReadConfigBackgroundPath(it)) updated = true }
        runCatching { ReadBookConfig.shareConfig }.getOrNull()?.let { if (fixReadConfigBackgroundPath(it)) updated = true }
        if (updated) ReadBookConfig.save()
    }

    private fun fixReadConfigBackgroundPath(config: ReadBookConfig.Config): Boolean {
        var updated = false
        if (config.bgType == 2) {
            val fixed = fixReadBgPath(config.bgStr)
            if (fixed != config.bgStr) { config.bgStr = fixed; updated = true }
        }
        if (config.bgTypeNight == 2) {
            val fixed = fixReadBgPath(config.bgStrNight)
            if (fixed != config.bgStrNight) { config.bgStrNight = fixed; updated = true }
        }
        if (config.bgTypeEInk == 2) {
            val fixed = fixReadBgPath(config.bgStrEInk)
            if (fixed != config.bgStrEInk) { config.bgStrEInk = fixed; updated = true }
        }
        return updated
    }

    private fun fixReadBgPath(bgPath: String): String {
        if (bgPath.isBlank()) return bgPath
        val bgName = File(bgPath).name
        val local = appCtx.externalFiles.getFile("bg", bgName)
        return if (local.exists()) local.absolutePath else bgPath
    }

    // -------- 主题背景相关 --------
    private fun restoreThemeBackgrounds(backupPath: String, clearExisting: Boolean) {
        if (clearExisting) clearThemeBackgrounds()
        val configPrefs = readBackupPrefs(backupPath, "config")
        (configPrefs?.get(PreferKey.bgImage) as? String)?.let { restoreThemeBgFile(backupPath, it, PreferKey.bgImage) }
        (configPrefs?.get(PreferKey.bgImageN) as? String)?.let { restoreThemeBgFile(backupPath, it, PreferKey.bgImageN) }
        File(backupPath, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { config ->
            val bgPath = config.backgroundImgPath ?: return@forEach
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            restoreThemeBgFile(backupPath, bgPath, prefKey)
        }
    }

    private fun restoreThemeBgFile(backupPath: String, bgPath: String, prefKey: String) {
        if (bgPath.isBlank()) return
        val bgName = if (bgPath.startsWith("http")) ThemeConfig.getUrlToFile(bgPath) else File(bgPath).name
        val backupFile = File(backupPath, "$prefKey${File.separator}$bgName").takeIf { it.exists() && it.isFile }
            ?: File(backupPath, bgName).takeIf { it.exists() && it.isFile }
        if (backupFile != null) {
            val targetDir = appCtx.externalFiles.getFile(prefKey).apply { if (!exists()) mkdirs() }
            backupFile.copyTo(File(targetDir, bgName), overwrite = true)
        }
    }

    private fun clearThemeRestorePrefs() {
        appCtx.defaultSharedPreferences.edit { themeRestorePrefKeys.forEach(::remove) }
    }

    private fun fixThemeBackgroundPaths() {
        appCtx.getPrefString(PreferKey.bgImage)?.let { bgPath ->
            val fixed = fixThemeBgPath(bgPath, PreferKey.bgImage)
            if (fixed != bgPath) appCtx.putPrefString(PreferKey.bgImage, fixed)
        }
        appCtx.getPrefString(PreferKey.bgImageN)?.let { bgPath ->
            val fixed = fixThemeBgPath(bgPath, PreferKey.bgImageN)
            if (fixed != bgPath) appCtx.putPrefString(PreferKey.bgImageN, fixed)
        }
    }

    private fun fixThemeConfigBackgroundPaths() {
        var updated = false
        ThemeConfig.configList.forEachIndexed { index, config ->
            val bgPath = config.backgroundImgPath ?: return@forEachIndexed
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            val fixed = fixThemeBgPath(bgPath, prefKey)
            if (fixed != bgPath) {
                ThemeConfig.configList[index] = config.copy(backgroundImgPath = fixed)
                updated = true
            }
        }
        if (updated) ThemeConfig.save()
    }

    private fun fixThemeBgPath(bgPath: String, prefKey: String): String {
        if (bgPath.isBlank() || bgPath.startsWith("http") || !bgPath.contains(File.separator)) return bgPath
        val bgName = File(bgPath).name
        val newFile = appCtx.externalFiles.getFile(prefKey, bgName)
        return if (newFile.exists()) newFile.absolutePath else bgName
    }

    // ======================== 书籍缓存恢复（流式处理） ========================

    private fun restoreBookCache(path: String) {
        LogUtils.d(TAG, "开始恢复书籍缓存，路径: $path")
        if (BackupConfig.ignoreBookCache) {
            LogUtils.d(TAG, "忽略书籍缓存恢复（配置项已禁用）")
            AppLog.put("书籍缓存恢复被忽略")
            return
        }

        // 1. 从 bookCacheBooks.json 恢复缺失书籍
        restoreBookCacheBooks(path)

        // 2. 恢复章节目录
        restoreBookChapterCache(path)

        // 3. 流式解析索引，恢复缓存文件
        val indexFile = File(path, bookCacheIndexFileName)
        if (!indexFile.exists()) {
            LogUtils.d(TAG, "书籍缓存索引文件不存在: ${indexFile.absolutePath}")
            AppLog.put("书籍缓存索引文件不存在，跳过缓存文件恢复")
            return
        }

        LogUtils.d(TAG, "找到索引文件: ${indexFile.absolutePath}, 大小: ${indexFile.length()}")

        val backupCacheRoot = if (File(path, bookCacheFolderName).exists()) {
            File(path, bookCacheFolderName)
        } else File(path)

        val targetCacheDir = File(BookHelp.cachePath)
        if (!targetCacheDir.exists()) targetCacheDir.mkdirs()

        val allBooks = appDb.bookDao.all
        LogUtils.d(TAG, "书架共有 ${allBooks.size} 本书")

        var restoredCount = 0
        var chapterRestoredCount = 0

        parseBookCacheIndexStream(indexFile) { cacheIndex ->
            val matchedBook = findMatchingBook(cacheIndex, allBooks)
            if (matchedBook == null) {
                LogUtils.d(TAG, "未找到匹配书籍: ${cacheIndex.bookName}")
                return@parseBookCacheIndexStream
            }

            val sourceCacheDir = File(backupCacheRoot, cacheIndex.folderName)
            if (!sourceCacheDir.exists()) {
                LogUtils.d(TAG, "备份缓存目录不存在: ${cacheIndex.folderName}")
                return@parseBookCacheIndexStream
            }

            val targetFolderName = matchedBook.getFolderName()
            val targetBookDir = File(targetCacheDir, targetFolderName)
            if (!targetBookDir.exists()) targetBookDir.mkdirs()

            val currentChapters = appDb.bookChapterDao.getChapterList(matchedBook.bookUrl)
            val byIndex = currentChapters.associateBy { it.index }
            val byTitle = currentChapters.associateBy { it.title }

            val copiedNames = hashSetOf<String>()
            cacheIndex.chapters.forEach { chapterInfo ->
                val sourceFile = File(sourceCacheDir, chapterInfo.fileName)
                if (!sourceFile.exists()) return@forEach

                val targetChapter = byIndex[chapterInfo.index] ?: byTitle[chapterInfo.title]
                if (targetChapter == null) {
                    LogUtils.d(TAG, "未找到匹配章节: ${chapterInfo.title}")
                    return@forEach
                }

                val targetFile = File(targetBookDir, targetChapter.getFileName())
                sourceFile.copyTo(targetFile, overwrite = true)
                copiedNames.add(sourceFile.name)
                chapterRestoredCount++
            }

            // 复制未被索引引用的 .nb 文件
            sourceCacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".nb") && it.name !in copiedNames }
                ?.forEach { it.copyTo(File(targetBookDir, it.name), overwrite = true) }

            // 复制图片文件夹
            File(sourceCacheDir, "images").takeIf { it.exists() }?.let {
                it.copyRecursively(File(targetBookDir, "images"), overwrite = true)
            }

            restoredCount++
            LogUtils.d(TAG, "恢复书籍缓存: ${matchedBook.name} -> $targetFolderName")
        }

        LogUtils.d(TAG, "书籍缓存恢复完成，共恢复 $restoredCount 本书，$chapterRestoredCount 个章节")
    }

    private fun restoreBookCacheBooks(path: String) {
        val booksFile = File(path, bookCacheBooksFileName)
        if (!booksFile.exists()) {
            LogUtils.d(TAG, "bookCacheBooks.json 不存在，跳过书籍恢复")
            return
        }
        try {
            ensureDefaultBookGroups()
            val books = fileToListT<Book>(path, bookCacheBooksFileName)
                .orEmpty()
                .mapNotNull { it.sanitizeForCacheRestore() }
            if (books.isEmpty()) return

            val localBooks = appDb.bookDao.all
            val missing = books.filter { book ->
                localBooks.none { it.bookUrl == book.bookUrl || it.name == book.name }
            }.map { it.copy(group = 0, type = it.type and BookType.notShelf.inv()) }

            if (missing.isNotEmpty()) {
                appDb.bookDao.insert(*missing.toTypedArray())
                LogUtils.d(TAG, "从 bookCacheBooks.json 恢复书籍: ${missing.size}")
                AppLog.put("从书籍缓存恢复 ${missing.size} 本书到书架")
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
            }
        } catch (e: Exception) {
            LogUtils.d(TAG, "从 bookCacheBooks.json 恢复失败: ${e.message}")
            AppLog.put("从 bookCacheBooks.json 恢复失败\n${e.localizedMessage}", e)
        }
    }

    private fun restoreBookChapterCache(path: String) {
        val chapterFile = File(path, "bookChapterCache.json")
        if (!chapterFile.exists()) {
            LogUtils.d(TAG, "章节目录文件不存在")
            return
        }

        val chapters = fileToListT<BookChapter>(path, "bookChapterCache.json")
        if (chapters.isNullOrEmpty()) {
            LogUtils.d(TAG, "章节目录为空")
            return
        }

        val byBook = chapters.groupBy { it.bookUrl }
        var restoredBookCount = 0
        var restoredChapterCount = 0

        byBook.forEach { (bookUrl, chapterList) ->
            var book = appDb.bookDao.getBook(bookUrl)
            if (book == null) {
                val firstChapter = chapterList.firstOrNull()
                if (firstChapter != null) {
                    val matched = appDb.bookDao.all.find {
                        it.name == firstChapter.bookName && it.author?.trim() == firstChapter.bookAuthor?.trim()
                    }
                    if (matched != null) book = matched
                }
            }

            if (book != null) {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                val updated = chapterList.map { it.copy(bookUrl = book.bookUrl) }
                appDb.bookChapterDao.insert(*updated.toTypedArray())
                restoredBookCount++
                restoredChapterCount += updated.size
                LogUtils.d(TAG, "恢复章节目录: ${book.name}, ${updated.size} 章")
            } else {
                LogUtils.d(TAG, "未找到匹配书籍，跳过章节目录: $bookUrl")
            }
        }

        LogUtils.d(TAG, "章节目录恢复完成，共 $restoredBookCount 本书，$restoredChapterCount 章")
    }

    private fun ensureDefaultBookGroups() {
        val defaults = arrayOf(
            BookGroup(BookGroup.IdAll, appCtx.getString(R.string.all), order = -10, show = true),
            BookGroup(BookGroup.IdLocal, appCtx.getString(R.string.local), order = -9, enableRefresh = false, show = true),
            BookGroup(BookGroup.IdAudio, appCtx.getString(R.string.audio), order = -8, show = true),
            BookGroup(BookGroup.IdNetNone, appCtx.getString(R.string.net_no_group), order = -7, show = true),
            BookGroup(BookGroup.IdLocalNone, appCtx.getString(R.string.local_no_group), order = -6, show = false),
            BookGroup(BookGroup.IdVideo, appCtx.getString(R.string.video), order = -5, show = true),
            BookGroup(BookGroup.IdError, appCtx.getString(R.string.update_book_fail), order = -1, show = true)
        ).filter { appDb.bookGroupDao.getByID(it.groupId) == null }
        if (defaults.isNotEmpty()) appDb.bookGroupDao.insert(*defaults.toTypedArray())
    }

    private fun findMatchingBook(cacheIndex: BookCacheIndexData, allBooks: List<Book>): Book? {
        allBooks.find { it.bookUrl == cacheIndex.bookUrl }?.let { return it }
        val author = cacheIndex.author.trim()
        allBooks.find { it.name == cacheIndex.bookName && (it.author?.trim() ?: "") == author }?.let { return it }
        allBooks.find { it.name == cacheIndex.bookName }?.let { return it }
        return null
    }

    // ======================== 流式解析器 ========================

    private fun parseBookCacheIndexStream(file: File, onIndex: (BookCacheIndexData) -> Unit) {
        runCatching {
            JsonReader(FileReader(file)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var bookUrl = ""
                    var bookName = ""
                    var author = ""
                    var folderName = ""
                    val chapters = mutableListOf<ChapterCacheInfoData>()

                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "bookUrl" -> bookUrl = reader.nextString()
                            "bookName" -> bookName = reader.nextString()
                            "author" -> author = reader.nextString()
                            "folderName" -> folderName = reader.nextString()
                            "chapters" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    reader.beginObject()
                                    var index = 0
                                    var title = ""
                                    var titleMD5 = ""
                                    var fileName = ""
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "index" -> index = reader.nextInt()
                                            "title" -> title = reader.nextString()
                                            "titleMD5" -> titleMD5 = reader.nextString()
                                            "fileName" -> fileName = reader.nextString()
                                        }
                                    }
                                    reader.endObject()
                                    if (fileName.isNotBlank()) {
                                        chapters.add(ChapterCacheInfoData(index, title, titleMD5, fileName))
                                    }
                                }
                                reader.endArray()
                            }
                        }
                    }
                    reader.endObject()

                    if (folderName.isNotBlank() && (bookUrl.isNotBlank() || bookName.isNotBlank())) {
                        onIndex(BookCacheIndexData(bookUrl, bookName, author, folderName, chapters))
                    }
                }
                reader.endArray()
            }
        }.onFailure {
            AppLog.put("$bookCacheIndexFileName\n流式解析出错\n${it.localizedMessage}", it)
            LogUtils.d(TAG, "流式解析索引文件失败: ${it.message}")
        }
    }

    // ======================== 内部数据类 ========================

    private data class BookCacheIndexData(
        val bookUrl: String,
        val bookName: String,
        val author: String,
        val folderName: String,
        val chapters: List<ChapterCacheInfoData>  // 必须保留
    )

    private data class ChapterCacheInfoData(
        val index: Int,
        val title: String,
        val titleMD5: String,
        val fileName: String
    )

    // ======================== 扩展函数 ========================

    private fun Book.sanitizeForCacheRestore(): Book? {
        @Suppress("USELESS_CAST")
        bookUrl = (bookUrl as String?) ?: ""
        @Suppress("USELESS_CAST")
        name = (name as String?) ?: ""
        @Suppress("USELESS_CAST")
        author = (author as String?) ?: ""
        @Suppress("USELESS_CAST")
        originName = (originName as String?) ?: name
        return if (bookUrl.isNotBlank() || name.isNotBlank()) this else null
    }
}