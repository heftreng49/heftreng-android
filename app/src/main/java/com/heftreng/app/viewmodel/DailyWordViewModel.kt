package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DailyWordViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    data class WordItem(
        val word      : String = "",
        val meaningTr : String = "",
        val meaningKu : String = "",
        val exampleKu : String = "",
    )

    private val _words   = MutableStateFlow<List<WordItem>>(emptyList())
    val words = _words.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    // Gösterilen tarih (UI'da paylaş kartında kullanılır)
    private val _displayDate = MutableStateFlow("")
    val displayDate = _displayDate.asStateFlow()

    init { loadWords() }

    /**
     * Önce bugünün dokümanını dene; yoksa koleksiyondaki en son dokümanı getir.
     * Bu sayede admin kelimeyi dün kaydettiyse ekran boş görünmez.
     */
    fun loadWords(requestedDate: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val dateToTry = requestedDate ?: today

                // 1. İstenen tarihi dene
                var doc = firestore.collection("daily_word").document(dateToTry).get().await()

                // 2. Bulunamazsa — koleksiyondaki tüm dokümanları getir,
                //    ID (yyyy-MM-dd) sıralamasıyla en büyüğü = en yeni tarih
                if (!doc.exists()) {
                    val all = firestore.collection("daily_word")
                        .get()
                        .await()
                    // Doküman ID'leri tarih formatında (yyyy-MM-dd) — string sort yeterli
                    val latest = all.documents
                        .filter { it.id.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                        .maxByOrNull { it.id }
                    doc = latest ?: run {
                        _words.value = emptyList()
                        _loading.value = false
                        return@launch
                    }
                    android.util.Log.d("DailyWordVM", "Bugün yok, fallback: ${doc.id}")
                }

                _displayDate.value = doc.id  // doküman ID = tarih (yyyy-MM-dd)

                @Suppress("UNCHECKED_CAST")
                val wordsRaw = doc.get("words") as? List<Map<String, String>>
                if (wordsRaw != null && wordsRaw.isNotEmpty()) {
                    _words.value = wordsRaw.map { m ->
                        WordItem(
                            word      = m["word"]       ?: "",
                            meaningTr = m["meaning_tr"] ?: "",
                            meaningKu = m["meaning_ku"] ?: "",
                            exampleKu = m["example_ku"] ?: "",
                        )
                    }
                } else {
                    // Eski tek-kelime formatı — geriye dönük uyumluluk
                    val single = WordItem(
                        word      = doc.getString("word")       ?: "",
                        meaningTr = doc.getString("meaning_tr") ?: "",
                        meaningKu = doc.getString("meaning_ku") ?: "",
                        exampleKu = doc.getString("example_ku") ?: "",
                    )
                    _words.value = if (single.word.isNotBlank()) listOf(single) else emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.w("DailyWordVM", "loadWords: ${e.message}")
                _words.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    // Eski çağrılarla uyumluluk için alias
    fun loadTodayWords() = loadWords()
}
