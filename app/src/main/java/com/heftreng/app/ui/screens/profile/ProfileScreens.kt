package com.heftreng.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.heftreng.app.data.model.Post
import com.heftreng.app.data.model.ReadingListEntry
import com.heftreng.app.data.model.Serial
import com.heftreng.app.data.model.User
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.auth.heftrangTextFieldColors
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.screens.serials.SerialCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uid          : String,
    navController: NavController,
    language     : String = "tr",
    vm           : ProfileViewModel     = hiltViewModel(),
    feedVm       : FeedViewModel        = hiltViewModel(),
    serialsVm    : SerialsViewModel     = hiltViewModel(),
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

    val isMe      = uid == "me" || uid == vm.myUid
    val targetUid = if (uid == "me") vm.myUid else uid

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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        // ── Dış yapı: Column — stickyHeader yerine Column + TabRow + LazyColumn ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profil başlığı + Tab bar — kaydırılmaz kısım
            ProfileHeader(
                user           = user,
                isMe           = isMe,
                isFollowing    = isFollowing,
                followersCount = followersCount,
                followingCount = followingCount,
                postsCount     = posts.size,
                onFollow       = { vm.toggleFollow(targetUid) },
                onEditProfile  = { navController.navigate(Screen.EditProfile.route) },
                onMessage      = {
                    // Mesaj başlat — NavHost'ta messages rotasına git
                    navController.navigate(Screen.Messages.route)
                },
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Amber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = Amber,
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

            // ── Tab içerikleri — her biri kendi LazyColumn'ına sahip ──
            when (selectedTab) {

                // ─── Gönderiler ───────────────────────────────────────
                0 -> {
                    if (posts.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Article, null, tint = Muted, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Henüz gönderi yok", color = Muted)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            items(posts, key = { it.id }) { post ->
                                PostCard(
                                    post      = post,
                                    onLike    = { feedVm.toggleLike(post) },
                                    onSave    = { feedVm.toggleSave(post) },
                                    onProfile = {},
                                    onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                    onShare   = { feedVm.repost(post) },
                                    onDelete  = if (isMe) ({ feedVm.deletePost(post.id) }) else null,
                                    onEdit    = if (isMe) ({ newText -> feedVm.editPost(post.id, newText) }) else null,
                                    onTap     = { navController.navigate(Screen.PostDetail.go(post.id)) },
                                )
                                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // ─── Seriler ──────────────────────────────────────────
                1 -> {
                    if (mySerials.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.AutoStories, null, tint = Muted, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Henüz seri yok", color = Muted)
                                if (isMe) {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { navController.navigate("serials") }) {
                                        Text("+ Yeni Seri", color = Amber)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(mySerials) { serial ->
                                SerialCard(
                                    serial  = serial,
                                    onClick = { navController.navigate("serial/${serial.id}") },
                                    onLike  = { serialsVm.toggleLikeSerial(serial) },
                                )
                            }
                        }
                    }
                }

                // ─── Okuma Listesi ────────────────────────────────────
                2 -> {
                    val allEmpty = rlEntries.values.all { it.isEmpty() }
                    if (allEmpty) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LibraryBooks, null, tint = Muted, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Okuma listesi boş", color = Muted)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
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
                                    item(key = "header_$key") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Box(
                                                Modifier.size(8.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(color)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("$label (${list.size})", color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                    items(list, key = { "rl_${it.sid}" }) { entry ->
                                        RlEntryRow(entry, onClick = { navController.navigate("serial/${entry.sid}") })
                                    }
                                    item(key = "div_$key") {
                                        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
    onMessage     : () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Kapak fotoğrafı
        Box(
            Modifier.fillMaxWidth().height(100.dp).background(SurfaceVar)
        ) {
            if (user?.coverPhoto?.isNotEmpty() == true) {
                AsyncImage(
                    model = user.coverPhoto, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Avatar satırı
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                AsyncImage(
                    model             = user?.photoURL?.ifEmpty {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                    } ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl,
                    contentDescription = null,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .size(76.dp)
                        .offset(y = (-28).dp)
                        .clip(CircleShape)
                        .background(SurfaceVar),
                )
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
                        ) { Text(if (isFollowing) "Takip Ediliyor" else "Takip Et", fontSize = 13.sp) }
                    }
                }
            }

            // İsim & kullanıcı adı
            Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
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
                StatItem(postsCount,     "Nivîs")
                StatItem(followersCount, "Şopîner")
                StatItem(followingCount, "Şopandî")
                if ((user?.xp ?: 0) > 0) StatItem(user?.xp ?: 0, "XP")
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
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.coverImg.isNotEmpty()) {
            AsyncImage(
                model        = entry.coverImg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.size(36.dp, 50.dp).clip(RoundedCornerShape(5.dp)).background(SurfaceVar),
            )
        } else {
            Box(
                Modifier.size(36.dp, 50.dp).clip(RoundedCornerShape(5.dp)).background(SurfaceVar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(entry.title, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
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
                label = { Text("Adın / Nav") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangTextFieldColors(),
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("Bio") }, maxLines = 5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp), colors = heftrangTextFieldColors(),
            )
            OutlinedTextField(
                value = website, onValueChange = { website = it },
                label = { Text("Website") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = heftrangTextFieldColors(),
            )
        }
    }
}
