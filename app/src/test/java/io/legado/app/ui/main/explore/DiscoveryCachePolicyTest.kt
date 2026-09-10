package io.legado.app.ui.main.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryCachePolicyTest {
    @Test
    fun cacheEvictsOldestEntryWhenLimitIsReached() {
        val cache = DiscoveryResultCache<String, String>(
            DiscoveryCachePolicy(maxEntries = 2, maxBooksPerEntry = 10, ttlMillis = 60_000)
        )
        cache.put("a", "A", now = 1)
        cache.put("b", "B", now = 2)
        cache.put("c", "C", now = 3)

        assertNull(cache.get("a", now = 4))
        assertEquals("B", cache.get("b", now = 4))
        assertEquals("C", cache.get("c", now = 4))
    }

    @Test
    fun staleWhileRevalidateKeepsExpiredValue() {
        val cache = DiscoveryResultCache<String, String>(
            DiscoveryCachePolicy(maxEntries = 2, maxBooksPerEntry = 10, ttlMillis = 10, staleWhileRevalidate = true)
        )
        cache.put("a", "A", now = 1)
        assertEquals("A", cache.get("a", now = 12))
    }

    @Test
    fun strictTtlRemovesExpiredValue() {
        val cache = DiscoveryResultCache<String, String>(
            DiscoveryCachePolicy(maxEntries = 2, maxBooksPerEntry = 10, ttlMillis = 10, staleWhileRevalidate = false)
        )
        cache.put("a", "A", now = 1)
        assertNull(cache.get("a", now = 12))
        assertEquals(0, cache.size())
    }
}
