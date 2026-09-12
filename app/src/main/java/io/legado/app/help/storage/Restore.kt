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
import kotlinx.coroutines.CancellationException
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
            if (it is CancellationException) throw it
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
                if (e is CancellationException) throw e
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
        val ignoreLocal = BackupConfig.ignoreLocalBook
        restoreListBatched<Book>(path, "bookshelf.json") { batch ->
            batch.forEach { it.upType() }
            batch.filter { it.isLocal }.forEach { it.coverUrl = LocalBook.getCoverPath(it) }
            val filtered = batch.filterNot { ignoreLocal && it.isLocal }
            if (filtered.isNotEmpty()) appDb.bookDao.insert(*filtered.toTypedArray())
        }

        progress("bookmark.json")
        appDb.bookmarkDao.deleteAll()
        restoreListBatched<Bookmark>(path, "bookmark.json") { batch ->
            if (batch.isNotEmpty()) appDb.bookmarkDao.insert(*batch.toTypedArray())
        }

        progress("bookGroup.json")
        appDb.bookGroupDao.deleteAll()
        restoreListBatched<BookGroup>(path, "bookGroup.json") { batch ->
            if (batch.isNotEmpty()) appDb.bookGroupDao.insert(*batch.toTypedArray())
        }

        progress("bookSource.json")
        appDb.bookSourceDao.deleteAll()
        if (!restoreListBatched<BookSource>(path, "bookSource.json") { batch ->
            if (batch.isNotEmpty()) appDb.bookSourceDao.insert(*batch.toTypedArray())
        }) {
            File(path, "bookSource.json").takeIf { it.exists() }?.inputStream()?.reader()?.use {
                ImportOldData.importOldSource(it.readText())
            }
        }

        progress("rssSources.json")
        appDb.rssSourceDao.deleteAll()
        restoreListBatched<RssSource>(path, "rssSources.json") { batch ->
            if (batch.isNotEmpty()) appDb.rssSourceDao.insert(*batch.toTypedArray())
        }

        progress("rssStar.json")
        appDb.rssStarDao.deleteAll()
        restoreListBatched<RssStar>(path, "rssStar.json") { batch ->
            if (batch.isNotEmpty()) appDb.rssStarDao.insert(*batch.toTypedArray())
        }

        progress("sourceSub.json")
        appDb.ruleSubDao.deleteAll()
        restoreListBatched<RuleSub>(path, "sourceSub.json") { batch ->
            if (batch.isNotEmpty()) appDb.ruleSubDao.insert(*batch.toTypedArray())
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
        restoreListBatched<ReplaceRule>(path, "replaceRule.json") { batch ->
            if (batch.isNotEmpty()) appDb.replaceRuleDao.insert(*batch.toTypedArray())
        }

        progress(HighlightRuleStore.backupFileName)
        File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                HighlightRuleStore.restoreBackupData(appCtx, it, path)
            }
        }

        progress("searchHistory.json")
        appDb.searchKeywordDao.deleteAll()
        restoreListBatched<SearchKeyword>(path, "searchHistory.json") { batch ->
            if (batch.isNotEmpty()) appDb.searchKeywordDao.insert(*batch.toTypedArray())
        }

        progress("txtTocRule.json")
        appDb.txtTocRuleDao.deleteAll()
        restoreListBatched<TxtTocRule>(path, "txtTocRule.json") { batch ->
            if (batch.isNotEmpty()) appDb.txtTocRuleDao.insert(*batch.toTypedArray())
        }

        progress("httpTTS.json")
        appDb.httpTTSDao.deleteAll()
        restoreListBatched<HttpTTS>(path, "httpTTS.json") { batch ->
            if (batch.isNotEmpty()) appDb.httpTTSDao.insert(*batch.toTypedArray())
        }

        progress("dictRule.json")
        appDb.dictRuleDao.deleteAll()
        restoreListBatched<DictRule>(path, "dictRule.json") { batch ->
            if (batch.isNotEmpty()) appDb.dictRuleDao.insert(*batch.toTypedArray())
        }

        progress("keyboardAssists.json")
        appDb.keyboardAssistsDao.deleteAll()
        restoreListBatched<KeyboardAssist>(path, "keyboardAssists.json") { batch ->
            if (batch.isNotEmpty()) appDb.keyboardAssistsDao.insert(*batch.toTypedArray())
        }

        progress(CoverGalleryRepository.backupDirName)
        restoreCoverGallery(path)

        progress("readRecord.json")
        restoreReadRecordsStreaming(path)


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
            val ignoreLocal = BackupConfig.ignoreLocalBook
            restoreListBatched<Book>(path, "bookshelf.json") { batch ->
                batch.forEach { it.upType() }
                batch.filter { it.isLocal }.forEach { it.coverUrl = LocalBook.getCoverPath(it) }
                val filtered = batch.filterNot { ignoreLocal && it.isLocal }
                if (filtered.isNotEmpty()) appDb.bookDao.insert(*filtered.toTypedArray())
            }
        }

        if ("bookmark.json" in selectedSet) {
            progress("bookmark.json")
            appDb.bookmarkDao.deleteAll()
            restoreListBatched<Bookmark>(path, "bookmark.json") { batch ->
                if (batch.isNotEmpty()) appDb.bookmarkDao.insert(*batch.toTypedArray())
            }
        }

        if ("bookGroup.json" in selectedSet) {
            progress("bookGroup.json")
            appDb.bookGroupDao.deleteAll()
            restoreListBatched<BookGroup>(path, "bookGroup.json") { batch ->
                if (batch.isNotEmpty()) appDb.bookGroupDao.insert(*batch.toTypedArray())
            }
        }

        if ("bookSource.json" in selectedSet) {
            progress("bookSource.json")
            appDb.bookSourceDao.deleteAll()
            if (!restoreListBatched<BookSource>(path, "bookSource.json") { batch ->
                if (batch.isNotEmpty()) appDb.bookSourceDao.insert(*batch.toTypedArray())
            }) {
                File(path, "bookSource.json").takeIf { it.exists() }?.inputStream()?.reader()?.use {
                    ImportOldData.importOldSource(it.readText())
                }
            }
        }

        if ("rssSources.json" in selectedSet) {
            progress("rssSources.json")
            appDb.rssSourceDao.deleteAll()
            restoreListBatched<RssSource>(path, "rssSources.json") { batch ->
                if (batch.isNotEmpty()) appDb.rssSourceDao.insert(*batch.toTypedArray())
            }
        }

        if ("rssStar.json" in selectedSet) {
            progress("rssStar.json")
            appDb.rssStarDao.deleteAll()
            restoreListBatched<RssStar>(path, "rssStar.json") { batch ->
                if (batch.isNotEmpty()) appDb.rssStarDao.insert(*batch.toTypedArray())
            }
        }

        if ("sourceSub.json" in selectedSet) {
            progress("sourceSub.json")
            appDb.ruleSubDao.deleteAll()
            restoreListBatched<RuleSub>(path, "sourceSub.json") { batch ->
                if (batch.isNotEmpty()) appDb.ruleSubDao.insert(*batch.toTypedArray())
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
            restoreListBatched<ReplaceRule>(path, "replaceRule.json") { batch ->
                if (batch.isNotEmpty()) appDb.replaceRuleDao.insert(*batch.toTypedArray())
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
            restoreListBatched<SearchKeyword>(path, "searchHistory.json") { batch ->
                if (batch.isNotEmpty()) appDb.searchKeywordDao.insert(*batch.toTypedArray())
            }
        }

        if ("txtTocRule.json" in selectedSet) {
            progress("txtTocRule.json")
            appDb.txtTocRuleDao.deleteAll()
            restoreListBatched<TxtTocRule>(path, "txtTocRule.json") { batch ->
                if (batch.isNotEmpty()) appDb.txtTocRuleDao.insert(*batch.toTypedArray())
            }
        }

        if ("httpTTS.json" in selectedSet) {
            progress("httpTTS.json")
            appDb.httpTTSDao.deleteAll()
            restoreListBatched<HttpTTS>(path, "httpTTS.json") { batch ->
                if (batch.isNotEmpty()) appDb.httpTTSDao.insert(*batch.toTypedArray())
            }
        }

        if ("dictRule.json" in selectedSet) {
            progress("dictRule.json")
            appDb.dictRuleDao.deleteAll()
            restoreListBatched<DictRule>(path, "dictRule.json") { batch ->
                if (batch.isNotEmpty()) appDb.dictRuleDao.insert(*batch.toTypedArray())
            }
        }

        if ("keyboardAssists.json" in selectedSet) {
            progress("keyboardAssists.json")
            appDb.keyboardAssistsDao.deleteAll()
            restoreListBatched<KeyboardAssist>(path, "keyboardAssists.json") { batch ->
                if (batch.isNotEmpty()) appDb.keyboardAssistsDao.insert(*batch.toTypedArray())
            }
        }

        if (CoverGalleryRepository.backupDirName in selectedSet) {
            progress(CoverGalleryRepository.backupDirName)
            restoreCoverGallery(path)
        }

        if ("readRecord.json" in selectedSet || "readRecordDetail.json" in selectedSet || "readRecordSession.json" in selectedSet) {
            progress("readRecord.json")
            restoreReadRecordsStreaming(
                path = path,
                restoreRecords = "readRecord.json" in selectedSet,
                restoreDetails = "readRecordDetail.json" in selectedSet,
                restoreSessions = "readRecordSession.json" in selectedSet
            )
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
        }
        if (HighlightRuleStore.backupFileName in selectedSet) HighlightRuleStore.clearCache()

        if ("themeBackgroundImages" in selectedSet) {
            progress("themeBackgroundImages")
            restoreThemeBackgrounds(path, clearExisting = true)
            fixThemeBackgroundPaths()
            fixThemeConfigBackgroundPaths()
        }

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

        if (bookCacheFolderName in selectedSet) {
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

    /**
     * 以固定批次读取大型 JSON 数组，避免一次性把整个备份文件转换成 List。
     * DAO 插入仍然按批次执行，因此恢复过程的峰值内存与备份文件总大小基本无关。
     */
    private inline fun <reified T> restoreListBatched(
        path: String,
        fileName: String,
        batchSize: Int = 50,
        crossinline action: (List<T>) -> Unit
    ): Boolean {
        val file = File(path, fileName)
        if (!file.isFile) return false
        return runCatching {
            FileInputStream(file).use { input ->
                GSON.forEachJsonArrayBatch<T>(input, batchSize) { batch ->
                    action(batch)
                }.getOrThrow()
            }
            true
        }.onFailure {
            LogUtils.d(TAG, "批量恢复 $fileName 失败: ${it.message}")
            AppLog.put("$fileName\n批量恢复失败\n${it.localizedMessage}", it)
        }.getOrDefault(false)
    }

    private fun safeCopyFile(source: File, target: File): Boolean {
        return runCatching {
            if (!source.isFile) return false
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            true
        }.onFailure {
            LogUtils.d(TAG, "复制文件失败: ${source.absolutePath} -> ${target.absolutePath}: ${it.message}")
        }.getOrDefault(false)
    }

    private fun safeCopyRecursively(source: File, target: File): Boolean {
        return runCatching {
            if (!source.exists()) return false
            source.copyRecursively(target, overwrite = true)
            true
        }.onFailure {
            LogUtils.d(TAG, "复制目录失败: ${source.absolutePath} -> ${target.absolutePath}: ${it.message}")
        }.getOrDefault(false)
    }

    private suspend fun restoreReadRecordsStreaming(
        path: String,
        restoreRecords: Boolean = true,
        restoreDetails: Boolean = true,
        restoreSessions: Boolean = true
    ) {
        val repository = ReadRecordRepository(appDb.readRecordDao)
        val authorMap = appDb.bookDao.all
            .asSequence()
            .filter { it.author.isNotBlank() }
            .associate { it.name to it.author.trim() }

        appDb.readRecordDao.clear()
        appDb.readRecordDao.clearDetails()
        appDb.readRecordDao.clearSessions()
        repository.beginStreamingImport()
        try {
            if (restoreRecords) {
                File(path, "readRecord.json").takeIf { it.isFile }?.inputStream()?.use { input ->
                    GSON.forEachJsonArrayBatchSuspend<ReadRecord>(input, 200) { batch ->
                        repository.importRecordBatch(batch)
                    }.getOrThrow()
                }
            }
            if (restoreDetails) {
                File(path, "readRecordDetail.json").takeIf { it.isFile }?.inputStream()?.use { input ->
                    GSON.forEachJsonArrayBatchSuspend<ReadRecordDetail>(input, 200) { batch ->
                        repository.importDetailBatch(batch)
                    }.getOrThrow()
                }
            }
            if (restoreSessions) {
                File(path, "readRecordSession.json").takeIf { it.isFile }?.inputStream()?.use { input ->
                    GSON.forEachJsonArrayBatchSuspend<ReadRecordSession>(input, 200) { batch ->
                        repository.importSessionBatch(batch)
                    }.getOrThrow()
                }
            }
            repository.finishStreamingImport()
            repository.repairRecordsAfterStreamingImport { authorMap[it] }
            appCtx.putPrefInt(
                PreferKey.readRecordRepairVersion,
                ReadRecordRepository.CURRENT_REPAIR_VERSION
            )
        } catch (e: Throwable) {
            repository.abortStreamingImport()
            throw e
        }
    }

    private fun restoreRuntimeSourceCaches(path: String) {
        val file = File(path, runtimeSourceCacheFileName)
        if (!file.exists()) return
        appDb.cacheDao.deleteAllRuntimeSourceCaches()
        AppCacheManager.clearSourceVariables()
        restoreListBatched<Cache>(path, runtimeSourceCacheFileName) { batch ->
            if (batch.isNotEmpty()) appDb.cacheDao.insert(*batch.toTypedArray())
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
                    ?.mapIndexedNotNull { imageIndex, imageFile ->
                        val targetFile = File(
                            targetDir,
                            uniqueCoverGalleryImageName(imageFile.name, usedImageNames)
                        )
                        if (!safeCopyFile(imageFile, targetFile)) return@mapIndexedNotNull null
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

    private fun File.isCoverGalleryImageFile() = extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

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
            backupFile?.let { safeCopyFile(it, File(bgDir, bgName)) }
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
            safeCopyFile(backupFile, File(targetDir, bgName))
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

    private suspend fun fixThemeConfigBackgroundPaths() {
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

        restoreBookCacheBooks(path)
        restoreBookChapterCache(path)

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
                if (safeCopyFile(sourceFile, targetFile)) {
                    copiedNames.add(sourceFile.name)
                    chapterRestoredCount++
                }
            }

            sourceCacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".nb") && it.name !in copiedNames }
                ?.forEach { safeCopyFile(it, File(targetBookDir, it.name)) }

            File(sourceCacheDir, "images").takeIf { it.exists() }?.let {
                safeCopyRecursively(it, File(targetBookDir, "images"))
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
            val localBooks = appDb.bookDao.all
            val existingUrls = localBooks.mapTo(hashSetOf()) { it.bookUrl }
            val existingNames = localBooks.mapTo(hashSetOf()) { it.name }
            var restoredCount = 0
            restoreListBatched<Book>(path, bookCacheBooksFileName) { batch ->
                val missing = batch.mapNotNull { it.sanitizeForCacheRestore() }
                    .filter { book ->
                        book.bookUrl !in existingUrls && book.name !in existingNames
                    }
                    .map { it.copy(group = 0, type = it.type and BookType.notShelf.inv()) }
                if (missing.isNotEmpty()) {
                    appDb.bookDao.insert(*missing.toTypedArray())
                    missing.forEach {
                        existingUrls.add(it.bookUrl)
                        existingNames.add(it.name)
                    }
                    restoredCount += missing.size
                }
            }
            if (restoredCount > 0) {
                LogUtils.d(TAG, "从 bookCacheBooks.json 恢复书籍: $restoredCount")
                AppLog.put("从书籍缓存恢复 $restoredCount 本书到书架")
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

        val restoredBooks = hashSetOf<String>()
        val skippedBooks = hashSetOf<String>()
        val clearedBooks = hashSetOf<String>()
        var restoredBookCount = 0
        var restoredChapterCount = 0

        restoreListBatched<BookChapter>(path, "bookChapterCache.json") { batch ->
            val byBook = batch.groupBy { it.bookUrl }
            byBook.forEach { (bookUrl, chapterList) ->
                val book = appDb.bookDao.getBook(bookUrl)
                if (book != null) {
                    if (clearedBooks.add(book.bookUrl)) {
                        appDb.bookChapterDao.delByBook(book.bookUrl)
                    }
                    val updated = chapterList.map { it.copy(bookUrl = book.bookUrl) }
                    if (updated.isNotEmpty()) {
                        appDb.bookChapterDao.insert(*updated.toTypedArray())
                        restoredChapterCount += updated.size
                    }
                    if (restoredBooks.add(book.bookUrl)) restoredBookCount++
                    LogUtils.d(TAG, "恢复章节目录: ${book.name}, ${updated.size} 章")
                } else if (skippedBooks.add(bookUrl)) {
                    LogUtils.d(TAG, "未找到匹配书籍（bookUrl=$bookUrl），跳过章节目录恢复")
                }
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
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (fileName.isNotBlank()) {
                                        chapters.add(ChapterCacheInfoData(index, title, titleMD5, fileName))
                                    }
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
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
}
