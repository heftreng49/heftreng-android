# Heftreng iOS — Adım Adım Kurulum
### Mac yok, para yok, test edilebilir build

---

## Ne yapıyoruz?

```
Mevcut web proje (SvelteKit)
        ↓
    Capacitor ekle
        ↓
  iOS Xcode projesi (web/ios/) oluştur  ← BİR KEZ, Codespaces'te
        ↓
  GitHub Actions her push'ta build alır ← Otomatik, ücretsiz
        ↓
  Simulator'da test edilebilir          ← Geliştirici hesabı lazım değil
        ↓
  TestFlight / App Store                ← Hazır olunca
```

---

## ADIM 1 — Dosyaları repoya ekle

Bu paketteki dosyaları projeye kopyala:

```
web/package.json          → web/package.json'u GÜNCELLE (mevcut yerine yaz)
web/capacitor.config.ts   → web/ klasörüne ekle (YENİ dosya)
web/ios-patch.css         → web/src/ klasörüne ekle (YENİ dosya)
web/capacitor-push-init.ts→ web/src/lib/services/ klasörüne ekle (YENİ dosya)
.github/workflows/ios.yml → .github/workflows/ klasörüne ekle (YENİ dosya)
```

Sonra `web/src/app.css` dosyasının EN SONUNA şu satırı ekle:
```css
@import './ios-patch.css';
```

Sonra `web/src/routes/+layout.svelte` içinde `<script>` bölümüne import ekle
ve `onMount` içinde çağır:
```typescript
import { initCapacitorPush } from '$lib/services/capacitor-push-init';

onMount(() => {
  initCapacitorPush();   // ← bunu mevcut onMount'un EN BAŞINA ekle
  // ... mevcut kodun devamı
});
```

---

## ADIM 2 — Codespaces'te web/ios/ oluştur (BİR KEZ)

1. GitHub'da repona git
2. Yeşil "Code" butonu → "Codespaces" sekmesi → "New codespace"
3. Codespace açıldığında terminal'e yaz:

```bash
bash codespaces-ilk-kurulum.sh
git commit -m "feat(ios): Capacitor iOS projesi eklendi"
git push
```

Bu işlem ~5 dakika sürer. `web/ios/` klasörü repoya eklenir.
**Bir daha bu adımı yapmana gerek yok.**

---

## ADIM 3 — GitHub Actions'ta test et

Push yaptıktan sonra:
- GitHub → Actions → "iOS Build" workflow'u otomatik başlar
- `macos-15` runner üzerinde çalışır (ücretsiz, public repo)
- ~15-20 dakika sonra sonuç görünür

✅ Yeşil → Build başarılı, her şey hazır
❌ Kırmızı → Log'u buraya gönder, birlikte bakarız

---

## ADIM 4 — Gerçek cihazda test (Apple hesabı açınca)

Apple Developer hesabı ($99/yıl) açtıktan sonra şu secrets'ları ekle:
GitHub → Repo → Settings → Secrets and variables → Actions

| Secret | Ne |
|---|---|
| `IOS_CERTIFICATE_BASE64` | Dağıtım sertifikası .p12 → base64 |
| `IOS_CERTIFICATE_PASSWORD` | Sertifika şifresi |
| `IOS_PROVISIONING_BASE64` | App Store profile → base64 |
| `IOS_KEYCHAIN_PASSWORD` | Rastgele güçlü bir şifre yaz |
| `APPLE_TEAM_ID` | Developer hesabındaki 10 haneli ID |

Sonra Actions → "iOS Build" → "Run workflow" → `sign_and_export: true`

`.ipa` dosyası Artifacts'te belirir → TestFlight'a yükle → iPhone'da test et.

---

## Mevcut Firebase/Supabase değişecek mi?

**Hayır.** Hiçbir şey değişmiyor:
- Firebase JS SDK iOS WebView içinde çalışır
- Supabase JS SDK iOS WebView içinde çalışır
- Tüm servisler, store'lar, componentler aynı

`capacitor-push-init.ts` sadece FCM token'ını iOS için de kaydeder —
mevcut Android FCM altyapısıyla tam uyumlu.

---

## Takvim

| Hafta | Hedef |
|---|---|
| Bu hafta | ADIM 1-2-3: İlk başarılı GitHub Actions build |
| Sonra | UI ince ayarlar (safe-area, iOS hissi) |
| Hazır olunca | ADIM 4: Apple hesabı + TestFlight |
| Son | App Store |
