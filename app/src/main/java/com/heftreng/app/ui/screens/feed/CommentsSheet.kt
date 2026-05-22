package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.heftreng.app.ui.i18n.Strings
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
    val edited       : Boolean    = false,
    val ts           : Timestamp? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    postId        : String,
    postAuthorUid : String,
    language      : String = "tr",
    onDismiss     : () -> Unit,
) {
    val db    = FirebaseFirestore.getInstance()
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    // ── Auth state ────────────────────────────────────────────────────────────
    var myUid   by remember { mutableStateOf(auth.currentUser?.uid ?: "") }
    var myName  by remember { mutableStateOf(auth.currentUser?.displayName ?: "") }
    var myPhoto by remember { mutableStateOf(auth.currentUser?.photoUrl?.toString() ?: "") }
    LaunchedEffect(Unit) {
        if (myUid.isBlank()) {
            myUid   = auth.currentUser?.uid ?: ""
            myName  = auth.currentUser?.displayName ?: ""
            myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    var comments      by remember { mutableStateOf<List<FeedComment>>(emptyList()) }
    var loading       by remember { mutableStateOf(true) }
    var inputText     by remember { mutableStateOf("") }
    var replyTo       by remember { mutableStateOf<FeedComment?>(null) }
    var editTarget    by remember { mutableStateOf<FeedComment?>(null) }
    var deleteTarget  by remember { mutableStateOf<FeedComment?>(null) }
    var menuTarget    by remember { mutableStateOf<FeedComment?>(null) }
    var errorMsg      by remember { mutableStateOf("") }
    val listState     = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Düzenleme moduna girilince inputText'i doldur
    LaunchedEffect(editTarget) {
        editTarget?.let {
            inputText = it.text
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── Realtime listener ─────────────────────────────────────────────────────
    DisposableEffect(postId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("feed").document(postId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { loading = false; return@addSnapshotListener }
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    FeedComment(
                        id           = doc.id,
                        uid          = d["uid"]          as? String ?: "",
                        name         = (d["name"] as? String)?.ifBlank { null }
                                       ?: d["displayName"] as? String ?: "?",
                        photoURL     = d["photoURL"]     as? String ?: "",
                        text         = d["text"]         as? String ?: "",
                        replyTo      = d["replyTo"]      as? String ?: "",
                        replyToCmtId = d["replyToCmtId"] as? String ?: "",
                        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
                        edited       = d["edited"]       as? Boolean ?: false,
                        ts           = d["ts"]           as? Timestamp,
                    )
                }
                loading = false
            }
        onDispose { reg?.remove() }
    }

    // ── Yorum gönder / güncelle ───────────────────────────────────────────────
    fun submitComment() {
        val text = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return

        val editing = editTarget
        inputText  = ""
        editTarget = null

        if (editing != null) {
            // Düzenleme modu
            scope.launch {
                try {
                    db.collection("feed").document(postId)
                        .collection("comments").document(editing.id)
                        .update(mapOf("text" to text, "edited" to true))
                        .await()
                } catch (e: Exception) { errorMsg = e.message ?: Strings.error(language) }
            }
            return
        }

        // Yeni yorum
        val replyRef = replyTo
        replyTo = null
        scope.launch {
            try {
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
                        "edited"       to false,
                        "ts"           to Timestamp.now(),
                    )).await()

                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) { errorMsg = e.message ?: Strings.error(language) }
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
                errorMsg = "${Strings.deleteFailed(language)}: ${e.message}"
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Background,
        contentColor     = OnBackground,
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
                .fillMaxHeight(0.9f),
        ) {
            // ── Başlık ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(Strings.comments(language), color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (comments.isNotEmpty()) {
                    Text("${comments.size}", color = Muted, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = Divider)

            // ── Liste ─────────────────────────────────────────────────────────
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
                            Text(Strings.noComments(language), color = Muted, fontSize = 14.sp)
                        }
                    }
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(comments, key = { it.id }) { cmt ->
                            val isOwner  = cmt.uid.isNotBlank() && cmt.uid == myUid
                            val canEdit  = isOwner
                            val canDelete = isOwner || (myUid.isNotBlank() && myUid == postAuthorUid)
                            CommentRow(
                                cmt       = cmt,
                                canEdit   = canEdit,
                                canDelete = canDelete,
                                language  = language,
                                onLongPress = { menuTarget = cmt },
                                onReply   = { replyTo = cmt; editTarget = null },
                                onEdit    = { editTarget = cmt; replyTo = null },
                                onDelete  = { deleteTarget = cmt },
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

            // ── Düzenleme / Yanıt göstergesi ─────────────────────────────────
            val indicator = editTarget ?: replyTo
            if (indicator != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVar)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (editTarget != null) {
                            Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                Strings.editCommentTitle(language),
                                color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "@${replyTo!!.name} ${Strings.replyingToSuffix(language)}",
                                color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    IconButton(
                        onClick = { editTarget = null; replyTo = null; inputText = "" },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Giriş kutusu — klavyenin ÜSTÜNDE sabit ───────────────────────
            HorizontalDivider(color = Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    placeholder   = {
                        Text(
                            when {
                                editTarget != null -> Strings.editCommentHint(language)
                                replyTo != null    -> Strings.replyHint(language, replyTo!!.name)
                                else               -> Strings.commentHint(language)
                            },
                            color = Muted, fontSize = 14.sp,
                        )
                    },
                    modifier  = Modifier.weight(1f).focusRequester(focusRequester),
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
                        .clickable(enabled = inputText.isNotBlank()) { submitComment() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = Strings.send(language),
                        tint     = if (inputText.isNotBlank()) Color.Black else Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    // ── Long-press context menüsü ─────────────────────────────────────────────
    menuTarget?.let { cmt ->
        val isOwner   = cmt.uid.isNotBlank() && cmt.uid == myUid
        val canDelete = isOwner || (myUid.isNotBlank() && myUid == postAuthorUid)
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor   = HeftSurface,
            title = {
                Text(
                    cmt.name,
                    color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        cmt.text.take(100) + if (cmt.text.length > 100) "…" else "",
                        color = Muted, fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Yanıtla
                    TextButton(
                        onClick = { replyTo = cmt; editTarget = null; menuTarget = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Strings.replyAction(language), color = Amber)
                    }
                    // Düzenle (sadece kendi yorumu)
                    if (isOwner) {
                        TextButton(
                            onClick = { editTarget = cmt; replyTo = null; menuTarget = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Edit, null, tint = OnBackground, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.editAction(language), color = OnBackground)
                        }
                    }
                    // Sil
                    if (canDelete) {
                        TextButton(
                            onClick = { deleteTarget = cmt; menuTarget = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.deleteAction(language), color = Color(0xFFEF4444))
                        }
                    }
                    TextButton(onClick = { menuTarget = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(Strings.cancelAction(language), color = Muted)
                    }
                }
            },
        )
    }

    // ── Silme onay dialogu ────────────────────────────────────────────────────
    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text(Strings.deleteCommentTitle(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { deleteComment(cmt); deleteTarget = null }) {
                    Text(Strings.deleteAction(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(Strings.cancelAction(language), color = Muted)
                }
            },
        )
    }

    // ── Hata dialogu ─────────────────────────────────────────────────────────
    if (errorMsg.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorMsg = "" },
            containerColor   = HeftSurface,
            title  = { Text(Strings.error(language), color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
            text   = { Text(errorMsg, color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { errorMsg = "" }) {
                    Text(Strings.confirm(language), color = Amber)
                }
            },
        )
    }
}

// ── Yorum satırı ─────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    cmt         : FeedComment,
    canEdit     : Boolean,
    canDelete   : Boolean,
    language    : String = "tr",
    onLongPress : () -> Unit,
    onReply     : () -> Unit,
    onEdit      : () -> Unit,
    onDelete    : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick    = {},
                onLongClick = onLongPress,
            )
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
                    color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // İçerik
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (cmt.edited) {
                    Spacer(Modifier.width(4.dp))
                    Text("· ${Strings.editedLabel(language)}", color = Muted, fontSize = 10.sp)
                }
            }
            if (cmt.replyTo.isNotBlank()) {
                Text(
                    "@${cmt.replyTo}",
                    color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)

            // Alt aksiyonlar
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cmt.likes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${cmt.likes}", color = Muted, fontSize = 11.sp)
                    }
                }
                Text(
                    Strings.replyAction(language),
                    color = Muted, fontSize = 11.sp,
                    modifier = Modifier.clickable { onReply() },
                )
                if (canEdit) {
                    Text(
                        Strings.editAction(language),
                        color = Muted, fontSize = 11.sp,
                        modifier = Modifier.clickable { onEdit() },
                    )
                }
                if (canDelete) {
                    Text(
                        Strings.deleteAction(language),
                        color = Color(0xFFEF4444).copy(alpha = 0.7f), fontSize = 11.sp,
                        modifier = Modifier.clickable { onDelete() },
                    )
                }
            }
        }
    }
}
