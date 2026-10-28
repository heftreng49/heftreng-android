package com.heftreng.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DailyWordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    data class WordItem(
        val word      : String = "",
        val meaningTr : String = "",
        val meaningKu : String = "",
        val exampleKu : String = "",
        val exampleTr : String = "",
        val emoji     : String = "",
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hf_daily_word_cache", Context.MODE_PRIVATE)

    private val _words   = MutableStateFlow<List<WordItem>>(emptyList())
    val words = _words.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    // true = Firestore'da hiç belge yok (admin henüz eklemedi)
    private val _notFound = MutableStateFlow(false)
    val notFound = _notFound.asStateFlow()

    // Gösterilen tarih (UI'da paylaş kartında kullanılır)
    private val _displayDate = MutableStateFlow("")
    val displayDate = _displayDate.asStateFlow()

    init {
        // Uygulama açılışında önce cache'den göster — ağ gecikmesinde boş ekran olmaz
        loadFromCache()
        loadWords()
    }

    /**
     * Önce bugünün dokümanını dene; yoksa koleksiyondaki en son dokümanı getir.
     * Başarılı sonucu cache'e yaz — sonraki açılışlarda anında gösterilir.
     */
    fun loadWords(requestedDate: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val dateToTry = requestedDate ?: today

                // 1. İstenen tarihi dene
                var doc = firestore.collection("daily_word").document(dateToTry).get().await()

                // 2. Bulunamazsa — en yeni dokümanı getir
                if (!doc.exists()) {
                    val all = firestore.collection("daily_word")
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(7)
                        .get()
                        .await()
                    val latest = all.documents
                        .filter { it.id.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                        .maxByOrNull { it.id }
                    doc = latest ?: run {
                        // Firestore'da gerçekten hiç belge yok — cache varsa koru
                        if (_words.value.isEmpty()) _notFound.value = true
                        _loading.value = false
                        return@launch
                    }
                    android.util.Log.d("DailyWordVM", "Bugün yok, fallback: ${doc.id}")
                }

                _displayDate.value = doc.id

                @Suppress("UNCHECKED_CAST")
                val wordsRaw = doc.get("words") as? List<Map<String, String>>
                val items: List<WordItem> = if (wordsRaw != null && wordsRaw.isNotEmpty()) {
                    wordsRaw.map { m ->
                        WordItem(
                            word      = m["word"]        ?: "",
                            meaningTr = m["meaning_tr"]  ?: "",
                            meaningKu = m["meaning_ku"]  ?: "",
                            exampleKu = m["example_ku"]  ?: "",
                            exampleTr = m["example_tr"]  ?: "",
                            emoji     = m["emoji"]       ?: "",
                        )
                    }
                } else {
                    // Eski tek-kelime formatı
                    val single = WordItem(
                        word      = doc.getString("word")       ?: "",
                        meaningTr = doc.getString("meaning_tr") ?: "",
                        meaningKu = doc.getString("meaning_ku") ?: "",
                        exampleKu = doc.getString("example_ku") ?: "",
                        exampleTr = doc.getString("example_tr") ?: "",
                        emoji     = doc.getString("emoji")      ?: "",
                    )
                    if (single.word.isNotBlank()) listOf(single) else emptyList()
                }

                if (items.isNotEmpty()) {
                    _words.value = items
                    _notFound.value = false
                    // Cache'e yaz — tarih + kelimeler birlikte
                    saveToCache(doc.id, items)
                }

            } catch (e: Exception) {
                android.util.Log.w("DailyWordVM", "loadWords: ${e.message}")
                // Ağ hatasında cache'den yüklenenler zaten gösteriliyor — state'i bozma
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Cache yardımcıları ────────────────────────────────────────────────────

    /** SharedPreferences'taki son başarılı listeyi state'e yükler. */
    private fun loadFromCache() {
        val date  = prefs.getString("date", null)  ?: return
        val json  = prefs.getString("words", null) ?: return
        try {
            val arr   = JSONArray(json)
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WordItem(
                    word      = o.optString("word"),
                    meaningTr = o.optString("meaningTr"),
                    meaningKu = o.optString("meaningKu"),
                    exampleKu = o.optString("exampleKu"),
                    exampleTr = o.optString("exampleTr"),
                    emoji     = o.optString("emoji"),
                )
            }
            if (items.isNotEmpty()) {
                _words.value    = items
                _displayDate.value = date
                android.util.Log.d("DailyWordVM", "Cache'den yüklendi: $date (${items.size} kelime)")
            }
        } catch (_: Exception) {}
    }

    /** Başarılı Firestore sonucunu SharedPreferences'a yazar. */
    private fun saveToCache(date: String, items: List<WordItem>) {
        try {
            val arr = JSONArray()
            items.forEach { w ->
                arr.put(JSONObject().apply {
                    put("word",      w.word)
                    put("meaningTr", w.meaningTr)
                    put("meaningKu", w.meaningKu)
                    put("exampleKu", w.exampleKu)
                    put("exampleTr", w.exampleTr)
                    put("emoji",     w.emoji)
                })
            }
            prefs.edit()
                .putString("date",  date)
                .putString("words", arr.toString())
                .apply()
        } catch (_: Exception) {}
    }

    // Eski çağrılarla uyumluluk için alias
    fun loadTodayWords() = loadWords()
}
