package com.heftreng.app.ads

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kullanıcı başına günlük reklam frekansı — Firestore'da tutulur.
 *
 * Koleksiyon: user_ad_frequency
 * Döküman ID: {uid}
 * Yapı:
 *   {
 *     "rewarded_2025-06-21": 2,   // bugün kaç rewarded izledi
 *     "interstitial_2025-06-21": 1
 *   }
 *
 * Eski tarihler otomatik olarak silinmez (Firestore TTL ile silebilirsin)
 * ama sorgu sadece bugünün key'ini okur — eski veriler etkisiz kalır.
 */
class AdFrequencyManager(
    private val firestore: FirebaseFirestore,
) {
    private val col = firestore.collection("user_ad_frequency")
    private val dateKey get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Bugün bu reklam türünden kaç kez gösterildi */
    suspend fun getCount(uid: String, adType: String): Int {
        return try {
            val doc = col.document(uid).get().await()
            (doc.getLong("${adType}_$dateKey") ?: 0L).toInt()
        } catch (_: Exception) { 0 }
    }

    /** Gösterimden sonra sayacı artır */
    suspend fun increment(uid: String, adType: String) {
        try {
            val key = "${adType}_$dateKey"
            col.document(uid).set(
                mapOf(key to com.google.firebase.firestore.FieldValue.increment(1)),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) { /* sessizce geç — kritik değil */ }
    }

    /** Bugün limit dolmuş mu */
    suspend fun isLimitReached(uid: String, adType: String, limit: Int): Boolean {
        if (limit <= 0) return false
        return getCount(uid, adType) >= limit
    }
}
