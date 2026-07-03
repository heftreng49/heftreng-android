package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.heftreng.app.viewmodel.FeedViewModel

/**
 * `@` yazınca yorum/gönderi input alanının üstünde açılan yatay kullanıcı öneri barı.
 * Boşsa hiçbir şey çizmez.
 */
@Composable
fun MentionSuggestionBar(
    suggestions : List<FeedViewModel.MentionUser>,
    onSelect    : (FeedViewModel.MentionUser) -> Unit,
    modifier    : Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    Column(modifier = modifier) {
        HorizontalDivider()
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(suggestions, key = { it.uid }) { user ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .clickable { onSelect(user) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier         = Modifier.size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (user.photoURL.isNotBlank()) {
                            AsyncImage(
                                model              = user.photoURL,
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                user.name.firstOrNull()?.uppercase() ?: "?",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(user.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
