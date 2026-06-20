package com.heftreng.app.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.AdMobTestIds
import com.heftreng.app.data.model.AdMobProdIds
import com.heftreng.app.data.model.CmsAdConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AdsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    // ── Preloaded banner cache ─────────────────────────────────────────────────
    private val _bannerFeedLoaded    = MutableStateFlow(false)
    val bannerFeedLoaded    = _bannerFeedLoaded.asStateFlow()

    private val _bannerLibLoaded     = MutableStateFlow(false)
    val bannerLibLoaded     = _bannerLibLoaded.asStateFlow()

    private val _bannerKurdiLoaded   = MutableStateFlow(false)
    val bannerKurdiLoaded   = _bannerKurdiLoaded.asStateFlow()

    private val _bannerBlogLoaded    = MutableStateFlow(false)
    val bannerBlogLoaded    = _bannerBlogLoaded.asStateFlow()

    var cachedFeedBanner   : AdView? = null; private set
    var cachedLibBanner    : AdView? = null; private set
    var cachedKurdiBanner  : AdView? = null; private set
    var cachedBlogBanner   : AdView? = null; private set

    private var feedBannerSize  : String = ""
    private var libBannerSize   : String = ""
    private var kurdiBannerSize : String = ""
    private var blogBannerSize  : String = ""

    private var feedRetryCount = 0
    private var libRetryCount = 0
    private var kurdiRetryCount = 0
    private var blogRetryCount  = 0
    private val MAX_RETRY_ATTEMPTS = 5
    private val RETRY_DELAY_MS = 10_000L

    fun preloadBanner(unitId: String, slot: BannerSlot, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val currentSize = when (slot) {
            BannerSlot.FEED  -> feedBannerSize
            BannerSlot.LIB   -> libBannerSize
            BannerSlot.KURDI -> kurdiBannerSize
            BannerSlot.BLOG  -> blogBannerSize
        }
        val isAlreadyLoaded = when (slot) {
            BannerSlot.FEED  -> _bannerFeedLoaded.value
            BannerSlot.LIB   -> _bannerLibLoaded.value
            BannerSlot.KURDI -> _bannerKurdiLoaded.value
            BannerSlot.BLOG  -> _bannerBlogLoaded.value
        }
        if (isAlreadyLoaded && currentSize == bannerSize) return
        if (isAlreadyLoaded && currentSize != bannerSize) {
            when (slot) {
                BannerSlot.FEED  -> { _bannerFeedLoaded.value  = false; cachedFeedBanner?.destroy();  cachedFeedBanner  = null }
                BannerSlot.LIB   -> { _bannerLibLoaded.value   = false; cachedLibBanner?.destroy();   cachedLibBanner   = null }
                BannerSlot.KURDI -> { _bannerKurdiLoaded.value = false; cachedKurdiBanner?.destroy(); cachedKurdiBanner = null }
                BannerSlot.BLOG  -> { _bannerBlogLoaded.value  = false; cachedBlogBanner?.destroy();  cachedBlogBanner  = null }
            }
        }
        when (slot) {
            BannerSlot.FEED  -> feedBannerSize  = bannerSize
            BannerSlot.LIB   -> libBannerSize   = bannerSize
            BannerSlot.KURDI -> kurdiBannerSize  = bannerSize
            BannerSlot.BLOG  -> blogBannerSize   = bannerSize
        }
        val adView = AdView(appContext).apply {
            setAdSize(getAdSize(bannerSize))
            adUnitId = unitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    when (slot) {
                        BannerSlot.FEED  -> { feedRetryCount = 0; cachedFeedBanner  = this@apply; _bannerFeedLoaded.value  = true }
                        BannerSlot.LIB   -> { libRetryCount = 0; cachedLibBanner   = this@apply; _bannerLibLoaded.value   = true }
                        BannerSlot.KURDI -> { kurdiRetryCount = 0; cachedKurdiBanner = this@apply; _bannerKurdiLoaded.value = true }
                        BannerSlot.BLOG  -> { blogRetryCount  = 0; cachedBlogBanner  = this@apply; _bannerBlogLoaded.value  = true }
                    }
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    viewModelScope.launch {
                        val retryCount = when (slot) {
                            BannerSlot.FEED -> ++feedRetryCount
                            BannerSlot.LIB -> ++libRetryCount
                            BannerSlot.KURDI -> ++kurdiRetryCount
                            BannerSlot.BLOG -> ++blogRetryCount
                        }
                        if (retryCount <= MAX_RETRY_ATTEMPTS) {
                            val delayMs = RETRY_DELAY_MS * Math.pow(2.0, (retryCount - 1).toDouble()).toLong()
                            delay(delayMs)
                            preloadBanner(unitId, slot, bannerSize)
                        }
                    }
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        when (slot) {
            BannerSlot.FEED  -> { cachedFeedBanner?.destroy(); cachedFeedBanner = adView }
            BannerSlot.LIB  -> { cachedLibBanner?.destroy(); cachedLibBanner = adView }
            BannerSlot.KURDI -> { cachedKurdiBanner?.destroy(); cachedKurdiBanner = adView }
            BannerSlot.BLOG  -> { cachedBlogBanner?.destroy();  cachedBlogBanner  = adView }
        }
    }

    enum class BannerSlot { FEED, LIB, KURDI, BLOG }

    // ── Liste içinde tekrarlanan banner'lar için pozisyon bazlı cache ───────────
    // Tek bir AdView'ı birden fazla liste konumunda paylaşmak (eski mimari)
    // View'ın sürekli söküp-takılmasına ve impression'ın sayılmamasına yol açıyordu.
    // Bunun yerine her liste konumu (key ile) kendi bağımsız AdView'ını yükler ve tutar.
    private val _positionedBanners = mutableMapOf<String, AdView>()
    private val _positionedBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val _positionedBannerRetryCount = mutableMapOf<String, Int>()
    private val _positionedBannerSize = mutableMapOf<String, String>()

    fun positionedBannerLoadedFlow(key: String): StateFlow<Boolean> =
        _positionedBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedBanner(key: String): AdView? = _positionedBanners[key]

    /**
     * Liste içinde belirli bir pozisyonda (key) gösterilecek banner'ı yükler.
     * Her key kendi AdView nesnesine sahiptir; aynı View birden fazla konumda
     * paylaşılmaz, böylece viewability/impression sorunları önlenir.
     */
    fun preloadPositionedBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow = _positionedBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val currentSize = _positionedBannerSize[key]
        if (loadedFlow.value && currentSize == bannerSize) return
        if (loadedFlow.value && currentSize != bannerSize) {
            loadedFlow.value = false
            _positionedBanners[key]?.destroy()
            _positionedBanners.remove(key)
        }
        _positionedBannerSize[key] = bannerSize
        val adView = AdView(appContext).apply {
            setAdSize(getAdSize(bannerSize))
            adUnitId = unitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    _positionedBannerRetryCount[key] = 0
                    loadedFlow.value = true
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    viewModelScope.launch {
                        val retryCount = (_positionedBannerRetryCount[key] ?: 0) + 1
                        _positionedBannerRetryCount[key] = retryCount
                        if (retryCount <= MAX_RETRY_ATTEMPTS) {
                            val delayMs = RETRY_DELAY_MS * Math.pow(2.0, (retryCount - 1).toDouble()).toLong()
                            delay(delayMs)
                            preloadPositionedBanner(key, unitId, bannerSize)
                        }
                    }
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        _positionedBanners[key]?.destroy()
        _positionedBanners[key] = adView
    }

    /** Liste yeniden çekildiğinde / ekrandan çıkıldığında artık kullanılmayan banner'ları temizler. */
    fun releasePositionedBanners(keyPrefix: String? = null) {
        val keysToRemove = if (keyPrefix == null) _positionedBanners.keys.toList()
            else _positionedBanners.keys.filter { it.startsWith(keyPrefix) }
        keysToRemove.forEach { k ->
            _positionedBanners[k]?.destroy()
            _positionedBanners.remove(k)
            _positionedBannerLoaded.remove(k)
            _positionedBannerSize.remove(k)
            _positionedBannerRetryCount.remove(k)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cachedFeedBanner?.destroy()
        cachedLibBanner?.destroy()
        cachedKurdiBanner?.destroy()
        cachedBlogBanner?.destroy()
        cachedNativeFeedAd?.destroy()
        cachedNativeBlogAd?.destroy()
        releasePositionedBanners()
        releasePositionedNatives()
    }

    // ── Konfigürasyonlar (Public API - CmsScreen/KurdiScreen için gerekli) ────────────────
    private val _bannerConfig       = MutableStateFlow<CmsAdConfig?>(null)
    val bannerConfig = _bannerConfig.asStateFlow()

    private val _bannerLibraryConfig = MutableStateFlow<CmsAdConfig?>(null)
    val bannerLibraryConfig = _bannerLibraryConfig.asStateFlow()

    private val _bannerKurdiConfig  = MutableStateFlow<CmsAdConfig?>(null)
    val bannerKurdiConfig = _bannerKurdiConfig.asStateFlow()

    private val _bannerBlogConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val bannerBlogConfig = _bannerBlogConfig.asStateFlow()

    private val _interstitialConfig = MutableStateFlow<CmsAdConfig?>(null)
    val interstitialConfig = _interstitialConfig.asStateFlow()

    private val _rewardedConfig     = MutableStateFlow<CmsAdConfig?>(null)
    val rewardedConfig = _rewardedConfig.asStateFlow()

    private val _nativeFeedConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val nativeFeedConfig = _nativeFeedConfig.asStateFlow()

    private val _nativeBlogConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val nativeBlogConfig = _nativeBlogConfig.asStateFlow()
    
    private val _allAdConfigs = MutableStateFlow<Map<String, CmsAdConfig>>(emptyMap())
    val allAdConfigs = _allAdConfigs.asStateFlow()

    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled = _adsEnabled.asStateFlow()

    // ── Unit ID StateFlows ──────────────────────────────────────────────────
    val bannerUnitId: StateFlow<String?> = combine(_bannerConfig, _adsEnabled) { config, enabled ->
        if (config == null || !config.enabled || !enabled) null
        else if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerLibraryUnitId: StateFlow<String?> = combine(_bannerLibraryConfig, _adsEnabled) { config, enabled ->
        if (config == null || !config.enabled || !enabled) null
        else if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerKurdiUnitId: StateFlow<String?> = combine(_bannerKurdiConfig, _adsEnabled) { config, enabled ->
        if (config == null || !config.enabled || !enabled) null
        else if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerBlogUnitId: StateFlow<String?> = combine(_bannerBlogConfig, _adsEnabled) { config, enabled ->
        if (config == null || !config.enabled || !enabled) null
        else if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeFeedUnitId: StateFlow<String?> = combine(_nativeFeedConfig, _adsEnabled) { config, enabled ->
        if (config == null || !config.enabled || !enabled) null
        else if (config.testMode) AdMobTestIds.NATIVE else AdMobProdIds.NATIVE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nativeBlogUnitId: StateFlow<String?> = combine(_nativeBlogConfig, _adsEnabled) { config, enabled ->
        if (config == null || !config.enabled || !enabled) null
        else if (config.testMode) AdMobTestIds.NATIVE else AdMobProdIds.NATIVE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bannerPosition: StateFlow<Int> = _bannerConfig.map { it?.position ?: 5 }.stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    fun getAdSize(bannerSize: String): com.google.android.gms.ads.AdSize {
        val displayMetrics = appContext.resources.displayMetrics
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return when (bannerSize) {
            "banner"           -> com.google.android.gms.ads.AdSize.BANNER
            "medium_rectangle" -> com.google.android.gms.ads.AdSize.MEDIUM_RECTANGLE
            "large_banner"     -> com.google.android.gms.ads.AdSize.LARGE_BANNER
            else               -> com.google.android.gms.ads.AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, adWidth)
        }
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd:     RewardedAd?     = null

    fun loadAdConfigs() {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("cms_ads").get().await()
                val global = snap.documents.find { it.id == "global" }
                _adsEnabled.value = global?.getBoolean("enabled") ?: true
                snap.documents.forEach { doc ->
                    if (doc.id == "global") return@forEach
                    val d = doc.data ?: return@forEach
                    val config = CmsAdConfig(
                        id                   = doc.id,
                        unitId               = d["unitId"]    as? String  ?: "",
                        enabled              = d["enabled"]   as? Boolean ?: false,
                        testMode             = d["testMode"]  as? Boolean ?: true,
                        position             = (d["position"]  as? Long)?.toInt() ?: 5,
                        frequency            = (d["frequency"] as? Long)?.toInt() ?: 3,
                        adType               = d["adType"]      as? String ?: "banner",
                        bannerSize           = (d["bannerSize"] as? String ?: "adaptive").trim().lowercase(),
                        screens              = (d["screens"] as? String ?: "feed").trim().lowercase()
                    )
                    when (doc.id) {
                        "banner_feed" -> {
                            _bannerConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val unitId = when {
                                    config.testMode            -> AdMobTestIds.BANNER
                                    config.unitId.isNotBlank() -> config.unitId
                                    else                       -> AdMobProdIds.BANNER
                                }
                                preloadBanner(unitId, BannerSlot.FEED, config.bannerSize)
                            }
                        }
                        "banner_library", "banner_lib" -> {
                            _bannerLibraryConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val unitId = when {
                                    config.testMode            -> AdMobTestIds.BANNER
                                    config.unitId.isNotBlank() -> config.unitId
                                    else                       -> AdMobProdIds.BANNER
                                }
                                preloadBanner(unitId, BannerSlot.LIB, config.bannerSize)
                            }
                        }
                        "banner_kurdi" -> {
                            _bannerKurdiConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val unitId = when {
                                    config.testMode            -> AdMobTestIds.BANNER
                                    config.unitId.isNotBlank() -> config.unitId
                                    else                       -> AdMobProdIds.BANNER
                                }
                                preloadBanner(unitId, BannerSlot.KURDI, config.bannerSize)
                            }
                        }
                        "banner_blog" -> {
                            _bannerBlogConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val unitId = when {
                                    config.testMode            -> AdMobTestIds.BANNER
                                    config.unitId.isNotBlank() -> config.unitId
                                    else                       -> AdMobProdIds.BANNER
                                }
                                preloadBanner(unitId, BannerSlot.BLOG, config.bannerSize)
                            }
                        }
                        "interstitial_serial" -> _interstitialConfig.value = config
                        "rewarded_xp" -> {
                            _rewardedConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadRewardedAd(if (config.testMode) AdMobTestIds.REWARDED else AdMobProdIds.REWARDED)
                        }
                        "native_feed" -> {
                            _nativeFeedConfig.value = config
                            android.util.Log.d("AdsVM", "native_feed config geldi: enabled=${config.enabled}, testMode=${config.testMode}, size=${config.bannerSize}, unitId=${config.unitId}")
                            if (config.enabled && _adsEnabled.value) {
                                val unitId = when {
                                    config.testMode          -> AdMobTestIds.NATIVE
                                    config.unitId.isNotBlank() -> config.unitId
                                    else                     -> AdMobProdIds.NATIVE
                                }
                                android.util.Log.d("AdsVM", "native_feed preload başlıyor: unitId=$unitId")
                                preloadNativeAd(unitId, NativeAdSlot.FEED, config.bannerSize)
                            }
                        }
                        "native_blog" -> {
                            _nativeBlogConfig.value = config
                            android.util.Log.d("AdsVM", "native_blog config geldi: enabled=${config.enabled}, testMode=${config.testMode}, unitId=${config.unitId}")
                            if (config.enabled && _adsEnabled.value) {
                                val unitId = when {
                                    config.testMode            -> AdMobTestIds.NATIVE
                                    config.unitId.isNotBlank() -> config.unitId
                                    else                       -> AdMobProdIds.NATIVE
                                }
                                preloadNativeAd(unitId, NativeAdSlot.BLOG, config.bannerSize)
                            }
                        }
                    }
                    _allAdConfigs.value = _allAdConfigs.value.toMutableMap().apply { put(doc.id, config) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Interstitial Ad ────────────────────────────────────────────────────────
    fun loadInterstitialAd(unitId: String) {
        if (unitId.isBlank()) return
        InterstitialAd.load(appContext, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) { interstitialAd = null }
            override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
        })
    }

    // Alias — NavHost kısa adı kullanıyor
    fun loadInterstitial(context: android.content.Context) {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled) return
        val unitId = if (config.testMode) AdMobTestIds.INTERSTITIAL else AdMobProdIds.INTERSTITIAL
        loadInterstitialAd(unitId)
    }

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (interstitialAd == null) { onAdDismissed(); return }
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitialAd = null; onAdDismissed() }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) { interstitialAd = null; onAdDismissed() }
        }
        interstitialAd?.show(activity)
    }

    // Alias — ScreenTracker kısa adı kullanıyor
    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) =
        showInterstitialAd(activity, onAdDismissed)

    // ── Rewarded Ad ────────────────────────────────────────────────────────────
    fun preloadRewardedAd(unitId: String) {
        if (unitId.isBlank()) return
        RewardedAd.load(appContext, unitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
        })
    }

    fun showRewardedAd(activity: Activity, onUserEarnedReward: (RewardItem) -> Unit, onAdDismissed: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { rewardedAd = null; onAdDismissed() }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) { rewardedAd = null; onAdDismissed() }
            }
            rewardedAd?.show(activity) { onUserEarnedReward(it) }
        } else onAdDismissed()
    }

    // ── Native Ad ──────────────────────────────────────────────────────────────
    private val _nativeFeedAd = MutableStateFlow<NativeAd?>(null)
    val nativeFeedAd = _nativeFeedAd.asStateFlow()
    private val _nativeBlogAd = MutableStateFlow<NativeAd?>(null)
    val nativeBlogAd = _nativeBlogAd.asStateFlow()
    var cachedNativeFeedAd: NativeAd? = null; private set
    var cachedNativeBlogAd: NativeAd? = null; private set
    private var nativeFeedRetryCount = 0
    private var nativeBlogRetryCount = 0
    enum class NativeAdSlot { FEED, BLOG }

    private var nativeFeedAdSize: String = "small"
    private var nativeBlogAdSize: String = "small"

    fun preloadNativeAd(unitId: String, slot: NativeAdSlot, adSize: String = "small") {
        if (unitId.isBlank()) return
        
        // Boyut değiştiyse veya henüz yüklenmediyse yükle
        val currentSize = if (slot == NativeAdSlot.FEED) nativeFeedAdSize else nativeBlogAdSize
        val isLoaded = if (slot == NativeAdSlot.FEED) _nativeFeedAd.value != null else _nativeBlogAd.value != null
        
        if (isLoaded && currentSize == adSize) return
        
        if (slot == NativeAdSlot.FEED) nativeFeedAdSize = adSize else nativeBlogAdSize = adSize

        AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                android.util.Log.d("AdsVM", "Native ad yüklendi: slot=$slot")
                when (slot) {
                    NativeAdSlot.FEED -> { cachedNativeFeedAd?.destroy(); cachedNativeFeedAd = nativeAd; _nativeFeedAd.value = nativeAd; nativeFeedRetryCount = 0 }
                    NativeAdSlot.BLOG -> { cachedNativeBlogAd?.destroy(); cachedNativeBlogAd = nativeAd; _nativeBlogAd.value = nativeAd; nativeBlogRetryCount = 0 }
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    android.util.Log.e("AdsVM", "Native ad HATA: slot=$slot code=${adError.code} msg=${adError.message}")
                    viewModelScope.launch {
                        val retryCount = if (slot == NativeAdSlot.FEED) ++nativeFeedRetryCount else ++nativeBlogRetryCount
                        if (retryCount <= MAX_RETRY_ATTEMPTS) {
                            delay(RETRY_DELAY_MS * Math.pow(2.0, (retryCount - 1).toDouble()).toLong())
                            preloadNativeAd(unitId, slot, adSize)
                        }
                    }
                }
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NATIVE AD HAVUZU (POOL) — yavaş yüklenme sorununu çözer
    //  Mantık: pozisyon ekrana gelince sıfırdan yüklemek yerine, önceden
    //  doldurulmuş bir kuyruktan ANINDA çekilir. Kuyruk arka planda sürekli
    //  doldurulur (hedef boyutun altına düşünce otomatik yeniden doldurma).
    // ══════════════════════════════════════════════════════════════════════
    private val POOL_TARGET_SIZE = 3       // havuzda hep hazır bekleyecek reklam sayısı
    private val POOL_MAX_SIZE    = 6       // bellek/limit koruması

    // unitId bazlı ayrı havuzlar — feed ve blog farklı unit kullanabilir
    private val _adPools       = mutableMapOf<String, ArrayDeque<NativeAd>>()
    private val _adPoolFilling = mutableMapOf<String, Boolean>()

    // Pozisyon → havuzdan çekilmiş NativeAd eşlemesi (ekranda kalıcı referans)
    private val _positionedNativeAds = mutableMapOf<String, NativeAd>()
    private val _positionedNativeLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()

    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> =
        _positionedNativeLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedNative(key: String): NativeAd? = _positionedNativeAds[key]

    /**
     * Havuzu önceden doldurur — feed/blog ekranı açılır açılmaz çağrılmalı.
     * Arka planda POOL_TARGET_SIZE kadar reklam yükler, kullanıcı scroll
     * ettiğinde reklamlar zaten hazır olur, bekleme olmaz.
     */
    fun warmUpNativePool(unitId: String) {
        if (unitId.isBlank()) return
        val pool = _adPools.getOrPut(unitId) { ArrayDeque() }
        val needed = POOL_TARGET_SIZE - pool.size
        if (needed <= 0) return
        if (_adPoolFilling[unitId] == true) return
        _adPoolFilling[unitId] = true
        repeat(needed) { fillPoolOnce(unitId) }
    }

    private fun fillPoolOnce(unitId: String) {
        AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                val pool = _adPools.getOrPut(unitId) { ArrayDeque() }
                if (pool.size < POOL_MAX_SIZE) {
                    pool.addLast(nativeAd)
                } else {
                    nativeAd.destroy() // havuz dolu — fazlalığı serbest bırak
                }
                _adPoolFilling[unitId] = pool.size < POOL_TARGET_SIZE
                // Havuz hâlâ hedefin altındaysa devam et
                if (pool.size < POOL_TARGET_SIZE) fillPoolOnce(unitId)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    viewModelScope.launch {
                        delay(RETRY_DELAY_MS)
                        val pool = _adPools[unitId]
                        if (pool != null && pool.size < POOL_TARGET_SIZE) fillPoolOnce(unitId)
                        else _adPoolFilling[unitId] = false
                    }
                }
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    /**
     * Belirli bir pozisyon (key) için havuzdan ANINDA bir reklam çeker.
     * Havuz boşsa eski (yavaş) yola düşer — kullanıcı asla boş kalmaz.
     * Havuzdan bir reklam çekildikçe arka planda otomatik yeniden doldurulur.
     */
    fun preloadPositionedNative(key: String, unitId: String) {
        if (unitId.isBlank()) return
        val loadedFlow = _positionedNativeLoaded.getOrPut(key) { MutableStateFlow(false) }
        if (loadedFlow.value) return

        val pool = _adPools.getOrPut(unitId) { ArrayDeque() }
        val fromPool = pool.removeFirstOrNull()
        if (fromPool != null) {
            // Havuzdan anında çekildi — sıfır bekleme
            _positionedNativeAds[key]?.destroy()
            _positionedNativeAds[key] = fromPool
            loadedFlow.value = true
            // Havuzu otomatik yeniden doldur (arka planda, kullanıcı beklemez)
            warmUpNativePool(unitId)
            return
        }

        // Havuz boş — ilk açılışta veya hızlı ardışık pozisyonlarda olabilir.
        // Doğrudan yükle, aynı zamanda havuzu da ısıt ki bir sonraki sefer
        // havuzdan gelsin.
        warmUpNativePool(unitId)
        AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                _positionedNativeAds[key]?.destroy()
                _positionedNativeAds[key] = nativeAd
                loadedFlow.value = true
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    viewModelScope.launch {
                        delay(RETRY_DELAY_MS)
                        preloadPositionedNative(key, unitId)
                    }
                }
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    fun releasePositionedNatives(keyPrefix: String? = null) {
        val keysToRemove = if (keyPrefix == null) _positionedNativeAds.keys.toList()
            else _positionedNativeAds.keys.filter { it.startsWith(keyPrefix) }
        keysToRemove.forEach { k ->
            _positionedNativeAds[k]?.destroy()
            _positionedNativeAds.remove(k)
            _positionedNativeLoaded.remove(k)
        }
    }

    /** Ekran kapanırken havuzdaki kullanılmayan reklamları da temizle (memory leak önler) */
    fun releaseAdPool(unitId: String? = null) {
        val pools = if (unitId == null) _adPools.values else listOfNotNull(_adPools[unitId])
        pools.forEach { pool ->
            while (pool.isNotEmpty()) pool.removeFirst().destroy()
        }
        if (unitId != null) _adPools.remove(unitId) else _adPools.clear()
    }

    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    fun initPrefs(context: android.content.Context) {}

    private val _remainingRewardedAds = MutableStateFlow(3)
    val canWatchRewardedAd  = MutableStateFlow(true).asStateFlow()
    val remainingRewardedAds = _remainingRewardedAds.asStateFlow()

    fun canShowScenario(rewardType: RewardType): Boolean =
        _remainingRewardedAds.value > 0

    fun showRewarded(
        activity      : android.app.Activity,
        rewardType    : RewardType,
        onRewarded    : (com.google.android.gms.ads.rewarded.RewardItem, RewardType) -> Unit,
        onDismiss     : () -> Unit = {},
        onLimitReached: () -> Unit = {},
    ) {
        if (_remainingRewardedAds.value <= 0) { onLimitReached(); return }
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    onDismiss()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onDismiss()
                }
            }
            rewardedAd?.show(activity) { rewardItem ->
                _remainingRewardedAds.value = (_remainingRewardedAds.value - 1).coerceAtLeast(0)
                onRewarded(rewardItem, rewardType)
            }
        } else {
            onDismiss()
        }
    }
}
