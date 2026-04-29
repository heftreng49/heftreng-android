package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// XML: _buildBadgeHtml / _buildInlineBadge
// Firestore: users/{uid}.badges = [{id, label, color, icon}]
// Admin: users/{uid}.badges alanını elle günceller

data class Badge(
    val id    : String = "",
    val label : String = "",
    val color : String = "#8B5CF6",   // hex
    val icon  : String = "✦",
)

// ── Rozet satırı (profilde ve gönderi kartında) ───────────────────────────────
@Composable
fun BadgeRow(
    badges  : List<Badge>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return
    LazyRow(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(badges) { badge ->
            BadgeChip(badge)
        }
    }
}

@Composable
fun BadgeChip(badge: Badge) {
    val bg = try {
        Color(android.graphics.Color.parseColor(badge.color))
    } catch (_: Exception) {
        Color(0xFF8B5CF6)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badge.icon.isNotBlank()) {
            Text(badge.icon, fontSize = 10.sp)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            badge.label,
            color      = bg,
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
