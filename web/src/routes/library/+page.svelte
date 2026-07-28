<script lang="ts">
  import { onMount }      from 'svelte';
  import { goto }         from '$app/navigation';
  import { page }         from '$app/stores';
  import { currentUser }  from '$lib/stores/auth';
  import QuoteCard        from '$lib/components/QuoteCard.svelte';
  import Avatar           from '$lib/components/Avatar.svelte';
  import InfiniteScroll   from '$lib/components/InfiniteScroll.svelte';
  import {
    fetchLibraryQuotes, fetchReviews,
    fetchAuthors, fetchBooks,
    type LibraryQuotePage,
  } from '$lib/services/library.service';
  import type { Author, LibraryBook, BookReview } from '$lib/models/library';

  // ── Sekmeler (Android: Alıntılar | İncelemeler | Yazarlar | Kitaplar) ───
  const TABS = ['Alıntılar', 'İncelemeler', 'Yazarlar', 'Kitaplar'] as const;
  let activeTab = $state(0);

  // ── Veri ─────────────────────────────────────────────────────────────────
  let quotes:  any[]        = $state([]);
  let reviews: BookReview[] = $state([]);
  let authors: Author[]     = $state([]);
  let books:   LibraryBook[] = $state([]);

  let loading     = $state(true);
  let quotesLastDoc = $state<any>(null);
  let quotesHasMore = $state(false);
  let quotesLoadingMore = $state(false);

  // ── Init ─────────────────────────────────────────────────────────────────
  onMount(async () => {
    const t = $page.url.searchParams.get('tab');
    if (t) activeTab = parseInt(t) || 0;

    loading = true;
    await Promise.all([loadQuotes(), loadReviews(), loadAuthors(), loadBooks()]);
    loading = false;
  });

  async function loadQuotes() {
    const p: LibraryQuotePage = await fetchLibraryQuotes();
    quotes        = p.posts;
    quotesLastDoc = p.lastDoc;
    quotesHasMore = p.hasMore;
  }

  async function loadMoreQuotes() {
    if (!quotesHasMore || quotesLoadingMore) return;
    quotesLoadingMore = true;
    const p = await fetchLibraryQuotes(quotesLastDoc);
    quotes        = [...quotes, ...p.posts];
    quotesLastDoc = p.lastDoc;
    quotesHasMore = p.hasMore;
    quotesLoadingMore = false;
  }

  async function loadReviews() {
    reviews = await fetchReviews();
  }

  async function loadAuthors() {
    authors = await fetchAuthors();
  }

  async function loadBooks() {
    books = await fetchBooks();
  }

  // Yardımcı
  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }
</script>

<svelte:head><title>Kütüphane — Heftreng</title></svelte:head>

