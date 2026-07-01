package com.heftreng.app.ads

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kullanıcı başına günlük reklam frekansı (rewarded XP limiti vb.)
 *
 * TAMAMEN YEREL — Firestore yok, ağ isteği yok.
 * Sayaç SharedPreferences'ta tutulur; kontrol anında (0 gecikme).
 *
 * Kural: "Firestore kullanmamalı" — analitik yazma da kaldırıldı.
 * Rewarded limit kontrolü zaten yerel; sunucu tarafı doğrulama
 * SSV (Server-Side Verification) ile AdMob tarafında yapılıyor.
 */
@Singleton
class AdFrequencyManager @Inject constructor(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("ad_frequency", Context.MODE_PRIVATE)

    private val todayKey get() =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun localKey(uid: String, adType: String) = "${uid}_${adType}_$todayKey"

    /** Bugün bu reklam türünden kaç kez gösterildi — anında, ağ yok. */
    fun getCount(uid: String, adType: String): Int =
        prefs.getInt(localKey(uid, adType), 0)

    /** Bugün limit dolmuş mu — anında, ağ yok. */
    fun isLimitReached(uid: String, adType: String, limit: Int): Boolean {
        if (limit <= 0) return false
        return getCount(uid, adType) >= limit
    }

    /** Gösterimden sonra sayacı artır — anında, ağ yok. */
    fun increment(uid: String, adType: String) {
        if (uid.isBlank()) return
        val key = localKey(uid, adType)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
}
