package com.heftreng.app.ui.screens.feed

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
import androidx.navigation.NavController
import com.heftreng.app.ui.component.PostCard
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    viewModel: FeedViewModel,
    postId: String
) {
    val posts by viewModel.posts.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val post = posts.find { it.id == postId }

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mijar", color = OnBackground, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    PostCard(
                        post      = post,
                        onLike    = { viewModel.toggleLike(post) },
                        onSave    = { viewModel.toggleSave(post) },
                        onProfile = {},
                        onComment = {},
                        onShare   = { viewModel.repost(post) }
                    )
                    HorizontalDivider(color = SurfaceVar, thickness = 8.dp)
                }

                item {
                    Text(
                        text = "${post.likesCount} Beğeni",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground
                    )
                }

                item {
                    Text(
                        text = "Yorumlar (${post.commentsCount})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted
                    )
                }

                items(comments) { comment ->
                    Column(Modifier.padding(16.dp)) {
                        Text(comment.displayName, fontWeight = FontWeight.Bold, color = OnBackground)
                        Text(comment.text, color = OnBackground, fontSize = 14.sp)
                        HorizontalDivider(color = Divider, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}
