package io.legado.app.model

import android.content.Context
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.postEvent

/**
 * Max Audio System
 *
 * 音频播放的统一控制层。底层仍复用 Legado 现有 AudioPlay/AudioPlayService，
 * 这里负责把播放状态、队列、播放模式和用户操作收敛到一个稳定入口，避免 UI
 * 直接依赖 Service 的内部状态。
 */
object MaxAudioSystem {

    enum class PlaybackState {
        IDLE,
        LOADING,
        PLAYING,
        PAUSED,
        STOPPED,
        ERROR
    }

    data class QueueItem(
        val bookUrl: String,
        val title: String,
        val author: String = ""
    )

    data class Snapshot(
        val state: PlaybackState,
        val book: Book?,
        val chapterIndex: Int,
        val chapterTitle: String,
        val position: Int,
        val duration: Int,
        val bufferedPosition: Int,
        val speed: Float,
        val playMode: AudioPlay.PlayMode,
        val sleepTimerMinutes: Int
    )

    private val queue = ArrayList<QueueItem>()

    @Volatile
    private var lastError: String? = null

    fun snapshot(): Snapshot {
        val state = when {
            AudioPlay.status == Status.PLAY -> PlaybackState.PLAYING
            AudioPlay.status == Status.PAUSE -> PlaybackState.PAUSED
            AudioPlay.status == Status.STOP && AudioPlayService.isRun -> PlaybackState.STOPPED
            else -> PlaybackState.IDLE
        }
        return Snapshot(
            state = state,
            book = AudioPlay.book,
            chapterIndex = AudioPlay.durChapterIndex,
            chapterTitle = AudioPlay.durChapter?.title.orEmpty(),
            position = AudioPlay.durChapterPos,
            duration = AudioPlay.durAudioSize,
            bufferedPosition = 0,
            speed = AudioPlayService.playSpeed,
            playMode = AudioPlay.playMode,
            sleepTimerMinutes = AudioPlayService.timeMinute
        )
    }

    fun setQueue(items: List<QueueItem>) {
        synchronized(queue) {
            queue.clear()
            queue.addAll(items)
        }
        postEvent(EventBus.AUDIO_QUEUE_CHANGED, queue.toList())
    }

    fun queue(): List<QueueItem> = synchronized(queue) { queue.toList() }

    fun addToQueue(item: QueueItem) {
        synchronized(queue) {
            if (queue.none { it.bookUrl == item.bookUrl }) queue.add(item)
        }
        postEvent(EventBus.AUDIO_QUEUE_CHANGED, queue.toList())
    }

    fun removeFromQueue(bookUrl: String) {
        synchronized(queue) { queue.removeAll { it.bookUrl == bookUrl } }
        postEvent(EventBus.AUDIO_QUEUE_CHANGED, queue.toList())
    }

    fun clearQueue() {
        synchronized(queue) { queue.clear() }
        postEvent(EventBus.AUDIO_QUEUE_CHANGED, emptyList<QueueItem>())
    }

    fun play(context: Context) {
        if (AudioPlay.status == Status.PAUSE && AudioPlayService.isRun) {
            AudioPlay.resume(context)
        } else {
            AudioPlay.loadOrUpPlayUrl()
        }
    }

    fun pause(context: Context) {
        AudioPlay.pause(context)
    }

    fun toggle(context: Context) {
        if (AudioPlay.status == Status.PLAY) pause(context) else play(context)
    }

    fun stop() {
        AudioPlay.stop()
    }

    fun next() {
        AudioPlay.next()
    }

    fun previous() {
        AudioPlay.prev()
    }

    fun seekTo(position: Int) {
        AudioPlay.adjustProgress(position.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        AudioPlay.setSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        AudioPlay.setTimer(minutes.coerceIn(0, 180))
    }

    fun addSleepTimer() {
        AudioPlay.addTimer()
    }

    fun cyclePlayMode() {
        AudioPlay.changePlayMode()
    }

    fun setError(message: String?) {
        lastError = message
        postEvent(EventBus.AUDIO_ERROR, message.orEmpty())
    }

    fun lastError(): String? = lastError
}
