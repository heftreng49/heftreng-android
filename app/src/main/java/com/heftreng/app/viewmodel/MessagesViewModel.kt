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
import com.heftreng.app.util.AppLifecycleObserver

// ═══════════════════════════════════════════════════════════════
//  MessagesViewModel — Firestore tabanlı [GÜNCELLENDİ]
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

    val totalUnread: StateFlow<Int> = _conversations
        .map { list -> list.sumOf { it.unreadCount } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _uid = MutableStateFlow(auth.currentUser?.uid ?: "")
    val uid get() = _uid.value

    private var _myFirestoreName: String = auth.currentUser?.displayName ?: "Biri"

    private var convListener: ListenerRegistration? = null
    private var msgListener : ListenerRegistration? = null

    private val _hasOlderMessages = MutableStateFlow(false)
    val hasOlderMessages = _hasOlderMessages.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder = _loadingOlder.asStateFlow()

    private var oldestMsgDoc : com.google.firebase.firestore.DocumentSnapshot? = null
    private var newestMsgTs  : com.google.firebase.Timestamp? = null
    private var currentConvId: String = ""
    private val MSG_PAGE     = 50

    init {
        // Kullanıcı adı önbelleğe al (auth state değişince güncelle)
        auth.addAuthStateListener { firebaseAuth ->
            val newUid = firebaseAuth.currentUser?.uid ?: ""
            if (newUid != _uid.value) {
                _uid.value = newUid
                if (newUid.isNotEmpty()) {
                    // listenConversations() — foreground callback'ten çağrılır, burada değil
                    val authName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                    if (authName != null) {
                        _myFirestoreName = authName
                    } else {
                        viewModelScope.launch {
                            try {
                                val doc = firestore.collection("users").document(newUid).get().await()
                                val name = (doc.getString("displayName") ?: doc.getString("name"))
                                    ?.takeIf { it.isNotBlank() }
                                if (name != null) _myFirestoreName = name
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    // Çıkış yapıldı — listener kapat
                    convListener?.remove(); convListener = null
                    _conversations.value = emptyList()
                }
            }
        }

        // Foreground → konuşmaları dinle; background → sadece convListener kapat
        // (msgListener MessagesScreen'den yönetilir — o ekran kapalıyken zaten kapalı)
        val foregroundCb: () -> Unit = {
            if (uid.isNotEmpty()) listenConversations()
        }
        val backgroundCb: () -> Unit = {
            convListener?.remove()
            convListener = null
        }
        AppLifecycleObserver.addForegroundCallback(foregroundCb)
        AppLifecycleObserver.addBackgroundCallback(backgroundCb)
        viewModelScope.launch {
            kotlinx.coroutines.awaitCancellation()
        }.invokeOnCompletion {
            AppLifecycleObserver.removeForegroundCallback(foregroundCb)
            AppLifecycleObserver.removeBackgroundCallback(backgroundCb)
        }

        // Şu an zaten foreground'daysa hemen başlat
        if (AppLifecycleObserver.isInForeground.value && uid.isNotEmpty()) {
            listenConversations()
        }
        val curUid = auth.currentUser?.uid
        if (!curUid.isNullOrBlank()) {
            val authName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            if (authName != null) {
                _myFirestoreName = authName
            } else {
                viewModelScope.launch {
                    try {
                        val doc = firestore.collection("users").document(curUid).get().await()
                        val name = (doc.getString("displayName") ?: doc.getString("name"))
                            ?.takeIf { it.isNotBlank() }
                        if (name != null) _myFirestoreName = name
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private val userCache = mutableMapOf<String, User>()

    // ── Konuşma listesi — realtime ────────────────────────────
    fun listenConversations() {
        if (uid.isEmpty()) return
        _loading.value = _conversations.value.isEmpty()
        convListener?.remove()
        convListener = firestore.collection("conversations")
            .whereArrayContains("participants", uid)
            .orderBy("updated_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    data class RawConv(
                        val id: String, val parts: List<String>, val otherUid: String,
                        val lastMsg: String, val updatedAt: Timestamp?, val unread: Int,
                    )
                    val rawList = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        val parts = ((d["participants"] as? List<*>)
                            ?: (d["participantIds"] as? List<*>)
                            ?: (d["members"] as? List<*>))
                            ?.filterIsInstance<String>() ?: emptyList()
                        val otherUid = parts.firstOrNull { it != uid } ?: return@mapNotNull null
                        RawConv(
                            id        = doc.id,
                            parts     = parts,
                            otherUid  = otherUid,
                            lastMsg   = d["last_msg"]   as? String ?: "",
                            // ÇÖZÜLDÜ: String yerine doğrudan model yapısına uygun Timestamp atandı
                            updatedAt = d["updated_at"] as? Timestamp,
                            unread    = (d["unread_$uid"] as? Long)?.toInt() ?: 0,
                        )
                    }

                    val missingUids = rawList.map { it.otherUid }
                        .filter { it !in userCache }.distinct()

                    missingUids.chunked(10).forEach { chunk ->
                        try {
                            val usersSnap = firestore.collection("users")
                                .whereIn(FieldPath.documentId(), chunk)
                                .get().await()
                            usersSnap.documents.forEach { doc ->
                                val ud = doc.data ?: return@forEach
                                userCache[doc.id] = User(
                                    uid         = doc.id,
                                    displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "",
                                    photoURL    = ud["photoURL"]    as? String ?: "",
                                    email       = ud["email"]       as? String ?: "",
                                )
                            }
                        } catch (_: Exception) {}
                    }

                    _conversations.value = rawList.map { raw ->
                        Conversation(
                            id             = raw.id,
                            participantIds = raw.parts,
                            lastMessage    = raw.lastMsg,
                            lastMessageAt  = raw.updatedAt,
                            otherUser      = userCache[raw.otherUid],
                            unreadCount    = raw.unread,
                        )
                    }
                    _loading.value = false
                }
            }
    }

    // ── Mesaj listesi — hibrit sistem ────────────────────────
    fun listenMessages(convId: String) {
        msgListener?.remove()
        currentConvId    = convId
        oldestMsgDoc     = null
        newestMsgTs      = null
        _messages.value  = emptyList()
        _hasOlderMessages.value = false
        _loading.value   = true

        viewModelScope.launch {
            try {
                val snap = firestore.collection("convMessages").document(convId)
                    .collection("msgs")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(MSG_PAGE.toLong())
                    .get().await()

                val docs = snap.documents
                if (docs.isNotEmpty()) {
                    oldestMsgDoc = docs.last()
                    newestMsgTs  = docs.first().getTimestamp("createdAt")
                }

                val initial = docs.reversed()
                    .mapNotNull { it.toMessage(convId) }
                _messages.value = initial
                _hasOlderMessages.value = docs.size >= MSG_PAGE
                _loading.value = false
                markRead(convId)

            } catch (e: Exception) {
                e.printStackTrace()
                _loading.value = false
            }

            val afterTs = newestMsgTs ?: Timestamp.now()
            msgListener = firestore.collection("convMessages").document(convId)
                .collection("msgs")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .whereGreaterThan("createdAt", afterTs)
                .addSnapshotListener { snap, _ ->
                    if (snap == null || snap.isEmpty) return@addSnapshotListener
                    val newMsgs = snap.documents.mapNotNull { it.toMessage(convId) }
                    if (newMsgs.isEmpty()) return@addSnapshotListener
                    val existing = _messages.value
                    val existingIds = existing.map { it.id }.toSet()
                    val toAdd = newMsgs.filter { it.id !in existingIds }
                    if (toAdd.isNotEmpty()) {
                        _messages.value = existing + toAdd
                        newestMsgTs = snap.documents.last().getTimestamp("createdAt") ?: newestMsgTs
                        markRead(convId)
                    }
                }
        }
    }

    fun loadOlderMessages() {
        val convId = currentConvId.takeIf { it.isNotBlank() } ?: return
        val oldest = oldestMsgDoc ?: return
        if (_loadingOlder.value) return
        viewModelScope.launch {
            _loadingOlder.value = true
            try {
                val snap = firestore.collection("convMessages").document(convId)
                    .collection("msgs")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(oldest)
                    .limit(MSG_PAGE.toLong())
                    .get().await()
                if (snap.documents.isNotEmpty()) {
                    oldestMsgDoc = snap.documents.last()
                    val older = snap.documents.reversed()
                        .mapNotNull { it.toMessage(convId) }
                    _messages.value = older + _messages.value
                    _hasOlderMessages.value = snap.documents.size >= MSG_PAGE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    // ÇÖZÜLDÜ: 'likedBy' parametresi temizlendi, createdAt alanı Timestamp? yapısına çekildi
    private fun com.google.firebase.firestore.DocumentSnapshot.toMessage(convId: String): Message? {
        val d = data ?: return null
        if (d["deleted"] as? Boolean == true) return null
        return Message(
            id             = id,
            conversationId = convId,
            senderId       = d["senderUid"]     as? String ?: "",
            text           = d["text"]          as? String ?: "",
            imageUrl       = d["image_url"]     as? String ?: "",
            audioUrl       = d["audio_url"]     as? String ?: "",
            createdAt      = d["createdAt"]     as? Timestamp,
            read           = d["read"]          as? Boolean ?: false,
            deleted        = d["deleted"]       as? Boolean ?: false,
            edited         = d["edited"]        as? Boolean ?: false,
            replyToId      = d["reply_to_id"]   as? String ?: "",
            replyToText    = d["reply_to_text"] as? String ?: "",
            replyToName    = d["reply_to_name"] as? String ?: "",
        )
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
                    "edited"    to false
                )
                if (replyToId.isNotBlank()) {
                    msgData["reply_to_id"]   = replyToId
                    msgData["reply_to_text"] = replyToText
                    msgData["reply_to_name"] = replyToName
                }
                firestore.collection("convMessages").document(convId)
                    .collection("msgs").add(msgData).await()

                val convUpd = mutableMapOf<String, Any>(
                    "last_msg"      to (text.ifBlank { "📷 Görsel" }),
                    "updated_at"    to FieldValue.serverTimestamp(),
                    "unread_$toUid" to FieldValue.increment(1),
                    "unread_$uid"   to 0L,
                )
                firestore.collection("conversations").document(convId)
                    .set(convUpd, SetOptions.merge()).await()

                try {
                    val myName = _myFirestoreName
                    com.google.firebase.functions.FirebaseFunctions
                        .getInstance("europe-west1")
                        .getHttpsCallable("sendPush")
                        .call(hashMapOf(
                            "targetUid" to toUid,
                            "title"     to myName,
                            "body"      to text.ifBlank { "📷 Görsel gönderdi" },
                            "type"      to "message",
                            "convId"    to convId,
                            "fromUid"   to uid,
                            "postId"    to "",
                        )).await()
                    android.util.Log.d("HF_PUSH", "Mesaj push gönderildi → $toUid")
                } catch (e: Exception) {
                    android.util.Log.e("HF_PUSH", "Mesaj push hatası: ${e.message}")
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Resim: Storage'a yükle → mesaj gönder ────────────────────────────────
    private val _uploading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()

    fun uploadImageAndSend(
        convId  : String,
        toUid   : String,
        uri     : android.net.Uri,
        replyToId   : String = "",
        replyToText : String = "",
        replyToName : String = "",
    ) {
        viewModelScope.launch {
            try {
                _uploading.value = true
                val ref = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .reference.child("messages/$convId/${System.currentTimeMillis()}.jpg")
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()
                sendMessage(
                    convId      = convId,
                    toUid       = toUid,
                    text        = "",
                    imageUrl    = url,
                    replyToId   = replyToId,
                    replyToText = replyToText,
                    replyToName = replyToName,
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uploading.value = false
            }
        }
    }

    // ── Sesli mesaj: Storage'a yükle → mesaj gönder ──────────────────────────
    fun uploadAudioAndSend(
        convId : String,
        toUid  : String,
        file   : java.io.File,
    ) {
        viewModelScope.launch {
            try {
                _uploading.value = true
                val uri = android.net.Uri.fromFile(file)
                val ref = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .reference.child("messages/$convId/audio_${System.currentTimeMillis()}.m4a")
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()
                val msgData = mutableMapOf<String, Any>(
                    "senderUid" to uid,
                    "text"      to "",
                    "image_url" to "",
                    "audio_url" to url,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "read"      to false,
                    "deleted"   to false,
                    "edited"    to false
                )
                firestore.collection("convMessages").document(convId)
                    .collection("msgs").add(msgData).await()
                val convUpd = mutableMapOf<String, Any>(
                    "last_msg"      to "🎤 Sesli mesaj",
                    "updated_at"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "unread_$toUid" to com.google.firebase.firestore.FieldValue.increment(1),
                    "unread_$uid"   to 0L,
                )
                firestore.collection("conversations").document(convId)
                    .set(convUpd, com.google.firebase.firestore.SetOptions.merge()).await()
                try {
                    val myName = _myFirestoreName
                    com.google.firebase.functions.FirebaseFunctions
                        .getInstance("europe-west1")
                        .getHttpsCallable("sendPush")
                        .call(hashMapOf(
                            "targetUid" to toUid,
                            "title"     to myName,
                            "body"      to "🎤 Sesli mesaj gönderdi",
                            "type"      to "message",
                            "convId"    to convId,
                            "fromUid"   to uid,
                            "postId"    to "",
                        )).await()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uploading.value = false
                file.delete()
            }
        }
    }

    // ── ÇÖZÜLDÜ: Alt koleksiyon (Subcollection) Destekli Yeni Beğeni Yapısı ──
    fun toggleLike(msg: Message) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val likeRef = firestore.collection("convMessages")
                    .document(msg.conversationId)
                    .collection("msgs").document(msg.id)
                    .collection("likes").document(uid)

                val doc = likeRef.get().await()
                if (doc.exists()) {
                    likeRef.delete().await()
                } else {
                    likeRef.set(mapOf("uid" to uid, "ts" to FieldValue.serverTimestamp())).await()
                }
                // Mesaj beğenildiğinde UI katmanında dinleyen bir alt tetikleyici yoksa 
                // ya da listeyi yerelde anlık güncellemek istersen (isteğe bağlı) burayı kullanabilirsin.
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
    fun startOrOpenConversation(otherUid: String, onReady: (String) -> Unit) {
        if (otherUid.isBlank()) return

        val myUid = uid.ifBlank {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        }
        if (myUid.isBlank() || myUid == otherUid) return

        viewModelScope.launch {
            try {
                val pa     = minOf(myUid, otherUid)
                val pb     = maxOf(myUid, otherUid)
                val convId = "${pa}__${pb}"

                firestore.collection("conversations").document(convId)
                    .set(
                        mapOf(
                            "participants" to listOf(pa, pb),
                            "updated_at"   to FieldValue.serverTimestamp(),
                            "unread_$pa"   to 0,
                            "unread_$pb"   to 0,
                            "last_msg"     to "",
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()

                onReady(convId)
            } catch (e: Exception) {
                e.printStackTrace()
                val pa = minOf(myUid, otherUid)
                val pb = maxOf(myUid, otherUid)
                onReady("${pa}__${pb}")
            }
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

                userCache[otherUid]?.let { _otherUser.value = it; return@launch }

                val ud = firestore.collection("users").document(otherUid).get().await().data ?: return@launch
                val user = User(
                    uid         = otherUid,
                    displayName = ud["displayName"] as? String ?: ud["name"] as? String ?: "",
                    photoURL    = ud["photoURL"]    as? String ?: "",
                    email       = ud["email"]       as? String ?: "",
                )
                userCache[otherUid] = user
                _otherUser.value = user
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadConversations()             = listenConversations()
    fun loadMessages(convId: String)    = listenMessages(convId)
    fun subscribeToMessages(convId: String) = listenMessages(convId)

    // ── Konuşma sil ──────────────────────────────────────────────────────────
    fun deleteConversation(convId: String, onDone: () -> Unit = {}) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val convRef = firestore.collection("conversations").document(convId)
                val batch   = firestore.batch()

                batch.update(convRef, mapOf(
                    "participants"  to com.google.firebase.firestore.FieldValue.arrayRemove(uid),
                    "participantIds" to com.google.firebase.firestore.FieldValue.arrayRemove(uid),
                    "deletedBy_$uid" to true,
                    "deletedAt_$uid" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ))

                val msgs = firestore.collection("convMessages")
                    .document(convId).collection("msgs")
                    .whereEqualTo("senderUid", uid)
                    .get().await()
                msgs.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "deleted"   to true,
                        "deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ))
                }

                batch.commit().await()

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
