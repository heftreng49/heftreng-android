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
    val isPrivate      : Boolean= false,   // Gizli hesap
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
    // tema: repostSerial alanları
    val repostSerialId        : String  = "",
    val repostSerialTitle     : String  = "",
    val repostSerialDesc      : String  = "",
    val repostSerialCover     : String  = "",
    val repostSerialAuthorName: String  = "",
    val repostSerialAuthorUid : String  = "",
    val repostSerialBg        : String  = "",
    val repostSerialChCount   : Int     = 0,
    val serialId         : String     = "",   // chapter repost için
    val chapterId        : String     = "",   // chapter repost için
    val isLikedByMe      : Boolean    = false,
    val isSavedByMe      : Boolean    = false,
    val isRepostedByMe   : Boolean    = false,
    val myRepostId       : String     = "",
    // Kütüphane alıntı/inceleme için Firestore ID'leri
    val libraryBookId    : String     = "",
    val libraryAuthorId  : String     = "",
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
    val audioUrl       : String       = "",
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
    val ts          : Timestamp? = null,
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
    // "book" veya "serial" — Firestore'da hangi koleksiyondan geldiğini belirtir
    val type         : String     = "book",
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
    val ts           : com.google.firebase.Timestamp? = null,
)

// ─── KİTAP BÖLÜMÜ ──────────────────────────────────────
data class BookChapter(
    val id        : String     = "",
    val bookId    : String     = "",   // books koleksiyonu için
    val serialId  : String     = "",   // serials koleksiyonu için (type=="serial")
    val title     : String     = "",
    val body      : String     = "",
    val order     : Int        = 0,
    val wordCount : Int        = 0,
    val uid       : String     = "",
    val likes     : Int        = 0,
    val cmtCount  : Int        = 0,
    val isLikedByMe: Boolean   = false,
    val ts        : Timestamp? = null,
) {
    // Hangi koleksiyonda olursa olsun parent ID'yi döner
    val parentId get() = serialId.ifBlank { bookId }
}

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

// ─── CMS ─────────────────────────────────────────────────
data class CmsPage(
    val id        : String                         = "",
    val slug      : String                         = "",
    val title     : String                         = "",
    val body      : String                         = "",
    val lang      : String                         = "tr",
    val published : Boolean                        = true,
    val order     : Int                            = 0,
    val updatedAt : com.google.firebase.Timestamp? = null,
    val updatedBy : String                         = "",
)

data class CmsBanner(
    val id        : String                         = "",
    val title     : String                         = "",
    val subtitle  : String                         = "",
    val imageUrl  : String                         = "",
    val linkUrl   : String                         = "",
    val active    : Boolean                        = true,
    val order     : Int                            = 0,
    val updatedAt : com.google.firebase.Timestamp? = null,
)

data class CmsAnnouncement(
    val id     : String                         = "",
    val title  : String                         = "",
    val body   : String                         = "",
    val type   : String                         = "info",
    val active : Boolean                        = true,
    val ts     : com.google.firebase.Timestamp? = null,
)

