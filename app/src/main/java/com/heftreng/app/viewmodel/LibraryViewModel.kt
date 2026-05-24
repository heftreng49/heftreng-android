package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Author
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════
//  LibraryViewModel
//
//  Sorumlu olduğu koleksiyonlar:
//    authors/{authorId}
//      follows/{uid}            → takip ilişkisi
//      books/{bookId}           → libary_books referans (isteğe bağlı sub)
//    library_books/{bookId}
//      quotes/{quoteId}
//      reviews/{reviewId}
//
//  Her alıntı / inceleme aynı anda:
//    • library_books/{id}/quotes (veya reviews)  ← detay sayfası için
//    • authors/{authorId} quoteCount/reviewCount  ← sayaç güncelleme
//    • feed/{newId}                               ← sosyal akış için
//    • users/{uid}/posts/{newId}                  ← profil için
// ═══════════════════════════════════════════════════════════════════════

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val library  : LibraryRepository,   // Adım 2.1 — ensureAuthorAndBook buradan
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────
    private val _authors       = MutableStateFlow<List<Author>>(emptyList())
    val authors = _authors.asStateFlow()

    private val _selectedAuthor = MutableStateFlow<Author?>(null)
    val selectedAuthor = _selectedAuthor.asStateFlow()

    private val _authorBooks   = MutableStateFlow<List<LibraryBook>>(emptyList())
    val authorBooks = _authorBooks.asStateFlow()

    private val _authorQuotes  = MutableStateFlow<List<BookQuote>>(emptyList())
    val authorQuotes = _authorQuotes.asStateFlow()

    private val _authorReviews = MutableStateFlow<List<BookReview>>(emptyList())
    val authorReviews = _authorReviews.asStateFlow()

    private val _selectedBook   = MutableStateFlow<LibraryBook?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _bookQuotes    = MutableStateFlow<List<BookQuote>>(emptyList())
    val bookQuotes = _bookQuotes.asStateFlow()

    private val _bookReviews   = MutableStateFlow<List<BookReview>>(emptyList())
    val bookReviews = _bookReviews.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isFollowingAuthor = MutableStateFlow(false)
    val isFollowingAuthor = _isFollowingAuthor.asStateFlow()

    val myUid get() = auth.currentUser?.uid ?: ""
    val myName get() = auth.currentUser?.displayName ?: ""
    val myPhoto get() = auth.currentUser?.photoUrl?.toString() ?: ""

    // ── Kütüphane Genel Bakışı — LibraryScreen için ───────────────────────
    // Adım 4.1 — Daha önce bu sorgular doğrudan LibraryScreen Composable'ında
    // LaunchedEffect içinde çalışıyordu. MVVM ihlali düzeltildi.
    fun loadLibraryOverview() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Tüm alıntılar (collectionGroup)
                val qSnap = firestore.collectionGroup("quotes")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()
                _bookQuotes.value = qSnap.documents.mapNotNull { doc ->
                    doc.toObject(BookQuote::class.java)?.copy(id = doc.id)
                }
                // Tüm incelemeler (collectionGroup)
                val rSnap = firestore.collectionGroup("reviews")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()
                _bookReviews.value = rSnap.documents.mapNotNull { doc ->
                    doc.toObject(BookReview::class.java)?.copy(id = doc.id)
                }
                // Kütüphane kitapları (library_books koleksiyonu)
                val bSnap = firestore.collection("library_books")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()
                _authorBooks.value = bSnap.documents.mapNotNull { doc ->
                    doc.toObject(LibraryBook::class.java)?.copy(id = doc.id)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Yazar Listesi ─────────────────────────────────────────────────────
    fun loadAuthors() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("authors")
                    .orderBy("name")
                    .limit(100)
                    .get().await()
                _authors.value = snap.documents.mapNotNull { doc ->
                    doc.toObject(Author::class.java)?.copy(id = doc.id)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Yazar Detayı ──────────────────────────────────────────────────────
    fun loadAuthor(authorId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = firestore.collection("authors").document(authorId).get().await()
                val author = doc.toObject(Author::class.java)?.copy(id = doc.id)
                _selectedAuthor.value = author

                // Takip durumu
                if (myUid.isNotBlank()) {
                    val followDoc = firestore.collection("authors").document(authorId)
                        .collection("follows").document(myUid).get().await()
                    _isFollowingAuthor.value = followDoc.exists()
                }

                // Yazarın kitapları
                loadBooksByAuthor(authorId)
                // Yazarın alıntıları (tüm kitaplardan)
                loadAuthorQuotes(authorId)
                // Yazarın incelemeleri
                loadAuthorReviews(authorId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun loadBooksByAuthor(authorId: String) {
        val snap = firestore.collection("library_books")
            .whereEqualTo("authorId", authorId)
            .orderBy("publishYear", Query.Direction.DESCENDING)
            .limit(50).get().await()
        _authorBooks.value = snap.documents.mapNotNull { doc ->
            doc.toObject(LibraryBook::class.java)?.copy(id = doc.id)
        }
    }

    private suspend fun loadAuthorQuotes(authorId: String) {
        // authors/{authorId} için tüm library_books altındaki quotes collection group
        val snap = firestore.collectionGroup("quotes")
            .whereEqualTo("authorId", authorId)
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50).get().await()
        _authorQuotes.value = snap.documents.mapNotNull { doc ->
            doc.toObject(BookQuote::class.java)?.copy(id = doc.id)
        }
    }

    private suspend fun loadAuthorReviews(authorId: String) {
        val snap = firestore.collectionGroup("reviews")
            .whereEqualTo("authorId", authorId)
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50).get().await()
        _authorReviews.value = snap.documents.mapNotNull { doc ->
            doc.toObject(BookReview::class.java)?.copy(id = doc.id)
        }
    }

    // ── Yazar Takip ───────────────────────────────────────────────────────
    fun toggleFollowAuthor(authorId: String) {
        if (myUid.isBlank()) return
        viewModelScope.launch {
            val followRef = firestore.collection("authors").document(authorId)
                .collection("follows").document(myUid)
            val authorRef = firestore.collection("authors").document(authorId)
            val following = _isFollowingAuthor.value

            if (following) {
                followRef.delete().await()
                authorRef.update("followerCount", FieldValue.increment(-1)).await()
                _isFollowingAuthor.value = false
                _selectedAuthor.value = _selectedAuthor.value?.copy(
                    followerCount = (_selectedAuthor.value?.followerCount ?: 1) - 1
                )
            } else {
                followRef.set(mapOf("uid" to myUid, "ts" to Timestamp.now())).await()
                authorRef.update("followerCount", FieldValue.increment(1)).await()
                _isFollowingAuthor.value = true
                _selectedAuthor.value = _selectedAuthor.value?.copy(
                    followerCount = (_selectedAuthor.value?.followerCount ?: 0) + 1
                )
            }
        }
    }

    // ── Yazar Oluştur (Admin) ─────────────────────────────────────────────
    fun createAuthor(
        name       : String,
        bio        : String,
        photoURL   : String = "",
        birthYear  : Int    = 0,
        nationality: String = "",
    ) {
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "name"        to name,
                    "bio"         to bio,
                    "photoURL"    to photoURL,
                    "birthYear"   to birthYear,
                    "nationality" to nationality,
                    "bookCount"   to 0,
                    "quoteCount"  to 0,
                    "reviewCount" to 0,
                    "followerCount" to 0,
                    "ts"          to Timestamp.now(),
                )
                firestore.collection("authors").add(data).await()
                loadAuthors()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Kitap Detayı ──────────────────────────────────────────────────────
    fun loadLibraryBook(bookId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = firestore.collection("library_books").document(bookId).get().await()
                _selectedBook.value = doc.toObject(LibraryBook::class.java)?.copy(id = doc.id)
                loadBookQuotes(bookId)
                loadBookReviews(bookId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun loadBookQuotes(bookId: String) {
        val snap = firestore.collection("library_books").document(bookId)
            .collection("quotes")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50).get().await()
        _bookQuotes.value = snap.documents.mapNotNull { doc ->
            doc.toObject(BookQuote::class.java)?.copy(id = doc.id)
        }
    }

    private suspend fun loadBookReviews(bookId: String) {
        val snap = firestore.collection("library_books").document(bookId)
            .collection("reviews")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50).get().await()
        _bookReviews.value = snap.documents.mapNotNull { doc ->
            doc.toObject(BookReview::class.java)?.copy(id = doc.id)
        }
    }

    // ── Kütüphane Kitabı Oluştur ──────────────────────────────────────────
    fun createLibraryBook(
        title      : String,
        authorId   : String,
        authorName : String,
        genre      : String = "",
        publishYear: Int    = 0,
        synopsis   : String = "",
        pageCount  : Int    = 0,
        coverImg   : String = "",
    ) {
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "title"       to title,
                    "authorId"    to authorId,
                    "authorName"  to authorName,
                    "coverImg"    to coverImg,
                    "genre"       to genre,
                    "publishYear" to publishYear,
                    "synopsis"    to synopsis,
                    "pageCount"   to pageCount,
                    "quoteCount"  to 0,
                    "reviewCount" to 0,
                    "avgRating"   to 0f,
                    "ts"          to Timestamp.now(),
                )
                firestore.collection("library_books").add(data).await()
                // Yazar kitap sayacını artır
                firestore.collection("authors").document(authorId)
                    .update("bookCount", FieldValue.increment(1)).await()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Alıntı Ekle ───────────────────────────────────────────────────────
    // library_books/{id}/quotes  +  authors/{id} sayaç  +  feed  +  users/{uid}/posts
    fun addBookQuote(book: LibraryBook, quoteText: String) {
        if (myUid.isBlank() || quoteText.isBlank()) return
        viewModelScope.launch {
            try {
                val now = Timestamp.now()
                val quoteData = hashMapOf(
                    "bookId"          to book.id,
                    "authorId"        to book.authorId,
                    "bookTitle"       to book.title,
                    "authorName"      to book.authorName,
                    "text"            to quoteText,
                    "uid"             to myUid,
                    "userDisplayName" to myName,
                    "userPhotoURL"    to myPhoto,
                    "likesCount"      to 0,
                    "ts"              to now,
                )

                // 1. library_books/{id}/quotes
                val quoteRef = firestore.collection("library_books").document(book.id)
                    .collection("quotes").add(quoteData).await()

                // 2. feed'e yaz — Adım 2.2: nested "quote" objesi kaldırıldı, yalnızca flat alanlar
                val feedData = hashMapOf(
                    "uid"             to myUid,
                    "displayName"     to myName,
                    "name"            to myName,   // legacy tema uyumu
                    "photoURL"        to myPhoto,
                    "quoteText"       to quoteText,
                    "bookName"        to book.title,
                    "authorName"      to book.authorName,
                    "libraryBookId"   to book.id,
                    "libraryAuthorId" to book.authorId,
                    "type"            to "library_quote",
                    "likes" to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"              to now,
                )
                val feedRef = firestore.collection("feed").add(feedData).await()

                // 3. quoteRef'e feedPostId geri yaz
                quoteRef.update("feedPostId", feedRef.id)

                // 4. Sayaçları güncelle
                firestore.collection("library_books").document(book.id)
                    .update("quoteCount", FieldValue.increment(1))
                firestore.collection("authors").document(book.authorId)
                    .update("quoteCount", FieldValue.increment(1))

                // 5. Yerel state güncelle
                loadBookQuotes(book.id)
                loadAuthorQuotes(book.authorId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── İnceleme Ekle ────────────────────────────────────────────────────
    fun addBookReview(book: LibraryBook, reviewText: String, rating: Float) {
        if (myUid.isBlank() || reviewText.isBlank()) return
        viewModelScope.launch {
            try {
                val now = Timestamp.now()
                val reviewData = hashMapOf(
                    "bookId"          to book.id,
                    "authorId"        to book.authorId,
                    "bookTitle"       to book.title,
                    "authorName"      to book.authorName,
                    "text"            to reviewText,
                    "rating"          to rating,
                    "uid"             to myUid,
                    "userDisplayName" to myName,
                    "userPhotoURL"    to myPhoto,
                    "ts"              to now,
                )

                // 1. library_books/{id}/reviews
                val reviewRef = firestore.collection("library_books").document(book.id)
                    .collection("reviews").add(reviewData).await()

                // 2. feed'e yaz
                val feedData = hashMapOf(
                    "uid"            to myUid,
                    "displayName"    to myName,
                    "name"           to myName,
                    "photoURL"       to myPhoto,
                    "text"           to reviewText,
                    "bookName"       to book.title,
                    "authorName"     to book.authorName,
                    "rating"         to rating,
                    "libraryBookId"  to book.id,
                    "libraryAuthorId" to book.authorId,
                    "type"           to "library_review",
                    "ts"             to now,
                )
                val feedRef = firestore.collection("feed").add(feedData).await()
                reviewRef.update("feedPostId", feedRef.id)

                // 3. Sayaçlar + ortalama puan
                val snap = firestore.collection("library_books").document(book.id)
                    .collection("reviews").get().await()
                val ratings = snap.documents.mapNotNull { it.getDouble("rating")?.toFloat() }
                val avg = if (ratings.isNotEmpty()) ratings.average().toFloat() else rating

                firestore.collection("library_books").document(book.id).update(
                    mapOf(
                        "reviewCount" to ratings.size,
                        "avgRating"   to avg,
                    )
                )
                firestore.collection("authors").document(book.authorId)
                    .update("reviewCount", FieldValue.increment(1))

                // 4. Yerel state güncelle
                loadBookReviews(book.id)
                loadAuthorReviews(book.authorId)
                // Kitabı yenile
                val updatedDoc = firestore.collection("library_books").document(book.id).get().await()
                _selectedBook.value = updatedDoc.toObject(LibraryBook::class.java)?.copy(id = book.id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Eski feed/quotes uyumluluk yüklemesi ─────────────────────────────
    // authorName veya bookName alanı üzerinden feed'den alıntıları getir
    fun loadLegacyFeedQuotesByAuthor(authorName: String) {
        viewModelScope.launch {
            try {
                val snap1 = firestore.collection("feed")
                    .whereEqualTo("quote.author", authorName).limit(50).get().await()
                val snap2 = firestore.collection("feed")
                    .whereEqualTo("authorName", authorName).limit(50).get().await()

                val all = (snap1.documents + snap2.documents).distinctBy { it.id }
                val mapped = all.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val qObj = d["quote"] as? Map<*, *>
                    val text = (qObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                        ?: d["quoteText"] as? String ?: return@mapNotNull null
                    BookQuote(
                        id              = doc.id,
                        text            = text,
                        bookTitle       = (qObj?.get("book") as? String) ?: d["bookName"] as? String ?: "",
                        authorName      = authorName,
                        uid             = d["uid"] as? String ?: "",
                        userDisplayName = (d["name"] as? String) ?: d["displayName"] as? String ?: "",
                        userPhotoURL    = d["photoURL"] as? String ?: "",
                        ts              = d["ts"] as? Timestamp,
                    )
                }.sortedByDescending { it.ts?.seconds ?: 0L }

                // Sadece yazar tabına ekliyoruz; authorQuotes state'ini eski + yeni olarak birleştir
                val existing = _authorQuotes.value
                val ids = existing.map { it.id }.toSet()
                _authorQuotes.value = existing + mapped.filter { it.id !in ids }
            } catch (_: Exception) {}
        }
    }

    fun clearError() { _error.value = null }

    // ══════════════════════════════════════════════════════════════════════
    //  OTOMATİK YAZAR / KİTAP OLUŞTURMA
    //  Adım 2.1 — Duplicate implementasyon kaldırıldı.
    //  LibraryRepository.ensureAuthorAndBook() kullanılır.
    // ══════════════════════════════════════════════════════════════════════

    suspend fun ensureAuthorAndBook(
        authorName: String,
        bookName  : String,
    ): Pair<String, String> = library.ensureAuthorAndBook(authorName, bookName)

    // ══════════════════════════════════════════════════════════════════════
    //  ESKİ FEED ALINTILARI → KİTAP / YAZAR SAYFASINA ENTEGRASYON
    //  Mevcut feed belgelerinde libraryBookId/libraryAuthorId boş olanları
    //  tarayıp bookName/authorName üzerinden eşleştirerek doldurur.
    //  Admin veya background job olarak tek seferlik çalıştırılır.
    // ══════════════════════════════════════════════════════════════════════
    fun migrateLegacyFeedQuotes(onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                // 1. libraryBookId boş olan, quoteText dolu feed belgelerini getir
                val snap = firestore.collection("feed")
                    .whereEqualTo("libraryBookId", "")
                    .limit(200).get().await()

                val docs = snap.documents.filter { doc ->
                    val d = doc.data ?: return@filter false
                    val qObj   = d["quote"] as? Map<*, *>
                    val qText  = (qObj?.get("text") as? String)?.isNotBlank() == true
                        || (d["quoteText"] as? String)?.isNotBlank() == true
                    val bName  = (qObj?.get("book") as? String)?.isNotBlank() == true
                        || (d["bookName"] as? String)?.isNotBlank() == true
                    qText && bName
                }

                val total = docs.size
                var done  = 0

                docs.forEach { doc ->
                    val d          = doc.data ?: return@forEach
                    val qObj       = d["quote"] as? Map<*, *>
                    val bookName   = ((qObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                        ?: d["bookName"] as? String ?: "").trim()
                    val authorName = ((qObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                        ?: d["authorName"] as? String ?: "").trim()

                    if (bookName.isBlank() && authorName.isBlank()) { done++; return@forEach }

                    val (authorId, bookId) = ensureAuthorAndBook(authorName, bookName)

                    val updates = mutableMapOf<String, Any>()
                    if (authorId.isNotBlank()) updates["libraryAuthorId"] = authorId
                    if (bookId.isNotBlank())   updates["libraryBookId"]   = bookId

                    if (updates.isNotEmpty()) {
                        try { doc.reference.update(updates).await() } catch (_: Exception) {}
                    }

                    // Ayrıca library_books/{bookId}/quotes'a ekle (yoksa)
                    if (bookId.isNotBlank()) {
                        val qText = (qObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                            ?: d["quoteText"] as? String ?: ""
                        if (qText.isNotBlank()) {
                            migrateQuoteToLibrary(
                                feedDocId  = doc.id,
                                bookId     = bookId,
                                authorId   = authorId,
                                bookTitle  = bookName,
                                authorName = authorName,
                                quoteText  = qText,
                                uid        = d["uid"] as? String ?: "",
                                userName   = (d["name"] as? String)?.takeIf { it.isNotBlank() }
                                    ?: d["displayName"] as? String ?: "",
                                userPhoto  = d["photoURL"] as? String ?: "",
                                ts         = d["ts"] as? com.google.firebase.Timestamp,
                            )
                        }
                    }

                    done++
                    onProgress(done, total)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private suspend fun migrateQuoteToLibrary(
        feedDocId : String,
        bookId    : String,
        authorId  : String,
        bookTitle : String,
        authorName: String,
        quoteText : String,
        uid       : String,
        userName  : String,
        userPhoto : String,
        ts        : com.google.firebase.Timestamp?,
    ) {
        // Aynı feedPostId zaten varsa ekleme
        val existing = firestore.collection("library_books").document(bookId)
            .collection("quotes")
            .whereEqualTo("feedPostId", feedDocId)
            .limit(1).get().await()
        if (!existing.isEmpty) return

        val data = hashMapOf(
            "bookId"          to bookId,
            "authorId"        to authorId,
            "bookTitle"       to bookTitle,
            "authorName"      to authorName,
            "text"            to quoteText,
            "uid"             to uid,
            "userDisplayName" to userName,
            "userPhotoURL"    to userPhoto,
            "feedPostId"      to feedDocId,
            "likesCount"      to 0,
            "ts"              to (ts ?: com.google.firebase.Timestamp.now()),
        )
        try {
            firestore.collection("library_books").document(bookId)
                .collection("quotes").add(data).await()
            firestore.collection("library_books").document(bookId)
                .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(1))
            if (authorId.isNotBlank()) {
                firestore.collection("authors").document(authorId)
                    .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(1))
            }
        } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADMİN: Yazar / Kitap Güncelleme
    // ══════════════════════════════════════════════════════════════════════
    fun updateAuthor(
        authorId   : String,
        name       : String,
        bio        : String,
        photoURL   : String,
        birthYear  : Int,
        nationality: String,
    ) {
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "name"        to name,
                    "nameLower"   to name.trim().lowercase(),
                    "bio"         to bio,
                    "photoURL"    to photoURL,
                    "birthYear"   to birthYear,
                    "nationality" to nationality,
                    "updatedAt"   to com.google.firebase.Timestamp.now(),
                )
                firestore.collection("authors").document(authorId).update(data).await()
                // State güncelle
                _selectedAuthor.value = _selectedAuthor.value?.copy(
                    name        = name,
                    bio         = bio,
                    photoURL    = photoURL,
                    birthYear   = birthYear,
                    nationality = nationality,
                )
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateLibraryBook(
        bookId     : String,
        title      : String,
        synopsis   : String,
        genre      : String,
        publishYear: Int,
        pageCount  : Int,
        coverImg   : String,
    ) {
        viewModelScope.launch {
            try {
                val data = mapOf(
                    "title"       to title,
                    "titleLower"  to title.trim().lowercase(),
                    "synopsis"    to synopsis,
                    "genre"       to genre,
                    "publishYear" to publishYear,
                    "pageCount"   to pageCount,
                    "coverImg"    to coverImg,
                    "updatedAt"   to com.google.firebase.Timestamp.now(),
                )
                firestore.collection("library_books").document(bookId).update(data).await()
                _selectedBook.value = _selectedBook.value?.copy(
                    title       = title,
                    synopsis    = synopsis,
                    genre       = genre,
                    publishYear = publishYear,
                    pageCount   = pageCount,
                    coverImg    = coverImg,
                )
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
