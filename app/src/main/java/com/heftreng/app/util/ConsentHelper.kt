package com.heftreng.app.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *
 *  ÖNEMLİ — ZAMAN AŞIMI KORUMASI:
 *  UMP'nin requestConsentInfoUpdate() ve loadAndShowConsentFormIfRequired()
 *  çağrıları Google'ın sunucularına ağ isteği yapar. Bu istek YAVAŞ veya
 *  TAKILI bir ağda (zayıf bağlantı, kurumsal/okul ağı, bazı VPN'ler) çok uzun
 *  sürebilir ya da hiç tamamlanmayabilir — SDK'nın bunun için yerleşik bir
 *  zaman aşımı YOK. Bu durumda [onCanRequestAds] hiç çağrılmaz, dolayısıyla
 *  AdsViewModel.loadAdConfigs() de hiç tetiklenmez ve TÜM reklamlar o oturum
 *  boyunca "hiç yüklenmez" hale gelir. CONSENT_TIMEOUT_MS sonunda hâlâ
 *  sonuçlanmamışsa, güvenli tarafta kalarak (NPA — kişiselleştirilmemiş
 *  reklam) akışı zorla ilerletiyoruz; kullanıcı deneyimi reklamsız/boş
 *  kalmasın diye.
 */
object ConsentHelper {

    private const val TAG = "ConsentHelper"

    // UMP ağ çağrısı bu süre içinde sonuçlanmazsa, NPA-güvenli modda zorla ilerle.
    // Google UMP dokümanı: istek genellikle 3–5 saniye sürer; 2 saniye çok kısaydı
    // — yavaş ağlarda gerçek onay hiç gelmeden NPA'ya düşüyordu, bu hem fill rate'i
    // düşürüyor hem de GDPR uyumunu riske atıyordu (kullanıcıya form gösterilmeden
    // NPA-consent sayılıyor). 8 saniye hem güvenli hem AdMob önerisiyle uyumlu.
    private const val CONSENT_TIMEOUT_MS = 8_000L

    // ── Consent durumu akışı — AdsViewModel ve NavHost buradan dinler ─────
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds = _canRequestAds.asStateFlow()

    private val _consentStatus = MutableStateFlow(ConsentInformation.ConsentStatus.UNKNOWN)
    val consentStatus = _consentStatus.asStateFlow()

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * Activity.onCreate'da çağır.
     * [onCanRequestAds] reklam yüklemeye başlayabildiğinde tetiklenir.
     * Bu callback'in EN FAZLA CONSENT_TIMEOUT_MS içinde (başarı, hata veya
     * zaman aşımı — hangisi önce gelirse) çağrılacağı garanti edilir.
     */
    fun initialize(
        activity: Activity,
        debugMode: Boolean = false,
        testDeviceHashedId: String? = null,   // AdMob test cihaz hash'i (debug için)
        onCanRequestAds: () -> Unit = {},
    ) {
        // Aynı çağrıyı iki kez tetiklememek için tek seferlik guard
        var resolved = false
        val resolveOnce: (Boolean, Int) -> Unit = { canAds, status ->
            if (!resolved) {
                resolved = true
                timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
                _consentStatus.value = status
                _canRequestAds.value = canAds
                onCanRequestAds()
            }
        }

        // Zaman aşımı koruması — Google sunucusu yanıt vermese/yavaş olsa bile
        // reklam akışı CONSENT_TIMEOUT_MS sonunda NPA-güvenli modda ilerler.
        val runnable = Runnable {
            Log.w(TAG, "UMP zaman aşımı (${CONSENT_TIMEOUT_MS}ms) — NPA-güvenli modda zorla ilerleniyor")
            resolveOnce(true, ConsentInformation.ConsentStatus.REQUIRED)
        }
        timeoutRunnable = runnable
        timeoutHandler.postDelayed(runnable, CONSENT_TIMEOUT_MS)

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
                if (consentInfo.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, consentInfo, resolveOnce)
                } else {
                    // Form gerekmiyor (GDPR/CCPA dışı bölge) — hemen izin ver
                    Log.d(TAG, "Form gerekmez — consentStatus=${consentInfo.consentStatus}")
                    resolveOnce(true, consentInfo.consentStatus)
                }
            },
            { formError ->
                // Güncelleme başarısız — önceki onay durumuna bak
                Log.w(TAG, "Consent info update hatası: ${formError.message}")
                resolveOnce(consentInfo.canRequestAds(), consentInfo.consentStatus)
            },
        )
    }

    private fun loadAndShowConsentForm(
        activity: Activity,
        consentInfo: ConsentInformation,
        resolveOnce: (Boolean, Int) -> Unit,
    ) {
        if (!consentInfo.isConsentFormAvailable) {
            resolveOnce(consentInfo.canRequestAds(), consentInfo.consentStatus)
            return
        }

        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
            activity,
        ) { formError ->
            if (formError != null) {
                Log.w(TAG, "Form gösterme hatası: ${formError.message}")
            }
            val canAds = consentInfo.canRequestAds()
            Log.d(TAG, "Form tamamlandı — canRequestAds=$canAds status=${consentInfo.consentStatus}")
            resolveOnce(canAds, consentInfo.consentStatus)
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
