package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Local yorum modeli ────────────────────────────────────────────────────────
private data class DetailComment(
    val id       : String     = "",
    val uid      : String     = "",
    val name     : String     = "",
    val photoURL : String     = "",
    val text     : String     = "",
    val ts       : Timestamp? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    viewModel    : FeedViewModel,
    postId       : String,
    socialVm     : SocialViewModel = hiltViewModel(),
) {
    val posts         by viewModel.posts.collectAsState()
    val likers        by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    val post          = posts.find { it.id == postId }

    val db    = FirebaseFirestore.getInstance()
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    val myUid = remember { auth.currentUser?.uid ?: "" }

    var comments     by remember { mutableStateOf<List<DetailComment>>(emptyList()) }
    var cmtLoading   by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DetailComment?>(null) }
    var errorMsg     by remember { mutableStateOf("") }
    var showLikers   by remember { mutableStateOf(false) }

    // ── Realtime yorum listener ───────────────────────────────────────────────
    DisposableEffect(postId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("feed").document(postId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    comments = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        DetailComment(
                            id       = doc.id,
                            uid      = d["uid"]         as? String ?: "",
                            name     = (d["name"]       as? String)?.ifBlank { null }
                                       ?: d["displayName"] as? String ?: "?",
                            photoURL = d["photoURL"]    as? String ?: "",
                            text     = d["text"]        as? String ?: "",
                            ts       = d["ts"]          as? Timestamp,
                        )
                    }
                }
                cmtLoading = false
            }
        onDispose { reg?.remove() }
    }

    // ── Yorum gönder ─────────────────────────────────────────────────────────
    fun sendComment() {
        val text = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return
        inputText = ""
        scope.launch {
            try {
                val userDoc = db.collection("users").document(myUid).get().await()
                val name    = userDoc.getString("displayName") ?: userDoc.getString("name") ?: "?"
                val photo   = userDoc.getString("photoURL") ?: ""
                db.collection("feed").document(postId).collection("comments").add(
                    mapOf(
                        "uid"         to myUid,
                        "name"        to name,
                        "displayName" to name,
                        "photoURL"    to photo,
                        "text"        to text,
                        "likes"       to 0,
                        "ts"          to Timestamp.now(),
                    )
                ).await()
                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Hata"
            }
        }
    }

    // ── Yorum sil ────────────────────────────────────────────────────────────
    fun deleteComment(cmt: DetailComment) {
        scope.launch {
            try {
                db.collection("feed").document(postId)
                    .collection("comments").document(cmt.id).delete().await()
                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
            } catch (e: Exception) {
                errorMsg = "Silinemedi: ${e.message}"
            }
        }
    }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                // Gönderi kartı
                item {
                    PostCard(
                        post         = post,
                        onLike       = { viewModel.toggleLike(post) },
                        onSave       = { viewModel.toggleSave(post) },
                        onProfile    = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment    = {},
                        onShare      = { viewModel.repost(post) },
                        onShowLikers = { socialVm.loadPostLikers(post.id); showLikers = true },
                    )
                    HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                }

                // Beğeni sayısı
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

                // Yorumlar başlığı
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Yorumlar", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (comments.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(SurfaceVar, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text("${comments.size}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = Divider)
                }

                // Yükleniyor
                if (cmtLoading) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Boş durum
                if (!cmtLoading && comments.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Henüz yorum yok", color = Muted, fontSize = 14.sp)
                        }
                    }
                }

                // Yorum listesi
                items(comments, key = { it.id }) { cmt ->
                    val canDelete = myUid.isNotBlank() &&
                        (cmt.uid == myUid || post.uid == myUid)
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Avatar
                        Box(
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceVar),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cmt.photoURL.isNotBlank()) {
                                AsyncImage(
                                    model              = cmt.photoURL,
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize(),
                                    contentScale       = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    cmt.name.firstOrNull()?.uppercase() ?: "?",
                                    color      = OnBackground,
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        if (canDelete) {
                            IconButton(
                                onClick  = { deleteTarget = cmt },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint               = Color(0xFFEF4444).copy(alpha = 0.7f),
                                    modifier           = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color     = Divider.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 56.dp),
                    )
                }
            }

            // ── Yorum yazma alanı ─────────────────────────────────────────────
            HorizontalDivider(color = Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    placeholder   = { Text("Yorum yaz...", color = Muted, fontSize = 14.sp) },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        cursorColor             = Amber,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        focusedContainerColor   = SurfaceVar,
                        unfocusedContainerColor = SurfaceVar,
                    ),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (inputText.isNotBlank()) Amber else Muted.copy(0.2f),
                            CircleShape,
                        )
                        .clickable(enabled = inputText.isNotBlank()) { sendComment() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint               = if (inputText.isNotBlank()) Color.Black else Muted,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    // ── Silme onay dialogu ────────────────────────────────────────────────────
    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text("Yorumu Sil", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { deleteComment(cmt); deleteTarget = null }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("İptal", color = Muted)
                }
            },
        )
    }

    // ── Hata dialogu ─────────────────────────────────────────────────────────
    if (errorMsg.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorMsg = "" },
            containerColor   = HeftSurface,
            title  = { Text("Hata", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
            text   = { Text(errorMsg, color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { errorMsg = "" }) { Text("Tamam", color = Amber) }
            },
        )
    }

    // ── Beğenenler sheet ──────────────────────────────────────────────────────
    if (showLikers) {
        LikerListSheet(
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }
}
