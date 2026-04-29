#!/bin/bash

echo "🔥 Chat sistemi kuruluyor..."

BASE=app/src/main/java

mkdir -p $BASE/com/chat

# Message Model
cat <<EOL > $BASE/com/chat/Message.kt
package com.chat

data class Message(
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val seen: Boolean = false
)
EOL

# Chat Screen
cat <<EOL > $BASE/com/chat/ChatScreen.kt
package com.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ChatScreen(receiverId: String) {

    val currentUser = FirebaseAuth.getInstance().currentUser!!.uid
    val db = FirebaseFirestore.getInstance()

    val conversationId =
        if (currentUser < receiverId) "\${currentUser}_\$receiverId"
        else "\${receiverId}_\$currentUser"

    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }

    LaunchedEffect(Unit) {
        db.collection("messages")
            .document(conversationId)
            .collection("chats")
            .orderBy("timestamp")
            .addSnapshotListener { value, _ ->
                messages.clear()
                value?.documents?.forEach {
                    it.toObject(Message::class.java)?.let { msg ->
                        messages.add(msg)
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Text("Sohbet", fontSize = 20.sp, modifier = Modifier.padding(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) {
                MessageItem(it, currentUser)
            }
        }

        Row(modifier = Modifier.padding(8.dp)) {
            TextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f)
            )

            Button(onClick = {
                if (message.isNotBlank()) {

                    val msg = Message(currentUser, message)

                    db.collection("messages")
                        .document(conversationId)
                        .collection("chats")
                        .add(msg)

                    db.collection("messages")
                        .document(conversationId)
                        .set(
                            mapOf(
                                "participants" to listOf(currentUser, receiverId),
                                "lastMessage" to message,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )

                    message = ""
                }
            }) {
                Text("Gönder")
            }
        }
    }
}
EOL

# Message Item
cat <<EOL > $BASE/com/chat/MessageItem.kt
package com.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MessageItem(msg: Message, currentUser: String) {

    val isMe = msg.senderId == currentUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(
                    if (isMe) Color(0xFFDCF8C6) else Color.LightGray
                )
                .padding(12.dp)
        ) {
            Text(text = msg.text)
        }
    }
}
EOL

echo "✅ Chat sistemi eklendi!"


