package com.heftreng.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.heftreng.app.worker.SendReplyWorker

// ═══════════════════════════════════════════════════════════════
//  ReplyReceiver — Bildirimden doğrudan yanıt (Quick Reply)
//
//  GARANTİLİ SENARYO: Bu receiver artık Firestore ile HİÇ konuşmuyor.
//  Tek görevi SendReplyWorker'ı kuyruğa koymak — bu işlem anlıktır
//  ve asla asılı kalmaz (network/Firebase/App Check'e bağımlı değil).
//
//  Asıl mesaj gönderme işi WorkManager'a devredildi çünkü:
//  - BroadcastReceiver.goAsync() en fazla ~10sn yaşar.
//  - Uygulama tamamen kapalıyken (cold start) Firebase Auth/App Check/
//    Firestore SDK'larının ilk ısınması bu süreyi aşabilir — mesaj
//    hiç gönderilmeden receiver process'i öldürülür (bu tam olarak
//    "uygulama kapalıyken takılıyor" şikayetinin kök sebebiydi).
//  - WorkManager, process öldürülse bile işi sistem seviyesinde
//    GARANTİ olarak tekrar dener ve tamamlar; goAsync() sınırına
//    tabi değildir.
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
            ?: return

        val convId   = intent.getStringExtra(EXTRA_CONV_ID)   ?: return
        val toUid    = intent.getStringExtra(EXTRA_TO_UID)    ?: ""
        val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val ownerUid = intent.getStringExtra(EXTRA_OWNER_UID) ?: ""

        if (replyText.isBlank() || toUid.isBlank()) return

        // Anlık işlem — Firestore/network beklemez, asla asılı kalmaz.
        SendReplyWorker.enqueue(
            context = context.applicationContext,
            convId  = convId,
            toUid   = toUid,
            text    = replyText,
            notifId = notifId,
            ownerUid = ownerUid,
        )
    }
}
