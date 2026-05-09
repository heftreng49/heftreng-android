package com.heftreng.app.ui.screens.messages

import androidx.compose.material3.AlertDialog

import androidx.compose.foundation.layout.imeNestedScroll

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Message
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.MessagesViewModel
import com.heftreng.app.viewmodel.PresenceViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.zIndex

// ── Konuşma Listesi ─────────────────────────────────────────────────────────
// Tema: .msgp-wrap, .msgp-hd, .msgp-conv-item, .msgp-conv-av, .msgp-unread-dot

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : MessagesViewModel = hiltViewModel(),
) {
    val conversations by vm.conversations.collectAsState()
    val loading       by vm.loading.collectAsState()
    var searchQuery   by remember { mutableStateOf("") }
    var showSearch    by remember { mutableStateOf(false) }

    val uid = vm.uid
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) vm.listenConversations()
    }

    val filtered = if (searchQuery.isBlank()) conversations
    else conversations.filter {
        it.otherUser?.displayName?.contains(searchQuery, ignoreCase = true) == true ||
        it.otherUser?.email?.contains(searchQuery, ignoreCase = true) == true ||
        it.lastMessage.contains(searchQuery, ignoreCase = true)
    }

    // ── Kayıt Overlay ────────────────────────────────────────────────────────
    if (isRecording || showAudioPreview) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .zIndex(10f),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape          = RoundedCornerShape(24.dp),
                colors         = CardDefaults.cardColors(containerColor = HeftSurface),
                elevation      = CardDefaults.cardElevation(8.dp),
            ) {
                Column(
                    modifier              = Modifier.padding(28.dp),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                    verticalArrangement   = Arrangement.spacedBy(20.dp),
                ) {
                    if (isRecording) {
                        // ── Kayıt ekranı ──────────────────────────────────
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue  = 1.25f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "pulseScale",
                        )
                        Text(
                            "Kayıt yapılıyor",
                            color      = OnBackground,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size((72 * pulseScale).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Mic, null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        Text(
                            "%02d:%02d".format(recordSecs / 60, recordSecs % 60),
                            color      = Color(0xFFEF4444),
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Durdurmak için butona bas",
                            color    = Muted,
                            fontSize = 12.sp,
                        )
                        // Durdur butonu
                        Button(
                            onClick = {
                                try {
                                    recorder?.stop()
                                    recorder?.release()
                                    recorder = null
                                } catch (e: Exception) { e.printStackTrace() }
                                isRecording = false
                                if ((audioFile?.length() ?: 0) > 0) {
                                    showAudioPreview = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape  = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Durdur")
                        }
                        // İptal
                        TextButton(onClick = {
                            recorder?.stop(); recorder?.release(); recorder = null
                            isRecording = false
                            audioFile?.delete(); audioFile = null
                        }) {
                            Text("İptal", color = Muted)
                        }

                    } else if (showAudioPreview) {
                        // ── Önizleme ekranı ───────────────────────────────
                        Text(
                            "Sesli Mesaj",
                            color      = OnBackground,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Süre: %02d:%02d".format(recordSecs / 60, recordSecs % 60),
                            color    = Muted,
                            fontSize = 13.sp,
                        )
                        // Dinle/Durdur
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment      = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPreviewPlaying) {
                                        previewPlayer?.pause()
                                        isPreviewPlaying = false
                                    } else {
                                        if (previewPlayer == null) {
                                            try {
                                                val mp = android.media.MediaPlayer().apply {
                                                    setDataSource(audioFile!!.absolutePath)
                                                    setOnPreparedListener { start(); isPreviewPlaying = true }
                                                    setOnCompletionListener { isPreviewPlaying = false; reset(); previewPlayer = null }
                                                    prepareAsync()
                                                }
                                                previewPlayer = mp
                                            } catch (e: Exception) { e.printStackTrace() }
                                        } else {
                                            previewPlayer?.start()
                                            isPreviewPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Primary),
                            ) {
                                Icon(
                                    if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Sil
                            OutlinedButton(
                                onClick = {
                                    previewPlayer?.release(); previewPlayer = null
                                    isPreviewPlaying = false
                                    audioFile?.delete(); audioFile = null
                                    showAudioPreview = false
                                    recordSecs = 0
                                },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(50),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sil")
                            }
                            // Gönder
                            Button(
                                onClick = {
                                    previewPlayer?.release(); previewPlayer = null
                                    isPreviewPlaying = false
                                    showAudioPreview = false
                                    recordSecs = 0
                                    audioFile?.let { f ->
                                        if (f.exists() && f.length() > 0) {
                                            vm.uploadAudioAndSend(convId, otherUid, f)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape    = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Gönder")
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            // Tema: .msgp-hd stili
            TopAppBar(
                title = {
                    if (showSearch) {
                        // Tema: .msgp-search-bar, .msgp-search-inp stili
                        OutlinedTextField(
                            value         = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder   = { Text(if (language == "ku") "Peyaman bigere..." else "Mesajlarda ara...", color = Muted, fontSize = 13.sp) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(20.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Primary,
                                unfocusedBorderColor    = Divider,
                                focusedTextColor        = OnBackground,
                                unfocusedTextColor      = OnBackground,
                                unfocusedContainerColor = SurfaceVar,
                                focusedContainerColor   = SurfaceVar,
                            ),
                        )
                    } else {
                        Text(
                            if (language == "ku") "Peyam" else "Mesajlar",
                            fontWeight = FontWeight.ExtraBold,
                            color      = OnBackground,
                            fontSize   = 17.sp,
                        )
                    }
                },
                actions = {
                    // Arama toggle — tema: .msgp-search-bar
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Outlined.Search,
                            null, tint = if (showSearch) Primary else Muted)
                    }
                    // Yeni konuşma — tema: .msgp-new-btn
                    IconButton(onClick = { navController.navigate("search") }) {
                        Icon(Icons.Default.Create, null, tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeftSurface),
            )
        }
    ) { padding ->
        when {
            loading && conversations.isEmpty() -> {
                // Tema: .msgp-loading
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                        Text(if (language == "ku") "Tê barkirin..." else "Yükleniyor...", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            filtered.isEmpty() -> {
                // Tema: .msgp-empty stili
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Divider, modifier = Modifier.size(52.dp))
                        Text(
                            if (language == "ku") "Peyam tune" else "Henüz mesajın yok",
                            color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                        Text(
                            if (language == "ku") "Peyamek nû dest pê bike" else "Yeni bir konuşma başlat",
                            color = Muted, fontSize = 12.sp
                        )
                    }
                }
            }
            else -> {
                var convToDelete by remember { mutableStateOf<String?>(null) }

                // Sil onay dialog'u
                convToDelete?.let { cid ->
                    AlertDialog(
                        onDismissRequest = { convToDelete = null },
                        title = { Text(if (language == "ku") "Sohbet Sil" else "Sohbeti Sil", color = OnBackground) },
                        text  = { Text(if (language == "ku") "Ev sohbet bê silîn?" else "Bu sohbeti silmek istiyor musun?", color = Muted) },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.deleteConversation(cid)
                                convToDelete = null
                            }) { Text(if (language == "ku") "Jêbibe" else "Sil", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { convToDelete = null }) {
                                Text(if (language == "ku") "Betal" else "İptal", color = Muted)
                            }
                        },
                        containerColor = HeftSurface,
                    )
                }

                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        val unread = conv.unreadCount > 0
                        // Tema: .msgp-conv-item, .msgp-conv-item.unread
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (unread) Primary.copy(alpha = 0.08f) else Color.Transparent
                                )
                                .then(
                                    if (unread) Modifier.startBorder(Primary, 3.dp) else Modifier
                                )
                                .combinedClickable(
                                    onClick      = { navController.navigate(Screen.MessageDetail.go(conv.id)) },
                                    onLongClick  = { convToDelete = conv.id },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Tema: .msgp-conv-av — gradient avatar
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Primary, Accent))),
                                contentAlignment = Alignment.Center,
                            ) {
                                val photo = conv.otherUser?.photoURL
                                if (!photo.isNullOrBlank()) {
                                    AsyncImage(model = photo, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Text(
                                        conv.otherUser?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                                    )
                                }
                            }

                            // Tema: .msgp-conv-body
                            Column(modifier = Modifier.weight(1f)) {
                                // .msgp-conv-name, .msgp-conv-name.unread
                                Text(
                                    conv.otherUser?.displayName?.ifBlank { conv.otherUser?.email } ?: "Kullanıcı",
                                    fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold,
                                    color      = OnBackground,
                                    fontSize   = 14.sp,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                )
                                // .msgp-conv-last, .msgp-conv-last.unread
                                if (conv.lastMessage.isNotBlank())
                                    Text(
                                        conv.lastMessage,
                                        color    = if (unread) OnBackground else Muted,
                                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                            }

                            // Tema: .msgp-conv-meta — sağ taraf
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (conv.lastMessageAt.isNotBlank()) {
                                    // .msgp-conv-time
                                    Text(formatTime(conv.lastMessageAt), color = Muted, fontSize = 10.sp)
                                }
                                // .msgp-unread-dot
                                if (unread) {
                                    Box(
                                        modifier         = Modifier
                                            .defaultMinSize(minWidth = 19.dp)
                                            .height(19.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(Primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(conv.unreadCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ── Mesaj Detay Ekranı ────────────────────────────────────────────────────────
// Tema: .msg-chat-ov, .msg-chat-hd, .msg-chat-body, .msg-inp-bar

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MessageDetailScreen(
    convId       : String,
    navController: NavController,
    language     : String = "tr",
    vm           : MessagesViewModel  = hiltViewModel(),
    presenceVm   : PresenceViewModel  = hiltViewModel(),
) {
    val messages      by vm.messages.collectAsState()
    val otherUser     by vm.otherUser.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val listState     = rememberLazyListState()

    var inputText     by remember { mutableStateOf("") }
    var replyTo       by remember { mutableStateOf<Message?>(null) }
    var editMsg       by remember { mutableStateOf<Message?>(null) }
    var ctxMsg        by remember { mutableStateOf<Message?>(null) }
    var ctxOffset     by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var isRecording     by remember { mutableStateOf(false) }
    var showAudioPreview by remember { mutableStateOf(false) }
    val uploading        by vm.uploading.collectAsState()
    val context          = LocalContext.current
    var recorder         by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile        by remember { mutableStateOf<File?>(null) }
    var recordSecs       by remember { mutableStateOf(0) }
    var previewPlayer    by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Kayıt sayacı
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSecs = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordSecs++
                if (recordSecs >= 120) {
                    // Max 2 dk — otomatik durdur
                    recorder?.stop()
                    recorder?.release()
                    recorder = null
                    isRecording = false
                    showAudioPreview = true
                }
            }
        }
    }

    // Önizleme player temizle
    DisposableEffect(Unit) {
        onDispose {
            previewPlayer?.release()
            previewPlayer = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedImage = uri }

    // Runtime izin launcher — ses kaydı
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* izin sonucu — kullanıcı tekrar basınca kayıt başlar */ }

    // Runtime izin launcher — galeri
    val imagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) imagePicker.launch("image/*") }

    val otherUid = remember(conversations, convId) {
        conversations.firstOrNull { it.id == convId }
            ?.participantIds?.firstOrNull { it != vm.uid } ?: ""
    }

    LaunchedEffect(convId) {
        if (conversations.isEmpty()) vm.listenConversations()
        vm.listenMessages(convId)
        
        vm.loadOtherUser(convId)
        presenceVm.goOnline()
    }

    val otherUidForPresence = remember(otherUser) { otherUser?.uid ?: "" }
    LaunchedEffect(otherUidForPresence) {
        if (otherUidForPresence.isNotEmpty()) {
            presenceVm.listenPresence(otherUidForPresence)
            presenceVm.listenTyping(convId, otherUidForPresence)
        }
    }
    val isOtherOnline = presenceVm.isOnline(otherUser?.uid ?: "")
    val isOtherTyping = presenceVm.isTyping(otherUser?.uid ?: "")

    LaunchedEffect(conversations) {
        if (conversations.isNotEmpty() && otherUser == null) vm.loadOtherUser(convId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    // Klavye açıldığında da son mesaja scroll et
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime
        .getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible && messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(editMsg) {
        if (editMsg != null) inputText = editMsg!!.text
    }

    Scaffold(
        modifier       = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            // Tema: .msg-chat-hd
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Tema: .msg-chat-hd-av-wrap + .msg-chat-hd-online
                        Box {
                            // Tema: .msg-chat-hd-av
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Primary, Accent))),
                                contentAlignment = Alignment.Center,
                            ) {
                                val photo = otherUser?.photoURL
                                if (!photo.isNullOrBlank())
                                    AsyncImage(model = photo, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text(otherUser?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            // Online durumu — gerçek presence
                            if (isOtherOnline) {
                                Box(
                                    modifier = Modifier.size(9.dp).clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                        .border(2.dp, HeftSurface, CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }
                        Column {
                            // Tema: .msg-chat-hd-name
                            Text(
                                otherUser?.displayName?.ifBlank { otherUser?.email ?: "…" } ?: "…",
                                color      = OnBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                            )
                            // Gerçek presence + typing durumu
                            when {
                                isOtherTyping -> Text(
                                    if (language == "ku") "dinivîse..." else "yazıyor...",
                                    color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                                isOtherOnline -> Text(
                                    if (language == "ku") "serhêl" else "çevrimiçi",
                                    color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                                else -> Text(
                                    if (language == "ku") "nediyar" else "çevrimdışı",
                                    color = Muted, fontSize = 11.sp,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, null, tint = Muted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeftSurface),
            )
        },
        bottomBar = {
            Column {
                // Tema: .msg-reply-bar
                AnimatedVisibility(visible = replyTo != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(SurfaceVar)
                            .border(BorderStroke(Dp.Hairline, Divider))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // .msg-reply-bar-line
                        Box(modifier = Modifier.size(3.dp, 32.dp).clip(RoundedCornerShape(2.dp)).background(Primary))
                        Column(modifier = Modifier.weight(1f)) {
                            // .msg-reply-bar-name
                            Text(if (replyTo?.senderId == vm.uid) "Sen" else otherUser?.displayName ?: "",
                                color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            // .msg-reply-bar-txt
                            Text(replyTo?.text ?: "", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // .msg-reply-bar-close
                        IconButton(onClick = { replyTo = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Tema: .msg-edit-bar
                AnimatedVisibility(visible = editMsg != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFFFBBF24).copy(alpha = 0.08f))
                            .border(BorderStroke(Dp.Hairline, Color(0xFFFBBF24).copy(alpha = 0.25f)))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Create, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Text(if (language == "ku") "Peyamê biguherîne" else "Mesajı düzenle",
                            color = OnBackground, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editMsg = null; inputText = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Tema: .msg-inp-bar
                Surface(color = HeftSurface, tonalElevation = 0.dp) {
                    Column {
                        // Seçili resim önizleme
                        if (selectedImage != null) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                AsyncImage(
                                    model = selectedImage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                IconButton(
                                    onClick  = { selectedImage = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 9.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        // Resim seçici butonu
                        IconButton(onClick = {
                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                android.Manifest.permission.READ_MEDIA_IMAGES
                            else android.Manifest.permission.READ_EXTERNAL_STORAGE
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                imagePicker.launch("image/*")
                            } else {
                                imagePermLauncher.launch(perm)
                            }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Image, null, tint = if (selectedImage != null) Primary else Muted, modifier = Modifier.size(22.dp))
                        }
                        // Tema: .msg-inp-wrap + .msg-inp
                        OutlinedTextField(
                            value         = inputText,
                            onValueChange = { inputText = it
                                presenceVm.setTyping(convId, it.isNotEmpty())
                            },
                            placeholder   = {
                                Text(if (language == "ku") "Peyamê binivîse..." else "Mesaj yaz...",
                                    color = Muted, fontSize = 13.sp)
                            },
                            modifier  = Modifier.weight(1f),
                            shape     = RoundedCornerShape(20.dp),
                            maxLines  = 4,
                            colors    = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Primary,
                                unfocusedBorderColor    = Divider,
                                focusedTextColor        = OnBackground,
                                unfocusedTextColor      = OnBackground,
                                unfocusedContainerColor = SurfaceVar,
                                focusedContainerColor   = SurfaceVar,
                            ),
                        )
                        // Sesli mesaj butonu
                        if (inputText.isBlank() && selectedImage == null) {
                            IconButton(
                                onClick = {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                                            context, android.Manifest.permission.RECORD_AUDIO)
                                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        return@IconButton
                                    }
                                    if (!isRecording) {
                                        try {
                                            val f = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                                            audioFile = f
                                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                                            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                                            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            mr.setAudioSamplingRate(44100)
                                            mr.setAudioEncodingBitRate(128000)
                                            mr.setOutputFile(f.absolutePath)
                                            mr.prepare()
                                            mr.start()
                                            recorder = mr
                                            isRecording = true
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    null,
                                    tint     = if (isRecording) Color(0xFFEF4444) else Muted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        // Tema: .msg-send-btn — gradient, circular
                        if (inputText.isNotBlank() || selectedImage != null) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(
                                    if (uploading) Brush.linearGradient(listOf(Divider, Divider))
                                    else Brush.linearGradient(listOf(PrimaryLight, Primary))
                                )
                                .clickable(enabled = !uploading) {
                                    if (editMsg != null) {
                                        vm.editMessage(editMsg!!, inputText.trim())
                                        editMsg = null
                                    } else if (selectedImage != null) {
                                        vm.uploadImageAndSend(
                                            convId      = convId,
                                            toUid       = otherUid,
                                            uri         = selectedImage!!,
                                            replyToId   = replyTo?.id ?: "",
                                            replyToText = replyTo?.text ?: "",
                                            replyToName = if (replyTo?.senderId == vm.uid) "Sen"
                                                          else otherUser?.displayName ?: "",
                                        )
                                        selectedImage = null
                                        replyTo = null
                                    } else {
                                        vm.sendMessage(
                                            convId      = convId,
                                            toUid       = otherUid,
                                            text        = inputText.trim(),
                                            replyToId   = replyTo?.id ?: "",
                                            replyToText = replyTo?.text ?: "",
                                            replyToName = if (replyTo?.senderId == vm.uid) "Sen"
                                                          else otherUser?.displayName ?: "",
                                        )
                                        replyTo = null
                                    }
                                    inputText = ""
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uploading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                        } // end send button if
                    }
                    } // end Row
                    } // end Column
                }
            }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (language == "ku") "Peyam tune, dest bi axaftinê bike!" else "Henüz mesaj yok, konuşmayı başlat!",
                    color = Muted, fontSize = 13.sp)
            }
        } else {
            // Tema: .msg-chat-body
            LazyColumn(
                state               = listState,
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imeNestedScroll(),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                reverseLayout       = false,
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == vm.uid
                    MsgRow(
                        msg      = msg,
                        isMine   = isMine,
                        myUid    = vm.uid,
                        otherPhotoURL = otherUser?.photoURL ?: "",
                        otherName     = otherUser?.displayName ?: "",
                        language = language,
                        onReply  = { replyTo = msg; ctxMsg = null },
                        onEdit   = { editMsg = msg; ctxMsg = null },
                        onDelete = { vm.deleteMessage(msg); ctxMsg = null },
                        onLike   = { vm.toggleLike(msg) },
                        onLongPress = { offset ->
                            ctxMsg    = msg
                            ctxOffset = offset
                        },
                    )
                }
            }
        }

        // Tema: .msg-ctx-menu — uzun basma menüsü
        if (ctxMsg != null) {
            Box(modifier = Modifier.fillMaxSize().clickable { ctxMsg = null }.background(Color.Black.copy(alpha = 0.35f))) {
                Surface(
                    shape          = RoundedCornerShape(14.dp),
                    color          = HeftSurface,
                    tonalElevation = 0.dp,
                    modifier       = Modifier.align(Alignment.Center).width(180.dp),
                    shadowElevation = 24.dp,
                    border          = BorderStroke(1.dp, Divider),
                ) {
                    Column(modifier = Modifier.padding(5.dp)) {
                        MsgCtxItem(Icons.Default.Reply, if (language == "ku") "Bersiv bide" else "Yanıtla", false) { replyTo = ctxMsg; ctxMsg = null }
                        if (ctxMsg?.senderId == vm.uid) {
                            MsgCtxItem(Icons.Default.Create, if (language == "ku") "Biguherîne" else "Düzenle", false) { editMsg = ctxMsg; ctxMsg = null }
                            MsgCtxItem(Icons.Default.Delete, if (language == "ku") "Jê bibe" else "Sil", true) { vm.deleteMessage(ctxMsg!!); ctxMsg = null }
                        }
                        MsgCtxItem(Icons.Default.FavoriteBorder, if (language == "ku") "Hez bike" else "Beğen", false) { vm.toggleLike(ctxMsg!!); ctxMsg = null }
                    }
                }
            }
        }
    }
}

// ── Sesli Mesaj Oynatıcı ─────────────────────────────────────────────────────
@Composable
private fun AudioMessagePlayer(audioUrl: String, isMine: Boolean) {
    var isPlaying  by remember { mutableStateOf(false) }
    var player     by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var currentSec by remember { mutableStateOf(0) }
    var totalSec   by remember { mutableStateOf(0) }

    DisposableEffect(audioUrl) {
        onDispose { player?.release(); player = null }
    }

    // Oynatma sayacı
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                kotlinx.coroutines.delay(500)
                currentSec = (player?.currentPosition ?: 0) / 1000
            }
        }
    }

    fun formatTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

    Row(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isMine) Color.White.copy(alpha = 0.15f) else Color(0xFF8B5CF6).copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                } else {
                    if (player == null) {
                        try {
                            val mp = android.media.MediaPlayer().apply {
                                setDataSource(audioUrl)
                                setOnPreparedListener {
                                    totalSec = duration / 1000
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentSec = 0
                                    reset()
                                    player = null
                                }
                                setOnErrorListener { _, _, _ -> isPlaying = false; player = null; false }
                                prepareAsync()
                            }
                            player = mp
                        } catch (e: Exception) { e.printStackTrace() }
                    } else {
                        player?.start()
                        isPlaying = true
                    }
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                null,
                tint     = if (isMine) Color.White else Color(0xFF8B5CF6),
                modifier = Modifier.size(20.dp),
            )
        }
        Icon(
            Icons.Default.GraphicEq,
            null,
            tint     = if (isMine) Color.White.copy(alpha = 0.7f) else Color(0xFF8B5CF6).copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                "🎤 Sesli mesaj",
                color    = if (isMine) Color.White.copy(alpha = 0.85f) else Color(0xFF8B5CF6),
                fontSize = 11.sp,
            )
            Text(
                if (isPlaying) "${formatTime(currentSec)} / ${formatTime(totalSec)}"
                else if (totalSec > 0) formatTime(totalSec)
                else "⏳",
                color    = if (isMine) Color.White.copy(alpha = 0.6f) else Color(0xFF8B5CF6).copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
        }
    }
}

// Tema: .msg-ctx-item, .msg-ctx-item.danger
@Composable
private fun MsgCtxItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, tint = if (danger) Color(0xFFF43F5E) else Primary, modifier = Modifier.size(17.dp))
        Text(label, color = if (danger) Color(0xFFF43F5E) else OnBackground, fontSize = 13.sp)
    }
}

// ── Mesaj Satırı ─────────────────────────────────────────────────────────────
// Tema: .msg-row, .msg-row.me, .msg-row.them, .msg-row-av, .msg-bubble

@Composable
private fun MsgRow(
    msg           : Message,
    isMine        : Boolean,
    myUid         : String,
    otherPhotoURL : String,
    otherName     : String,
    language      : String,
    onReply       : () -> Unit,
    onEdit        : () -> Unit,
    onDelete      : () -> Unit,
    onLike        : () -> Unit,
    onLongPress   : (androidx.compose.ui.geometry.Offset) -> Unit,
) {
    if (msg.text.isBlank() && msg.imageUrl.isBlank() && msg.audioUrl.isBlank()) return
    val iLiked = myUid in msg.likedBy

    // Tema: .msg-row, .msg-row.me / .msg-row.them
    Row(
        modifier              = Modifier.fillMaxWidth().pointerInput(msg.id) {
            detectTapGestures(onLongPress = { onLongPress(it) })
        },
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom,
    ) {
        // Tema: .msg-row-av (karşı taraf için)
        if (!isMine) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Primary, Accent))),
                contentAlignment = Alignment.Center,
            ) {
                if (otherPhotoURL.isNotBlank())
                    AsyncImage(model = otherPhotoURL, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(otherName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            // Tema: reply preview — .msg-reply-preview
            if (msg.replyToId.isNotBlank() && msg.replyToText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 250.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isMine) Color.Black.copy(alpha = 0.15f)
                            else Primary.copy(alpha = 0.1f)
                        )
                        .startBorder(
                            color = if (isMine) Color.White.copy(alpha = 0.5f) else Primary,
                            width = 3.dp,
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column {
                        if (msg.replyToName.isNotBlank())
                            // .msg-reply-preview-name
                            Text(msg.replyToName, color = if (isMine) Color.White.copy(alpha = 0.9f) else Primary,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        // .msg-reply-preview-txt
                        Text(msg.replyToText, color = if (isMine) Color.White.copy(alpha = 0.75f) else Muted,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // Tema: .msg-bubble — ana balon
            // .msg-row.me .msg-bubble → gradient, .msg-row.them .msg-bubble → s3
            Box(
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart    = 16.dp,
                            topEnd      = 16.dp,
                            bottomStart = if (isMine) 16.dp else 3.dp,
                            bottomEnd   = if (isMine) 3.dp  else 16.dp,
                        )
                    )
                    .then(
                        if (msg.deleted)
                        // Tema: .msg-bubble.deleted
                            Modifier.background(SurfaceVar).border(1.dp, Divider, RoundedCornerShape(16.dp))
                        else if (isMine)
                            Modifier.background(Brush.linearGradient(listOf(PrimaryLight, Primary)))
                        else
                            Modifier.background(SurfaceVar).border(1.dp, Divider, RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp, bottomStart = 3.dp, bottomEnd = 16.dp))
                    )
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                if (msg.deleted) {
                    Text(if (language == "ku") "Peyam hat jêbirin" else "Bu mesaj silindi",
                        color = Muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                } else {
                    Column {
                        if (msg.imageUrl.isNotBlank()) {
                            // Tema: .msg-bubble-img
                            AsyncImage(model = msg.imageUrl, contentDescription = null,
                                modifier = Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.audioUrl.isNotBlank()) {
                            AudioMessagePlayer(audioUrl = msg.audioUrl, isMine = isMine)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.text.isNotBlank())
                            Text(msg.text, color = if (isMine) Color.White else OnBackground, fontSize = 13.sp, lineHeight = 19.sp)
                        if (msg.edited)
                            Text(if (language == "ku") "(guherî)" else "(düzenlendi)",
                                color = if (isMine) Color.White.copy(alpha = 0.55f) else Muted,
                                fontSize = 9.sp)
                    }
                }
            }

            // Tema: .msg-meta — saat + okundu
            Row(
                modifier              = Modifier.padding(top = 2.dp, start = 3.dp, end = 3.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            ) {
                Text(formatTime(msg.createdAt), color = Muted, fontSize = 9.sp)
                if (isMine) {
                    Spacer(Modifier.width(3.dp))
                    // Tema: .msg-read.read → mavi, .msg-read.sent → soluk
                    Icon(
                        Icons.Default.DoneAll, null,
                        tint     = if (msg.read) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            // Tema: .msg-like-badge
            if (msg.likedBy.isNotEmpty()) {
                Surface(
                    shape  = RoundedCornerShape(99.dp),
                    color  = if (iLiked) Color(0xFFF43F5E).copy(alpha = 0.1f) else SurfaceVar,
                    border = BorderStroke(1.dp, if (iLiked) Color(0xFFF43F5E) else Divider),
                    modifier = Modifier.clickable { onLike() }.padding(top = 3.dp),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.Favorite, null,
                            tint = if (iLiked) Color(0xFFF43F5E) else Muted,
                            modifier = Modifier.size(11.dp))
                        Text("${msg.likedBy.size}", color = if (iLiked) Color(0xFFF43F5E) else Muted, fontSize = 10.sp)
                    }
                }
            }
        }

        if (isMine) Spacer(Modifier.width(6.dp))
    }
}

// ── Yardımcı ─────────────────────────────────────────────────────────────────
private fun formatTime(ts: String): String {
    if (ts.isBlank()) return ""
    return try {
        val ms = ts.toLongOrNull() ?: return ts.take(5)
        val cal  = Calendar.getInstance().apply { timeInMillis = ms }
        val now  = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR))
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        else
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ms))
    } catch (_: Exception) { ts.take(5) }
}

// ── Sol kenarlık yardımcısı ──────────────────────────────────────────────────
private fun Modifier.startBorder(color: Color, width: androidx.compose.ui.unit.Dp): Modifier =
    this.drawBehind {
        drawRect(
            color    = color,
            topLeft  = Offset.Zero,
            size     = Size(width.toPx(), this.size.height),
        )
    }
