package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Notification
import com.heftreng.app.util.AppLifecycleObserver
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

    // Sadece ilk yüklemede true — bildirimler zaten varken spinner gösterme
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    // Pull-to-refresh için ayrı state
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private var listenerReg: ListenerRegistration? = null
    private var listenerUid: String? = null   // hangi uid için listener kurulu

    init {
        val foregroundCb: () -> Unit = {
            val uid = auth.currentUser?.uid
            // Listener zaten bu uid için kuruluysa yeniden kurma
            if (!uid.isNullOrBlank() && listenerUid != uid) {
                startListening(uid)
            }
        }
        val backgroundCb: () -> Unit = {
            // Arka planda listener'ı kapat — FCM devralır
            listenerReg?.remove()
            listenerReg = null
            listenerUid = null
        }
        AppLifecycleObserver.addForegroundCallback(foregroundCb)
        AppLifecycleObserver.addBackgroundCallback(backgroundCb)

        viewModelScope.launch {
            kotlinx.coroutines.awaitCancellation()
        }.invokeOnCompletion {
            AppLifecycleObserver.removeForegroundCallback(foregroundCb)
            AppLifecycleObserver.removeBackgroundCallback(backgroundCb)
        }

        // Şu an foreground'daysa hemen başlat
        if (AppLifecycleObserver.isInForeground.value) {
            auth.currentUser?.uid?.let { if (it.isNotEmpty()) startListening(it) }
        }
    }

    private fun startListening(uid: String) {
        // Aynı uid için listener zaten kuruluysa tekrar kurma
        if (listenerUid == uid && listenerReg != null) return

        listenerReg?.remove()
        listenerUid = uid

        // Sadece liste tamamen boşsa spinner göster (cache varsa gösterme)
        if (_notifications.value.isEmpty()) {
            _loading.value = true
        }

        listenerReg = firestore
            .collection("userNotifs")
            .document(uid)
            .collection("msgs")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                _loading.value   = false
                _refreshing.value = false
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
                        sub       = (d["sub"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["body"] as? String ?: "",
                        postId    = (d["feedId"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["postId"] as? String,
                        imageUrl  = d["imageUrl"] as? String ?: "",
                        url       = d["url"]   as? String  ?: "",
                        read      = d["read"]  as? Boolean ?: false,
                        ts        = d["ts"]    as? Timestamp,
                    )
                }
                _notifications.value = notifs.sortedByDescending { it.ts?.seconds ?: 0L }
                _unreadCount.value   = notifs.count { !it.read }
            }
    }

    /** Pull-to-refresh: var olan listener'ı koru, sadece refreshing flag'ini aç */
    fun refresh() {
        val uid = auth.currentUser?.uid ?: return
        _refreshing.value = true
        // Listener zaten canlıysa Firestore otomatik günceller;
        // yoksa yeniden kur (uid değişmiş olabilir)
        if (listenerUid != uid || listenerReg == null) {
            startListening(uid)
        }
        // Firestore snapshot gelince refreshing = false yapılacak
        // Güvenlik için 3sn sonra kapat
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _refreshing.value = false
        }
    }

    /** Ekran ilk açıldığında çağır — listener kurulu değilse başlatır */
    fun load() {
        val uid = auth.currentUser?.uid ?: return
        startListening(uid)
    }

    /** Ekrandan çıkınca çağırma — listener canlı kalsın (badge için).
     *  Sadece ViewModel temizlenince veya arka plana geçince kapat. */
    fun stopListening() {
        // Artık ekrandan çıkınca kapatmıyoruz; AppLifecycleObserver halleder
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
        viewModelScope.launch {
            try {
                val reqRef = firestore.collection("followRequests").document(myUid)
                    .collection("pending").document(fromUid)
                if (!reqRef.get().await().exists()) return@launch

                val reqDoc    = reqRef.get().await()
                val fromName  = reqDoc.getString("fromName")  ?: ""
                val fromPhoto = reqDoc.getString("fromPhoto") ?: ""

                val myDoc   = firestore.collection("users").document(myUid).get().await()
                val myName  = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                val myPhoto = myDoc.getString("photoURL") ?: ""

                // ✅ Supabase follows — tek kaynak
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
                // Firestore: sadece sayaç güncelle
                try {
                    firestore.collection("users").document(myUid)
                        .update("followerCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                    firestore.collection("users").document(fromUid)
                        .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                } catch (_: Exception) {}

                reqRef.delete().await()

                if (notifId.isNotBlank()) {
                    firestore.collection("userNotifs").document(myUid)
                        .collection("msgs").document(notifId)
                        .update("read", true, "status", "accepted").await()
                }

                firestore.collection("userNotifs").document(fromUid).collection("msgs").add(mapOf(
                    "fromUid"   to myUid,
                    "fromName"  to myName,
                    "fromPhoto" to myPhoto,
                    "type"      to "follow_request_accepted",
                    "feedId"    to "",
                    "postId"    to "",
                    "title"     to "$myName takip isteğini kabul etti",
                    "sub"       to "",
                    "ico"       to "person_add",
                    "message"   to "$myName takip isteğini kabul etti",
                    "url"       to "",
                    "read"      to false,
                    "ts"        to com.google.firebase.Timestamp.now(),
                )).await()

                markRead(notifId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun declineFollowRequest(fromUid: String, notifId: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("followRequests").document(myUid)
                    .collection("pending").document(fromUid).delete().await()
                if (notifId.isNotBlank()) {
                    firestore.collection("userNotifs").document(myUid)
                        .collection("msgs").document(notifId)
                        .update("read", true, "status", "declined").await()
                }
                markRead(notifId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
