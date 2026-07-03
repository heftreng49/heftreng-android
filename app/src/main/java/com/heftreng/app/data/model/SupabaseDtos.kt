package com.heftreng.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Supabase DTO'ları — tüm ViewModel'ler buradan import eder
// ══════════════════════════════════════════════════════════════

// ── Kayıtlı kullanıcılar — öneri listesi ve takip sistemi için ─────────────────
// Supabase'deki tablo: users (uid, display_name, photo_url, bio, banned, created_at)
// Firebase Firestore'daki users koleksiyonunun ayna kaydı (temel alanlar).
// Kayıt sırasında createUserDoc() tarafından yazılır, profil güncellemelerinde senkronize edilir.
@Serializable
data class UserRow(
    val uid          : String  = "",
    @SerialName("display_name") val displayName: String  = "",
    @SerialName("photo_url")    val photoUrl   : String  = "",
    val bio          : String  = "",
    val banned       : Boolean = false,
    @SerialName("created_at")   val createdAt  : String  = "",
)

@Serializable
data class FollowRow(
    val id           : String = "",
    @SerialName("from_uid")     val fromUid    : String = "",
    @SerialName("from_name")    val fromName   : String = "",
    @SerialName("from_photo")   val fromPhoto  : String = "",
    @SerialName("target_uid")   val targetUid  : String = "",
    @SerialName("target_name")  val targetName : String = "",
    @SerialName("target_photo") val targetPhoto: String = "",
    @SerialName("created_at")   val createdAt  : String = "",
)

@Serializable
data class FeedLikeRow(
    val id        : String  = "",
    @SerialName("post_id")    val postId   : String  = "",
    val uid       : String  = "",
    val name      : String? = null,
    @SerialName("photo_url")  val photoUrl : String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class FeedSaveRow(
    val id        : String  = "",
    @SerialName("post_id")    val postId   : String  = "",
    val uid       : String  = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class FeedCommentRow(
    val id              : String  = "",
    @SerialName("post_id")         val postId        : String  = "",
    val uid             : String  = "",
    val name            : String? = null,
    @SerialName("photo_url")       val photoUrl      : String? = null,
    val text            : String  = "",
    @SerialName("likes_count")     val likesCount    : Int     = 0,
    @SerialName("reply_to_cmt_id") val replyToCmtId  : String? = null,
    @SerialName("mentions")        val mentions      : List<String>? = null,
    @SerialName("created_at")      val createdAt     : String? = null,
)

@Serializable
data class CommentLikeRow(
    val id         : String = "",
    @SerialName("comment_id") val commentId: String = "",
    val uid        : String = "",
    val name       : String = "",
    @SerialName("photo_url")  val photoUrl : String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SerialLikeRow(
    val id        : String  = "",
    @SerialName("serial_id")  val serialId : String  = "",
    val uid       : String  = "",
    val name      : String? = null,
    @SerialName("photo_url")  val photoUrl : String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ── Bölüm bazlı okuma yüzdesi — users/{uid}/readProgress taşındı ───────────────
@Serializable
data class ReadProgressRow(
    val uid          : String = "",
    @SerialName("parent_id")  val parentId : String = "",
    @SerialName("chapter_id") val chapterId: String = "",
    val pct          : Int    = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ── Günlük aktivite — streak hesaplamasını genişletmek için ────────────────────
@Serializable
data class DailyActivityRow(
    val uid           : String = "",
    @SerialName("activity_date") val activityDate: String = "",
    val actions       : Int    = 0,
    @SerialName("created_at")   val createdAt    : String? = null,
)

// ── Rozetler — kazanılan rozetler (katalog Kotlin'de: BadgeCatalog) ────────────
@Serializable
data class UserBadgeRow(
    val uid          : String = "",
    @SerialName("badge_id") val badgeId : String = "",
    @SerialName("earned_at") val earnedAt: String? = null,
)

// ── book_quotes tablosu — Admin "Alıntılar'dan Seç" için ──────────────────────
// Not: BookQuoteRow zaten LibraryRepository.kt'de tanımlı, burada duplicate yok.
// AdminViewModel.searchBookQuotes() LibraryRepository.BookQuoteRow kullanır.
