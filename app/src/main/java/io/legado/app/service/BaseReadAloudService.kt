@file:Suppress("DEPRECATION")

package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.CallSuper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.receiver.MediaButtonReceiver
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.LogUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.broadcastPendingIntent
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeSharedPreferences
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.telephonyManager
import splitties.systemservices.wifiManager

/**
 * 朗读服务
 */
abstract class BaseReadAloudService : BaseService(),
    AudioManager.OnAudioFocusChangeListener {

    companion object {
        @JvmStatic
        var isRun = false
            private set

        @JvmStatic
        var pause = true
            private set

        @JvmStatic
        var timeMinute: Int = 0
            private set

        @Volatile
        @JvmStatic
        var lastTtsProgress: Int = 0
            private set

        @Volatile
        @JvmStatic
        var lastTtsChapterIndex: Int = -1
            private set

        @Volatile
        @JvmStatic
        var activeBookUrl: String? = null
            private set

        @Volatile
        @JvmStatic
        var activeBookName: String? = null
            private set

        @Volatile
        @JvmStatic
        var activeBookAuthor: String? = null
            private set

        @Volatile
        @JvmStatic
        var activeBookCover: String? = null
            private set

        @Volatile
        @JvmStatic
        var activeChapterTitle: String? = null
            private set

        @Volatile
        @JvmStatic
        var activePreviewText: String? = null
            private set

        @Volatile
        @JvmStatic
        var pendingChapterSwitchIndex: Int? = null
            private set

        fun isPlay(): Boolean {
            return isRun && !pause
        }

        fun isActiveBook(bookUrl: String?): Boolean {
            return isRun && !bookUrl.isNullOrEmpty() && activeBookUrl == bookUrl
        }

        fun markPendingChapterSwitch(chapterIndex: Int) {
            if (isRun) {
                pendingChapterSwitchIndex = chapterIndex
            }
        }

        fun hasPendingChapterSwitch(): Boolean {
            return pendingChapterSwitchIndex != null
        }

        fun shouldIgnoreProgressSync(currentChapterIndex: Int, ttsChapterIndex: Int): Boolean {
            val pendingIndex = pendingChapterSwitchIndex ?: return false
            return when {
                ttsChapterIndex == pendingIndex -> {
                    pendingChapterSwitchIndex = null
                    false
                }

                currentChapterIndex == pendingIndex -> true

                else -> {
                    pendingChapterSwitchIndex = null
                    false
                }
            }
        }

        private const val TAG = "BaseReadAloudService"

    }

    private fun captureSessionFromForeground() {
        val book = ReadBook.book ?: return
        sessionBook = book.copy()
        sessionBookSource = ReadBook.bookSource
        sessionChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            appDb.bookChapterDao.getChapterCount(book.bookUrl)
        }
        activeBookUrl = book.bookUrl
        activeBookName = book.name
        activeBookAuthor = book.author
        activeBookCover = BookCover.getDisplayCover(book)
        activeChapterTitle = ReadBook.curTextChapter?.title ?: book.durChapterTitle
        activePreviewText = null
    }

    private fun persistSessionProgress() {
        sessionBook?.let { book ->
            book.durChapterTime = System.currentTimeMillis()
            activeChapterTitle?.let { book.durChapterTitle = it }
            book.update()
        }
    }

    private fun loadActiveCover() {
        execute {
            ImageLoader.loadBitmap(this@BaseReadAloudService, activeBookCover).submit().get()
        }.onSuccess {
            if (it.width > 16 && it.height > 16) {
                cover = it
                upReadAloudNotification()
            }
        }
    }

    private fun updateActivePreviewText() {
        activePreviewText = contentList.getOrNull(nowSpeak)
            ?.replace("\n", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: activeChapterTitle
    }

    private fun nextChapterUrl(book: Book, chapter: BookChapter): String? {
        return appDb.bookChapterDao.getChapter(book.bookUrl, chapter.index + 1)?.url
    }

    private fun getCachedNextSessionTextChapter(chapterIndex: Int): TextChapter? {
        return if (cachedNextTextChapterIndex == chapterIndex) {
            cachedNextTextChapter
        } else {
            null
        }
    }

    private fun cacheNextSessionTextChapter(chapterIndex: Int, textChapter: TextChapter?) {
        cachedNextTextChapterIndex = if (textChapter != null) chapterIndex else null
        cachedNextTextChapter = textChapter
    }

    private fun clearCachedNextSessionTextChapter() {
        cachedNextTextChapterIndex = null
        cachedNextTextChapter = null
    }

    private fun getForegroundCachedTextChapter(chapterIndex: Int): TextChapter? {
        if (!isActiveBook(ReadBook.book?.bookUrl)) return null
        val candidates = listOf(
            ReadBook.curTextChapter,
            ReadBook.nextTextChapter,
            ReadBook.prevTextChapter
        )
        return candidates.firstOrNull {
            it?.chapter?.index == chapterIndex && it.isCompleted
        }
    }

    protected fun getPreparedNextTextChapter(): TextChapter? {
        val nextChapterIndex = (textChapter?.chapter?.index ?: return null) + 1
        return getForegroundCachedTextChapter(nextChapterIndex)
            ?: getCachedNextSessionTextChapter(nextChapterIndex)
    }

    private suspend fun buildSessionTextChapter(chapterIndex: Int): TextChapter? {
        val book = sessionBook ?: return null
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return null
        val rawContent = if (book.isLocal) {
            LocalBook.getContent(book, chapter)
        } else {
            BookHelp.getContent(book, chapter) ?: run {
                val source = sessionBookSource ?: return null
                WebBook.getContentAwait(source, book, chapter, nextChapterUrl(book, chapter), true)
            }
        } ?: return null
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val displayTitle = chapter.getDisplayTitle(
            contentProcessor.getTitleReplaceRules(),
            book.getUseReplaceRule(),
            replaceBook = book.toReplaceBook()
        )
        val contents = contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
        currentCoroutineContext().ensureActive()
        val textChapter = ChapterProvider.getTextChapterAsync(
            kotlinx.coroutines.CoroutineScope(currentCoroutineContext()),
            book,
            chapter,
            displayTitle,
            contents,
            sessionChapterSize
        )
        for (page in textChapter.layoutChannel) {
            currentCoroutineContext().ensureActive()
        }
        return textChapter
    }

    private suspend fun loadSessionTextChapter(chapterIndex: Int): TextChapter? {
        getCachedNextSessionTextChapter(chapterIndex)?.let { cachedChapter ->
            activeChapterTitle = cachedChapter.title
            clearCachedNextSessionTextChapter()
            return cachedChapter
        }
        getForegroundCachedTextChapter(chapterIndex)?.let { cachedChapter ->
            activeChapterTitle = cachedChapter.title
            return cachedChapter
        }
        return buildSessionTextChapter(chapterIndex)?.also {
            activeChapterTitle = it.title
        }
    }

    private fun preloadNextSessionTextChapter(currentChapterIndex: Int) {
        preloadNextTextChapterJob?.cancel()
        val nextChapterIndex = currentChapterIndex + 1
        if (nextChapterIndex !in 0 until sessionChapterSize) {
            clearCachedNextSessionTextChapter()
            return
        }
        getForegroundCachedTextChapter(nextChapterIndex)?.let { cachedChapter ->
            cacheNextSessionTextChapter(nextChapterIndex, cachedChapter)
            return
        }
        if (cachedNextTextChapterIndex == nextChapterIndex && cachedNextTextChapter?.isCompleted == true) {
            return
        }
        clearCachedNextSessionTextChapter()
        preloadNextTextChapterJob = lifecycleScope.launch(IO) {
            kotlin.runCatching {
                buildSessionTextChapter(nextChapterIndex)
            }.onSuccess { textChapter ->
                if (textChapter != null) {
                    cacheNextSessionTextChapter(nextChapterIndex, textChapter)
                }
            }
        }
    }

    private fun updateSessionProgress(progress: Int) {
        val chapterIndex = textChapter?.chapter?.index ?: sessionBook?.durChapterIndex ?: -1
        sessionBook?.let { book ->
            book.durChapterIndex = chapterIndex
            book.durChapterPos = progress
            book.durChapterTime = System.currentTimeMillis()
            activeChapterTitle = textChapter?.title ?: activeChapterTitle ?: book.durChapterTitle
            activeChapterTitle?.let { book.durChapterTitle = it }
            lastTtsChapterIndex = chapterIndex
        } ?: run {
            lastTtsChapterIndex = -1
        }
        updateActivePreviewText()
        lastTtsProgress = progress
        val now = System.currentTimeMillis()
        val shouldDispatch = chapterIndex != lastDispatchTtsChapterIndex ||
            now - lastDispatchTtsProgressTime >= 50L ||
            kotlin.math.abs(progress - lastDispatchTtsProgress) >= 6
        if (shouldDispatch) {
            lastDispatchTtsChapterIndex = chapterIndex
            lastDispatchTtsProgress = progress
            lastDispatchTtsProgressTime = now
            postEvent(EventBus.TTS_PROGRESS, progress)
        }
    }

    private fun prepareReadAloud(play: Boolean, pageIndex: Int, startPos: Int) {
        this.pageIndex = pageIndex
        val textChapter = textChapter ?: return
        if (!textChapter.isCompleted) {
            return
        }
        if (textChapter.chapter.index == pendingChapterSwitchIndex) {
            pendingChapterSwitchIndex = null
        }
        activeChapterTitle = textChapter.title
        readAloudNumber = textChapter.getReadLength(pageIndex) + startPos
        readAloudByPage = getPrefBoolean(PreferKey.readAloudByPage)
        contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0)
            .split("\n")
            .filter { it.isNotEmpty() }
        var pos = startPos
        val page = textChapter.getPage(pageIndex) ?: return
        if (pos > 0) {
            for (paragraph in page.paragraphs) {
                val tmp = pos - paragraph.length - 1
                if (tmp < 0) break
                pos = tmp
            }
        }
        nowSpeak = textChapter.getParagraphNum(readAloudNumber + 1, readAloudByPage) - 1
        if (!readAloudByPage && startPos == 0 && !toLast) {
            pos = page.chapterPosition -
                    textChapter.paragraphs[nowSpeak].chapterPosition
        }
        if (toLast) {
            toLast = false
            readAloudNumber = textChapter.getLastParagraphPosition()
            nowSpeak = contentList.lastIndex
            if (page.paragraphs.size == 1) {
                pos = page.chapterPosition -
                        textChapter.paragraphs[nowSpeak].chapterPosition
            }
        }
        paragraphStartPos = pos
        updateActivePreviewText()
        preloadNextSessionTextChapter(textChapter.chapter.index)
        lifecycleScope.launch(Main) {
            if (play) play() else pageChanged = true
        }
    }

    private fun changeChapter(chapterIndex: Int, toLastParagraph: Boolean = false) {
        execute(executeContext = IO) {
            val book = sessionBook ?: return@execute false
            if (chapterIndex !in 0 until sessionChapterSize) {
                return@execute false
            }
            persistSessionProgress()
            val nextTextChapter = loadSessionTextChapter(chapterIndex) ?: return@execute false
            sessionBook = book.apply {
                durChapterIndex = chapterIndex
                durChapterPos = if (toLastParagraph) Int.MAX_VALUE else 0
                durChapterTitle = nextTextChapter.title
            }
            textChapter = nextTextChapter
            val nextPageIndex = if (toLastParagraph) {
                (nextTextChapter.pageSize - 1).coerceAtLeast(0)
            } else {
                0
            }
            toLast = toLastParagraph
            prepareReadAloud(play = true, pageIndex = nextPageIndex, startPos = 0)
            true
        }.onSuccess {
            if (!it) {
                stopSelf()
            }
        }.onError {
            AppLog.put("切换朗读章节失败\n${it.localizedMessage}", it, true)
            stopSelf()
        }
    }

    private val useWakeLock = appCtx.getPrefBoolean(PreferKey.readAloudWakeLock, false)
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:ReadAloudService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")
            ?.apply {
                setReferenceCounted(false)
            }
    }
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val mediaSessionCompat by lazy {
        MediaSessionCompat(this, "readAloud")
    }
    private val phoneStateListener by lazy {
        ReadAloudPhoneStateListener()
    }
    internal var contentList = emptyList<String>()
    internal var nowSpeak: Int = 0
    internal var readAloudNumber: Int = 0
    internal var textChapter: TextChapter? = null
    internal var pageIndex = 0
    private var sessionBook: Book? = null
    private var sessionBookSource: BookSource? = null
    private var sessionChapterSize = 0
    private var needResumeOnAudioFocusGain = false
    private var needResumeOnCallStateIdle = false
    private var registeredPhoneStateListener = false
    private var dsJob: Job? = null
    private var preloadNextTextChapterJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var cachedNextTextChapterIndex: Int? = null
    private var cachedNextTextChapter: TextChapter? = null
    private var lastDispatchTtsProgressTime = 0L
    private var lastDispatchTtsProgress = -1
    private var lastDispatchTtsChapterIndex = -1
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.icon_read_book)
    var pageChanged = false
    private var toLast = false
    var paragraphStartPos = 0
    var readAloudByPage = false
        private set

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                pauseReadAloud()
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        isRun = true
        pause = false
        observeLiveBus()
        initMediaSession()
        initBroadcastReceiver()
        initPhoneStateListener()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        setTimer(AppConfig.ttsTimer)
        if (AppConfig.ttsTimer > 0) {
            toastOnUi("朗读定时 ${AppConfig.ttsTimer} 分钟")
        }
        loadActiveCover()
    }

    fun observeLiveBus() {
        observeEvent<Bundle>(EventBus.READ_ALOUD_PLAY) {
            val play = it.getBoolean("play")
            val pageIndex = it.getInt("pageIndex")
            val startPos = it.getInt("startPos")
            newReadAloud(play, pageIndex, startPos)
        }
        observeSharedPreferences { _, key ->
            when (key) {
                PreferKey.ignoreAudioFocus,
                PreferKey.pauseReadAloudWhilePhoneCalls -> {
                    initPhoneStateListener()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preloadNextTextChapterJob?.cancel()
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        isRun = false
        pause = true
        lastTtsProgress = 0
        lastTtsChapterIndex = -1
        persistSessionProgress()
        activeBookUrl = null
        activeBookName = null
        activeBookAuthor = null
        activeBookCover = null
        activeChapterTitle = null
        pendingChapterSwitchIndex = null
        clearCachedNextSessionTextChapter()
        lastDispatchTtsProgressTime = 0L
        lastDispatchTtsProgress = -1
        lastDispatchTtsChapterIndex = -1
        abandonFocus()
        unregisterReceiver(broadcastReceiver)
        postEvent(EventBus.ALOUD_STATE, Status.STOP)
        notificationManager.cancel(NotificationId.ReadAloudService)
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSessionCompat.release()
        sessionBook?.update()
        unregisterPhoneStateListener(phoneStateListener)
        upNotificationJob?.invokeOnCompletion {
            notificationManager.cancel(NotificationId.ReadAloudService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.play -> newReadAloud(
                intent.getBooleanExtra("play", true),
                intent.getIntExtra("pageIndex", ReadBook.durPageIndex),
                intent.getIntExtra("startPos", 0)
            )

            IntentAction.pause -> pauseReadAloud()
            IntentAction.resume -> resumeReadAloud()
            IntentAction.upTtsSpeechRate -> upSpeechRate(true)
            IntentAction.prevParagraph -> prevP()
            IntentAction.nextParagraph -> nextP()
            IntentAction.prev -> prevChapter()
            IntentAction.next -> nextChapter()
            IntentAction.addTimer -> addTimer()
            IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
            IntentAction.stop -> stopReadAloud()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun stopReadAloud() {
        pause = true
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        lastTtsProgress = 0
        lastTtsChapterIndex = -1
        pendingChapterSwitchIndex = null
        playStop()
        postEvent(EventBus.ALOUD_STATE, Status.STOP)
        stopSelf()
    }

    private fun newReadAloud(play: Boolean, pageIndex: Int, startPos: Int) {
        execute(executeContext = IO) {
            captureSessionFromForeground()
            textChapter = ReadBook.curTextChapter ?: textChapter
            loadActiveCover()
            prepareReadAloud(play, pageIndex, startPos)
        }.onError {
            AppLog.put("启动朗读出错\n${it.localizedMessage}", it, true)
        }
    }

    @SuppressLint("WakelockTimeout")
    open fun play() {
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        isRun = true
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
    }

    abstract fun playStop()

    @CallSuper
    open fun pauseReadAloud(abandonFocus: Boolean = true) {
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        pause = true
        if (abandonFocus) {
            abandonFocus()
        }
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        postEvent(EventBus.ALOUD_STATE, Status.PAUSE)
        persistSessionProgress()
        doDs()
    }

    @SuppressLint("WakelockTimeout")
    @CallSuper
    open fun resumeReadAloud() {
        resumeReadAloudInternal()
    }

    private fun resumeReadAloudInternal() {
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
    }

    abstract fun upSpeechRate(reset: Boolean = false)

    fun upTtsProgress(progress: Int) {
        updateSessionProgress(progress)
    }

    private fun prevP() {
        if (nowSpeak > 0) {
            playStop()
            do {
                nowSpeak--
                readAloudNumber -= contentList[nowSpeak].length + 1 + paragraphStartPos
                paragraphStartPos = 0
            } while (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex))
            textChapter?.let {
                if (readAloudByPage) {
                    val paragraphs = it.getParagraphs(true)
                    if (!paragraphs[nowSpeak].isParagraphEnd) readAloudNumber++
                }
                if (readAloudNumber < it.getReadLength(pageIndex)) {
                    pageIndex--
                }
            }
            upTtsProgress(readAloudNumber + 1)
            updateActivePreviewText()
            play()
        } else {
            changeChapter((sessionBook?.durChapterIndex ?: return) - 1, toLastParagraph = true)
        }
    }

    private fun nextP() {
        if (nowSpeak < contentList.size - 1) {
            playStop()
            readAloudNumber += contentList[nowSpeak].length.plus(1) - paragraphStartPos
            paragraphStartPos = 0
            nowSpeak++
            textChapter?.let {
                if (readAloudByPage) {
                    val paragraphs = it.getParagraphs(true)
                    if (!paragraphs[nowSpeak].isParagraphEnd) readAloudNumber--
                }
                if (pageIndex + 1 < it.pageSize
                    && readAloudNumber >= it.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                }
            }
            upTtsProgress(readAloudNumber + 1)
            updateActivePreviewText()
            play()
        } else {
            nextChapter()
        }
    }

    private fun setTimer(minute: Int) {
        timeMinute = minute
        doDs()
    }

    private fun addTimer() {
        if (timeMinute == 180) {
            timeMinute = 0
        } else {
            timeMinute += 10
            if (timeMinute > 180) timeMinute = 180
        }
        doDs()
    }

    /**
     * 定时
     */
    @Synchronized
    private fun doDs() {
        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
        upReadAloudNotification()
        dsJob?.cancel()
        dsJob = lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (!pause) {
                    if (timeMinute >= 0) {
                        timeMinute--
                    }
                    if (timeMinute == 0) {
                        ReadAloud.stop(this@BaseReadAloudService)
                        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                        break
                    }
                }
                postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                upReadAloudNotification()
            }
        }
    }

    /**
     * 请求音频焦点
     * @return 音频焦点
     */
    fun requestFocus(): Boolean {
        if (AppConfig.ignoreAudioFocus) {
            return true
        }
        val requestFocus = MediaHelp.requestFocus(mFocusRequest)
        if (!requestFocus) {
            pauseReadAloud(false)
            toastOnUi("未获取到音频焦点")
        }
        return requestFocus
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, mFocusRequest)
    }

    /**
     * 更新媒体状态
     */
    private fun upMediaSessionPlaybackState(state: Int) {
        mediaSessionCompat.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(MediaHelp.MEDIA_SESSION_ACTIONS)
                .setState(state, nowSpeak.toLong(), 1f)
                // 为系统媒体控件添加定时按钮
                .addCustomAction(
                    "ACTION_ADD_TIMER",
                    getString(R.string.set_timer),
                    R.drawable.ic_time_add_24dp
                )
                .build()
        )
    }

    /**
     * 初始化MediaSession, 注册多媒体按钮
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun initMediaSession() {
        mediaSessionCompat.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                resumeReadAloud()
            }

            override fun onPause() {
                pauseReadAloud()
            }

            override fun onSkipToNext() {
                if (getPrefBoolean("mediaButtonPerNext", false)) {
                    nextChapter()
                } else {
                    nextP()
                }
            }

            override fun onSkipToPrevious() {
                if (getPrefBoolean("mediaButtonPerNext", false)) {
                    prevChapter()
                } else {
                    prevP()
                }
            }

            override fun onStop() {
                stopSelf()
            }

            override fun onCustomAction(action: String, extras: Bundle?) {
                if (action == "ACTION_ADD_TIMER") addTimer()
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                return MediaButtonReceiver.handleIntent(
                    this@BaseReadAloudService, mediaButtonEvent
                )
            }
        })
        mediaSessionCompat.setMediaButtonReceiver(
            broadcastPendingIntent<MediaButtonReceiver>(Intent.ACTION_MEDIA_BUTTON)
        )
        mediaSessionCompat.isActive = true
    }

    private fun upMediaMetadata() {
        var nTitle: String = when {
            pause -> getString(R.string.read_aloud_pause)
            timeMinute > 0 -> getString(
                R.string.read_aloud_timer,
                timeMinute
            )

            else -> getString(R.string.read_aloud_t)
        }
        nTitle += ": ${activeBookName ?: getString(R.string.read_aloud)}"
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
            .putText(MediaMetadataCompat.METADATA_KEY_TITLE, activeChapterTitle ?: "null")
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, nTitle)
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, activeBookAuthor ?: "null")
//            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, nowSpeak.toLong())
            .build()
        mediaSessionCompat.setMetadata(metadata)
    }

    /**
     * 注册多媒体按钮监听
     */
    private fun initBroadcastReceiver() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 音频焦点变化
     */
    override fun onAudioFocusChange(focusChange: Int) {
        if (AppConfig.ignoreAudioFocus) {
            AppLog.put("忽略音频焦点处理(TTS)")
            return
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    AppLog.put("音频焦点获得,继续朗读")
                    resumeReadAloud()
                } else {
                    AppLog.put("音频焦点获得")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLog.put("音频焦点丢失,暂停朗读")
                pauseReadAloud()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLog.put("音频焦点暂时丢失并会很快再次获得,暂停朗读")
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pauseReadAloud(false)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
                AppLog.put("音频焦点短暂丢失,不做处理")
            }
        }
    }

    private fun upReadAloudNotification() {
        upNotificationJob = execute {
            try {
                upMediaMetadata()
                val notification = createNotification()
                notificationManager.notify(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val useChapterSkip = getPrefBoolean("mediaButtonPerNext", false)
        var nTitle: String = when {
            pause -> getString(R.string.read_aloud_pause)
            timeMinute > 0 -> getString(
                R.string.read_aloud_timer,
                timeMinute
            )

            else -> getString(R.string.read_aloud_t)
        }
        nTitle += ": ${activeBookName ?: getString(R.string.read_aloud)}"
        var nSubtitle = activeChapterTitle
        if (nSubtitle.isNullOrBlank())
            nSubtitle = getString(R.string.read_aloud_s)
        val builder = NotificationCompat
            .Builder(this, AppConst.channelIdReadAloud)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.read_aloud))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(
                activityPendingIntent<ReadBookActivity>("activity") {
                    activeBookUrl?.let { putExtra("bookUrl", it) }
                }
            )
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
        builder.setLargeIcon(cover)
        // 按钮定义：上一章、播放、停止、下一章、定时
        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(
                if (useChapterSkip) R.string.previous_chapter else R.string.read_aloud_prev_paragraph
            ),
            aloudServicePendingIntent(
                if (useChapterSkip) IntentAction.prev else IntentAction.prevParagraph
            )
        )
        if (pause) {
            builder.addAction(
                R.drawable.ic_play_24dp,
                getString(R.string.resume),
                aloudServicePendingIntent(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_24dp,
                getString(R.string.pause),
                aloudServicePendingIntent(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(
                if (useChapterSkip) R.string.next_chapter else R.string.read_aloud_next_paragraph
            ),
            aloudServicePendingIntent(
                if (useChapterSkip) IntentAction.next else IntentAction.nextParagraph
            )
        )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            aloudServicePendingIntent(IntentAction.stop)
        )
        builder.addAction(
            R.drawable.ic_time_add_24dp,
            getString(R.string.set_timer),
            aloudServicePendingIntent(IntentAction.addTimer)
        )
        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSessionCompat.sessionToken)
        )
        return builder
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        execute {
            try {
                upMediaMetadata()
                val notification = createNotification()
                startForeground(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
                //创建通知出错不结束服务就会崩溃,服务必须绑定通知
                stopSelf()
            }
        }
    }

    abstract fun aloudServicePendingIntent(actionStr: String): PendingIntent?

    open fun prevChapter() {
        resumeReadAloudInternal()
        changeChapter((sessionBook?.durChapterIndex ?: return) - 1, toLastParagraph = false)
    }

    open fun nextChapter() {
        AppLog.putDebug("${ReadBook.curTextChapter?.chapter?.title} 朗读结束跳转下一章并朗读")
        resumeReadAloudInternal()
        AppLog.putDebug("${activeChapterTitle} 朗读结束跳转下一章并朗读")
        changeChapter((sessionBook?.durChapterIndex ?: return) + 1, toLastParagraph = false)
    }

    private fun initPhoneStateListener() {
        val needRegister = AppConfig.ignoreAudioFocus && AppConfig.pauseReadAloudWhilePhoneCalls
        if (needRegister && registeredPhoneStateListener) {
            return
        }
        if (needRegister) {
            registerPhoneStateListener(phoneStateListener)
        } else {
            unregisterPhoneStateListener(phoneStateListener)
        }
    }

    private fun unregisterPhoneStateListener(l: PhoneStateListener) {
        if (registeredPhoneStateListener) {
            withReadPhoneStatePermission {
                telephonyManager.listen(l, PhoneStateListener.LISTEN_NONE)
                registeredPhoneStateListener = false
            }
        }
    }

    private fun registerPhoneStateListener(l: PhoneStateListener) {
        withReadPhoneStatePermission {
            telephonyManager.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            registeredPhoneStateListener = true
        }
    }

    private fun withReadPhoneStatePermission(block: () -> Unit) {
        try {
            block.invoke()
        } catch (_: SecurityException) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.READ_PHONE_STATE)
                .rationale(R.string.read_aloud_read_phone_state_permission_rationale)
                .onGranted {
                    try {
                        block.invoke()
                    } catch (_: SecurityException) {
                        LogUtils.d(TAG, "Grant read phone state permission fail.")
                    }
                }
                .request()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    inner class ReadAloudPhoneStateListener : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (needResumeOnCallStateIdle) {
                        AppLog.put("来电结束,继续朗读")
                        resumeReadAloud()
                    } else {
                        AppLog.put("来电结束")
                    }
                }

                TelephonyManager.CALL_STATE_RINGING -> {
                    if (!pause) {
                        AppLog.put("来电响铃,暂停朗读")
                        needResumeOnCallStateIdle = true
                        pauseReadAloud()
                    } else {
                        AppLog.put("来电响铃")
                    }
                }

                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    AppLog.put("来电接听,不做处理")
                }
            }
        }
    }

}
