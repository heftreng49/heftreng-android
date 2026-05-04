package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.heftreng.app.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _conversations  = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _messages       = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _totalUnread    = MutableStateFlow(0)
    val totalUnread: StateFlow<Int> = _totalUnread

    private val _loading        = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _sending        = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _error          = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var convUnsub : ListenerRegistration? = null
    private var msgUnsub  : ListenerRegistration? = null

    // ─── Konuşma listesini dinle ─────────────────────────────────────────────
    // Tema: conversations, participants array-contains, updated_at DESC
    // Composite index gerekir: participants(ARRAY) + updated_at(DESC)
    fun listenConversations() {
        val uid = auth.currentUser?.uid ?: return
        convUnsub?.remove()
        convUnsub = firestore.collection("conversations")
            .whereArrayContains("participants", uid)
            .orderBy("updated_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Composite index yoksa fallback — orderBy olmadan
                    listenConversationsFallback(uid)
                    return@addSnapshotListener
                }
                snap ?: return@addSnapshotListener
                viewModelScope.launch {
                    processConvSnap(snap, uid)
                }
            }
    }

    private fun listenConversationsFallback(uid: String) {
        convUnsub?.remove()
        convUnsub = firestore.collection("conversations")
            .whereArrayContains("participants", uid)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                viewModelScope.launch {
                    processConvSnap(snap, uid)
                }
            }
    }

    private suspend fun processConvSnap(snap: QuerySnapshot, uid: String) {
        val list = mutableListOf<Conversation>()
        snap.documents.forEach { doc ->
            val d         = doc.data ?: return@forEach
            val parts     = (d["participants"] as? List<*>)?.filterIsInstance<String>() ?: return@forEach
            val otherUid  = parts.firstOrNull { it != uid } ?: return@forEach
            try {
                val uDoc  = firestore.collection("users").document(otherUid).get().await()
                val ud    = uDoc.data ?: emptyMap()
                val other = User(
                    uid         = otherUid,
                    displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "?",
                    photoURL    = ud["photoURL"] as? String ?: "",
                )
                list.add(Conversation(
                    id             = doc.id,
                    participantIds = parts,
                    lastMessage    = d["last_msg"] as? String ?: "",
                    lastMessageAt  = (d["updated_at"] as? Timestamp)?.toDate()?.time?.toString() ?: "",
                    otherUser      = other,
                    unreadCount    = (d["unread_$uid"] as? Long)?.toInt() ?: 0,
                ))
            } catch (_: Exception) {}
        }
        // Client-side sırala (index olmadığında da çalışır)
        _conversations.value = list.sortedByDescending { it.lastMessageAt }
        _totalUnread.value   = list.sumOf { it.unreadCount }
    }

    // ─── Konuşma başlat / aç ────────────────────────────────────────────────
    // Tema: _cid(a,b) = a<b ? a+'__'+b : b+'__'+a
    fun openOrCreateConversation(otherUid: String, onReady: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val pa  = minOf(uid, otherUid)
        val pb  = maxOf(uid, otherUid)
        val convId = "${pa}__${pb}"

        viewModelScope.launch {
            try {
                val me = firestore.collection("users").document(uid).get().await()
                val fromName  = me.getString("displayName") ?: me.getString("name") ?: ""
                val fromPhoto = me.getString("photoURL") ?: ""
                val them = firestore.collection("users").document(otherUid).get().await()
                val toName    = them.getString("displayName") ?: them.getString("name") ?: ""
                val toPhoto   = them.getString("photoURL") ?: ""

                val convRef = firestore.collection("conversations").document(convId)
                val convDoc = convRef.get().await()
                if (!convDoc.exists()) {
                    convRef.set(mapOf(
                        "participants"    to listOf(pa, pb),
                        "participantData" to mapOf(
                            uid      to mapOf("name" to fromName, "photo" to fromPhoto),
                            otherUid to mapOf("name" to toName,   "photo" to toPhoto),
                        ),
                        "last_msg"        to "",
                        "updated_at"      to FieldValue.serverTimestamp(),
                        "unread_$uid"     to 0,
                        "unread_$otherUid"to 0,
                    )).await()
                }
                onReady(convId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ─── Mesajları dinle ─────────────────────────────────────────────────────
    // Tema: convMessages/{convId}/msgs, createdAt ASC
    fun listenMessages(convId: String) {
        msgUnsub?.remove()
        _messages.value = emptyList()
        _loading.value  = true
        msgUnsub = firestore.collection("convMessages")
            .document(convId)
            .collection("msgs")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                _loading.value = false
                _messages.value = snap.documents.mapNotNull { d ->
                    val data = d.data ?: return@mapNotNull null
                    if (data["deleted"] == true) return@mapNotNull null
                    Message(
                        id            = d.id,
                        conversationId= convId,
                        senderId      = data["senderUid"] as? String ?: "",
                        text          = data["text"] as? String ?: "",
                        imageUrl      = data["image_url"] as? String ?: "",
                        createdAt     = (data["createdAt"] as? Timestamp)?.toDate()?.time?.toString() ?: "",
                        read          = data["read"]    as? Boolean ?: false,
                        seen          = data["seen"]    as? Boolean ?: false,
                        deleted       = data["deleted"] as? Boolean ?: false,
                        edited        = data["edited"]  as? Boolean ?: false,
                        likedBy       = (data["liked_by"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        replyToId     = data["reply_to_id"]   as? String ?: "",
                        replyToText   = data["reply_to_text"] as? String ?: "",
                        replyToName   = data["reply_to_name"] as? String ?: "",
                    )
                }
                // Okunmamışları işaretle
                markSeen(convId)
            }
    }

    // ─── Mesaj gönder ────────────────────────────────────────────────────────
    // Tema: senderUid, text, image_url, createdAt, read:false, seen:false,
    //       deleted:false, edited:false, liked_by:[]
    fun sendMessage(
        convId  : String,
        otherUid: String,
        text    : String,
        imageUrl: String = "",
        replyTo : Message? = null,
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (text.isBlank() && imageUrl.isBlank()) return

        viewModelScope.launch {
            _sending.value = true
            try {
                val msgData = mutableMapOf<String, Any>(
                    "senderUid"   to uid,
                    "text"        to text.trim(),
                    "image_url"   to imageUrl,
                    "createdAt"   to FieldValue.serverTimestamp(),
                    "read"        to false,
                    "seen"        to false,
                    "deleted"     to false,
                    "edited"      to false,
                    "liked_by"    to emptyList<String>(),
                )
                replyTo?.let {
                    msgData["reply_to_id"]   = it.id
                    msgData["reply_to_text"] = it.text
                    msgData["reply_to_name"] = it.senderId
                }
                firestore.collection("convMessages").document(convId)
                    .collection("msgs").add(msgData).await()

                // Konuşma güncelle
                firestore.collection("conversations").document(convId)
                    .update(mapOf(
                        "last_msg"          to (text.ifBlank { "📷" }),
                        "updated_at"        to FieldValue.serverTimestamp(),
                        "unread_$otherUid"  to FieldValue.increment(1),
                        "unread_$uid"       to 0,
                    )).await()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _sending.value = false
            }
        }
    }

    // ─── Mesaj sil ───────────────────────────────────────────────────────────
    fun deleteMessage(convId: String, msgId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("convMessages").document(convId)
                    .collection("msgs").document(msgId)
                    .update(mapOf("deleted" to true, "text" to "")).await()
            } catch (_: Exception) {}
        }
    }

    // ─── Beğen ───────────────────────────────────────────────────────────────
    fun toggleLikeMessage(convId: String, msg: Message) {
        val uid    = auth.currentUser?.uid ?: return
        val liked  = msg.likedBy.contains(uid)
        viewModelScope.launch {
            try {
                val ref = firestore.collection("convMessages").document(convId)
                    .collection("msgs").document(msg.id)
                if (liked) ref.update("liked_by", FieldValue.arrayRemove(uid)).await()
                else       ref.update("liked_by", FieldValue.arrayUnion(uid)).await()
            } catch (_: Exception) {}
        }
    }

    // ─── Okundu işareti ──────────────────────────────────────────────────────
    // Tema: seen:true + read:true
    private fun markSeen(convId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Unread sayacı sıfırla
                firestore.collection("conversations").document(convId)
                    .update("unread_$uid", 0).await()
                // Karşı tarafın gönderdiği okunmamış mesajları işaretle
                val unread = firestore.collection("convMessages").document(convId)
                    .collection("msgs")
                    .whereEqualTo("read", false)
                    .whereNotEqualTo("senderUid", uid)
                    .limit(50).get().await()
                val batch = firestore.batch()
                unread.documents.forEach { d ->
                    batch.update(d.reference, mapOf("read" to true, "seen" to true))
                }
                batch.commit().await()
            } catch (_: Exception) {}
        }
    }

    fun stopListeningMessages() {
        msgUnsub?.remove()
        msgUnsub = null
        _messages.value = emptyList()
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        convUnsub?.remove()
        msgUnsub?.remove()
    }
}
