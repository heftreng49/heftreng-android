package com.heftreng.app.ui.screens.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Book
import com.heftreng.app.ui.screens.auth.heftrangFieldColors
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.BooksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    vm: BooksViewModel = hiltViewModel(),
) {
    val books       by vm.books.collectAsState()
    val readingList by vm.readingList.collectAsState()
    val loading     by vm.loading.collectAsState()
    var tab         by remember { mutableStateOf(0) }
    var showAdd     by remember { mutableStateOf(false) }
    val tabs = listOf("Hemû", "Kurdî", "Okuma Listesi")

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title  = { Text("Kitêbxane / Kitaplık", fontWeight = FontWeight.Bold, color = onBg()) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, "Kitêb zêde bike", tint = accent())
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Özet kart
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = surf(),
            ) {
                Row(
                    modifier              = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BookStat(books.size.toString(),                                    "Toplam")
                    BookStat(books.count { it.language == "ku" }.toString(),           "Kurdî")
                    BookStat(readingList.count { it.status == "okuyorum" }.toString(), "Okunuyor")
                    BookStat(readingList.count { it.status == "okudum" }.toString(),   "Bitti")
                }
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor   = bg(),
                contentColor     = accent(),
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tab]),
                        color    = accent(),
                    )
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected             = tab == i,
                        onClick              = { tab = i },
                        text                 = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        selectedContentColor = accent(),
                        unselectedContentColor = muted(),
                    )
                }
            }

            val filtered = when (tab) {
                0    -> books
                1    -> books.filter { it.language == "ku" }
                else -> books.filter { b -> readingList.any { it.sid == b.id } }
            }

            if (loading && books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent())
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MenuBook, null, tint = muted(), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (tab == 2) "Okuma listesi boş" else "Kitap bulunamadı", color = muted())
                        if (tab != 2) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showAdd = true }) {
                                Text("+ İlk kitabı ekle", color = accent())
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns             = GridCells.Fixed(2),
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { book ->
                        val status = vm.getReadingStatus(book.id)
                        BookCard(
                            book   = book,
                            status = status,
                            onStatusChange = { newStatus -> vm.setReadingStatus(book, newStatus) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddBookSheet(
            onDismiss = { showAdd = false },
            onSubmit  = { title, author, genre, desc, lang ->
                vm.addBook(title, author, genre, desc, lang)
                showAdd = false
            }
        )
    }
}

@Composable
fun BookCard(
    book          : Book,
    status        : String?,
    onStatusChange: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(12.dp), color = surf()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Kapak
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(surfVar()),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverURL.isNotBlank()) {
                    AsyncImage(
                        model = book.coverURL, contentDescription = book.title,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MenuBook, null, tint = accent(), modifier = Modifier.size(40.dp))
                        if (book.language == "ku")
                            Text("KU", color = accent(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Durum rozeti
                if (status != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape    = RoundedCornerShape(6.dp),
                        color    = statusColor(status),
                    ) {
                        Text(statusLabel(status), color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }

            // Bilgi
            Column(modifier = Modifier.padding(10.dp)) {
                Text(book.title, color = onBg(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank())
                    Text(book.author, color = muted(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (book.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("%.1f".format(book.rating), color = Color(0xFFF59E0B), fontSize = 11.sp)
                        Text(" (${book.ratingCount})", color = muted(), fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Okuma listesi butonu
                Box {
                    OutlinedButton(
                        onClick  = { showMenu = true },
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = accent()),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, accent().copy(alpha = 0.4f)),
                    ) {
                        Text(if (status != null) statusLabel(status) else "+ Ekle",
                            fontSize = 10.sp, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded        = showMenu,
                        onDismissRequest= { showMenu = false },
                        containerColor  = surf(),
                    ) {
                        listOf(
                            "okuyorum"          to "📖 Okuyorum",
                            "okumak_istiyorum"  to "📌 Okumak İstiyorum",
                            "okudum"            to "✅ Okudum",
                            "biraktim"          to "⏸ Bıraktım",
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text    = { Text(label, color = if (status == key) accent() else onBg(), fontSize = 13.sp) },
                                onClick = { onStatusChange(key); showMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = accent(), fontSize = 18.sp)
        Text(label, color = muted(), fontSize = 10.sp)
    }
}

fun statusLabel(status: String) = when (status) {
    "okuyorum"         -> "Okuyorum"
    "okumak_istiyorum" -> "Okuyacağım"
    "okudum"           -> "Okudum"
    "biraktim"         -> "Bıraktım"
    else               -> status
}

fun statusColor(status: String) = when (status) {
    "okuyorum"         -> Color(0xFF3B82F6)
    "okudum"           -> Color(0xFF10B981)
    "biraktim"         -> Color(0xFF71717A)
    else               -> Color(0xFFF59E0B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookSheet(
    onDismiss: () -> Unit,
    onSubmit : (title: String, author: String, genre: String, desc: String, lang: String) -> Unit,
) {
    var title  by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var genre  by remember { mutableStateOf("") }
    var desc   by remember { mutableStateOf("") }
    var lang   by remember { mutableStateOf("ku") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = surf()) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("İptal / Betal", color = muted()) }
                Text("Kitap Ekle / Kitêb Zêde bike", fontWeight = FontWeight.SemiBold, color = onBg(), fontSize = 13.sp)
                TextButton(onClick = {
                    if (title.isNotBlank()) onSubmit(title, author, genre, desc, lang)
                }) {
                    Text("Kaydet", color = accent(), fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Kitap Adı / Sernavê kitêbê") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangFieldColors(), singleLine = true)

            OutlinedTextField(value = author, onValueChange = { author = it },
                label = { Text("Yazar / Nivîskar") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangFieldColors(), singleLine = true)

            OutlinedTextField(value = genre, onValueChange = { genre = it },
                label = { Text("Tür / Celeb (Roman, Şiir...)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangFieldColors(), singleLine = true)

            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("Açıklama / Danasîn (isteğe bağlı)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                shape = RoundedCornerShape(12.dp), colors = heftrangFieldColors())

            // Dil seçici
            Text("Dil / Ziman", color = muted(), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ku" to "Kurdî", "tr" to "Türkçe", "ar" to "Arapça", "en" to "İngilizce").forEach { (code, label) ->
                    FilterChip(
                        selected = lang == code,
                        onClick  = { lang = code },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = accent().copy(alpha = 0.2f),
                            selectedLabelColor       = accent(),
                            unselectedContainerColor = surfVar(),
                            labelColor               = muted(),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
