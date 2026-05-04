package com.heftreng.app.ui.screens.serials

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Serial
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.component.HeftAvatar
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SerialsViewModel

// ─── SERİ LİSTESİ ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialsScreen(
    navController: NavController,
    language     : String,
    vm           : SerialsViewModel = hiltViewModel(),
) {
    val serials by vm.serials.collectAsState()
    val loading by vm.loading.collectAsState()

    val genres = listOf(
        "" to (if (language == "ku") "Hemû" else "Tümü"),
        "roman"   to (if (language == "ku") "Roman" else "Roman"),
        "siir"    to (if (language == "ku") "Şiir" else "Şiir"),
        "hikaye"  to (if (language == "ku") "Çîrok" else "Hikaye"),
        "deneme"  to (if (language == "ku") "Deneme" else "Deneme"),
        "kurdi"   to "Kurdî",
    )
    var selectedGenre by remember { mutableStateOf("") }

    LaunchedEffect(selectedGenre) { vm.loadSerials(selectedGenre) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (language == "ku") "Pirtûk" else "Kitaplar",
                        fontWeight = FontWeight.Bold, color = OnBackground,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Genre chip listesi
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { (key, label) ->
                    FilterChip(
                        selected = selectedGenre == key,
                        onClick  = { selectedGenre = key },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor    = Primary,
                            selectedLabelColor        = Color.White,
                            containerColor            = SurfaceVar,
                            labelColor                = Muted,
                        ),
                        border   = FilterChipDefaults.filterChipBorder(
                            borderColor         = Divider,
                            selectedBorderColor = Primary,
                            enabled = true, selected = selectedGenre == key,
                        ),
                    )
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (serials.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AutoStories, null, tint = Muted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (language == "ku") "Pirtûk tune" else "Henüz kitap yok", color = Muted)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns        = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                ) {
                    items(serials, key = { it.id }) { serial ->
                        SerialCard(
                            serial  = serial,
                            onClick = { navController.navigate(Screen.SerialDetail.go(serial.id)) },
                        )
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ─── SERİ KART ───────────────────────────────────────────────────────────────
@Composable
private fun SerialCard(serial: Serial, onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().aspectRatio(0.65f),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        shape     = RoundedCornerShape(12.dp),
        border    = BorderStroke(1.dp, Divider),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box {
            if (serial.coverImg.isNotEmpty()) {
                AsyncImage(
                    model              = serial.coverImg,
                    contentDescription = serial.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    serial.bg.takeIf { it.isNotEmpty() }
                                        ?.let { Color(android.graphics.Color.parseColor(it.ifBlank { "#8b5cf6" })) }
                                        ?: Primary,
                                    Surface,
                                )
                            )
                        )
                )
            }
            // Alt gradient overlay
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.85f))
                        )
                    )
            )
            // Metin
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Text(
                    serial.title,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    fontSize   = 13.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    serial.name,
                    fontSize = 10.sp,
                    color    = Color.White.copy(0.7f),
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (serial.genre.isNotEmpty()) {
                        Text(serial.genre, fontSize = 9.sp, color = PrimaryVar)
                    }
                    Text("${serial.chapterCount} bölüm", fontSize = 9.sp, color = Color.White.copy(0.5f))
                }
            }
        }
    }
}

