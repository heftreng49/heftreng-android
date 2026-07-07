# Reklam Sistemi — Kapsamlı Denetim ve Yeniden Yapılandırma Planı

Amaç: uygulamadaki **her ekranı** tek tek gözden geçirip reklam yerleşimini
doğru, AdMob politikalarına uygun, Remote Config ile yönetilebilir ve
kullanıcı deneyimini gözeten bir hale getirmek. Adım adım gidiyoruz.

Durum lejantı: ⬜ Yapılmadı · 🔧 Devam ediyor · ✅ Tamamlandı

---

## 0. Bu denetimin dayandığı mevcut durum tespiti (kod taraması sonucu)

Aşağıdaki tablo, projedeki **24 ekran dosyasının tamamının** taranmasıyla
çıkarıldı (`AdSlotView` / `planFor` / `warmVisiblePositions` / container tipi
/ `release*` çağrısı var mı kontrol edildi).

### Reklamı olan ekranlar — durumu

| Ekran | Container | warmVisiblePositions | release temizliği | Durum |
|---|---|---|---|---|
| Feed | LazyColumn | ✅ var | ✅ var | ✅ Sağlıklı |
| Kurdi | LazyColumn | ✅ var | ✅ var | ✅ Sağlıklı (önceden düzeltildi) |
| Profile | LazyColumn | ✅ var (yeni düzeltildi) | ✅ var (prefix düzeltildi) | ✅ Sağlıklı |
| Blog listesi | LazyColumn | ✅ var (yeni düzeltildi) | ✅ var | ✅ Sağlıklı |
| Blog yazı detayı | **Column+verticalScroll** | yok (viewability gate ile korunuyor) | yok | 🟡 Kısmen — gate var ama gerçek ısıtma tetikleyicisi hiç yok |
| Library (alıntılar/yorumlar/yazarlar/kitaplar — 4 sekme) | LazyColumn/Grid | ✅ var (yeni düzeltildi) | ✅ var | ✅ Sağlıklı |
| Search | LazyColumn | ✅ var (yeni düzeltildi) | ✅ var | ✅ Sağlıklı |

### Reklamı hiç olmayan ekranlar (bu denetimde ilk kez değerlendirilecek)

| Ekran | İçerik tipi | Container | Uzun/scroll'lanabilir mi | Durum |
|---|---|---|---|---|
| SinglePostScreen (tekil gönderi) | Post detayı + yorumlar | LazyColumn | Evet, yorum sayısına göre uzun olabilir | ⬜ |
| AuthorBookQuoteScreens (6 ayrı ekran: yazar detay, kitap detay, alıntı detay vb.) | Liste + detay karışık | LazyColumn / verticalScroll karışık | Evet | ⬜ |
| BookScreens (3 ekran) | Kitap/seri listeleri | LazyColumn | Evet | ⬜ |
| ReadingListScreen | Okuma listesi | LazyColumn/Grid | Evet | ⬜ |
| YazarScreen | Yazar detay | LazyColumn | Evet | ⬜ |
| SerialsScreen | Seri listesi | (fonksiyon yapısı farklı, ayrı incelenecek) | Belirsiz — ayrı incelenecek | ⬜ |
| **NotificationsScreen** | **Bildirim listesi** | **LazyColumn** | **Evet** | **✅ Tamamlandı — banner eklendi (kapalı, enabled:false)** |
| MessagesScreens (liste + sohbet) | Mesajlaşma | LazyColumn | Sohbet ekranına reklam UX açısından uygun değil, konuşma listesi ayrı değerlendirilecek | ⬜ |
| SavedPostsScreen | Kaydedilenler listesi | LazyColumn | Evet | ⬜ |
| PeopleHubScreen / UserListSheet | Kullanıcı listeleri | LazyColumn | Kısa listeler olabilir, düşük öncelik | ⬜ |
| CmsPageScreen | Statik CMS sayfası | verticalScroll | Uygulamaya göre değişir | ⬜ |

**Reklam KONULMAMASI gereken ekranlar (bilinçli hariç tutma):**
- AuthScreen (giriş/kayıt) — asla reklam olmamalı
- SettingsScreen — asla reklam olmamalı
- AdminScreen / CmsScreen / KurdiAdminScreen — yönetici ekranları, reklam olmamalı
- Mesajlaşma sohbet ekranı (konuşma içi) — WhatsApp/Instagram DM'lerinde reklam olmaz, kullanıcı deneyimini ciddi bozar

