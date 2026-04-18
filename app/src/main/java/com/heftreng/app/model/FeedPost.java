package com.heftreng.app.model;
import com.google.firebase.Timestamp;
import java.util.List;
public class FeedPost {
    public String id, authorId, authorName, authorPhoto, content, imageUrl, type;
    public int likeCount, commentCount;
    public List<String> likedBy;
    public Timestamp createdAt;
    public FeedPost() {}
}
