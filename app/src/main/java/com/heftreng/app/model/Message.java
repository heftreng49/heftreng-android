package com.heftreng.app.model;
public class Message {
    public long id;
    public String convId, fromUid, toUid, text, imageUrl, ts, replyToId, replyToText, replyToName;
    public boolean isDeleted;
    public boolean isMine(String myUid) { return myUid != null && myUid.equals(fromUid); }
}
