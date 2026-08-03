# Heftreng Web — Modülerleştirme Planı v2
> Mevcut planın eksiklerini kapatan, gerçek kod analizine dayalı tam plan.

---

## Mevcut Durum — Gerçek Tekrar Analizi

| Kalıp | Kaç dosyada tekrar ediyor | Toplam satır israfı |
|---|---|---|
| `ago()` fonksiyonu | 4 dosya | ~15 satır × 4 = 60 |
| Pull-to-refresh mantığı | 3 dosya | ~20 satır × 3 = 60 |
| Tab çubuğu + URL sync | 4 dosya | ~15 satır × 4 = 60 |
| Kullanıcı başlık satırı (avatar + isim + zaman + menü) | 3 dosya | ~30 satır × 3 = 90 |
| Beğeni/yorum/paylaş aksiyonları | 3 dosya | ~25 satır × 3 = 75 |
| Empty state bloğu | 5 dosya | ~8 satır × 5 = 40 |
| Back button + top bar | 2 detay sayfası | ~10 satır × 2 = 20 |
| Skeleton kart kalıbı | 4 dosya | ~15 satır × 4 = 60 |
| **Toplam** | | **~465 satır kopyala-yapıştır** |

### Sayfa boyutları (şu an)
| Sayfa | Satır |
|---|---|
| `profile/[uid]/+page.svelte` | **1933** |
| `post/[id]/+page.svelte` | 792 |
| `library/+page.svelte` | 694 |
| `feed/+page.svelte` | 555 |

---

## Hedef Yapı

```
web/src/lib/
│
├── utils/
│   └── time.ts              ← ago() tek kaynak — 4 dosyadan kaldırılır
│
├── components/
│   │
│   ├── # Mevcut — değişmeden kalıyor
│   ├── Avatar.svelte
│   ├── LikeButton.svelte
│   ├── QuoteCard.svelte
│   ├── PostCard.svelte
│   ├── CommentPanel.svelte
│   ├── Skeleton.svelte
│   ├── Modal.svelte
│   ├── InfiniteScroll.svelte
│   ├── LikersModal.svelte
│   ├── UserChip.svelte
│   ├── Navbar.svelte
│   │
│   ├── # YENİ — UI primitifleri
│   ├── EmptyState.svelte    ← ikon + başlık + hint; 5 dosyadan kaldırılır
│   ├── TabBar.svelte        ← sticky sekmeler + URL sync; 4 dosyadan kaldırılır
│   ├── PullToRefresh.svelte ← slot wrapper; 3 dosyadan kaldırılır
│   ├── UserHeader.svelte    ← avatar + isim + zaman + 3-nokta menü; 3 dosyadan
│   ├── ActionBar.svelte     ← beğeni + yorum + paylaş; 3 dosyadan kaldırılır
│   └── PageTopBar.svelte    ← geri butonu + başlık; detay sayfalarında
│
routes/library/
│   ├── +page.svelte         ← 694 → ~80 satır (sadece tab yönetimi)
│   ├── _Quotes.svelte       ← ~150 satır
│   ├── _Reviews.svelte      ← ~100 satır
│   ├── _Authors.svelte      ← ~80 satır
│   └── _Books.svelte        ← ~60 satır
```

---

## Bileşen Spesifikasyonları (Genişletilmiş)

### `utils/time.ts`
Bütün sayfalarda kopyalanmış `ago()` fonksiyonu buraya taşınır.

```ts
export function ago(ts: unknown): string { ... }
// Kullanım: import { ago } from '$lib/utils/time';
```

---

### `EmptyState.svelte`
```svelte
<EmptyState icon="💬" message="Henüz alıntı yok." />
<EmptyState icon="✍️" message="Bu yazara ait alıntı yok." hint="İlk paylaşan sen ol!" />
<!-- SVG ikon da desteklenir: -->
<EmptyState message="Gönderi bulunamadı.">
  <svelte:fragment slot="icon"><MyIcon /></svelte:fragment>
</EmptyState>
```
**Props:** `icon?: string`, `message: string`, `hint?: string`

---

### `TabBar.svelte`
URL sync dahil, sticky, aktif sekme underline animasyonu.

```svelte
<TabBar
  tabs={['Alıntılar', 'İncelemeler', 'Yazarlar', 'Kitaplar']}
  bind:active={activeTab}
  counts={[quotes.length, reviews.length, authors.length, books.length]}
  urlParam="tab"
  stickyTop={52}
/>
<!-- counts — opsiyonel, sekme yanında sayı badge gösterir (yazar sayfası gibi) -->
```

**Props:**
| Prop | Tip | Default |
|---|---|---|
| `tabs` | `string[]` | zorunlu |
| `active` | `number` | zorunlu (bind) |
| `counts` | `number[]?` | — |
| `urlParam` | `string?` | `"tab"` |
| `stickyTop` | `number?` | `52` |

**URL sync:** `replaceState` — history eklenmez, geri tuşu sekme değiştirmez.

---

### `PullToRefresh.svelte`
Slot tabanlı; içeriye ne geçilirse onu sarar.

```svelte
<PullToRefresh onRefresh={loadAll} bind:refreshing>
  <!-- içerik -->
</PullToRefresh>
```

**Props:** `onRefresh: () => Promise<void>`, `threshold?: number (72)`, `disabled?: boolean`
**Bind:** `refreshing: boolean` — parent gerekirse dinleyebilir

---

