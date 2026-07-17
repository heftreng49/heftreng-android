# Prefetch Kullanım Kılavuzu

## Ekranlara Eklenmesi Gereken Tek Satır

Her liste ekranında `adPlan` hazırlandıktan hemen sonra
`prefetchForScreen` çağrısı ekle. Bu sayede kullanıcı scroll
etmeden önce ilk 3 reklam yüklenir.

---

### FeedScreen / LazyColumn içeren tüm ekranlar

```kotlin
// Mevcut kod (değişmez):
val adPlan = remember(items.size) {
    adsVm.planFor("feed", items.size,
        nativeKey = RemoteConfigManager.KEY_NATIVE_FEED,
        bannerKey = RemoteConfigManager.KEY_BANNER_FEED,
    )
}

// EKLE — plan değişince (ilk yükleme dahil) prefetch tetikler:
LaunchedEffect(adPlan) {
    adsVm.prefetchForScreen(adPlan)
}
```

---

### KurdiScreen

```kotlin
LaunchedEffect(adPlan) {
    adsVm.prefetchForScreen(adPlan)
}
```

---

### LibraryScreen / BlogScreen / vb.

Aynı pattern — sadece `adPlan` değişkeninin adını eşleştir.

---

## warmVisiblePositions — değişen tek şey

`WARM_LOOKAHEAD` 3'ten 12'ye çıktı (AdEngine sabiti).
`warmVisiblePositions` çağrıları değişmez — parametre almıyor.

---

## onAppBackground değişikliği

```kotlin
// MainActivity veya AdsViewModel kullanan yer:
// ESKİ:
adsVm.onAppBackground()  // sadece banner pause + native sil

// YENİ: aynı çağrı, ama engine.onBackground() çalışıyor:
// → banner pause
// → grace job'ları iptal
// → 30sn sonra native temizlik (race condition yok)
adsVm.onAppBackground()  // çağrı değişmez, davranış düzeldi
```

---

## Özet: Ne Değişti

| Alan | v2 | v3 |
|------|----|----|
| Prefetch | Yok | İlk 3 pozisyon ekran açılırken |
| Warm lookahead | viewport+3 kart | viewport+12 kart |
| Banner no-fill | Anında exhausted | 60sn sonra 1 deneme daha |
| Background temizlik | Anında + race | 30sn grace, race yok |
| Kod değişikliği | — | `LaunchedEffect(adPlan) { prefetchForScreen(adPlan) }` |
