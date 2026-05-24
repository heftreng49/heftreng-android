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
//  Alıntı ekle  → Feed'deki QuoteDialog kullanılır, FeedViewModel.createPost ile kaydedilir
//  İnceleme ekle → kitap seçici + AddReviewDialog (AuthorBookQuoteScreens ile aynı mantık)
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Author
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
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
    libraryVm    : LibraryViewModel = hiltViewModel(),
    feedVm       : FeedViewModel    = hiltViewModel(),
) {
    val tabs = listOf(
        Strings.libraryTabQuotes(language),
        Strings.libraryTabReviews(language),
        Strings.libraryTabAuthors(language),
        Strings.libraryTabBooks(language),
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    val db = remember { FirebaseFirestore.getInstance() }

    var quotes  by remember { mutableStateOf<List<BookQuote>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<BookReview>>(emptyList()) }
    val authors by libraryVm.authors.collectAsState()
    var books   by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true

        // ── Alıntılar — feed'den type=="library_quote" ile çek ──────────
        try {
            val qSnap = db.collection("feed")
                .whereEqualTo("type", "library_quote")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50).get().await()
            quotes = qSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val qObj = d["quote"] as? Map<*, *>
                val text = (qObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                    ?: d["quoteText"] as? String ?: return@mapNotNull null
                BookQuote(
                    id              = doc.id,
                    bookId          = d["libraryBookId"] as? String ?: "",
                    authorId        = d["libraryAuthorId"] as? String ?: "",
                    bookTitle       = (qObj?.get("book") as? String) ?: d["bookName"] as? String ?: "",
                    authorName      = d["authorName"] as? String ?: "",
                    text            = text,
                    uid             = d["uid"] as? String ?: "",
                    userDisplayName = (d["name"] as? String) ?: d["displayName"] as? String ?: "",
                    userPhotoURL    = d["photoURL"] as? String ?: "",
                    ts              = d["ts"] as? com.google.firebase.Timestamp,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "quotes load error: ${e.message}")
        }

        // ── İncelemeler — feed'den type=="library_review" ile çek ───────
        try {
            val rSnap = db.collection("feed")
                .whereEqualTo("type", "library_review")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50).get().await()
            reviews = rSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val text = d["text"] as? String ?: return@mapNotNull null
                BookReview(
                    id              = doc.id,
                    bookId          = d["libraryBookId"] as? String ?: "",
                    authorId        = d["libraryAuthorId"] as? String ?: "",
                    bookTitle       = d["bookName"] as? String ?: "",
                    authorName      = d["authorName"] as? String ?: "",
                    text            = text,
                    rating          = (d["rating"] as? Number)?.toFloat() ?: 0f,
                    uid             = d["uid"] as? String ?: "",
                    userDisplayName = (d["name"] as? String) ?: d["displayName"] as? String ?: "",
                    userPhotoURL    = d["photoURL"] as? String ?: "",
                    ts              = d["ts"] as? com.google.firebase.Timestamp,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "reviews load error: ${e.message}")
        }

        // ── Yazarlar ─────────────────────────────────────────────────────
        try {
            libraryVm.loadAuthors()
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "authors load error: ${e.message}")
        }

        // ── Kitaplar ─────────────────────────────────────────────────────
        try {
            val bSnap = db.collection("library_books")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50).get().await()
            books = bSnap.documents.mapNotNull { it.toObject(LibraryBook::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "books load error: ${e.message}")
        }

        loading = false
    }

    // ── Dialog state ──────────────────────────────────────────────────────
    var showQuoteDialog        by remember { mutableStateOf(false) }
    var showReviewBookPicker   by remember { mutableStateOf(false) }
    var reviewTargetBook       by remember { mutableStateOf<LibraryBook?>(null) }
    var showReviewDialog       by remember { mutableStateOf(false) }

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
                    onClick        = { showQuoteDialog = true },
                    containerColor = Primary,
                    contentColor   = Color.White,
                    shape          = CircleShape,
                ) { Icon(Icons.Filled.FormatQuote, contentDescription = null) }

                1 -> FloatingActionButton(
                    onClick        = { showReviewBookPicker = true },
                    containerColor = Primary,
                    contentColor   = Color.White,
                    shape          = CircleShape,
                ) { Icon(Icons.Filled.RateReview, contentDescription = null) }

                else -> {}
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

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
                                text       = title,
                                fontSize   = 13.sp,
                                maxLines   = 1,
                                color      = if (selectedTab == index) Primary else Muted,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                when (selectedTab) {
                    0 -> LibraryQuotesTab(quotes = quotes, navController = navController, language = language)
                    1 -> LibraryReviewsTab(reviews = reviews, navController = navController, language = language)
                    2 -> LibraryAuthorsTab(authors = authors, navController = navController, language = language)
                    3 -> LibraryBooksTab(books = books, navController = navController, language = language)
                }
            }
        }
    }

    // ── Alıntı Ekle — Feed'deki QuoteDialog ──────────────────────────────
    if (showQuoteDialog) {
        QuoteDialog(
            onDismiss = { showQuoteDialog = false },
            onConfirm = { payload ->
                showQuoteDialog = false
                // Feed'deki ile aynı sistem: createPost quoteText ile çağrılır
                // ensureAuthorAndBook otomatik çalışır, library_books'a da yazar
                feedVm.createPost(
                    text       = "",
                    quoteText  = payload.text,
                    authorName = payload.authorName,
                    bookName   = payload.bookName,
                    type       = "library_quote",
                )
            },
        )
    }

    // ── İnceleme: Kitap Seç ───────────────────────────────────────────────
    if (showReviewBookPicker) {
        LibraryBookPickerDialog(
            books    = books,
            language = language,
            onDismiss = { showReviewBookPicker = false },
            onSelect  = { book ->
                reviewTargetBook     = book
                showReviewBookPicker = false
                showReviewDialog     = true
            },
        )
    }

    // ── İnceleme Ekle ────────────────────────────────────────────────────
    if (showReviewDialog && reviewTargetBook != null) {
        LibraryAddReviewDialog(
            bookTitle = reviewTargetBook!!.title,
            onDismiss = { showReviewDialog = false; reviewTargetBook = null },
            onSubmit  = { text, rating ->
                libraryVm.addBookReview(reviewTargetBook!!, text, rating)
                showReviewDialog = false
                reviewTargetBook = null
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sekmeler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryQuotesTab(
    quotes       : List<BookQuote>,
    language     : String,
    navController: NavController,
) {
    if (quotes.isEmpty()) {
        LibraryEmptyState(Icons.Outlined.FormatQuote, Strings.libraryNoQuotes(language))
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(quotes, key = { it.id }) { quote ->
            LibraryQuoteCard(quote = quote, navController = navController)
        }
    }
}

@Composable
private fun LibraryReviewsTab(
    reviews      : List<BookReview>,
    language     : String,
    navController: NavController,
) {
    if (reviews.isEmpty()) {
        LibraryEmptyState(Icons.Outlined.RateReview, Strings.libraryNoReviews(language))
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(reviews, key = { it.id }) { review ->
            LibraryReviewCard(review = review, navController = navController)
        }
    }
}

@Composable
private fun LibraryAuthorsTab(
    authors      : List<Author>,
    language     : String,
    navController: NavController,
) {
    if (authors.isEmpty()) {
        LibraryEmptyState(Icons.Outlined.Person, Strings.libraryNoAuthors(language))
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(authors, key = { it.id }) { author ->
            LibraryAuthorRow(author = author, navController = navController)
        }
    }
}

@Composable
private fun LibraryBooksTab(
    books        : List<LibraryBook>,
    language     : String,
    navController: NavController,
) {
    if (books.isEmpty()) {
        LibraryEmptyState(Icons.Outlined.AutoStories, Strings.libraryNoBooks(language))
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(books, key = { it.id }) { book ->
            LibraryBookRow(book = book, navController = navController)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kartlar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryQuoteCard(quote: BookQuote, navController: NavController) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.FormatQuote, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text      = quote.text,
                    color     = OnBackground,
                    fontSize  = 15.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp,
                )
            }
            if (quote.bookTitle.isNotBlank() || quote.authorName.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text       = buildString {
                        if (quote.bookTitle.isNotBlank())  append("— ${quote.bookTitle}")
                        if (quote.authorName.isNotBlank()) append(", ${quote.authorName}")
                    },
                    color      = Primary,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.clickable {
                        if (quote.bookId.isNotBlank())
                            navController.navigate("library_book_detail/${quote.bookId}")
                        else if (quote.authorId.isNotBlank())
                            navController.navigate("author_detail/${quote.authorId}")
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            UserMiniRow(quote.userDisplayName, quote.userPhotoURL)
        }
    }
}

@Composable
private fun LibraryReviewCard(review: BookReview, navController: NavController) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier
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
                if (review.rating > 0f) StarRatingRow(review.rating)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = review.text, color = OnBackground, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            UserMiniRow(review.userDisplayName, review.userPhotoURL)
        }
    }
}

