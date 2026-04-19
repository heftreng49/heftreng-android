package com.heftreng.app.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.heftreng.app.R;
import com.heftreng.app.ui.MainActivity;

import java.util.HashMap;
import java.util.Map;

public class HeftMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "heftreng_notifications";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Tema: users koleksiyonunda fcmToken field adı
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("fcmToken", token); // tema ile aynı field adı
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .set(data, SetOptions.merge());
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        createNotificationChannel();

        String title = message.getNotification() != null
            ? message.getNotification().getTitle() : "Heftreng";
        String body = message.getNotification() != null
            ? message.getNotification().getBody() : "";

        // Data payload'dan da al (tema push notification yapısı)
        if (message.getData().containsKey("title"))
            title = message.getData().get("title");
        if (message.getData().containsKey("body"))
            body = message.getData().get("body");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager mgr =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null)
            mgr.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Heftreng Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Beğeni, yorum ve takip bildirimleri");
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }
}
