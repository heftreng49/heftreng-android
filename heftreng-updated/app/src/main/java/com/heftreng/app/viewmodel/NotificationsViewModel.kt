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
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("userNotifs")
                    .document(uid)
                    .collection("msgs")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()

                _notifications.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Notification(
                        id        = doc.id,
                        userId    = uid,
                        fromUid   = d["fromUid"]   as? String  ?: "",
                        fromName  = d["fromName"]  as? String  ?: "",
                        fromPhoto = d["fromPhoto"] as? String  ?: "",
                        type      = d["type"]      as? String  ?: "",
                        message   = d["message"]   as? String  ?: "",
                        postId    = d["postId"]    as? String,
                        url       = d["url"]       as? String  ?: "",
                        read      = d["read"]      as? Boolean ?: false,
                        ts        = d["ts"]        as? Timestamp,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    /** Tek bildirimi okundu yap (ekranda göster, Firestore'a yaz) */
    fun markRead(notifId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("userNotifs")
                    .document(uid).collection("msgs")
                    .document(notifId)
                    .update("read", true).await()

                _notifications.value = _notifications.value.map { n ->
                    if (n.id == notifId) n.copy(read = true) else n
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /** Tüm okunmamışları okundu yap */
    fun markAllRead() {
        viewModelScope.launch {
            try {
                val unread = _notifications.value.filter { !it.read }
                val col    = firestore.collection("userNotifs").document(uid).collection("msgs")
                unread.forEach { n -> col.document(n.id).update("read", true) }
                _notifications.value = _notifications.value.map { it.copy(read = true) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
