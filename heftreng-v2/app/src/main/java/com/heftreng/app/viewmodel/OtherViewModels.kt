package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.KurdiLesson
import com.heftreng.app.data.model.Notification
import com.heftreng.app.data.model.ReadingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════
//  NotificationsViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { observe() }

    // userNotifs/{uid}/msgs — Rules: isOwner(uid)
    private fun observe() {
        if (uid.isEmpty()) { _loading.value = false; return }
        _loading.value = true
        firestore.collection("userNotifs").document(uid)
            .collection("msgs")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                _notifications.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Notification(
                        id        = doc.id,
                        fromUid   = d["fromUid"]   as? String ?: "",
                        fromName  = d["fromName"]  as? String ?: "",
                        fromPhoto = d["fromPhoto"] as? String ?: "",
                        type      = d["type"]      as? String ?: "",
                        message   = d["message"]   as? String ?: "",
                        url       = d["url"]       as? String ?: "",
                        read      = d["read"]      as? Boolean ?: false,
                        ts        = d["ts"] as? Timestamp,
                    )
                }
                _loading.value = false
                // Okunmamışları işaretle — Rules: update onlyChanging(['read'])
                snap.documents.filter { it.getBoolean("read") == false }
                    .forEach { it.reference.update("read", true) }
            }
    }
}

// ═══════════════════════════════════════════════════════════
//  KurdiViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<KurdiLesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _xp      = MutableStateFlow(0)
    val xp = _xp.asStateFlow()

    private val _streak  = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _level   = MutableStateFlow(1)
    val level = _level.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (uid.isNotEmpty()) {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    _xp.value     = (userDoc.getLong("xp")     ?: 0).toInt()
                    _streak.value = (userDoc.getLong("streak")  ?: 0).toInt()
                    _level.value  = (userDoc.getLong("level")   ?: 1).toInt()
                }

                // kurdiLessons koleksiyonu — yoksa kf_lessons'a bak
                val snap = try {
                    firestore.collection("kurdiLessons")
                        .orderBy("order", Query.Direction.ASCENDING).get().await()
                } catch (_: Exception) {
                    firestore.collection("kf_lessons")
                        .orderBy("order", Query.Direction.ASCENDING).get().await()
                }

                val completedIds = if (uid.isNotEmpty()) {
                    // users/{uid}/kf_progress — Rules: isOwner(uid)
                    try {
                        firestore.collection("users").document(uid)
                            .collection("kf_progress").get().await()
                            .documents.map { it.id }.toSet()
                    } catch (_: Exception) { emptySet() }
                } else emptySet()

                _lessons.value = if (snap.documents.isNotEmpty()) {
                    snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        KurdiLesson(
                            id       = doc.id,
                            title    = d["title"]    as? String ?: "",
                            subtitle = d["subtitle"] as? String ?: d["desc"] as? String ?: "",
                            type     = d["type"]     as? String ?: "mcq",
                            xpReward = (d["xpReward"] as? Long)?.toInt() ?: 10,
                            completed= doc.id in completedIds,
                            order    = (d["order"] as? Long)?.toInt() ?: 0,
                        )
                    }
                } else sampleLessons
            } catch (e: Exception) {
                _lessons.value = sampleLessons
                e.printStackTrace()
            } finally { _loading.value = false }
        }
    }

    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // users/{uid}/kf_progress/{lessonId} — Rules: isOwner(uid)
                firestore.collection("users").document(uid)
                    .collection("kf_progress").document(lessonId)
                    .set(mapOf("completedAt" to Timestamp.now())).await()
                val newXp = _xp.value + 10
                firestore.collection("users").document(uid)
                    .update(mapOf("xp" to newXp, "level" to (newXp / 100) + 1)).await()
                _xp.value    = newXp
                _level.value = (newXp / 100) + 1
                _lessons.value = _lessons.value.map {
                    if (it.id == lessonId) it.copy(completed = true) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private val sampleLessons = listOf(
        KurdiLesson("1", "Silav û Nasîn",   "Merhaba ve Tanışma",   "mcq",   10, false, 1),
        KurdiLesson("2", "Jimare",           "Sayılar 1-10",          "fill",  10, false, 2),
        KurdiLesson("3", "Reng",             "Renkler",               "match", 10, false, 3),
        KurdiLesson("4", "Malbat",           "Aile üyeleri",          "build", 10, false, 4),
        KurdiLesson("5", "Xwarinên rojane",  "Günlük yiyecekler",     "mcq",   15, false, 5),
        KurdiLesson("6", "Roj û Meh",        "Günler ve Aylar",       "fill",  15, false, 6),
        KurdiLesson("7", "Rengên xwezayê",   "Doğa renkleri",         "match", 15, false, 7),
        KurdiLesson("8", "Hebûn û Tunebûn",  "Var/Yok cümleleri",     "build", 20, false, 8),
    )
}

