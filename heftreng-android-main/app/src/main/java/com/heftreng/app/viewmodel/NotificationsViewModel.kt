package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    fun load() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                // Realtime için SnapshotListener kullan
                firestore.collection("userNotifs")
                    .document(uid).collection("msgs")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(60)
                    .addSnapshotListener { snap, err ->
                        if (err != null || snap == null) {
                            viewModelScope.launch { _loading.value = false }
                            return@addSnapshotListener
                        }
                        _notifications.value = snap.documents.mapNotNull { doc ->
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
                        viewModelScope.launch { _loading.value = false }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _loading.value = false
            }
        }
    }

    fun markRead(notifId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("userNotifs").document(uid)
                    .collection("msgs").document(notifId)
                    .update("read", true).await()
                _notifications.value = _notifications.value.map { n ->
                    if (n.id == notifId) n.copy(read = true) else n
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                val col = firestore.collection("userNotifs").document(uid).collection("msgs")
                _notifications.value.filter { !it.read }.forEach { n ->
                    col.document(n.id).update("read", true)
                }
                _notifications.value = _notifications.value.map { it.copy(read = true) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
