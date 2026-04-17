package com.heftreng.app.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.heftreng.app.HeftApp;
import com.heftreng.app.R;
import com.heftreng.app.ui.MainActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class HeftMessagingService extends FirebaseMessagingService {

    private static final String TAG = "HeftFCM";
    private static final AtomicInteger notifCounter = new AtomicInteger(0);

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Yeni FCM token: " + token);
        // Token değiştiğinde Firestore'a kaydet
        // Bu işlemi uygulama açıkken MainActivity üzerinden yapıyoruz
        // Burada da yapabilirsiniz:
        // saveTokenToFirestore(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM mesajı alındı: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        RemoteMessage.Notification notif = remoteMessage.getNotification();

        // Başlık ve içerik belirle
        String title = data.getOrDefault("title", null);
        String body = data.getOrDefault("body", null);
        String url = data.getOrDefault("url", null);
        String type = data.getOrDefault("type", "general");
        String icon = data.getOrDefault("icon", null);
        String postId = data.getOrDefault("postId", null);
        String imageUrl = data.getOrDefault("image", null);

        // Notification object'ten fallback
        if (notif != null) {
            if (title == null) title = notif.getTitle();
            if (body == null) body = notif.getBody();
            if (imageUrl == null) imageUrl = notif.getImageUrl() != null
                ? notif.getImageUrl().toString() : null;
        }

        // Default değerler
        if (title == null) title = "Heftreng";
        if (body == null) body = "Yeni bir bildiriminiz var";

        showNotification(title, body, url, type, postId, imageUrl);
    }

    private void showNotification(String title, String body, String url,
                                   String type, String postId, String imageUrl) {
        // Tıklama intent'i
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (url != null) intent.putExtra("url", url);
        if (postId != null) intent.putExtra("postId", postId);

        int reqCode = notifCounter.incrementAndGet();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pendingIntent = PendingIntent.getActivity(this, reqCode, intent, flags);

        // Kanal seç (türe göre)
        String channelId = getChannelId(type);

        // Ses
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setColor(getResources().getColor(R.color.notification_color, null));

        // Büyük görsel varsa indir ve göster
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Bitmap bigPicture = downloadBitmap(imageUrl);
            if (bigPicture != null) {
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    .setSummaryText(body));
                builder.setLargeIcon(bigPicture);
            }
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(reqCode, builder.build());

        Log.d(TAG, "Bildirim gösterildi: " + title);
    }

    private String getChannelId(String type) {
        if (type == null) return HeftApp.CHANNEL_ID_MAIN;
        switch (type) {
            case "cmt":
            case "comment": return HeftApp.CHANNEL_ID_COMMENTS;
            case "like":
            case "favorite": return HeftApp.CHANNEL_ID_LIKES;
            case "follow":
            case "person_add": return HeftApp.CHANNEL_ID_FOLLOWS;
            default: return HeftApp.CHANNEL_ID_MAIN;
        }
    }

    private Bitmap downloadBitmap(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoInput(true);
            conn.connect();
            InputStream is = conn.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            conn.disconnect();
            return bmp;
        } catch (Exception e) {
            Log.w(TAG, "Görsel indirilemedi: " + e.getMessage());
            return null;
        }
    }
}