// ─── SERİ DETAY ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialDetailScreen(
    serialId     : String,
    navController: NavController,
    language     : String,
    vm           : SerialsViewModel = hiltViewModel(),
) {
    val serial  by vm.serial.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val loading by vm.loading.collectAsState()
    val rlEntry by vm.rlEntry.collectAsState()

    var showRlSheet by remember { mutableStateOf(false) }

    LaunchedEffect(serialId) { vm.loadSerial(serialId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(serial?.title ?: "...", color = OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { showRlSheet = true }) {
                        Icon(
                            if (rlEntry != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            null, tint = if (rlEntry != null) Primary else Muted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Background,
    ) { padding ->
        if (loading && serial == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                serial?.let { s ->
                    item {
                        // Cover banner
                        Box(
                            Modifier.fillMaxWidth().height(200.dp)
                                .background(Brush.verticalGradient(listOf(PrimaryDark, Surface)))
                        ) {
                            if (s.coverImg.isNotEmpty()) {
                                AsyncImage(
                                    model              = s.coverImg,
                                    contentDescription = null,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize(),
                                )
                            }
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Background)))
                            )
                        }
                    }
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.title, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HeftAvatar(url = s.photoURL, name = s.name, size = 22)
                                Spacer(Modifier.width(6.dp))
                                Text(s.name, color = Muted, fontSize = 13.sp)
                            }
                            if (s.genre.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(99.dp)).background(SurfaceVar)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text(s.genre, fontSize = 11.sp, color = Primary) }
                            }
                            if (s.desc.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(s.desc, fontSize = 14.sp, color = OnSurface, lineHeight = 20.sp)
                            }

                            // Okuma listesi durumu
                            rlEntry?.let {
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceVar).padding(8.dp)
                                ) {
                                    Text("📌 ${it.status.replace("_", " ")}", color = Primary, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Divider)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (language == "ku") "Beş (${chapters.size})" else "Bölümler (${chapters.size})",
                                fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp,
                            )
                        }
                    }
                    items(chapters, key = { it.id }) { ch ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(Screen.ChapterRead.go(serialId, ch.id))
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceVar),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${ch.order}", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.title, color = OnBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (ch.wordCount > 0)
                                    Text("${ch.wordCount} kelime", fontSize = 11.sp, color = Muted)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                        HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Okuma listesi sheet
    if (showRlSheet && serial != null) {
        ReadingListSheet(
            serial    = serial!!,
            current   = rlEntry?.status ?: "",
            language  = language,
            onSelect  = { status -> vm.setReadingStatus(serialId, status, serial!!); showRlSheet = false },
            onDismiss = { showRlSheet = false },
        )
    }
}

// ─── OKUMA LİSTESİ SHEET ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingListSheet(
    serial   : Serial,
    current  : String,
    language : String,
    onSelect : (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val statuses = listOf(
        "okuyorum"         to (if (language == "ku") "Dixwînim" else "Okuyorum") to "📖",
        "okumak_istiyorum" to (if (language == "ku") "Dixwazim bixwînim" else "Okumak İstiyorum") to "🔖",
        "okudum"           to (if (language == "ku") "Xwendim" else "Okudum") to "✅",
        "biraktim"         to (if (language == "ku") "Berda" else "Bıraktım") to "⏸",
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (language == "ku") "Lîsteya Xwendinê" else "Okuma Listesine Ekle",
                fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            statuses.forEach { (pairStatus, emoji) ->
                val (statusKey, statusLabel) = pairStatus
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (current == statusKey) Primary.copy(0.15f) else Color.Transparent)
                        .clickable { onSelect(statusKey) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(statusLabel, color = OnBackground, fontWeight = if (current == statusKey) FontWeight.Bold else FontWeight.Normal)
                    if (current == statusKey) {
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (current.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onSelect("") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (language == "ku") "Ji lîsteyê derxe" else "Listeden Kaldır", color = ErrorColor) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── BÖLÜM OKUMA ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterReadScreen(
    serialId     : String,
    chapterId    : String,
    navController: NavController,
    vm           : SerialsViewModel = hiltViewModel(),
) {
    val chapter by vm.chapter.collectAsState()
    val loading by vm.loading.collectAsState()

    var fontSize by remember { mutableStateOf(16) }

    LaunchedEffect(chapterId) { vm.loadChapter(serialId, chapterId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chapter?.title ?: "...",
                        color    = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { if (fontSize < 22) fontSize++ }) {
                        Text("A+", color = Muted, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { if (fontSize > 12) fontSize-- }) {
                        Text("A−", color = Muted, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Color(0xFF0A0A1A),
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            chapter?.let { ch ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        ch.title,
                        fontWeight = FontWeight.Bold,
                        fontSize   = (fontSize + 4).sp,
                        color      = OnBackground,
                        lineHeight = ((fontSize + 4) * 1.4f).sp,
                    )
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Divider)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        ch.body,
                        fontSize   = fontSize.sp,
                        color      = Color(0xFFD4CEFF),
                        lineHeight = (fontSize * 1.8f).sp,
                    )
                    Spacer(Modifier.height(40.dp))
                    if (ch.wordCount > 0)
                        Text("${ch.wordCount} kelime", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}
