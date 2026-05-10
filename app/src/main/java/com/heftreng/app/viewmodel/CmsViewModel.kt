package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Firestore: cms/settings belgesi
// Ekran toggle'ları: screenFeed, screenMessages, screenSerials, screenBooks,
//                   screenKurdi, screenNotifications, screenSearch, screenStories
// Feed özellikleri: feedShowImages, feedShowQuotes, feedShowReposts
// Genel: maintenanceMode, minAppVersion

data class CmsSettings(
    // Ekranlar
    val screenFeed          : Boolean = true,
    val screenMessages      : Boolean = true,
    val screenSerials       : Boolean = true,
    val screenBooks         : Boolean = true,
    val screenKurdi         : Boolean = true,
    val screenNotifications : Boolean = true,
    val screenSearch        : Boolean = true,
    val screenStories       : Boolean = false,
    // Feed özellikleri
    val feedShowImages      : Boolean = true,
    val feedShowQuotes      : Boolean = true,
    val feedShowReposts     : Boolean = true,
    // Genel
    val maintenanceMode     : Boolean = false,
    val minAppVersion       : Int     = 1,
    // Duyurular
    val announcementText    : String  = "",
    val announcementActive  : Boolean = false,
    val announcementUrl     : String  = "",
)

@HiltViewModel
class CmsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _settings = MutableStateFlow(CmsSettings())
    val settings = _settings.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val ref = firestore.collection("cms").document("settings")

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = ref.get().await()
                val d = doc.data ?: return@launch
                _settings.value = CmsSettings(
                    screenFeed          = d["screenFeed"]          as? Boolean ?: true,
                    screenMessages      = d["screenMessages"]      as? Boolean ?: true,
                    screenSerials       = d["screenSerials"]       as? Boolean ?: true,
                    screenBooks         = d["screenBooks"]         as? Boolean ?: true,
                    screenKurdi         = d["screenKurdi"]         as? Boolean ?: true,
                    screenNotifications = d["screenNotifications"] as? Boolean ?: true,
                    screenSearch        = d["screenSearch"]        as? Boolean ?: true,
                    screenStories       = d["screenStories"]       as? Boolean ?: false,
                    feedShowImages      = d["feedShowImages"]      as? Boolean ?: true,
                    feedShowQuotes      = d["feedShowQuotes"]      as? Boolean ?: true,
                    feedShowReposts     = d["feedShowReposts"]     as? Boolean ?: true,
                    maintenanceMode     = d["maintenanceMode"]     as? Boolean ?: false,
                    minAppVersion       = (d["minAppVersion"]      as? Long)?.toInt() ?: 1,
                    announcementText    = d["announcementText"]    as? String  ?: "",
                    announcementActive  = d["announcementActive"]  as? Boolean ?: false,
                    announcementUrl     = d["announcementUrl"]     as? String  ?: "",
                )
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun update(new: CmsSettings) {
        _settings.value = new
    }

    fun toggle(key: String) {
        val s = _settings.value
        _settings.value = when (key) {
            "screenFeed"          -> s.copy(screenFeed          = !s.screenFeed)
            "screenMessages"      -> s.copy(screenMessages      = !s.screenMessages)
            "screenSerials"       -> s.copy(screenSerials       = !s.screenSerials)
            "screenBooks"         -> s.copy(screenBooks         = !s.screenBooks)
            "screenKurdi"         -> s.copy(screenKurdi         = !s.screenKurdi)
            "screenNotifications" -> s.copy(screenNotifications = !s.screenNotifications)
            "screenSearch"        -> s.copy(screenSearch        = !s.screenSearch)
            "screenStories"       -> s.copy(screenStories       = !s.screenStories)
            "feedShowImages"      -> s.copy(feedShowImages      = !s.feedShowImages)
            "feedShowQuotes"      -> s.copy(feedShowQuotes      = !s.feedShowQuotes)
            "feedShowReposts"     -> s.copy(feedShowReposts     = !s.feedShowReposts)
            "maintenanceMode"     -> s.copy(maintenanceMode     = !s.maintenanceMode)
            "announcementActive"  -> s.copy(announcementActive  = !s.announcementActive)
            else -> s
        }
    }

    fun save(onDone: () -> Unit = {}) {
        val s = _settings.value
        viewModelScope.launch {
            _loading.value = true
            try {
                ref.set(mapOf(
                    "screenFeed"          to s.screenFeed,
                    "screenMessages"      to s.screenMessages,
                    "screenSerials"       to s.screenSerials,
                    "screenBooks"         to s.screenBooks,
                    "screenKurdi"         to s.screenKurdi,
                    "screenNotifications" to s.screenNotifications,
                    "screenSearch"        to s.screenSearch,
                    "screenStories"       to s.screenStories,
                    "feedShowImages"      to s.feedShowImages,
                    "feedShowQuotes"      to s.feedShowQuotes,
                    "feedShowReposts"     to s.feedShowReposts,
                    "maintenanceMode"     to s.maintenanceMode,
                    "minAppVersion"       to s.minAppVersion,
                    "announcementText"    to s.announcementText,
                    "announcementActive"  to s.announcementActive,
                    "announcementUrl"     to s.announcementUrl,
                ), SetOptions.merge()).await()
                _saved.value = true
                onDone()
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun resetSaved() { _saved.value = false }
}
