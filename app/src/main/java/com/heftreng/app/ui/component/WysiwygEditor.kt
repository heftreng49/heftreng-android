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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.heftreng.app.ui.theme.*

private data class ToolbarBtn(val icon: ImageVector, val label: String, val js: String)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WysiwygEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    minHeightDp: Int = 220,
) {
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var alignMenu   by remember { mutableStateOf(false) }
    var headingMenu by remember { mutableStateOf(false) }
    var currentAlign by remember { mutableStateOf("left") }

    val bgHex   = colorToHex(SurfaceVar)
    val txtHex  = colorToHex(OnBackground)
    val phHex   = colorToHex(Muted)

    val editorHtml = remember(bgHex, txtHex, phHex, minHeightDp) {
        buildEditorHtml(bgHex, txtHex, phHex, minHeightDp)
    }

    fun js(script: String) = webRef?.evaluateJavascript(script, null)

    Column(modifier = modifier) {
        // ── Toolbar ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeftSurface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, Divider, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // B
            IconButton(onClick = { js("wrapTag('b')") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FormatBold, "Kalın", tint = OnBackground, modifier = Modifier.size(20.dp))
            }
            // I
            IconButton(onClick = { js("wrapTag('i')") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FormatItalic, "İtalik", tint = OnBackground, modifier = Modifier.size(20.dp))
            }
            // U
            IconButton(onClick = { js("wrapTag('u')") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.FormatUnderlined, "Altı Çizili", tint = OnBackground, modifier = Modifier.size(20.dp))
            }

            // Başlık
            Box {
                TextButton(onClick = { headingMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(40.dp)) {
                    Text("Aa", color = OnBackground, fontSize = 15.sp)
                }
                DropdownMenu(expanded = headingMenu, onDismissRequest = { headingMenu = false }, modifier = Modifier.background(HeftSurface)) {
                    listOf("Normal" to "p", "Başlık 1" to "h1", "Başlık 2" to "h2", "Başlık 3" to "h3").forEach { (label, tag) ->
                        DropdownMenuItem(
                            text    = { Text(label, color = OnBackground, fontSize = 13.sp) },
                            onClick = { js("changeBlock('$tag')"); headingMenu = false },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Hizalama
            Box {
                IconButton(onClick = { alignMenu = true }, modifier = Modifier.size(40.dp)) {
                    val icon = when (currentAlign) {
                        "center"  -> Icons.Filled.FormatAlignCenter
                        "right"   -> Icons.Filled.FormatAlignRight
                        "justify" -> Icons.Filled.FormatAlignJustify
                        else      -> Icons.Filled.FormatAlignLeft
                    }
                    Icon(icon, "Hizalama", tint = OnBackground, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = alignMenu, onDismissRequest = { alignMenu = false }, modifier = Modifier.background(HeftSurface)) {
                    listOf("left" to "Sola", "center" to "Ortala", "right" to "Sağa", "justify" to "İki Yana").forEach { (align, label) ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(
                                when(align) {
                                    "center"  -> Icons.Filled.FormatAlignCenter
                                    "right"   -> Icons.Filled.FormatAlignRight
                                    "justify" -> Icons.Filled.FormatAlignJustify
                                    else      -> Icons.Filled.FormatAlignLeft
                                },
                                align,
                                tint = if (currentAlign == align) Amber else OnBackground,
                                modifier = Modifier.size(16.dp)
                            )},
                            text    = { Text(label, color = if (currentAlign == align) Amber else OnBackground, fontSize = 13.sp) },
                            onClick = { js("setAlign('$align')"); currentAlign = align; alignMenu = false },
                        )
                    }
                }
            }
        }

        // ── WebView ────────────────────────────────────────────
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
                    loadDataWithBaseURL(null, editorHtml, "text/html", "UTF-8", null)
                    webRef = this
                }
            },
            update = { wv ->
                webRef = wv
                if (value.isNotEmpty()) {
                    val escaped = value
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("$", "\\$")
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

private fun buildEditorHtml(bg: String, text: String, placeholder: String, minH: Int) = """
<!DOCTYPE html><html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
html,body { background:$bg; min-height:100%; }
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
#editor:empty::before { content:attr(data-ph); color:$placeholder; pointer-events:none; display:block; }
b,strong { font-weight:bold; }
i,em { font-style:italic; }
u { text-decoration:underline; }
h1 { font-size:1.8em; font-weight:bold; margin:6px 0; }
h2 { font-size:1.4em; font-weight:bold; margin:5px 0; }
h3 { font-size:1.2em; font-weight:bold; margin:4px 0; }
</style>
</head><body>
<div id="editor" contenteditable="true" data-ph="Yazını buraya yaz..."></div>
<script>
var editor = document.getElementById('editor');
var timer = null;

function notify() {
  clearTimeout(timer);
  timer = setTimeout(function(){ Android.onChanged(editor.innerHTML); }, 300);
}
editor.addEventListener('input', notify);

function setContent(html) {
  if (editor.innerHTML !== html) editor.innerHTML = html;
}

function wrapTag(tag) {
  var sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return;
  var range = sel.getRangeAt(0);
  if (range.collapsed) {
    // İmleç konumuna boş tag ekle, içine yaz
    var el = document.createElement(tag);
    el.appendChild(document.createTextNode('\u200b'));
    range.insertNode(el);
    range.setStart(el.firstChild, 1);
    range.setEnd(el.firstChild, 1);
    sel.removeAllRanges();
    sel.addRange(range);
  } else {
    // Seçili metni sar
    var el = document.createElement(tag);
    try { range.surroundContents(el); }
    catch(e) { el.appendChild(range.extractContents()); range.insertNode(el); }
  }
  notify();
}

function changeBlock(tag) {
  var sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return;
  var node = sel.getRangeAt(0).commonAncestorContainer;
  var block = node.nodeType === 3 ? node.parentElement : node;
  while (block && block !== editor && !/^(P|H[1-6]|DIV)${'$'}.test(block.tagName)) {
    block = block.parentElement;
  }
  var newBlock = document.createElement(tag);
  if (block && block !== editor) {
    newBlock.innerHTML = block.innerHTML;
    newBlock.style.textAlign = block.style.textAlign;
    block.replaceWith(newBlock);
  } else {
    newBlock.appendChild(sel.getRangeAt(0).extractContents());
    sel.getRangeAt(0).insertNode(newBlock);
  }
  notify();
}

function setAlign(align) {
  var sel = window.getSelection();
  var node = sel && sel.rangeCount > 0 ? sel.getRangeAt(0).commonAncestorContainer : null;
  var block = node ? (node.nodeType === 3 ? node.parentElement : node) : null;
  while (block && block !== editor && !/^(P|H[1-6]|DIV)${'$'}.test(block.tagName)) {
    block = block.parentElement;
  }
  if (block && block !== editor) {
    block.style.textAlign = align;
  } else {
    // Yeni paragraf oluştur
    var p = document.createElement('p');
    p.style.textAlign = align;
    if (sel && sel.rangeCount > 0) {
      p.appendChild(sel.getRangeAt(0).extractContents());
      sel.getRangeAt(0).insertNode(p);
    }
  }
  notify();
}
</script>
</body></html>
""".trimIndent()
