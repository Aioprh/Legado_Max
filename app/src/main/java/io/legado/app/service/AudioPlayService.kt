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
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.Status
import io.legado.app.exception.NoStackTraceException
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
        @JvmStatic var timeMinute: Int = 0
        @JvmStatic var playSpeed: Float = 1f
        @JvmStatic var bufferedPosition: Int = 0; private set
        var url: String = ""; private set
        private const val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        private const val APP_ACTION_STOP = "Stop"
        private const val APP_ACTION_TIMER = "Timer"
    }
    private val useWakeLock = AppConfig.audioPlayUseWakeLock
    private val wakeLock by lazy { powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"legado:AudioPlayService").apply{setReferenceCounted(false)} }
    private val wifiLock by lazy { @Suppress("DEPRECATION") wifiManager?.createWifiLock(WIFI_MODE_FULL_HIGH_PERF,"legado:AudioPlayService")?.apply{setReferenceCounted(false)} }
    private val mFocusRequest: AudioFocusRequestCompat by lazy { MediaHelp.buildAudioFocusRequestCompat(this) }
    private val exoPlayer: ExoPlayer by lazy { ExoPlayerHelper.createHttpExoPlayer(this) }
    private val mediaSessionCompat by lazy { MediaSessionCompat(this,"readAloud") }
    private var broadcastReceiver: BroadcastReceiver? = null
    private var needResumeOnAudioFocusGain = false
    private var position = AudioPlay.book?.durChapterPos ?: 0
    private var dsJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var upPlayProgressJob: Job? = null
    private var lastBufferedPosition = -1
    private var lastDuration = -1
    private var lastMediaSessionUpdate = 0L
    private var cover: Bitmap = BitmapFactory.decodeResource(appCtx.resources,R.drawable.icon_read_book)

    override fun onCreate(){super.onCreate();isRun=true;bufferedPosition=0;exoPlayer.addListener(this);AudioPlay.registerService(this);initMediaSession();initBroadcastReceiver();upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);doDs();execute{ImageLoader.loadBitmap(this@AudioPlayService,AudioPlay.book?.let{BookCover.getDisplayCover(it)}).submit().get()}.onSuccess{if(it.width>16&&it.height>16){cover=it;upMediaMetadata();upAudioPlayNotification()}}}

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{when(intent?.action){IntentAction.play,IntentAction.playNew->{exoPlayer.stop();upPlayProgressJob?.cancel();pause=false;AudioPlay.markReadStart();position=if(intent.action==IntentAction.playNew)0 else AudioPlay.book?.durChapterPos?:0;url=AudioPlay.durPlayUrl;if(playSpeed!=1f)upSpeed(playSpeed);upMediaSessionPlaybackState(PlaybackStateCompat.STATE_BUFFERING);play()};IntentAction.stopPlay->{AudioPlay.upReadTime();exoPlayer.stop();upPlayProgressJob?.cancel();AudioPlay.status=Status.STOP;upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);postEvent(EventBus.AUDIO_STATE,Status.STOP);pause=true;upAudioPlayNotification()};IntentAction.pause->pause();IntentAction.resume->resume();IntentAction.prev->MaxAudioSystem.previous();IntentAction.next->MaxAudioSystem.next();IntentAction.setSpeed->upSpeed(intent.getFloatExtra("speed",1f));IntentAction.addTimer->addTimer();IntentAction.setTimer->setTimer(intent.getIntExtra("minute",0));IntentAction.adjustProgress->adjustProgress(intent.getIntExtra("position",position));IntentAction.stop->{AudioPlay.upReadTime();pause=true;stopSelf()}};return super.onStartCommand(intent,flags,startId)}

    override fun onDestroy(){if(!pause)AudioPlay.upReadTime();super.onDestroy();if(useWakeLock){wakeLock.release();wifiLock?.release()};isRun=false;bufferedPosition=0;abandonFocus();exoPlayer.release();mediaSessionCompat.release();unregisterReceiver(broadcastReceiver);upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);AudioPlay.status=Status.STOP;postEvent(EventBus.AUDIO_STATE,Status.STOP);AudioPlay.unregisterService();upNotificationJob?.invokeOnCompletion{notificationManager.cancel(NotificationId.AudioPlayService)}}

    @OptIn(UnstableApi::class) @SuppressLint("WakelockTimeout") private fun play(){if(useWakeLock){wakeLock.acquire();wifiLock?.acquire()};upAudioPlayNotification();if(!requestFocus())return;val book=AudioPlay.book;execute(context=Main){AudioPlay.status=Status.STOP;postEvent(EventBus.AUDIO_STATE,Status.STOP);upPlayProgressJob?.cancel();if(url.isJsonArray()){val mediaSource=ExoPlayerHelper.getMediaSource(this@AudioPlayService,url);if(mediaSource==null){NoStackTraceException("url格式错误");return@execute};exoPlayer.setMediaSource(mediaSource);position=0}else{val analyzeUrl=AnalyzeUrl(url,source=AudioPlay.bookSource,ruleData=book,chapter=AudioPlay.durChapter,coroutineContext=coroutineContext);exoPlayer.setMediaItem(analyzeUrl.getMediaItem())};exoPlayer.playWhenReady=true;val skipStartMs=(book?.getOpenCredits()?:0)*1000L;exoPlayer.seekTo(if(position==0)skipStartMs else position.toLong());exoPlayer.prepare()}.onError{AppLog.put("播放出错\n${it.localizedMessage}",it);toastOnUi("$url ${it.localizedMessage}");MaxAudioSystem.setError(it.localizedMessage);stopSelf()}}

    private fun pause(abandonFocus:Boolean=true){if(useWakeLock){wakeLock.release();wifiLock?.release()};try{AudioPlay.upReadTime();pause=true;if(abandonFocus)abandonFocus();upPlayProgressJob?.cancel();position=exoPlayer.currentPosition.toInt();AudioPlay.durChapterPos=position;if(exoPlayer.isPlaying)exoPlayer.pause();upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);AudioPlay.status=Status.PAUSE;postEvent(EventBus.AUDIO_STATE,Status.PAUSE);upAudioPlayNotification()}catch(e:Exception){e.printOnDebug()}}
    @SuppressLint("WakelockTimeout") private fun resume(){if(useWakeLock){wakeLock.acquire();wifiLock?.acquire()};try{AudioPlay.markReadStart();pause=false;if(url.isEmpty()){AudioPlay.loadOrUpPlayUrl();return};if(exoPlayer.playbackState==Player.STATE_IDLE){position=AudioPlay.durChapterPos;play();return};if(!exoPlayer.isPlaying)exoPlayer.play();upPlayProgress();AudioPlay.status=Status.PLAY;postEvent(EventBus.AUDIO_STATE,Status.PLAY);upAudioPlayNotification()}catch(e:Exception){e.printOnDebug();MaxAudioSystem.setError(e.localizedMessage);stopSelf()}}
    private fun adjustProgress(position:Int){this.position=position.coerceAtLeast(0);AudioPlay.durChapterPos=this.position;exoPlayer.seekTo(this.position.toLong())}
    @SuppressLint("ObsoleteSdkInt") private fun upSpeed(speed:Float){runCatching{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){playSpeed=speed.coerceIn(0.25f,4f);exoPlayer.setPlaybackSpeed(playSpeed);postEvent(EventBus.AUDIO_SPEED,playSpeed)}}}

    override fun onPlaybackStateChanged(playbackState:Int){super.onPlaybackStateChanged(playbackState);when(playbackState){Player.STATE_BUFFERING->AudioPlay.upLoading(true);Player.STATE_READY->{AudioPlay.upLoading(false);if(exoPlayer.playWhenReady){AudioPlay.status=Status.PLAY;postEvent(EventBus.AUDIO_STATE,Status.PLAY)}else{AudioPlay.status=Status.PAUSE;postEvent(EventBus.AUDIO_STATE,Status.PAUSE)};postEvent(EventBus.AUDIO_SIZE,exoPlayer.duration.toInt());lastDuration=exoPlayer.duration.toInt();upMediaMetadata();upPlayProgress();AudioPlay.saveDurChapter(exoPlayer.duration)};Player.STATE_ENDED->{upPlayProgressJob?.cancel();AudioPlay.playPositionChanged(exoPlayer.duration.toInt());if(!MaxAudioSystem.onPlaybackEnded()){AudioPlay.status=Status.STOP;postEvent(EventBus.AUDIO_STATE,Status.STOP)}}};upAudioPlayNotification()}

    private fun upMediaMetadata(){mediaSessionCompat.setMetadata(MediaMetadataCompat.Builder().putBitmap(MediaMetadataCompat.METADATA_KEY_ART,cover).putText(MediaMetadataCompat.METADATA_KEY_TITLE,AudioPlay.durChapter?.title?:"null").putText(MediaMetadataCompat.METADATA_KEY_ARTIST,AudioPlay.book?.name?:"null").putText(MediaMetadataCompat.METADATA_KEY_ALBUM,AudioPlay.book?.author?:"null").putLong(MediaMetadataCompat.METADATA_KEY_DURATION,exoPlayer.duration).build())}
    override fun onPlayerError(error:PlaybackException){val msg=error.localizedMessage?:error.errorCodeName;AudioPlay.upLoading(false);AudioPlay.status=Status.STOP;postEvent(EventBus.AUDIO_STATE,Status.STOP);MaxAudioSystem.setError(msg);AppLog.put("播放出错\n$msg",error,true);toastOnUi(msg);upAudioPlayNotification()}

    private fun upPlayProgress(){upPlayProgressJob?.cancel();upPlayProgressJob=lifecycleScope.launch(Main){while(isActive&&isRun){position=exoPlayer.currentPosition.toInt();bufferedPosition=exoPlayer.bufferedPosition.toInt().coerceAtLeast(position);AudioPlay.playPositionChanged(position);postEvent(EventBus.AUDIO_BUFFER_PROGRESS,bufferedPosition);postEvent(EventBus.AUDIO_PROGRESS,position);postEvent(EventBus.AUDIO_SIZE,exoPlayer.duration.toInt());if(System.currentTimeMillis()-lastMediaSessionUpdate>1000)upMediaSessionPlaybackState(if(pause)PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING);delay(250)}}}