### Remote Config'te tanımlı olan key'ler (mevcut durum)

```
KEY_BANNER_FEED, KEY_BANNER_LIBRARY, KEY_BANNER_KURDI, KEY_BANNER_BLOG
KEY_NATIVE_FEED, KEY_NATIVE_BLOG, KEY_NATIVE_LIBRARY, KEY_NATIVE_KURDI,
KEY_NATIVE_PROFILE, KEY_NATIVE_SEARCH
KEY_INTERSTITIAL, KEY_REWARDED
```

Dikkat: `KEY_NATIVE_PROFILE` ve `KEY_NATIVE_SEARCH` zaten tanımlı ama
`warmVisiblePositions` hiç çağrılmadığı için şu ana kadar hiç işe yaramıyordu.
Profile bu denetimden önce düzeltildi; **Search henüz düzeltilmedi.**

`KEY_BANNER_PROFILE`, `KEY_BANNER_SEARCH` gibi key'ler **hiç yok** — bu
ekranlarda şu an sadece native planlanabiliyor, banner planlanamıyor.

### Reklamla ilgili tüm Kotlin dosyaları (toplam ~1966 satır, 12 dosya)

```
ads/AdConfigRepository.kt     (73 satır)   — Remote Config → Map dönüşümü
ads/AdEngine.kt                (415 satır)  — asıl yükleme/cache/retry motoru
ads/AdFrequencyManager.kt      (47 satır)   — rewarded günlük limit sayacı
ads/AdPlanner.kt               (118 satır)  — index→placement saf hesaplama
ads/RemoteConfigManager.kt     (210 satır)  — key tanımları + Firebase RC bağlantısı
ads/ScreenTracker.kt           (79 satır)   — interstitial ekran/frekans takibi
ads/ScreenTrackerEntryPoint.kt (11 satır)   — Hilt entry point
ui/component/AdSlotView.kt     (264 satır)  — banner/native render
ui/component/NativeAdCompose.kt(134 satır)  — NativeAdView AndroidView sarmalayıcı
viewmodel/AdsViewModel.kt      (412 satır)  — ekranlara açılan public API
util/ConsentHelper.kt          (174 satır)  — UMP/GDPR onay akışı
di/AdsModule.kt                (29 satır)   — Hilt binding
```

---

## Adım 1 — Her ekranı tek tek incele (reklamı olan + olmayan) ⬜

**Amaç:** Yukarıdaki taramayı doğrulamak ve her ekran için nihai kararı
netleştirmek: banner mı, native mı, ikisi de mi, hiçbiri mi.

**Yapılacaklar (her ekran için tek tek):**
1. Ekranın içerik yoğunluğunu değerlendir (kaç öğe, ortalama scroll süresi).
2. Ekranın `LazyColumn`/`LazyVerticalGrid` mi yoksa `Column+verticalScroll`
   mü kullandığını doğrula (verticalScroll kullananlar viewability riski
   taşır — bkz. Adım 4).
3. Banner mı native mı daha uygun olduğuna karar ver (bkz. Adım 5 kriterleri).
4. Reklamın **hiç olmaması gerektiği** ekranları kesin listeye al (Auth,
   Settings, Admin ekranları, mesajlaşma sohbet içi).
5. Kararları bu MD'deki tabloya işle.

**Çıktı:** Bu MD dosyasındaki tablo, her ekran için "banner/native/ikisi/hiçbiri"
kararıyla güncellenmiş olacak — sonraki adımlar bu karara göre ilerleyecek.

---

## Adım 2 — Firebase Remote Config uyumluluğu ⬜

**Mevcut durum:** Sistem zaten Remote Config kullanıyor
(`RemoteConfigManager.kt`), her ekran için `enabled/unitId/position/frequency`
JSON'u tek bir key altında tutuluyor. Mimari doğru, ama **eksik ekranlar için
key hiç tanımlı değil**.

