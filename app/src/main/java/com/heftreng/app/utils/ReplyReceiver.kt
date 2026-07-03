package com.heftreng.app.utils

import android.app.NotificationManager
import android.app.PendingIntent
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
//  Bildirimdeki "Yanıtla" aksiyonundan gelen RemoteInput metnini alır,
//  Firestore'a yazar ve bildirimi "gönderildi" durumuna günceller.
//  Hilt inject burada kullanılamadığından (BroadcastReceiver Hilt
//  entry point değil) FirebaseAuth/Firestore doğrudan instance
//  üzerinden alınır — MessagesViewModel.sendMessage()'in sadeleştirilmiş
//  senkron/coroutine'siz bir eşleniği.
// ═══════════════════════════════════════════════════════════════
class ReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY   = "com.heftreng.app.ACTION_REPLY"
        const val KEY_REPLY_TEXT = "key_reply_text"
        const val EXTRA_CONV_ID  = "extra_conv_id"
        const val EXTRA_TO_UID   = "extra_to_uid"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_OWNER_UID = "extra_owner_uid"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()

        val convId  = intent.getStringExtra(EXTRA_CONV_ID) ?: return
        val toUid   = intent.getStringExtra(EXTRA_TO_UID) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val ownerUid = intent.getStringExtra(EXTRA_OWNER_UID) ?: ""

        if (replyText.isNullOrBlank() || toUid.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return

        // Çoklu hesap koruması: bildirim başka bir hesaba aitse ve cihazda
        // şu an farklı bir hesap açıksa, yanıtı YANLIŞ hesaptan göndermemek
        // için burada durduruyoruz. Kullanıcı doğru hesaba geçip uygulama
        // içinden yanıtlamalı.
        if (ownerUid.isNotBlank() && ownerUid != uid) {
            markNotificationWrongAccount(context, notifId)
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

                // Push bildirimi karşı tarafa gönder (best-effort, hata sessizce yutulur)
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
                } catch (_: Exception) { /* best-effort */ }

                markNotificationSent(context, notifId, replyText)
            }
            .addOnFailureListener {
                markNotificationFailed(context, notifId)
            }
    }

    // Yanıt başarıyla gönderildikten sonra bildirimi günceller —
    // WhatsApp'taki gibi "Sen: <mesaj>" şeklinde anlık geri bildirim.
    private fun markNotificationSent(context: Context, notifId: Int, replyText: String) {
        if (notifId == -1) return
        val builder = NotificationCompat.Builder(context, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentText("Sen: $replyText")
            .setAutoCancel(true)
            .setColor(0xFF8B5CF6.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    private fun markNotificationFailed(context: Context, notifId: Int) {
        if (notifId == -1) return
        val builder = NotificationCompat.Builder(context, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentText("Mesaj gönderilemedi, uygulamayı açıp tekrar dene")
            .setAutoCancel(true)
            .setColor(0xFF8B5CF6.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    // Bildirim başka bir hesaba aitken cihazda farklı bir hesap açıksa —
    // yanıtı sessizce yanlış hesaptan göndermek yerine kullanıcıyı uyar.
    private fun markNotificationWrongAccount(context: Context, notifId: Int) {
        if (notifId == -1) return
        val builder = NotificationCompat.Builder(context, HeftrangMessagingService.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentText("Bu mesaj başka bir hesaba ait — yanıtlamak için o hesaba geç")
            .setAutoCancel(true)
            .setColor(0xFF8B5CF6.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }
}
