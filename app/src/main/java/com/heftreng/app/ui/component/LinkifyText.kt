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
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.Amber
import com.heftreng.app.ui.theme.Muted
import com.heftreng.app.ui.theme.OnBackground
import com.heftreng.app.utils.openUrl

private val URL_REGEX     = Regex("""https?://[^\s]+|www\.[^\s]+""")
private val HASHTAG_REGEX = Regex("""#[a-zA-Z0-9_-]{6,}""")
private val MENTION_REGEX = Regex("""@[\p{L}0-9_]+""")

// Kaç satırdan sonra "daha fazlasını göster" çıksın
private const val COLLAPSED_LINES = 4

@Composable
fun LinkifyText(
    text            : String,
    modifier        : Modifier  = Modifier,
    fontSize        : TextUnit  = 15.sp,
    lineHeight      : TextUnit  = 22.sp,
    maxLines        : Int       = Int.MAX_VALUE,
    overflow        : TextOverflow = TextOverflow.Clip,
    expandable      : Boolean   = false,   // Feed listesinde true, detail'de false
    language        : String    = "tr",
    onHashtagClick  : ((postId: String) -> Unit)? = null,  // null ise #etiketler tıklanamaz, sadece renklendirilir
    mentionUids     : List<String> = emptyList(),          // metindeki @mention'ların görünme sırasına göre uid'leri
    onMentionClick  : ((uid: String) -> Unit)? = null,     // null ise @mention'lar tıklanamaz, sadece renklendirilir
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    // Link + hashtag + mention annotasyonları (tek geçişte, pozisyona göre sıralı)
    val annotated = remember(text, mentionUids) {
        buildAnnotatedString {
            val matches = (URL_REGEX.findAll(text).map { "URL" to it } +
                           HASHTAG_REGEX.findAll(text).map { "HASHTAG" to it } +
                           MENTION_REGEX.findAll(text).map { "MENTION" to it })
                .sortedBy { it.second.range.first }
            var last = 0
            var mentionIdx = 0
            for ((tag, match) in matches) {
                if (match.range.first < last) continue // çakışan eşleşmeyi atla
                append(text.substring(last, match.range.first))
                if (tag == "MENTION") {
                    val uid = mentionUids.getOrNull(mentionIdx)
                    mentionIdx++
                    if (uid != null) {
                        pushStringAnnotation("MENTION", uid)
                        withStyle(SpanStyle(color = Amber)) { append(match.value) }
                        pop()
                    } else {
                        append(match.value) // eşleşen uid yoksa düz metin, tıklanamaz
                    }
                } else {
                    val annotation = if (tag == "HASHTAG") match.value.removePrefix("#") else match.value
                    pushStringAnnotation(tag, annotation)
                    withStyle(SpanStyle(color = Amber)) { append(match.value) }
                    pop()
                }
                last = match.range.last + 1
            }
            append(text.substring(last))
        }
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
                annotated.getStringAnnotations("HASHTAG", offset, offset)
                    .firstOrNull()?.let { onHashtagClick?.invoke(it.item) }
                annotated.getStringAnnotations("MENTION", offset, offset)
                    .firstOrNull()?.let { onMentionClick?.invoke(it.item) }
            },
        )

        // "Daha fazlasını göster / Daha az göster" butonu
        if (expandable && isLong) {
            Text(
                text     = if (expanded) Strings.showLess(language) else Strings.showMore(language),
                color    = Amber,
                fontSize = 13.sp,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
