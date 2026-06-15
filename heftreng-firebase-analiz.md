# Heftreng — Firebase Okuma Analizi & Supabase Migrasyon Planı
> Tarih: Haziran 2026 | Mevcut proje taranarak üretildi

---

## 0. Durum Güncellemesi (bu oturumda yapılanlar)

| Madde | Durum | Not |
|-------|-------|-----|
| `readingLists` → `reading_status` | ✅ Tamamlandı | Önceki oturum |
| `readProgress` → `read_progress` | ✅ Tamamlandı | Önceki oturum |
| `daily_activity` (streak) | ✅ Tamamlandı | Önceki oturum |
| `author_followers` (author_follows) | ✅ Tamamlandı | Önceki oturum |
| `badges` (user_badges) | ✅ Tamamlandı | Önceki oturum |
| Feed ana sayfa `limit(300)→limit(20)` | ✅ Zaten düzeltilmiş | `PAGE_SIZE=30` |
| `SearchViewModel` debounce | ✅ Zaten vardı | 300ms |
| `ProfileViewModel` post sayfalama | ✅ Zaten vardı | `POST_PAGE` |
| `KurdiViewModel` ders cache | ✅ Zaten vardı | `lessonContentCache` |
| **SearchViewModel: alıntı yazar/kitap arama** (`feed`→4 sorgu) | ✅ **Bu oturumda** | `book_quotes` ilike, 2 sorgu |
| **Keşfet → Alıntılar şeridi** (`feed`→2 sorgu/açılış) | ✅ **Bu oturumda** | `book_quotes.getRecentQuotes`, 1 sorgu |
| `chapters` + `chapterLikes` → `book_chapters` | ⏳ **Kalan — büyük** | Aşama 1, ayrı oturum önerilir |
| `serials`/`books` → `library_books` birleşimi | ⏳ **Kalan — büyük** | Aşama 1, ayrı oturum önerilir |
| Profil post listesi → Supabase `feed` tablosu | ⏳ **Kalan — çok büyük** | Aşama 2, tüm feed metadata taşınması gerekir |

---

## 1. Özet: Günlük Okuma Riski

Firebase Spark planı: **50.000 okuma/gün** limiti.

| Risk | ViewModel | Koleksiyon | Okuma Tipi |
|------|-----------|-----------|------------|
| 🔴 Çok Yüksek | FeedViewModel | `feed` (limit 300!) | Her açılışta 300 belge |
| 🔴 Çok Yüksek | BookViewModel | `chapters` (11 sorgu) | Her kitap açılışında |
| 🔴 Çok Yüksek | SearchViewModel | `feed` + `users` (14 sorgu) | Her arama |
| 🟡 Yüksek | ProfileViewModel | `feed` + `users` (21 sorgu) | Her profil ziyareti |
| 🟡 Yüksek | BookViewModel | `readProgress` | Bölüm bazlı sık yazma/okuma |
| 🟡 Yüksek | LibraryViewModel | `feed` (quotes collectionGroup) | Library ekranı açılışı |
| 🟢 Düşük | MessagesViewModel | `addSnapshotListener` | Realtime — Firebase'de kalmalı |
| 🟢 Düşük | NotificationsViewModel | `addSnapshotListener` | Realtime — Firebase'de kalmalı |
| 🟢 Düşük | KurdiViewModel | Ders içerikleri | Nadiren değişir, cache'lenebilir |

---

## 2. ViewModel Bazlı Detaylı Analiz

### 🔴 FeedViewModel — 32 Firestore erişim
```
feed              → 21 erişim  ← EN PAHALI
users             →  9 erişim
collectionGroup(quotes) → 1 erişim  ← tüm alt koleksiyonu tarar
userNotifs/msgs   →  1 erişim
```
**Kritik sorun:** Ana feed yüklemesi `limit(300)` — her ekran açılışında 300 belge okunuyor.  
Paginate edilmiş `PAGE_SIZE` sorgusu da var ama 300'lük ilk yükleme baskın.  
**feed_likes / feed_saves / feed_comments** zaten Supabase'de ✅

### 🔴 BookViewModel — 39 Firestore erişim
```
chapters     → 11 erişim  ← Her bölüm listesi + içerik ayrı okuma
users        →  7 erişim
readProgress →  4 erişim  ← Her bölüm okumasında yazılıp okunuyor
comments     →  3 erişim
chapterLikes →  3 erişim
serials      →  2 erişim
books        →  2 erişim
```
**Kritik sorun:** `chapters` koleksiyonu her kitap açılışında tam liste çekiyor.  
`readProgress` bölüm bazlı sık güncelleniyor = çift maliyet (yazma + okuma).

### 🔴 SearchViewModel — 16 Firestore erişim
```
users   →  9 erişim  ← Her arama sorgusunda
feed    →  5 erişim  ← feed'de yazar/kitap araması (çok pahalı)
serials →  2 erişim
```
**Kritik sorun:** Arama her tuş basışında veya submit'te `feed` koleksiyonunu tarıyor.  
Firestore full-text search desteklemiyor, bu yüzden birden fazla sorgu atılıyor.

### 🟡 ProfileViewModel — 30 Firestore erişim
```
users          → 14 erişim
feed           →  7 erişim  ← Profil post listesi (her ziyarette)
usernames      →  3 erişim
userNotifs/msgs→  6 erişim
followRequests →  3 erişim
```
**Sorun:** Her profil ziyaretinde `feed` koleksiyonu kullanıcı postlarını çekiyor.