@Composable
private fun LibraryAuthorRow(author: Author, navController: NavController) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("author_detail/${author.id}") },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model             = author.photoURL.ifBlank { null },
                contentDescription = author.name,
                modifier          = Modifier.size(52.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(author.name, color = OnBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (author.nationality.isNotBlank())
                    Text(author.nationality, color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (author.bookCount  > 0) StatChip("${author.bookCount}",  Icons.Filled.MenuBook)
                    if (author.quoteCount > 0) StatChip("${author.quoteCount}", Icons.Filled.FormatQuote)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LibraryBookRow(book: LibraryBook, navController: NavController) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("library_book_detail/${book.id}") },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            if (book.coverImg.isNotBlank()) {
                AsyncImage(
                    model             = book.coverImg,
                    contentDescription = book.title,
                    modifier          = Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(8.dp)).background(HeftSurface),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, color = OnBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.authorName, color = Primary, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (book.avgRating  > 0f) StarRatingRow(book.avgRating)
                    if (book.quoteCount > 0)  StatChip("${book.quoteCount}", Icons.Filled.FormatQuote)
                }
                if (book.genre.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.genre, color = Muted, fontSize = 11.sp)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp).align(Alignment.CenterVertically))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diyaloglar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LibraryBookPickerDialog(
    books    : List<LibraryBook>,
    language : String,
    onDismiss: () -> Unit,
    onSelect : (LibraryBook) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text(Strings.libraryReviewBook(language), color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(book) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoStories, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(book.title, color = OnBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (book.authorName.isNotBlank())
                                Text(book.authorName, color = Muted, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel(language), color = Muted) }
        },
    )
}

