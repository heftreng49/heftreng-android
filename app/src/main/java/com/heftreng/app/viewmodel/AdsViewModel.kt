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
import com.heftreng.app.HeftrangApp
import com.heftreng.app.ads.AdConfigRepository
import com.heftreng.app.ads.AdEngine
import com.heftreng.app.ads.AdFrequencyManager
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.ads.AdPlacement
import com.heftreng.app.ads.SlotSpec
import com.heftreng.app.ads.buildAdPlan
import com.heftreng.app.ads.toSlotSpec
import com.heftreng.app.data.model.CmsAdConfig
import com.heftreng.app.util.ConsentHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdsViewModel — v2, sıfırdan yazım.
 *
 *  Bu sınıf artık İNCE BİR CEPHE: AdEngine (banner/native slot yönetimi) +
 *  AdConfigRepository (Remote Config'ten tek Map) + Interstitial/Rewarded
 *  (ekran-bağımsız, dokunulmadı) bir araya getirir. Ekran-özel StateFlow
 *  çoğaltması, ekran-özel resolveOrDefault tekrarı YOK.
 *
 *  Ekranlar için tek kullanım deseni:
 *    val cfg   = adsVm.configFor(RemoteConfigManager.KEY_NATIVE_FEED)
 *    val plan  = remember(itemCount, cfg) { adsVm.planFor("feed", itemCount, nativeCfg = cfg, ...) }
 *    // render sırasında: plan[index]?.let { AdSlotView(it, adsVm) }
 *
 *  Bkz. ads/AdPlanner.kt (yerleşim hesaplama — çakışma yapısal olarak imkansız)
 *       ads/AdEngine.kt   (tek slot deposu — banner/native yükleme, retry, lifecycle)
 *       ui/component/AdSlotView.kt (tek render noktası)
 * ═══════════════════════════════════════════════════════════════════════════
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val configRepo         : AdConfigRepository,
    private val frequencyManager   : AdFrequencyManager,
    private val auth               : com.google.firebase.auth.FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val engine = AdEngine(appContext, viewModelScope)

    // ── Config erişimi (ekranlar buradan okur) ─────────────────────────────
    val allConfigs: StateFlow<Map<String, CmsAdConfig>> = configRepo.configs
    val adsEnabled: StateFlow<Boolean> = configRepo.adsEnabled

    /** Belirli bir RC key'i için reaktif config. */
    fun configFlow(key: String): StateFlow<CmsAdConfig?> =
        configRepo.configs.map { it[key] }.stateIn(viewModelScope, SharingStarted.Eagerly, configRepo.get(key))

    /** Belirli bir RC key'i için reaktif, çözümlenmiş unitId (enabled+global+boş kontrolü dahil). */
    fun unitIdFlow(key: String): StateFlow<String?> =
        combine(configRepo.configs, configRepo.adsEnabled) { configs, enabled ->
            if (!enabled) return@combine null
            val c = configs[key] ?: return@combine null
            if (!c.enabled) return@combine null
            c.unitId.ifBlank { null }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, configRepo.resolvedUnitId(key))

    // ── Reklam planı (AdPlanner'a ince sarmalayıcı) ────────────────────────
    /**
     * Bir ekrandaki listede hangi index'te ne gösterileceğini hesaplar.
     * Native ve banner aynı çağrıda planlandığı için aynı index'e ikisinin
     * birden düşmesi yapısal olarak imkansızdır (bkz. AdPlanner.kt).
     */
    fun planFor(
        screenKey   : String,
        itemCount   : Int,
        nativeKey   : String? = null,
        bannerKey   : String? = null,
    ): Map<Int, AdPlacement> {
        val nativeSpec: SlotSpec? = nativeKey?.let { key ->
            configRepo.get(key)?.toSlotSpec(configRepo.resolvedUnitId(key))
        }
        val bannerSpec: SlotSpec? = bannerKey?.let { key ->
            configRepo.get(key)?.toSlotSpec(configRepo.resolvedUnitId(key))
        }
        return buildAdPlan(itemCount, nativeSpec, bannerSpec, screenKey)
    }

    // ── Banner slot API (AdEngine'e ince sarmalayıcı) ──────────────────────
    fun bannerLoadedFlow(key: String): StateFlow<Boolean> = engine.bannerLoadedFlow(key)
    fun bannerExhaustedFlow(key: String): StateFlow<Boolean> = engine.bannerExhaustedFlow(key)
    fun cachedBanner(key: String): AdView? = engine.cachedBanner(key)
    fun requestBanner(key: String, unitId: String, bannerSize: String = "adaptive") =
        engine.requestBanner(key, unitId, bannerSize)
    fun releaseBanner(key: String) = engine.releaseBanner(key)
    fun releaseBanners(keyPrefix: String? = null) = engine.releaseBanners(keyPrefix)
    fun getAdSize(bannerSize: String): AdSize = engine.resolveAdSize(bannerSize)

    // ── Native slot API (AdEngine'e ince sarmalayıcı) ──────────────────────
    fun nativeLoadedFlow(key: String): StateFlow<Boolean> = engine.nativeLoadedFlow(key)
    fun nativeExhaustedFlow(key: String): StateFlow<Boolean> = engine.nativeExhaustedFlow(key)
    fun cachedNative(key: String): NativeAd? = engine.cachedNative(key)
    fun requestNative(key: String, unitId: String) = engine.requestNative(key, unitId)
    fun releaseNative(key: String) = engine.releaseNative(key)
    fun releaseAllNatives(keyPrefix: String? = null) = engine.releaseAllNatives(keyPrefix)

    /**
     * Bir liste ekranında görünür pozisyona göre ileri pozisyonları ısıtır.
     * Isıtma penceresi viewportItemCount'a göre dinamik — sabit "3 ileri"
     * yerine görünen kart sayısının katları kullanılır; hızlı scroll'da da
     * reklamın 1-3sn yükleme süresini karşılayacak kadar erken tetiklenir.
     *
     * Ekranlar bunu tek bir snapshotFlow.collect içinde çağırır; kendi
     * index formüllerini YAZMAZ — plan zaten hesaplanmış durumda.
     *
     * @param maxInitialAds Adım 4: SADECE ilk çağrıda (firstVisibleIndex == 0)
     *   uygulanan bir üst sınır — pencere içine kaç placement düşerse düşsün,
     *   en fazla bu kadarı için istek atılır (varsayılan: sınır yok, mevcut
     *   davranışla birebir uyumlu). Amaç: ekran ilk açıldığı an, kullanıcı
     *   henüz hiç scroll etmemişken plandaki TÜM ileri pozisyonlara (örn.
     *   frequency düşükse aynı pencerede 4-5 reklam) birden istek atılmasını
     *   önlemek — "ilk 3'ü yükle, gerisini kullanıcı scroll ettikçe getir"
     *   prensibi. firstVisibleIndex > 0 olan sonraki çağrılarda (kullanıcı
     *   zaten scroll ediyor) bu sınır uygulanmaz — snapshotFlow.debounce
     *   zaten kademeli tetiklemeyi doğal olarak sağlıyor.
     */
    fun warmVisiblePositions(
        plan             : Map<Int, AdPlacement>,
        firstVisibleIndex: Int,
        viewportItemCount: Int = 8,
        maxInitialAds    : Int? = null,
    ) {
        // Pencere: görünür viewport + 3 kart öne bak.
        // Daha geniş pencere (eski: viewport*2=16) gereksiz yüzlerce istek atıyordu —
        // kullanıcının hiç görmeyeceği pozisyonlar önceden yükleniyor, istek/gösterim
        // oranı düşüyor, AdMob "low match rate" penaltısı uyguluyor.
        val windowEnd = firstVisibleIndex + viewportItemCount + 3
        val candidates = plan.entries
            .filter { (idx, _) -> idx >= firstVisibleIndex && idx <= windowEnd }
            .sortedBy { it.key }
        val toWarm = if (firstVisibleIndex == 0 && maxInitialAds != null) {
            candidates.take(maxInitialAds)
        } else {
            candidates
        }
        toWarm.forEach { (_, placement) ->
            when (placement) {
                is AdPlacement.Banner -> requestBanner(placement.slotKey, placement.unitId, placement.size)
                is AdPlacement.Native -> requestNative(placement.slotKey, placement.unitId)
            }
        }
    }

    // ── Remote Config yükleme ──────────────────────────────────────────────
    fun loadAdConfigs(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // SDK hazır olana kadar bekle (UMP tamamlandıktan sonra sdkReady=true olur).
            HeftrangApp.sdkReady.first { it }
            configRepo.refresh(forceRefresh)
            preloadTopLevelAds()
        }
    }

    /**
     * Liste-içi (pozisyon bazlı) olmayan, ekran açılır açılmaz doğrudan
     * gösterilecek reklamları önceden yükler (tekil banner slotları,
     * interstitial, rewarded). Liste içi slotlar warmVisiblePositions ile
     * ekranın kendi scroll akışında ısıtılır.
     */
    private fun preloadTopLevelAds() {
        configRepo.get(RemoteConfigManager.KEY_INTERSTITIAL)?.let { config ->
            if (config.enabled) {
                config.unitId.ifBlank { null }?.let {
                    interstitialUnitId = it
                    loadInterstitialAd(it)
                }
            }
        }
        configRepo.get(RemoteConfigManager.KEY_REWARDED)?.let { config ->
            syncRemainingRewardedAds(config.dailyLimit)
            if (config.enabled) {
                config.unitId.ifBlank { null }?.let { preloadRewardedAd(it) }
            }
        }
    }

    // ── Interstitial (ekran-bağımsız, davranış korunuyor) ──────────────────
    private var interstitialAd: InterstitialAd? = null
    private var interstitialUnitId: String = ""
    private var interstitialLoading: Boolean = false

    private fun loadInterstitialAd(unitId: String) {
        if (unitId.isBlank()) return
        // rewardedLoading ile aynı koruma: bu bayrak olmadan onAppForeground()
        // (uygulamalar arası geçiş, ekran kilidi aç/kapa gibi sık tetiklenen bir
        // lifecycle olayı) callback dönmeden tekrar tekrar çağrılırsa her seferinde
        // yeni bir InterstitialAd.load() isteği atılıyordu — istek/gösterim oranını
        // bozan ana sebeplerden biri buydu.
        if (interstitialAd != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            appContext, unitId, engine.adRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    interstitialLoading = false
                }
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialLoading = false
                }
            },
        )
    }

    fun loadInterstitial() {
        val config = configRepo.get(RemoteConfigManager.KEY_INTERSTITIAL) ?: return
        if (!config.enabled) return
        val unitId = config.unitId.ifBlank { null } ?: return
        interstitialUnitId = unitId
        loadInterstitialAd(unitId)
    }

    // Adım 3 (madde 5) savunma katmanı: şu an gösterimin TEK giriş noktası
    // ScreenTracker.tryShowInterstitial() (MIN_SCREENS_BETWEEN=4 ile korunuyor).
    // Ama showInterstitial public bir fonksiyon — ileride biri ScreenTracker'ı
    // atlayıp buradan doğrudan çağırırsa hiçbir frekans koruması devreye
    // girmez. Bu minimum-süre kontrolü ScreenTracker'ın yerini almaz (o hâlâ
    // asıl karar mercii), sadece ikinci bir güvenlik ağı.
    private var lastInterstitialShownAtMs = 0L
    private val MIN_INTERSTITIAL_INTERVAL_MS = 60_000L // 60 sn — makul bir alt sınır

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAtMs < MIN_INTERSTITIAL_INTERVAL_MS) {
            onAdDismissed()
            return
        }
        val ad = interstitialAd
        if (ad == null) { onAdDismissed(); return }
        lastInterstitialShownAtMs = now
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



    // ── Rewarded (ekran-bağımsız, davranış korunuyor) ──────────────────────
    private var rewardedAd      : RewardedAd? = null
    private var rewardedUnitId  : String = ""
    private var rewardedLoading : Boolean = false

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
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd      = null
                    rewardedLoading = false
                    // No-fill veya ağ hatası — 30 sn sonra bir kez daha dene.
                    // Sürekli retry fill rate'i bozar; tek gecikmiş deneme yeterli.
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(30_000L)
                        if (rewardedAd == null && !rewardedLoading) {
                            preloadRewardedAd(unitId)
                        }
                    }
                }
            },
        )
    }

    enum class RewardType { DOUBLE_XP, UNLOCK_LESSON, SAVE_STREAK }

    fun loadRewarded() {
        val config = configRepo.get(RemoteConfigManager.KEY_REWARDED) ?: return
        if (!config.enabled) return
        val unitId = config.unitId.ifBlank { null } ?: return
        preloadRewardedAd(unitId)
    }

    private val _remainingRewardedAds = MutableStateFlow(3)
    val remainingRewardedAds = _remainingRewardedAds.asStateFlow()

    fun canShowScenario(rewardType: RewardType): Boolean {
        val cfg = configRepo.get(RemoteConfigManager.KEY_REWARDED) ?: return false
        if (!cfg.enabled || cfg.unitId.isBlank()) return false
        val scenarioEnabled = when (rewardType) {
            RewardType.DOUBLE_XP     -> cfg.scenarioDoubleXp
            RewardType.UNLOCK_LESSON -> cfg.scenarioUnlockLesson
            RewardType.SAVE_STREAK   -> cfg.scenarioSaveStreak
        }
        return scenarioEnabled && _remainingRewardedAds.value > 0
    }

    private fun syncRemainingRewardedAds(dailyLimit: Int = configRepo.get(RemoteConfigManager.KEY_REWARDED)?.dailyLimit ?: 3) {
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
        // onDismiss'ten BİLEREK ayrı tutuldu: onDismiss hem "reklam normal
        // izlenip kapatıldı" hem "reklam hiç gösterilemedi" durumunda
        // çağrılıyordu — kullanıcı reklamı gerçekten izlese de izlemese de
        // aynı callback tetiklendiği için ekranlar bu ikisini ayıramıyordu.
        // Şimdi reklam o an yüklü değilse SADECE bu çağrılır, onDismiss'e
        // dokunulmaz.
        onAdNotReady  : () -> Unit = {},
    ) {
        val uid        = auth.currentUser?.uid ?: ""
        val dailyLimit = configRepo.get(RemoteConfigManager.KEY_REWARDED)?.dailyLimit ?: 3

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
            // Reklam henüz preload edilmemiş (ör. az önce başka bir rewarded
            // gösterildi, yenisi yüklenmeyi bekliyor). Önceden burada sadece
            // onDismiss() çağrılıyordu — kullanıcı butona bastığında sheet
            // sessizce kapanıyor, hiçbir reklam açılmıyor, hiçbir açıklama
            // görünmüyordu. Şimdi ekran bu durumu ayrıca yakalayıp kullanıcıya
            // "reklam hazır değil, birazdan tekrar dene" diyebiliyor.
            onAdNotReady()
            if (rewardedUnitId.isNotBlank()) preloadRewardedAd(rewardedUnitId)
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────
    fun onAppForeground() {
        viewModelScope.launch {
            if (!ConsentHelper.canRequestAds.value) return@launch
            engine.resumeAllBanners()
            loadAdConfigs()
            if (interstitialAd == null && !interstitialLoading && interstitialUnitId.isNotBlank()) loadInterstitialAd(interstitialUnitId)
            if (rewardedAd == null && rewardedUnitId.isNotBlank() && !rewardedLoading) preloadRewardedAd(rewardedUnitId)
        }
    }

    fun onAppBackground() {
        engine.pauseAllBanners()
        engine.releaseUnseenNativesOnBackground()  // DÜZELTME: görünmemiş native'leri temizle
        // Stale timeout (30dk) hâlâ aktif — ancak arka planda gereksiz bellek tutmamak için
        // görünmemiş (PENDING) native'leri anında serbest bırakıyoruz.
    }

    override fun onCleared() {
        super.onCleared()
        engine.destroyAll()
    }

    init {
        // NOT: syncRemainingRewardedAds() burada BİLEREK çağrılmıyor.
        // Bu noktada configRepo henüz refresh() edilmemiş olabilir, yani
        // dailyLimit fallback (3) ile geçici/yanlış bir değer set edilip
        // "kalan reklam" UI'ında kısa süreli bir sıçrama yaratabilirdi.
        // Gerçek senkronizasyon preloadTopLevelAds() içinde, Remote Config
        // çekildikten SONRA gerçek dailyLimit ile yapılıyor (satır ~174).
        //
        // TEK fetch tetikleyici: loadAdConfigs() zaten configRepo.refresh() çağırıyor
        // ve sdkReady + consent hazır olana kadar bekliyor. Burada AYRICA
        // configRepo.refresh() çağırmak, aynı Remote Config isteğini bağımsız
        // iki coroutine'den tetikleyip lastFetchMs üzerinde yarış durumu
        // yaratıyordu (biri "throttle geçti" derken diğeri de aynı anda deniyor).
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
