package com.heftreng.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Gönderi oluşturma sırasında yazılan metni geçici olarak saklar.
 *
 * Sorun: FeedScreen'deki `inlineText` gibi alanlar Compose'un `remember`
 * state'inde tutuluyordu — kullanıcı yazarken bildirim gelip uygulama arka
 * planda öldürülürse (process death) yazdığı metin tamamen kayboluyordu.
 *
 * Çözüm: Metin, debounce'lu şekilde SharedPreferences'a yazılır (küçük ve
 * geçici bir veri olduğu için Room/Firestore gerekmiyor). Kullanıcı gönderiyi
 * paylaştığında ya da taslağı bilerek temizlediğinde silinir.
 *
 * NOT: Taslak sadece TEK bir "aktif" taslağı tutar (kullanıcı aynı anda tek
 * bir gönderi yazabiliyor); birden fazla taslak desteklemez.
 */
object DraftManager {
    private const val PREFS_NAME = "post_draft_prefs"
    private const val KEY_TEXT   = "draft_text"
    private const val KEY_TITLE  = "draft_title"
    private const val KEY_SAVED_AT = "draft_saved_at"

    // Bir günden eski taslaklar otomatik göz ardı edilir — kullanıcı haftalar
    // sonra tekrar "gönderi yaz" ekranını açtığında eski/alakasız bir metin
    // görüp şaşırmasın.
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, text: String, title: String = "") {
        // Boş taslak kaydetmeye gerek yok — zaten okuma tarafı boşu taslak saymıyor.
        prefs(context).edit()
            .putString(KEY_TEXT, text)
            .putString(KEY_TITLE, title)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    /** @return (text, title) çifti, geçerli bir taslak yoksa null. */
    fun load(context: Context): Pair<String, String>? {
        val p = prefs(context)
        val text = p.getString(KEY_TEXT, "") ?: ""
        val savedAt = p.getLong(KEY_SAVED_AT, 0L)
        if (text.isBlank()) return null
        if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) {
            clear(context)
            return null
        }
        val title = p.getString(KEY_TITLE, "") ?: ""
        return text to title
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
