package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.heftreng.app.data.model.Conversation
import com.heftreng.app.data.model.Message
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Mesajlar Supabase'den Firestore'a taşındı.
// SupabaseClient inject edilmeye devam ediyor (AppModule bağımlılığı korunuyor)
// ancak artık kullanılmıyor.

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @Suppress("UNUSED_PARAMETER")
    private val supabase : SupabaseClient,   // bağımlılık korunuyor
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _messages  = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _otherUser = MutableStateFlow<User?>(null)
    val otherUser = _otherUser.asStateFlow()

    private val _loading   = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var convListener: ListenerRegistration? = null
    private var msgListener : ListenerRegistration? = null

    // ── Konuşma listesi — Firestore realtime ──────────────────────────────────
    fun listenConversations() {
        if (uid.isEmpty()) return
        convListener?.remove()
        convListener = firestore.collection("conversations")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                viewModelScope.launch {
                    val list = snap.documents.mapNotNull { doc ->
                        val d  = doc.data ?: return@mapNotNull null
                        val pa = d["participant_a"] as? String ?: return@mapNotNull null
                        val pb = d["participant_b"] as? String ?: return@mapNotNull null
                        val otherUid = if (pa == uid) pb else pa

                        val other = try {
                            val ud = firestore.collection("users").document(otherUid).get().await().data
                            if (ud != null) User(
                                uid         = otherUid,
                                displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "",
                                photoURL    = ud["photoURL"] as? String ?: "",
                            ) else null
                        } catch (_: Exception) { null }

                        Conversation(
                            id             = doc.id,
                            participantIds = listOf(pa, pb),
                            lastMessage    = d["last_msg"] as? String ?: "",
                            lastMessageAt  = d["last_at"]?.toString() ?: "",
                            otherUser      = other,
                            unreadCount    = (d["unread_$uid"] as? Long)?.toInt() ?: 0,
                        )
                    }.sortedByDescending { it.lastMessageAt }
                    _conversations.value = list
                }
            }
    }

    // ── Mesaj listesi — Firestore realtime ────────────────────────────────────
    fun listenMessages(convId: String) {
        msgListener?.remove()
        msgListener = firestore.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _messages.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Message(
                        id             = doc.id,
                        conversationId = convId,
                        senderId       = d["from_uid"]  as? String  ?: "",
                        text           = d["msg_text"]  as? String  ?: "",
                        createdAt      = d["ts"]?.toString()        ?: "",
                        read           = d["read"]      as? Boolean ?: false,
                    )
                }
                markRead(convId)
            }
        loadOtherUser(convId)
    }

    // ── Mesaj gönder ───────────────────────────────────────────────────────────
    fun sendMessage(convId: String, toUid: String, text: String) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val convRef = firestore.collection("conversations").document(convId)
                convRef.collection("messages").add(mapOf(
                    "conv_id"  to convId,
                    "from_uid" to uid,
                    "to_uid"   to toUid,
                    "msg_text" to text,
                    "ts"       to FieldValue.serverTimestamp(),
                    "read"     to false,
                )).await()
                convRef.update(mapOf(
                    "last_msg"      to text,
                    "last_at"       to FieldValue.serverTimestamp(),
                    "unread_$toUid" to FieldValue.increment(1),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun markRead(convId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("conversations").document(convId)
                    .update("unread_$uid", 0).await()
            } catch (_: Exception) {}
        }
    }

    fun startOrOpenConversation(otherUid: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val pa = minOf(uid, otherUid)
                val pb = maxOf(uid, otherUid)
                val existing = firestore.collection("conversations")
                    .whereEqualTo("participant_a", pa)
                    .whereEqualTo("participant_b", pb)
                    .limit(1).get().await()
                val convId = if (!existing.isEmpty) {
                    existing.documents.first().id
                } else {
                    firestore.collection("conversations").add(mapOf(
                        "participant_a" to pa,
                        "participant_b" to pb,
                        "participants"  to listOf(pa, pb),
                        "last_msg"      to "",
                        "last_at"       to FieldValue.serverTimestamp(),
                        "unread_$pa"    to 0,
                        "unread_$pb"    to 0,
                    )).await().id
                }
                onReady(convId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadOtherUser(convId: String) {
        viewModelScope.launch {
            try {
                val doc    = firestore.collection("conversations").document(convId).get().await()
                val pa     = doc.getString("participant_a") ?: return@launch
                val pb     = doc.getString("participant_b") ?: return@launch
                val other  = if (pa == uid) pb else pa
                val ud     = firestore.collection("users").document(other).get().await().data ?: return@launch
                _otherUser.value = User(
                    uid         = other,
                    displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "",
                    photoURL    = ud["photoURL"] as? String ?: "",
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }




    // MessagesScreens uyumluluğu için alias'lar
    fun loadConversations()              = listenConversations()
    fun loadMessages(convId: String)     = listenMessages(convId)
    fun subscribeToMessages(convId: String) = listenMessages(convId)

    override fun onCleared() {
        super.onCleared()
        convListener?.remove()
        msgListener?.remove()
    }
}
