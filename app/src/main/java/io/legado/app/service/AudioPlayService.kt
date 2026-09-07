package io.legado.app.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.Status
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.MaxAudioSystem
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.receiver.MediaButtonReceiver
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.broadcastPendingIntent
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.wifiManager

class AudioPlayService : BaseService(), AudioManager.OnAudioFocusChangeListener, Player.Listener {
    companion object {
        @JvmStatic var isRun = false; private set
        @JvmStatic var pause = true; private set
        @JvmStatic var timeMinute = 0
        @JvmStatic var playSpeed = 1f
        @JvmStatic var bufferedPosition = 0; private set
        var url = ""; private set
        private var instance: AudioPlayService? = null
        private const val MEDIA_SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        private const val APP_ACTION_STOP = "Stop"
        private const val APP_ACTION_TIMER = "Timer"
        @JvmStatic fun refreshMediaSession() { instance?.refreshCurrentMediaInfo() }
    }

    private val useWakeLock = AppConfig.audioPlayUseWakeLock
    private val wakeLock by lazy { powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:AudioPlayService").apply { setReferenceCounted(false) } }
    private val wifiLock by lazy { @Suppress("DEPRECATION") wifiManager?.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")?.apply { setReferenceCounted(false) } }
    private val mFocusRequest: AudioFocusRequestCompat by lazy { MediaHelp.buildAudioFocusRequestCompat(this) }
    private val exoPlayer: ExoPlayer by lazy { ExoPlayerHelper.createHttpExoPlayer(this) }
    private val mediaSessionCompat by lazy { MediaSessionCompat(this, "readAloud") }
    private var broadcastReceiver: BroadcastReceiver? = null
    private var needResumeOnAudioFocusGain = false
    private var position = AudioPlay.book?.durChapterPos ?: 0
    private var dsJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var upPlayProgressJob: Job? = null
    private var lastMediaSessionUpdate = 0L
    private var lastNotificationChapter = -1
    private var playGeneration = 0L
    private var cover: Bitmap = BitmapFactory.decodeResource(appCtx.resources, R.drawable.icon_read_book)

    override fun onCreate() {
        super.onCreate(); instance = this; isRun = true; bufferedPosition = 0; lastNotificationChapter = -1
        exoPlayer.addListener(this); AudioPlay.registerService(this); initMediaSession(); initBroadcastReceiver(); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED); doDs()
        execute { ImageLoader.loadBitmap(this@AudioPlayService, AudioPlay.book?.let { BookCover.getDisplayCover(it) }).submit().get() }.onSuccess { if (it.width > 16 && it.height > 16) { cover = it; upMediaMetadata(); upAudioPlayNotification() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.play, IntentAction.playNew -> startPlayback(intent.action == IntentAction.playNew)
            IntentAction.stopPlay -> stopPlayback(false, savePosition = false)
            IntentAction.pause -> pausePlayback()
            IntentAction.resume -> resumePlayback()
            IntentAction.prev -> { MaxAudioSystem.previous(); refreshCurrentMediaInfo() }
            IntentAction.next -> { MaxAudioSystem.next(); refreshCurrentMediaInfo() }
            IntentAction.setSpeed -> upSpeed(intent.getFloatExtra("speed", 1f))
            IntentAction.addTimer -> addTimer()
            IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
            IntentAction.adjustProgress -> adjustProgress(intent.getIntExtra("position", position))
            IntentAction.stop -> stopPlayback(true)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun invalidatePlayback() { playGeneration++; pause = true; upPlayProgressJob?.cancel() }

    private fun stopPlayback(destroy: Boolean, savePosition: Boolean = true) {
        invalidatePlayback()
        if (savePosition) {
            AudioPlay.upReadTime()
            AudioPlay.playPositionChanged(exoPlayer.currentPosition.toInt())
        }
        exoPlayer.playWhenReady = false; exoPlayer.stop(); abandonFocus(); releaseLocks(); AudioPlay.status = Status.STOP; upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED); postEvent(EventBus.AUDIO_STATE, Status.STOP); upAudioPlayNotification(); if (destroy) stopSelf()
    }

    private fun releaseLocks() { if (useWakeLock) { runCatching { if (wakeLock.isHeld) wakeLock.release() }; runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() } } }

    private fun startPlayback(playNew: Boolean) {
        val generation = ++playGeneration; exoPlayer.playWhenReady = false; exoPlayer.stop(); upPlayProgressJob?.cancel(); pause = false; AudioPlay.markReadStart()
        if (playNew) position = 0 else position = AudioPlay.durChapterPos.coerceAtLeast(0)
        url = AudioPlay.durPlayUrl; upSpeed(playSpeed); upMediaMetadata(); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_BUFFERING); play(generation)
    }

    private fun refreshCurrentMediaInfo() { lastNotificationChapter = AudioPlay.durChapterIndex; upMediaMetadata(); upMediaSessionPlaybackState(if (pause) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_BUFFERING); upAudioPlayNotification() }

    override fun onDestroy() {
        AudioPlay.playPositionChanged(exoPlayer.currentPosition.toInt()); if (!pause) AudioPlay.upReadTime(); MaxAudioSystem.savePlaybackState(); invalidatePlayback(); releaseLocks(); abandonFocus(); exoPlayer.playWhenReady = false; exoPlayer.release(); mediaSessionCompat.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_STOPPED, 0, 0f).build()); mediaSessionCompat.isActive = false; mediaSessionCompat.release(); broadcastReceiver?.let { runCatching { unregisterReceiver(it) } }; isRun = false; instance = null; bufferedPosition = 0; AudioPlay.status = Status.STOP; postEvent(EventBus.AUDIO_STATE, Status.STOP); AudioPlay.unregisterService(); upNotificationJob?.invokeOnCompletion { notificationManager.cancel(NotificationId.AudioPlayService) }; super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("WakelockTimeout")
    private fun play(generation: Long) {
        if (useWakeLock) { wakeLock.acquire(); wifiLock?.acquire() }; upAudioPlayNotification()
        if (!requestFocus()) { pause = true; releaseLocks(); AudioPlay.status = Status.PAUSE; postEvent(EventBus.AUDIO_STATE, Status.PAUSE); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED); upAudioPlayNotification(); return }
        val book = AudioPlay.book
        execute(context = Main) {
            if (generation != playGeneration || pause || !isRun) return@execute
            AudioPlay.status = Status.STOP; postEvent(EventBus.AUDIO_STATE, Status.STOP); upPlayProgressJob?.cancel()
            if (url.isJsonArray()) {
                val mediaSource = ExoPlayerHelper.getMediaSource(this@AudioPlayService, url)
                if (mediaSource == null) { MaxAudioSystem.setError("url格式错误"); return@execute }
                exoPlayer.setMediaSource(mediaSource)
            } else {
                val analyzeUrl = AnalyzeUrl(url, source = AudioPlay.bookSource, ruleData = book, chapter = AudioPlay.durChapter, coroutineContext = coroutineContext)
                exoPlayer.setMediaItem(analyzeUrl.getMediaItem())
            }
            if (generation != playGeneration || pause || !isRun) return@execute
            exoPlayer.playWhenReady = true
            val skipStartMs = (book?.getOpenCredits() ?: 0) * 1000L
            val seekPosition = if (position > 0) position.toLong() else skipStartMs
            exoPlayer.seekTo(seekPosition.coerceAtLeast(0L)); exoPlayer.prepare()
        }.onError {
            if (generation != playGeneration || pause) return@onError
            handlePlaybackError(it.localizedMessage ?: "播放失败", it)
        }
    }

    private fun handlePlaybackError(message: String, error: Throwable? = null) {
        AudioPlay.upLoading(false); pause = true; playGeneration++; upPlayProgressJob?.cancel(); releaseLocks(); abandonFocus(); exoPlayer.playWhenReady = false; AudioPlay.status = Status.STOP; postEvent(EventBus.AUDIO_STATE, Status.STOP); MaxAudioSystem.setError(message); if (error != null) AppLog.put("播放出错\n$message", error, true); else AppLog.put("播放出错\n$message"); toastOnUi(message); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_ERROR); upAudioPlayNotification()
    }

    private fun pausePlayback(abandonAudioFocus: Boolean = true) {
        try { playGeneration++; pause = true; exoPlayer.playWhenReady = false; position = exoPlayer.currentPosition.toInt().coerceAtLeast(0); AudioPlay.durChapterPos = position; AudioPlay.playPositionChanged(position); AudioPlay.upReadTime(); upPlayProgressJob?.cancel(); exoPlayer.pause(); releaseLocks(); if (abandonAudioFocus) abandonFocus(); AudioPlay.status = Status.PAUSE; postEvent(EventBus.AUDIO_STATE, Status.PAUSE); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED); upAudioPlayNotification() } catch (e: Exception) { e.printOnDebug() }
    }

    @SuppressLint("WakelockTimeout")
    private fun resumePlayback() {
        try {
            if (!isRun) return
            if (!requestFocus()) { pause = true; releaseLocks(); AudioPlay.status = Status.PAUSE; postEvent(EventBus.AUDIO_STATE, Status.PAUSE); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED); return }
            if (useWakeLock) { wakeLock.acquire(); wifiLock?.acquire() }
            AudioPlay.markReadStart(); pause = false; playGeneration++
            if (url.isEmpty() || exoPlayer.playbackState == Player.STATE_IDLE) { url = AudioPlay.durPlayUrl; position = AudioPlay.durChapterPos.coerceAtLeast(0); val generation = playGeneration; upMediaSessionPlaybackState(PlaybackStateCompat.STATE_BUFFERING); play(generation); return }
            exoPlayer.seekTo(AudioPlay.durChapterPos.coerceAtLeast(0).toLong()); exoPlayer.playWhenReady = true; exoPlayer.play(); AudioPlay.status = Status.PLAY; MaxAudioSystem.clearError(); postEvent(EventBus.AUDIO_STATE, Status.PLAY); upPlayProgress(); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING); upAudioPlayNotification()
        } catch (e: Exception) { handlePlaybackError(e.localizedMessage ?: "恢复播放失败", e) }
    }

    private fun adjustProgress(newPosition: Int) { position = newPosition.coerceAtLeast(0); AudioPlay.durChapterPos = position; AudioPlay.playPositionChanged(position); if (!pause) exoPlayer.seekTo(position.toLong()); upMediaSessionPlaybackState(if (pause) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING) }

    @SuppressLint("ObsoleteSdkInt")
    private fun upSpeed(speed: Float) { runCatching { playSpeed = speed.coerceIn(0.25f, 4f); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) exoPlayer.setPlaybackSpeed(playSpeed); postEvent(EventBus.AUDIO_SPEED, playSpeed) } }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_BUFFERING -> { AudioPlay.upLoading(true); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_BUFFERING) }
            Player.STATE_READY -> {
                AudioPlay.upLoading(false)
                if (pause) { exoPlayer.playWhenReady = false; exoPlayer.pause(); AudioPlay.status = Status.PAUSE; postEvent(EventBus.AUDIO_STATE, Status.PAUSE); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED) }
                else { val playing = exoPlayer.isPlaying || exoPlayer.playWhenReady; AudioPlay.status = if (playing) Status.PLAY else Status.PAUSE; postEvent(EventBus.AUDIO_STATE, AudioPlay.status); upMediaSessionPlaybackState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED); if (playing) upPlayProgress() }
                postEvent(EventBus.AUDIO_SIZE, exoPlayer.duration.toInt()); upMediaMetadata(); lastNotificationChapter = AudioPlay.durChapterIndex; AudioPlay.saveDurChapter(exoPlayer.duration)
            }
            Player.STATE_ENDED -> { upPlayProgressJob?.cancel(); AudioPlay.playPositionChanged(exoPlayer.duration.toInt().coerceAtLeast(0)); if (!pause && !MaxAudioSystem.onPlaybackEnded()) { pause = true; releaseLocks(); abandonFocus(); AudioPlay.status = Status.STOP; postEvent(EventBus.AUDIO_STATE, Status.STOP); upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED) } }
        }
        upAudioPlayNotification()
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { super.onMediaItemTransition(mediaItem, reason); upMediaMetadata(); upMediaSessionPlaybackState(if (pause) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_BUFFERING); upAudioPlayNotification() }

    override fun onIsPlayingChanged(isPlaying: Boolean) { super.onIsPlayingChanged(isPlaying); if (pause) { if (isPlaying) exoPlayer.playWhenReady = false; return }; AudioPlay.status = if (isPlaying) Status.PLAY else Status.PAUSE; postEvent(EventBus.AUDIO_STATE, AudioPlay.status); upMediaSessionPlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED); upAudioPlayNotification() }

    private fun upMediaMetadata() {
        val title = AudioPlay.durChapter?.title ?: getString(R.string.data_loading); val bookName = AudioPlay.book?.name ?: getString(R.string.audio_play_s); val author = AudioPlay.book?.author.orEmpty()
        mediaSessionCompat.setMetadata(MediaMetadataCompat.Builder().putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover).putText(MediaMetadataCompat.METADATA_KEY_TITLE, title).putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title).putText(MediaMetadataCompat.METADATA_KEY_ARTIST, bookName).putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, bookName).putText(MediaMetadataCompat.METADATA_KEY_ALBUM, author).putLong(MediaMetadataCompat.METADATA_KEY_DURATION, exoPlayer.duration.coerceAtLeast(0L)).build())
    }

    override fun onPlayerError(error: PlaybackException) { handlePlaybackError(error.localizedMessage ?: error.errorCodeName, error) }

    private fun upPlayProgress() {
        upPlayProgressJob?.cancel(); upPlayProgressJob = lifecycleScope.launch(Main) {
            while (isActive && isRun && !pause) {
                position = exoPlayer.currentPosition.toInt().coerceAtLeast(0); bufferedPosition = exoPlayer.bufferedPosition.toInt().coerceAtLeast(position); AudioPlay.playPositionChanged(position); postEvent(EventBus.AUDIO_BUFFER_PROGRESS, bufferedPosition); postEvent(EventBus.AUDIO_PROGRESS, position); postEvent(EventBus.AUDIO_SIZE, exoPlayer.duration.toInt())
                if (lastNotificationChapter != AudioPlay.durChapterIndex) { lastNotificationChapter = AudioPlay.durChapterIndex; upMediaMetadata(); upMediaSessionPlaybackState(if (pause) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING); upAudioPlayNotification(); MaxAudioSystem.savePlaybackState() }
                if (System.currentTimeMillis() - lastMediaSessionUpdate > 1000) { lastMediaSessionUpdate = System.currentTimeMillis(); upMediaSessionPlaybackState(if (pause) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING) }
                AudioPlay.callback?.upLyricP(position); delay(250)
            }
        }
    }
