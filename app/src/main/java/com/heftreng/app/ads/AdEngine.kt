package com.heftreng.app.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.ads.mediation.admob.AdMobAdapter
import com.heftreng.app.HeftrangApp
import com.heftreng.app.data.model.CmsAdConfig
import com.heftreng.app.util.ConsentHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdEngine — Heftreng'in TEK reklam motoru.
 *
 *  Tüm banner ve native reklam yükleme/retry/havuz mantığı tek yerde.
 *  Ekranlar sadece key (String) bazında istekte bulunur.
 *
 *  Düzeltmeler (v2):
 *   - spawnBannerAdView: retry döngüsünde ESKİ AdView destroy edilmeden
 *     önce yeni istek başlıyordu → yarış durumu + bellek sızıntısı. Düzeltildi.
 *   - loadBanner / loadPositionedBanner: "zaten yüklü" guard'ı unit ID
 *     değişimini de doğru şekilde algılar hale getirildi.
 *   - warmUpNativePool: havuz dolduğunda nativePoolFilling bayrağı temizlenir.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class AdEngine(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val MAX_RETRY         = 4
        private const val RETRY_BASE_DELAY  = 8_000L
        private const val POOL_TARGET       = 3
        private const val POOL_MAX          = 6
        private const val PREFETCH_BATCH    = 3   // loadAds(n): tek HTTP'de N native
    }

    // SDK hazır olana kadar bekle — coroutine içinde, UI'ı bloklamaz (<200ms)
    private suspend fun awaitSdk() = HeftrangApp.sdkReady.first { it }

    // AdRequest — consent durumuna göre kişiselleştirilmiş veya NPA modu.
    //
    // ConsentHelper.consentStatus:
    //   OBTAINED  → kullanıcı kabul etti → personalized reklam
    //   NOT_REQUIRED → GDPR/CCPA dışı bölge (Türkiye vb.) → personalized reklam
    //   REQUIRED  → form gösterildi ama ret/belirsiz → NPA ("npa"="1")
    //   UNKNOWN   → henüz bilinmiyor → NPA (güvenli taraf)
    fun adRequest(): AdRequest {
        val status = ConsentHelper.consentStatus.value
        val npa = status == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED

        return if (npa) {
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, android.os.Bundle().apply {
                    putString("npa", "1")
                })
                .build()
        } else {
            AdRequest.Builder().build()
        }
    }

    // Native optimizasyon: statik reklam öncelikli, video başlarsa sessiz
    private val nativeOptions = NativeAdOptions.Builder()
        .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
        .setRequestMultipleImages(false)
        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
        .build()

    // ── Tekil (single) banner ─────────────────────────────────────────────
    private val singleBannerView   = mutableMapOf<String, AdView>()
    private val singleBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val singleBannerSize   = mutableMapOf<String, String>()
    private val singleBannerUnit   = mutableMapOf<String, String>()
    private val singleRetryCount   = mutableMapOf<String, Int>()

    fun bannerLoadedFlow(key: String): StateFlow<Boolean> =
        singleBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedBanner(key: String): AdView? = singleBannerView[key]

    /** Tekil banner slotunu yükler. Unit ID veya boyut değiştiyse otomatik yeniler. */
    fun loadBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow  = singleBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val sizeChanged = singleBannerSize[key] != null && singleBannerSize[key] != bannerSize
        val unitChanged = singleBannerUnit[key] != null && singleBannerUnit[key] != unitId
        if (loadedFlow.value && !sizeChanged && !unitChanged) return

        if (sizeChanged || unitChanged) {
            singleBannerView.remove(key)?.destroy()
            loadedFlow.value = false
        }
        singleBannerSize[key]  = bannerSize
        singleBannerUnit[key]  = unitId
        singleRetryCount[key]  = 0
        scope.launch {
            awaitSdk()
            spawnBannerAdView(key, unitId, bannerSize) { adView ->
                singleBannerView.remove(key)?.destroy()
                singleBannerView[key] = adView
                loadedFlow.value = true
            }
        }
    }

    // ── Pozisyon bazlı banner ──────────────────────────────────────────────
    private val posBannerView   = mutableMapOf<String, AdView>()
    private val posBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val posBannerSize   = mutableMapOf<String, String>()
    private val posBannerUnit   = mutableMapOf<String, String>()

    fun positionedBannerLoadedFlow(key: String): StateFlow<Boolean> =
        posBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedBanner(key: String): AdView? = posBannerView[key]

    fun loadPositionedBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow  = posBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val sizeChanged = posBannerSize[key] != null && posBannerSize[key] != bannerSize
        val unitChanged = posBannerUnit[key] != null && posBannerUnit[key] != unitId
        if (loadedFlow.value && !sizeChanged && !unitChanged) return

        if (sizeChanged || unitChanged) {
            posBannerView.remove(key)?.destroy()
            loadedFlow.value = false
        }
        posBannerSize[key] = bannerSize
        posBannerUnit[key] = unitId
        scope.launch {
            awaitSdk()
            spawnBannerAdView(key, unitId, bannerSize) { adView ->
                posBannerView.remove(key)?.destroy()
                posBannerView[key] = adView
                loadedFlow.value = true
            }
        }
    }

    fun releasePositionedBanners(keyPrefix: String? = null) {
        val keys = if (keyPrefix == null) posBannerView.keys.toList()
                   else posBannerView.keys.filter { it.startsWith(keyPrefix) }
        keys.forEach { k ->
            posBannerView.remove(k)?.destroy()
            posBannerLoaded.remove(k)
            posBannerSize.remove(k)
            posBannerUnit.remove(k)
        }
    }

    /**
     * Tek bir AdView üretip yükler.
     * Retry'da: callback saklayarak sadece en son istek "kazanır",
     * aradaki stale AdView'lar anında destroy edilir.
     */
    private fun spawnBannerAdView(
        key: String,
        unitId: String,
        bannerSize: String,
        onLoaded: (AdView) -> Unit,
    ) {
        val adView = AdView(appContext).apply {
            setAdSize(resolveAdSize(bannerSize))
            adUnitId = unitId
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                singleRetryCount[key] = 0
                onLoaded(adView)
            }
            override fun onAdFailedToLoad(e: LoadAdError) {
                adView.destroy()  // ← başarısız AdView'ı hemen temizle
                scope.launch {
                    val retry = (singleRetryCount[key] ?: 0) + 1
                    singleRetryCount[key] = retry
                    if (retry <= MAX_RETRY) {
                        delay(backoffDelay(retry))
                        spawnBannerAdView(key, unitId, bannerSize, onLoaded)
                    }
                }
            }
        }

        adView.loadAd(adRequest())
    }

    fun resolveAdSize(bannerSize: String): AdSize {
        val dm    = appContext.resources.displayMetrics
        val width = (dm.widthPixels / dm.density).toInt().coerceAtLeast(320)
        return when (bannerSize) {
            "banner"           -> AdSize.BANNER
            "medium_rectangle" -> AdSize.MEDIUM_RECTANGLE
            "large_banner"     -> AdSize.LARGE_BANNER
            else               -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, width)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NATIVE AD HAVUZU
    // ══════════════════════════════════════════════════════════════════════
    private val nativePool        = mutableMapOf<String, ArrayDeque<NativeAd>>()
    private val nativePoolFilling = mutableMapOf<String, Int>()  // kaç slot doldurulmakta

    private val posNativeAd     = mutableMapOf<String, NativeAd>()
    private val posNativeLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val posNativeUnit   = mutableMapOf<String, String>()

    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> =
        posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedNative(key: String): NativeAd? = posNativeAd[key]

    /** Havuzu doldurur — loadAds(BATCH) ile TEK HTTP'de N reklam çeker. */
    fun warmUpNativePool(unitId: String) {
        if (unitId.isBlank()) return
        val pool    = nativePool.getOrPut(unitId) { ArrayDeque() }
        val filling = nativePoolFilling[unitId] ?: 0
        val needed  = POOL_TARGET - pool.size - filling
        if (needed <= 0) return
        nativePoolFilling[unitId] = (filling + needed)

        scope.launch {
            awaitSdk()
            // loadAds(n) = tek HTTP isteği, n reklam — her slot için ayrı istek değil
            AdLoader.Builder(appContext, unitId)
                .forNativeAd { nativeAd ->
                    nativePoolFilling[unitId] = ((nativePoolFilling[unitId] ?: 1) - 1).coerceAtLeast(0)
                    val p = nativePool.getOrPut(unitId) { ArrayDeque() }
                    if (p.size < POOL_MAX) p.addLast(nativeAd) else nativeAd.destroy()
                }
                .withNativeAdOptions(nativeOptions)
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        nativePoolFilling[unitId] = ((nativePoolFilling[unitId] ?: 1) - 1).coerceAtLeast(0)
                    }
                })
                .build()
                .loadAds(adRequest(), needed.coerceAtMost(PREFETCH_BATCH))
        }
    }

    /**
     * Pozisyon için reklam hazırlar.
     * Havuzda reklam varsa → anında (0 gecikme).
     * Yoksa → doğrudan yükler, arka planda havuzu doldurur.
     */
    fun preloadPositionedNative(key: String, unitId: String) {
        if (unitId.isBlank()) return
        val loadedFlow = posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }

        // Zaten bu unitId için yüklü ve geçerli bir reklam varsa tekrar istek atma
        if (loadedFlow.value && posNativeUnit[key] == unitId && posNativeAd[key] != null) return
        posNativeUnit[key] = unitId

        // Havuzdan çek
        val pool     = nativePool.getOrPut(unitId) { ArrayDeque() }
        val fromPool = pool.removeFirstOrNull()
        if (fromPool != null) {
            posNativeAd.remove(key)?.destroy()
            posNativeAd[key] = fromPool
            loadedFlow.value = true
            warmUpNativePool(unitId)  // boşalan havuz slotunu arka planda doldur
            return
        }

        // Havuz boş — doğrudan yükle
        warmUpNativePool(unitId)
        loadedFlow.value = false
        scope.launch {
            awaitSdk()
            loadOneNative(
                unitId    = unitId,
                retry     = 0,
                onSuccess = { ad ->
                    posNativeAd.remove(key)?.destroy()
                    posNativeAd[key] = ad
                    loadedFlow.value = true
                },
                onFail = {},
            )
        }
    }

    /**
     * TEK native ad yükleme primitifi.
     * forNativeAd ile withAdListener AYRI zincirde — ikisi aynı builder'da
     * olduğunda AdMob SDK başarılı yüklemede withAdListener.onAdLoaded()'ı
     * çağırmaz (beklenen davranış); ama bazen forNativeAd callback'i de
     * güvenilir şekilde çalışmaz. Çözüm: her şeyi TEK bir AdListener'a topla
     * ve NativeAdOptions ile forNativeAd'i ayrı tanımla.
     */
    private fun loadOneNative(
        unitId   : String,
        retry    : Int = 0,
        onSuccess: (NativeAd) -> Unit,
        onFail   : () -> Unit,
    ) {
        var received = false  // callback'in iki kez çalışmasını engelle

        val adLoader = AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                if (!received) {
                    received = true
                    onSuccess(nativeAd)
                } else {
                    nativeAd.destroy()
                }
            }
            .withNativeAdOptions(nativeOptions)
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (retry < MAX_RETRY) {
                        scope.launch {
                            delay(backoffDelay(retry + 1))
                            loadOneNative(unitId, retry + 1, onSuccess, onFail)
                        }
                    } else {
                        onFail()
                    }
                }
            })
            .build()

        adLoader.loadAd(adRequest())
    }

    fun releasePositionedNatives(keyPrefix: String? = null) {
        val keys = if (keyPrefix == null) posNativeAd.keys.toList()
                   else posNativeAd.keys.filter { it.startsWith(keyPrefix) }
        keys.forEach { k ->
            posNativeAd.remove(k)?.destroy()
            posNativeLoaded.remove(k)
            posNativeUnit.remove(k)
        }
    }

    fun releaseAdPool(unitId: String? = null) {
        val pools = if (unitId == null) nativePool.values else listOfNotNull(nativePool[unitId])
        pools.forEach { pool -> while (pool.isNotEmpty()) pool.removeFirst().destroy() }
        if (unitId != null) nativePool.remove(unitId) else nativePool.clear()
        if (unitId != null) nativePoolFilling.remove(unitId) else nativePoolFilling.clear()
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────
    private fun backoffDelay(retry: Int): Long =
        RETRY_BASE_DELAY * (1L shl (retry - 1).coerceAtMost(4))   // 8s,16s,32s,64s,128s

    /**
     * CMS config'inden gerçek unit ID'yi belirler.
     * CMS'te özel unitId tanımlıysa → onu kullan, yoksa → prod varsayılanı.
     * enabled=false ise null döner → reklam gösterilmez.
     */
    fun resolveUnitId(config: CmsAdConfig?, prodId: String): String? {
        config ?: return null
        if (!config.enabled) return null
        return config.unitId.ifBlank { prodId }
    }

    /** MainActivity.onResume() → AdsViewModel.onAppForeground() üzerinden çağrılır. */
    fun resumeAllBanners() {
        singleBannerView.values.forEach { runCatching { it.resume() } }
        posBannerView.values.forEach    { runCatching { it.resume() } }
    }

    /** MainActivity.onPause() → AdsViewModel.onAppBackground() üzerinden çağrılır. */
    fun pauseAllBanners() {
        singleBannerView.values.forEach { runCatching { it.pause() } }
        posBannerView.values.forEach    { runCatching { it.pause() } }
    }

    fun destroyAll() {
        singleBannerView.values.forEach { it.destroy() }
        posBannerView.values.forEach { it.destroy() }
        posNativeAd.values.forEach { it.destroy() }
        nativePool.values.forEach { pool -> pool.forEach { it.destroy() } }
        singleBannerView.clear(); singleBannerLoaded.clear()
        singleBannerSize.clear(); singleBannerUnit.clear()
        posBannerView.clear();    posBannerLoaded.clear()
        posNativeAd.clear();      posNativeLoaded.clear(); posNativeUnit.clear()
        nativePool.clear();       nativePoolFilling.clear()
    }
}
