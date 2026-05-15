package com.heftreng.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.Amber
import com.heftreng.app.ui.theme.Muted
import com.heftreng.app.ui.theme.OnBackground
import com.heftreng.app.utils.openUrl

private val URL_REGEX = Regex("""https?://[^\s]+|www\.[^\s]+""")

// Kaç satırdan sonra "daha fazlasını göster" çıksın
private const val COLLAPSED_LINES = 4

@Composable
fun LinkifyText(
    text       : String,
    modifier   : Modifier  = Modifier,
    fontSize   : TextUnit  = 15.sp,
    lineHeight : TextUnit  = 22.sp,
    maxLines   : Int       = Int.MAX_VALUE,
    overflow   : TextOverflow = TextOverflow.Clip,
    expandable : Boolean   = false,   // Feed listesinde true, detail'de false
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    // Link annotasyonları
    val annotated = buildAnnotatedString {
        var last = 0
        URL_REGEX.findAll(text).forEach { match ->
            append(text.substring(last, match.range.first))
            pushStringAnnotation("URL", match.value)
            withStyle(SpanStyle(color = Amber)) { append(match.value) }
            pop()
            last = match.range.last + 1
        }
        append(text.substring(last))
    }

    // Metnin kaç satır olduğunu tahmin et
    val isLong = text.length > 220 || text.count { it == '\n' } >= COLLAPSED_LINES

    Column(modifier = modifier) {
        ClickableText(
            text     = annotated,
            style    = TextStyle(
                color      = OnBackground,
                fontSize   = fontSize,
                lineHeight = lineHeight,
            ),
            maxLines = when {
                !expandable || !isLong -> maxLines
                expanded               -> Int.MAX_VALUE
                else                   -> COLLAPSED_LINES
            },
            overflow = if (expandable && isLong && !expanded) TextOverflow.Ellipsis else overflow,
            onClick  = { offset ->
                annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.let { openUrl(context, it.item) }
            },
        )

        // "Daha fazlasını göster / Daha az göster" butonu
        if (expandable && isLong) {
            Text(
                text     = if (expanded) "Daha az göster" else "Daha fazlasını göster",
                color    = Amber,
                fontSize = 13.sp,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
