package io.legado.app.ui.main.explore

/**
 * Discovery cache policy shared by the modern discovery surface.
 *
 * The policy deliberately stays independent from the database/network layer so
 * existing WebBook discovery code remains untouched. Callers can use the
 * limits to decide when to reuse, refresh or evict discovery results.
 */
data class DiscoveryCachePolicy(
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    val maxBooksPerEntry: Int = DEFAULT_MAX_BOOKS_PER_ENTRY,
    val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    val staleWhileRevalidate: Boolean = true
) {
    init {
        require(maxEntries > 0)
        require(maxBooksPerEntry > 0)
        require(ttlMillis >= 0)
    }

    fun normalized(): DiscoveryCachePolicy = copy(
        maxEntries = maxEntries.coerceIn(1, 64),
        maxBooksPerEntry = maxBooksPerEntry.coerceIn(1, 100),
        ttlMillis = ttlMillis.coerceIn(0L, 24L * 60L * 60L * 1000L)
    )

    companion object {
        const val DEFAULT_MAX_ENTRIES = 24
        const val DEFAULT_MAX_BOOKS_PER_ENTRY = 60
        const val DEFAULT_TTL_MILLIS = 10L * 60L * 1000L
    }
}

/** Small in-memory LRU cache used by discovery UI state. */
class DiscoveryResultCache<K, V>(private val policy: DiscoveryCachePolicy = DiscoveryCachePolicy()) {
    private data class Entry<V>(val value: V, val createdAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > policy.normalized().maxEntries
    }

    @Synchronized
    fun get(key: K, now: Long = System.currentTimeMillis()): V? {
        val entry = entries[key] ?: return null
        if (policy.ttlMillis == 0L || now - entry.createdAt > policy.ttlMillis) {
            if (!policy.staleWhileRevalidate) entries.remove(key)
            return if (policy.staleWhileRevalidate) entry.value else null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V, now: Long = System.currentTimeMillis()) {
        entries[key] = Entry(value, now)
        while (entries.size > policy.normalized().maxEntries) {
            entries.remove(entries.entries.firstOrNull()?.key ?: break)
        }
    }

    @Synchronized
    fun remove(key: K) { entries.remove(key) }

    @Synchronized
    fun clear() { entries.clear() }

    @Synchronized
    fun size(): Int = entries.size
}
