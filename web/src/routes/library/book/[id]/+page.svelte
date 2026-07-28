<script lang="ts">
  import { onMount }     from 'svelte';
  import { page }        from '$app/stores';
  import { goto }        from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import Avatar          from '$lib/components/Avatar.svelte';
  import Modal           from '$lib/components/Modal.svelte';
  import QuoteCard       from '$lib/components/QuoteCard.svelte';
  import {
    fetchBookById, fetchBookQuotes, fetchBookReviews, addBookReview,
  } from '$lib/services/library.service';
  import type { LibraryBook, BookReview } from '$lib/models/library';

  const id = $derived($page.params.id);

  let book:    LibraryBook | null = $state(null);
  let quotes:  any[]              = $state([]);
  let reviews: BookReview[]       = $state([]);
  let loading                     = $state(true);

  // Android: 2 sekme — Alıntılar | İncelemeler
  const TABS = ['Alıntılar', 'İncelemeler'] as const;
  let activeTab = $state(0);

  // İnceleme ekleme (Android AddReviewDialog)
  let showReviewModal  = $state(false);
  let reviewText       = $state('');
  let reviewRating     = $state(4);
  let reviewSubmitting = $state(false);

  onMount(async () => {
    loading = true;
    book = await fetchBookById(id);
    await Promise.all([
      fetchBookQuotes(id, book?.title).then(d => quotes = d),
      fetchBookReviews(id).then(d => reviews = d),
    ]);
    loading = false;
  });

  async function submitReview() {
    if (!$currentUser || !reviewText.trim() || !book) return;
    reviewSubmitting = true;
    try {
      const newReview = await addBookReview({
        bookId:          id,
        authorId:        book.authorId ?? '',
        bookTitle:       book.title,
        authorName:      book.authorName ?? '',
        text:            reviewText.trim(),
        rating:          reviewRating,
        uid:             $currentUser.uid,
        userDisplayName: $currentUser.displayName ?? '',
        userPhotoURL:    $currentUser.photoURL    ?? '',
      });
      if (newReview) reviews = [newReview, ...reviews];
      reviewText = ''; reviewRating = 4; showReviewModal = false;
    } finally { reviewSubmitting = false; }
  }

  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }
</script>

<svelte:head><title>{book?.title ?? 'Kitap'} — Heftreng</title></svelte:head>

