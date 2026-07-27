# Heftreng Web — Mimari Plan ve Yol Haritası

> **Konum:** `web/heftreng-web-plan.md` (web build tetikler, Android tetiklemez)
> **Durum:** Faz 3 tamamlandı ✅. Sırada Faz 4.

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

---

## 1. Mevcut Durum (v29 itibarıyla)

| Dosya | Durum | Not |
|---|---|---|
| `routes/+layout.svelte` | ✅ | `initAuthListener()` kullanıyor, `userProfile` doluyor |
| `routes/feed/+page.svelte` | ✅ | |
| `routes/compose/+page.svelte` | ✅ | |
| `routes/post/[id]/+page.svelte` | ✅ | |
| `routes/profile/[uid]/+page.svelte` | ✅ | |
| `routes/login/+page.svelte` | ✅ | `auth.service.ts#signIn()` |
| `routes/register/+page.svelte` | ✅ | `auth.service.ts#register()` + Firestore profil |
| `lib/store/auth.ts` | ✅ | Re-export shim → `$lib/stores/auth`'a yönlendirir |
| `lib/stores/auth.ts` | ✅ | Tek gerçek kaynak |
| `lib/components/Navbar.svelte` | — | Layout içinde inline, ayrı bileşen gerekmedi |

### Store Durumu

| Klasör | Durum |
|---|---|
| `lib/store/auth.ts` | Shim — sadece `$lib/stores/auth`'a re-export eder, silinebilir |
| `lib/store/theme.ts` | Aktif — `+layout.svelte` hâlâ bu dosyayı kullanıyor |
| `lib/stores/auth.ts` | ✅ Tek gerçek auth store |
| `lib/stores/feed.store.ts` | ✅ |
| `lib/stores/profile.store.ts` | ✅ |
| `lib/stores/ui.store.ts` | ✅ |

---

## 2. Mimari Harita

```
web/src/lib/
├── models/              ✅ Tüm interface'ler tamam
│
├── services/
│   ├── auth.service.ts          ✅ Faz 1 — initAuthListener/signIn/register/signOut
│   ├── compose.service.ts       ✅ Faz 1
│   ├── feed.service.ts          ✅ Faz 1
│   ├── social.service.ts        ✅ Faz 1
│   ├── comment.service.ts       ✅ Faz 1
│   ├── profile.service.ts       ✅ Faz 1+2
│   ├── post.service.ts          ✅ Faz 2
│   ├── notification.service.ts  ⏳ Faz 4
│   ├── message.service.ts       ⏳ Faz 4
│   └── library.service.ts       ⏳ Faz 4
│
├── stores/
│   ├── auth.ts          ✅ currentUser + userProfile + isLoggedIn
│   ├── feed.store.ts    ✅
│   ├── profile.store.ts ✅
│   └── ui.store.ts      ✅
│
├── store/ (eski — kısmen korunuyor)
│   ├── auth.ts   → shim, $lib/stores/auth'a re-export
│   └── theme.ts  → aktif, layout kullanıyor
│
├── components/
│   ├── Avatar.svelte        ✅ Faz 1
│   ├── Skeleton.svelte      ✅ Faz 1
│   ├── LikeButton.svelte    ✅ Faz 1
│   ├── PostCard.svelte      ✅ Faz 1
│   ├── CommentPanel.svelte  ✅ Faz 1
│   ├── QuoteCard.svelte     ✅ Faz 1
│   ├── UserChip.svelte      ✅ Faz 2
│   ├── InfiniteScroll.svelte ✅ Faz 2
│   ├── Modal.svelte         ✅ Faz 2
│   └── LikersModal.svelte   ✅ Faz 2
│
├── firebase/ ✅
└── supabase/ ✅

web/src/routes/
├── +layout.svelte               ✅ Faz 3 — initAuthListener() entegre
├── feed/+page.svelte            ✅
├── compose/+page.svelte         ✅
├── post/[id]/+page.svelte       ✅
├── profile/[uid]/+page.svelte   ✅
├── login/+page.svelte           ✅ Faz 3 — auth.service.ts#signIn()
├── register/+page.svelte        ✅ Faz 3 — auth.service.ts#register()
├── notifications/               ⏳ Faz 4
├── messages/                    ⏳ Faz 4
├── library/                     ⏳ Faz 4
└── kurdi/                       ⏳ Faz 4
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
- [x] `lib/models/` + tüm `lib/services/` temeli + `lib/stores/` + core bileşenler
- [x] `routes/feed` + `routes/compose` refactor

### Faz 2 ✅ (v27 → v28 bugfix)
- [x] UserChip, InfiniteScroll, Modal, LikersModal bileşenleri
- [x] `lib/services/post.service.ts`
- [x] `routes/post/[id]` + `routes/profile/[uid]` refactor

### Faz 3 ✅ (v29)
- [x] `routes/+layout.svelte` → `initAuthListener()` entegrasyonu
- [x] `routes/login/+page.svelte` → `auth.service.ts#signIn()`
- [x] `routes/register/+page.svelte` → `auth.service.ts#register()`
- [x] `lib/store/auth.ts` → re-export shim (geriye dönük uyumluluk)

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

## 5. Servis API Özeti

### auth.service.ts
```typescript
initAuthListener()        → unsubscribe fn  // layout'ta kullan
signIn(email, pw)         → UserCredential
register(email, pw, name) → UserCredential + Firestore user doc
signOut()                 → void
```

### compose.service.ts
```typescript
loadPost(id)            → Partial<Post> | null
uploadImage(file, uid)  → string (URL)
createPost(payload)     → string (id)
createQuote(payload)    → string (id)
updatePost(id, fields)  → void
```

### post.service.ts
```typescript
fetchPost(postId, uid?)            → {post, likesCount, isLikedByMe, isSavedByMe}
deletePost(postId)                 → void
fetchComments(postId, uid?)        → Comment[]
togglePostLike / togglePostSave    → void
addComment / editComment / deleteComment → Comment / void
toggleCommentLike                  → void
fetchPostLikers / fetchCommentLikers → Liker[]
```

### profile.service.ts
```typescript
fetchProfile / fetchSocialCounts / checkFollowStatus
toggleFollow / sendFollowRequest / cancelFollowRequest
fetchFollowers / fetchFollowing
fetchUserPosts / enrichPostsWithInteractions
fetchReadingList
updateProfile / checkUsernameAvailable / syncUsernameToSupabase
uploadAvatar / uploadCoverPhoto
```

---

## 6. Component API

```svelte
<InfiniteScroll hasMore={bool} loading={bool} onLoadMore={fn} />
<Modal bind:open title="..." maxWidth="480px" onclose={fn}> ... </Modal>
<LikersModal bind:open={bool} {likers} {loading} onclose={fn} />
<UserChip uid="..." name="..." photoURL="..." subtitle="..." />
<QuoteCard quoteText="..." bookName="..." authorName="..." coverImg="..." expanded={false} />
```
