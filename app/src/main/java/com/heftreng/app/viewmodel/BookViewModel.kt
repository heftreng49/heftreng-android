package com.heftreng.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.BookChapter
import com.heftreng.app.data.model.ChapterComment
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import com.heftreng.app.data.model.SerialLikeRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class BookViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase : SupabaseClient,
) : ViewModel() {

    // ── State ────────────────────────────────────────────────────────────────
    private val _books           = MutableStateFlow<List<Book>>(emptyList())
    val books = _books.asStateFlow()

    private val _myBooks         = MutableStateFlow<List<Book>>(emptyList())
    val myBooks = _myBooks.asStateFlow()

    private val _selectedBook    = MutableStateFlow<Book?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _chapters        = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters = _chapters.asStateFlow()

    private val _selectedChapter = MutableStateFlow<BookChapter?>(null)
    val selectedChapter = _selectedChapter.asStateFlow()

    private val _chapterComments = MutableStateFlow<List<ChapterComment>>(emptyList())
    val chapterComments = _chapterComments.asStateFlow()

    private val _loading         = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // Beğeni hafızası (Sadece ilk açılışta veya ihtiyaç halinde güncellenir)
    private var likedBookIds   = emptySet<String>()
    private var likedSerialIds = emptySet<String>()
    private var likedChapterIds = emptySet<String>() // Bölüm beğenileri için eklendi

    private var isLikesLoaded = false

    // TTL cache — 5 dk içinde aynı listeyi tekrar Firestore'dan çekme
    private val BOOK_CACHE_TTL_MS   = 30L * 60_000L
    private var lastBooksFetchMs    = 0L
    private var lastMyBooksFetchUid = ""
    private var lastMyBooksFetchMs  = 0L

    init {
        // ViewModel ilk yaratıldığında kullanıcının beğeni geçmişini bir kez önbelleğe alalım
        preloadUserLikes()
    }

    private fun preloadUserLikes() {
        if (uid.isEmpty() || isLikesLoaded) return
        viewModelScope.launch {
            try {
                coroutineScope {
                    val booksJob = async { loadLikedBooks() }
                    val serialsJob = async { loadLikedSerials() }
                    val chaptersJob = async { loadLikedChapters() }
                    
                    booksJob.await()
                    serialsJob.await()
                    chaptersJob.await()
                }
                isLikesLoaded = true
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LİSTE — books + serials birleşik
    // ════════════════════════════════════════════════════════════════════════

    fun loadBooks(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _books.value.isNotEmpty() && (now - lastBooksFetchMs) < BOOK_CACHE_TTL_MS) return
        viewModelScope.launch {
            _loading.value = true
            try {
                // Eğer daha önce yüklenmediyse beğenileri yükle (Güvenlik kalkanı)
                if (uid.isNotEmpty() && !isLikesLoaded) {
                    coroutineScope {
                        async { loadLikedBooks() }.await()
                        async { loadLikedSerials() }.await()
                        async { loadLikedChapters() }.await()
                    }
                    isLikesLoaded = true
                }

                // Paralel veri çekimi (Maksimum performans için async-await)
                val (booksSnap, serialsSnap) = coroutineScope {
                    val bDeferred = async { firestore.collection("books").orderBy("updatedAt", Query.Direction.DESCENDING).limit(30).get().await() }
                    val sDeferred = async { firestore.collection("serials").orderBy("updatedAt", Query.Direction.DESCENDING).limit(30).get().await() }
                    Pair(bDeferred.await(), sDeferred.await())
                }

                val bookList   = booksSnap.documents.mapNotNull { it.toBook("book", likedBookIds) }
                val serialList = serialsSnap.documents.mapNotNull { it.toBook("serial", likedSerialIds) }

                _books.value = (bookList + serialList)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.ts?.seconds ?: 0L }
                lastBooksFetchMs = System.currentTimeMillis()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
            finally { _loading.value = false }
        }
    }

    fun loadMyBooks(targetUid: String = uid, forceRefresh: Boolean = false) {
        if (targetUid.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!forceRefresh && lastMyBooksFetchUid == targetUid
            && _myBooks.value.isNotEmpty()
            && (now - lastMyBooksFetchMs) < BOOK_CACHE_TTL_MS) return
        viewModelScope.launch {
            try {
                val (booksSnap, serialsSnap) = coroutineScope {
                    val bDeferred = async { firestore.collection("books").whereEqualTo("uid", targetUid).orderBy("updatedAt", Query.Direction.DESCENDING).limit(30).get().await() }
                    val sDeferred = async { firestore.collection("serials").whereEqualTo("uid", targetUid).orderBy("updatedAt", Query.Direction.DESCENDING).limit(30).get().await() }
                    Pair(bDeferred.await(), sDeferred.await())
                }

                val bookList   = booksSnap.documents.mapNotNull { it.toBook("book", likedBookIds) }
                val serialList = serialsSnap.documents.mapNotNull { it.toBook("serial", likedSerialIds) }

                _myBooks.value = (bookList + serialList)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.ts?.seconds ?: 0L }
                lastMyBooksFetchUid = targetUid
                lastMyBooksFetchMs  = System.currentTimeMillis()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DETAY — type'a göre doğru koleksiyona git
    // ════════════════════════════════════════════════════════════════════════

    fun loadBook(bookId: String, type: String = "book") {
        viewModelScope.launch {
            _loading.value = true
            try {
                val col = if (type == "serial") "serials" else "books"
                
                val (doc, chapSnap) = coroutineScope {
                    val dJob = async { firestore.collection(col).document(bookId).get().await() }
                    val cJob = async { firestore.collection(col).document(bookId).collection("chapters").orderBy("order", Query.Direction.ASCENDING).get().await() }
                    Pair(dJob.await(), cJob.await())
                }

                val likedIds = if (type == "serial") likedSerialIds else likedBookIds
                _selectedBook.value = doc.toBook(type, likedIds)

                _chapters.value = chapSnap.documents.mapNotNull { ch ->
                    ch.toBookChapter(
                        bookId = if (type == "book") bookId else "",
                        serialId = if (type == "serial") bookId else "",
                        likedChapterIds = likedChapterIds // Beğeni kontrolü eklendi
                    )
                }
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
            finally { _loading.value = false }
        }
    }

    fun loadChapter(parentId: String, chapterId: String, type: String = "book") {
        viewModelScope.launch {
            try {
                val col = if (type == "serial") "serials" else "books"
                val doc = firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId).get().await()
                _selectedChapter.value = doc.toBookChapter(
                    bookId   = if (type == "book") parentId else "",
                    serialId = if (type == "serial") parentId else "",
                    likedChapterIds = likedChapterIds
                )
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  OLUŞTUR
    // ════════════════════════════════════════════════════════════════════════

    fun createBook(title: String, desc: String, genre: String, coverImg: String = "", bg: String = "", type: String = "book") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: ""
                val now     = Timestamp.now()
                val col     = if (type == "serial") "serials" else "books"
                
                firestore.collection(col).add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "photoURL"     to myPhoto,
                    "title"        to title,
                    "desc"         to desc,
                    "genre"        to genre,
                    "coverImg"     to coverImg,
                    "bg"           to bg,
                    "chapterCount" to 0,
                    "likes"        to 0,
                    "ts"           to now,
                    "updatedAt"    to now,
                )).await()
                loadMyBooks()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BÖLÜM EKLE
    // ════════════════════════════════════════════════════════════════════════

    fun addChapter(parentId: String, title: String, body: String, type: String = "book") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val col = if (type == "serial") "serials" else "books"
                
                // Güvenlik: sahibi mi kontrol et
                val parentDoc = firestore.collection(col).document(parentId).get().await()
                if (parentDoc.getString("uid") != uid) return@launch

                val order     = (_chapters.value.maxOfOrNull { it.order } ?: 0) + 1
                val wordCount = body.trim().split("\\s+".toRegex()).count { it.isNotBlank() }

                // OPTİMİZASYON: add() metodundan dönen döküman referansı doğrudan ID'yi verir.
                val newChapterRef = firestore.collection(col).document(parentId)
                    .collection("chapters").add(mapOf(
                        "uid"       to uid,
                        "title"     to title,
                        "body"      to body,
                        "order"     to order,
                        "wordCount" to wordCount,
                        "ts"        to Timestamp.now(),
                        if (type == "serial") "serialId" to parentId else "bookId" to parentId,
                    )).await()
                
                val newChId = newChapterRef.id // Ekstra get().await() çağrısı elendi!

                firestore.collection(col).document(parentId).update(
                    "chapterCount", FieldValue.increment(1),
                    "updatedAt",    Timestamp.now(),
                ).await()

                // Kullanıcı bilgileri ve feed güncellemesi
                val userDoc   = firestore.collection("users").document(uid).get().await()
                val myName    = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val parent    = _selectedBook.value
                
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (userDoc.getString("username") ?: ""),
                    "photoURL"     to (userDoc.getString("photoURL") ?: ""),
                    "text"         to "📖 ${parent?.title ?: ""} — Bölüm $order: $title",
                    "imgUrl"       to (parent?.coverImg ?: ""),
                    "imageURL"     to (parent?.coverImg ?: ""),
                    "bookName"     to (parent?.title ?: ""),
                    "authorName"   to myName,
                    "repostType"   to if (type == "serial") "chapter" else "book_chapter",
                    "repostId"     to newChId,
                    if (type == "serial") "serialId" to parentId else "bookId" to parentId,
                    "chapterId"    to newChId,
                    "chapterTitle" to title,
                    "chapterOrder" to order,
                    if (type == "serial") "serialTitle" to (parent?.title ?: "") else "bookTitle" to (parent?.title ?: ""),
                    if (type == "serial") "serialCover" to (parent?.coverImg ?: "") else "bookCover" to (parent?.coverImg ?: ""),
                    "likes"        to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"           to FieldValue.serverTimestamp(),
                )).await()
                
                loadBook(parentId, type)
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BÖLÜM DÜZENLE / SİL
    // ════════════════════════════════════════════════════════════════════════

    fun updateChapter(parentId: String, chapterId: String, newTitle: String, newBody: String, type: String = "book") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val col = if (type == "serial") "serials" else "books"
                val parentDoc = firestore.collection(col).document(parentId).get().await()
                if (parentDoc.getString("uid") != uid) return@launch

                val wc = newBody.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                    .update(mapOf("title" to newTitle.trim(), "body" to newBody.trim(), "wordCount" to wc)).await()

                _chapters.value = _chapters.value.map {
                    if (it.id == chapterId) it.copy(title = newTitle.trim(), body = newBody.trim(), wordCount = wc) else it
                }
                _selectedChapter.value = _selectedChapter.value?.let {
                    if (it.id == chapterId) it.copy(title = newTitle.trim(), body = newBody.trim(), wordCount = wc) else it
                }
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    fun deleteChapter(parentId: String, chapterId: String, type: String = "book") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val col = if (type == "serial") "serials" else "books"
                val parentDoc = firestore.collection(col).document(parentId).get().await()
                if (parentDoc.getString("uid") != uid) return@launch

                firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId).delete().await()
                _chapters.value = _chapters.value.filter { it.id != chapterId }
                firestore.collection(col).document(parentId)
                    .update("chapterCount", _chapters.value.size).await()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BEĞENİ
    // ════════════════════════════════════════════════════════════════════════

    fun toggleLikeBook(book: Book) {
        if (uid.isEmpty()) return
        val nowLiked = !book.isLikedByMe
        val col      = if (book.type == "serial") "serials" else "books"
        val likeCol  = if (book.type == "serial") "serialLikes" else "chapterLikes"

        // Lokal cache'i anında senkronize et (Maksimum UI akıcılığı)
        if (book.type == "serial") {
            likedSerialIds = if (nowLiked) likedSerialIds + book.id else likedSerialIds - book.id
        } else {
            likedBookIds = if (nowLiked) likedBookIds + book.id else likedBookIds - book.id
        }
        updateBookLikeState(book.id, nowLiked)

        viewModelScope.launch {
            try {
                val likeRef = firestore.collection(likeCol).document("${book.id}_$uid")
                val bookRef = firestore.collection(col).document(book.id)
                if (nowLiked) {
                    val myName  = auth.currentUser?.displayName ?: ""
                    val myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
                    likeRef.set(mapOf(
                        "uid"      to uid,
                        "feedId"   to book.id,
                        "serialId" to book.id,
                        "name"     to myName,
                        "photoURL" to myPhoto,
                        "ts"       to Timestamp.now(),
                    )).await()
                    bookRef.update("likes", FieldValue.increment(1)).await()
                    // Supabase — sadece serial için
                    if (book.type == "serial") {
                        supabase.postgrest["serial_likes"].upsert(
                            SerialLikeRow(
                                id       = "${book.id}_$uid",
                                serialId = book.id,
                                uid      = uid,
                                name     = myName,
                                photoUrl = myPhoto,
                            )
                        )
                    }
                } else {
                    likeRef.delete().await()
                    bookRef.update("likes", FieldValue.increment(-1)).await()
                    if (book.type == "serial") {
                        supabase.postgrest["serial_likes"].delete {
                            filter { eq("id", "${book.id}_$uid") }
                        }
                    }
                }
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    private fun updateBookLikeState(bookId: String, liked: Boolean) {
        val delta = if (liked) 1 else -1
        _books.value = _books.value.map {
            if (it.id == bookId) it.copy(isLikedByMe = liked, likes = it.likes + delta) else it
        }
        _selectedBook.value = _selectedBook.value?.let {
            if (it.id == bookId) it.copy(isLikedByMe = liked, likes = it.likes + delta) else it
        }
    }

    fun toggleLikeChapter(parentId: String, chapterId: String, currentlyLiked: Boolean, type: String = "serial"): Boolean {
        if (uid.isEmpty()) return currentlyLiked
        val nowLiked = !currentlyLiked
        
        // Lokal cache güncellemesi
        likedChapterIds = if (nowLiked) likedChapterIds + chapterId else likedChapterIds - chapterId

        _selectedChapter.value?.let { ch ->
            if (ch.id == chapterId) _selectedChapter.value = ch.copy(
                isLikedByMe = nowLiked, likes = ch.likes + if (nowLiked) 1 else -1,
            )
        }
        _chapters.value = _chapters.value.map { ch ->
            if (ch.id == chapterId) ch.copy(isLikedByMe = nowLiked, likes = ch.likes + if (nowLiked) 1 else -1)
            else ch
        }
        
        viewModelScope.launch {
            try {
                val col   = if (type == "serial") "serials" else "books"
                val ref   = firestore.collection("chapterLikes").document("${chapterId}_$uid")
                val chRef = firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                if (nowLiked) {
                    ref.set(mapOf("chapterId" to chapterId, "uid" to uid, "ts" to FieldValue.serverTimestamp())).await()
                    chRef.update("likes", FieldValue.increment(1)).await()
                } else {
                    ref.delete().await()
                    chRef.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
        return nowLiked
    }

    // ════════════════════════════════════════════════════════════════════════
    //  YORUMLAR
    // ════════════════════════════════════════════════════════════════════════

    fun addChapterComment(parentId: String, chapterId: String, text: String, replyTo: String = "", replyToCmtId: String = "", type: String = "serial") {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val col     = if (type == "serial") "serials" else "books"
                val userDoc = firestore.collection("users").document(uid).get().await()
                val name    = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val photo   = userDoc.getString("photoURL") ?: ""
                firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                    .collection("comments").add(mapOf(
                        "uid"          to uid,
                        "name"         to name,
                        "displayName"  to name,
                        "photoURL"     to photo,
                        "text"         to text,
                        "replyTo"      to replyTo,
                        "replyToCmtId" to replyToCmtId,
                        "likes"        to 0,
                        "edited"       to false,
                        "ts"           to FieldValue.serverTimestamp(),
                    )).await()
                firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    fun editChapterComment(parentId: String, chapterId: String, commentId: String, newText: String, type: String = "serial") {
        if (uid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                val col    = if (type == "serial") "serials" else "books"
                val cmtDoc = firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                    .collection("comments").document(commentId).get().await()
                if (cmtDoc.getString("uid") != uid) return@launch
                cmtDoc.reference.update(mapOf("text" to newText, "edited" to true)).await()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    fun deleteChapterComment(parentId: String, chapterId: String, commentId: String, type: String = "serial") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val col       = if (type == "serial") "serials" else "books"
                val cmtDoc    = firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                    .collection("comments").document(commentId).get().await()
                val parentDoc = firestore.collection(col).document(parentId).get().await()
                val isCommentOwner = cmtDoc.getString("uid") == uid
                val isParentOwner  = parentDoc.getString("uid") == uid
                if (!isCommentOwner && !isParentOwner) return@launch
                cmtDoc.reference.delete().await()
                firestore.collection(col).document(parentId)
                    .collection("chapters").document(chapterId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  KİTAP SİL
    // ════════════════════════════════════════════════════════════════════════

    fun deleteBook(bookId: String, type: String = "book") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val col     = if (type == "serial") "serials" else "books"
                val bookDoc = firestore.collection(col).document(bookId).get().await()
                if (bookDoc.getString("uid") != uid) return@launch
                firestore.collection(col).document(bookId).delete().await()
                _books.value   = _books.value.filter { it.id != bookId }
                _myBooks.value = _myBooks.value.filter { it.id != bookId }
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  OKUMA İLERLEMESİ
    // ════════════════════════════════════════════════════════════════════════

    fun initReadPrefs(context: Context) {}

    // Supabase: read_progress (uid, parent_id, chapter_id) — yüksek frekanslı
    // scroll yüzdesi artık Firestore'da değil (fatura optimizasyonu).
    fun saveReadProgress(parentId: String, chapterId: String, scrollPct: Float) {
        if (uid.isEmpty()) return
        val pct = (scrollPct * 1000).toInt().coerceIn(0, 1000)
        _readProgressCache["${parentId}_$chapterId"] = scrollPct
        viewModelScope.launch {
            try {
                supabase.postgrest["read_progress"].upsert(
                    com.heftreng.app.data.model.ReadProgressRow(
                        uid       = uid,
                        parentId  = parentId,
                        chapterId = chapterId,
                        pct       = pct,
                    )
                )
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    fun loadReadProgress(parentId: String, chapterId: String, onResult: (Float) -> Unit) {
        if (uid.isEmpty()) { onResult(0f); return }
        viewModelScope.launch {
            try {
                val row = supabase.postgrest["read_progress"].select {
                    filter {
                        eq("uid", uid)
                        eq("parent_id", parentId)
                        eq("chapter_id", chapterId)
                    }
                    limit(1)
                }.decodeSingleOrNull<com.heftreng.app.data.model.ReadProgressRow>()
                onResult((row?.pct ?: 0) / 1000f)
            } catch (e: Exception) { onResult(0f) }
        }
    }

    private val _readProgressCache = mutableMapOf<String, Float>()

    fun loadReadProgress(parentId: String, chapterId: String): Float =
        _readProgressCache["${parentId}_$chapterId"] ?: 0f

    fun prefetchReadProgress(parentId: String, chapterId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val row = supabase.postgrest["read_progress"].select {
                    filter {
                        eq("uid", uid)
                        eq("parent_id", parentId)
                        eq("chapter_id", chapterId)
                    }
                    limit(1)
                }.decodeSingleOrNull<com.heftreng.app.data.model.ReadProgressRow>()
                _readProgressCache["${parentId}_$chapterId"] = (row?.pct ?: 0) / 1000f
            } catch (e: Exception) { }
        }
    }

    fun clearReadProgress(parentId: String, chapterId: String) {
        _readProgressCache.remove("${parentId}_$chapterId")
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                supabase.postgrest["read_progress"].delete {
                    filter {
                        eq("uid", uid)
                        eq("parent_id", parentId)
                        eq("chapter_id", chapterId)
                    }
                }
            } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS (Performans Odaklı Cache Yapısı)
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun loadLikedBooks() {
        try {
            likedBookIds = firestore.collection("chapterLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull {
                    it.getString("feedId") ?: it.id.substringBefore("_$uid").takeIf { id -> id.isNotBlank() }
                }.toSet()
        } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
    }

    private suspend fun loadLikedSerials() {
        try {
            likedSerialIds = firestore.collection("serialLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull { it.getString("serialId") }.toSet()
        } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
    }

    // Bölüm beğenileri için döküman ID'lerini önbelleğe alan yeni helper metot
    private suspend fun loadLikedChapters() {
        try {
            likedChapterIds = firestore.collection("chapterLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull { it.getString("chapterId") }.toSet()
        } catch (e: Exception) { android.util.Log.w("BookVM", e.message ?: "") }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toBook(
        type: String, likedIds: Set<String>,
    ): Book? {
        val d = data ?: return null
        return Book(
            id           = id,
            uid          = d["uid"]           as? String ?: "",
            name         = (d["displayName"]  as? String)?.takeIf { it.isNotBlank() } ?: d["name"] as? String ?: "",
            photoURL     = d["photoURL"]      as? String ?: "",
            title        = d["title"]         as? String ?: "",
            desc         = d["desc"]          as? String ?: "",
            genre        = d["genre"]         as? String ?: "",
            coverImg     = d["coverImg"]      as? String ?: "",
            bg           = d["bg"]            as? String ?: "",
            chapterCount = (d["chapterCount"] as? Long)?.toInt() ?: 0,
            likes        = (d["likes"]        as? Long)?.toInt() ?: 0,
            ts           = d["ts"]            as? Timestamp,
            updatedAt    = d["updatedAt"]     as? Timestamp,
            isLikedByMe  = id in likedIds,
            type         = type,
        )
    }

    // OPTİMİZASYON: Artık gelen listeden bölümlerin beğenilip beğenilmediğini kontrol ediyor
    private fun com.google.firebase.firestore.DocumentSnapshot.toBookChapter(
        bookId: String = "", serialId: String = "", likedChapterIds: Set<String> = emptySet()
    ): BookChapter? {
        val d = data ?: return null
        return BookChapter(
            id          = id,
            bookId      = bookId,
            serialId    = serialId,
            title       = d["title"]      as? String ?: "",
            body        = d["body"]       as? String ?: "",
            order       = (d["order"]     as? Long)?.toInt() ?: 0,
            wordCount   = (d["wordCount"] as? Long)?.toInt() ?: 0,
            uid         = d["uid"]        as? String ?: "",
            likes       = (d["likes"]     as? Long)?.toInt() ?: 0,
            cmtCount    = (d["cmtCount"]  as? Long)?.toInt() ?: 0,
            isLikedByMe = id in likedChapterIds, // Dinamik hale getirildi!
            ts          = d["ts"]         as? Timestamp,
        )
    }

    // ── Geriye dönük uyumluluk köprüleri ──────────────────────────────────────────
    @Deprecated("loadBook(id, \"serial\") kullan")
    fun loadSerial(serialId: String) = loadBook(serialId, "serial")

    @Deprecated("loadChapter(parentId, chapterId, \"serial\") kullan")
    fun loadSerialChapter(serialId: String, chapterId: String) = loadChapter(serialId, chapterId, "serial")

    @Deprecated("addChapter(parentId, title, body, \"serial\") kullan")
    fun addSerialChapter(serialId: String, title: String, body: String) = addChapter(serialId, title, body, "serial")

    @Deprecated("updateChapter(parentId, chapterId, title, body, \"serial\") kullan")
    fun updateSerialChapter(serialId: String, chapterId: String, newTitle: String, newBody: String) =
        updateChapter(serialId, chapterId, newTitle, newBody, "serial")

    @Deprecated("deleteChapter(parentId, chapterId, \"serial\") kullan")
    fun deleteSerialChapter(serialId: String, chapterId: String) = deleteChapter(serialId, chapterId, "serial")

    val serials get() = books  
    val selectedSerial get() = selectedBook
    val serialChapters get() = chapters
    val selectedSerialChapter get() = selectedChapter

    fun toggleLikeSerial(book: Book) = toggleLikeBook(book)
    fun toggleLikeSerialChapter(serialId: String, chapterId: String, currentlyLiked: Boolean) =
        toggleLikeChapter(serialId, chapterId, currentlyLiked, "serial")
}
