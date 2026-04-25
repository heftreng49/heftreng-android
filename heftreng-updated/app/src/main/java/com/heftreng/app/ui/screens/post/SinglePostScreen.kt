package com.heftreng.app.ui.screens.post

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.feed.CommentSheet
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePostScreen(
    postId        : String,
    navController : NavController,
    vm            : FeedViewModel = hiltViewModel(),
) {
    val posts by vm.posts.collectAsState()
    val post  = posts.find { it.id == postId }
    var showComments by remember { mutableStateOf(false) }

    LaunchedEffect(postId) { vm.loadComments(postId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Gönderi", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        val comments by vm.comments.collectAsState()

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                PostCard(
                    post      = post,
                    onLike    = { vm.toggleLike(post) },
                    onSave    = { vm.toggleSave(post) },
                    onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                    onComment = { showComments = true },
                    onShare   = { vm.repost(post) },
                    onDelete  = { vm.deletePost(post.id); navController.popBackStack() },
                    onEdit    = { newText -> vm.editPost(post.id, newText) },
                    onTap     = null,
                )
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Text(
                    "Yorumlar",
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            items(comments, key = { it.id }) { cmt ->
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(cmt.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }

        if (showComments) {
            CommentSheet(post = post, onDismiss = { showComments = false }, vm = vm)
        }
    }
}
