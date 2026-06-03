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
    // Her Banner AdView için ayrı cache — Compose reuse hatasını önler
    private val _bannerFeedLoaded    = MutableStateFlow(false)
    val bannerFeedLoaded    = _bannerFeedLoaded.asStateFlow()

    private val _bannerLibLoaded     = MutableStateFlow(false)
    val bannerLibLoaded     = _bannerLibLoaded.asStateFlow()

    private val _bannerKurdiLoaded   = MutableStateFlow(false)
    val bannerKurdiLoaded   = _bannerKurdiLoaded.asStateFlow()

    // Preloaded AdView örnekleri — ViewModel ömrünce yaşar, Compose her yeniden
    // çizildiğinde yeni reklam istemek yerine bu hazır nesneyi kullanır
    var cachedFeedBanner   : AdView? = null; private set
    var cachedLibBanner    : AdView? = null; private set
    var cachedKurdiBanner  : AdView? = null; private set

    fun preloadBanner(unitId: String, slot: BannerSlot) {
        if (unitId.isBlank()) return
        val adView = AdView(appContext).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            adUnitId = unitId
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    when (slot) {
                        BannerSlot.FEED  -> { cachedFeedBanner  = this@apply; _bannerFeedLoaded.value  = true }
                        BannerSlot.LIB   -> { cachedLibBanner   = this@apply; _bannerLibLoaded.value   = true }
                        BannerSlot.KURDI -> { cachedKurdiBanner = this@apply; _bannerKurdiLoaded.value = true }
                    }
                }
                override fun onAdFailedToLoad(e: com.google.android.gms.ads.LoadAdError) {
                    android.util.Log.w("AdsVM", "Preload failed [${slot}]: ${e.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        // Önceki cache'i temizle
        when (slot) {
            BannerSlot.FEED  -> { cachedFeedBanner?.destroy(); cachedFeedBanner = adView }
            BannerSlot.LIB   -> { cachedLibBanner?.destroy(); cachedLibBanner = adView }
            BannerSlot.KURDI -> { cachedKurdiBanner?.destroy(); cachedKurdiBanner = adView }
        }
    }

    enum class BannerSlot { FEED, LIB, KURDI }

    override fun onCleared() {
        super.onCleared()
        cachedFeedBanner?.destroy()
        cachedLibBanner?.destroy()
        cachedKurdiBanner?.destroy()
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

    private val _interstitialConfig = MutableStateFlow<CmsAdConfig?>(null)
    val interstitialConfig = _interstitialConfig.asStateFlow()

    private val _rewardedConfig     = MutableStateFlow<CmsAdConfig?>(null)
    val rewardedConfig = _rewardedConfig.asStateFlow()

    // ── Global kill switch ────────────────────────────────────────────────────
    private val _adsEnabled         = MutableStateFlow(true)
    val adsEnabled = _adsEnabled.asStateFlow()

    // ── Banner unit ID'leri ───────────────────────────────────────────────────
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

    val bannerPosition: StateFlow<Int> = _bannerConfig
        .map { it?.position ?: 5 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    // ── Yüklenmiş reklamlar ───────────────────────────────────────────────────
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
                    )
                    when (doc.id) {
                        "banner_feed" -> {
                            _bannerConfig.value = config
                            if (config.enabled) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.FEED)
                            }
                        }
                        "banner_library" -> {
                            _bannerLibraryConfig.value = config
                            if (config.enabled) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.LIB)
                            }
                        }
                        "banner_kurdi" -> {
                            _bannerKurdiConfig.value = config
                            if (config.enabled) {
                                val uid = if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
                                preloadBanner(uid, BannerSlot.KURDI)
                            }
                        }
                        "interstitial_serial" -> _interstitialConfig.value = config
                        "rewarded_xp"         -> _rewardedConfig.value     = config
                    }
                }
            } catch (e: Exception) {
                // Firestore erişilemiyorsa test modunda varsayılan banner göster
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
    fun loadInterstitial(context: Context) {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled || !_adsEnabled.value) return

        val unitId = if (config.testMode) AdMobTestIds.INTERSTITIAL else AdMobProdIds.INTERSTITIAL
        if (unitId.isBlank()) return

        InterstitialAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
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
                }
            }
            ad.show(activity)
        } else {
            onDismiss()
        }
    }

    // ── Günlük ödüllü reklam sayacı (SharedPreferences) ─────────────────────
    // Her gece 00:00'da sıfırlanır — AdMob frequency cap'in kod tarafı kalkanı
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

    // Günlük limit — CMS'den gelir, yoksa varsayılan 3
    private val DAILY_LIMIT get() = _rewardedConfig.value?.dailyLimit ?: 3

    val dailyRewardCount: Int get() {
        resetDailyCounterIfNeeded()
        return prefs?.getInt("reward_count", 0) ?: 0
    }
    val canWatchRewardedAd: Boolean get() = dailyRewardCount < DAILY_LIMIT

    // Kaç hak kaldı (UI badge için)
    val remainingRewardedAds: Int get() = (DAILY_LIMIT - dailyRewardCount).coerceAtLeast(0)

    // Senaryo aktif mi? (CMS'den kontrol)
    val isDoubleXpEnabled     : Boolean get() = _rewardedConfig.value?.scenarioDoubleXp     ?: true
    val isUnlockLessonEnabled : Boolean get() = _rewardedConfig.value?.scenarioUnlockLesson ?: true
    val isSaveStreakEnabled    : Boolean get() = _rewardedConfig.value?.scenarioSaveStreak   ?: true

    // Belirli bir senaryo için reklam izlenebilir mi?
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

    // ── Senaryo türleri ───────────────────────────────────────────────────────
    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    // ── Rewarded yükle ────────────────────────────────────────────────────────
    fun loadRewarded(context: Context) {
        val config = _rewardedConfig.value ?: return
        if (!config.enabled || !_adsEnabled.value) return

        val unitId = if (config.testMode) AdMobTestIds.REWARDED else AdMobProdIds.REWARDED
        if (unitId.isBlank()) return

        RewardedAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
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
        val ad     = rewardedAd ?: run { onDismiss(); return }
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
            }
        }
        ad.show(activity) {
            incrementDailyCount()
            onRewarded(rewardType, config?.xpReward ?: 50)
        }
    }

    // Eski imza — geriye uyumluluk için (FeedScreen vs. çağırıyorsa çalışmaya devam eder)
    fun showRewarded(activity: Activity, onRewarded: (Int) -> Unit, onDismiss: () -> Unit = {}) {
        showRewarded(
            activity    = activity,
            rewardType  = RewardType.DOUBLE_XP,
            onRewarded  = { _, xp -> onRewarded(xp) },
            onDismiss   = onDismiss,
        )
    }
}
