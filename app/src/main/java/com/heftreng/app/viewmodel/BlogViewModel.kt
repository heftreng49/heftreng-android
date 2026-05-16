package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

// ── Veri modelleri ────────────────────────────────────────────────────────────
data class BlogPost(
    val id          : String,
    val title       : String,
    val content     : String,       // tam HTML içerik
    val summary     : String,       // ilk 180 karakter, düz metin
    val published   : String,       // ISO 8601
    val url         : String,
    val authorName  : String,
    val authorPhoto : String,
    val thumbnail   : String,       // ilk <img> src
    val labels      : List<String>,
)

data class BlogState(
    val posts      : List<BlogPost> = emptyList(),
    val loading    : Boolean        = true,
    val error      : String?        = null,
    val nextToken  : String?        = null,
    val hasMore    : Boolean        = true,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class BlogViewModel @Inject constructor() : ViewModel() {

    private val BLOG_ID = "6362476808834153672"
    private val API_KEY = "AIzaSyCu2ZmHntoZ9txhSFLzafy9JEvgm6LPZLI"
    private val PAGE_SIZE = 10

    private val _state   = MutableStateFlow(BlogState())
    val state            = _state.asStateFlow()

    private val _detail  = MutableStateFlow<BlogPost?>(null)
    val detail           = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading           = _detailLoading.asStateFlow()

    // Etiket filtresi
    private var activeLabel: String? = null

    init { loadPosts(refresh = true) }

    fun loadPosts(refresh: Boolean = false, label: String? = activeLabel) {
        if (!refresh && (_state.value.loading || !_state.value.hasMore)) return
        activeLabel = label
        viewModelScope.launch {
            _state.value = if (refresh)
                BlogState(loading = true)
            else
                _state.value.copy(loading = true)

            try {
                val token = if (refresh) null else _state.value.nextToken
                val (posts, nextToken) = fetchPosts(token, label)
                val combined = if (refresh) posts else _state.value.posts + posts
                _state.value = BlogState(
                    posts     = combined,
                    loading   = false,
                    nextToken = nextToken,
                    hasMore   = nextToken != null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun loadMore() = loadPosts(refresh = false)

    fun filterByLabel(label: String?) {
        if (activeLabel == label) return
        loadPosts(refresh = true, label = label)
    }

    fun loadPostDetail(postId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detail.value = null
            try {
                val url = buildString {
                    append("https://www.googleapis.com/blogger/v3/blogs/$BLOG_ID/posts/$postId")
                    append("?key=$API_KEY&fetchBody=true&fetchImages=true")
                }
                val json = httpGet(url)
                _detail.value = parsePost(JSONObject(json))
            } catch (_: Exception) {}
            _detailLoading.value = false
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────
    private suspend fun fetchPosts(
        pageToken : String?,
        label     : String?,
    ): Pair<List<BlogPost>, String?> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://www.googleapis.com/blogger/v3/blogs/$BLOG_ID/posts")
            append("?key=$API_KEY&maxResults=$PAGE_SIZE&fetchBodies=true&fetchImages=true")
            if (pageToken != null) append("&pageToken=$pageToken")
            if (label != null) append("&labels=${java.net.URLEncoder.encode(label, "UTF-8")}")
        }
        val json      = httpGet(url)
        val root      = JSONObject(json)
        val items     = root.optJSONArray("items")
        val nextToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
        val posts     = (0 until (items?.length() ?: 0)).map { i ->
            parsePost(items!!.getJSONObject(i))
        }
        posts to nextToken
    }

    private fun parsePost(obj: JSONObject): BlogPost {
        val author     = obj.optJSONObject("author")
        val authorImg  = author?.optJSONObject("image")
        val content    = obj.optString("content")
        val labelArr   = obj.optJSONArray("labels")
        val labels     = (0 until (labelArr?.length() ?: 0)).map { labelArr!!.getString(it) }
        return BlogPost(
            id          = obj.optString("id"),
            title       = obj.optString("title"),
            content     = content,
            summary     = htmlToPlainText(content).take(180).trimEnd() + if (content.length > 180) "…" else "",
            published   = obj.optString("published"),
            url         = obj.optString("url"),
            authorName  = author?.optString("displayName") ?: "",
            authorPhoto = authorImg?.optString("url") ?: "",
            thumbnail   = extractFirstImage(content),
            labels      = labels,
        )
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout    = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────
    private fun extractFirstImage(html: String): String {
        val m = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        return m?.groupValues?.getOrNull(1) ?: ""
    }

    private fun htmlToPlainText(html: String): String =
        html
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&[a-z]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
