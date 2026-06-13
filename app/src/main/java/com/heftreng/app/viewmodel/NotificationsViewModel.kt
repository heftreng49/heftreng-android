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
import com.heftreng.app.data.model.FollowRow
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
    private val supabase : io.github.jan.supabase.SupabaseClient,
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
        // Foreground → listener başlat; background → listener kapat
        // Böylece uygulama arka planda olduğunda Firestore okuması yapmaz.
        val foregroundCb: () -> Unit = {
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) startListening(uid)
        }
        val backgroundCb: () -> Unit = {
            listenerReg?.remove()
            listenerReg = null
        }
        AppLifecycleObserver.addForegroundCallback(foregroundCb)
        AppLifecycleObserver.addBackgroundCallback(backgroundCb)

        // ViewModel temizlenince callback'leri kaldır — memory leak önleme
        // onCleared() içinde de remove yapılıyor; çift güvenlik.
        viewModelScope.launch {
            // onCleared'a kadar bekleyip temizle
            kotlinx.coroutines.awaitCancellation()
        }.invokeOnCompletion {
            AppLifecycleObserver.removeForegroundCallback(foregroundCb)
            AppLifecycleObserver.removeBackgroundCallback(backgroundCb)
        }

        // Şu an zaten foreground'daysa hemen başlat
        if (AppLifecycleObserver.isInForeground.value) {
            auth.currentUser?.uid?.let { if (it.isNotEmpty()) startListening(it) }
        }
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
                        sub       = (d["sub"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["body"] as? String ?: "",
                        // feedId → postId olarak map et
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

    // Dışarıdan manuel reload (pull-to-refresh gibi durumlar için)
    fun load() {
        val uid = auth.currentUser?.uid ?: return
        startListening(uid)
    }

    /** Ekrandan çıkınca çağır — listener kapatılır, FCM devralır. */
    fun stopListening() {
        listenerReg?.remove()
        listenerReg = null
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

    // ── Takip isteğini onayla ─────────────────────────────────
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

                // follows — Supabase'e yaz
                supabase.postgrest["follows"].upsert(
                    FollowRow(
                        id          = "${fromUid}_$myUid",
                        fromUid     = fromUid,
                        fromName    = fromName,
                        fromPhoto   = fromPhoto,
                        targetUid   = myUid,
                        targetName  = myName,
                        targetPhoto = myPhoto,
                    )
                )

                // Sayaçlar
                firestore.collection("users").document(myUid)
                    .update("followerCount", com.google.firebase.firestore.FieldValue.increment(1))
                firestore.collection("users").document(fromUid)
                    .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1))

                // Bekleyen isteği sil
                reqRef.delete().await()

                // Bildirimi okundu işaretle + status güncelle
                if (notifId.isNotBlank()) {
                    firestore.collection("userNotifs").document(myUid)
                        .collection("msgs").document(notifId)
                        .update("read", true, "status", "accepted").await()
                }

                // İsteği gönderene kabul bildirimi gönder
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

                // UI'da bildirimi kaldır (snapshot listener zaten güncelleyecek ama anında hissettir)
                markRead(notifId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Takip isteğini reddet ─────────────────────────────────
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
