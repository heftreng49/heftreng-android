package com.heftreng.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.component.PostCard
import com.heftreng.app.ui.screens.auth.heftrangFieldColors
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uid          : String,
    navController: NavController,
    vm           : ProfileViewModel = hiltViewModel(),
    authVm       : AuthViewModel    = hiltViewModel(),
    feedVm       : FeedViewModel    = hiltViewModel(),
) {
    val user           by vm.user.collectAsState()
    val posts          by vm.posts.collectAsState()
    val isFollowing    by vm.isFollowing.collectAsState()
    val followersCount by vm.followersCount.collectAsState()
    val followingCount by vm.followingCount.collectAsState()
    val loading        by vm.loading.collectAsState()
    val isMe = uid == "me" || uid == vm.myUid

    LaunchedEffect(uid) { vm.load(uid) }

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        user?.username?.ifBlank { user?.displayName ?: "" } ?: "",
                        color = onBg(), fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (!isMe) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = onBg())
                        }
                    }
                },
                actions = {
                    if (isMe) {
                        IconButton(onClick = { navController.navigate(Screen.EditProfile.route) }) {
                            Icon(Icons.Default.Edit, "Düzenle", tint = muted())
                        }
                        IconButton(onClick = { authVm.signOut() }) {
                            Icon(Icons.Default.Logout, "Çıkış", tint = muted())
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        }
    ) { padding ->
        if (loading && user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent())
            }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // ── Profil başlığı ────────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // Cover fotoğrafı
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(surfVar()),
                    ) {
                        if (user?.coverPhoto?.isNotBlank() == true) {
                            AsyncImage(
                                model = user?.coverPhoto, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            )
                        } else {
                            // Degrade arka plan
                            Box(modifier = Modifier.fillMaxSize().background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(accent().copy(alpha = 0.35f), accent().copy(alpha = 0.1f))
                                )
                            ))
                        }
                    }

                    // Avatar — cover üzerine taşar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .offset(y = (-36).dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(surfVar())
                                .align(Alignment.CenterStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (user?.photoURL?.isNotBlank() == true) {
                                AsyncImage(
                                    model = user?.photoURL, contentDescription = user?.displayName,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    user?.displayName?.firstOrNull()?.uppercase() ?: "H",
                                    color = accent(), fontWeight = FontWeight.Bold, fontSize = 26.sp,
                                )
                            }
                        }
                    }

                    // Bilgi bloğu
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-28).dp)
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = onBg(), fontSize = 18.sp)
                                if (user?.username?.isNotBlank() == true)
                                    Text("@${user?.username}", color = muted(), fontSize = 13.sp)
                            }
                            if (!isMe) {
                                Button(
                                    onClick = { vm.toggleFollow(uid) },
                                    shape   = RoundedCornerShape(10.dp),
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowing) surfVar() else accent(),
                                        contentColor   = if (isFollowing) onBg()    else Color.Black,
                                    ),
                                ) {
                                    Text(
                                        if (isFollowing) "Tê şopandin" else "Bişopîne",
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    )
                                }
                            }
                        }

                        if (user?.bio?.isNotBlank() == true) {
                            Spacer(Modifier.height(8.dp))
                            Text(user?.bio ?: "", color = onSurf(), fontSize = AppFontSize.sp, lineHeight = (AppFontSize + 6).sp)
                        }
                        if (user?.website?.isNotBlank() == true) {
                            Spacer(Modifier.height(4.dp))
                            Text(user?.website ?: "", color = accent(), fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(12.dp))

                        // İstatistikler
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            StatItem(posts.size,     "Nivîs")
                            StatItem(followersCount, "Şopîner")
                            StatItem(followingCount, "Tê şopandin")
                            if ((user?.xp ?: 0) > 0) StatItem(user?.xp ?: 0, "XP")
                        }

                        // XP seviye çubuğu
                        val xp    = user?.xp ?: 0
                        val level = user?.level ?: 1
                        if (xp > 0) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accent().copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "Sev. $level",
                                        color    = accent(),
                                        fontWeight= FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress   = { (xp % 100) / 100f },
                                    modifier   = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    color      = accent(),
                                    trackColor = surfVar(),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$xp XP", color = muted(), fontSize = 11.sp)
                            }
                        }

                        if (isMe) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick  = { navController.navigate(Screen.EditProfile.route) },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = onBg()),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, divider()),
                            ) {
                                Text("Profili Düzenle / Profîlê Biguherîne")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = divider())
                    }
                }
            }

            // ── Paylaşılan içerikler ──────────────────────
            if (posts.isEmpty() && !loading) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Article, null, tint = muted(), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Henüz gönderi yok / Nivîs tune", color = muted(), fontSize = 14.sp)
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
                        onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onShare   = { feedVm.repost(post) },
                    )
                    HorizontalDivider(color = divider(), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun StatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontWeight = FontWeight.Bold, color = onBg(), fontSize = 16.sp)
        Text(label, color = muted(), fontSize = 11.sp)
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
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title = { Text("Profili Düzenle", color = onBg(), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = onBg())
                    }
                },
                actions = {
                    TextButton(onClick = {
                        vm.updateProfile(displayName, bio, website)
                        navController.popBackStack()
                    }) {
                        Text("Kaydet / Tomar bike", color = accent(), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
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
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = heftrangFieldColors(),
                singleLine = true,
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = heftrangFieldColors(),
                maxLines = 5,
            )
            OutlinedTextField(
                value = website, onValueChange = { website = it },
                label = { Text("Website / Malper") },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = heftrangFieldColors(),
                singleLine = true,
            )
        }
    }
}
