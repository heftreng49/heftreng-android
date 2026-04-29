package com.heftreng.app.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePostScreen(
    postId       : String,
    navController: NavController,
    vm           : FeedViewModel = hiltViewModel(),
) {
    val posts    by vm.posts.collectAsState()
    val comments by vm.comments.collectAsState()
    val post      = posts.find { it.id == postId }
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(postId) { vm.loadComments(postId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Gönderi", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                item {
                    PostCard(
                        post      = post,
                        onLike    = { vm.toggleLike(post) },
                        onSave    = { vm.toggleSave(post) },
                        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment = {},
                        onShare   = { vm.repost(post) },
                        onDelete  = { vm.deletePost(post.id); navController.popBackStack() },
                        onEdit    = { newText -> vm.editPost(post.id, newText) },
                        onTap     = null,
                    )
                    HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                }

                if (post.likesCount > 0) {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text("${post.likesCount}", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                            Text(" Beğeni", color = Muted, fontSize = 15.sp)
                        }
                        HorizontalDivider(color = Divider)
                    }
                }

                item {
                    Text(
                        "Yorumlar (${post.commentsCount})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    )
                }

                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Henüz yorum yok — ilk sen yaz", color = Muted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { cmt ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AsyncImage(
                                model = cmt.photoURL.ifEmpty { null }, contentDescription = null,
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(SurfaceVar),
                                contentScale = ContentScale.Crop,
                            )
                            Column {
                                Text(cmt.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp)
                                Spacer(Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                                    color = SurfaceVar,
                                ) {
                                    Text(cmt.text,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }

            // Yorum giriş alanı — altta sabit
            HorizontalDivider(color = Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = commentText, onValueChange = { commentText = it },
                    placeholder = { Text("Yorum yaz...", color = Muted, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar, cursorColor = Amber,
                    ),
                )
                val canSend = commentText.isNotBlank()
                IconButton(
                    onClick = {
                        if (canSend) { vm.addComment(post, commentText.trim()); commentText = "" }
                    },
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(if (canSend) Amber else SurfaceVar),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Gönder",
                        tint = if (canSend) Color.Black else Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
