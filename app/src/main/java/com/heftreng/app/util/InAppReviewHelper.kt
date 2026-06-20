package com.heftreng.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Play Store'un native "puan ver + yorum yaz" kutusunu (aşağıdan açılan kart) yönetir.
 *
 * NOT: Google Play, bu kutunun ne zaman/ne sıklıkla gösterileceğini kendi içinde
 * kotasıyla belirler — biz `launchReviewFlow` çağırsak da garanti gösterilmez
 * (kullanıcı zaten yakın zamanda görmüşse Play sessizce hiçbir şey yapmaz).
 * Bu yüzden:
 *  - [maybeRequestReview] → fırsatçı/otomatik tetikleme (uygulamayı biraz kullandıktan
 *    sonra, kendi basit aç-sayısı/cooldown kontrolümüzle, spam olmasın diye).
 *  - [requestReviewNow]   → Ayarlar'daki "Bizi Değerlendir" gibi MANUEL bir tıklama
 *    sonrası çağrılır. Play kotası dolu olup kutu çıkmasa bile kullanıcı boşa
 *    tıklamış olmasın diye, kutu çıkmazsa doğrudan Play Store sayfasına yönlendirir.
 */
object InAppReviewHelper {

    private const val PREFS        = "heftreng_review_prefs"
    private const val KEY_OPEN_CNT = "open_count"
    private const val KEY_LAST_TS  = "last_prompt_ts"
    private const val KEY_ASKED    = "ever_asked"

    private const val MIN_OPENS_BEFORE_ASK = 4          // ilk istemden önce en az 4. açılış
    private const val COOLDOWN_MS = 1000L * 60 * 60 * 24 * 60 // 60 gün

    /** Uygulama her açıldığında (MainActivity#onCreate) çağrılır — sayaç burada artar. */
    fun maybeRequestReview(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val openCount = prefs.getInt(KEY_OPEN_CNT, 0) + 1
        prefs.edit().putInt(KEY_OPEN_CNT, openCount).apply()

        if (openCount < MIN_OPENS_BEFORE_ASK) return

        val lastTs = prefs.getLong(KEY_LAST_TS, 0L)
        val now    = System.currentTimeMillis()
        if (lastTs != 0L && now - lastTs < COOLDOWN_MS) return

        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (!request.isSuccessful) {
                    Log.d("InAppReview", "requestReviewFlow başarısız: ${request.exception?.message}")
                    return@addOnCompleteListener
                }
                val reviewInfo = request.result
                manager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                    // Play kutuyu gösterip göstermediğini bize bildirmez (tasarım gereği) —
                    // ama denediğimiz an cooldown'u başlatıyoruz, spam olmasın.
                    prefs.edit()
                        .putLong(KEY_LAST_TS, now)
                        .putBoolean(KEY_ASKED, true)
                        .apply()
                }
            }
        } catch (e: Exception) {
            Log.w("InAppReview", "maybeRequestReview hata: ${e.message}")
        }
    }

    /** Ayarlar ekranındaki "Bizi Değerlendir" butonu — manuel, garanti bir aksiyon üretir. */
    fun requestReviewNow(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    manager.launchReviewFlow(activity, request.result)
                } else {
                    openPlayStoreListing(activity)
                }
            }
        } catch (e: Exception) {
            Log.w("InAppReview", "requestReviewNow hata: ${e.message}")
            openPlayStoreListing(activity)
        }
    }

    /** In-app review kutusu çıkmazsa ya da hata olursa — doğrudan Play Store sayfası. */
    private fun openPlayStoreListing(context: Context) {
        val pkg = context.packageName
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }
}
