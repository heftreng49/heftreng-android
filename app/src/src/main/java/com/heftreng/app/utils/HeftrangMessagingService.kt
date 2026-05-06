package com.heftreng.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
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
    }

    // ── Token yenilenince Firestore'a kaydet ──────────────────────────────────
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update(mapOf(
                "fcmToken"     to token,
                "fcmUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            ))
    }

    // ── Gelen bildirim ────────────────────────────────────────────────────────
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data     = message.data
        val notif    = message.notification

        val title    = notif?.title ?: data["title"] ?: "Heftreng"
        val body     = notif?.body  ?: data["body"]  ?: ""
        val type     = data["type"] ?: "default"
        val postId   = data["postId"] ?: ""
        val fromUid  = data["fromUid"] ?: ""
        val convId   = data["convId"] ?: ""

        // Bildirim tipine göre deep link intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (type) {
                "like", "comment", "repost" -> {
                    if (postId.isNotBlank()) putExtra("navigate_to", "post/$postId")
                }
                "follow" -> {
                    if (fromUid.isNotBlank()) putExtra("navigate_to", "profile/$fromUid")
                }
                "message" -> {
                    if (convId.isNotBlank()) putExtra("navigate_to", "message/$convId")
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

        // Bildirim tipine göre kanal
        val channelId = when (type) {
            "message"             -> CHANNEL_ID_MESSAGES
            "like", "repost"      -> CHANNEL_ID_LIKES
            else                  -> CHANNEL_ID_DEFAULT
        }

        ensureChannels()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF8B5CF6.toInt())
            .setLights(0xFF8B5CF6.toInt(), 500, 500)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ── Bildirim kanallarını oluştur ──────────────────────────────────────────
    private fun ensureChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        listOf(
            Triple(CHANNEL_ID_DEFAULT,  "Genel Bildirimler",  NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_ID_MESSAGES, "Mesajlar",           NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_ID_LIKES,    "Beğeni & Repost",    NotificationManager.IMPORTANCE_DEFAULT),
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
