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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.launch
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
import com.heftreng.app.ui.component.BookQuoteCard
import com.heftreng.app.ui.component.BookReviewCard
import com.heftreng.app.ui.component.BookCardActions
import com.heftreng.app.ui.component.EmptyState
import com.heftreng.app.ui.component.LibraryBookCard
import com.heftreng.app.ui.component.AddReviewDialog
import com.heftreng.app.ui.component.BookPickerDialog
import com.heftreng.app.ui.component.AdBannerView
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.AdsViewModel
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.data.model.Post
import com.heftreng.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.roundToInt
import java.net.URLEncoder

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
    adsVm        : AdsViewModel     = hiltViewModel(),
) {
    val tabs = listOf(
        Strings.libraryTabQuotes(language),
        Strings.libraryTabReviews(language),
        Strings.libraryTabAuthors(language),
        Strings.libraryTabBooks(language),
    )
    val pagerState  = rememberPagerState { tabs.size }
    val selectedTab by derivedStateOf { pagerState.currentPage }
    val scope       = rememberCoroutineScope()

    val db = remember { FirebaseFirestore.getInstance() }

    val quotes by feedVm.libraryQuotes.collectAsState()
    var reviews by remember { mutableStateOf<List<BookReview>>(emptyList()) }
    val authors by libraryVm.authors.collectAsState()
    var books   by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val bannerUnitId by adsVm.bannerLibraryUnitId.collectAsState()

    LaunchedEffect(Unit) {
        loading = true
        adsVm.loadAdConfigs()
        // Tüm sorgular paralel — N+1 yerine collectionGroup tek sorguda
        val quotesJob  = launch { feedVm.loadLibraryQuotesAsync() }
        val authorsJob = launch {
            try { libraryVm.loadAuthors() }
            catch (e: Exception) { android.util.Log.e("LibraryScreen", "authors: ${e.message}") }
        }
        val booksJob = launch {
            try {
                val bSnap = db.collection("library_books").limit(100).get().await()
                books = bSnap.documents
                    .mapNotNull { it.toObject(LibraryBook::class.java)?.copy(id = it.id) }
                    .sortedByDescending { it.ts?.seconds ?: 0L }
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "books: ${e.message}")
            }
        }
        val reviewsJob = launch {
            // collectionGroup tek sorguda tüm reviews — N+1 yok
            try {
                val rSnap = db.collectionGroup("reviews").limit(200).get().await()
                reviews = rSnap.documents.mapNotNull { doc ->
                    val d    = doc.data ?: return@mapNotNull null
                    val text = d["text"] as? String ?: return@mapNotNull null
                    BookReview(
                        id              = doc.id,
                        bookId          = d["bookId"] as? String ?: "",
                        authorId        = d["authorId"] as? String ?: "",
                        bookTitle       = d["bookName"] as? String ?: "",
                        authorName      = (d["authorName"] as? String)?.trim() ?: "",
                        text            = text,
                        rating          = (d["rating"] as? Number)?.toFloat() ?: 0f,
                        uid             = d["uid"] as? String ?: "",
                        userDisplayName = (d["name"] as? String) ?: d["displayName"] as? String ?: "",
                        userPhotoURL    = d["photoURL"] as? String ?: "",
                        ts              = d["ts"] as? com.google.firebase.Timestamp,
                    )
                }.sortedByDescending { it.ts?.seconds ?: 0L }
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "reviews: ${e.message}")
            }
        }
        // Hepsi paralel — hepsi bitince loading=false
        quotesJob.join(); authorsJob.join(); booksJob.join(); reviewsJob.join()
        loading = false
    }

    // ── Dialog state ──────────────────────────────────────────────────────
    val isAdmin = libraryVm.isAdmin
    var showQuoteDialog        by remember { mutableStateOf(false) }
    var showReviewBookPicker   by remember { mutableStateOf(false) }
    var reviewTargetBook       by remember { mutableStateOf<LibraryBook?>(null) }
    var showReviewDialog       by remember { mutableStateOf(false) }
    var showAddAuthor          by remember { mutableStateOf(false) }
    var showAddBook            by remember { mutableStateOf(false) }

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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                // Admin: sekmeye göre yazar/kitap ekle
                if (isAdmin) {
                    when (selectedTab) {
                        2 -> SmallFloatingActionButton(
                            onClick        = { showAddAuthor = true },
                            containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                            contentColor   = Color.White,
                            shape          = CircleShape,
                        ) { Icon(Icons.Filled.PersonAdd, contentDescription = null) }
                        3 -> SmallFloatingActionButton(
                            onClick        = { showAddBook = true },
                            containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                            contentColor   = Color.White,
                            shape          = CircleShape,
                        ) { Icon(Icons.Filled.AddCircleOutline, contentDescription = null) }
                        else -> {}
                    }
                }
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
                        onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
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
                HorizontalPager(
                    state                   = pagerState,
                    beyondViewportPageCount = 1,
                    modifier                = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> LibraryQuotesTab(quotes = quotes, navController = navController, language = language, feedVm = feedVm, bannerUnitId = bannerUnitId)
                        1 -> LibraryReviewsTab(reviews = reviews, navController = navController, language = language, vm = libraryVm, bannerUnitId = bannerUnitId)
                        2 -> LibraryAuthorsTab(authors = authors, navController = navController, language = language, bannerUnitId = bannerUnitId)
                        3 -> LibraryBooksTab(books = books, navController = navController, language = language, bannerUnitId = bannerUnitId)
                        else -> {}
                    }
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
        BookPickerDialog(
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
        AddReviewDialog(
            bookTitle = reviewTargetBook!!.title,
            language  = language,
            onDismiss = { showReviewDialog = false; reviewTargetBook = null },
            onSubmit  = { text, rating ->
                libraryVm.addBookReview(reviewTargetBook!!, text, rating)
                showReviewDialog = false
                reviewTargetBook = null
            },
        )
    }

    // ── Admin: Yazar Ekle ─────────────────────────────────────────────
    if (showAddAuthor) {
        LibraryAdminAddAuthorDialog(
            onDismiss = { showAddAuthor = false },
            onSave    = { name, bio, photoURL, birthYear, nationality ->
                libraryVm.createAuthor(name, bio, photoURL, birthYear, nationality)
                showAddAuthor = false
            },
        )
    }

    // ── Admin: Kitap Ekle ─────────────────────────────────────────────
    if (showAddBook) {
        LibraryAdminAddBookDialog(
            authors   = libraryVm.authors.collectAsState().value,
            onDismiss = { showAddBook = false },
            onSave    = { title, authorId, authorName, synopsis, genre, publishYear, pageCount, coverImg ->
                libraryVm.createLibraryBook(title = title, authorId = authorId, authorName = authorName, synopsis = synopsis, genre = genre, publishYear = publishYear, pageCount = pageCount, coverImg = coverImg)
                showAddBook = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sekmeler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryQuotesTab(
    quotes       : List<Post>,
    language     : String,
    navController: NavController,
    feedVm       : FeedViewModel,
    bannerUnitId : String? = null,
) {
    if (quotes.isEmpty()) {
        EmptyState(Icons.Outlined.FormatQuote, Strings.libraryNoQuotes(language))
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 0.dp)) {
        itemsIndexed(quotes, key = { _, p -> p.id }) { index, post ->
            PostCard(
                post         = post,
                language     = language,
                onLike       = { feedVm.toggleLike(post) },
                onSave       = { feedVm.toggleSave(post) },
                onProfile    = { navController.navigate("profile/${post.uid}") },
                onComment    = { navController.navigate("post/${post.id}") },
                onShare      = { if (post.isRepostedByMe) feedVm.unrepost(post) else feedVm.repost(post) },
                onDelete     = if (post.uid == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid)
                                   {{ feedVm.deletePost(post.id) }} else null,
                onTapAuthor  = { _ ->
                    if (post.libraryAuthorId.isNotBlank())
                        navController.navigate("author_detail/${post.libraryAuthorId}")
                    else if (post.authorName.isNotBlank())
                        navController.navigate("author_quotes/${URLEncoder.encode(post.authorName, "UTF-8")}")
                },
                onTapBook    = { _ ->
                    if (post.libraryBookId.isNotBlank())
                        navController.navigate("library_book_detail/${post.libraryBookId}")
                    else if (post.bookName.isNotBlank())
                        navController.navigate("book_quotes/${URLEncoder.encode(post.bookName, "UTF-8")}")
                },
            )
            if (bannerUnitId != null && (index + 1) % 5 == 0) {
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB)
            }
        }
    }
}

@Composable
private fun LibraryReviewsTab(
    reviews      : List<BookReview>,
    language     : String,
    navController: NavController,
    vm           : LibraryViewModel? = null,
    bannerUnitId : String? = null,
) {
    if (reviews.isEmpty()) {
        EmptyState(Icons.Outlined.RateReview, Strings.libraryNoReviews(language))
        return
    }
    val actions = BookCardActions(vm = vm, navController = navController)
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(reviews, key = { _, r -> r.id }) { index, review ->
            BookReviewCard(review = review, actions = actions, language = language)
            if (bannerUnitId != null && (index + 1) % 5 == 0) {
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB)
            }
        }
    }
}