**Yapılacaklar:**
1. Adım 1'de "banner ve/veya native uygun" kararı verilen her yeni ekran için
   `RemoteConfigManager.kt`'ye yeni key sabitleri ekle (örn.
   `KEY_NATIVE_SINGLEPOST`, `KEY_BANNER_READINGLIST` gibi — proje genelindeki
   isimlendirme deseniyle tutarlı).
2. `defaultsMap()` içine her yeni key için **`enabled: false`** varsayılanıyla
   JSON şablonu ekle (Adım 5'te açıklanan "önce hazır olsun, sonra Firebase'e
   değer gir" prensibiyle uyumlu — kod hazır ama reklam kapalı başlar).
3. `ALL_KEYS` listesine yeni key'leri ekle (Remote Config fetch döngüsünün
   bunları da çekmesi için — bu liste zaten `AdConfigRepository.refresh()`
   tarafından kullanılıyor).
4. Firebase Console tarafında (uygulama koduyla ilgisi yok, ayrı bir adım)
   bu key'lerin gerçek `unitId` değerleriyle doldurulması **daha sonraya**
   bırakılacak — bu adımda sadece kod tarafı hazırlanacak.

**Riskler:** Yok — `enabled: false` varsayılanı sayesinde yeni key'ler
eklenmesi mevcut davranışı hiç değiştirmez, sadece altyapıyı hazırlar.

---

## Adım 3 — Gereksiz istek önleme + ekrandan çıkışta isteklerin kesilmesi ⬜

**Mevcut durum (kısmen sağlıklı, kısmen eksik):**
- Feed/Kurdi/Profile/Blog: `warmVisiblePositions` dar pencereli
  (viewport + 3 kart), debounce'lu (300ms), `exhausted` guard'ı var — istek
  şişmesine karşı zaten korunuyor.
- Library/Search: `warmVisiblePositions` **hiç çağrılmadığı** için şu an
  hiç istek atmıyor (paradoksal olarak "gereksiz istek" sorunu yok ama
  "hiç reklam yok" sorunu var — bu Adım 1'de çözülecek, düzeltilirken
  aynı dar-pencere deseni uygulanacak).
- `DisposableEffect(Unit) { onDispose { adsVm.releaseBanners(...); adsVm.releaseAllNatives(...) } }`
  deseni Feed/Kurdi/Blog'da var, Profile'da vardı ama prefix hatası
  düzeltildi. Library/Search'te release çağrısı var ama warmVisiblePositions
  hiç çalışmadığı için pratikte test edilmemiş durumda.

**Yapılacaklar:**
1. Her ekranın `onDispose` bloğunda gerçekten **o ekrana özel** slotKey
   prefix'iyle `releaseBanners`/`releaseAllNatives` çağrıldığını doğrula
   (Profile'da bulunan `targetUid` prefix uyuşmazlığı gibi sessiz
   hatalar başka ekranda da olabilir — her birini tek tek kontrol et).
2. `AdEngine.kt`'deki `exhausted` guard'ının **hem banner hem native**
   için tutarlı çalıştığını doğrula (daha önce banner'da eksikti, düzeltildi
   — ama yeni eklenecek her ekran için bu guard'ın devreye girdiğinden emin ol).
3. `BlogPostScreen`'deki `VisibilityGatedAdSlot` deseni — şu an sadece
   "görünürlük" kontrolü yapıyor, henüz gerçek bir `requestBanner` tetikleyicisi
   yok. Bu ekrana gerçek reklam yükleme eklenirse (Adım 5), mutlaka bu
   gate'in içinden tetiklenmeli, `AdSlotView` composable'ının kendisinden değil.
4. Uygulama arka plana alındığında (`ON_STOP`) native/banner isteklerinin
   iptal edilip edilmediğini kontrol et — şu an sadece banner'ın
   `resume()/pause()` lifecycle'ı yönetiliyor (`AdSlotView.kt` içindeki
   `BannerAndroidView`), native tarafı için böyle bir yaşam döngüsü kontrolü
   yok, eklenmesi değerlendirilecek.
