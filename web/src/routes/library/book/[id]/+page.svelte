<script lang="ts">
  import { onMount } from 'svelte';
  import { page }    from '$app/stores';
  import { currentUser } from '$lib/stores/auth';
  import QuoteCard   from '$lib/components/QuoteCard.svelte';
  import Avatar      from '$lib/components/Avatar.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import {
    fetchBookById, fetchQuotesByBook, fetchReviewsByBook,
    addBookReview, toggleLibraryItemLike, hydrateQuoteLikes,
  } from '$lib/services/library.service';
  import type { LibraryBook, BookQuote, BookReview } from '$lib/models/library';

  const id = $derived($page.params.id);
  const TABS = ['Alıntılar', 'İncelemeler'] as const;
  let activeTab = $state(0);

  let book:    LibraryBook | null = $state(null);
  let quotes:  BookQuote[]        = $state([]);
  let reviews: BookReview[]       = $state([]);
  let loading  = $state(true);

  // İnceleme formu
  let reviewText   = $state('');
  let reviewRating = $state(5);
  let submitting   = $state(false);

  onMount(async () => {
    loading = true;
    try {
      book = await fetchBookById(id);
      const uid = $currentUser?.uid ?? null;
      const [rawQuotes, rawReviews] = await Promise.all([
        fetchQuotesByBook(id, book?.title),
        fetchReviewsByBook(id),
      ]);
      quotes  = await hydrateQuoteLikes(rawQuotes, uid);
      reviews = rawReviews;
    } catch(e) { console.error('book detail load error:', e); }
    finally { loading = false; }
  });

  async function handleQuoteLike(q: BookQuote) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const res = await toggleLibraryItemLike(q.feedPostId, u.uid, u.displayName ?? '', u.photoURL ?? '');
    quotes = quotes.map(x => x.id === q.id ? { ...x, likesCount: res.count, isLikedByMe: res.liked } : x);
  }

  async function submitReview() {
    const u = $currentUser;
    if (!u || !book || !reviewText.trim()) return;
    submitting = true;
    try {
      const rv = await addBookReview({
        bookId:          id,
        authorId:        book.authorId,
        bookTitle:       book.title,
        authorName:      book.authorName,
        text:            reviewText.trim(),
        rating:          reviewRating,
        uid:             u.uid,
        userDisplayName: u.displayName ?? '',
        userPhotoURL:    u.photoURL    ?? '',
      });
      if (rv) {
        reviews = [rv, ...reviews];
        reviewText = '';
        reviewRating = 5;
      }
    } finally { submitting = false; }
  }

  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }
</script>

<svelte:head><title>{book?.title ?? 'Kitap'} — Heftreng</title></svelte:head>

