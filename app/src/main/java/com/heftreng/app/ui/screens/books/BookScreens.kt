package com.heftreng.app.ui.screens.books

// ═══════════════════════════════════════════════════════
//  BookScreens — Kitap listesi, detay, bölüm okuma
//
//  Tema (site) ile tam uyumlu:
//  - Firestore: books/{bookId}
//  - Firestore: books/{bookId}/chapters/{chapterId}
//  - chapterLikes/{bookId_uid} — beğeni
//  - readingLists/{uid}/books/{bookId} — okuma listesi
// ═══════════════════════════════════════════════════════

import androidx.compose.foundation.*
import android.text.Html
import android.text.Spanned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.BookChapter
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.screens.social.UserAvatar
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.BookViewModel
import com.heftreng.app.viewmodel.SocialViewModel

// ── Kitap Listesi ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : BookViewModel = hiltViewModel(),
) {
    val books   by vm.books.collectAsState()
    val loading by vm.loading.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadBooks() }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(if (language == "ku") "Pirtûk" else "Kitaplar", fontWeight = FontWeight.Bold, color = OnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, null, tint = Amber)
                    }
                }
            )
        }
    ) { padding ->
        if (loading && books.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.MenuBook, null, tint = Divider, modifier = Modifier.size(56.dp))
                    Text(if (language == "ku") "Pirtûk tune" else "Henüz kitap yok", color = Muted)
                    TextButton(onClick = { showCreate = true }) {
                        Text("+ ${if (language == "ku") "Pirtûk Zêde Bike" else "Kitap Ekle"}", color = Amber)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book    = book,
                        onClick = { navController.navigate("book/${book.id}") },
                        onLike  = { vm.toggleLikeBook(book) },
                        onProfile = { navController.navigate("profile/${book.uid}") },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateBookDialog(
            language  = language,
            onDismiss = { showCreate = false },
            onCreate  = { title, desc, genre ->
                vm.createBook(title, desc, genre)
                showCreate = false
            },
        )
    }
}

// ── Kitap Detay ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId       : String,
    navController: NavController,
    language     : String = "tr",
    vm           : BookViewModel  = hiltViewModel(),
    socialVm     : SocialViewModel = hiltViewModel(),
) {
    val book     by vm.selectedBook.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val loading  by vm.loading.collectAsState()
    val likers   by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()

    var showLikers by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) { vm.loadBook(bookId) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "", color = OnBackground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        if (loading && book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        val b = book ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Kapak + bilgi
            item {
                BookDetailHeader(
                    book       = b,
                    onProfile  = { navController.navigate("profile/${b.uid}") },
                    onLike     = { vm.toggleLikeBook(b) },
                    onShowLikers = {
                        socialVm.loadPostLikers(b.id)
                        showLikers = true
                    },
                )
            }

            // Bölüm listesi başlığı
            if (chapters.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FormatListNumbered, null, tint = Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (language == "ku") "Beş" else "Bölüm"}ler (${chapters.size})",
                            color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                    }
                }
                items(chapters, key = { it.id }) { chapter ->
                    ChapterListItem(
                        chapter = chapter,
                        onClick = { navController.navigate("book_chapter/${b.id}/${chapter.id}") },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                }
            } else if (!loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(if (language == "ku") "Beş tune" else "Henüz bölüm eklenmemiş", color = Muted, fontSize = 13.sp)
                    }
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
}

