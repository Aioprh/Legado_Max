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
 * 统一音频会话控制层。底层继续复用 AudioPlay / AudioPlayService / ExoPlayer，
 * 逐步把队列、状态、播放控制和 UI 解耦，方便后续接入 Mini Player、通知栏和完整播放器。
 */
object MaxAudioSystem {

    enum class PlaybackState {
        IDLE, LOADING, PLAYING, PAUSED, STOPPED, ERROR
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
        val speed: Float,
        val playMode: AudioPlay.PlayMode,
        val sleepTimerMinutes: Int,
        val queueSize: Int,
        val lastError: String?
    )

    private val queue = ArrayList<QueueItem>()

    @Volatile
    private var lastError: String? = null

    fun snapshot(): Snapshot {
        val state = when {
            lastError != null && AudioPlay.status == Status.STOP -> PlaybackState.ERROR
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
            speed = AudioPlayService.playSpeed,
            playMode = AudioPlay.playMode,
            sleepTimerMinutes = AudioPlayService.timeMinute,
            queueSize = synchronized(queue) { queue.size },
            lastError = lastError
        )
    }

    fun setQueue(items: List<QueueItem>) {
        synchronized(queue) {
            queue.clear()
            queue.addAll(items.distinctBy { it.bookUrl })
        }
        clearError()
        notifyQueueChanged()
    }

    fun queue(): List<QueueItem> = synchronized(queue) { queue.toList() }

    fun addToQueue(item: QueueItem) {
        synchronized(queue) {
            if (queue.none { it.bookUrl == item.bookUrl }) queue.add(item)
        }
        notifyQueueChanged()
    }

    fun addAllToQueue(items: List<QueueItem>) {
        synchronized(queue) {
            items.forEach { item ->
                if (queue.none { it.bookUrl == item.bookUrl }) queue.add(item)
            }
        }
        notifyQueueChanged()
    }

    fun removeFromQueue(bookUrl: String) {
        synchronized(queue) { queue.removeAll { it.bookUrl == bookUrl } }
        notifyQueueChanged()
    }

    fun clearQueue() {
        synchronized(queue) { queue.clear() }
        notifyQueueChanged()
    }

    /** 播放当前音频。暂停状态优先恢复，避免重新加载 URL。 */
    fun play(context: Context) {
        clearError()
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
        clearError()
        AudioPlay.next()
    }

    fun previous() {
        clearError()
        AudioPlay.prev()
    }

    fun seekTo(position: Int) {
        AudioPlay.adjustProgress(position.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        AudioPlay.setSpeed(speed.coerceIn(0.25f, 4f))
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
        lastError = message?.takeIf { it.isNotBlank() }
        postEvent(EventBus.AUDIO_ERROR, lastError.orEmpty())
        postEvent(EventBus.AUDIO_STATE, Status.STOP)
    }

    fun clearError() {
        if (lastError != null) {
            lastError = null
            postEvent(EventBus.AUDIO_ERROR, "")
        }
    }

    fun lastError(): String? = lastError

    private fun notifyQueueChanged() {
        postEvent(EventBus.AUDIO_QUEUE_CHANGED, queue())
    }
}
