<script lang="ts">
  import { onMount }     from 'svelte';
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

  let quotes:   BookQuote[]  = $state([]);
  let reviews:  BookReview[] = $state([]);
  let authors:  Author[]     = $state([]);
  let books:    LibraryBook[] = $state([]);
  let loading   = $state(true);

  let quoteOffset       = $state(0);
  let quotesHasMore     = $state(false);
  let quotesLoadingMore = $state(false);

  onMount(async () => {
    const t = $page.url.searchParams.get('tab');
    if (t) activeTab = parseInt(t) || 0;

    loading = true;
    await Promise.all([loadQuotes(), loadReviews(), loadAuthors(), loadBooks()]);
    loading = false;
  });

  async function loadQuotes() {
    const p: QuotePage = await fetchRecentQuotes(0);
    const uid = $currentUser?.uid ?? null;
    quotes        = await hydrateQuoteLikes(p.quotes, uid);
    quoteOffset   = p.offset;
    quotesHasMore = p.hasMore;
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
    reviews = await fetchRecentReviews();
  }

  async function loadAuthors() {
    authors = await fetchAuthors();
  }

  async function loadBooks() {
    books = await fetchBooks();
  }

  async function handleQuoteLike(quote: BookQuote) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const res = await toggleLibraryItemLike(quote.feedPostId, u.uid, u.displayName ?? '', u.photoURL ?? '');
    quotes = quotes.map(q => q.id === quote.id
      ? { ...q, likesCount: res.count, isLikedByMe: res.liked }
      : q
    );
  }

  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }
</script>

<svelte:head><title>Kütüphane — Heftreng</title></svelte:head>

