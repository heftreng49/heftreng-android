package com.heftreng.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.heftreng.app.MainActivity
import com.heftreng.app.R
import java.util.concurrent.TimeUnit

class KurdiReminderWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    companion object {
        const val CHANNEL_ID      = "kurdi_reminder"
        const val WORK_NAME       = "kurdi_daily_reminder"
        const val PREF_NAME       = "kurdi_prefs"
        const val KEY_LAST_LESSON = "last_lesson_ts"

        private val messages = listOf(
            Pair("Îro dersên te li bendê ne! 📖",    "Bugün derslerin seni bekliyor! 📖"),
            Pair("Zincîra xwe neke! 🔥",             "Zincirini kırma! 🔥"),
            Pair("Her roj hinekî fêr bibe 🌱",       "Her gün biraz öğren 🌱"),
            Pair("Kurdî ji bîr neke! ✨",             "Kürtçeyi unutma! ✨"),
            Pair("Derseke kurt bes e! ⚡",            "Kısa bir ders yeter! ⚡"),
            Pair("Pêşve here, tu dikarî! 💜",        "İlerlemeye devam et, yapabilirsin! 💜"),
        )

        fun schedule(context: Context, hourOfDay: Int = 20) {
            createChannel(context)
            val now    = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis
            val req = PeriodicWorkRequestBuilder<KurdiReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req,
            )
        }

        fun cancel(context: Context) =
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        fun recordLessonDone(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_LESSON, System.currentTimeMillis()).apply()
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Kurdî Hatırlatıcı",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Günlük Kurdî ders hatırlatması"
                    enableVibration(true)
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(ch)
            }
        }
    }

    override suspend fun doWork(): Result {
        val prefs      = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastLesson = prefs.getLong(KEY_LAST_LESSON, 0L)
        val hoursAgo   = (System.currentTimeMillis() - lastLesson) / 3_600_000L
        if (lastLesson > 0 && hoursAgo < 20) return Result.success()
        sendNotification()
        return Result.success()
    }

    private fun sendNotification() {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "kurdi")
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        val (ku, tr) = messages[day % messages.size]

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(tr)
            .setContentText(ku)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ku).setSummaryText("Kurdî Fêrbibe"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(0xFF7C3AED.toInt())
            .build()

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(42, notif)
    }
}