<div class="page">

  <!-- Başlık -->
  <div class="top-bar">
    <h1 class="page-title">Kütüphane</h1>
    <!-- Android'deki FAB: sekmeye göre değişir -->
    {#if activeTab === 0}
      <a href="/compose?type=quote" class="fab">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
        Alıntı Ekle
      </a>
    {/if}
  </div>

  <!-- TabRow (Android SecondaryIndicator stili) -->
  <div class="tab-row">
    {#each TABS as tab, i}
      <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>
        {tab}
      </button>
    {/each}
  </div>

  {#if loading}
    <div class="center"><div class="spinner"></div></div>

  <!-- ── 0: Alıntılar ─────────────────────────────────────────── -->
  {:else if activeTab === 0}
    {#if quotes.length === 0}
      <div class="empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" width="52" height="52"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
        <p>Henüz alıntı yok</p>
        <a href="/compose?type=quote" class="empty-cta">İlk alıntıyı ekle →</a>
      </div>
    {:else}
      <div class="feed-list">
        {#each quotes as p (p.id)}
          <!-- Android: ConnectedPostCard ile aynı; alıntı gönderisi -->
          <article class="post-row" onclick={() => goto(`/post/${p.id}`)} role="button" tabindex="0">
            <div class="post-head">
              <Avatar src={p.photoURL} name={p.displayName ?? p.name} size={34} />
              <a href="/profile/{p.uid}" class="post-name" onclick={e => e.stopPropagation()}>
                {p.displayName ?? p.name}
              </a>
            </div>
            <QuoteCard
              quoteText={p.quoteText}
              bookName={p.bookName ?? ''}
              authorName={p.authorName ?? ''}
              coverImg={p.coverImg ?? ''}
            />
          </article>
        {/each}

        <InfiniteScroll
          hasMore={quotesHasMore}
          loading={quotesLoadingMore}
          onLoadMore={loadMoreQuotes}
        />
      </div>
    {/if}

  <!-- ── 1: İncelemeler ───────────────────────────────────────── -->
  {:else if activeTab === 1}
    {#if reviews.length === 0}
      <div class="empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" width="52" height="52"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
        <p>Henüz inceleme yok</p>
      </div>
    {:else}
      <div class="feed-list">
        {#each reviews as r (r.id)}
          <div class="review-card">
            <div class="review-head">
              <Avatar src={r.userPhotoURL} name={r.userDisplayName} size={36} />
              <div class="review-user">
                <span class="review-uname">{r.userDisplayName}</span>
                <span class="review-stars">{stars(r.rating)}</span>
              </div>
              {#if r.bookTitle}
                <a href="/library/book/{r.bookId}" class="review-book" onclick={e => e.stopPropagation()}>
                  {r.bookTitle}
                </a>
              {/if}
            </div>
            <p class="review-text">{r.text}</p>
            {#if r.authorName}
              <span class="review-author">{r.authorName}</span>
            {/if}
          </div>
        {/each}
      </div>
    {/if}

  <!-- ── 2: Yazarlar (LibraryAuthorRow) ───────────────────────── -->
  {:else if activeTab === 2}
    {#if authors.length === 0}
      <div class="empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" width="52" height="52"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
        <p>Henüz yazar yok</p>
      </div>
    {:else}
      <div class="authors-list">
        {#each authors as a (a.id)}
          <a href="/library/author/{a.id}" class="author-row">
            <Avatar src={a.photoURL} name={a.name} size={52} />
            <div class="author-info">
              <span class="author-name">{a.name}</span>
              {#if a.nationality}<span class="author-nat">{a.nationality}</span>{/if}
              <div class="author-stats">
                {#if a.bookCount > 0}
                  <span class="stat">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                    {a.bookCount}
                  </span>
                {/if}
                {#if a.quoteCount > 0}
                  <span class="stat">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
                    {a.quoteCount}
                  </span>
                {/if}
              </div>
            </div>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" class="chevron"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      </div>
    {/if}

  <!-- ── 3: Kitaplar (LazyVerticalGrid 2 sütun) ───────────────── -->
  {:else if activeTab === 3}
    {#if books.length === 0}
      <div class="empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" width="52" height="52"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        <p>Henüz kitap yok</p>
      </div>
    {:else}
      <div class="books-grid">
        {#each books as b (b.id)}
          <a href="/library/book/{b.id}" class="book-card">
            {#if b.coverImg}
              <img src={b.coverImg} alt={b.title} class="book-cover"/>
            {:else}
              <div class="book-cover-ph">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" width="32" height="32"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              </div>
            {/if}
            <div class="book-meta">
              <span class="book-title">{b.title}</span>
              <span class="book-author">{b.authorName}</span>
              {#if b.avgRating > 0}
                <span class="book-rating">{'★'.repeat(Math.round(b.avgRating))} {b.avgRating.toFixed(1)}</span>
              {/if}
            </div>
          </a>
        {/each}
      </div>
    {/if}
  {/if}

</div>

<style>
.page { max-width: 700px; margin: 0 auto; padding-bottom: 80px; }

.top-bar { display: flex; align-items: center; justify-content: space-between; padding: 16px 16px 8px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--on-bg); margin: 0; }
.fab {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 24px; font-size: 14px; font-weight: 600;
  background: var(--primary); color: #fff; text-decoration: none;
}
.fab:hover { opacity: .85; }

/* TabRow */
.tab-row {
  display: flex; border-bottom: 1px solid var(--divider);
  position: sticky; top: 56px; background: var(--surface); z-index: 9;
}
.tab {
  flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 500;
  color: var(--muted); background: none; border: none; cursor: pointer;
  border-bottom: 2px solid transparent; transition: all .15s; font-family: inherit;
}
.tab.active { color: var(--primary); font-weight: 600; border-bottom-color: var(--primary); }

/* Loading / empty */
.center { display: flex; justify-content: center; padding: 60px; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 60px 20px; color: var(--muted); text-align: center; }
.empty p { font-size: 15px; }
.empty-cta { color: var(--primary); font-size: 14px; text-decoration: none; }

/* Feed list */
.feed-list { display: flex; flex-direction: column; }
.post-row {
  padding: 14px 16px; border-bottom: 1px solid var(--divider);
  cursor: pointer; display: flex; flex-direction: column; gap: 10px;
  transition: background .1s;
}
.post-row:hover { background: var(--surface-var); }
.post-head { display: flex; align-items: center; gap: 8px; }
.post-name { font-size: 14px; font-weight: 600; color: var(--on-bg); text-decoration: none; }
.post-name:hover { text-decoration: underline; }

/* İnceleme kartı */
.review-card { padding: 14px 16px; border-bottom: 1px solid var(--divider); }
.review-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.review-user { display: flex; flex-direction: column; gap: 1px; }
.review-uname { font-size: 13px; font-weight: 600; color: var(--on-bg); }
.review-stars { font-size: 12px; color: #F59E0B; }
.review-book { font-size: 11px; color: var(--primary); margin-left: auto; text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 120px; }
.review-text { font-size: 14px; color: var(--on-bg); line-height: 1.6; margin: 0 0 4px; }
.review-author { font-size: 11px; color: var(--muted); }

/* Yazarlar listesi */
.authors-list { display: flex; flex-direction: column; gap: 10px; padding: 12px 16px; }
.author-row {
  display: flex; align-items: center; gap: 12px; padding: 12px;
  background: var(--card); border-radius: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.07);
  text-decoration: none; transition: box-shadow .15s;
}
.author-row:hover { box-shadow: 0 3px 10px rgba(0,0,0,.12); }
.author-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.author-name { font-size: 15px; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.author-nat { font-size: 12px; color: var(--muted); }
.author-stats { display: flex; gap: 12px; margin-top: 3px; }
.stat { display: flex; align-items: center; gap: 3px; font-size: 11px; color: var(--muted); }
.chevron { color: var(--muted); flex-shrink: 0; }

/* Kitaplar grid (LazyVerticalGrid 2 sütun) */
.books-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; padding: 12px 16px; }
.book-card {
  display: flex; flex-direction: column; border-radius: 14px;
  background: var(--card); box-shadow: 0 1px 4px rgba(0,0,0,.08);
  text-decoration: none; overflow: hidden; transition: box-shadow .15s;
}
.book-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.12); }
.book-cover { width: 100%; aspect-ratio: 2/3; object-fit: cover; display: block; }
.book-cover-ph {
  width: 100%; aspect-ratio: 2/3;
  background: color-mix(in srgb, var(--primary) 8%, var(--surface-var));
  display: flex; align-items: center; justify-content: center; color: var(--muted);
}
.book-meta { padding: 10px; display: flex; flex-direction: column; gap: 3px; }
.book-title { font-size: 13px; font-weight: 600; color: var(--on-bg); line-height: 1.3; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.book-author { font-size: 11px; color: var(--primary); }
.book-rating { font-size: 11px; color: #F59E0B; }

/* Spinner */
.spinner { width: 32px; height: 32px; border: 3px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
