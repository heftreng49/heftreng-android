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
    // FAZ 1: Yeni eklenen sütun. `ignoreUnknownKeys` supabase-kt'de
    // varsayılan olarak açık DEĞİL (bilinen bir kütüphane kısıtlaması —
    // bkz. supabase-kt issue #334) — bu alan data class'ta OLMAZSA
    // sütun eklendikten sonra TÜM book_quotes sorguları
    // SerializationException ile patlar. Bu yüzden bu alan migration'ın
    // ayrılmaz bir parçası, atlanamaz.
    @SerialName("moderation_status")
    val moderationStatus  : String  = "active",
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
    // FAZ 1: bkz. BookQuoteRow'daki aynı açıklama — bu alan zorunlu.
    @SerialName("moderation_status")
    val moderationStatus  : String  = "active",
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

    /**
     * QuoteDialog için: yazar adı aratıp öneri formatında döner
     * (isim + varsa en güncel kitap adı + kitap sayısı). Firestore limit(50)
     * sınırından etkilenmez, doğrudan Supabase'de arar.
     */
    suspend fun searchAuthorsForQuote(query: String): List<com.heftreng.app.ui.component.QuoteSuggestion> {
        if (query.isBlank()) return emptyList()
        return try {
            searchAuthors(query).map { a ->
                val firstBook = try {
                    getBooksByAuthor(a.id).firstOrNull()?.title ?: ""
                } catch (_: Exception) { "" }
                com.heftreng.app.ui.component.QuoteSuggestion(
                    bookName   = firstBook,
                    authorName = a.name,
                    count      = a.quoteCount,
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun searchBooks(query: String): List<LibraryBookRow> =
        db["library_books"].select {
            filter { ilike("title", "%$query%") }
            limit(20)
        }.decodeList()

    /**
     * QuoteDialog için: kitap adı aratıp öneri formatında döner.
     * Firestore limit(50) sınırından etkilenmez, doğrudan Supabase'de arar.
     */
    suspend fun searchBooksForQuote(query: String): List<com.heftreng.app.ui.component.QuoteSuggestion> {
        if (query.isBlank()) return emptyList()
        return try {
            searchBooks(query).map { b ->
                com.heftreng.app.ui.component.QuoteSuggestion(
                    bookName   = b.title,
                    authorName = b.authorName,
                    coverImg   = b.coverImg,
                    count      = b.quoteCount,
                )
            }
        } catch (_: Exception) { emptyList() }
    }

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

    /** Sorun 2: Kapak değişince book_quotes'taki eski kayıtları güncelle */
    suspend fun updateQuotesCover(bookId: String, coverImg: String) {
        db["book_quotes"].update(mapOf("cover_img" to coverImg)) {
            filter { eq("book_id", bookId) }
        }
    }

    /** Sorun 2: Kapak değişince reading_status'taki okuma listesi kayıtlarını güncelle */
    suspend fun updateReadingStatusCover(bookId: String, coverImg: String) {
        db["reading_status"].update(mapOf("cover_img" to coverImg)) {
            filter { and { eq("book_id", bookId); eq("source", "library") } }
        }
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

    /** Yazar/Kitap detay ekranı için: aktif + banlı olmayan alıntılar.
     *  LegacyQuoteListPage'in (Firestore, 2×limit(50)) yerini alır. */
    suspend fun getActiveQuotesByBookName(bookName: String, limit: Int = 50): List<BookQuoteRow> {
        if (bookName.isBlank()) return emptyList()
        val banned = getBannedUids()
        return db["book_quotes"].select {
            filter {
                ilike("book_title", bookName.trim())
                eq("moderation_status", "active")
            }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<BookQuoteRow>().filter { it.uid !in banned }
    }

    suspend fun getActiveQuotesByAuthorName(authorName: String, limit: Int = 50): List<BookQuoteRow> {
        if (authorName.isBlank()) return emptyList()
        val banned = getBannedUids()
        return db["book_quotes"].select {
            filter {
                ilike("author_name", authorName.trim())
                eq("moderation_status", "active")
            }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<BookQuoteRow>().filter { it.uid !in banned }
    }

    /** Profil — "X alıntı" kartına basınca kullanıcının paylaştığı tüm alıntılar */
    suspend fun getQuotesByUser(uid: String, limit: Int = 100): List<BookQuoteRow> =
        db["book_quotes"].select {
            filter { eq("uid", uid) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    /** FAZ 1 devamı: `users` tablosunda `banned = true` olan uid'lerin
     *  kümesini döndürür. Supabase-kt doğrudan JOIN desteklemediği için
     *  (bkz. FeedViewModel.fetchSuggestedUsersPage'deki aynı desen),
     *  banlı uid'ler ayrı bir sorguyla çekilip client-side dışlanıyor.
     *  Hata olursa boş küme döner — banned filtresi devre dışı kalır ama
     *  ekran hiç açılmaz hale gelmez (fail-open, sadece bu filtre için). */
    private suspend fun getBannedUids(): Set<String> = try {
        db["users"].select {
            filter { eq("banned", true) }
        }.decodeList<com.heftreng.app.data.model.UserRow>().map { it.uid }.toSet()
    } catch (e: Exception) {
        android.util.Log.w("LibraryRepo", "getBannedUids: ${e.message}")
        emptySet()
    }

    /** Keşfet → Alıntılar — en son eklenen kütüphane alıntıları.
     *  ÖNCEKİ: Firestore `feed` (type='library_quote' + 300'lük legacy tarama, 2 sorgu).
     *  ŞİMDİ:  Supabase `book_quotes` — 1 sorgu, RLS public read.
     *  FAZ 1: `moderation_status = 'active'` filtresi eklendi — moderatör
     *  Firestore feed'de bir alıntıyı kaldırdığında (moderatePost), bu artık
     *  book_quotes tarafına da yansıyor (bkz. setQuoteModerationStatusByFeedPostId)
     *  ve buradaki filtre sayesinde Kütüphane ekranında görünmeye devam etmiyor.
     *  FAZ 1 devamı: banlı kullanıcıların alıntıları da client-side dışlanıyor
     *  (bkz. getBannedUids) — sayfalama limitinden az sonuç dönebilir ama
     *  bu ekran zaten "son eklenenler" akışı, kesin toplam sayı garantisi yok. */
    suspend fun getRecentQuotes(limit: Int = 20, offset: Int = 0): List<BookQuoteRow> {
        val banned = getBannedUids()
        return db["book_quotes"].select {
            filter { eq("moderation_status", "active") }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
            if (offset > 0) range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<BookQuoteRow>().filter { it.uid !in banned }
    }

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

    /** FAZ 1: Moderatör Firestore feed'de bir alıntıyı kaldırdığında/geri
     *  getirdiğinde (AdminViewModel.moderatePost/restorePost), Supabase
     *  tarafındaki karşılığını da güncelle. `status` "active" veya
     *  "removed" (ya da moderatePost'un kullandığı diğer durumlarla aynı)
     *  olmalı — Firestore'daki moderationStatus değeriyle birebir aynı
     *  string kullanılıyor, ayrıca bir eşleme tablosu yok.
     *  Satır bulunamazsa (ör. bu alıntı bir kitap alıntısı değilse) sessizce
     *  hiçbir şey yapmaz — hata fırlatmaz, çünkü her feed post'u bir kitap
     *  alıntısı olmak zorunda değil. */
    suspend fun setQuoteModerationStatusByFeedPostId(feedPostId: String, status: String) {
        if (feedPostId.isBlank()) return
        try {
            db["book_quotes"].update(mapOf("moderation_status" to status)) {
                filter { eq("feed_post_id", feedPostId) }
            }
        } catch (e: Exception) {
            android.util.Log.w("LibraryRepo", "setQuoteModerationStatusByFeedPostId: ${e.message}")
        }
    }

    /** FAZ 1 devamı: Kullanıcı feed'deki kitap alıntısı gönderisini normal
     *  şekilde sildiğinde (FeedViewModel.deletePost), Supabase'deki
     *  book_quotes satırı da silinsin — aksi halde alıntı feed'den
     *  kaybolduğu halde Kütüphane ekranında görünmeye devam ediyordu.
     *  feed_post_id ile eşleşen satır yoksa (bu gönderi bir kitap alıntısı
     *  değilse) sessizce hiçbir şey yapmaz. */
    suspend fun deleteQuoteByFeedPostId(feedPostId: String) {
        if (feedPostId.isBlank()) return
        try {
            db["book_quotes"].delete { filter { eq("feed_post_id", feedPostId) } }
        } catch (e: Exception) {
            android.util.Log.w("LibraryRepo", "deleteQuoteByFeedPostId: ${e.message}")
        }
    }

    suspend fun deleteQuote(id: String) {
        db["book_quotes"].delete { filter { eq("id", id) } }
    }

    /** ESKİ: book_quotes.likes_count'u doğrudan artırıp azaltıyordu — feed_likes ile
     *  senkron değildi, bu yüzden kütüphane akışı ile kitap/yazar detay sayfası farklı
     *  beğeni sayıları gösteriyordu. Artık feed_likes tek kaynak; bkz. toggleQuoteFeedLike. */
    @Deprecated("feed_likes tabanlı toggleQuoteFeedLike kullan")
    suspend fun incrementQuoteLikes(id: String, delta: Int) {
        val row = db["book_quotes"].select {
            filter { eq("id", id) }
            limit(1)
        }.decodeSingleOrNull<BookQuoteRow>() ?: return
        db["book_quotes"].update(
            mapOf("likes_count" to (row.likesCount + delta).coerceAtLeast(0))
        ) { filter { eq("id", id) } }
    }

    /** Verilen feed gönderi id'leri için gerçek beğeni sayılarını ve giriş yapan
     *  kullanıcının beğenip beğenmediğini feed_likes tablosundan döndürür.
     *  Hem kütüphane akışı (ConnectedPostCard) hem de kitap/yazar detay sayfası
     *  (BookQuoteCard) artık aynı tabloyu okuyup yazdığı için sayılar her zaman eşleşir. */
    suspend fun getQuoteLikeStates(feedPostIds: List<String>): Map<String, Pair<Int, Boolean>> {
        val ids = feedPostIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val myUid = auth.currentUser?.uid.orEmpty()
        val rows = try {
            db["feed_likes"].select {
                filter { isIn("post_id", ids) }
            }.decodeList<com.heftreng.app.data.model.FeedLikeRow>()
        } catch (e: Exception) { return emptyMap() }
        val counts = rows.groupingBy { it.postId }.eachCount()
        val likedByMe = rows.filter { it.uid == myUid }.map { it.postId }.toSet()
        return ids.associateWith { (counts[it] ?: 0) to (it in likedByMe) }
    }

    /** Bir alıntının (kütüphane akışındaki karşılığı olan) feed gönderisini
     *  feed_likes tablosu üzerinden beğenir/beğeniyi geri alır. Dönüş: yeni (sayı, beğenildi mi). */
    suspend fun toggleQuoteFeedLike(feedPostId: String, myName: String, myPhoto: String): Pair<Int, Boolean> {
        val myUid = auth.currentUser?.uid.orEmpty()
        if (myUid.isEmpty() || feedPostId.isBlank()) return 0 to false
        val existing = try {
            db["feed_likes"].select {
                filter { eq("post_id", feedPostId); eq("uid", myUid) }
                limit(1)
            }.decodeList<com.heftreng.app.data.model.FeedLikeRow>()
        } catch (e: Exception) { emptyList() }

        if (existing.isEmpty()) {
            db["feed_likes"].insert(
                mapOf(
                    "id"        to "${feedPostId}_$myUid",
                    "post_id"   to feedPostId,
                    "uid"       to myUid,
                    "name"      to myName,
                    "photo_url" to myPhoto,
                )
            )
        } else {
            db["feed_likes"].delete { filter { eq("post_id", feedPostId); eq("uid", myUid) } }
        }

        val count = try {
            db["feed_likes"].select { filter { eq("post_id", feedPostId) } }
                .decodeList<com.heftreng.app.data.model.FeedLikeRow>().size
        } catch (e: Exception) { 0 }
        return count to existing.isEmpty()
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
     *  ŞİMDİ: 1 sorgu, doğrudan en son incelemeler.
     *  FAZ 1: `moderation_status = 'active'` filtresi eklendi — bkz.
     *  getRecentQuotes'daki aynı açıklama. Banned filtresi de aynı şekilde
     *  eklendi (bkz. getBannedUids). */
    suspend fun getRecentReviews(limit: Int = 50): List<BookReviewRow> {
        val banned = getBannedUids()
        return db["book_reviews"].select {
            filter { eq("moderation_status", "active") }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<BookReviewRow>().filter { it.uid !in banned }
    }

    suspend fun insertReview(row: BookReviewRow) {
        db["book_reviews"].insert(row)
    }

    suspend fun updateReviewText(id: String, newText: String, newRating: Float) {
        db["book_reviews"].update(
            mapOf("text" to newText, "rating" to newRating)
        ) { filter { eq("id", id) } }
    }

    /** FAZ 1: bkz. setQuoteModerationStatusByFeedPostId'deki aynı açıklama —
     *  incelemeler için karşılık gelen senkron fonksiyonu. */
    suspend fun setReviewModerationStatusByFeedPostId(feedPostId: String, status: String) {
        if (feedPostId.isBlank()) return
        try {
            db["book_reviews"].update(mapOf("moderation_status" to status)) {
                filter { eq("feed_post_id", feedPostId) }
            }
        } catch (e: Exception) {
            android.util.Log.w("LibraryRepo", "setReviewModerationStatusByFeedPostId: ${e.message}")
        }
    }

    /** FAZ 1 devamı: bkz. deleteQuoteByFeedPostId'deki aynı açıklama —
     *  incelemeler için karşılık gelen fonksiyon. */
    suspend fun deleteReviewByFeedPostId(feedPostId: String) {
        if (feedPostId.isBlank()) return
        try {
            db["book_reviews"].delete { filter { eq("feed_post_id", feedPostId) } }
        } catch (e: Exception) {
            android.util.Log.w("LibraryRepo", "deleteReviewByFeedPostId: ${e.message}")
        }
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

    /**
     * Benzer isimde yazar var mı diye arar (yazım hatalarını da yakalar).
     * SADECE bilgi amaçlıdır — otomatik birleştirme yapmaz. UI'da
     * "Böyle bir yazar zaten var, bunu mu demek istediniz?" onayı için kullan.
     */
    suspend fun findSimilarAuthors(name: String, minSimilarity: Double = 0.35): List<AuthorRow> {
        if (name.isBlank()) return emptyList()
        return try {
            db.rpc(
                "find_similar_author",
                kotlinx.serialization.json.buildJsonObject {
                    put("search_name", kotlinx.serialization.json.JsonPrimitive(name.trim()))
                    put("min_similarity", kotlinx.serialization.json.JsonPrimitive(minSimilarity))
                }
            ).decodeList<AuthorRow>()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun findSimilarBooks(title: String, minSimilarity: Double = 0.35): List<LibraryBookRow> {
        if (title.isBlank()) return emptyList()
        return try {
            db.rpc(
                "find_similar_book",
                kotlinx.serialization.json.buildJsonObject {
                    put("search_title", kotlinx.serialization.json.JsonPrimitive(title.trim()))
                    put("min_similarity", kotlinx.serialization.json.JsonPrimitive(minSimilarity))
                }
            ).decodeList<LibraryBookRow>()
        } catch (_: Exception) { emptyList() }
    }

    private fun normalizeName(s: String) =
        s.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Yazarı normalize edilmiş isimle arar (trim + boşluk + case farklarını yakalar).
     * Bulunamazsa yeni kayıt açar. Fuzzy/yazım-hatası eşleştirmesi YAPMAZ —
     * yanlışlıkla iki farklı yazarı birleştirme riskini almamak için.
     */
    private suspend fun findOrCreateAuthor(name: String): String {
        return try {
            // name_normalized sütunu üzerinden TAM eşleşme (bkz. scripts/add_author_dedup.sql)
            val existing = db["authors"].select {
                filter { eq("name_normalized", normalizeName(name)) }
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
                filter { eq("title_normalized", normalizeName(title)) }
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
        val newId    = UUID.randomUUID().toString()
        val coverUrl = try { getBook(libraryBookId)?.coverImg ?: "" } catch (_: Exception) { "" }
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
            coverImg        = coverUrl,
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

    /** "okudum" durumundaki kitap sayısı — Rozet/Profil özet kartı için.
     *  count(EXACT) + limit(0): tüm satırları çekmeden sadece sayı alır. */
    suspend fun getBooksReadCount(uid: String): Int =
        try {
            db["reading_status"].select {
                filter { eq("uid", uid); eq("status", "okudum") }
                count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                limit(0)
            }.countOrNull()?.toInt() ?: 0
        } catch (_: Exception) { 0 }

    /** Kullanıcının eklediği toplam alıntı sayısı — Rozet/Profil özet kartı için.
     *  count(EXACT) + limit(0): tüm satırları çekmeden sadece sayı alır. */
    suspend fun getQuotesSharedCount(uid: String): Int =
        try {
            db["book_quotes"].select {
                filter { eq("uid", uid) }
                count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                limit(0)
            }.countOrNull()?.toInt() ?: 0
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
