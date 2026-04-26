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

    private val _isAdmin   = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    private val _users     = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _loading   = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _pushResult = MutableStateFlow("")
    val pushResult = _pushResult.asStateFlow()

    fun checkAdmin() {
        _isAdmin.value = auth.currentUser?.email == ADMIN_EMAIL
    }

    // ── Tüm kullanıcıları listele ──────────────────────────────────────────────
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
    // Firestore: users/{uid} → banned: true/false
    fun toggleBan(uid: String, ban: Boolean) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("banned", ban).await()
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(banned = ban) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Toplu push bildirimi ───────────────────────────────────────────────────
    // XML: _hfAdminSendPush → pushQueue koleksiyonuna yazar
    // OneSignal Edge Function bu kuyruğu okur ve gönderir
    fun sendPushToAll(title: String, body: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                _pushResult.value = "Gönderiliyor…"
                firestore.collection("pushQueue").add(
                    mapOf(
                        "targetUid" to "ALL",
                        "title"     to title,
                        "body"      to body,
                        "url"       to "https://heft-reng.blogspot.com/",
                        "ts"        to FieldValue.serverTimestamp(),
                    )
                ).await()
                _pushResult.value = "✓ Push kuyruğa eklendi"
            } catch (e: Exception) {
                _pushResult.value = "✗ Hata: ${e.message}"
            }
        }
    }

    // ── Bekleyen gönderiler (pendingPosts) ─────────────────────────────────────
    fun approvePost(postId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("pendingPosts").document(postId).get().await()
                val d   = doc.data ?: return@launch
                // feed'e taşı
                firestore.collection("feed").add(d).await()
                firestore.collection("pendingPosts").document(postId).delete().await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun rejectPost(postId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).delete().await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
