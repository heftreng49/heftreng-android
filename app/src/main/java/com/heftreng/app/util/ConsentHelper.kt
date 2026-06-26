package com.heftreng.app.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UMP (User Messaging Platform) yöneticisi.
 *
 * Sorumluluklar:
 *  1. Kullanıcının onay durumunu kontrol et (GDPR/CCPA bölgeleri)
 *  2. Gerekirse onay formu göster
 *  3. Reklam yüklenip yüklenemeyeceğini bildirmek için [canRequestAds] akışı sun
 *
 * Kullanım:
 *   - Uygulama başladığında [initialize] çağır (MainActivity.onCreate)
 *   - [canRequestAds] == true olana kadar reklam yükleme
 *
 * Notlar:
 *  - GDPR/CCPA dışı bölgelerde (Türkiye gibi) form gösterilmez,
 *    [canRequestAds] hemen true olur.
 *  - AB/Kaliforniya kullanıcıları için form zorunlu.
 *  - Debug modda test cihazı ekleyerek formu her zaman tetikleyebilirsin.
 */
object ConsentHelper {

    private const val TAG = "ConsentHelper"

    // ── Consent durumu akışı — AdsViewModel ve NavHost buradan dinler ─────
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds = _canRequestAds.asStateFlow()

    private val _consentStatus = MutableStateFlow(ConsentInformation.ConsentStatus.UNKNOWN)
    val consentStatus = _consentStatus.asStateFlow()

    /**
     * Activity.onCreate'da çağır.
     * [onCanRequestAds] reklam yüklemeye başlayabildiğinde tetiklenir.
     */
    fun initialize(
        activity: Activity,
        debugMode: Boolean = false,
        testDeviceHashedId: String? = null,   // AdMob test cihaz hash'i (debug için)
        onCanRequestAds: () -> Unit = {},
    ) {
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        if (debugMode && testDeviceHashedId != null) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(testDeviceHashedId)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                // Güncelleme başarılı — form gerekli mi kontrol et
                _consentStatus.value = consentInfo.consentStatus

                if (consentInfo.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, consentInfo, onCanRequestAds)
                } else {
                    // Form gerekmiyor (GDPR/CCPA dışı bölge) — hemen izin ver
                    Log.d(TAG, "Form gerekmez — consentStatus=${consentInfo.consentStatus}")
                    _canRequestAds.value = true
                    onCanRequestAds()
                }
            },
            { formError ->
                // Güncelleme başarısız — önceki izne bak, yoksa yine de başlat
                Log.w(TAG, "Consent info update hatası: ${formError.message}")
                val canAds = consentInfo.canRequestAds()
                _canRequestAds.value = canAds
                if (canAds) onCanRequestAds()
                else {
                    // Hata durumunda engelleme — reklam gösterme ama uygulamayı kilitleme
                    _canRequestAds.value = true
                    onCanRequestAds()
                }
            },
        )
    }

    private fun loadAndShowConsentForm(
        activity: Activity,
        consentInfo: ConsentInformation,
        onCanRequestAds: () -> Unit,
    ) {
        if (!consentInfo.isConsentFormAvailable) {
            _canRequestAds.value = consentInfo.canRequestAds()
            if (_canRequestAds.value) onCanRequestAds()
            return
        }

        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
            activity,
        ) { formError ->
            if (formError != null) {
                Log.w(TAG, "Form gösterme hatası: ${formError.message}")
            }
            _consentStatus.value = consentInfo.consentStatus
            val canAds = consentInfo.canRequestAds()
            Log.d(TAG, "Form tamamlandı — canRequestAds=$canAds status=${consentInfo.consentStatus}")
            _canRequestAds.value = canAds
            if (canAds) onCanRequestAds()
            else {
                // Kullanıcı reddetti — yine de başlat (reklam göstermeyiz, kişisel olmayan gösteririz)
                _canRequestAds.value = true
                onCanRequestAds()
            }
        }
    }

    /** Kullanıcı gizlilik ayarlarından tekrar onay formunu açmak isterse */
    fun showPrivacyOptionsForm(activity: Activity, onDismiss: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Gizlilik formu hatası: ${formError.message}")
            }
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
            _consentStatus.value = consentInfo.consentStatus
            _canRequestAds.value = consentInfo.canRequestAds()
            onDismiss()
        }
    }

    /** Onay formunun tekrar gösterilip gösterilemeyeceği (gizlilik ayarları butonu için) */
    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return UserMessagingPlatform.getConsentInformation(context)
            .privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
