# 📊 PROGRESS - Heftreng Android

Bu dosya otomatik veya manuel olarak güncellenebilecek, proje ilerlemesini, teknik değerlendirmeleri ve yapılacakları eyleme dönük bir şekilde tutmak için hazırlanmıştır. Rapor repo dosyalarından (build.gradle.kts, app/build.gradle.kts, functions/, firestore.rules, vb.) çıkarımlar yapılarak oluşturuldu.

---

## Özet (kısa)
- Proje: Android uygulaması (Jetpack Compose etkin), Firebase backend (Functions, Firestore, Messaging, Storage) kullanılıyor.
- DI: Hilt; Coroutines, Ktor client, Coil, AdMob, Play In-App Update, Firebase Functions entegre edilmiş.
- Mevcut dikkat çeken durumlar: `functions/package-lock.json` büyük (~100 KB), repository içinde geçmişte node_modules veya büyük dosyalar olmuş olabilir, `.firebase/` dizini repoda bulunuyor. `functions/.gitignore` ve root `.gitignore` mevcut (iyi) fakat geçmişte büyük dosyalar commit edilmişse repoda yer kaplamaya devam eder.

---

## Kritik bulgular (öncelik: yüksek)
1. functions/package-lock.json ve olası node_modules geçmişi
   - `functions/package-lock.json` repoda kalabilir (dependency determinism için normal), fakat geçmişte `node_modules/` commit edildiyse repo geçmişi şişmiş olabilir. Eğer hâlihazırda `functions/node_modules/` reponun HEAD'inde yoksa problem yok; ama geçmişte büyük dosyalar varsa `git history` temizliği gerekebilir.
   - Öneri: `git rm -r --cached functions/node_modules/` (ve root için `node_modules/` varsa) → commit + push. Ardından geçmişten tamamen silmek istersen BFG veya git filter-repo kullan.

2. Gizli/config dosyaları kontrolü
   - Depoda `.firebase/` klasörü görünüyor; içinde gizli token/ci config olabilir. Benzer şekilde `gradle.properties` veya başka dosyalarda gizli anahtarlar olup olmadığı kontrol edilmeli.
   - Öneri: `.firebase/` ve `.env` türü dosyaları `.gitignore` içine ekle ve eğer hassas içerik geçmişe geçmişse `git-secrets` ve `git filter-repo` ile geçmişi temizle.

3. Firestore rules ve indeksler
   - `firestore.rules` ve `firestore.indexes.json` repoda yer alıyor. Bu iyi: kuralların kaynak kontrolde olması gerekiyor. Ancak kuralları periyodik gözden geçirme (least-privilege) ve test etme önerilir.
   - Öneri: Security review; unit test veya emulator testleri ile kuralların beklenen davranışı sağladığından emin olun.

---

## Teknik ve organizasyonel öneriler (önceliklendirilmiş)

A. Yüksek öncelikli (yapılmalı - 1 gün içinde)
- 1. node_modules'in HEAD'te olmadığından emin ol ve cache'ten kaldır
  - Komutlar:
    ```bash
    git rm -r --cached node_modules/ || true
    git rm -r --cached functions/node_modules/ || true
    git add .
    git commit -m "chore: Remove node_modules from repository and update .gitignore"
    git push
    ```
  - Etki: Repo küçük kalır; clone/pull hızlanır.
  - Tahmini süre: 10–30 dakika (push süresine bağlı).

- 2. `.firebase/` ve hassas dosyaları `.gitignore`'a ekle ve geçmişte varsa temizle
  - Komutlar (basit):
    ```bash
    echo ".firebase/" >> .gitignore
    git add .gitignore
    git commit -m "chore: Ignore .firebase directory"
    git push
    ```
  - Eğer gizli anahtar geçmişe geçmişse: `git filter-repo` veya BFG kullanın (aşağıda adımlar var).

- 3. Firestore rules testleri
  - Firebase Emulator Suite ile kuralları test et: unit/integration testleri yaz.
  - Tahmini süre: 1–2 gün (test kapsamına göre).

B. Orta öncelikli (hafta içinde)
- 4. CI pipeline ekle (GitHub Actions)
  - Önerilen işler: Gradle build (assembleDebug), lint check, ktlint/kotlin-format, unit tests, ./gradlew detekt (statik analiz), functions için npm audit veya npm ci + lint.
  - Etki: Otomatik kalite kontrol, PR'larda erken hata yakalama.
  - Tahmini süre: 1–2 gün.

