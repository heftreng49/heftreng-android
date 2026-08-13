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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import com.heftreng.app.ui.component.ConnectedPostCard
import com.heftreng.app.ads.AdPlacement
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.ui.component.AdSlotView
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce

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
    val quotesOffline by feedVm.libraryQuotesOffline.collectAsState()
    val quotesHasMore by feedVm.libraryHasMore.collectAsState()
    val quotesLoadingMore by feedVm.libraryLoadingMore.collectAsState()
    var reviews by remember { mutableStateOf<List<BookReview>>(emptyList()) }
    val authors by libraryVm.authors.collectAsState()
    var books   by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val adConfigs by adsVm.allConfigs.collectAsState()


    DisposableEffect(Unit) {
        onDispose {
            adsVm.releaseBanners("lib_")
            adsVm.releaseAllNatives("lib_")
        }
    }

    // Adım 3 düzeltmesi: pager sekmeleri aynı composition içinde kaldığı için
    // yukarıdaki onDispose SADECE Library ekranından tamamen çıkılınca (geri
    // tuşu) tetikleniyordu — kullanıcı Alıntılar→Yazarlar→Kitaplar gibi sekmeler
    // arası gezindikçe önceki sekmenin native/banner reklamları hiç release
    // edilmiyordu (sessiz bellek sızıntısı + gereksiz canlı AdView/NativeAd).
    // Her sekmeye özel prefix'i, o sekmeden ayrılırken (currentPage değişince)
    // release ediyoruz; genel "lib_" temizliği ekrandan tam çıkışta hâlâ geçerli.
    val libraryTabPrefixes = listOf("lib_quotes_", "lib_reviews_", "lib_authors_", "lib_books_")
    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != previousPage) {
                val leftPrefix = libraryTabPrefixes.getOrNull(previousPage)
                if (leftPrefix != null) {
                    adsVm.releaseBanners(leftPrefix)
                    adsVm.releaseAllNatives(leftPrefix)
                }
                previousPage = page
            }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
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
    // FAZ -1 DÜZELTME: `libraryVm.isAdmin` bir Compose state'i DEĞİLDİ
    // (val get()) — perms Firestore'dan asenkron dolduğunda burası
    // recompose olmuyordu, "kutuphaneci" rolündeki kullanıcı (hatta admin)
    // her zaman yetkisiz görünebiliyordu. libraryPerms'i doğrudan izliyoruz.
    // NOT: Bu, kitap/yazar EKLEME yetkisi ("library" izni, kutuphaneci
    // rolü) — başkasının alıntı/yorumunu silme/düzenleme (moderasyon,
    // "pending" izni) ile KARIŞTIRILMAMALI, o kontrol BookQuoteCard/
    // BookReviewCard içinde canModerateLibrary ile ayrı yapılıyor.
    val libraryPermsState by libraryVm.libraryPerms.collectAsState()
    val isLibrarian = libraryPermsState?.can("library") == true
    val isAdmin = isLibrarian // geriye dönük uyumluluk için (aşağıda kullanılıyor)
    var showQuoteDialog        by remember { mutableStateOf(false) }
    var showReviewBookPicker   by remember { mutableStateOf(false) }
    var reviewTargetBook       by remember { mutableStateOf<LibraryBook?>(null) }
    var showReviewDialog       by remember { mutableStateOf(false) }
    var showAddAuthor          by remember { mutableStateOf(false) }
    var showAddBook            by remember { mutableStateOf(false) }


    // ── Hata bildirimi — Snackbar ────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val libraryError by libraryVm.error.collectAsState()
    LaunchedEffect(libraryError) {
        if (libraryError != null) {
            snackbarHostState.showSnackbar(
                message  = libraryError ?: "",
                duration = SnackbarDuration.Short,
            )
            libraryVm.clearError()
        }
    }

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
        containerColor  = Background,
        snackbarHost    = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData     = data,
                    containerColor   = MaterialTheme.colorScheme.errorContainer,
                    contentColor     = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = Strings.discoverTitle(language),
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
                        0 -> LibraryQuotesTab(quotes = quotes, navController = navController, language = language, feedVm = feedVm, adsVm = adsVm, isOffline = quotesOffline, hasMore = quotesHasMore, loadingMore = quotesLoadingMore, onLoadMore = { feedVm.loadMoreLibraryQuotes() })
                        1 -> LibraryReviewsTab(reviews = reviews, navController = navController, language = language, vm = libraryVm, adsVm = adsVm)
                        2 -> LibraryAuthorsTab(authors = authors, navController = navController, language = language, adsVm = adsVm)
                        3 -> LibraryBooksTab(books = books, navController = navController, language = language, adsVm = adsVm)
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
            language        = language,
            onDismiss       = { showQuoteDialog = false },
            onConfirm       = { payload ->
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
            onSearchBooks   = { q -> feedVm.searchBooksForQuote(q) },
            onSearchAuthors = { q -> feedVm.searchAuthorsForQuote(q) },
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
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel,
    isOffline    : Boolean = false,
    hasMore      : Boolean = false,
    loadingMore  : Boolean = false,
    onLoadMore   : () -> Unit = {},
) {
    if (quotes.isEmpty()) {
        EmptyState(Icons.Outlined.FormatQuote, Strings.libraryNoQuotes(language))
        return
    }
    val adConfigs by adsVm.allConfigs.collectAsState()
    val adPlan = remember(quotes.size, adConfigs[RemoteConfigManager.KEY_NATIVE_LIBRARY]?.enabled, adConfigs) {
        adsVm.planFor(
            screenKey = "lib_quotes",
            itemCount = quotes.size,
            nativeKey = RemoteConfigManager.KEY_NATIVE_LIBRARY,
            bannerKey = RemoteConfigManager.KEY_BANNER_LIBRARY,
        )
    }
    val quotesListState = rememberLazyListState()
    // Feed/Kurdi/Profile/Blog'daki kanıtlanmış ısıtma deseninin birebir aynısı —
    // önceden bu sekmede warmVisiblePositions hiç çağrılmıyordu, bu yüzden
    // native/banner reklamlar hiç yüklenmiyordu.
    LaunchedEffect(quotesListState, adPlan) {
        adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = 0, maxInitialAds = 3)
        snapshotFlow { quotesListState.firstVisibleItemIndex }
            .debounce(300L)
            .collect { firstVisible ->
                adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = firstVisible)
            }
    }
    LazyColumn(state = quotesListState, contentPadding = PaddingValues(vertical = 0.dp)) {
        if (isOffline) {
            item(key = "offline_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Amber.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CloudOff, null, tint = Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (language == "ku") "Hûn ne girêdayî ne — alîqên dawî tên xuyang kirin"
                        else "Çevrimdışısın — son görüntülenen alıntılar gösteriliyor",
                        color    = Amber,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        itemsIndexed(quotes, key = { _, p -> p.id }) { index, post ->
            ConnectedPostCard(
                post             = post,
                navController    = navController,
                feedVm           = feedVm,
                language         = language,
                onDeleteOverride = if (post.uid == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid)
                                       {{ feedVm.deletePost(post.id) }} else null,
            )
            // Reklam yerleşimi adPlan'dan gelir — banner/native çakışması
            // yapısal olarak imkansız (bkz. AdPlanner.kt).
            adPlan[index]?.let { placement ->
                AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        // "Daha Fazla Göster" butonu — sadece daha fazla alıntı varsa göster
        if (hasMore) {
            item(key = "load_more_quotes") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        OutlinedButton(onClick = onLoadMore) {
                            Text(
                                if (language == "ku") "Bêtir Nîşan Bide"
                                else "Daha Fazla Göster"
                            )
                        }
                    }
                }
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
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel,
) {
    if (reviews.isEmpty()) {
        EmptyState(Icons.Outlined.RateReview, Strings.libraryNoReviews(language))
        return
    }
    val adConfigs by adsVm.allConfigs.collectAsState()
    val adPlan = remember(reviews.size, adConfigs[RemoteConfigManager.KEY_NATIVE_LIBRARY]?.enabled, adConfigs) {
        adsVm.planFor(screenKey = "lib_reviews", itemCount = reviews.size, bannerKey = RemoteConfigManager.KEY_BANNER_LIBRARY)
    }
    val reviewsListState = rememberLazyListState()
    LaunchedEffect(reviewsListState, adPlan) {
        adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = 0, maxInitialAds = 3)
        snapshotFlow { reviewsListState.firstVisibleItemIndex }
            .debounce(300L)
            .collect { firstVisible ->
                adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = firstVisible)
            }
    }
    val actions = BookCardActions(vm = vm, navController = navController)
    LazyColumn(state = reviewsListState, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(reviews, key = { _, r -> r.id }) { index, review ->
            BookReviewCard(review = review, actions = actions, language = language)
            adPlan[index]?.let { placement ->
                AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun LibraryAuthorsTab(
    authors      : List<Author>,
    language     : String,
    navController: NavController,
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel,
) {
    if (authors.isEmpty()) {
        EmptyState(Icons.Outlined.Person, Strings.libraryNoAuthors(language))
        return
    }
    val adConfigs by adsVm.allConfigs.collectAsState()
    val adPlan = remember(authors.size, adConfigs[RemoteConfigManager.KEY_NATIVE_LIBRARY]?.enabled, adConfigs) {
        adsVm.planFor(screenKey = "lib_authors", itemCount = authors.size, bannerKey = RemoteConfigManager.KEY_BANNER_LIBRARY)
    }
    val authorsListState = rememberLazyListState()
    LaunchedEffect(authorsListState, adPlan) {
        adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = 0, maxInitialAds = 3)
        snapshotFlow { authorsListState.firstVisibleItemIndex }
            .debounce(300L)
            .collect { firstVisible ->
                adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = firstVisible)
            }
    }
    LazyColumn(
        state               = authorsListState,
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(authors, key = { _, a -> a.id }) { index, author ->
            LibraryAuthorRow(author = author, navController = navController)
            adPlan[index]?.let { placement ->
                AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun LibraryBooksTab(
    books        : List<LibraryBook>,
    language     : String,
    navController: NavController,
    adsVm        : com.heftreng.app.viewmodel.AdsViewModel,
) {
    if (books.isEmpty()) {
        EmptyState(Icons.Outlined.AutoStories, Strings.libraryNoBooks(language))
        return
    }
    val bannerUnitId by adsVm.unitIdFlow(RemoteConfigManager.KEY_BANNER_LIBRARY).collectAsState()
    val bannerCfg    by adsVm.configFlow(RemoteConfigManager.KEY_BANNER_LIBRARY).collectAsState()
    // AdSlotView artık kendi başına requestBanner çağırmıyor (bkz. AdSlotView.kt) —
    // istek warmVisiblePositions veya doğrudan requestBanner ile atılmak zorunda.
    // Bu tek statik slot bir liste penceresi değil, tek seferlik olduğu için
    // burada doğrudan requestBanner çağrılıyor.
    LaunchedEffect(bannerUnitId) {
        bannerUnitId?.let { unitId ->
            adsVm.requestBanner("lib_books_banner_0", unitId, bannerCfg?.bannerSize ?: "adaptive")
        }
    }
    LazyVerticalGrid(
        columns             = GridCells.Fixed(2),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(books, key = { it.id }) { book ->
            LibraryBookGridCard(
                book    = book,
                onClick = { navController.navigate("library_book_detail/${book.id}") },
            )
        }
        if (bannerUnitId != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AdSlotView(
                    placement = AdPlacement.Banner("lib_books_banner_0", bannerUnitId!!, bannerCfg?.bannerSize ?: "adaptive"),
                    adsVm     = adsVm,
                    modifier  = Modifier.padding(vertical = 4.dp),
                )
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
