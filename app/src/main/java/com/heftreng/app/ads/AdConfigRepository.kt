package com.heftreng.app.ads

import com.heftreng.app.data.model.CmsAdConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdConfigRepository — Remote Config'ten okunan tüm reklam config'leri için
 *  TEK erişim noktası.
 *
 *  ESKİ SİSTEM: AdsViewModel içinde her ekran için ayrı
 *  MutableStateFlow<CmsAdConfig?> + ayrı resolveOrDefault(...) combine'ı vardı
 *  (10+ tekrarlanan blok). Yeni bir ekrana reklam eklemek = yeni StateFlow +
 *  yeni combine + yeni case demekti.
 *
 *  YENİ SİSTEM: tek Map<String, CmsAdConfig> StateFlow'u. Ekranlar/ViewModel
 *  bu tek StateFlow'u collect edip get(key) ile ihtiyaç duydukları config'e
 *  bakar — reaktif türetme (resolvedUnitId gibi) çağıran tarafta (AdsViewModel)
 *  `combine`/`map` ile yapılır; repository sadece veriyi tutar, scope tutmaz.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Singleton
class AdConfigRepository @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
) {
    private val _configs = MutableStateFlow<Map<String, CmsAdConfig>>(emptyMap())
    val configs: StateFlow<Map<String, CmsAdConfig>> = _configs.asStateFlow()

    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled: StateFlow<Boolean> = _adsEnabled.asStateFlow()

    /** Belirli bir key için ham config (enabled/unitId kontrolü yapılmaz). */
    fun get(key: String): CmsAdConfig? = _configs.value[key]

    /**
     * Gösterilebilir unitId — global kapalıysa, config gelmemişse, admin
     * kapattıysa veya unitId boşsa null. Kural: Remote Config dışından
     * hardcode ID gelmez; RC henüz gelmediyse (c==null) reklam yüklenmez.
     * Reaktif değil — anlık okuma. Reaktif ihtiyaç için AdsViewModel.unitIdFlow(key).
     */
    fun resolvedUnitId(key: String): String? {
        if (!_adsEnabled.value) return null
        val c = _configs.value[key] ?: return null
        if (!c.enabled) return null
        return c.unitId.ifBlank { null }
    }

    /**
     * Remote Config'ten tüm bilinen key'leri (RemoteConfigManager.ALL_AD_KEYS)
     * okur ve tek Map'i günceller. Ağ isteği yapmaz — fetchAndActivate()
     * önceden çağrılmış olmalı (bkz. refresh()).
     */
    fun reloadFromCache() {
        _adsEnabled.value = remoteConfigManager.isAdsEnabled()
        val newMap = mutableMapOf<String, CmsAdConfig>()
        for (key in RemoteConfigManager.ALL_AD_KEYS) {
            remoteConfigManager.getAdConfig(key)?.let { newMap[key] = it }
        }
        _configs.value = newMap
    }

    /** Ağdan/cache'ten çeker (fetchAndActivate) ve Map'i günceller. */
    suspend fun refresh(forceRefresh: Boolean = false): Boolean {
        val result = if (forceRefresh) remoteConfigManager.forceFetchAndActivate()
                     else remoteConfigManager.fetchAndActivate()
        reloadFromCache()
        return result
    }
}