- 5. README.md oluştur
  - İçerik: proje tanımı, hızlı kurulum, çalışma adımları (firebase emulators, local functions), katkı rehberi.
  - Tahmini süre: 30–60 dakika.

C. Düşük/İyileştirme (2–6 hafta planı)
- 6. Repodaki büyük dosya geçmişini temizleme (isteğe bağlı ama önerilir)
  - Araç: BFG Repo-Cleaner veya git filter-repo
  - Adımlar:
    1. Lokal repoyu klonla (mirror):
       ```bash
       git clone --mirror https://github.com/heftreng49/heftreng-android.git
       ```
    2. BFG ile `node_modules` ve `.firebase` vb. sil:
       ```bash
       bfg --delete-folders node_modules --delete-files ".DS_Store" --delete-folders .firebase heftreng-android.git
       ```
    3. push:
       ```bash
       cd heftreng-android.git
       git reflog expire --expire=now --all && git gc --prune=now --aggressive
       git push
       ```
    - Uyarı: Bu işlem commit geçmişini değiştirir; tüm contributor'ların tekrar klonlama yapması gerekir. Öncesinde takım bilgilendirilmeli.
  - Tahmini süre: 1–3 saat (büyüklüğe göre).

- 7. Dependency yönetimi ve güvenlik
  - `functions/package-lock.json` varsa `npm audit` çalıştır, high/critical CVE varsa güncelle veya patch uygula.
  - `./gradlew dependencies` ile Android tarafı bağımlılıklarını incele, potansiyel outdated/duplicated paketleri güncelle.
  - Tahmini süre: 1–2 gün.

- 8. Test coverage ve kalite metrikleri
  - Unit testler (ViewModel, repository), instrumentation tests (kritik flow) ekle. Coverage hedefi %60+ başlangıç.
  - Tahmini süre: geniş kapsamda birkaç hafta.

---

## Riskler ve dikkat edilmesi gerekenler
- Git geçmişini temizlemek (BFG/filter-repo) geri dönüşü zor bir işlemdir; dikkatli planlanmalı ve yedek alınmalı.
- `.firebase/` veya benzeri dosyalar içinde serviceAccount json veya token gibi hassas bilgiler varsa bunlar derhal invalidate edilmeli (ör. Firebase servis hesabı keyleri), çünkü geçmişe erişim hala mümkün olabilir.
- Keystore bilgileri `gradle.properties` veya repo içinde bulunuyorsa (**kritik risk**) bu bilgileri ortam değişkenleri ile CI'da sakla ve mevcut credential'ları rotasyonla değiştir.

---

## Hemen atılacak adımlar (checklist)
- [ ] 1. `git rm -r --cached functions/node_modules/` + commit & push (Hızlı)
- [ ] 2. `.firebase/` ve diğer hassas yolları `.gitignore` a ekle (Hızlı)
- [ ] 3. Firestore rules için emulator tabanlı testler yaz (Gün içinde başla)
- [ ] 4. GitHub Actions CI pipeline ekle: build + lint + tests (Bu sprint içinde)
- [ ] 5. README.md ekle (Bu sprint içinde)
- [ ] 6. Eğer repo geçmişi şişkinse: BFG / git filter-repo planı ve uygulama (Planla ve duyur)

---

## Önerilen repo dosyaları ve şablonlar
- README.md (kısa proje özeti + kurulum)
- PROGRESS.md (bu dosya)
- .github/workflows/ci.yml (Gradle build, lint, tests)
- .github/PULL_REQUEST_TEMPLATE.md (PR açıklama şablonu)
- CONTRIBUTING.md (kod standartları, branch policy)

---

## Kapanış
Bu rapor tamamen repodaki mevcut dosyalardan çıkarım yapılarak hazırlanmıştır. İstersen ben bu dosyayı repoya commit edebilirim (direkt main'e veya yeni branch + PR). Ayrıca istersen BFG ile geçmiş temizliği adımlarını ben hazırlayıp bir PR veya runbook olarak ekleyebilirim.

Yapmamı ister misin?
- A) PROGRESS.md'yi main'e commit et
- B) yeni branch açıp commit + PR oluştur
- C) Önce bir taslak gösterip onay alalım
