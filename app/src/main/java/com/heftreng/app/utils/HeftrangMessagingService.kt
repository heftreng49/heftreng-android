package com.heftreng.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.heftreng.app.MainActivity
import com.heftreng.app.R

class HeftrangMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID_DEFAULT  = "heftreng_default"
        const val CHANNEL_ID_MESSAGES = "heftreng_messages"
        const val CHANNEL_ID_LIKES    = "heftreng_likes"
        const val CHANNEL_ID_DAILY    = "heftreng_daily"   // Günün Alıntısı / Kelimesi

        // Sabit ID'ler — her gün tek bildirim, üst üste yazar
        const val NOTIF_ID_DAILY_QUOTE = 9001
        const val NOTIF_ID_DAILY_WORD  = 9002

        // ── Aktif ekran takibi ─────────────────────────────────────
        // MessagesScreen açıkken "true" set eder → mesaj bildirimi bastırılır
        @Volatile var isMessagesScreenActive: Boolean = false

        // Kullanıcı henüz giriş yapmamışsa token'ı burada sakla;
        // AuthViewModel login sonrasında bu değeri okuyup Firestore'a yazar.
        private const val PREFS_NAME  = "hf_prefs"
        private const val KEY_PENDING_TOKEN = "pending_fcm_token"

        fun savePendingToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING_TOKEN, token).apply()
        }

        fun consumePendingToken(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_PENDING_TOKEN, null)
            if (token != null) prefs.edit().remove(KEY_PENDING_TOKEN).apply()
            return token
        }
    }

    // ── Token yenilenince Firestore'a kaydet ──────────────────────────────────
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            saveTokenToFirestore(uid, token)
        } else {
            // Kullanıcı giriş yapmamış — token'ı beklet
            savePendingToken(applicationContext, token)
        }
    }

    private fun saveTokenToFirestore(uid: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update(mapOf(
                "fcmToken"     to token,
                "fcmUpdatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    // ── Gelen bildirim ────────────────────────────────────────────────────────
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Artık data-only payload — notification bloğu yok, her şey data'da
        val data    = message.data

        val title   = data["title"]   ?: "Heftreng"
        val body    = data["body"]    ?: ""
        val type    = data["type"]    ?: "default"
        val postId  = data["postId"]  ?: ""
        val fromUid = data["fromUid"] ?: ""
        val convId  = data["convId"]  ?: ""

        // Mesajlar ekranı açıksa mesaj bildirimini bastır
        if (type == "message" && isMessagesScreenActive) return

        // Bildirim tipine göre deep link intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (type) {
                "like", "cmt", "comment", "mention", "repost" -> {
                    if (postId.isNotBlank()) putExtra("navigate_to", "post/$postId")
                    else putExtra("navigate_to", "notifications")
                }
                "follow" -> {
                    if (fromUid.isNotBlank()) putExtra("navigate_to", "profile/$fromUid")
                    else putExtra("navigate_to", "notifications")
                }
                "message" -> {
                    if (convId.isNotBlank()) putExtra("navigate_to", "message/$convId")
                    else putExtra("navigate_to", "messages")
                }
                "daily_quote", "daily_word" -> {
                    // postId varsa orijinal alıntı/kelime paylaşımına git, yoksa bildirimler
                    if (postId.isNotBlank()) putExtra("navigate_to", "post/$postId")
                    else putExtra("navigate_to", "notifications")
                }
                else -> putExtra("navigate_to", "notifications")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = data["channelId"]?.takeIf { it.isNotBlank() } ?: when (type) {
            "message"                    -> CHANNEL_ID_MESSAGES
            "like", "repost"             -> CHANNEL_ID_LIKES
            "daily_quote", "daily_word"  -> CHANNEL_ID_DAILY
            else                         -> CHANNEL_ID_DEFAULT
        }

        ensureChannels()

        // Bildirim ID — mesaj ve takip aynı kişiden gelirse üst üste yaz
        val notifId = when {
            type == "message"   && convId.isNotBlank()   -> convId.hashCode()
            type == "follow"    && fromUid.isNotBlank()  -> ("follow_$fromUid").hashCode()
            type == "daily_quote"                        -> NOTIF_ID_DAILY_QUOTE
            type == "daily_word"                         -> NOTIF_ID_DAILY_WORD
            else -> System.currentTimeMillis().toInt()
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF8B5CF6.toInt())
            .setLights(0xFF8B5CF6.toInt(), 500, 500)
            .setVibrate(longArrayOf(0, 250, 100, 250))

        when (type) {
            "daily_quote" -> {
                // Alıntı tam metin olarak büyük kutuda göster
                notificationBuilder
                    .setColor(0xFF8B5CF6.toInt())
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(body)
                            .setSummaryText("📖 Günün Alıntısı")
                    )
            }
            "daily_word" -> {
                notificationBuilder
                    .setColor(0xFF0EA5E9.toInt())
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(body)
                            .setSummaryText("📝 Günün Kelimesi")
                    )
            }
            "message" -> {
                // Aynı konuşma gruplanır, sadece ilk bildirimde ses
                notificationBuilder
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setGroup("conv_$convId")
                    .setOnlyAlertOnce(true)
            }
            else -> {
                notificationBuilder
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notificationBuilder.build())
    }

    // ── Bildirim kanallarını oluştur ──────────────────────────────────────────
    private fun ensureChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            Triple(CHANNEL_ID_DEFAULT,  "Genel Bildirimler",           NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_ID_MESSAGES, "Mesajlar",                    NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_ID_LIKES,    "Beğeni & Repost",             NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_ID_DAILY,    "Günün Alıntısı & Kelimesi",   NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { (id, name, importance) ->
            val channel = NotificationChannel(id, name, importance).apply {
                enableLights(true)
                lightColor = Color.rgb(139, 92, 246)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
