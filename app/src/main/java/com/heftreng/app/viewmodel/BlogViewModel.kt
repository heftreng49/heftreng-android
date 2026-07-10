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
    val content     : String,       // tam HTML içerik (hf-author-card striplendi)
    val summary     : String,       // ilk 180 karakter, düz metin
    val published   : String,       // ISO 8601
    val url         : String,
    val authorName  : String,
    val authorPhoto : String,
    val authorUid   : String,       // Firestore users/{uid} — profile navigasyonu için
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
            // Cache'de varsa hemen göster (liste fetchBodies=true ile geldiğinden içerik dolu)
            val cached = _state.value.posts.find { it.id == postId }
            if (cached != null && cached.content.isNotBlank()) {
                _detail.value = cached
                _detailLoading.value = false
                return@launch
            }
            // Cache yoksa veya içerik boşsa API'den çek
            if (cached != null) _detail.value = cached
            try {
                val apiUrl = "https://www.googleapis.com/blogger/v3/blogs/$BLOG_ID/posts/$postId?key=$API_KEY&fetchBodies=true"
                val json   = httpGet(apiUrl)
                _detail.value = parsePost(JSONObject(json))
            } catch (e: Exception) {
                if (cached != null) _detail.value = cached
            }
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
        val rawContent = obj.optString("content")
        val labelArr   = obj.optJSONArray("labels")
        val labels     = (0 until (labelArr?.length() ?: 0)).map { labelArr!!.getString(it) }

        // HTML içeriğinden hf-author-card bloğunu çıkar — bu blok
        // publishToBlogger tarafından ekleniyor (bkz. Cloud Function),
        // Android tarafında android.text.Html.fromHtml() tarafından
        // düz metin olarak bozuk render ediliyordu. Gerçek yazar bilgisini
        // (isim, foto, uid) bu bloktan çekip native Kotlin kartına aktarıyoruz.
        val (content, realAuthorName, realAuthorPhoto, realAuthorUid) =
            extractAuthorCard(rawContent)

        // Blogger'ın kendi "author" alanı site sahibini döndürür (Heft Reng
        // hesabı). Gerçek yazar karttan çıkarılamazsa bu alana fallback.
        return BlogPost(
            id          = obj.optString("id"),
            title       = obj.optString("title"),
            content     = content,
            summary     = htmlToPlainText(content).take(180).trimEnd() +
                          if (content.length > 180) "…" else "",
            published   = obj.optString("published"),
            url         = obj.optString("url"),
            authorName  = realAuthorName.ifBlank { author?.optString("displayName") ?: "" },
            authorPhoto = realAuthorPhoto.ifBlank { authorImg?.optString("url") ?: "" },
            authorUid   = realAuthorUid,
            thumbnail   = extractFirstImage(rawContent), // kapak görseli orijinal içerikten
            labels      = labels,
        )
    }

    /** hf-author-card bloğunu içerikten ayırır. Blok varsa:
     *  - içerikten striplenmiş HTML döner
     *  - isim, foto URL, uid (data attribute olarak gömülü) çıkarılır
     *  Blok yoksa orijinal içerik + boş yazar bilgileri döner. */
    private data class AuthorCardResult(
        val content    : String,
        val authorName : String,
        val authorPhoto: String,
        val authorUid  : String,
    )

    private fun extractAuthorCard(html: String): AuthorCardResult {
        // hf-author-card div'inin başlangıcını bul
        val startTag  = Regex("""<div[^>]*class=["']hf-author-card["'][^>]*>""", RegexOption.IGNORE_CASE)
        val startMatch = startTag.find(html) ?: return AuthorCardResult(html, "", "", "")
        val blockStart = startMatch.range.first
        val innerStart = startMatch.range.last + 1

        // İç içe <div> sayacıyla gerçek kapanış </div>'ini bul
        var depth  = 1
        var i      = innerStart
        val len    = html.length
        val openRx  = Regex("""<div""",  RegexOption.IGNORE_CASE)
        val closeRx = Regex("""</div>""", RegexOption.IGNORE_CASE)

        while (i < len && depth > 0) {
            val nextOpen  = openRx.find(html, i)
            val nextClose = closeRx.find(html, i)
            when {
                nextClose == null -> break
                nextOpen != null && nextOpen.range.first < nextClose.range.first -> {
                    depth++; i = nextOpen.range.last + 1
                }
                else -> {
                    depth--; i = nextClose.range.last + 1
                }
            }
        }

        val blockEnd = i  // </div>'in hemen sonrası
        val cardHtml = html.substring(blockStart, blockEnd)

        // İsim: <span> içindeki metin (font-weight:600 ya da font-weight:700)
        val name = Regex("""<span[^>]*font-weight:\s*[67]00[^>]*>([^<]+)</span>""", RegexOption.IGNORE_CASE)
            .find(cardHtml)?.groupValues?.getOrNull(1)?.trim() ?: ""

        // Foto: <img src="...">
        val photo = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(cardHtml)?.groupValues?.getOrNull(1) ?: ""

        // UID: data-uid attribute
        val uid = Regex("""data-uid=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(cardHtml)?.groupValues?.getOrNull(1) ?: ""

        // Kartı ve ardından gelen boşlukları içerikten çıkar
        val strippedContent = (html.substring(0, blockStart) +
                html.substring(blockEnd)).trimStart()
        return AuthorCardResult(strippedContent, name, photo, uid)
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