@Composable
private fun LibraryAuthorsTab(
    authors      : List<Author>,
    language     : String,
    navController: NavController,
    bannerUnitId : String? = null,
) {
    if (authors.isEmpty()) {
        EmptyState(Icons.Outlined.Person, Strings.libraryNoAuthors(language))
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(authors, key = { _, a -> a.id }) { index, author ->
            LibraryAuthorRow(author = author, navController = navController)
            if (bannerUnitId != null && (index + 1) % 6 == 0) {
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB)
            }
        }
    }
}

@Composable
private fun LibraryBooksTab(
    books        : List<LibraryBook>,
    language     : String,
    navController: NavController,
    bannerUnitId : String? = null,
) {
    if (books.isEmpty()) {
        EmptyState(Icons.Outlined.AutoStories, Strings.libraryNoBooks(language))
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(books, key = { _, b -> b.id }) { index, book ->
            LibraryBookCard(
                book    = book,
                onClick = { navController.navigate("library_book_detail/${book.id}") },
            )
            if (bannerUnitId != null && (index + 1) % 6 == 0) {
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Yazar listesi satırı — yalnızca LibraryScreen'e özgü
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
//  Yardımcı bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = Muted, fontSize = 11.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Admin: Yeni Yazar Ekle
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun LibraryAdminAddAuthorDialog(
    onDismiss: () -> Unit,
    onSave   : (name: String, bio: String, photoURL: String, birthYear: Int, nationality: String) -> Unit,
) {
    var name        by remember { mutableStateOf("") }
    var bio         by remember { mutableStateOf("") }
    var photoURL    by remember { mutableStateOf("") }
    var birthYearTxt by remember { mutableStateOf("") }
    var nationality by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.PersonAdd, null, tint = androidx.compose.ui.graphics.Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                Text("Yeni Yazar / Nivîskarê Nû", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibAdminTextField("Ad * / Nav *", name) { name = it }
                LibAdminTextField("Biyografi / Biyografi", bio, minLines = 3) { bio = it }
                LibAdminTextField("Fotoğraf URL / Wêne URL", photoURL) { photoURL = it }
                LibAdminTextField("Doğum Yılı / Sala Jidayikbûnê", birthYearTxt) { birthYearTxt = it }
                LibAdminTextField("Milliyet / Netewe", nationality) { nationality = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), bio.trim(), photoURL.trim(), birthYearTxt.toIntOrNull() ?: 0, nationality.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Ekle / Zêde Bike", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal / Betal", color = Muted) } },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  Admin: Yeni Kitap Ekle (Yazarı listeden seç)
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryAdminAddBookDialog(
    authors  : List<com.heftreng.app.data.model.Author>,
    onDismiss: () -> Unit,
    onSave   : (title: String, authorId: String, authorName: String, synopsis: String, genre: String, publishYear: Int, pageCount: Int, coverImg: String) -> Unit,
) {
    var title       by remember { mutableStateOf("") }
    var synopsis    by remember { mutableStateOf("") }
    var genre       by remember { mutableStateOf("") }
    var publishYearTxt by remember { mutableStateOf("") }
    var pageCountTxt   by remember { mutableStateOf("") }
    var coverImg    by remember { mutableStateOf("") }
    var selectedAuthor by remember { mutableStateOf<com.heftreng.app.data.model.Author?>(null) }
    var authorExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoStories, null, tint = androidx.compose.ui.graphics.Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                Text("Yeni Kitap / Pirtûkê Nû", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibAdminTextField("Başlık * / Sernavê *", title) { title = it }
                // Yazar seçici
                ExposedDropdownMenuBox(expanded = authorExpanded, onExpandedChange = { authorExpanded = it }) {
                    OutlinedTextField(
                        value         = selectedAuthor?.name ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Yazar / Nivîskar *", color = Muted, fontSize = 12.sp) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authorExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        ),
                    )
                    ExposedDropdownMenu(expanded = authorExpanded, onDismissRequest = { authorExpanded = false },
                        modifier = Modifier.background(HeftSurface)) {
                        authors.forEach { author ->
                            DropdownMenuItem(
                                text    = { Text(author.name, color = OnBackground) },
                                onClick = { selectedAuthor = author; authorExpanded = false },
                            )
                        }
                    }
                }
                LibAdminTextField("Açıklama / Danasîn", synopsis, minLines = 3) { synopsis = it }
                LibAdminTextField("Tür / Celeb", genre) { genre = it }
                LibAdminTextField("Yayın Yılı / Sala Weşanê", publishYearTxt) { publishYearTxt = it }
                LibAdminTextField("Sayfa Sayısı / Hejmara Rûpelên", pageCountTxt) { pageCountTxt = it }
                LibAdminTextField("Kapak URL / URL ya Bergê", coverImg) { coverImg = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = selectedAuthor
                    if (title.isNotBlank() && a != null)
                        onSave(title.trim(), a.id, a.name, synopsis.trim(), genre.trim(), publishYearTxt.toIntOrNull() ?: 0, pageCountTxt.toIntOrNull() ?: 0, coverImg.trim())
                },
                enabled = title.isNotBlank() && selectedAuthor != null,
            ) { Text("Ekle / Zêde Bike", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal / Betal", color = Muted) } },
    )
}

@Composable
private fun LibAdminTextField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, color = Muted, fontSize = 12.sp) },
        minLines      = minLines,
        modifier      = Modifier.fillMaxWidth(),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Amber,
            unfocusedBorderColor = Divider,
            focusedTextColor     = OnBackground,
            unfocusedTextColor   = OnBackground,
            focusedLabelColor    = Amber,
        ),
    )
}
