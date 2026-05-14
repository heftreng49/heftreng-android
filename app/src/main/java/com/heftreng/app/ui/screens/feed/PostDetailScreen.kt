package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

private data class DetailComment(
    val id           : String     = "",
    val uid          : String     = "",
    val name         : String     = "",
    val photoURL     : String     = "",
    val text         : String     = "",
    val replyTo      : String     = "",
    val replyToCmtId : String     = "",
    val likes        : Int        = 0,
    val ts           : Timestamp? = null,
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

    // Auth — her zaman taze al, remember olmadan
    val myUid   = auth.currentUser?.uid ?: ""
    val myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester     = remember { FocusRequester() }
    var openKeyboard       by remember { mutableStateOf(false) }

    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            focusRequester.requestFocus()
            keyboardController?.show()
            openKeyboard = false
        }
    }

    var comments     by remember { mutableStateOf<List<DetailComment>>(emptyList()) }
    var cmtLoading   by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var replyTo      by remember { mutableStateOf<DetailComment?>(null) }
    var deleteTarget by remember { mutableStateOf<DetailComment?>(null) }
    var errorMsg     by remember { mutableStateOf("") }
    var showLikers   by remember { mutableStateOf(false) }
    val listState    = rememberLazyListState()

    // Firestore'dan kullanıcı adını çek (yorum göndermek için)
    var myFirestoreName  by remember { mutableStateOf("") }
    var myFirestorePhoto by remember { mutableStateOf("") }
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val doc = db.collection("users").document(myUid).get().await()
            myFirestoreName  = doc.getString("displayName") ?: doc.getString("name") ?: ""
            myFirestorePhoto = doc.getString("photoURL") ?: myPhoto
        } catch (_: Exception) {}
    }

    // Realtime yorum listener
    DisposableEffect(postId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("feed").document(postId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { cmtLoading = false; return@addSnapshotListener }
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    DetailComment(
                        id           = doc.id,
                        uid          = d["uid"]          as? String ?: "",
                        name         = (d["displayName"] as? String)?.ifBlank { null }
                                       ?: d["name"]      as? String ?: "?",
                        photoURL     = d["photoURL"]     as? String ?: "",
                        text         = d["text"]         as? String ?: "",
                        replyTo      = d["replyTo"]      as? String ?: "",
                        replyToCmtId = d["replyToCmtId"] as? String ?: "",
                        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
                        ts           = d["ts"]           as? Timestamp,
                    )
                }
                cmtLoading = false
            }
        onDispose { reg?.remove() }
    }

    fun sendComment() {
        val text = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return
        inputText = ""
        val replyRef = replyTo
        replyTo = null
        scope.launch {
            try {
                val name  = myFirestoreName.ifBlank { auth.currentUser?.displayName ?: "?" }
                val photo = myFirestorePhoto.ifBlank { myPhoto }
                db.collection("feed").document(postId).collection("comments").add(
                    mapOf(
                        "uid"          to myUid,
                        "displayName"  to name,
                        "name"         to name,
                        "photoURL"     to photo,
                        "text"         to text,
                        "replyTo"      to (replyRef?.name ?: ""),
                        "replyToCmtId" to (replyRef?.id   ?: ""),
                        "likes"        to 0,
                        "ts"           to Timestamp.now(),
                    )
                ).await()
                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Hata"
            }
        }
    }

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
        containerColor    = Background,
        // imePadding Scaffold seviyesinde — klavye açılınca içerik yukarı kayar
        // gönder butonu her zaman görünür kalır
        contentWindowInsets = WindowInsets(0),
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

        val postAuthorUid = post.uid

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Yorumlar listesi
            LazyColumn(
                state          = listState,
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    PostCard(
                        post         = post,
                        onLike       = { viewModel.toggleLike(post) },
                        onSave       = { viewModel.toggleSave(post) },
                        onProfile    = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment    = { openKeyboard = true },
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Yorumlar", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (comments.isNotEmpty()) {
                            Text("${comments.size}", color = Muted, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(color = Divider)
                }

                if (cmtLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                if (!cmtLoading && comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💬", fontSize = 32.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Henüz yorum yok", color = Muted, fontSize = 14.sp)
                            }
                        }
                    }
                }

                items(comments, key = { it.id }) { cmt ->
                    // canDelete: kendi yorumu VEYA gönderi sahibi
                    val canDelete = myUid.isNotBlank() &&
                        (cmt.uid == myUid || postAuthorUid == myUid)
                    DetailCommentRow(
                        cmt       = cmt,
                        canDelete = canDelete,
                        onDelete  = { deleteTarget = cmt },
                        onReply   = { replyTo = cmt; openKeyboard = true },
                    )
                    HorizontalDivider(
                        color     = Divider.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 56.dp),
                    )
                }
            }

            // Yanıtlama göstergesi
            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVar)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "@${replyTo!!.name} yanıtlanıyor",
                        color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = { replyTo = null }, contentPadding = PaddingValues(0.dp)) {
                        Text("İptal", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            // Yorum yazma alanı — klavye açık kalır, gönder butonu her zaman görünür
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
                    placeholder   = {
                        Text(
                            if (replyTo != null) "@${replyTo!!.name} yanıtla..." else "Yorum yaz...",
                            color = Muted, fontSize = 14.sp,
                        )
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    shape    = RoundedCornerShape(24.dp),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        cursorColor             = Amber,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        focusedContainerColor   = SurfaceVar,
                        unfocusedContainerColor = SurfaceVar,
                    ),
                    maxLines       = 4,
                    // ImeAction.Send — klavyedeki gönder tuşu da çalışır, klavye kapanmaz
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendComment() }),
                )
                Spacer(Modifier.width(8.dp))
                // IconButton kullan — clickable klavyeyi kapatır
                IconButton(
                    onClick  = { sendComment() },
                    enabled  = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (inputText.isNotBlank()) Amber else Muted.copy(alpha = 0.15f),
                            CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint     = if (inputText.isNotBlank()) Color.Black else Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    // Silme onay dialogu
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
                TextButton(onClick = { deleteTarget = null }) { Text("İptal", color = Muted) }
            },
        )
    }

    if (errorMsg.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorMsg = "" },
            containerColor   = HeftSurface,
            title  = { Text("Hata", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
            text   = { Text(errorMsg, color = Muted, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { errorMsg = "" }) { Text("Tamam", color = Amber) } },
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

@Composable
private fun DetailCommentRow(
    cmt       : DetailComment,
    canDelete : Boolean,
    onDelete  : () -> Unit,
    onReply   : () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(CircleShape).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (cmt.photoURL.isNotBlank()) {
                AsyncImage(
                    model = cmt.photoURL, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    cmt.name.firstOrNull()?.uppercase() ?: "?",
                    color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (cmt.replyTo.isNotBlank()) {
                Text("@${cmt.replyTo}", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            Row(
                modifier              = Modifier.padding(top = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cmt.likes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${cmt.likes}", color = Muted, fontSize = 11.sp)
                    }
                }
                Text("Yanıtla", color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onReply() })
            }
        }
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint     = Color(0xFFEF4444).copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
