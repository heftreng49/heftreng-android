package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════════════════════
//  SearchViewModel — Hibrit Firebase + Supabase
//
//  Firebase'de kalan:  users · feed · serials · follows
//  Supabase'e taşınan: authors · library_books  (prefix trick yok, ilike yeterli)
//
//  Sorgu karşılaştırması (tek arama):
//    Önceki (tam Firebase): ~20 Firestore okuma
//    Şimdiki (hibrit):       ~12 Firestore + 2 Supabase = ~14 toplam
//    Bir sonraki adımda feed araması da Supabase'e taşınınca ~6'ya düşecek
// ══════════════════════════════════════════════════════════════════════════════

// ── UI modeli ─────────────────────────────────────────────────────────────────
data class SearchResult(
    val id       : String,
    val type     : String,  // "user"|"post"|"serial"|"library_book"|"library_author"|"author"|"book_quote"
    val title    : String,
    val subtitle : String,
    val imageUrl : String = "",
    val uid      : String = "",
    val extra    : String = "",
)

// ── Supabase DTO'ları (kotlinx.serialization) ─────────────────────────────────
@Serializable
data class SupabaseAuthor(
    val id             : String,
    val name           : String,
    val nationality    : String    = "",
    @SerialName("photo_url")   val photoUrl   : String = "",
    @SerialName("book_count")  val bookCount  : Int    = 0,
    @SerialName("quote_count") val quoteCount : Int    = 0,
)

