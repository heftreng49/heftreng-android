package com.heftreng.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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

    /** Profil "okuma özet kartı" için — kullanıcının paylaştığı alıntı sayısı */
    suspend fun countQuotesByUser(uid: String): Int =
        try {
            db["book_quotes"].select {
                filter { eq("uid", uid) }
            }.decodeList<BookQuoteRow>().size
        } catch (e: Exception) { 0 }

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
}
