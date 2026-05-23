package com.heftreng.app.ui.screens.library

// ═══════════════════════════════════════════════════════════════════════════
//  LibraryScreen  —  Kütüphane / Pirtûkxane
//
//  4 sekme:
//    • Alıntılar  (Gotinên Bijarte)  — collectionGroup("quotes")
//    • İncelemeler (Nirxandin)       — collectionGroup("reviews")
//    • Yazarlar   (Nivîskar)         — authors collection
//    • Kitaplar   (Pirtûk)           — library_books collection
//
//  Her sekmede sağ altta FAB ile yeni kayıt eklenebilir.
// ═══════════════════════════════════════════════════════════════════════════

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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Author
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  Ana ekran
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    language     : String,
    vm           : LibraryViewModel = hiltViewModel(),
) {
    val tabs = listOf(
        Strings.libraryTabQuotes(language),
        Strings.libraryTabReviews(language),
        Strings.libraryTabAuthors(language),
        Strings.libraryTabBooks(language),
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Firestore state ────────────────────────────────────────────────────
    val db    = remember { FirebaseFirestore.getInstance() }
    val auth  = remember { FirebaseAuth.getInstance() }

    var quotes  by remember { mutableStateOf<List<BookQuote>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<BookReview>>(emptyList()) }
    val authors by vm.authors.collectAsState()
    var books   by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // İlk yükleme
    LaunchedEffect(Unit) {
        loading = true
        try {
            // Alıntılar
            val qSnap = db.collectionGroup("quotes")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50).get().await()
            quotes = qSnap.documents.mapNotNull { it.toObject(BookQuote::class.java)?.copy(id = it.id) }

            // İncelemeler
            val rSnap = db.collectionGroup("reviews")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50).get().await()
            reviews = rSnap.documents.mapNotNull { it.toObject(BookReview::class.java)?.copy(id = it.id) }

            // Yazarlar (ViewModel üzerinden — zaten var)
            vm.loadAuthors()

            // Kitaplar
            val bSnap = db.collection("library_books")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50).get().await()
            books = bSnap.documents.mapNotNull { it.toObject(LibraryBook::class.java)?.copy(id = it.id) }
        } catch (_: Exception) { }
        loading = false
    }

    // ── Dialog state ───────────────────────────────────────────────────────
    var showAddQuoteDialog  by remember { mutableStateOf(false) }
    var showAddReviewDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = Strings.libraryTitle(language),
                        color      = OnBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick           = { showAddQuoteDialog = true },
                    containerColor    = Primary,
                    contentColor      = Color.White,
                    shape             = CircleShape,
                ) { Icon(Icons.Filled.FormatQuote, contentDescription = null) }
                1 -> FloatingActionButton(
                    onClick           = { showAddReviewDialog = true },
                    containerColor    = Primary,
                    contentColor      = Color.White,
                    shape             = CircleShape,
                ) { Icon(Icons.Filled.RateReview, contentDescription = null) }
                else -> { /* Yazarlar ve Kitaplar sekmesinde FAB yok */ }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Tab Row ───────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Primary,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = Primary,
                    )
                },
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = {
                            Text(
                                text     = title,
                                fontSize = 13.sp,
                                maxLines = 1,
                                color    = if (selectedTab == index) Primary else Muted,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // ── İçerik ───────────────────────────────────────────────────
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                when (selectedTab) {
                    0 -> LibraryQuotesTab(
                        quotes      = quotes,
                        language    = language,
                        navController = navController,
                    )
                    1 -> LibraryReviewsTab(
                        reviews   = reviews,
                        language  = language,
                        navController = navController,
                    )
                    2 -> LibraryAuthorsTab(
                        authors   = authors,
                        language  = language,
                        navController = navController,
                    )
                    3 -> LibraryBooksTab(
                        books     = books,
                        language  = language,
                        navController = navController,
                    )
                }
            }
        }
    }

    // ── Alıntı Ekle Dialog ────────────────────────────────────────────────
    if (showAddQuoteDialog) {
        LibraryAddQuoteDialog(
            language = language,
            onDismiss = { showAddQuoteDialog = false },
            onConfirm = { bookTitle, authorName, text ->
                showAddQuoteDialog = false
                val uid  = auth.currentUser?.uid ?: return@LibraryAddQuoteDialog
                val user = auth.currentUser
                // Basit kayıt: önce veya mevcut library_book ara, sonra kaydet
                // Burada vm.addBookQuote benzeri bir flow yerine doğrudan ekliyoruz
                // (seçili book nesnesi olmadığı için hafifletilmiş versiyon)
                val quoteData = hashMapOf(
                    "bookTitle"       to bookTitle,
                    "authorName"      to authorName,
                    "text"            to text,
                    "uid"             to uid,
                    "userDisplayName" to (user?.displayName ?: ""),
                    "userPhotoURL"    to (user?.photoUrl?.toString() ?: ""),
                    "ts"              to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "bookId"          to "",
                    "authorId"        to "",
                    "feedPostId"      to "",
                    "likesCount"      to 0,
                )
                FirebaseFirestore.getInstance()
                    .collection("library_books")
                    .document("_standalone_quotes")
                    .collection("quotes")
                    .add(quoteData)
            },
        )
    }

    // ── İnceleme Ekle Dialog ──────────────────────────────────────────────
    if (showAddReviewDialog) {
        LibraryAddReviewDialog(
            language  = language,
            books     = books,
            onDismiss = { showAddReviewDialog = false },
            onConfirm = { book, reviewText, rating ->
                showAddReviewDialog = false
                vm.addBookReview(book, reviewText, rating)
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SEKME 1 — Alıntılar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryQuotesTab(
    quotes       : List<BookQuote>,
    language     : String,
    navController: NavController,
) {
    if (quotes.isEmpty()) {
        LibraryEmptyState(
            icon    = Icons.Outlined.FormatQuote,
            message = Strings.libraryNoQuotes(language),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(quotes, key = { it.id }) { quote ->
            LibraryQuoteCard(
                quote        = quote,
                navController = navController,
            )
        }
    }
}

@Composable
private fun LibraryQuoteCard(
    quote        : BookQuote,
    navController: NavController,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Alıntı metni
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.FormatQuote,
                    contentDescription = null,
                    tint   = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = quote.text,
                    color      = OnBackground,
                    fontSize   = 15.sp,
                    fontStyle  = FontStyle.Italic,
                    lineHeight = 22.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            // Kitap / Yazar bilgisi
            if (quote.bookTitle.isNotBlank() || quote.authorName.isNotBlank()) {
                Text(
                    text      = buildString {
                        if (quote.bookTitle.isNotBlank()) append("— ${quote.bookTitle}")
                        if (quote.authorName.isNotBlank()) append(", ${quote.authorName}")
                    },
                    color     = Primary,
                    fontSize  = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier  = Modifier.clickable {
                        if (quote.bookId.isNotBlank())
                            navController.navigate("library_book_detail/${quote.bookId}")
                        else if (quote.authorId.isNotBlank())
                            navController.navigate("author_detail/${quote.authorId}")
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            // Paylaşan kullanıcı
            UserMiniRow(
                displayName = quote.userDisplayName,
                photoURL    = quote.userPhotoURL,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SEKME 2 — İncelemeler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryReviewsTab(
    reviews      : List<BookReview>,
    language     : String,
    navController: NavController,
) {
    if (reviews.isEmpty()) {
        LibraryEmptyState(
            icon    = Icons.Outlined.RateReview,
            message = Strings.libraryNoReviews(language),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(reviews, key = { it.id }) { review ->
            LibraryReviewCard(review = review, navController = navController)
        }
    }
}

@Composable
private fun LibraryReviewCard(
    review       : BookReview,
    navController: NavController,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Kitap adı + yıldız
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = review.bookId.isNotBlank()) {
                        navController.navigate("library_book_detail/${review.bookId}")
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = review.bookTitle.ifBlank { review.authorName },
                    color      = Primary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f),
                )
                if (review.rating > 0f) {
                    StarRatingRow(rating = review.rating)
                }
            }
            Spacer(Modifier.height(8.dp))
            // İnceleme metni
            Text(
                text      = review.text,
                color     = OnBackground,
                fontSize  = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(10.dp))
            UserMiniRow(
                displayName = review.userDisplayName,
                photoURL    = review.userPhotoURL,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SEKME 3 — Yazarlar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryAuthorsTab(
    authors      : List<Author>,
    language     : String,
    navController: NavController,
) {
    if (authors.isEmpty()) {
        LibraryEmptyState(
            icon    = Icons.Outlined.Person,
            message = Strings.libraryNoAuthors(language),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(authors, key = { it.id }) { author ->
            LibraryAuthorRow(
                author       = author,
                navController = navController,
            )
        }
    }
}

@Composable
private fun LibraryAuthorRow(
    author       : Author,
    navController: NavController,
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("author_detail/${author.id}") },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            AsyncImage(
                model             = author.photoURL.ifBlank { null },
                contentDescription = author.name,
                modifier          = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.2f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = author.name,
                    color      = OnBackground,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (author.nationality.isNotBlank()) {
                    Text(
                        text     = author.nationality,
                        color    = Muted,
                        fontSize = 12.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (author.bookCount > 0)
                        StatChip("${author.bookCount}", Icons.Filled.MenuBook)
                    if (author.quoteCount > 0)
                        StatChip("${author.quoteCount}", Icons.Filled.FormatQuote)
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SEKME 4 — Kitaplar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryBooksTab(
    books        : List<LibraryBook>,
    language     : String,
    navController: NavController,
) {
    if (books.isEmpty()) {
        LibraryEmptyState(
            icon    = Icons.Outlined.AutoStories,
            message = Strings.libraryNoBooks(language),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(books, key = { it.id }) { book ->
            LibraryBookRow(
                book         = book,
                navController = navController,
            )
        }
    }
}

@Composable
private fun LibraryBookRow(
    book         : LibraryBook,
    navController: NavController,
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("library_book_detail/${book.id}") },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Kapak
            if (book.coverImg.isNotBlank()) {
                AsyncImage(
                    model             = book.coverImg,
                    contentDescription = book.title,
                    modifier          = Modifier
                        .size(width = 54.dp, height = 76.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HeftSurface),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = book.title,
                    color      = OnBackground,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    text     = book.authorName,
                    color    = Primary,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (book.avgRating > 0f) StarRatingRow(rating = book.avgRating)
                    if (book.quoteCount > 0)
                        StatChip("${book.quoteCount}", Icons.Filled.FormatQuote)
                }
                if (book.genre.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = book.genre, color = Muted, fontSize = 11.sp)
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint     = Muted,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diyaloglar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryAddQuoteDialog(
    language : String,
    onDismiss: () -> Unit,
    onConfirm: (bookTitle: String, authorName: String, text: String) -> Unit,
) {
    var quoteText  by remember { mutableStateOf("") }
    var bookTitle  by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                Strings.libraryAddQuote(language),
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = quoteText,
                    onValueChange = { quoteText = it },
                    label         = { Text(Strings.libraryQuoteHint(language), color = Muted) },
                    minLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = dialogFieldColors(),
                )
                OutlinedTextField(
                    value         = bookTitle,
                    onValueChange = { bookTitle = it },
                    label         = { Text(Strings.libraryQuoteBook(language), color = Muted) },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = dialogFieldColors(),
                )
                OutlinedTextField(
                    value         = authorName,
                    onValueChange = { authorName = it },
                    label         = { Text(Strings.libraryQuoteAuthor(language), color = Muted) },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = dialogFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (quoteText.isNotBlank()) onConfirm(bookTitle, authorName, quoteText) },
                enabled  = quoteText.isNotBlank(),
            ) {
                Text(Strings.save(language), color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel(language), color = Muted)
            }
        },
    )
}

@Composable
private fun LibraryAddReviewDialog(
    language : String,
    books    : List<LibraryBook>,
    onDismiss: () -> Unit,
    onConfirm: (book: LibraryBook, reviewText: String, rating: Float) -> Unit,
) {
    var reviewText   by remember { mutableStateOf("") }
    var selectedBook by remember { mutableStateOf<LibraryBook?>(null) }
    var rating       by remember { mutableStateOf(4f) }
    var expanded     by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                Strings.libraryAddReview(language),
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Kitap seçici
                Box {
                    OutlinedTextField(
                        value         = selectedBook?.title ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(Strings.libraryReviewBook(language), color = Muted) },
                        trailingIcon  = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Filled.ArrowDropDown, null, tint = Muted)
                            }
                        },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = dialogFieldColors(),
                    )
                    DropdownMenu(
                        expanded        = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        books.forEach { book ->
                            DropdownMenuItem(
                                text    = { Text(book.title, color = OnBackground) },
                                onClick = { selectedBook = book; expanded = false },
                            )
                        }
                    }
                }
                // İnceleme metni
                OutlinedTextField(
                    value         = reviewText,
                    onValueChange = { reviewText = it },
                    label         = { Text(Strings.libraryReviewHint(language), color = Muted) },
                    minLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = dialogFieldColors(),
                )
                // Yıldız puanı
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Strings.libraryReviewRating(language)}: ${rating.roundToInt()}",
                        color    = OnBackground,
                        fontSize = 13.sp,
                        modifier = Modifier.width(90.dp),
                    )
                    Slider(
                        value         = rating,
                        onValueChange = { rating = it },
                        valueRange    = 1f..5f,
                        steps         = 3,
                        colors        = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary),
                        modifier      = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    val book = selectedBook
                    if (book != null && reviewText.isNotBlank()) onConfirm(book, reviewText, rating)
                },
                enabled  = selectedBook != null && reviewText.isNotBlank(),
            ) {
                Text(Strings.save(language), color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel(language), color = Muted)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Yardımcı bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryEmptyState(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
) {
    Box(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment    = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(text = message, color = Muted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun UserMiniRow(displayName: String, photoURL: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model             = photoURL.ifBlank { null },
            contentDescription = displayName,
            modifier          = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.15f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text     = displayName.ifBlank { "—" },
            color    = Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StarRatingRow(rating: Float) {
    val full = rating.roundToInt().coerceIn(0, 5)
    Row {
        repeat(5) { i ->
            Icon(
                imageVector        = if (i < full) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint               = if (i < full) Color(0xFFFFC107) else Muted,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun StatChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text = text, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = Muted,
    focusedTextColor     = OnBackground,
    unfocusedTextColor   = OnBackground,
    cursorColor          = Primary,
)
