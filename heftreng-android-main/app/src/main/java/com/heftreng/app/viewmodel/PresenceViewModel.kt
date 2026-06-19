package com.heftreng.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Firestore: presence/{uid} → { online, lastSeen, uid }
// Firestore: typing/{convId}_{uid} → { uid, convId, ts }

@HiltViewModel
class PresenceViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers = _onlineUsers.asStateFlow()

    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingUsers = _typingUsers.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // Kaç saniyedir heartbeat gelmemişse offline say (12 dk)
    private val ONLINE_TIMEOUT_MS = 720_000L
    // Heartbeat aralığı — 5 dakikada bir yaz (90s→300s: 3x daha az yazma)
    private val HEARTBEAT_INTERVAL_MS = 300_000L

    private var heartbeatJob     : kotlinx.coroutines.Job? = null
    private var presenceListener : com.google.firebase.firestore.ListenerRegistration? = null
    private var typingListener   : com.google.firebase.firestore.ListenerRegistration? = null

    // ── Online durumunu yaz + heartbeat başlat ────────────────────────────────
    fun goOnline() {
        if (uid.isEmpty()) return
        sendHeartbeat()
        // Her 30 sn'de bir lastSeen güncelle — uygulama açıkken online kalır
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        viewModelScope.launch {
            try {
                firestore.collection("presence").document(uid).set(
                    mapOf(
                        "online"     to true,
                        "lastSeen"   to FieldValue.serverTimestamp(),
                        "uid"        to uid,
                        "appVersion" to appVersion,
                        "platform"   to "android",
                    )
                ).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun goOffline() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("presence").document(uid).update(
                    mapOf("online" to false, "lastSeen" to FieldValue.serverTimestamp())
                ).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kullanıcı online mı? — lastSeen 2 dk'dan eskiyse offline say ──────────
    fun listenPresence(targetUid: String) {
        presenceListener?.remove()
        presenceListener = firestore.collection("presence").document(targetUid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) {
                    _onlineUsers.value = _onlineUsers.value - targetUid
                    return@addSnapshotListener
                }
                val onlineFlag = snap.getBoolean("online") ?: false
                val lastSeen   = snap.getTimestamp("lastSeen")?.toDate()?.time ?: 0L
                val ageMs      = System.currentTimeMillis() - lastSeen

                // online=true olsa bile lastSeen 2 dk'dan eskiyse offline say
                val isReallyOnline = onlineFlag && ageMs < ONLINE_TIMEOUT_MS

                _onlineUsers.value = if (isReallyOnline)
                    _onlineUsers.value + targetUid
                else
                    _onlineUsers.value - targetUid
                // Not: addSnapshotListener zaten sunucudan her değişimde tetiklenir;
                // ayrıca delayed .get() ile ekstra okuma yapmaya gerek yok.
            }
    }

    // ── Yazıyor göstergesi ────────────────────────────────────────────────────
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
        typingListener?.remove()
        typingListener = firestore.collection("typing").document("${convId}_$otherUid")
            .addSnapshotListener { snap, _ ->
                val exists = snap?.exists() == true
                if (!exists) {
                    _typingUsers.value = _typingUsers.value - otherUid
                    return@addSnapshotListener
                }
                val ts     = snap?.getTimestamp("ts")?.toDate()?.time ?: 0L
                val ageSec = (System.currentTimeMillis() - ts) / 1000
                if (ageSec < 8) {
                    _typingUsers.value = _typingUsers.value + otherUid
                    // Listener, typing dokümanı silinince/güncellenince zaten tetiklenir;
                    // ekstra delayed .get() gereksiz okuma yaratırdı.
                } else {
                    _typingUsers.value = _typingUsers.value - otherUid
                }
            }
    }

    fun isOnline(targetUid: String) = targetUid in _onlineUsers.value
    fun isTyping(targetUid: String) = targetUid in _typingUsers.value

    // ── Chat ekranından çıkınca çağır ────────────────────────────────────────
    fun stopListening() {
        presenceListener?.remove()
        presenceListener = null
        typingListener?.remove()
        typingListener = null
        _onlineUsers.value = emptySet()
        _typingUsers.value = emptySet()
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
        presenceListener?.remove()
        typingListener?.remove()
    }
}
