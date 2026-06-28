package com.heftreng.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.heftreng.app.data.model.DailyActivityRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ── Supabase Row Modelleri ────────────────────────────────────────────────────

@Serializable
data class AuthorRow(
    val id             : String  = "",
    val name           : String  = "",
    val bio            : String  = "",
    @SerialName("photo_url")
    val photoUrl       : String  = "",
    @SerialName("birth_year")
    val birthYear      : Int     = 0,
    val nationality    : String  = "",
    @SerialName("book_count")
    val bookCount      : Int     = 0,
    @SerialName("quote_count")
    val quoteCount     : Int     = 0,
    @SerialName("review_count")
    val reviewCount    : Int     = 0,
    @SerialName("follower_count")
    val followerCount  : Int     = 0,
)

@Serializable
data class LibraryBookRow(
    val id           : String  = "",
    val title        : String  = "",
    @SerialName("author_id")
    val authorId     : String? = null,
    @SerialName("author_name")
    val authorName   : String  = "",
    @SerialName("cover_img")
    val coverImg     : String  = "",
    val genre        : String  = "",
    @SerialName("publish_year")
    val publishYear  : Int     = 0,
    val synopsis     : String  = "",
    @SerialName("page_count")
    val pageCount    : Int     = 0,
    @SerialName("quote_count")
    val quoteCount   : Int     = 0,
    @SerialName("review_count")
    val reviewCount  : Int     = 0,
    @SerialName("avg_rating")
    val avgRating    : Float   = 0f,
    @SerialName("likes_count")
    val likesCount   : Int     = 0,
    @SerialName("created_at")
    val createdAt    : String  = "",
)

@Serializable
data class BookQuoteRow(
    val id                : String  = "",
    @SerialName("book_id")
    val bookId            : String  = "",
    @SerialName("author_id")
    val authorId          : String? = null,
    @SerialName("book_title")
    val bookTitle         : String  = "",
    @SerialName("author_name")
    val authorName        : String  = "",
    val text              : String  = "",
    val uid               : String  = "",
    @SerialName("user_display_name")
    val userDisplayName   : String  = "",
    @SerialName("user_photo_url")
    val userPhotoUrl      : String  = "",
    @SerialName("feed_post_id")
    val feedPostId        : String  = "",
    @SerialName("likes_count")
    val likesCount        : Int     = 0,
    @SerialName("cover_img")
    val coverImg          : String  = "",
    @SerialName("created_at")
    val createdAt         : String  = "",
)

@Serializable
data class BookReviewRow(
    val id                : String  = "",
    @SerialName("book_id")
    val bookId            : String  = "",
    @SerialName("author_id")
    val authorId          : String? = null,
    @SerialName("book_title")
    val bookTitle         : String  = "",
    @SerialName("author_name")
    val authorName        : String  = "",
    val text              : String  = "",
    val rating            : Float   = 0f,
    val uid               : String  = "",
    @SerialName("user_display_name")
    val userDisplayName   : String  = "",
    @SerialName("user_photo_url")
    val userPhotoUrl      : String  = "",
    @SerialName("feed_post_id")
    val feedPostId        : String  = "",
    @SerialName("likes_count")
    val likesCount        : Int     = 0,
    @SerialName("created_at")
    val createdAt         : String  = "",
)

@Serializable
data class ReadingStatusRow(
    val uid          : String  = "",
    @SerialName("book_id")
    val bookId       : String  = "",
    val status       : String  = "",
    val title        : String  = "",
    @SerialName("cover_img")
    val coverImg     : String  = "",
    val bg           : String  = "",
    @SerialName("author_name")
    val authorName   : String  = "",
    val source       : String  = "serial",
    @SerialName("current_page")
    val currentPage  : Int     = 0,
    @SerialName("updated_at")
    val updatedAt    : String  = "",
)

