package io.legado.app.ui.video.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoEpisodePrefetcherTest {
    @Test
    fun consumeOnlyReturnsMatchingIndexAndClearsCache() = runBlocking {
        val prefetcher = VideoEpisodePrefetcher<String>()
        prefetcher.prefetch(this, 2) { "episode-3" }
        delay(10)

        assertEquals("episode-3", prefetcher.consume(2))
        assertNull(prefetcher.consume(2))
        assertNull(prefetcher.consume(1))
    }

    @Test
    fun invalidateCancelsPendingResult() = runBlocking {
        val prefetcher = VideoEpisodePrefetcher<String>()
        prefetcher.prefetch(this, 1) {
            delay(100)
            "stale"
        }
        prefetcher.invalidate()
        delay(120)
        assertNull(prefetcher.consume(1))
    }
}
