package com.heftreng.app.ads

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.heftreng.app.data.model.CmsAdConfig
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  RemoteConfigManager — Firebase Remote Config tek erişim noktası.
 *
 *  NEDEN REMOTE CONFIG? (Firestore cms_ads yerine)
 *  ─────────────────────────────────────────────────
 *  • Firestore okuma = para (her kullanıcı açılışı = 1 okuma faturası)
 *  • Remote Config = ücretsiz, Google CDN'den gelir, built-in offline cache var
 *  • Minimum fetch interval: 12 saat → günde 1 fetch/kullanıcı, Firestore'da her açılış
 *  • A/B test ve Conditions Remote Config'de built-in (Firestore'da manuel yapıyorduk)
 *
 *  ÇALIŞMA MANTIĞI:
 *  ─────────────────
 *  1. App açılır → fetchAndActivate() çağrılır (async, UI'ı bloklamaz)
 *  2. Cache (12 saat — hem SDK'nın minimumFetchInterval'ı hem bizim
 *     client-side throttle'ımız AYNI süre) geçerli ise network'e gitmez → 0ms gecikme
 *  3. Cache bayatsa arka planda fetch → activate → StateFlow güncellenir
 *  4. İlk açılış veya network yok → defaultValues devreye girer (reklam hiç durmuyor)
 *
 *  DEFAULT VALUES:
 *  ───────────────
 *  Remote Config konsolunda henüz değer yok veya offline → buradaki sabitler kullanılır.
 *  Bu sayede yeni kurulumda bile reklamlar çalışır, "boş config" hatası olmaz.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Singleton
class RemoteConfigManager @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) {
    companion object {
        // ── Fetch interval ───────────────────────────────────────────────
        // TEK KAYNAK: prod cache süresi 12 saat. Debug'da 0 (her açılışta anında
        // günceller, geliştirme sırasında bekleme olmasın).
        // ÖNEMLİ: CLIENT_THROTTLE_MS bu değerle AYNI olmalı — aksi halde iki
        // katman (SDK'nın kendi minimumFetchInterval'ı + bizim client-side
        // throttle'ımız) birbirinden habersiz farklı sürelerle çalışır ve biri
        // diğerini anlamsız kılar (biri kilitliyken diğeri gereksiz yere
        // "süresi doldu" sanıp fetch dener, SDK zaten cache'ten döner).
        private const val FETCH_INTERVAL_HOURS = 12L
        private const val FETCH_INTERVAL_PROD  = FETCH_INTERVAL_HOURS * 3_600L
        private const val FETCH_INTERVAL_DEBUG = 0L

        // Client-side throttle — SDK'nın minimumFetchInterval'ı ile AYNI süre.
        // Farklı bir değer olursa iki katman senkron olmayan kararlar verir.
        private const val CLIENT_THROTTLE_MS = FETCH_INTERVAL_HOURS * 3_600_000L  // 12 saat

        // ── Remote Config key isimleri ───────────────────────────────────
        // Firebase konsolunda bu isimlerle değer tanımlanacak.
        // JSON formatında: {"enabled":true,"unitId":"ca-app-pub-xxx/yyy","bannerSize":"adaptive",...}
        const val KEY_ADS_GLOBAL        = "ads_global"        // {"enabled": true}
        const val KEY_BANNER_FEED       = "banner_feed"
        const val KEY_BANNER_LIBRARY    = "banner_library"
        const val KEY_BANNER_KURDI      = "banner_kurdi"
        const val KEY_BANNER_BLOG       = "banner_blog"
        const val KEY_INTERSTITIAL      = "interstitial_serial"
        const val KEY_REWARDED          = "rewarded_xp"
        const val KEY_NATIVE_FEED       = "native_feed"
        const val KEY_NATIVE_BLOG       = "native_blog"
        const val KEY_NATIVE_LIBRARY    = "native_library"
        const val KEY_NATIVE_KURDI      = "native_kurdi"
        const val KEY_NATIVE_PROFILE    = "native_profile"
        const val KEY_NATIVE_SEARCH     = "native_search"

        /**
         * Tüm banner/native config key'lerinin tek listesi. Yeni bir ekrana
         * reklam eklemek istendiğinde tek yapılması gereken: burada bir KEY_*
         * sabiti + bu listeye giriş + aşağıdaki DEFAULTS'a bir satır eklemek.
         * (Interstitial/Rewarded ayrı ele alınır — AdConfigRepository.refresh()'e bakınız.)
         */
        val ALL_AD_KEYS = listOf(
            KEY_BANNER_FEED, KEY_BANNER_LIBRARY, KEY_BANNER_KURDI, KEY_BANNER_BLOG,
            KEY_NATIVE_FEED, KEY_NATIVE_BLOG, KEY_NATIVE_LIBRARY, KEY_NATIVE_KURDI,
            KEY_NATIVE_PROFILE, KEY_NATIVE_SEARCH,
            KEY_INTERSTITIAL, KEY_REWARDED,
        )

        // ── Default JSON değerleri ───────────────────────────────────────
        // Firebase konsolu henüz yapılandırılmamışsa veya offline iken devreye girer.
        // Production unit ID'leri buraya yazılır → ilk açılışta reklam zaten çalışır.
        // DEFAULTS: Firebase konsolu henüz yapılandırılmamışsa veya offline iken devreye girer.
        // unitId boş bırakıldı — RC'de gerçek ID tanımlanmadan reklam yüklenmesin (Kural 4).
        // enabled:false → kesinlikle gösterme. enabled:true + unitId:"" → ID gelene kadar bekle.
        private val DEFAULTS = mapOf(
            KEY_ADS_GLOBAL     to """{"enabled":true}""",
            KEY_BANNER_FEED    to """{"enabled":false,"unitId":"","bannerSize":"adaptive","position":5,"frequency":5}""",
            KEY_BANNER_LIBRARY to """{"enabled":false,"unitId":"","bannerSize":"adaptive","position":5,"frequency":5}""",
            KEY_BANNER_KURDI   to """{"enabled":false,"unitId":"","bannerSize":"adaptive","position":5,"frequency":5}""",
            KEY_BANNER_BLOG    to """{"enabled":false,"unitId":"","bannerSize":"adaptive","position":5,"frequency":5}""",
            KEY_INTERSTITIAL   to """{"enabled":false,"unitId":"","frequency":4,"screens":"feed,library,kurdi,blog"}""",
            KEY_REWARDED       to """{"enabled":false,"unitId":"","dailyLimit":3,"xpReward":50,"scenarioDoubleXp":true,"scenarioUnlockLesson":true,"scenarioSaveStreak":true}""",
            KEY_NATIVE_FEED    to """{"enabled":false,"unitId":"","position":5,"frequency":5}""",
            KEY_NATIVE_BLOG    to """{"enabled":false,"unitId":"","position":5,"frequency":5}""",
            KEY_NATIVE_LIBRARY to """{"enabled":false,"unitId":"","position":5,"frequency":5}""",
            KEY_NATIVE_KURDI   to """{"enabled":false,"unitId":"","position":5,"frequency":5}""",
            KEY_NATIVE_PROFILE to """{"enabled":false,"unitId":"","position":5,"frequency":5}""",
            KEY_NATIVE_SEARCH  to """{"enabled":false,"unitId":"","position":5,"frequency":5}""",
        )
    }

    init {
        // Fetch interval: debug'da 0 (her açılışta anında günceller),
        // production'da 12 saat (FETCH_INTERVAL_PROD ile CLIENT_THROTTLE_MS aynı kaynaktan türetilir)
        val interval = if (com.heftreng.app.BuildConfig.DEBUG)
            FETCH_INTERVAL_DEBUG
        else
            FETCH_INTERVAL_PROD

        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(interval)
                .build()
        )
        // Default değerleri SDK'ya kaydet — network öncesi her zaman anında okunabilir
        remoteConfig.setDefaultsAsync(DEFAULTS)
    }

    private var lastFetchMs = 0L

    /**
     * Remote Config'i fetch et ve aktive et.
     * Client-side throttle: aynı oturumda 12 saatten sık fetch yapmaz —
     * bu süre SDK'nın minimumFetchIntervalInSeconds'ı (FETCH_INTERVAL_PROD)
     * ile bilerek AYNI: iki katman farklı süre kullanırsa biri "süresi
     * doldu" sanıp gereksiz yere fetch dener ama SDK zaten kendi cache'inden
     * döner — hiçbir şey kazandırmadan kod karmaşıklaştırır.
     * Debug build'de throttle yok — her açılışta anında günceller.
     */
    suspend fun fetchAndActivate(): Boolean {
        val now     = System.currentTimeMillis()
        val throttle = if (com.heftreng.app.BuildConfig.DEBUG) 0L else CLIENT_THROTTLE_MS
        if (now - lastFetchMs < throttle) return false
        return runCatching {
            val result = remoteConfig.fetchAndActivate().await()
            if (result) lastFetchMs = now
            result
        }.getOrDefault(false)
    }

    /**
     * Gerçek zorla yenileme — SDK'nın 12 saatlik minimumFetchInterval kuralını
     * BYPASS eder (fetch(0) ile). Admin panelinden "Reklamları Yenile" gibi bir
     * butona bağlanabilir. Normal kullanıcı akışında ÇAĞRILMAMALI — Firebase'in
     * ücretsiz kotasını (günde sınırlı istek) hızla tüketebilir.
     */
    suspend fun forceFetchAndActivate(): Boolean = runCatching {
        remoteConfig.fetch(0).await()
        remoteConfig.activate().await()
    }.getOrDefault(false)

    // ── Config okuma yardımcıları ────────────────────────────────────────

    /** Global reklam açık/kapalı flag'i */
    fun isAdsEnabled(): Boolean =
        remoteConfig.getString(KEY_ADS_GLOBAL)
            .parseJsonBoolean("enabled", default = true)

    /**
     * Verilen key için CmsAdConfig döner.
     *
     * Remote Config'den gelen JSON → CmsAdConfig.
     * Alan yoksa veya JSON bozuksa → null (reklam gösterilmez, crash olmaz).
     */
    fun getAdConfig(key: String): CmsAdConfig? = runCatching {
        val raw = remoteConfig.getString(key)
        if (raw.isBlank()) return null
        val json = JSONObject(raw)
        CmsAdConfig(
            id            = key,
            unitId        = json.optString("unitId", ""),
            enabled       = json.optBoolean("enabled", false),
            testMode      = json.optBoolean("testMode", false),
            position      = json.optInt("position", 5),
            frequency     = json.optInt("frequency", 3),
            xpReward      = json.optInt("xpReward", 50),
            dailyLimit    = json.optInt("dailyLimit", 3),
            scenarioDoubleXp     = json.optBoolean("scenarioDoubleXp", true),
            scenarioUnlockLesson = json.optBoolean("scenarioUnlockLesson", true),
            scenarioSaveStreak   = json.optBoolean("scenarioSaveStreak", true),
            adType        = json.optString("adType", "banner"),
            bannerSize    = json.optString("bannerSize", "adaptive").trim().lowercase(),
            placement     = json.optString("placement", "in_list"),
            screens       = json.optString("screens", "feed").trim().lowercase(),
            label         = json.optString("label", ""),
            bgColor       = json.optString("bgColor", ""),
            cornerRadius  = json.optInt("cornerRadius", 0),
            paddingTop    = json.optInt("paddingTop", 0),
            paddingBottom = json.optInt("paddingBottom", 0),
        )
    }.getOrNull()

    // ── Yardımcı uzantılar ───────────────────────────────────────────────

    private fun String.parseJsonBoolean(key: String, default: Boolean): Boolean = runCatching {
        JSONObject(this).optBoolean(key, default)
    }.getOrDefault(default)
}
