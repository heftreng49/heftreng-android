package com.heftreng.app.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.heftreng.app.R
import com.heftreng.app.utils.HeftrangMessagingService
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════
//  SendReplyWorker — Bildirimden gelen "Quick Reply" yanıtını
//  GARANTİLİ şekilde gönderir.
//
//  NEDEN BroadcastReceiver İÇİNDE DEĞİL:
//  ReplyReceiver.goAsync() en fazla ~10sn yaşar. Uygulama tamamen
//  kapalıyken (cold start) Firestore/App Check/Auth SDK'larının ilk
//  ısınması (Play Integrity token alımı, ağ el sıkışması vb.) bu
//  süreyi kolayca aşabilir — bu durumda mesaj hiç gönderilmeden
//  receiver process'i öldürülür.
//
//  WorkManager bu sorunu kökten çözer: iş bir kez kuyruklandıktan
//  sonra sistem, process öldürülse bile (kısıtlı ağ, Doze, cold
//  start fark etmez) işi arka planda GARANTİ olarak tekrar dener ve
//  tamamlar. ReplyReceiver'ın tek görevi artık bu işi kuyruğa
//  koymak — bu adım anlık ve asla asılı kalmaz.
// ═══════════════════════════════════════════════════════════════
class SendReplyWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val KEY_CONV_ID   = "conv_id"
        private const val KEY_TO_UID    = "to_uid"
        private const val KEY_TEXT      = "text"
        private const val KEY_NOTIF_ID  = "notif_id"
        private const val KEY_OWNER_UID = "owner_uid"

        fun enqueue(
            context: Context,
            convId: String,
            toUid: String,
            text: String,
            notifId: Int,
            ownerUid: String,
        ) {
            val data = workDataOf(
                KEY_CONV_ID   to convId,
                KEY_TO_UID    to toUid,
                KEY_TEXT      to text,
                KEY_NOTIF_ID  to notifId,
                KEY_OWNER_UID to ownerUid,
            )
            val request = OneTimeWorkRequestBuilder<SendReplyWorker>()
                .setInputData(data)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.LINEAR,
                    10, java.util.concurrent.TimeUnit.SECONDS,
                )
                .build()
            // convId+notifId ile unique work adı: aynı bildirime art arda
            // hızlı yanıt basılırsa mükerrer gönderim olmasın.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "reply_${convId}_$notifId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        val convId   = inputData.getString(KEY_CONV_ID)   ?: return Result.failure()
        val toUid    = inputData.getString(KEY_TO_UID)    ?: ""
        val text     = inputData.getString(KEY_TEXT)      ?: ""
        val notifId  = inputData.getInt(KEY_NOTIF_ID, -1)
        val ownerUid = inputData.getString(KEY_OWNER_UID) ?: ""

        if (text.isBlank() || toUid.isBlank()) return Result.failure()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            // Auth henüz hazır değilse (cold start ihtimali) — yeniden dene,
            // WorkManager kendi backoff'uyla tekrar çağıracak.
            return Result.retry()
        }

        if (ownerUid.isNotBlank() && ownerUid != uid) {
            markNotification(notifId, "Bu mesaj başka bir hesaba ait — o hesaba geç")
            return Result.failure()
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()

            val msgData = mapOf(
                "senderUid" to uid,
                "text"      to text,
                "image_url" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "read"      to false,
                "deleted"   to false,
                "edited"    to false,
            )
            firestore.collection("convMessages").document(convId)
                .collection("msgs").add(msgData).await()

            val convUpd = mapOf(
                "last_msg"      to text,
                "updated_at"    to FieldValue.serverTimestamp(),
                "unread_$toUid" to FieldValue.increment(1),
                "unread_$uid"   to 0L,
            )
            firestore.collection("conversations").document(convId)
                .set(convUpd, SetOptions.merge()).await()

            markNotificationSent(notifId, text)

            // Push bildirimi: en iyi çaba (best-effort). Başarısız olsa da
            // asıl mesaj zaten gönderildi — Worker'ı başarısız saymıyoruz.
            try {
                val myName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Biri"
                FirebaseFunctions.getInstance("europe-west1")
                    .getHttpsCallable("sendPush")
                    .call(hashMapOf(
                        "targetUid" to toUid,
                        "title"     to myName,
                        "body"      to text,
                        "type"      to "message",
                        "convId"    to convId,
                        "fromUid"   to uid,
                        "postId"    to "",
                    )).await()
            } catch (_: Exception) { /* best-effort, mesaj zaten gönderildi */ }

            Result.success()
        } catch (e: Exception) {
            // Ağ hatası vb. — WorkManager otomatik yeniden dener (LINEAR backoff).
            // runAttemptCount belirli bir sayıyı aşarsa WorkManager kendisi durdurur.
            if (runAttemptCount >= 5) {
                markNotification(notifId, "Mesaj gönderilemedi, uygulamayı açıp tekrar dene")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private fun markNotificationSent(notifId: Int, text: String) {
        if (notifId == -1) return
        NotificationManagerCompat.from(ctx).notify(
            notifId,
            NotificationCompat.Builder(ctx, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Sen")
                .setContentText(text)
                .setAutoCancel(true)
                .setColor(0xFF8B5CF6.toInt())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    private fun markNotification(notifId: Int, msg: String) {
        if (notifId == -1) return
        NotificationManagerCompat.from(ctx).notify(
            notifId,
            NotificationCompat.Builder(ctx, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentText(msg)
                .setAutoCancel(true)
                .setColor(0xFF8B5CF6.toInt())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }
}
