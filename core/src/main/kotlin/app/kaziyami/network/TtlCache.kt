package app.kaziyami.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Thread-safe memory cache with Time-To-Live (TTL) expiration and in-flight request deduplication.
 * Prevents redundant HTTP requests during browse actions and caches static filter options.
 */
class TtlCache<K : Any, V : Any>(
    private val defaultTtlMs: Long = TimeUnit.MINUTES.toMillis(15),
    private val maxEntries: Int = 200
) {
    private data class CacheEntry<V>(val value: V, val expiresAt: Long)

    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val inFlightLocks = ConcurrentHashMap<K, Any>()

    /**
     * Retrieves an entry if present and not expired.
     */
    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key)
            return null
        }
        return entry.value
    }

    /**
     * Stores an entry with a specific TTL.
     */
    fun put(key: K, value: V, ttlMs: Long = defaultTtlMs) {
        if (cache.size >= maxEntries) {
            evictExpired()
            if (cache.size >= maxEntries) {
                // Drop oldest entry if still full
                val oldest = cache.keys().asSequence().firstOrNull()
                if (oldest != null) cache.remove(oldest)
            }
        }
        val expiresAt = System.currentTimeMillis() + ttlMs
        cache[key] = CacheEntry(value, expiresAt)
    }

    /**
     * Deduplicates concurrent loads for the same key. Only one loader executes; others wait for the result.
     */
    fun getOrPut(key: K, ttlMs: Long = defaultTtlMs, loader: () -> V): V {
        val cached = get(key)
        if (cached != null) return cached

        val lock = inFlightLocks.computeIfAbsent(key) { Any() }
        synchronized(lock) {
            val doubleCheck = get(key)
            if (doubleCheck != null) return doubleCheck

            val result = loader()
            put(key, result, ttlMs)
            inFlightLocks.remove(key)
            return result
        }
    }

    fun evictExpired() {
        val now = System.currentTimeMillis()
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now > entry.value.expiresAt) {
                it.remove()
            }
        }
    }

    fun clear() {
        cache.clear()
        inFlightLocks.clear()
    }

    fun size(): Int = cache.size
}
