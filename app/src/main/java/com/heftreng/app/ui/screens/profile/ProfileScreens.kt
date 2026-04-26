package com.heftreng.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import com.heftreng.app.data.model.ReadingListEntry
import com.heftreng.app.data.model.Serial
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.auth.heftrangTextFieldColors
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.screens.serials.SerialCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.*

// ── Profil ekranı — Sekmeler: Gönderiler | Seriler | Okuma Listesi ─────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uid          : String,
    navController: NavController,
    vm           : ProfileViewModel    = hiltViewModel(),
    authVm       : AuthViewModel       = hiltViewModel(),
    feedVm       : FeedViewModel       = hiltViewModel(),
    serialsVm    : SerialsViewModel    = hiltViewModel(),
    rlVm         : ReadingListViewModel = hiltViewModel(),
) {
    val user           by vm.user.collectAsState()
    val posts          by vm.posts.collectAsState()
    val isFollowing    by vm.isFollowing.collectAsState()
    val followersCount by vm.followersCount.collectAsState()
    val followingCount by vm.followingCount.collectAsState()
    val loading        by vm.loading.collectAsState()
    val mySerials      by serialsVm.mySerials.collectAsState()
    val rlEntries      by rlVm.entries.collectAsState()

    val isMe = uid == "me" || uid == vm.myUid
    val targetUid = if (uid == "me") vm.myUid else uid

    // Sekme seçimi — XML temasıyla aynı: Gönderiler | Seriler | Kitaplar | Okuma Listesi
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Gönderiler", "Seriler", "Okuma Listesi")

    LaunchedEffect(uid) {
        vm.load(uid)
        serialsVm.loadMySerials(targetUid)
        rlVm.load(targetUid)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        user?.username?.let { "@$it" } ?: user?.displayName ?: "",
                        color      = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (!isMe) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = OnBackground)
                        }
                    }
                },
                actions = {
                    if (isMe) {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, "Ayarlar", tint = Muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        if (loading && user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // ── Profil başlığı ────────────────────────────────────────────
            item {
                ProfileHeader(
                    user           = user,
                    isMe           = isMe,
                    isFollowing    = isFollowing,
                    followersCount = followersCount,
                    followingCount = followingCount,
                    postsCount     = posts.size,
                    onFollow       = { vm.toggleFollow(targetUid) },
                    onEditProfile  = { navController.navigate(Screen.EditProfile.route) },
                )
            }

            // ── Sekme bar ─────────────────────────────────────────────────
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Background,
                    contentColor     = Amber,
                    indicator        = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = Amber,
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected             = selectedTab == i,
                            onClick              = { selectedTab = i },
                            text                 = { Text(title, fontSize = 12.sp) },
                            selectedContentColor   = Amber,
                            unselectedContentColor = Muted,
                        )
                    }
                }
            }

            // ── Sekme içerikleri ──────────────────────────────────────────
            when (selectedTab) {

                // Gönderiler
                0 -> {
                    if (posts.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Article, null, tint = Muted, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("Henüz gönderi yok", color = Muted, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post      = post,
                                onLike    = { feedVm.toggleLike(post) },
                                onSave    = { feedVm.toggleSave(post) },
                                onProfile = {},
                                onComment = {},
                                onShare   = { feedVm.repost(post) },
                                onDelete  = if (isMe) ({ feedVm.deletePost(post.id); vm.load(uid) }) else null,
                                onEdit    = if (isMe) ({ newText -> feedVm.editPost(post.id, newText) }) else null,
                                onTap     = { navController.navigate(Screen.PostDetail.go(post.id)) },
                            )
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }

                // Seriler — XML'deki serials sekmesiyle aynı yapı
                1 -> {
                    if (mySerials.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.AutoStories, null, tint = Muted, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("Henüz seri yok", color = Muted, fontSize = 14.sp)
                                    if (isMe) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = { navController.navigate("serials") }) {
                                            Text("+ Yeni Seri", color = Amber)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(mySerials) { serial ->
                            SerialCard(
                                serial  = serial,
                                onClick = { navController.navigate("serial/${serial.id}") },
                                onLike  = { serialsVm.toggleLikeSerial(serial) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Okuma Listesi — 4 durum: okuyorum | okumak_istiyorum | okudum | biraktim
                2 -> {
                    item {
                        ReadingListProfileSection(
                            entries       = rlEntries,
                            onSerialClick = { sid -> navController.navigate("serial/$sid") },
                        )
                    }
                }
            }
        }
    }
}

// ── Profil başlık bileşeni ────────────────────────────────────────────────────
@Composable
private fun ProfileHeader(
    user          : com.heftreng.app.data.model.User?,
    isMe          : Boolean,
    isFollowing   : Boolean,
    followersCount: Int,
    followingCount: Int,
    postsCount    : Int,
    onFollow      : () -> Unit,
    onEditProfile : () -> Unit,
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
                    model        = user.coverPhoto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Avatar
            Box(Modifier.offset(y = (-36).dp)) {
                AsyncImage(
                    model             = user?.photoURL,
                    contentDescription = "Profil fotoğrafı",
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(SurfaceVar)
                        .background(Background.copy(alpha = 0.4f)),
                )
            }

            Row(
                modifier          = Modifier.offset(y = (-28).dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(86.dp))   // avatar boşluğu
                Spacer(Modifier.weight(1f))
                if (isMe) {
                    OutlinedButton(
                        onClick  = onEditProfile,
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnBackground),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                    ) { Text("Düzenle", fontSize = 13.sp) }
                } else {
                    Button(
                        onClick  = onFollow,
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) SurfaceVar else Amber,
                            contentColor   = if (isFollowing) OnBackground else Color.Black,
                        ),
                    ) { Text(if (isFollowing) "Takip Ediliyor" else "Takip Et", fontSize = 13.sp) }
                }
            }

            // İsim
            Text(
                user?.displayName ?: "",
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
                fontSize   = 18.sp,
                modifier   = Modifier.offset(y = (-20).dp),
            )
            user?.username?.let { un ->
                Text(
                    "@$un",
                    color    = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.offset(y = (-18).dp),
                )
            }

            if (user?.bio?.isNotBlank() == true) {
                Spacer(Modifier.height(4.dp))
                Text(user.bio, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (user?.website?.isNotBlank() == true) {
                Spacer(Modifier.height(2.dp))
                Text(user.website, color = Amber, fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatItem(postsCount,     "Nivîs")
                StatItem(followersCount, "Şopîner")
                StatItem(followingCount, "Tê şopandin")
                if ((user?.xp ?: 0) > 0) StatItem(user?.xp ?: 0, "XP")
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
        }
    }
}

// ── Profil sayfasında okuma listesi bölümü ─────────────────────────────────────
@Composable
private fun ReadingListProfileSection(
    entries      : Map<String, List<ReadingListEntry>>,
    onSerialClick: (String) -> Unit,
) {
    val statuses = listOf(
        "okuyorum"          to "Okuyorum",
        "okumak_istiyorum"  to "Okumak İstiyorum",
        "okudum"            to "Okudum",
        "biraktim"          to "Bıraktım",
    )
    val statusColors = mapOf(
        "okuyorum"          to Color(0xFF2563EB),
        "okumak_istiyorum"  to Color(0xFF7C3AED),
        "okudum"            to Color(0xFF059669),
        "biraktim"          to Color(0xFFDC2626),
    )

    Column(modifier = Modifier.padding(12.dp)) {
        statuses.forEach { (key, label) ->
            val list = entries[key] ?: emptyList()
            if (list.isNotEmpty()) {
                val color = statusColors[key] ?: Amber
                Row(
                    modifier          = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                list.take(6).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSerialClick(entry.sid) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.coverImg.isNotEmpty()) {
                            AsyncImage(
                                model        = entry.coverImg,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier     = Modifier
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
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        if (entries.values.all { it.isEmpty() }) {
            Box(
                Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryBooks, null, tint = Muted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Okuma listesi boş", color = Muted, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Yardımcı bileşenler ───────────────────────────────────────────────────────
@Composable
fun StatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    vm           : ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var bio         by remember(user) { mutableStateOf(user?.bio ?: "") }
    var website     by remember(user) { mutableStateOf(user?.website ?: "") }

    LaunchedEffect(Unit) { vm.load("me") }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Profili Düzenle", color = OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = OnBackground)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        vm.updateProfile(displayName, bio, website)
                        navController.popBackStack()
                    }) {
                        Text("Kaydet", color = Amber, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = displayName, onValueChange = { displayName = it },
                label = { Text("Adın / Nav") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangTextFieldColors(), singleLine = true,
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp), colors = heftrangTextFieldColors(), maxLines = 5,
            )
            OutlinedTextField(
                value = website, onValueChange = { website = it },
                label = { Text("Website") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangTextFieldColors(), singleLine = true,
            )
        }
    }
}
