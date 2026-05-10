package com.heftreng.app.ui.screens.serials

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SerialsViewModel

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
                title = { Text(if (language == "ku") "Nivîsandina Pirtûkê" else "Kitap Yazma", fontWeight = FontWeight.Bold, color = OnBackground) },
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
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateSerialDialog(
            onDismiss = { showCreate = false },
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
                    Text("${serial.chapterCount} bölüm", color = Muted, fontSize = 11.sp)
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
) {
    val serial   by vm.selectedSerial.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val loading  by vm.loading.collectAsState()
    val likers   by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var showAddChapter  by remember { mutableStateOf(false) }
    var showLikers      by remember { mutableStateOf(false) }
    var chapterToEdit   by remember { mutableStateOf<Chapter?>(null) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }

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
                    "Bölümler (${chapters.size})",
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
                    Text("Henüz bölüm yok.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }
            } else {
                items(chapters) { ch ->
                    ChapterRow(
                        chapter  = ch,
                        canEdit  = myUid == (serial?.uid ?: ""),
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
            title     = "Beğenenler",
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false; socialVm.clearLikers() },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }

    if (showAddChapter) {
        AddChapterDialog(
            onDismiss = { showAddChapter = false },
            onAdd     = { title, body ->
                vm.addChapter(serialId, title, body)
                showAddChapter = false
            }
        )
    }

    chapterToEdit?.let { ch ->
        EditChapterDialog(
            chapter   = ch,
            onDismiss = { chapterToEdit = null },
            onSave    = { newTitle, newBody ->
                vm.updateChapter(serialId, ch.id, newTitle, newBody)
                chapterToEdit = null
            },
        )
    }

    chapterToDelete?.let { ch ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            containerColor   = HeftSurface,
            title = { Text("Bölümü Sil", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text  = { Text("\"${ch.title}\" bölümünü silmek istediğine emin misin?", color = Muted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteChapter(serialId, ch.id); chapterToDelete = null }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { chapterToDelete = null }) { Text("İptal", color = Muted) } },
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
            Text("${chapter.wordCount} kelime", color = Muted, fontSize = 11.sp)
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
private fun EditChapterDialog(chapter: Chapter, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(chapter.title) }
    var body  by remember { mutableStateOf(chapter.body) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text("Bölümü Düzenle", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Başlık") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                    ),
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text("İçerik") }, minLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank() && body.isNotBlank()) onSave(title, body) }) {
                Text("Kaydet", color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = Muted) } },
    )
}

// ── Bölüm okuma ekranı ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterReadScreen(
    serialId      : String,
    chapterId     : String,
    navController : NavController,
    vm            : SerialsViewModel = hiltViewModel(),
) {
    val chapter by vm.selectedChapter.collectAsState()

    LaunchedEffect(chapterId) { vm.loadChapter(serialId, chapterId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(chapter?.title ?: "Bölüm", color = OnBackground, fontWeight = FontWeight.SemiBold) },
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
                        "Bölüm ${ch.order} · ${ch.wordCount} kelime",
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

// ── Dialoglar ────────────────────────────────────────────────────────────────
@Composable
private fun CreateSerialDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
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
                    label       = { Text("Başlık") },
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
                    label       = { Text("Tür (Roman, Şiir…)") },
                    singleLine  = true,
                    modifier    = Modifier.fillMaxWidth(),
                    colors      = hfTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onCreate(title, desc, genre) }) {
                Text("Oluştur", color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = Muted) } },
    )
}

@Composable
private fun AddChapterDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title      by remember { mutableStateOf("") }
    var body       by remember { mutableStateOf("") }
    var activeFormat by remember { mutableStateOf<String?>(null) }

    // HTML formatlama yardımcısı
    fun applyFormat(tag: String) {
        val open  = "<$tag>"
        val close = "</$tag>"
        body = if (body.endsWith(close)) body else body + "$open$close"
        activeFormat = tag
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = HeftSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Yeni Bölüm", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)

                    // Başlık
                    OutlinedTextField(
                        value         = title,
                        onValueChange = { title = it },
                        label         = { Text("Bölüm Başlığı *") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = hfTextFieldColors(),
                    )

                    // HTML biçimlendirme araç çubuğu
                    Text("Biçimlendirme", color = Muted, fontSize = 11.sp)
                    Row(
                        modifier             = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        data class Fmt(val label: String, val tag: String, val tooltip: String)
                        val formats = listOf(
                            Fmt("B", "b",          "Kalın"),
                            Fmt("I", "i",          "İtalik"),
                            Fmt("U", "u",          "Altı Çizili"),
                            Fmt("H2", "h2",        "Başlık"),
                            Fmt("¶",  "p",         "Paragraf"),
                            Fmt("«»", "blockquote","Alıntı Blok"),
                            Fmt("—",  "hr",        "Yatay Çizgi"),
                        )
                        formats.forEach { fmt ->
                            val isHr = fmt.tag == "hr"
                            TextButton(
                                onClick  = {
                                    body = if (isHr) body + "<hr/>" else {
                                        val o = "<${fmt.tag}>"; val c = "</${fmt.tag}>"
                                        body + "$o$c"
                                    }
                                },
                                modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors   = ButtonDefaults.textButtonColors(contentColor = Amber),
                            ) {
                                Text(
                                    fmt.label,
                                    fontSize   = if (fmt.label == "B") 14.sp else 12.sp,
                                    fontWeight = if (fmt.label == "B") FontWeight.ExtraBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    // İçerik alanı
                    OutlinedTextField(
                        value         = body,
                        onValueChange = { body = it },
                        label         = { Text("İçerik (HTML destekli)") },
                        placeholder   = { Text("<p>Bölüm içeriğinizi buraya yazın...</p>", color = Muted, fontSize = 12.sp) },
                        minLines      = 8,
                        modifier      = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        colors        = hfTextFieldColors(),
                    )

                    // Hızlı şablon satırı
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Diyalog"  to "<p>— ... dedi.</p>",
                            "Sahne"    to "<p>* * *</p>",
                            "Son"      to "<p>~ Son ~</p>",
                        ).forEach { (label, snippet) ->
                            OutlinedButton(
                                onClick = { body += "\n" + snippet },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                            ) {
                                Text(label, color = Muted, fontSize = 10.sp)
                            }
                        }
                    }

                    // Kelime sayısı önizleme
                    val wordCount = body.replace(Regex("<[^>]+>"), "").trim()
                        .split(Regex("\\s+")).count { it.isNotBlank() }
                    Text("$wordCount kelime", color = Muted, fontSize = 11.sp)

                    // Butonlar
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick  = { if (title.isNotBlank() && body.isNotBlank()) onAdd(title, body) },
                            enabled  = title.isNotBlank() && body.isNotBlank(),
                            colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = androidx.compose.ui.graphics.Color.Black),
                        ) {
                            Text("Bölümü Ekle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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