data class CmsCategory(
    val id     : String = "",
    val name   : String = "",
    val nameKu : String = "",
    val slug   : String = "",
    val order  : Int    = 0,
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

// ─── CMS REKLAM KONFİGÜRASYONU ─────────────────────────────
data class CmsAdConfig(
    val id          : String  = "",   // Firestore doc ID (ör. "banner_feed")
    val unitId      : String  = "",   // AdMob unit ID
    val enabled     : Boolean = false,
    val testMode    : Boolean = true,
    // Banner özel
    val position    : Int     = 5,    // Feed'de kaçıncı kart sonrası
    // Interstitial özel
    val frequency   : Int     = 3,    // Kaç chapter'da bir
    // Rewarded özel
    val xpReward    : Int     = 50,
)

// ─── ŞİKAYET ────────────────────────────────────────────────────────
data class Report(
    val id          : String     = "",
    val reporterUid : String     = "",
    val reporterName: String     = "",
    val targetUid   : String     = "",
    val targetName  : String     = "",
    val targetPostId: String     = "",   // boşsa kullanıcı şikayeti
    val reason      : String     = "",
    val status      : String     = "pending",  // pending | reviewed | dismissed
    val ts          : Timestamp? = null,
)

// ─── ENGELLİ KULLANICI ───────────────────────────────────────────────
data class BlockedUser(
    val uid         : String     = "",
    val displayName : String     = "",
    val photoURL    : String     = "",
    val blockedAt   : Timestamp? = null,
)

// ─── APP CONFIG (CMS Özellik Yönetimi) ─────────────────────────────
data class AppConfig(
    // Ekran aktiflik
    val feedEnabled         : Boolean = true,
    val messagesEnabled     : Boolean = true,
    val serialsEnabled      : Boolean = true,
    val booksEnabled        : Boolean = true,
    val kurdiEnabled        : Boolean = true,
    val notificationsEnabled: Boolean = true,
    val searchEnabled       : Boolean = true,
    val storiesEnabled      : Boolean = true,

    // Feed özellikleri
    val feedShowImages      : Boolean = true,
    val feedShowReposts     : Boolean = true,
    val feedAllowQuotes     : Boolean = true,
    val feedMaxTextLength   : Int     = 1000,

    // Mesaj özellikleri
    val messagesAllowImages : Boolean = true,
    val messagesAllowVoice  : Boolean = true,

    // Profil özellikleri
    val profileShowXp       : Boolean = true,
    val profileShowStreak   : Boolean = true,
    val profileShowBadges   : Boolean = true,
    val profileShowReadList : Boolean = true,

    // Kurdî Fêrbibe
    val kurdiShowAiLesson   : Boolean = true,
    val kurdiShowWordOfDay  : Boolean = true,

    // Bakım modu
    val maintenanceMode     : Boolean = false,
    val maintenanceMessage  : String  = "Uygulama güncelleniyor, lütfen bekleyin.",
    val minVersion          : Int     = 1,

    // Özel başlıklar
    val feedTitle           : String  = "",
    val messagesTitle       : String  = "",
    val kurdiTitle          : String  = "",
)

// Test Unit ID'leri — testMode = true olduğunda bunlar kullanılır
object AdMobTestIds {
    const val BANNER        = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL  = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED      = "ca-app-pub-3940256099942544/5224354917"
}

// Gerçek Unit ID'ler — testMode = false olduğunda bunlar kullanılır
object AdMobProdIds {
    const val BANNER        = "ca-app-pub-6463746824939277/7866834575"
    const val INTERSTITIAL  = "ca-app-pub-6463746824939277/4989839500"
    const val REWARDED      = "ca-app-pub-6463746824939277/9693325673"
}
// ─────────────────────────────────────────────────────────
//  KÜTÜPHANE — Yazar / LibraryBook / BookQuote / BookReview
//  Firestore yapısı:
//    authors/{authorId}
//      books/ (sub) → {bookId} referansları
//    library_books/{bookId}
//      quotes/ (sub)
//      reviews/ (sub)
// ─────────────────────────────────────────────────────────

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
    val isFollowedByMe: Boolean = false,
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
    val ts          : com.google.firebase.Timestamp? = null,
)

data class BookQuote(
    val id          : String    = "",
    val bookId      : String    = "",
    val authorId    : String    = "",
    val bookTitle   : String    = "",
    val authorName  : String    = "",
    val text        : String    = "",
    // Paylaşan kullanıcı
    val uid         : String    = "",
    val userDisplayName: String = "",
    val userPhotoURL: String    = "",
    val feedPostId  : String    = "",   // feed'deki post id (ters referans)
    val likesCount  : Int       = 0,
    val ts          : com.google.firebase.Timestamp? = null,
)

data class BookReview(
    val id          : String    = "",
    val bookId      : String    = "",
    val authorId    : String    = "",
    val bookTitle   : String    = "",
    val authorName  : String    = "",
    val text        : String    = "",
    val rating      : Float     = 0f,  // 1..5
    // Paylaşan kullanıcı
    val uid         : String    = "",
    val userDisplayName: String = "",
    val userPhotoURL: String    = "",
    val feedPostId  : String    = "",
    val ts          : com.google.firebase.Timestamp? = null,
)
