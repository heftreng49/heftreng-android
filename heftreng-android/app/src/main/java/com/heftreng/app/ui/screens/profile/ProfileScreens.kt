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
import com.heftreng.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uid: String,
    navController: NavController,
    vm: ProfileViewModel = hiltViewModel(),
    authVm: AuthViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    val posts by vm.posts.collectAsState()
    val isFollowing by vm.isFollowing.collectAsState()
    val loading by vm.loading.collectAsState()
    val isMe = uid == "me" || uid == vm.myUid

    LaunchedEffect(uid) { vm.load(uid) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "", color = OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (!isMe) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = OnBackground)
                        }
                    }
                },
                actions = {
                    if (isMe) {
                        IconButton(onClick = { authVm.signOut() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Çıkış", tint = Muted)
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Profil başlığı
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = user?.photoURL?.ifEmpty { null },
                            contentDescription = user?.displayName,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(SurfaceVar),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(16.dp))
                        // İstatistikler
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem(user?.postsCount ?: 0, "Gönderi")
                            StatItem(user?.followersCount ?: 0, "Takipçi")
                            StatItem(user?.followingCount ?: 0, "Takip")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
                    if (user?.bio?.isNotBlank() == true) {
                        Spacer(Modifier.height(4.dp))
                        Text(user?.bio ?: "", color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(12.dp))

                    // Aksiyon butonu
                    if (isMe) {
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.EditProfile.route) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnBackground),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                        ) {
                            Text("Profili Düzenle")
                        }
                    } else {
                        Button(
                            onClick = { vm.toggleFollow(uid) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) SurfaceVar else Amber,
                                contentColor   = if (isFollowing) OnBackground else Color.Black,
                            ),
                        ) {
                            Text(
                                if (isFollowing) "Takip Ediliyor" else "Takip Et",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Divider)
                }
            }

            // Gönderiler
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onLike = {},
                    onSave = {},
                    onProfile = {},
                    onComment = {},
                )
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun StatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

// ── Profil Düzenle ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var bio by remember(user) { mutableStateOf(user?.bio ?: "") }

    LaunchedEffect(Unit) { vm.load("me") }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Profili Düzenle", color = OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = OnBackground)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        vm.updateProfile(displayName, bio)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Adın") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = heftrangTextFieldColors(),
                singleLine = true,
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = heftrangTextFieldColors(),
                maxLines = 5,
            )
        }
    }
}
