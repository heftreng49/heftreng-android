package com.heftreng.app.ui.screens.social

// ═══════════════════════════════════════════════════════
//  UserListSheet — Takipçi / Takip / Beğenen listesi
//  Tema (site): .follow-list, .follow-item, .fl-av, .fl-name
// ═══════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.heftreng.app.data.model.FollowEntry
import com.heftreng.app.data.model.LikeEntry
import com.heftreng.app.ui.theme.*

// ── Takipçi / Takip bottom sheet ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListSheet(
    title     : String,
    entries   : List<FollowEntry>,
    loading   : Boolean,
    onDismiss : () -> Unit,
    onProfile : (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = HeftSurface,
        dragHandle        = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Divider))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = Muted)
            }
        }
        HorizontalDivider(color = Divider)

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp)) }

            entries.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, null, tint = Divider, modifier = Modifier.size(44.dp))
                    Text("Henüz kimse yok", color = Muted, fontSize = 13.sp)
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(entries, key = { it.uid }) { entry ->
                    FollowEntryRow(entry = entry, onClick = { onProfile(entry.uid) })
                    HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
    }
}

// ── Beğenen listesi bottom sheet ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikerListSheet(
    title     : String = "Beğenenler",
    likers    : List<LikeEntry>,
    loading   : Boolean,
    onDismiss : () -> Unit,
    onProfile : (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = HeftSurface,
        dragHandle        = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Divider))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = Muted)
            }
        }
        HorizontalDivider(color = Divider)

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp)) }

            likers.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("❤️", fontSize = 32.sp)
                    Text("Henüz beğeni yok", color = Muted, fontSize = 13.sp)
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(likers, key = { it.uid }) { entry ->
                    LikeEntryRow(entry = entry, onClick = { onProfile(entry.uid) })
                    HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
    }
}

// ── Satır bileşenleri ─────────────────────────────────────────────────────────
@Composable
fun FollowEntryRow(entry: FollowEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(name = entry.name, photoURL = entry.photoURL, size = 42)
        Spacer(Modifier.width(12.dp))
        Text(
            entry.name.ifBlank { "Kullanıcı" },
            color      = OnBackground,
            fontWeight = FontWeight.Medium,
            fontSize   = 14.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LikeEntryRow(entry: LikeEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(name = entry.name, photoURL = entry.photoURL, size = 42)
        Spacer(Modifier.width(12.dp))
        Text(
            entry.name.ifBlank { "Kullanıcı" },
            color      = OnBackground,
            fontWeight = FontWeight.Medium,
            fontSize   = 14.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

// ── Ortak avatar bileşeni ─────────────────────────────────────────────────────
@Composable
fun UserAvatar(name: String, photoURL: String, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Primary, PrimaryLight))),
        contentAlignment = Alignment.Center,
    ) {
        if (photoURL.isNotBlank()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(
                    androidx.compose.ui.platform.LocalContext.current
                ).data(photoURL).crossfade(true).build(),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = (size / 2.5).sp,
            )
        }
    }
}
