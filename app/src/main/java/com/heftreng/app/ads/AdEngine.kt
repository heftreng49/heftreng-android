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
 *  Tüm banner ve native reklam yükleme/retry/yaşam döngüsü tek yerde.
 *  Ekranlar sadece key (String) bazında istekte bulunur.
 *
 *  AdMob politika uyumu (v4):
 *   - STALE_AD_TIMEOUT: yüklenip gösterilmeyen native reklamlar belirli süre
 *     sonra otomatik destroy edilir. AdMob kuralı: yüklenen reklam makul
 *     sürede gösterilmeli; ekranda kalmayan ama hâlâ bellekte tutulan reklam
 *     politika ihlali sayılır. Composable dispose olmasa da (örn. arka plana
 *     alınan uygulama) reklam sonsuza dek canlı tutulmaz.
 *   - Havuz/stoklama YOK: her pozisyon kendi tek isteğini atar, kullanılmazsa
 *     hemen imha edilir (AdMob'un kendi önerisi).
 *   - spawnBannerAdView: retry döngüsünde ESKİ AdView destroy edilmeden
 *     önce yeni istek başlıyordu → yarış durumu + bellek sızıntısı. Düzeltildi.
 *   - loadBanner / loadPositionedBanner: "zaten yüklü" guard'ı unit ID
 *     değişimini de doğru şekilde algılar.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class AdEngine(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val MAX_RETRY         = 4
        private const val RETRY_BASE_DELAY  = 8_000L

        // Yüklenip gösterilmeyen native reklam bu süre sonunda otomatik imha edilir.
        // AdMob guideline: yüklenen reklam "makul sürede" gösterilmeli. Kullanıcı
        // genelde saniyeler içinde kaydırır; 5dk çok daha sıkı ve güvenli bir limit.
        private const val STALE_AD_TIMEOUT_MS = 5 * 60_000L   // 5 dakika

        // LazyColumn recycle/recompose sırasında composable KALICI OLARAK değil,
        // kısa süreliğine dispose-recreate olabilir (hafif scroll, animateContentSize
        // tetiklemesi, parent recomposition vb.) — ama daha gerçekçi olarak kullanıcı
        // pozisyonu geçip birkaç saniye başka bir kartı okuyup GERİ dönebilir; bu da
        // "terk etme" değil. DisposableEffect.onDispose bu durumları ayırt edemez.
        // Çözüm: imhayı bu kadar geciktir; aynı pozisyon bu süre içinde tekrar
        // preload isterse (ekrana geri döndüyse) bekleyen imha iptal edilir ve
        // zaten yüklü reklam olduğu gibi kullanılır. Çok kısa tutulursa (ör. 1sn)
        // gerçekçi "geri dön" senaryolarını kaçırır; çok uzun tutulursa kalıcı
        // terk edilen pozisyonlarda reklam gereksiz yere bellekte/istekte kalır.
        private const val DISPOSE_GRACE_MS = 10_000L
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

    /**
     * DÜZELTME: "bannerSize" alanı CMS/RC'de hem NATIVE hem BANNER reklamlar için
     * aynı isimle kullanılıyor. Native tarafı "small"/"medium"/"large" kelimelerini
     * okuyor (bkz. ekranlardaki NativeAdSize eşlemesi); banner tarafı ise sadece
     * "banner"/"medium_rectangle"/"large_banner" kelimelerini tanıyordu — yani
     * bir admin native'de işe yarayan "medium"'u banner'da yazsa sessizce
     * "adaptive"e düşüyordu. Artık iki kelime seti birbirine eşleniyor; hangisi
     * yazılırsa yazılsın aynı sonucu verir.
     */
    fun resolveAdSize(bannerSize: String): AdSize {
        val dm    = appContext.resources.displayMetrics
        val width = (dm.widthPixels / dm.density).toInt().coerceAtLeast(320)
        return when (bannerSize) {
            "banner", "small"            -> AdSize.BANNER
            "medium_rectangle", "medium" -> AdSize.MEDIUM_RECTANGLE
            "large_banner", "large"      -> AdSize.LARGE_BANNER
            else                         -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, width)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NATIVE AD — basit model: pozisyon başına TEK istek, TEK kullanım.
    //
    //  ÖNCEKİ SİSTEM (havuz + prefetch + recycle) AdMob fill-rate'i kötüleştiriyordu:
    //  her pozisyon kendi reklamını + bir sonraki pozisyonun reklamını önceden
    //  istiyordu, kullanıcı hızlı kaydırınca çoğu hiç gösterilmeden çöpe gidiyordu
    //  (istek/gösterim oranı çok düşüktü). AdMob'un kendi önerisi: reklamı sadece
    //  gerçekten gösterileceği an iste, kullanılmazsa hemen imha et — stoklama yapma.
    // ══════════════════════════════════════════════════════════════════════
    private val posNativeAd     = mutableMapOf<String, NativeAd>()
    private val posNativeLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val posNativeUnit   = mutableMapOf<String, String>()
    private val posNativeJob    = mutableMapOf<String, kotlinx.coroutines.Job>()
    // Yüklenip gösterilmeyen reklamı STALE_AD_TIMEOUT_MS sonunda imha eden zamanlayıcı.
    // Composable dispose çağrılmasa bile (örn. arka plana alınmış uygulama) reklam
    // sonsuza dek bellekte tutulmaz — AdMob politika gerekliliği.
    private val posNativeStaleJob = mutableMapOf<String, kotlinx.coroutines.Job>()
    // Gecikmeli imha job'ı — pozisyon kısa süreliğine dispose olduğunda hemen
    // silmek yerine bu job planlanır; aynı pozisyon DISPOSE_GRACE_MS içinde
    // tekrar preload isterse (kullanıcı geri döndüyse) iptal edilir.
    private val posNativeReleaseJob = mutableMapOf<String, kotlinx.coroutines.Job>()
    // Bir pozisyonun son denemesi başarısızlıkla tükendiyse işaretle; ekrana
    // tekrar girildiğinde (preloadPositionedNative tekrar çağrıldığında) bu
    // sayede yeniden denenir, aksi halde "sonsuza dek yüklenmedi" kalırdı.
    private val posNativeExhausted = mutableSetOf<String>()

    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> =
        posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedNative(key: String): NativeAd? = posNativeAd[key]

    /**
     * Pozisyon için TEK reklam ister. Aynı key+unitId için zaten yüklü veya
     * yükleniyorsa tekrar istek atmaz (idempotent). Önceki deneme tükenmişse
     * (MAX_RETRY aşıldıysa) burada tekrar çağrılınca yeniden denenir.
     */
    fun preloadPositionedNative(key: String, unitId: String) {
        if (unitId.isBlank()) return

        // Pozisyon geri geldi — bekleyen gecikmeli imha varsa iptal et.
        // Bu, kısa süreli scroll-out/scroll-in'de reklamı canlı tutar.
        posNativeReleaseJob.remove(key)?.cancel()

        val loadedFlow = posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }

        // Zaten bu unitId için yüklü VEYA şu an yükleniyor → tekrar isteme.
        // Tükenmiş (exhausted) durumdaysa bu guard'ı atla — yeniden dene.
        val exhausted = key in posNativeExhausted
        if (!exhausted && posNativeUnit[key] == unitId &&
            (loadedFlow.value || posNativeJob[key]?.isActive == true)
        ) return

        // unitId değiştiyse veya yeniden deneniyorsa eski isteği/reklamı temizle
        posNativeExhausted.remove(key)
        posNativeStaleJob.remove(key)?.cancel()
        posNativeJob[key]?.cancel()
        posNativeAd.remove(key)?.destroy()
        posNativeUnit[key] = unitId
        loadedFlow.value = false

        posNativeJob[key] = scope.launch {
            awaitSdk()
            loadOneNative(
                unitId    = unitId,
                retry     = 0,
                onSuccess = { ad ->
                    posNativeAd[key] = ad
                    loadedFlow.value = true
                    // Gösterilmeden bekleyen reklam için politika-uyumlu otomatik temizlik
                    posNativeStaleJob[key] = scope.launch {
                        delay(STALE_AD_TIMEOUT_MS)
                        android.util.Log.d("AdEngine", "Native '$key' gösterilmeden zaman aşımına uğradı, imha ediliyor")
                        releasePositionedNative(key)
                    }
                },
                onFail = {
                    loadedFlow.value = false
                    posNativeExhausted.add(key)
                },
            )
        }
    }

    /**
     * Composable LazyColumn dışına çıkıp dispose olduğunda ÇAĞRILIR, ya da
     * STALE_AD_TIMEOUT_MS dolunca otomatik tetiklenir.
     *
     * DÜZELTME: Eskiden burada ANINDA imha ediyorduk. Ama LazyColumn'da bir
     * composable geçici olarak dispose-recreate olabilir (hafif scroll,
     * animateContentSize tetiklemesi, parent recomposition) — bu "pozisyon
     * kalıcı olarak terk edildi" anlamına gelmez. Anında imha, ısıtılmış bir
     * reklamı kullanıcı tam o pozisyona geldiği anda siliyordu (alan boş
     * kalıyordu) ve ısıtma job'ı ile yarışıyordu (ısıtılan reklam hemen
     * imha ediliyordu). Artık DISPOSE_GRACE_MS kadar bekliyoruz; pozisyon bu
     * süre içinde preloadPositionedNative ile tekrar istenirse (kullanıcı
     * geri döndüyse) imha iptal edilir ve reklam olduğu gibi kalır.
     */
    fun releasePositionedNative(key: String) {
        posNativeStaleJob.remove(key)?.cancel()
        posNativeReleaseJob.remove(key)?.cancel()
        posNativeReleaseJob[key] = scope.launch {
            delay(DISPOSE_GRACE_MS)
            posNativeJob.remove(key)?.cancel()
            posNativeAd.remove(key)?.destroy()
            // Önce false'a çek (aktif collector'lar görsün), sonra map'ten sil
            posNativeLoaded[key]?.value = false
            posNativeLoaded.remove(key)
            posNativeUnit.remove(key)
            posNativeExhausted.remove(key)
            posNativeReleaseJob.remove(key)
        }
    }

    /**
     * Anında ve kalıcı imha — grace period BEKLEMEZ. Ekran tamamen kapanırken
     * (releaseAllPositionedNatives, destroyAll) kullanılır; orada "geri dönüş"
     * ihtimali yok, bekletmenin bir faydası olmaz.
     */
    private fun releasePositionedNativeImmediate(key: String) {
        posNativeStaleJob.remove(key)?.cancel()
        posNativeReleaseJob.remove(key)?.cancel()
        posNativeJob.remove(key)?.cancel()
        posNativeAd.remove(key)?.destroy()
        posNativeLoaded[key]?.value = false
        posNativeLoaded.remove(key)
        posNativeUnit.remove(key)
        posNativeExhausted.remove(key)
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

    /** Birden çok pozisyonu serbest bırakmak için (örn. tüm ekran kapanırken). */
    fun releaseAllPositionedNatives(keyPrefix: String? = null) {
        val allKeys = posNativeAd.keys + posNativeJob.keys + posNativeStaleJob.keys + posNativeReleaseJob.keys
        val keys = if (keyPrefix == null) allKeys.toList()
                   else allKeys.filter { it.startsWith(keyPrefix) }
        keys.toSet().forEach { releasePositionedNativeImmediate(it) }
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

    /**
     * MainActivity.onPause() → AdsViewModel.onAppBackground() üzerinden çağrılır.
     * Banner'lar pause edilir. Native reklamlardaki MediaView (video) için AdMob
     * SDK'sında manuel pause API'si yok — video kontrolü SDK içinde otomatik
     * yönetilir (Activity lifecycle'a bağlı). Burada ekstra olarak: arka plana
     * alınmış uygulamada gösterilmeyi bekleyen native reklamları hemen serbest
     * bırakıyoruz (politika: arka planda boşa tutulan reklam istenmez).
     */
    fun pauseAllBanners() {
        singleBannerView.values.forEach { runCatching { it.pause() } }
        posBannerView.values.forEach    { runCatching { it.pause() } }
    }

    /**
     * Uygulama arka plana alındığında, henüz gösterilmemiş (kullanıcı görmedi)
     * native reklamları serbest bırakır. Bekleyen yükleme istekleri de iptal edilir.
     * Bu, STALE_AD_TIMEOUT_MS'i beklemeden anında politika-uyumlu temizlik sağlar.
     */
    fun releaseUnseenNativesOnBackground() {
        releaseAllPositionedNatives()
    }

    fun destroyAll() {
        singleBannerView.values.forEach { it.destroy() }
        posBannerView.values.forEach { it.destroy() }
        posNativeJob.values.forEach { it.cancel() }
        posNativeStaleJob.values.forEach { it.cancel() }
        posNativeReleaseJob.values.forEach { it.cancel() }
        posNativeAd.values.forEach { it.destroy() }
        singleBannerView.clear(); singleBannerLoaded.clear()
        singleBannerSize.clear(); singleBannerUnit.clear(); singleRetryCount.clear()
        posBannerView.clear();    posBannerLoaded.clear()
        posBannerSize.clear();    posBannerUnit.clear()
        posNativeAd.clear();      posNativeLoaded.clear()
        posNativeUnit.clear();    posNativeJob.clear()
        posNativeStaleJob.clear(); posNativeExhausted.clear()
        posNativeReleaseJob.clear()
    }
}
