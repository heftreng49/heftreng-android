package com.heftreng.app.util

/**
 * TTL (Time-To-Live) tabanlı in-memory cache wrapper.
 *
 * Kullanım:
 *   private val authorCache = CacheEntry<List<Author>>(ttlMs = 5 * 60_000L)
 *
 *   val data = authorCache.get() ?: fetchFromFirestore().also { authorCache.set(it) }
 */
class CacheEntry<T>(
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {
    private var value    : T? = null
    private var fetchedAt: Long = 0L

    /** Cache geçerliyse değeri döner, süresi dolmuşsa null döner. */
    fun get(): T? {
        if (value == null) return null
        if (System.currentTimeMillis() - fetchedAt > ttlMs) {
            value = null
            return null
        }
        return value
    }

    /** Yeni değeri kaydeder ve zamanlayıcıyı sıfırlar. */
    fun set(v: T) {
        value     = v
        fetchedAt = System.currentTimeMillis()
    }

    /** Cache'i zorla temizler (pull-to-refresh sonrası). */
    fun invalidate() {
        value     = null
        fetchedAt = 0L
    }

    /** Cache dolu ve geçerli mi? */
    fun isValid(): Boolean = get() != null

    companion object {
        /** Varsayılan TTL: 5 dakika */
        const val DEFAULT_TTL_MS = 5 * 60_000L
    }
}
