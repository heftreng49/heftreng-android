package com.heftreng.app.viewmodel

// ═══════════════════════════════════════════════════════════════
//  SocialViewModel — Takipçi / Takip / Beğenen listeleri
//
//  Firestore yapısı:
//  feedLikes/{postId_uid}    → { uid, feedId, name, photoURL, ts }
//  commentLikes/{cmtId_uid}  → { uid, cmtId, name, photoURL, ts }
//  serialLikes/{sid_uid}     → { uid, serialId, name, photoURL, ts }
//  follows/{fromUid_toUid}   → { fromUid, fromName, fromPhoto,
//                                targetUid, targetName, targetPhoto, ts }
//
//  FATURA OPTİMİZASYONLARI (v2):
//  - Tüm listeler sayfalama (cursor-based pagination) kullanır
//  - FOLLOW_PAGE = 20 (30'dan düşürüldü — fatura dostu)
//  - enrichFromUsers() — zaten cache'de olan uid'leri Firestore'a sormaz
//  - Session cache: _userEnrichCache → uid → (name, photoURL)
//  - loadPostLikers / loadCommentLikers / loadSerialLikers → limit(50)
// ═══════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.FollowEntry
import com.heftreng.app.data.model.LikeEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _followers = MutableStateFlow<List<FollowEntry>>(emptyList())
    val followers = _followers.asStateFlow()

    private val _following = MutableStateFlow<List<FollowEntry>>(emptyList())
    val following = _following.asStateFlow()

    private val _likers    = MutableStateFlow<List<LikeEntry>>(emptyList())
    val likers = _likers.asStateFlow()

    private val _loading        = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _followersLoading = MutableStateFlow(false)
    val followersLoading = _followersLoading.asStateFlow()

    private val _followingLoading = MutableStateFlow(false)
    val followingLoading = _followingLoading.asStateFlow()

    private val _hasMoreFollowers = MutableStateFlow(false)
    val hasMoreFollowers = _hasMoreFollowers.asStateFlow()

    private val _hasMoreFollowing = MutableStateFlow(false)
    val hasMoreFollowing = _hasMoreFollowing.asStateFlow()

    private var lastFollowerDoc : com.google.firebase.firestore.DocumentSnapshot? = null
    private var lastFollowingDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private var followersTargetUid: String = ""
    private var followingTargetUid: String = ""

    // FATURA OPTİMİZASYONU: Sayfa boyutu 20 (30'dan düşürüldü)
    // Kullanıcı "Daha Fazla" tuşuna basmadıkça yeni okuma yapılmaz
    private val FOLLOW_PAGE = 20

    // Session-level cache — aynı kullanıcı için tekrar Firestore'a gitme
    private val _userEnrichCache = mutableMapOf<String, Pair<String, String>>() // uid → (name, photoURL)

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Gönderi beğenenleri ──────────────────────────────────────────────────
    fun loadPostLikers(postId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val snap = firestore.collection("feedLikes")
                    .whereEqualTo("feedId", postId)
                    .limit(50)
                    .get().await()

                val results = snap.documents.mapNotNull { doc ->
                    mapToLikeEntry(doc.data ?: return@mapNotNull null)
                }.filter { it.uid.isNotBlank() }

                if (results.isNotEmpty()) {
                    _likers.value = enrichFromUsers(results)
                    return@launch
                }

                // Fallback: belge ID'si "{postId}_{uid}" formatından
                val prefixSnap = firestore.collection("feedLikes")
                    .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                    .startAt("${postId}_")
                    .endAt("${postId}_\uF8FF")
                    .limit(50)
                    .get().await()

                val fromDocs = prefixSnap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val likeUid = d["uid"] as? String
                        ?: doc.id.substringAfter("${postId}_").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val name    = (d["name"] as? String)?.takeIf { it.isNotBlank() }
                                ?: (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
                                ?: ""
                    val photo   = d["photoURL"] as? String ?: ""
                    LikeEntry(uid = likeUid, name = name, photoURL = photo,
                        ts = d["ts"] as? com.google.firebase.Timestamp)
                }.filter { it.uid.isNotBlank() }

                _likers.value = enrichFromUsers(fromDocs)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Yorum beğenenleri ────────────────────────────────────────────────────
    fun loadCommentLikers(commentId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val snap = firestore.collection("commentLikes")
                    .whereEqualTo("cmtId", commentId)
                    .limit(50)
                    .get().await()

                val results = snap.documents.mapNotNull { doc ->
                    mapToLikeEntry(doc.data ?: return@mapNotNull null)
                }.filter { it.uid.isNotBlank() }

                val commentResults = if (results.isNotEmpty()) results else {
                    firestore.collection("commentLikes")
                        .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                        .startAt("${commentId}_")
                        .endAt("${commentId}_\uF8FF")
                        .limit(50).get().await()
                        .documents.mapNotNull { doc ->
                            mapToLikeEntry(doc.data ?: return@mapNotNull null)
                        }.filter { it.uid.isNotBlank() }
                }
                _likers.value = enrichFromUsers(commentResults)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Seri beğenenleri ─────────────────────────────────────────────────────
    fun loadSerialLikers(serialId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val snap = firestore.collection("serialLikes")
                    .whereEqualTo("serialId", serialId)
                    .limit(50)
                    .get().await()

                val serialResults = snap.documents.mapNotNull { doc ->
                    mapToLikeEntry(doc.data ?: return@mapNotNull null)
                }.filter { it.uid.isNotBlank() }
                _likers.value = enrichFromUsers(serialResults)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Takipçiler (sayfalama ile) ───────────────────────────────────────────
    fun loadFollowers(targetUid: String) {
        // Aynı kullanıcı tekrar istenirse sıfırla
        if (followersTargetUid == targetUid && _followers.value.isNotEmpty()) return
        followersTargetUid = targetUid
        lastFollowerDoc    = null
        viewModelScope.launch {
            _followersLoading.value = true
            _followers.value = emptyList()
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("targetUid", targetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(FOLLOW_PAGE.toLong())
                    .get().await()

                if (snap.documents.isNotEmpty()) lastFollowerDoc = snap.documents.last()
                _hasMoreFollowers.value = snap.documents.size >= FOLLOW_PAGE

                val rawFollowers = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FollowEntry(
                        uid      = d["fromUid"]  as? String ?: "",
                        name     = (d["fromName"] as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["fromDisplayName"] as? String ?: "",
                        photoURL = d["fromPhoto"] as? String
                                 ?: d["fromPhotoURL"] as? String ?: "",
                        ts       = d["ts"] as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }

                _followers.value = rawFollowers

                // Sadece isim/foto eksikse enrich yap — gereksiz okuma önlenir
                val needsEnrich = rawFollowers.any { it.name.isBlank() || it.photoURL.isBlank() }
                if (needsEnrich) {
                    val enriched = enrichFollowFromUsers(rawFollowers)
                    if (enriched.isNotEmpty()) _followers.value = enriched
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadFollowers hata: ${e.message}")
            } finally {
                _followersLoading.value = false
            }
        }
    }

    fun loadMoreFollowers() {
        val last = lastFollowerDoc ?: return
        if (_followersLoading.value || !_hasMoreFollowers.value) return
        viewModelScope.launch {
            _followersLoading.value = true
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("targetUid", followersTargetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .startAfter(last)
                    .limit(FOLLOW_PAGE.toLong())
                    .get().await()

                if (snap.documents.isNotEmpty()) lastFollowerDoc = snap.documents.last()
                _hasMoreFollowers.value = snap.documents.size >= FOLLOW_PAGE

                val more = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FollowEntry(
                        uid      = d["fromUid"]  as? String ?: "",
                        name     = (d["fromName"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                        photoURL = d["fromPhoto"] as? String ?: "",
                        ts       = d["ts"] as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }

                // Yeni sayfa için de enrich kontrolü
                val needsEnrich = more.any { it.name.isBlank() || it.photoURL.isBlank() }
                val enriched = if (needsEnrich) enrichFollowFromUsers(more) else more
                _followers.value = _followers.value + enriched

            } catch (e: Exception) { e.printStackTrace() }
            finally { _followersLoading.value = false }
        }
    }

    // ── Takip edilenler (sayfalama ile) ──────────────────────────────────────
    fun loadFollowing(targetUid: String) {
        if (followingTargetUid == targetUid && _following.value.isNotEmpty()) return
        followingTargetUid = targetUid
        lastFollowingDoc   = null
        viewModelScope.launch {
            _followingLoading.value = true
            _following.value = emptyList()
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("fromUid", targetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(FOLLOW_PAGE.toLong())
                    .get().await()

                if (snap.documents.isNotEmpty()) lastFollowingDoc = snap.documents.last()
                _hasMoreFollowing.value = snap.documents.size >= FOLLOW_PAGE

                val rawFollowing = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FollowEntry(
                        uid      = d["targetUid"]  as? String ?: "",
                        name     = (d["targetName"] as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["targetDisplayName"] as? String ?: "",
                        photoURL = d["targetPhoto"] as? String
                                 ?: d["targetPhotoURL"] as? String ?: "",
                        ts       = d["ts"] as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }

                _following.value = rawFollowing

                val needsEnrich = rawFollowing.any { it.name.isBlank() || it.photoURL.isBlank() }
                if (needsEnrich) {
                    val enriched = enrichFollowFromUsers(rawFollowing)
                    if (enriched.isNotEmpty()) _following.value = enriched
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadFollowing hata: ${e.message}")
            } finally {
                _followingLoading.value = false
            }
        }
    }

    fun loadMoreFollowing() {
        val last = lastFollowingDoc ?: return
        if (_followingLoading.value || !_hasMoreFollowing.value) return
        viewModelScope.launch {
            _followingLoading.value = true
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("fromUid", followingTargetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .startAfter(last)
                    .limit(FOLLOW_PAGE.toLong())
                    .get().await()

                if (snap.documents.isNotEmpty()) lastFollowingDoc = snap.documents.last()
                _hasMoreFollowing.value = snap.documents.size >= FOLLOW_PAGE

                val more = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FollowEntry(
                        uid      = d["targetUid"]  as? String ?: "",
                        name     = (d["targetName"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                        photoURL = d["targetPhoto"] as? String ?: "",
                        ts       = d["ts"] as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }

                val needsEnrich = more.any { it.name.isBlank() || it.photoURL.isBlank() }
                val enriched = if (needsEnrich) enrichFollowFromUsers(more) else more
                _following.value = _following.value + enriched

            } catch (e: Exception) { e.printStackTrace() }
            finally { _followingLoading.value = false }
        }
    }

    // ── Temizle ──────────────────────────────────────────────────────────────
    fun clearLikers()    { _likers.value    = emptyList() }
    fun clearFollowers() {
        _followers.value = emptyList()
        followersTargetUid = ""
        lastFollowerDoc = null
        _hasMoreFollowers.value = false
    }
    fun clearFollowing() {
        _following.value = emptyList()
        followingTargetUid = ""
        lastFollowingDoc = null
        _hasMoreFollowing.value = false
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────
    private fun mapToLikeEntry(d: Map<String, Any?>): LikeEntry {
        return LikeEntry(
            uid      = d["uid"]         as? String ?: "",
            name     = (d["name"]       as? String)?.takeIf { it.isNotBlank() }
                     ?: (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
                     ?: "",
            photoURL = d["photoURL"]    as? String ?: "",
            ts       = d["ts"]          as? com.google.firebase.Timestamp,
        )
    }

    // ── users koleksiyonundan güncel isim/fotoğraf ile zenginleştir ──────────
    // FATURA OPTİMİZASYONU:
    //   - Cache'de olan uid'ler için Firestore get() yapılmaz
    //   - whereIn → 10'lu chunk'lar (Firestore limiti)
    //   - Enrich sadece name.isBlank() || photoURL.isBlank() durumunda çalışır
    private suspend fun enrichFromUsers(entries: List<LikeEntry>): List<LikeEntry> {
        if (entries.isEmpty()) return entries
        val enriched = entries.toMutableList()
        val missing = entries.map { it.uid }
            .filter { it.isNotBlank() && it !in _userEnrichCache }
            .distinct()

        if (missing.isEmpty()) {
            // Tüm uid'ler cache'de — Firestore'a gitme
            enriched.forEachIndexed { idx, entry ->
                val cached = _userEnrichCache[entry.uid] ?: return@forEachIndexed
                enriched[idx] = entry.copy(
                    name     = cached.first.takeIf { it.isNotBlank() } ?: entry.name,
                    photoURL = cached.second.takeIf { it.isNotBlank() } ?: entry.photoURL,
                )
            }
            return enriched
        }

        missing.chunked(10).forEach { chunk ->
            try {
                val userSnap = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                userSnap.documents.forEach { userDoc ->
                    val uData = userDoc.data ?: return@forEach
                    _userEnrichCache[userDoc.id] = Pair(
                        (uData["displayName"] as? String)?.takeIf { it.isNotBlank() }
                            ?: (uData["name"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                        (uData["photoURL"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                    )
                }
            } catch (_: Exception) {}
        }

        enriched.forEachIndexed { idx, entry ->
            val cached = _userEnrichCache[entry.uid] ?: return@forEachIndexed
            if (cached.first.isNotBlank() || cached.second.isNotBlank()) {
                enriched[idx] = entry.copy(
                    name     = cached.first.takeIf { it.isNotBlank() } ?: entry.name,
                    photoURL = cached.second.takeIf { it.isNotBlank() } ?: entry.photoURL,
                )
            }
        }
        return enriched
    }

    private suspend fun enrichFollowFromUsers(entries: List<FollowEntry>): List<FollowEntry> {
        if (entries.isEmpty()) return entries
        val enriched = entries.toMutableList()
        val missing = entries.map { it.uid }
            .filter { it.isNotBlank() && it !in _userEnrichCache }
            .distinct()

        if (missing.isEmpty()) {
            enriched.forEachIndexed { idx, entry ->
                val cached = _userEnrichCache[entry.uid] ?: return@forEachIndexed
                enriched[idx] = entry.copy(
                    name     = cached.first.takeIf { it.isNotBlank() } ?: entry.name,
                    photoURL = cached.second.takeIf { it.isNotBlank() } ?: entry.photoURL,
                )
            }
            return enriched
        }

        missing.chunked(10).forEach { chunk ->
            try {
                val userSnap = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                userSnap.documents.forEach { userDoc ->
                    val uData = userDoc.data ?: return@forEach
                    _userEnrichCache[userDoc.id] = Pair(
                        (uData["displayName"] as? String)?.takeIf { it.isNotBlank() }
                            ?: (uData["name"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                        (uData["photoURL"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                    )
                }
            } catch (_: Exception) {}
        }

        enriched.forEachIndexed { idx, entry ->
            val cached = _userEnrichCache[entry.uid] ?: return@forEachIndexed
            enriched[idx] = entry.copy(
                name     = cached.first.takeIf { it.isNotBlank() } ?: entry.name,
                photoURL = cached.second.takeIf { it.isNotBlank() } ?: entry.photoURL,
            )
        }
        return enriched
    }
}