5. Interstitial'ın `showInterstitial` fonksiyonunda hâlâ dahili bir cooldown
   olmadığı (frekans kontrolü sadece çağıran `ScreenTracker`'da) durumu
   tekrar gözden geçirilecek — ileride başka bir yerden `showInterstitial`
   doğrudan çağrılırsa `ScreenTracker`'ı bypass edip sık gösterime yol açabilir.
   Fonksiyonun kendi içine de bir minimum süre kontrolü eklenmesi düşünülecek.

---

## Adım 4 — İlk 3 reklam ön yükleme, sonrakiler kademeli ⬜

**Mevcut durum:** `warmVisiblePositions(plan, firstVisibleIndex, viewportItemCount=8)`
zaten viewport + 3 kart penceresiyle çalışıyor — ama bu, "ilk 3 reklamı
yükle, sonrakini kullanıcı scroll ettikçe yükle" mantığından farklı: şu anki
sistem **pozisyon bazlı** (index penceresi), senin istediğin **reklam sayısı
bazlı** bir kademeli yükleme.

**Yapılacaklar:**
1. `warmVisiblePositions` fonksiyonuna, index penceresine ek olarak bir
   "aynı anda en fazla N reklam isteği aktif olsun" sınırı eklenmesi
   değerlendirilecek — şu anki dar pencere zaten dolaylı olarak bunu
   sağlıyor olabilir, ölçülüp gerekirse netleştirilecek.
2. Ekran ilk açıldığında (`firstVisibleIndex = 0` çağrısı) sadece
   **plandaki ilk 3 reklam placement'ı** için istek atılacak şekilde
   `warmVisiblePositions`'a opsiyonel bir `maxInitialAds` parametresi
   eklenmesi değerlendirilecek.
3. Kullanıcı scroll ettikçe devreye giren mekanizma zaten mevcut
   (`snapshotFlow { firstVisibleItemIndex }.debounce(300L)`) — bunun
   "kademeli" davranışı doğru karşıladığından emin olmak için
   gerçek cihazda ölçüm yapılacak (kaç istek atıldığı loglanarak).
4. Bu değişikliğin AdMob "match rate"/"request density" metriklerini
   nasıl etkilediği bir önceki ve sonraki durum karşılaştırılarak
   izlenecek (AdMob panelinden).

**Not:** Bu adım, önceki oturumlarda zaten kısmen ele alınmış bir konuyu
(istek yoğunluğu) daha da hassaslaştırıyor — mevcut dar pencere zaten
iyi bir temel, burada asıl iş "ilk 3" sınırının nicel/somut hale getirilmesi.

---

## Adım 5 — Reklam yerleşim mantığının gözden geçirilmesi ⬜

**Mevcut durum:** `AdPlanner.kt` tek bir saf fonksiyonla (`buildAdPlan`)
native'i önce yerleştirip banner'ı boş index'lere kaydırıyor — bu mimari
sağlam (çakışma yapısal olarak imkansız). Ama `position`/`frequency`
değerleri Remote Config'ten geliyor ve hâlâ **varsayılan olarak 5,5**
(her ekran için aynı) — ekranın içerik yoğunluğuna göre özelleştirilmemiş.

**Yapılacaklar:**
1. Her ekran için "position/frequency" değerinin o ekranın tipik içerik
   uzunluğuna göre mantıklı olup olmadığını değerlendir (örn. kısa bir
   liste — okuma listesi gibi — için `position=5` çok geç kalabilir,
   hiç reklam görünmeyebilir; uzun bir feed için `frequency=5` çok sık
   olabilir).
2. **Banner mı native mı** kararının kriterleri netleştirilecek:
   - Kart bazlı, görsel ağırlıklı listeler (feed, kütüphane alıntıları) → native
     (feed içeriğiyle görsel bütünlük sağlar)
   - Metin ağırlıklı, düz listeler (bildirimler, arama sonuçları) → banner
     (daha az müdahaleci)
   - Tekil detay sayfaları (blog yazısı, kitap detayı) → banner (üst/alt,
     içerik ortasına native koymak okuma akışını böler)
3. Yeni eklenecek her ekran için bu kritere göre banner/native/ikisi kararı
   verilip Adım 1'deki tabloya işlenecek.
4. `AdPlanner.kt`'nin native/banner dışında üçüncü bir tür (örn. sticky
   bottom banner, interstitial-like ara sayfa) ihtiyacı olup olmadığı
   değerlendirilecek — şu an için gerek görülmüyor, ama not düşülüyor.

---

