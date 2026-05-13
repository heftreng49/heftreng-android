package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    viewModel    : FeedViewModel,
    postId       : String,
    socialVm     : SocialViewModel = hiltViewModel(),
) {
    val posts          by viewModel.posts.collectAsState()
    val likers         by socialVm.likers.collectAsState()
    val socialLoading  by socialVm.loading.collectAsState()
    val post           = posts.find { it.id == postId }

    var showLikers   by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Gönderi", color = OnBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                PostCard(
                    post         = post,
                    onLike       = { viewModel.toggleLike(post) },
                    onSave       = { viewModel.toggleSave(post) },
                    onProfile    = { navController.navigate(Screen.Profile.go(post.uid)) },
                    onComment    = { showComments = true },
                    onShare      = { viewModel.repost(post) },
                    onShowLikers = { socialVm.loadPostLikers(post.id); showLikers = true },
                )
                HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
            }

            if (post.likesCount > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { socialVm.loadPostLikers(post.id); showLikers = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${post.likesCount} beğeni", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Divider)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showComments = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Yorumlar", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (post.commentsCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(SurfaceVar, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("${post.commentsCount}", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider(color = Divider)
            }
        }
    }

    if (showComments) {
        CommentsSheet(
            postId        = postId,
            postAuthorUid = post?.uid ?: "",
            onDismiss     = { showComments = false },
        )
    }

    if (showLikers) {
        LikerListSheet(
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }
}
