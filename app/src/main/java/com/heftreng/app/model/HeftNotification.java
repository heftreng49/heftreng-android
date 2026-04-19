package com.heftreng.app.model;
import com.google.firebase.Timestamp;
public class HeftNotification {
    public String id, text, title, sub, type;
    public String fromUid, fromName, fromPhoto, postId;
    public Boolean read;
    public Timestamp ts;
    public HeftNotification() {}
}
