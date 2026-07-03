package com.heftreng.app.ui.component

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Amber renk — mention/hashtag vurgusu için. Tema renklerinden bağımsız, tutarlı bir vurgu rengi. */
private val MentionAmber = Color(0xFFFFA726)

/**
 * Yorum/gönderi metnini `@DisplayName` mention'larını tıklanabilir amber span'lara çevirerek gösterir.
 *
 * `mentionUids`, metindeki `@...` parçalarının GÖRÜNME SIRASINA göre eşlenen uid listesidir.
 * Örn: metin "Selam @Ali ve @Veli" ise mentionUids = [aliUid, veliUid] olmalı.
 * Bu eşleme, mention'ın yalnızca öneri listesinden SEÇİLEREK eklenmesiyle korunur;
 * elle yazılan "@kelime" parçaları mentionUids'te karşılığı yoksa düz metin olarak kalır (tıklanamaz).
 */
@Composable
fun MentionText(
    text        : String,
    mentionUids : List<String>,
    modifier    : Modifier = Modifier,
    fontSize    : TextUnit = 14.sp,
    lineHeight  : TextUnit = 20.sp,
    color       : Color = LocalContentColor.current,
    onMentionClick: (uid: String) -> Unit = {},
    onHashtagClick: ((postId: String) -> Unit)? = null,
) {
    val annotated = buildRichAnnotatedString(text, mentionUids)
    ClickableText(
        text     = annotated,
        modifier = modifier,
        style    = TextStyle(
            color      = color,
            fontSize   = fontSize,
            lineHeight = lineHeight,
        ),
        onClick  = { offset ->
            annotated.getStringAnnotations(tag = MENTION_TAG, start = offset, end = offset)
                .firstOrNull()?.let { onMentionClick(it.item) }
            annotated.getStringAnnotations(tag = HASHTAG_TAG, start = offset, end = offset)
                .firstOrNull()?.let { onHashtagClick?.invoke(it.item) }
        },
    )
}

private const val MENTION_TAG = "mention"

// Basit mention regex: @ sonrası harf/rakam/alt çizgi ve Kürtçe/Türkçe özel harfler dahil kelime
private val MENTION_REGEX = Regex("@[\\p{L}0-9_]+")

@Composable
private fun buildRichAnnotatedString(text: String, mentionUids: List<String>): AnnotatedString {
    return remember(text, mentionUids) {
        buildAnnotatedString {
            val matches = (MENTION_REGEX.findAll(text).map { MENTION_TAG to it } +
                           HASHTAG_REGEX.findAll(text).map { HASHTAG_TAG to it })
                .sortedBy { it.second.range.first }
            var lastIndex = 0
            var mentionIdx = 0
            for ((tag, match) in matches) {
                if (match.range.first < lastIndex) continue // çakışan eşleşme, atla
                append(text.substring(lastIndex, match.range.first))
                if (tag == MENTION_TAG) {
                    val uid = mentionUids.getOrNull(mentionIdx)
                    mentionIdx++
                    if (uid != null) {
                        pushStringAnnotation(tag = MENTION_TAG, annotation = uid)
                        withStyle(SpanStyle(color = MentionAmber, fontWeight = FontWeight.SemiBold)) {
                            append(match.value)
                        }
                        pop()
                    } else {
                        append(match.value) // mentionUids'te karşılığı yoksa düz metin
                    }
                } else {
                    val postId = match.value.removePrefix("#")
                    pushStringAnnotation(tag = HASHTAG_TAG, annotation = postId)
                    withStyle(SpanStyle(color = MentionAmber, fontWeight = FontWeight.SemiBold)) {
                        append(match.value)
                    }
                    pop()
                }
                lastIndex = match.range.last + 1
            }
            append(text.substring(lastIndex))
        }
    }
}

/**
 * Metindeki `#<postId>` etiketlerini tıklanabilir amber span'lara çevirir.
 * postId formatı en az 6 karakter harf/rakam/alt çizgi/tire olarak kabul edilir.
 */
@Composable
fun HashtagText(
    text        : String,
    modifier    : Modifier = Modifier,
    fontSize    : TextUnit = 14.sp,
    lineHeight  : TextUnit = 20.sp,
    color       : Color = LocalContentColor.current,
    onHashtagClick: (postId: String) -> Unit = {},
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in HASHTAG_REGEX.findAll(text)) {
                append(text.substring(lastIndex, match.range.first))
                val postId = match.value.removePrefix("#")
                pushStringAnnotation(tag = HASHTAG_TAG, annotation = postId)
                withStyle(SpanStyle(color = MentionAmber, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.None)) {
                    append(match.value)
                }
                pop()
                lastIndex = match.range.last + 1
            }
            append(text.substring(lastIndex))
        }
    }
    ClickableText(
        text     = annotated,
        modifier = modifier,
        style    = TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight),
        onClick  = { offset ->
            annotated.getStringAnnotations(tag = HASHTAG_TAG, start = offset, end = offset)
                .firstOrNull()?.let { onHashtagClick(it.item) }
        },
    )
}

private const val HASHTAG_TAG = "hashtag"
private val HASHTAG_REGEX = Regex("#[a-zA-Z0-9_-]{6,}")
