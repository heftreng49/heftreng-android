package com.heftreng.app.ui.screens.messages

import androidx.compose.material3.AlertDialog
import com.heftreng.app.utils.HeftrangMessagingService

import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import com.heftreng.app.ui.component.LinkifyText
import com.heftreng.app.ui.component.FullScreenImageViewer
import com.heftreng.app.ui.i18n.Strings
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
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.core.content.ContextCompat

// ── Konuşma Listesi ─────────────────────────────────────────────────────────
// Tema: .msgp-wrap, .msgp-hd, .msgp-conv-item, .msgp-conv-av, .msgp-unread-dot

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : MessagesViewModel = hiltViewModel(),
) {
    val ku = language == "ku"
    val conversations by vm.conversations.collectAsState()
    val loading       by vm.loading.collectAsState()
    var searchQuery   by remember { mutableStateOf("") }
    var showSearch    by remember { mutableStateOf(false) }

    val uid = vm.uid
    // Ekran açıkken mesaj bildirimlerini bastır
    DisposableEffect(Unit) {
        HeftrangMessagingService.isMessagesScreenActive = true
        onDispose { HeftrangMessagingService.isMessagesScreenActive = false }
    }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) vm.listenConversations()
    }

    val filtered = if (searchQuery.isBlank()) conversations
    else conversations.filter {
        it.otherUser?.displayName?.contains(searchQuery, ignoreCase = true) == true ||
        it.otherUser?.email?.contains(searchQuery, ignoreCase = true) == true ||
        it.lastMessage.contains(searchQuery, ignoreCase = true)
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
                            placeholder   = { Text(Strings.searchMessages(language), color = Muted, fontSize = 13.sp) },
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
                            Strings.messagesTitle(language),
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
                        Text(Strings.loading(language), color = Muted, fontSize = 12.sp)
                    }
                }
            }
            filtered.isEmpty() -> {
                // Tema: .msgp-empty stili
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Divider, modifier = Modifier.size(52.dp))
                        Text(
                            Strings.noMessages(language),
                            color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                        Text(
                            Strings.newConversation(language),
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
                        text  = { Text(Strings.deleteConvConfirm(language), color = Muted) },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.deleteConversation(cid)
                                convToDelete = null
                            }) { Text(Strings.delete(language), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { convToDelete = null }) {
                                Text(Strings.cancel(language), color = Muted)
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
                                    conv.otherUser?.displayName?.ifBlank { conv.otherUser?.email } ?: if (ku) "Bikarhêner" else "Kullanıcı",
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
    val ku = language == "ku"
    val messages         by vm.messages.collectAsState()
    val hasOlderMessages by vm.hasOlderMessages.collectAsState()
    val loadingOlder     by vm.loadingOlder.collectAsState()
    val otherUser     by vm.otherUser.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val listState     = rememberLazyListState()

    var inputText     by remember { mutableStateOf("") }
    var replyTo       by remember { mutableStateOf<Message?>(null) }
    var editMsg       by remember { mutableStateOf<Message?>(null) }
    var ctxMsg        by remember { mutableStateOf<Message?>(null) }
    var ctxOffset     by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var isRecording      by remember { mutableStateOf(false) }
    var showAudioPreview by remember { mutableStateOf(false) }
    var recordSecs       by remember { mutableStateOf(0) }
    var recorder         by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile        by remember { mutableStateOf<File?>(null) }
    var previewPlayer    by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Saniye sayacı — kayıt sırasında her saniye artır
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSecs = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                if (isRecording) recordSecs++
            }
        }
    }

    val uploading = vm.uploading.collectAsState().value
    val context   = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedImage = uri }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    // Runtime izin launcher — ses kaydı
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message     = if (language == "ku")
                        "Destûra mîkrofonê nehat dayîn. Ji mîhengên têlefonê destûrê bide."
                    else
                        "Mikrofon izni verilmedi. Telefon ayarlarından izin ver.",
                    duration    = SnackbarDuration.Long,
                )
            }
        }
    }

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
    }

    // Mesaj bildirimleri: sohbet ekranındayken bastır
    DisposableEffect(Unit) {
        HeftrangMessagingService.isMessagesScreenActive = true
        onDispose { HeftrangMessagingService.isMessagesScreenActive = false }
    }

    // Ekran açılınca online yap, kapanınca offline yap
    DisposableEffect(convId) {
        presenceVm.goOnline()
        onDispose {
            presenceVm.goOffline()
            presenceVm.setTyping(convId, false)
        }
    }

    val otherUidForPresence = remember(otherUser) { otherUser?.uid ?: "" }
    LaunchedEffect(otherUidForPresence) {
        if (otherUidForPresence.isNotEmpty()) {
            presenceVm.listenPresence(otherUidForPresence)
            presenceVm.listenTyping(convId, otherUidForPresence)

    // Ekrandan çıkınca presence/typing listener'ları kapat — zombie önleme
    androidx.compose.runtime.DisposableEffect(convId) {
        onDispose {
            presenceVm.stopListening()
        }
    }
        }
    }

    // StateFlow reaktif olarak dinle — UI otomatik güncellenir
    val onlineUsers by presenceVm.onlineUsers.collectAsState()
    val typingUsers by presenceVm.typingUsers.collectAsState()
    val isOtherOnline = (otherUser?.uid ?: "") in onlineUsers
    val isOtherTyping = (otherUser?.uid ?: "") in typingUsers

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

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier       = Modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable {
                            otherUser?.uid?.let { navController.navigate("profile/$it") }
                        },
                    ) {
                        Box {
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
                            Text(
                                otherUser?.displayName?.ifBlank { otherUser?.email ?: "…" } ?: "…",
                                color      = OnBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                            )
                            when {
                                isOtherTyping -> Text(
                                    Strings.typing(language),
                                    color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                                isOtherOnline -> Text(
                                    Strings.online(language),
                                    color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                                else -> Text(
                                    Strings.offline(language),
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
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Muted)
                        }
                        DropdownMenu(
                            expanded         = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor   = HeftSurface,
                        ) {
                            DropdownMenuItem(
                                text        = { Text(if (language == "ku") "Profîl" else "Profile git", color = OnBackground) },
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = Amber) },
                                onClick     = {
                                    menuExpanded = false
                                    otherUser?.uid?.let { navController.navigate("profile/$it") }
                                },
                            )
                            DropdownMenuItem(
                                text        = { Text(if (language == "ku") "Sohbetê jê bibe" else "Sohbeti sil", color = Color(0xFFEF4444)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                                onClick     = {
                                    menuExpanded = false
                                    vm.deleteConversation(convId)
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeftSurface),
            )
        },
        bottomBar = {
            Column {

                // ── Kayıt Çubuğu — kayıt sırasında göster ────────────────
                AnimatedVisibility(
                    visible = isRecording,
                    enter   = slideInVertically { it } + fadeIn(),
                    exit    = slideOutVertically { it } + fadeOut(),
                ) {
                    Surface(color = Color(0xFFEF4444).copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Yanıp sönen nokta
                            val infiniteTransition = rememberInfiniteTransition(label = "blink")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation  = tween(600),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "blinkAlpha",
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = alpha), CircleShape)
                            )
                            Text(
                                if (ku) "Tê tomarkirin" else "Kayıt yapılıyor",
                                color     = Color(0xFFEF4444),
                                fontSize  = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier  = Modifier.weight(1f),
                            )
                            Text(
                                "%02d:%02d".format(recordSecs / 60, recordSecs % 60),
                                color      = Color(0xFFEF4444),
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            // İptal
                            TextButton(
                                onClick = {
                                    try { recorder?.stop(); recorder?.release(); recorder = null }
                                    catch (e: Exception) { e.printStackTrace() }
                                    isRecording = false
                                    audioFile?.delete(); audioFile = null; recordSecs = 0
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(if (ku) "Betal bike" else "İptal", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ── Önizleme Çubuğu — kayıt bitti, göndermeden önce dinle ─
                AnimatedVisibility(
                    visible = showAudioPreview && !isRecording,
                    enter   = slideInVertically { it } + fadeIn(),
                    exit    = slideOutVertically { it } + fadeOut(),
                ) {
                    Surface(color = HeftSurface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Dinle/Durdur
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Primary)
                                    .clickable {
                                        if (isPreviewPlaying) {
                                            previewPlayer?.pause()
                                            isPreviewPlaying = false
                                        } else {
                                            if (previewPlayer == null) {
                                                try {
                                                    val mp = android.media.MediaPlayer().apply {
                                                        setDataSource(audioFile!!.absolutePath)
                                                        setOnPreparedListener { start(); isPreviewPlaying = true }
                                                        setOnCompletionListener {
                                                            isPreviewPlaying = false
                                                            reset()
                                                            previewPlayer = null
                                                        }
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
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null, tint = Color.White, modifier = Modifier.size(22.dp),
                                )
                            }
                            // Süre
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (ku) "Dengbêjiya dengî" else "Sesli mesaj",
                                    color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "%02d:%02d".format(recordSecs / 60, recordSecs % 60),
                                    color = Muted, fontSize = 11.sp,
                                )
                            }
                            // Sil
                            IconButton(
                                onClick = {
                                    previewPlayer?.release(); previewPlayer = null
                                    isPreviewPlaying = false
                                    audioFile?.delete(); audioFile = null
                                    showAudioPreview = false; recordSecs = 0
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(20.dp))
                            }
                            // Gönder
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(PrimaryLight, Primary)))
                                    .clickable {
                                        previewPlayer?.release(); previewPlayer = null
                                        isPreviewPlaying = false
                                        showAudioPreview = false
                                        val f = audioFile
                                        audioFile = null; recordSecs = 0
                                        if (f != null && f.exists() && f.length() > 0) {
                                            vm.uploadAudioAndSend(convId, otherUid, f)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    null, tint = Color.White, modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
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
                            Text(if (replyTo?.senderId == vm.uid) if (ku) "Tu" else "Sen" else otherUser?.displayName ?: "",
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
                        Text(Strings.edit(language),
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
                        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
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
                                Text(Strings.messageHint(language),
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
                        // Sesli mesaj butonu — tek tıkla başlat
                        if (inputText.isBlank() && selectedImage == null && !showAudioPreview) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording)
                                            Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                                        else
                                            Brush.linearGradient(listOf(SurfaceVar, SurfaceVar))
                                    )
                                    .clickable {
                                        if (isRecording) {
                                            // Kaydı durdur → önizlemeye geç
                                            try {
                                                recorder?.stop()
                                                recorder?.release()
                                                recorder = null
                                            } catch (e: Exception) { e.printStackTrace() }
                                            isRecording = false
                                            if ((audioFile?.length() ?: 0L) > 0L) {
                                                showAudioPreview = true
                                            } else {
                                                audioFile?.delete(); audioFile = null; recordSecs = 0
                                            }
                                        } else {
                                            // Kayıt başlat
                                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context, android.Manifest.permission.RECORD_AUDIO)
                                                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                return@clickable
                                            }
                                            try {
                                                val f = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                                                audioFile = f
                                                val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                    MediaRecorder(context)
                                                else @Suppress("DEPRECATION") MediaRecorder()
                                                mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                                                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                mr.setOutputFile(f.absolutePath)
                                                mr.prepare()
                                                mr.start()
                                                recorder = mr
                                                isRecording = true
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    null,
                                    tint     = if (isRecording) Color.White else Muted,
                                    modifier = Modifier.size(20.dp),
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
                                            replyToName = if (replyTo?.senderId == vm.uid) if (ku) "Tu" else "Sen"
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
                                            replyToName = if (replyTo?.senderId == vm.uid) if (ku) "Tu" else "Sen"
                                                          else otherUser?.displayName ?: "",
                                        )
                                        presenceVm.setTyping(convId, false)
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
                Text(Strings.noMessages(language),
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
                // ── Eski mesajları yükle ────────────────────────────
                item(key = "older_btn") {
                    if (hasOlderMessages) {
                        Box(
                            modifier            = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            contentAlignment    = Alignment.Center,
                        ) {
                            if (loadingOlder) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    color       = Primary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                TextButton(onClick = { vm.loadOlderMessages() }) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint     = Muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Eski mesajları gör",
                                        color    = Muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

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
    } // end Scaffold

    // Context menu — Scaffold dışında, tüm ekranı kaplar
    if (ctxMsg != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { ctxMsg = null }
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            Surface(
                shape           = RoundedCornerShape(14.dp),
                color           = HeftSurface,
                tonalElevation  = 0.dp,
                modifier        = Modifier.align(Alignment.Center).width(180.dp),
                shadowElevation = 24.dp,
                border          = BorderStroke(1.dp, Divider),
            ) {
                Column(modifier = Modifier.padding(5.dp)) {
                    MsgCtxItem(Icons.Default.Reply, Strings.reply(language), false) { replyTo = ctxMsg; ctxMsg = null }
                    if (ctxMsg?.senderId == vm.uid) {
                        MsgCtxItem(Icons.Default.Create, Strings.edit(language), false) { editMsg = ctxMsg; ctxMsg = null }
                        MsgCtxItem(Icons.Default.Delete, Strings.delete(language), true) { vm.deleteMessage(ctxMsg!!); ctxMsg = null }
                    }
                    MsgCtxItem(Icons.Default.FavoriteBorder, Strings.like(language), false) { vm.toggleLike(ctxMsg!!); ctxMsg = null }
                }
            }
        }
    }
    } // end outer Box
}

// ── Sesli Mesaj Oynatıcı ─────────────────────────────────────────────────────
@Composable
private fun AudioMessagePlayer(audioUrl: String, isMine: Boolean, language: String = "tr") {
    var isPlaying by remember { mutableStateOf(false) }
    var player    by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val context   = LocalContext.current

    DisposableEffect(audioUrl) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        modifier          = Modifier
            .widthIn(min = 140.dp, max = 200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isMine) Color.White.copy(alpha = 0.15f) else Color(0xFF8B5CF6).copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                setOnPreparedListener { start(); isPlaying = true }
                                setOnCompletionListener { isPlaying = false; reset(); player = null }
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
            modifier = Modifier.size(20.dp),
        )
        Text(
            if (isPlaying) Strings.playing(language) else Strings.voice(language),
            color    = if (isMine) Color.White.copy(alpha = 0.85f) else Color(0xFF8B5CF6),
            fontSize = 11.sp,
        )
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

@OptIn(ExperimentalFoundationApi::class)
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

    // ── Swipe + LongPress + DoubleTap — tek pointerInput ─────────────────────
    val swipeThreshold = 80f
    var rawOffset      by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue   = rawOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "swipe",
    )
    var triggered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                // Swipe
                detectHorizontalDragGestures(
                    onDragEnd    = {
                        if (rawOffset >= swipeThreshold && !triggered) {
                            triggered = true
                            onReply()
                        }
                        rawOffset = 0f
                        triggered = false
                    },
                    onDragCancel = { rawOffset = 0f },
                ) { _, dragAmount ->
                    val direction = if (isMine) -1f else 1f
                    val delta = dragAmount * direction
                    if (delta > 0) rawOffset = (rawOffset + delta).coerceIn(0f, swipeThreshold * 1.2f)
                }
            }
            .pointerInput(Unit) {
                // Uzun basma + çift tıklama
                detectTapGestures(
                    onLongPress   = { offset -> onLongPress(offset) },
                    onDoubleTap   = { onLike() },
                )
            },
    ) {
        // Yanıtla ikonu — swipe sırasında görünür
        val swipeProgress = (animatedOffset / swipeThreshold).coerceIn(0f, 1f)
        if (swipeProgress > 0.05f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint     = Primary.copy(alpha = swipeProgress),
                modifier = Modifier
                    .size(22.dp)
                    .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 4.dp),
            )
        }

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .offset(x = (if (isMine) -animatedOffset else animatedOffset).dp),
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
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        // .msg-reply-preview-txt
                        Text(msg.replyToText, color = if (isMine) Color.White.copy(alpha = 0.75f) else Muted,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // Tema: .msg-bubble — ana balon
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
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
                            Modifier.background(SurfaceVar).border(1.dp, Divider, RoundedCornerShape(16.dp))
                        else if (isMine)
                            Modifier.background(Brush.linearGradient(listOf(PrimaryLight, Primary)))
                        else
                            Modifier.background(SurfaceVar).border(1.dp, Divider, RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp, bottomStart = 3.dp, bottomEnd = 16.dp))
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (msg.deleted) {
                    Text(Strings.deleted(language),
                        color = Muted, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                } else {
                    Column {
                        if (msg.imageUrl.isNotBlank()) {
                            var showMsgImg by remember { mutableStateOf(false) }
                            AsyncImage(
                                model              = msg.imageUrl,
                                contentDescription = null,
                                modifier           = Modifier
                                    .widthIn(max = 200.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showMsgImg = true },
                                contentScale       = ContentScale.Crop,
                            )
                            if (showMsgImg) FullScreenImageViewer(url = msg.imageUrl) { showMsgImg = false }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.audioUrl.isNotBlank()) {
                            AudioMessagePlayer(audioUrl = msg.audioUrl, isMine = isMine, language = language)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.text.isNotBlank())
                            LinkifyText(
                                text       = msg.text,
                                fontSize   = 15.sp,
                                lineHeight = 22.sp,
                                modifier   = Modifier,
                            )
                        if (msg.edited)
                            Text(Strings.edited(language),
                                color = if (isMine) Color.White.copy(alpha = 0.55f) else Muted,
                                fontSize = 11.sp)
                    }
                }
            }

            // Tema: .msg-meta — saat + okundu
            Row(
                modifier              = Modifier.padding(top = 2.dp, start = 3.dp, end = 3.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            ) {
                Text(formatTime(msg.createdAt), color = Muted, fontSize = 11.sp)
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
        } // end Row
    } // end Box (swipe)
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
