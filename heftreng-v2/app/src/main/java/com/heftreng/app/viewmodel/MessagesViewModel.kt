package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.Conversation
import com.heftreng.app.data.model.Message
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase : SupabaseClient,
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

    // ── Konuşma listesi ───────────────────────────────────
    // Supabase tablo: conversations(id, participant_a, participant_b, last_msg, updated_at)
    fun loadConversations() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = supabase.postgrest["conversations"].select {
                    filter {
                        or {
                            eq("participant_a", uid)
                            eq("participant_b", uid)
                        }
                    }
                }
                val convList = result.decodeList<JsonObject>().mapNotNull { obj ->
                    val pa = obj["participant_a"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val pb = obj["participant_b"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val otherUid = if (pa == uid) pb else pa

                    val userDoc = try {
                        firestore.collection("users").document(otherUid).get().await()
                    } catch (_: Exception) { null }
                    val d = userDoc?.data
                    val other = if (d != null) User(
                        uid         = otherUid,
                        displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email       = d["email"]       as? String ?: "",
                        photoURL    = d["photoURL"]    as? String ?: "",
                    ) else null

                    Conversation(
                        id             = obj["id"]?.jsonPrimitive?.content ?: "",
                        participantIds = listOf(pa, pb),
                        lastMessage    = obj["last_msg"]?.jsonPrimitive?.content ?: "",
                        lastMessageAt  = obj["updated_at"]?.jsonPrimitive?.content ?: "",
                        otherUser      = other,
                        unreadCount    = obj["unread_count"]?.jsonPrimitive?.int ?: 0,
                    )
                }
                _conversations.value = convList.sortedByDescending { it.lastMessageAt }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally { _loading.value = false }
        }
    }

    // ── Mesaj listesi ─────────────────────────────────────
    // Supabase tablo: messages(id, conv_id, from_uid, to_uid, msg_text, created_at, read_at)
    fun loadMessages(convId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = supabase.postgrest["messages"].select {
                    filter { eq("conv_id", convId) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                _messages.value = result.decodeList<JsonObject>().map { obj ->
                    Message(
                        id             = obj["id"]?.jsonPrimitive?.content ?: "",
                        conversationId = obj["conv_id"]?.jsonPrimitive?.content ?: "",
                        senderId       = obj["from_uid"]?.jsonPrimitive?.content ?: "",
                        text           = obj["msg_text"]?.jsonPrimitive?.content ?: "",
                        createdAt      = obj["created_at"]?.jsonPrimitive?.content ?: "",
                        read           = obj["read_at"]?.jsonPrimitive?.contentOrNull != null,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally { _loading.value = false }
        }
    }

    // ── Mesaj gönder ──────────────────────────────────────
    fun sendMessage(convId: String, toUid: String, text: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest["messages"].insert(
                    buildJsonObject {
                        put("conv_id",  convId)
                        put("from_uid", uid)
                        put("to_uid",   toUid)
                        put("msg_text", text)
                    }
                )
                supabase.postgrest["conversations"].update(
                    buildJsonObject { put("last_msg", text) }
                ) { filter { eq("id", convId) } }

                loadMessages(convId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Konuşma başlat veya mevcut aç ────────────────────
    // participant_a = minOf(uid, otherUid), participant_b = maxOf — sıra sabit tutulur
    fun startOrOpenConversation(otherUid: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val pa = minOf(uid, otherUid)
                val pb = maxOf(uid, otherUid)

                val existing = supabase.postgrest["conversations"].select {
                    filter {
                        eq("participant_a", pa)
                        eq("participant_b", pb)
                    }
                }.decodeList<JsonObject>().firstOrNull()

                val convId = if (existing != null) {
                    existing["id"]?.jsonPrimitive?.content ?: ""
                } else {
                    supabase.postgrest["conversations"].insert(
                        buildJsonObject {
                            put("participant_a", pa)
                            put("participant_b", pb)
                            put("last_msg", "")
                        }
                    ).decodeList<JsonObject>().firstOrNull()
                        ?.get("id")?.jsonPrimitive?.content ?: ""
                }

                if (convId.isNotEmpty()) onReady(convId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Realtime mesaj aboneliği ──────────────────────────
    fun subscribeToMessages(convId: String) {
        viewModelScope.launch {
            try {
                val channel = supabase.channel("msgs:$convId")
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { action ->
                    val obj = action.record
                    if (obj["conv_id"]?.jsonPrimitive?.content != convId) return@onEach
                    val newMsg = Message(
                        id             = obj["id"]?.jsonPrimitive?.content ?: "",
                        conversationId = convId,
                        senderId       = obj["from_uid"]?.jsonPrimitive?.content ?: "",
                        text           = obj["msg_text"]?.jsonPrimitive?.content ?: "",
                        createdAt      = obj["created_at"]?.jsonPrimitive?.content ?: "",
                    )
                    // Gelen mesajı listeye ekle (tekrar yükleme gerekmez)
                    if (_messages.value.none { it.id == newMsg.id }) {
                        _messages.value = _messages.value + newMsg
                    }
                }.launchIn(viewModelScope)
                channel.subscribe()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Karşı kullanıcıyı yükle ───────────────────────────
    fun loadOtherUser(convId: String) {
        viewModelScope.launch {
            try {
                val conv     = _conversations.value.firstOrNull { it.id == convId }
                val otherUid = conv?.participantIds?.firstOrNull { it != uid } ?: return@launch
                val doc = firestore.collection("users").document(otherUid).get().await()
                val d   = doc.data ?: return@launch
                _otherUser.value = User(
                    uid         = otherUid,
                    displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                    photoURL    = d["photoURL"]    as? String ?: "",
                    email       = d["email"]       as? String ?: "",
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