### 🟡 LibraryViewModel — 5 Firestore erişim
```
feed → 5 erişim  ← quotes için feed koleksiyonuna bakıyor
```
Not: authors/library_books zaten Supabase'e taşınmış ✅  
Kalan `feed` sorguları alıntıları feed üzerinden çekiyor — bunlar da Supabase'e taşınabilir.

### 🟢 KurdiViewModel — 39 Firestore erişim (GÜVENLI)
İçerik nadiren değiştiği için cache'leniyor. AppConfig `source = CACHE` ile çalışıyor.  
**Firebase'de kalmalı** — zaten cache ile okuma minimumda.

### 🟢 MessagesViewModel — 25 Firestore erişim (GÜVENLI)
`addSnapshotListener` × 2 — realtime mesajlaşma için zorunlu.  
**Firebase'de kalmalı.**

### 🟢 NotificationsViewModel — 13 Firestore erişim (GÜVENLI)
`addSnapshotListener` × 1 — realtime bildirim için zorunlu.  
**Firebase'de kalmalı.**

---

## 3. Koleksiyon Bazlı Migrasyon Kararı

| Koleksiyon | Şu an | Karar | Gerekçe |
|-----------|-------|-------|---------|
| `feed` | Firebase | 🟡 **Karma** | Post metadata Firebase, likes/comments/saves Supabase'de zaten |
| `chapters` | Firebase | 🔴 **Supabase'e taşı** | 11 sorgu, her kitap açılışında |
| `readProgress` | Firebase | 🔴 **Supabase'e taşı** | Sık yazma/okuma, bölüm bazlı |
| `chapterLikes` | Firebase | 🔴 **Supabase'e taşı** | chapters ile birlikte |
| `serials` / `books` | Firebase | 🔴 **Supabase'e taşı** | library_books ile birleştir |
| `readingLists` | Firebase | 🔴 **Supabase'e taşı** | MD'de reading_status tablosu planlandı |
| `users` | Firebase | 🟢 **Firebase'de kal** | Auth ile senkron, az okunur |
| `conversations/messages` | Firebase | 🟢 **Firebase'de kal** | Realtime zorunlu |
| `userNotifs` | Firebase | 🟢 **Firebase'de kal** | FCM altyapısı |
| `kf_*` (Kurdî) | Firebase | 🟢 **Firebase'de kal** | Cache'leniyor |
| `admins` | Firebase | 🟢 **Firebase'de kal** | Az trafik |
| `appConfig` | Firebase | 🟢 **Firebase'de kal** | Az trafik |
| `feed_likes` | Supabase | ✅ Zaten taşındı | |
| `feed_saves` | Supabase | ✅ Zaten taşındı | |
| `feed_comments` | Supabase | ✅ Zaten taşındı | |
| `authors` | Supabase | ✅ Zaten taşındı | |
| `library_books` | Supabase | ✅ Zaten taşındı | |

---

## 4. Acil Düzeltme — Feed limit(300)

`FeedViewModel.kt` satır ~138:
```kotlin
// MEVCUT — çok pahalı
.limit(300)

// OLMASI GEREKEN — sayfalı yükleme
.limit(PAGE_SIZE)  // PAGE_SIZE = 20
```
Bu tek değişiklik günlük okuma sayısını dramatik düşürür.  
**Supabase migrasyonu beklenmeden hemen uygulanabilir.**

---

## 5. Supabase Migrasyon Öncelik Sırası

### 🔴 Aşama 1 — En yüksek etki
1. `readingLists` → `reading_status` (Supabase) — MD'de planlandı
2. `chapters` + `chapterLikes` → Supabase `book_chapters` tablosu
3. `readProgress` → Supabase `read_progress` tablosu
4. `serials` / `books` → Supabase `library_books` ile birleştir

### 🟡 Aşama 2 — Orta etki
5. Arama (`SearchViewModel`) → Supabase full-text search (`to_tsvector`)
6. Profil post listesi → Supabase `feed` tablosu (post meta)
7. `daily_activity` → Supabase (streak genişletme — MD'de planlandı)

### 🟢 Aşama 3 — Düşük öncelik
8. `author_followers` → Supabase (yazara abone ol)
9. `badges` → Supabase (rozet sistemi)

---

## 6. Tahmini Okuma Tasarrufu

| Değişiklik | Tahmini Tasarruf |
|-----------|-----------------|
| feed limit(300) → limit(20) | ~%93 feed okuma azalması |
| readingLists → Supabase | ~500 okuma/gün |
| chapters → Supabase | ~1.000 okuma/gün |
| readProgress → Supabase | ~2.000 okuma/gün |
| Arama → Supabase | ~3.000 okuma/gün |

---

## 7. Hemen Yapılabilecek (Supabase beklemeden)

- [ ] `feed` limit(300) → limit(20) — `FeedViewModel.kt` ~138
- [ ] `KurdiViewModel` cache stratejisi kontrol — zaten `CACHE` source kullanıyor mu doğrula
- [ ] `ProfileViewModel` — profil postları için sayfalama ekle (şu an tüm postlar çekiliyor)
- [ ] `SearchViewModel` — arama debounce ekle (her tuşa basışta sorgu atılıyor mu kontrol et)
