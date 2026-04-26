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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Chapter
import com.heftreng.app.data.model.Serial
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SerialsViewModel

// ── Seri listesi ekranı ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialsScreen(
    navController : NavController,
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
                title = { Text("Seriler", fontWeight = FontWeight.Bold, color = OnBackground) },
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
        color = Surface,
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
    vm            : SerialsViewModel = hiltViewModel(),
) {
    val serial   by vm.selectedSerial.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val loading  by vm.loading.collectAsState()
    var showAddChapter by remember { mutableStateOf(false) }

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
                    SerialHeader(s, onLike = { vm.toggleLikeSerial(s) })
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
                    ChapterRow(ch) { navController.navigate("chapter/${serialId}/${ch.id}") }
                }
            }
        }
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
}

@Composable
private fun SerialHeader(serial: Serial, onLike: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
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
private fun ChapterRow(chapter: Chapter, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
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
                    Text(
                        ch.body,
                        color      = OnBackground,
                        fontSize   = 16.sp,
                        lineHeight = 26.sp,
                    )
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
        containerColor   = Surface,
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
    var title by remember { mutableStateOf("") }
    var body  by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        title = { Text("Yeni Bölüm", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Bölüm Başlığı") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = hfTextFieldColors(),
                )
                OutlinedTextField(
                    value         = body,
                    onValueChange = { body = it },
                    label         = { Text("İçerik") },
                    minLines      = 5,
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    colors        = hfTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank() && body.isNotBlank()) onAdd(title, body) }) {
                Text("Ekle", color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = Muted) } },
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
