package com.heftreng.app.model;

import com.google.firebase.Timestamp;

public class FeedPost {
    public String    id;
    public String    uid;
    public String    authorName;
    public String    authorPhoto;
    public String    text;
    public String    imageUrl;
    public long      likes;
    public int       cmtCount;
    public Timestamp ts;

    public FeedPost() {}
}
