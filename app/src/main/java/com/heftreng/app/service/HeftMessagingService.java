package com.heftreng.app.service;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
public class HeftMessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage r){}
    @Override public void onNewToken(String t){}
}
