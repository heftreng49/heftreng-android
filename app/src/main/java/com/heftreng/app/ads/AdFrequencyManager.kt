package com.heftreng.app.ads

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kullanıcı başına günlük reklam frekansı (örn. rewarded XP limiti).
 *
 * ÖNCEKİ TASARIM: her "izlenebilir mi?" kontrolünde Firestore'a SENKRON gidip
 * sunucudan okuyordu — kullanıcı "reklam izle" butonuna her bastığında reklam
 * gösterilmeden önce bir ağ round-trip'i bekleniyordu (yavaş, gereksiz).
 *
 * YENİ TASARIM: sayaç SharedPreferences'ta YEREL tutulur — kontrol ANINDA
 * (0 gecikme, ağ yok). Firestore'a yazma sadece analitik/çapraz-cihaz takibi
 * için arka planda, "fire and forget" şeklinde yapılır; hiçbir şeyi bloklamaz.
 * Bu, normal uygulamaların (sektör standardı) frekans/limit yönetimini yaptığı
 * yöntemdir — sunucu sadece ikincil/analitik kayıt, asıl karar yerelde.
 */
class AdFrequencyManager(
    context        : Context,
    private val firestore: FirebaseFirestore,
    private val scope     : CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("ad_frequency", Context.MODE_PRIVATE)
    private val dateKey get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun localKey(uid: String, adType: String) = "${uid}_${adType}_$dateKey"

    /** Bugün bu reklam türünden kaç kez gösterildi — YEREL, anında, ağ yok. */
    fun getCount(uid: String, adType: String): Int =
        prefs.getInt(localKey(uid, adType), 0)

    /** Bugün limit dolmuş mu — YEREL, anında, ağ yok. */
    fun isLimitReached(uid: String, adType: String, limit: Int): Boolean {
        if (limit <= 0) return false
        return getCount(uid, adType) >= limit
    }

    /**
     * Gösterimden sonra sayacı artır. Yerel sayaç ANINDA güncellenir (UI bunu
     * hemen kullanabilir). Firestore'a yazma arka planda, beklemeden yapılır —
     * sadece analitik/çapraz-cihaz senkronizasyonu için, kritik değil.
     */
    fun increment(uid: String, adType: String) {
        if (uid.isBlank()) return
        val key = localKey(uid, adType)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()

        // Arka planda, beklemeden — analitik amaçlı. Başarısız olsa da önemli değil.
        scope.launch {
            try {
                val docKey = "${adType}_$dateKey"
                firestore.collection("user_ad_frequency").document(uid).set(
                    mapOf(docKey to com.google.firebase.firestore.FieldValue.increment(1)),
                    SetOptions.merge()
                ).await()
            } catch (_: Exception) { /* sessizce geç — kritik değil */ }
        }
    }
}
