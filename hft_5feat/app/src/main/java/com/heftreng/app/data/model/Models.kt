package com.heftreng.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

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
    val banned             : Boolean= false,
    val isPrivate          : Boolean= false,
    val messagePermission  : String = "everyone", // "everyone" | "followers" | "nobody"
)

// ─── FEED GÖNDERISI ────────────────────────────────────
data class Post(
    val id            : String     = "",
    val uid           : String     = "",
    val displayName   : String     = "",
    val username      : String     = "",
    val photoURL      : String     = "",
    val text          : String     = "",
    val imgUrl        : String     = "",
    val ytVid         : String     = "",
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
    val name          : String     = "",
    val repostType    : String     = "",
    val repostId      : String     = "",
    val repostText       : String     = "",
    val repostAuthor     : String     = "",
    val repostAuthorPhoto: String     = "",
    val repostAuthorUid  : String     = "",
    val serialTitle      : String     = "",
    val serialCover      : String     = "",
    val chapterTitle     : String     = "",
    val chapterOrder     : Int        = 0,
    val repostSerialId        : String  = "",
    val repostSerialTitle     : String  = "",
    val repostSerialDesc      : String  = "",
    val repostSerialCover     : String  = "",
    val repostSerialAuthorName: String  = "",
    val repostSerialAuthorUid : String  = "",
    val repostSerialBg        : String  = "",
    val repostSerialChCount   : Int     = 0,
    val serialId         : String     = "",
    val chapterId        : String     = "",
    val isLikedByMe      : Boolean    = false,
    val isSavedByMe      : Boolean    = false,
    val isRepostedByMe   : Boolean    = false,
    val myRepostId       : String     = "",
    val libraryBookId    : String     = "",
    val libraryAuthorId  : String     = "",
    val type             : String     = "",
    val moderationStatus : String     = "active",
    val moderationNote   : String     = "",
    val moderationReason : String     = "",
    val visibility       : String     = "public"
)

// ─── MODERASYON İTİRAZ ────────────────────────────────────────────────
data class Appeal(
    val id              : String     = "",
    val postId          : String     = "",
    val postOwnerUid    : String     = "",
    val postOwnerName   : String     = "",
    val moderationStatus: String     = "",
    val text            : String     = "",
    val status          : String     = "pending",
    val adminNote       : String     = "",
    val ts              : Timestamp? = null,
    val resolvedAt      : Timestamp? = null
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
    val ts          : Timestamp? = null
)

data class ReplyTo(
    val uid         : String = "",
    val displayName : String = ""
)

// ─── BEĞENİ GİRİŞİ ─────────────────────────────────────
data class LikeEntry(
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val ts       : Timestamp? = null
)

// ─── TAKİP GİRİŞİ ──────────────────────────────────────
data class FollowEntry(
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val ts       : Timestamp? = null
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
    val ts        : Timestamp? = null
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
    val ts          : Timestamp? = null
)

// ─── MESAJ / KONUŞMA ───────────────────────────────────
data class Message(
    val id             : String       = "",
    val conversationId : String       = "",
    val senderId       : String       = "",
    val text           : String       = "",
    val imageUrl       : String       = "",
    val audioUrl       : String       = "",
    val createdAt      : String       = "",
    val read           : Boolean      = false,
    val deleted        : Boolean      = false,
    val edited         : Boolean      = false,
    val likedBy        : List<String> = emptyList(),
    val replyToId      : String       = "",
    val replyToText    : String       = "",
    val replyToName    : String       = ""
)

data class Conversation(
    val id            : String       = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage   : String       = "",
    val lastMessageAt : String       = "",
    val otherUser     : User?        = null,
    val unreadCount   : Int          = 0
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
    val isLikedByMe  : Boolean    = false
)

// ─── BÖLÜM ─────────────────────────────────────────────
data class Chapter(
    val id          : String     = "",
    val serialId    : String     = "",
    val title       : String     = "",
    val body        : String     = "",
    val order       : Int        = 0,
    val wordCount   : Int        = 0,
    val uid         : String     = "",
    val likes       : Int        = 0,
    val cmtCount    : Int        = 0,
    val isLikedByMe : Boolean    = false,
    val ts          : Timestamp? = null
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
    val type         : String     = "book"
)

