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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.heftreng.app.ads.AdEngine
import com.heftreng.app.ads.AdFrequencyManager
import com.heftreng.app.data.model.AdMobProdIds
import com.heftreng.app.data.model.CmsAdConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
 *  AdsViewModel — Heftreng reklam yönetim katmanı.
 *
 *  Sorumluluklar:
 *   1. CMS'den (Firestore cms_ads) config okuma — cache-first, 30dk TTL
 *   2. Config'i StateFlow olarak ekranlara sunma
 *   3. AdEngine ve AdFrequencyManager'a delege etme
 *
 *  Önemli kural: loadAdConfigs() sadece init{} bloğunda çağrılır.
 *  Ekranlar (FeedScreen vb.) ARTIK kendi LaunchedEffect'lerinde bu fonksiyonu
 *  çağırmamalı — çift Firestore isteği ve gereksiz yeniden yükleme olur.
 *  Pull-to-refresh veya "zorla güncelle" için loadAdConfigs(forceServer=true)
 *  kullanılır.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val engine           = AdEngine(appContext, viewModelScope)
    private val frequencyManager = AdFrequencyManager(appContext, firestore, viewModelScope)

    // ── SharedPreferences kalıcı config cache'i ────────────────────────────
    // Bir sonraki açılışta Firestore round-trip'i beklemeden doğru unit ID ile
    // hemen yüklemeye başlamak için son bilinen config buraya yazılır.
    private val adPrefs = appContext.getSharedPreferences(
        "heftreng_ad_config_cache",
        android.content.Context.MODE_PRIVATE,
    )
    private val PREF_SEP = "\u0001"

    private fun persistConfig(docId: String, c: CmsAdConfig) {
        val raw = listOf(
            c.unitId, c.enabled, c.testMode, c.position, c.frequency, c.xpReward, c.dailyLimit,
            c.scenarioDoubleXp, c.scenarioUnlockLesson, c.scenarioSaveStreak, c.adType, c.bannerSize,
            c.placement, c.screens, c.label, c.bgColor, c.cornerRadius, c.paddingTop, c.paddingBottom,
        ).joinToString(PREF_SEP)
        adPrefs.edit().putString(docId, raw).apply()
    }

    private fun loadPersistedConfig(docId: String): CmsAdConfig? {
        val raw = adPrefs.getString(docId, null) ?: return null
        val p   = raw.split(PREF_SEP)
        if (p.size < 19) return null
        return try {
            CmsAdConfig(
                id = docId, unitId = p[0], enabled = p[1].toBoolean(), testMode = p[2].toBoolean(),
                position = p[3].toInt(), frequency = p[4].toInt(), xpReward = p[5].toInt(), dailyLimit = p[6].toInt(),
                scenarioDoubleXp = p[7].toBoolean(), scenarioUnlockLesson = p[8].toBoolean(), scenarioSaveStreak = p[9].toBoolean(),
                adType = p[10], bannerSize = p[11], placement = p[12], screens = p[13],
                label = p[14], bgColor = p[15], cornerRadius = p[16].toInt(), paddingTop = p[17].toInt(), paddingBottom = p[18].toInt(),
            )
        } catch (_: Exception) { null }
    }

    // ── Cache TTL — cms_ads nadiren değişir, 30 dakikada bir server'a git ──
    private val ADS_CONFIG_TTL_MS = 30L * 60L * 1000L
    private var lastServerFetchMs = 0L

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
    fun releasePositionedNatives(keyPrefix: String? = null) = engine.releasePositionedNatives(keyPrefix)
    fun warmUpNativePool(unitId: String) = engine.warmUpNativePool(unitId)
    fun releaseAdPool(unitId: String? = null) = engine.releaseAdPool(unitId)

    // ── Config StateFlow'ları — başlangıç değeri = kalıcı cache ───────────
    // Bu sayede uygulama açılır açılmaz doğru unit ID ile yükleme başlar,
    // Firestore round-trip'i beklenmez.
    private val _bannerConfig         = MutableStateFlow(loadPersistedConfig("banner_feed"))
    val bannerConfig                  = _bannerConfig.asStateFlow()
    private val _bannerLibraryConfig  = MutableStateFlow(loadPersistedConfig("banner_library"))
    val bannerLibraryConfig           = _bannerLibraryConfig.asStateFlow()
    private val _bannerKurdiConfig    = MutableStateFlow(loadPersistedConfig("banner_kurdi"))
    val bannerKurdiConfig             = _bannerKurdiConfig.asStateFlow()
    private val _bannerBlogConfig     = MutableStateFlow(loadPersistedConfig("banner_blog"))
    val bannerBlogConfig              = _bannerBlogConfig.asStateFlow()
    private val _interstitialConfig   = MutableStateFlow(loadPersistedConfig("interstitial_serial"))
    val interstitialConfig            = _interstitialConfig.asStateFlow()
    private val _rewardedConfig       = MutableStateFlow(loadPersistedConfig("rewarded_xp"))
    val rewardedConfig                = _rewardedConfig.asStateFlow()
    private val _nativeFeedConfig     = MutableStateFlow(loadPersistedConfig("native_feed"))
    val nativeFeedConfig              = _nativeFeedConfig.asStateFlow()
    private val _nativeBlogConfig     = MutableStateFlow(loadPersistedConfig("native_blog"))
    val nativeBlogConfig              = _nativeBlogConfig.asStateFlow()
    private val _nativeLibraryConfig  = MutableStateFlow(loadPersistedConfig("native_library"))
    val nativeLibraryConfig           = _nativeLibraryConfig.asStateFlow()
    private val _nativeKurdiConfig    = MutableStateFlow(loadPersistedConfig("native_kurdi"))
    val nativeKurdiConfig             = _nativeKurdiConfig.asStateFlow()
    private val _nativeProfileConfig  = MutableStateFlow(loadPersistedConfig("native_profile"))
    val nativeProfileConfig           = _nativeProfileConfig.asStateFlow()
    private val _nativeSearchConfig   = MutableStateFlow(loadPersistedConfig("native_search"))
    val nativeSearchConfig            = _nativeSearchConfig.asStateFlow()

    private val _allAdConfigs = MutableStateFlow<Map<String, CmsAdConfig>>(emptyMap())
    val allAdConfigs          = _allAdConfigs.asStateFlow()

    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled          = _adsEnabled.asStateFlow()

    // ── Unit ID StateFlow'ları ─────────────────────────────────────────────
    // ÖNCEKİ HATA: `engine.resolveUnitId(c, prodId) ?: prodId` ifadesi hem
    // "config henüz gelmedi" hem de "admin bu slotu kapattı (enabled=false)"
    // durumunda null dönüyordu — `?:` fallback ikisini ayırt edemediği için
    // admin CMS'den bir slotu kapattığında bile reklam gösterilmeye devam
    // ediyordu (null görüp prod ID'ye geri dönüyordu).
    //
    // YENİ MANTIK:
    //   c == null → config henüz Firestore'dan gelmedi → prod ID kullan (anlık başlasın)
    //   c != null && !c.enabled → admin kapattı → null döndür (reklam gösterme)
    //   c != null && c.enabled  → resolveUnitId: custom ID varsa onu, yoksa prod ID
    private fun resolveOrDefault(c: CmsAdConfig?, e: Boolean, prodId: String): String? {
        if (!e) return null                   // global "ads_enabled = false"
        if (c == null) return prodId          // config henüz gelmedi → prod ID ile başla
        if (!c.enabled) return null           // admin bu slotu kapattı
        return c.unitId.ifBlank { prodId }    // custom ID yoksa prod ID
    }

    val bannerUnitId: StateFlow<String?> = combine(_bannerConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val bannerLibraryUnitId: StateFlow<String?> = combine(_bannerLibraryConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val bannerKurdiUnitId: StateFlow<String?> = combine(_bannerKurdiConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val bannerBlogUnitId: StateFlow<String?> = combine(_bannerBlogConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.BANNER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.BANNER)

    val nativeFeedUnitId: StateFlow<String?> = combine(_nativeFeedConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val nativeBlogUnitId: StateFlow<String?> = combine(_nativeBlogConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val nativeLibraryUnitId: StateFlow<String?> = combine(_nativeLibraryConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val nativeKurdiUnitId: StateFlow<String?> = combine(_nativeKurdiConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val nativeProfileUnitId: StateFlow<String?> = combine(_nativeProfileConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val nativeSearchUnitId: StateFlow<String?> = combine(_nativeSearchConfig, _adsEnabled) { c, e ->
        resolveOrDefault(c, e, AdMobProdIds.NATIVE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdMobProdIds.NATIVE)

    val bannerPosition: StateFlow<Int> =
        _bannerConfig.combine(_adsEnabled) { c, _ -> c?.position ?: 5 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    // ── loadAdConfigs: Kendi bağımsız cache'imiz + TTL'li sunucu tazeleme ─────
    // Tek otomatik çağrı noktası: init{} bloğu. Admin paneli kendi kaydetme
    // işlemlerinden sonra forceServer=true ile çağırır (anında doğruluk istiyor).
    //
    // ÖNCEDEN burada önce Firestore'un Source.CACHE'i okunuyordu. SORUN: O cache
    // uygulamanın TÜM Firestore koleksiyonları (feed, library, profil, vb.) ile
    // PAYLAŞILAN, sabit boyutlu (50MB), LRU mantığıyla çalışan TEK bir havuzdur
    // (bkz. AppModule.kt cacheSizeBytes). cms_ads çok küçük ve nadiren dokunulan
    // bir koleksiyon olduğu için, feed/library gibi çok daha yoğun kullanılan
    // koleksiyonlar yer açmak için onu kolayca tahliye (evict) edebiliyordu — yani
    // reklamların "cache'i" uygulamanın genel cache'i altında ezilebiliyordu.
    //
    // ŞİMDİ: reklamlar SADECE kendi ayrı, asla tahliye edilmeyen SharedPreferences
    // cache'ine (adPrefs, dosyanın başında tanımlı) güveniyor — bu zaten ViewModel
    // oluşturulduğu anda her config'i anında dolduruyor (bkz. loadPersistedConfig).
    // Firestore'a sadece TAZELEME için, TTL'e bağlı olarak gidiyoruz; başarısız
    // olsa da sorun değil, ekranlar zaten adPrefs'ten gelen son bilinen değerle
    // çalışıyor — uygulamanın genel cache sisteminden tamamen bağımsız.
    fun loadAdConfigs(forceServer: Boolean = false) {
        viewModelScope.launch {
            val now        = System.currentTimeMillis()
            val ttlExpired = (now - lastServerFetchMs) > ADS_CONFIG_TTL_MS
            if (!forceServer && !ttlExpired && lastServerFetchMs != 0L) return@launch // hâlâ taze, tekrar sorma

            try {
                val serverSnap = firestore.collection("cms_ads").get(Source.SERVER).await()
                lastServerFetchMs = now
                applyAdConfigs(serverSnap, preloadAds = true)
            } catch (_: Exception) {
                // Sunucuya ulaşılamadı — reklamlar zaten kendi ayrı cache'imizden
                // (adPrefs) anında yüklenmiş durumda, Firestore'un paylaşımlı
                // cache'ine hiç bağımlı değiliz.
            }
        }
    }

    private fun applyAdConfigs(
        snap: com.google.firebase.firestore.QuerySnapshot,
        preloadAds: Boolean,
    ) {
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

            persistConfig(doc.id, config)

            when (doc.id) {
                "banner_feed"                  -> applyBannerConfig(_bannerConfig, config, BannerSlot.FEED, preloadAds)
                "banner_library", "banner_lib" -> applyBannerConfig(_bannerLibraryConfig, config, BannerSlot.LIB, preloadAds)
                "banner_kurdi"                 -> applyBannerConfig(_bannerKurdiConfig, config, BannerSlot.KURDI, preloadAds)
                "banner_blog"                  -> applyBannerConfig(_bannerBlogConfig, config, BannerSlot.BLOG, preloadAds)
                "interstitial_serial" -> {
                    val changed = _interstitialConfig.value != config
                    _interstitialConfig.value = config
                    // Interstitial'ı burada önceden yükle — ScreenTracker istediği an hazır olsun
                    if (preloadAds && changed && config.enabled && _adsEnabled.value) {
                        engine.resolveUnitId(config, AdMobProdIds.INTERSTITIAL)?.let {
                            interstitialUnitId = it
                            loadInterstitialAd(it)
                        }
                    }
                }
                "rewarded_xp" -> {
                    val changed = _rewardedConfig.value != config
                    _rewardedConfig.value = config
                    syncRemainingRewardedAds(config.dailyLimit)
                    if (preloadAds && changed && config.enabled && _adsEnabled.value) {
                        engine.resolveUnitId(config, AdMobProdIds.REWARDED)
                            ?.let { preloadRewardedAd(it) }
                    }
                }
                "native_feed"    -> applyNativeConfig(_nativeFeedConfig,    config, NativeAdSlot.FEED,    preloadAds)
                "native_blog"    -> applyNativeConfig(_nativeBlogConfig,    config, NativeAdSlot.BLOG,    preloadAds)
                "native_library" -> applyNativeConfig(_nativeLibraryConfig, config, NativeAdSlot.LIBRARY, preloadAds)
                "native_kurdi"   -> applyNativeConfig(_nativeKurdiConfig,   config, NativeAdSlot.KURDI,   preloadAds)
                "native_profile" -> applyNativeConfig(_nativeProfileConfig, config, NativeAdSlot.PROFILE, preloadAds)
                "native_search"  -> applyNativeConfig(_nativeSearchConfig,  config, NativeAdSlot.SEARCH,  preloadAds)
            }
            newAllConfigs[doc.id] = config
        }
        _allAdConfigs.value = newAllConfigs
    }

    private fun applyBannerConfig(
        target: MutableStateFlow<CmsAdConfig?>,
        config: CmsAdConfig,
        slot: BannerSlot,
        preloadAds: Boolean,
    ) {
        val changed = target.value != config
        target.value = config
        if (preloadAds && changed && config.enabled && _adsEnabled.value) {
            engine.resolveUnitId(config, AdMobProdIds.BANNER)
                ?.let { engine.loadBanner(slot.key(), it, config.bannerSize) }
        }
    }

    private fun applyNativeConfig(
        target: MutableStateFlow<CmsAdConfig?>,
        config: CmsAdConfig,
        slot: NativeAdSlot,
        preloadAds: Boolean,
    ) {
        val changed = target.value != config
        target.value = config
        if (preloadAds && changed && config.enabled && _adsEnabled.value) {
            engine.resolveUnitId(config, AdMobProdIds.NATIVE)
                ?.let { engine.warmUpNativePool(it) }
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────
    // ÖNEMLİ DÜZELTME: eskiden reklam gösterildikten (dismiss/fail) sonra bir
    // daha ASLA yeniden yüklenmiyordu — bir sonraki gösterim fırsatında 4 ekran
    // sonra ScreenTracker boş elle kalıyordu. Artık her show sonrası arka planda
    // hemen yeni reklam istenir; bir sonraki fırsatta 0 gecikmeyle hazırdır.
    private var interstitialAd: InterstitialAd? = null
    private var interstitialUnitId: String = ""

    private fun loadInterstitialAd(unitId: String) {
        if (unitId.isBlank()) return
        InterstitialAd.load(
            appContext, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) { interstitialAd = null }
                override fun onAdLoaded(ad: InterstitialAd)         { interstitialAd = ad }
            },
        )
    }

    /** Tek çağrı noktası: NavHost'taki interstitialConfig LaunchedEffect'i. */
    fun loadInterstitial() {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled) return
        val unitId = engine.resolveUnitId(config, AdMobProdIds.INTERSTITIAL) ?: return
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
                loadInterstitialAd(interstitialUnitId)   // bir sonraki gösterim için hemen ısıt
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
    private var rewardedAd       : RewardedAd? = null
    private var rewardedUnitId   : String = ""
    private var rewardedLoading  : Boolean = false   // aynı anda tek istek koruması
    private var rewardedRetry    : Int = 0

    fun preloadRewardedAd(unitId: String) {
        if (unitId.isBlank()) return
        rewardedUnitId = unitId
        if (rewardedAd != null || rewardedLoading) return  // zaten hazır veya yükleniyor
        rewardedLoading = true
        RewardedAd.load(
            appContext, unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd      = ad
                    rewardedLoading = false
                    rewardedRetry   = 0
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd      = null
                    rewardedLoading = false
                    // Retry: üstel geri çekilme (8s → 16s → 32s), max 4 deneme.
                    // Başarısız olursa sessizce bırakmak yerine kısa süre sonra
                    // tekrar dener — geçici ağ sorunu olsa bile sonraki attempt'ta hazır olur.
                    if (rewardedRetry < 4) {
                        rewardedRetry++
                        val delayMs = 8_000L * (1L shl (rewardedRetry - 1).coerceAtMost(2))
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            rewardedLoading = false
                            preloadRewardedAd(unitId)
                        }
                    } else {
                        rewardedRetry = 0  // bir sonraki tetiklemede sıfırdan başlasın
                    }
                }
            },
        )
    }

    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    // ÖNEMLİ DÜZELTME — "ödüllü reklam hazırda olmuyor" sorununun asıl kaynağı:
    // Interstitial'ın NavHost'ta `LaunchedEffect(interstitialConfig) { loadInterstitial() }`
    // gibi KOŞULSUZ bir tetikleyicisi var. Rewarded'ın ise YOKTU — tek preload noktası
    // applyAdConfigs içindeki "changed" kontrolüydü. Config artık kalıcı cache'den
    // (adPrefs) anında seed edildiği için, dönen kullanıcılarda (config son oturumdan
    // değişmediyse — en yaygın durum) "changed" hep FALSE oluyor, yani preloadRewardedAd
    // o oturumda HİÇ çağrılmıyordu. Kullanıcı "2x XP" veya "kilidi aç" dediğinde elde
    // hazır reklam olmuyordu. Çözüm: Interstitial ile birebir aynı desen — NavHost'tan
    // koşulsuz çağrılacak bir giriş noktası.
    fun loadRewarded() {
        val config = _rewardedConfig.value ?: return
        if (!config.enabled) return
        val unitId = engine.resolveUnitId(config, AdMobProdIds.REWARDED) ?: return
        preloadRewardedAd(unitId)
    }

    private val _remainingRewardedAds = MutableStateFlow(3)
    val remainingRewardedAds = _remainingRewardedAds.asStateFlow()

    // DÜZELTME: CMS admin panelinde her senaryo için ayrı bir aç/kapa anahtarı
    // var (scenarioDoubleXp / scenarioUnlockLesson / scenarioSaveStreak) ama bu
    // fonksiyon parametreyi (rewardType) tamamen yok sayıp sadece günlük limiti
    // kontrol ediyordu — admin bir senaryoyu kapatsa bile UI'da görünmeye devam
    // ediyordu. Artık ilgili CMS bayrağı da kontrol ediliyor.
    fun canShowScenario(rewardType: RewardType): Boolean {
        val cfg = _rewardedConfig.value ?: return _remainingRewardedAds.value > 0
        val scenarioEnabled = when (rewardType) {
            RewardType.DOUBLE_XP     -> cfg.scenarioDoubleXp
            RewardType.UNLOCK_LESSON -> cfg.scenarioUnlockLesson
            RewardType.SAVE_STREAK   -> cfg.scenarioSaveStreak
        }
        return scenarioEnabled && _remainingRewardedAds.value > 0
    }

    private fun syncRemainingRewardedAds(dailyLimit: Int) {
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
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null; onDismiss()
                    preloadRewardedAd(rewardedUnitId)   // bir sonraki gösterim için hemen ısıt
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

    override fun onCleared() {
        super.onCleared()
        engine.destroyAll()
    }

    // NOT: init bloğu en sona konuldu — yukarıdaki tüm property'ler
    // hazır olduktan sonra çalışması garanti altında.
    init {
        loadAdConfigs()
    }
}
