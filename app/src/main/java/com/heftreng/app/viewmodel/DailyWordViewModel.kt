package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
        val exampleTr : String = "",
        val emoji     : String = "",
    )

    private val _words   = MutableStateFlow<List<WordItem>>(emptyList())
    val words = _words.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    // true = Firestore'da hiç belge yok (admin henüz eklemedi)
    // false = yükleniyor / yüklendi / ağ hatası (kelimeler gösterilmeye devam)
    private val _notFound = MutableStateFlow(false)
    val notFound = _notFound.asStateFlow()

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

                // 2. Bulunamazsa — en yeni dokümanı getir. Doküman ID'leri tarih
                //    (yyyy-MM-dd) olduğundan ID'ye göre azalan sıralama = en yeni.
                //    Önceden TÜM koleksiyon okunuyordu (her gün büyüyen, her açılışta
                //    N okuma); artık en fazla 7 doküman okunuyor. 7: ID'si tarih
                //    formatında olmayan yanlışlıkla eklenmiş dokümanlar varsa
                //    aşağıdaki regex filtresi sonrası hâlâ geçerli biri kalsın diye.
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
                        // Firestore'da gerçekten hiç belge yok
                        _notFound.value = true
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
                            word      = m["word"]        ?: "",
                            meaningTr = m["meaning_tr"]  ?: "",
                            meaningKu = m["meaning_ku"]  ?: "",
                            exampleKu = m["example_ku"]  ?: "",
                            exampleTr = m["example_tr"]  ?: "",
                            emoji     = m["emoji"]       ?: "",
                        )
                    }
                } else {
                    // Eski tek-kelime formatı — geriye dönük uyumluluk
                    val single = WordItem(
                        word      = doc.getString("word")       ?: "",
                        meaningTr = doc.getString("meaning_tr") ?: "",
                        meaningKu = doc.getString("meaning_ku") ?: "",
                        exampleKu = doc.getString("example_ku") ?: "",
                        exampleTr = doc.getString("example_tr") ?: "",
                        emoji     = doc.getString("emoji")      ?: "",
                    )
                    _words.value = if (single.word.isNotBlank()) listOf(single) else emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.w("DailyWordVM", "loadWords: ${e.message}")
                // Ağ hatasında mevcut kelimeleri koru — ekranı boşaltma.
                // Eğer daha önce yüklenmiş kelimeler varsa onlar görünmeye devam eder.
                // _notFound false kalır → "kelime eklenmedi" mesajı çıkmaz.
            } finally {
                _loading.value = false
            }
        }
    }

    // Eski çağrılarla uyumluluk için alias
    fun loadTodayWords() = loadWords()
}
