package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.Author
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.data.repository.AuthorRow
import com.heftreng.app.data.repository.BookQuoteRow
import com.heftreng.app.data.repository.BookReviewRow
import com.heftreng.app.data.repository.LibraryBookRow
import com.heftreng.app.data.repository.LibraryRepository
import com.heftreng.app.data.local.AuthorDao
import com.heftreng.app.data.local.BookDao
import com.heftreng.app.data.local.CachedAuthor
import com.heftreng.app.data.local.CachedBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════
//  LibraryViewModel — Supabase tabanlı (v2)
//
//  Okuma + Yazma: Supabase (authors, library_books, book_quotes, book_reviews)
//  Feed yazımı: Firestore (sosyal akış orada kalıyor)
// ═══════════════════════════════════════════════════════════════════════

// ── Row → Domain model dönüşümleri ───────────────────────────────────────────

private fun AuthorRow.toDomain() = Author(
    id            = id,
    name          = name,
    bio           = bio,
    photoURL      = photoUrl,
    birthYear     = birthYear,
    nationality   = nationality,
    bookCount     = bookCount,
    quoteCount    = quoteCount,
    reviewCount   = reviewCount,
    followerCount = followerCount,
)

private fun LibraryBookRow.toDomain() = LibraryBook(
    id          = id,
    title       = title,
    authorId    = authorId ?: "",
    authorName  = authorName,
    coverImg    = coverImg,
    genre       = genre,
    publishYear = publishYear,
    synopsis    = synopsis,
    pageCount   = pageCount,
    quoteCount  = quoteCount,
    reviewCount = reviewCount,
    avgRating   = avgRating,
    likesCount  = likesCount,
)

private fun BookQuoteRow.toDomain() = BookQuote(
    id              = id,
    bookId          = bookId,
    authorId        = authorId ?: "",
    bookTitle       = bookTitle,
    authorName      = authorName,
    coverImg        = coverImg,
    text            = text,
    uid             = uid,
    userDisplayName = userDisplayName,
    userPhotoURL    = userPhotoUrl,
    feedPostId      = feedPostId,
    likesCount      = likesCount,
)

private fun BookReviewRow.toDomain() = BookReview(
    id              = id,
    bookId          = bookId,
    authorId        = authorId ?: "",
    bookTitle       = bookTitle,
    authorName      = authorName,
    text            = text,
    rating          = rating,
    uid             = uid,
    userDisplayName = userDisplayName,
    userPhotoURL    = userPhotoUrl,
    feedPostId      = feedPostId,
    likesCount      = likesCount,
)

private fun AuthorRow.toCached() = CachedAuthor(
    id            = id,
    name          = name,
    bio           = bio,
    photoUrl      = photoUrl,
    birthYear     = birthYear,
    nationality   = nationality,
    bookCount     = bookCount,
    quoteCount    = quoteCount,
    reviewCount   = reviewCount,
    followerCount = followerCount,
)

private fun LibraryBookRow.toCached() = CachedBook(
    id          = id,
    title       = title,
    authorId    = authorId ?: "",
    authorName  = authorName,
    coverImg    = coverImg,
    genre       = genre,
    publishYear = publishYear,
    synopsis    = synopsis,
    pageCount   = pageCount,
    quoteCount  = quoteCount,
    reviewCount = reviewCount,
    avgRating   = avgRating,
)

private fun CachedAuthor.toDomain() = Author(
    id            = id,
    name          = name,
    bio           = bio,
    photoURL      = photoUrl,
    birthYear     = birthYear,
    nationality   = nationality,
    bookCount     = bookCount,
    quoteCount    = quoteCount,
    reviewCount   = reviewCount,
    followerCount = followerCount,
)

