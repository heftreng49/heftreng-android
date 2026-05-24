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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
//  1. YAZAR DETAY EKRANI
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorDetailScreen(
    authorId     : String,
    navController: NavController,
    vm           : LibraryViewModel = hiltViewModel(),
) {
    val author      by vm.selectedAuthor.collectAsState()
    val books       by vm.authorBooks.collectAsState()
    val quotes      by vm.authorQuotes.collectAsState()
    val reviews     by vm.authorReviews.collectAsState()
    val loading     by vm.loading.collectAsState()
    val isFollowing by vm.isFollowingAuthor.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Kitapları", "Alıntılar", "İncelemeler")

    LaunchedEffect(authorId) { vm.loadAuthor(authorId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(author?.name ?: "Yazar", color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        if (loading && author == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // ── Yazar Başlık / Profil ──────────────────────────────────
                item {
                    AuthorHeaderSection(
                        photoURL    = author?.photoURL ?: "",
                        name        = author?.name ?: "",
                        bio         = author?.bio ?: "",
                        birthYear   = author?.birthYear ?: 0,
                        nationality = author?.nationality ?: "",
                        bookCount   = author?.bookCount ?: 0,
                        quoteCount  = author?.quoteCount ?: 0,
                        reviewCount = author?.reviewCount ?: 0,
                        followerCount = author?.followerCount ?: 0,
                        isFollowing = isFollowing,
                        onFollowClick = { vm.toggleFollowAuthor(authorId) },
                    )
                }

                // ── Tab Bar ───────────────────────────────────────────────
                item {
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
                        tabs.forEachIndexed { idx, label ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick  = { selectedTab = idx },
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
                }

                // ── Tab İçeriği ───────────────────────────────────────────
                when (selectedTab) {
                    // Kitaplar
                    0 -> {
                        if (books.isEmpty()) {
                            item { EmptyState(Icons.Outlined.MenuBook, "Henüz kitap yok") }
                        } else {
                            items(books, key = { it.id }) { book ->
                                LibraryBookCard(
                                    book    = book,
                                    onClick = { navController.navigate("library_book_detail/${book.id}") },
                                )
                            }
                        }
                    }
                    // Alıntılar
                    1 -> {
                        if (quotes.isEmpty()) {
                            item { EmptyState(Icons.Default.FormatQuote, "Henüz alıntı yok") }
                        } else {
                            items(quotes, key = { it.id }) { quote ->
                                BookQuoteCard(quote = quote)
                            }
                        }
                    }
                    // İncelemeler
                    2 -> {
                        if (reviews.isEmpty()) {
                            item { EmptyState(Icons.Outlined.RateReview, "Henüz inceleme yok") }
                        } else {
                            items(reviews, key = { it.id }) { review ->
                                BookReviewCard(review = review)
                            }
                        }
                    }
                }
            }
        }
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
    vm           : LibraryViewModel = hiltViewModel(),
) {
    val book    by vm.selectedBook.collectAsState()
    val quotes  by vm.bookQuotes.collectAsState()
    val reviews by vm.bookReviews.collectAsState()
    val loading by vm.loading.collectAsState()

    var selectedTab      by remember { mutableIntStateOf(0) }
    var showAddQuote     by remember { mutableStateOf(false) }
    var showAddReview    by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) { vm.loadLibraryBook(bookId) }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                // ── Kitap Başlık ────────────────────────────────────────
                item {
                    book?.let { b ->
                        LibraryBookHeader(
                            book          = b,
                            onAuthorClick = {
                                if (b.authorId.isNotBlank())
                                    navController.navigate("author_detail/${b.authorId}")
                            },
                        )
                    }
                }

                // ── Tab Bar ─────────────────────────────────────────────
                item {
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
                        listOf("Alıntılar", "İncelemeler").forEachIndexed { idx, label ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick  = { selectedTab = idx },
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
                }

                // ── Tab İçeriği ─────────────────────────────────────────
                if (selectedTab == 0) {
                    // Alıntılar
                    if (quotes.isEmpty()) {
                        item { EmptyState(Icons.Default.FormatQuote, "Henüz alıntı yok.\nİlk alıntıyı sen ekle!") }
                    } else {
                        items(quotes, key = { it.id }) { quote ->
                            BookQuoteCard(quote = quote)
                        }
                    }
                } else {
                    // İncelemeler
                    if (reviews.isEmpty()) {
                        item { EmptyState(Icons.Outlined.RateReview, "Henüz inceleme yok.\nBu kitabı incele!") }
                    } else {
                        items(reviews, key = { it.id }) { review ->
                            BookReviewCard(review = review)
                        }
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
//  3. ORTAK KARTLAR
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun BookQuoteCard(quote: BookQuote) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape    = RoundedCornerShape(14.dp),
        color    = HeftSurface,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Box(
                    Modifier.width(3.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(Primary)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "❝ ${quote.text}",
                    color = OnSurface, fontSize = 14.sp,
                    fontStyle = FontStyle.Italic, lineHeight = 22.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            if (quote.bookTitle.isNotBlank() || quote.authorName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        buildString {
                            if (quote.bookTitle.isNotBlank()) append(quote.bookTitle)
                            if (quote.bookTitle.isNotBlank() && quote.authorName.isNotBlank()) append(" — ")
                            if (quote.authorName.isNotBlank()) append(quote.authorName)
                        },
                        color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
            UserRow(displayName = quote.userDisplayName, photoURL = quote.userPhotoURL)
        }
    }
}

@Composable
fun BookReviewCard(review: BookReview) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape    = RoundedCornerShape(14.dp),
        color    = HeftSurface,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Puan yıldızları
            if (review.rating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            if (i < review.rating.roundToInt()) Icons.Default.Star
                            else Icons.Outlined.StarBorder,
                            null,
                            tint = Amber,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${review.rating}", color = Amber, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Kitap/yazar meta (sadece yazar sayfasında gösterilir)
            if (review.bookTitle.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = Primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(review.bookTitle, color = Primary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
            }

            Text(review.text, color = OnSurface, fontSize = 14.sp, lineHeight = 21.sp)

            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
            UserRow(displayName = review.userDisplayName, photoURL = review.userPhotoURL)
        }
    }
}

@Composable
private fun LibraryBookCard(
    book   : LibraryBook,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = HeftSurface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Küçük kapak
            Box(
                modifier = Modifier.width(52.dp).height(72.dp)
                    .clip(RoundedCornerShape(8.dp)).background(SurfaceVar),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverImg.isNotBlank()) {
                    AsyncImage(model = book.coverImg, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, color = OnBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.genre.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(book.genre, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (book.avgRating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${(book.avgRating * 10).roundToInt() / 10f}", color = Amber, fontSize = 11.sp)
                        }
                    }
                    Text("${book.quoteCount} alıntı", color = Muted, fontSize = 11.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun UserRow(displayName: String, photoURL: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (photoURL.isNotBlank()) {
                AsyncImage(model = photoURL, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    displayName.firstOrNull()?.uppercase() ?: "?",
                    color = OnBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(displayName.ifBlank { "Kullanıcı" }, color = Muted, fontSize = 12.sp,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = Divider, modifier = Modifier.size(52.dp))
            Text(message, color = Muted, fontSize = 14.sp,
                lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

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

@Composable
private fun AddReviewDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onSubmit : (String, Float) -> Unit,
) {
    var text   by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text("İnceleme Yaz", color = OnBackground, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bookTitle, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                // Yıldız seçici
                Text("Puan ver:", color = Muted, fontSize = 12.sp)
                Row {
                    repeat(5) { i ->
                        IconButton(onClick = { rating = (i + 1).toFloat() }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (i < rating.roundToInt()) Icons.Default.Star
                                else Icons.Outlined.StarBorder,
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
                onClick = { if (text.isNotBlank() && rating > 0) onSubmit(text.trim(), rating) },
                enabled = text.isNotBlank() && rating > 0,
            ) {
                Text("Paylaş", color = Primary, fontWeight = FontWeight.Bold)
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

@Deprecated(
    message = "AuthorQuotesSmartScreen kullan. Bu fonksiyon yalnızca feed koleksiyonunu sorgular; kütüphane sistemiyle entegre değildir.",
    replaceWith = ReplaceWith("AuthorQuotesSmartScreen(authorName, navController, language)"),
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorQuotesScreen(
    authorName : String,
    onBack     : () -> Unit,
) {
    LegacyQuoteListPage(
        title    = authorName,
        subtitle = "Yazar Alıntıları",
        icon     = Icons.Default.Person,
        field    = "quote.author",
        flatField = "authorName",
        value    = authorName,
        onBack   = onBack,
    )
}

@Deprecated(
    message = "BookQuotesSmartScreen kullan. Bu fonksiyon yalnızca feed koleksiyonunu sorgular; kütüphane sistemiyle entegre değildir.",
    replaceWith = ReplaceWith("BookQuotesSmartScreen(bookName, navController, language)"),
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookQuotesScreen(
    bookName : String,
    onBack   : () -> Unit,
) {
    LegacyQuoteListPage(
        title    = bookName,
        subtitle = "Kitap Alıntıları",
        icon     = Icons.Default.AutoStories,
        field    = "quote.book",
        flatField = "bookName",
        value    = bookName,
        onBack   = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyQuoteListPage(
    title    : String,
    subtitle : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    field    : String,
    flatField: String,
    value    : String,
    onBack   : () -> Unit,
) {
    val db      = remember { FirebaseFirestore.getInstance() }
    var posts   by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(value) {
        loading = true
        try {
            val snap1 = db.collection("feed").whereEqualTo(field, value).limit(50).get().await()
            val snap2 = db.collection("feed").whereEqualTo(flatField, value).limit(50).get().await()
            val all   = (snap1.documents + snap2.documents).distinctBy { it.id }
                .sortedByDescending { (it.data?.get("ts") as? com.google.firebase.Timestamp)?.seconds ?: 0L }

            posts = all.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val qObj   = d["quote"] as? Map<*, *>
                val qText  = (qObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                    ?: d["quoteText"] as? String ?: return@mapNotNull null
                Post(
                    id          = doc.id,
                    uid         = d["uid"]          as? String ?: "",
                    displayName = (d["name"]        as? String)?.takeIf { it.isNotBlank() }
                        ?: d["displayName"] as? String ?: "",
                    photoURL    = d["photoURL"]     as? String ?: "",
                    quoteText   = qText,
                    bookName    = (qObj?.get("book")   as? String)?.takeIf { it.isNotBlank() } ?: d["bookName"]   as? String ?: "",
                    authorName  = (qObj?.get("author") as? String)?.takeIf { it.isNotBlank() } ?: d["authorName"] as? String ?: "",
                    ts          = d["ts"] as? com.google.firebase.Timestamp,
                )
            }
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
                    BookQuoteCard(
                        quote = BookQuote(
                            id              = post.id,
                            text            = post.quoteText,
                            bookTitle       = post.bookName,
                            authorName      = post.authorName,
                            userDisplayName = post.displayName,
                            userPhotoURL    = post.photoURL,
                        )
                    )
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
) {
    val db = remember { FirebaseFirestore.getInstance() }
    // null = yükleniyor, "" = bulunamadı, "id" = bulundu
    var resolvedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authorName) {
        if (authorName.isBlank()) { resolvedId = ""; return@LaunchedEffect }
        try {
            // 1. Tam ada göre ara
            var snap = db.collection("authors")
                .whereEqualTo("name", authorName.trim())
                .limit(1).get().await()
            if (!snap.isEmpty) {
                resolvedId = snap.documents[0].id
                return@LaunchedEffect
            }
            // 2. lowercase'e göre ara
            snap = db.collection("authors")
                .whereEqualTo("nameLower", authorName.trim().lowercase())
                .limit(1).get().await()
            if (!snap.isEmpty) {
                resolvedId = snap.documents[0].id
                return@LaunchedEffect
            }
            // 3. Bulunamadı → otomatik oluştur
            val newId = db.collection("authors").add(hashMapOf(
                "name"          to authorName.trim(),
                "nameLower"     to authorName.trim().lowercase(),
                "bio"           to "", "photoURL" to "", "birthYear" to 0,
                "nationality"   to "", "bookCount" to 0, "quoteCount" to 0,
                "reviewCount"   to 0, "followerCount" to 0,
                "autoCreated"   to true,
                "ts"            to com.google.firebase.Timestamp.now(),
            )).await().id
            resolvedId = newId
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
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var resolvedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookName) {
        if (bookName.isBlank()) { resolvedId = ""; return@LaunchedEffect }
        try {
            // 1. Tam ada göre ara
            var snap = db.collection("library_books")
                .whereEqualTo("title", bookName.trim())
                .limit(1).get().await()
            if (!snap.isEmpty) {
                resolvedId = snap.documents[0].id
                return@LaunchedEffect
            }
            // 2. lowercase'e göre ara
            snap = db.collection("library_books")
                .whereEqualTo("titleLower", bookName.trim().lowercase())
                .limit(1).get().await()
            if (!snap.isEmpty) {
                resolvedId = snap.documents[0].id
                return@LaunchedEffect
            }
            // 3. Bulunamadı → otomatik oluştur
            val newId = db.collection("library_books").add(hashMapOf(
                "title"       to bookName.trim(),
                "titleLower"  to bookName.trim().lowercase(),
                "authorId"    to "", "authorName" to "",
                "coverImg"    to "", "genre" to "", "publishYear" to 0,
                "synopsis"    to "", "pageCount" to 0,
                "quoteCount"  to 0, "reviewCount" to 0, "avgRating" to 0f,
                "autoCreated" to true,
                "ts"          to com.google.firebase.Timestamp.now(),
            )).await().id
            resolvedId = newId
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
