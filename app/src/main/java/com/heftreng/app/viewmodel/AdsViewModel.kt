package com.heftreng.app.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.heftreng.app.ads.AdEngine
import com.heftreng.app.ads.AdFrequencyManager
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.HeftrangApp
import com.heftreng.app.data.model.CmsAdConfig
import com.heftreng.app.util.ConsentHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdsViewModel — Remote Config geçişi sonrası temizlenmiş versiyon.
 *
 *  DEĞİŞEN ŞEYLER (Firestore → Remote Config):
 *  ─────────────────────────────────────────────
 *  ✗ KALKAN: FirebaseFirestore bağımlılığı (tamamen kaldırıldı)
 *  ✗ KALKAN: loadAdConfigs() → Firestore get() çağrısı
 *  ✗ KALKAN: persistConfig() / loadPersistedConfig() → 19 alanlı string serialization
 *  ✗ KALKAN: adPrefs SharedPreferences → RC kendi cache'ini yönetiyor
 *  ✗ KALKAN: lastServerFetchMs + ADS_CONFIG_TTL_MS → RC'de built-in (setMinimumFetchInterval)
 *
 *  ✓ GELEN: RemoteConfigManager bağımlılığı
 *  ✓ GELEN: loadAdConfigs() → RC.fetchAndActivate() + applyConfigs()
 *
 *  DEĞİŞMEYEN ŞEYLER:
 *  ──────────────────
 *  • AdEngine (banner pool, native pool, retry/backoff) — dokunmadık
 *  • AdFrequencyManager (local-first sayaç) — dokunmadık
 *  • Interstitial / Rewarded yükleme ve gösterme mantığı — dokunmadık
 *  • ConsentHelper.canRequestAds kontrolleri — dokunmadık
 *  • BannerSlot / NativeAdSlot enum'ları — dokunmadık
 *  • resolveOrDefault() mantığı — dokunmadık
 *
 *  NOT: AdFrequencyManager artık tamamen yerel (SharedPreferences). Firestore yok.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
    private val frequencyManager   : AdFrequencyManager,
    private val auth               : com.google.firebase.auth.FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val engine = AdEngine(appContext, viewModelScope)

    // ── Slot tanımları ─────────────────────────────────────────────────────
    enum class BannerSlot  { FEED, LIB, KURDI, BLOG }
    enum class NativeAdSlot { FEED, BLOG, LIBRARY, KURDI, PROFILE, SEARCH }

    private fun BannerSlot.key() = when (this) {
        BannerSlot.FEED  -> "banner_feed"
        BannerSlot.LIB   -> "banner_library"
        BannerSlot.KURDI -> "banner_kurdi"
        BannerSlot.BLOG  -> "banner_blog"
    }

    private fun NativeAdSlot.key() = when (this) {
        NativeAdSlot.FEED    -> "native_feed"
        NativeAdSlot.BLOG    -> "native_blog"
        NativeAdSlot.LIBRARY -> "native_library"
        NativeAdSlot.KURDI   -> "native_kurdi"
        NativeAdSlot.PROFILE -> "native_profile"
        NativeAdSlot.SEARCH  -> "native_search"
    }

    // ── Banner public API ──────────────────────────────────────────────────
    val bannerFeedLoaded  get() = engine.bannerLoadedFlow(BannerSlot.FEED.key())
    val bannerLibLoaded   get() = engine.bannerLoadedFlow(BannerSlot.LIB.key())
    val bannerKurdiLoaded get() = engine.bannerLoadedFlow(BannerSlot.KURDI.key())
    val bannerBlogLoaded  get() = engine.bannerLoadedFlow(BannerSlot.BLOG.key())

    val cachedFeedBanner  : AdView? get() = engine.cachedBanner(BannerSlot.FEED.key())
    val cachedLibBanner   : AdView? get() = engine.cachedBanner(BannerSlot.LIB.key())
    val cachedKurdiBanner : AdView? get() = engine.cachedBanner(BannerSlot.KURDI.key())
    val cachedBlogBanner  : AdView? get() = engine.cachedBanner(BannerSlot.BLOG.key())

    fun preloadBanner(unitId: String, slot: BannerSlot, bannerSize: String = "adaptive") =
        engine.loadBanner(slot.key(), unitId, bannerSize)

    fun getAdSize(bannerSize: String): AdSize = engine.resolveAdSize(bannerSize)

    // ── Pozisyon bazlı banner API ─────────────────────────────────────────
    fun positionedBannerLoadedFlow(key: String): StateFlow<Boolean> = engine.positionedBannerLoadedFlow(key)
    fun cachedPositionedBanner(key: String): AdView? = engine.cachedPositionedBanner(key)
    fun preloadPositionedBanner(key: String, unitId: String, bannerSize: String = "adaptive") =
        engine.loadPositionedBanner(key, unitId, bannerSize)
    fun releasePositionedBanners(keyPrefix: String? = null) = engine.releasePositionedBanners(keyPrefix)

    // ── Native API ────────────────────────────────────────────────────────
    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> = engine.positionedNativeLoadedFlow(key)
    fun cachedPositionedNative(key: String): NativeAd? = engine.cachedPositionedNative(key)
    fun preloadPositionedNative(key: String, unitId: String) = engine.preloadPositionedNative(key, unitId)
    fun releasePositionedNative(key: String) = engine.releasePositionedNative(key)
    fun releaseAllPositionedNatives(keyPrefix: String? = null) = engine.releaseAllPositionedNatives(keyPrefix)

    // ── Config StateFlow'ları ─────────────────────────────────────────────
    // Başlangıç değeri null → Remote Config fetch gelene kadar prod ID ile yüklenir
    // (resolveOrDefault: c==null → prod ID kullan, hemen başlat)
    private val _bannerConfig        = MutableStateFlow<CmsAdConfig?>(null)
    val bannerConfig                 = _bannerConfig.asStateFlow()
    private val _bannerLibraryConfig = MutableStateFlow<CmsAdConfig?>(null)
    val bannerLibraryConfig          = _bannerLibraryConfig.asStateFlow()
    private val _bannerKurdiConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val bannerKurdiConfig            = _bannerKurdiConfig.asStateFlow()
    private val _bannerBlogConfig    = MutableStateFlow<CmsAdConfig?>(null)
    val bannerBlogConfig             = _bannerBlogConfig.asStateFlow()
    private val _interstitialConfig  = MutableStateFlow<CmsAdConfig?>(null)
    val interstitialConfig           = _interstitialConfig.asStateFlow()
    private val _rewardedConfig      = MutableStateFlow<CmsAdConfig?>(null)
    val rewardedConfig               = _rewardedConfig.asStateFlow()
    private val _nativeFeedConfig    = MutableStateFlow<CmsAdConfig?>(null)
    val nativeFeedConfig             = _nativeFeedConfig.asStateFlow()
    private val _nativeBlogConfig    = MutableStateFlow<CmsAdConfig?>(null)
    val nativeBlogConfig             = _nativeBlogConfig.asStateFlow()
    private val _nativeLibraryConfig = MutableStateFlow<CmsAdConfig?>(null)
    val nativeLibraryConfig          = _nativeLibraryConfig.asStateFlow()
    private val _nativeKurdiConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val nativeKurdiConfig            = _nativeKurdiConfig.asStateFlow()
    private val _nativeProfileConfig = MutableStateFlow<CmsAdConfig?>(null)
    val nativeProfileConfig          = _nativeProfileConfig.asStateFlow()
    private val _nativeSearchConfig  = MutableStateFlow<CmsAdConfig?>(null)
    val nativeSearchConfig           = _nativeSearchConfig.asStateFlow()

    private val _allAdConfigs = MutableStateFlow<Map<String, CmsAdConfig>>(emptyMap())
    val allAdConfigs          = _allAdConfigs.asStateFlow()

    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled = _adsEnabled.asStateFlow()

    // ── Unit ID StateFlow'ları ─────────────────────────────────────────────
    // resolveOrDefault mantığı: c==null → config henüz gelmedi → prod ID ile başla
    //                           c!=null && !c.enabled → admin kapattı → null (reklam yok)
    //                           c!=null && c.enabled  → custom ID varsa onu, yoksa prod ID
    // Remote Config henüz gelmemişse (c==null) reklam yüklenmez — hardcode prod ID fallback YOK.
    // Kural: Remote Config dışından hardcode ID gelmemeli. RC gelince unitId dolar.
    // enabled=false → admin kapattı → null. unitId boşsa → RC'de tanımsız → null.
    private fun resolveOrDefault(c: CmsAdConfig?, e: Boolean): String? {
        if (!e) return null
        if (c == null) return null          // RC henüz gelmedi — bekle
        if (!c.enabled) return null         // admin kapattı
        return c.unitId.ifBlank { null }    // unitId boşsa RC'de tanımsız — gösterme
    }

    val bannerUnitId: StateFlow<String?> = combine(_bannerConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerLibraryUnitId: StateFlow<String?> = combine(_bannerLibraryConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerKurdiUnitId: StateFlow<String?> = combine(_bannerKurdiConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerBlogUnitId: StateFlow<String?> = combine(_bannerBlogConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeFeedUnitId: StateFlow<String?> = combine(_nativeFeedConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeBlogUnitId: StateFlow<String?> = combine(_nativeBlogConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeLibraryUnitId: StateFlow<String?> = combine(_nativeLibraryConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeKurdiUnitId: StateFlow<String?> = combine(_nativeKurdiConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeProfileUnitId: StateFlow<String?> = combine(_nativeProfileConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeSearchUnitId: StateFlow<String?> = combine(_nativeSearchConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerPosition: StateFlow<Int> =
        _bannerConfig.combine(_adsEnabled) { c, _ -> c?.position ?: 5 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    // ── Remote Config'den config yükleme ──────────────────────────────────
    //
    // ESKİ (Firestore): firestore.collection("cms_ads").get(Source.SERVER).await()
    //   → Her açılışta Firestore okuma = fatura
    //   → Offline'da çalışmıyor
    //   → 19 alanlı String serialization (kırılgan)
    //
    // YENİ (Remote Config): remoteConfigManager.fetchAndActivate()
    //   → 12 saatte bir ağ isteği (SDK kendi cache'ini yönetiyor)
    //   → Offline'da son cached değer — hiç fail olmuyor
    //   → JSON parse — güvenli, alan ekleyince format bozulmuyor
    fun loadAdConfigs(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // SDK hazır olana kadar bekle — UMP tamamlandıktan sonra MainActivity
            // notifySdkReady() → sdkReady=true yapar. Burada beklemek, canRequestAds
            // guard'ının yarattığı race condition'ı ortadan kaldırır: init{}'ten gelen
            // çağrı canRequestAds true olduğunda tetiklendiği için güvenli; ama
            // onAppForeground() gibi başka kod yollarından gelen çağrılarda SDK
            // henüz hazır olmayabilir — awaitSdk() bunu garantiler.
            HeftrangApp.sdkReady.first { it }
            // ÖNCEDEN forceRefresh parametresi hiçbir şey yapmıyordu — normal
            // fetchAndActivate() her zaman 12 saatlik cache'e tabiydi. Artık
            // forceRefresh=true → gerçekten cache'i bypass edip anında sunucudan çeker.
            val fetchResult = if (forceRefresh) remoteConfigManager.forceFetchAndActivate()
                               else remoteConfigManager.fetchAndActivate()
            android.util.Log.e("HeftrengAdsDebug", "fetchAndActivate sonucu: $fetchResult (forceRefresh=$forceRefresh)")
            applyRemoteConfigs(preloadAds = true)
        }
    }

    /**
     * Remote Config'den tüm slot değerlerini okur ve StateFlow'ları günceller.
     * Bu fonksiyon sync'tir (ağ isteği yok) — tüm değerler zaten SDK cache'inde.
     */
    private fun applyRemoteConfigs(preloadAds: Boolean) {
        // Global flag
        _adsEnabled.value = remoteConfigManager.isAdsEnabled()

        val newAll = mutableMapOf<String, CmsAdConfig>()

        fun applyBanner(flow: MutableStateFlow<CmsAdConfig?>, key: String, slot: BannerSlot) {
            val config = remoteConfigManager.getAdConfig(key) ?: return
            android.util.Log.e("HeftrengAdsDebug", "$key -> enabled=${config.enabled} unitId=${config.unitId} raw config yüklendi")
            flow.value = config
            newAll[key] = config
            if (preloadAds && config.enabled && _adsEnabled.value) {
                config.unitId.ifBlank { null }
                    ?.let { engine.loadBanner(slot.key(), it, config.bannerSize) }
            }
        }

        fun applyNative(flow: MutableStateFlow<CmsAdConfig?>, key: String, slot: NativeAdSlot) {
            val config = remoteConfigManager.getAdConfig(key) ?: return
            flow.value = config
            newAll[key] = config
            // Native ad havuzu kaldırıldı — her pozisyon kendi istek/release döngüsünü yönetiyor
        }

        applyBanner(_bannerConfig,        RemoteConfigManager.KEY_BANNER_FEED,    BannerSlot.FEED)
        applyBanner(_bannerLibraryConfig, RemoteConfigManager.KEY_BANNER_LIBRARY, BannerSlot.LIB)
        applyBanner(_bannerKurdiConfig,   RemoteConfigManager.KEY_BANNER_KURDI,   BannerSlot.KURDI)
        applyBanner(_bannerBlogConfig,    RemoteConfigManager.KEY_BANNER_BLOG,    BannerSlot.BLOG)

        applyNative(_nativeFeedConfig,    RemoteConfigManager.KEY_NATIVE_FEED,    NativeAdSlot.FEED)
        applyNative(_nativeBlogConfig,    RemoteConfigManager.KEY_NATIVE_BLOG,    NativeAdSlot.BLOG)
        applyNative(_nativeLibraryConfig, RemoteConfigManager.KEY_NATIVE_LIBRARY, NativeAdSlot.LIBRARY)
        applyNative(_nativeKurdiConfig,   RemoteConfigManager.KEY_NATIVE_KURDI,   NativeAdSlot.KURDI)
        applyNative(_nativeProfileConfig, RemoteConfigManager.KEY_NATIVE_PROFILE, NativeAdSlot.PROFILE)
        applyNative(_nativeSearchConfig,  RemoteConfigManager.KEY_NATIVE_SEARCH,  NativeAdSlot.SEARCH)

        // Interstitial
        remoteConfigManager.getAdConfig(RemoteConfigManager.KEY_INTERSTITIAL)?.let { config ->
            val changed = _interstitialConfig.value != config
            _interstitialConfig.value = config
            newAll[RemoteConfigManager.KEY_INTERSTITIAL] = config
            if (preloadAds && changed && config.enabled && _adsEnabled.value) {
                config.unitId.ifBlank { null }?.let {
                    interstitialUnitId = it
                    loadInterstitialAd(it)
                }
            }
        }

        // Rewarded
        remoteConfigManager.getAdConfig(RemoteConfigManager.KEY_REWARDED)?.let { config ->
            val changed = _rewardedConfig.value != config
            _rewardedConfig.value = config
            newAll[RemoteConfigManager.KEY_REWARDED] = config
            syncRemainingRewardedAds(config.dailyLimit)
            if (preloadAds && changed && config.enabled && _adsEnabled.value) {
                config.unitId.ifBlank { null }?.let { preloadRewardedAd(it) }
            }
        }

        _allAdConfigs.value = newAll
    }

    // ── Interstitial ──────────────────────────────────────────────────────
    private var interstitialAd: InterstitialAd? = null
    private var interstitialUnitId: String = ""

    private fun loadInterstitialAd(unitId: String) {
        if (unitId.isBlank()) return
        InterstitialAd.load(
            appContext, unitId, engine.adRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) { interstitialAd = null }
                override fun onAdLoaded(ad: InterstitialAd)         { interstitialAd = ad }
            },
        )
    }

    fun loadInterstitial() {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled) return
        val unitId = config.unitId.ifBlank { null } ?: return
        interstitialUnitId = unitId
        loadInterstitialAd(unitId)
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) { onAdDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onAdDismissed()
                loadInterstitialAd(interstitialUnitId)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                interstitialAd = null
                onAdDismissed()
                loadInterstitialAd(interstitialUnitId)
            }
        }
        ad.show(activity)
    }

    // ── Rewarded ──────────────────────────────────────────────────────────
    private var rewardedAd      : RewardedAd? = null
    private var rewardedUnitId  : String = ""
    private var rewardedLoading : Boolean = false
    private var rewardedRetry   : Int = 0

    fun preloadRewardedAd(unitId: String) {
        if (unitId.isBlank()) return
        rewardedUnitId = unitId
        if (rewardedAd != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            appContext, unitId, engine.adRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd      = ad
                    rewardedLoading = false
                    rewardedRetry   = 0
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd      = null
                    rewardedLoading = false
                    if (rewardedRetry < 4) {
                        rewardedRetry++
                        val delayMs = 8_000L * (1L shl (rewardedRetry - 1).coerceAtMost(2))
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            rewardedLoading = false
                            preloadRewardedAd(unitId)
                        }
                    } else {
                        rewardedRetry = 0
                    }
                }
            },
        )
    }

    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    fun loadRewarded() {
        val config = _rewardedConfig.value ?: return
        if (!config.enabled) return
        val unitId = config.unitId.ifBlank { null } ?: return
        preloadRewardedAd(unitId)
    }

    private val _remainingRewardedAds = MutableStateFlow(3)
    val remainingRewardedAds = _remainingRewardedAds.asStateFlow()

    fun canShowScenario(rewardType: RewardType): Boolean {
        val cfg = _rewardedConfig.value ?: return _remainingRewardedAds.value > 0
        val scenarioEnabled = when (rewardType) {
            RewardType.DOUBLE_XP     -> cfg.scenarioDoubleXp
            RewardType.UNLOCK_LESSON -> cfg.scenarioUnlockLesson
            RewardType.SAVE_STREAK   -> cfg.scenarioSaveStreak
        }
        return scenarioEnabled && _remainingRewardedAds.value > 0
    }

    // dailyLimit: RC'den gelir. RC henüz gelmemişse default 3 kullan.
    // Bu fonksiyon hem RC gelince hem uygulama açılışında çağrılır.
    private fun syncRemainingRewardedAds(dailyLimit: Int = _rewardedConfig.value?.dailyLimit ?: 3) {
        val uid = auth.currentUser?.uid ?: return
        val used = frequencyManager.getCount(uid, "rewarded")
        _remainingRewardedAds.value = (dailyLimit - used).coerceAtLeast(0)
    }

    fun showRewarded(
        activity      : Activity,
        rewardType    : RewardType,
        onRewarded    : (RewardItem, RewardType) -> Unit,
        onDismiss     : () -> Unit = {},
        onLimitReached: () -> Unit = {},
    ) {
        val uid        = auth.currentUser?.uid ?: ""
        val dailyLimit = _rewardedConfig.value?.dailyLimit ?: 3

        if (uid.isNotEmpty() && frequencyManager.isLimitReached(uid, "rewarded", dailyLimit)) {
            _remainingRewardedAds.value = 0
            onLimitReached()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            if (uid.isNotEmpty()) {
                ad.setServerSideVerificationOptions(
                    com.google.android.gms.ads.rewarded.ServerSideVerificationOptions.Builder()
                        .setUserId(uid)
                        .setCustomData(rewardType.name)
                        .build(),
                )
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null; onDismiss()
                    preloadRewardedAd(rewardedUnitId)
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    rewardedAd = null; onDismiss()
                    preloadRewardedAd(rewardedUnitId)
                }
            }
            ad.show(activity) { rewardItem ->
                if (uid.isNotEmpty()) frequencyManager.increment(uid, "rewarded")
                _remainingRewardedAds.value = (_remainingRewardedAds.value - 1).coerceAtLeast(0)
                onRewarded(rewardItem, rewardType)
            }
        } else {
            onDismiss()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────
    fun onAppForeground() {
        viewModelScope.launch {
            if (!ConsentHelper.canRequestAds.value) return@launch
            engine.resumeAllBanners()
            // Arka plandan dönünce Remote Config'i de yenile (cache geçerliyse 0ms)
            loadAdConfigs()
            // Native havuz feed ekranında PositionedNativeAdView tarafından zaten dolduruluyor.
            // Native ad artık per-position lazy-load — burada ekstra çağrı gerekmiyor.
            if (interstitialAd == null && interstitialUnitId.isNotBlank()) loadInterstitialAd(interstitialUnitId)
            if (rewardedAd == null && rewardedUnitId.isNotBlank() && !rewardedLoading) preloadRewardedAd(rewardedUnitId)
        }
    }

    fun onAppBackground() {
        engine.pauseAllBanners()
        // Politika uyumu: arka plana alınmış uygulamada henüz gösterilmemiş
        // native reklamları hemen serbest bırak — STALE_AD_TIMEOUT_MS'i bekleme.
        engine.releaseUnseenNativesOnBackground()
    }

    override fun onCleared() {
        super.onCleared()
        engine.destroyAll()
    }

    init {
        // Uygulama açılışında kalan rewarded hakkını SharedPreferences'tan hemen yükle.
        // RC beklemeye gerek yok — dailyLimit bilinmese de "bugün kaç kullandım" yerel.
        // RC gelince syncRemainingRewardedAds(config.dailyLimit) ile kesin değer güncellenir.
        syncRemainingRewardedAds()

        // Remote Config'i hemen arka planda fetch et (cache geçerliyse 0ms, ağdan ~200ms)
        // Bu, reklam config'ini UMP onayından bağımsız olarak hazır tutar.
        viewModelScope.launch { remoteConfigManager.fetchAndActivate() }

        // canRequestAds true olduğu anda (UMP tamamlandı veya UMP gereksiz) config yükle.
        // ESKİ HATA: init{} içinde önce canRequestAds.value'ya bakıp "true ise hemen çağır,
        // değilse koleksiyona başla" yapısı bir race condition yaratıyordu:
        //   - AdsViewModel, MainActivity UMP callback'inden (notifySdkReady()) ÖNCE
        //     oluşturulursa → canRequestAds.value == false → collect başlar
        //   - Ama collecten sonra UMP callback gelirse → collect tepki verir ✓
        //   - AdsViewModel, notifySdkReady() SONRA oluşturulursa → canRequestAds.value == true
        //     → doğrudan çağırır ✓ (bu yol çalışıyordu)
        //   - Problem: Hilt, ViewModel'i her zaman Activity.onCreate'nin ortasında yaratır;
        //     UMP çağrısı da onCreate'de başlar, ama UMP async — ViewModel UMP'den önce
        //     yaratılıyor, canRequestAds her zaman false başlıyor, collect üzerinden
        //     gidiyor. AMA: collect, "ilk true emisyonu" görünce loadAdConfigs() çağırıyor,
        //     bu tamam. Asıl sorun: loadAdConfigs() içindeki erken return:
        //       if (!ConsentHelper.canRequestAds.value) return@launch
        //     Bu satır collect callback'inden çağrılınca TRUE'dur ama aynı satır
        //     onAppForeground() gibi farklı kod yollarından çağrılınca FALSE olabilir.
        //     Tek bir flag yerine, zaten canRequestAds.first{it} bekleme deseni kullanılıyor.
        viewModelScope.launch {
            ConsentHelper.canRequestAds.collect { canAds ->
                if (canAds) {
                    loadAdConfigs()
                    return@collect
                }
            }
        }
    }
}
