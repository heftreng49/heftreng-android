# Heftreng Web — Mimari Plan ve Yol Haritası

> **Konum:** `web/heftreng-web-plan.md` (web build tetikler, Android tetiklemez)
> **Durum:** Faz 4 tamamlandı ✅. Sırada Faz 5.

---

## Sürüm Geçmişi

| Sürüm | Açıklama |
|---|---|
| v25 | Faz 1: compose.service, auth.service, QuoteCard — yanlış zip yapısı |
| v26 | v25 zip yol düzeltmesi |
| v26b | MD dosyası zipten çıkarıldı |
| v27 | Faz 2: post.service, profile.service, UserChip/Modal/LikersModal/InfiniteScroll |
| v28 | Bugfix: çift `</script>` kapanış etiketi düzeltildi |
| v29 | Faz 3: layout + login + register → auth.service.ts entegrasyonu, eski store shim |
| v30 | Bugfix: login/register çift script, feed/Modal eski a11y formatı, QuoteCard isRtl |
| v31 | Profil düzelt: closeEditModal eksikti; compose alıntı ekranı Android stili; lib/store/auth.ts shim silindi |
| v32 | Faz 4: library.service.ts + routes/library/ + author/[id] + book/[id] + Navbar güncellendi |

---

## 1. Mevcut Durum (v32 itibarıyla)

| Dosya | Durum | Not |
|---|---|---|
| `routes/+layout.svelte` | ✅ | `initAuthListener()` kullanıyor |
| `routes/feed/+page.svelte` | ✅ | |
| `routes/compose/+page.svelte` | ✅ | Alıntı ekranı Android QuoteDialog stili |
| `routes/post/[id]/+page.svelte` | ✅ | |
| `routes/profile/[uid]/+page.svelte` | ✅ | |
| `routes/login/+page.svelte` | ✅ | |
| `routes/register/+page.svelte` | ✅ | |
| `routes/library/+page.svelte` | ✅ **YENİ** | 4 sekme: Alıntılar/İncelemeler/Yazarlar/Kitaplar |
| `routes/library/author/[id]/+page.svelte` | ✅ **YENİ** | 3 sekme, takip butonu |
| `routes/library/book/[id]/+page.svelte` | ✅ **YENİ** | 2 sekme, inceleme ekleme |
| `lib/services/library.service.ts` | ✅ **YENİ** | Android LibraryRepository karşılığı |
| `lib/components/Navbar.svelte` | ✅ | Kütüphane linki + aktif route göstergesi eklendi |

---

## 2. Mimari Harita

```
web/src/lib/
├── models/              ✅ Tüm interface'ler tamam
│
├── services/
│   ├── auth.service.ts          ✅ Faz 1
│   ├── compose.service.ts       ✅ Faz 1
│   ├── feed.service.ts          ✅ Faz 1
│   ├── social.service.ts        ✅ Faz 1
│   ├── comment.service.ts       ✅ Faz 1
│   ├── profile.service.ts       ✅ Faz 1+2
│   ├── post.service.ts          ✅ Faz 2
│   ├── library.service.ts       ✅ Faz 4 YENİ
│   ├── notification.service.ts  ⏳ Faz 5
│   └── message.service.ts       ⏳ Faz 5
│
├── stores/
│   ├── auth.ts          ✅
│   ├── feed.store.ts    ✅
│   ├── profile.store.ts ✅
│   └── ui.store.ts      ✅
│
├── store/
│   └── theme.ts  → aktif, layout kullanıyor
│
├── components/
│   ├── Avatar.svelte        ✅
│   ├── Skeleton.svelte      ✅
│   ├── LikeButton.svelte    ✅
│   ├── PostCard.svelte      ✅
│   ├── CommentPanel.svelte  ✅
│   ├── QuoteCard.svelte     ✅
│   ├── UserChip.svelte      ✅
│   ├── InfiniteScroll.svelte ✅
│   ├── Modal.svelte         ✅
│   ├── LikersModal.svelte   ✅
│   └── Navbar.svelte        ✅ Kütüphane linki eklendi
│
├── firebase/ ✅
└── supabase/ ✅

web/src/routes/
├── +layout.svelte                    ✅
├── feed/+page.svelte                 ✅
├── compose/+page.svelte              ✅
├── post/[id]/+page.svelte            ✅
├── profile/[uid]/+page.svelte        ✅
├── login/+page.svelte                ✅
├── register/+page.svelte             ✅
├── library/+page.svelte              ✅ Faz 4
├── library/author/[id]/+page.svelte  ✅ Faz 4
├── library/book/[id]/+page.svelte    ✅ Faz 4
├── notifications/                    ⏳ Faz 5
├── messages/                         ⏳ Faz 5
└── kurdi/                            ⏳ Faz 6
```

