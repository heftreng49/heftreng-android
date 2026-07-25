package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Compat shims
// ─────────────────────────────────────────────────────────────────────────────

fun spansToHtml(text: String, @Suppress("UNUSED_PARAMETER") spans: Any?): String = text
fun htmlToSpans(html: String): HtmlParseResult = HtmlParseResult(html, emptyList())
fun htmlStrip(html: String): String = html
    .replace(Regex("<br/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .trimEnd()

data class HtmlParseResult(val text: String, val spans: List<Any>)

// ─────────────────────────────────────────────────────────────────────────────
// HTML toolbar aksiyonları
// ─────────────────────────────────────────────────────────────────────────────

private sealed class HtmlAction {
    data class Wrap(val icon: ImageVector, val label: String, val open: String, val close: String) : HtmlAction()
    data class Block(val icon: ImageVector, val label: String, val tag: String) : HtmlAction()
    data class Align(val alignment: String) : HtmlAction()
}

private val TOOLBAR_ACTIONS = listOf(
    HtmlAction.Wrap(Icons.Filled.FormatBold,          "Kalın",    "<b>",  "</b>"),
    HtmlAction.Wrap(Icons.Filled.FormatItalic,        "İtalik",   "<i>",  "</i>"),
    HtmlAction.Wrap(Icons.Filled.FormatUnderlined,    "Altı Çizili", "<u>", "</u>"),
    HtmlAction.Block(Icons.Filled.Title,              "H2",        "h2"),
    HtmlAction.Block(Icons.Filled.TextFields,         "H3",        "h3"),
)

// ─────────────────────────────────────────────────────────────────────────────
// Ana RichTextEditor — HTML tabanlı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RichTextEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    placeholder: String   = "Yazını buraya yaz...",
) {
    var tfv       by remember(value) { mutableStateOf(TextFieldValue(value)) }
    var isFocused by remember { mutableStateOf(false) }
    var alignment by remember { mutableStateOf("left") }
    var showAlignMenu by remember { mutableStateOf(false) }

    val surface    = HeftSurface
    val surfaceVar = SurfaceVar
    val onBg       = OnBackground
    val muted      = Muted
    val divider    = Divider

    fun applyWrap(open: String, close: String) {
        val sel  = tfv.selection
        val text = tfv.text
        val selected = if (!sel.collapsed) text.substring(sel.min, sel.max) else ""
        val newText = text.substring(0, sel.min) + open + selected + close + text.substring(sel.max)
        val newCursor = if (selected.isNotEmpty()) sel.max + open.length + close.length
                        else sel.min + open.length
        tfv = TextFieldValue(newText, TextRange(newCursor))
        onChange(newText)
    }

    fun applyBlock(tag: String) {
        val sel  = tfv.selection
        val text = tfv.text
        val selected = if (!sel.collapsed) text.substring(sel.min, sel.max) else ""
        val ins = "<$tag>$selected</$tag>"
        val newText = text.substring(0, sel.min) + ins + text.substring(sel.max)
        val newCursor = sel.min + ins.length
        tfv = TextFieldValue(newText, TextRange(newCursor))
        onChange(newText)
    }

    fun applyAlign(align: String) {
        alignment = align
        val sel  = tfv.selection
        val text = tfv.text
        val selected = if (!sel.collapsed) text.substring(sel.min, sel.max) else ""
        val ins = "<p style=\"text-align:$align\">$selected</p>"
        val newText = text.substring(0, sel.min) + ins + text.substring(sel.max)
        val newCursor = sel.min + ins.length
        tfv = TextFieldValue(newText, TextRange(newCursor))
        onChange(newText)
        showAlignMenu = false
    }

    Column(modifier = modifier) {
        // ── Toolbar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, divider, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            TOOLBAR_ACTIONS.forEach { action ->
                when (action) {
                    is HtmlAction.Wrap -> {
                        IconButton(
                            onClick  = { applyWrap(action.open, action.close) },
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                        ) {
                            Icon(action.icon, action.label, tint = onBg, modifier = Modifier.size(18.dp))
                        }
                    }
                    is HtmlAction.Block -> {
                        TextButton(
                            onClick  = { applyBlock(action.tag) },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(action.label, color = onBg, fontSize = 12.sp)
                        }
                    }
                    else -> {}
                }
            }

            // Ayırıcı
            Spacer(Modifier.width(4.dp))
            Divider(modifier = Modifier.height(20.dp).width(1.dp), color = divider)
            Spacer(Modifier.width(4.dp))

            // Hizalama dropdown
            Box {
                IconButton(
                    onClick  = { showAlignMenu = true },
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                ) {
                    val alignIcon = when (alignment) {
                        "center" -> Icons.Filled.FormatAlignCenter
                        "right"  -> Icons.Filled.FormatAlignRight
                        else     -> Icons.Filled.FormatAlignLeft
                    }
                    Icon(alignIcon, "Hizalama", tint = onBg, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded         = showAlignMenu,
                    onDismissRequest = { showAlignMenu = false },
                    modifier         = Modifier.background(HeftSurface),
                ) {
                    listOf(
                        "left"   to Icons.Filled.FormatAlignLeft,
                        "center" to Icons.Filled.FormatAlignCenter,
                        "right"  to Icons.Filled.FormatAlignRight,
                    ).forEach { (align, icon) ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(icon, align, tint = if (alignment == align) Amber else onBg, modifier = Modifier.size(16.dp)) },
                            text        = { Text(when(align) { "left" -> "Sola"; "center" -> "Ortala"; else -> "Sağa" }, color = if (alignment == align) Amber else onBg, fontSize = 13.sp) },
                            onClick     = { applyAlign(align) },
                        )
                    }
                }
            }
        }

        // ── Yazı alanı ────────────────────────────────────────────────────
        BasicTextField(
            value         = tfv,
            onValueChange = { tfv = it; onChange(it.text) },
            cursorBrush   = SolidColor(Amber),
            textStyle     = LocalTextStyle.current.copy(
                color      = onBg,
                fontSize   = 14.sp,
                lineHeight = 22.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .background(surfaceVar, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, if (isFocused) Amber else divider, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(12.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (tfv.text.isEmpty()) Text(placeholder, color = muted, fontSize = 14.sp)
                    inner()
                }
            },
        )

        val charCount = tfv.text.length
        Text(
            "$charCount karakter",
            color    = if (charCount < 100) Color(0xFFEF4444) else muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Markdown compat (artık kullanılmıyor ama import hataları için)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val context      = androidx.compose.ui.platform.LocalContext.current
    val textColorInt = OnSurface.let { c ->
        android.graphics.Color.argb(
            (c.alpha * 255).toInt(), (c.red * 255).toInt(),
            (c.green * 255).toInt(), (c.blue * 255).toInt(),
        )
    }
    val markwon = remember(context) {
        io.noties.markwon.Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .build()
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(textColorInt)
                textSize     = 15f
                setLineSpacing(0f, 1.4f)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(0, 0, 0, 0)
            }
        },
        update = { tv ->
            markwon.setMarkdown(tv, markdown)
            tv.setTextColor(textColorInt)
        },
    )
}
