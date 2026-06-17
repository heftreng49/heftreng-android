package com.heftreng.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

// ═══════════════════════════════════════════════════════════════════════════
//  Rozet Sistemi — Öncelik 4
//  Katalog burada (Kotlin) tanımlı; kazanılan rozetler Supabase
//  `user_badges` (uid, badge_id) tablosunda saklanır.
// ═══════════════════════════════════════════════════════════════════════════

enum class BadgeStat { BOOKS_READ, QUOTES_SHARED, STREAK }

data class BadgeDef(
    val id        : String,
    val titleTr   : String,
    val titleKu   : String,
    val descTr    : String,
    val descKu    : String,
    val icon      : ImageVector,
    val stat      : BadgeStat,
    val threshold : Int,
) {
    fun title(language: String) = if (language == "ku") titleKu else titleTr
    fun desc(language: String)  = if (language == "ku") descKu else descTr
}

object BadgeCatalog {
    val all = listOf(
        BadgeDef(
            id = "first_book",
            titleTr = "İlk Kitap", titleKu = "Pirtûka Yekem",
            descTr = "İlk kitabını okudum olarak işaretledin",
            descKu = "Pirtûka xwe ya yekem wek xwendî nîşan kir",
            icon = Icons.Filled.AutoStories, stat = BadgeStat.BOOKS_READ, threshold = 1,
        ),
        BadgeDef(
            id = "bookworm",
            titleTr = "Kitap Kurdu", titleKu = "Kurmê Pirtûkan",
            descTr = "10 kitap okudun",
            descKu = "10 pirtûk xwendin",
            icon = Icons.Filled.MenuBook, stat = BadgeStat.BOOKS_READ, threshold = 10,
        ),
        BadgeDef(
            id = "library_master",
            titleTr = "Kütüphane Ustası", titleKu = "Mastera Pirtûkxaneyê",
            descTr = "25 kitap okudun",
            descKu = "25 pirtûk xwendin",
            icon = Icons.Filled.LocalLibrary, stat = BadgeStat.BOOKS_READ, threshold = 25,
        ),
        BadgeDef(
            id = "quote_collector",
            titleTr = "Alıntı Koleksiyoncusu", titleKu = "Berhevkarê Gotinan",
            descTr = "5 alıntı paylaştın",
            descKu = "5 gotin par kirin",
            icon = Icons.Filled.FormatQuote, stat = BadgeStat.QUOTES_SHARED, threshold = 5,
        ),
        BadgeDef(
            id = "quote_master",
            titleTr = "Alıntı Ustası", titleKu = "Mastera Gotinan",
            descTr = "25 alıntı paylaştın",
            descKu = "25 gotin par kirin",
            icon = Icons.Filled.WorkspacePremium, stat = BadgeStat.QUOTES_SHARED, threshold = 25,
        ),
        BadgeDef(
            id = "streak_7",
            titleTr = "7 Günlük Seri", titleKu = "Rêza 7 Rojan",
            descTr = "7 gün üst üste aktif oldun",
            descKu = "7 roj li pey hev çalak bûyî",
            icon = Icons.Filled.LocalFireDepartment, stat = BadgeStat.STREAK, threshold = 7,
        ),
        BadgeDef(
            id = "streak_30",
            titleTr = "30 Günlük Seri", titleKu = "Rêza 30 Rojan",
            descTr = "30 gün üst üste aktif oldun",
            descKu = "30 roj li pey hev çalak bûyî",
            icon = Icons.Filled.Whatshot, stat = BadgeStat.STREAK, threshold = 30,
        ),
        BadgeDef(
            id = "streak_100",
            titleTr = "100 Günlük Seri", titleKu = "Rêza 100 Rojan",
            descTr = "100 gün üst üste aktif oldun",
            descKu = "100 roj li pey hev çalak bûyî",
            icon = Icons.Filled.EmojiEvents, stat = BadgeStat.STREAK, threshold = 100,
        ),
    )

    fun byId(id: String): BadgeDef? = all.find { it.id == id }

    /** Verilen istatistiklere göre kazanılmış olması gereken rozet ID'leri. */
    fun eligibleIds(booksRead: Int, quotesShared: Int, streak: Int): Set<String> =
        all.filter { def ->
            when (def.stat) {
                BadgeStat.BOOKS_READ    -> booksRead    >= def.threshold
                BadgeStat.QUOTES_SHARED -> quotesShared >= def.threshold
                BadgeStat.STREAK        -> streak       >= def.threshold
            }
        }.map { it.id }.toSet()
}

// ── Supabase: user_badges (uid, badge_id, earned_at) ────────────────────────
data class UserBadge(
    val badgeId : String = "",
    val earnedAt: String = "",
)