// ─── BÖLÜM YORUMU ──────────────────────────────────────
data class ChapterComment(
    val id           : String     = "",
    val uid          : String     = "",
    val name         : String     = "",
    val photoURL     : String     = "",
    val text         : String     = "",
    val replyTo      : String     = "",
    val replyToCmtId : String     = "",
    val likes        : Int        = 0,
    val edited       : Boolean    = false,
    val ts           : Timestamp? = null
)

// ─── KİTAP BÖLÜMÜ ──────────────────────────────────────
data class BookChapter(
    val id        : String     = "",
    val bookId    : String     = "",
    val serialId  : String     = "",
    val title     : String     = "",
    val body      : String     = "",
    val order     : Int        = 0,
    val wordCount : Int        = 0,
    val uid       : String     = "",
    val likes     : Int        = 0,
    val cmtCount  : Int        = 0,
    val isLikedByMe: Boolean   = false,
    val ts        : Timestamp? = null
) {
    @get:Exclude
    val parentId get() = serialId.ifBlank { bookId }
}

// ─── OKUMA LİSTESİ ─────────────────────────────────────
data class ReadingListEntry(
    val sid        : String     = "",
    val title      : String     = "",
    val coverImg   : String     = "",
    val bg         : String     = "",
    val status     : String     = "",
    val updatedAt  : Timestamp? = null,
    val source     : String     = "serial",
    val authorName : String     = ""
)

// ─── KURDİ DERS ────────────────────────────────────────
data class KurdiLesson(
    val id        : String  = "",
    val title     : String  = "",
    val subtitle  : String  = "",
    val type      : String  = "",
    val xpReward  : Int     = 10,
    val completed : Boolean = false,
    val order     : Int     = 0
)

// ─── CMS ─────────────────────────────────────────────────
data class CmsPage(
    val id        : String     = "",
    val slug      : String     = "",
    val title     : String     = "",
    val body      : String     = "",
    val lang      : String     = "tr",
    val published : Boolean    = true,
    val order     : Int        = 0,
    val updatedAt : Timestamp? = null,
    val updatedBy : String     = ""
)

data class CmsBanner(
    val id        : String     = "",
    val title     : String     = "",
    val subtitle  : String     = "",
    val imageUrl  : String     = "",
    val linkUrl   : String     = "",
    val active    : Boolean    = true,
    val order     : Int        = 0,
    val updatedAt : Timestamp? = null
)

data class CmsAnnouncement(
    val id     : String     = "",
    val title  : String     = "",
    val body   : String     = "",
    val type   : String     = "info",
    val active : Boolean    = true,
    val ts     : Timestamp? = null
)

data class CmsCategory(
    val id     : String = "",
    val name   : String = "",
    val nameKu : String = "",
    val slug   : String = "",
    val order  : Int    = 0
)

// ─── KURDİ AI DERS ──────────────────────────────────────
data class AiLesson(
    val topic    : String           = "",
    val level    : String           = "destpêk",
    val exercises: List<AiExercise> = emptyList()
)

data class AiExercise(
    val type    : String                    = "mcq",
    val ku      : String                    = "",
    val tr      : String                    = "",
    val options : List<String>              = emptyList(),
    val answer  : String                    = "",
    val pairs   : List<Pair<String,String>> = emptyList(),
    val words   : List<String>              = emptyList()
)

// ─── CMS REKLAM KONFİGÜRASYONU ─────────────────────────────
data class CmsAdConfig(
    val id          : String  = "",
    val unitId      : String  = "",
    val enabled     : Boolean = false,
    val testMode    : Boolean = true,
    val position    : Int     = 5,
    val frequency   : Int     = 3,
    val xpReward    : Int     = 50,
    val dailyLimit  : Int     = 3,
    val scenarioDoubleXp     : Boolean = true,
    val scenarioUnlockLesson : Boolean = true,
    val scenarioSaveStreak   : Boolean = true
)

// ─── ŞİKAYET ────────────────────────────────────────────────────────
data class Report(
    val id          : String     = "",
    val reporterUid : String     = "",
    val reporterName: String     = "",
    val targetUid   : String     = "",
    val targetName  : String     = "",
    val targetPostId: String     = "",
    val reason      : String     = "",
    val status      : String     = "pending",
    val ts          : Timestamp? = null
)

