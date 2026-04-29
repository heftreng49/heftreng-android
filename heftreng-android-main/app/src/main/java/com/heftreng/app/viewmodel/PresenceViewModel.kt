package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// XML: _startPresence / hfInitPresence
// Firestore: presence/{uid} → { online, lastSeen, uid }
// Firestore: typing/{convId} → { uid, convId, ts }

@HiltViewModel
class PresenceViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers = _onlineUsers.asStateFlow()

    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingUsers = _typingUsers.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Online durumunu yaz ────────────────────────────────────────────────────
    fun goOnline() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("presence").document(uid).set(
                    mapOf("online" to true, "lastSeen" to FieldValue.serverTimestamp(), "uid" to uid)
                ).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun goOffline() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("presence").document(uid).update(
                    mapOf("online" to false, "lastSeen" to FieldValue.serverTimestamp())
                ).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kullanıcı online mı? ──────────────────────────────────────────────────
    fun listenPresence(targetUid: String) {
        firestore.collection("presence").document(targetUid)
            .addSnapshotListener { snap, _ ->
                val online = snap?.getBoolean("online") ?: false
                _onlineUsers.value = if (online)
                    _onlineUsers.value + targetUid
                else
                    _onlineUsers.value - targetUid
            }
    }

    // ── Yazıyor göstergesi ────────────────────────────────────────────────────
    // Firestore: typing/{convId}_{uid}
    fun setTyping(convId: String, isTyping: Boolean) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val ref = firestore.collection("typing").document("${convId}_$uid")
                if (isTyping) {
                    ref.set(mapOf("uid" to uid, "convId" to convId, "ts" to FieldValue.serverTimestamp())).await()
                } else {
                    ref.delete().await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun listenTyping(convId: String, otherUid: String) {
        firestore.collection("typing").document("${convId}_$otherUid")
            .addSnapshotListener { snap, _ ->
                val exists = snap?.exists() == true
                _typingUsers.value = if (exists)
                    _typingUsers.value + otherUid
                else
                    _typingUsers.value - otherUid
            }
    }

    fun isOnline(targetUid: String) = targetUid in _onlineUsers.value
    fun isTyping(targetUid: String) = targetUid in _typingUsers.value
}
