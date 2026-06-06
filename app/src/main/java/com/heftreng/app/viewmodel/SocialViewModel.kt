package com.heftreng.app.viewmodel

// ═══════════════════════════════════════════════════════════════
//  SocialViewModel — Takipçi / Takip / Beğenen listeleri
//
//  Site (heft-reng.blogspot.com) Firestore yapısı:
//  feedLikes/{postId_uid}    → { uid, feedId, name, photoURL, ts }
//  commentLikes/{cmtId_uid}  → { uid, cmtId, name, photoURL, ts }
//  serialLikes/{sid_uid}     → { uid, serialId, name, photoURL, ts }
//  follows/{fromUid_toUid}   → { fromUid, fromName, fromPhoto,
//                                targetUid, targetName, targetPhoto, ts }
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

    // Takipçi/takip için ayrı loading — ortak flag'in üst üste yazılmasını engeller
    private val _followersLoading = MutableStateFlow(false)
    val followersLoading = _followersLoading.asStateFlow()

    private val _followingLoading = MutableStateFlow(false)
    val followingLoading = _followingLoading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Gönderi beğenenleri ──────────────────────────────────────────────────
    // Site: feedLikes/{postId}_{uid} → { uid, feedId, name, photoURL, ts }
    // Sorgu: feedId == postId  (orderBy YOK — composite index gerektirmez)
    fun loadPostLikers(postId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                // Birincil sorgu: feedId alanı (site yazım formatı)
                val snap = firestore.collection("feedLikes")
                    .whereEqualTo("feedId", postId)
                    .limit(200)
                    .get().await()

                val results = snap.documents.mapNotNull { doc ->
                    mapToLikeEntry(doc.data ?: return@mapNotNull null)
                }.filter { it.uid.isNotBlank() }

                if (results.isNotEmpty()) {
                    _likers.value = enrichFromUsers(results)
                    return@launch
                }

                // Fallback: belge ID'si "{postId}_{uid}" formatından uid'leri çek
                // ve users koleksiyonundan isim/foto al
                val prefixSnap = firestore.collection("feedLikes")
                    .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                    .startAt("${postId}_")
                    .endAt("${postId}_\uF8FF")
                    .limit(200)
                    .get().await()

                if (prefixSnap.isEmpty) {
                    _likers.value = emptyList()
                    return@launch
                }

                val fromDocs = prefixSnap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    // feedId alanı belge ID'den türetilmiş olabilir
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

                // Her zaman users koleksiyonundan güncel isim/fotoğraf çek
                _likers.value = enrichFromUsers(fromDocs)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Yorum beğenenleri ────────────────────────────────────────────────────
    // Site: commentLikes/{cmtId}_{uid} → { uid, cmtId, name, photoURL, ts }
    fun loadCommentLikers(commentId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val snap = firestore.collection("commentLikes")
                    .whereEqualTo("cmtId", commentId)
                    .limit(200)
                    .get().await()

                val results = snap.documents.mapNotNull { doc ->
                    mapToLikeEntry(doc.data ?: return@mapNotNull null)
                }.filter { it.uid.isNotBlank() }

                val commentResults = if (results.isNotEmpty()) results else {
                    // Belge ID prefix fallback
                    firestore.collection("commentLikes")
                        .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                        .startAt("${commentId}_")
                        .endAt("${commentId}_\uF8FF")
                        .limit(200).get().await()
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
                    .limit(200)
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

    // ── Takipçiler ───────────────────────────────────────────────────────────
    // Site: follows/{fromUid_targetUid} → { fromUid, fromName, fromPhoto, targetUid, … }
    fun loadFollowers(targetUid: String) {
        viewModelScope.launch {
            _followersLoading.value = true
            _followers.value = emptyList()
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("targetUid", targetUid)
                    .limit(200)
                    .get().await()
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
                // follows belgesinden gelen isim/foto önce göster (anında)
                _followers.value = rawFollowers
                // Sadece isim/foto eksik olanlar için enrich yap
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

    // ── Takip edilenler ──────────────────────────────────────────────────────
    fun loadFollowing(targetUid: String) {
        viewModelScope.launch {
            _followingLoading.value = true
            _following.value = emptyList()
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("fromUid", targetUid)
                    .limit(200)
                    .get().await()
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
                // follows belgesinden gelen isim/foto önce göster (anında)
                _following.value = rawFollowing
                // Sadece isim/foto eksik olanlar için enrich yap
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

    // ── Temizle ──────────────────────────────────────────────────────────────
    fun clearLikers()    { _likers.value    = emptyList() }
    fun clearFollowers() { _followers.value = emptyList() }
    fun clearFollowing() { _following.value = emptyList() }

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
    // Beğenenler veya takipçi listelerinde eski/yanlış isim gösterilmemesi için
    // her zaman users/{uid} dokümanından displayName, name ve photoURL çekilir.
    private suspend fun enrichFromUsers(entries: List<LikeEntry>): List<LikeEntry> {
        if (entries.isEmpty()) return entries
        val enriched = entries.toMutableList()
        entries.map { it.uid }.filter { it.isNotBlank() }.chunked(10).forEach { chunk ->
            try {
                val userSnap = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                userSnap.documents.forEach { userDoc ->
                    val idx = enriched.indexOfFirst { it.uid == userDoc.id }
                    if (idx >= 0) {
                        val uData = userDoc.data ?: return@forEach
                        val freshName = (uData["displayName"] as? String)?.takeIf { it.isNotBlank() }
                            ?: (uData["name"] as? String)?.takeIf { it.isNotBlank() }
                            ?: enriched[idx].name
                        val freshPhoto = (uData["photoURL"] as? String)?.takeIf { it.isNotBlank() }
                            ?: enriched[idx].photoURL
                        enriched[idx] = enriched[idx].copy(name = freshName, photoURL = freshPhoto)
                    }
                }
            } catch (_: Exception) {}
        }
        return enriched
    }

    private suspend fun enrichFollowFromUsers(entries: List<FollowEntry>): List<FollowEntry> {
        if (entries.isEmpty()) return entries
        val enriched = entries.toMutableList()
        entries.map { it.uid }.filter { it.isNotBlank() }.chunked(10).forEach { chunk ->
            try {
                val userSnap = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                userSnap.documents.forEach { userDoc ->
                    val idx = enriched.indexOfFirst { it.uid == userDoc.id }
                    if (idx >= 0) {
                        val uData = userDoc.data ?: return@forEach
                        val freshName = (uData["displayName"] as? String)?.takeIf { it.isNotBlank() }
                            ?: (uData["name"] as? String)?.takeIf { it.isNotBlank() }
                            ?: enriched[idx].name
                        val freshPhoto = (uData["photoURL"] as? String)?.takeIf { it.isNotBlank() }
                            ?: enriched[idx].photoURL
                        enriched[idx] = enriched[idx].copy(name = freshName, photoURL = freshPhoto)
                    }
                }
            } catch (_: Exception) {}
        }
        return enriched
    }
}
