package com.heftreng.app.ui.screens.quotes

// ═══════════════════════════════════════════════════════════════════════════
//  AuthorBookQuoteScreens — Tam Yeniden Yazım
//
//  Ekranlar:
//    AuthorDetailScreen       — Yazar sayfası (biyografi, kitaplar, alıntılar, incelemeler)
//    LibraryBookDetailScreen  — Kitap sayfası (kapak, açıklama, alıntılar, incelemeler)
//
//  Legacy uyumluluk (sadece ad üzerinden, yeni yapı yoksa):
//    AuthorQuotesScreen       — Eski rota korunur, yeni AuthorDetailScreen'e yönlendirir
//    BookQuotesScreen         — Eski rota korunur, yeni LibraryBookDetailScreen'e yönlendirir
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.heftreng.app.ui.component.AdSlotView
import com.heftreng.app.ads.RemoteConfigManager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.component.BookQuoteCard
import com.heftreng.app.ui.component.BookReviewCard
import com.heftreng.app.ui.component.BookCardActions
import com.heftreng.app.ui.component.EmptyState
import com.heftreng.app.ui.component.LibraryBookCard
import com.heftreng.app.ui.component.AddReviewDialog
import com.heftreng.app.ui.component.BookPickerDialog
import com.heftreng.app.ui.component.ConnectedPostCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdsViewModel
import com.heftreng.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.flow.debounce
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.viewmodel.ReadingListViewModel
import com.heftreng.app.viewmodel.RlStatus
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
//  1. YAZAR DETAY EKRANI
// ═══════════════════════════════════════════════════════════════════════════

