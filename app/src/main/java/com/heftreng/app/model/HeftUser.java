package com.heftreng.app.model;

/**
 * Firestore "users" koleksiyonundaki belge yapısı.
 * Tema (Blogger XML) ile birebir eşleşen field adları kullanılmaktadır.
 */
public class HeftUser {
    // Tema field adları
    public String uid;
    public String name;         // tema: name (displayName değil)
    public String photoURL;     // tema: photoURL (photoUrl değil)
    public String username;
    public String bio;
    public String email;
    public int    postCount;
    public int    followerCount;
    public int    followingCount;

    public HeftUser() {}
}