// ═══════════════════════════════════════════════════════════
//  BooksViewModel — Kitap bölümü (books koleksiyonu)
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _books        = MutableStateFlow<List<Book>>(emptyList())
    val books = _books.asStateFlow()

    private val _readingList  = MutableStateFlow<List<ReadingEntry>>(emptyList())
    val readingList = _readingList.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init {
        observeBooks()
        if (uid.isNotEmpty()) loadReadingList()
    }

    // books/{bookId} — Rules: read: true
    private fun observeBooks() {
        _loading.value = true
        firestore.collection("books")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                _books.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Book(
                        id          = doc.id,
                        uid         = d["uid"]         as? String ?: "",
                        title       = d["title"]       as? String ?: "",
                        author      = d["author"]      as? String ?: "",
                        coverURL    = d["coverURL"]    as? String ?: "",
                        description = d["description"] as? String ?: "",
                        genre       = d["genre"]       as? String ?: "",
                        pageCount   = (d["pageCount"]  as? Long)?.toInt() ?: 0,
                        language    = d["language"]    as? String ?: "ku",
                        rating      = (d["rating"]     as? Double) ?: 0.0,
                        ratingCount = (d["ratingCount"]as? Long)?.toInt() ?: 0,
                        ts          = d["ts"] as? Timestamp,
                    )
                }
                _loading.value = false
            }
    }

    // readingLists/{uid}/books — Rules: isOwner(uid) create/update/delete
    fun loadReadingList() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val snap = firestore.collection("readingLists").document(uid)
                    .collection("books").get().await()
                _readingList.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ReadingEntry(
                        sid      = d["sid"]       as? String ?: "",
                        title    = d["title"]     as? String ?: "",
                        coverImg = d["coverImg"]  as? String ?: "",
                        bg       = d["bg"]        as? String ?: "",
                        status   = d["status"]    as? String ?: "okumak_istiyorum",
                        updatedAt= d["updatedAt"] as? Timestamp,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // books create — Rules: uid==auth.uid && notBanned()
    fun addBook(title: String, author: String, genre: String, description: String, language: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("books").add(mapOf(
                    "uid"         to uid,
                    "title"       to title,
                    "author"      to author,
                    "coverURL"    to "",
                    "description" to description,
                    "genre"       to genre,
                    "pageCount"   to 0,
                    "language"    to language,
                    "rating"      to 0.0,
                    "ratingCount" to 0,
                    "ts"          to Timestamp.now(),
                )).await()
                // XP ödülü
                firestore.collection("users").document(uid)
                    .update("xp", FieldValue.increment(10)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // readingLists/{uid}/books/{bookId} — status güncelle
    // Rules: onlyChanging(['status','updatedAt','title','coverImg','bg'])
    fun setReadingStatus(book: Book, status: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val docRef = firestore.collection("readingLists").document(uid)
                    .collection("books").document(book.id)
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(mapOf("status" to status, "updatedAt" to Timestamp.now())).await()
                } else {
                    // create — Rules: sid is string && status in [...]
                    docRef.set(mapOf(
                        "sid"      to book.id,
                        "title"    to book.title,
                        "coverImg" to book.coverURL,
                        "bg"       to "",
                        "status"   to status,
                        "updatedAt"to Timestamp.now(),
                    )).await()
                }
                loadReadingList()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun removeFromReadingList(bookId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid)
                    .collection("books").document(bookId).delete().await()
                loadReadingList()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getReadingStatus(bookId: String): String? =
        _readingList.value.firstOrNull { it.sid == bookId }?.status
}
