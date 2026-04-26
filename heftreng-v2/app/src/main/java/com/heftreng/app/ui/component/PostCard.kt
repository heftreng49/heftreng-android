package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.theme.*

@Composable
fun PostCard(
    post     : Post,
    onLike   : () -> Unit,
    onSave   : () -> Unit,
    onProfile: () -> Unit,
    onComment: () -> Unit,
    onShare  : () -> Unit,
    onCardClick: () -> Unit = onComment,
) {
    val bg      = bg()
    val sfVar   = surfVar()
    val onBg    = onBg()
    val onSurf  = onSurf()
    val mut     = muted()
    val acc     = accent()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable { onCardClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ── Başlık ───────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(sfVar)
                    .clickable { onProfile() },
                contentAlignment = Alignment.Center,
            ) {
                if (post.photoURL.isNotBlank()) {
                    AsyncImage(
                        model              = post.photoURL,
                        contentDescription = post.displayName,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text       = post.displayName.firstOrNull()?.uppercase() ?: "H",
                        color      = acc,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f).clickable { onProfile() }) {
                val name = post.displayName.ifBlank { post.username.ifBlank { "Bikarhênerê Heftreng" } }
                Text(name, fontWeight = FontWeight.SemiBold, color = onBg, fontSize = AppFontSize.sp)
                if (post.username.isNotBlank())
                    Text("@${post.username}", color = mut, fontSize = (AppFontSize - 3).sp)
            }

            IconButton(onClick = onComment, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.OpenInNew, null, tint = mut, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Alıntı bloğu ─────────────────────────────────
        if (post.quoteText.isNotBlank()) {
            Surface(shape = RoundedCornerShape(10.dp), color = sfVar) {
                Row(modifier = Modifier.padding(12.dp)) {
                    // Sol çizgi
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(IntrinsicSize.Min)
                            .background(acc, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text       = "\"${post.quoteText}\"",
                            color      = onSurf,
                            fontSize   = AppFontSize.sp,
                            lineHeight = (AppFontSize + 7).sp,
                            fontStyle  = FontStyle.Italic,
                        )
                        if (post.bookName.isNotBlank() || post.authorName.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.MenuBook, null, tint = acc, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    buildString {
                                        if (post.authorName.isNotBlank()) append(post.authorName)
                                        if (post.authorName.isNotBlank() && post.bookName.isNotBlank()) append(" · ")
                                        if (post.bookName.isNotBlank()) append(post.bookName)
                                    },
                                    color    = mut,
                                    fontSize = (AppFontSize - 3).sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Metin ────────────────────────────────────────
        if (post.text.isNotBlank()) {
            Text(
                post.text,
                color      = onBg,
                fontSize   = AppFontSize.sp,
                lineHeight = (AppFontSize + 7).sp,
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Görsel ───────────────────────────────────────
        if (post.imageURL.isNotBlank()) {
            AsyncImage(
                model              = post.imageURL,
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Etkileşim butonları ───────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Beğen
            IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint     = if (post.isLikedByMe) Error else mut,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (post.likesCount > 0)
                Text(post.likesCount.toString(), color = mut, fontSize = 12.sp)

            Spacer(Modifier.width(4.dp))

            // Yorum
            IconButton(onClick = onComment, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = mut, modifier = Modifier.size(18.dp))
            }
            if (post.commentsCount > 0)
                Text(post.commentsCount.toString(), color = mut, fontSize = 12.sp)

            Spacer(Modifier.width(4.dp))

            // Repost
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Repeat, null, tint = mut, modifier = Modifier.size(18.dp))
            }
            if (post.repostsCount > 0)
                Text(post.repostsCount.toString(), color = mut, fontSize = 12.sp)

            Spacer(Modifier.weight(1f))

            // Kaydet
            IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    null,
                    tint     = if (post.isSavedByMe) acc else mut,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
