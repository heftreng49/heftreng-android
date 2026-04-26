package com.heftreng.app.data.model

import com.google.firebase.Timestamp

// ── Firestore: users/{uid} ────────────────────────────────
data class User(
    val uid           : String    = "",
    val displayName   : String    = "",
    val name          : String    = "",   // orijinal projede var
    val username      : String    = "",
    val email         : String    = "",
    val photoURL      : String    = "",
    val coverPhoto    : String    = "",
    val bio           : String    = "",
    val website       : String    = "",
    val followersCount: Int       = 0,
    val followingCount: Int       = 0,
    val postsCount    : Int       = 0,
    val level         : Int       = 1,
    val xp            : Int       = 0,
    val streak        : Int       = 0,
    val banned        : Boolean   = false,
)

// ── Firestore: feed/{postId} ──────────────────────────────
data class Post(
    val id           : String     = "",
    val uid          : String     = "",
    val text         : String     = "",
    val imageURL     : String     = "",
    val quoteText    : String     = "",
    val authorName   : String     = "",
    val bookName     : String     = "",
    val ts           : Timestamp? = null,
    // Sayaçlar (alan isimleri Firestore'dakiyle birebir: likes, saves, cmtCount, reposts)
    val likesCount   : Int        = 0,
    val commentsCount: Int        = 0,
    val repostsCount : Int        = 0,
    val savesCount   : Int        = 0,
    // UI durumu
    val isLikedByMe  : Boolean    = false,
    val isSavedByMe  : Boolean    = false,
    // Kullanıcı bilgisi
    val displayName  : String     = "",
    val username     : String     = "",
    val photoURL     : String     = "",
)

// ── Firestore: feed/{postId}/comments/{cmtId} ────────────
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
    val uid        : String = "",
    val displayName: String = "",
)

// ── Firestore: userNotifs/{uid}/msgs/{id} ─────────────────
data class Notification(
    val id       : String     = "",
    val userId   : String     = "",
    val fromUid  : String     = "",
    val fromName : String     = "",
    val fromPhoto: String     = "",
    val type     : String     = "",
    val message  : String     = "",
    val url      : String     = "",
    val read     : Boolean    = false,
    val ts       : Timestamp? = null,
)

// ── Supabase: conversations tablosu ──────────────────────
//  Sütunlar: id, participant_a, participant_b, last_msg, updated_at
data class Conversation(
    val id            : String       = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage   : String       = "",
    val lastMessageAt : String       = "",
    val otherUser     : User?        = null,
    val unreadCount   : Int          = 0,
)

// ── Supabase: messages tablosu ────────────────────────────
//  Sütunlar: id, conv_id, from_uid, to_uid, msg_text, created_at, read_at
data class Message(
    val id            : String  = "",
    val conversationId: String  = "",
    val senderId      : String  = "",   // from_uid
    val text          : String  = "",   // msg_text
    val createdAt     : String  = "",
    val read          : Boolean = false,
)

// ── Firestore: kurdiLessons/{id} ─────────────────────────
data class KurdiLesson(
    val id       : String  = "",
    val title    : String  = "",
    val subtitle : String  = "",
    val type     : String  = "mcq",
    val xpReward : Int     = 10,
    val completed: Boolean = false,
    val order    : Int     = 0,
)

// ── Firestore: books/{bookId} ────────────────────────────
//  Kullanıcıların eklediği kitaplar
data class Book(
    val id         : String     = "",
    val uid        : String     = "",   // ekleyen
    val title      : String     = "",
    val author     : String     = "",
    val coverURL   : String     = "",
    val description: String     = "",
    val genre      : String     = "",
    val pageCount  : Int        = 0,
    val language   : String     = "ku",
    val rating     : Double     = 0.0,
    val ratingCount: Int        = 0,
    val ts         : Timestamp? = null,
)

// ── Firestore: readingLists/{uid}/books/{sid} ────────────
//  status: okuyorum | okumak_istiyorum | okudum | biraktim
data class ReadingEntry(
    val sid      : String     = "",
    val title    : String     = "",
    val coverImg : String     = "",
    val bg       : String     = "",
    val status   : String     = "okumak_istiyorum",
    val updatedAt : Timestamp? = null,
)