@Composable
private fun LibraryAddReviewDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onSubmit : (String, Float) -> Unit,
) {
    var text   by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text("İnceleme Yaz", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bookTitle, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Puan ver:", color = Muted, fontSize = 12.sp)
                Row {
                    repeat(5) { i ->
                        IconButton(onClick = { rating = (i + 1).toFloat() }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (i < rating.roundToInt()) Icons.Default.Star else Icons.Outlined.StarBorder,
                                null, tint = Amber, modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = { Text("İncelemenizi yazın…", color = Muted) },
                    minLines      = 4,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (text.isNotBlank() && rating > 0) onSubmit(text.trim(), rating) },
                enabled  = text.isNotBlank() && rating > 0,
            ) { Text("Paylaş", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
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
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Muted, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = Muted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun UserMiniRow(displayName: String, photoURL: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model             = photoURL.ifBlank { null },
            contentDescription = displayName,
            modifier          = Modifier.size(22.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(displayName.ifBlank { "—" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StarRatingRow(rating: Float) {
    val full = rating.roundToInt().coerceIn(0, 5)
    Row {
        repeat(5) { i ->
            Icon(
                if (i < full) Icons.Filled.Star else Icons.Outlined.StarBorder,
                null,
                tint     = if (i < full) Color(0xFFFFC107) else Muted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun StatChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = Muted, fontSize = 11.sp)
    }
}