// ── BookQuote → Post dönüştürücü (PostCard kullanımı için) ────────────────
// feedPostId varsa feed'deki orijinal post'u temsil eder → like/save o ID üzerinden çalışır
// feedPostId yoksa quote'un kendi id'si kullanılır (alt koleksiyon kaydı)
private fun BookQuote.toPost() = Post(
    id            = feedPostId.takeIf { it.isNotBlank() } ?: id,
    uid           = uid,
    displayName   = userDisplayName,
    name          = userDisplayName,
    username      = "",
    photoURL      = userPhotoURL,
    quoteText     = text,
    bookName      = bookTitle,
    authorName    = authorName,
    text          = "",
    likesCount    = likesCount,
    ts            = ts,
    libraryBookId   = bookId,
    libraryAuthorId = authorId,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorDetailScreen(
    authorId     : String,
    navController: NavController,
    language     : String = "tr",
    vm           : LibraryViewModel = hiltViewModel(),
    feedVm       : com.heftreng.app.viewmodel.FeedViewModel = hiltViewModel(),
    adsVm        : AdsViewModel = hiltViewModel(),
) {
    val author      by vm.selectedAuthor.collectAsState()
    val books       by vm.authorBooks.collectAsState()
    val quotes      by vm.authorQuotes.collectAsState()
    val reviews     by vm.authorReviews.collectAsState()
    val loading     by vm.loading.collectAsState()
    val isFollowing by vm.isFollowingAuthor.collectAsState()
    val vmError     by vm.error.collectAsState()

    val authorTabs  = listOf("Kitapları", "Alıntılar", "İncelemeler")
    val pagerState  = rememberPagerState { authorTabs.size }
    val selectedTab by derivedStateOf { pagerState.currentPage }
    val scope       = rememberCoroutineScope()
    var showAddQuoteFab   by remember { mutableStateOf(false) }
    var showAddReviewFab  by remember { mutableStateOf(false) }
    var reviewTargetBook  by remember { mutableStateOf<com.heftreng.app.data.model.LibraryBook?>(null) }
    var showBookPicker    by remember { mutableStateOf(false) }
    var showEditAuthor    by remember { mutableStateOf(false) }
    var showAddBook       by remember { mutableStateOf(false) }
    val isAdmin = vm.isAdmin
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = listOf("Kitapları", "Alıntılar", "İncelemeler")

    LaunchedEffect(authorId) { vm.loadAuthor(authorId) }

    // createLibraryBook/updateLibraryBook gibi admin işlemleri hata alırsa
    // önceden hiçbir yerde gösterilmiyordu — dialog kapanıyor, kitap
    // eklenmemiş oluyor ve kullanıcı sebebini hiç göremiyordu.
    LaunchedEffect(vmError) {
        vmError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    // ── Reklam altyapısı (enabled:false — unitId Firebase Console'dan girilene kadar kapalı) ──
    val adConfigs by adsVm.allConfigs.collectAsState()
    val authorBooksListState  = rememberLazyListState()
    val authorQuotesListState = rememberLazyListState()
    val authorReviewsListState = rememberLazyListState()
    val authorBooksAdPlan = remember(books.size, adConfigs) {
        adsVm.planFor("author_books", books.size, bannerKey = RemoteConfigManager.KEY_BANNER_AUTHOR_DETAIL)
    }
    val authorQuotesAdPlan = remember(quotes.size, adConfigs) {
        adsVm.planFor("author_quotes", quotes.size, bannerKey = RemoteConfigManager.KEY_BANNER_AUTHOR_DETAIL)
    }
    val authorReviewsAdPlan = remember(reviews.size, adConfigs) {
        adsVm.planFor("author_reviews", reviews.size, bannerKey = RemoteConfigManager.KEY_BANNER_AUTHOR_DETAIL)
    }
    LaunchedEffect(authorBooksListState, authorBooksAdPlan) {
        adsVm.warmVisiblePositions(authorBooksAdPlan, firstVisibleIndex = 0, maxInitialAds = 2)
        snapshotFlow { authorBooksListState.firstVisibleItemIndex }.debounce(300L)
            .collect { adsVm.warmVisiblePositions(authorBooksAdPlan, firstVisibleIndex = it) }
    }
    LaunchedEffect(authorQuotesListState, authorQuotesAdPlan) {
        adsVm.warmVisiblePositions(authorQuotesAdPlan, firstVisibleIndex = 0, maxInitialAds = 2)
        snapshotFlow { authorQuotesListState.firstVisibleItemIndex }.debounce(300L)
            .collect { adsVm.warmVisiblePositions(authorQuotesAdPlan, firstVisibleIndex = it) }
    }
    LaunchedEffect(authorReviewsListState, authorReviewsAdPlan) {
        adsVm.warmVisiblePositions(authorReviewsAdPlan, firstVisibleIndex = 0, maxInitialAds = 2)
        snapshotFlow { authorReviewsListState.firstVisibleItemIndex }.debounce(300L)
            .collect { adsVm.warmVisiblePositions(authorReviewsAdPlan, firstVisibleIndex = it) }
    }
    DisposableEffect(authorId) {
        onDispose { adsVm.releaseBanners("author_books_banner_"); adsVm.releaseBanners("author_quotes_banner_"); adsVm.releaseBanners("author_reviews_banner_") }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(author?.name ?: "Yazar", color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showEditAuthor = true }) {
                            Icon(Icons.Default.Edit, null, tint = Amber)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (isAdmin && selectedTab == 0) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick        = { showAddBook = true },
                        containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                        contentColor   = androidx.compose.ui.graphics.Color.White,
                        shape          = androidx.compose.foundation.shape.CircleShape,
                    ) { Icon(Icons.Default.Add, null) }
                }
                when (selectedTab) {
                    1 -> FloatingActionButton(
                        onClick        = { showAddQuoteFab = true },
                        containerColor = Primary,
                        contentColor   = androidx.compose.ui.graphics.Color.White,
                        shape          = androidx.compose.foundation.shape.CircleShape,
                    ) { Icon(Icons.Default.FormatQuote, null) }
                    2 -> FloatingActionButton(
                        onClick        = { showBookPicker = true },
                        containerColor = Amber,
                        contentColor   = androidx.compose.ui.graphics.Color(0xFF1A1040),
                        shape          = androidx.compose.foundation.shape.CircleShape,
                    ) { Icon(Icons.Default.RateReview, null) }
                    else -> {}
                }
            }
        },
    ) { padding ->
        if (loading && author == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ── Tab Bar (sabit, sticky) ───────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = HeftSurface,
                    contentColor     = Primary,
                    indicator        = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = Primary,
                        )
                    },
                ) {
                    authorTabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick  = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            text = {
                                Text(
                                    label,
                                    fontSize   = 13.sp,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selectedTab == idx) Primary else Muted,
                                )
                            }
                        )
                    }
                }

                // ── Tab İçeriği: HorizontalPager ──────────────────────────
                HorizontalPager(
                    state                   = pagerState,
                    beyondViewportPageCount = 1,
                    modifier                = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> LazyColumn(
                            state          = authorBooksListState,
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            // Header her tab'ın ilk item'ı olarak scroll'a dahil
                            item {
                                AuthorHeaderSection(
                                    photoURL      = author?.photoURL ?: "",
                                    name          = author?.name ?: "",
                                    bio           = author?.bio ?: "",
                                    birthYear     = author?.birthYear ?: 0,
                                    nationality   = author?.nationality ?: "",
                                    bookCount     = author?.bookCount ?: 0,
                                    quoteCount    = author?.quoteCount ?: 0,
                                    reviewCount   = author?.reviewCount ?: 0,
                                    followerCount = author?.followerCount ?: 0,
                                    isFollowing   = isFollowing,
                                    onFollowClick = { vm.toggleFollowAuthor(authorId) },
                                )
                            }
                            if (books.isEmpty()) {
                                item { EmptyState(Icons.Outlined.MenuBook, "Henüz kitap yok") }
                            } else {
                                itemsIndexed(books, key = { _, b -> b.id }) { index, book ->
                                    LibraryBookCard(
                                        book    = book,
                                        onClick = { navController.navigate("library_book_detail/${book.id}") },
                                    )
                                    authorBooksAdPlan[index]?.let { placement ->
                                        AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        1 -> LazyColumn(
                            state          = authorQuotesListState,
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            item {
                                AuthorHeaderSection(
                                    photoURL      = author?.photoURL ?: "",
                                    name          = author?.name ?: "",
                                    bio           = author?.bio ?: "",
                                    birthYear     = author?.birthYear ?: 0,
                                    nationality   = author?.nationality ?: "",
                                    bookCount     = author?.bookCount ?: 0,
                                    quoteCount    = author?.quoteCount ?: 0,
                                    reviewCount   = author?.reviewCount ?: 0,
                                    followerCount = author?.followerCount ?: 0,
                                    isFollowing   = isFollowing,
                                    onFollowClick = { vm.toggleFollowAuthor(authorId) },
                                )
                            }
                            if (quotes.isEmpty()) {
                                item { EmptyState(Icons.Default.FormatQuote, "Henüz alıntı yok") }
                            } else {
                                itemsIndexed(quotes, key = { _, q -> q.id }) { index, quote ->
                                    val post = quote.toPost()
                                    ConnectedPostCard(
                                        post          = post,
                                        navController = navController,
                                        feedVm        = feedVm,
                                        language      = language,
                                    )
                                    authorQuotesAdPlan[index]?.let { placement ->
                                        AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        2 -> LazyColumn(
                            state          = authorReviewsListState,
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            item {
                                AuthorHeaderSection(
                                    photoURL      = author?.photoURL ?: "",
                                    name          = author?.name ?: "",
                                    bio           = author?.bio ?: "",
                                    birthYear     = author?.birthYear ?: 0,
                                    nationality   = author?.nationality ?: "",
                                    bookCount     = author?.bookCount ?: 0,
                                    quoteCount    = author?.quoteCount ?: 0,
                                    reviewCount   = author?.reviewCount ?: 0,
                                    followerCount = author?.followerCount ?: 0,
                                    isFollowing   = isFollowing,
                                    onFollowClick = { vm.toggleFollowAuthor(authorId) },
                                )
                            }
                            if (reviews.isEmpty()) {
                                item { EmptyState(Icons.Outlined.RateReview, "Henüz inceleme yok") }
                            } else {
                                itemsIndexed(reviews, key = { _, r -> r.id }) { index, review ->
                                    BookReviewCard(
                                        review   = review,
                                        actions  = BookCardActions(vm = vm, navController = navController),
                                        language = language,
                                    )
                                    authorReviewsAdPlan[index]?.let { placement ->
                                        AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // ── Alıntı FAB Dialog (yazar adı pre-filled) ──────────────────────
    if (showAddQuoteFab) {
        AuthorQuoteDialog(
            authorName    = author?.name ?: "",
            authorId      = authorId,
            authorBooks   = books,
            onDismiss     = { showAddQuoteFab = false },
            onConfirm     = { quoteText, bookName, bookId ->
                // Kitap seçildiyse kütüphane alıntısı olarak ekle
                val selectedBook = books.find { it.id == bookId }
                if (selectedBook != null) {
                    vm.addBookQuote(selectedBook, quoteText)
                } else {
                    // Kitap seçilmediyse sadece feed'e yazar alıntısı olarak düş
                    feedVm.createPost(
                        text            = "",
                        quoteText       = quoteText,
                        authorName      = author?.name ?: "",
                        bookName        = bookName,
                        libraryAuthorId = authorId,
                        type            = "library_quote",
                    )
                }
                showAddQuoteFab = false
            },
        )
    }

    // ── İnceleme FAB: önce kitap seç, sonra dialog ────────────────────
    if (showBookPicker) {
        BookPickerDialog(
            books    = books,
            language = "tr",
            onDismiss = { showBookPicker = false },
            onSelect  = { book ->
                reviewTargetBook = book
                showBookPicker   = false
                showAddReviewFab = true
            },
        )
    }
    if (showAddReviewFab && reviewTargetBook != null) {
        AddReviewDialog(
            bookTitle = reviewTargetBook!!.title,
            onDismiss = { showAddReviewFab = false; reviewTargetBook = null },
            onSubmit  = { text, rating ->
                reviewTargetBook?.let { vm.addBookReview(it, text, rating) }
                showAddReviewFab = false
                reviewTargetBook = null
            },
        )
    }

    // ── Admin: Yazar Düzenle ───────────────────────────────────────────
    if (showEditAuthor && author != null) {
        AdminEditAuthorDialog(
            author    = author!!,
            onDismiss = { showEditAuthor = false },
            onSave    = { name, bio, photoURL, birthYear, nationality ->
                vm.updateAuthor(authorId, name, bio, photoURL, birthYear, nationality)
                showEditAuthor = false
            },
        )
    }

    // ── Admin: Kitap Ekle ──────────────────────────────────────────────
    if (showAddBook && author != null) {
        AdminAddBookDialog(
            authorId   = authorId,
            authorName = author!!.name,
            onDismiss  = { showAddBook = false },
            onSave     = { title, synopsis, genre, publishYear, pageCount, coverImg ->
                vm.createLibraryBook(
                    title       = title,
                    authorId    = authorId,
                    authorName  = author?.name ?: "",
                    synopsis    = synopsis,
                    genre       = genre,
                    publishYear = publishYear,
                    pageCount   = pageCount,
                    coverImg    = coverImg,
                )
                showAddBook = false
            },
        )
    }
}

// ─── Yazar Başlık Bölümü ────────────────────────────────────────────────────
@Composable
private fun AuthorHeaderSection(
    photoURL    : String,
    name        : String,
    bio         : String,
    birthYear   : Int,
    nationality : String,
    bookCount   : Int,
    quoteCount  : Int,
    reviewCount : Int,
    followerCount: Int,
    isFollowing : Boolean,
    onFollowClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Primary.copy(alpha = 0.12f), Color.Transparent)
                )
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Fotoğraf
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SurfaceVar)
                .border(2.dp, Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photoURL.isNotBlank()) {
                AsyncImage(model = photoURL, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Person, null, tint = Primary, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(name, color = OnBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // Ülke / Doğum yılı
        val meta = buildList {
            if (nationality.isNotBlank()) add(nationality)
            if (birthYear > 0) add("$birthYear")
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, color = Muted, fontSize = 13.sp)
        }

        // Biyografi
        if (bio.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(bio, color = OnSurface, fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 8.dp))
        }

        // İstatistikler
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AuthorStat(bookCount.toString(),   "Kitap")
            AuthorStat(quoteCount.toString(),  "Alıntı")
            AuthorStat(reviewCount.toString(), "İnceleme")
            AuthorStat(followerCount.toString(),"Takipçi")
        }

        // Takip butonu
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onFollowClick,
            colors  = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) SurfaceVar else Primary,
                contentColor   = if (isFollowing) OnSurface else Color.White,
            ),
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                null, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isFollowing) "Takip Ediliyor" else "Takip Et", fontSize = 13.sp)
        }
    }
}

@Composable
private fun AuthorStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = OnBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  2. KİTAP DETAY EKRANI
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBookDetailScreen(
    bookId       : String,
    navController: NavController,
    language     : String = "tr",
    vm           : LibraryViewModel     = hiltViewModel(),
    rlVm         : ReadingListViewModel = hiltViewModel(),
    feedVm       : com.heftreng.app.viewmodel.FeedViewModel = hiltViewModel(),
) {
    val book    by vm.selectedBook.collectAsState()
    val quotes  by vm.bookQuotes.collectAsState()
    val reviews by vm.bookReviews.collectAsState()
    val loading by vm.loading.collectAsState()

    val rlEntries by rlVm.entries.collectAsState()
    val currentRlStatus = remember(rlEntries, bookId) { rlVm.getLibraryBookStatus(bookId) }

    val bookTabs    = listOf("Alıntılar", "İncelemeler")
    val pagerState2 = rememberPagerState { bookTabs.size }
    val selectedTab by derivedStateOf { pagerState2.currentPage }
    val scope2      = rememberCoroutineScope()
    var showAddQuote     by remember { mutableStateOf(false) }
    var showAddReview    by remember { mutableStateOf(false) }
    var showRlSheet      by remember { mutableStateOf(false) }
    var showEditBook     by remember { mutableStateOf(false) }
    val isAdmin = vm.isAdmin

    LaunchedEffect(bookId) {
        vm.loadLibraryBook(bookId)
        rlVm.load()
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(book?.title ?: "Kitap", color = OnBackground,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showEditBook = true }) {
                            Icon(Icons.Default.Edit, null, tint = Amber)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedTab == 1) {
                    ExtendedFloatingActionButton(
                        onClick           = { showAddReview = true },
                        containerColor    = Amber,
                        contentColor      = Color(0xFF1A1040),
                        text = { Text("İnceleme Yaz", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.RateReview, null) },
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick        = { showAddQuote = true },
                        containerColor = Primary,
                        contentColor   = Color.White,
                        text = { Text("Alıntı Ekle", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.FormatQuote, null) },
                    )
                }
            }
        }
    ) { padding ->
        if (loading && book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ── Tab Bar (sabit, sticky) ─────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = HeftSurface,
                    contentColor     = Primary,
                    indicator        = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = Primary,
                        )
                    },
                ) {
                    bookTabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick  = { scope2.launch { pagerState2.animateScrollToPage(idx) } },
                            text = {
                                Text(
                                    label,
                                    fontSize   = 13.sp,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selectedTab == idx) Primary else Muted,
                                )
                            }
                        )
                    }
                }

                // ── Tab İçeriği: HorizontalPager ────────────────────────
                HorizontalPager(
                    state                   = pagerState2,
                    beyondViewportPageCount = 1,
                    modifier                = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            // Header scroll'a dahil
                            item {
                                book?.let { b ->
                                    LibraryBookHeader(
                                        book          = b,
                                        onAuthorClick = {
                                            if (b.authorId.isNotBlank())
                                                navController.navigate("author_detail/${b.authorId}")
                                        },
                                    )
                                    ReadingStatusButton(
                                        currentStatus = currentRlStatus,
                                        onClick       = { showRlSheet = true },
                                        modifier      = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            if (quotes.isEmpty()) {
                                item { EmptyState(Icons.Default.FormatQuote, "Henüz alıntı yok.\nİlk alıntıyı sen ekle!") }
                            } else {
                                items(quotes, key = { it.id }) { quote ->
                                    val post = quote.toPost()
                                    ConnectedPostCard(
                                        post          = post,
                                        navController = navController,
                                        feedVm        = feedVm,
                                        language      = language,
                                    )
                                }
                            }
                        }
                        1 -> LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            item {
                                book?.let { b ->
                                    LibraryBookHeader(
                                        book          = b,
                                        onAuthorClick = {
                                            if (b.authorId.isNotBlank())
                                                navController.navigate("author_detail/${b.authorId}")
                                        },
                                    )
                                    ReadingStatusButton(
                                        currentStatus = currentRlStatus,
                                        onClick       = { showRlSheet = true },
                                        modifier      = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            if (reviews.isEmpty()) {
                                item { EmptyState(Icons.Outlined.RateReview, "Henüz inceleme yok.\nBu kitabı incele!") }
                            } else {
                                items(reviews, key = { it.id }) { review ->
                                    BookReviewCard(
                                        review   = review,
                                        actions  = BookCardActions(vm = vm, navController = navController),
                                        language = language,
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // ── Alıntı Dialog ──────────────────────────────────────────────────
    if (showAddQuote && book != null) {
        AddQuoteDialog(
            bookTitle  = book!!.title,
            onDismiss  = { showAddQuote = false },
            onSubmit   = { text ->
                vm.addBookQuote(book!!, text)
                showAddQuote = false
            },
        )
    }

    // ── İnceleme Dialog ────────────────────────────────────────────────
    if (showAddReview && book != null) {
        AddReviewDialog(
            bookTitle = book!!.title,
            onDismiss = { showAddReview = false },
            onSubmit  = { text, rating ->
                vm.addBookReview(book!!, text, rating)
                showAddReview = false
            },
        )
    }

    // ── Okuma Listesi Durum Seçici ─────────────────────────────────────
    if (showRlSheet && book != null) {
        val b = book ?: return
        RlStatusPickerDialog(
            currentStatus = currentRlStatus,
            onDismiss     = { showRlSheet = false },
            onSelect      = { status ->
                rlVm.setLibraryBookStatus(
                    bookId     = b.id,
                    title      = b.title,
                    coverImg   = b.coverImg,
                    authorName = b.authorName,
                    status     = status,
                )
                showRlSheet = false
            },
            onRemove = {
                rlVm.removeLibraryBook(b.id)
                showRlSheet = false
            },
        )
    }

    // ── Admin: Kitap Düzenle ───────────────────────────────────────────
    if (showEditBook && book != null) {
        AdminEditBookDialog(
            book      = book!!,
            onDismiss = { showEditBook = false },
            onSave    = { title, synopsis, genre, publishYear, pageCount, coverImg ->
                vm.updateLibraryBook(bookId, title, synopsis, genre, publishYear, pageCount, coverImg)
                showEditBook = false
            },
        )
    }
}

// ─── Kitap Başlık Bölümü ────────────────────────────────────────────────────
@Composable
private fun LibraryBookHeader(
    book         : LibraryBook,
    onAuthorClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Amber.copy(alpha = 0.08f), Color.Transparent)
                )
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Kapak
            Box(
                modifier = Modifier
                    .width(96.dp).height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVar),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverImg.isNotBlank()) {
                    AsyncImage(model = book.coverImg, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.width(16.dp))

            // Meta bilgiler
            Column(Modifier.weight(1f)) {
                Text(book.title, color = OnBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp)

                Spacer(Modifier.height(6.dp))

                // Yazar — tıklanabilir
                Row(
                    modifier = Modifier.clickable(onClick = onAuthorClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(book.authorName, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(8.dp))

                // Genre / Yıl / Sayfa
                val chips = buildList {
                    if (book.genre.isNotBlank()) add(book.genre)
                    if (book.publishYear > 0) add("${book.publishYear}")
                    if (book.pageCount > 0) add("${book.pageCount} sayfa")
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chips) { chip ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SurfaceVar,
                        ) {
                            Text(chip, color = Muted, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Puan
                if (book.avgRating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${(book.avgRating * 10).roundToInt() / 10f}  (${book.reviewCount} inceleme)",
                            color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // İstatistikler
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${book.quoteCount} alıntı", color = Muted, fontSize = 12.sp)
                    Text("${book.reviewCount} inceleme", color = Muted, fontSize = 12.sp)
                }
            }
        }

        // Özet
        if (book.synopsis.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(10.dp))
            Text("Hakkında", color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(book.synopsis, color = OnSurface, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  3. KARTLAR — ui/component/UnifiedCards.kt'ten import edilir
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
//  4. DIALOGLAR
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AddQuoteDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onSubmit : (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text("Alıntı Ekle", color = OnBackground, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bookTitle, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = { Text("Alıntı metni…", color = Muted) },
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
                onClick  = { if (text.isNotBlank()) onSubmit(text.trim()) },
                enabled  = text.isNotBlank(),
            ) {
                Text("Ekle", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  5. LEGACY ROTALAR — Eski nav bağlantıları için uyumluluk katmanı
//     (Feed'deki mevcut author_quotes/{ad} ve book_quotes/{ad} rotaları
//      hâlâ çalışır; sadece ad üzerinden feed alıntılarını gösterir)
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorQuotesScreen(
    authorName   : String,
    onBack       : () -> Unit,
    language     : String = "tr",
    navController: NavController? = null,
    feedVm       : com.heftreng.app.viewmodel.FeedViewModel? = null,
) {
    LegacyQuoteListPage(
        title         = authorName,
        subtitle      = "Yazar Alıntıları",
        icon          = Icons.Default.Person,
        field         = "quote.author",
        flatField     = "authorName",
        value         = authorName,
        onBack        = onBack,
        language      = language,
        navController = navController,
        feedVm        = feedVm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookQuotesScreen(
    bookName     : String,
    onBack       : () -> Unit,
    language     : String = "tr",
    navController: NavController? = null,
    feedVm       : com.heftreng.app.viewmodel.FeedViewModel? = null,
) {
    LegacyQuoteListPage(
        title         = bookName,
        subtitle      = "Kitap Alıntıları",
        icon          = Icons.Default.AutoStories,
        field         = "quote.book",
        flatField     = "bookName",
        value         = bookName,
        onBack        = onBack,
        language      = language,
        navController = navController,
        feedVm        = feedVm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyQuoteListPage(
    title        : String,
    subtitle     : String,
    icon         : androidx.compose.ui.graphics.vector.ImageVector,
    field        : String,
    flatField    : String,
    value        : String,
    onBack       : () -> Unit,
    language     : String = "tr",
    navController: NavController? = null,
    feedVm       : com.heftreng.app.viewmodel.FeedViewModel? = null,
) {
    var posts   by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(value) {
        loading = true
        try {
            posts = if (feedVm != null) {
                // flatField "authorName" için yazar adına, "bookName" için kitap adına göre ara.
                // Supabase book_quotes tablosundan — Firestore'daki eski 2×limit(50) taramasının yerine.
                if (flatField == "authorName") {
                    feedVm.getQuotesAsPostsByAuthorName(value)
                } else {
                    feedVm.getQuotesAsPostsByBookName(value)
                }
            } else emptyList()
        } catch (e: Exception) { e.printStackTrace() } finally { loading = false }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
                        Text(subtitle, color = Primary, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        when {
            loading   -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            posts.isEmpty() -> EmptyState(Icons.Default.FormatQuote, "Henüz alıntı yok")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    if (feedVm != null && navController != null) {
                        ConnectedPostCard(
                            post          = post,
                            navController = navController,
                            feedVm        = feedVm,
                            language      = language,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  AKILLI YÖNLENDIRME EKRANLARİ
//
//  author_quotes/{ad} veya book_quotes/{ad} rotasına gelindiğinde:
//    1. Firestore'da o ada ait library kaydı ara
//    2. Varsa → AuthorDetailScreen / LibraryBookDetailScreen'e git
//    3. Yoksa → Kaydı otomatik oluştur ve detail ekranına git
//    4. Hata durumunda → Eski legacy liste ekranı (hiç bir şey kırılmaz)
//
//  Bu şekilde eski alıntılara basıldığında da tam kütüphane sayfası açılır.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun AuthorQuotesSmartScreen(
    authorName   : String,
    navController: NavController,
    vm           : LibraryViewModel = hiltViewModel(),
) {
    // null = yükleniyor, "" = bulunamadı, "id" = bulundu
    var resolvedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authorName) {
        if (authorName.isBlank()) { resolvedId = ""; return@LaunchedEffect }
        try {
            // 1. Supabase'de ilike ile ara
            val found = vm.findOrCreateAuthorByName(authorName.trim())
            resolvedId = found.ifBlank { "" }
        } catch (_: Exception) {
            resolvedId = "" // Hata: legacy ekrana düş
        }
    }

    when {
        // Yükleniyor
        resolvedId == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
        // Bulundu / oluşturuldu → detail ekrana git
        resolvedId!!.isNotBlank() -> {
            LaunchedEffect(resolvedId) {
                navController.navigate("author_detail/$resolvedId") {
                    popUpTo("author_quotes/${authorName}") { inclusive = true }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
        // Hata → eski ekran
        else -> {
            AuthorQuotesScreen(
                authorName = authorName,
                onBack     = { navController.popBackStack() },
            )
        }
    }
}

@Composable
fun BookQuotesSmartScreen(
    bookName     : String,
    navController: NavController,
    vm           : LibraryViewModel = hiltViewModel(),
) {
    var resolvedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookName) {
        if (bookName.isBlank()) { resolvedId = ""; return@LaunchedEffect }
        try {
            // Supabase'de ilike ile ara, yoksa oluştur
            val found = vm.findOrCreateBookByTitle(bookName.trim())
            resolvedId = found.ifBlank { "" }
        } catch (_: Exception) {
            resolvedId = ""
        }
    }

    when {
        resolvedId == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
        resolvedId!!.isNotBlank() -> {
            LaunchedEffect(resolvedId) {
                navController.navigate("library_book_detail/$resolvedId") {
                    popUpTo("book_quotes/${bookName}") { inclusive = true }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
        else -> {
            BookQuotesScreen(
                bookName = bookName,
                onBack   = { navController.popBackStack() },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Okuma Durumu Butonu
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReadingStatusButton(
    currentStatus: RlStatus?,
    onClick      : () -> Unit,
    modifier     : Modifier = Modifier,
) {
    val (label, bgColor, textColor) = when (currentStatus) {
        RlStatus.READING  -> Triple("📖 Okuyorum",          Color(0xFF2563EB), Color.White)
        RlStatus.WANT     -> Triple("🔖 Okumak İstiyorum",  Color(0xFF7C3AED), Color.White)
        RlStatus.READ     -> Triple("✅ Okudum",             Color(0xFF059669), Color.White)
        RlStatus.DROPPED  -> Triple("❌ Bıraktım",           Color(0xFFDC2626), Color.White)
        null              -> Triple("+ Okuma Listesine Ekle", HeftSurface,      Primary)
    }
    Button(
        onClick  = onClick,
        modifier = modifier.height(40.dp),
        shape    = RoundedCornerShape(10.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = textColor),
        border   = if (currentStatus == null)
            androidx.compose.foundation.BorderStroke(1.dp, Primary) else null,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Okuma Durumu Seçici Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RlStatusPickerDialog(
    currentStatus: RlStatus?,
    onDismiss    : () -> Unit,
    onSelect     : (RlStatus) -> Unit,
    onRemove     : () -> Unit,
) {
    val options = listOf(
        Triple(RlStatus.READING, "📖", "Okuyorum"),
        Triple(RlStatus.WANT,    "🔖", "Okumak İstiyorum"),
        Triple(RlStatus.READ,    "✅", "Okudum"),
        Triple(RlStatus.DROPPED, "❌", "Bıraktım"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text("Okuma Durumu", color = OnBackground, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (status, emoji, label) ->
                    val isSelected = currentStatus == status
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(status) },
                        shape    = RoundedCornerShape(10.dp),
                        color    = if (isSelected) Color(status.color).copy(alpha = 0.15f) else Color.Transparent,
                        border   = if (isSelected)
                            androidx.compose.foundation.BorderStroke(1.5.dp, Color(status.color))
                        else null,
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$emoji ", fontSize = 18.sp)
                            Text(
                                label,
                                color      = if (isSelected) Color(status.color) else OnBackground,
                                fontSize   = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                // Listeden çıkar (sadece eklenmiş ise göster)
                if (currentStatus != null) {
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(
                        onClick  = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Listeden Çıkar", color = Color(0xFFDC2626), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

// AuthorBookPickerDialog → BookPickerDialog (UnifiedCards.kt) ile değiştirildi

// ═══════════════════════════════════════════════════════════════════════════
//  Admin Dialog: Yazar Düzenle
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AdminEditAuthorDialog(
    author   : com.heftreng.app.data.model.Author,
    onDismiss: () -> Unit,
    onSave   : (name: String, bio: String, photoURL: String, birthYear: Int, nationality: String) -> Unit,
) {
    var name        by remember { mutableStateOf(author.name) }
    var bio         by remember { mutableStateOf(author.bio) }
    var photoURL    by remember { mutableStateOf(author.photoURL) }
    var birthYearTxt by remember { mutableStateOf(if (author.birthYear > 0) author.birthYear.toString() else "") }
    var nationality by remember { mutableStateOf(author.nationality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                Text("Yazar Düzenle", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdminTextField("Ad / Nav", name) { name = it }
                AdminTextField("Biyografi / Biyografi", bio, minLines = 3) { bio = it }
                AdminTextField("Fotoğraf URL / Wêne URL", photoURL) { photoURL = it }
                AdminTextField("Doğum Yılı / Sala Jidayikbûnê", birthYearTxt) { birthYearTxt = it }
                AdminTextField("Milliyet / Netewe", nationality) { nationality = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onSave(name.trim(), bio.trim(), photoURL.trim(), birthYearTxt.toIntOrNull() ?: 0, nationality.trim())
                },
                enabled = name.isNotBlank(),
            ) { Text("Kaydet / Tomarkirin", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal / Betal", color = Muted) }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  Admin Dialog: Kitap Düzenle
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AdminEditBookDialog(
    book     : LibraryBook,
    onDismiss: () -> Unit,
    onSave   : (title: String, synopsis: String, genre: String, publishYear: Int, pageCount: Int, coverImg: String) -> Unit,
) {
    var title       by remember { mutableStateOf(book.title) }
    var synopsis    by remember { mutableStateOf(book.synopsis) }
    var genre       by remember { mutableStateOf(book.genre) }
    var publishYearTxt by remember { mutableStateOf(if (book.publishYear > 0) book.publishYear.toString() else "") }
    var pageCountTxt   by remember { mutableStateOf(if (book.pageCount > 0) book.pageCount.toString() else "") }
    var coverImg    by remember { mutableStateOf(book.coverImg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                Text("Kitap Düzenle / Pirtûk Biguherîne", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdminTextField("Başlık / Sernavê", title) { title = it }
                AdminTextField("Açıklama / Danasîn", synopsis, minLines = 3) { synopsis = it }
                AdminTextField("Tür / Celeb", genre) { genre = it }
                AdminTextField("Yayın Yılı / Sala Weşanê", publishYearTxt) { publishYearTxt = it }
                AdminTextField("Sayfa Sayısı / Hejmara Rûpelên", pageCountTxt) { pageCountTxt = it }
                AdminTextField("Kapak URL / URL ya Bergê", coverImg) { coverImg = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) onSave(title.trim(), synopsis.trim(), genre.trim(), publishYearTxt.toIntOrNull() ?: 0, pageCountTxt.toIntOrNull() ?: 0, coverImg.trim())
                },
                enabled = title.isNotBlank(),
            ) { Text("Kaydet / Tomarkirin", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal / Betal", color = Muted) }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  Admin Dialog: Yeni Kitap Ekle
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AdminAddBookDialog(
    authorId  : String,
    authorName: String,
    onDismiss : () -> Unit,
    onSave    : (title: String, synopsis: String, genre: String, publishYear: Int, pageCount: Int, coverImg: String) -> Unit,
) {
    var title       by remember { mutableStateOf("") }
    var synopsis    by remember { mutableStateOf("") }
    var genre       by remember { mutableStateOf("") }
    var publishYearTxt by remember { mutableStateOf("") }
    var pageCountTxt   by remember { mutableStateOf("") }
    var coverImg    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                Column {
                    Text("Yeni Kitap Ekle", color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(authorName, color = Muted, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdminTextField("Başlık / Sernavê *", title) { title = it }
                AdminTextField("Açıklama / Danasîn", synopsis, minLines = 3) { synopsis = it }
                AdminTextField("Tür / Celeb", genre) { genre = it }
                AdminTextField("Yayın Yılı / Sala Weşanê", publishYearTxt) { publishYearTxt = it }
                AdminTextField("Sayfa Sayısı / Hejmara Rûpelên", pageCountTxt) { pageCountTxt = it }
                AdminTextField("Kapak URL / URL ya Bergê", coverImg) { coverImg = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) onSave(title.trim(), synopsis.trim(), genre.trim(), publishYearTxt.toIntOrNull() ?: 0, pageCountTxt.toIntOrNull() ?: 0, coverImg.trim())
                },
                enabled = title.isNotBlank(),
            ) { Text("Ekle / Zêde Bike", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal / Betal", color = Muted) }
        },
    )
}

// ── Paylaşılan admin text field ────────────────────────────────────────────
@Composable
private fun AdminTextField(
    label    : String,
    value    : String,
    minLines : Int = 1,
    onChange : (String) -> Unit,
) {
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

// ═══════════════════════════════════════════════════════════════════════════
//  AuthorQuoteDialog — Yazara/Kitaba bağlı alıntı ekleme
//  • Yazarın kütüphanedeki kitapları açılır listede gösterilir
//  • Kitap seçilirse addBookQuote ile kütüphane koleksiyonuna yazılır
//  • Seçilmezse sadece yazar adıyla feed'e düşer
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorQuoteDialog(
    authorName  : String,
    authorId    : String = "",
    authorBooks : List<com.heftreng.app.data.model.LibraryBook> = emptyList(),
    onDismiss   : () -> Unit,
    onConfirm   : (quoteText: String, bookName: String, bookId: String) -> Unit,
) {
    var quoteText     by remember { mutableStateOf("") }
    var selectedBook  by remember { mutableStateOf<com.heftreng.app.data.model.LibraryBook?>(null) }
    var manualBook    by remember { mutableStateOf("") }
    var dropExpanded  by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = HeftSurface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Başlık
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FormatQuote, null, tint = Primary,
                        modifier = Modifier.size(22.dp))
                    Text("Alıntı Ekle", color = OnBackground,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(authorName, color = Primary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                // Alıntı metni
                OutlinedTextField(
                    value         = quoteText,
                    onValueChange = { quoteText = it },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder   = { Text("Alıntı metni…", color = Muted) },
                    minLines      = 4,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Primary,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                // Kitap seçimi
                if (authorBooks.isNotEmpty()) {
                    // Kütüphanedeki kitaplar — dropdown
                    ExposedDropdownMenuBox(
                        expanded        = dropExpanded,
                        onExpandedChange = { dropExpanded = it },
                    ) {
                        OutlinedTextField(
                            value         = selectedBook?.title ?: "Kitap seç (isteğe bağlı)",
                            onValueChange = {},
                            readOnly      = true,
                            modifier      = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon  = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropExpanded)
                            },
                            label  = { Text("Kitap", color = Muted, fontSize = 12.sp) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedBorderColor      = Amber,
                                unfocusedBorderColor    = Divider,
                                focusedTextColor        = OnBackground,
                                unfocusedTextColor      = if (selectedBook != null) OnBackground else Muted,
                                unfocusedContainerColor = SurfaceVar,
                                focusedContainerColor   = SurfaceVar,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        ExposedDropdownMenu(
                            expanded        = dropExpanded,
                            onDismissRequest = { dropExpanded = false },
                            modifier = Modifier.background(HeftSurface),
                        ) {
                            DropdownMenuItem(
                                text    = { Text("— Kitap seçme", color = Muted, fontSize = 13.sp) },
                                onClick = { selectedBook = null; dropExpanded = false },
                            )
                            authorBooks.forEach { book ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(book.title, color = OnBackground,
                                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            if (book.publishYear > 0)
                                                Text("${book.publishYear}", color = Muted, fontSize = 11.sp)
                                        }
                                    },
                                    onClick = { selectedBook = book; dropExpanded = false },
                                )
                            }
                        }
                    }
                } else {
                    // Kütüphanede kitap yoksa manuel giriş
                    OutlinedTextField(
                        value         = manualBook,
                        onValueChange = { manualBook = it },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Kitap adı (isteğe bağlı)", color = Muted, fontSize = 12.sp) },
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Divider,
                            focusedTextColor        = OnBackground,
                            unfocusedTextColor      = OnBackground,
                            unfocusedContainerColor = SurfaceVar,
                            focusedContainerColor   = SurfaceVar,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Seçili kitap göstergesi
                if (selectedBook != null) {
                    Surface(
                        shape  = RoundedCornerShape(8.dp),
                        color  = Primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.AutoStories, null, tint = Primary,
                                modifier = Modifier.size(16.dp))
                            Text(selectedBook!!.title, color = Primary,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            Text("✓ Kütüphane bağlandı", color = Primary,
                                fontSize = 11.sp)
                        }
                    }
                }

                // Butonlar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal", color = Muted)
                    }
                    Button(
                        onClick = {
                            if (quoteText.isNotBlank()) {
                                val bookName = selectedBook?.title ?: manualBook
                                val bookId   = selectedBook?.id ?: ""
                                onConfirm(quoteText.trim(), bookName.trim(), bookId)
                            }
                        },
                        enabled = quoteText.isNotBlank(),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Primary, contentColor = Color.White),
                        shape   = RoundedCornerShape(10.dp),
                    ) {
                        Text("Ekle", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
