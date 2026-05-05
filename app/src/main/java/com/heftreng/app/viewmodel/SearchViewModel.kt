package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Arama sonucu tipi — kullanıcı / post / serial
data class SearchResult(
    val id         : String,
    val type       : String,   // "user" | "post" | "serial"
    val title      : String,
    val subtitle   : String,
    val imageUrl   : String = "",
    val uid        : String = "",
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

    // ── Kullanıcı araması (eski uyum) ────────────────────────
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) { _results.value = emptyList(); _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _loading.value = true
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

        // Kullanıcı — displayName prefix
        try {
            val uSnap = firestore.collection("users")
                .orderBy("displayName")
                .startAt(q).endAt(q + "\uF8FF")
                .limit(15).get().await()
            results += uSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val name  = d["displayName"] as? String ?: d["name"] as? String ?: return@mapNotNull null
                val uname = d["username"] as? String ?: ""
                SearchResult(
                    id       = doc.id,
                    type     = "user",
                    title    = name,
                    subtitle = if (uname.isNotBlank()) "@$uname" else d["email"] as? String ?: "",
                    imageUrl = d["photoURL"] as? String ?: "",
                    uid      = doc.id,
                )
            }
            // username prefix de dene
            val uSnap2 = firestore.collection("users")
                .orderBy("username")
                .startAt(qLower).endAt(qLower + "\uF8FF")
                .limit(10).get().await()
            val existingIds = results.map { it.id }.toSet()
            results += uSnap2.documents.mapNotNull { doc ->
                if (doc.id in existingIds) return@mapNotNull null
                val d = doc.data ?: return@mapNotNull null
                val name  = d["displayName"] as? String ?: d["name"] as? String ?: return@mapNotNull null
                val uname = d["username"] as? String ?: ""
                SearchResult(
                    id       = doc.id,
                    type     = "user",
                    title    = name,
                    subtitle = if (uname.isNotBlank()) "@$uname" else "",
                    imageUrl = d["photoURL"] as? String ?: "",
                    uid      = doc.id,
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Feed gönderi — text prefix
        try {
            val pSnap = firestore.collection("feed")
                .orderBy("text")
                .startAt(q).endAt(q + "\uF8FF")
                .limit(10).get().await()
            results += pSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val text = d["text"] as? String ?: return@mapNotNull null
                if (text.isBlank()) return@mapNotNull null
                val name = d["displayName"] as? String ?: d["name"] as? String ?: ""
                SearchResult(
                    id       = doc.id,
                    type     = "post",
                    title    = text.take(80),
                    subtitle = name,
                    imageUrl = d["photoURL"] as? String ?: "",
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Serials — title prefix
        try {
            val sSnap = firestore.collection("serials")
                .orderBy("title")
                .startAt(q).endAt(q + "\uF8FF")
                .limit(10).get().await()
            results += sSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val title = d["title"] as? String ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
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

        // Alıntı (feed quoteText prefix)
        try {
            val qSnap = firestore.collection("feed")
                .orderBy("quoteText")
                .startAt(q).endAt(q + "\uF8FF")
                .limit(8).get().await()
            val existingPostIds = results.filter { it.type == "post" }.map { it.id }.toSet()
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

        _searchResults.value = results

        // Geriye dönük uyum: User listesini de doldur
        _results.value = results.filter { it.type == "user" }.map { r ->
            User(uid = r.uid, displayName = r.title, username = r.subtitle.removePrefix("@"),
                 photoURL = r.imageUrl, bio = "")
        }
    }

    // ── Takip önerileri — TÜM kullanıcılar (takip edilmeyenler) ──
    fun loadSuggestions() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val followSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", uid)
                    .limit(200).get().await()
                val followedUids = followSnap.documents
                    .mapNotNull { it.getString("targetUid") }.toSet() + uid

                // Tüm kullanıcıları çek (limit 100 — Firestore ücretsiz plan için yeterli)
                val usersSnap = firestore.collection("users")
                    .limit(100).get().await()

                _suggestions.value = usersSnap.documents
                    .mapNotNull { it.toUser() }
                    .filter { it.uid !in followedUids }
                    .shuffled()
                    .take(20)
            } catch (e: Exception) { e.printStackTrace() }
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
                    ref.set(mapOf("fromUid" to uid, "targetUid" to targetUid)).await()
                }
                loadSuggestions()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
        val d = data ?: return null
        return User(
            uid            = id,
            displayName    = d["displayName"] as? String ?: d["name"] as? String ?: "",
            username       = d["username"]    as? String ?: "",
            photoURL       = d["photoURL"]    as? String ?: "",
            bio            = d["bio"]         as? String ?: "",
            followersCount = (d["followersCount"] as? Long)?.toInt() ?: 0,
        )
    }
}
