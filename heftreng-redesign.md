# Heftreng — Yeniden Tasarım Vizyonu
> **Hedef kimlik:** Kürt kültürünün Goodreads + Duolingo'su  
> **Tarih:** Haziran 2026  
> **Durum:** Fikir & Planlama

---

## 1. Neden Yeniden Tasarım?

Mevcut Heftreng "her şeyi yapan" bir sosyal platform. Güçlü parçalar var — Kurdî dil öğrenme modülü, kütüphane sistemi, alıntılar — ama hepsi dağınık. Kullanıcı uygulamayı açtığında **ne için geldiğini** hemen anlayamıyor.

Yeni vizyon tek cümle: **Dil, kültür ve kitap etrafında toplanan bir Kürt/Türk topluluğu.**

---

## 2. Mevcut Durum Analizi

### Bottom Nav (Şu An)
| Sekme | İçerik | Sorun |
|-------|--------|-------|
| Feed | Sosyal paylaşımlar | Genel, odaksız |
| Blog | Blogger içerikleri | Uygulamayı hybrid yapıyor |
| Library | Kitap/Yazar/Alıntı | Çok derin, keşfedilemiyor |
| Kurdî | Dil öğrenme | İzole, diğerlerine bağlı değil |
| Profil | Kullanıcı bilgisi | Okuma listesi gömülü kalmış |

### Güçlü Yanlar (Korunacak)
- ✅ Kurdî Fêrbibe — XP/streak sistemi, AI destekli dersler
- ✅ Library — Kitap, yazar, alıntı, inceleme ekosistemi
- ✅ Feed — Topluluk bağı
- ✅ ReadingListEntry — Okuma durumu takibi
- ✅ FCM bildirimleri — Altyapı hazır
- ✅ Mesajlar + Bildirimler — Mevcut konumları iyi, dokunulmayacak

### Zayıf Yanlar (Düzeltilecek)
- ❌ Blog sekmesi odak dağıtıyor
- ❌ Streak sadece Kurdî'de, diğer modüllerle bağlantısız
- ❌ Alıntı paylaşmak çok derin (Library → Kitap → Alıntı ekle)
- ❌ Library'de görsel yok, liste görünümü sıkıcı
- ❌ Ferheng ve Rêziman sekmeleri "Yakında" yazıyor
- ❌ Okuma listesi profilde gizli kalmış
- ❌ Firestore okuma limiti riski — yüksek sorgulu modüller Firebase'de

---

## 3. Yeni Bottom Nav

```
📚 Keşfet  ·  🌿 Kültür  ·  🔤 Kurdî  ·  💬 Topluluk  ·  👤 Profil
```

> ⚠️ Mesajlar ve Bildirimler mevcut konumlarında kalıyor — değiştirilmiyor.

### 📚 Keşfet *(yeni — Library'nin büyütülmüş hali)*
- Kitaplar grid görünümde (kapak görseli ön planda)
- Yazarlar + Alıntılar + İncelemeler
- **Günün Alıntısı** — büyük hero kartı
- "Bu hafta öne çıkan kitap" kuratoryal bölüm
- "En çok alıntılanan yazar" listesi
- Arama entegrasyonu

### 🌿 Kültür *(Feed + Blog birleşimi, filtreli)*
- Sadece kültür/kitap/dil odaklı paylaşımlar
- Post oluştururken **kategori zorunlu:**
  - 📖 Kitap Yorumu
  - 💬 Alıntı
  - ❓ Soru / Tartışma
  - 🌍 Genel Kültür
- Üstte "Arkadaşlar ne okuyor?" şeridi (profil fotoğrafları + kitap kapağı)

### 🔤 Kurdî *(mevcut KurdiScreen — genişletilecek, Firebase'de kalıyor)*
- Mevcut: Ünite yol haritası, XP, streak
- **Yeni:** Ferheng (Sözlük) sekmesi aktif
- **Yeni:** Rêziman (Dilbilgisi) sekmesi aktif
- **Yeni:** Günlük kelime bildirimi ile uygulama açılımı

