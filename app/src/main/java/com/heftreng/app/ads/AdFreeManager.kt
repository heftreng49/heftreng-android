package com.heftreng.app.ads

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

/**
 * AdFreeManager — Kullanıcının kendi başlattığı ödüllü reklam (Rewarded Ad)
 * sonrası 1 saatlik reklamsız deneyimi yönetir.
 *
 * Kapsam: sadece native ve banner reklamlar.
 * Etkilenmeyen: Kurdî dersleri için rewarded reklamlar (ayrı sistem, RewardType.UNLOCK_LESSON).
 * Etkilenmeyen: Rewarded interstitial (ekran geçişlerinde otomatik çıkan, ayrı sistem).
 *
 * GÜVENLİK: System.currentTimeMillis() KULLANILMAZ — kullanıcı cihaz saatini
 * ileri/geri alarak süreyi manipüle edebilirdi. Bunun yerine SystemClock.elapsedRealtime()
 * kullanılır: cihaz açıldığından beri geçen süre, kullanıcı tarafından değiştirilemez,
 * sistem saati ayarından etkilenmez (uyku modunda bile sayar).
 *
 * Not: elapsedRealtime cihaz yeniden başlatılınca sıfırlanır. Bu durumda kalan
 * reklamsız süre kaybolur — kabul edilebilir bir sınırlama (nadir senaryo,
 * kötüye kullanım riski çok daha düşük önceliklidir).
 */
object AdFreeManager {

    private const val PREFS_NAME     = "ad_free_prefs"
    private const val KEY_FREE_UNTIL = "ad_free_until_elapsed_ms"
    private const val AD_FREE_DURATION_MS = 60 * 60 * 1_000L // 1 saat

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Ödüllü reklam izlenince çağır — 1 saatlik reklamsız süre ekler.
     * Süre hala aktifse üstüne eklenir (max 24 saat).
     * Süre dolmuşsa sıfırdan 1 saat başlar.
     */
    fun grantAdFree() {
        val now     = SystemClock.elapsedRealtime()
        val current = prefs.getLong(KEY_FREE_UNTIL, 0L)
        // Kalan süre varsa üstüne ekle, yoksa sıfırdan başlat
        val base    = if (current > now) current else now
        // Max 24 saat — kötüye kullanım koruması
        val maxUntil = now + 24 * 60 * 60 * 1_000L
        val until   = minOf(base + AD_FREE_DURATION_MS, maxUntil)
        prefs.edit().putLong(KEY_FREE_UNTIL, until).apply()
    }

    /** Native ve banner göstermeden önce kontrol et. */
    fun isAdFree(): Boolean {
        val until = prefs.getLong(KEY_FREE_UNTIL, 0L)
        return SystemClock.elapsedRealtime() < until
    }

    /** Kalan süreyi dakika cinsinden döndürür (0 ise süresi dolmuş). */
    fun remainingMinutes(): Int {
        val remaining = prefs.getLong(KEY_FREE_UNTIL, 0L) - SystemClock.elapsedRealtime()
        return if (remaining > 0) (remaining / 60_000L).toInt() else 0
    }

    /** Kalan süreyi saniye cinsinden döndürür — UI'da canlı sayaç için. */
    fun remainingSeconds(): Long {
        val remaining = prefs.getLong(KEY_FREE_UNTIL, 0L) - SystemClock.elapsedRealtime()
        return if (remaining > 0) remaining / 1000L else 0L
    }
}
