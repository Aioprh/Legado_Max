package io.legado.app.ui.video.player

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoEpisodePrefetcherTest {
    @Test
    fun consumeOnlyReturnsMatchingIndexAndClearsCache() = runTest {
        val prefetcher = VideoEpisodePrefetcher<String>()
        prefetcher.prefetch(this, 2) { "episode-3" }
        delay(1)

        assertEquals("episode-3", prefetcher.consume(2))
        assertNull(prefetcher.consume(2))
        assertNull(prefetcher.consume(1))
    }

    @Test
    fun invalidateCancelsPendingResult() = runTest {
        val prefetcher = VideoEpisodePrefetcher<String>()
        val gate = async {
            prefetcher.prefetch(this@runTest, 1) {
                delay(100)
                "stale"
            }
        }
        gate.await()
        prefetcher.invalidate()
        delay(101)
        assertNull(prefetcher.consume(1))
    }
}