### 💬 Topluluk *(değiştirilmiyor)*
- Mesajlar ve Bildirimler mevcut konumlarında kalıyor
- Firebase altyapısı korunuyor (FCM, Firestore messages)

### 👤 Profil *(okuma odaklı yeniden düzenleme)*
- Hero bölümü: **"X kitap okudum · Y alıntı · Z gün streak"**
- Okuma listesi profilde birinci sırada (şu an gömülü)
- Rozetler bölümü (okuma meydan okumalarından kazanılan)
- Takip edilen yazarlar listesi

---

## 4. Kullanıcıyı Tutan Mekanizmalar

### 🔥 Günlük Açılma Sebepleri

#### Günün Alıntısı
Her gün farklı bir Kürtçe/Türkçe alıntı, Keşfet ekranında büyük kart olarak. Kullanıcı beğenirse koleksiyonuna ekler. Push bildirimi ile sabah gönderilir.

```
Bildirim: "Îro Peyvek — Cegerxwîn'den yeni bir alıntı seni bekliyor 📖"
```

#### Günlük Kelime (Ferheng Widget)
Kurdî derslerindeki kelimeler FCM ile bildirim olarak sabah iletilir. Tıklanınca uygulama açılır ve o kelimenin flashcard'ı gösterilir.

```
Bildirim: "Îro peyveke nû: Azadî — özgürlük 🔤"
```

#### "Bugün ne okudun?" Hatırlatıcısı
Öğleden sonra, kullanıcı o gün hiç okuma listesini güncellemediyse bildirim gönderilir.

---

### 📅 Streak Sistemi (Genişletilmiş)

Şu an streak sadece Kurdî derslerinde sayılıyor. Yeni sistemde şu eylemlerden **herhangi biri** günlük streak'i devam ettirir:

| Eylem | Puan |
|-------|------|
| Kurdî dersi tamamla | ⭐⭐⭐ |
| Alıntı paylaş / beğen | ⭐⭐ |
| Okuma listesini güncelle | ⭐⭐ |
| İnceleme yaz | ⭐⭐⭐ |
| Günün alıntısını koleksiyona ekle | ⭐ |

---

### 📖 Okuma Meydan Okuması

Aylık hedef sistemi:

- "Bu ay 3 kitap oku" → Profilde **Kitapçı** rozeti
- "Bu ay 10 alıntı paylaş" → **Gotinzan** rozeti
- "30 gün Kurdî streak" → **Zimanzan** rozeti

Topluluktaki diğer kullanıcıların ilerlemesi Feed'de görünür ("Ahmet bu ay 2. kitabını bitirdi").

---

### 👥 Sosyal Kancalar

**Yazara Abone Ol**
Bir yazarı takip et → o yazarla ilgili yeni alıntı/inceleme gelince bildirim al.  
→ Supabase: `author_followers` tablosu + Cloud Function ile FCM tetiklemesi.

**"Arkadaşlar ne okuyor?" Şeridi**
Kültür sekmesinin üstünde yatay kaydırmalı küçük bant. Her kart: profil fotoğrafı + kitap kapağı + "%47 tamamladı".  
→ Supabase: `reading_status` tablosuna tek sorgu (takip edilenler + status: reading).

**Kitap Sayfası Paylaşımı**
ReadingListEntry'e `current_page: Int` alanı eklenir. "123. sayfadayım" paylaşımı — Goodreads benzeri güncelleme.  
→ Supabase: `reading_status` tablosunda `current_page` kolonu.

---

### 🎨 Paylaşılabilir Alıntı Kartı

Bir alıntıya uzun basınca "Görsel oluştur" seçeneği. Amber arka plan + alıntı metni + yazar adı + Heftreng logosu → PNG üretilir → sistem paylaşım sheet'i açılır. Instagram/WhatsApp'a paylaşılabilir. **Organik büyüme.**

