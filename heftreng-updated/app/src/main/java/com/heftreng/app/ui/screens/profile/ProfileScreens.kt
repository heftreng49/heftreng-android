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
import com.heftreng.app.ui.screens.auth.heftrangTextFieldColors
import com.heftreng.app.ui.screens.feed.PostCard
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
                        IconButton(onClick = { navController.navigate(Screen.EditProfile.route) }) {
                            Icon(Icons.Default.Edit, "Düzenle", tint = Muted)
                        }
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, "Ayarlar", tint = Muted)
                        }
                        IconButton(onClick = { authVm.signOut() }) {
                            Icon(Icons.Default.Logout, "Çıkış", tint = Muted)
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Kapak fotoğrafı
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(SurfaceVar),
                    ) {
                        if (user?.coverPhoto?.isNotBlank() == true) {
                            AsyncImage(
                                model = user?.coverPhoto, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    // Avatar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .offset(y = (-36).dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Background)
                                .align(Alignment.CenterStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = user?.photoURL?.ifEmpty { null }, contentDescription = user?.displayName,
                                modifier = Modifier.size(76.dp).clip(CircleShape).background(SurfaceVar),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    // Bilgi bloğu
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-24).dp)
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
                                if (user?.username?.isNotBlank() == true)
                                    Text("@${user?.username}", color = Muted, fontSize = 13.sp)
                            }
                            if (!isMe) {
                                Button(
                                    onClick = { vm.toggleFollow(uid) },
                                    shape   = RoundedCornerShape(10.dp),
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowing) SurfaceVar else Amber,
                                        contentColor   = if (isFollowing) OnBackground else Color.Black,
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
                            Text(user?.bio ?: "", color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        if (user?.website?.isNotBlank() == true) {
                            Spacer(Modifier.height(4.dp))
                            Text(user?.website ?: "", color = Amber, fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            StatItem(posts.size,     "Nivîs")
                            StatItem(followersCount, "Şopîner")
                            StatItem(followingCount, "Tê şopandin")
                            if ((user?.xp ?: 0) > 0) StatItem(user?.xp ?: 0, "XP")
                        }

                        if (isMe) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick  = { navController.navigate(Screen.EditProfile.route) },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnBackground),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                            ) { Text("Profili Düzenle / Profîlê Biguherîne") }
                        }

                        HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Divider)
                    }
                }
            }

            // ── Gönderiler ────────────────────────────────────────────────
            if (posts.isEmpty() && !loading) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Article, null, tint = Muted, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Henüz gönderi yok / Nivîs tune", color = Muted, fontSize = 14.sp)
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
