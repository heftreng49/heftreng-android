package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.heftreng.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Model ─────────────────────────────────────────────────────────────────────
private data class FeedComment(
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
fun CommentsSheet(
    postId        : String,
    postAuthorUid : String,
    onDismiss     : () -> Unit,
) {
    val db    = FirebaseFirestore.getInstance()
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    // ── Auth state ────────────────────────────────────────────────────────────
    var myUid   by remember { mutableStateOf(auth.currentUser?.uid ?: "") }
    var myName  by remember { mutableStateOf(auth.currentUser?.displayName ?: "") }
    var myPhoto by remember { mutableStateOf(auth.currentUser?.photoUrl?.toString() ?: "") }
    // Auth yüklenmemişse tekrar dene
    LaunchedEffect(Unit) {
        if (myUid.isBlank()) {
            myUid   = auth.currentUser?.uid ?: ""
            myName  = auth.currentUser?.displayName ?: ""
            myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    var comments     by remember { mutableStateOf<List<FeedComment>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var replyTo      by remember { mutableStateOf<FeedComment?>(null) }
    var deleteTarget by remember { mutableStateOf<FeedComment?>(null) }
    var errorMsg     by remember { mutableStateOf("") }
    val listState    = rememberLazyListState()

    // ── Realtime listener ─────────────────────────────────────────────────────
    DisposableEffect(postId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("feed").document(postId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { loading = false; return@addSnapshotListener }
                if (snap == null) { loading = false; return@addSnapshotListener }
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FeedComment(
                        id           = doc.id,
                        uid          = d["uid"]          as? String ?: "",
                        name         = (d["name"]        as? String)?.ifBlank { null }
                                       ?: d["displayName"] as? String ?: "?",
                        photoURL     = d["photoURL"]     as? String ?: "",
                        text         = d["text"]         as? String ?: "",
                        replyTo      = d["replyTo"]      as? String ?: "",
                        replyToCmtId = d["replyToCmtId"] as? String ?: "",
                        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
                        ts           = d["ts"]           as? Timestamp,
                    )
                }
                loading = false
            }
        onDispose { reg?.remove() }
    }

    // ── Yorum gönder ──────────────────────────────────────────────────────────
    fun sendComment() {
        val text = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return
        inputText = ""
        val replyRef = replyTo
        replyTo = null
        scope.launch {
            try {
                // Kullanıcı adını Firestore'dan al
                val userDoc = db.collection("users").document(myUid).get().await()
                val name    = userDoc.getString("displayName")
                              ?: userDoc.getString("name")
                              ?: myName.ifBlank { "?" }
                val photo   = userDoc.getString("photoURL") ?: myPhoto

                db.collection("feed").document(postId)
                    .collection("comments").add(mapOf(
                        "uid"          to myUid,
                        "name"         to name,
                        "displayName"  to name,
                        "photoURL"     to photo,
                        "text"         to text,
                        "replyTo"      to (replyRef?.name ?: ""),
                        "replyToCmtId" to (replyRef?.id   ?: ""),
                        "likes"        to 0,
                        "ts"           to Timestamp.now(),
                    )).await()

                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Hata"
            }
        }
    }

    // ── Yorum sil ─────────────────────────────────────────────────────────────
    fun deleteComment(cmt: FeedComment) {
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

    // ── UI ────────────────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = Background,
        contentColor      = OnBackground,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(36.dp).height(4.dp).background(Muted.copy(alpha = 0.3f), CircleShape))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Yorumlar", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (comments.isNotEmpty()) {
                    Text("${comments.size}", color = Muted, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = Divider)

            // Liste
            when {
                loading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                    }
                }
                comments.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💬", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Henüz yorum yok", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(comments, key = { it.id }) { cmt ->
                            // Sil butonu: uid eşleşiyorsa VEYA post sahibiyse göster
                            val canDelete = (cmt.uid.isNotBlank() && cmt.uid == myUid)
                                         || (myUid.isNotBlank() && myUid == postAuthorUid)
                            CommentRow(
                                cmt       = cmt,
                                canDelete = canDelete,
                                onDelete  = { deleteTarget = cmt },
                                onReply   = { replyTo = cmt },
                            )
                            HorizontalDivider(
                                color     = Divider.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                                modifier  = Modifier.padding(start = 52.dp),
                            )
                        }
                    }
                }
            }

            // Yanıtlama göstergesi
            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVar)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "@${replyTo!!.name} yanıtlanıyor",
                        color    = Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(
                        onClick        = { replyTo = null },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("İptal", color = Muted, fontSize = 12.sp)
                    }
                }
            }

            // Yorum yazma
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
                            if (replyTo != null) "@${replyTo!!.name} yanıtla..."
                            else "Yorum yaz...",
                            color    = Muted,
                            fontSize = 14.sp,
                        )
                    },
                    modifier  = Modifier.weight(1f),
                    shape     = RoundedCornerShape(24.dp),
                    colors    = OutlinedTextFieldDefaults.colors(
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
                TextButton(onClick = { deleteTarget = null }) {
                    Text("İptal", color = Muted)
                }
            },
        )
    }

    // Hata dialogu
    if (errorMsg.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorMsg = "" },
            containerColor   = HeftSurface,
            title  = { Text("Hata", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
            text   = { Text(errorMsg, color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { errorMsg = "" }) {
                    Text("Tamam", color = Amber)
                }
            },
        )
    }
}

// ── Yorum satırı ─────────────────────────────────────────────────────────────
@Composable
private fun CommentRow(
    cmt       : FeedComment,
    canDelete : Boolean,
    onDelete  : () -> Unit,
    onReply   : () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Avatar
        Box(
            modifier = Modifier
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

        // İçerik
        Column(Modifier.weight(1f)) {
            Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            // Yanıt göstergesi
            if (cmt.replyTo.isNotBlank()) {
                Text(
                    "@${cmt.replyTo}",
                    color    = Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            // Alt aksiyonlar
            Row(
                modifier            = Modifier.padding(top = 4.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Beğeni sayısı
                if (cmt.likes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Favorite,
                            null,
                            tint     = Color(0xFFEF4444),
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("${cmt.likes}", color = Muted, fontSize = 11.sp)
                    }
                }
                // Yanıtla
                Text(
                    "Yanıtla",
                    color    = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onReply() },
                )
            }
        }

        // Sil butonu
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint     = Color(0xFFEF4444).copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