---

## 5. UX İyileştirmeleri

### Alıntı Ekleme Kısayolu
Şu an: Library → Kitap seç → Alıntı ekle (3 adım)  
Yeni: Feed FAB → "Alıntı" → Kitap seçici → Metin → Paylaş (2 adım)

### Library Grid Görünümü
Şu an liste görünümü. Kitap kapağı ön planda, 2 sütun grid. Görsel hafıza güçlü — kullanıcı kapağı görünce kitabı hatırlar.

### Offline Destek
- Son 10 alıntı cache'lenir (Room)
- Tamamlanan Kurdî dersleri offline erişilebilir
- İnternet yokken uygulama "boş ekran" yerine cached içerik gösterir

---

## 6. Kaldırılacaklar

| Özellik | Sebep |
|---------|-------|
| Blog sekmesi (ayrı nav) | Odak dağıtıyor; içerik Kültür'e entegre edilir |
| StoryShare ekranı | Yarım kalmış, kötü izlenim |
| Serials (şimdilik) | Geliştirilinceye kadar gizle |
| Yazar ekranı (ayrı nav) | Library içinde Author Detail yeterli |
| SupabaseTest ViewModel | Production'da olmamalı |

---

## 7. Firebase / Supabase Hibrit Mimari

### Temel İlke

Firebase'in ücretsiz planındaki **50.000 okuma/gün** limitini aşmamak için yüksek sorgulu modüller Supabase'e taşınır. Firebase'in güçlü olduğu — realtime push, auth, FCM — alanlarda kalır.

---

### 🔵 Firebase'de Kalacaklar

| Modül | Koleksiyon | Gerekçe |
|-------|-----------|---------|
| Kullanıcı kaydı / profil | `users/{uid}` | Auth ile senkron, az okunur |
| Mesajlar | `conversations / messages` | Realtime listener kritik |
| Bildirimler | `notifications/{uid}` | FCM tetikleme zinciri |
| Kurdî dersleri | `kf_units / kf_lessons / kf_vocab / kf_exercises` | İçerik nadiren değişir, cache'lenebilir |
| XP / Streak (Kurdî) | `users/{uid}` alt alanı | Kurdî ile senkron kalmalı |
| Günün Alıntısı (meta) | `daily_quote/{date}` | Günde 1 belge, az okunur |
| Admin paneli | `admins/{uid}` | Yönetimsel, az trafik |
| FCM token | `users/{uid}.fcmToken` | Push altyapısı |

---

### 🟢 Supabase'e Taşınacaklar

| Modül | Tablo | Gerekçe |
|-------|-------|---------|
| Kitaplar kataloğu | `library_books` | Grid görünüm → her açılışta tam liste okunur |
| Yazarlar | `authors` | Keşfet'te sık listelenir |
| Alıntılar | `quotes` | collectionGroup sorgusu pahalı, Supabase JOIN ucuz |
| İncelemeler | `reviews` | collectionGroup sorgusu pahalı |
| Okuma durumu | `reading_status` | Her profil ziyaretinde okunur, sosyal şerit için toplu sorgu |
| Okuma ilerlemesi | `read_progress` | Her bölüm okumada güncellenir, yüksek yazma/okuma |
| Yazar takibi | `author_followers` | Bildirim tetiklemesi için toplu sorgu |
| Günlük aktivite (streak genişletme) | `daily_activity` | Her gün her kullanıcı için yazılır/okunur |
| Rozet / Meydan okuma | `badges`, `challenges` | Aggregation sorgular gerektirir |
| Arama indexi | `search_index` | Full-text search, Firestore desteklemiyor |

---

### Supabase Tablo Şeması (Yeni)

