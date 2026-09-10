package io.legado.app.ui.video.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 下一集媒体解析预取器。
 *
 * 只负责缓存“解析后的播放计划”，不创建播放器、不持有 Activity/View，
 * 因此可以安全地在 VideoPlay 的 IO scope 中使用。
 */
class VideoEpisodePrefetcher<T> {
    private val mutex = Mutex()
    private var generation = 0L
    private var cachedIndex = -1
    private var cachedValue: T? = null
    private var running: Job? = null

    suspend fun get(index: Int): T? = mutex.withLock {
        if (cachedIndex == index) cachedValue else null
    }

    fun prefetch(
        scope: CoroutineScope,
        index: Int,
        resolver: suspend () -> T?
    ) {
        if (index < 0) return
        running?.cancel()
        val requestGeneration = ++generation
        running = scope.launch {
            val value = runCatching { resolver() }.getOrNull()
            mutex.withLock {
                if (requestGeneration == generation) {
                    cachedIndex = if (value != null) index else -1
                    cachedValue = value
                }
            }
        }
    }

    fun invalidate() {
        generation++
        running?.cancel()
        running = null
        cachedIndex = -1
        cachedValue = null
    }

    suspend fun consume(index: Int): T? = mutex.withLock {
        if (cachedIndex != index) return@withLock null
        val value = cachedValue
        cachedIndex = -1
        cachedValue = null
        value
    }
}
