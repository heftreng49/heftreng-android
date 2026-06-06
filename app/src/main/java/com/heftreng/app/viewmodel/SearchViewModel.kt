package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Arama sonucu tipi — kullanıcı / post / serial / library
data class SearchResult(
    val id         : String,
    val type       : String,   // "user" | "post" | "serial" | "library_book" | "library_author" | "author" | "book_quote"
    val title      : String,
    val subtitle   : String,
    val imageUrl   : String = "",
    val uid        : String = "",
    val extra      : String = "",  // avgRating, quoteCount vb. özet bilgi
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _results     = MutableStateFlow<List<User>>(emptyList())
    val results = _results.asStateFlow()

    // Geniş arama sonuçları (post + serial + user birlikte)
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<User>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    // Sekme: 0=Hepsi 1=Kullanıcı 2=Gönderi 3=Kitap
    private val _activeTab = MutableStateFlow(0)
    val activeTab = _activeTab.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    fun setTab(tab: Int) { _activeTab.value = tab }

    private var searchJob: Job? = null

    // ── Kullanıcı araması — 300ms debounce ile ────────────────────────────
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            searchJob?.cancel()
            _results.value = emptyList()
            _searchResults.value = emptyList()
            _loading.value = false
            return
        }
        // Önceki job'u iptal et — kullanıcı yazmaya devam ediyorsa eski sorguyu atla
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _loading.value = true
            delay(300) // 300ms bekle — harf harf Firestore'a gitme
            try {
                searchAll(q)
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Geniş arama — kullanıcı + post + serial ───────────────
    private suspend fun searchAll(q: String) {
        val results = mutableListOf<SearchResult>()
        val qLower  = q.lowercase()
        val qCap    = q.replaceFirstChar { it.uppercaseChar() }

        // Kullanıcı — displayName + name + username prefix araması (3 varyant)
        // Firestore prefix case-sensitive olduğu için orijinal, lowercase, capitalize denenir
        try {
            val seenIds = mutableSetOf<String>()

            fun mapUser(doc: com.google.firebase.firestore.DocumentSnapshot): SearchResult? {
                val d = doc.data ?: return null
                val name  = (d["displayName"] as? String)?.ifBlank { null }
                            ?: (d["name"]        as? String)?.ifBlank { null }
                            ?: (d["email"]       as? String)?.substringBefore("@")?.ifBlank { null }
                            ?: (d["username"]    as? String)?.ifBlank { null }
                            ?: return null
                val uname = d["username"] as? String ?: ""
                return SearchResult(
                    id       = doc.id,
                    type     = "user",
                    title    = name,
                    subtitle = if (uname.isNotBlank()) "@$uname" else d["email"] as? String ?: "",
                    imageUrl = d["photoURL"] as? String ?: "",
                    uid      = doc.id,
                )
            }

            for (prefix in listOf(q, qLower, qCap).distinct()) {
                val snap = firestore.collection("users")
                    .orderBy("displayName")
                    .startAt(prefix).endAt(prefix + "\uF8FF")
                    .limit(15).get().await()
                for (doc in snap.documents) {
                    if (seenIds.add(doc.id)) mapUser(doc)?.let { results += it }
                }
            }
            // username lowercase prefix
            val uSnap2 = firestore.collection("users")
                .orderBy("username")
                .startAt(qLower).endAt(qLower + "\uF8FF")
                .limit(15).get().await()
            for (doc in uSnap2.documents) {
                if (seenIds.add(doc.id)) mapUser(doc)?.let { results += it }
            }
            // name alanı ile de dene
            for (prefix in listOf(q, qLower, qCap).distinct()) {
                val snap = firestore.collection("users")
                    .orderBy("name")
                    .startAt(prefix).endAt(prefix + "\uF8FF")
                    .limit(10).get().await()
                for (doc in snap.documents) {
                    if (seenIds.add(doc.id)) mapUser(doc)?.let { results += it }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Feed gönderi — orderBy index gerektirdiği için son 300 postu çekip client-side filtrele
        try {
            val pSnap = firestore.collection("feed")
                .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await()
            results += pSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val text = d["text"] as? String ?: ""
                val qText = d["quoteText"] as? String ?: ""
                val combined = "$text $qText".lowercase()
                if (!combined.contains(qLower)) return@mapNotNull null
                val display = text.ifBlank { qText }
                if (display.isBlank()) return@mapNotNull null
                val name = (d["displayName"] as? String)?.ifBlank { null }
                           ?: (d["name"] as? String)?.ifBlank { null }
                           ?: (d["email"] as? String)?.substringBefore("@") ?: ""
                SearchResult(
                    id       = doc.id,
                    type     = "post",
                    title    = display.take(80),
                    subtitle = name,
                    imageUrl = d["photoURL"] as? String ?: "",
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Serials — tümünü çekip client-side filtrele (index gerektirmez)
        try {
            val sSnap = firestore.collection("serials")
                .limit(100).get().await()
            results += sSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val title = d["title"] as? String ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
                if (!title.lowercase().contains(qLower)) return@mapNotNull null
                SearchResult(
                    id       = doc.id,
                    type     = "serial",
                    title    = title,
                    // Tema: serials dokümanında yazar adı "name" alanında
                    subtitle = d["name"] as? String ?: d["authorName"] as? String ?: "",
                    // Tema: serials dokümanında kapak "coverImg" alanında (coverURL değil)
                    imageUrl = d["coverImg"] as? String ?: "",
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Alıntı yazar — "quote.author" nested field (site formatı)
        try {
            val authorSnap = firestore.collection("feed")
                .whereEqualTo("quote.author", q)
                .limit(10).get().await()
            // Ayrıca flat authorName field
            val authorSnap2 = firestore.collection("feed")
                .orderBy("authorName")
                .startAt(q).endAt(q + "")
                .limit(10).get().await()
            val allAuthorDocs = (authorSnap.documents + authorSnap2.documents)
                .distinctBy { it.id }
            val authorNames = allAuthorDocs.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val name = (d["quote"] as? Map<*, *>)?.get("author") as? String
                           ?: d["authorName"] as? String ?: return@mapNotNull null
                name.takeIf { it.isNotBlank() }
            }.distinct().take(5)
            results += authorNames.map { name ->
                SearchResult(id = name, type = "author", title = name,
                    subtitle = "Yazar Alıntıları", imageUrl = "")
            }
        } catch (_: Exception) {}

        // Alıntı kitap — "quote.book" nested field (site formatı)
        try {
            val bookSnap = firestore.collection("feed")
                .whereEqualTo("quote.book", q)
                .limit(10).get().await()
            val bookSnap2 = firestore.collection("feed")
                .orderBy("bookName")
                .startAt(q).endAt(q + "")
                .limit(10).get().await()
            val allBookDocs = (bookSnap.documents + bookSnap2.documents)
                .distinctBy { it.id }
            val bookNames = allBookDocs.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val name = (d["quote"] as? Map<*, *>)?.get("book") as? String
                           ?: d["bookName"] as? String ?: return@mapNotNull null
                name.takeIf { it.isNotBlank() }
            }.distinct().take(5)
            results += bookNames.map { name ->
                SearchResult(id = name, type = "book_quote", title = name,
                    subtitle = "Kitap Alıntıları", imageUrl = "")
            }
        } catch (_: Exception) {}

        // Alıntı — zaten feed taramasında yakalanıyor (quoteText client-side filtrelendi)
        // Burada sadece library_books subcollection quotes'dan gelen ek alıntıları ekle
        try {
            val existingPostIds = results.filter { it.type == "post" }.map { it.id }.toSet()
            val qSnap = firestore.collection("feed")
                .whereEqualTo("type", "library_quote")
                .limit(50).get().await()
            results += qSnap.documents.mapNotNull { doc ->
                if (doc.id in existingPostIds) return@mapNotNull null
                val d = doc.data ?: return@mapNotNull null
                val qt = d["quoteText"] as? String
                    ?: (d["quote"] as? Map<*, *>)?.get("text") as? String
                    ?: return@mapNotNull null
                if (qt.isBlank()) return@mapNotNull null
                SearchResult(
                    id       = doc.id,
                    type     = "post",
                    title    = qt.take(80),
                    subtitle = d["displayName"] as? String ?: d["name"] as? String ?: "",
                    imageUrl = "",
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // ── Kütüphane Yazarları — authors koleksiyonu ───────────────────
        try {
            val seenAuthorIds = mutableSetOf<String>()
            for (prefix in listOf(q, qLower, qCap).distinct()) {
                val aSnap = firestore.collection("authors")
                    .orderBy("nameLower")
                    .startAt(prefix.lowercase()).endAt(prefix.lowercase() + "\uF8FF")
                    .limit(8).get().await()
                for (doc in aSnap.documents) {
                    if (!seenAuthorIds.add(doc.id)) continue
                    val d = doc.data ?: continue
                    val name = d["name"] as? String ?: continue
                    val bookCount  = (d["bookCount"]  as? Long)?.toInt() ?: 0
                    val quoteCount = (d["quoteCount"] as? Long)?.toInt() ?: 0
                    results += SearchResult(
                        id       = doc.id,
                        type     = "library_author",
                        title    = name,
                        subtitle = d["nationality"] as? String ?: "",
                        imageUrl = d["photoURL"] as? String ?: "",
                        extra    = buildString {
                            if (bookCount  > 0) append("$bookCount kitap")
                            if (quoteCount > 0) append(" · $quoteCount alıntı")
                        }.trim(' ', '·'),
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // ── Kütüphane Kitapları — library_books koleksiyonu ─────────────
        try {
            val seenBookIds = mutableSetOf<String>()
            for (prefix in listOf(q, qLower, qCap).distinct()) {
                val bSnap = firestore.collection("library_books")
                    .orderBy("titleLower")
                    .startAt(prefix.lowercase()).endAt(prefix.lowercase() + "\uF8FF")
                    .limit(8).get().await()
                for (doc in bSnap.documents) {
                    if (!seenBookIds.add(doc.id)) continue
                    val d = doc.data ?: continue
                    val title = d["title"] as? String ?: continue
                    val avgRating  = (d["avgRating"]  as? Number)?.toFloat() ?: 0f
                    val quoteCount = (d["quoteCount"] as? Long)?.toInt() ?: 0
                    results += SearchResult(
                        id       = doc.id,
                        type     = "library_book",
                        title    = title,
                        subtitle = d["authorName"] as? String ?: "",
                        imageUrl = d["coverImg"]   as? String ?: "",
                        extra    = buildString {
                            if (avgRating  > 0f) append("★ ${"%.1f".format(avgRating)}")
                            if (quoteCount > 0)  append(" · $quoteCount alıntı")
                        }.trim(' ', '·'),
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        _searchResults.value = results

        // Geriye dönük uyum: User listesini de doldur
        _results.value = results.filter { it.type == "user" }.map { r ->
            User(uid = r.uid, displayName = r.title, username = r.subtitle.removePrefix("@"),
                 photoURL = r.imageUrl, bio = "")
        }
    }

    // ── Takip önerileri — düşük maliyetli: sadece 50 kullanıcı çek ──
    fun loadSuggestions() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Takip edilenleri küçük limit ile al (500 -> 100)
                val followSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", uid)
                    .limit(100).get().await()
                val followedUids = followSnap.documents
                    .mapNotNull { it.getString("targetUid") }.toSet() + uid

                // Kullanıcıları followersCount desc ile al — zaten sıralı gelir (200 -> 50)
                val usersSnap = firestore.collection("users")
                    .orderBy("followerCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()

                _suggestions.value = usersSnap.documents
                    .mapNotNull { it.toUser() }
                    .filter { it.uid !in followedUids }
                    .take(30)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Takip et / bırak ─────────────────────────────────────
    fun toggleFollow(targetUid: String) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("follows").document("${uid}_$targetUid")
                if (ref.get().await().exists()) {
                    ref.delete().await()
                } else {
                    val myDoc = try { firestore.collection("users").document(uid).get().await() } catch (_: Exception) { null }
                    ref.set(mapOf(
                        "fromUid"   to uid,
                        "fromName"  to (myDoc?.getString("displayName") ?: myDoc?.getString("name") ?: ""),
                        "fromPhoto" to (myDoc?.getString("photoURL") ?: ""),
                        "targetUid" to targetUid,
                        "ts"        to com.google.firebase.Timestamp.now(),
                    )).await()
                    // Sayaç güncelle
                    firestore.collection("users").document(uid)
                        .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1))
                    firestore.collection("users").document(targetUid)
                        .update("followerCount", com.google.firebase.firestore.FieldValue.increment(1))
                }
                loadSuggestions()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
        val d = data ?: return null
        // displayName → name → email prefix → username → "Kullanıcı" sıralamasıyla al
        val rawName = (d["displayName"] as? String)?.ifBlank { null }
                      ?: (d["name"]        as? String)?.ifBlank { null }
                      ?: (d["email"]       as? String)?.substringBefore("@")?.ifBlank { null }
                      ?: (d["username"]    as? String)?.ifBlank { null }
                      ?: id.take(8) // Firestore dokümanı var ama tüm alanlar boş
        return User(
            uid            = id,
            displayName    = rawName,
            username       = d["username"]    as? String ?: "",
            photoURL       = d["photoURL"]    as? String ?: "",
            bio            = d["bio"]         as? String ?: "",
            followersCount = (d["followersCount"] as? Long)?.toInt() ?: 0,
        )
    }
}