```sql
-- Kitap kataloğu
CREATE TABLE library_books (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title_ku    TEXT,
  title_tr    TEXT,
  cover_url   TEXT,
  author_id   UUID REFERENCES authors(id),
  genre       TEXT,
  year        INT,
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- Yazarlar
CREATE TABLE authors (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  bio_ku      TEXT,
  bio_tr      TEXT,
  photo_url   TEXT,
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- Alıntılar
CREATE TABLE quotes (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  book_id     UUID REFERENCES library_books(id),
  author_id   UUID REFERENCES authors(id),
  uid         TEXT NOT NULL,            -- Firebase UID
  text_ku     TEXT,
  text_tr     TEXT,
  like_count  INT DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- İncelemeler
CREATE TABLE reviews (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  book_id     UUID REFERENCES library_books(id),
  uid         TEXT NOT NULL,
  rating      INT CHECK (rating BETWEEN 1 AND 5),
  body        TEXT,
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- Okuma durumu
CREATE TABLE reading_status (
  uid         TEXT NOT NULL,
  book_id     UUID REFERENCES library_books(id),
  status      TEXT CHECK (status IN ('reading','completed','dropped','want')),
  current_page INT DEFAULT 0,
  updated_at  TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (uid, book_id)
);

-- Okuma ilerlemesi (bölüm bazlı)
CREATE TABLE read_progress (
  uid         TEXT NOT NULL,
  book_id     UUID,
  chapter_id  TEXT,
  read_at     TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (uid, book_id, chapter_id)
);

-- Yazar takibi
CREATE TABLE author_followers (
  uid         TEXT NOT NULL,
  author_id   UUID REFERENCES authors(id),
  created_at  TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (uid, author_id)
);

-- Günlük aktivite (streak genişletme)
CREATE TABLE daily_activity (
  uid              TEXT NOT NULL,
  date             DATE NOT NULL,
  lesson_done      BOOLEAN DEFAULT false,
  quote_shared     BOOLEAN DEFAULT false,
  reading_updated  BOOLEAN DEFAULT false,
  review_written   BOOLEAN DEFAULT false,
  PRIMARY KEY (uid, date)
);

-- Rozetler
CREATE TABLE badges (
  uid         TEXT NOT NULL,
  badge_key   TEXT NOT NULL,   -- 'kitapci', 'gotinzan', 'zimanzan'
  earned_at   TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (uid, badge_key)
);
```

---

### Veri Akışı — Kimlik Köprüsü

Firebase Auth UID, Supabase tablolarında `uid TEXT` olarak primary key görevi görür. Her Supabase sorgusunda Firebase'den alınan JWT token doğrulama için kullanılır.

```
Firebase Auth  →  uid  →  Supabase (tüm tablolarda uid TEXT kolonu)
                           RLS policy: auth.uid() = uid
```

---

### Firebase'de Kalacak Koleksiyonlar (Değişmeden)

```
users/{uid}                    ← profil, fcmToken, dil tercihi
conversations/{convId}         ← mesaj listesi
messages/{convId}/msgs/{msgId} ← mesaj içeriği
notifications/{uid}/items/     ← bildirim listesi
kf_units / kf_lessons          ← Kurdî ders içerikleri
kf_vocab / kf_exercises        ← Kurdî kelime/alıştırma
daily_quote/{date}             ← günün alıntısı meta
admins/{uid}                   ← admin rolleri
```

---

## 8. Teknik Yol Haritası

### 🔴 Öncelik 1 — Kimlik Değişikliği (Nav)
- [x] Blog sekmesini kaldır, Keşfet sekmesi oluştur
- [x] Library'yi Keşfet altına taşı, grid görünümü ekle
- [x] Profilde okuma özet kartı ekle (kitap sayısı, alıntı sayısı, streak)

