package com.heftreng.app.ui.screens.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Conversation
import com.heftreng.app.data.model.Message
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.MessagesViewModel
import com.heftreng.app.viewmodel.SearchViewModel

// ════════════════════════════════════════════════════════════════
//  CONVERSATIONS LIST
// ════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : MessagesViewModel = hiltViewModel(),
) {
    val conversations by vm.conversations.collectAsState()
    val loading       by vm.loading.collectAsState()
    var searchQuery   by remember { mutableStateOf("") }
    var showNewChat   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadConversations() }

    val filtered = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            val name = it.otherUser?.displayName?.lowercase() ?: ""
            val last = it.lastMessage.lowercase()
            val q    = searchQuery.lowercase()
            name.contains(q) || last.contains(q)
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title  = { Text(if (language == "ku") "Peyam" else "Mesajlar", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    IconButton(onClick = { showNewChat = true }) {
                        Icon(Icons.Default.EditNote, "Yeni Mesaj", tint = Primary)
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Arama
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text(if (language == "ku") "Lêbigere..." else "Ara...", color = Muted, fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                trailingIcon  = if (searchQuery.isNotBlank()) ({
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                    }
                }) else null,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape         = RoundedCornerShape(14.dp),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = Primary,
                    unfocusedBorderColor    = Divider,
                    focusedTextColor        = OnBackground,
                    unfocusedTextColor      = OnBackground,
                    unfocusedContainerColor = SurfaceVar,
                    focusedContainerColor   = SurfaceVar,
                ),
            )

            when {
                loading && conversations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(32.dp))
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "Sonuç bulunamadı"
                                else if (language == "ku") "Peyam tune" else "Henüz mesajın yok",
                                color = Muted,
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { showNewChat = true }) {
                                    Text("Yeni konuşma başlat", color = Primary)
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        items(filtered, key = { it.id }) { conv ->
                            ConvItem(conv) { navController.navigate(Screen.MessageDetail.go(conv.id)) }
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            onDismiss = { showNewChat = false },
            onSelect  = { uid, _, _ ->
                showNewChat = false
                vm.startOrOpenConversation(uid) { convId ->
                    navController.navigate(Screen.MessageDetail.go(convId))
                }
            },
        )
    }
}

