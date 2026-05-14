package com.heftreng.app.ui.component

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.Amber
import com.heftreng.app.ui.theme.OnBackground
import com.heftreng.app.utils.openUrl

private val URL_REGEX = Regex("""https?://[^\s]+|www\.[^\s]+""")

@Composable
fun LinkifyText(
    text      : String,
    modifier  : Modifier  = Modifier,
    fontSize  : TextUnit  = 15.sp,
    lineHeight: TextUnit  = 22.sp,
    maxLines  : Int       = Int.MAX_VALUE,
    overflow  : TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current

    val annotated = buildAnnotatedString {
        var last = 0
        URL_REGEX.findAll(text).forEach { match ->
            // Normal metin
            append(text.substring(last, match.range.first))
            // Link kısmı
            pushStringAnnotation(tag = "URL", annotation = match.value)
            withStyle(SpanStyle(color = Amber)) {
                append(match.value)
            }
            pop()
            last = match.range.last + 1
        }
        append(text.substring(last))
    }

    ClickableText(
        text     = annotated,
        modifier = modifier,
        style    = TextStyle(
            color      = OnBackground,
            fontSize   = fontSize,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = overflow,
        onClick  = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { openUrl(context, it.item) }
        },
    )
}
