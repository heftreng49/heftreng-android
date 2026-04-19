package com.heftreng.app.model;

import com.google.firebase.Timestamp;

/**
 * Firestore "userNotifs/{uid}/msgs" subcollection belge yapısı.
 * Global "notifications" koleksiyonuyla aynı field yapısını kullanır.
 * Tema field adları: type, title, sub, ico, read, ts,
 *                   feedId, postId, fromUid, fromName, fromPhoto
 */
public class HeftNotification {
    public String    id;
    public String    type;      // "like", "cmt", "follow", "sys", "post_approved", "post_rejected"
    public String    title;
    public String    sub;       // tema: sub (message değil)
    public String    ico;       // Material Icons adı
    public boolean   read;
    public Timestamp ts;        // tema: ts (createdAt değil)
    public String    feedId;
    public String    postId;
    public String    fromUid;
    public String    fromName;
    public String    fromPhoto;

    public HeftNotification() {}
}
