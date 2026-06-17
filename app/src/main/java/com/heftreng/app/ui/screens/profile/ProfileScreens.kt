package com.heftreng.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.heftreng.app.utils.openUrl
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.data.model.ReadingListEntry
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.User
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.auth.heftrangTextFieldColors
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.component.FullScreenImageViewer
import com.heftreng.app.ui.screens.books.BookCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.screens.social.FollowListSheet
import com.heftreng.app.viewmodel.SettingsViewModel
import com.heftreng.app.viewmodel.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ProfileScreen(
    uid          : String,
    navController: NavController,
    language     : String = "tr",
    vm           : ProfileViewModel     = hiltViewModel(),
    feedVm       : FeedViewModel        = hiltViewModel(),
    bookVm       : BookViewModel        = hiltViewModel(),
    rlVm         : ReadingListViewModel = hiltViewModel(),
    msgsVm       : MessagesViewModel    = hiltViewModel(),
    socialVm     : SocialViewModel      = hiltViewModel(),
    settingsVm   : SettingsViewModel    = hiltViewModel(),
) {
    val user           by vm.user.collectAsState()
    val badgeIds       by vm.badgeIds.collectAsState()
    val posts          by vm.posts.collectAsState()
    val isFollowing    by vm.isFollowing.collectAsState()
    val followRequestStatus by vm.followRequestStatus.collectAsState()
    val hasMorePosts   by vm.hasMorePosts.collectAsState()
    val loadingMore    by vm.loadingMorePosts.collectAsState()
    val hasMoreFollowers by socialVm.hasMoreFollowers.collectAsState()
    val hasMoreFollowing by socialVm.hasMoreFollowing.collectAsState()
    val followersCountRaw by vm.followersCount.collectAsState()
    val followingCountRaw by vm.followingCount.collectAsState()
    val loading        by vm.loading.collectAsState()
    val userNotFound   by vm.userNotFound.collectAsState()
    val allMyBooks     by bookVm.myBooks.collectAsState()
    val mySerials      = allMyBooks.filter { it.type == "serial" }
    val myBooks        = allMyBooks.filter { it.type == "book" }
    val rlEntries      by rlVm.entries.collectAsState()

    val followers     by socialVm.followers.collectAsState()
    val following     by socialVm.following.collectAsState()
    val followersLoading by socialVm.followersLoading.collectAsState()
    val followingLoading by socialVm.followingLoading.collectAsState()

    // Gerçek liste yüklendikten sonra sayacı güncelle; yoksa Firestore users dokümanındaki değeri kullan
    val followersCount = if (!followersLoading && followers.isNotEmpty()) followers.size else followersCountRaw
    val followingCount = if (!followingLoading && following.isNotEmpty()) following.size else followingCountRaw
    val socialLoading by socialVm.loading.collectAsState()
    var showFollowers  by remember { mutableStateOf(false) }
    var showFollowing  by remember { mutableStateOf(false) }
    var msgPermDenied  by remember { mutableStateOf(false) }
    var showReadBooksSheet by remember { mutableStateOf(false) }
    var showQuotesSheet    by remember { mutableStateOf(false) }
    val userQuotes        by vm.userQuotes.collectAsState()
    val userQuotesLoading by vm.userQuotesLoading.collectAsState()

    val isMe      = uid == "me" || uid == vm.myUid
    val targetUid = if (uid == "me") vm.myUid else uid
    val ku = language == "ku"

    val isPrivate      = user?.isPrivate ?: false
    val canSeeContent  = isMe || !isPrivate || isFollowing

    val tabs = listOf(
        Strings.posts(language),
        Strings.readingList(language),
        if (ku) "Pirtûk & Rêze" else "Kitaplar & Seriler",
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    // Mesaj navigate state — composable dışında navigate yapabilmek için
    var navigateToConv by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(navigateToConv) {
        navigateToConv?.let { convId ->
            navController.navigate("message/$convId")
            navigateToConv = null
        }
    }

    LaunchedEffect(uid) {
        vm.load(uid)
        bookVm.loadMyBooks(targetUid)
        rlVm.load(targetUid)
    }


    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh  = {
            isRefreshing = true
            vm.load(targetUid, forceRefresh = true)
        }
    )
    LaunchedEffect(isRefreshing) { if (isRefreshing) isRefreshing = false }

    // Silinmiş / bulunamayan hesap
    if (userNotFound) {
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = { Text(if (ku) "Profîl" else "Profil", color = OnBackground) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            }
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonOff,
                        null,
                        tint     = Muted,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (ku) "Ev hesab tune" else "Bu hesap mevcut değil",
                        color    = OnBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (ku) "Dibe ku hatibe rakirin." else "Silinmiş veya askıya alınmış olabilir.",
                        color    = Muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(if (ku) "Vegere" else "Geri Dön", color = Primary)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        modifier       = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        user?.let { u -> if (u.username.isNotBlank()) "@${u.username}" else u.displayName.ifBlank { if (ku) "Profîl" else "Profil" } } ?: Strings.loading(language),
                        color = OnBackground, fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (!isMe) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                        }
                    }
                },
                actions = {
                    if (isMe) {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, null, tint = Muted)
                        }
                    } else {
                        // Başkasının profilinde — 3 nokta menü
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, null, tint = Muted)
                            }
                            DropdownMenu(
                                expanded         = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier         = Modifier.background(HeftSurface),
                            ) {
                                DropdownMenuItem(
                                    text    = { Text(Strings.blockUser(language), color = Color(0xFFEF4444)) },
                                    onClick = {
                                        menuExpanded = false
                                        if (user != null) {
                                            settingsVm.blockUser(
                                                targetUid   = user!!.uid,
                                                targetName  = user!!.displayName,
                                                targetPhoto = user!!.photoURL,
                                            )
                                            navController.popBackStack()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Block, null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp))
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {

        if (loading && user == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        // ── TEK LazyColumn — header + sticky tabbar + içerik hepsi scroll ──
        val listState = rememberLazyListState()

        // Tab'ın sticky olması için: header kaç item — 1 item (header), 1 item (tabbar), sonra içerik
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {

            // ── 1. Profil başlığı ────────────────────────────────────────
            item(key = "profile_header") {
                ProfileHeader(
                    user           = user,
                    isMe           = isMe,
                    isFollowing    = isFollowing,
                    followRequestStatus = followRequestStatus,
                    followersCount = followersCount,
                    followingCount = followingCount,
                    postsCount     = posts.size,
                    onFollow       = { vm.toggleFollow(targetUid) },
                    onEditProfile  = { navController.navigate(Screen.EditProfile.route) },
                    onFollowers    = {
                        socialVm.loadFollowers(targetUid)
                        showFollowers = true
                    },
                    onFollowing    = {
                        socialVm.loadFollowing(targetUid)
                        showFollowing = true
                    },
                    onMessage      = {
                        if (!isMe && targetUid.isNotBlank()) {
                            // Mesaj iznini kontrol et
                            val targetUser = user
                            val perm = targetUser?.messagePermission ?: "everyone"
                            val canMsg = when (perm) {
                                "nobody"    -> false
                                "followers" -> isFollowing
                                else        -> true // "everyone"
                            }
                            if (canMsg) {
                                msgsVm.startOrOpenConversation(targetUid) { convId ->
                                    navigateToConv = convId
                                }
                            } else {
                                msgPermDenied = true
                            }
                        }
                    },
                    language = language,
                )
            }

            // ── 1b. Okuma Özeti Hero — "X kitap okudum · Y alıntı · Z gün streak" ──
            item(key = "reading_summary_hero") {
                ReadingSummaryHero(
                    booksRead   = user?.booksRead ?: (rlEntries["okudum"]?.size ?: 0),
                    quotesCount = user?.quotesShared ?: posts.count { it.quoteText.isNotBlank() },
                    streak      = user?.streak ?: 0,
                    language    = language,
                    onBooksClick  = { showReadBooksSheet = true },
                    onQuotesClick = {
                        vm.loadUserQuotes(targetUid)
                        showQuotesSheet = true
                    },
                )
            }

            // ── 1c. Rozetler — Öncelik 4 (user_badges) ─────────────────────
            if (badgeIds.isNotEmpty()) {
                item(key = "badges_row") {
                    BadgesRow(badgeIds = badgeIds, language = language)
                }
            }

            // ── 2. Tab bar — stickyHeader ────────────────────────────────
            stickyHeader(key = "tab_bar") {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Background,
                    contentColor     = OnBackground,
                    divider          = { androidx.compose.material3.HorizontalDivider(color = Divider, thickness = 0.5.dp) },
                    indicator = { tabPositions ->
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .padding(horizontal = 24.dp)
                                .height(2.5.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(androidx.compose.ui.graphics.Color(0xFF8B5CF6), androidx.compose.ui.graphics.Color(0xFFEC4899))
                                    )
                                )
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected               = selectedTab == i,
                            onClick                = { selectedTab = i },
                            text                   = {
                                Text(
                                    title,
                                    fontSize   = 12.sp,
                                    fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            selectedContentColor   = OnBackground,
                            unselectedContentColor = Muted,
                        )
                    }
                }
            }

            // ── 3. Tab içerikleri — inline items ─────────────────────────
            if (!canSeeContent) {
                // Gizli hesap — takipçi değil
                item(key = "locked_profile") {
                    Column(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 32.dp),
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        verticalArrangement   = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint               = Muted,
                            modifier           = Modifier.size(52.dp),
                        )
                        Text(
                            if (ku) "Ev hesab taybet e" else "Bu hesap gizli",
                            color      = OnBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 16.sp,
                        )
                        Text(
                            if (ku) "Ji bo dîtina barkirin û nivîsên vê hesabê, pêwîste hûn bişopînin."
                            else    "Gönderileri görmek için takip et.",
                            color    = Muted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else when (selectedTab) {

                // ─── Gönderiler ───────────────────────────────────────────
                0 -> {
                    if (loading && posts.isEmpty()) {
                        item(key = "posts_loading") {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color    = Amber,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    } else if (posts.isEmpty()) {
                        item(key = "posts_empty") {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Article, null,
                                        tint     = Muted,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(Strings.noPosts(language), color = Muted)
                                }
                            }
                        }
                    } else {
                        items(posts, key = { "post_${it.id}" }) { post ->
                            PostCard(
                                post      = post,
                                onLike    = { vm.toggleLikePost(post) },
                                onSave    = { feedVm.toggleSave(post) },
                                onProfile = {
                                    if (!isMe) navController.navigate(Screen.Profile.go(post.uid))
                                },
                                onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                onShare   = { feedVm.repost(post) },
                                onDelete  = if (isMe) ({ vm.deleteOwnPost(post.id) }) else null,
                                onEdit    = if (isMe) ({ newText -> vm.editOwnPost(post.id, newText) }) else null,
                                onTap     = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                onTapRepost = { repostId, repostType ->
                                    when (repostType) {
                                        "feed"    -> navController.navigate(Screen.PostDetail.go(repostId))
                                        "serial"  -> navController.navigate("serial/$repostId")
                                        "chapter" -> navController.navigate("chapter/$repostId")
                                        else      -> navController.navigate(Screen.PostDetail.go(repostId))
                                    }
                                },
                                language = language,
                            )
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                        // ── Daha Fazla Yükle ──────────────────────────────
                        if (hasMorePosts) {
                            item(key = "load_more_posts") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(
                                            color = Primary,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        OutlinedButton(
                                            onClick = { vm.loadMorePosts(targetUid) },
                                            shape = RoundedCornerShape(20.dp),
                                        ) {
                                            Text(if (language == "ku") "Zêdetir bar bike" else "Daha Fazla Yükle")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Kitaplar & Seriler ───────────────────────────────────
                2 -> {
                    val allMyContent = allMyBooks
                    if (allMyContent.isEmpty()) {
                        item(key = "books_serials_empty") {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.AutoStories, null,
                                        tint     = Muted,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        if (ku) "Hîn pirtûk/rêze tune" else "Henüz kitap veya seri yok",
                                        color = Muted,
                                    )
                                    if (isMe) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = { navController.navigate("serials") }) {
                                            Text(if (ku) "+ Nû Zêde Bike" else "+ Yeni Ekle", color = Amber)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Seriler önce, kitaplar sonra — her gruba küçük başlık
                        if (mySerials.isNotEmpty()) {
                            item(key = "serials_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.AutoStories, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        if (ku) "Rêze" else "Seriler",
                                        color      = Primary,
                                        fontSize   = 12.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    )
                                }
                            }
                            items(mySerials, key = { "serial_${it.id}" }) { book ->
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    BookCard(
                                        book      = book,
                                        onClick   = { navController.navigate("book/${book.id}?type=${book.type}") },
                                        onLike    = { bookVm.toggleLikeBook(book) },
                                        onProfile = { navController.navigate("profile/${book.uid}") },
                                    )
                                }
                            }
                        }
                        if (myBooks.isNotEmpty()) {
                            item(key = "books_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.MenuBook, null, tint = Amber, modifier = Modifier.size(16.dp))
                                    Text(
                                        if (ku) "Pirtûk" else "Kitaplar",
                                        color      = Amber,
                                        fontSize   = 12.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    )
                                }
                            }
                            items(myBooks, key = { "book_${it.id}" }) { book ->
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    BookCard(
                                        book      = book,
                                        onClick   = { navController.navigate("book/${book.id}?type=${book.type}") },
                                        onLike    = { bookVm.toggleLikeBook(book) },
                                        onProfile = { navController.navigate("profile/${book.uid}") },
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Okuma Listesi ────────────────────────────────────────
                1 -> {
                    val allEmpty = rlEntries.values.all { it.isEmpty() }
                    if (allEmpty) {
                        item(key = "rl_empty") {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.LibraryBooks, null,
                                        tint     = Muted,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(if (ku) "Lîsteya xwendinê vala ye" else "Okuma listesi boş", color = Muted)
                                }
                            }
                        }
                    } else {
                        val statusKeys = listOf("okuyorum", "okumak_istiyorum", "okudum", "biraktim")
                        val statuses: List<Pair<String, String>> = statusKeys.map { key -> key to Strings.readingStatus(language, key) }
                        val statusColors = mapOf(
                            "okuyorum"         to Color(0xFF2563EB),
                            "okumak_istiyorum" to Color(0xFF7C3AED),
                            "okudum"           to Color(0xFF059669),
                            "biraktim"         to Color(0xFFDC2626),
                        )
                        statuses.forEach { (key, label) ->
                            val list = rlEntries[key] ?: emptyList()
                            if (list.isNotEmpty()) {
                                val color = statusColors[key] ?: Amber
                                item(key = "rl_header_$key") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier          = Modifier.padding(
                                            horizontal = 12.dp, vertical = 8.dp
                                        ),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(color)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "$label (${list.size})",
                                            color      = color,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 13.sp,
                                        )
                                    }
                                }
                                items(list, key = { "rl_${it.sid}" }) { entry ->
                                    RlEntryRow(
                                        entry   = entry,
                                        onClick = {
                                            if (entry.source == "library")
                                                navController.navigate("library_book_detail/${entry.sid}")
                                            else
                                                navController.navigate("serial/${entry.sid}")
                                        },
                                    )
                                }
                                item(key = "rl_div_$key") {
                                    HorizontalDivider(
                                        color    = Divider,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
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

    // ── Takipçi/Takip sheet'leri ─────────────────────────────────────────────
    if (showFollowers) {
        FollowListSheet(
            title      = Strings.followersTitle(language, followersCount),
            entries    = followers,
            loading    = followersLoading,
            hasMore    = hasMoreFollowers,
            onLoadMore = { socialVm.loadMoreFollowers() },
            onDismiss  = { showFollowers = false; socialVm.clearFollowers() },
            onProfile  = { u ->
                showFollowers = false
                navController.navigate("profile/$u")
            },
        )
    }
    if (showFollowing) {
        FollowListSheet(
            title      = Strings.followingTitle(language, followingCount),
            entries    = following,
            loading    = followingLoading,
            hasMore    = hasMoreFollowing,
            onLoadMore = { socialVm.loadMoreFollowing() },
            onDismiss  = { showFollowing = false; socialVm.clearFollowing() },
            onProfile  = { u ->
                showFollowing = false
                navController.navigate("profile/$u")
            },
        )
    }

    // ── Mesaj izni reddedildi dialog ─────────────────────────────────────
    if (msgPermDenied) {
        AlertDialog(
            onDismissRequest = { msgPermDenied = false },
            title = { Text(if (language == "ku") "Peyam nayê şandin" else "Mesaj Gönderilemez", color = OnBackground, fontWeight = FontWeight.Bold) },
            text  = { Text(if (language == "ku") "Ev bikarhêner tenê ji şopînerên xwe peyam qebûl dike." else "Bu kullanıcı mesajları kısıtlamış.", color = Muted) },
            confirmButton = { TextButton(onClick = { msgPermDenied = false }) { Text("Tamam", color = Primary) } },
            containerColor = HeftSurface,
        )
    }

    // ── "X kitap okudum" → okunan kitaplar sheet'i ───────────────────────────
    if (showReadBooksSheet) {
        ReadBooksSheet(
            entries  = rlEntries["okudum"] ?: emptyList(),
            language = language,
            onDismiss = { showReadBooksSheet = false },
            onClick   = { entry ->
                showReadBooksSheet = false
                if (entry.source == "library")
                    navController.navigate("library_book_detail/${entry.sid}")
                else
                    navController.navigate("serial/${entry.sid}")
            },
        )
    }

    // ── "X alıntı" → kullanıcının paylaştığı alıntılar sheet'i ───────────────
    if (showQuotesSheet) {
        UserQuotesSheet(
            quotes    = userQuotes,
            loading   = userQuotesLoading,
            language  = language,
            onDismiss = { showQuotesSheet = false },
            onClick   = { q ->
                showQuotesSheet = false
                if (q.bookId.isNotBlank())
                    navController.navigate("library_book_detail/${q.bookId}")
            },
        )
    }
}

// ── Profil başlık bileşeni ────────────────────────────────────────────────────
@Composable
private fun ProfileHeader(
    user          : User?,
    isMe          : Boolean,
    isFollowing   : Boolean,
    followRequestStatus: String = "none",
    followersCount: Int,
    followingCount: Int,
    postsCount    : Int,
    onFollow      : () -> Unit,
    onEditProfile : () -> Unit,
    onFollowers   : () -> Unit = {},
    onFollowing   : () -> Unit = {},
    onMessage     : () -> Unit,
    language      : String = "tr",
) {
    val ku = language == "ku"
    Column(modifier = Modifier.fillMaxWidth()) {
        // Kapak fotoğrafı
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(SurfaceVar)
        ) {
            var showCover by remember { mutableStateOf(false) }
            if (user?.coverPhoto?.isNotEmpty() == true) {
                AsyncImage(
                    model              = user.coverPhoto,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize().clickable { showCover = true },
                )
                if (showCover) FullScreenImageViewer(url = user.coverPhoto) { showCover = false }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Avatar satırı
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                val avatarUrl = user?.photoURL?.ifEmpty { null }
                var showAvatar by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .offset(y = (-28).dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Primary, PrimaryLight)
                            )
                        )
                        .then(if (avatarUrl != null) Modifier.clickable { showAvatar = true } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model              = avatarUrl,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 26.sp,
                        )
                    }
                }
                if (showAvatar && avatarUrl != null)
                    FullScreenImageViewer(url = avatarUrl) { showAvatar = false }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                if (isMe) {
                    OutlinedButton(
                        onClick = onEditProfile,
                        shape   = RoundedCornerShape(10.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = OnBackground),
                    ) { Text(Strings.edit(language), fontSize = 13.sp) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Mesaj butonu
                        IconButton(
                            onClick  = onMessage,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceVar),
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = OnBackground)
                        }
                        // Takip butonu — 3 durum: takip ediliyor / istek bekliyor / takip et
                        Button(
                            onClick = onFollow,
                            shape   = RoundedCornerShape(10.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isFollowing                       -> SurfaceVar
                                    followRequestStatus == "pending"  -> SurfaceVar
                                    else                              -> Amber
                                },
                                contentColor = when {
                                    isFollowing                       -> OnBackground
                                    followRequestStatus == "pending"  -> OnBackground
                                    else                              -> Color.Black
                                },
                            ),
                        ) {
                            Text(
                                when {
                                    isFollowing                       -> Strings.unfollow(language)
                                    followRequestStatus == "pending"  -> Strings.followRequested(language)
                                    else                              -> Strings.follow(language)
                                },
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // İsim & kullanıcı adı
            Text(
                user?.displayName ?: "",
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
                fontSize   = 18.sp,
            )
            if (user?.username?.isNotBlank() == true) {
                Text("@${user.username}", color = Muted, fontSize = 13.sp)
            }
            if (user?.bio?.isNotBlank() == true) {
                Spacer(Modifier.height(6.dp))
                Text(user.bio, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (user?.website?.isNotBlank() == true) {
                Spacer(Modifier.height(2.dp))
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { openUrl(context, user.website) }
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint     = Amber,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        user.website,
                        color    = Amber,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // İstatistikler
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem(postsCount,     Strings.posts(language),      onClick = null)
                StatItem(followersCount, Strings.followers(language),   onClick = onFollowers)
                StatItem(followingCount, Strings.following(language),   onClick = onFollowing)
                if ((user?.xp ?: 0) > 0) StatItem(user?.xp ?: 0, "XP", onClick = null)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
        }
    }
}

@Composable
private fun RlEntryRow(entry: ReadingListEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.coverImg.isNotEmpty()) {
            AsyncImage(
                model              = entry.coverImg,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(36.dp, 50.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(SurfaceVar),
            )
        } else {
            Box(
                Modifier
                    .size(36.dp, 50.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(SurfaceVar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            entry.title,
            color      = OnBackground,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ReadBooksSheet — "X kitap okudum" tıklanınca açılan, "okudum" durumundaki
//  kitap/serileri listeleyen bottom sheet (Öncelik: profil ilerleme kartı)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadBooksSheet(
    entries  : List<ReadingListEntry>,
    language : String,
    onDismiss: () -> Unit,
    onClick  : (ReadingListEntry) -> Unit,
) {
    val ku = language == "ku"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Divider))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (ku) "Pirtûkên xwendî (${entries.size})" else "Okunan Kitaplar (${entries.size})",
                color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Muted) }
        }
        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text(if (ku) "Hîn pirtûk tune" else "Henüz okunan kitap yok", color = Muted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(entries, key = { "readbook_${it.sid}" }) { entry ->
                    RlEntryRow(entry = entry, onClick = { onClick(entry) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  UserQuotesSheet — "X alıntı" tıklanınca açılan, kullanıcının paylaştığı
//  tüm alıntıları (Supabase book_quotes) listeleyen bottom sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserQuotesSheet(
    quotes   : List<com.heftreng.app.data.repository.BookQuoteRow>,
    loading  : Boolean,
    language : String,
    onDismiss: () -> Unit,
    onClick  : (com.heftreng.app.data.repository.BookQuoteRow) -> Unit,
) {
    val ku = language == "ku"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Divider))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (ku) "Gotinên min (${quotes.size})" else "Alıntılarım (${quotes.size})",
                color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Muted) }
        }
        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        if (loading) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
            }
        } else if (quotes.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text(if (ku) "Hîn gotin tune" else "Henüz alıntı yok", color = Muted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(quotes, key = { "userquote_${it.id}" }) { q ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick(q) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "\u201C${q.text}\u201D",
                            color    = OnBackground,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOf(q.authorName, q.bookTitle).filter { it.isNotBlank() }.joinToString(" · "),
                            color    = Amber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ReadingSummaryHero(
    booksRead  : Int,
    quotesCount: Int,
    streak     : Int,
    language   : String = "tr",
    onBooksClick : () -> Unit = {},
    onQuotesClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Primary.copy(alpha = 0.18f), Amber.copy(alpha = 0.10f))
                )
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        ReadingHeroStat(
            icon  = Icons.Outlined.AutoStories,
            value = booksRead.toString(),
            label = if (language == "ku") "pirtûk xwendin" else "kitap okudum",
            onClick = onBooksClick,
        )
        ReadingHeroDivider()
        ReadingHeroStat(
            icon  = Icons.Outlined.FormatQuote,
            value = quotesCount.toString(),
            label = if (language == "ku") "gotin" else "alıntı",
            onClick = onQuotesClick,
        )
        ReadingHeroDivider()
        ReadingHeroStat(
            icon  = Icons.Outlined.LocalFireDepartment,
            value = streak.toString(),
            label = if (language == "ku") "roj streak" else "gün streak",
            valueColor = Amber,
        )
    }
}

@Composable
private fun ReadingHeroDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(Divider)
    )
}

@Composable
private fun ReadingHeroStat(
    icon      : ImageVector,
    value     : String,
    label     : String,
    valueColor: Color = OnBackground,
    onClick   : (() -> Unit)? = null,
) {
    Column(
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = valueColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, color = valueColor, fontSize = 16.sp)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BadgesRow — Öncelik 4 rozet sistemi (Supabase user_badges + BadgeCatalog)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BadgesRow(badgeIds: Set<String>, language: String = "tr") {
    val earned = com.heftreng.app.data.model.BadgeCatalog.all.filter { it.id in badgeIds }
    if (earned.isEmpty()) return

    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(earned, key = { it.id }) { badge ->
            var showInfo by remember { mutableStateOf(false) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { showInfo = true },
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Amber.copy(alpha = 0.30f), Primary.copy(alpha = 0.20f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(badge.icon, null, tint = Amber, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    badge.title(language),
                    color      = Muted,
                    fontSize   = 9.sp,
                    maxLines   = 2,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 11.sp,
                )
            }

            if (showInfo) {
                AlertDialog(
                    onDismissRequest = { showInfo = false },
                    containerColor   = HeftSurface,
                    icon  = { Icon(badge.icon, null, tint = Amber) },
                    title = { Text(badge.title(language), color = OnBackground, fontWeight = FontWeight.Bold) },
                    text  = { Text(badge.desc(language), color = Muted) },
                    confirmButton = {
                        TextButton(onClick = { showInfo = false }) {
                            Text(Strings.ok(language), color = Primary)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun StatItem(count: Int, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
    ) {
        Text(
            count.toString(),
            fontWeight = FontWeight.Bold,
            color      = OnBackground,
            fontSize   = 16.sp,
        )
        Text(
            label,
            color    = if (onClick != null) Primary else Muted,
            fontSize = 11.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    vm           : ProfileViewModel = hiltViewModel(),
    language     : String           = "tr",
) {
    val ku = language == "ku"
    val user        by vm.user.collectAsState()
    val loading     by vm.loading.collectAsState()
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var bio         by remember(user) { mutableStateOf(user?.bio ?: "") }
    var website     by remember(user) { mutableStateOf(user?.website ?: "") }
    var username    by remember(user) { mutableStateOf(user?.username ?: "") }
    var usernameErr by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    val storage     = com.google.firebase.storage.FirebaseStorage.getInstance()
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            vm.updateProfilePhoto(
                imageUri = it,
                storage  = storage,
                onDone   = { scope.launch { snackbarHostState.showSnackbar(if (ku) "Wêneya profîlê hate nûkirin ✓" else "Profil fotoğrafı güncellendi ✓") } },
                onError  = { msg -> scope.launch { snackbarHostState.showSnackbar(if (ku) "Çewtî: $msg" else "Hata: $msg") } },
            )
        }
    }
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            vm.updateCoverPhoto(
                imageUri = it,
                storage  = storage,
                onDone   = { scope.launch { snackbarHostState.showSnackbar(if (ku) "Wêneya bergê hate nûkirin ✓" else "Kapak fotoğrafı güncellendi ✓") } },
                onError  = { msg -> scope.launch { snackbarHostState.showSnackbar(if (ku) "Çewtî: $msg" else "Hata: $msg") } },
            )
        }
    }

    LaunchedEffect(Unit) { vm.load("me") }

    Scaffold(
        modifier          = Modifier.imePadding(),
        containerColor    = Background,
        snackbarHost      = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Strings.editProfile(language),
                        color      = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp).padding(end = 4.dp),
                            color       = Amber,
                            strokeWidth = 2.dp,
                        )
                    }
                    TextButton(
                        onClick  = {
                            val trimmedUsername = username.trim()
                            val currentUsername = user?.username ?: ""
                            val usernameChanged = trimmedUsername != currentUsername

                            if (usernameChanged && trimmedUsername.isNotBlank()) {
                                vm.updateUsername(trimmedUsername,
                                    onSuccess = {
                                        vm.updateProfile(displayName, bio, website)
                                        navController.popBackStack()
                                    },
                                    onError = { usernameErr = it },
                                )
                            } else {
                                vm.updateProfile(displayName, bio, website)
                                navController.popBackStack()
                            }
                        },
                        enabled = !loading,
                    ) {
                        Text(Strings.save(language), color = if (loading) Muted else Amber, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick  = { if (!loading) photoPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Amber, strokeWidth = 2.dp)
                    else Text(Strings.profilePhoto(language), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick  = { if (!loading) coverPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Amber, strokeWidth = 2.dp)
                    else Text(Strings.coverPhoto(language), fontSize = 12.sp)
                }
            }

            // Mevcut fotoğrafları göster
            user?.let { u ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (u.photoURL.isNotEmpty()) {
                        AsyncImage(
                            model        = u.photoURL,
                            contentDescription = "Profil",
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SurfaceVar),
                        )
                    }
                    if (u.coverPhoto.isNotEmpty()) {
                        AsyncImage(
                            model        = u.coverPhoto,
                            contentDescription = "Kapak",
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier
                                .height(48.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVar),
                        )
                    }
                }
            }

            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text(Strings.fullName(language)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = heftrangTextFieldColors(),
            )
            OutlinedTextField(
                value         = username,
                onValueChange = {
                    username    = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }
                    usernameErr = null
                },
                label         = { Text("@kullanıcı adı") },
                singleLine    = true,
                isError       = usernameErr != null,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = heftrangTextFieldColors(),
            )
            if (usernameErr != null) {
                Text(usernameErr!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            OutlinedTextField(
                value         = bio,
                onValueChange = { bio = it },
                label         = { Text("Bio") },
                maxLines      = 5,
                modifier      = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape         = RoundedCornerShape(12.dp),
                colors        = heftrangTextFieldColors(),
            )
            OutlinedTextField(
                value         = website,
                onValueChange = { website = it },
                label         = { Text("Website") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = heftrangTextFieldColors(),
            )
        }
    }
}