---

## 3. Temel Kural: Nereye Ne Gider?

| Katman | Ne içerir | Ne içermez |
|---|---|---|
| **models/** | TypeScript interface'ler | Fonksiyon, iş mantığı |
| **services/** | Firestore/Supabase sorguları | UI state, Svelte store |
| **stores/** | Reactive state | Doğrudan DB sorgusu |
| **components/** | Tekrar kullanılan bileşen | Sayfa-özel mantık |
| **routes/\*/+page.svelte** | Store bağlama + component dizimi | Doğrudan DB sorgusu |

---

## 4. Yol Haritası

### Faz 1 ✅ (v26)
- [x] `lib/models/` + `lib/services/` temeli + `lib/stores/` + core bileşenler
- [x] `routes/feed` + `routes/compose` refactor

### Faz 2 ✅ (v27 → v28)
- [x] UserChip, InfiniteScroll, Modal, LikersModal
- [x] `lib/services/post.service.ts`
- [x] `routes/post/[id]` + `routes/profile/[uid]` refactor

### Faz 3 ✅ (v29 → v31)
- [x] `routes/+layout.svelte` → `initAuthListener()`
- [x] `routes/login` + `routes/register` → `auth.service.ts`
- [x] `lib/store/auth.ts` shim silindi
- [x] Çeşitli build hataları giderildi

### Faz 4 ✅ (v32)
- [x] `lib/services/library.service.ts` — Android LibraryRepository karşılığı
  - fetchLibraryQuotes (Firebase, sayfalama)
  - fetchReviews / fetchAuthors / fetchBooks (Supabase)
  - fetchAuthorById / fetchAuthorBooks / fetchAuthorReviews / fetchAuthorQuotesFromFeed
  - fetchBookById / fetchBookQuotes / fetchBookReviews
  - addBookReview / createAuthor / createLibraryBook
  - checkAuthorFollow / followAuthor / unfollowAuthor
  - searchBooks / searchAuthors (compose QuoteDialog için)
- [x] `routes/library/+page.svelte` — 4 sekme (Android LibraryScreen)
- [x] `routes/library/author/[id]/+page.svelte` — yazar detay + takip (Android AuthorDetailScreen)
- [x] `routes/library/book/[id]/+page.svelte` — kitap detay + inceleme (Android LibraryBookDetailScreen)
- [x] `Navbar.svelte` — Kütüphane linki + aktif route göstergesi

### Faz 5 — Mesajlar & Bildirimler
- [ ] `lib/services/notification.service.ts`
- [ ] `routes/notifications/+page.svelte`
- [ ] `lib/services/message.service.ts`
- [ ] `routes/messages/+page.svelte` + `routes/messages/[uid]/+page.svelte`

### Faz 6 — Kurdî & Admin
- [ ] `routes/kurdi/+page.svelte`
- [ ] `routes/admin/+page.svelte`
- [ ] `lib/models/cms.ts` (varsa genişletme)

---

## 5. library.service.ts API Özeti

```typescript
// Alıntılar (Firebase)
fetchLibraryQuotes(lastDoc?)   → LibraryQuotePage { posts, lastDoc, hasMore }

// İncelemeler (Supabase)
fetchReviews()                 → BookReview[]
fetchBookReviews(bookId)       → BookReview[]
addBookReview(params)          → BookReview | null

// Yazarlar (Supabase)
fetchAuthors()                 → Author[]
fetchAuthorById(id)            → Author | null
fetchAuthorBooks(authorId)     → LibraryBook[]
fetchAuthorReviews(authorId)   → BookReview[]
fetchAuthorQuotesFromFeed(name)→ any[]  (Firebase feed)
checkAuthorFollow(uid, authorId) → boolean
followAuthor(uid, authorId)    → void
unfollowAuthor(uid, authorId)  → void
createAuthor(params)           → Author | null

// Kitaplar (Supabase)
fetchBooks()                   → LibraryBook[]
fetchBookById(id)              → LibraryBook | null
fetchBookQuotes(bookId, title?)→ any[]  (Firebase önce, Supabase fallback)
createLibraryBook(params)      → LibraryBook | null

// Arama (compose QuoteDialog)
searchBooks(q)                 → { id, title, authorName, coverImg }[]
searchAuthors(q)               → { id, name }[]
```
