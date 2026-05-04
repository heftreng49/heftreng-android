package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.heftreng.app.data.model.Conversation
import com.heftreng.app.data.model.Message
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════
//  MessagesViewModel — Firestore tabanlı
//
//  Koleksiyon yapısı (temadakiyle birebir):
//  conversations/{convId}
//    participants : [uid_a, uid_b]
//    last_msg     : String
//    updated_at   : Timestamp
//    unread_{uid} : Int
//
//  convMessages/{convId}/msgs/{msgId}
//    senderUid    : String
//    text         : String
//    image_url    : String
//    createdAt    : Timestamp (serverTimestamp)
//    read         : Boolean
//    deleted      : Boolean
//    edited       : Boolean
//    liked_by     : List<String>
//    reply_to_id  : String
//    reply_to_text: String
//    reply_to_name: String
// ═══════════════════════════════════════════════════════════════

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _messages  = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _otherUser = MutableStateFlow<User?>(null)
    val otherUser = _otherUser.asStateFlow()

    private val _loading   = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    // Toplam okunmamış — bottom nav badge
    val totalUnread: StateFlow<Int> = _conversations
        .map { list -> list.sumOf { it.unreadCount } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _uid = MutableStateFlow(auth.currentUser?.uid ?: "")
    val uid get() = _uid.value

    private var convListener: ListenerRegistration? = null
    private var msgListener : ListenerRegistration? = null

    init {
        // Auth state değişince uid güncelle ve conversations'ı yeniden dinle
        auth.addAuthStateListener { firebaseAuth ->
            val newUid = firebaseAuth.currentUser?.uid ?: ""
            if (newUid != _uid.value) {
                _uid.value = newUid
                if (newUid.isNotEmpty()) listenConversations()
            }
        }
    }

    // ── Konuşma listesi — realtime ────────────────────────────
    fun listenConversations() {
        if (uid.isEmpty()) return
        _loading.value = true
        convListener?.remove()
        convListener = firestore.collection("conversations")
            .whereArrayContains("participants", uid)
            // Not: Eğer Firestore'da "members" kullanıyorsan yukarıdaki satırı değiştir
            .orderBy("updated_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    val list = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        val parts = ((d["participants"] as? List<*>)
                            ?: (d["participantIds"] as? List<*>)
                            ?: (d["members"] as? List<*>))
                            ?.filterIsInstance<String>() ?: emptyList()
                        val otherUid = parts.firstOrNull { it != uid } ?: return@mapNotNull null

                        val other = try {
                            val ud = firestore.collection("users").document(otherUid).get().await().data
                            if (ud != null) User(
                                uid         = otherUid,
                                displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "",
                                photoURL    = ud["photoURL"] as? String ?: "",
                                email       = ud["email"]    as? String ?: "",
                            ) else null
                        } catch (_: Exception) { null }

                        Conversation(
                            id             = doc.id,
                            participantIds = parts,
                            lastMessage    = d["last_msg"]   as? String ?: "",
                            lastMessageAt  = (d["updated_at"] as? Timestamp)?.toDate()?.time?.toString() ?: "",
                            otherUser      = other,
                            unreadCount    = (d["unread_$uid"] as? Long)?.toInt() ?: 0,
                        )
                    }
                    _conversations.value = list
                    _loading.value = false
                }
            }
    }

    // ── Mesaj listesi — realtime ──────────────────────────────
    fun listenMessages(convId: String) {
        msgListener?.remove()
        _loading.value = true
        msgListener = firestore.collection("convMessages").document(convId)
            .collection("msgs")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { _loading.value = false; return@addSnapshotListener }
                _messages.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    if (d["deleted"] as? Boolean == true) return@mapNotNull null
                    Message(
                        id             = doc.id,
                        conversationId = convId,
                        senderId       = d["senderUid"]     as? String ?: "",
                        text           = d["text"]          as? String ?: "",
                        imageUrl       = d["image_url"]     as? String ?: "",
                        createdAt      = (d["createdAt"] as? Timestamp)?.toDate()?.time?.toString() ?: "",
                        read           = d["read"]          as? Boolean ?: false,
                        deleted        = d["deleted"]       as? Boolean ?: false,
                        edited         = d["edited"]        as? Boolean ?: false,
                        likedBy        = (d["liked_by"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        replyToId      = d["reply_to_id"]   as? String ?: "",
                        replyToText    = d["reply_to_text"] as? String ?: "",
                        replyToName    = d["reply_to_name"] as? String ?: "",
                    )
                }
                _loading.value = false
                markRead(convId)
            }
    }

    // ── Mesaj gönder ──────────────────────────────────────────
    fun sendMessage(
        convId     : String,
        toUid      : String,
        text       : String,
        imageUrl   : String = "",
        replyToId  : String = "",
        replyToText: String = "",
        replyToName: String = "",
    ) {
        if (uid.isEmpty() || (text.isBlank() && imageUrl.isBlank())) return
        viewModelScope.launch {
            try {
                val msgData = mutableMapOf<String, Any>(
                    "senderUid" to uid,
                    "text"      to text,
                    "image_url" to imageUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "read"      to false,
                    "deleted"   to false,
                    "edited"    to false,
                    "liked_by"  to emptyList<String>(),
                )
                if (replyToId.isNotBlank()) {
                    msgData["reply_to_id"]   = replyToId
                    msgData["reply_to_text"] = replyToText
                    msgData["reply_to_name"] = replyToName
                }
                // convMessages/{convId}/msgs
                firestore.collection("convMessages").document(convId)
                    .collection("msgs").add(msgData).await()
                // conversations güncelle
                val convUpd = mutableMapOf<String, Any>(
                    "last_msg"      to (text.ifBlank { "📷 Görsel" }),
                    "updated_at"    to FieldValue.serverTimestamp(),
                    "unread_$toUid" to FieldValue.increment(1),
                    "unread_$uid"   to 0L,
                )
                firestore.collection("conversations").document(convId)
                    .set(convUpd, SetOptions.merge()).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Mesaj beğen/beğenmekten vazgeç ───────────────────────
    fun toggleLike(msg: Message) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val ref = firestore.collection("convMessages")
                    .document(msg.conversationId)
                    .collection("msgs").document(msg.id)
                val likes = msg.likedBy.toMutableList()
                if (uid in likes) likes.remove(uid) else likes.add(uid)
                ref.update("liked_by", likes).await()
                _messages.value = _messages.value.map {
                    if (it.id == msg.id) it.copy(likedBy = likes) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Mesaj sil (soft delete) ───────────────────────────────
    fun deleteMessage(msg: Message) {
        if (uid.isEmpty() || msg.senderId != uid) return
        viewModelScope.launch {
            try {
                firestore.collection("convMessages")
                    .document(msg.conversationId)
                    .collection("msgs").document(msg.id)
                    .update("deleted", true).await()
                _messages.value = _messages.value.filter { it.id != msg.id }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Mesaj düzenle ─────────────────────────────────────────
    fun editMessage(msg: Message, newText: String) {
        if (uid.isEmpty() || msg.senderId != uid || newText.isBlank()) return
        viewModelScope.launch {
            try {
                firestore.collection("convMessages")
                    .document(msg.conversationId)
                    .collection("msgs").document(msg.id)
                    .update(mapOf("text" to newText, "edited" to true)).await()
                _messages.value = _messages.value.map {
                    if (it.id == msg.id) it.copy(text = newText, edited = true) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Okundu işaretleme ─────────────────────────────────────
    private fun markRead(convId: String) {
        if (uid.isEmpty()) return
        // Yerel state anında sıfırla — badge hemen güncellensin
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id == convId) conv.copy(unreadCount = 0) else conv
        }
        viewModelScope.launch {
            try {
                firestore.collection("conversations").document(convId)
                    .update("unread_$uid", 0L).await()
            } catch (_: Exception) {}
        }
    }

    // ── Konuşma başlat veya mevcut aç ────────────────────────
    // ID deterministik: minOf(uid, otherUid) + "__" + maxOf(...)
    fun startOrOpenConversation(otherUid: String, onReady: (String) -> Unit) {
        // Kullanıcı giriş yapmamışsa veya hedef uid boşsa işlem yapma
        if (uid.isBlank() || otherUid.isBlank() || uid == otherUid) return
        viewModelScope.launch {
            try {
                val pa = minOf(uid, otherUid)
                val pb = maxOf(uid, otherUid)
                val convId = "${pa}__${pb}"

                val existing = firestore.collection("conversations")
                    .document(convId).get().await()

                if (!existing.exists()) {
                    firestore.collection("conversations").document(convId).set(
                        mapOf(
                            "participants"    to listOf(pa, pb),
                            "last_msg"        to "",
                            "updated_at"      to FieldValue.serverTimestamp(),
                            "unread_$pa"      to 0,
                            "unread_$pb"      to 0,
                        )
                    ).await()
                }
                onReady(convId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Karşı kullanıcı bilgisi ───────────────────────────────
    fun loadOtherUser(convId: String) {
        viewModelScope.launch {
            try {
                val conv     = _conversations.value.firstOrNull { it.id == convId }
                val otherUid = conv?.participantIds?.firstOrNull { it != uid }
                    ?: firestore.collection("conversations").document(convId).get().await()
                        .let { doc ->
                            val parts = (doc.data?.get("participants") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                            parts.firstOrNull { it != uid }
                        } ?: return@launch
                val ud = firestore.collection("users").document(otherUid).get().await().data ?: return@launch
                _otherUser.value = User(
                    uid         = otherUid,
                    displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "",
                    photoURL    = ud["photoURL"]    as? String ?: "",
                    email       = ud["email"]       as? String ?: "",
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Alias'lar
    fun loadConversations()             = listenConversations()
    fun loadMessages(convId: String)    = listenMessages(convId)
    fun subscribeToMessages(convId: String) = listenMessages(convId)

    // ── Konuşma sil ──────────────────────────────────────────────────────────
    // Tema: _msgDelConvConfirm — kendi mesajlarını sil, conv listesinden çıkar
    fun deleteConversation(convId: String, onDone: () -> Unit = {}) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Sadece kendi gönderdiği mesajları sil (soft delete)
                val msgs = firestore.collection("convMessages")
                    .document(convId).collection("msgs")
                    .whereEqualTo("senderUid", uid)
                    .get().await()
                val batch = firestore.batch()
                msgs.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "deleted"   to true,
                        "deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ))
                }
                batch.commit().await()
                // Lokal listeden kaldır
                _conversations.value = _conversations.value.filter { it.id != convId }
                onDone()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        convListener?.remove()
        msgListener?.remove()
    }
}
