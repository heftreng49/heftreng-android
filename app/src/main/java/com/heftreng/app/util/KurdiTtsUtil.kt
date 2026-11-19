package com.heftreng.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/**
 * Kürtçe TTS — Google Translate ses endpoint'i (gTTS benzeri, belgesiz API).
 * - İlk seferinde cihaz cache dizinine MP3 olarak kaydedilir
 * - Sonraki çağrılarda ağ isteği atmadan cache'ten çalınır
 * - Kürmanci (Kurmancî) için tl=ku parametresi
 */
object KurdiTtsUtil {

    private const val TAG = "KurdiTTS"

    /**
     * Verilen Kürtçe kelimeyi sesli okur.
     * IO thread'de çalışır, ana thread'e geçmez — composable'dan
     * coroutineScope.launch(Dispatchers.IO) { } ile çağır.
     */
    suspend fun speak(context: Context, word: String) = withContext(Dispatchers.IO) {
        if (word.isBlank()) return@withContext
        try {
            val mp3 = cachedFile(context, word)
            if (!mp3.exists() || mp3.length() == 0L) {
                download(word, mp3)
            }
            if (mp3.exists() && mp3.length() > 0L) {
                playFile(mp3)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS hatası: ${e.message}")
        }
    }

    // ── Özel ─────────────────────────────────────────────────────────────────

    private fun cachedFile(context: Context, word: String): File {
        val dir = File(context.cacheDir, "kurdi_tts").also { it.mkdirs() }
        val safe = word.trim().lowercase()
            .replace(Regex("[^a-züşğıöçêîûéá ]"), "")
            .replace(" ", "_")
            .take(60)
        return File(dir, "$safe.mp3")
    }

    private fun download(word: String, dest: File) {
        val encoded = URLEncoder.encode(word.trim(), "UTF-8")
        val url = "https://translate.google.com/translate_tts" +
            "?ie=UTF-8&tl=ku&client=tw-ob&q=$encoded"
        val conn = java.net.URL(url).openConnection().apply {
            connectTimeout = 6000
            readTimeout    = 8000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.6099.230 Mobile Safari/537.36"
            )
            setRequestProperty("Referer", "https://translate.google.com/")
        }
        conn.getInputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun playFile(file: File) {
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener { release() }
        }
        mp.start()
    }
}
