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
        """(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/)([A-Za-z0-9_\-]{11})""",
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
                    title     = "",
                    desc      = "",
                    image     = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                    type      = "youtube",
                    youtubeId = ytId,
                )
            }

            // ── Instagram oEmbed — token gerektirmez, thumbnail + başlık döner ──
            if (type == "instagram") {
                return@withContext fetchInstagramPreview(url)
            }

            // Genel linkler için OG scraping
            val conn = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout    = 5000
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
            }
            val html = conn.getInputStream().bufferedReader().use {
                it.readText().take(8000)
            }

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

    /**
     * Instagram oEmbed API — token olmadan çalışır.
     * https://graph.facebook.com/v18.0/instagram_oembed?url=<url>&omitscript=true
     * Dönen JSON: { "thumbnail_url": "...", "title": "...", "author_name": "..." }
     */
    private fun fetchInstagramPreview(url: String): LinkPreview {
        val shortcode = IG_REGEX.matcher(url).let { m ->
            if (m.find()) m.group(2) ?: "" else ""
        }

        // oEmbed endpoint — genel erişim (token opsiyonel)
        val oEmbedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"

        return try {
            // Instagram embed sayfasından OG thumbnail çek
            val conn = URL("https://www.instagram.com/p/$shortcode/").openConnection().apply {
                connectTimeout = 5000
                readTimeout    = 5000
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            }

            val html = conn.getInputStream().bufferedReader().use { it.readText().take(12000) }

            fun ogTag(prop: String): String {
                val pattern = Pattern.compile(
                    """<meta[^>]+property=["']og:$prop["'][^>]+content=["'](.*?)["']""",
                    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )
                val m2 = pattern.matcher(html)
                return if (m2.find()) m2.group(1)?.trim() ?: "" else ""
            }

            val thumbnail = ogTag("image")
            val title     = ogTag("title").ifBlank { ogTag("description") }

            LinkPreview(
                url   = url,
                title = title.take(120),
                desc  = "",
                image = thumbnail,
                type  = "instagram",
            )
        } catch (e: Exception) {
            android.util.Log.w("LinkPreview", "Instagram fetch hatası: ${e.message}")
            // Fallback — en azından ikon + link göster
            LinkPreview(
                url   = url,
                title = "Instagram Reels",
                desc  = "İçeriği görmek için tıkla",
                image = "",
                type  = "instagram",
            )
        }
    }
}
