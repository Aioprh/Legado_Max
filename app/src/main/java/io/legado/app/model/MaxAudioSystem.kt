package io.legado.app.model

import android.content.Context
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.postEvent

/** Unified audio session facade for Max. */
object MaxAudioSystem {
    enum class PlaybackState { IDLE, LOADING, PLAYING, PAUSED, STOPPED, ERROR }

    data class QueueItem(val bookUrl: String, val title: String, val author: String = "")

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
        val sleepTimerMinutes: Int,
        val queueSize: Int,
        val queueIndex: Int,
        val lastError: String?
    )

    private val queue = ArrayList<QueueItem>()
    private var queueIndex = -1
    @Volatile private var lastError: String? = null

    fun snapshot(): Snapshot {
        val state = when {
            lastError != null && AudioPlay.status == Status.STOP -> PlaybackState.ERROR
            AudioPlay.status == Status.PLAY -> PlaybackState.PLAYING
            AudioPlay.status == Status.PAUSE -> PlaybackState.PAUSED
            AudioPlay.status == Status.STOP && AudioPlayService.isRun -> PlaybackState.STOPPED
            else -> PlaybackState.IDLE
        }
        return Snapshot(
            state, AudioPlay.book, AudioPlay.durChapterIndex,
            AudioPlay.durChapter?.title.orEmpty(), AudioPlay.durChapterPos,
            AudioPlay.durAudioSize, AudioPlayService.bufferedPosition,
            AudioPlayService.playSpeed, AudioPlay.playMode,
            AudioPlayService.timeMinute, queueSize(), queueIndex, lastError
        )
    }

    fun setQueue(items: List<QueueItem>) {
        synchronized(queue) {
            queue.clear()
            queue.addAll(items.distinctBy { it.bookUrl })
            queueIndex = queueIndex.coerceIn(-1, queue.lastIndex)
        }
        clearError()
        notifyQueueChanged()
    }

    fun queue(): List<QueueItem> = synchronized(queue) { queue.toList() }
    fun queueSize(): Int = synchronized(queue) { queue.size }
    fun currentQueueIndex(): Int = synchronized(queue) { queueIndex }

    fun setQueueIndex(index: Int) {
        synchronized(queue) { queueIndex = index.coerceIn(-1, queue.lastIndex) }
        notifyQueueChanged()
    }

    fun addToQueue(item: QueueItem) = addAllToQueue(listOf(item))

    fun addAllToQueue(items: List<QueueItem>) {
        synchronized(queue) {
            items.forEach { if (queue.none { it.bookUrl == item.bookUrl }) queue.add(item) }
        }
        notifyQueueChanged()
    }

    fun removeFromQueue(bookUrl: String) {
        synchronized(queue) {
            val removed = queue.indexOfFirst { it.bookUrl == bookUrl }
            queue.removeAll { it.bookUrl == bookUrl }
            if (removed >= 0 && removed < queueIndex) queueIndex--
            queueIndex = queueIndex.coerceIn(-1, queue.lastIndex)
        }
        notifyQueueChanged()
    }

    fun clearQueue() {
        synchronized(queue) { queue.clear(); queueIndex = -1 }
        notifyQueueChanged()
    }

    fun moveInQueue(from: Int, to: Int): Boolean {
        synchronized(queue) {
            if (from !in queue.indices || to !in queue.indices || from == to) return false
            val item = queue.removeAt(from)
            queue.add(to, item)
            if (queueIndex == from) queueIndex = to
            else if (from < queueIndex && to >= queueIndex) queueIndex--
            else if (from > queueIndex && to <= queueIndex) queueIndex++
        }
        notifyQueueChanged()
        return true
    }

    fun play(context: Context) {
        clearError()
        if (AudioPlay.status == Status.PAUSE && AudioPlayService.isRun) AudioPlay.resume(context)
        else AudioPlay.loadOrUpPlayUrl()
    }

    fun pause(context: Context) = AudioPlay.pause(context)
    fun toggle(context: Context) { if (AudioPlay.status == Status.PLAY) pause(context) else play(context) }
    fun stop() = AudioPlay.stop()
    fun next() { clearError(); AudioPlay.next() }
    fun previous() { clearError(); AudioPlay.prev() }
    fun seekTo(position: Int) = AudioPlay.adjustProgress(position.coerceAtLeast(0))
    fun setSpeed(speed: Float) = AudioPlay.setSpeed(speed.coerceIn(0.25f, 4f))
    fun setSleepTimer(minutes: Int) = AudioPlay.setTimer(minutes.coerceIn(0, 180))
    fun addSleepTimer() = AudioPlay.addTimer()
    fun cyclePlayMode() = AudioPlay.changePlayMode()

    fun setError(message: String?) {
        lastError = message?.takeIf { it.isNotBlank() }
        postEvent(EventBus.AUDIO_ERROR, lastError.orEmpty())
    }

    fun clearError() {
        if (lastError != null) {
            lastError = null
            postEvent(EventBus.AUDIO_ERROR, "")
        }
    }

    fun lastError(): String? = lastError

    private fun notifyQueueChanged() = postEvent(EventBus.AUDIO_QUEUE_CHANGED, queue())
}
