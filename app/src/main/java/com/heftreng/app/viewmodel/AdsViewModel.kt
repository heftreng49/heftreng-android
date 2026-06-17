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

    override fun onCleared() {
        super.onCleared()
        cachedFeedBanner?.destroy()
        cachedLibBanner?.destroy()
        cachedKurdiBanner?.destroy()
        cachedBlogBanner?.destroy()
        cachedNativeFeedAd?.destroy()
        cachedNativeBlogAd?.destroy()
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
                            if (config.enabled && _adsEnabled.value) preloadBanner(if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER, BannerSlot.FEED, config.bannerSize)
                        }
                        "banner_library", "banner_lib" -> {
                            _bannerLibraryConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadBanner(if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER, BannerSlot.LIB, config.bannerSize)
                        }
                        "banner_kurdi" -> {
                            _bannerKurdiConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadBanner(if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER, BannerSlot.KURDI, config.bannerSize)
                        }
                        "banner_blog" -> {
                            _bannerBlogConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadBanner(if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER, BannerSlot.BLOG, config.bannerSize)
                        }
                        "interstitial_serial" -> _interstitialConfig.value = config
                        "rewarded_xp" -> {
                            _rewardedConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadRewardedAd(if (config.testMode) AdMobTestIds.REWARDED else AdMobProdIds.REWARDED)
                        }
                        "native_feed" -> {
                            _nativeFeedConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadNativeAd(if (config.testMode) AdMobTestIds.NATIVE else AdMobProdIds.NATIVE, NativeAdSlot.FEED, config.bannerSize)
                        }
                        "native_blog" -> {
                            _nativeBlogConfig.value = config
                            if (config.enabled && _adsEnabled.value) preloadNativeAd(if (config.testMode) AdMobTestIds.NATIVE else AdMobProdIds.NATIVE, NativeAdSlot.BLOG, config.bannerSize)
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

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (interstitialAd == null) { onAdDismissed(); return }
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitialAd = null; onAdDismissed() }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) { interstitialAd = null; onAdDismissed() }
        }
        interstitialAd?.show(activity)
    }

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
                when (slot) {
                    NativeAdSlot.FEED -> { cachedNativeFeedAd?.destroy(); cachedNativeFeedAd = nativeAd; _nativeFeedAd.value = nativeAd; nativeFeedRetryCount = 0 }
                    NativeAdSlot.BLOG -> { cachedNativeBlogAd?.destroy(); cachedNativeBlogAd = nativeAd; _nativeBlogAd.value = nativeAd; nativeBlogRetryCount = 0 }
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
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

    // ── Rewarded Ads Stats (KurdiScreen için) ──────────────────────────────────
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
