package com.heftreng.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import java.net.URLEncoder
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.heftreng.app.data.model.Post
import com.heftreng.app.data.model.ReadingListEntry
import com.heftreng.app.data.model.Serial
import com.heftreng.app.data.model.User
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.auth.heftrangTextFieldColors
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.screens.serials.SerialCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.screens.social.FollowListSheet
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.component.QuoteInputSection
import com.heftreng.app.ui.component.QuotePayload
import com.heftreng.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    uid          : String,
    navController: NavController,
    language     : String = "tr",
    vm           : ProfileViewModel     = hiltViewModel(),
    feedVm       : FeedViewModel        = hiltViewModel(),
    serialsVm    : SerialsViewModel     = hiltViewModel(),
    rlVm         : ReadingListViewModel = hiltViewModel(),
    msgsVm       : MessagesViewModel    = hiltViewModel(),
    socialVm     : SocialViewModel      = hiltViewModel(),
) {
    val user           by vm.user.collectAsState()
    val posts          by vm.posts.collectAsState()
    val savedPosts     by vm.savedPosts.collectAsState()
    val savedLoading   by vm.savedLoading.collectAsState()

    val myPhotoURL   by remember {
        val u = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        mutableStateOf(u?.photoUrl?.toString() ?: "")
    }
    var composeText  by remember { mutableStateOf("") }
    var composeQuote by remember { mutableStateOf<QuotePayload?>(null) }
    var showQuoteDlg by remember { mutableStateOf(false) }
    val isFollowing    by vm.isFollowing.collectAsState()
    val followersCount by vm.followersCount.collectAsState()
    val followingCount by vm.followingCount.collectAsState()
    val loading        by vm.loading.collectAsState()
    val mySerials      by serialsVm.mySerials.collectAsState()
    val rlEntries      by rlVm.entries.collectAsState()

    val followers     by socialVm.followers.collectAsState()
    val following     by socialVm.following.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    var showFollowers  by remember { mutableStateOf(false) }
    var showFollowing  by remember { mutableStateOf(false) }

    val isMe      = uid == "me" || uid == vm.myUid
    val targetUid = if (uid == "me") vm.myUid else uid

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Gönderiler", "Seriler", "Okuma Listesi", "Beğendikleri")

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
        serialsVm.loadMySerials(targetUid)
        rlVm.load(targetUid)
    }

    if (showQuoteDlg) {
        QuoteDialog(
            onDismiss = { showQuoteDlg = false },
            onConfirm = { payload -> composeQuote = payload; showQuoteDlg = false },
        )
    }

    Scaffold(
        modifier       = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        user?.username?.let { "@$it" } ?: user?.displayName ?: "",
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->

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
                        val myUid = vm.myUid
                        if (!isMe && targetUid.isNotBlank() && myUid.isNotBlank()) {
                            msgsVm.startOrOpenConversation(targetUid) { convId ->
                                navigateToConv = convId
                            }
                        }
                    },
                )
            }

            // ── 1.5 Compose alanı — sadece kendi profilde ────────────
            if (isMe) {
                item(key = "prof_compose") {
                    ProfileComposeBox(
                        text          = composeText,
                        onTextChange  = { composeText = it },
                        quote         = composeQuote,
                        onQuoteAdd    = { showQuoteDlg = true },
                        onQuoteRemove = { composeQuote = null },
                        onSend        = {
                            if (composeText.isNotBlank() || composeQuote != null) {
                                val q = composeQuote
                                feedVm.createPost(
                                    text       = composeText.trim(),
                                    quoteText  = q?.text ?: "",
                                    authorName = q?.authorName ?: "",
                                    bookName   = q?.bookName ?: "",
                                )
                                composeText  = ""
                                composeQuote = null
                            }
                        },
                        photoURL = myPhotoURL,
                        language = language,
                    )
                }
            }

            // ── 2. Tab bar — stickyHeader ────────────────────────────────
            stickyHeader(key = "tab_bar") {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Background,
                    contentColor     = Amber,
                    indicator = { tabPositions ->
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .height(2.dp)
                                .background(Amber)
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected               = selectedTab == i,
                            onClick                = { selectedTab = i },
                            text                   = { Text(title, fontSize = 12.sp) },
                            selectedContentColor   = Amber,
                            unselectedContentColor = Muted,
                        )
                    }
                }
            }

            // ── 3. Tab içerikleri — inline items ─────────────────────────
            when (selectedTab) {

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
                                    Text("Henüz gönderi yok", color = Muted)
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
                                onTap       = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                onTapAuthor = { author -> val enc = URLEncoder.encode(author, "UTF-8"); navController.navigate("author_quotes/$enc") },
                                onTapBook   = { book   -> val enc = URLEncoder.encode(book, "UTF-8"); navController.navigate("book_quotes/$enc") },
                            )
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }

                // ─── Seriler ──────────────────────────────────────────────
                1 -> {
                    if (mySerials.isEmpty()) {
                        item(key = "serials_empty") {
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
                                    Text("Henüz seri yok", color = Muted)
                                    if (isMe) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(
                                            onClick = { navController.navigate("serials") },
                                        ) {
                                            Text("+ Yeni Seri", color = Amber)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(mySerials, key = { "serial_${it.id}" }) { serial ->
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                SerialCard(
                                    serial  = serial,
                                    onClick = { navController.navigate("serial/${serial.id}") },
                                    onLike  = { serialsVm.toggleLikeSerial(serial) },
                                )
                            }
                        }
                    }
                }

                // ─── Okuma Listesi ────────────────────────────────────────
                2 -> {
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
                                    Text("Okuma listesi boş", color = Muted)
                                }
                            }
                        }
                    } else {
                        val statuses = listOf(
                            "okuyorum"         to "Okuyorum",
                            "okumak_istiyorum" to "Okumak İstiyorum",
                            "okudum"           to "Okudum",
                            "biraktim"         to "Bıraktım",
                        )
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
                                        onClick = { navController.navigate("serial/${entry.sid}") },
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
            
                3 -> {
                    // ── Beğendikleri ─────────────────────────────────────────
                    if (savedLoading) {
                        item(key = "saved_loading") {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp)) }
                        }
                    } else if (savedPosts.isEmpty()) {
                        item(key = "saved_empty") {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Bookmark, null,
                                        tint     = Muted,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("Henüz kaydedilen gönderi yok", color = Muted)
                                }
                            }
                        }
                    } else {
                        items(savedPosts, key = { "saved_${it.id}" }) { post ->
                            PostCard(
                                post      = post,
                                onLike    = { vm.toggleLikePost(post) },
                                onSave    = { /* zaten kayıtlı */ },
                                onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                                onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                onShare   = { },
                                onDelete  = if (vm.myUid == post.uid) ({ vm.deleteOwnPost(post.id) }) else null,
                                onEdit    = if (vm.myUid == post.uid) ({ newText -> vm.editOwnPost(post.id, newText) }) else null,
                                onTap       = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                onTapAuthor = { author -> val enc = URLEncoder.encode(author, "UTF-8"); navController.navigate("author_quotes/$enc") },
                                onTapBook   = { book   -> val enc = URLEncoder.encode(book, "UTF-8"); navController.navigate("book_quotes/$enc") },
                            )
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }
}
        }
    }

    // ── Takipçi/Takip sheet'leri ─────────────────────────────────────────────
    if (showFollowers) {
        FollowListSheet(
            title     = "Şopîner ($followersCount)",
            entries   = followers,
            loading   = socialLoading,
            onDismiss = { showFollowers = false; socialVm.clearFollowers() },
            onProfile = { u ->
                showFollowers = false
                navController.navigate("profile/$u")
            },
        )
    }
    if (showFollowing) {
        FollowListSheet(
            title     = "Şopandî ($followingCount)",
            entries   = following,
            loading   = socialLoading,
            onDismiss = { showFollowing = false; socialVm.clearFollowing() },
            onProfile = { u ->
                showFollowing = false
                navController.navigate("profile/$u")
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
    followersCount: Int,
    followingCount: Int,
    postsCount    : Int,
    onFollow      : () -> Unit,
    onEditProfile : () -> Unit,
    onFollowers   : () -> Unit = {},
    onFollowing   : () -> Unit = {},
    onMessage     : () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Kapak fotoğrafı
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(SurfaceVar)
        ) {
            if (user?.coverPhoto?.isNotEmpty() == true) {
                AsyncImage(
                    model              = user.coverPhoto,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Avatar satırı
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                val avatarUrl = user?.photoURL?.ifEmpty { null }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .offset(y = (-28).dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Primary, PrimaryLight)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(
                                androidx.compose.ui.platform.LocalContext.current
                            ).data(avatarUrl).crossfade(true).build(),
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
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                if (isMe) {
                    OutlinedButton(
                        onClick = onEditProfile,
                        shape   = RoundedCornerShape(10.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = OnBackground),
                    ) { Text("Düzenle", fontSize = 13.sp) }
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
                        // Takip butonu
                        Button(
                            onClick = onFollow,
                            shape   = RoundedCornerShape(10.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) SurfaceVar else Amber,
                                contentColor   = if (isFollowing) OnBackground else Color.Black,
                            ),
                        ) {
                            Text(
                                if (isFollowing) "Takip Ediliyor" else "Takip Et",
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
                Text(user.website, color = Amber, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            // İstatistikler
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem(postsCount,     "Nivîs",   onClick = null)
                StatItem(followersCount, "Şopîner", onClick = onFollowers)
                StatItem(followingCount, "Şopandî", onClick = onFollowing)
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
) {
    val user        by vm.user.collectAsState()
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var bio         by remember(user) { mutableStateOf(user?.bio ?: "") }
    var website     by remember(user) { mutableStateOf(user?.website ?: "") }
    var username    by remember(user) { mutableStateOf(user?.username ?: "") }
    var usernameErr by remember { mutableStateOf<String?>(null) }

    val storage     = com.google.firebase.storage.FirebaseStorage.getInstance()
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.updateProfilePhoto(it, storage) } }
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.updateCoverPhoto(it, storage) } }

    LaunchedEffect(Unit) { vm.load("me") }

    Scaffold(
        modifier       = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profili Düzenle",
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
                    TextButton(onClick = {
                        vm.updateProfile(displayName, bio, website)
                        val usernameChanged = username.isNotBlank() &&
                            username != (user?.username ?: "")
                        if (usernameChanged) {
                            vm.updateUsername(username,
                                onSuccess = { navController.popBackStack() },
                                onError   = { usernameErr = it },
                            )
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Text("Kaydet", color = Amber, fontWeight = FontWeight.Bold)
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
                    onClick  = { photoPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                ) { Text("📷 Profil Foto", fontSize = 12.sp) }
                OutlinedButton(
                    onClick  = { coverPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                ) { Text("🖼 Kapak Foto", fontSize = 12.sp) }
            }
            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text("Adın / Nav") },
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


// ── Profil compose alanı ──────────────────────────────────────────────────────
@Composable
private fun ProfileComposeBox(
    text          : String,
    onTextChange  : (String) -> Unit,
    quote         : QuotePayload?,
    onQuoteAdd    : () -> Unit,
    onQuoteRemove : () -> Unit,
    onSend        : () -> Unit,
    photoURL      : String,
    language      : String,
) {
    Surface(
        modifier       = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape          = RoundedCornerShape(14.dp),
        color          = HeftSurface,
        border         = androidx.compose.foundation.BorderStroke(1.dp, Divider),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(
                        androidx.compose.ui.platform.LocalContext.current
                    ).data(photoURL.ifEmpty { null }).crossfade(true).build(),
                    contentDescription = null,
                    modifier           = Modifier.size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(SurfaceVar),
                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                )
                OutlinedTextField(
                    value           = text,
                    onValueChange   = onTextChange,
                    placeholder     = {
                        Text(
                            if (language == "ku") "Tu çi difikire?" else "Bir şeyler paylaş...",
                            color = Muted, fontSize = 14.sp,
                        )
                    },
                    modifier        = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 160.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Primary,
                        unfocusedBorderColor    = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor   = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor             = Primary,
                    ),
                    shape           = RoundedCornerShape(8.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    ),
                    maxLines        = 6,
                )
            }
            if (quote != null) {
                Spacer(Modifier.height(8.dp))
                QuoteInputSection(quote = quote, onRemove = onQuoteRemove)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onQuoteAdd, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.FormatQuote, null,
                        tint     = if (quote != null) Primary else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    if (language == "ku") "Alıntî" else "Alıntı ekle",
                    color    = if (quote != null) Primary else Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onQuoteAdd() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${text.length}/1000",
                    color    = if (text.length > 900) Error else Muted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick        = onSend,
                    enabled        = text.isNotBlank() || quote != null,
                    shape          = RoundedCornerShape(99.dp),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = Primary,
                        contentColor           = androidx.compose.ui.graphics.Color.White,
                        disabledContainerColor = Divider,
                        disabledContentColor   = Muted,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    modifier       = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (language == "ku") "Parve bike" else "Paylaş",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
