package com.heftreng.app.model;
import com.google.firebase.Timestamp;
public class HeftNotification {
    public String id, text, type, fromUid, fromName, postId;
    public Boolean read;
    public Timestamp ts;
    public HeftNotification() {}
}
