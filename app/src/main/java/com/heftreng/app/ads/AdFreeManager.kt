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
 * ── Zaman sistemi ──────────────────────────────────────────────────────────
 * İKİ değer birlikte saklanır:
 *
 *   KEY_FREE_UNTIL_WALL   → System.currentTimeMillis() (gerçek saat, epoch ms)
 *   KEY_FREE_UNTIL_BOOT   → SystemClock.elapsedRealtime() (boot'tan itibaren ms)
 *
 * isAdFree() her ikisini de kontrol eder; hangisi daha kısa süre gösteriyorsa
 * onu kullanır. Bu sayede:
 *   - Kullanıcı cihaz saatini ileri alsa → wall clock doğrular (max 24 saat koruması)
 *   - Cihaz yeniden başlasa → boot clock sıfırlanır, wall clock devam eder
 *   - Uygulama güncellemesi → SharedPreferences korunur, wall clock doğru kalır
 *
 * Eski yöntemin bug'ı: sadece elapsedRealtime() saklanıyordu. Cihaz yeniden
 * başlayınca elapsedRealtime() sıfırlanır ama prefs'teki büyük değer kalır →
 * isAdFree() = true → 700 saate kadar reklam çıkmıyordu.
 */
object AdFreeManager {

    private const val PREFS_NAME          = "ad_free_prefs"
    // Eski key — migration için okunur, artık yazılmaz
    private const val KEY_FREE_UNTIL_LEGACY = "ad_free_until_elapsed_ms"
    // Yeni keyler
    private const val KEY_FREE_UNTIL_WALL   = "ad_free_until_wall_ms"
    private const val KEY_FREE_UNTIL_BOOT   = "ad_free_until_boot_ms"
    private const val KEY_BOOT_EPOCH        = "ad_free_boot_epoch_ms"

    private const val AD_FREE_DURATION_MS   = 60 * 60 * 1_000L  // 1 saat
    private const val MAX_AD_FREE_MS        = 24 * 60 * 60 * 1_000L // max 24 saat

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyIfNeeded()
    }

    /**
     * Eski yalnızca-elapsedRealtime sisteminden migration.
     * Eski key varsa ve makul bir değerdeyse (24 saatten az kalmış) wall clock'a çevir.
     * Makul değilse (cihaz yeniden başlamış, değer saçma) sil.
     */
    private fun migrateLegacyIfNeeded() {
        val legacy = prefs.getLong(KEY_FREE_UNTIL_LEGACY, 0L)
        if (legacy == 0L) return  // zaten migrate edilmiş veya hiç grant yapılmamış

        val now = SystemClock.elapsedRealtime()
        val remaining = legacy - now

        if (remaining in 1..MAX_AD_FREE_MS) {
            // Makul kalan süre var — wall clock'a çevir
            val wallUntil = System.currentTimeMillis() + remaining
            prefs.edit()
                .putLong(KEY_FREE_UNTIL_WALL, wallUntil)
                .putLong(KEY_FREE_UNTIL_BOOT, legacy)
                .putLong(KEY_BOOT_EPOCH, System.currentTimeMillis() - now)
                .remove(KEY_FREE_UNTIL_LEGACY)
                .apply()
        } else {
            // Saçma değer (cihaz yeniden başlamış, 700 saat senaryosu) — temizle
            prefs.edit()
                .remove(KEY_FREE_UNTIL_LEGACY)
                .remove(KEY_FREE_UNTIL_WALL)
                .remove(KEY_FREE_UNTIL_BOOT)
                .remove(KEY_BOOT_EPOCH)
                .apply()
        }
    }

    /**
     * Ödüllü reklam izlenince çağır — 1 saatlik reklamsız süre ekler.
     * Süre hâlâ aktifse üstüne eklenir (max 24 saat).
     */
    fun grantAdFree() {
        val nowWall = System.currentTimeMillis()
        val nowBoot = SystemClock.elapsedRealtime()

        val currentWall = prefs.getLong(KEY_FREE_UNTIL_WALL, 0L)
        val currentBoot = prefs.getLong(KEY_FREE_UNTIL_BOOT, 0L)

        // Kalan süre varsa üstüne ekle, yoksa sıfırdan başlat
        val baseWall = if (currentWall > nowWall) currentWall else nowWall
        val baseBoot = if (currentBoot > nowBoot) currentBoot else nowBoot

        val untilWall = minOf(baseWall + AD_FREE_DURATION_MS, nowWall + MAX_AD_FREE_MS)
        val untilBoot = minOf(baseBoot + AD_FREE_DURATION_MS, nowBoot + MAX_AD_FREE_MS)

        prefs.edit()
            .putLong(KEY_FREE_UNTIL_WALL, untilWall)
            .putLong(KEY_FREE_UNTIL_BOOT, untilBoot)
            .putLong(KEY_BOOT_EPOCH, nowWall - nowBoot)
            .apply()
    }

    /**
     * Native ve banner göstermeden önce kontrol et.
     * Her iki clock'u da kontrol eder — hangisi daha kısa süre veriyorsa o kazanır.
     */
    fun isAdFree(): Boolean {
        val nowWall = System.currentTimeMillis()
        val nowBoot = SystemClock.elapsedRealtime()

        val untilWall = prefs.getLong(KEY_FREE_UNTIL_WALL, 0L)
        val untilBoot = prefs.getLong(KEY_FREE_UNTIL_BOOT, 0L)

        // Cihaz yeniden başladıysa boot clock'u geçersiz say
        // (boot epoch kaydedilmişse tutarlılık kontrolü yap)
        val bootEpoch = prefs.getLong(KEY_BOOT_EPOCH, -1L)
        val bootClockValid = if (bootEpoch >= 0L) {
            // Şu anki boot epoch ile kaydedilen örtüşüyor mu?
            val currentBootEpoch = nowWall - nowBoot
            // 5 dakika tolerans (cihaz NTP güncellemesi yapabilir)
            kotlin.math.abs(currentBootEpoch - bootEpoch) < 5 * 60 * 1_000L
        } else true

        val wallAdFree = untilWall > nowWall
        val bootAdFree = bootClockValid && untilBoot > nowBoot

        // İkisi de aktifse reklamsız; biri bile geçersizse reklamlı
        return wallAdFree && bootAdFree
    }

    /** Kalan süreyi dakika cinsinden döndürür (0 ise süresi dolmuş). */
    fun remainingMinutes(): Int = (remainingSeconds() / 60L).toInt()

    /** Kalan süreyi saniye cinsinden döndürür — UI'da canlı sayaç için. */
    fun remainingSeconds(): Long {
        val nowWall = System.currentTimeMillis()
        val nowBoot = SystemClock.elapsedRealtime()

        val untilWall = prefs.getLong(KEY_FREE_UNTIL_WALL, 0L)
        val untilBoot = prefs.getLong(KEY_FREE_UNTIL_BOOT, 0L)

        val bootEpoch = prefs.getLong(KEY_BOOT_EPOCH, -1L)
        val bootClockValid = if (bootEpoch >= 0L) {
            val currentBootEpoch = nowWall - nowBoot
            kotlin.math.abs(currentBootEpoch - bootEpoch) < 5 * 60 * 1_000L
        } else true

        val remainingWall = (untilWall - nowWall).coerceAtLeast(0L)
        val remainingBoot = if (bootClockValid) (untilBoot - nowBoot).coerceAtLeast(0L) else 0L

        // Hangisi daha az süre gösteriyorsa onu döndür
        return if (remainingWall == 0L || remainingBoot == 0L) 0L
        else minOf(remainingWall, remainingBoot) / 1000L
    }
}
