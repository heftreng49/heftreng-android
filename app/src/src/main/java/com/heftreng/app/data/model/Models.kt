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
data class Post(
    val id            : String     = "",
    val uid           : String     = "",
    val displayName   : String     = "",
    val username      : String     = "",
    val photoURL      : String     = "",
    val text          : String     = "",
    val imgUrl        : String     = "",   // tema: imgUrl
    val ytVid         : String     = "",   // tema: ytVid
    val badges        : List<String> = emptyList(),
    val repostTitle   : String     = "",
    val repostUrl     : String     = "",
    val repostImg     : String     = "",
    val imageURL      : String     = "",
    val likesCount    : Int        = 0,
    val commentsCount : Int        = 0,
    val repostsCount  : Int        = 0,
    val ts            : Timestamp? = null,
    val quoteText     : String     = "",
    val bookName      : String     = "",
    val authorName    : String     = "",
    val repostOf      : String     = "",
    val repostUid     : String     = "",
    val name          : String     = "",   // tema: name (displayName alternatifi)
    val repostType    : String     = "",   // tema: repostType
    val repostId      : String     = "",   // tema: repostId
    val repostText       : String     = "",   // tema: orijinal post metni
    val repostAuthor     : String     = "",   // tema: orijinal yazar adı
    val repostAuthorPhoto: String     = "",   // tema: orijinal yazar fotoğrafı
    val repostAuthorUid  : String     = "",   // tema: orijinal yazar uid
    val serialTitle      : String     = "",   // tema: serial başlığı
    val serialCover      : String     = "",   // tema: serial kapak
    val chapterTitle     : String     = "",   // tema: bölüm başlığı
    val chapterOrder     : Int        = 0,    // tema: bölüm sırası
    val isLikedByMe   : Boolean    = false,
    val isSavedByMe   : Boolean    = false,
)

// ─── YORUM ─────────────────────────────────────────────
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

data class ReplyTo(
    val uid         : String = "",
    val displayName : String = "",
)

// ─── BEĞENİ GİRİŞİ ─────────────────────────────────────
data class LikeEntry(
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val ts       : Timestamp? = null,
)

// ─── TAKİP GİRİŞİ ──────────────────────────────────────
data class FollowEntry(
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val ts       : Timestamp? = null,
)

// ─── BİLDİRİM ──────────────────────────────────────────
data class Notification(
    val id        : String     = "",
    val userId    : String     = "",
    val fromUid   : String     = "",
    val fromName  : String     = "",
    val fromPhoto : String     = "",
    val type      : String     = "",
    val message   : String     = "",
    val postId    : String?    = null,
    val url       : String     = "",
    val read      : Boolean    = false,
    val ts        : Timestamp? = null,
)

// ─── TAKİP ─────────────────────────────────────────────
data class FollowRelation(
    val id          : String     = "",
    val fromUid     : String     = "",
    val fromName    : String     = "",
    val fromPhoto   : String     = "",
    val targetUid   : String     = "",
    val targetName  : String     = "",
    val targetPhoto : String     = "",
    val ts          : Timestamp? = null,
)

// ─── MESAJ / KONUŞMA ───────────────────────────────────
data class Message(
    val id             : String       = "",
    val conversationId : String       = "",
    val senderId       : String       = "",
    val text           : String       = "",
    val imageUrl       : String       = "",
    val createdAt      : String       = "",
    val read           : Boolean      = false,
    val deleted        : Boolean      = false,
    val edited         : Boolean      = false,
    val likedBy        : List<String> = emptyList(),
    val replyToId      : String       = "",
    val replyToText    : String       = "",
    val replyToName    : String       = "",
)

data class Conversation(
    val id            : String       = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage   : String       = "",
    val lastMessageAt : String       = "",
    val otherUser     : User?        = null,
    val unreadCount   : Int          = 0,
)

// ─── SERİ (KİTAP/ROMAN) ────────────────────────────────
data class Serial(
    val id           : String     = "",
    val uid          : String     = "",
    val name         : String     = "",
    val photoURL     : String     = "",
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

// ─── KİTAP ─────────────────────────────────────────────
data class Book(
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

// ─── KİTAP BÖLÜMÜ ──────────────────────────────────────
data class BookChapter(
    val id        : String     = "",
    val bookId    : String     = "",
    val title     : String     = "",
    val body      : String     = "",
    val order     : Int        = 0,
    val wordCount : Int        = 0,
    val uid       : String     = "",
    val ts        : Timestamp? = null,
)

// ─── OKUMA LİSTESİ ─────────────────────────────────────
data class ReadingListEntry(
    val sid       : String     = "",
    val title     : String     = "",
    val coverImg  : String     = "",
    val bg        : String     = "",
    val status    : String     = "",
    val updatedAt : Timestamp? = null,
)

// ─── KURDİ DERS ────────────────────────────────────────
data class KurdiLesson(
    val id        : String  = "",
    val title     : String  = "",
    val subtitle  : String  = "",
    val type      : String  = "",
    val xpReward  : Int     = 10,
    val completed : Boolean = false,
    val order     : Int     = 0,
)

// ─── KURDİ AI DERS ──────────────────────────────────────
data class AiLesson(
    val topic    : String           = "",
    val level    : String           = "destpêk",
    val exercises: List<AiExercise> = emptyList(),
)

data class AiExercise(
    val type    : String       = "mcq",
    val ku      : String       = "",
    val tr      : String       = "",
    val options : List<String> = emptyList(),
    val answer  : String       = "",
)
