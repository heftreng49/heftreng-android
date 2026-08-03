package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.heftreng.app.data.model.Notification
import com.heftreng.app.util.AppLifecycleObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase : SupabaseClient,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications = _notifications.asStateFlow()
    private val handledIds = mutableSetOf<String>()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount = _unreadCount.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private var lastServerFetchMs = 0L
    // FCM push ile bildirimler zaten anında geliyor.
    // Polling sadece uygulama açılışında bir kez yapılır (foreground callback).
    // 5 dk'da bir yoklama gereksiz Firestore okuması — kaldırıldı.

    init {
        val foregroundCb: () -> Unit = {
            // Uygulama foreground'a gelince sadece cache'i göster,
            // server'a sadece ilk açılışta veya liste boşsa git.
            auth.currentUser?.uid?.let { if (it.isNotEmpty()) load() }
        }
        val backgroundCb: () -> Unit = { /* listener yok */ }
        AppLifecycleObserver.addForegroundCallback(foregroundCb)
        AppLifecycleObserver.addBackgroundCallback(backgroundCb)
        viewModelScope.launch {
            kotlinx.coroutines.awaitCancellation()
        }.invokeOnCompletion {
            AppLifecycleObserver.removeForegroundCallback(foregroundCb)
            AppLifecycleObserver.removeBackgroundCallback(backgroundCb)
        }
        if (AppLifecycleObserver.isInForeground.value) {
            auth.currentUser?.uid?.let { if (it.isNotEmpty()) load() }
        }
    }

    fun load() {
        val uid = auth.currentUser?.uid ?: return
        if (_notifications.value.isEmpty()) _loading.value = true
        viewModelScope.launch {
            val query = firestore
                .collection("userNotifs").document(uid).collection("msgs")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50)

            // 1. Cache'den hemen göster — 0 Firestore okuması
            try {
                val cacheSnap = query.get(Source.CACHE).await()
                if (!cacheSnap.isEmpty) {
                    _notifications.value = parseNotifs(uid, cacheSnap.documents)
                    _unreadCount.value = _notifications.value.count { !it.read }
                    _loading.value = false
                }
            } catch (_: Exception) {}

            // 2. Server — sadece liste boşsa veya kullanıcı manuel yenileme yaptıysa
            // FCM push zaten Firestore cache'i günceller; bir sonraki cache okuma
            // güncel veriyi getirir. Periyodik polling yok.
            val now = System.currentTimeMillis()
            val shouldHitServer = _notifications.value.isEmpty()
                || (now - lastServerFetchMs) > 60 * 60 * 1000L // sadece 1 saatte bir fallback
            if (!shouldHitServer) { _loading.value = false; return@launch }

            try {
                val snap = withTimeoutOrNull(10_000L) {
                    query.get(Source.SERVER).await()
                }
                if (snap != null && !snap.isEmpty) {
                    lastServerFetchMs = now
                    _notifications.value = parseNotifs(uid, snap.documents)
                    _unreadCount.value = _notifications.value.count { !it.read }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun refresh() {
        lastServerFetchMs = 0L // throttle sifirla, server'dan cek
        _refreshing.value = true
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val query = firestore
                .collection("userNotifs").document(uid).collection("msgs")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50)
            try {
                val snap = withTimeoutOrNull(10_000L) {
                    query.get(Source.SERVER).await()
                }
                if (snap != null && !snap.isEmpty) {
                    lastServerFetchMs = System.currentTimeMillis()
                    _notifications.value = parseNotifs(uid, snap.documents)
                    _unreadCount.value = _notifications.value.count { !it.read }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _refreshing.value = false }
        }
    }

    private fun parseNotifs(
        uid: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): List<Notification> {
        val notifs = docs.mapNotNull { doc ->
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
                sub       = (d["sub"] as? String)?.takeIf { it.isNotBlank() }
                    ?: d["body"] as? String ?: "",
                postId    = (d["feedId"] as? String)?.takeIf { it.isNotBlank() }
                    ?: d["postId"] as? String,
                imageUrl  = d["imageUrl"] as? String ?: "",
                url       = d["url"]   as? String  ?: "",
                read      = d["read"]  as? Boolean ?: false,
                ts        = d["ts"]    as? Timestamp,
                status    = d["status"] as? String ?: "",
            )
        }
        return notifs.filter { n ->
            if (n.id in handledIds) return@filter false
            if (n.type == "follow_request" &&
                (n.status == "accepted" || n.status == "declined")
            ) return@filter false
            true
        }.sortedByDescending { it.ts?.seconds ?: 0L }
    }

    fun stopListening() { /* artık listener yok */ }

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
                unread.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { n -> batch.update(col.document(n.id), "read", true) }
                    batch.commit().await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun acceptFollowRequest(fromUid: String, notifId: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (notifId.isNotBlank()) {
            handledIds.add(notifId)
            _notifications.value = _notifications.value.filter { it.id != notifId }
            _unreadCount.value   = _notifications.value.count { !it.read }
        }
        viewModelScope.launch {
            try {
                val reqRef = firestore.collection("followRequests").document(myUid)
                    .collection("pending").document(fromUid)
                val reqDoc    = try { reqRef.get().await() } catch (_: Exception) { null }
                val fromName  = reqDoc?.getString("fromName")  ?: ""
                val fromPhoto = reqDoc?.getString("fromPhoto") ?: ""
                val myDoc   = firestore.collection("users").document(myUid).get().await()
                val myName  = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                val myPhoto = myDoc.getString("photoURL") ?: ""
                try {
                    supabase.postgrest["follows"].upsert(
                        com.heftreng.app.data.model.FollowRow(
                            id          = "${fromUid}_$myUid",
                            fromUid     = fromUid,
                            fromName    = fromName,
                            fromPhoto   = fromPhoto,
                            targetUid   = myUid,
                            targetName  = myName,
                            targetPhoto = myPhoto,
                        )
                    )
                } catch (_: Exception) {}
                try {
                    firestore.collection("users").document(myUid)
                        .update("followersCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                    firestore.collection("users").document(fromUid)
                        .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                } catch (_: Exception) {}
                try { reqRef.delete().await() } catch (_: Exception) {}
                if (notifId.isNotBlank()) {
                    try {
                        firestore.collection("userNotifs").document(myUid)
                            .collection("msgs").document(notifId)
                            .update("read", true, "status", "accepted").await()
                    } catch (_: Exception) {}
                }
                try {
                    firestore.collection("userNotifs").document(fromUid).collection("msgs").add(mapOf(
                        "fromUid"   to myUid, "fromName" to myName, "fromPhoto" to myPhoto,
                        "type"      to "follow_request_accepted", "feedId" to "", "postId" to "",
                        "title"     to "$myName takip istegini kabul etti", "sub" to "",
                        "ico"       to "person_add", "message" to "$myName takip istegini kabul etti",
                        "url"       to "", "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                    )).await()
                } catch (_: Exception) {}
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun declineFollowRequest(fromUid: String, notifId: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (notifId.isNotBlank()) {
            handledIds.add(notifId)
            _notifications.value = _notifications.value.filter { it.id != notifId }
            _unreadCount.value   = _notifications.value.count { !it.read }
        }
        viewModelScope.launch {
            try {
                try {
                    firestore.collection("followRequests").document(myUid)
                        .collection("pending").document(fromUid).delete().await()
                } catch (_: Exception) {}
                if (notifId.isNotBlank()) {
                    try {
                        firestore.collection("userNotifs").document(myUid)
                            .collection("msgs").document(notifId)
                            .update("read", true, "status", "declined").await()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
