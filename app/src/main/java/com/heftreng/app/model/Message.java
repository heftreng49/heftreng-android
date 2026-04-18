package com.heftreng.app.model;

public class Message {
    public long   id;
    public String convId;
    public String fromUid;
    public String toUid;
    public String text;
    public String imageUrl;
    public String ts;
    public String replyToId;
    public String replyToText;
    public String replyToName;
    public boolean isDeleted;

    public boolean isMine(String myUid) {
        return myUid != null && myUid.equals(fromUid);
    }
}