@Singleton
class LibraryRepository @Inject constructor(
    private val supabase : SupabaseClient,
    private val auth     : FirebaseAuth,
) {
    // ── Kısayol ───────────────────────────────────────────────────────────────
    private val db get() = supabase.postgrest

    // ── Authors ───────────────────────────────────────────────────────────────

    suspend fun getAuthors(limit: Int = 50): List<AuthorRow> =
        db["authors"].select {
            order("name", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun getAuthor(id: String): AuthorRow? =
        db["authors"].select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingleOrNull()

    suspend fun searchAuthors(query: String): List<AuthorRow> =
        db["authors"].select {
            filter { ilike("name", "%$query%") }
            limit(20)
        }.decodeList()

    suspend fun searchBooks(query: String): List<LibraryBookRow> =
        db["library_books"].select {
            filter { ilike("title", "%$query%") }
            limit(20)
        }.decodeList()

    suspend fun upsertAuthor(row: AuthorRow) {
        db["authors"].upsert(row)
    }

    suspend fun updateAuthorCounters(
        id          : String,
        quoteCount  : Int? = null,
        reviewCount : Int? = null,
        bookCount   : Int? = null,
    ) {
        val c = getAuthor(id) ?: return
        db["authors"].update(
            c.copy(
                quoteCount  = quoteCount  ?: c.quoteCount,
                reviewCount = reviewCount ?: c.reviewCount,
                bookCount   = bookCount   ?: c.bookCount,
            )
        ) { filter { eq("id", id) } }
    }

    // ── Library Books ─────────────────────────────────────────────────────────

    suspend fun getBooks(limit: Int = 50): List<LibraryBookRow> =
        db["library_books"].select {
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun getBooksByAuthor(authorId: String): List<LibraryBookRow> =
        db["library_books"].select {
            filter { eq("author_id", authorId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun getBook(id: String): LibraryBookRow? =
        db["library_books"].select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingleOrNull()

    suspend fun upsertBook(row: LibraryBookRow) {
        db["library_books"].upsert(row)
    }

    suspend fun updateBookCounters(
        id          : String,
        quoteCount  : Int?   = null,
        reviewCount : Int?   = null,
        avgRating   : Float? = null,
    ) {
        val c = getBook(id) ?: return
        db["library_books"].update(
            c.copy(
                quoteCount  = quoteCount  ?: c.quoteCount,
                reviewCount = reviewCount ?: c.reviewCount,
                avgRating   = avgRating   ?: c.avgRating,
            )
        ) { filter { eq("id", id) } }
    }

    // ── Book Quotes ───────────────────────────────────────────────────────────

    suspend fun getQuotesByBook(bookId: String, limit: Int = 50): List<BookQuoteRow> =
        db["book_quotes"].select {
            filter { eq("book_id", bookId) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun getQuotesByAuthor(authorId: String, limit: Int = 50): List<BookQuoteRow> =
        db["book_quotes"].select {
            filter { eq("author_id", authorId) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    /** Profil — "X alıntı" kartına basınca kullanıcının paylaştığı tüm alıntılar */
    suspend fun getQuotesByUser(uid: String, limit: Int = 100): List<BookQuoteRow> =
        db["book_quotes"].select {
            filter { eq("uid", uid) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    /** Keşfet → Alıntılar — en son eklenen kütüphane alıntıları.
     *  ÖNCEKİ: Firestore `feed` (type='library_quote' + 300'lük legacy tarama, 2 sorgu).
     *  ŞİMDİ:  Supabase `book_quotes` — 1 sorgu, RLS public read. */
    suspend fun getRecentQuotes(limit: Int = 50): List<BookQuoteRow> =
        db["book_quotes"].select {
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun insertQuote(row: BookQuoteRow) {
        db["book_quotes"].insert(row)
    }

    suspend fun updateQuoteText(id: String, newText: String) {
        db["book_quotes"].update(mapOf("text" to newText)) {
            filter { eq("id", id) }
        }
    }

    /** Feed gönderisindeki alıntı düzenlenince ilgili book_quotes kaydını da güncelle */
    suspend fun updateQuoteTextByFeedPostId(feedPostId: String, newText: String) {
        db["book_quotes"].update(mapOf("text" to newText)) {
            filter { eq("feed_post_id", feedPostId) }
        }
    }

    suspend fun deleteQuote(id: String) {
        db["book_quotes"].delete { filter { eq("id", id) } }
    }

    suspend fun incrementQuoteLikes(id: String, delta: Int) {
        val row = db["book_quotes"].select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingleOrNull<BookQuoteRow>() ?: return
        db["book_quotes"].update(
            mapOf("likes_count" to (row.likesCount + delta).coerceAtLeast(0))
        ) { filter { eq("id", id) } }
    }

    // ── Book Reviews ──────────────────────────────────────────────────────────

    suspend fun getReviewsByBook(bookId: String, limit: Int = 50): List<BookReviewRow> =
        db["book_reviews"].select {
            filter { eq("book_id", bookId) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun getReviewsByAuthor(authorId: String, limit: Int = 50): List<BookReviewRow> =
        db["book_reviews"].select {
            filter { eq("author_id", authorId) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    /** Kütüphane → İncelemeler sekmesi — en son eklenen tüm incelemeler.
     *  ÖNCEKİ: önce 100 kitap çekilip, her biri için ayrı ayrı (sıralı/N+1) inceleme sorgusu
     *  atılıyordu → kütüphane sekmesinin çok yavaş açılmasına sebep oluyordu.
     *  ŞİMDİ: 1 sorgu, doğrudan en son incelemeler. */
    suspend fun getRecentReviews(limit: Int = 50): List<BookReviewRow> =
        db["book_reviews"].select {
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun insertReview(row: BookReviewRow) {
        db["book_reviews"].insert(row)
    }

    suspend fun updateReviewText(id: String, newText: String, newRating: Float) {
        db["book_reviews"].update(
            mapOf("text" to newText, "rating" to newRating)
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteReview(id: String) {
        db["book_reviews"].delete { filter { eq("id", id) } }
    }

    suspend fun incrementReviewLikes(id: String, delta: Int) {
        val row = db["book_reviews"].select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingleOrNull<BookReviewRow>() ?: return
        db["book_reviews"].update(
            mapOf("likes_count" to (row.likesCount + delta).coerceAtLeast(0))
        ) { filter { eq("id", id) } }
    }

    // ── Author Follows ────────────────────────────────────────────────────────

    suspend fun isFollowingAuthor(authorId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db["author_follows"].select {
                filter {
                    eq("author_id", authorId)
                    eq("user_id", uid)
                }
                limit(1)
            }.decodeList<Map<String, String>>().isNotEmpty()
        } catch (_: Exception) { false }
    }

    suspend fun followAuthor(authorId: String) {
        val uid = auth.currentUser?.uid ?: return
        db["author_follows"].upsert(
            mapOf("author_id" to authorId, "user_id" to uid)
        )
        val a = getAuthor(authorId) ?: return
        db["authors"].update(
            mapOf("follower_count" to a.followerCount + 1)
        ) { filter { eq("id", authorId) } }
    }

    suspend fun unfollowAuthor(authorId: String) {
        val uid = auth.currentUser?.uid ?: return
        db["author_follows"].delete {
            filter {
                eq("author_id", authorId)
                eq("user_id", uid)
            }
        }
        val a = getAuthor(authorId) ?: return
        db["authors"].update(
            mapOf("follower_count" to (a.followerCount - 1).coerceAtLeast(0))
        ) { filter { eq("id", authorId) } }
    }

    // ── ensureAuthorAndBook ───────────────────────────────────────────────────

    suspend fun ensureAuthorAndBook(
        authorName: String,
        bookName  : String,
    ): Pair<String, String> {
        val authorId = if (authorName.isNotBlank()) findOrCreateAuthor(authorName.trim()) else ""
        val bookId   = if (bookName.isNotBlank())   findOrCreateBook(bookName.trim(), authorId, authorName.trim()) else ""
        return Pair(authorId, bookId)
    }

    private suspend fun findOrCreateAuthor(name: String): String {
        return try {
            val existing = db["authors"].select {
                filter { ilike("name", name) }
                limit(1)
            }.decodeSingleOrNull<AuthorRow>()
            if (existing != null) return existing.id

            val newId = UUID.randomUUID().toString()
            db["authors"].insert(AuthorRow(id = newId, name = name))
            newId
        } catch (_: Exception) { "" }
    }

    private suspend fun findOrCreateBook(
        title     : String,
        authorId  : String,
        authorName: String,
    ): String {
        return try {
            val existing = db["library_books"].select {
                filter { ilike("title", title) }
                limit(1)
            }.decodeSingleOrNull<LibraryBookRow>()
            if (existing != null) return existing.id

            val newId = UUID.randomUUID().toString()
            db["library_books"].insert(LibraryBookRow(
                id         = newId,
                title      = title,
                authorId   = authorId.ifBlank { null },
                authorName = authorName,
            ))
            if (authorId.isNotBlank()) {
                val a = getAuthor(authorId)
                if (a != null) db["authors"].update(
                    mapOf("book_count" to a.bookCount + 1)
                ) { filter { eq("id", authorId) } }
            }
            newId
        } catch (_: Exception) { "" }
    }

    // ── addQuoteToLibrary ─────────────────────────────────────────────────────

    suspend fun addQuoteToLibrary(
        libraryBookId  : String,
        libraryAuthorId: String,
        bookName       : String,
        authorName     : String,
        quoteText      : String,
        uid            : String,
        userDisplayName: String,
        userPhotoURL   : String,
        feedPostId     : String,
    ) {
        if (libraryBookId.isBlank()) return
        val newId = UUID.randomUUID().toString()
        insertQuote(BookQuoteRow(
            id              = newId,
            bookId          = libraryBookId,
            authorId        = libraryAuthorId.ifBlank { null },
            bookTitle       = bookName,
            authorName      = authorName,
            text            = quoteText,
            uid             = uid,
            userDisplayName = userDisplayName,
            userPhotoUrl    = userPhotoURL,
            feedPostId      = feedPostId,
        ))
        val book = getBook(libraryBookId)
        if (book != null) updateBookCounters(libraryBookId, quoteCount = book.quoteCount + 1)
        if (libraryAuthorId.isNotBlank()) {
            val author = getAuthor(libraryAuthorId)
            if (author != null) updateAuthorCounters(libraryAuthorId, quoteCount = author.quoteCount + 1)
        }
    }

    // ── fetchBooksForAuthor ───────────────────────────────────────────────────

    suspend fun fetchBooksForAuthor(authorId: String): List<LibraryBookRow> =
        try { getBooksByAuthor(authorId) } catch (_: Exception) { emptyList() }

    // ── Reading Status ────────────────────────────────────────────────────────
    //  reading_status: uid + book_id birincil anahtar. Hem "library_books"
    //  (source = "library") hem "serials/books" (source = "serial"|"book")
    //  içerikleri için kullanılır. "Arkadaşlar ne okuyor?" şeridi ve profil
    //  okuma listesi buradan beslenir.

    suspend fun getReadingStatus(uid: String, limit: Int = 50): List<ReadingStatusRow> =
        db["reading_status"].select {
            filter { eq("uid", uid) }
            order("updated_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun upsertReadingStatus(row: ReadingStatusRow) {
        db["reading_status"].upsert(row)
    }

    suspend fun deleteReadingStatus(uid: String, bookId: String) {
        db["reading_status"].delete {
            filter {
                eq("uid", uid)
                eq("book_id", bookId)
            }
        }
    }

    /** "Arkadaşlar ne okuyor?" şeridi — takip edilen kullanıcıların 'okuyorum' durumundaki kayıtları */
    suspend fun getReadingStatusForUids(uids: List<String>, status: String = "okuyorum", limit: Int = 20): List<ReadingStatusRow> {
        if (uids.isEmpty()) return emptyList()
        return db["reading_status"].select {
            filter {
                isIn("uid", uids)
                eq("status", status)
            }
            order("updated_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()
    }

    // ── Daily Activity / Streak ──────────────────────────────────────────────
    //  Kurdî ders streak'inden bağımsız, genel uygulama etkileşim streak'i.
    //  Uygulama her foreground'a geldiğinde recordDailyActivity çağrılır,
    //  ardından computeStreak ile users/{uid}.streak güncellenir.

    suspend fun recordDailyActivity(uid: String) {
        if (uid.isBlank()) return
        val today = java.time.LocalDate.now().toString() // yyyy-MM-dd
        try {
            val existing = db["daily_activity"].select {
                filter { eq("uid", uid); eq("activity_date", today) }
                limit(1)
            }.decodeSingleOrNull<DailyActivityRow>()
            if (existing != null) {
                db["daily_activity"].update(
                    mapOf("actions" to existing.actions + 1)
                ) { filter { eq("uid", uid); eq("activity_date", today) } }
            } else {
                db["daily_activity"].insert(DailyActivityRow(uid = uid, activityDate = today, actions = 1))
            }
        } catch (_: Exception) { }
    }

    /** Bugün (veya dün, gün henüz bitmediyse) dahil ardışık aktif gün sayısı. */
    suspend fun computeStreak(uid: String): Int {
        if (uid.isBlank()) return 0
        return try {
            val rows = db["daily_activity"].select {
                filter { eq("uid", uid) }
                order("activity_date", Order.DESCENDING)
                limit(400)
            }.decodeList<DailyActivityRow>()
            if (rows.isEmpty()) return 0

            val dates = rows.mapNotNull {
                try { java.time.LocalDate.parse(it.activityDate) } catch (_: Exception) { null }
            }.toSet()

            var cursor = java.time.LocalDate.now()
            if (cursor !in dates) cursor = cursor.minusDays(1)

            var streak = 0
            while (cursor in dates) {
                streak++
                cursor = cursor.minusDays(1)
            }
            streak
        } catch (_: Exception) { 0 }
    }

    // ── Rozetler (user_badges) ───────────────────────────────────────────────
    //  Katalog Kotlin'de (BadgeCatalog); burada sadece kazanılan rozetler
    //  saklanır/okunur.

    /** "okudum" durumundaki kitap sayısı — Rozet/Profil özet kartı için. */
    suspend fun getBooksReadCount(uid: String): Int =
        try {
            db["reading_status"].select {
                filter { eq("uid", uid); eq("status", "okudum") }
            }.decodeList<ReadingStatusRow>().size
        } catch (_: Exception) { 0 }

    /** Kullanıcının eklediği toplam alıntı sayısı — Rozet/Profil özet kartı için. */
    suspend fun getQuotesSharedCount(uid: String): Int =
        try {
            db["book_quotes"].select {
                filter { eq("uid", uid) }
            }.decodeList<BookQuoteRow>().size
        } catch (_: Exception) { 0 }

    suspend fun getUserBadgeIds(uid: String): Set<String> {
        if (uid.isBlank()) return emptySet()
        return try {
            db["user_badges"].select {
                filter { eq("uid", uid) }
            }.decodeList<com.heftreng.app.data.model.UserBadgeRow>().map { it.badgeId }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    suspend fun awardBadge(uid: String, badgeId: String) {
        if (uid.isBlank()) return
        try {
            db["user_badges"].upsert(
                com.heftreng.app.data.model.UserBadgeRow(uid = uid, badgeId = badgeId)
            )
        } catch (_: Exception) { }
    }

    /** Kazanılması gereken yeni rozetleri hesaplar, kaydeder ve listesini döner. */
    suspend fun checkAndAwardBadges(uid: String, booksRead: Int, quotesShared: Int, streak: Int): Set<String> {
        if (uid.isBlank()) return emptySet()
        val eligible = com.heftreng.app.data.model.BadgeCatalog.eligibleIds(booksRead, quotesShared, streak)
        val existing = getUserBadgeIds(uid)
        val newOnes  = eligible - existing
        newOnes.forEach { awardBadge(uid, it) }
        return existing + newOnes
    }
}