@Serializable
data class SupabaseBook(
    val id             : String,
    val title          : String,
    @SerialName("author_name") val authorName : String = "",
    @SerialName("cover_img")   val coverImg   : String = "",
    @SerialName("avg_rating")  val avgRating  : Float  = 0f,
    @SerialName("quote_count") val quoteCount : Int    = 0,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val auth      : FirebaseAuth,
    private val firestore : FirebaseFirestore,
    private val supabase  : SupabaseClient,
) : ViewModel() {

    private val _results       = MutableStateFlow<List<User>>(emptyList())
    val results = _results.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _suggestions   = MutableStateFlow<List<User>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _loading       = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _activeTab     = MutableStateFlow(0)
    val activeTab = _activeTab.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    fun setTab(tab: Int) { _activeTab.value = tab }

    private var searchJob: Job? = null

    // ── Arama girişi — 300ms debounce ────────────────────────────────────────
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            searchJob?.cancel()
            _results.value      = emptyList()
            _searchResults.value = emptyList()
            _loading.value      = false
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _loading.value = true
            delay(300)
            try {
                searchAll(q)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Ana arama — Firebase ve Supabase paralel çalışır ─────────────────────
    private suspend fun searchAll(q: String) {
        val qLower = q.lowercase()
        val qCap   = q.replaceFirstChar { it.uppercaseChar() }

        // Firebase (users + feed + serials) ve Supabase (authors + books) paralel
        val firebaseJob  = viewModelScope.async { searchFirebase(q, qLower, qCap) }
        val supabaseJob  = viewModelScope.async { searchSupabase(qLower) }

        val (fbResults, sbResults) = awaitAll(firebaseJob, supabaseJob)

        val combined = fbResults + sbResults
        _searchResults.value = combined
        _results.value = combined.filter { it.type == "user" }.map { r ->
            User(
                uid         = r.uid,
                displayName = r.title,
                username    = r.subtitle.removePrefix("@"),
                photoURL    = r.imageUrl,
                bio         = "",
            )
        }
    }

    // ── Firebase kısmı: users + feed + serials ────────────────────────────────
    private suspend fun searchFirebase(q: String, qLower: String, qCap: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // Kullanıcı — displayName + name + username prefix (3 varyant, case-sensitive Firestore)
        try {
            val seenIds = mutableSetOf<String>()
            for (prefix in listOf(q, qLower, qCap).distinct()) {
                val snap = firestore.collection("users")
                    .orderBy("displayName")
                    .startAt(prefix).endAt(prefix + "\uF8FF")
                    .limit(15).get().await()
                for (doc in snap.documents) {
                    if (seenIds.add(doc.id)) mapUser(doc)?.let { results += it }
                }
            }
            val uSnap = firestore.collection("users")
                .orderBy("username")
                .startAt(qLower).endAt(qLower + "\uF8FF")
                .limit(15).get().await()
            for (doc in uSnap.documents) {
                if (seenIds.add(doc.id)) mapUser(doc)?.let { results += it }
            }
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

        // Feed gönderi — son 50 postu çek, client-side filtrele
        try {
            val pSnap = firestore.collection("feed")
                .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await()
            results += pSnap.documents.mapNotNull { doc ->
                val d       = doc.data ?: return@mapNotNull null
                val text    = d["text"]      as? String ?: ""
                val qText   = d["quoteText"] as? String ?: ""
                val combined = "$text $qText".lowercase()
                if (!combined.contains(qLower)) return@mapNotNull null
                val display = text.ifBlank { qText }
                if (display.isBlank()) return@mapNotNull null
                val name = (d["displayName"] as? String)?.ifBlank { null }
                           ?: (d["name"]     as? String)?.ifBlank { null }
                           ?: (d["email"]    as? String)?.substringBefore("@") ?: ""
                SearchResult(
                    id       = doc.id,
                    type     = "post",
                    title    = display.take(80),
                    subtitle = name,
                    imageUrl = d["photoURL"] as? String ?: "",
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Serials — titleLower prefix araması
        try {
            val seenSerialIds = mutableSetOf<String>()
            var found = false
            for (prefix in listOf(qLower, q, qCap).distinct()) {
                val sSnap = firestore.collection("serials")
                    .orderBy("titleLower")
                    .startAt(prefix.lowercase()).endAt(prefix.lowercase() + "\uF8FF")
                    .limit(10).get().await()
                for (doc in sSnap.documents) {
                    if (!seenSerialIds.add(doc.id)) continue
                    val d     = doc.data ?: continue
                    val title = d["title"] as? String ?: continue
                    if (title.isBlank()) continue
                    found = true
                    results += SearchResult(
                        id       = doc.id,
                        type     = "serial",
                        title    = title,
                        subtitle = d["name"] as? String ?: d["authorName"] as? String ?: "",
                        imageUrl = d["coverImg"] as? String ?: "",
                    )
                }
            }
            // titleLower yoksa fallback
            if (!found && qLower.length >= 2) {
                val sSnap = firestore.collection("serials").limit(30).get().await()
                results += sSnap.documents.mapNotNull { doc ->
                    if (doc.id in seenSerialIds) return@mapNotNull null
                    val d     = doc.data ?: return@mapNotNull null
                    val title = d["title"] as? String ?: return@mapNotNull null
                    if (title.isBlank() || !title.lowercase().contains(qLower)) return@mapNotNull null
                    SearchResult(
                        id       = doc.id,
                        type     = "serial",
                        title    = title,
                        subtitle = d["name"] as? String ?: d["authorName"] as? String ?: "",
                        imageUrl = d["coverImg"] as? String ?: "",
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Feed'den legacy alıntı yazar/kitap isimleri
        try {
            val authorSnap  = firestore.collection("feed").whereEqualTo("quote.author", q).limit(10).get().await()
            val authorSnap2 = firestore.collection("feed").orderBy("authorName")
                .startAt(q).endAt(q + "\uF8FF").limit(10).get().await()
            val authorNames = (authorSnap.documents + authorSnap2.documents).distinctBy { it.id }
                .mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ((d["quote"] as? Map<*, *>)?.get("author") as? String
                        ?: d["authorName"] as? String)?.takeIf { it.isNotBlank() }
                }.distinct().take(5)
            results += authorNames.map { name ->
                SearchResult(id = name, type = "author", title = name, subtitle = "Yazar Alıntıları")
            }
        } catch (_: Exception) {}

        try {
            val bookSnap  = firestore.collection("feed").whereEqualTo("quote.book", q).limit(10).get().await()
            val bookSnap2 = firestore.collection("feed").orderBy("bookName")
                .startAt(q).endAt(q + "\uF8FF").limit(10).get().await()
            val bookNames = (bookSnap.documents + bookSnap2.documents).distinctBy { it.id }
                .mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ((d["quote"] as? Map<*, *>)?.get("book") as? String
                        ?: d["bookName"] as? String)?.takeIf { it.isNotBlank() }
                }.distinct().take(5)
            results += bookNames.map { name ->
                SearchResult(id = name, type = "book_quote", title = name, subtitle = "Kitap Alıntıları")
            }
        } catch (_: Exception) {}

        return results
    }

    // ── Supabase kısmı: authors + library_books ───────────────────────────────
    // Önceki: 6 Firestore sorgusu (3 prefix × 2 koleksiyon)
    // Şimdi:  2 Supabase sorgusu — ilike '%q%' server-side, case-insensitive
    private suspend fun searchSupabase(qLower: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // Kütüphane yazarları
        try {
            val authors = supabase.postgrest["authors"]
                .select {
                    filter { ilike("name_lower", "%$qLower%") }
                    limit(10)
                }
                .decodeList<SupabaseAuthor>()

            results += authors.map { a ->
                SearchResult(
                    id       = a.id,
                    type     = "library_author",
                    title    = a.name,
                    subtitle = a.nationality,
                    imageUrl = a.photoUrl,
                    extra    = buildString {
                        if (a.bookCount  > 0) append("${a.bookCount} kitap")
                        if (a.quoteCount > 0) append(" · ${a.quoteCount} alıntı")
                    }.trim(' ', '·'),
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Kütüphane kitapları
        try {
            val books = supabase.postgrest["library_books"]
                .select {
                    filter { ilike("title_lower", "%$qLower%") }
                    limit(10)
                }
                .decodeList<SupabaseBook>()

            results += books.map { b ->
                SearchResult(
                    id       = b.id,
                    type     = "library_book",
                    title    = b.title,
                    subtitle = b.authorName,
                    imageUrl = b.coverImg,
                    extra    = buildString {
                        if (b.avgRating  > 0f) append("★ ${"%.1f".format(b.avgRating)}")
                        if (b.quoteCount > 0)  append(" · ${b.quoteCount} alıntı")
                    }.trim(' ', '·'),
                )
            }
        } catch (e: Exception) { e.printStackTrace() }

        return results
    }

    // ── Yardımcı: Firestore doc → SearchResult (user tipi) ───────────────────
    private fun mapUser(doc: com.google.firebase.firestore.DocumentSnapshot): SearchResult? {
        val d     = doc.data ?: return null
        val name  = (d["displayName"] as? String)?.ifBlank { null }
                    ?: (d["name"]      as? String)?.ifBlank { null }
                    ?: (d["email"]     as? String)?.substringBefore("@")?.ifBlank { null }
                    ?: (d["username"]  as? String)?.ifBlank { null }
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

    // ── Takip önerileri ───────────────────────────────────────────────────────
    private val _followingUids = mutableSetOf<String>()

    fun loadSuggestions() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val followSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", uid)
                    .limit(500).get().await()
                val followedUids = followSnap.documents
                    .mapNotNull { it.getString("targetUid") ?: it.getString("followingUid") }
                    .toMutableSet()
                followedUids.add(uid)
                _followingUids.clear()
                _followingUids.addAll(followedUids)

                val usersSnap = firestore.collection("users")
                    .orderBy("followersCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().await()
                _suggestions.value = usersSnap.documents
                    .mapNotNull { it.toUser() }
                    .filter { it.uid !in _followingUids }
                    .take(30)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Takip et / bırak ─────────────────────────────────────────────────────
    fun toggleFollow(targetUid: String) {
        val isFollowing = targetUid in _followingUids
        if (isFollowing) _followingUids.remove(targetUid) else _followingUids.add(targetUid)
        if (!isFollowing) _suggestions.value = _suggestions.value.filter { it.uid != targetUid }

        viewModelScope.launch {
            try {
                val ref = firestore.collection("follows").document("${uid}_$targetUid")
                if (isFollowing) {
                    ref.delete().await()
                    firestore.collection("users").document(uid)
                        .update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1))
                    firestore.collection("users").document(targetUid)
                        .update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1))
                } else {
                    val myDoc = try { firestore.collection("users").document(uid).get().await() }
                                catch (_: Exception) { null }
                    ref.set(mapOf(
                        "fromUid"   to uid,
                        "fromName"  to (myDoc?.getString("displayName") ?: myDoc?.getString("name") ?: ""),
                        "fromPhoto" to (myDoc?.getString("photoURL") ?: ""),
                        "targetUid" to targetUid,
                        "ts"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )).await()
                    firestore.collection("users").document(uid)
                        .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1))
                    firestore.collection("users").document(targetUid)
                        .update("followersCount", com.google.firebase.firestore.FieldValue.increment(1))
                }
            } catch (e: Exception) {
                if (isFollowing) _followingUids.add(targetUid) else _followingUids.remove(targetUid)
                e.printStackTrace()
            }
        }
    }

    fun isFollowing(targetUid: String): Boolean = targetUid in _followingUids

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
        val d = data ?: return null
        val rawName = (d["displayName"] as? String)?.ifBlank { null }
                      ?: (d["name"]     as? String)?.ifBlank { null }
                      ?: (d["email"]    as? String)?.substringBefore("@")?.ifBlank { null }
                      ?: (d["username"] as? String)?.ifBlank { null }
                      ?: id.take(8)
        return User(
            uid            = id,
            displayName    = rawName,
            username       = d["username"]      as? String ?: "",
            photoURL       = d["photoURL"]      as? String ?: "",
            bio            = d["bio"]           as? String ?: "",
            followersCount = (d["followersCount"] as? Long)?.toInt() ?: 0,
        )
    }
}