// ─── ENGELLİ KULLANICI ───────────────────────────────────────────────
data class BlockedUser(
    val uid         : String     = "",
    val displayName : String     = "",
    val photoURL    : String     = "",
    val blockedAt   : Timestamp? = null
)

// ─── APP CONFIG (CMS Özellik Yönetimi) ─────────────────────────────
data class AppConfig(
    val feedEnabled         : Boolean = true,
    val messagesEnabled     : Boolean = true,
    val serialsEnabled      : Boolean = true,
    val booksEnabled        : Boolean = true,
    val kurdiEnabled        : Boolean = true,
    val notificationsEnabled: Boolean = true,
    val searchEnabled       : Boolean = true,
    val storiesEnabled      : Boolean = true,
    val feedShowImages      : Boolean = true,
    val feedShowReposts     : Boolean = true,
    val feedAllowQuotes     : Boolean = true,
    val feedMaxTextLength   : Int     = 1000,
    val messagesAllowImages : Boolean = true,
    val messagesAllowVoice  : Boolean = true,
    val profileShowXp       : Boolean = true,
    val profileShowStreak   : Boolean = true,
    val profileShowBadges   : Boolean = true,
    val profileShowReadList : Boolean = true,
    val kurdiShowAiLesson   : Boolean = true,
    val kurdiShowWordOfDay  : Boolean = true,
    val maintenanceMode     : Boolean = false,
    val maintenanceMessage  : String  = "Uygulama güncelleniyor, lütfen bekleyin.",
    val minVersion          : Int     = 1,
    val feedTitle           : String  = "",
    val messagesTitle       : String  = "",
    val kurdiTitle          : String  = ""
)

object AdMobTestIds {
    const val BANNER        = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL  = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED      = "ca-app-pub-3940256099942544/5224354917"
}

object AdMobProdIds {
    const val BANNER        = "ca-app-pub-6463746824939277/7866834575"
    const val INTERSTITIAL  = "ca-app-pub-6463746824939277/4989839500"
    const val REWARDED      = "ca-app-pub-6463746824939277/9693325673"
}

// ─── KÜTÜPHANE YAPISI ──────────────────────────────────────────────────
data class Author(
    val id          : String    = "",
    val name        : String    = "",
    val bio         : String    = "",
    val photoURL    : String    = "",
    val birthYear   : Int       = 0,
    val nationality : String    = "",
    val bookCount   : Int       = 0,
    val quoteCount  : Int       = 0,
    val reviewCount : Int       = 0,
    val followerCount: Int      = 0,
    @get:Exclude @set:Exclude
    var isFollowedByMe: Boolean = false
)

data class LibraryBook(
    val id          : String    = "",
    val title       : String    = "",
    val authorId    : String    = "",
    val authorName  : String    = "",
    val coverImg    : String    = "",
    val genre       : String    = "",
    val publishYear : Int       = 0,
    val synopsis    : String    = "",
    val pageCount   : Int       = 0,
    val quoteCount  : Int       = 0,
    val reviewCount : Int       = 0,
    val avgRating   : Float     = 0f,
    val ts          : Timestamp? = null
)

data class BookQuote(
    val id          : String    = "",
    val bookId      : String    = "",
    val authorId    : String    = "",
    val bookTitle   : String    = "",
    val authorName  : String    = "",
    val text        : String    = "",
    val uid         : String    = "",
    val userDisplayName: String = "",
    val userPhotoURL: String    = "",
    val feedPostId  : String    = "",
    val visibility  : String    = "public",
    val likesCount  : Int       = 0,
    val likedBy     : List<String> = emptyList(),
    val ts          : Timestamp? = null,
    @get:Exclude @set:Exclude
    var isLikedByMe: Boolean = false
)

data class BookReview(
    val id          : String    = "",
    val bookId      : String    = "",
    val authorId    : String    = "",
    val bookTitle   : String    = "",
    val authorName  : String    = "",
    val text        : String    = "",
    val rating      : Float     = 0f,
    val uid         : String    = "",
    val userDisplayName: String = "",
    val userPhotoURL: String    = "",
    val feedPostId  : String    = "",
    val likesCount  : Int       = 0,
    val likedBy     : List<String> = emptyList(),
    val ts          : Timestamp? = null,
    @get:Exclude @set:Exclude
    var isLikedByMe: Boolean = false
)
