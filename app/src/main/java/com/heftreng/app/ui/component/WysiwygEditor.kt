package com.heftreng.app.ui.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// WYSIWYG HTML Editör — WebView tabanlı
// ─────────────────────────────────────────────────────────────────────────────

private data class EditorAction(
    val icon   : ImageVector,
    val label  : String,
    val command: String,
)

private val EDITOR_ACTIONS = listOf(
    EditorAction(Icons.Filled.FormatBold,       "Kalın",         "bold"),
    EditorAction(Icons.Filled.FormatItalic,     "İtalik",        "italic"),
    EditorAction(Icons.Filled.FormatUnderlined, "Altı Çizili",   "underline"),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WysiwygEditor(
    value   : String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 220,
) {
    val bgHex      = colorToHex(SurfaceVar)
    val textHex    = colorToHex(OnBackground)
    val placeholderHex = colorToHex(Muted)
    val accentHex  = colorToHex(Amber)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var alignMenu  by remember { mutableStateOf(false) }
    var currentAlign by remember { mutableStateOf("left") }
    var headingMenu by remember { mutableStateOf(false) }

    val html = remember(bgHex, textHex, placeholderHex, accentHex, minHeightDp) {
        buildEditorHtml(bgHex, textHex, placeholderHex, accentHex, minHeightDp)
    }

    Column(modifier = modifier) {
        // ── Toolbar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeftSurface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, Divider, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // B, I, U
            EDITOR_ACTIONS.forEach { action ->
                IconButton(
                    onClick  = { webViewRef?.execCommand(action.command) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(action.icon, action.label, tint = OnBackground, modifier = Modifier.size(20.dp))
                }
            }

            // Başlık dropdown
            Box {
                TextButton(
                    onClick = { headingMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text("A↕", color = OnBackground, fontSize = 14.sp)
                }
                DropdownMenu(
                    expanded = headingMenu,
                    onDismissRequest = { headingMenu = false },
                    modifier = Modifier.background(HeftSurface),
                ) {
                    listOf("Normal" to "p", "Başlık 1" to "h1", "Başlık 2" to "h2", "Başlık 3" to "h3").forEach { (label, tag) ->
                        DropdownMenuItem(
                            text    = { Text(label, color = OnBackground, fontSize = 13.sp) },
                            onClick = {
                                webViewRef?.evaluateJavascript("formatBlock('$tag')", null)
                                headingMenu = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))
            HorizontalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = Divider)
            Spacer(Modifier.width(4.dp))

            // Hizalama dropdown
            Box {
                IconButton(
                    onClick  = { alignMenu = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    val icon = when (currentAlign) {
                        "center" -> Icons.Filled.FormatAlignCenter
                        "right"  -> Icons.Filled.FormatAlignRight
                        "justify" -> Icons.Filled.FormatAlignJustify
                        else     -> Icons.Filled.FormatAlignLeft
                    }
                    Icon(icon, "Hizalama", tint = OnBackground, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = alignMenu,
                    onDismissRequest = { alignMenu = false },
                    modifier = Modifier.background(HeftSurface),
                ) {
                    listOf(
                        "left"    to Icons.Filled.FormatAlignLeft,
                        "center"  to Icons.Filled.FormatAlignCenter,
                        "right"   to Icons.Filled.FormatAlignRight,
                        "justify" to Icons.Filled.FormatAlignJustify,
                    ).forEach { (align, icon) ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(icon, align, tint = if (currentAlign == align) Amber else OnBackground, modifier = Modifier.size(16.dp)) },
                            text = { Text(when(align) { "left" -> "Sola"; "center" -> "Ortala"; "right" -> "Sağa"; else -> "İki Yana" }, color = if (currentAlign == align) Amber else OnBackground, fontSize = 13.sp) },
                            onClick = {
                                webViewRef?.execCommand("justify${align.replaceFirstChar { it.uppercase() }}")
                                currentAlign = align
                                alignMenu = false
                            },
                        )
                    }
                }
            }
        }

        // ── WebView editör alanı ──────────────────────────────────────────
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeightDp.dp)
                .background(SurfaceVar, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, Divider, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onContentChanged(newHtml: String) {
                                onChange(newHtml)
                            }
                        },
                        "Android"
                    )
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    webViewRef = this
                }.also { webViewRef = it }
            },
            update = { wv ->
                webViewRef = wv
                // Sadece ilk yüklemede içeriği set et
                if (value.isNotEmpty()) {
                    val escaped = value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "")
                    wv.evaluateJavascript("setContent(\"$escaped\")", null)
                }
            }
        )
    }
}

private fun WebView.execCommand(command: String) {
    evaluateJavascript("document.execCommand('$command', false, null)", null)
}

private fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
    val r = (color.red   * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue  * 255).toInt()
    return "#%02x%02x%02x".format(r, g, b)
}

private fun buildEditorHtml(bg: String, text: String, placeholder: String, accent: String, minH: Int): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  html, body { background:$bg; height:100%; }
  #editor {
    min-height:${minH}px;
    padding:12px;
    color:$text;
    font-size:15px;
    line-height:1.6;
    outline:none;
    word-break:break-word;
    -webkit-user-modify: read-write-plaintext-only;
  }
  #editor:empty:before {
    content: attr(data-placeholder);
    color:$placeholder;
    pointer-events:none;
  }
  #editor b, #editor strong { font-weight:bold; }
  #editor i, #editor em { font-style:italic; }
  #editor u { text-decoration:underline; }
  #editor h1 { font-size:1.8em; font-weight:bold; margin:8px 0; }
  #editor h2 { font-size:1.4em; font-weight:bold; margin:6px 0; }
  #editor h3 { font-size:1.2em; font-weight:bold; margin:4px 0; }
</style>
</head>
<body>
<div id="editor"
     contenteditable="true"
     data-placeholder="Yazını buraya yaz..."
></div>
<script>
  var editor = document.getElementById('editor');
  var timeout = null;

  function notifyChange() {
    clearTimeout(timeout);
    timeout = setTimeout(function() {
      Android.onContentChanged(editor.innerHTML);
    }, 300);
  }

  editor.addEventListener('input', notifyChange);
  editor.addEventListener('keyup', notifyChange);

  function setContent(html) {
    if (editor.innerHTML !== html) {
      editor.innerHTML = html;
    }
  }

  function formatBlock(tag) {
    document.execCommand('formatBlock', false, tag);
    notifyChange();
  }
</script>
</body>
</html>
""".trimIndent()
