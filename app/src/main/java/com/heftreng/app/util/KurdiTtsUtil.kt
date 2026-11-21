package com.heftreng.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Kürtçe TTS — iki katmanlı:
 * 1. gTTS (Google Translate endpoint) — ses varsa cache'e yazar, çalar
 * 2. Fallback: Android yerleşik TTS (Türkçe motor, yaklaşık telaffuz)
 */
object KurdiTtsUtil {

    private const val TAG = "KurdiTTS"

    // gTTS için farklı client parametreleri — biri çalışmazsa diğerini dener
    private val GTTS_CLIENTS = listOf("tw-ob", "gtx", "t")

    suspend fun speak(context: Context, word: String) {
        if (word.isBlank()) return
        val spoke = tryGtts(context, word)
        if (!spoke) {
            tryAndroidTts(context, word)
        }
    }

    // ── 1. gTTS ──────────────────────────────────────────────────────────────

    private suspend fun tryGtts(context: Context, word: String): Boolean =
        withContext(Dispatchers.IO) {
            val mp3 = cachedFile(context, word)
            // Cache'te geçerli dosya var mı?
            if (mp3.exists() && mp3.length() > 1024L) {
                return@withContext playFile(mp3)
            }
            // Farklı client param'larıyla dene
            for (client in GTTS_CLIENTS) {
                try {
                    val encoded = URLEncoder.encode(word.trim(), "UTF-8")
                    val url = "https://translate.google.com/translate_tts" +
                        "?ie=UTF-8&tl=ku&client=$client&q=$encoded"
                    val conn = java.net.URL(url).openConnection().apply {
                        connectTimeout = 7000
                        readTimeout    = 10000
                        setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.6099.230 Mobile Safari/537.36"
                        )
                        setRequestProperty("Referer", "https://translate.google.com/")
                        setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
                    }
                    val bytes = conn.getInputStream().use { it.readBytes() }
                    if (bytes.size > 1024) {
                        mp3.writeBytes(bytes)
                        return@withContext playFile(mp3)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "gTTS client=$client hatası: ${e.message}")
                }
            }
            false
        }

    private fun cachedFile(context: Context, word: String): File {
        val dir = File(context.cacheDir, "kurdi_tts").also { it.mkdirs() }
        val safe = word.trim().lowercase()
            .replace(Regex("[^a-züşğıöçêîûéáabcdefghijklmnopqrstuvwxyz0-9_ ]"), "")
            .replace(" ", "_")
            .take(60)
            .ifBlank { word.hashCode().toString() }
        return File(dir, "$safe.mp3")
    }

    private fun playFile(file: File): Boolean {
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { release() }
                start()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer hatası: ${e.message}")
            false
        }
    }

    // ── 2. Android TTS fallback ───────────────────────────────────────────────

    private suspend fun tryAndroidTts(context: Context, word: String) {
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // Kürtçe locale dene, yoksa Türkçe
                    val kuLocale = Locale("ku")
                    val result = tts?.isLanguageAvailable(kuLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (result >= TextToSpeech.LANG_AVAILABLE) {
                        tts?.language = kuLocale
                    } else {
                        tts?.language = Locale("tr") // yaklaşık telaffuz
                    }
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { tts?.shutdown(); if (cont.isActive) cont.resume(Unit) }
                        override fun onError(id: String?) { tts?.shutdown(); if (cont.isActive) cont.resume(Unit) }
                    })
                    tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "ku_tts")
                } else {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            cont.invokeOnCancellation { tts?.shutdown() }
        }
    }
}
