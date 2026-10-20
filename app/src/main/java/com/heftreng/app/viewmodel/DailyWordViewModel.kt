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

    init { loadTodayWords() }

    fun loadTodayWords() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = firestore.collection("daily_word").document(date).get().await()
                if (doc.exists()) {
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
                } else {
                    _words.value = emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.w("DailyWordVM", "loadTodayWords: ${e.message}")
                _words.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
