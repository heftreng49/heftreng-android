package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val ADMIN_EMAIL = "siirgibi49@gmail.com"

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _isAdmin      = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    private val _users        = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _pendingPosts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingPosts = _pendingPosts.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _pushResult   = MutableStateFlow("")
    val pushResult = _pushResult.asStateFlow()

    private val _stats        = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats = _stats.asStateFlow()

    fun checkAdmin() {
        _isAdmin.value = auth.currentUser?.email == ADMIN_EMAIL
    }

    // ── Kullanıcıları listele ──────────────────────────────────────────────────
    fun loadUsers() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("users").limit(100).get().await()
                _users.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    User(
                        uid         = doc.id,
                        displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email       = d["email"] as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                        banned      = d["banned"] as? Boolean ?: false,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Ban / Unban ────────────────────────────────────────────────────────────
    fun toggleBan(uid: String, ban: Boolean) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update("banned", ban).await()
                _users.value = _users.value.map { if (it.uid == uid) it.copy(banned = ban) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Push bildirimi — Cloud Function sendPush çağırır ──────────────────────
    fun sendPush(title: String, body: String, url: String = "", targetUid: String = "") {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                _pushResult.value = "Gönderiliyor…"
                val functions = com.google.firebase.functions.FirebaseFunctions
                    .getInstance("europe-west1")
                val data = hashMapOf(
                    "targetUid" to targetUid.ifBlank { auth.currentUser?.uid ?: "" },
                    "title"     to title,
                    "body"      to body,
                    "url"       to url.ifBlank { "https://heft-reng.blogspot.com/" },
                    "type"      to "default",
                )
                functions.getHttpsCallable("sendPush")
                    .call(data)
                    .await()
                _pushResult.value = "✓ Push gönderildi"
            } catch (e: Exception) {
                _pushResult.value = "✗ Hata: ${e.message}"
            }
        }
    }

    // ── Sistem bildirimi — XML: sendAdminNotifFromPanel ────────────────────────
    // notifications koleksiyonuna yazar (tüm kullanıcılara)
    fun sendSystemNotif(title: String, body: String, type: String = "sys") {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                val icoMap = mapOf("sys" to "campaign", "cmt" to "chat_bubble", "like" to "favorite", "bm" to "bookmark")
                firestore.collection("notifications").add(
                    mapOf(
                        "type"   to type,
                        "title"  to title,
                        "sub"    to body,
                        "ico"    to (icoMap[type] ?: "campaign"),
                        "read"   to false,
                        "ts"     to FieldValue.serverTimestamp(),
                        "sentBy" to (auth.currentUser?.email ?: "admin"),
                    )
                ).await()
                _pushResult.value = "✓ Bildirim gönderildi"
            } catch (e: Exception) {
                _pushResult.value = "✗ Hata: ${e.message}"
            }
        }
    }

    // ── Bekleyen gönderiler — XML: pendingPosts ────────────────────────────────
    fun loadPendingPosts() {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("pendingPosts").limit(50).get().await()
                _pendingPosts.value = snap.documents.mapNotNull { doc ->
                    (doc.data ?: return@mapNotNull null).toMutableMap().also { it["id"] = doc.id }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun approvePost(postId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("pendingPosts").document(postId).get().await()
                val d   = doc.data ?: return@launch
                firestore.collection("feed").add(d).await()
                firestore.collection("pendingPosts").document(postId).delete().await()
                _pendingPosts.value = _pendingPosts.value.filter { it["id"] != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun rejectPost(postId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).delete().await()
                _pendingPosts.value = _pendingPosts.value.filter { it["id"] != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }


    // ── Kullanıcı adı / fotoğraf düzelt ──────────────────────────────────────
    private val _editResult = MutableStateFlow("")
    val editResult = _editResult.asStateFlow()

    fun updateUserProfile(uid: String, displayName: String, photoURL: String) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>()
                if (displayName.isNotBlank()) {
                    updates["displayName"] = displayName
                    updates["name"]        = displayName
                }
                if (photoURL.isNotBlank()) updates["photoURL"] = photoURL
                if (updates.isEmpty()) { _editResult.value = "✗ Değişiklik yok"; return@launch }
                firestore.collection("users").document(uid).update(updates).await()
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(
                        displayName = displayName.ifBlank { it.displayName },
                        photoURL    = photoURL.ifBlank    { it.photoURL },
                    ) else it
                }
                _editResult.value = "✓ Profil güncellendi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Kullanıcıyı tamamen sil ───────────────────────────────────────────────
    fun deleteUser(uid: String) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                // Firestore dökümanını sil
                firestore.collection("users").document(uid).delete().await()
                // Kullanıcının gönderilerini sil
                val posts = firestore.collection("feed")
                    .whereEqualTo("uid", uid).get().await()
                posts.documents.forEach { it.reference.delete() }
                _users.value = _users.value.filter { it.uid != uid }
                _editResult.value = "✓ Kullanıcı silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Feed gönderisi sil ────────────────────────────────────────────────────
    private val _feedPosts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val feedPosts = _feedPosts.asStateFlow()

    fun loadFeedPosts(query: String = "") {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()
                _feedPosts.value = snap.documents.mapNotNull { doc ->
                    (doc.data ?: return@mapNotNull null)
                        .toMutableMap().also { it["id"] = doc.id }
                }.filter {
                    query.isBlank() ||
                    (it["text"] as? String ?: "").contains(query, ignoreCase = true) ||
                    (it["uid"]  as? String ?: "").contains(query, ignoreCase = true)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deletePost(postId: String) {
        if (postId.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId).delete().await()
                _feedPosts.value = _feedPosts.value.filter { it["id"] != postId }
                _editResult.value = "✓ Gönderi silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Yorum sil ─────────────────────────────────────────────────────────────
    fun deleteComment(postId: String, commentId: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId)
                    .collection("comments").document(commentId).delete().await()
                firestore.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
                _editResult.value = "✓ Yorum silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    fun clearEditResult() { _editResult.value = "" }

    // ── İstatistikler ─────────────────────────────────────────────────────────
    fun loadStats() {
        viewModelScope.launch {
            try {
                val users   = firestore.collection("users").get().await().size()
                val posts   = firestore.collection("feed").limit(500).get().await().size()
                val serials = firestore.collection("serials").get().await().size()
                val books   = firestore.collection("books").get().await().size()
                val pending = firestore.collection("pendingPosts").get().await().size()
                _stats.value = mapOf(
                    "users"   to users,
                    "posts"   to posts,
                    "serials" to serials,
                    "books"   to books,
                    "pending" to pending,
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
