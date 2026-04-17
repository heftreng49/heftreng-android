package com.heftreng.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.google.firebase.FirebaseApp;

public class HeftApp extends Application {

    public static final String CHANNEL_ID_MAIN = "heftreng_main";
    public static final String CHANNEL_ID_COMMENTS = "heftreng_comments";
    public static final String CHANNEL_ID_LIKES = "heftreng_likes";
    public static final String CHANNEL_ID_FOLLOWS = "heftreng_follows";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // Ana kanal
            NotificationChannel main = new NotificationChannel(
                CHANNEL_ID_MAIN,
                "Heftreng Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            );
            main.setDescription("Genel bildirimler");
            main.enableVibration(true);
            nm.createNotificationChannel(main);

            // Yorumlar
            NotificationChannel comments = new NotificationChannel(
                CHANNEL_ID_COMMENTS,
                "Yorumlar",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            comments.setDescription("Yorum bildirimleri");
            nm.createNotificationChannel(comments);

            // Beğeniler
            NotificationChannel likes = new NotificationChannel(
                CHANNEL_ID_LIKES,
                "Beğeniler",
                NotificationManager.IMPORTANCE_LOW
            );
            likes.setDescription("Beğeni bildirimleri");
            nm.createNotificationChannel(likes);

            // Takipçiler
            NotificationChannel follows = new NotificationChannel(
                CHANNEL_ID_FOLLOWS,
                "Takipçiler",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            follows.setDescription("Takip bildirimleri");
            nm.createNotificationChannel(follows);
        }
    }
}