### 🟡 Öncelik 2 — Supabase Migrasyonu
- [x] `library_books`, `authors`, `quotes`, `reviews` tablolarını oluştur
- [x] `LibraryViewModel` → Firestore yerine Supabase client
- [x] `ReadingListViewModel` → `reading_status` tablosuna taşı
- [x] `BookViewModel` → `read_progress` tablosuna taşı (bölüm bazlı scroll yüzdesi, yüksek frekanslı yazma artık Supabase'de)
- [x] `SearchViewModel` → Supabase full-text search (hibrit: `ilike` + `*_lower` index, authors/library_books)
- [x] RLS policy'leri yaz (`public read` + `with check(true)` — anon-key yazma deseni, mevcut tablolarla aynı)

### 🟡 Öncelik 3 — Retention
- [x] Günün Alıntısı → Cloud Function (scheduled, günlük) + FCM (`scheduledDailyQuote`, `book_quotes`'tan rastgele seçim → `userNotifs` → `onNewNotif` FCM)
- [x] `daily_activity` tablosu ile streak genişlet (Feed açılışında kayıt + ardışık gün hesabı → `users.streak`, Profil hero'sundaki streak artık buradan da besleniyor)
- [x] `reading_status.current_page` alanı → "Arkadaşlar ne okuyor?" şeridi (Feed üstü, takip edilenler · `okuyorum` durumu; Okuma Listesi'nde "Sayfayı güncelle" menüsü ile yazılıyor)
- [x] Yazara abone ol → `author_follows` + Cloud Function ile FCM (`notifyAuthorFollowers`, alıntı/inceleme eklenince takipçilere bildirim)

### 🟢 Öncelik 4 — Büyüme
- [ ] Ferheng + Rêziman sekmelerini aktif et (Firebase'de kalır)
- [ ] Paylaşılabilir alıntı kartı (Canvas API → PNG)
- [ ] Rozet sistemi (`badges` tablosu)
- [ ] Offline cache (Room + alıntılar)

---

## 9. Modül → Altyapı Referans Tablosu

| Modül | Altyapı | Notlar |
|-------|---------|--------|
| Kullanıcı kaydı | 🔵 Firebase Auth | Değişmez |
| Profil bilgisi | 🔵 Firestore | `users/{uid}` |
| Mesajlar | 🔵 Firestore | Realtime critical |
| Bildirimler | 🔵 Firestore + FCM | Push altyapısı |
| Kurdî dersler | 🔵 Firestore | Cache'lenebilir, az güncellenir |
| Kitap kataloğu | 🟢 Supabase | Yüksek liste okuma |
| Yazarlar | 🟢 Supabase | JOIN ile alıntı/inceleme |
| Alıntılar | 🟢 Supabase | collectionGroup → JOIN |
| İncelemeler | 🟢 Supabase | collectionGroup → JOIN |
| Okuma durumu | 🟢 Supabase | Sosyal şerit için toplu sorgu |
| Okuma ilerlemesi | 🟢 Supabase | Sık güncellenen |
| Yazar takibi | 🟢 Supabase | Aggregation |
| Günlük aktivite | 🟢 Supabase | Streak genişletme |
| Arama | 🟢 Supabase | Full-text search |
| Rozetler | 🟢 Supabase | Aggregation |

---

## 10. Vizyon Özeti

```
ÖNCE                          SONRA
─────────────────────         ──────────────────────────
Genel sosyal platform    →    Kültür/dil/kitap topluluğu
Blog hybrid yapısı       →    Native içerik odağı
Streak = Kurdî dersi     →    Streak = kültürel katılım
Library gömülü           →    Keşfet = ana kimlik
Her şey Firestore        →    Firebase (auth/msg/notif/kurdî)
                               + Supabase (içerik/okuma/sosyal)
Bildirim yok / zayıf     →    Günlük açılma sebepleri
```

> **Tek sayfa versiyonu:** Heftreng, Kürtçe öğrenmek isteyen, Kürt/Türk edebiyatı okuyan ve bu deneyimi bir toplulukla paylaşmak isteyen insanların her gün açtığı uygulamadır. Firebase auth ve iletişimin omurgasını tutarken Supabase içerik ve sosyal okuma verilerini taşır — ikisi birlikte Firebase limitine takılmadan ölçeklenir.
