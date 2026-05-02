package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.KurdiLesson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Tema koleksiyonları:
// kf_lessons     — ders listesi
// kf_units       — ünite grupları
// kf_vocab       — kelime listesi
// kf_words       — kelimeler
// kf_exercises   — egzersizler
// kf_sentences   — cümleler
// kf_cats        — kategoriler
// kf_reports     — raporlar
// users/{uid}/kf_progress — tamamlanan dersler

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _lessons      = MutableStateFlow<List<KurdiLesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _xp           = MutableStateFlow(0)
    val xp = _xp.asStateFlow()

    private val _streak       = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _level        = MutableStateFlow(1)
    val level = _level.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            // Önce sample göster, kullanıcı boş ekran görmesin
            if (_lessons.value.isEmpty()) _lessons.value = sampleLessons
            _loading.value = true

            try {
                // Kullanıcı XP/streak/level
                // Tema: users dokümanında "xp","streak","level" alanları
                // Eski web verisi fallback: "kf_xp","kf_streak" — her ikisini de oku
                if (uid.isNotEmpty()) {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    // XML: kf_xp alanı, Android: xp alanı — ikisinin büyüğünü al
                    val xpVal   = maxOf(
                        (userDoc.getLong("xp")    ?: 0).toInt(),
                        (userDoc.getLong("kf_xp") ?: 0).toInt(),
                    )
                    _xp.value     = xpVal
                    _streak.value = ((userDoc.getLong("streak") ?: 0)
                        .coerceAtLeast(userDoc.getLong("kf_streak") ?: 0)).toInt()
                    _level.value  = (userDoc.getLong("level") ?: 1).toInt().coerceAtLeast((xpVal / 100) + 1)
                }

                // Tamamlanan dersler — iki kaynaktan topla:
                // 1) users/{uid}.kf_done array (XML temasıyla uyumlu)
                // 2) users/{uid}/kf_progress/{id} subcollection (Android)
                val completedIds = if (uid.isNotEmpty()) {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    // kf_done array — XML teması bu alanı kullanıyor
                    val kfDoneArray = (userDoc.get("kf_done") as? List<*>)
                        ?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    // kf_progress subcollection — Android tarafı
                    val kfProgressIds = try {
                        firestore.collection("users").document(uid)
                            .collection("kf_progress").get().await()
                            .documents.map { it.id }.toSet()
                    } catch (_: Exception) { emptySet() }
                    kfDoneArray + kfProgressIds
                } else emptySet()

                // Ders listesi — tema: kf_lessons koleksiyonu
                val snap = firestore.collection("kf_lessons")
                    .limit(100).get().await()

                _lessons.value = if (snap.documents.isNotEmpty()) {
                    snap.documents
                        .mapNotNull { doc ->
                            val d = doc.data ?: return@mapNotNull null
                            KurdiLesson(
                                id        = doc.id,
                                title     = d["title"]    as? String ?: "",
                                subtitle  = (d["subtitle"] as? String)?.takeIf { it.isNotBlank() }
                                    ?: d["desc"] as? String ?: "",
                                type      = d["type"]     as? String ?: "mcq",
                                xpReward  = (d["xpReward"] as? Long)?.toInt() ?: 10,
                                completed = completedIds.contains(doc.id),
                                order     = (d["order"]   as? Long)?.toInt() ?: 0,
                            )
                        }
                        .sortedBy { it.order }
                } else {
                    sampleLessons.map { it.copy(completed = completedIds.contains(it.id)) }
                }
            } catch (e: Exception) {
                _lessons.value = sampleLessons
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun startLesson(lesson: KurdiLesson) {
        // NavHost üzerinden ders detay ekranına geçiş
        // ViewModel'de navigasyon yapılmaz, Screen event'i callback ile NavHost'a iletilir
    }

    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        if (lesson.completed) return

        val gained   = lesson.xpReward
        val newXp    = _xp.value + gained
        val newLevel = (newXp / 100) + 1

        // Anlık UI güncelle
        _xp.value    = newXp
        _level.value = newLevel
        _lessons.value = _lessons.value.map { if (it.id == lessonId) it.copy(completed = true) else it }

        viewModelScope.launch {
            try {
                // 1) users/{uid}/kf_progress/{lessonId} — Android yapısı
                firestore.collection("users").document(uid)
                    .collection("kf_progress").document(lessonId)
                    .set(mapOf("ts" to Timestamp.now(), "xpEarned" to gained)).await()

                // 2) kf_done array — XML temasıyla uyum (FieldValue.arrayUnion)
                // 3) kf_xp, kf_streak — XML teması bu alanları kullanıyor
                firestore.collection("users").document(uid).update(mapOf(
                    "xp"     to newXp,
                    "kf_xp"  to newXp,        // XML tema uyumu
                    "level"  to newLevel,
                    "kf_done" to com.google.firebase.firestore.FieldValue.arrayUnion(lessonId),
                )).await()

                // Streak güncelle
                updateStreak()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun updateStreak() {
        try {
            val userDoc  = firestore.collection("users").document(uid).get().await()
            val lastDate = userDoc.getTimestamp("lastKurdiDate")
            val now      = Timestamp.now()
            val diffDays = if (lastDate != null) (now.seconds - lastDate.seconds) / 86400 else -1L
            val newStreak = when {
                lastDate == null || diffDays > 1 -> 1
                diffDays == 1L                   -> _streak.value + 1
                else                             -> _streak.value
            }
            _streak.value = newStreak
            // kf_streak de yaz — XML temasıyla uyum
            firestore.collection("users").document(uid).update(mapOf(
                "streak"        to newStreak,
                "kf_streak"     to newStreak,  // XML: localStorage.setItem('kf_streak',...)
                "lastKurdiDate" to now,
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private val sampleLessons = listOf(
        KurdiLesson("1", "Silav û Nasîn",   "Merhaba ve Tanışma",  "mcq",   10, false, 1),
        KurdiLesson("2", "Jimare",          "Sayılar 1-10",        "fill",  10, false, 2),
        KurdiLesson("3", "Reng",            "Renkler",             "match", 10, false, 3),
        KurdiLesson("4", "Malbat",          "Aile üyeleri",        "build", 10, false, 4),
        KurdiLesson("5", "Xwarinên rojane", "Günlük yiyecekler",   "mcq",   15, false, 5),
        KurdiLesson("6", "Roj û Meh",       "Günler ve Aylar",     "fill",  15, false, 6),
        KurdiLesson("7", "Cih û Welat",     "Yerler ve Ülkeler",   "match", 15, false, 7),
        KurdiLesson("8", "Rengdêr",         "Sıfatlar",            "build", 20, false, 8),
    )
}
