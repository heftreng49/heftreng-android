package com.heftreng.app.model;

import com.google.firebase.Timestamp;
import java.util.List;

public class FeedPost {
    public String    id;
    public String    authorId;
    public String    authorName;
    public String    authorPhoto;
    public String    content;
    public String    imageUrl;
    public int       likeCount;
    public int       commentCount;
    public List<String> likedBy;
    public Timestamp createdAt;
    public String    type;

    public FeedPost() {}
}
