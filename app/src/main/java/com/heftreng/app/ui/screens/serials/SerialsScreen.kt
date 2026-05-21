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
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SerialsViewModel
import com.heftreng.app.viewmodel.SettingsViewModel

// ── Seri listesi ekranı ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialsScreen(
    navController : NavController,
    language      : String = "tr",
    vm            : SerialsViewModel = hiltViewModel(),
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
    vm            : SerialsViewModel  = hiltViewModel(),
    socialVm      : com.heftreng.app.viewmodel.SocialViewModel = hiltViewModel(),
    settingsVm    : SettingsViewModel = hiltViewModel(),
) {
    val serial   by vm.selectedSerial.collectAsState()
    val chapters by vm.chapters.collectAsState()
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
            onTitleChange = { addTitle = it },
            onBodyChange  = { addBody  = it },
            heading       = Strings.newChapter(language),
            saveLabel     = Strings.save(language),
            wordCount     = addBody.replace(Regex("<[^>]+>"), "").trim()
                                .split(Regex("\s+")).count { it.isNotBlank() },
            canSave       = addTitle.isNotBlank() && addBody.isNotBlank(),
            language      = language,
            onDismiss     = { showAddChapter = false; addTitle = ""; addBody = "" },
            onSave        = {
                vm.addChapter(serialId, addTitle, addBody)
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
            onTitleChange = { editTitle = it },
            onBodyChange  = { editBody  = it },
            heading       = Strings.editChapter(language),
            saveLabel     = Strings.save(language),
            wordCount     = editBody.replace(Regex("<[^>]+>"), "").trim()
                                .split(Regex("\s+")).count { it.isNotBlank() },
            canSave       = editTitle.isNotBlank() && editBody.isNotBlank(),
            language      = language,
            onDismiss     = { chapterToEdit = null },
            onSave        = {
                vm.updateChapter(serialId, ch.id, editTitle, editBody)
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
                TextButton(onClick = { vm.deleteChapter(serialId, ch.id); chapterToDelete = null }) {
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

@Composable
// ── Bölüm okuma ekranı ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterReadScreen(
    serialId      : String,
    chapterId     : String,
    navController : NavController,
    vm            : SerialsViewModel = hiltViewModel(),
    settingsVm    : SettingsViewModel = hiltViewModel(),
) {
    val chapter  by vm.selectedChapter.collectAsState()
    val language by settingsVm.language.collectAsState()

    LaunchedEffect(chapterId) { vm.loadChapter(serialId, chapterId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(chapter?.title ?: Strings.chapter(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    var liked by remember { mutableStateOf(false) }
                    LaunchedEffect(chapterId) { vm.isChapterLiked(chapterId) { liked = it } }
                    IconButton(onClick = { vm.toggleLikeChapter(serialId, chapterId); liked = !liked }) {
                        Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            null, tint = if (liked) Color(0xFFEF4444) else Muted)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            item {
                chapter?.let { ch ->
                    Text(
                        "${Strings.chapter(language)} ${ch.order} · ${Strings.wordCount(language, ch.wordCount)}",
                        color    = Muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (ch.body.contains("<") && ch.body.contains(">")) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory  = { ctx ->
                                TextView(ctx).apply {
                                    setTextColor(android.graphics.Color.parseColor("#E5E5EA"))
                                    textSize = 16f
                                    setLineSpacing(0f, 1.6f)
                                    setPadding(0, 0, 0, 0)
                                }
                            },
                            update = { tv ->
                                @Suppress("DEPRECATION")
                                val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                                    Html.fromHtml(ch.body, Html.FROM_HTML_MODE_COMPACT)
                                else Html.fromHtml(ch.body)
                                tv.text = spanned
                            }
                        )
                    } else {
                        Text(
                            ch.body,
                            color      = OnBackground,
                            fontSize   = 16.sp,
                            lineHeight = 26.sp,
                        )
                    }
                } ?: CircularProgressIndicator(color = Amber)
            }
        }
    }
}

// ── Tam ekran bölüm yazma overlay (Dialog yerine — IME sorunu bypass) ────────
@Composable
fun ChapterEditorOverlay(
    title        : String,
    body         : String,
    onTitleChange: (String) -> Unit,
    onBodyChange : (String) -> Unit,
    heading      : String,
    saveLabel    : String,
    wordCount    : Int,
    canSave      : Boolean,
    language     : String,
    onDismiss    : () -> Unit,
    onSave       : () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HeftSurface)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Başlık çubuğu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeftSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Muted)
                }
                Text(heading, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(
                    onClick = onSave,
                    enabled = canSave,
                ) {
                    Text(
                        saveLabel,
                        color      = if (canSave) Amber else Muted,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                    )
                }
            }

            HorizontalDivider(color = Divider)

            // Başlık input
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

            // İçerik editörü + kelime sayısı — imePadding burada
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                RichTextEditor(
                    value       = body,
                    onChange    = onBodyChange,
                    placeholder = heading + "...",
                    modifier    = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = Divider)
                Text(
                    Strings.wordCount(language, wordCount),
                    color    = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