// ── Bölüm Okuma ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChapterReadScreen(
    bookId       : String,
    chapterId    : String,
    navController: NavController,
    vm           : BookViewModel = hiltViewModel(),
) {
    val chapter  by vm.selectedChapter.collectAsState()
    val chapters by vm.chapters.collectAsState()

    LaunchedEffect(bookId, chapterId) {
        vm.loadBook(bookId)
        vm.loadChapter(bookId, chapterId)
    }

    val currentIndex = chapters.indexOfFirst { it.id == chapterId }
    val prevChapter  = if (currentIndex > 0) chapters[currentIndex - 1] else null
    val nextChapter  = if (currentIndex >= 0 && currentIndex < chapters.size - 1) chapters[currentIndex + 1] else null

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(chapter?.title ?: "", color = OnBackground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            // Önceki / Sonraki bölüm gezinme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeftSurface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (prevChapter != null) {
                    TextButton(onClick = { navController.navigate("book_chapter/$bookId/${prevChapter.id}") { popUpTo("book_chapter/$bookId/$chapterId") { inclusive = true } } }) {
                        Icon(Icons.Default.ChevronLeft, null, tint = Primary)
                        Text("Önceki", color = Primary, fontSize = 13.sp)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (nextChapter != null) {
                    TextButton(onClick = { navController.navigate("book_chapter/$bookId/${nextChapter.id}") { popUpTo("book_chapter/$bookId/$chapterId") { inclusive = true } } }) {
                        Text("Sonraki", color = Amber, fontSize = 13.sp)
                        Icon(Icons.Default.ChevronRight, null, tint = Amber)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    ) { padding ->
        if (chapter == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }
        val ch = chapter!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(ch.title, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp)
            if (ch.wordCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text("${ch.wordCount} kelime", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(16.dp))
            // HTML içerik render — body HTML tag içeriyorsa AndroidView/TextView kullan
            val ctx = LocalContext.current
            if (ch.body.contains("<") && ch.body.contains(">")) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory  = { context ->
                        TextView(context).apply {
                            setTextColor(android.graphics.Color.parseColor("#E5E5EA"))
                            textSize = 16f
                            setLineSpacing(0f, 1.6f)
                            setPadding(0, 0, 0, 0)
                            setLinkTextColor(android.graphics.Color.parseColor("#F59E0B"))
                            movementMethod = android.text.method.LinkMovementMethod.getInstance()
                        }
                    },
                    update = { tv ->
                        val htmlStr = ch.body
                            .replace("<hr>", "<hr/>")
                            .replace("<hr >", "<hr/>")
                        @Suppress("DEPRECATION")
                        val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml(htmlStr, Html.FROM_HTML_MODE_COMPACT)
                        else Html.fromHtml(htmlStr)
                        tv.text = spanned
                    }
                )
            } else {
                // Düz metin
                Text(
                    ch.body,
                    color      = OnSurface,
                    fontSize   = 16.sp,
                    lineHeight = 28.sp,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Alt bileşenler ───────────────────────────────────────────────────────────

@Composable
fun BookCard(
    book      : Book,
    onClick   : () -> Unit,
    onLike    : () -> Unit,
    onProfile : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeftSurface)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Kapak
        Box(
            modifier = Modifier
                .size(64.dp, 88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverImg.isNotBlank()) {
                AsyncImage(
                    model = book.coverImg, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.MenuBook, null, tint = Muted, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // Yazar
            Row(
                modifier = Modifier.clickable { onProfile() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(name = book.name, photoURL = book.photoURL, size = 18)
                Spacer(Modifier.width(5.dp))
                Text(book.name, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            if (book.desc.isNotBlank()) {
                Text(book.desc, color = OnSurface, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
            }
            if (book.genre.isNotBlank()) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceVar).padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(book.genre, color = Primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.clickable { onLike() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (book.isLikedByMe) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        null, tint = if (book.isLikedByMe) Color(0xFFEF4444) else Muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${book.likes}", color = Muted, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatListNumbered, null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${book.chapterCount} bölüm", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun BookDetailHeader(
    book        : Book,
    onProfile   : () -> Unit,
    onLike      : () -> Unit,
    onShowLikers: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Kapak banner
        Box(
            Modifier.fillMaxWidth().height(180.dp)
                .background(
                    if (book.bg.isNotBlank())
                        try { Color(android.graphics.Color.parseColor(book.bg)) } catch (_: Exception) { SurfaceVar }
                    else SurfaceVar
                )
        ) {
            if (book.coverImg.isNotBlank()) {
                AsyncImage(
                    model = book.coverImg, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f },
                )
            }
            // Kapak kitap görseli — ortada dik
            if (book.coverImg.isNotBlank()) {
                AsyncImage(
                    model = book.coverImg, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.Center).size(100.dp, 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shadow(16.dp, RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    Modifier.align(Alignment.Center).size(100.dp, 140.dp)
                        .clip(RoundedCornerShape(8.dp)).background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.MenuBook, null, tint = Muted, modifier = Modifier.size(40.dp)) }
            }
        }

        Column(Modifier.padding(16.dp)) {
            Text(book.title, color = OnBackground, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.clickable { onProfile() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(name = book.name, photoURL = book.photoURL, size = 22)
                Spacer(Modifier.width(6.dp))
                Text(book.name, color = Muted, fontSize = 13.sp)
            }
            if (book.genre.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceVar).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(book.genre, color = Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (book.desc.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(book.desc, color = OnSurface, fontSize = 14.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Beğen
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceVar)
                        .clickable { onLike() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (book.isLikedByMe) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        null, tint = if (book.isLikedByMe) Color(0xFFEF4444) else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${book.likes}", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                // Beğenenler listesi
                TextButton(onClick = onShowLikers) {
                    Text("Beğenenler", color = Muted, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = Divider)
    }
}

@Composable
fun ChapterListItem(chapter: BookChapter, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            Text("${chapter.order + 1}", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chapter.title, color = OnBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (chapter.wordCount > 0) {
                Text("${chapter.wordCount} kelime", color = Muted, fontSize = 11.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

// ── Kitap oluşturma dialog ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBookDialog(
    language  : String,
    onDismiss : () -> Unit,
    onCreate  : (String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var desc  by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = Amber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (language == "ku") "Pirtûk Nû" else "Yeni Kitap", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(if (language == "ku") "Sernavê Pirtûkê *" else "Kitap Adı *") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = bookTextFieldColors(),
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text(if (language == "ku") "Danasîn" else "Açıklama") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                    colors = bookTextFieldColors(),
                )
                OutlinedTextField(
                    value = genre, onValueChange = { genre = it },
                    label = { Text(if (language == "ku") "Cûre" else "Tür") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = bookTextFieldColors(),
                    leadingIcon = { Icon(Icons.Default.Category, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (title.isNotBlank()) onCreate(title.trim(), desc.trim(), genre.trim()) },
                enabled  = title.isNotBlank(),
            ) { Text(if (language == "ku") "Çêke" else "Oluştur", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (language == "ku") "Betal bike" else "İptal", color = Muted) }
        },
    )
}

@Composable
private fun bookTextFieldColors() = OutlinedTextFieldDefaults.colors(
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

private fun Modifier.shadow(elevation: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape): Modifier = this
