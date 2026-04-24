package com.heftreng.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.theme.*

@Composable
fun PostCard(
    post: Post,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onProfile: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onComment() },
        colors = CardDefaults.cardColors(containerColor = Background)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Üst Kısım: Profil ve İsim
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onProfile() }
            ) {
                AsyncImage(
                    model = post.photoURL.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = post.displayName.ifBlank { post.username.ifBlank { "Bikarhênerê Heftreng" } },
                        fontWeight = FontWeight.Bold,
                        color = OnBackground
                    )
                    if (post.username.isNotBlank()) {
                        Text("@${post.username}", fontSize = 12.sp, color = Muted)
                    }
                }
            }

            // Quote block
            if (post.quoteText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = SurfaceVar,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("\"${post.quoteText}\"", color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                        if (post.bookName.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("— ${post.authorName}, ${post.bookName}", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }

            // İçerik
            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(post.text, color = OnBackground, fontSize = 15.sp, lineHeight = 22.sp)
            }

            // Resim
            if (post.imageURL.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = post.imageURL,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Alt Kısım: Etkileşim Butonları
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike) {
                        Icon(
                            if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (post.isLikedByMe) Color(0xFFEF4444) else Muted
                        )
                    }
                    if (post.likesCount > 0)
                        Text("${post.likesCount}", color = Muted, fontSize = 13.sp)

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = onComment) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Muted)
                    }
                    if (post.commentsCount > 0)
                        Text("${post.commentsCount}", color = Muted, fontSize = 13.sp)

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Repeat, contentDescription = null, tint = Muted)
                    }
                    if (post.repostsCount > 0)
                        Text("${post.repostsCount}", color = Muted, fontSize = 13.sp)
                }

                IconButton(onClick = onSave) {
                    Icon(
                        if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = if (post.isSavedByMe) Amber else Muted
                    )
                }
            }
        }
    }
}
