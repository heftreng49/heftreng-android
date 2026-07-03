package com.heftreng.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.heftreng.app.R

// ═══════════════════════════════════════════════════════════════
//  ReplyReceiver — Bildirimden doğrudan yanıt (Quick Reply)
//
//  DÜZELTME: sendPush çağrısı artık fire-and-forget.
//  Önceki sürümde sendPush'un addOnCompleteListener'ı içinde
//  pendingResult.finish() çağrılıyordu — Cloud Function geç
//  yanıt verince goAsync() 10sn limitini aşıyor, Android
//  process'i öldürüyor, bildirim "gönderiliyor" ekranında
//  donup kalıyordu.
//
//  Yeni akış:
//    1. Firestore mesaj yaz
//    2. conversations doc güncelle
//    3. markNotificationSent → pendingResult.finish()  ← hızlı
//    4. sendPush fire-and-forget (sonucu önemsemiyoruz)
// ═══════════════════════════════════════════════════════════════
class ReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY    = "com.heftreng.app.ACTION_REPLY"
        const val KEY_REPLY_TEXT  = "key_reply_text"
        const val EXTRA_CONV_ID   = "extra_conv_id"
        const val EXTRA_TO_UID    = "extra_to_uid"
        const val EXTRA_NOTIF_ID  = "extra_notif_id"
        const val EXTRA_OWNER_UID = "extra_owner_uid"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()

        val convId   = intent.getStringExtra(EXTRA_CONV_ID)   ?: return
        val toUid    = intent.getStringExtra(EXTRA_TO_UID)    ?: ""
        val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val ownerUid = intent.getStringExtra(EXTRA_OWNER_UID) ?: ""

        if (replyText.isNullOrBlank() || toUid.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return

        val pendingResult = goAsync()

        // Çoklu hesap koruması
        if (ownerUid.isNotBlank() && ownerUid != uid) {
            markNotification(context, notifId, "Bu mesaj başka bir hesaba ait — o hesaba geç")
            pendingResult.finish()
            return
        }

        val firestore = FirebaseFirestore.getInstance()

        val msgData = mapOf(
            "senderUid" to uid,
            "text"      to replyText,
            "image_url" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "read"      to false,
            "deleted"   to false,
            "edited"    to false,
        )

        firestore.collection("convMessages").document(convId)
            .collection("msgs").add(msgData)
            .addOnSuccessListener {
                val convUpd = mapOf(
                    "last_msg"      to replyText,
                    "updated_at"    to FieldValue.serverTimestamp(),
                    "unread_$toUid" to FieldValue.increment(1),
                    "unread_$uid"   to 0L,
                )
                firestore.collection("conversations").document(convId)
                    .set(convUpd, SetOptions.merge())
                    .addOnCompleteListener {
                        // ✅ Firestore tamamlandı → hemen bildirim güncelle + finish
                        markNotificationSent(context, notifId, replyText)
                        pendingResult.finish()

                        // Push: fire-and-forget, finish() beklemiyoruz
                        // (Cloud Function geç yanıt verse bile kullanıcıyı etkilemez)
                        try {
                            val myName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Biri"
                            FirebaseFunctions.getInstance("europe-west1")
                                .getHttpsCallable("sendPush")
                                .call(hashMapOf(
                                    "targetUid" to toUid,
                                    "title"     to myName,
                                    "body"      to replyText,
                                    "type"      to "message",
                                    "convId"    to convId,
                                    "fromUid"   to uid,
                                    "postId"    to "",
                                ))
                            // addOnCompleteListener YOK — fire-and-forget
                        } catch (_: Exception) {}
                    }
            }
            .addOnFailureListener {
                markNotification(context, notifId, "Mesaj gönderilemedi, uygulamayı açıp tekrar dene")
                pendingResult.finish()
            }
    }

    private fun markNotificationSent(context: Context, notifId: Int, replyText: String) {
        if (notifId == -1) return
        NotificationManagerCompat.from(context).notify(
            notifId,
            NotificationCompat.Builder(context, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Sen")
                .setContentText(replyText)
                .setAutoCancel(true)
                .setColor(0xFF8B5CF6.toInt())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    private fun markNotification(context: Context, notifId: Int, msg: String) {
        if (notifId == -1) return
        NotificationManagerCompat.from(context).notify(
            notifId,
            NotificationCompat.Builder(context, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentText(msg)
                .setAutoCancel(true)
                .setColor(0xFF8B5CF6.toInt())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }
}
