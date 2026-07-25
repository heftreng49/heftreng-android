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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.heftreng.app.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WysiwygEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    minHeightDp: Int = 220,
) {
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var alignMenu    by remember { mutableStateOf(false) }
    var headingMenu  by remember { mutableStateOf(false) }
    var currentAlign by remember { mutableStateOf("left") }

    val bgHex  = colorToHex(SurfaceVar)
    val txtHex = colorToHex(OnBackground)
    val phHex  = colorToHex(Muted)

    val html = remember(bgHex, txtHex, phHex, minHeightDp) {
        buildEditorHtml(bgHex, txtHex, phHex, minHeightDp)
    }

    fun js(script: String) = webRef?.evaluateJavascript(script, null)

    Column(modifier = modifier) {
        // ── Toolbar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeftSurface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, Divider, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { js("cmd('bold')") },      modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FormatBold,       "Kalın",       tint = OnBackground, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { js("cmd('italic')") },    modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FormatItalic,     "İtalik",      tint = OnBackground, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { js("cmd('underline')") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FormatUnderlined, "Altı Çizili", tint = OnBackground, modifier = Modifier.size(20.dp))
            }

            Box {
                TextButton(onClick = { headingMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(40.dp)) {
                    Text("Aa", color = OnBackground, fontSize = 15.sp)
                }
                DropdownMenu(expanded = headingMenu, onDismissRequest = { headingMenu = false }, modifier = Modifier.background(HeftSurface)) {
                    listOf("Normal" to "p", "Başlık 1" to "h1", "Başlık 2" to "h2", "Başlık 3" to "h3").forEach { (label, tag) ->
                        DropdownMenuItem(
                            text    = { Text(label, color = OnBackground, fontSize = 13.sp) },
                            onClick = { js("cmd('formatBlock','<$tag>')"); headingMenu = false },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box {
                IconButton(onClick = { alignMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        when (currentAlign) {
                            "center"  -> Icons.Filled.FormatAlignCenter
                            "right"   -> Icons.Filled.FormatAlignRight
                            "justify" -> Icons.Filled.FormatAlignJustify
                            else      -> Icons.Filled.FormatAlignLeft
                        },
                        "Hizalama", tint = OnBackground, modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = alignMenu, onDismissRequest = { alignMenu = false }, modifier = Modifier.background(HeftSurface)) {
                    listOf(
                        "justifyLeft"    to Pair("left",    "Sola"),
                        "justifyCenter"  to Pair("center",  "Ortala"),
                        "justifyRight"   to Pair("right",   "Sağa"),
                        "justifyFull"    to Pair("justify", "İki Yana"),
                    ).forEach { (execCmd, pair) ->
                        val (alignKey, label) = pair
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    when(alignKey) {
                                        "center"  -> Icons.Filled.FormatAlignCenter
                                        "right"   -> Icons.Filled.FormatAlignRight
                                        "justify" -> Icons.Filled.FormatAlignJustify
                                        else      -> Icons.Filled.FormatAlignLeft
                                    },
                                    alignKey,
                                    tint = if (currentAlign == alignKey) Amber else OnBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            text    = { Text(label, color = if (currentAlign == alignKey) Amber else OnBackground, fontSize = 13.sp) },
                            onClick = { js("cmd('$execCmd')"); currentAlign = alignKey; alignMenu = false },
                        )
                    }
                }
            }
        }

        // ── WebView ───────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeightDp.dp)
                .background(SurfaceVar, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, Divider, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled  = true
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onChanged(html: String) { onChange(html) }
                    }, "Android")
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    webRef = this
                }
            },
            update = { wv ->
                webRef = wv
                if (value.isNotEmpty()) {
                    val escaped = value
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                    wv.evaluateJavascript("setContent(`$escaped`)", null)
                }
            }
        )
    }
}

private fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
    val r = (color.red   * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue  * 255).toInt()
    return "#%02x%02x%02x".format(r, g, b)
}

private fun buildEditorHtml(bg: String, text: String, ph: String, minH: Int) = """
<!DOCTYPE html><html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
html,body { background:$bg; }
#editor {
  min-height:${minH}px;
  padding:12px;
  color:$text;
  font-size:15px;
  line-height:1.6;
  outline:none;
  word-break:break-word;
  font-family:sans-serif;
}
#editor:empty::before { content:attr(data-ph); color:$ph; pointer-events:none; display:block; }
</style>
</head><body>
<div id="editor" contenteditable="true" data-ph="Yazını buraya yaz..."></div>
<script>
var editor = document.getElementById('editor');
var savedRange = null;
var timer = null;

// Selection'ı kaydet (buton focus almadan önce)
editor.addEventListener('blur', function() {
  var sel = window.getSelection();
  if (sel && sel.rangeCount > 0) savedRange = sel.getRangeAt(0).cloneRange();
});

// Değişikliği bildir
editor.addEventListener('input', function() {
  clearTimeout(timer);
  timer = setTimeout(function(){ Android.onChanged(editor.innerHTML); }, 300);
});

// Kayıtlı selection'ı geri yükle ve komutu çalıştır
function cmd(command, value) {
  editor.focus();
  if (savedRange) {
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(savedRange);
  }
  document.execCommand(command, false, value || null);
  Android.onChanged(editor.innerHTML);
}

function setContent(html) {
  if (editor.innerHTML !== html) editor.innerHTML = html;
}
</script>
</body></html>
""".trimIndent()