<div class="page">
  <header class="lib-header">
    <h1 class="lib-title">Kütüphane</h1>
  </header>

  <!-- Sekmeler — Android TabRow ile aynı stil -->
  <div class="tabs">
    {#each TABS as tab, i}
      <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>
        {tab}
      </button>
    {/each}
  </div>

  {#if loading}
    <div class="skeleton-list">
      {#each {length: 6} as _}
        <div class="skel-card">
          <Skeleton width="64px" height="90px" radius="8px" />
          <div style="flex:1;display:flex;flex-direction:column;gap:6px">
            <Skeleton width="60%" height="14px" />
            <Skeleton width="40%" height="12px" />
            <Skeleton width="90%" height="12px" />
          </div>
        </div>
      {/each}
    </div>

  {:else}

    <!-- ── Alıntılar — Android LibraryQuotesTab / ConnectedPostCard ile birebir ── -->
    {#if activeTab === 0}
      {#if quotes.length === 0}
        <div class="empty-state">
          <span class="empty-icon">❝</span>
          <p class="empty-text">Henüz alıntı yok.</p>
        </div>
      {:else}
        <div class="quote-list">
          {#each quotes as q (q.id)}
            <!-- Android'deki ConnectedPostCard wrapper kartı -->
            <div class="quote-post-card">
              <!-- Üst: kullanıcı satırı (Android PostCard header gibi) -->
              <div class="quote-post-header">
                <a href="/profile/{q.uid}" class="quote-post-user">
                  <img
                    src={q.userPhotoURL || '/placeholder.png'}
                    alt={q.userDisplayName}
                    class="post-avatar"
                  />
                  <div class="post-user-info">
                    <span class="post-username">{q.userDisplayName}</span>
                    <span class="post-tag">alıntı paylaştı</span>
                  </div>
                </a>
              </div>

              <!-- QuoteCard: mevcut bileşen (Android QuoteCompose karşılığı) -->
              <div class="quote-card-wrap">
                <QuoteCard
                  quoteText={q.text}
                  bookName={q.bookTitle}
                  authorName={q.authorName}
                  coverImg={q.coverImg}
                />
              </div>

              <!-- Alt: eylemler (Android BookCardActions / like row) -->
              <div class="quote-post-actions">
                {#if q.bookId}
                  <a href="/library/book/{q.bookId}" class="action-book-link">
                    <span class="action-icon">📖</span>
                    <span class="action-label">{q.bookTitle}</span>
                  </a>
                {/if}
                <div class="action-spacer"></div>
                <!-- Beğeni butonu — Android BookQuoteCard likeButton ile aynı -->
                <button
                  class="like-btn"
                  class:liked={q.isLikedByMe}
                  onclick={() => handleQuoteLike(q)}
                  aria-label="Beğen"
                >
                  <span class="like-icon">{q.isLikedByMe ? '♥' : '♡'}</span>
                  {#if q.likesCount > 0}
                    <span class="like-count">{q.likesCount}</span>
                  {/if}
                </button>
              </div>
            </div>
          {/each}

          <!-- Daha Fazla Göster — Android OutlinedButton ile aynı -->
          {#if quotesHasMore}
            <div class="load-more-wrap">
              {#if quotesLoadingMore}
                <div class="spinner"></div>
              {:else}
                <button class="load-more-btn" onclick={loadMoreQuotes}>
                  Daha Fazla Göster
                </button>
              {/if}
            </div>
          {/if}
        </div>
      {/if}

    <!-- ── İncelemeler ───────────────────────────────────────────────────── -->
    {:else if activeTab === 1}
      {#if reviews.length === 0}
        <div class="empty-state">
          <span class="empty-icon">⭐</span>
          <p class="empty-text">Henüz inceleme yok.</p>
        </div>
      {:else}
        <div class="review-list">
          {#each reviews as rv (rv.id)}
            <a href="/library/book/{rv.bookId}" class="review-card">
              <div class="review-header">
                <span class="stars">{stars(rv.rating)}</span>
                <span class="rating-num">{rv.rating.toFixed(1)}</span>
                <span class="book-title-sm">{rv.bookTitle}</span>
              </div>
              <p class="review-text">{rv.text}</p>
              <div class="review-user">
                <img src={rv.userPhotoURL || '/placeholder.png'} alt={rv.userDisplayName} class="mini-avatar" />
                <span>{rv.userDisplayName}</span>
              </div>
            </a>
          {/each}
        </div>
      {/if}

    <!-- ── Yazarlar ──────────────────────────────────────────────────────── -->
    {:else if activeTab === 2}
      {#if authors.length === 0}
        <div class="empty-state">
          <span class="empty-icon">👤</span>
          <p class="empty-text">Henüz yazar yok.</p>
        </div>
      {:else}
        <div class="author-list">
          {#each authors as a (a.id)}
            <!-- Android LibraryAuthorRow → Card + Row birebir karşılığı -->
            <a href="/library/author/{a.id}" class="author-card">
              <Avatar src={a.photoURL} name={a.name} size={52} />
              <div class="author-info">
                <span class="author-name">{a.name}</span>
                {#if a.nationality}
                  <span class="author-nat">{a.nationality}</span>
                {/if}
                <div class="author-stats">
                  {#if a.bookCount > 0}
                    <span class="stat-chip">📚 {a.bookCount}</span>
                  {/if}
                  {#if a.quoteCount > 0}
                    <span class="stat-chip">❝ {a.quoteCount}</span>
                  {/if}
                </div>
              </div>
              <span class="chevron">›</span>
            </a>
          {/each}
        </div>
      {/if}

    <!-- ── Kitaplar ──────────────────────────────────────────────────────── -->
    {:else if activeTab === 3}
      {#if books.length === 0}
        <div class="empty-state">
          <span class="empty-icon">📖</span>
          <p class="empty-text">Henüz kitap yok.</p>
        </div>
      {:else}
        <div class="book-grid">
          {#each books as b (b.id)}
            <a href="/library/book/{b.id}" class="book-card">
              {#if b.coverImg}
                <img src={b.coverImg} alt={b.title} class="book-cover" />
              {:else}
                <div class="book-cover-placeholder">{b.title[0]}</div>
              {/if}
              <div class="book-info">
                <span class="book-title">{b.title}</span>
                <span class="book-author">{b.authorName}</span>
                {#if b.avgRating > 0}
                  <span class="book-rating">★ {b.avgRating.toFixed(1)}</span>
                {/if}
              </div>
            </a>
          {/each}
        </div>
      {/if}
    {/if}

  {/if}
</div>

<style>
  .page { max-width: 720px; margin: 0 auto; padding-bottom: 80px; }

  .lib-header { padding: 16px 16px 0; }
  .lib-title  { font-family: 'Playfair Display', serif; font-size: 26px; font-weight: 700; color: var(--primary); }

  /* Sekmeler — Android TabRow ile aynı */
  .tabs { display: flex; border-bottom: 2px solid var(--divider); position: sticky; top: 0; background: var(--bg); z-index: 10; }
  .tab  {
    flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 600; color: var(--muted);
    border-bottom: 2px solid transparent; margin-bottom: -2px;
    transition: color .15s, border-color .15s;
  }
  .tab.active { color: var(--primary); border-bottom-color: var(--primary); }

  /* Boş durum — Android EmptyState ile aynı */
  .empty-state {
    display: flex; flex-direction: column; align-items: center;
    padding: 48px 16px; gap: 12px;
  }
  .empty-icon { font-size: 40px; opacity: .4; }
  .empty-text { font-size: 14px; color: var(--muted); text-align: center; }

  /* Skeleton */
  .skeleton-list { padding: 12px; display: flex; flex-direction: column; gap: 12px; }
  .skel-card { display: flex; gap: 12px; padding: 12px; background: var(--card); border-radius: 12px; }

  /* ── Alıntılar — Android ConnectedPostCard / LibraryQuotesTab ── */
  .quote-list { padding: 0; display: flex; flex-direction: column; gap: 0; }

  /* Kart wrapper — Android ConnectedPostCard → Card(containerColor=HeftSurface) */
  .quote-post-card {
    background: var(--card);
    border-bottom: 1px solid var(--divider);
  }

  /* Üst kullanıcı satırı — Android PostCard header */
  .quote-post-header {
    display: flex;
    align-items: center;
    padding: 12px 14px 8px;
  }
  .quote-post-user {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .post-avatar {
    width: 36px; height: 36px;
    border-radius: 50%;
    object-fit: cover;
    background: color-mix(in srgb, var(--primary) 15%, transparent);
  }
  .post-user-info {
    display: flex; flex-direction: column; gap: 1px;
  }
  .post-username {
    font-size: 14px; font-weight: 600; color: var(--on-bg);
  }
  .post-tag {
    font-size: 11px; color: var(--muted);
  }

  /* QuoteCard sarıcı — Android içindeki QuoteCard padding */
  .quote-card-wrap {
    padding: 0 12px 10px;
  }

  /* Alt eylem satırı — Android BookCardActions */
  .quote-post-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px 12px;
    border-top: 1px solid var(--divider);
  }
  .action-book-link {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: var(--primary);
    max-width: 55%;
    overflow: hidden;
  }
  .action-icon { font-size: 13px; flex-shrink: 0; }
  .action-label {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    text-decoration: underline;
    text-underline-offset: 2px;
  }
  .action-spacer { flex: 1; }

  /* Beğeni butonu — Android BookQuoteCard likeButton */
  .like-btn {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 13px;
    color: var(--muted);
    padding: 6px 10px;
    border-radius: 20px;
    background: var(--surface-var);
    transition: color .15s, background .15s;
  }
  .like-btn.liked { color: var(--error); background: color-mix(in srgb, var(--error) 12%, transparent); }
  .like-icon { font-size: 15px; line-height: 1; }
  .like-count { font-size: 12px; font-weight: 600; }

  /* Daha Fazla — Android OutlinedButton */
  .load-more-wrap {
    display: flex; justify-content: center;
    padding: 16px;
  }
  .load-more-btn {
    padding: 10px 28px;
    border: 1.5px solid var(--primary);
    border-radius: 20px;
    font-size: 14px; font-weight: 600;
    color: var(--primary);
    background: transparent;
    transition: background .15s;
  }
  .load-more-btn:hover { background: color-mix(in srgb, var(--primary) 8%, transparent); }

  .spinner {
    width: 28px; height: 28px;
    border: 2.5px solid var(--divider);
    border-top-color: var(--primary);
    border-radius: 50%;
    animation: spin .7s linear infinite;
  }
  @keyframes spin { to { transform: rotate(360deg); } }

  /* ── İncelemeler ── */
  .review-list { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
  .review-card { display: block; background: var(--card); border-radius: 14px; padding: 14px; }
  .review-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
  .stars { color: var(--primary); font-size: 14px; }
  .rating-num { font-weight: 700; font-size: 14px; color: var(--on-bg); }
  .book-title-sm { font-size: 13px; color: var(--muted); margin-left: auto; }
  .review-text { font-size: 14px; line-height: 1.5; color: var(--on-surface); margin-bottom: 10px; }
  .review-user { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted); }
  .mini-avatar { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }

  /* ── Yazarlar — Android LibraryAuthorRow / Card + Row ── */
  .author-list {
    display: flex; flex-direction: column;
    gap: 10px;
    padding: 12px 16px;
  }
  .author-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    background: var(--card);
    border-radius: 14px;
    box-shadow: 0 1px 2px rgba(0,0,0,.06);
    transition: background .12s;
  }
  .author-card:hover { background: var(--surface-var); }
  .author-info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
  .author-name { font-weight: 600; font-size: 15px; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .author-nat  { font-size: 12px; color: var(--muted); }
  .author-stats { display: flex; gap: 10px; margin-top: 2px; }
  .stat-chip { font-size: 11px; color: var(--muted); display: flex; align-items: center; gap: 3px; }
  .chevron { font-size: 22px; color: var(--muted); font-weight: 300; flex-shrink: 0; }

  /* ── Kitaplar — Android LazyVerticalGrid(2 sütun) ── */
  .book-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 14px;
    padding: 14px;
  }
  .book-card  { display: flex; flex-direction: column; gap: 8px; }
  .book-cover { width: 100%; aspect-ratio: 2/3; object-fit: cover; border-radius: 8px; }
  .book-cover-placeholder {
    width: 100%; aspect-ratio: 2/3; background: var(--surface-var);
    border-radius: 8px; display: flex; align-items: center;
    justify-content: center; font-size: 36px; color: var(--muted);
  }
  .book-info   { display: flex; flex-direction: column; gap: 2px; }
  .book-title  { font-weight: 700; font-size: 13px; color: var(--on-bg); line-height: 1.3; }
  .book-author { font-size: 12px; color: var(--muted); }
  .book-rating { font-size: 12px; color: var(--primary); }
</style>
