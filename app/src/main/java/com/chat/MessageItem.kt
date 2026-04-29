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
