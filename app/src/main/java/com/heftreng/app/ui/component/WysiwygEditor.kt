package com.heftreng.app.ui.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.heftreng.app.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WysiwygEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    minHeightDp: Int = 300,
) {
    val bgHex     = colorToHex(SurfaceVar)
    val toolbarBg = colorToHex(HeftSurface)
    val textHex   = colorToHex(OnBackground)
    val phHex     = colorToHex(Muted)
    val iconHex   = colorToHex(OnBackground)
    val accentHex = colorToHex(Amber)
    val divHex    = colorToHex(Divider)

    var webRef      by remember { mutableStateOf<WebView?>(null) }
    var initialized by remember { mutableStateOf(false) }

    val html = remember(bgHex, toolbarBg, textHex, phHex, iconHex, accentHex, divHex, minHeightDp) {
        buildQuillHtml(bgHex, toolbarBg, textHex, phHex, iconHex, accentHex, divHex, "${minHeightDp}px")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
            .background(SurfaceVar, RoundedCornerShape(12.dp))
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeightDp.dp),
            factory  = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled  = true
                    webChromeClient = WebChromeClient()
                    webViewClient   = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (!initialized && value.isNotEmpty()) {
                                val escaped = value
                                    .replace("\\", "\\\\")
                                    .replace("`", "\\`")
                                view.evaluateJavascript("setContent(`$escaped`)", null)
                                initialized = true
                            }
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onChanged(html: String) { onChange(html) }
                    }, "Android")
                    loadDataWithBaseURL("https://cdn.quilljs.com", html, "text/html", "UTF-8", null)
                    webRef = this
                }
            },
            update = { wv -> webRef = wv }
        )
    }
}

private fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
    val r = (color.red   * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue  * 255).toInt()
    return "#%02x%02x%02x".format(r, g, b)
}

private fun buildQuillHtml(
    bg: String, toolbarBg: String, text: String, ph: String,
    icon: String, accent: String, div: String, minH: String,
) = """
<!DOCTYPE html><html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link href="https://cdn.quilljs.com/1.3.7/quill.snow.css" rel="stylesheet">
<style>
*{margin:0;padding:0;box-sizing:border-box;}
html,body{background:$bg;height:100%;font-family:sans-serif;display:flex;flex-direction:column;}
.ql-container.ql-snow{
  border:none;background:$bg;flex:1;
  display:flex;flex-direction:column;
}
.ql-editor{
  flex:1;min-height:$minH;color:$text;
  font-size:15px;line-height:1.7;padding:14px;
}
.ql-editor.ql-blank::before{color:$ph;font-style:normal;left:14px;}
.ql-toolbar.ql-snow{
  background:$toolbarBg;
  border:none;
  border-top:1px solid $div;
  padding:8px 4px;
  position:sticky;bottom:0;z-index:10;
}
.ql-toolbar .ql-stroke{stroke:$icon!important;}
.ql-toolbar .ql-fill{fill:$icon!important;}
.ql-toolbar .ql-picker-label{color:$icon!important;}
.ql-toolbar .ql-picker-options{background:$toolbarBg!important;border:1px solid $div!important;}
.ql-toolbar .ql-picker-item{color:$icon!important;}
.ql-toolbar button:hover .ql-stroke,.ql-toolbar button.ql-active .ql-stroke{stroke:$accent!important;}
.ql-toolbar button:hover .ql-fill,.ql-toolbar button.ql-active .ql-fill{fill:$accent!important;}
.ql-toolbar .ql-picker-label:hover,.ql-toolbar .ql-picker-item:hover,.ql-toolbar .ql-picker-item.ql-selected{color:$accent!important;}
</style>
</head><body>
<div id="editor"></div>
<script src="https://cdn.quilljs.com/1.3.7/quill.min.js"></script>
<script>
var quill = new Quill('#editor', {
  theme:'snow',
  placeholder:'Yazını buraya yaz...',
  modules:{toolbar:[
    ['bold','italic','underline'],
    [{header:[1,2,3,false]}],
    [{align:[]}]
  ]}
});
var timer=null;
quill.on('text-change',function(){
  clearTimeout(timer);
  timer=setTimeout(function(){
    var h=quill.root.innerHTML;
    if(h==='<p><br></p>')h='';
    Android.onChanged(h);
  },300);
});
function setContent(html){
  if(!html||html===''){quill.setContents([]);}
  else{quill.clipboard.dangerouslyPasteHTML(html);}
}
</script>
</body></html>
""".trimIndent()