private fun CachedBook.toDomain() = LibraryBook(
    id          = id,
    title       = title,
    authorId    = authorId,
    authorName  = authorName,
    coverImg    = coverImg,
    genre       = genre,
    publishYear = publishYear,
    synopsis    = synopsis,
    pageCount   = pageCount,
    quoteCount  = quoteCount,
    reviewCount = reviewCount,
    avgRating   = avgRating,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val auth      : FirebaseAuth,
    private val firestore : FirebaseFirestore,
    private val library   : LibraryRepository,
    private val bookDao   : BookDao,
    private val authorDao : AuthorDao,
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────
    private val _authors        = MutableStateFlow<List<Author>>(emptyList())
    val authors = _authors.asStateFlow()

    private val _selectedAuthor = MutableStateFlow<Author?>(null)
    val selectedAuthor = _selectedAuthor.asStateFlow()

    private val _authorBooks    = MutableStateFlow<List<LibraryBook>>(emptyList())
    val authorBooks = _authorBooks.asStateFlow()

    private val _authorQuotes   = MutableStateFlow<List<BookQuote>>(emptyList())
    val authorQuotes = _authorQuotes.asStateFlow()

    private val _authorReviews  = MutableStateFlow<List<BookReview>>(emptyList())
    val authorReviews = _authorReviews.asStateFlow()

    private val _selectedBook   = MutableStateFlow<LibraryBook?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _bookQuotes     = MutableStateFlow<List<BookQuote>>(emptyList())
    val bookQuotes = _bookQuotes.asStateFlow()

    private val _bookReviews    = MutableStateFlow<List<BookReview>>(emptyList())
    val bookReviews = _bookReviews.asStateFlow()

    private val _loading        = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error          = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isFollowingAuthor = MutableStateFlow(false)
    val isFollowingAuthor = _isFollowingAuthor.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline = _isOffline.asStateFlow()

    val myUid   get() = auth.currentUser?.uid ?: ""
    val myName  get() = auth.currentUser?.displayName ?: ""
    val myPhoto get() = auth.currentUser?.photoUrl?.toString() ?: ""
    val myUser  get() = auth.currentUser?.email?.substringBefore("@") ?: ""

    // FAZ -1 devamı: Kütüphaneci ve moderatör yetkileri birbirinden AYRI.
    // "kutuphaneci" rolü sadece kitap/yazar ekleme-düzenleme (createAuthor,
    // updateAuthor, createLibraryBook, updateLibraryBook) yapabilir —
    // bu "library" iznine bağlı. Başkasının yazdığı alıntı/yorumu
    // silme-düzenleme (deleteQuote/editQuote/deleteReview/editReview) ise
    // bir MODERASYON işlemi, "kutuphaneci" rolünün kapsamında değil —
    // bu "pending" iznine (moderator/editor/admin rolleri) bağlı.
    // Önceden ikisi de tek bir `isAdmin` ("library" iznine bağlı) altında
    // birleşmişti; bu yanlıştı çünkü kütüphaneci rolü moderasyon yetkisi
    // içermiyor.
    private val _libraryPerms = MutableStateFlow<StaffPermissions?>(null)
    val libraryPerms = _libraryPerms.asStateFlow()

    // Kitap/yazar ekleme-düzenleme yetkisi ("kutuphaneci" veya "admin")
    val isLibrarian: Boolean get() = _libraryPerms.value?.can("library") == true
    // Başkasının alıntı/yorumunu silme-düzenleme yetkisi ("moderator"/"editor"/"admin")
    val canModerateLibrary: Boolean get() = _libraryPerms.value?.can("pending") == true

    // Geriye dönük uyumluluk: eski kod `isAdmin`'i "kitap/yazar ekleyebilir
    // mi" anlamında kullanıyordu — LibraryScreen'deki ekleme butonları için
    // isLibrarian'a eşit tutuyoruz. Silme/düzenleme kontrolleri artık
    // doğrudan canModerateLibrary kullanıyor (bkz. deleteQuote vb. altta).
    val isAdmin: Boolean get() = isLibrarian

    init { loadLibraryPerms() }

    fun loadLibraryPerms() {
        viewModelScope.launch {
            _libraryPerms.value = null
            val user = auth.currentUser ?: run { _libraryPerms.value = StaffPermissions(); return@launch }
            try {
                val doc = firestore.collection("admins").document(user.uid).get().await()
                if (doc.exists()) {
                    val role  = doc.getString("role") ?: "none"
                    val title = doc.getString("title") ?: role.replaceFirstChar { it.uppercase() }
                    @Suppress("UNCHECKED_CAST")
                    val legacy = doc.get("permissions") as? List<String>
                    val permSet = if (!legacy.isNullOrEmpty()) legacy.toSet() else roleToPermissions(role)
                    _libraryPerms.value = StaffPermissions(uid = user.uid, title = title, permissions = permSet)
                } else {
                    _libraryPerms.value = StaffPermissions()
                }
            } catch (e: Exception) {
                android.util.Log.w("LibraryVM", "loadLibraryPerms: ${e.message}")
                _libraryPerms.value = StaffPermissions()
            }
        }
    }

    // ── Authors ───────────────────────────────────────────────────────────────

    // ── Yazarlar sayfalama state ──────────────────────────────────────────────
    private val _authorsOffset  = MutableStateFlow(0)
    private val _authorsHasMore = MutableStateFlow(true)
    val authorsHasMore          = _authorsHasMore.asStateFlow()
    private val _authorsLoading = MutableStateFlow(false)
    val authorsLoading          = _authorsLoading.asStateFlow()

    fun loadAuthors(forceRefresh: Boolean = false) {
        if (_authorsLoading.value) return
        viewModelScope.launch {
            _authorsLoading.value = true
            if (forceRefresh) {
                _authorsOffset.value = 0
                _authors.value = emptyList()
                _authorsHasMore.value = true
            }
            // İlk sayfada cache'e bak
            if (_authorsOffset.value == 0) {
                val cached = try { authorDao.getCachedAuthors(PAGE_SIZE) } catch (_: Exception) { emptyList() }
                if (cached.isNotEmpty() && !forceRefresh) {
                    _authors.value = cached.map { it.toDomain() }
                    _isOffline.value = false
                    _loading.value = false
                }
            }
            try {
                val fresh = library.getAuthors(PAGE_SIZE, _authorsOffset.value)
                val domain = fresh.map { it.toDomain() }
                _authors.value = if (_authorsOffset.value == 0) domain
                                 else _authors.value + domain
                _authorsHasMore.value = fresh.size == PAGE_SIZE
                _authorsOffset.value += fresh.size
                _isOffline.value = false
                if (_authorsOffset.value == fresh.size) {
                    authorDao.replaceAll(fresh.map { it.toCached() })
                }
            } catch (e: Exception) {
                if (_authors.value.isEmpty()) _error.value = e.message
                _isOffline.value = _authors.value.isNotEmpty()
            } finally {
                _authorsLoading.value = false
                _loading.value = false
            }
        }
    }

    fun loadMoreAuthors() {
        if (!_authorsHasMore.value || _authorsLoading.value) return
        loadAuthors()
    }

    fun loadAuthor(authorId: String) {
        viewModelScope.launch {
            // ── Stale-while-revalidate ────────────────────────────────────────
            // 1. Room cache'ten yazar ve kitaplarını anında göster
            val cachedAuthor = try { authorDao.getCachedAuthor(authorId) } catch (_: Exception) { null }
            val cachedBooks  = try {
                bookDao.getCachedBooks().filter { it.authorId == authorId }
            } catch (_: Exception) { emptyList() }

            if (cachedAuthor != null) {
                _selectedAuthor.value = cachedAuthor.toDomain()
                _authorBooks.value    = cachedBooks.map { it.toDomain() }
                _isOffline.value      = false
                _loading.value        = false
            } else {
                _loading.value = true
            }
            // 2. Arka planda Supabase'den taze veri çek
            try {
                val freshAuthor = library.getAuthor(authorId) ?: run {
                    _loading.value = false
                    return@launch
                }
                // isFollowingAuthor ayrı try-catch içinde — bu çağrı offline'da crash yaratıyordu
                val following = try { library.isFollowingAuthor(authorId) } catch (_: Exception) { false }
                _isFollowingAuthor.value = following
                _selectedAuthor.value = freshAuthor.toDomain()
                    .copy(isFollowedByMe = following)

                val booksDeferred   = async { library.getBooksByAuthor(authorId) }
                val quotesDeferred  = async { library.getQuotesByAuthor(authorId) }
                val reviewsDeferred = async { library.getReviewsByAuthor(authorId) }

                val freshBooks = booksDeferred.await()
                _authorBooks.value   = freshBooks.map { it.toDomain() }
                _authorQuotes.value  = quotesDeferred.await().map { it.toDomain() }
                _authorReviews.value = reviewsDeferred.await().map { it.toDomain() }
                _isOffline.value     = false
                syncQuoteLikeStates(_authorQuotes.value, _authorQuotes)

                // 3. Room'u güncelle
                authorDao.insert(freshAuthor.toCached())
                bookDao.insertAll(freshBooks.map { it.toCached() })
            } catch (e: Exception) {
                // Offline veya ağ hatası — cache varsa sessiz geç, crash yok
                if (cachedAuthor == null) _error.value = e.message
                _isOffline.value = cachedAuthor != null
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleFollowAuthor(authorId: String) {
        viewModelScope.launch {
            try {
                if (_isFollowingAuthor.value) {
                    library.unfollowAuthor(authorId)
                    _isFollowingAuthor.value = false
                    _selectedAuthor.value = _selectedAuthor.value?.copy(
                        isFollowedByMe = false,
                        followerCount  = (_selectedAuthor.value?.followerCount ?: 1) - 1,
                    )
                } else {
                    library.followAuthor(authorId)
                    _isFollowingAuthor.value = true
                    _selectedAuthor.value = _selectedAuthor.value?.copy(
                        isFollowedByMe = true,
                        followerCount  = (_selectedAuthor.value?.followerCount ?: 0) + 1,
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun createAuthor(
        name       : String,
        bio        : String = "",
        photoURL   : String = "",
        birthYear  : Int    = 0,
        nationality: String = "",
    ) {
        viewModelScope.launch {
            try {
                val id = UUID.randomUUID().toString()
                library.upsertAuthor(AuthorRow(
                    id          = id,
                    name        = name,
                    bio         = bio,
                    photoUrl    = photoURL,
                    birthYear   = birthYear,
                    nationality = nationality,
                ))
                loadAuthors(forceRefresh = true)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

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
                val current = library.getAuthor(authorId) ?: return@launch
                library.upsertAuthor(current.copy(
                    name        = name,
                    bio         = bio,
                    photoUrl    = photoURL,
                    birthYear   = birthYear,
                    nationality = nationality,
                ))
                loadAuthor(authorId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Library Books ─────────────────────────────────────────────────────────

    fun loadLibraryBook(bookId: String) {
        viewModelScope.launch {
            // ── Stale-while-revalidate ────────────────────────────────────────
            // 1. Room cache'ten kitabı anında göster
            val cachedBook = try { bookDao.getCachedBook(bookId) } catch (_: Exception) { null }
            if (cachedBook != null) {
                _selectedBook.value = cachedBook.toDomain()
                _isOffline.value    = false
                _loading.value      = false
                // Alıntı/inceleme cache yok — spinner göstermeden aşağıda çekilecek
            } else {
                _loading.value = true
            }
            // 2. Arka planda Supabase'den taze veri çek
            try {
                val freshBook = library.getBook(bookId) ?: return@launch
                val book      = freshBook.toDomain()
                _selectedBook.value = book
                _isOffline.value    = false

                val qDeferred = async { library.getQuotesByBook(bookId) }
                val rDeferred = async { library.getReviewsByBook(bookId) }
                val quotes    = qDeferred.await()
                val bookCover = book.coverImg
                _bookQuotes.value  = quotes.map { row ->
                    val q = row.toDomain()
                    if (q.coverImg.isBlank() && bookCover.isNotBlank()) q.copy(coverImg = bookCover) else q
                }
                _bookReviews.value = rDeferred.await().map { it.toDomain() }
                syncQuoteLikeStates(_bookQuotes.value, _bookQuotes)

                // 3. Room'u güncelle
                bookDao.insert(freshBook.toCached())
            } catch (e: Exception) {
                if (cachedBook == null) _error.value = e.message
                _isOffline.value = cachedBook != null
            } finally {
                _loading.value = false
            }
        }
    }

    fun createLibraryBook(
        title      : String,
        authorId   : String,
        authorName : String,
        coverImg   : String = "",
        genre      : String = "",
        publishYear: Int    = 0,
        synopsis   : String = "",
        pageCount  : Int    = 0,
    ) {
        viewModelScope.launch {
            try {
                val id = UUID.randomUUID().toString()
                library.upsertBook(LibraryBookRow(
                    id          = id,
                    title       = title,
                    authorId    = authorId.ifBlank { null },
                    authorName  = authorName,
                    coverImg    = coverImg,
                    genre       = genre,
                    publishYear = publishYear,
                    synopsis    = synopsis,
                    pageCount   = pageCount,
                ))
                if (authorId.isNotBlank()) {
                    val author = library.getAuthor(authorId)
                    if (author != null) library.updateAuthorCounters(authorId, bookCount = author.bookCount + 1)
                }
                loadAuthors(forceRefresh = true)
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
        authorId   : String = "",
        authorName : String = "",
    ) {
        viewModelScope.launch {
            try {
                val current = library.getBook(bookId) ?: return@launch
                library.upsertBook(current.copy(
                    title       = title,
                    authorId    = authorId.ifBlank { current.authorId },
                    authorName  = authorName.ifBlank { current.authorName },
                    coverImg    = coverImg,
                    genre       = genre,
                    publishYear = publishYear,
                    synopsis    = synopsis,
                    pageCount   = pageCount,
                ))
                loadLibraryBook(bookId)

                // Sorun 2 düzeltmesi: kapak değiştiyse (boştan doluysa veya
                // farklı URL'ye geçildiyse) o kitaba bağlı tüm kayıtları
                // güncelle. Yoksa Firestore'daki eski alıntılar ve okuma
                // listesi kayıtları boş/eski kapakla kalmaya devam ederdi.
                if (coverImg.isNotBlank() && coverImg != current.coverImg) {
                    propagateCoverUpdate(bookId, coverImg)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Kapak fotoğrafı değiştiğinde ilgili tüm tablolara yansıt.
     *  1. Supabase book_quotes  — kütüphane alıntı kartları
     *  2. Supabase reading_status — okuma listesi kartları
     *  3. Firestore feed — sosyal akış alıntı postları (Firestore batch) */
    private suspend fun propagateCoverUpdate(bookId: String, coverImg: String) {
        // 1. Supabase book_quotes
        try {
            library.updateQuotesCover(bookId, coverImg)
        } catch (e: Exception) {
            android.util.Log.w("LibraryVM", "book_quotes cover güncelleme: ${e.message}")
        }

        // 2. Supabase reading_status
        try {
            library.updateReadingStatusCover(bookId, coverImg)
        } catch (e: Exception) {
            android.util.Log.w("LibraryVM", "reading_status cover güncelleme: ${e.message}")
        }

        // 3. Firestore feed — libraryBookId ile eşleşen alıntı postları
        try {
            val snap = firestore.collection("feed")
                .whereEqualTo("libraryBookId", bookId)
                .whereEqualTo("type", "library_quote")
                .get().await()
            if (snap.documents.isNotEmpty()) {
                val batch = firestore.batch()
                snap.documents.forEach { doc ->
                    batch.update(doc.reference, "coverImg", coverImg)
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            android.util.Log.w("LibraryVM", "feed cover güncelleme: ${e.message}")
        }
    }

    // ── Alıntı Ekle ───────────────────────────────────────────────────────────

    fun addBookQuote(book: LibraryBook, quoteText: String) {
        if (myUid.isBlank() || quoteText.isBlank()) return
        viewModelScope.launch {
            try {
                val now = Timestamp.now()
                val (resolvedAuthorId, resolvedBookId) = if (book.id.isBlank())
                    library.ensureAuthorAndBook(book.authorName, book.title)
                else Pair(book.authorId, book.id)

                // Kapak fotoğrafı: book.coverImg doluysa direkt kullan.
                // Boşsa (book nesnesi oluşturulurken henüz yüklenmemişse)
                // Supabase'den bir kez çek — addQuoteToLibrary da aynısını
                // yapıyor ama Firestore tarafı için burada ayrıca gerekiyor.
                // Sorun 1 düzeltmesi: daha önce feed'e coverImg hiç yazılmıyordu,
                // enrichMissingCovers bunu sonradan kitap adıyla aratarak
                // güvenilmez biçimde tamamlamaya çalışıyordu.
                val resolvedCoverImg = book.coverImg.ifBlank {
                    if (resolvedBookId.isNotBlank())
                        try { library.getBook(resolvedBookId)?.coverImg ?: "" }
                        catch (_: Exception) { "" }
                    else ""
                }

                // Feed'e yaz (Firestore — sosyal akış)
                val feedRef = firestore.collection("feed").add(
                    hashMapOf(
                        "uid"             to myUid,
                        "displayName"     to myName,
                        "name"            to myName,
                        "username"        to myUser,
                        "photoURL"        to myPhoto,
                        "text"            to "",
                        "imgUrl"          to "",
                        "imageURL"        to "",
                        "quoteText"       to quoteText,
                        "bookName"        to book.title,
                        "authorName"      to book.authorName,
                        "coverImg"        to resolvedCoverImg,
                        "libraryBookId"   to resolvedBookId,
                        "libraryAuthorId" to resolvedAuthorId,
                        "type"            to "library_quote",
                        "likes"           to 0,
                        "saves"           to 0,
                        "cmtCount"        to 0,
                        "reposts"         to 0,
                        "ts"              to now,
                    )
                ).await()

                // Supabase'e yaz
                library.addQuoteToLibrary(
                    libraryBookId   = resolvedBookId,
                    libraryAuthorId = resolvedAuthorId,
                    bookName        = book.title,
                    authorName      = book.authorName,
                    quoteText       = quoteText,
                    uid             = myUid,
                    userDisplayName = myName,
                    userPhotoURL    = myPhoto,
                    feedPostId      = feedRef.id,
                )

                // Yerel state güncelle
                if (resolvedBookId.isNotBlank()) {
                    _bookQuotes.value = library.getQuotesByBook(resolvedBookId).map { it.toDomain() }
                }
                if (resolvedAuthorId.isNotBlank()) {
                    _authorQuotes.value = library.getQuotesByAuthor(resolvedAuthorId).map { it.toDomain() }
                }

                // Yazarı takip edenlere bildirim (Cloud Function — fire & forget)
                if (resolvedAuthorId.isNotBlank()) {
                    notifyAuthorFollowers(resolvedAuthorId, book.authorName, "quote", quoteText)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Yorum Ekle ────────────────────────────────────────────────────────────

    fun addBookReview(book: LibraryBook, reviewText: String, rating: Float) {
        if (myUid.isBlank() || reviewText.isBlank()) return
        viewModelScope.launch {
            try {
                val now   = Timestamp.now()
                val newId = UUID.randomUUID().toString()

                // Feed'e yaz (Firestore)
                val feedRef = firestore.collection("feed").add(
                    hashMapOf(
                        "uid"             to myUid,
                        "displayName"     to myName,
                        "name"            to myName,
                        "photoURL"        to myPhoto,
                        "text"            to reviewText,
                        "bookName"        to book.title,
                        "authorName"      to book.authorName,
                        "rating"          to rating,
                        "libraryBookId"   to book.id,
                        "libraryAuthorId" to book.authorId,
                        "type"            to "library_review",
                        "ts"              to now,
                    )
                ).await()

                // Supabase'e yaz
                library.insertReview(BookReviewRow(
                    id              = newId,
                    bookId          = book.id,
                    authorId        = book.authorId.ifBlank { null },
                    bookTitle       = book.title,
                    authorName      = book.authorName,
                    text            = reviewText,
                    rating          = rating,
                    uid             = myUid,
                    userDisplayName = myName,
                    userPhotoUrl    = myPhoto,
                    feedPostId      = feedRef.id,
                ))

                // Sayaçları güncelle
                val currentBook    = _selectedBook.value
                val newReviewCount = (currentBook?.reviewCount ?: 0) + 1
                val currentAvg     = currentBook?.avgRating ?: 0f
                val newAvg = if (currentAvg == 0f) rating
                    else ((currentAvg * (newReviewCount - 1)) + rating) / newReviewCount

                library.updateBookCounters(book.id, reviewCount = newReviewCount, avgRating = newAvg)
                if (book.authorId.isNotBlank()) {
                    val author = library.getAuthor(book.authorId)
                    if (author != null) library.updateAuthorCounters(book.authorId, reviewCount = author.reviewCount + 1)
                }

                _selectedBook.value = currentBook?.copy(reviewCount = newReviewCount, avgRating = newAvg)
                _bookReviews.value  = library.getReviewsByBook(book.id).map { it.toDomain() }

                // Yazarı takip edenlere bildirim (Cloud Function — fire & forget)
                if (book.authorId.isNotBlank()) {
                    notifyAuthorFollowers(book.authorId, book.authorName, "review", reviewText)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ── Beğeni ────────────────────────────────────────────────────────────────

    fun toggleLikeQuote(bookId: String, quoteId: String) {
        viewModelScope.launch {
            try {
                val current = _bookQuotes.value.find { it.id == quoteId }
                    ?: _authorQuotes.value.find { it.id == quoteId } ?: return@launch
                if (current.feedPostId.isBlank()) return@launch
                // Optimistic UI
                val nowLiked = !current.isLikedByMe
                val optimisticDelta = if (nowLiked) 1 else -1
                _bookQuotes.value = _bookQuotes.value.map {
                    if (it.id == quoteId) it.copy(isLikedByMe = nowLiked, likesCount = (it.likesCount + optimisticDelta).coerceAtLeast(0)) else it
                }
                _authorQuotes.value = _authorQuotes.value.map {
                    if (it.id == quoteId) it.copy(isLikedByMe = nowLiked, likesCount = (it.likesCount + optimisticDelta).coerceAtLeast(0)) else it
                }
                val (realCount, liked) = library.toggleQuoteFeedLike(current.feedPostId, myName, myPhoto)
                _bookQuotes.value = _bookQuotes.value.map {
                    if (it.id == quoteId) it.copy(isLikedByMe = liked, likesCount = realCount) else it
                }
                _authorQuotes.value = _authorQuotes.value.map {
                    if (it.id == quoteId) it.copy(isLikedByMe = liked, likesCount = realCount) else it
                }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    /** Yüklenen alıntıların gerçek beğeni sayısını/durumunu feed_likes'tan çekip
     *  state'e yansıtır — kütüphane akışıyla (ConnectedPostCard) aynı sayıyı gösterir. */
    private fun syncQuoteLikeStates(quotes: List<BookQuote>, target: MutableStateFlow<List<BookQuote>>) {
        val feedIds = quotes.map { it.feedPostId }.filter { it.isNotBlank() }
        if (feedIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val states = library.getQuoteLikeStates(feedIds)
                if (states.isEmpty()) return@launch
                target.value = target.value.map { q ->
                    val s = states[q.feedPostId] ?: return@map q
                    q.copy(likesCount = s.first, isLikedByMe = s.second)
                }
            } catch (e: Exception) {
                // Beğeni durumu senkronize edilemedi — sessiz geç (UI kritik değil)
                android.util.Log.w("LibraryVM", "syncQuoteLikeStates: ${e.message}")
            }
        }
    }

    fun toggleLikeReview(bookId: String, reviewId: String) {
        viewModelScope.launch {
            try {
                val current = _bookReviews.value.find { it.id == reviewId } ?: return@launch
                val delta   = if (current.isLikedByMe) -1 else 1
                library.incrementReviewLikes(reviewId, delta)
                _bookReviews.value = _bookReviews.value.map {
                    if (it.id == reviewId) it.copy(
                        isLikedByMe = !it.isLikedByMe,
                        likesCount  = (it.likesCount + delta).coerceAtLeast(0),
                    ) else it
                }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    // ── Sil / Düzenle ─────────────────────────────────────────────────────────

    fun deleteQuote(bookId: String, quoteId: String, quoteUid: String) {
        if (myUid != quoteUid && !canModerateLibrary) return
        viewModelScope.launch {
            try {
                library.deleteQuote(quoteId)
                val book = library.getBook(bookId)
                if (book != null) library.updateBookCounters(bookId, quoteCount = (book.quoteCount - 1).coerceAtLeast(0))
                _bookQuotes.value = _bookQuotes.value.filter { it.id != quoteId }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun editQuote(bookId: String, quoteId: String, quoteUid: String, newText: String) {
        if (myUid != quoteUid && !canModerateLibrary) return
        viewModelScope.launch {
            try {
                library.updateQuoteText(quoteId, newText)
                _bookQuotes.value = _bookQuotes.value.map {
                    if (it.id == quoteId) it.copy(text = newText) else it
                }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun deleteReview(bookId: String, reviewId: String, reviewUid: String) {
        if (myUid != reviewUid && !canModerateLibrary) return
        viewModelScope.launch {
            try {
                library.deleteReview(reviewId)
                _bookReviews.value = _bookReviews.value.filter { it.id != reviewId }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun editReview(bookId: String, reviewId: String, reviewUid: String, newText: String, newRating: Float) {
        if (myUid != reviewUid && !canModerateLibrary) return
        viewModelScope.launch {
            try {
                library.updateReviewText(reviewId, newText, newRating)
                _bookReviews.value = _bookReviews.value.map {
                    if (it.id == reviewId) it.copy(text = newText, rating = newRating) else it
                }
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    // ── Legacy / Uyumluluk ───────────────────────────────────────────────────
    // Feed'deki eski alıntılar (Firestore) — yalnızca okuma, taşıma için
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
                val existing = _authorQuotes.value
                val ids = existing.map { it.id }.toSet()
                _authorQuotes.value = existing + mapped.filter { it.id !in ids }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // fetchBooksForAuthor — FeedViewModel'den çağrılıyor
    suspend fun fetchBooksForAuthor(authorId: String): List<LibraryBook> =
        try { library.getBooksByAuthor(authorId).map { it.toDomain() } }
        catch (_: Exception) { emptyList() }

    // ── Admin: Eski feed alıntılarını Supabase'e taşı ────────────────────────
    fun migrateLegacyFeedQuotes(onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().await()

                val docs = snap.documents.filter { doc ->
                    val d = doc.data ?: return@filter false
                    val existingBookId = d["libraryBookId"] as? String ?: ""
                    if (existingBookId.isNotBlank()) return@filter false
                    val qObj  = d["quote"] as? Map<*, *>
                    val bName = (qObj?.get("book") as? String)?.trim()?.isNotBlank() == true
                        || (d["bookName"] as? String)?.trim()?.isNotBlank() == true
                    val qText = (qObj?.get("text") as? String)?.trim()?.isNotBlank() == true
                        || (d["quoteText"] as? String)?.trim()?.isNotBlank() == true
                    bName && qText
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

                    if (bookName.isBlank() && authorName.isBlank()) {
                        done++; onProgress(done, total); return@forEach
                    }

                    val (authorId, bookId) = library.ensureAuthorAndBook(authorName, bookName)

                    // Feed dökümanını güncelle
                    val updates = mutableMapOf<String, Any>()
                    if (authorId.isNotBlank()) updates["libraryAuthorId"] = authorId
                    if (bookId.isNotBlank())   updates["libraryBookId"]   = bookId
                    val currentType = d["type"] as? String ?: ""
                    if (currentType.isBlank()) updates["type"] = "library_quote"
                    if (updates.isNotEmpty()) {
                        try { doc.reference.update(updates).await() } catch (_: Exception) {}
                    }

                    // Supabase book_quotes'a ekle
                    if (bookId.isNotBlank()) {
                        val qText = (qObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                            ?: d["quoteText"] as? String ?: ""
                        if (qText.isNotBlank()) {
                            try {
                                library.addQuoteToLibrary(
                                    libraryBookId   = bookId,
                                    libraryAuthorId = authorId,
                                    bookName        = bookName,
                                    authorName      = authorName,
                                    quoteText       = qText,
                                    uid             = d["uid"] as? String ?: "",
                                    userDisplayName = (d["name"] as? String)?.takeIf { it.isNotBlank() }
                                        ?: d["displayName"] as? String ?: "",
                                    userPhotoURL    = d["photoURL"] as? String ?: "",
                                    feedPostId      = doc.id,
                                )
                            } catch (_: Exception) {}
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

    fun clearError() { _error.value = null }

    // rebuildCounters (Admin araç)
    fun rebuildCounters(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val authors = library.getAuthors(500)
                for (author in authors) {
                    val books  = library.getBooksByAuthor(author.id)
                    var qTotal = 0
                    var rTotal = 0
                    for (book in books) {
                        val qCount = library.getQuotesByBook(book.id).size
                        val rCount = library.getReviewsByBook(book.id).size
                        library.updateBookCounters(book.id, quoteCount = qCount, reviewCount = rCount)
                        qTotal += qCount
                        rTotal += rCount
                    }
                    library.updateAuthorCounters(author.id,
                        bookCount   = books.size,
                        quoteCount  = qTotal,
                        reviewCount = rTotal,
                    )
                }
                onComplete("✅ Sayaçlar yenilendi")
            } catch (e: Exception) {
                onComplete("❌ ${e.message}")
            }
        }
    }

    // ── LibraryScreen için doğrudan Supabase okuma ────────────────────────────
    // LibraryScreen local state'ini doldurmak için kullanılır

    // ── Smart Screen yönlendirme — Supabase'de ara, yoksa oluştur ───────────
    // AuthorQuotesSmartScreen ve BookQuotesSmartScreen tarafından kullanılır

    suspend fun findOrCreateAuthorByName(name: String): String {
        return try {
            val existing = library.searchAuthors(name).firstOrNull {
                it.name.equals(name, ignoreCase = true)
            }
            if (existing != null) existing.id
            else {
                val newId = java.util.UUID.randomUUID().toString()
                library.upsertAuthor(
                    com.heftreng.app.data.repository.AuthorRow(id = newId, name = name)
                )
                newId
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryVM", "findOrCreateAuthorByName: ${e.message}")
            ""
        }
    }

    suspend fun findOrCreateBookByTitle(title: String): String {
        return try {
            val existing = library.searchBooks(title).firstOrNull {
                it.title.equals(title, ignoreCase = true)
            }
            if (existing != null) existing.id
            else {
                val newId = java.util.UUID.randomUUID().toString()
                library.upsertBook(
                    com.heftreng.app.data.repository.LibraryBookRow(id = newId, title = title)
                )
                newId
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryVM", "findOrCreateBookByTitle: ${e.message}")
            ""
        }
    }

    // ── Kitaplar sayfalama state ──────────────────────────────────────────────
    private val PAGE_SIZE = 20
    private val _books         = MutableStateFlow<List<LibraryBook>>(emptyList())
    val books                  = _books.asStateFlow()
    private val _booksOffset   = MutableStateFlow(0)
    private val _booksHasMore  = MutableStateFlow(true)
    val booksHasMore           = _booksHasMore.asStateFlow()
    private val _booksLoading  = MutableStateFlow(false)
    val booksLoading           = _booksLoading.asStateFlow()

    fun loadBooks(forceRefresh: Boolean = false) {
        if (_booksLoading.value) return
        viewModelScope.launch {
            _booksLoading.value = true
            if (forceRefresh) {
                _booksOffset.value = 0
                _books.value = emptyList()
                _booksHasMore.value = true
            }
            try {
                val rows = library.getBooks(PAGE_SIZE, _booksOffset.value)
                    .map { it.toDomain() }
                _books.value = if (_booksOffset.value == 0) rows
                               else _books.value + rows
                _booksHasMore.value = rows.size == PAGE_SIZE
                _booksOffset.value += rows.size
            } catch (e: Exception) {
                android.util.Log.e("LibraryVM", "loadBooks: ${e.message}")
            }
            _booksLoading.value = false
        }
    }

    fun loadMoreBooks() {
        if (!_booksHasMore.value || _booksLoading.value) return
        loadBooks()
    }

    // Geriye uyum için — LibraryScreen'deki eski çağrıyı kaldır
    @Deprecated("loadBooks() kullan")
    suspend fun loadBooksForScreen(): List<LibraryBook> {
        loadBooks()
        return _books.value
    }

    suspend fun loadReviewsForScreen(): List<BookReview> =
        try {
            // ÖNCEKİ: 100 kitap çekip her biri için sıralı (N+1) inceleme sorgusu atılıyordu.
            // Bu, kütüphane sekmesinin çok yavaş açılmasının asıl sebebiydi. Artık tek sorgu.
            library.getRecentReviews(50).map { it.toDomain() }
        } catch (e: Exception) {
            android.util.Log.e("LibraryVM", "loadReviewsForScreen: ${e.message}")
            emptyList()
        }

    // ── Yazar takipçilerine bildirim — Cloud Function (fire & forget) ───────
    // notifyAuthorFollowers: author_follows (Supabase) içindeki kullanıcılara
    // userNotifs/{uid}/msgs yazar → onNewNotif otomatik FCM gönderir.
    private fun notifyAuthorFollowers(authorId: String, authorName: String, type: String, text: String) {
        viewModelScope.launch {
            try {
                com.google.firebase.functions.FirebaseFunctions
                    .getInstance("europe-west1")
                    .getHttpsCallable("notifyAuthorFollowers")
                    .call(hashMapOf(
                        "authorId"   to authorId,
                        "authorName" to authorName,
                        "type"       to type,
                        "text"       to text.take(140),
                    )).await()
            } catch (e: Exception) {
                android.util.Log.w("LibraryVM", "notifyAuthorFollowers: ${e.message}")
            }
        }
    }
}
