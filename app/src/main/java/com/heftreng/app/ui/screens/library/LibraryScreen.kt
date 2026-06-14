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
import androidx.compose.foundation.border
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
import com.heftreng.app.ui.component.LibraryBookGridCard
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.roundToInt
import java.net.URLEncoder
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState

// ─────────────────────────────────────────────────────────────────────────────
//  Ana ekran
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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


    val quotes by feedVm.libraryQuotes.collectAsState()
    var reviews by remember { mutableStateOf<List<BookReview>>(emptyList()) }
    val authors by libraryVm.authors.collectAsState()
    var books   by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val bannerUnitId    by adsVm.bannerLibraryUnitId.collectAsState()
    val bannerLibCfg    by adsVm.bannerLibraryConfig.collectAsState()
    val libBannerSize   = bannerLibCfg?.bannerSize ?: "adaptive"

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
                books = libraryVm.loadBooksForScreen()
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "books: ${e.message}")
            }
        }
        val reviewsJob = launch {
            try {
                reviews = libraryVm.loadReviewsForScreen()
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


    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh  = {
            isRefreshing = true
            libraryVm.loadAuthors(forceRefresh = true)
        }
    )
    LaunchedEffect(isRefreshing) { if (isRefreshing) isRefreshing = false }
    Scaffold(
        containerColor = Background,
        // TopAppBar kaldırıldı — NavHost'taki Discover TopAppBar kullanılıyor
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
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
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
                        0 -> LibraryQuotesTab(quotes = quotes, navController = navController, language = language, feedVm = feedVm, bannerUnitId = bannerUnitId, adsVm = adsVm, bannerSize = libBannerSize, books = books, authors = authors)
                        1 -> LibraryReviewsTab(reviews = reviews, navController = navController, language = language, vm = libraryVm, bannerUnitId = bannerUnitId, adsVm = adsVm, bannerSize = libBannerSize)
                        2 -> LibraryAuthorsTab(authors = authors, navController = navController, language = language, bannerUnitId = bannerUnitId, adsVm = adsVm, bannerSize = libBannerSize)
                        3 -> LibraryBooksTab(books = books, navController = navController, language = language, bannerUnitId = bannerUnitId, adsVm = adsVm, bannerSize = libBannerSize)
                        else -> {}
                    }
                }
            }
        }
    
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state      = pullRefreshState,
            modifier   = Modifier.align(Alignment.TopCenter),
        )
        } // pullRefresh Box
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
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel? = null,
    bannerSize   : String = "adaptive",
    books        : List<LibraryBook> = emptyList(),
    authors      : List<Author> = emptyList(),
) {
    if (quotes.isEmpty()) {
        EmptyState(Icons.Outlined.FormatQuote, Strings.libraryNoQuotes(language))
        return
    }
    // ── Günün Alıntısı — gün damgasına göre deterministik seçim ──────────
    val dayIndex   = (System.currentTimeMillis() / 86_400_000L).toInt()
    val dailyQuote = quotes[((dayIndex % quotes.size) + quotes.size) % quotes.size]

    // ── Bu hafta öne çıkan kitap — en yüksek avgRating, yoksa en çok alıntılı ──
    val featuredBook = books
        .filter { it.avgRating > 0f || it.quoteCount > 0 }
        .maxWithOrNull(compareBy({ it.avgRating }, { it.quoteCount }))

    // ── En çok alıntılanan yazar ──────────────────────────────────────────
    val topAuthor = quotes
        .filter { it.authorName.isNotBlank() }
        .groupingBy { it.authorName }
        .eachCount()
        .maxByOrNull { it.value }

    LazyColumn(contentPadding = PaddingValues(vertical = 0.dp)) {
        item(key = "daily_quote_hero") {
            DailyQuoteHeroCard(
                quote    = dailyQuote,
                language = language,
                onClick  = { navController.navigate("post/${dailyQuote.id}") },
            )
        }
        if (featuredBook != null || topAuthor != null) {
            item(key = "discover_highlights") {
                DiscoverHighlightsRow(
                    featuredBook = featuredBook,
                    topAuthor    = topAuthor?.key,
                    topAuthorCount = topAuthor?.value ?: 0,
                    language     = language,
                    onBookClick  = { book -> navController.navigate("library_book_detail/${book.id}") },
                    onAuthorClick = { name ->
                        val authorId = authors.find { it.name == name }?.id
                        if (!authorId.isNullOrBlank()) {
                            navController.navigate("author_detail/$authorId")
                        }
                    },
                )
            }
        }
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
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB, bannerSize = bannerSize)
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
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel? = null,
    bannerSize   : String = "adaptive",
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
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB, bannerSize = bannerSize)
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
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel? = null,
    bannerSize   : String = "adaptive",
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
                AdBannerView(unitId = bannerUnitId, modifier = Modifier.padding(vertical = 4.dp), adsVm = adsVm, slot = com.heftreng.app.viewmodel.AdsViewModel.BannerSlot.LIB, bannerSize = bannerSize)
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
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel? = null,
    bannerSize   : String = "adaptive",
) {
    if (books.isEmpty()) {
        EmptyState(Icons.Outlined.AutoStories, Strings.libraryNoBooks(language))
        return
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns             = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(books, key = { _, b -> b.id }) { index, book ->
            LibraryBookGridCard(
                book    = book,
                onClick = { navController.navigate("library_book_detail/${book.id}") },
            )
            // Banner: tam genişlik için grid span — her 12 kitapta bir
            if (bannerUnitId != null && (index + 1) % 12 == 0) {
                // Not: LazyVerticalGrid içinde tam-genişlik banner için item span gerekir,
                // burada basitlik için banner'ı atlıyoruz (grid + banner span ayrı PR konusu)
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

// ─────────────────────────────────────────────────────────────────────────────
//  DailyQuoteHeroCard — "Günün Alıntısı" büyük hero kartı (Keşfet)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DailyQuoteHeroCard(
    quote   : Post,
    language: String,
    onClick : () -> Unit,
) {
    val ku = language == "ku"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Amber.copy(alpha = 0.18f), Primary.copy(alpha = 0.12f))
                )
            )
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Amber.copy(alpha = 0.5f), Primary.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable { onClick() }
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (ku) "Îro Peyvek" else "Günün Alıntısı",
                    color      = Amber,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "\u201C${quote.quoteText.take(220)}${if (quote.quoteText.length > 220) "\u2026" else ""}\u201D",
                color      = OnBackground,
                fontSize   = 16.sp,
                fontStyle  = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                maxLines   = 5,
                overflow   = TextOverflow.Ellipsis,
            )
            if (quote.authorName.isNotBlank() || quote.bookName.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (quote.authorName.isNotBlank()) {
                        Text(quote.authorName, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (quote.authorName.isNotBlank() && quote.bookName.isNotBlank()) {
                        Text("  ·  ", color = Muted, fontSize = 13.sp)
                    }
                    if (quote.bookName.isNotBlank()) {
                        Text(quote.bookName, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DiscoverHighlightsRow — "Bu hafta öne çıkan kitap" + "En çok alıntılanan yazar"
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DiscoverHighlightsRow(
    featuredBook  : LibraryBook?,
    topAuthor     : String?,
    topAuthorCount: Int,
    language      : String,
    onBookClick   : (LibraryBook) -> Unit,
    onAuthorClick : (String) -> Unit,
) {
    val ku = language == "ku"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (featuredBook != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HeftSurface)
                    .clickable { onBookClick(featuredBook) }
                    .padding(12.dp),
            ) {
                Text(
                    if (ku) "Pirtûka Vê Hefteyê" else "Bu Hafta Öne Çıkan",
                    color      = Muted,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceVar),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (featuredBook.coverImg.isNotBlank()) {
                            AsyncImage(
                                model = featuredBook.coverImg,
                                contentDescription = featuredBook.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            featuredBook.title,
                            color      = OnBackground,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        if (featuredBook.authorName.isNotBlank()) {
                            Text(featuredBook.authorName, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (topAuthor != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HeftSurface)
                    .clickable { onAuthorClick(topAuthor) }
                    .padding(12.dp),
            ) {
                Text(
                    if (ku) "Nivîskarê Populer" else "En Çok Alıntılanan",
                    color      = Muted,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceVar),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            topAuthor,
                            color      = OnBackground,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (ku) "$topAuthorCount gotin" else "$topAuthorCount alıntı",
                            color    = Primary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
