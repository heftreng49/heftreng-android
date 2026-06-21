package com.heftreng.app.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.heftreng.app.ads.AdEngine
import com.heftreng.app.data.model.AdMobProdIds
import com.heftreng.app.data.model.AdMobTestIds
import com.heftreng.app.data.model.CmsAdConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdsViewModel — Heftreng'in reklam yönetim katmanı.
 *
 *  Bu sınıf artık tüm yükleme/retry/havuz mantığını [AdEngine]'e devreder.
 *  Burada sadece şunlar var:
 *   1. CMS'den (Firestore cms_ads) config okuma — cache-first, TTL'li
 *   2. Config'i StateFlow olarak ekranlara sunma
 *   3. Ekranların kullandığı ince bir "slot adı" sözlüğü (geriye dönük
 *      uyumluluk için BannerSlot/NativeAdSlot enum'ları korunuyor, ama
 *      içeride hepsi tek bir String key'e çevrilip AdEngine'e gidiyor)
 * ═══════════════════════════════════════════════════════════════════════════
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val engine = AdEngine(appContext, viewModelScope)

    // ── Cache TTL — cms_ads nadiren değişir, 30 dakikada bir server'a git ──────
    private val ADS_CONFIG_TTL_MS = 30L * 60L * 1000L
    private var lastServerFetchMs = 0L

    // ═════════════════════════ SLOT TANIMLARI ════════════════════════════════
    // Eski enum'lar korunuyor (ekranlar bunu kullanıyor) — içeride sadece
    // birer String key'e çevriliyor, AdEngine bunları hiç bilmiyor.
    enum class BannerSlot { FEED, LIB, KURDI, BLOG }
    enum class NativeAdSlot { FEED, BLOG }

    private fun BannerSlot.key() = when (this) {
        BannerSlot.FEED  -> "banner_feed"
        BannerSlot.LIB   -> "banner_library"
        BannerSlot.KURDI -> "banner_kurdi"
        BannerSlot.BLOG  -> "banner_blog"
    }

    private fun NativeAdSlot.key() = when (this) {
        NativeAdSlot.FEED -> "native_feed"
        NativeAdSlot.BLOG -> "native_blog"
    }

    // ═════════════════════════ BANNER — public API ═══════════════════════════
    // Tek satırlık delegasyon — gerçek iş AdEngine'de.
    val bannerFeedLoaded    get() = engine.bannerLoadedFlow(BannerSlot.FEED.key())
    val bannerLibLoaded     get() = engine.bannerLoadedFlow(BannerSlot.LIB.key())
    val bannerKurdiLoaded   get() = engine.bannerLoadedFlow(BannerSlot.KURDI.key())
    val bannerBlogLoaded    get() = engine.bannerLoadedFlow(BannerSlot.BLOG.key())

    val cachedFeedBanner    : AdView? get() = engine.cachedBanner(BannerSlot.FEED.key())
    val cachedLibBanner     : AdView? get() = engine.cachedBanner(BannerSlot.LIB.key())
    val cachedKurdiBanner   : AdView? get() = engine.cachedBanner(BannerSlot.KURDI.key())
    val cachedBlogBanner    : AdView? get() = engine.cachedBanner(BannerSlot.BLOG.key())

    fun preloadBanner(unitId: String, slot: BannerSlot, bannerSize: String = "adaptive") =
        engine.loadBanner(slot.key(), unitId, bannerSize)

    fun getAdSize(bannerSize: String): AdSize = engine.resolveAdSize(bannerSize)

    // ── Pozisyon bazlı (liste içi tekrarlanan) banner ─────────────────────────
    fun positionedBannerLoadedFlow(key: String): StateFlow<Boolean> = engine.positionedBannerLoadedFlow(key)
    fun cachedPositionedBanner(key: String): AdView? = engine.cachedPositionedBanner(key)
    fun preloadPositionedBanner(key: String, unitId: String, bannerSize: String = "adaptive") =
        engine.loadPositionedBanner(key, unitId, bannerSize)
    fun releasePositionedBanners(keyPrefix: String? = null) = engine.releasePositionedBanners(keyPrefix)

    // ═════════════════════════ NATIVE — public API ════════════════════════════
    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> = engine.positionedNativeLoadedFlow(key)
    fun cachedPositionedNative(key: String): NativeAd? = engine.cachedPositionedNative(key)
    fun preloadPositionedNative(key: String, unitId: String) = engine.preloadPositionedNative(key, unitId)
    fun releasePositionedNatives(keyPrefix: String? = null) = engine.releasePositionedNatives(keyPrefix)
    fun warmUpNativePool(unitId: String) = engine.warmUpNativePool(unitId)
    fun releaseAdPool(unitId: String? = null) = engine.releaseAdPool(unitId)

    // ═════════════════════════ KONFİGÜRASYONLAR (CMS) ═════════════════════════
    private val _bannerConfig        = MutableStateFlow<CmsAdConfig?>(null)
    val bannerConfig = _bannerConfig.asStateFlow()
    private val _bannerLibraryConfig = MutableStateFlow<CmsAdConfig?>(null)
    val bannerLibraryConfig = _bannerLibraryConfig.asStateFlow()
    private val _bannerKurdiConfig   = MutableStateFlow<CmsAdConfig?>(null)
    val bannerKurdiConfig = _bannerKurdiConfig.asStateFlow()
    private val _bannerBlogConfig    = MutableStateFlow<CmsAdConfig?>(null)
    val bannerBlogConfig = _bannerBlogConfig.asStateFlow()
    private val _interstitialConfig  = MutableStateFlow<CmsAdConfig?>(null)
    val interstitialConfig = _interstitialConfig.asStateFlow()
    private val _rewardedConfig      = MutableStateFlow<CmsAdConfig?>(null)
    val rewardedConfig = _rewardedConfig.asStateFlow()
    private val _nativeFeedConfig    = MutableStateFlow<CmsAdConfig?>(null)
    val nativeFeedConfig = _nativeFeedConfig.asStateFlow()
    private val _nativeBlogConfig    = MutableStateFlow<CmsAdConfig?>(null)
    val nativeBlogConfig = _nativeBlogConfig.asStateFlow()

    private val _allAdConfigs = MutableStateFlow<Map<String, CmsAdConfig>>(emptyMap())
    val allAdConfigs = _allAdConfigs.asStateFlow()

    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled = _adsEnabled.asStateFlow()

    // ÖNEMLİ — "Reklamlar yüklenmiyor / çok geç geliyor" sorununun asıl kaynağı:
    // Eskiden bu Flow'lar CMS config (Firestore) gelene kadar hep `null` dönüyordu,
    // ekranlar da `if (unitId != null)` diye kontrol ettiği için reklam yükleme
    // İSTEĞİ bile atılmıyordu. Yani her açılışta: Firestore round-trip + AdMob
    // round-trip art arda (paralel değil!) bekleniyordu. Artık config gelene kadar
    // ANINDA prod unit ID ile yükleme başlıyor; CMS gelince (testMode/disabled/özel
    // unitId) varsa kararını uygular — AdEngine artık unit ID değişimini de
    // algılayıp gerekirse swap ediyor (bkz. AdEngine.loadBanner/preloadPositionedNative).
    val bannerUnitId: StateFlow<String?> = combine(_bannerConfig, _adsEnabled) { c, e ->
        if (!e) null
        else if (c == null) AdMobProdIds.BANNER
        else engine.resolveUnitId(c, AdMobTestIds.BANNER, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val bannerLibraryUnitId: StateFlow<String?> = combine(_bannerLibraryConfig, _adsEnabled) { c, e ->
        if (!e) null
        else if (c == null) AdMobProdIds.BANNER
        else engine.resolveUnitId(c, AdMobTestIds.BANNER, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val bannerKurdiUnitId: StateFlow<String?> = combine(_bannerKurdiConfig, _adsEnabled) { c, e ->
        if (!e) null
        else if (c == null) AdMobProdIds.BANNER
        else engine.resolveUnitId(c, AdMobTestIds.BANNER, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val bannerBlogUnitId: StateFlow<String?> = combine(_bannerBlogConfig, _adsEnabled) { c, e ->
        if (!e) null
        else if (c == null) AdMobProdIds.BANNER
        else engine.resolveUnitId(c, AdMobTestIds.BANNER, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val nativeFeedUnitId: StateFlow<String?> = combine(_nativeFeedConfig, _adsEnabled) { c, e ->
        if (!e) null
        else if (c == null) AdMobProdIds.NATIVE
        else engine.resolveUnitId(c, AdMobTestIds.NATIVE, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val nativeBlogUnitId: StateFlow<String?> = combine(_nativeBlogConfig, _adsEnabled) { c, e ->
        if (!e) null
        else if (c == null) AdMobProdIds.NATIVE
        else engine.resolveUnitId(c, AdMobTestIds.NATIVE, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val bannerPosition: StateFlow<Int> =
        _bannerConfig.combine(_adsEnabled) { c, _ -> c?.position ?: 5 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    // ── loadAdConfigs: Hibrit Cache + Server ──────────────────────────────────
    // 1. Cache'den anlık yükle (0 gecikme), 2. TTL dolduysa server'dan tazele.
    fun loadAdConfigs(forceServer: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val ttlExpired = (now - lastServerFetchMs) > ADS_CONFIG_TTL_MS

            try {
                val cacheSnap = firestore.collection("cms_ads").get(Source.CACHE).await()
                applyAdConfigs(cacheSnap, preloadAds = true)
            } catch (_: Exception) { /* cache yok — ilk kurulum, server'a geçilecek */ }

            if (forceServer || ttlExpired || lastServerFetchMs == 0L) {
                try {
                    val serverSnap = firestore.collection("cms_ads").get(Source.SERVER).await()
                    lastServerFetchMs = now
                    applyAdConfigs(serverSnap, preloadAds = true)
                } catch (_: Exception) { /* server'a ulaşılamadı — cache ile devam */ }
            }
        }
    }

    private fun applyAdConfigs(snap: com.google.firebase.firestore.QuerySnapshot, preloadAds: Boolean) {
        val global = snap.documents.find { it.id == "global" }
        _adsEnabled.value = global?.getBoolean("enabled") ?: true

        val newAllConfigs = _allAdConfigs.value.toMutableMap()

        snap.documents.forEach { doc ->
            if (doc.id == "global") return@forEach
            val d = doc.data ?: return@forEach
            val config = CmsAdConfig(
                id          = doc.id,
                unitId      = d["unitId"]    as? String  ?: "",
                enabled     = d["enabled"]   as? Boolean ?: false,
                testMode    = d["testMode"]  as? Boolean ?: true,
                position    = (d["position"]  as? Long)?.toInt() ?: 5,
                frequency   = (d["frequency"] as? Long)?.toInt() ?: 3,
                xpReward    = (d["xpReward"]   as? Long)?.toInt() ?: 50,
                dailyLimit  = (d["dailyLimit"] as? Long)?.toInt() ?: 3,
                scenarioDoubleXp     = d["scenarioDoubleXp"]     as? Boolean ?: true,
                scenarioUnlockLesson = d["scenarioUnlockLesson"] as? Boolean ?: true,
                scenarioSaveStreak   = d["scenarioSaveStreak"]   as? Boolean ?: true,
                adType      = d["adType"]      as? String ?: "banner",
                bannerSize  = (d["bannerSize"] as? String ?: "adaptive").trim().lowercase(),
                placement   = d["placement"]   as? String ?: "in_list",
                screens     = (d["screens"] as? String ?: "feed").trim().lowercase(),
                label       = d["label"]       as? String ?: "",
                bgColor     = d["bgColor"]     as? String ?: "",
                cornerRadius  = (d["cornerRadius"]  as? Long)?.toInt() ?: 0,
                paddingTop    = (d["paddingTop"]    as? Long)?.toInt() ?: 0,
                paddingBottom = (d["paddingBottom"] as? Long)?.toInt() ?: 0,
            )

            when (doc.id) {
                "banner_feed" -> applyBannerConfig(_bannerConfig, config, BannerSlot.FEED, preloadAds)
                "banner_library", "banner_lib" -> applyBannerConfig(_bannerLibraryConfig, config, BannerSlot.LIB, preloadAds)
                "banner_kurdi" -> applyBannerConfig(_bannerKurdiConfig, config, BannerSlot.KURDI, preloadAds)
                "banner_blog"  -> applyBannerConfig(_bannerBlogConfig, config, BannerSlot.BLOG, preloadAds)
                "interstitial_serial" -> _interstitialConfig.value = config
                "rewarded_xp" -> {
                    val changed = _rewardedConfig.value != config
                    _rewardedConfig.value = config
                    if (preloadAds && changed && config.enabled && _adsEnabled.value) {
                        engine.resolveUnitId(config, AdMobTestIds.REWARDED, AdMobProdIds.REWARDED)
                            ?.let { preloadRewardedAd(it) }
                    }
                }
                "native_feed" -> applyNativeConfig(_nativeFeedConfig, config, NativeAdSlot.FEED, preloadAds)
                "native_blog" -> applyNativeConfig(_nativeBlogConfig, config, NativeAdSlot.BLOG, preloadAds)
            }
            newAllConfigs[doc.id] = config
        }
        _allAdConfigs.value = newAllConfigs
    }

    private fun applyBannerConfig(target: MutableStateFlow<CmsAdConfig?>, config: CmsAdConfig, slot: BannerSlot, preloadAds: Boolean) {
        val changed = target.value != config
        target.value = config
        if (preloadAds && changed && config.enabled && _adsEnabled.value) {
            engine.resolveUnitId(config, AdMobTestIds.BANNER, AdMobProdIds.BANNER)
                ?.let { engine.loadBanner(slot.key(), it, config.bannerSize) }
        }
    }

    private fun applyNativeConfig(target: MutableStateFlow<CmsAdConfig?>, config: CmsAdConfig, slot: NativeAdSlot, preloadAds: Boolean) {
        val changed = target.value != config
        target.value = config
        if (preloadAds && changed && config.enabled && _adsEnabled.value) {
            engine.resolveUnitId(config, AdMobTestIds.NATIVE, AdMobProdIds.NATIVE)
                ?.let { engine.warmUpNativePool(it) }
        }
    }

    // ═════════════════════════ INTERSTITIAL ════════════════════════════════
    private var interstitialAd: InterstitialAd? = null

    fun loadInterstitialAd(unitId: String) {
        if (unitId.isBlank()) return
        InterstitialAd.load(appContext, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) { interstitialAd = null }
            override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
        })
    }

    fun loadInterstitial(context: android.content.Context) {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled) return
        loadInterstitialAd(if (config.testMode) AdMobTestIds.INTERSTITIAL else AdMobProdIds.INTERSTITIAL)
    }

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (interstitialAd == null) { onAdDismissed(); return }
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitialAd = null; onAdDismissed() }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) { interstitialAd = null; onAdDismissed() }
        }
        interstitialAd?.show(activity)
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) = showInterstitialAd(activity, onAdDismissed)

    // ═════════════════════════ REWARDED ═════════════════════════════════════
    private var rewardedAd: RewardedAd? = null

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

    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    fun initPrefs(context: android.content.Context) {}

    private val _remainingRewardedAds = MutableStateFlow(3)
    val canWatchRewardedAd   = MutableStateFlow(true).asStateFlow()
    val remainingRewardedAds = _remainingRewardedAds.asStateFlow()

    fun canShowScenario(rewardType: RewardType): Boolean = _remainingRewardedAds.value > 0

    fun showRewarded(
        activity      : Activity,
        rewardType    : RewardType,
        onRewarded    : (RewardItem, RewardType) -> Unit,
        onDismiss     : () -> Unit = {},
        onLimitReached: () -> Unit = {},
    ) {
        if (_remainingRewardedAds.value <= 0) { onLimitReached(); return }
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { rewardedAd = null; onDismiss() }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) { rewardedAd = null; onDismiss() }
            }
            rewardedAd?.show(activity) { rewardItem ->
                _remainingRewardedAds.value = (_remainingRewardedAds.value - 1).coerceAtLeast(0)
                onRewarded(rewardItem, rewardType)
            }
        } else onDismiss()
    }

    override fun onCleared() {
        super.onCleared()
        engine.destroyAll()
    }
}
