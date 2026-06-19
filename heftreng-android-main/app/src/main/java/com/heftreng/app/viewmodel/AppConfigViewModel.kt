package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.heftreng.app.data.model.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _config  = MutableStateFlow(AppConfig())
    val config = _config.asStateFlow()

    private val _loaded  = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    // AppConfig nadiren değişir — 6 saatte bir server'a git
    private val CONFIG_TTL_MS = 6L * 60L * 60L * 1000L
    private var lastFetchMs = 0L

    init { load() }

    fun load(forceServer: Boolean = false) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val stale = (now - lastFetchMs) > CONFIG_TTL_MS
                // Önce cache'den hızlıca yükle — sonra TTL dolmuşsa server'a git
                val source = if (forceServer || stale) Source.DEFAULT else Source.CACHE
                val doc = firestore.collection("appConfig")
                    .document("features").get(source).await()
                if (doc.exists()) lastFetchMs = now
                if (doc.exists()) {
                    val d = doc.data ?: return@launch
                    _config.value = AppConfig(
                        feedEnabled          = d["feedEnabled"]          as? Boolean ?: true,
                        messagesEnabled      = d["messagesEnabled"]      as? Boolean ?: true,
                        serialsEnabled       = d["serialsEnabled"]       as? Boolean ?: true,
                        booksEnabled         = d["booksEnabled"]         as? Boolean ?: true,
                        kurdiEnabled         = d["kurdiEnabled"]         as? Boolean ?: true,
                        notificationsEnabled = d["notificationsEnabled"] as? Boolean ?: true,
                        searchEnabled        = d["searchEnabled"]        as? Boolean ?: true,
                        storiesEnabled       = d["storiesEnabled"]       as? Boolean ?: true,
                        feedShowImages       = d["feedShowImages"]       as? Boolean ?: true,
                        feedShowReposts      = d["feedShowReposts"]      as? Boolean ?: true,
                        feedAllowQuotes      = d["feedAllowQuotes"]      as? Boolean ?: true,
                        feedMaxTextLength    = (d["feedMaxTextLength"]   as? Long)?.toInt() ?: 1000,
                        messagesAllowImages  = d["messagesAllowImages"]  as? Boolean ?: true,
                        messagesAllowVoice   = d["messagesAllowVoice"]   as? Boolean ?: true,
                        profileShowXp        = d["profileShowXp"]        as? Boolean ?: true,
                        profileShowStreak    = d["profileShowStreak"]    as? Boolean ?: true,
                        profileShowBadges    = d["profileShowBadges"]    as? Boolean ?: true,
                        profileShowReadList  = d["profileShowReadList"]  as? Boolean ?: true,
                        kurdiShowAiLesson    = d["kurdiShowAiLesson"]    as? Boolean ?: true,
                        kurdiShowWordOfDay   = d["kurdiShowWordOfDay"]   as? Boolean ?: true,
                        maintenanceMode      = d["maintenanceMode"]      as? Boolean ?: false,
                        maintenanceMessage   = d["maintenanceMessage"]   as? String  ?: "Uygulama güncelleniyor.",
                        minVersion           = (d["minVersion"]          as? Long)?.toInt() ?: 1,
                        feedTitle            = d["feedTitle"]            as? String  ?: "",
                        messagesTitle        = d["messagesTitle"]        as? String  ?: "",
                        kurdiTitle           = d["kurdiTitle"]           as? String  ?: "",
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loaded.value = true
            }
        }
    }

    fun save(config: AppConfig, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                firestore.collection("appConfig").document("features").set(
                    mapOf(
                        "feedEnabled"          to config.feedEnabled,
                        "messagesEnabled"      to config.messagesEnabled,
                        "serialsEnabled"       to config.serialsEnabled,
                        "booksEnabled"         to config.booksEnabled,
                        "kurdiEnabled"         to config.kurdiEnabled,
                        "notificationsEnabled" to config.notificationsEnabled,
                        "searchEnabled"        to config.searchEnabled,
                        "storiesEnabled"       to config.storiesEnabled,
                        "feedShowImages"       to config.feedShowImages,
                        "feedShowReposts"      to config.feedShowReposts,
                        "feedAllowQuotes"      to config.feedAllowQuotes,
                        "feedMaxTextLength"    to config.feedMaxTextLength,
                        "messagesAllowImages"  to config.messagesAllowImages,
                        "messagesAllowVoice"   to config.messagesAllowVoice,
                        "profileShowXp"        to config.profileShowXp,
                        "profileShowStreak"    to config.profileShowStreak,
                        "profileShowBadges"    to config.profileShowBadges,
                        "profileShowReadList"  to config.profileShowReadList,
                        "kurdiShowAiLesson"    to config.kurdiShowAiLesson,
                        "kurdiShowWordOfDay"   to config.kurdiShowWordOfDay,
                        "maintenanceMode"      to config.maintenanceMode,
                        "maintenanceMessage"   to config.maintenanceMessage,
                        "minVersion"           to config.minVersion,
                        "feedTitle"            to config.feedTitle,
                        "messagesTitle"        to config.messagesTitle,
                        "kurdiTitle"           to config.kurdiTitle,
                    )
                ).await()
                _config.value = config
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}
