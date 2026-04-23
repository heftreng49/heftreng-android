package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    vm: FeedViewModel = hiltViewModel(),
) {
    val posts by vm.posts.collectAsState()
    val loading by vm.loading.collectAsState()
    var showNewPost by remember { mutableStateOf(false) }
    var newPostText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "heftreng",
                        fontWeight = FontWeight.Bold,
                        color = Amber,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewPost = true },
                containerColor = Amber,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Yeni gönderi")
            }
        }
    ) { padding ->
        if (loading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onLike = { vm.toggleLike(post) },
                        onSave = { vm.toggleSave(post) },
                        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment = { /* CommentSheet */ },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
    }

    // Yeni gönderi bottom sheet
    if (showNewPost) {
        ModalBottomSheet(
            onDismissRequest = { showNewPost = false },
            containerColor = Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNewPost = false }) {
                        Text("İptal", color = Muted)
                    }
                    Text("Yeni Gönderi", fontWeight = FontWeight.SemiBold, color = OnBackground)
                    TextButton(
                        onClick = {
                            if (newPostText.isNotBlank()) {
                                vm.createPost(newPostText.trim())
                                newPostText = ""
                                showNewPost = false
                            }
                        }
                    ) {
                        Text("Paylaş", color = Amber, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPostText,
                    onValueChange = { newPostText = it },
                    placeholder = { Text("Ne düşünüyorsun?", color = Muted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Amber,
                        unfocusedBorderColor = Divider,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                        unfocusedContainerColor = Surface,
                        focusedContainerColor   = Surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onProfile: () -> Unit,
    onComment: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Kullanıcı bilgisi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProfile() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = post.photoURL.ifEmpty { null },
                contentDescription = post.displayName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceVar),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    post.displayName,
                    fontWeight = FontWeight.SemiBold,
                    color = OnBackground,
                    fontSize = 14.sp,
                )
                Text(
                    "@${post.username}",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Alıntı kutusu
        if (post.quoteText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = SurfaceVar,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "\"${post.quoteText}\"",
                        color = OnSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    if (post.bookName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "— ${post.authorName}, ${post.bookName}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Post metni
        if (post.text.isNotBlank()) {
            Text(
                post.text,
                color = OnBackground,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Görsel
        if (post.imageURL.isNotBlank()) {
            AsyncImage(
                model = post.imageURL,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Aksiyon butonları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Beğen
            IconButton(onClick = onLike) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Beğen",
                    tint = if (post.isLikedByMe) Color(0xFFEF4444) else Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (post.likesCount > 0) {
                Text(post.likesCount.toString(), color = Muted, fontSize = 13.sp)
            }

            Spacer(Modifier.width(4.dp))

            // Yorum
            IconButton(onClick = onComment) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Yorum",
                    tint = Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (post.commentsCount > 0) {
                Text(post.commentsCount.toString(), color = Muted, fontSize = 13.sp)
            }

            Spacer(Modifier.weight(1f))

            // Kaydet
            IconButton(onClick = onSave) {
                Icon(
                    if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Kaydet",
                    tint = if (post.isSavedByMe) Amber else Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
