package com.heftreng.app.model;
import java.util.List;
public class HeftUser {
    public String uid, name, username, bio, photoURL, coverURL, email;
    public int postCount, followerCount, followingCount, xp;
    public List<String> badges;
    public boolean isAdmin, isWriter;
    public HeftUser() {}
}
