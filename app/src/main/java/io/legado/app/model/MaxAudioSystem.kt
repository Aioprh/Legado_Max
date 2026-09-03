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
        val state: PlaybackState, val book: Book?, val chapterIndex: Int,
        val chapterTitle: String, val position: Int, val duration: Int,
        val bufferedPosition: Int, val speed: Float,
        val playMode: AudioPlay.PlayMode, val sleepTimerMinutes: Int,
        val queueSize: Int, val queueIndex: Int, val lastError: String?
    )

    private val queue = ArrayList<QueueItem>()
    private var queueIndex = -1
    @Volatile private var lastError: String? = null
    @Volatile private var restored = false

    private fun ensureRestored() {
        if (restored) return
        synchronized(queue) {
            if (restored) return
            val saved = MaxAudioSession.restore()
            queue.addAll(saved.queue.distinctBy { it.bookUrl })
            queueIndex = saved.currentIndex.coerceIn(-1, queue.lastIndex)
            restored = true
        }
    }

    fun snapshot(): Snapshot {
        ensureRestored()
        val state = when {
            lastError != null && AudioPlay.status == Status.STOP -> PlaybackState.ERROR
            AudioPlay.status == Status.PLAY -> PlaybackState.PLAYING
            AudioPlay.status == Status.PAUSE -> PlaybackState.PAUSED
            AudioPlay.status == Status.STOP && AudioPlayService.isRun -> PlaybackState.STOPPED
            else -> PlaybackState.IDLE
        }
        return Snapshot(state, AudioPlay.book, AudioPlay.durChapterIndex, AudioPlay.durChapter?.title.orEmpty(), AudioPlay.durChapterPos, AudioPlay.durAudioSize, AudioPlayService.bufferedPosition, AudioPlayService.playSpeed, AudioPlay.playMode, AudioPlayService.timeMinute, queueSize(), queueIndex, lastError)
    }

    fun setQueue(items: List<QueueItem>) { ensureRestored(); synchronized(queue) { queue.clear(); queue.addAll(items.distinctBy { it.bookUrl }); queueIndex = queue.indexOfFirst { it.bookUrl == AudioPlay.book?.bookUrl } }; persist(); clearError(); notifyQueueChanged() }
    fun queue(): List<QueueItem> { ensureRestored(); return synchronized(queue) { queue.toList() } }
    fun queueSize(): Int = synchronized(queue) { ensureRestored(); queue.size }
    fun currentQueueIndex(): Int = synchronized(queue) { ensureRestored(); queueIndex }
    fun setQueueIndex(index: Int) { ensureRestored(); synchronized(queue) { queueIndex = index.coerceIn(-1, queue.lastIndex) }; persist(); notifyQueueChanged() }
    fun addToQueue(item: QueueItem) = addAllToQueue(listOf(item))
    fun addAllToQueue(items: List<QueueItem>) { ensureRestored(); synchronized(queue) { items.forEach { item -> if (queue.none { it.bookUrl == item.bookUrl }) queue.add(item) } }; persist(); notifyQueueChanged() }
    fun removeFromQueue(bookUrl: String) { ensureRestored(); synchronized(queue) { val removed=queue.indexOfFirst { it.bookUrl==bookUrl }; queue.removeAll { it.bookUrl==bookUrl }; if(removed>=0&&removed<queueIndex) queueIndex--; queueIndex=queueIndex.coerceIn(-1,queue.lastIndex) }; persist(); notifyQueueChanged() }
    fun clearQueue() { synchronized(queue) { queue.clear(); queueIndex=-1 }; persist(); notifyQueueChanged() }
    fun moveInQueue(from:Int,to:Int):Boolean { ensureRestored(); synchronized(queue){ if(from !in queue.indices||to !in queue.indices||from==to)return false; val item=queue.removeAt(from);queue.add(to,item);if(queueIndex==from)queueIndex=to else if(from<queueIndex&&to>=queueIndex)queueIndex-- else if(from>queueIndex&&to<=queueIndex)queueIndex++ }; persist(); notifyQueueChanged(); return true }

    fun syncCurrentBook(book: Book?) {
        ensureRestored(); if(book==null)return
        synchronized(queue){val i=queue.indexOfFirst{it.bookUrl==book.bookUrl}; if(i>=0)queueIndex=i else{queue.add(QueueItem(book.bookUrl,book.name,book.author));queueIndex=queue.lastIndex}}
        persist(); postEvent(EventBus.AUDIO_STATE,AudioPlay.status)
    }

    fun onPlaybackEnded(): Boolean {
        ensureRestored()
        if(AudioPlay.book==null)return false
        if(AudioPlay.durChapterIndex+1<AudioPlay.simulatedChapterSize){AudioPlay.next();return true}
        val next=synchronized(queue){when{queue.isEmpty()->-1;AudioPlay.playMode==AudioPlay.PlayMode.RANDOM->queue.indices.filter{it!=queueIndex}.randomOrNull()?:-1;queueIndex+1<queue.size->queueIndex+1;AudioPlay.playMode==AudioPlay.PlayMode.LIST_LOOP->0;else->-1}}
        return if(next>=0)playQueueIndex(next)else{persist();false}
    }

    fun playQueueIndex(index:Int):Boolean {
        ensureRestored(); val item=synchronized(queue){queue.getOrNull(index)}?:return false
        val target=io.legado.app.data.appDb.bookDao.getBook(item.bookUrl)?:return false
        synchronized(queue){queueIndex=index}; clearError(); AudioPlay.resetData(target); AudioPlay.loadOrUpPlayUrl(); persist(); notifyQueueChanged(); return true
    }

    fun play(context:Context){clearError();if(AudioPlay.status==Status.PAUSE&&AudioPlayService.isRun)AudioPlay.resume(context)else AudioPlay.loadOrUpPlayUrl()}
    fun pause(context:Context)=AudioPlay.pause(context)
    fun toggle(context:Context){if(AudioPlay.status==Status.PLAY)pause(context)else play(context)}
    fun stop()=AudioPlay.stop()
    fun next(){clearError();if(!onPlaybackEnded())AudioPlay.next()}
    fun previous(){clearError();AudioPlay.prev()}
    fun seekTo(position:Int)=AudioPlay.adjustProgress(position.coerceAtLeast(0))
    fun setSpeed(speed:Float){AudioPlay.setSpeed(speed.coerceIn(0.25f,4f));persist()}
    fun setSleepTimer(minutes:Int){AudioPlay.setTimer(minutes.coerceIn(0,180));persist()}
    fun addSleepTimer()=AudioPlay.addTimer()
    fun cyclePlayMode(){AudioPlay.changePlayMode();persist()}
    fun setError(message:String?){lastError=message?.takeIf{it.isNotBlank()};postEvent(EventBus.AUDIO_ERROR,lastError.orEmpty())}
    fun clearError(){if(lastError!=null){lastError=null;postEvent(EventBus.AUDIO_ERROR,"")}}
    fun lastError():String?=lastError
    private fun persist(){ensureRestored();MaxAudioSession.save(queue(),queueIndex,AudioPlay.durChapterPos,AudioPlayService.playSpeed,AudioPlay.playMode,AudioPlay.book)}
    private fun notifyQueueChanged()=postEvent(EventBus.AUDIO_QUEUE_CHANGED,queue())
}
