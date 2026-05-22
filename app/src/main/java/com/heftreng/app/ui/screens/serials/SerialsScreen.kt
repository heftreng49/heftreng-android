package com.heftreng.app.ui.screens.serials

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.heftreng.app.ui.component.RichTextEditor
import com.heftreng.app.ui.component.htmlStrip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.text.Html
import android.text.Spanned
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.heftreng.app.data.model.Chapter
import com.heftreng.app.data.model.Serial
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.BookViewModel
import com.heftreng.app.viewmodel.SettingsViewModel

// ── Seri listesi ekranı ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialsScreen(
    navController : NavController,
    language      : String = "tr",
    vm : BookViewModel = hiltViewModel(),
) {
    val serials by vm.serials.collectAsState()
    val loading by vm.loading.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadSerials() }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.navBooks(language), fontWeight = FontWeight.Bold, color = OnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, null, tint = Amber)
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(serials) { serial ->
                    SerialCard(
                        serial   = serial,
                        onClick  = { navController.navigate("serial/${serial.id}") },
                        onLike   = { vm.toggleLikeSerial(serial) },
                        language = language,
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateSerialDialog(
            onDismiss = { showCreate = false },
            language  = language,
            onCreate  = { title, desc, genre ->
                vm.createSerial(title, desc, genre)
                showCreate = false
            }
        )
    }
}

