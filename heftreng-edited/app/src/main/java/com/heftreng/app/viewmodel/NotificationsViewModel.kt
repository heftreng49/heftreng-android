package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Tema: userNotifs/{uid}/msgs
// Alan yapısı: fromUid, fromName, fromPhoto, type, feedId, title, sub, ico, read, ts
// feedId → PostDetail navigasyonu için kullanılır
// type: like, cmt, follow, repost, bm (bookmark)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount = _unreadCount.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private var listenerReg: ListenerRegistration? = null

    // uid — auth state değişince güncellenir
    private val _uid = MutableStateFlow(auth.currentUser?.uid ?: "")

    init {
        // Auth state listener — login sonrası uid gelince load et
        auth.addAuthStateListener { firebaseAuth ->
            val newUid = firebaseAuth.currentUser?.uid ?: ""
            if (newUid.isNotEmpty() && newUid != _uid.value) {
                _uid.value = newUid
                startListening(newUid)
            } else if (newUid.isEmpty()) {
                _uid.value = ""
                listenerReg?.remove()
                listenerReg = null
                _notifications.value = emptyList()
                _unreadCount.value = 0
            }
        }
        // Zaten login durumdaysa hemen başlat
        auth.currentUser?.uid?.let { if (it.isNotEmpty()) startListening(it) }
    }

    private fun startListening(uid: String) {
        listenerReg?.remove()
        _loading.value = true
        listenerReg = firestore
            .collection("userNotifs")
            .document(uid)
            .collection("msgs")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(25)  // 60→25: listener başlangıçta daha az döküman çeker
            .addSnapshotListener { snap, err ->
                _loading.value = false
                if (err != null || snap == null) {
                    err?.printStackTrace()
                    return@addSnapshotListener
                }
                val notifs = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Notification(
                        id        = doc.id,
                        userId    = uid,
                        fromUid   = d["fromUid"]   as? String  ?: "",
                        fromName  = d["fromName"]  as? String  ?: "",
                        fromPhoto = d["fromPhoto"] as? String  ?: "",
                        type      = d["type"]      as? String  ?: "",
                        message   = (d["message"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["title"] as? String ?: "",
                        // feedId → postId olarak map et (tema feedId, Android postId kullanıyor)
                        postId    = (d["feedId"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["postId"] as? String,
                        url       = d["url"]   as? String  ?: "",
                        read      = d["read"]  as? Boolean ?: false,
                        ts        = d["ts"]    as? Timestamp,
                    )
                }
                _notifications.value = notifs.sortedByDescending { it.ts?.seconds ?: 0L }
                _unreadCount.value   = notifs.count { !it.read }
            }
    }

    // Dışarıdan manuel reload (pull-to-refresh gibi durumlar için)
    fun load() {
        val uid = auth.currentUser?.uid ?: return
        startListening(uid)
    }

    fun markRead(notifId: String) {
        _notifications.value = _notifications.value.map { n ->
            if (n.id == notifId) n.copy(read = true) else n
        }
        _unreadCount.value = _notifications.value.count { !it.read }
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("userNotifs").document(uid)
                    .collection("msgs").document(notifId)
                    .update("read", true).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun markAllRead() {
        val unread = _notifications.value.filter { !it.read }
        if (unread.isEmpty()) return
        _notifications.value = _notifications.value.map { it.copy(read = true) }
        _unreadCount.value = 0
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val col = firestore.collection("userNotifs").document(uid).collection("msgs")
                // Batch ile tek network round-trip'te hepsini güncelle
                unread.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { n -> batch.update(col.document(n.id), "read", true) }
                    batch.commit().await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