// ── Konuşma öğesi ─────────────────────────────────────────────
@Composable
fun ConvItem(conv: Conversation, onClick: () -> Unit) {
    val other  = conv.otherUser
    val unread = conv.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (unread) Primary.copy(alpha = 0.07f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(SurfaceVar), contentAlignment = Alignment.Center) {
            if (other?.photoURL?.isNotBlank() == true) {
                AsyncImage(model = other.photoURL, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(other?.displayName?.firstOrNull()?.uppercase() ?: "?", color = Primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                other?.displayName?.ifBlank { other.email }?.ifBlank { "Kullanıcı" } ?: "Kullanıcı",
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                color      = OnBackground, fontSize = 14.sp,
                maxLines   = 1, overflow = TextOverflow.Ellipsis,
            )
            if (conv.lastMessage.isNotBlank())
                Text(
                    conv.lastMessage,
                    color      = if (unread) OnSurface else Muted,
                    fontSize   = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                )
        }
        if (unread) {
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                Text(if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Yeni konuşma sheet ────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatSheet(
    onDismiss: () -> Unit,
    onSelect : (uid: String, name: String, photo: String) -> Unit,
    searchVm : SearchViewModel = hiltViewModel(),
) {
    val results by searchVm.results.collectAsState()
    val loading by searchVm.loading.collectAsState()
    var query   by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.length >= 2) searchVm.search(query) else searchVm.search("")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Yeni Mesaj", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("İsim ara...", color = Muted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = Divider,
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                    unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                ),
            )
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(results, key = { it.uid }) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(user.uid, user.displayName, user.photoURL) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(SurfaceVar), contentAlignment = Alignment.Center) {
                                if (user.photoURL.isNotBlank()) {
                                    AsyncImage(model = user.photoURL, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Text(user.displayName.firstOrNull()?.uppercase() ?: "?", color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName.ifBlank { "Kullanıcı" }, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (user.email.isNotBlank()) Text(user.email, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  MESSAGE DETAIL
// ════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    convId       : String,
    navController: NavController,
    language     : String = "tr",
    vm           : MessagesViewModel = hiltViewModel(),
) {
    val messages      by vm.messages.collectAsState()
    val otherUser     by vm.otherUser.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val listState     = rememberLazyListState()

    var inputText     by remember { mutableStateOf("") }
    var replyTo       by remember { mutableStateOf<Message?>(null) }
    var editMsg       by remember { mutableStateOf<Message?>(null) }
    var ctxMsg        by remember { mutableStateOf<Message?>(null) }  // context menu

    val otherUid = remember(conversations, convId) {
        conversations.firstOrNull { it.id == convId }
            ?.participantIds?.firstOrNull { it != vm.uid } ?: ""
    }

    LaunchedEffect(convId) {
        if (conversations.isEmpty()) vm.loadConversations()
        vm.loadMessages(convId)
        vm.loadOtherUser(convId)
    }
    LaunchedEffect(conversations) {
        if (conversations.isNotEmpty() && otherUser == null) vm.loadOtherUser(convId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    // Düzenleme modunda input'u doldur
    LaunchedEffect(editMsg) {
        inputText = editMsg?.text ?: ""
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { }) {
                        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(SurfaceVar), contentAlignment = Alignment.Center) {
                            if (otherUser?.photoURL?.isNotBlank() == true) {
                                AsyncImage(model = otherUser?.photoURL, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text(otherUser?.displayName?.firstOrNull()?.uppercase() ?: "…", color = Primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(otherUser?.displayName?.ifBlank { otherUser?.email ?: "…" } ?: "…", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            Column {
                // Yanıt çubuğu
                AnimatedVisibility(visible = replyTo != null) {
                    Surface(color = SurfaceVar) {
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(36.dp).background(Primary, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(replyTo?.replyToName?.ifBlank { "Yanıt" } ?: "Yanıt", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(replyTo?.text?.take(50) ?: "", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { replyTo = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                // Düzenleme çubuğu
                AnimatedVisibility(visible = editMsg != null) {
                    Surface(color = Primary.copy(alpha = 0.1f)) {
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mesaj düzenleniyor", color = Primary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editMsg = null; inputText = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                // Input alanı
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value         = inputText,
                            onValueChange = { inputText = it },
                            placeholder   = { Text(if (language == "ku") "Peyamê binivîse..." else "Mesaj yaz...", color = Muted, fontSize = 13.sp) },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(24.dp),
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                handleSend(vm, convId, otherUid, inputText, replyTo, editMsg,
                                    onDone = { inputText = ""; replyTo = null; editMsg = null })
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Primary,
                                unfocusedBorderColor    = Divider,
                                focusedTextColor        = OnBackground,
                                unfocusedTextColor      = OnBackground,
                                unfocusedContainerColor = SurfaceVar,
                                focusedContainerColor   = SurfaceVar,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick  = {
                                handleSend(vm, convId, otherUid, inputText, replyTo, editMsg,
                                    onDone = { inputText = ""; replyTo = null; editMsg = null })
                            },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (inputText.isNotBlank()) Primary else SurfaceVar),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Gönder",
                                tint     = if (inputText.isNotBlank()) Color.Black else Muted,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (language == "ku") "Peyam tune" else "Henüz mesaj yok", color = Muted)
                    Text("İlk mesajı gönder!", color = Primary, fontSize = 12.sp)
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
                    MsgBubble(
                        msg      = msg,
                        isMine   = msg.senderId == vm.uid,
                        myUid    = vm.uid,
                        onLike   = { vm.toggleLike(msg) },
                        onReply  = { replyTo = msg },
                        onDelete = { vm.deleteMessage(msg) },
                        onEdit   = { editMsg = msg },
                        onLongPress = { ctxMsg = msg },
                    )
                }
            }
        }
    }

    // Context menu dialog
    ctxMsg?.let { msg ->
        MsgContextMenu(
            msg    = msg,
            isMine = msg.senderId == vm.uid,
            onDismiss = { ctxMsg = null },
            onReply  = { replyTo = msg; ctxMsg = null },
            onLike   = { vm.toggleLike(msg); ctxMsg = null },
            onEdit   = { editMsg = msg; ctxMsg = null },
            onDelete = { vm.deleteMessage(msg); ctxMsg = null },
        )
    }
}

// ── Gönder/Düzenle logic ─────────────────────────────────────
private fun handleSend(
    vm      : MessagesViewModel,
    convId  : String,
    toUid   : String,
    text    : String,
    replyTo : Message?,
    editMsg : Message?,
    onDone  : () -> Unit,
) {
    if (text.isBlank()) return
    if (editMsg != null) {
        vm.editMessage(editMsg, text.trim())
    } else if (toUid.isNotEmpty()) {
        vm.sendMessage(
            convId      = convId,
            toUid       = toUid,
            text        = text.trim(),
            replyToId   = replyTo?.id ?: "",
            replyToText = replyTo?.text ?: "",
            replyToName = replyTo?.senderId?.take(8) ?: "",
        )
    }
    onDone()
}

// ── Mesaj balonu ──────────────────────────────────────────────
@Composable
fun MsgBubble(
    msg        : Message,
    isMine     : Boolean,
    myUid      : String,
    onLike     : () -> Unit,
    onReply    : () -> Unit,
    onDelete   : () -> Unit,
    onEdit     : () -> Unit,
    onLongPress: () -> Unit,
) {
    if (msg.text.isBlank() && msg.imageUrl.isBlank()) return
    val iLiked = myUid in msg.likedBy

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        // Yanıt önizlemesi
        if (msg.replyToId.isNotBlank() && msg.replyToText.isNotBlank()) {
            Surface(
                shape  = RoundedCornerShape(8.dp),
                color  = Primary.copy(alpha = 0.12f),
                modifier = Modifier.widthIn(max = 260.dp).padding(bottom = 2.dp),
            ) {
                Row(modifier = Modifier.padding(6.dp)) {
                    Box(modifier = Modifier.width(2.dp).height(28.dp).background(Primary, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        if (msg.replyToName.isNotBlank())
                            Text(msg.replyToName, color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(msg.replyToText.take(50), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Balon
        Surface(
            shape = RoundedCornerShape(
                topStart    = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd   = if (isMine) 4.dp  else 16.dp,
            ),
            color    = if (isMine) Primary else SurfaceVar,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.text.isNotBlank()) {
                    Text(
                        msg.text,
                        color    = if (isMine) Color.Black else OnBackground,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Meta — saat + okundu + düzenlendi + beğeni
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier              = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (msg.edited)
                Text("düzenlendi", color = Muted, fontSize = 9.sp, fontStyle = FontStyle.Italic)
            val time = remember(msg.createdAt) {
                msg.createdAt.toLongOrNull()?.let {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                } ?: ""
            }
            if (time.isNotBlank()) Text(time, color = Muted, fontSize = 10.sp)
            if (isMine) {
                Icon(
                    if (msg.read) Icons.Default.DoneAll else Icons.Default.Done,
                    null,
                    tint     = if (msg.read) Primary else Muted,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        // Beğeni badge
        if (msg.likedBy.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = if (iLiked) Primary.copy(alpha = 0.15f) else SurfaceVar,
                modifier = Modifier.clickable { onLike() }.padding(top = 2.dp),
            ) {
                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (iLiked) "♥" else "♡", color = if (iLiked) Primary else Muted, fontSize = 12.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(msg.likedBy.size.toString(), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Context menu dialog ───────────────────────────────────────
@Composable
fun MsgContextMenu(
    msg      : Message,
    isMine   : Boolean,
    onDismiss: () -> Unit,
    onReply  : () -> Unit,
    onLike   : () -> Unit,
    onEdit   : () -> Unit,
    onDelete : () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(8.dp)) {
                listOfNotNull(
                    Triple(Icons.AutoMirrored.Filled.Reply, "Yanıtla",  onReply),
                    Triple(Icons.Default.FavoriteBorder,   "Beğen",    onLike),
                    if (isMine) Triple(Icons.Default.Edit, "Düzenle",  onEdit)  else null,
                    if (isMine) Triple(Icons.Default.Delete,"Sil",     onDelete) else null,
                ).forEach { (icon, label, action) ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { action(); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            icon, null,
                            tint     = if (label == "Sil") Color(0xFFEF4444) else Primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            label,
                            color    = if (label == "Sil") Color(0xFFEF4444) else OnBackground,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
