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

    // Yüklenen banner'ın boyutunu takip et — boyut değişince yeniden yükle
    private var feedBannerSize  : String = ""
    private var libBannerSize   : String = ""
    private var kurdiBannerSize : String = ""
    private var blogBannerSize  : String = ""

    private var feedRetryCount = 0
    private var libRetryCount = 0
    private var kurdiRetryCount = 0
    private var blogRetryCount  = 0
    private val MAX_RETRY_ATTEMPTS = 5 // Daha fazla deneme
    private val RETRY_DELAY_MS = 10_000L // 10 saniye bekleme

    fun preloadBanner(unitId: String, slot: BannerSlot, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return

        // Yüklü olan boyutla yeni boyut aynıysa atla
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
        // Boyut değiştiyse zorla yeniden yükle
        if (isAlreadyLoaded && currentSize == bannerSize) return
        // Boyut değiştiyse loaded'ı sıfırla
        if (isAlreadyLoaded && currentSize != bannerSize) {
            when (slot) {
                BannerSlot.FEED  -> { _bannerFeedLoaded.value  = false; cachedFeedBanner?.destroy();  cachedFeedBanner  = null }
                BannerSlot.LIB   -> { _bannerLibLoaded.value   = false; cachedLibBanner?.destroy();   cachedLibBanner   = null }
                BannerSlot.KURDI -> { _bannerKurdiLoaded.value = false; cachedKurdiBanner?.destroy(); cachedKurdiBanner = null }
                BannerSlot.BLOG  -> { _bannerBlogLoaded.value  = false; cachedBlogBanner?.destroy();  cachedBlogBanner  = null }
            }
        }

        // Boyutu kaydet
        when (slot) {
            BannerSlot.FEED  -> feedBannerSize  = bannerSize
            BannerSlot.LIB   -> libBannerSize   = bannerSize
            BannerSlot.KURDI -> kurdiBannerSize  = bannerSize
            BannerSlot.BLOG  -> blogBannerSize   = bannerSize
        }

        val adView = AdView(appContext).apply {
            setAdSize(getAdSize(bannerSize))
            adUnitId = unitId
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                    android.util.Log.w("AdsVM", "Preload failed [${slot}]: ${e.message}")
                    
                    viewModelScope.launch {
                        val retryCount = when (slot) {
                            BannerSlot.FEED -> ++feedRetryCount
                            BannerSlot.LIB -> ++libRetryCount
                            BannerSlot.KURDI -> ++kurdiRetryCount
                            BannerSlot.BLOG -> ++blogRetryCount
                        }
                        
                        if (retryCount <= MAX_RETRY_ATTEMPTS) {
                            // Üstel bekleme (Exponential Backoff): 10s, 20s, 40s...
                            val delayMs = RETRY_DELAY_MS * Math.pow(2.0, (retryCount - 1).toDouble()).toLong()
                            android.util.Log.d("AdsVM", "Retrying preload [${slot}] in ${delayMs/1000}s (Attempt $retryCount)")
                            delay(delayMs)
                            preloadBanner(unitId, slot, bannerSize)
                        }
                    }
                }
            }
            loadAd(
                AdRequest.Builder()
                    .setContentUrl("https://heftreng.app")
                    .build()
            )
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
        interstitialAd = null
        rewardedAd     = null
    }

    // ── Konfigürasyonlar ──────────────────────────────────────────────────────
    private val _bannerConfig       = MutableStateFlow<CmsAdConfig?>(null)
    val bannerConfig = _bannerConfig.asStateFlow()

    private val _bannerLibraryConfig = MutableStateFlow<CmsAdConfig?>(null)
    val bannerLibraryConfig = _bannerLibraryConfig.asStateFlow()

    private val _bannerKurdiConfig  = MutableStateFlow<CmsAdConfig?>(null)
    val bannerKurdiConfig = _bannerKurdiConfig.asStateFlow()

    private val _bannerBlogConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val bannerBlogConfig  = _bannerBlogConfig.asStateFlow()

    private val _interstitialConfig = MutableStateFlow<CmsAdConfig?>(null)
    val interstitialConfig = _interstitialConfig.asStateFlow()

    private val _rewardedConfig     = MutableStateFlow<CmsAdConfig?>(null)
    val rewardedConfig = _rewardedConfig.asStateFlow()

    private val _allBannerConfigs = MutableStateFlow<Map<String, com.heftreng.app.data.model.CmsAdConfig>>(emptyMap())
    val allBannerConfigs = _allBannerConfigs.asStateFlow()

    // ÇÖZÜLDÜ: List<AdScreen> tip uyuşmazlığı ve lambda parametre çıkarımı düzeltildi
    fun getBannerConfigsForScreen(screen: String): List<com.heftreng.app.data.model.CmsAdConfig> {
        if (!_adsEnabled.value) return emptyList()
        val targetScreen = screen.trim().lowercase()
        return _allBannerConfigs.value.values.filter { config ->
            config.enabled && config.screens.contains(targetScreen)
        }.sortedBy { config -> config.position }
    }

    fun getAdSize(bannerSize: String): com.google.android.gms.ads.AdSize {
        val displayMetrics = appContext.resources.displayMetrics
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return when (bannerSize) {
            "banner"           -> com.google.android.gms.ads.AdSize.BANNER
            "medium_rectangle" -> com.google.android.gms.ads.AdSize.MEDIUM_RECTANGLE
            "large_banner"     -> com.google.android.gms.ads.AdSize.LARGE_BANNER
            else               -> com.google.android.gms.ads.AdSize
                .getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, adWidth)
        }
    }

    private val _adsEnabled         = MutableStateFlow(true)
    val adsEnabled = _adsEnabled.asStateFlow()

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

    val bannerPosition: StateFlow<Int> = _bannerConfig
        .map { it?.position ?: 5 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd:     RewardedAd?     = null

    // ── Firestore'dan config yükle ────────────────────────────────────────────
    fun loadAdConfigs() {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("cms_ads").get().await()

                val global = snap.documents.find { it.id == "global" }
                _adsEnabled.value = global?.getBoolean("enabled") ?: true

                snap.documents.forEach { doc ->
                    if (doc.id == "global") return@forEach
                    val d = doc.data ?: return@forEach
                    
                    // ÇÖZÜLDÜ: String alanlar yeni Enum ve List yapılarına güvenli biçimde parse ediliyor
                    val config = CmsAdConfig(
                        id                   = doc.id,
                        unitId               = d["unitId"]    as? String  ?: "",
                        enabled              = d["enabled"]   as? Boolean ?: false,
                        testMode             = d["testMode"]  as? Boolean ?: true,
                        position             = (d["position"]  as? Long)?.toInt() ?: 5,
                        frequency            = (d["frequency"] as? Long)?.toInt() ?: 3,
                        xpReward             = (d["xpReward"]  as? Long)?.toInt() ?: 50,
                        dailyLimit           = (d["dailyLimit"] as? Long)?.toInt() ?: 3,
                        scenarioDoubleXp     = d["scenarioDoubleXp"]     as? Boolean ?: true,
                        scenarioUnlockLesson = d["scenarioUnlockLesson"] as? Boolean ?: true,
                        scenarioSaveStreak   = d["scenarioSaveStreak"]   as? Boolean ?: true,
                        adType               = d["adType"]      as? String ?: "banner",
                        bannerSize           = (d["bannerSize"] as? String ?: "adaptive").trim().lowercase(),
                        placement            = (d["placement"] as? String ?: "in_list").trim().lowercase(),
                        screens              = (d["screens"] as? String ?: "feed").trim().lowercase(),
                        label                = d["label"]       as? String ?: "",
                        bgColor              = d["bgColor"]     as? String ?: "",
                        cornerRadius         = (d["cornerRadius"]  as? Long)?.toInt() ?: 0,
                        paddingTop           = (d["paddingTop"]    as? Long)?.toInt() ?: 0,
                        paddingBottom        = (d["paddingBottom"]  as? Long)?.toInt() ?: 0,
                    )
                    
                    when {
                        doc.id == "banner_feed" -> {
                            _bannerConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.FEED, config.bannerSize)
                            }
                        }
                        doc.id == "banner_library" || doc.id == "banner_lib" -> {
                            _bannerLibraryConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.LIB, config.bannerSize)
                            }
                        }
                        doc.id == "banner_kurdi" -> {
                            _bannerKurdiConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.KURDI, config.bannerSize)
                            }
                        }
                        doc.id == "banner_blog" -> {
                            _bannerBlogConfig.value = config
                            if (config.enabled && _adsEnabled.value) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.BLOG, config.bannerSize)
                            }
                        }
                        doc.id == "interstitial_serial" -> _interstitialConfig.value = config
                        doc.id == "rewarded_xp" -> {
                            _rewardedConfig.value = config
                            // Config gelir gelmez rewarded'ı yükle
                            if (config.enabled && _adsEnabled.value) {
                                loadRewarded(appContext)
                            }
                        }
                        config.adType == "banner" && config.enabled -> {
                            _allBannerConfigs.value = _allBannerConfigs.value + (doc.id to config)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _bannerConfig.value = CmsAdConfig(
                    id       = "banner_feed",
                    unitId   = AdMobTestIds.BANNER,
                    enabled  = true,
                    testMode = true,
                    position = 5,
                )
                e.printStackTrace()
            }
        }
    }

    // ── Interstitial yükle ────────────────────────────────────────────────────
    private var isLoadingInterstitial = false

    fun loadInterstitial(context: Context) {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled || !_adsEnabled.value) return
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true

        val unitId = if (config.testMode) AdMobTestIds.INTERSTITIAL else AdMobProdIds.INTERSTITIAL
        if (unitId.isBlank()) { isLoadingInterstitial = false; return }

        InterstitialAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismiss: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onDismiss()
                    loadInterstitial(activity)
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    interstitialAd = null
                    onDismiss()
                    loadInterstitial(activity)
                }
            }
            ad.show(activity)
        } else {
            onDismiss()
            if (!isLoadingInterstitial) loadInterstitial(activity)
        }
    }

    // ── Günlük ödüllü reklam sayacı (SharedPreferences) ─────────────────────
    private var prefs: android.content.SharedPreferences? = null

    fun initPrefs(context: android.content.Context) {
        prefs = context.getSharedPreferences("heft_ads", android.content.Context.MODE_PRIVATE)
        resetDailyCounterIfNeeded()
    }

    private fun resetDailyCounterIfNeeded() {
        val p = prefs ?: return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        if (p.getString("reward_date", "") != today) {
            p.edit().putString("reward_date", today).putInt("reward_count", 0).apply()
        }
    }

    private val DAILY_LIMIT get() = _rewardedConfig.value?.dailyLimit ?: 3

    val dailyRewardCount: Int get() {
        resetDailyCounterIfNeeded()
        return prefs?.getInt("reward_count", 0) ?: 0
    }
    val canWatchRewardedAd: Boolean get() = dailyRewardCount < DAILY_LIMIT
    val remainingRewardedAds: Int get() = (DAILY_LIMIT - dailyRewardCount).coerceAtLeast(0)

    val isDoubleXpEnabled     : Boolean get() = _rewardedConfig.value?.scenarioDoubleXp     ?: true
    val isUnlockLessonEnabled : Boolean get() = _rewardedConfig.value?.scenarioUnlockLesson ?: true
    val isSaveStreakEnabled    : Boolean get() = _rewardedConfig.value?.scenarioSaveStreak   ?: true

    fun canShowScenario(type: RewardType): Boolean {
        if (!canWatchRewardedAd) return false
        return when (type) {
            RewardType.DOUBLE_XP      -> isDoubleXpEnabled
            RewardType.UNLOCK_LESSON  -> isUnlockLessonEnabled
            RewardType.SAVE_STREAK    -> isSaveStreakEnabled
        }
    }

    private fun incrementDailyCount() {
        val p = prefs ?: return
        p.edit().putInt("reward_count", dailyRewardCount + 1).apply()
    }

    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    // ── Rewarded yükle ────────────────────────────────────────────────────────
    fun loadRewarded(context: Context) {
        val config = _rewardedConfig.value ?: return
        if (!config.enabled || !_adsEnabled.value) return
        if (rewardedAd != null) return

        val unitId = if (config.testMode) AdMobTestIds.REWARDED else AdMobProdIds.REWARDED
        if (unitId.isBlank()) return

        RewardedAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    android.util.Log.w("AdsVM", "Rewarded yüklenemedi: ${error.message} — 5sn sonra tekrar denenecek")
                    viewModelScope.launch {
                        delay(5000L)
                        loadRewarded(context)
                    }
                }
            }
        )
    }

    fun showRewarded(
        activity   : Activity,
        rewardType : RewardType = RewardType.DOUBLE_XP,
        onRewarded : (type: RewardType, xp: Int) -> Unit,
        onDismiss  : () -> Unit = {},
        onLimitReached: () -> Unit = {},
    ) {
        if (!canShowScenario(rewardType)) { onLimitReached(); return }
        val ad     = rewardedAd ?: run { onDismiss(); loadRewarded(activity); return }
        val config = _rewardedConfig.value

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onDismiss()
                loadRewarded(activity)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                rewardedAd = null
                onDismiss()
                loadRewarded(activity)
            }
        }
        ad.show(activity) {
            incrementDailyCount()
            onRewarded(rewardType, config?.xpReward ?: 50)
        }
    }

    fun showRewarded(activity: Activity, onRewarded: (Int) -> Unit, onDismiss: () -> Unit = {}) {
        showRewarded(
            activity    = activity,
            rewardType  = RewardType.DOUBLE_XP,
            onRewarded  = { _, xp -> onRewarded(xp) },
            onDismiss   = onDismiss,
        )
    }
}
