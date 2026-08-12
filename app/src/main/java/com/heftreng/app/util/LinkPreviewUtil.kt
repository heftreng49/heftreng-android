package com.heftreng.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.regex.Pattern

data class LinkPreview(
    val url       : String = "",
    val title     : String = "",
    val desc      : String = "",
    val image     : String = "",
    val type      : String = "", // "youtube" | "instagram" | "link"
    val youtubeId : String = "",
)

object LinkPreviewUtil {

    private val URL_REGEX = Pattern.compile(
        """(https?://[\w\-._~:/?#\[\]@!$&'()*+,;=%]+)""",
        Pattern.CASE_INSENSITIVE
    )

    private val YT_REGEX = Pattern.compile(
        """(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/)([\w\-]{11})""",
        Pattern.CASE_INSENSITIVE
    )

    private val IG_REGEX = Pattern.compile(
        """instagram\.com/(p|reel|tv)/([\w\-]+)""",
        Pattern.CASE_INSENSITIVE
    )

    /** Metindeki ilk URL'i tespit eder */
    fun extractUrl(text: String): String? {
        val m = URL_REGEX.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    /** URL tipini belirle */
    fun detectType(url: String): String = when {
        YT_REGEX.matcher(url).find() -> "youtube"
        IG_REGEX.matcher(url).find() -> "instagram"
        url.isNotBlank()             -> "link"
        else                         -> ""
    }

    /** YouTube video ID'sini çıkar */
    fun extractYoutubeId(url: String): String {
        val m = YT_REGEX.matcher(url)
        return if (m.find()) m.group(1) ?: "" else ""
    }

    /** OG meta taglarını çek — ağ üzerinde çalışır */
    suspend fun fetchPreview(url: String): LinkPreview = withContext(Dispatchers.IO) {
        try {
            val type = detectType(url)
            val ytId = if (type == "youtube") extractYoutubeId(url) else ""

            // YouTube için thumbnail API'si — OG fetch gerekmez
            if (type == "youtube" && ytId.isNotBlank()) {
                return@withContext LinkPreview(
                    url       = url,
                    title     = "",  // YouTube OG'dan çekebiliriz ama thumbnail yeterli
                    desc      = "",
                    image     = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                    type      = "youtube",
                    youtubeId = ytId,
                )
            }

            // Instagram ve genel linkler için OG scraping
            val conn = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout    = 5000
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
            }
            val html = conn.getInputStream().bufferedReader().use { it.read(5000) }

            fun ogTag(prop: String): String {
                val pattern = Pattern.compile(
                    """<meta[^>]+(?:property|name)=["']og:$prop["'][^>]+content=["'](.*?)["'""",
                    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )
                val m2 = pattern.matcher(html)
                return if (m2.find()) m2.group(1)?.trim() ?: "" else ""
            }

            val title = ogTag("title").ifBlank {
                val t = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val tm = t.matcher(html)
                if (tm.find()) tm.group(1)?.trim() ?: "" else ""
            }

            LinkPreview(
                url   = url,
                title = title.take(100),
                desc  = ogTag("description").take(200),
                image = ogTag("image"),
                type  = type,
            )
        } catch (e: Exception) {
            android.util.Log.w("LinkPreview", "Fetch hatası: ${e.message}")
            LinkPreview(url = url, type = detectType(url), youtubeId = extractYoutubeId(url))
        }
    }
}
