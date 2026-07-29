<!--
  _Quotes.svelte — Kütüphane Alıntılar sekmesi.
  library/+page.svelte tarafından import edilir.
  Kendi verisini, loading ve error state'ini yönetir.
-->
<script lang="ts">
  import { onMount }   from 'svelte';
  import { currentUser } from '$lib/stores/auth';
  import QuoteCard     from '$lib/components/QuoteCard.svelte';
  import UserHeader    from '$lib/components/UserHeader.svelte';
  import ActionBar     from '$lib/components/ActionBar.svelte';
  import EmptyState    from '$lib/components/EmptyState.svelte';
  import Skeleton      from '$lib/components/Skeleton.svelte';
  import {
    fetchRecentQuotes, hydrateQuoteLikes, toggleLibraryItemLike,
    type QuotePage,
  } from '$lib/services/library.service';
  import type { BookQuote } from '$lib/models/library';

  let quotes:       BookQuote[] = $state([]);
  let loading       = $state(true);
  let offset        = $state(0);
  let hasMore       = $state(false);
  let loadingMore   = $state(false);

  // Pull-to-refresh'ten dışarıya refresh fonksiyonu
  export async function refresh() { await load(true); }

  onMount(() => load());

  async function load(reset = false) {
    loading = true;
    const p: QuotePage = await fetchRecentQuotes(0);
    const uid = $currentUser?.uid ?? null;
    quotes    = await hydrateQuoteLikes(p.quotes, uid);
    offset    = p.offset;
    hasMore   = p.hasMore;
    loading   = false;
  }

  async function loadMore() {
    if (!hasMore || loadingMore) return;
    loadingMore = true;
    const p   = await fetchRecentQuotes(offset);
    const uid = $currentUser?.uid ?? null;
    const hydrated = await hydrateQuoteLikes(p.quotes, uid);
    quotes    = [...quotes, ...hydrated];
    offset    = p.offset;
    hasMore   = p.hasMore;
    loadingMore = false;
  }

  async function handleLike(q: BookQuote) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const was = q.isLikedByMe ?? false;
    quotes = quotes.map(x => x.id === q.id
      ? { ...x, isLikedByMe: !was, likesCount: Math.max(0, x.likesCount + (was ? -1 : 1)) }
      : x
    );
    try {
      const res = await toggleLibraryItemLike(q.feedPostId, u.uid, u.displayName ?? '', u.photoURL ?? '');
      quotes = quotes.map(x => x.id === q.id
        ? { ...x, likesCount: res.count, isLikedByMe: res.liked } : x);
    } catch {
      quotes = quotes.map(x => x.id === q.id
        ? { ...x, isLikedByMe: was, likesCount: Math.max(0, x.likesCount + (was ? 1 : -1)) } : x);
    }
  }

  function share(q: BookQuote) {
    const url = window.location.origin + '/post/' + q.feedPostId;
    if (navigator.share) navigator.share({ url });
    else { navigator.clipboard.writeText(url); }
  }
</script>

{#if loading}
  <div class="skel-list">
    {#each {length: 4} as _}
      <div class="skel-card">
        <Skeleton width="36px" height="36px" radius="50%" />
        <div style="flex:1;display:flex;flex-direction:column;gap:6px">
          <Skeleton width="40%" height="13px" />
          <Skeleton width="80%" height="60px" />
        </div>
      </div>
    {/each}
  </div>

{:else if quotes.length === 0}
  <EmptyState icon="💬" message="Henüz alıntı yok." />

{:else}
  <div class="quote-list">
    {#each quotes as q (q.id)}
      <div class="quote-item">
        <UserHeader
          uid={q.uid}
          displayName={q.userDisplayName}
          photoURL={q.userPhotoURL}
          ts={q.ts ?? q.createdAt}
          menuItems={[
            { label: 'Gönderiye git', href: '/post/' + q.feedPostId },
            { label: 'Paylaş', onclick: () => share(q) },
          ]}
        />

        <div class="qc-wrap">
          <QuoteCard
            quoteText={q.text}
            bookName={q.bookTitle}
            authorName={q.authorName}
            coverImg={q.coverImg}
            bookId={q.bookId ?? ''}
            authorId={q.authorId ?? ''}
          />
        </div>

        <ActionBar
          liked={q.isLikedByMe ?? false}
          likeCount={q.likesCount}
          commentHref="/post/{q.feedPostId}"
          onLike={() => handleLike(q)}
          onShare={() => share(q)}
        />
      </div>
    {/each}

    {#if hasMore}
      <button class="load-more" onclick={loadMore} disabled={loadingMore}>
        {loadingMore ? 'Yükleniyor…' : 'Daha fazla göster'}
      </button>
    {/if}
  </div>
{/if}

<style>
.skel-list  { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.skel-card  { display: flex; gap: 10px; padding: 14px; background: var(--card); border-radius: 14px; }

.quote-list { display: flex; flex-direction: column; }
.quote-item {
  background: var(--card);
  border-bottom: 1px solid var(--divider);
}

.qc-wrap { padding: 8px 12px 0; }

.load-more {
  display: block; width: 100%; padding: 14px;
  background: var(--surface-var); border: none; border-radius: 12px;
  font-size: 14px; font-weight: 600; color: var(--primary);
  cursor: pointer; font-family: inherit; margin: 8px 12px;
  width: calc(100% - 24px);
}
.load-more:disabled { opacity: 0.5; cursor: default; }
</style>