<div class="page">
  {#if loading}
    <div class="skel-header">
      <Skeleton width="100px" height="140px" radius="10px" />
      <div style="flex:1;display:flex;flex-direction:column;gap:8px">
        <Skeleton width="70%" height="18px" />
        <Skeleton width="50%" height="14px" />
        <Skeleton width="40%" height="12px" />
      </div>
    </div>
  {:else if !book}
    <p class="empty">Kitap bulunamadı.</p>
  {:else}
    <!-- Kitap başlık bloğu -->
    <div class="book-header">
      {#if book.coverImg}
        <img src={book.coverImg} alt={book.title} class="book-cover" />
      {:else}
        <div class="cover-placeholder">{book.title[0]}</div>
      {/if}
      <div class="book-meta">
        <h1 class="book-title">{book.title}</h1>
        {#if book.authorName}
          <a href="/library/author/{book.authorId}" class="author-link">{book.authorName}</a>
        {/if}
        {#if book.genre}<span class="badge">{book.genre}</span>{/if}
        {#if book.avgRating > 0}
          <span class="rating">★ {book.avgRating.toFixed(1)} · {book.reviewCount} inceleme</span>
        {/if}
        {#if book.synopsis}
          <p class="synopsis">{book.synopsis}</p>
        {/if}
      </div>
    </div>

    <!-- Sekmeler -->
    <div class="tabs">
      {#each TABS as tab, i}
        <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>
          {tab}
          <span class="count">
            {i === 0 ? quotes.length : reviews.length}
          </span>
        </button>
      {/each}
    </div>

    <!-- Alıntılar -->
    {#if activeTab === 0}
      {#if quotes.length === 0}
        <p class="empty">Bu kitap için alıntı yok.</p>
      {:else}
        <div class="quote-list">
          {#each quotes as q (q.id)}
            <div class="quote-item">
              <QuoteCard quoteText={q.text} bookName={q.bookTitle} authorName={q.authorName} coverImg={q.coverImg || book?.coverImg || ''} />
              <div class="quote-meta">
                <a href="/profile/{q.uid}" class="quote-user">
                  <img src={q.userPhotoURL || '/placeholder.png'} alt={q.userDisplayName} class="mini-av" />
                  <span>{q.userDisplayName}</span>
                </a>
                <button class="like-btn" class:liked={q.isLikedByMe} onclick={() => handleQuoteLike(q)}>
                  ♥ {q.likesCount > 0 ? q.likesCount : ''}
                </button>
              </div>
            </div>
          {/each}
        </div>
      {/if}

    <!-- İncelemeler -->
    {:else}
      <!-- Yeni inceleme formu -->
      {#if $currentUser}
        <div class="review-form">
          <div class="star-picker">
            {#each [1,2,3,4,5] as s}
              <button class="star" class:filled={s <= reviewRating} onclick={() => reviewRating = s}>★</button>
            {/each}
          </div>
          <textarea bind:value={reviewText} placeholder="İncelemenizi yazın…" rows="3" class="review-input"></textarea>
          <button class="submit-btn" disabled={submitting || !reviewText.trim()} onclick={submitReview}>
            {submitting ? 'Gönderiliyor…' : 'İnceleme Ekle'}
          </button>
        </div>
      {/if}

      {#if reviews.length === 0}
        <p class="empty">Bu kitap için inceleme yok.</p>
      {:else}
        <div class="review-list">
          {#each reviews as rv (rv.id)}
            <div class="review-card">
              <div class="rv-header">
                <span class="stars">{stars(rv.rating)}</span>
                <a href="/profile/{rv.uid}" class="rv-user">
                  <img src={rv.userPhotoURL || '/placeholder.png'} alt={rv.userDisplayName} class="mini-av" />
                  {rv.userDisplayName}
                </a>
              </div>
              <p class="rv-text">{rv.text}</p>
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  {/if}
</div>

<style>
  .page { max-width: 720px; margin: 0 auto; padding-bottom: 80px; }
  .empty { text-align: center; padding: 40px 16px; color: var(--muted); }

  .skel-header { display: flex; gap: 14px; padding: 16px; }

  .book-header { display: flex; gap: 16px; padding: 16px; border-bottom: 1px solid var(--divider); }
  .book-cover  { width: 100px; height: 140px; object-fit: cover; border-radius: 10px; flex-shrink: 0; }
  .cover-placeholder { width: 100px; height: 140px; background: var(--surface-var); border-radius: 10px;
                        display: flex; align-items: center; justify-content: center; font-size: 40px; color: var(--muted); }
  .book-meta   { display: flex; flex-direction: column; gap: 5px; }
  .book-title  { font-family: 'Playfair Display', serif; font-size: 20px; font-weight: 700; color: var(--on-bg); }
  .author-link { font-size: 14px; color: var(--primary); }
  .badge       { font-size: 11px; background: var(--surface-var); color: var(--muted); padding: 2px 8px; border-radius: 10px; width: fit-content; }
  .rating      { font-size: 13px; color: var(--muted); }
  .synopsis    { font-size: 13px; line-height: 1.5; color: var(--on-surface); margin-top: 4px; }

  .tabs { display: flex; border-bottom: 2px solid var(--divider); position: sticky; top: 0; background: var(--bg); z-index: 5; }
  .tab  { flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 600; color: var(--muted);
          border-bottom: 2px solid transparent; margin-bottom: -2px; }
  .tab.active { color: var(--primary); border-bottom-color: var(--primary); }
  .count { font-size: 11px; margin-left: 4px; background: var(--surface-var); padding: 1px 5px; border-radius: 8px; }

  .quote-list  { padding: 12px; display: flex; flex-direction: column; gap: 12px; }
  .quote-item  { background: var(--card); border-radius: 14px; overflow: hidden; }
  .quote-meta  { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-top: 1px solid var(--divider); }
  .quote-user  { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--on-surface); }
  .like-btn    { margin-left: auto; font-size: 13px; color: var(--muted); padding: 4px 8px; border-radius: 12px; }
  .like-btn.liked { color: var(--error); }
  .mini-av     { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }

  .review-form  { padding: 14px; border-bottom: 1px solid var(--divider); display: flex; flex-direction: column; gap: 10px; }
  .star-picker  { display: flex; gap: 8px; }
  .star         { font-size: 28px; color: var(--surface-var); }
  .star.filled  { color: var(--primary); }
  .review-input { width: 100%; padding: 10px; border: 1px solid var(--divider); border-radius: 10px;
                  background: var(--surface); color: var(--on-bg); font-size: 14px; resize: vertical; }
  .submit-btn   { align-self: flex-end; padding: 8px 18px; background: var(--primary); color: #fff;
                  border-radius: 20px; font-size: 13px; font-weight: 600; }
  .submit-btn:disabled { opacity: .5; }

  .review-list  { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
  .review-card  { background: var(--card); border-radius: 14px; padding: 14px; }
  .rv-header    { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
  .stars        { color: var(--primary); }
  .rv-user      { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); margin-left: auto; }
  .rv-text      { font-size: 14px; line-height: 1.5; color: var(--on-surface); }
</style>