## Adım 6 — Eksik ekranlara reklam alt yapısının eklenmesi (kapalı halde) ⬜

**Prensip:** Bu adımda **kod hazırlanacak ama reklam gerçek unitId ile
aktif edilmeyecek** — Remote Config'te `enabled: false` kalacak, gerçek
`unitId` değerleri Firebase Console'dan **daha sonra** girilecek.

**Yapılacaklar (Adım 1'deki karara göre, ekran ekran):**
1. `RemoteConfigManager.kt`'ye yeni key'ler eklenecek (Adım 2 ile birlikte).
2. Her ekranda:
   - `planFor()` çağrısı eklenecek (native/banner/ikisi, Adım 1 kararına göre)
   - `warmVisiblePositions` + `LaunchedEffect` + `rememberLazyListState`
     deseni eklenecek (Feed/Kurdi/Profile/Blog'daki kanıtlanmış desenin
     birebir aynısı — yeni bir desen icat edilmeyecek, tutarlılık için)
   - `DisposableEffect(Unit) { onDispose { ... } }` ile temizlik eklenecek
   - `AdSlotView` çağrısı listenin uygun noktalarına yerleştirilecek
3. `Column+verticalScroll` kullanan ekranlar (varsa) için `BlogPostScreen`'de
   kurulan `VisibilityGatedAdSlot` deseni tekrar kullanılacak.
4. Sohbet ekranı gibi kesin "reklam olmayacak" ekranlarda hiçbir değişiklik
   yapılmayacak — bu ekranlar listede "N/A" olarak işaretlenecek.

**Kontrol listesi (her ekran eklendiğinde):**
- [ ] `enabled: false` ile test edildi mi (reklam görünmemeli, hata da olmamalı)
- [ ] Ekrandan çıkışta `release*` çağrısı doğru prefix ile çalışıyor mu
- [ ] `warmVisiblePositions` dar pencere + debounce ile çalışıyor mu
- [ ] Diğer ekranlardaki mevcut reklamlar bu değişiklikten etkilenmedi mi

---

## Adım 7 — AdMob politikalarına uygunluk kontrolü ⬜

**Yapılacaklar:**
1. Her reklamın yanında "Reklam" etiketi ve bilgi ikonu olduğunu doğrula
   (`AdSlotView.kt` içinde `AdLabel` zaten var — tüm yeni eklenen ekranlarda
   da kullanıldığından emin ol).
2. Reklamların gerçek içerikle **görsel olarak karıştırılmadığından**
   emin ol (arka plan rengi, "Reklam" etiketi kontrastı, kenarlık) —
   AdMob'un "yanlışlıkla tıklama" politikası için kritik.
3. Interstitial'ın **kullanıcı eylemini kesintiye uğratmayan** noktalarda
   gösterildiğini doğrula (şu an `ScreenTracker.INTERSTITIAL_ALLOWED_ROUTES`
   ile okuma/yazma/auth/checkout ekranları zaten hariç tutulmuş — bu liste
   Adım 6'da eklenen yeni ekranlarla senkron tutulacak, örn. yeni bir
   "checkout benzeri" akış eklenirse listeye dahil edilmeyecek).
4. Native reklamların `NativeAdView` ile doğru sarmalandığını, zorunlu
   AdMob öğelerinin (advertiser, headline, call-to-action, ad choices icon)
   eksiksiz olduğunu `NativeAdCompose.kt` üzerinden doğrula.
5. UMP/GDPR onay akışının (`ConsentHelper.kt`) her yeni ekran için de
   `sdkReady` beklemesi gerektiği hatırlanacak — `HeftrangApp.sdkReady.first { it }`
   kalıbı zaten `AdsViewModel.loadAdConfigs()`'te var, yeni ekranlarda da
   reklam isteği bu bekleme tamamlanmadan atılmamalı.
6. Çocuklara yönelik olmayan/hedefli reklam ayarlarının (varsa) tutarlı
   olduğu kontrol edilecek.

---

## Adım 8 — Kullanıcı deneyimi değerlendirmesi ⬜

**Yapılacaklar:**
1. Her yeni eklenen reklamın **okuma/kullanım akışını böldüğü** noktalar
   tek tek gözden geçirilecek (örn. bir kitap alıntısını okurken araya
   giren native kart, akışı ne kadar bozuyor).
2. Shimmer/boş durum davranışı tutarlı mı kontrol edilecek — şu an
   `exhausted && adView == null` durumunda alan tamamen kaldırılıyor
   (`AdSlotView.kt`), bu davranış tüm yeni ekranlarda da korunacak
   (kalıcı "kırık" görünüm olmayacak).
3. Reklam yoğunluğunun (kaç kartta bir reklam) kullanıcıyı yormadığından
   emin olunacak — Adım 5'teki position/frequency kararlarıyla birlikte
   değerlendirilecek.
4. İnterstitial'ın `MIN_SCREENS_BETWEEN = 4` değerinin yeterli olup
   olmadığı, gerçek kullanım verisiyle (varsa analytics) tekrar
   değerlendirilecek.
5. Reklam bilgi dialogundaki metnin (`AdInfoDialog`) güncel ve doğru
   olduğu teyit edilecek.

---

## Genel Test Kontrol Listesi (her adım sonrası)

- [ ] Uygulama derleniyor mu (`./gradlew assembleDebug`)
- [ ] Değişen her ekran için: reklam `enabled:false` iken hiç görünmüyor
      ve hata vermiyor mu
- [ ] Ekrandan çıkışta (geri tuşu + navigasyonla başka ekrana geçiş)
      `release*` çağrılıyor mu (log veya breakpoint ile doğrulanabilir)
- [ ] Var olan reklamlı ekranlar (Feed/Kurdi/Profile/Blog) bu değişiklikten
      etkilenmedi mi
- [ ] Git commit mesajı `fix(ads): ...` veya `feat(ads): ...` formatında mı

---

## Termux Komut Taslağı (her adım için ortak şablon)

Her adımda güncellenmiş dosyaları içeren bir zip verilecek
(`heftreng-reklam-adimX.zip` gibi isimlendirilecek). `Download` klasörüne
indirdikten sonra aşağıdaki şablonu kullan — sadece zip adını ve
`git add` yolunu o adımda değişen dosyalarla değiştir:

```bash
cd ~/heftreng-android && \
cp /sdcard/Download/ZIP_ADI.zip . && \
mkdir -p ~/tmpx && \
unzip -o ZIP_ADI.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx ZIP_ADI.zip && \
git add DEĞİŞEN_DOSYA_YOLU_1 DEĞİŞEN_DOSYA_YOLU_2 && \
git commit -m "AÇIKLAYICI_COMMIT_MESAJI" && \
git push
```

### Adım 2 için örnek (Remote Config key'leri eklenirken):
```bash
cd ~/heftreng-android && \
cp /sdcard/Download/heftreng-reklam-adim2.zip . && \
mkdir -p ~/tmpx && \
unzip -o heftreng-reklam-adim2.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx heftreng-reklam-adim2.zip && \
git add app/src/main/java/com/heftreng/app/ads/RemoteConfigManager.kt && \
git commit -m "feat(ads): eksik ekranlar için Remote Config key'leri eklendi (enabled:false)" && \
git push
```

### Adım 6 için örnek (bir ekrana alt yapı eklenirken — örnek: SinglePostScreen):
```bash
cd ~/heftreng-android && \
cp /sdcard/Download/heftreng-reklam-adim6-singlepost.zip . && \
mkdir -p ~/tmpx && \
unzip -o heftreng-reklam-adim6-singlepost.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx heftreng-reklam-adim6-singlepost.zip && \
git add app/src/main/java/com/heftreng/app/ui/screens/post/SinglePostScreen.kt \
        app/src/main/java/com/heftreng/app/ads/RemoteConfigManager.kt && \
git commit -m "feat(ads): SinglePostScreen'e reklam alt yapısı eklendi (kapalı, enabled:false)" && \
git push
```

---

## Sıradaki Adım

👉 **Adım 1 — Her ekranı tek tek incele** ile başlıyoruz (bu MD'deki tablo
zaten ilk taramayla dolduruldu — sırada Search ve Library'nin
`warmVisiblePositions` eksikliğini gidermek, sonra sırayla reklamı hiç
olmayan ekranlara geçmek var).

Hazır olduğunda "başla" de, hangi ekrandan devam edeceğimizi netleştirip
kodu yazmaya başlayayım.
