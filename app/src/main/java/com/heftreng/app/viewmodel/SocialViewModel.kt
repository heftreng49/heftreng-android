package com.heftreng.app.viewmodel

// ═══════════════════════════════════════════════════════════════
//  SocialViewModel — Takipçi/Takip/Beğenen listeleri
//
//  Tema (site) ile tam uyumlu Firestore yapısı:
//  - follows/{fromUid_targetUid}  → fromUid, fromName, fromPhoto,
//                                    targetUid, targetName, targetPhoto, ts
//  - feedLikes/{postId_uid}       → uid, feedId, name, photoURL, ts
//  - commentLikes/{cmtId_uid}     → uid, name, photoURL, ts
//  - serialLikes/{serialId_uid}   → uid, name, photoURL, ts
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

    private val _followers  = MutableStateFlow<List<FollowEntry>>(emptyList())
    val followers = _followers.asStateFlow()

    private val _following  = MutableStateFlow<List<FollowEntry>>(emptyList())
    val following = _following.asStateFlow()

    private val _likers     = MutableStateFlow<List<LikeEntry>>(emptyList())
    val likers = _likers.asStateFlow()

    private val _loading    = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Takipçi listesi ─────────────────────────────────
    fun loadFollowers(targetUid: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("targetUid", targetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(200).get().await()
                _followers.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FollowEntry(
                        uid      = d["fromUid"]   as? String ?: "",
                        name     = (d["fromName"] as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["fromDisplayName"] as? String ?: "",
                        photoURL = d["fromPhoto"] as? String ?: "",
                        ts       = d["ts"]        as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Takip edilenler listesi ──────────────────────────
    fun loadFollowing(targetUid: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("follows")
                    .whereEqualTo("fromUid", targetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(200).get().await()
                _following.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FollowEntry(
                        uid      = d["targetUid"]   as? String ?: "",
                        name     = (d["targetName"] as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["targetDisplayName"] as? String ?: "",
                        photoURL = d["targetPhoto"] as? String ?: "",
                        ts       = d["ts"]          as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Gönderi beğenenleri ──────────────────────────────
    // feedLikes: uid, feedId, name, photoURL, ts
    fun loadPostLikers(postId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("feedLikes")
                    .whereEqualTo("feedId", postId)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(200).get().await()
                // Geriye dönük uyum: feedId yoksa postId
                val snap2 = if (snap.isEmpty) {
                    firestore.collection("feedLikes")
                        .whereEqualTo("postId", postId)
                        .orderBy("ts", Query.Direction.DESCENDING)
                        .limit(200).get().await()
                } else snap
                _likers.value = snap2.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LikeEntry(
                        uid      = d["uid"]      as? String ?: "",
                        name     = (d["name"]    as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["displayName"] as? String ?: "",
                        photoURL = d["photoURL"] as? String ?: "",
                        ts       = d["ts"]       as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Yorum beğenenleri ────────────────────────────────
    // commentLikes: uid, cmtId, name, photoURL, ts
    fun loadCommentLikers(commentId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("commentLikes")
                    .whereEqualTo("uid", "") // uid boş olamaz — cmtId alanıyla sorgula
                // commentLikes belge ID'si "cmtId_uid" şeklinde — whereEqualTo("cmtId", commentId)
                val snap2 = firestore.collection("commentLikes")
                    .whereEqualTo("cmtId", commentId)
                    .limit(200).get().await()
                _likers.value = snap2.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LikeEntry(
                        uid      = d["uid"]      as? String ?: "",
                        name     = (d["name"]    as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["displayName"] as? String ?: "",
                        photoURL = d["photoURL"] as? String ?: "",
                        ts       = d["ts"]       as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Seri beğenenleri ─────────────────────────────────
    fun loadSerialLikers(serialId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("serialLikes")
                    .whereEqualTo("serialId", serialId)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(200).get().await()
                _likers.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LikeEntry(
                        uid      = d["uid"]      as? String ?: "",
                        name     = (d["name"]    as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["displayName"] as? String ?: "",
                        photoURL = d["photoURL"] as? String ?: "",
                        ts       = d["ts"]       as? com.google.firebase.Timestamp,
                    )
                }.filter { it.uid.isNotBlank() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun clearLikers() { _likers.value = emptyList() }
    fun clearFollowers() { _followers.value = emptyList() }
    fun clearFollowing() { _following.value = emptyList() }
}
