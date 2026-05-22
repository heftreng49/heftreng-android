package com.heftreng.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.BookChapter
import com.heftreng.app.data.model.Chapter
import com.heftreng.app.data.model.Serial
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════════
//  BookViewModel — "books" ve "serials" koleksiyonlarını yönetir.
//
//  Firestore koleksiyonları ayrı kalır (veri migration gerektirmez):
//    - books/{id}/chapters/{id}      → Book / BookChapter modelleri
//    - serials/{id}/chapters/{id}    → Serial / Chapter modelleri
//
//  İleride tek koleksiyona geçilmek istenirse sadece collectionName
//  parametresi değiştirilir.
// ═══════════════════════════════════════════════════════════════════════════

@HiltViewModel
class BookViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    // ── Books state ──────────────────────────────────────────────────────────
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

    // ── Serials state ────────────────────────────────────────────────────────
    private val _serials         = MutableStateFlow<List<Serial>>(emptyList())
    val serials = _serials.asStateFlow()

    private val _mySerials       = MutableStateFlow<List<Serial>>(emptyList())
    val mySerials = _mySerials.asStateFlow()

    private val _selectedSerial  = MutableStateFlow<Serial?>(null)
    val selectedSerial = _selectedSerial.asStateFlow()

    private val _serialChapters  = MutableStateFlow<List<Chapter>>(emptyList())
    val serialChapters = _serialChapters.asStateFlow()

    private val _selectedSerialChapter = MutableStateFlow<Chapter?>(null)
    val selectedSerialChapter = _selectedSerialChapter.asStateFlow()

    private val _chapterComments = MutableStateFlow<List<com.heftreng.app.data.model.ChapterComment>>(emptyList())
    val chapterComments = _chapterComments.asStateFlow()

    // ── Ortak state ──────────────────────────────────────────────────────────
    private val _loading         = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedBookIds   = emptySet<String>()
    private var likedSerialIds = emptySet<String>()

    // ════════════════════════════════════════════════════════════════════════
    //  BOOKS
    // ════════════════════════════════════════════════════════════════════════

    fun loadBooks() {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (uid.isNotEmpty()) loadLikedBooks()
                val snap = firestore.collection("books")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _books.value = snap.documents.mapNotNull { it.toBook(likedBookIds) }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun loadMyBooks(targetUid: String = uid) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("books")
                    .whereEqualTo("uid", targetUid)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _myBooks.value = snap.documents.mapNotNull { it.toBook(likedBookIds) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = firestore.collection("books").document(bookId).get().await()
                _selectedBook.value = doc.toBook(likedBookIds)

                val chapSnap = firestore.collection("books").document(bookId)
                    .collection("chapters")
                    .orderBy("order", Query.Direction.ASCENDING).get().await()
                _chapters.value = chapSnap.documents.mapNotNull { ch ->
                    val d = ch.data ?: return@mapNotNull null
                    BookChapter(
                        id        = ch.id,
                        bookId    = bookId,
                        title     = d["title"]      as? String ?: "",
                        body      = d["body"]       as? String ?: "",
                        order     = (d["order"]     as? Long)?.toInt() ?: 0,
                        wordCount = (d["wordCount"] as? Long)?.toInt() ?: 0,
                        uid       = d["uid"]        as? String ?: "",
                        ts        = d["ts"]         as? Timestamp,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun loadChapter(bookId: String, chapterId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("books").document(bookId)
                    .collection("chapters").document(chapterId).get().await()
                val d = doc.data ?: return@launch
                _selectedChapter.value = BookChapter(
                    id        = doc.id,
                    bookId    = bookId,
                    title     = d["title"]      as? String ?: "",
                    body      = d["body"]       as? String ?: "",
                    order     = (d["order"]     as? Long)?.toInt() ?: 0,
                    wordCount = (d["wordCount"] as? Long)?.toInt() ?: 0,
                    uid       = d["uid"]        as? String ?: "",
                    ts        = d["ts"]         as? Timestamp,
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createBook(title: String, desc: String, genre: String, coverImg: String = "", bg: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: ""
                val now     = Timestamp.now()
                firestore.collection("books").add(mapOf(
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
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addBookChapter(bookId: String, title: String, body: String, order: Int) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: kitabın sahibi mi kontrol et
                val bookDoc = firestore.collection("books").document(bookId).get().await()
                val bookOwner = bookDoc.getString("uid") ?: ""
                if (bookOwner != uid) return@launch  // Sahip değilse işlemi durdur
                val wordCount = body.trim().split("\\s+".toRegex()).size
                firestore.collection("books").document(bookId)
                    .collection("chapters").add(mapOf(
                        "uid"       to uid,
                        "title"     to title,
                        "body"      to body,
                        "order"     to order,
                        "wordCount" to wordCount,
                        "ts"        to Timestamp.now(),
                    )).await()
                firestore.collection("books").document(bookId).update(
                    "chapterCount", FieldValue.increment(1),
                    "updatedAt",    Timestamp.now(),
                ).await()
                loadBook(bookId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleLikeBook(book: Book) {
        if (uid.isEmpty()) return
        val nowLiked = !book.isLikedByMe
        likedBookIds = if (nowLiked) likedBookIds + book.id else likedBookIds - book.id
        _books.value = _books.value.map {
            if (it.id == book.id) it.copy(isLikedByMe = nowLiked, likes = it.likes + if (nowLiked) 1 else -1) else it
        }
        _selectedBook.value = _selectedBook.value?.let {
            if (it.id == book.id) it.copy(isLikedByMe = nowLiked, likes = it.likes + if (nowLiked) 1 else -1) else it
        }
        viewModelScope.launch {
            try {
                val likeRef = firestore.collection("chapterLikes").document("${book.id}_$uid")
                val bookRef = firestore.collection("books").document(book.id)
                if (nowLiked) {
                    val myName  = auth.currentUser?.displayName ?: ""
                    val myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
                    likeRef.set(mapOf(
                        "uid"      to uid,
                        "feedId"   to book.id,
                        "name"     to myName,
                        "photoURL" to myPhoto,
                        "ts"       to Timestamp.now(),
                    )).await()
                    bookRef.update("likes", FieldValue.increment(1)).await()
                } else {
                    likeRef.delete().await()
                    bookRef.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteBook(bookId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: kitabın sahibi mi kontrol et
                val bookDoc = firestore.collection("books").document(bookId).get().await()
                if (bookDoc.getString("uid") != uid) return@launch
                firestore.collection("books").document(bookId).delete().await()
                _books.value   = _books.value.filter { it.id != bookId }
                _myBooks.value = _myBooks.value.filter { it.id != bookId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun loadLikedBooks() {
        try {
            likedBookIds = firestore.collection("chapterLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull {
                    it.getString("feedId") ?: it.id.substringBefore("_$uid").takeIf { id -> id.isNotBlank() }
                }.toSet()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SERIALS
    // ════════════════════════════════════════════════════════════════════════

    fun loadSerials() {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (uid.isNotEmpty()) loadLikedSerials()
                val snap = firestore.collection("serials")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _serials.value = snap.documents.mapNotNull { it.toSerial(likedSerialIds) }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun loadMySerials(targetUid: String = uid) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("serials")
                    .whereEqualTo("uid", targetUid)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _mySerials.value = snap.documents.mapNotNull { it.toSerial(likedSerialIds) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadSerial(serialId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = firestore.collection("serials").document(serialId).get().await()
                _selectedSerial.value = doc.toSerial(likedSerialIds)

                val chapSnap = firestore.collection("serials").document(serialId)
                    .collection("chapters")
                    .orderBy("order", Query.Direction.ASCENDING)
                    .limit(100).get().await()
                _serialChapters.value = chapSnap.documents.mapNotNull { it.toChapter() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun loadSerialChapter(serialId: String, chapterId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId).get().await()
                _selectedSerialChapter.value = doc.toChapter()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createSerial(title: String, desc: String, genre: String, coverImg: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val name    = userDoc.getString("displayName") ?: auth.currentUser?.displayName ?: ""
                val photo   = userDoc.getString("photoURL") ?: auth.currentUser?.photoUrl?.toString() ?: ""
                firestore.collection("serials").add(mapOf(
                    "uid"          to uid,
                    "name"         to name,
                    "photoURL"     to photo,
                    "title"        to title,
                    "desc"         to desc,
                    "genre"        to genre,
                    "coverImg"     to coverImg,
                    "chapterCount" to 0,
                    "likes"        to 0,
                    "ts"           to FieldValue.serverTimestamp(),
                    "updatedAt"    to FieldValue.serverTimestamp(),
                )).await()
                loadMySerials()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addSerialChapter(serialId: String, title: String, body: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: serinin sahibi mi kontrol et
                val serialDoc = firestore.collection("serials").document(serialId).get().await()
                val serialOwner = serialDoc.getString("uid") ?: ""
                if (serialOwner != uid) return@launch  // Sahip değilse işlemi durdur
                val order = (_serialChapters.value.maxOfOrNull { it.order } ?: 0) + 1
                val wc    = body.trim().split("\\s+".toRegex()).size
                firestore.collection("serials").document(serialId)
                    .collection("chapters").add(mapOf(
                        "serialId"  to serialId,
                        "title"     to title,
                        "body"      to body,
                        "order"     to order,
                        "wordCount" to wc,
                        "uid"       to uid,
                        "ts"        to FieldValue.serverTimestamp(),
                    )).await()
                firestore.collection("serials").document(serialId).update(mapOf(
                    "chapterCount" to FieldValue.increment(1),
                    "updatedAt"    to FieldValue.serverTimestamp(),
                )).await()
                // Feed'e otomatik paylaş
                val userDoc    = firestore.collection("users").document(uid).get().await()
                val serial     = _selectedSerial.value
                val myName     = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val chapSnap   = firestore.collection("serials").document(serialId)
                    .collection("chapters")
                    .orderBy("order", Query.Direction.DESCENDING)
                    .limit(1).get().await()
                val newChapterId = chapSnap.documents.firstOrNull()?.id ?: ""
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (userDoc.getString("username") ?: ""),
                    "photoURL"     to (userDoc.getString("photoURL") ?: ""),
                    "text"         to "📖 ${serial?.title ?: ""} — Bölüm $order: $title",
                    "imgUrl"       to (serial?.coverImg ?: ""),
                    "imageURL"     to (serial?.coverImg ?: ""),
                    "bookName"     to (serial?.title ?: ""),
                    "authorName"   to myName,
                    "repostType"   to "chapter",
                    "repostId"     to newChapterId,
                    "serialId"     to serialId,
                    "chapterId"    to newChapterId,
                    "chapterTitle" to title,
                    "chapterOrder" to order,
                    "serialTitle"  to (serial?.title ?: ""),
                    "serialCover"  to (serial?.coverImg ?: ""),
                    "likes"        to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"           to FieldValue.serverTimestamp(),
                )).await()
                loadSerial(serialId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleLikeSerial(serial: Serial) {
        if (uid.isEmpty()) return
        val nowLiked = !serial.isLikedByMe
        likedSerialIds = if (nowLiked) likedSerialIds + serial.id else likedSerialIds - serial.id
        _serials.value = _serials.value.map {
            if (it.id == serial.id) it.copy(isLikedByMe = nowLiked, likes = it.likes + if (nowLiked) 1 else -1) else it
        }
        _selectedSerial.value?.let { s ->
            if (s.id == serial.id) _selectedSerial.value = s.copy(isLikedByMe = nowLiked, likes = s.likes + if (nowLiked) 1 else -1)
        }
        viewModelScope.launch {
            try {
                val ref = firestore.collection("serialLikes").document("${serial.id}_$uid")
                val ser = firestore.collection("serials").document(serial.id)
                if (nowLiked) {
                    ref.set(mapOf("uid" to uid, "serialId" to serial.id, "ts" to Timestamp.now())).await()
                    ser.update("likes", FieldValue.increment(1)).await()
                } else {
                    ref.delete().await()
                    ser.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleLikeSerialChapter(serialId: String, chapterId: String, currentlyLiked: Boolean): Boolean {
        if (uid.isEmpty()) return currentlyLiked
        val nowLiked = !currentlyLiked
        _selectedSerialChapter.value?.let { ch ->
            if (ch.id == chapterId) _selectedSerialChapter.value = ch.copy(
                isLikedByMe = nowLiked, likes = ch.likes + if (nowLiked) 1 else -1,
            )
        }
        _serialChapters.value = _serialChapters.value.map { ch ->
            if (ch.id == chapterId) ch.copy(isLikedByMe = nowLiked, likes = ch.likes + if (nowLiked) 1 else -1)
            else ch
        }
        viewModelScope.launch {
            try {
                val ref   = firestore.collection("chapterLikes").document("${chapterId}_$uid")
                val chRef = firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                if (nowLiked) {
                    ref.set(mapOf("chapterId" to chapterId, "serialId" to serialId, "uid" to uid, "ts" to FieldValue.serverTimestamp())).await()
                    chRef.update("likes", FieldValue.increment(1)).await()
                } else {
                    ref.delete().await()
                    chRef.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        return nowLiked
    }

    fun isChapterLiked(chapterId: String, callback: (Boolean) -> Unit) {
        if (uid.isEmpty()) { callback(false); return }
        viewModelScope.launch {
            try {
                val exists = firestore.collection("chapterLikes")
                    .document("${chapterId}_$uid").get().await().exists()
                callback(exists)
            } catch (e: Exception) { callback(false) }
        }
    }

    fun addChapterComment(serialId: String, chapterId: String, text: String, replyTo: String = "", replyToCmtId: String = "") {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val name    = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val photo   = userDoc.getString("photoURL") ?: ""
                firestore.collection("serials").document(serialId)
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
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editChapterComment(serialId: String, chapterId: String, commentId: String, newText: String) {
        viewModelScope.launch {
            try {
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                    .collection("comments").document(commentId)
                    .update(mapOf("text" to newText, "edited" to true)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteChapterComment(serialId: String, chapterId: String, commentId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                    .collection("comments").document(commentId).delete().await()
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Okuma ilerlemesi (SharedPreferences) ─────────────────────────────────
    private var _readPrefs: android.content.SharedPreferences? = null

    fun initReadPrefs(context: Context) {
        _readPrefs = context.getSharedPreferences("heft_read_progress", Context.MODE_PRIVATE)
    }

    fun saveReadProgress(serialId: String, chapterId: String, scrollPct: Float) {
        val pct = (scrollPct * 1000).toInt().coerceIn(0, 1000)
        _readPrefs?.edit()?.putInt("rp_${serialId}_$chapterId", pct)?.apply()
    }

    fun loadReadProgress(serialId: String, chapterId: String): Float {
        return (_readPrefs?.getInt("rp_${serialId}_$chapterId", 0) ?: 0) / 1000f
    }

    fun clearReadProgress(serialId: String, chapterId: String) {
        _readPrefs?.edit()?.remove("rp_${serialId}_$chapterId")?.apply()
    }

    fun updateSerialChapter(serialId: String, chapterId: String, newTitle: String, newBody: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: serinin sahibi mi kontrol et
                val serialDoc = firestore.collection("serials").document(serialId).get().await()
                if (serialDoc.getString("uid") != uid) return@launch
                val wc = newBody.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                    .update(mapOf("title" to newTitle.trim(), "body" to newBody.trim(), "wordCount" to wc)).await()
                _serialChapters.value = _serialChapters.value.map {
                    if (it.id == chapterId) it.copy(title = newTitle.trim(), body = newBody.trim(), wordCount = wc) else it
                }
                _selectedSerialChapter.value = _selectedSerialChapter.value?.let {
                    if (it.id == chapterId) it.copy(title = newTitle.trim(), body = newBody.trim(), wordCount = wc) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteSerialChapter(serialId: String, chapterId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: serinin sahibi mi kontrol et
                val serialDoc = firestore.collection("serials").document(serialId).get().await()
                if (serialDoc.getString("uid") != uid) return@launch
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId).delete().await()
                _serialChapters.value = _serialChapters.value.filter { it.id != chapterId }
                firestore.collection("serials").document(serialId)
                    .update("chapterCount", _serialChapters.value.size).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun loadLikedSerials() {
        try {
            likedSerialIds = firestore.collection("serialLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull { it.getString("serialId") }.toSet()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Extension mapper'lar ─────────────────────────────────────────────────
    private fun com.google.firebase.firestore.DocumentSnapshot.toBook(likedIds: Set<String>): Book? {
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
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toSerial(liked: Set<String>): Serial? {
        val d = data ?: return null
        return Serial(
            id           = id,
            uid          = d["uid"]           as? String ?: "",
            name         = d["name"]          as? String ?: "",
            photoURL     = d["photoURL"]      as? String ?: "",
            title        = d["title"]         as? String ?: "",
            desc         = d["desc"]          as? String ?: "",
            genre        = d["genre"]         as? String ?: "",
            coverImg     = d["coverImg"]      as? String ?: "",
            chapterCount = (d["chapterCount"] as? Long)?.toInt() ?: 0,
            likes        = (d["likes"]        as? Long)?.toInt() ?: 0,
            ts           = d["ts"]            as? Timestamp,
            updatedAt    = d["updatedAt"]     as? Timestamp,
            isLikedByMe  = id in liked,
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChapter(likedIds: Set<String> = emptySet()): Chapter? {
        val d = data ?: return null
        return Chapter(
            id          = id,
            serialId    = d["serialId"]   as? String ?: "",
            title       = d["title"]      as? String ?: "",
            body        = d["body"]       as? String ?: "",
            order       = (d["order"]     as? Long)?.toInt() ?: 0,
            wordCount   = (d["wordCount"] as? Long)?.toInt() ?: 0,
            uid         = d["uid"]        as? String ?: "",
            likes       = (d["likes"]     as? Long)?.toInt() ?: 0,
            cmtCount    = (d["cmtCount"]  as? Long)?.toInt() ?: 0,
            isLikedByMe = id in likedIds,
            ts          = d["ts"]         as? Timestamp,
        )
    }
}
