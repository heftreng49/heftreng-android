package com.heftreng.app.model;

import com.google.firebase.Timestamp;
import java.util.List;

/**
 * Firestore "pendingPosts" koleksiyonundaki belge yapısı.
 * Tema ile uyumlu field adları: authorId, authorName, authorEmail,
 * title, content, summary, cover, category, lang, status, createdAt
 */
public class BlogPost {
    public String id;
    public String authorId;       // tema: authorId
    public String authorName;     // tema: authorName
    public String authorEmail;    // tema: authorEmail
    public String authorPhoto;    // yerel, Firestore users'dan çekiliyor
    public String title;
    public String summary;
    public String content;
    public String cover;          // tema: cover (coverUrl değil)
    public String category;
    public String lang;           // "tr" veya "ku"
    public String status;         // "pending", "approved", "rejected"
    public String adminNote;
    public String bloggerPostId;
    public String bloggerPostUrl;
    public int    likeCount;
    public int    commentCount;
    public int    readTimeMinutes;
    public List<String> likedBy;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    public BlogPost() {}
}
