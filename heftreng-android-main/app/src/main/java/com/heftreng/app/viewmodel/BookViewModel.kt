package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.BookChapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class BookViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _books          = MutableStateFlow<List<Book>>(emptyList())
    val books = _books.asStateFlow()

    private val _myBooks        = MutableStateFlow<List<Book>>(emptyList())
    val myBooks = _myBooks.asStateFlow()

    private val _selectedBook   = MutableStateFlow<Book?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _chapters       = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters = _chapters.asStateFlow()

    private val _selectedChapter = MutableStateFlow<BookChapter?>(null)
    val selectedChapter = _selectedChapter.asStateFlow()

    private val _loading        = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedBookIds = emptySet<String>()

    // ── Tüm kitaplar ────────────────────────────────────
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

    // ── Kullanıcının kitapları ───────────────────────────
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

    // ── Kitap detayı + bölümler ─────────────────────────
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
                        title     = d["title"]     as? String ?: "",
                        body      = d["body"]      as? String ?: "",
                        order     = (d["order"]    as? Long)?.toInt() ?: 0,
                        wordCount = (d["wordCount"]as? Long)?.toInt() ?: 0,
                        uid       = d["uid"]       as? String ?: "",
                        ts        = d["ts"]        as? Timestamp,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Bölüm oku ───────────────────────────────────────
    fun loadChapter(bookId: String, chapterId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("books").document(bookId)
                    .collection("chapters").document(chapterId).get().await()
                val d = doc.data ?: return@launch
                _selectedChapter.value = BookChapter(
                    id        = doc.id,
                    bookId    = bookId,
                    title     = d["title"]     as? String ?: "",
                    body      = d["body"]      as? String ?: "",
                    order     = (d["order"]    as? Long)?.toInt() ?: 0,
                    wordCount = (d["wordCount"]as? Long)?.toInt() ?: 0,
                    uid       = d["uid"]       as? String ?: "",
                    ts        = d["ts"]        as? Timestamp,
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kitap oluştur ───────────────────────────────────
    fun createBook(title: String, desc: String, genre: String, coverImg: String = "", bg: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc  = firestore.collection("users").document(uid).get().await()
                val myName   = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val myPhoto  = userDoc.getString("photoURL") ?: ""
                val now      = Timestamp.now()
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

    // ── Bölüm ekle ──────────────────────────────────────
    fun addChapter(bookId: String, title: String, body: String, order: Int) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
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
                firestore.collection("books").document(bookId)
                    .update(
                        "chapterCount", FieldValue.increment(1),
                        "updatedAt",    Timestamp.now(),
                    ).await()
                loadBook(bookId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Beğeni toggle ───────────────────────────────────
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
                val likeRef  = firestore.collection("chapterLikes").document("${book.id}_$uid")
                val bookRef  = firestore.collection("books").document(book.id)
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

    // ── Kitap sil ───────────────────────────────────────
    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            try {
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
}

// ── Extension ───────────────────────────────────────────────────────────────
private fun com.google.firebase.firestore.DocumentSnapshot.toBook(likedIds: Set<String>): Book? {
    val d = data ?: return null
    return Book(
        id           = id,
        uid          = d["uid"]          as? String ?: "",
        name         = (d["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: d["name"] as? String ?: "",
        photoURL     = d["photoURL"]     as? String ?: "",
        title        = d["title"]        as? String ?: "",
        desc         = d["desc"]         as? String ?: "",
        genre        = d["genre"]        as? String ?: "",
        coverImg     = d["coverImg"]     as? String ?: "",
        bg           = d["bg"]           as? String ?: "",
        chapterCount = (d["chapterCount"]as? Long)?.toInt() ?: 0,
        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
        ts           = d["ts"]           as? Timestamp,
        updatedAt    = d["updatedAt"]    as? Timestamp,
        isLikedByMe  = id in likedIds,
    )
}
