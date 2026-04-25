package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
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
    onOpenDrawer : () -> Unit = {},
    vm           : FeedViewModel = hiltViewModel(),
) {
    val posts   by vm.posts.collectAsState()
    val loading by vm.loading.collectAsState()
    var showNewPost by remember { mutableStateOf(false) }
    var commentPost by remember { mutableStateOf<Post?>(null) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(
                                brush      = Brush.horizontalGradient(listOf(Amber, Color(0xFFFF6B00))),
                                fontWeight = FontWeight.Black,
                                fontSize   = 24.sp,
                            )) { append("heft") }
                            withStyle(SpanStyle(
                                color      = OnBackground,
                                fontWeight = FontWeight.Black,
                                fontSize   = 24.sp,
                            )) { append("reng") }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menü", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Search, contentDescription = "Ara", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showNewPost = true },
                containerColor = Amber,
                contentColor   = Color.Black,
                shape          = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Yeni gönderi")
            }
        }
    ) { padding ->
        if (loading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.DynamicFeed, contentDescription = null, tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Henüz gönderi yok", color = Muted)
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post      = post,
                        onLike    = { vm.toggleLike(post) },
                        onSave    = { vm.toggleSave(post) },
                        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                        // Karta tıklayınca detay sayfasına git
                        onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onShare   = { vm.repost(post) },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
    }

    // Yeni gönderi sheet
    if (showNewPost) {
        var text       by remember { mutableStateOf("") }
        var showQuote  by remember { mutableStateOf(false) }
        var quoteText  by remember { mutableStateOf("") }
        var authorName by remember { mutableStateOf("") }
        var bookName   by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showNewPost = false },
            containerColor   = Surface,
            dragHandle = {
                Box(Modifier.padding(vertical = 10.dp).size(width = 36.dp, height = 4.dp).background(Divider, RoundedCornerShape(2.dp)))
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNewPost = false }) {
                        Text("İptal", color = Muted, fontSize = 13.sp)
                    }
                    Text("Yeni Gönderi", fontWeight = FontWeight.Bold, color = OnBackground)
                    Button(
                        onClick = {
                            if (text.isNotBlank() || quoteText.isNotBlank()) {
                                vm.createPost(text.trim(), quoteText.trim(), authorName.trim(), bookName.trim())
                                showNewPost = false
                            }
                        },
                        enabled = text.isNotBlank() || quoteText.isNotBlank(),
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Amber, contentColor = Color.Black,
                            disabledContainerColor = SurfaceVar, disabledContentColor = Muted,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("Paylaş", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = { Text("Ne düşünüyorsun?", color = Muted, fontSize = 14.sp) },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Color.Transparent,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape           = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showQuote = !showQuote }, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        if (showQuote) Icons.Default.RemoveCircleOutline else Icons.Default.FormatQuote,
                        null, tint = Amber, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (showQuote) "Alıntıyı kaldır" else "Alıntı ekle", color = Amber, fontSize = 13.sp)
                }
                AnimatedVisibility(visible = showQuote) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quoteText, onValueChange = { quoteText = it },
                            placeholder = { Text("Alıntı metni...", color = Muted) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                                focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                                unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = authorName, onValueChange = { authorName = it },
                                placeholder = { Text("Yazar", color = Muted) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                                    unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                                ),
                            )
                            OutlinedTextField(
                                value = bookName, onValueChange = { bookName = it },
                                placeholder = { Text("Kitap", color = Muted) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                                    unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// PostCard — Feed listesi için (inline)
@Composable
fun PostCard(
    post      : Post,
    onLike    : () -> Unit,
    onSave    : () -> Unit,
    onProfile : () -> Unit,
    onComment : () -> Unit,
    onShare   : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Header
        androidx.compose.foundation.layout.Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model              = post.photoURL.ifEmpty { null },
                contentDescription = post.displayName,
                modifier           = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceVar)
                    .let { m ->
                        // Profil tıklanabilir
                        m
                    },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        Modifier.let { it }
                    )
            ) {
                val nameToShow = post.displayName.ifBlank { post.username.ifBlank { "Bikarhênerê Heftreng" } }
                Text(nameToShow, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
                if (post.username.isNotBlank() && post.username != nameToShow)
                    Text("@${post.username}", color = Muted, fontSize = 12.sp)
            }
            // Detay butonu
            IconButton(onClick = onComment, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "Detay", tint = Muted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quote
        if (post.quoteText.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceVar),
            ) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Amber, Color(0xFFFF6B00))),
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .align(Alignment.CenterStart))
                Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    Icon(Icons.Default.FormatQuote, null, tint = Amber.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(post.quoteText, color = OnSurface, fontSize = 14.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic)
                    if (post.authorName.isNotBlank() || post.bookName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = Divider.copy(alpha = 0.4f), thickness = 0.5.dp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(11.dp))
                            Text(
                                buildString {
                                    if (post.authorName.isNotBlank()) append(post.authorName)
                                    if (post.authorName.isNotBlank() && post.bookName.isNotBlank()) append(" — ")
                                    if (post.bookName.isNotBlank()) append(post.bookName)
                                },
                                color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Text
        if (post.text.isNotBlank()) {
            Text(post.text, color = OnBackground, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(8.dp))
        }

        // Image - tam genislik
        if (post.imageURL.isNotBlank()) {
            AsyncImage(
                model              = post.imageURL,
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Like
            IconButton(onClick = onLike) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Beğen",
                    tint               = if (post.isLikedByMe) Color(0xFFEF4444) else Muted,
                    modifier           = Modifier.size(20.dp),
                )
            }
            if (post.likesCount > 0)
                Text(post.likesCount.toString(), color = Muted, fontSize = 13.sp)

            Spacer(Modifier.width(4.dp))

            // Comment → detay sayfasına git
            IconButton(onClick = onComment) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Yorumlar", tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.commentsCount > 0)
                Text(post.commentsCount.toString(), color = Muted, fontSize = 13.sp)

            Spacer(Modifier.width(4.dp))

            // Repost
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Repeat, contentDescription = "Paylaş", tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.repostsCount > 0)
                Text(post.repostsCount.toString(), color = Muted, fontSize = 13.sp)

            Spacer(Modifier.weight(1f))

            // Save
            IconButton(onClick = onSave) {
                Icon(
                    if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Kaydet",
                    tint               = if (post.isSavedByMe) Amber else Muted,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }
}
