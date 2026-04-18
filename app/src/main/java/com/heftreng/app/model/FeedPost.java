package com.heftreng.app.model;

import com.google.firebase.Timestamp;
import java.util.List;

public class FeedPost {
    public String id;
    public String uid;
    public String name;
    public String photoURL;
    public String text;
    public String imgUrl;
    public String ytVid;
    public int likes;
    public int saves;
    public int cmtCount;
    public Timestamp ts;
    public List<String> likedBy;
    public FeedPost() {}
}
