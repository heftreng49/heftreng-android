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

    // Kaç saniyedir heartbeat gelmemişse offline say (120 sn = 2 dk)
    private val ONLINE_TIMEOUT_MS = 120_000L
    // Heartbeat aralığı
    private val HEARTBEAT_INTERVAL_MS = 30_000L

    private var heartbeatJob: kotlinx.coroutines.Job? = null

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
        firestore.collection("presence").document(targetUid)
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

                // Eğer lastSeen yeni ama online=true ve timeout dolmuşsa
                // 2 dk sonra tekrar kontrol et
                if (isReallyOnline) {
                    val remaining = ONLINE_TIMEOUT_MS - ageMs
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(remaining + 1000)
                        val current = try {
                            firestore.collection("presence").document(targetUid).get().await()
                        } catch (e: Exception) { null }
                        val currentLastSeen = current?.getTimestamp("lastSeen")?.toDate()?.time ?: 0L
                        val currentAge      = System.currentTimeMillis() - currentLastSeen
                        val currentOnline   = current?.getBoolean("online") ?: false
                        if (!currentOnline || currentAge >= ONLINE_TIMEOUT_MS) {
                            _onlineUsers.value = _onlineUsers.value - targetUid
                        }
                    }
                }
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
        firestore.collection("typing").document("${convId}_$otherUid")
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
                    viewModelScope.launch {
                        kotlinx.coroutines.delay((8 - ageSec) * 1000)
                        val current = try {
                            firestore.collection("typing").document("${convId}_$otherUid").get().await()
                        } catch (e: Exception) { null }
                        val currentTs = current?.getTimestamp("ts")?.toDate()?.time ?: 0L
                        if (currentTs == ts) {
                            _typingUsers.value = _typingUsers.value - otherUid
                        }
                    }
                } else {
                    _typingUsers.value = _typingUsers.value - otherUid
                }
            }
    }

    fun isOnline(targetUid: String) = targetUid in _onlineUsers.value
    fun isTyping(targetUid: String) = targetUid in _typingUsers.value

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
    }
}
