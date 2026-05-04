package com.heftreng.app.data.model

import com.google.firebase.Timestamp

// ─── KULLANICI ──────────────────────────────────────────────────────────────
// Firestore: users/{uid}
// Tema: uid, name, displayName, photoURL, coverPhoto, bio, website,
//       xp, kf_xp, level, streak, kf_streak, kf_done[],
//       postCount, badges[], email, createdAt, lastSeen, banned
data class User(
    val uid            : String        = "",
    val displayName    : String        = "",
    val name           : String        = "",
    val username       : String        = "",
    val email          : String        = "",
    val photoURL       : String        = "",
    val coverPhoto     : String        = "",
    val bio            : String        = "",
    val website        : String        = "",
    val followersCount : Int           = 0,
    val followingCount : Int           = 0,
    val postsCount     : Int           = 0,
    val level          : Int           = 1,
    val xp             : Int           = 0,
    val streak         : Int           = 0,
    val kfXp           : Int           = 0,   // kf_xp — Kurdî XP (New ekledi)
    val kfStreak       : Int           = 0,   // kf_streak
    val kfDone         : List<String>  = emptyList(), // kf_done array
    val badges         : List<String>  = emptyList(),
    val banned         : Boolean       = false,
)

// ─── FEED GÖNDERİSİ ────────────────────────────────────────────────────────
// Firestore: feed/{postId}
// Tema: uid, name, photoURL, text, imgUrl, ytVid, likes, saves, cmtCount,
//       repostType, repostId, repostText, repostImg, repostTitle, repostUrl,
//       repostAuthor, repostAuthorPhoto, repostAuthorUid,
//       quote{text,book,author} + quoteText/bookName/authorName,
//       type, serialId, chapterId, serialTitle, chapterTitle,
//       serialCover, serialBg, chapterOrder, chapterWordCount,
//       badges[], authorEmail, ts
data class Post(
    val id                : String        = "",
    val uid               : String        = "",
    val displayName       : String        = "",
    val name              : String        = "",   // tema: name alanı
    val username          : String        = "",
    val photoURL          : String        = "",
    val text              : String        = "",
    val imgUrl            : String        = "",   // tema: imgUrl (primary)
    val imageURL          : String        = "",   // eski alan — fallback
    val ytVid             : String        = "",
    val likesCount        : Int           = 0,
    val savesCount        : Int           = 0,
    val commentsCount     : Int           = 0,
    val repostsCount      : Int           = 0,
    val repostType        : String        = "",   // "feed"|"blog"|""
    val repostId          : String        = "",
    val repostOf          : String        = "",   // eski alan
    val repostUid         : String        = "",
    val repostText        : String        = "",
    val repostImg         : String        = "",
    val repostTitle       : String        = "",
    val repostUrl         : String        = "",
    val repostAuthor      : String        = "",   // New ekledi
    val repostAuthorPhoto : String        = "",
    val repostAuthorUid   : String        = "",
    val quoteText         : String        = "",
    val bookName          : String        = "",
    val authorName        : String        = "",
    val postType          : String        = "",   // "chapter"|""
    val serialId          : String        = "",
    val chapterId         : String        = "",
    val serialTitle       : String        = "",
    val chapterTitle      : String        = "",
    val chapterSnippet    : String        = "",
    val serialCover       : String        = "",
    val serialBg          : String        = "",
    val chapterOrder      : Int           = 0,
    val chapterWordCount  : Int           = 0,
    val badges            : List<String>  = emptyList(),
    val authorEmail       : String        = "",
    val ts                : Timestamp?    = null,
    val isLikedByMe       : Boolean       = false,
    val isSavedByMe       : Boolean       = false,
    val isRepostedByMe    : Boolean       = false,
)

// ─── YORUM ──────────────────────────────────────────────────────────────────
// Firestore: feed/{postId}/comments/{cmtId}
data class ReplyTo(
    val uid         : String = "",
    val displayName : String = "",
    val cmtId       : String = "",
    val text        : String = "",
)

data class Comment(
    val id          : String     = "",
    val postId      : String     = "",
    val uid         : String     = "",
    val displayName : String     = "",
    val photoURL    : String     = "",
    val text        : String     = "",
    val likesCount  : Int        = 0,
    val isLikedByMe : Boolean    = false,
    val replyTo     : ReplyTo?   = null,
    val ts          : Timestamp? = null,
)

// ─── BİLDİRİM ───────────────────────────────────────────────────────────────
// Firestore: userNotifs/{uid}/msgs/{notifId}
// Tema: type, title, sub, ico, fromUid, fromName, fromPhoto, feedId, read, ts
data class Notification(
    val id        : String     = "",
    val userId    : String     = "",
    val type      : String     = "",
    val title     : String     = "",
    val sub       : String     = "",
    val ico       : String     = "",
    val message   : String     = "",
    val fromUid   : String     = "",
    val fromName  : String     = "",
    val fromPhoto : String     = "",
    val feedId    : String     = "",
    val postId    : String?    = null,
    val url       : String     = "",
    val read      : Boolean    = false,
    val ts        : Timestamp? = null,
)