### `UserHeader.svelte`
PostCard üstündeki kullanıcı satırı. Feed, library alıntıları ve yazar sayfasında aynı kalıp.

```svelte
<UserHeader
  uid={q.uid}
  displayName={q.userDisplayName}
  photoURL={q.userPhotoURL}
  ts={q.ts}
  menuItems={[
    { label: 'Gönderiye git', href: '/post/' + q.feedPostId },
    { label: 'Paylaş', onclick: () => share(q) },
  ]}
/>
<!-- menuItems yoksa 3-nokta butonu render edilmez -->
```

**Props:**
| Prop | Tip |
|---|---|
| `uid` | `string` |
| `displayName` | `string` |
| `photoURL?` | `string` |
| `ts?` | `unknown` |
| `size?` | `number (36)` |
| `menuItems?` | `{ label: string; href?: string; onclick?: () => void }[]` |
| `tag?` | `string` — "alıntı paylaştı" gibi alt etiket |

---

### `ActionBar.svelte`
Beğeni + yorum + paylaş satırı. Feed ve library'de birebir aynı.

```svelte
<ActionBar
  liked={q.isLikedByMe}
  likeCount={q.likesCount}
  commentHref="/post/{q.feedPostId}"
  onLike={() => handleLike(q)}
  onShare={() => share(q)}
/>
<!-- compact mod: inceleme kartı altı gibi küçük gösterim -->
<ActionBar liked={rv.isLikedByMe} likeCount={rv.likesCount} onLike={...} compact />
```

**Props:**
| Prop | Tip | Default |
|---|---|---|
| `liked` | `boolean` | `false` |
| `likeCount` | `number` | `0` |
| `commentHref?` | `string` | — (yoksa yorum ikonu yok) |
| `commentCount?` | `number` | — |
| `onLike` | `() => void` | zorunlu |
| `onShare?` | `() => void` | — (yoksa paylaş ikonu yok) |
| `compact?` | `boolean` | `false` |

---

### `PageTopBar.svelte`
Detay sayfalarında (yazar, kitap, post) üstteki geri + başlık çubuğu.

```svelte
<PageTopBar title={author?.name ?? ''} loading={loading} />
<!-- veya sağ aksiyon ile -->
<PageTopBar title={book?.title ?? ''}>
  <svelte:fragment slot="right">
    <button onclick={share}>...</button>
  </svelte:fragment>
</PageTopBar>
```

---

## Library Sekme Parçaları

`library/+page.svelte` sadece state ve tab yönetimi:

```svelte
<!-- Sadece bu kadar kalır (~80 satır) -->
<script>
  import TabBar from '$lib/components/TabBar.svelte';
  import PullToRefresh from '$lib/components/PullToRefresh.svelte';
  import Quotes   from './_Quotes.svelte';
  import Reviews  from './_Reviews.svelte';
  import Authors  from './_Authors.svelte';
  import Books    from './_Books.svelte';

  const TABS = ['Alıntılar', 'İncelemeler', 'Yazarlar', 'Kitaplar'];
  let activeTab = $state(0);
  ...
</script>

<PullToRefresh onRefresh={refresh}>
  <TabBar tabs={TABS} bind:active={activeTab} urlParam="tab" />
  {#if activeTab === 0}<Quotes />{/if}
  ...
</PullToRefresh>
```

Her sekme parçası kendi verisini yönetir — parent'a bağımlılığı yok.

---

## Uygulama Sırası

### Aşama 1 — Bağımsız yardımcılar (sıfır bağımlılık)
1. `utils/time.ts` — ago() ortak fonksiyon
2. `EmptyState.svelte` — saf UI, prop'tan render
3. `PageTopBar.svelte` — saf UI

### Aşama 2 — Davranışlı primitifler
4. `PullToRefresh.svelte` — dokunma mantığı + slot
5. `TabBar.svelte` — URL sync + animasyon

### Aşama 3 — Bileşik bileşenler (diğerlerine bağlı)
6. `UserHeader.svelte` — Avatar + ago + dropdown
7. `ActionBar.svelte` — beğeni + yorum + paylaş

### Aşama 4 — Library parçalanması
8. `_Quotes.svelte` — UserHeader + QuoteCard + ActionBar
9. `_Reviews.svelte`
10. `_Authors.svelte`
11. `_Books.svelte`
12. `library/+page.svelte` yeniden yaz

### Aşama 5 — Diğer sayfalar (isteğe bağlı)
- `feed/+page.svelte` → TabBar + PullToRefresh kullan
- `profile/[uid]/+page.svelte` → TabBar + EmptyState + PageTopBar

---

## CSS Değişkeni — Sticky Binme Kalıcı Çözümü

`+layout.svelte` `<style>` içine:
```css
:root { --header-h: 52px; }
```

Tüm `top: 52px` → `top: var(--header-h)` olur.  
Navbar yüksekliği değişirse tek yerden güncellenir.

---

## Beklenen Sonuç

| Sayfa | Şimdi | Sonra |
|---|---|---|
| `profile/[uid]/+page.svelte` | 1933 satır | ~500 satır |
| `library/+page.svelte` | 694 satır | ~80 satır |
| `feed/+page.svelte` | 555 satır | ~300 satır |
| `post/[id]/+page.svelte` | 792 satır | ~400 satır |

**Yeni bileşen değişikliğinde:** Beğeni butonu stili değişince → 1 dosya. Zaman formatı değişince → 1 dosya. Sekme animasyonu değişince → 1 dosya.
