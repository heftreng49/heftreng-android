package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.Conversation
import com.heftreng.app.data.model.Message
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase: io.github.jan.supabase.SupabaseClient,
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    fun loadConversations() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = supabase.postgrest["conversations"]
                    .select {
                        filter { contains("participant_ids", listOf(uid)) }
                    }

                val convList = result.decodeList<JsonObject>().map { obj ->
                    val participantIds = obj["participant_ids"]
                        ?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val otherUid = participantIds.firstOrNull { it != uid } ?: ""
                    val otherUser = if (otherUid.isNotEmpty()) {
                        val doc = firestore.collection("users").document(otherUid).get().await()
                        doc.toObject(User::class.java)
                    } else null

                    Conversation(
                        id                = obj["id"]?.jsonPrimitive?.content ?: "",
                        participantIds    = participantIds,
                        lastMessage       = obj["last_message"]?.jsonPrimitive?.content ?: "",
                        lastMessageAt     = obj["last_message_at"]?.jsonPrimitive?.content ?: "",
                        otherUser         = otherUser,
                        unreadCount       = obj["unread_count"]?.jsonPrimitive?.int ?: 0,
                    )
                }
                _conversations.value = convList.sortedByDescending { it.lastMessageAt }
            } catch (_: Exception) {}
            finally { _loading.value = false }
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = supabase.postgrest["messages"]
                    .select {
                        filter { eq("conversation_id", conversationId) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    }

                _messages.value = result.decodeList<JsonObject>().map { obj ->
                    Message(
                        id             = obj["id"]?.jsonPrimitive?.content ?: "",
                        conversationId = obj["conversation_id"]?.jsonPrimitive?.content ?: "",
                        senderId       = obj["sender_id"]?.jsonPrimitive?.content ?: "",
                        text           = obj["text"]?.jsonPrimitive?.content ?: "",
                        createdAt      = obj["created_at"]?.jsonPrimitive?.content ?: "",
                        read           = obj["read"]?.jsonPrimitive?.boolean ?: false,
                    )
                }
            } catch (_: Exception) {}
            finally { _loading.value = false }
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest["messages"].insert(
                    buildJsonObject {
                        put("conversation_id", conversationId)
                        put("sender_id", uid)
                        put("text", text)
                        put("read", false)
                    }
                )
                // last_message güncelle
                supabase.postgrest["conversations"].update(
                    buildJsonObject {
                        put("last_message", text)
                        put("last_message_at", kotlinx.datetime.Clock.System.now().toString())
                    }
                ) { filter { eq("id", conversationId) } }

                loadMessages(conversationId)
            } catch (_: Exception) {}
        }
    }

    fun subscribeToMessages(conversationId: String) {
        viewModelScope.launch {
            try {
                val channel = supabase.realtime.channel("messages:$conversationId")
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    filter = "conversation_id=eq.$conversationId"
                }.onEach { action ->
                    val obj = action.record
                    val newMsg = Message(
                        id             = obj["id"]?.jsonPrimitive?.content ?: "",
                        conversationId = conversationId,
                        senderId       = obj["sender_id"]?.jsonPrimitive?.content ?: "",
                        text           = obj["text"]?.jsonPrimitive?.content ?: "",
                        createdAt      = obj["created_at"]?.jsonPrimitive?.content ?: "",
                    )
                    _messages.value = _messages.value + newMsg
                }.launchIn(viewModelScope)
                channel.subscribe()
            } catch (_: Exception) {}
        }
    }
}
