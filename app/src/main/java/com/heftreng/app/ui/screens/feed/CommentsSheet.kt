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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

// ── Veri modeli ───────────────────────────────────────────────────────────────
private data class Cmt(
    val id        : String    = "",
    val uid       : String    = "",
    val name      : String    = "",
    val photoURL  : String    = "",
    val text      : String    = "",
    val likes     : Int       = 0,
    val likedByMe : Boolean   = false,
    val ts        : Timestamp? = null,
)

// ── Ana yorum sheet'i ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    postId      : String,
    postAuthorUid: String,
    onDismiss   : () -> Unit,
) {
    val db      = FirebaseFirestore.getInstance()
    val auth    = FirebaseAuth.getInstance()
    val myUid   = auth.currentUser?.uid ?: ""
    val myName  = auth.currentUser?.displayName ?: ""
    val myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""

    var comments    by remember { mutableStateOf<List<Cmt>>(emptyList()) }
    var inputText   by remember { mutableStateOf("") }
    var loading     by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<Cmt?>(null) }
    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()

    // ── Realtime listener ─────────────────────────────────────────────────────
    DisposableEffect(postId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("feed").document(postId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val myLikedIds = mutableSetOf<String>()

                // liked kontrolü için ayrı sorgu yerine likes alt koleksiyonunu kullanmıyoruz
                // sadece likes sayısını gösteriyoruz
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Cmt(
                        id       = doc.id,
                        uid      = d["uid"] as? String ?: "",
                        name     = (d["displayName"] as? String)?.ifBlank { null }
                                   ?: d["name"] as? String ?: "?",
                        photoURL = d["photoURL"] as? String ?: "",
                        text     = d["text"]     as? String ?: "",
                        likes    = (d["likes"]   as? Long)?.toInt() ?: 0,
                        ts       = d["ts"]       as? Timestamp,
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
                        "uid"         to myUid,
                        "name"        to name,
                        "displayName" to name,
                        "photoURL"    to photo,
                        "text"        to text,
                        "likes"       to 0,
                        "ts"          to Timestamp.now(),
                    )).await()

                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Yorum sil ─────────────────────────────────────────────────────────────
    fun deleteComment(cmt: Cmt) {
        scope.launch {
            try {
                db.collection("feed").document(postId)
                    .collection("comments").document(cmt.id).delete().await()
                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Muted.copy(alpha = 0.4f), CircleShape)
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Başlık
            Text(
                "Yorumlar",
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(color = Divider)

            // Yorum listesi
            if (loading) {
                Box(
                    modifier            = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment    = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                }
            } else if (comments.isEmpty()) {
                Box(
                    modifier            = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment    = Alignment.Center,
                ) {
                    Text("Henüz yorum yok", color = Muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state    = listState,
                    modifier = Modifier.weight(1f),
                ) {
                    items(comments, key = { it.id }) { cmt ->
                        val isOwn = myUid.isNotBlank() && cmt.uid == myUid
                        val isPostAuthor = myUid.isNotBlank() && myUid == postAuthorUid
                        val canDelete = isOwn || isPostAuthor

                        CmtRow(
                            cmt       = cmt,
                            canDelete = canDelete,
                            onDelete  = { deleteTarget = cmt },
                        )
                        HorizontalDivider(
                            color     = Divider.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier  = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            // Yorum yazma alanı
            HorizontalDivider(color = Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeftSurface)
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
                        focusedBorderColor   = Amber,
                        unfocusedBorderColor = Divider,
                        cursorColor          = Amber,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                    ),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = ::sendComment,
                    enabled  = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (inputText.isNotBlank()) Amber else Muted.copy(alpha = 0.2f),
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
                TextButton(onClick = {
                    deleteComment(cmt)
                    deleteTarget = null
                }) {
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
}

// ── Tek yorum satırı ──────────────────────────────────────────────────────────
@Composable
private fun CmtRow(
    cmt       : Cmt,
    canDelete : Boolean,
    onDelete  : () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    model            = cmt.photoURL,
                    contentDescription = null,
                    modifier         = Modifier.fillMaxSize(),
                    contentScale     = ContentScale.Crop,
                )
            } else {
                Text(
                    cmt.name.firstOrNull()?.uppercase() ?: "?",
                    color      = OnBackground,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // İçerik
        Column(Modifier.weight(1f)) {
            Text(
                cmt.name,
                color      = OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                cmt.text,
                color    = OnSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }

        // Sil butonu — sadece canDelete ise göster
        if (canDelete) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick  = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint     = Color(0xFFEF4444).copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
