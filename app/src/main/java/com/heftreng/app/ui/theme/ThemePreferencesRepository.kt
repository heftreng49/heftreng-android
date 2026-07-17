package com.heftreng.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "heftrang_theme")

// ─────────────────────────────────────────────────────────────────────────────
//  Tercih anahtarları
// ─────────────────────────────────────────────────────────────────────────────
private object ThemeKeys {
    val VARIANT   = stringPreferencesKey("theme_variant")
    val DARK_MODE = booleanPreferencesKey("dark_mode")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Repository — DataStore okuma/yazma
// ─────────────────────────────────────────────────────────────────────────────
@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeVariant: Flow<HeftrangThemeVariant> =
        context.themeDataStore.data.map { prefs ->
            val name = prefs[ThemeKeys.VARIANT] ?: HeftrangThemeVariant.CHARCOAL_INK.name
            runCatching { HeftrangThemeVariant.valueOf(name) }
                .getOrDefault(HeftrangThemeVariant.CHARCOAL_INK)
        }

    val isDarkMode: Flow<Boolean> =
        context.themeDataStore.data.map { prefs ->
            prefs[ThemeKeys.DARK_MODE] ?: true   // varsayılan: koyu mod
        }

    suspend fun setVariant(variant: HeftrangThemeVariant) {
        context.themeDataStore.edit { it[ThemeKeys.VARIANT] = variant.name }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.themeDataStore.edit { it[ThemeKeys.DARK_MODE] = dark }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel — Ayarlar ekranı için
//  Kullanım: val themeVm: ThemeViewModel = hiltViewModel()
//            HeftrangTheme(
//                darkMode = themeVm.isDark.collectAsStateWithLifecycle().value,
//                variant  = themeVm.variant.collectAsStateWithLifecycle().value,
//            ) { ... }
// ─────────────────────────────────────────────────────────────────────────────
// (Uygulamada ayrı bir dosyaya taşıyabilirsiniz)
/*
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repo: ThemePreferencesRepository,
) : ViewModel() {

    val variant  = repo.themeVariant.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeftrangThemeVariant.CHARCOAL_INK)
    val isDark   = repo.isDarkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setVariant(v: HeftrangThemeVariant) = viewModelScope.launch { repo.setVariant(v) }
    fun setDark(d: Boolean)                 = viewModelScope.launch { repo.setDarkMode(d) }
}
*/