<div class="page">

  <!-- Üst bar -->
  <div class="topbar">
    <button class="back" onclick={() => history.back()}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="22" height="22"><polyline points="15 18 9 12 15 6"/></svg>
    </button>
    <span class="topbar-title">{book?.title ?? 'Kitap'}</span>
  </div>

  {#if loading}
    <div class="center"><div class="spinner"></div></div>
  {:else if book}

  <!-- Kitap başlık (Android LibraryBookDetailHeader) -->
  <div class="book-header">
    {#if book.coverImg}
      <img src={book.coverImg} alt={book.title} class="book-cover"/>
    {:else}
      <div class="book-cover-ph">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" width="44" height="44"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
      </div>
    {/if}
    <div class="book-info">
      <h1 class="book-title">{book.title}</h1>
      {#if book.authorId}
        <a href="/library/author/{book.authorId}" class="book-author">{book.authorName}</a>
      {:else}
        <span class="book-author">{book.authorName}</span>
      {/if}
      <div class="book-chips">
        {#if book.genre}<span class="chip">{book.genre}</span>{/if}
        {#if book.publishYear > 0}<span class="chip">{book.publishYear}</span>{/if}
        {#if book.pageCount > 0}<span class="chip">{book.pageCount} s.</span>{/if}
      </div>
      {#if book.avgRating > 0}
        <div class="rating-row">
          <span class="stars">{stars(book.avgRating)}</span>
          <span class="rating-val">{book.avgRating.toFixed(1)}</span>
        </div>
      {/if}
    </div>
  </div>

  {#if book.synopsis}
    <p class="synopsis">{book.synopsis}</p>
  {/if}

  <!-- Sekmeler -->
  <div class="tab-row">
    {#each TABS as t, i}
      <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>{t}</button>
    {/each}
  </div>

  <!-- ── Alıntılar ── -->
  {#if activeTab === 0}
    <div class="tab-actions">
      <a href="/compose?type=quote" class="action-btn">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
        Alıntı Ekle
      </a>
    </div>
    {#if quotes.length === 0}
      <div class="empty"><p>Henüz alıntı yok. İlk alıntıyı sen ekle!</p></div>
    {:else}
      <div class="quotes-list">
        {#each quotes as q (q.id)}
          <div class="quote-row" onclick={() => goto(`/post/${q.id}`)} role="button" tabindex="0">
            <div class="quote-user">
              <Avatar src={q.photoURL} name={q.displayName ?? q.name} size={24}/>
              <span class="quote-uname">{q.displayName ?? q.name}</span>
            </div>
            <QuoteCard
              quoteText={q.quoteText}
              bookName={q.bookName ?? ''}
              authorName={q.authorName ?? ''}
              coverImg={q.coverImg ?? ''}
            />
          </div>
        {/each}
      </div>
    {/if}

  <!-- ── İncelemeler ── -->
  {:else}
    <div class="tab-actions">
      {#if $currentUser}
        <button class="action-btn amber" onclick={() => showReviewModal = true}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          İnceleme Yaz
        </button>
      {/if}
    </div>
    {#if reviews.length === 0}
      <div class="empty"><p>Henüz inceleme yok. İlk incelemeyi sen yaz!</p></div>
    {:else}
      <div class="reviews-list">
        {#each reviews as r (r.id)}
          <div class="review-item">
            <div class="review-head">
              <Avatar src={r.userPhotoURL} name={r.userDisplayName} size={34}/>
              <div>
                <span class="rv-name">{r.userDisplayName}</span>
                <span class="rv-stars">{stars(r.rating)}</span>
              </div>
            </div>
            <p class="rv-text">{r.text}</p>
          </div>
        {/each}
      </div>
    {/if}
  {/if}

  {/if}
</div>

<!-- İnceleme Ekle Modal (Android AddReviewDialog) -->
<Modal bind:open={showReviewModal} title="İnceleme Yaz" maxWidth="480px" onclose={() => showReviewModal = false}>
  <div class="review-form">
    <!-- Yıldız seçici (Android RatingBar) -->
    <div class="star-picker">
      {#each [1,2,3,4,5] as s}
        <button class="star-btn" class:lit={s <= reviewRating} onclick={() => reviewRating = s}>★</button>
      {/each}
      <span class="star-label">{reviewRating}/5</span>
    </div>
    <textarea
      class="review-ta"
      placeholder="İncelemenizi yazın..."
      bind:value={reviewText}
      rows={5}
    ></textarea>
    <div class="form-actions">
      <button class="cancel-btn" onclick={() => showReviewModal = false}>İptal</button>
      <button
        class="submit-btn"
        onclick={submitReview}
        disabled={!reviewText.trim() || reviewSubmitting}
      >
        {reviewSubmitting ? 'Gönderiliyor...' : 'Gönder'}
      </button>
    </div>
  </div>
</Modal>

<style>
.page { max-width: 700px; margin: 0 auto; padding-bottom: 80px; }
.topbar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid var(--divider); position: sticky; top: 56px; background: var(--surface); z-index: 9; }
.back { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; background: none; border: none; cursor: pointer; color: var(--on-bg); border-radius: 50%; }
.back:hover { background: var(--surface-var); }
.topbar-title { font-size: 17px; font-weight: 700; color: var(--on-bg); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.center { display: flex; justify-content: center; padding: 60px; }

/* Kitap başlık */
.book-header { display: flex; gap: 16px; padding: 16px; align-items: flex-start; }
.book-cover { width: 100px; height: 150px; border-radius: 8px; object-fit: cover; flex-shrink: 0; box-shadow: 0 4px 12px rgba(0,0,0,.2); }
.book-cover-ph { width: 100px; height: 150px; border-radius: 8px; background: color-mix(in srgb, var(--primary) 8%, var(--surface-var)); display: flex; align-items: center; justify-content: center; color: var(--muted); flex-shrink: 0; }
.book-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; padding-top: 4px; }
.book-title { font-size: 18px; font-weight: 700; color: var(--on-bg); margin: 0; line-height: 1.3; }
.book-author { font-size: 14px; color: var(--primary); text-decoration: none; font-weight: 500; }
.book-author:hover { text-decoration: underline; }
.book-chips { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 4px; }
.chip { font-size: 11px; background: var(--surface-var); color: var(--muted); border-radius: 6px; padding: 2px 8px; }
.rating-row { display: flex; align-items: center; gap: 5px; margin-top: 4px; }
.stars { color: #F59E0B; font-size: 14px; }
.rating-val { font-size: 13px; font-weight: 600; color: var(--on-bg); }
.synopsis { font-size: 14px; color: var(--on-bg); line-height: 1.65; margin: 0; padding: 0 16px 16px; }

/* Tabs */
.tab-row { display: flex; border-bottom: 1px solid var(--divider); }
.tab { flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 500; color: var(--muted); background: none; border: none; cursor: pointer; border-bottom: 2px solid transparent; transition: all .15s; font-family: inherit; }
.tab.active { color: var(--primary); font-weight: 600; border-bottom-color: var(--primary); }

.tab-actions { display: flex; justify-content: flex-end; padding: 10px 16px 4px; }
.action-btn { display: flex; align-items: center; gap: 5px; padding: 7px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; background: var(--primary); color: #fff; border: none; cursor: pointer; text-decoration: none; font-family: inherit; transition: opacity .15s; }
.action-btn.amber { background: #F59E0B; color: #1a1a1a; }
.action-btn:hover { opacity: .85; }

.empty { padding: 40px 20px; text-align: center; color: var(--muted); font-size: 14px; }

/* Alıntılar */
.quotes-list { display: flex; flex-direction: column; }
.quote-row { padding: 12px 16px; border-bottom: 1px solid var(--divider); cursor: pointer; display: flex; flex-direction: column; gap: 8px; transition: background .1s; }
.quote-row:hover { background: var(--surface-var); }
.quote-user { display: flex; align-items: center; gap: 6px; }
.quote-uname { font-size: 12px; font-weight: 600; color: var(--on-bg); }

/* İncelemeler */
.reviews-list { display: flex; flex-direction: column; }
.review-item { padding: 14px 16px; border-bottom: 1px solid var(--divider); }
.review-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.rv-name { display: block; font-size: 13px; font-weight: 600; color: var(--on-bg); }
.rv-stars { font-size: 12px; color: #F59E0B; }
.rv-text { font-size: 14px; color: var(--on-bg); line-height: 1.6; margin: 0; }

/* Review form (Modal içi) */
.review-form { display: flex; flex-direction: column; gap: 14px; }
.star-picker { display: flex; align-items: center; gap: 8px; }
.star-btn { font-size: 30px; background: none; border: none; cursor: pointer; color: var(--divider); transition: color .1s; padding: 0; line-height: 1; }
.star-btn.lit { color: #F59E0B; }
.star-label { font-size: 13px; color: var(--muted); margin-left: 4px; }
.review-ta { width: 100%; background: var(--surface-var); border: 1px solid var(--divider); border-radius: 10px; padding: 10px 12px; font-size: 14px; color: var(--on-bg); outline: none; font-family: inherit; resize: vertical; line-height: 1.6; box-sizing: border-box; }
.review-ta:focus { border-color: var(--primary); }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; }
.cancel-btn { padding: 8px 16px; background: none; border: none; color: var(--muted); cursor: pointer; font-family: inherit; font-size: 14px; }
.submit-btn { padding: 8px 20px; background: #F59E0B; color: #1a1a1a; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; transition: opacity .15s; }
.submit-btn:disabled { opacity: .4; cursor: not-allowed; }

.spinner { width: 32px; height: 32px; border: 3px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
