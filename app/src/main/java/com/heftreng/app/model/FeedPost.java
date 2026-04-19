package com.heftreng.app.model;

import com.google.firebase.Timestamp;
import java.util.List;

/**
 * Firestore "feed" koleksiyonundaki belge yapısı.
 * Tema (Blogger XML) ile birebir eşleşen field adları kullanılmaktadır.
 */
public class FeedPost {
    // ── Temel alanlar (tema ile eşleşiyor) ──────────────────────────────
    public String id;           // document ID
    public String uid;          // yazar Firebase UID
    public String name;         // yazar görünen adı
    public String photoURL;     // yazar profil fotoğrafı
    public String text;         // gönderi metni
    public String imgUrl;       // gönderi görseli (opsiyonel)
    public String category;     // kategori / etiket
    public int    likes;        // beğeni sayısı
    public int    cmtCount;     // yorum sayısı
    public Timestamp ts;        // oluşturulma zamanı

    // likedBy dizisi — beğeni toggle için
    public List<String> likedBy;

    public FeedPost() {}
}
