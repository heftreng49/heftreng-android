<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { page }        from '$app/stores';
  import { currentUser } from '$lib/stores/auth';
  import QuoteCard       from '$lib/components/QuoteCard.svelte';
  import Avatar          from '$lib/components/Avatar.svelte';
  import Skeleton        from '$lib/components/Skeleton.svelte';
  import {
    fetchRecentQuotes, fetchRecentReviews, fetchAuthors, fetchBooks,
    toggleLibraryItemLike, hydrateQuoteLikes,
    type QuotePage,
  } from '$lib/services/library.service';
  import type { Author, LibraryBook, BookReview, BookQuote } from '$lib/models/library';

  const TABS = ['Alıntılar', 'İncelemeler', 'Yazarlar', 'Kitaplar'] as const;
  let activeTab = $state(0);

  let quotes:   BookQuote[]   = $state([]);
  let reviews:  BookReview[]  = $state([]);
  let authors:  Author[]      = $state([]);
  let books:    LibraryBook[] = $state([]);
  let loading        = $state(true);
  let loadingQuotes  = $state(true);
  let loadingReviews = $state(true);
  let loadingAuthors = $state(true);
  let loadingBooks   = $state(true);

  let quoteOffset       = $state(0);
  let quotesHasMore     = $state(false);
  let quotesLoadingMore = $state(false);

  // Pull-to-refresh
  let refreshing  = $state(false);
  let touchStartY = 0;
  let pullDist    = $state(0);
  const PULL_THRESHOLD = 72;

  onMount(async () => {
    const t = $page.url.searchParams.get('tab');
    if (t) activeTab = parseInt(t) || 0;
    await loadAll();
  });

  async function loadAll() {
    loading = true;
    loadingQuotes = true; loadingReviews = true; loadingAuthors = true; loadingBooks = true;
    await Promise.all([loadQuotes(), loadReviews(), loadAuthors(), loadBooks()]);
    loading = false;
  }

  async function loadQuotes() {
    loadingQuotes = true;
    const p: QuotePage = await fetchRecentQuotes(0);
    const uid = $currentUser?.uid ?? null;
    quotes        = await hydrateQuoteLikes(p.quotes, uid);
    quoteOffset   = p.offset;
    quotesHasMore = p.hasMore;
    loadingQuotes = false;
  }

  async function loadMoreQuotes() {
    if (!quotesHasMore || quotesLoadingMore) return;
    quotesLoadingMore = true;
    const p = await fetchRecentQuotes(quoteOffset);
    const uid = $currentUser?.uid ?? null;
    const hydrated = await hydrateQuoteLikes(p.quotes, uid);
    quotes        = [...quotes, ...hydrated];
    quoteOffset   = p.offset;
    quotesHasMore = p.hasMore;
    quotesLoadingMore = false;
  }

  async function loadReviews() {
    loadingReviews = true;
    reviews = await fetchRecentReviews();
    loadingReviews = false;
  }

  async function loadAuthors() {
    loadingAuthors = true;
    authors = await fetchAuthors();
    loadingAuthors = false;
  }

  async function loadBooks() {
    loadingBooks = true;
    books = await fetchBooks();
    loadingBooks = false;
  }

  async function handleQuoteLike(quote: BookQuote) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const was = quote.isLikedByMe ?? false;
    quotes = quotes.map(q => q.id === quote.id
      ? { ...q, likesCount: Math.max(0, q.likesCount + (was ? -1 : 1)), isLikedByMe: !was }
      : q
    );
    try {
      const res = await toggleLibraryItemLike(quote.feedPostId, u.uid, u.displayName ?? '', u.photoURL ?? '');
      quotes = quotes.map(q => q.id === quote.id
        ? { ...q, likesCount: res.count, isLikedByMe: res.liked } : q);
    } catch {
      quotes = quotes.map(q => q.id === quote.id
        ? { ...q, likesCount: Math.max(0, q.likesCount + (was ? 1 : -1)), isLikedByMe: was } : q);
    }
  }

  async function handleReviewLike(review: BookReview) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const was = review.isLikedByMe ?? false;
    reviews = reviews.map(r => r.id === review.id
      ? { ...r, likesCount: Math.max(0, r.likesCount + (was ? -1 : 1)), isLikedByMe: !was }
      : r
    );
    try {
      const res = await toggleLibraryItemLike(review.id, u.uid, u.displayName ?? '', u.photoURL ?? '');
      reviews = reviews.map(r => r.id === review.id
        ? { ...r, likesCount: res.count, isLikedByMe: res.liked } : r);
    } catch {
      reviews = reviews.map(r => r.id === review.id
        ? { ...r, likesCount: Math.max(0, r.likesCount + (was ? 1 : -1)), isLikedByMe: was } : r);
    }
  }

  function onTouchStart(e: TouchEvent) { touchStartY = e.touches[0].clientY; }
  function onTouchMove(e: TouchEvent) {
    if (refreshing || loading) return;
    if (document.documentElement.scrollTop > 0) return;
    const dy = e.touches[0].clientY - touchStartY;
    if (dy > 0) pullDist = Math.min(dy * 0.5, PULL_THRESHOLD + 20);
  }
  async function onTouchEnd() {
    if (pullDist >= PULL_THRESHOLD) {
      refreshing = true; pullDist = 0;
      await loadAll();
      refreshing = false;
    } else { pullDist = 0; }
  }

  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }

  function ago(ts: any): string {
    const ms = ts?.seconds ? ts.seconds * 1000 : ts ? new Date(ts).getTime() : 0;
    const diff = Date.now() - ms;
    const m = Math.floor(diff/60000), h = Math.floor(diff/3600000), d = Math.floor(diff/86400000);
    if (m < 1) return 'şimdi';
    if (m < 60) return `${m}dk`;
    if (h < 24) return `${h}sa`;
    if (d < 7)  return `${d}g`;
    return `${Math.floor(d/30)}ay`;
  }

  let quoteMenuId = $state<string | null>(null);
  function toggleQuoteMenu(e: Event, id: string) {
    e.stopPropagation();
    quoteMenuId = quoteMenuId === id ? null : id;
  }
  function shareQuote(q: any) {
    quoteMenuId = null;
    const url = window.location.origin + '/post/' + q.feedPostId;
    if (navigator.share) navigator.share({ title: q.userDisplayName, url });
    else { navigator.clipboard.writeText(url); alert('Bağlantı kopyalandı!'); }
  }
  function closeMenus() { quoteMenuId = null; }
