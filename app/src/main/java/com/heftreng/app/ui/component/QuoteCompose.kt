package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.*

// XML: _buildQuotePayload / _applyQuote / _renderQuotePreview
// Besleme gönderisine alıntı ekleme bileşeni

data class QuotePayload(
    val text      : String = "",
    val authorName: String = "",
    val bookName  : String = "",
    val postId    : String = "",
)

// ── Alıntı önizleme kartı (gönderi içinde gösterilir) ─────────────────────────
@Composable
fun QuoteCard(
    quote   : QuotePayload,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVar)
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Amber)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            if (quote.bookName.isNotBlank()) {
                Text(
                    quote.bookName,
                    color      = Amber,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                quote.text.take(200) + if (quote.text.length > 200) "…" else "",
                color      = OnSurface,
                fontSize   = 13.sp,
                fontStyle  = FontStyle.Italic,
                lineHeight = 20.sp,
            )
            if (quote.authorName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "— ${quote.authorName}",
                    color    = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Alıntı compose input alanı ─────────────────────────────────────────────────
@Composable
fun QuoteInputSection(
    quote    : QuotePayload?,
    onRemove : () -> Unit,
    modifier : Modifier = Modifier,
) {
    if (quote == null) return
    Box(modifier = modifier) {
        QuoteCard(quote = quote)
        IconButton(
            onClick  = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Alıntıyı kaldır",
                tint     = Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Gönderi kartındaki alıntı seçme butonu ─────────────────────────────────────
@Composable
fun QuoteButton(
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(
            Icons.Default.FormatQuote,
            contentDescription = "Alıntıla",
            tint     = Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