// ─── MESAJ / KONUŞMA ────────────────────────────────────────────────────────
// Firestore: conversations/{pa__pb}
//   participants[], last_msg, updated_at, unread_{uid}
// Firestore: convMessages/{convId}/msgs/{msgId}
//   senderUid, text, image_url, createdAt, read, seen, deleted, edited,
//   liked_by[], reply_to_id, reply_to_text, reply_to_name
data class Message(
    val id             : String       = "",
    val conversationId : String       = "",
    val senderId       : String       = "",
    val text           : String       = "",
    val imageUrl       : String       = "",
    val createdAt      : String       = "",
    val read           : Boolean      = false,
    val seen           : Boolean      = false,
    val deleted        : Boolean      = false,
    val edited         : Boolean      = false,
    val likedBy        : List<String> = emptyList(),
    val replyToId      : String       = "",
    val replyToText    : String       = "",
    val replyToName    : String       = "",
)

data class Conversation(
    val id             : String       = "",
    val participantIds : List<String> = emptyList(),
    val lastMessage    : String       = "",
    val lastMessageAt  : String       = "",
    val otherUser      : User?        = null,
    val unreadCount    : Int          = 0,
)

// ─── SERİ ────────────────────────────────────────────────────────────────────
// Firestore: serials/{serialId}
data class Serial(
    val id           : String     = "",
    val uid          : String     = "",
    val name         : String     = "",
    val photoURL     : String     = "",
    val title        : String     = "",
    val desc         : String     = "",
    val genre        : String     = "",
    val coverImg     : String     = "",
    val bg           : String     = "",
    val chapterCount : Int        = 0,
    val likes        : Int        = 0,
    val ts           : Timestamp? = null,
    val updatedAt    : Timestamp? = null,
    val isLikedByMe  : Boolean    = false,
)

data class Chapter(
    val id        : String     = "",
    val serialId  : String     = "",
    val title     : String     = "",
    val body      : String     = "",
    val order     : Int        = 0,
    val wordCount : Int        = 0,
    val uid       : String     = "",
    val ts        : Timestamp? = null,
)

// ─── KİTAP (Books koleksiyonu — Uploaded'ın ek özelliği) ───────────────────
// Firestore: books/{bookId}
data class Book(
    val id          : String     = "",
    val uid         : String     = "",
    val name        : String     = "",
    val photoURL    : String     = "",
    val title       : String     = "",
    val desc        : String     = "",
    val genre       : String     = "",
    val coverImg    : String     = "",
    val bg          : String     = "",
    val chapterCount: Int        = 0,
    val likes       : Int        = 0,
    val ts          : Timestamp? = null,
    val updatedAt   : Timestamp? = null,
)

data class BookChapter(
    val id        : String     = "",
    val bookId    : String     = "",
    val title     : String     = "",
    val body      : String     = "",
    val order     : Int        = 0,
    val wordCount : Int        = 0,
    val ts        : Timestamp? = null,
)

// ─── OKUMA LİSTESİ ──────────────────────────────────────────────────────────
// Firestore: readingLists/{uid}/books/{sid}
// status: okuyorum | okumak_istiyorum | okudum | biraktim
data class ReadingListEntry(
    val sid      : String     = "",
    val title    : String     = "",
    val coverImg : String     = "",
    val bg       : String     = "",
    val status   : String     = "",
    val updatedAt: Timestamp? = null,
)

// ─── KURDİ DERS ─────────────────────────────────────────────────────────────
data class KurdiLesson(
    val id        : String  = "",
    val title     : String  = "",
    val subtitle  : String  = "",
    val type      : String  = "",   // fill|mcq|build|match
    val xpReward  : Int     = 10,
    val completed : Boolean = false,
    val order     : Int     = 0,
)

data class KurdiExercise(
    val id         : String       = "",
    val lessonId   : String       = "",
    val type       : String       = "",
    val question   : String       = "",
    val questionTr : String       = "",
    val answer     : String       = "",
    val wrong      : List<String> = emptyList(),
    val words      : List<String> = emptyList(),
    val tr         : String       = "",
)

data class KurdiWord(
    val id       : String = "",
    val ku       : String = "",
    val kp       : String = "",
    val tr       : String = "",
    val e        : String = "",
    val lessonId : String = "",
)

// ─── PRESENCE ────────────────────────────────────────────────────────────────
// Firestore: presence/{uid}
data class Presence(
    val uid     : String     = "",
    val online  : Boolean    = false,
    val lastSeen: Timestamp? = null,
)

// ─── FOLLOWS ────────────────────────────────────────────────────────────────
// Firestore: follows/{fromUid_targetUid}
data class FollowEntry(
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val ts       : Timestamp? = null,
)

// ─── LIKE ENTRY ─────────────────────────────────────────────────────────────
data class LikeEntry(
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val ts       : Timestamp? = null,
)
