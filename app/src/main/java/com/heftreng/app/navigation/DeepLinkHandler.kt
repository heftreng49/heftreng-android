package com.heftreng.app.util

import android.content.Intent
import android.net.Uri

/**
 * Web URL'sini veya heftreng:// URI'sini NavHost route string'ine çevirir.
 *
 * Desteklenen URL'ler:
 *   https://heftreng.onrender.com/post/ID         → "post/ID"
 *   https://heftreng.onrender.com/profile/UID     → "profile/UID"
 *   https://heftreng.onrender.com/library/book/ID → "library/book/ID"
 *   heftreng://app/post/ID                        → "post/ID"  (eski custom scheme)
 *
 * Kullanım (MainActivity.onCreate ve onNewIntent içinde):
 *   val route = DeepLinkHandler.resolve(intent)
 *   if (route != null) pendingNavTarget = route
 */
object DeepLinkHandler {

    private const val WEB_HOST = "heftreng.onrender.com"

    /** Intent'ten NavHost route string'i üretir. Tanınmayan link için null. */
    fun resolve(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        return when (uri.scheme) {
            "https", "http" -> resolveWeb(uri)
            "heftreng"      -> resolveCustom(uri)
            else            -> null
        }
    }

    // https://heftreng.onrender.com/post/abc123  →  "post/abc123"
    private fun resolveWeb(uri: Uri): String? {
        if (uri.host != WEB_HOST) return null
        val segments = uri.pathSegments  // ["post", "abc123"]
        if (segments.isEmpty()) return null
        return when (segments[0]) {
            "post"    -> if (segments.size >= 2) "post/${segments[1]}"    else null
            "profile" -> if (segments.size >= 2) "profile/${segments[1]}" else null
            "library" -> resolveLibrary(segments)
            "blog"    -> if (segments.size >= 2) "blog/${segments[1]}"    else "blog"
            "feed"    -> "feed"
            "kurdi"   -> resolveKurdi(segments)
            else      -> null
        }
    }

    private fun resolveLibrary(segments: List<String>): String? {
        if (segments.size < 2) return "library"
        return when (segments[1]) {
            "book"   -> if (segments.size >= 3) "library/book/${segments[2]}"   else "library"
            "author" -> if (segments.size >= 3) "library/author/${segments[2]}" else "library"
            else     -> "library"
        }
    }

    private fun resolveKurdi(segments: List<String>): String? {
        if (segments.size < 2) return "kurdi"
        return when (segments[1]) {
            "lesson"  -> if (segments.size >= 3) "kurdi/lesson/${segments[2]}"  else "kurdi"
            "grammar" -> if (segments.size >= 3) "kurdi/grammar/${segments[2]}" else "kurdi"
            else      -> "kurdi"
        }
    }

    // heftreng://app/post/abc123  →  "post/abc123"  (eski scheme, geriye uyum)
    private fun resolveCustom(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.size >= 2) "${segments[0]}/${segments[1]}" else null
    }
}