// ── Seri kartı ────────────────────────────────────────────────────────────────
@Composable
fun SerialCard(
    serial  : Serial,
    onClick : () -> Unit,
    onLike  : () -> Unit,
    modifier: Modifier = Modifier,
    language: String = "tr",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = HeftSurface,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            // Kapak fotoğrafı
            if (serial.coverImg.isNotEmpty()) {
                AsyncImage(
                    model        = serial.coverImg,
                    contentDescription = serial.title,
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier
                        .size(72.dp, 96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVar),
                )
            } else {
                Box(
                    Modifier
                        .size(72.dp, 96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // Tür etiketi
                if (serial.genre.isNotEmpty()) {
                    Text(
                        serial.genre,
                        color    = Amber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    serial.title,
                    fontWeight  = FontWeight.Bold,
                    color       = OnBackground,
                    fontSize    = 15.sp,
                    maxLines    = 2,
                    overflow    = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (serial.desc.isNotEmpty()) {
                    Text(
                        serial.desc,
                        color    = Muted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Yazar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model             = serial.photoURL,
                        contentDescription = null,
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(SurfaceVar),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(serial.name, color = Muted, fontSize = 11.sp)
                }

                Spacer(Modifier.height(8.dp))

                // Alt bilgi
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MenuBook, null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${serial.chapterCount} " + Strings.chapter(language), color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = onLike, modifier = Modifier.size(20.dp)) {
                        Icon(
                            if (serial.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            null,
                            tint     = if (serial.isLikedByMe) Color(0xFFF43F5E) else Muted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text("${serial.likes}", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Seri detay ekranı ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialDetailScreen(
    serialId      : String,
    navController : NavController,
    vm : BookViewModel = hiltViewModel(),
    socialVm      : com.heftreng.app.viewmodel.SocialViewModel = hiltViewModel(),
    settingsVm    : SettingsViewModel = hiltViewModel(),
) {
    val serial   by vm.selectedSerial.collectAsState()
    val chapters by vm.serialChapters.collectAsState()
    val loading  by vm.loading.collectAsState()
    val likers   by socialVm.likers.collectAsState()
    val language by settingsVm.language.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var showAddChapter  by remember { mutableStateOf(false) }
    var showLikers      by remember { mutableStateOf(false) }
    var chapterToEdit   by remember { mutableStateOf<Chapter?>(null) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    // Overlay state for editor
    var addTitle   by remember { mutableStateOf("") }
    var addBody    by remember { mutableStateOf("") }
    var editTitle  by remember { mutableStateOf("") }
    var editBody   by remember { mutableStateOf("") }

    LaunchedEffect(serialId) { vm.loadSerial(serialId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(serial?.title ?: "Seri", fontWeight = FontWeight.Bold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    IconButton(onClick = { showAddChapter = true }) {
                        Icon(Icons.Default.Add, null, tint = Amber)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Kapak + bilgi başlığı
            item {
                serial?.let { s ->
                    SerialHeader(
                        serial       = s,
                        onLike       = { vm.toggleLikeSerial(s) },
                        onShowLikers = {
                            socialVm.loadSerialLikers(s.id)
                            showLikers = true
                        },
                    )
                }
            }

            item {
                Text(
                    Strings.chapters(language) + " (${chapters.size})",
                    fontWeight = FontWeight.SemiBold,
                    color      = Amber,
                    fontSize   = 13.sp,
                    modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                    }
                }
            } else if (chapters.isEmpty()) {
                item {
                    Text(Strings.noChapters(language), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }
            } else {
                items(chapters) { ch ->
                    ChapterRow(
                        chapter  = ch,
                        canEdit  = myUid == (serial?.uid ?: ""),
                        language = language,
                        onClick  = { navController.navigate("chapter/${serialId}/${ch.id}") },
                        onEdit   = { chapterToEdit = ch },
                        onDelete = { chapterToDelete = ch },
                    )
                }
            }
        }
    }

    if (showLikers) {
        LikerListSheet(
            title     = Strings.likedBy(language),
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false; socialVm.clearLikers() },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }

    if (showAddChapter) {
        ChapterEditorOverlay(
            title         = addTitle,
            body          = addBody,
            onTitleChange = { v -> addTitle = v },
            onBodyChange  = { v -> addBody  = v },
            heading       = Strings.newChapter(language),
            saveLabel     = Strings.save(language),
            canSave       = addTitle.isNotBlank() && addBody.isNotBlank(),
            language      = language,
            onDismiss     = { showAddChapter = false; addTitle = ""; addBody = "" },
            onSave        = {
                vm.addSerialChapter(serialId, addTitle, addBody)
                showAddChapter = false
                addTitle = ""; addBody = ""
            },
        )
    }

    chapterToEdit?.let { ch ->
        LaunchedEffect(ch.id) { editTitle = ch.title; editBody = ch.body }
        ChapterEditorOverlay(
            title         = editTitle,
            body          = editBody,
            onTitleChange = { v -> editTitle = v },
            onBodyChange  = { v -> editBody  = v },
            heading       = Strings.editChapter(language),
            saveLabel     = Strings.save(language),
            canSave       = editTitle.isNotBlank() && editBody.isNotBlank(),
            language      = language,
            onDismiss     = { chapterToEdit = null },
            onSave        = {
                vm.updateSerialChapter(serialId, ch.id, editTitle, editBody)
                chapterToEdit = null
            },
        )
    }

    chapterToDelete?.let { ch ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            containerColor   = HeftSurface,
            title = { Text(Strings.deleteChapter(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text  = { Text("\"${ch.title}\" bölümünü silmek istediğine emin misin?", color = Muted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSerialChapter(serialId, ch.id); chapterToDelete = null }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { chapterToDelete = null }) { Text(Strings.cancel(language), color = Muted) } },
        )
    }
}

@Composable
private fun SerialHeader(serial: Serial, onLike: () -> Unit, onShowLikers: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeftSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (serial.coverImg.isNotEmpty()) {
            AsyncImage(
                model        = serial.coverImg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier
                    .size(90.dp, 120.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (serial.genre.isNotEmpty())
                Text(serial.genre, color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(serial.title, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(serial.desc, color = Muted, fontSize = 12.sp, maxLines = 4)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model             = serial.photoURL,
                    contentDescription = null,
                    modifier          = Modifier.size(20.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale      = ContentScale.Crop,
                )
                Spacer(Modifier.width(6.dp))
                Text(serial.name, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onLike, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (serial.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        null,
                        tint = if (serial.isLikedByMe) Color(0xFFF43F5E) else Muted,
                    )
                }
                Text("${serial.likes}", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter  : Chapter,
    canEdit  : Boolean,
    language : String = "tr",
    onClick  : () -> Unit,
    onEdit   : () -> Unit,
    onDelete : () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HeftSurface)
            .clickable { onClick() }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${chapter.order}",
            color      = Amber,
            fontWeight = FontWeight.Bold,
            fontSize   = 13.sp,
            modifier   = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(chapter.title, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(Strings.wordCount(language, chapter.wordCount), color = Muted, fontSize = 11.sp)
        }
        if (canEdit) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor   = HeftSurface,
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(16.dp))
                                Text("Düzenle", color = OnBackground, fontSize = 14.sp)
                            }
                        },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Text("Sil", color = Color(0xFFEF4444), fontSize = 14.sp)
                            }
                        },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        } else {
            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Bölüm okuma ekranı ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChapterReadScreen(
    serialId      : String,
    chapterId     : String,
    navController : NavController,
    vm : BookViewModel = hiltViewModel(),
    settingsVm    : SettingsViewModel = hiltViewModel(),
) {
    val chapter  by vm.selectedSerialChapter.collectAsState()
    val language by settingsVm.language.collectAsState()
    val ku = language == "ku"
    val auth  = FirebaseAuth.getInstance()
    val db    = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val myUid = auth.currentUser?.uid ?: ""

    LaunchedEffect(chapterId) { vm.loadSerialChapter(serialId, chapterId) }

    // ── Yorum state ───────────────────────────────────────────────────────────
    data class ChCmt(
        val id: String = "", val uid: String = "", val name: String = "",
        val photoURL: String = "", val text: String = "", val replyTo: String = "",
        val replyToCmtId: String = "", val likes: Int = 0, val edited: Boolean = false,
        val ts: com.google.firebase.Timestamp? = null,
    )

    var comments     by remember { mutableStateOf<List<ChCmt>>(emptyList()) }
    var cmtLoading   by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var replyTo      by remember { mutableStateOf<ChCmt?>(null) }
    var editTarget   by remember { mutableStateOf<ChCmt?>(null) }
    var deleteTarget by remember { mutableStateOf<ChCmt?>(null) }
    var menuTarget   by remember { mutableStateOf<ChCmt?>(null) }
    val focusRequester  = remember { FocusRequester() }
    val keyboardCtrl    = LocalSoftwareKeyboardController.current
    val listState       = rememberLazyListState()

    LaunchedEffect(editTarget) {
        editTarget?.let { inputText = it.text; focusRequester.requestFocus(); keyboardCtrl?.show() }
    }

    // Kullanıcı adı + fotoğrafı
    var myName  by remember { mutableStateOf("") }
    var myPhoto by remember { mutableStateOf("") }
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val doc = db.collection("users").document(myUid).get().await()
            myName  = doc.getString("displayName") ?: doc.getString("name") ?: ""
            myPhoto = doc.getString("photoURL") ?: ""
        } catch (_: Exception) {}
    }

    // Realtime yorum listener
    DisposableEffect(chapterId) {
        var reg: com.google.firebase.firestore.ListenerRegistration? = null
        reg = db.collection("serials").document(serialId)
            .collection("chapters").document(chapterId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { cmtLoading = false; return@addSnapshotListener }
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ChCmt(
                        id           = doc.id,
                        uid          = d["uid"]          as? String ?: "",
                        name         = (d["displayName"] as? String)?.ifBlank { null } ?: d["name"] as? String ?: "?",
                        photoURL     = d["photoURL"]     as? String ?: "",
                        text         = d["text"]         as? String ?: "",
                        replyTo      = d["replyTo"]      as? String ?: "",
                        replyToCmtId = d["replyToCmtId"] as? String ?: "",
                        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
                        edited       = d["edited"]       as? Boolean ?: false,
                        ts           = d["ts"]           as? com.google.firebase.Timestamp,
                    )
                }
                cmtLoading = false
            }
        onDispose { reg?.remove() }
    }

    fun submitComment() {
        val text = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return
        val editing  = editTarget
        inputText    = ""
        editTarget   = null
        if (editing != null) {
            vm.editChapterComment(serialId, chapterId, editing.id, text)
            return
        }
        val rTo = replyTo
        replyTo = null
        vm.addChapterComment(serialId, chapterId, text, rTo?.name ?: "", rTo?.id ?: "")
    }

    fun deleteComment(cmt: ChCmt) { vm.deleteChapterComment(serialId, chapterId, cmt.id) }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            chapter?.title ?: Strings.chapter(language),
                            color = OnBackground, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, null, tint = OnBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                // ── İçerik + yorumlar listesi ─────────────────────────────────
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    // Bölüm içeriği
                    item {
                        chapter?.let { ch ->
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                // Meta: bölüm no + kelime sayısı
                                Text(
                                    "${Strings.chapter(language)} ${ch.order} · ${Strings.wordCount(language, ch.wordCount)}",
                                    color = Muted, fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(16.dp))

                                // İçerik
                                if (ch.body.contains("<") && ch.body.contains(">")) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxWidth(),
                                        factory  = { ctx ->
                                            android.widget.TextView(ctx).apply {
                                                setTextColor(android.graphics.Color.parseColor("#E5E5EA"))
                                                textSize = 16f
                                                setLineSpacing(0f, 1.6f)
                                            }
                                        },
                                        update = { tv ->
                                            @Suppress("DEPRECATION")
                                            val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                                                android.text.Html.fromHtml(ch.body, android.text.Html.FROM_HTML_MODE_COMPACT)
                                            else android.text.Html.fromHtml(ch.body)
                                            tv.text = spanned
                                        }
                                    )
                                } else {
                                    Text(ch.body, color = OnBackground, fontSize = 16.sp, lineHeight = 26.sp)
                                }

                                Spacer(Modifier.height(24.dp))

                                // Beğeni + Yorum sayacı
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    // Beğen
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            val newLiked = vm.toggleLikeSerialChapter(serialId, chapterId, ch.isLikedByMe)
                                        },
                                    ) {
                                        Icon(
                                            if (ch.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            null,
                                            tint     = if (ch.isLikedByMe) Color(0xFFEF4444) else Muted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text("${ch.likes}", color = Muted, fontSize = 13.sp)
                                    }
                                    // Yorum
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            focusRequester.requestFocus(); keyboardCtrl?.show()
                                        },
                                    ) {
                                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("${ch.cmtCount}", color = Muted, fontSize = 13.sp)
                                    }
                                }
                            }
                        } ?: Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber)
                        }
                        HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                    }

                    // Yorum başlığı
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(Strings.comments(language), color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (comments.isNotEmpty()) Text("${comments.size}", color = Muted, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Divider)
                    }

                    if (cmtLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    if (!cmtLoading && comments.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💬", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(Strings.noComments(language), color = Muted, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    items(comments, key = { it.id }) { cmt ->
                        val isOwner   = myUid.isNotBlank() && cmt.uid == myUid
                        val canDelete = isOwner || (chapter?.uid?.let { it == myUid } ?: false)
                        ChapterCommentRow(
                            id          = cmt.id,
                            uid         = cmt.uid,
                            name        = cmt.name,
                            photoURL    = cmt.photoURL,
                            text        = cmt.text,
                            replyTo     = cmt.replyTo,
                            likes       = cmt.likes,
                            edited      = cmt.edited,
                            canEdit     = isOwner,
                            canDelete   = canDelete,
                            language    = language,
                            onLongPress = { menuTarget = cmt },
                            onReply     = { replyTo = cmt; editTarget = null; focusRequester.requestFocus(); keyboardCtrl?.show() },
                            onEdit      = { editTarget = cmt; replyTo = null },
                            onDelete    = { deleteTarget = cmt },
                        )
                        HorizontalDivider(color = Divider.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    }
                }

                // ── Düzenleme / Yanıt göstergesi ─────────────────────────────
                val indicator = editTarget ?: replyTo
                if (indicator != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SurfaceVar).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (editTarget != null) {
                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(Strings.editCommentTitle(language), color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("@${replyTo!!.name} ${Strings.replyingToSuffix(language)}", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        IconButton(onClick = { editTarget = null; replyTo = null; inputText = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── Giriş kutusu ─────────────────────────────────────────────
                HorizontalDivider(color = Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it },
                        placeholder   = {
                            Text(
                                when {
                                    editTarget != null -> Strings.editCommentHint(language)
                                    replyTo    != null -> "@${replyTo!!.name} ${Strings.reply(language)}..."
                                    else               -> Strings.commentHint(language)
                                },
                                color = Muted, fontSize = 14.sp,
                            )
                        },
                        modifier        = Modifier.weight(1f).focusRequester(focusRequester),
                        shape           = RoundedCornerShape(24.dp),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Divider,
                            cursorColor             = Amber,
                            focusedTextColor        = OnBackground,
                            unfocusedTextColor      = OnBackground,
                            focusedContainerColor   = SurfaceVar,
                            unfocusedContainerColor = SurfaceVar,
                        ),
                        maxLines        = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitComment() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = { submitComment() },
                        enabled  = inputText.isNotBlank(),
                        modifier = Modifier.size(44.dp).background(
                            if (inputText.isNotBlank()) Amber else Muted.copy(alpha = 0.15f), CircleShape,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null,
                            tint = if (inputText.isNotBlank()) Color.Black else Muted,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // ── Long-press menü ───────────────────────────────────────────────────────
    menuTarget?.let { cmt ->
        val isOwner   = myUid.isNotBlank() && cmt.uid == myUid
        val canDelete = isOwner || (chapter?.uid?.let { it == myUid } ?: false)
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor   = HeftSurface,
            title = { Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text  = { Text(cmt.text.take(100) + if (cmt.text.length > 100) "…" else "", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { replyTo = cmt; editTarget = null; menuTarget = null; focusRequester.requestFocus(); keyboardCtrl?.show() }, Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(Strings.replyAction(language), color = Amber)
                    }
                    if (isOwner) TextButton(onClick = { editTarget = cmt; replyTo = null; menuTarget = null }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, null, tint = OnBackground, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(Strings.editAction(language), color = OnBackground)
                    }
                    if (canDelete) TextButton(onClick = { deleteTarget = cmt; menuTarget = null }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(Strings.deleteAction(language), color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { menuTarget = null }, Modifier.fillMaxWidth()) {
                        Text(Strings.cancelAction(language), color = Muted)
                    }
                }
            },
        )
    }

    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text(Strings.deleteCommentTitle(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton  = { TextButton(onClick = { deleteComment(cmt); deleteTarget = null }) { Text(Strings.deleteAction(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) } },
            dismissButton  = { TextButton(onClick = { deleteTarget = null }) { Text(Strings.cancelAction(language), color = Muted) } },
        )
    }
}

// ── Bölüm yorum satırı ───────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChapterCommentRow(
    id: String, uid: String, name: String, photoURL: String, text: String,
    replyTo: String, likes: Int, edited: Boolean,
    canEdit: Boolean, canDelete: Boolean, language: String,
    onLongPress: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(SurfaceVar), contentAlignment = Alignment.Center) {
            if (photoURL.isNotBlank()) {
                AsyncImage(model = photoURL, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(name.firstOrNull()?.uppercase() ?: "?", color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (edited) { Spacer(Modifier.width(4.dp)); Text("· ${Strings.editedLabel(language)}", color = Muted, fontSize = 10.sp) }
            }
            if (replyTo.isNotBlank()) Text("@$replyTo", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (likes > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp)); Text("$likes", color = Muted, fontSize = 11.sp)
                }
                Text(Strings.replyAction(language), color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onReply() })
                if (canEdit) Text(Strings.editAction(language), color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onEdit() })
                if (canDelete) Text(Strings.deleteAction(language), color = Color(0xFFEF4444).copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.clickable { onDelete() })
            }
        }
    }
}


// ── Tam ekran bölüm yazma overlay ────────────────────────────────────────────
@Composable
fun ChapterEditorOverlay(
    title        : String,
    body         : String,
    onTitleChange: (String) -> Unit,
    onBodyChange : (String) -> Unit,
    heading      : String,
    saveLabel    : String,
    canSave      : Boolean,
    language     : String,
    onDismiss    : () -> Unit,
    onSave       : () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HeftSurface)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Üst çubuk: X | Başlık | Kaydet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeftSurface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Muted)
                }
                Text(heading, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(onClick = onSave, enabled = canSave) {
                    Text(
                        saveLabel,
                        color      = if (canSave) Amber else Muted,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                    )
                }
            }
            HorizontalDivider(color = Divider)
            // Bölüm başlığı
            OutlinedTextField(
                value         = title,
                onValueChange = onTitleChange,
                placeholder   = { Text(Strings.chapterTitle(language) + " *", color = Muted) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors        = hfTextFieldColors(),
            )
            HorizontalDivider(color = Divider)
            // İçerik editörü — kalan alanı doldurur
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                RichTextEditor(
                    value       = body,
                    onChange    = onBodyChange,
                    placeholder = "$heading...",
                    modifier    = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── Dialoglar ────────────────────────────────────────────────────────────────
@Composable
private fun CreateSerialDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit, language: String = "tr") {
    var title by remember { mutableStateOf("") }
    var desc  by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text("Yeni Seri", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value       = title,
                    onValueChange = { title = it },
                    label       = { Text(Strings.titleLabel(language)) },
                    singleLine  = true,
                    modifier    = Modifier.fillMaxWidth(),
                    colors      = hfTextFieldColors(),
                )
                OutlinedTextField(
                    value       = desc,
                    onValueChange = { desc = it },
                    label       = { Text("Açıklama") },
                    maxLines    = 3,
                    modifier    = Modifier.fillMaxWidth(),
                    colors      = hfTextFieldColors(),
                )
                OutlinedTextField(
                    value       = genre,
                    onValueChange = { genre = it },
                    label       = { Text("${Strings.genre(language)} (Roman, Şiir…)") },
                    singleLine  = true,
                    modifier    = Modifier.fillMaxWidth(),
                    colors      = hfTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onCreate(title, desc, genre) }) {
                Text(Strings.create(language), color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel(language), color = Muted) } },
    )
}

@Composable
fun hfTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Amber,
    unfocusedBorderColor    = Divider,
    focusedTextColor        = OnBackground,
    unfocusedTextColor      = OnBackground,
    unfocusedContainerColor = SurfaceVar,
    focusedContainerColor   = SurfaceVar,
    focusedLabelColor       = Amber,
    unfocusedLabelColor     = Muted,
    cursorColor             = Amber,
)
