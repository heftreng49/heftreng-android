package com.heftreng.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
//  ThemePreferencesRepository
//  DataStore gerekmez — standart SharedPreferences kullanır.
//  Hilt ile inject edilir; ViewModel'dan çağrılır.
// ─────────────────────────────────────────────────────────────────────────────
@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("heftrang_theme", Context.MODE_PRIVATE)

    private val _variant = MutableStateFlow(loadVariant())
    val variant: StateFlow<HeftrangThemeVariant> = _variant.asStateFlow()

    private val _isDark = MutableStateFlow(prefs.getBoolean(KEY_DARK, true))
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    // Yazı rengi override — null = tema varsayılanı kullan
    private val _textColorOverride = MutableStateFlow(loadTextColor())
    val textColorOverride: StateFlow<Color?> = _textColorOverride.asStateFlow()

    fun setTextColorOverride(color: Color?) {
        if (color == null) {
            prefs.edit().remove(KEY_TEXT_COLOR).apply()
        } else {
            // ARGB long olarak sakla
            prefs.edit().putLong(KEY_TEXT_COLOR, color.value.toLong()).apply()
        }
        _textColorOverride.value = color
    }

    private fun loadTextColor(): Color? {
        if (!prefs.contains(KEY_TEXT_COLOR)) return null
        return Color(prefs.getLong(KEY_TEXT_COLOR, 0xFFF4F4FAL).toULong())
    }

    fun setVariant(v: HeftrangThemeVariant) {
        prefs.edit().putString(KEY_VARIANT, v.name).apply()
        _variant.value = v
    }

    fun setDark(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
        _isDark.value = dark
    }

    private fun loadVariant(): HeftrangThemeVariant {
        val name = prefs.getString(KEY_VARIANT, HeftrangThemeVariant.CHARCOAL_INK.name)
            ?: return HeftrangThemeVariant.CHARCOAL_INK
        return runCatching { HeftrangThemeVariant.valueOf(name) }
            .getOrDefault(HeftrangThemeVariant.CHARCOAL_INK)
    }

    private companion object {
        const val KEY_VARIANT    = "theme_variant"
        const val KEY_DARK       = "dark_mode"
        const val KEY_TEXT_COLOR = "text_color_override"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Örnek ViewModel kullanımı (ayrı dosyaya taşıyabilirsiniz):
//
//  @HiltViewModel
//  class ThemeViewModel @Inject constructor(
//      private val repo: ThemePreferencesRepository,
//  ) : ViewModel() {
//      val variant = repo.variant
//      val isDark  = repo.isDark
//      fun setVariant(v: HeftrangThemeVariant) = repo.setVariant(v)
//      fun setDark(d: Boolean)                 = repo.setDark(d)
//  }
//
//  Kullanım (MainActivity veya NavHost):
//  val vm: ThemeViewModel = hiltViewModel()
//  val variant by vm.variant.collectAsStateWithLifecycle()
//  val isDark  by vm.isDark.collectAsStateWithLifecycle()
//  HeftrangTheme(darkMode = isDark, variant = variant) { ... }
// ─────────────────────────────────────────────────────────────────────────────
