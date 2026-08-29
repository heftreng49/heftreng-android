package com.heftreng.app.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * AdFreeManager — Rewarded interstitial izlendikten sonra 2 saatlik
 * reklamsız deneyimi yönetir.
 *
 * Kapsam: sadece native ve banner reklamlar.
 * Etkilenmeyen: Kurdî dersleri için rewarded reklamlar (ayrı sistem).
 * Etkilenmeyen: Rewarded interstitial'ın kendisi (geçiş reklamı).
 */
object AdFreeManager {

    private const val PREFS_NAME    = "ad_free_prefs"
    private const val KEY_FREE_UNTIL = "ad_free_until_ms"
    private const val AD_FREE_DURATION_MS = 2 * 60 * 60 * 1_000L // 2 saat

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Rewarded izlenince çağır — 2 saatlik reklamsız süre başlatır. */
    fun grantAdFree() {
        val until = System.currentTimeMillis() + AD_FREE_DURATION_MS
        prefs.edit().putLong(KEY_FREE_UNTIL, until).apply()
    }

    /** Native ve banner göstermeden önce kontrol et. */
    fun isAdFree(): Boolean {
        val until = prefs.getLong(KEY_FREE_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    /** Kalan süreyi dakika cinsinden döndürür (0 ise süresi dolmuş). */
    fun remainingMinutes(): Int {
        val remaining = prefs.getLong(KEY_FREE_UNTIL, 0L) - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 60_000L).toInt() else 0
    }
}
