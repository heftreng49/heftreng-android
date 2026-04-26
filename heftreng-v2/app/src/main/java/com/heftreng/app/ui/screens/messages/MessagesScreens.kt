package com.heftreng.app.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Conversation
import com.heftreng.app.data.model.Message
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.MessagesViewModel

// ════════════════════════════════════════════════════════════
//  CONVERSATIONS LIST
// ════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    navController: NavController,
    vm: MessagesViewModel = hiltViewModel(),
) {
    val conversations by vm.conversations.collectAsState()
    val loading       by vm.loading.collectAsState()

    LaunchedEffect(Unit) { vm.loadConversations() }

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title  = { Text("Peyam", fontWeight = FontWeight.SemiBold, color = onBg()) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        }
    ) { padding ->
        when {
            loading && conversations.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent())
                }
            }
            conversations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = muted(), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Peyam tune / Henüz mesaj yok", color = muted())
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        ConvItem(conv) {
                            navController.navigate(Screen.MessageDetail.go(conv.id))
                        }
                        HorizontalDivider(color = divider(), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConvItem(conv: Conversation, onClick: () -> Unit) {
    val other = conv.otherUser
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(bg())
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(50.dp).clip(CircleShape).background(surfVar()),
            contentAlignment = Alignment.Center,
        ) {
            if (other?.photoURL?.isNotBlank() == true) {
                AsyncImage(
                    model = other.photoURL, contentDescription = other.displayName,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    other?.displayName?.firstOrNull()?.uppercase() ?: "H",
                    color = accent(), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                other?.displayName?.ifEmpty { other.email }?.ifEmpty { "Kullanıcı" } ?: "Kullanıcı",
                fontWeight = FontWeight.SemiBold, color = onBg(), fontSize = 14.sp,
            )
            if (conv.lastMessage.isNotBlank())
                Text(conv.lastMessage, color = muted(), fontSize = 13.sp, maxLines = 1)
        }

        if (conv.unreadCount > 0) {
            Box(
                modifier         = Modifier.size(20.dp).clip(CircleShape).background(accent()),
                contentAlignment = Alignment.Center,
            ) {
                Text(conv.unreadCount.toString(), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  MESSAGE DETAIL — boş ekran sorunu çözüldü
// ════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    convId       : String,
    navController: NavController,
    vm           : MessagesViewModel = hiltViewModel(),
) {
    val messages      by vm.messages.collectAsState()
    val otherUser     by vm.otherUser.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val listState     = rememberLazyListState()
    var inputText     by remember { mutableStateOf("") }

    // otherUid — konuşmadan türetilir
    val otherUid = remember(conversations, convId) {
        conversations.firstOrNull { it.id == convId }
            ?.participantIds?.firstOrNull { it != vm.uid } ?: ""
    }

    LaunchedEffect(convId) {
        // Konuşma listesi yüklü değilse önce onu yükle
        if (conversations.isEmpty()) vm.loadConversations()
        vm.loadMessages(convId)
        vm.subscribeToMessages(convId)
        vm.loadOtherUser(convId)
    }

    // Konuşmalar yüklendikten sonra karşı kullanıcıyı tekrar dene
    LaunchedEffect(conversations) {
        if (conversations.isNotEmpty() && otherUser == null) {
            vm.loadOtherUser(convId)
        }
    }

    // Yeni mesajda en alta kaydır
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(34.dp).clip(CircleShape).background(surfVar()),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (otherUser?.photoURL?.isNotBlank() == true) {
                                AsyncImage(
                                    model = otherUser?.photoURL, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    otherUser?.displayName?.firstOrNull()?.uppercase() ?: "…",
                                    color = accent(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            otherUser?.displayName?.ifEmpty { otherUser?.email ?: "…" } ?: "…",
                            color = onBg(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = onBg())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        },
        bottomBar = {
            Surface(color = surf(), tonalElevation = 0.dp) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it },
                        placeholder   = { Text("Peyamê binivîse...", color = muted()) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(24.dp),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = accent(),
                            unfocusedBorderColor    = divider(),
                            focusedTextColor        = onBg(),
                            unfocusedTextColor      = onBg(),
                            unfocusedContainerColor = surfVar(),
                            focusedContainerColor   = surfVar(),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = {
                            if (inputText.isNotBlank() && otherUid.isNotEmpty()) {
                                vm.sendMessage(convId, otherUid, inputText.trim())
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(accent()),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, "Gönder",
                            tint     = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = muted(), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Peyam tune / Henüz mesaj yok", color = muted(), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("İlk mesajı gönder!", color = accent(), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                state               = listState,
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MsgBubble(msg, isMine = msg.senderId == vm.uid)
                }
            }
        }
    }
}

@Composable
fun MsgBubble(msg: Message, isMine: Boolean) {
    if (msg.text.isEmpty()) return
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart    = 16.dp,
                topEnd      = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd   = if (isMine) 4.dp  else 16.dp,
            ),
            color    = if (isMine) accent() else surfVar(),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                msg.text,
                color    = if (isMine) Color.Black else onBg(),
                fontSize = AppFontSize.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