</script>

<svelte:head><title>Kütüphane — Heftreng</title></svelte:head>
<svelte:window onclick={closeMenus} />

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<div class="page"
  role="main"
  ontouchstart={onTouchStart}
  ontouchmove={onTouchMove}
  ontouchend={onTouchEnd}
>
  <!-- Pull-to-refresh -->
  {#if pullDist > 10 || refreshing}
    <div class="ptr-indicator" style="height:{refreshing ? 48 : pullDist}px; opacity:{refreshing ? 1 : pullDist/PULL_THRESHOLD}">
      <div class="ptr-spinner" class:spinning={refreshing}></div>
    </div>
  {/if}

  <header class="lib-header">
    <h1 class="lib-title">Kütüphane</h1>
  </header>

  <!-- Sekme çubuğu — sticky, global header (52px) altına yapışır -->
  <div class="tabs">
    {#each TABS as tab, i}
      <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>{tab}</button>
    {/each}
  </div>

  <!--
    TAB-BODY: tabs'ın hemen altında normal document flow'da başlar.
    Sticky tabs scroll ederken üstüne çıkar ama bu div sabit yerinde kalır —
    içerik asla tabs'ın altına gizlenmez, üstüne de binmez.
  -->
  <div class="tab-body">

    <!-- ── Alıntılar ──────────────────────────────────────────────────────── -->
    {#if activeTab === 0}
      {#if loadingQuotes}
        <div class="skeleton-list">
          {#each {length: 5} as _}
            <div class="skel-card">
              <Skeleton width="64px" height="90px" radius="8px" />
              <div style="flex:1;display:flex;flex-direction:column;gap:6px">
                <Skeleton width="60%" height="14px" />
                <Skeleton width="40%" height="12px" />
                <Skeleton width="80%" height="12px" />
              </div>
            </div>
          {/each}
        </div>
      {:else if quotes.length === 0}
        <div class="empty-state">
          <span class="empty-icon">💬</span>
          <p>Henüz alıntı yok.</p>
        </div>
      {:else}
        <div class="quote-list">
          {#each quotes as q (q.id)}
            <div class="quote-item">
              <div class="quote-user-header">
                <a href="/profile/{q.uid}" class="q-av">
                  {#if q.userPhotoURL}
                    <img src={q.userPhotoURL} alt={q.userDisplayName} />
                  {:else}
                    <span>{(q.userDisplayName || '?')[0].toUpperCase()}</span>
                  {/if}
                </a>
                <div class="q-meta">
                  <a href="/profile/{q.uid}" class="q-name">{q.userDisplayName}</a>
                  {#if q.createdAt}
                    <span class="q-time">{ago(q.createdAt)}</span>
                  {/if}
                </div>
                <div class="q-menu-wrap" onclick={(e) => e.stopPropagation()}>
                  <button class="q-menu-btn" onclick={(e) => toggleQuoteMenu(e, q.id)} aria-label="Seçenekler">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                      <circle cx="12" cy="5" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="12" cy="19" r="1.8"/>
                    </svg>
                  </button>
                  {#if quoteMenuId === q.id}
                    <div class="q-dropdown">
                      <a href="/post/{q.feedPostId}" class="q-dropdown-item">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        Gönderiye git
                      </a>
                      <button class="q-dropdown-item" onclick={() => shareQuote(q)}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                        Bağlantıyı Kopyala
                      </button>
                    </div>
                  {/if}
                </div>
              </div>

              <QuoteCard
                quoteText={q.text}
                bookName={q.bookTitle}
                authorName={q.authorName}
                coverImg={q.coverImg}
                bookId={q.bookId ?? ''}
                authorId={q.authorId ?? ''}
              />

              <div class="quote-actions">
                <button class="act-like" class:liked={q.isLikedByMe} onclick={() => handleQuoteLike(q)} aria-label="Beğen">
                  {#if q.isLikedByMe}
                    <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {:else}
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {/if}
                  {#if q.likesCount > 0}<span>{q.likesCount}</span>{/if}
                </button>
                <a href="/post/{q.feedPostId}" class="act-comment" aria-label="Yorumlar">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                </a>
                <div class="act-spacer"></div>
                <button class="act-share" onclick={() => shareQuote(q)} aria-label="Paylaş">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                </button>
              </div>
            </div>
          {/each}

          {#if quotesHasMore}
            <button class="load-more" onclick={loadMoreQuotes} disabled={quotesLoadingMore}>
              {quotesLoadingMore ? 'Yükleniyor…' : 'Daha fazla göster'}
            </button>
          {/if}
        </div>
      {/if}

    <!-- ── İncelemeler ─────────────────────────────────────────────────────── -->
    {:else if activeTab === 1}
      {#if loadingReviews}
        <div class="skeleton-list">
          {#each {length: 4} as _}
            <div class="skel-card">
              <Skeleton width="44px" height="64px" radius="5px" />
              <div style="flex:1;display:flex;flex-direction:column;gap:6px">
                <Skeleton width="70%" height="14px" />
                <Skeleton width="50%" height="12px" />
                <Skeleton width="90%" height="12px" />
              </div>
            </div>
          {/each}
        </div>
      {:else if reviews.length === 0}
        <div class="empty-state">
          <span class="empty-icon">⭐</span>
          <p>Henüz inceleme yok.</p>
        </div>
      {:else}
        <div class="review-list">
          {#each reviews as rv (rv.id)}
            <div class="review-card">
              <a href="/library/book/{rv.bookId}" class="review-book-row">
                {#if rv.bookCoverImg}
                  <img src={rv.bookCoverImg} alt={rv.bookTitle} class="review-cover" />
                {:else}
                  <div class="review-cover review-cover-ph">📖</div>
                {/if}
                <div class="review-book-info">
                  <span class="review-book-title">{rv.bookTitle}</span>
                  <div class="review-stars">
                    {#each {length: 5} as _, i}
                      <svg viewBox="0 0 24 24" width="14" height="14"
                        fill={i < Math.round(rv.rating) ? '#F59E0B' : 'none'}
                        stroke="#F59E0B" stroke-width="1.5">
                        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                      </svg>
                    {/each}
                    <span class="review-rating-num">{rv.rating.toFixed(1)}</span>
                  </div>
                </div>
              </a>
              {#if rv.text}
                <p class="review-text">{rv.text}</p>
              {/if}
              <div class="review-footer">
                <a href="/profile/{rv.uid}" class="review-user">
                  <div class="mini-av">
                    {#if rv.userPhotoURL}
                      <img src={rv.userPhotoURL} alt={rv.userDisplayName} />
                    {:else}
                      <span>{(rv.userDisplayName || '?')[0].toUpperCase()}</span>
                    {/if}
                  </div>
                  <span>{rv.userDisplayName}</span>
                </a>
                <button class="act-like sm" class:liked={rv.isLikedByMe} onclick={() => handleReviewLike(rv)} aria-label="Beğen">
                  {#if rv.isLikedByMe}
                    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {:else}
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {/if}
                  {#if (rv.likesCount ?? 0) > 0}<span>{rv.likesCount}</span>{/if}
                </button>
              </div>
            </div>
          {/each}
        </div>
      {/if}

    <!-- ── Yazarlar ────────────────────────────────────────────────────────── -->
    {:else if activeTab === 2}
      {#if loadingAuthors}
        <div class="skeleton-list">
          {#each {length: 6} as _}
            <div class="skel-card">
              <Skeleton width="52px" height="52px" radius="50%" />
              <div style="flex:1;display:flex;flex-direction:column;gap:6px">
                <Skeleton width="55%" height="15px" />
                <Skeleton width="35%" height="12px" />
                <Skeleton width="70%" height="11px" />
              </div>
            </div>
          {/each}
        </div>
      {:else if authors.length === 0}
        <div class="empty-state">
          <span class="empty-icon">✍️</span>
          <p>Henüz yazar yok.</p>
        </div>
      {:else}
        <div class="author-list">
          {#each authors as a (a.id)}
            <a href="/library/author/{a.id}" class="author-card">
              <div class="author-av">
                {#if a.photoURL}
                  <img src={a.photoURL} alt={a.name} />
                {:else}
                  <span>{a.name[0]?.toUpperCase()}</span>
                {/if}
              </div>
              <div class="author-info">
                <span class="author-name">{a.name}</span>
                {#if a.nationality}
                  <span class="author-nat">{a.nationality}</span>
                {/if}
                <div class="author-chips">
                  {#if (a.bookCount ?? 0) > 0}
                    <span class="stat-chip">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                      {a.bookCount} kitap
                    </span>
                  {/if}
                  {#if (a.quoteCount ?? 0) > 0}
                    <span class="stat-chip">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
                      {a.quoteCount} alıntı
                    </span>
                  {/if}
                  {#if (a.followerCount ?? 0) > 0}
                    <span class="stat-chip">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
                      {a.followerCount} takipçi
                    </span>
                  {/if}
                </div>
              </div>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18" style="color:var(--muted);flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
            </a>
          {/each}
        </div>
      {/if}

    <!-- ── Kitaplar ────────────────────────────────────────────────────────── -->
    {:else if activeTab === 3}
      {#if loadingBooks}
        <div class="book-grid">
          {#each {length: 6} as _}
            <div style="display:flex;flex-direction:column;gap:7px">
              <Skeleton width="100%" height="180px" radius="10px" />
              <Skeleton width="80%" height="13px" />
              <Skeleton width="50%" height="12px" />
            </div>
          {/each}
        </div>
      {:else if books.length === 0}
        <div class="empty-state">
          <span class="empty-icon">📚</span>
          <p>Henüz kitap yok.</p>
        </div>
      {:else}
        <div class="book-grid">
          {#each books as b (b.id)}
            <a href="/library/book/{b.id}" class="book-card">
              {#if b.coverImg}
                <img src={b.coverImg} alt={b.title} class="book-cover" />
              {:else}
                <div class="book-cover book-cover-ph">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                </div>
              {/if}
              <div class="book-info">
                <span class="book-title">{b.title}</span>
                <span class="book-author">{b.authorName}</span>
                {#if (b.avgRating ?? 0) > 0}
                  <span class="book-rating">
                    <svg viewBox="0 0 24 24" fill="#F59E0B" width="11" height="11"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                    {b.avgRating.toFixed(1)}
                  </span>
                {/if}
              </div>
            </a>
          {/each}
        </div>
      {/if}
    {/if}

  </div><!-- /tab-body -->
</div><!-- /page -->

<style>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding-bottom: 80px;
  background: var(--bg);
  min-height: 100vh;
}

/* Pull-to-refresh */
.ptr-indicator {
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; transition: height 0.2s, opacity 0.2s;
}
.ptr-spinner {
  width: 22px; height: 22px;
  border: 2.5px solid color-mix(in srgb, var(--primary) 30%, transparent);
  border-top-color: var(--primary); border-radius: 50%;
}
.ptr-spinner.spinning { animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* Header */
.lib-header { padding: 16px 16px 4px; }
.lib-title  { font-size: 22px; font-weight: 800; color: var(--primary); margin: 0; }

/*
  TABS — sticky, global-header (height: 52px) altına yapışır.
  top: 52px sabit değer: layout.svelte'deki .global-header { height: 52px } ile eşleşir.
  z-index: 9 → global header (z:100) altında, içerik üstünde.
*/
.tabs {
  position: sticky;
  top: 52px;
  z-index: 9;
  display: flex;
  background: var(--bg);
  border-bottom: 1px solid var(--divider);
}
.tab {
  flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 500;
  color: var(--muted); background: none; border: none; cursor: pointer;
  border-bottom: 2.5px solid transparent; margin-bottom: -1px;
  transition: color 0.2s, border-color 0.2s; font-family: inherit;
}
.tab.active { color: var(--on-bg); font-weight: 700; border-bottom-color: var(--primary); }

/*
  TAB-BODY — normal document flow'da tabs'ın hemen altında başlar.
  "position: sticky" olan tabs scroll edildiğinde tab-body'nin üstüne çıkar
  ama tab-body kendisi yerinde kalır. Hiçbir binme olmaz.
*/
.tab-body { display: block; }

/* Skeleton */
.skeleton-list { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.skel-card { display: flex; gap: 12px; padding: 14px; background: var(--card); border-radius: 14px; }

/* Empty */
.empty-state { display: flex; flex-direction: column; align-items: center; padding: 60px 20px; gap: 10px; color: var(--muted); }
.empty-icon { font-size: 44px; }
.empty-state p { font-size: 14px; }

/* ── Alıntılar ──────────────────────────────────────────────────────────── */
.quote-list { padding: 10px 12px; display: flex; flex-direction: column; gap: 10px; }
.quote-item {
  background: var(--card);
  border-radius: 16px;
  overflow: hidden;
  border: 0.7px solid var(--divider);
}
.quote-user-header {
  display: flex; align-items: center; gap: 9px;
  padding: 11px 12px 8px;
}
.q-av {
  width: 36px; height: 36px; border-radius: 50%;
  background: var(--surface-var); overflow: hidden; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; color: var(--on-bg); text-decoration: none;
}
.q-av img { width: 100%; height: 100%; object-fit: cover; }
.q-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.q-name { font-size: 13px; font-weight: 700; color: var(--on-bg); text-decoration: none; }
.q-name:hover { text-decoration: underline; }
.q-time { font-size: 11px; color: var(--muted); }

.q-menu-wrap { position: relative; margin-left: auto; }
.q-menu-btn {
  width: 30px; height: 30px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: var(--muted); background: none; border: none; cursor: pointer;
  transition: background 0.15s;
}
.q-menu-btn:hover { background: var(--surface-var); }
.q-dropdown {
  position: absolute; right: 0; top: calc(100% + 4px);
  background: var(--surface); border: 1px solid var(--divider);
  border-radius: 12px; min-width: 180px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14); overflow: hidden; z-index: 200;
}
.q-dropdown-item {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 14px; font-size: 13px; color: var(--on-surface);
  background: none; border: none; cursor: pointer; width: 100%;
  text-align: left; text-decoration: none; font-family: inherit;
  transition: background 0.1s;
}
.q-dropdown-item:hover { background: var(--surface-var); }

.quote-actions {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 8px 8px;
  border-top: 1px solid var(--divider);
}
.act-like {
  display: flex; align-items: center; gap: 5px;
  background: none; border: none; cursor: pointer; color: var(--muted);
  padding: 6px 10px; border-radius: 20px; font-size: 13px; font-family: inherit;
  transition: background 0.15s;
}
.act-like:hover { background: var(--surface-var); }
.act-like.liked { color: #FF3A5C; }
.act-like.sm    { padding: 4px 8px; font-size: 12px; }
.act-comment {
  display: flex; align-items: center; gap: 5px; color: var(--muted);
  padding: 6px 10px; border-radius: 20px; text-decoration: none;
  transition: background 0.15s;
}
.act-comment:hover { background: var(--surface-var); }
.act-spacer { flex: 1; }
.act-share {
  display: flex; align-items: center; gap: 5px; color: var(--muted);
  padding: 6px 10px; border-radius: 20px; background: none; border: none;
  cursor: pointer; transition: background 0.15s;
}
.act-share:hover { background: var(--surface-var); }

/* ── İncelemeler ────────────────────────────────────────────────────────── */
.review-list { padding: 10px 12px; display: flex; flex-direction: column; gap: 10px; }
.review-card {
  background: var(--card); border-radius: 16px;
  border: 0.7px solid var(--divider); padding: 14px;
}
.review-book-row { display: flex; gap: 12px; align-items: center; margin-bottom: 10px; text-decoration: none; }
.review-cover { width: 44px; height: 64px; border-radius: 5px; object-fit: cover; flex-shrink: 0; }
.review-cover-ph { background: var(--surface-var); display: flex; align-items: center; justify-content: center; font-size: 22px; }
.review-book-info { display: flex; flex-direction: column; gap: 5px; }
.review-book-title { font-size: 14px; font-weight: 700; color: var(--on-bg); }
.review-stars { display: flex; align-items: center; gap: 2px; }
.review-rating-num { font-size: 12px; font-weight: 700; color: #F59E0B; margin-left: 4px; }
.review-text { font-size: 14px; color: var(--on-surface); line-height: 1.6; margin-bottom: 12px; }
.review-footer { display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--divider); padding-top: 10px; }
.review-user { display: flex; align-items: center; gap: 7px; text-decoration: none; color: var(--muted); font-size: 13px; font-weight: 500; }
.mini-av {
  width: 26px; height: 26px; border-radius: 50%; background: var(--surface-var);
  overflow: hidden; display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: var(--on-bg);
}
.mini-av img { width: 100%; height: 100%; object-fit: cover; }

/* ── Yazarlar ───────────────────────────────────────────────────────────── */
.author-list { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; }
.author-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--card); border-radius: 14px; padding: 12px 14px;
  text-decoration: none; border: 0.7px solid var(--divider);
  transition: border-color 0.15s;
}
.author-card:hover { border-color: color-mix(in srgb, var(--primary) 30%, var(--divider)); }
.author-av {
  width: 52px; height: 52px; border-radius: 50%; background: var(--surface-var);
  overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; color: var(--on-bg);
  border: 2px solid color-mix(in srgb, var(--primary) 20%, transparent);
}
.author-av img { width: 100%; height: 100%; object-fit: cover; }
.author-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.author-name { font-size: 15px; font-weight: 700; color: var(--on-bg); }
.author-nat  { font-size: 12px; color: var(--muted); }
.author-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 2px; }
.stat-chip { display: flex; align-items: center; gap: 3px; font-size: 11px; color: var(--muted); }

/* ── Kitaplar ───────────────────────────────────────────────────────────── */
.book-grid {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 12px; padding: 12px;
}
.book-card { display: flex; flex-direction: column; gap: 7px; text-decoration: none; }
.book-cover {
  width: 100%; aspect-ratio: 2/3; object-fit: cover;
  border-radius: 10px; display: block;
  box-shadow: 0 3px 10px rgba(0,0,0,0.15);
}
.book-cover-ph {
  background: var(--surface-var);
  display: flex; align-items: center; justify-content: center; color: var(--muted);
}
.book-info { display: flex; flex-direction: column; gap: 2px; padding: 0 2px; }
.book-title  { font-size: 13px; font-weight: 700; color: var(--on-bg); line-height: 1.3; }
.book-author { font-size: 12px; color: var(--muted); }
.book-rating { display: flex; align-items: center; gap: 3px; font-size: 12px; color: #F59E0B; font-weight: 600; }

.load-more {
  display: block; width: 100%; padding: 14px; margin: 4px 0;
  background: var(--surface-var); border: none; border-radius: 12px;
  font-size: 14px; font-weight: 600; color: var(--primary);
  cursor: pointer; font-family: inherit;
}
.load-more:disabled { opacity: .5; cursor: default; }
</style>
