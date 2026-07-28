# Heftreng Web — Mimari Plan ve Yol Haritası

> **Konum:** `web/heftreng-web-plan.md`
> **Durum:** Faz 3 tamamlandı ✅. Faz 4 devam ediyor.

---

## Sürüm Geçmişi

| Sürüm | Açıklama |
|---|---|
| v25 | Faz 1: compose.service, auth.service, QuoteCard |
| v26 | Zip yol düzeltmesi |
| v26b | MD zipten çıkarıldı |
| v27 | Faz 2: post.service, profile.service, UserChip/Modal/LikersModal/InfiniteScroll |
| v28 | Bugfix: çift `</script>` (post + profile) |
| v29 | Faz 3: layout + login + register → auth.service.ts |
| v29b | MD → `web/` klasörüne taşındı |
| v30 | FAB sheet: Gönderi Yaz / Alıntı Paylaş seçenekleri |
| v31 | Bugfix: login+register çift `</script>`, svelte-ignore syntax, QuoteCard `$derived` |
| v32 | Profil: Alıntılar sekmesi eklendi, Kitaplar 2'li grid, `feed_post_id` filtresi |

---

## 1. Mevcut Durum (v32 itibarıyla)

| Dosya | Durum | Not |
|---|---|---|
| `routes/+layout.svelte` | ✅ | `initAuthListener()` |
| `routes/feed/+page.svelte` | ✅ | FAB sheet |
| `routes/compose/+page.svelte` | ✅ | `?type=quote` parametresi |
| `routes/post/[id]/+page.svelte` | ✅ | post.service.ts |
| `routes/profile/[uid]/+page.svelte` | ✅ | Alıntılar sekmesi, 2'li kitap grid |
| `routes/login/+page.svelte` | ✅ | auth.service.ts |
| `routes/register/+page.svelte` | ✅ | auth.service.ts |

### Profil Sekmeler (v32)
```
0 → Gönderiler   (feed postları)
1 → Alıntılar    ← YENİ (book_quotes, feed_post_id boş olanlar filtrelenir)
2 → Okuma Listesi
3 → Kitaplar & Seriler  (2'li grid, created_at DESC, tür rozeti)
```

### Android → Web Mantık Eşleştirmesi

| Android | Web |
|---|---|
| `BookQuote.toPost()` → `id = feedPostId ?: id` | `feed_post_id` boş olanlar filter edilir |
| `return@forEach` ile boş feedPostId atlanır | `.filter(q => q.feed_post_id !== '')` |
| `LazyVerticalGrid(columns = GridCells.Fixed(2))` | `grid-template-columns: repeat(2, 1fr)` |
| `BookCard` rozetleri: Seri=Primary, Kitap=Amber | `.book-type-badge.serial/.book` |

---

## 2. Mimari Harita

```
web/src/lib/
├── models/              ✅
├── services/
│   ├── auth.service.ts          ✅
│   ├── compose.service.ts       ✅
│   ├── feed.service.ts          ✅
│   ├── social.service.ts        ✅
│   ├── comment.service.ts       ✅
│   ├── profile.service.ts       ✅
│   ├── post.service.ts          ✅
│   ├── notification.service.ts  ⏳ Faz 4
│   ├── message.service.ts       ⏳ Faz 4
│   └── library.service.ts       ⏳ Faz 4
├── stores/              ✅ auth, feed, profile, ui
├── store/               auth=shim, theme=aktif
└── components/          ✅ Avatar, Skeleton, LikeButton, PostCard,
                            CommentPanel, QuoteCard, UserChip,
                            InfiniteScroll, Modal, LikersModal

web/src/routes/
├── +layout.svelte               ✅
├── feed/+page.svelte            ✅ FAB sheet
├── compose/+page.svelte         ✅
├── post/[id]/+page.svelte       ✅
├── profile/[uid]/+page.svelte   ✅ Alıntılar sekmesi, 2'li grid
├── login/+page.svelte           ✅
├── register/+page.svelte        ✅
├── notifications/               ⏳ Faz 4
├── messages/                    ⏳ Faz 4
├── library/                     ⏳ Faz 4
└── kurdi/                       ⏳ Faz 4
```

---

## 3. Temel Kural

| Katman | Ne içerir | Ne içermez |
|---|---|---|
| **models/** | TypeScript interface'ler | Fonksiyon |
| **services/** | Firestore/Supabase sorguları | UI state |
| **stores/** | Reactive state | Doğrudan DB |
| **components/** | Tekrar kullanılan bileşen | Sayfa-özel mantık |
| **routes/\*/+page.svelte** | Store + component | Doğrudan DB |

---

## 4. Yol Haritası

### Faz 1 ✅ (v26) — Temel servisler + bileşenler
### Faz 2 ✅ (v28) — post/profile refactor
### Faz 3 ✅ (v29-v31) — auth entegrasyonu, FAB sheet, bugfixler
### Faz 3.5 ✅ (v32) — Profil alıntılar sekmesi + kitap grid

### Faz 4 — Mesajlar, Bildirimler & Kütüphane ← Sıradaki
- [ ] `lib/services/notification.service.ts`
- [ ] `lib/services/message.service.ts`
- [ ] `lib/services/library.service.ts`
- [ ] `routes/notifications/+page.svelte`
- [ ] `routes/messages/+page.svelte`
- [ ] `routes/library/+page.svelte`

### Faz 5 — Kurdî & Admin
- [ ] `routes/kurdi/+page.svelte`
- [ ] `routes/admin/+page.svelte`
- [ ] `lib/models/cms.ts`

---

## 5. Önemli Notlar

**Svelte 5 ignore syntax:** tire değil alt çizgi kullan
```svelte
<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
```

**$props() ile reactive değer:** `const` değil `$derived` kullan
```typescript
// ❌ const isRtl = language === 'ku';
// ✅
const isRtl = $derived(language === 'ku' || language === 'ar');
```

**Çift `</script>` hatası:** Script bölümü `</script>` ile bitmeli, template orijinalinde de `</script>` varsa birleştirince çift oluşur. Her zaman template'in ilk satırı kontrol edilmeli.

**book_quotes filtresi:**
```typescript
// Android toPost() mantığı — feed_post_id boş olanları atla
userQuotes = data.filter(q => q.feed_post_id && q.feed_post_id.trim() !== '');
// Linkleme: /post/{q.feed_post_id}
```
