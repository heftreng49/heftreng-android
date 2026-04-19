package com.heftreng.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.util.List;

public class FeedPost {
    public String id;
    public String type;
    public String originalAuthor;

    // Firestore field: "uid" -> authorId
    @PropertyName("uid")
    public String authorId;

    // Firestore field: "name" -> authorName
    @PropertyName("name")
    public String authorName;

    // Firestore field: "photoURL" -> authorPhoto
    @PropertyName("photoURL")
    public String authorPhoto;

    // Firestore field: "text" -> content
    @PropertyName("text")
    public String content;

    // Firestore field: "imgUrl" -> imageUrl
    @PropertyName("imgUrl")
    public String imageUrl;

    // Firestore field: "likes" -> likeCount
    @PropertyName("likes")
    public int likeCount;

    // Yorum sayısı
    @PropertyName("cmtCount")
    public int cmtCount;

    // commentCount (alternatif field)
    @PropertyName("commentCount")
    public int commentCount;

    @PropertyName("likedBy")
    public List<String> likedBy;

    // Firestore field: "ts" -> createdAt
    @PropertyName("ts")
    public Timestamp createdAt;

    public FeedPost() {}
}
