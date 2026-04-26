package com.heftreng.app.data.model

import com.google.firebase.Timestamp

// ─── KULLANICI ─────────────────────────────────────────
data class User(
    val uid            : String = "",
    val displayName    : String = "",
    val name           : String = "",
    val username       : String = "",
    val email          : String = "",
    val photoURL       : String = "",
    val coverPhoto     : String = "",
    val bio            : String = "",
    val website        : String = "",
    val followersCount : Int    = 0,
    val followingCount : Int    = 0,
    val postsCount     : Int    = 0,
    val level          : Int    = 1,
    val xp             : Int    = 0,
    val streak         : Int    = 0,
    val banned         : Boolean= false,
)

// ─── FEED GÖNDERISI ────────────────────────────────────
// Firestore: feed/{postId}
// Alanlar: uid, displayName, username, photoURL, text,
//          imageURL, likes, saves, cmtCount, reposts,
//          quoteText, bookName, authorName, repostOf,
//          repostUid, ts
data class Post(
    val id            : String     = "",
    val uid           : String     = "",
    val displayName   : String     = "",
    val username      : String     = "",
    val photoURL      : String     = "",
    val text          : String     = "",
    val imageURL      : String     = "",
    val likesCount    : Int        = 0,
    val commentsCount : Int        = 0,
    val repostsCount  : Int        = 0,
    val ts            : Timestamp? = null,
    val quoteText     : String     = "",
    val bookName      : String     = "",
    val authorName    : String     = "",
    val repostOf      : String     = "",   // repost ise orijinal postId
    val repostUid     : String     = "",
    val isLikedByMe   : Boolean    = false,
    val isSavedByMe   : Boolean    = false,
)

// ─── YORUM ─────────────────────────────────────────────
// Firestore: feed/{postId}/comments/{cmtId}
// Alanlar: uid, displayName, photoURL, text, likes, ts
data class Comment(
    val id          : String     = "",
    val postId      : String     = "",
    val uid         : String     = "",
    val displayName : String     = "",
    val photoURL    : String     = "",
    val text        : String     = "",
    val likesCount  : Int        = 0,
    val replyTo     : ReplyTo?   = null,
    val ts          : Timestamp? = null,
)

data class ReplyTo(
    val uid         : String = "",
    val displayName : String = "",
)

// ─── BİLDİRİM ──────────────────────────────────────────
// Firestore: userNotifs/{uid}/msgs/{notifId}
// Alanlar: fromUid, fromName, fromPhoto, type,
//          postId, message, url, read, ts
data class Notification(
    val id        : String     = "",
    val userId    : String     = "",
    val fromUid   : String     = "",
    val fromName  : String     = "",
    val fromPhoto : String     = "",
    val type      : String     = "",   // like, comment, follow, repost
    val message   : String     = "",
    val postId    : String?    = null,
    val url       : String     = "",
    val read      : Boolean    = false,
    val ts        : Timestamp? = null,
)

// ─── MESAJ / KONUŞMA ───────────────────────────────────
// Supabase tabanlı — conversations + messages tabloları
data class Message(
    val id             : String  = "",
    val conversationId : String  = "",
    val senderId       : String  = "",
    val text           : String  = "",
    val createdAt      : String  = "",
    val read           : Boolean = false,
    val deleted        : Boolean = false,
)

data class Conversation(
    val id            : String  = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage   : String  = "",
    val lastMessageAt : String  = "",
    val otherUser     : User?   = null,
    val unreadCount   : Int     = 0,
)

// ─── SERİ (KİTAP/ROMAN) ────────────────────────────────
// Firestore: serials/{serialId}
// Alanlar: uid, name, photoURL, title, desc, genre,
//          coverImg, chapterCount, likes, ts, updatedAt
data class Serial(
    val id           : String     = "",
    val uid          : String     = "",
    val name         : String     = "",   // yazar displayName
    val photoURL     : String     = "",   // yazar fotoğrafı
    val title        : String     = "",
    val desc         : String     = "",
    val genre        : String     = "",
    val coverImg     : String     = "",
    val chapterCount : Int        = 0,
    val likes        : Int        = 0,
    val ts           : Timestamp? = null,
    val updatedAt    : Timestamp? = null,
    val isLikedByMe  : Boolean    = false,
)

// ─── BÖLÜM ─────────────────────────────────────────────
// Firestore: serials/{serialId}/chapters/{chapterId}
// Alanlar: serialId, title, body, order, wordCount, uid, ts
data class Chapter(
    val id       : String     = "",
    val serialId : String     = "",
    val title    : String     = "",
    val body     : String     = "",
    val order    : Int        = 0,
    val wordCount: Int        = 0,
    val uid      : String     = "",
    val ts       : Timestamp? = null,
)

// ─── OKUMA LİSTESİ ─────────────────────────────────────
// Firestore: readingLists/{uid}/books/{sid}
// Alanlar: sid, title, coverImg, bg, status, updatedAt
// status: okuyorum | okumak_istiyorum | okudum | biraktim
data class ReadingListEntry(
    val sid       : String     = "",
    val title     : String     = "",
    val coverImg  : String     = "",
    val bg        : String     = "",
    val status    : String     = "",
    val updatedAt : Timestamp? = null,
)

// ─── KURDİ DERS ────────────────────────────────────────
// Firestore: kf_lessons/{id}
data class KurdiLesson(
    val id        : String  = "",
    val title     : String  = "",
    val subtitle  : String  = "",
    val type      : String  = "",   // fill, mcq, build, match
    val xpReward  : Int     = 10,
    val completed : Boolean = false,
    val order     : Int     = 0,
)
