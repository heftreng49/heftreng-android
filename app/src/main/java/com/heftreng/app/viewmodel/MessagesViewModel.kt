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
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    // ── Mesaj önbelleği — disk tabanlı, konuşma başına JSON dosyası ──────────
    // Strateji:
    //   1. Ekran açılınca cache'den yükle → anında göster (0 Firestore okuması)
    //   2. cache'deki en son mesajın ts'inden itibaren Firestore'dan sadece
    //      delta (yeni mesajlar) çek → çok az okuma
    //   3. Snapshot listener yalnızca delta için → gerçek zamanlı güncel kalır
    //   4. Her yeni mesaj gelince cache güncellenir (son MSG_CACHE_SIZE mesaj)
    //   5. Cache yoksa (ilk açılış) normal tam yükleme → cache oluşturulur
    private companion object {
        const val MSG_CACHE_SIZE = 50   // konuşma başına saklanacak max mesaj
        const val CACHE_DIR      = "msg_cache"
    }

    private lateinit var cacheDir: File

    fun initCache(context: android.content.Context) {
        cacheDir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
    }

    private fun cacheFile(convId: String): File =
        File(cacheDir, "${uid}_${convId}.json")

    private suspend fun readCache(convId: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            val f = cacheFile(convId)
            if (!f.exists()) return@withContext emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                try { arr.getJSONObject(i).toMessage(convId) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.w("MsgCache", "readCache error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun writeCache(convId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        try {
            val recent = messages.takeLast(MSG_CACHE_SIZE)
            val arr = JSONArray()
            recent.forEach { arr.put(it.toJson()) }
            cacheFile(convId).writeText(arr.toString())
        } catch (e: Exception) {
            Log.w("MsgCache", "writeCache error: ${e.message}")
        }
    }

    fun clearCache(convId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { cacheFile(convId).delete() } catch (_: Exception) {}
        }
    }

    // JSON serialize / deserialize — sadece temel alanlar, görüntüleme için yeterli
    private fun Message.toJson(): JSONObject = JSONObject().apply {
        put("id",          id)
        put("senderId",    senderId)
        put("text",        text)
        put("imageUrl",    imageUrl)
        put("audioUrl",    audioUrl)
        put("read",        read)
        put("readAtSeconds", readAt?.seconds ?: 0L)
        put("readAtNanos",   readAt?.nanoseconds ?: 0)
        put("deleted",     deleted)
        put("edited",      edited)
        put("replyToId",   replyToId)
        put("replyToText", replyToText)
        put("replyToName", replyToName)
        put("likesCount",  likesCount)
        put("isLikedByMe", isLikedByMe)
        put("tsSeconds",   createdAt?.seconds ?: 0L)
        put("tsNanos",     createdAt?.nanoseconds ?: 0)
        val mentionsArr = JSONArray(); mentions.forEach { mentionsArr.put(it) }
        put("mentions", mentionsArr)
    }

    private fun JSONObject.toMessage(convId: String): Message = Message(
        id             = getString("id"),
        conversationId = convId,
        senderId       = optString("senderId"),
        text           = optString("text"),
        imageUrl       = optString("imageUrl"),
        audioUrl       = optString("audioUrl"),
        read           = optBoolean("read"),
        readAt         = optLong("readAtSeconds").takeIf { it > 0 }?.let {
            Timestamp(it, optInt("readAtNanos"))
        },
        deleted        = optBoolean("deleted"),
        edited         = optBoolean("edited"),
        replyToId      = optString("replyToId"),
        replyToText    = optString("replyToText"),
        replyToName    = optString("replyToName"),
        likesCount     = optInt("likesCount"),
        isLikedByMe    = optBoolean("isLikedByMe"),
        createdAt      = optLong("tsSeconds").takeIf { it > 0 }?.let {
            Timestamp(it, optInt("tsNanos"))
        },
        mentions       = optJSONArray("mentions")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
    )

    // ── Mention (@kullanıcı) önerileri — FeedViewModel'deki ile aynı ortak
    //    MentionHelper üzerinden çalışır, kod tekrarı yok. ────────────────────
    private val _mentionSuggestions = MutableStateFlow<List<com.heftreng.app.util.MentionHelper.MentionUser>>(emptyList())
    val mentionSuggestions = _mentionSuggestions.asStateFlow()

    private var mentionSearchJob: kotlinx.coroutines.Job? = null

    fun searchMentionUsers(query: String) {
        mentionSearchJob?.cancel()
        if (query.isBlank()) {
            _mentionSuggestions.value = emptyList()
            return
        }
        mentionSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200) // basit debounce
            _mentionSuggestions.value = com.heftreng.app.util.MentionHelper.searchUsers(firestore, query)
        }
    }

    fun clearMentionSuggestions() {
        mentionSearchJob?.cancel()
        _mentionSuggestions.value = emptyList()
    }

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

    // ── Mesaj listesi — hibrit cache sistemi ─────────────────────────────────
    // Açılışta: cache → anında göster → sadece delta Firestore'dan çek
    // Her yeni mesajda: cache güncellenir
    // Net tasarruf: aynı konuşmanın tekrar açılmasında 0 Firestore okuması
    fun listenMessages(convId: String) {
        msgListener?.remove()
        currentConvId           = convId
        oldestMsgDoc            = null
        newestMsgTs             = null
        _messages.value         = emptyList()
        _hasOlderMessages.value = false
        _loading.value          = true

        viewModelScope.launch {
            // ── 1. Cache'den yükle → anında göster ────────────────────────────
            val cached = if (::cacheDir.isInitialized) readCache(convId) else emptyList()
            if (cached.isNotEmpty()) {
                _messages.value = cached
                _loading.value  = false
                // cache'deki en yeni mesajın ts'i → bu noktadan itibaren delta çekeceğiz
                newestMsgTs = cached.mapNotNull { it.createdAt }.maxByOrNull { it.seconds }
            }

            // ── 2. Firestore'dan delta çek (cache'den sonraki yeniler) ─────────
            try {
                if (newestMsgTs != null) {
                    // Cache var → sadece yeni mesajları çek
                    val deltaSnap = firestore.collection("convMessages").document(convId)
                        .collection("msgs")
                        .orderBy("createdAt", Query.Direction.ASCENDING)
                        .whereGreaterThan("createdAt", newestMsgTs!!)
                        .get().await()

                    if (deltaSnap.documents.isNotEmpty()) {
                        val delta = deltaSnap.documents.mapNotNull { it.toMessage(convId) }
                        val merged = mergeMessages(_messages.value, delta)
                        _messages.value = merged
                        newestMsgTs = deltaSnap.documents.last().getTimestamp("createdAt") ?: newestMsgTs
                        writeCache(convId, merged)
                    }
                    // ilk sayfanın tamamını çekip çekemeyeceğimizi bilmiyoruz,
                    // hasOlderMessages cache varsa true kabul et
                    _hasOlderMessages.value = cached.size >= MSG_CACHE_SIZE
                } else {
                    // Cache yok → tam ilk yükleme (eski davranış)
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
                    val initial = docs.reversed().mapNotNull { it.toMessage(convId) }
                    _messages.value         = initial
                    _hasOlderMessages.value = docs.size >= MSG_PAGE
                    if (initial.isNotEmpty() && ::cacheDir.isInitialized) writeCache(convId, initial)
                }
                _loading.value = false
                markRead(convId)

            } catch (e: Exception) {
                e.printStackTrace()
                _loading.value = false
            }

            // ── 3. Sadece yeni gelen mesajlar için listener ────────────────────
            val afterTs = newestMsgTs ?: Timestamp.now()
            msgListener = firestore.collection("convMessages").document(convId)
                .collection("msgs")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .whereGreaterThan("createdAt", afterTs)
                .addSnapshotListener { snap, _ ->
                    if (snap == null || snap.isEmpty) return@addSnapshotListener
                    val newMsgs = snap.documents.mapNotNull { it.toMessage(convId) }
                    if (newMsgs.isEmpty()) return@addSnapshotListener
                    val merged = mergeMessages(_messages.value, newMsgs)
                    _messages.value = merged
                    newestMsgTs = snap.documents.last().getTimestamp("createdAt") ?: newestMsgTs
                    markRead(convId)
                    // Cache'i arka planda güncelle
                    if (::cacheDir.isInitialized) {
                        viewModelScope.launch { writeCache(convId, merged) }
                    }
                }
        }
    }

    /** İki listeyi birleştir — id çakışması olmadan, sıraya göre */
    private fun mergeMessages(existing: List<Message>, incoming: List<Message>): List<Message> {
        val existingIds = existing.map { it.id }.toSet()
        val toAdd = incoming.filter { it.id !in existingIds }
        return (existing + toAdd).sortedWith(compareBy(
            { it.createdAt?.seconds ?: 0L },
            { it.createdAt?.nanoseconds ?: 0 },
        ))
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
        val likedByUids = (d["likedByUids"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        return Message(
            id             = id,
            conversationId = convId,
            senderId       = d["senderUid"]     as? String ?: "",
            text           = d["text"]          as? String ?: "",
            imageUrl       = d["image_url"]     as? String ?: "",
            audioUrl       = d["audio_url"]     as? String ?: "",
            createdAt      = d["createdAt"]     as? Timestamp,
            read           = d["read"]          as? Boolean ?: false,
            readAt         = d["readAt"]        as? Timestamp,
            deleted        = d["deleted"]       as? Boolean ?: false,
            edited         = d["edited"]        as? Boolean ?: false,
            replyToId      = d["reply_to_id"]   as? String ?: "",
            replyToText    = d["reply_to_text"] as? String ?: "",
            replyToName    = d["reply_to_name"] as? String ?: "",
            mentions       = (d["mentions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            likesCount     = (d["likesCount"] as? Long)?.toInt() ?: likedByUids.size,
            isLikedByMe    = myUid.isNotBlank() && myUid in likedByUids,
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
        mentions   : List<String> = emptyList(),
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
                if (mentions.isNotEmpty()) {
                    msgData["mentions"] = mentions
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

        // Optimistic UI güncellemesi — Firestore yanıtını beklemeden anında yansıt.
        val wasLiked = msg.isLikedByMe
        _messages.value = _messages.value.map {
            if (it.id == msg.id) it.copy(
                isLikedByMe = !wasLiked,
                likesCount  = (it.likesCount + if (!wasLiked) 1 else -1).coerceAtLeast(0),
            ) else it
        }

        viewModelScope.launch {
            try {
                val msgRef = firestore.collection("convMessages")
                    .document(msg.conversationId)
                    .collection("msgs").document(msg.id)

                val likeRef = msgRef.collection("likes").document(uid)

                if (wasLiked) {
                    likeRef.delete().await()
                    msgRef.update(
                        "likedByUids", FieldValue.arrayRemove(uid),
                        "likesCount",  FieldValue.increment(-1),
                    ).await()
                } else {
                    likeRef.set(mapOf("uid" to uid, "ts" to FieldValue.serverTimestamp())).await()
                    msgRef.update(
                        "likedByUids", FieldValue.arrayUnion(uid),
                        "likesCount",  FieldValue.increment(1),
                    ).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Firestore yazması başarısız oldu — optimistic değişikliği geri al.
                _messages.value = _messages.value.map {
                    if (it.id == msg.id) it.copy(
                        isLikedByMe = wasLiked,
                        likesCount  = (it.likesCount + if (wasLiked) 1 else -1).coerceAtLeast(0),
                    ) else it
                }
            }
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
    // ÇÖZÜLDÜ (Faz 2): Eskiden sadece conversations/{id}.unread_$uid = 0
    // yazılıyordu; mesajın kendi "read" alanı hiç güncellenmiyordu, bu yüzden
    // karşı tarafta mavi tik / görülme saati asla çalışmıyordu.
    // Artık bu konuşmada KARŞI TARAFIN gönderdiği ve henüz read=false olan
    // mesajlar (sadece o an ekranda yüklü olanlar, tüm geçmiş taranmıyor)
    // batch ile read=true + readAt=serverTimestamp() yapılıyor.
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

            // Sadece karşı tarafın gönderdiği + henüz okunmamış + o an listede
            // yüklü olan mesajları işaretle — gereksiz tüm-koleksiyon taraması yok.
            val unreadFromOther = _messages.value.filter {
                it.senderId != uid && it.senderId.isNotBlank() && !it.read
            }
            if (unreadFromOther.isEmpty()) return@launch

            try {
                val batch = firestore.batch()
                val now   = Timestamp.now()
                unreadFromOther.forEach { msg ->
                    val ref = firestore.collection("convMessages").document(convId)
                        .collection("msgs").document(msg.id)
                    batch.update(ref, mapOf(
                        "read"   to true,
                        "readAt" to FieldValue.serverTimestamp(),
                    ))
                }
                batch.commit().await()

                // Local state + cache'i de anında güncelle (listener zaten
                // aynı veriyi tekrar getirecek ama UI'da gecikme olmasın diye).
                val updatedIds = unreadFromOther.map { it.id }.toSet()
                val merged = _messages.value.map {
                    if (it.id in updatedIds) it.copy(read = true, readAt = now) else it
                }
                _messages.value = merged
                if (::cacheDir.isInitialized) writeCache(convId, merged)
            } catch (e: Exception) { e.printStackTrace() }
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

    /**
     * Sohbet ekranından çıkınca çağır — msgListener kapatılır.
     * Ekran tekrar açılınca listenMessages() yeni listener başlatır.
     */
    fun stopMsgListener() {
        msgListener?.remove()
        msgListener = null
        currentConvId = ""
        oldestMsgDoc  = null
        newestMsgTs   = null
    }

    override fun onCleared() {
        super.onCleared()
        convListener?.remove()
        msgListener?.remove()
    }
}
