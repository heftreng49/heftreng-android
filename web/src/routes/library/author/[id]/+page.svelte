<script lang="ts">
  import { onMount } from 'svelte';
  import { page }    from '$app/stores';
  import { currentUser } from '$lib/stores/auth';
  import QuoteCard   from '$lib/components/QuoteCard.svelte';
  import Avatar      from '$lib/components/Avatar.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import {
    fetchAuthorById, fetchAuthorBooks, fetchQuotesByAuthor, fetchReviewsByAuthor,
    checkAuthorFollow, followAuthor, unfollowAuthor,
    toggleLibraryItemLike, hydrateQuoteLikes,
  } from '$lib/services/library.service';
  import type { Author, LibraryBook, BookQuote, BookReview } from '$lib/models/library';

  const id = $derived($page.params.id);
  const TABS = ['Kitaplar', 'Alıntılar', 'İncelemeler'] as const;
  let activeTab  = $state(0);

  let author:    Author | null    = $state(null);
  let books:     LibraryBook[]    = $state([]);
  let quotes:    BookQuote[]      = $state([]);
  let reviews:   BookReview[]     = $state([]);
  let loading    = $state(true);
  let following  = $state(false);
  let followLoading = $state(false);

  onMount(async () => {
    loading = true;
    author  = await fetchAuthorById(id);
    if (!author) { loading = false; return; }

    const uid = $currentUser?.uid ?? null;
    const [rawBooks, rawQuotes, rawReviews] = await Promise.all([
      fetchAuthorBooks(id),
      fetchQuotesByAuthor(id, author.name),
      fetchReviewsByAuthor(id),
    ]);
    books   = rawBooks;
    quotes  = await hydrateQuoteLikes(rawQuotes, uid);
    reviews = rawReviews;

    if (uid) following = await checkAuthorFollow(uid, id);
    loading = false;
  });

  async function toggleFollow() {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    followLoading = true;
    if (following) {
      await unfollowAuthor(u.uid, id);
      following = false;
      if (author) author = { ...author, followerCount: Math.max(0, author.followerCount - 1) };
    } else {
      await followAuthor(u.uid, id);
      following = true;
      if (author) author = { ...author, followerCount: author.followerCount + 1 };
    }
    followLoading = false;
  }

  async function handleQuoteLike(q: BookQuote) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const res = await toggleLibraryItemLike(q.feedPostId, u.uid, u.displayName ?? '', u.photoURL ?? '');
    quotes = quotes.map(x => x.id === q.id ? { ...x, likesCount: res.count, isLikedByMe: res.liked } : x);
  }

  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }
</script>

<svelte:head><title>{author?.name ?? 'Yazar'} — Heftreng</title></svelte:head>

<div class="page">
  {#if loading}
    <div class="skel-header">
      <Skeleton width="80px" height="80px" radius="50%" />
      <div style="flex:1;display:flex;flex-direction:column;gap:8px">
        <Skeleton width="50%" height="18px" />
        <Skeleton width="35%" height="12px" />
      </div>
    </div>
  {:else if !author}
    <p class="empty">Yazar bulunamadı.</p>
  {:else}
    <!-- Yazar başlık bloğu -->
    <div class="author-header">
      <Avatar src={author.photoURL} name={author.name} size={76} />
      <div class="author-meta">
        <h1 class="author-name">{author.name}</h1>
        {#if author.nationality}
          <span class="author-nat">{author.nationality}{author.birthYear ? ` · ${author.birthYear}` : ''}</span>
        {/if}
        <div class="author-counts">
          <span>{author.bookCount} kitap</span>
          <span>·</span>
          <span>{author.quoteCount} alıntı</span>
          <span>·</span>
          <span>{author.followerCount} takipçi</span>
        </div>
        {#if author.bio}
          <p class="author-bio">{author.bio}</p>
        {/if}
        <button
          class="follow-btn" class:following
          onclick={toggleFollow}
          disabled={followLoading}
        >
          {following ? 'Takip Ediliyor' : 'Takip Et'}
        </button>
      </div>
    </div>

    <!-- Sekmeler -->
    <div class="tabs">
      {#each TABS as tab, i}
        <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>
          {tab}
          <span class="count">
            {i === 0 ? books.length : i === 1 ? quotes.length : reviews.length}
          </span>
        </button>
      {/each}
    </div>

    <!-- Kitaplar -->
    {#if activeTab === 0}
      {#if books.length === 0}
        <p class="empty">Bu yazara ait kitap yok.</p>
      {:else}
        <div class="book-list">
          {#each books as b (b.id)}
            <a href="/library/book/{b.id}" class="book-row">
              {#if b.coverImg}
                <img src={b.coverImg} alt={b.title} class="book-cover" />
              {:else}
                <div class="cover-ph">{b.title[0]}</div>
              {/if}
              <div class="book-info">
                <span class="book-title">{b.title}</span>
                {#if b.publishYear > 0}<span class="book-year">{b.publishYear}</span>{/if}
                {#if b.avgRating > 0}<span class="book-rating">★ {b.avgRating.toFixed(1)}</span>{/if}
                <span class="book-counts">{b.quoteCount} alıntı · {b.reviewCount} inceleme</span>
              </div>
            </a>
          {/each}
        </div>
      {/if}

    <!-- Alıntılar — artık Supabase book_quotes'tan -->
    {:else if activeTab === 1}
      {#if quotes.length === 0}
        <p class="empty">Bu yazara ait alıntı yok.</p>
      {:else}
        <div class="quote-list">
          {#each quotes as q (q.id)}
            <div class="quote-item">
              <QuoteCard quoteText={q.text} bookName={q.bookTitle} authorName={q.authorName} coverImg={q.coverImg} />
              <div class="quote-meta">
                <a href="/profile/{q.uid}" class="quote-user">
                  <img src={q.userPhotoURL || '/placeholder.png'} alt={q.userDisplayName} class="mini-av" />
                  <span>{q.userDisplayName}</span>
                </a>
                {#if q.bookId}
                  <a href="/library/book/{q.bookId}" class="book-link">{q.bookTitle}</a>
                {/if}
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
      {#if reviews.length === 0}
        <p class="empty">Bu yazara ait inceleme yok.</p>
      {:else}
        <div class="review-list">
          {#each reviews as rv (rv.id)}
            <a href="/library/book/{rv.bookId}" class="review-card">
              <div class="rv-header">
                <span class="stars">{stars(rv.rating)}</span>
                <span class="book-name">{rv.bookTitle}</span>
              </div>
              <p class="rv-text">{rv.text}</p>
              <div class="rv-user">
                <img src={rv.userPhotoURL || '/placeholder.png'} alt={rv.userDisplayName} class="mini-av" />
                <span>{rv.userDisplayName}</span>
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
  .empty { text-align: center; padding: 40px 16px; color: var(--muted); }
  .skel-header { display: flex; gap: 14px; padding: 16px; }

  .author-header { display: flex; gap: 16px; padding: 16px; border-bottom: 1px solid var(--divider); align-items: flex-start; }
  .author-meta   { display: flex; flex-direction: column; gap: 5px; flex: 1; }
  .author-name   { font-family: 'Playfair Display', serif; font-size: 22px; font-weight: 700; color: var(--on-bg); }
  .author-nat    { font-size: 13px; color: var(--muted); }
  .author-counts { display: flex; gap: 6px; font-size: 12px; color: var(--muted); flex-wrap: wrap; }
  .author-bio    { font-size: 13px; line-height: 1.5; color: var(--on-surface); }
  .follow-btn    { align-self: flex-start; padding: 7px 18px; border-radius: 20px; font-size: 13px;
                   font-weight: 700; border: 2px solid var(--primary); color: var(--primary); margin-top: 4px; }
  .follow-btn.following { background: var(--primary); color: #fff; }
  .follow-btn:disabled  { opacity: .6; }

  .tabs  { display: flex; border-bottom: 2px solid var(--divider); position: sticky; top: 0; background: var(--bg); z-index: 5; }
  .tab   { flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 600; color: var(--muted);
           border-bottom: 2px solid transparent; margin-bottom: -2px; }
  .tab.active { color: var(--primary); border-bottom-color: var(--primary); }
  .count { font-size: 11px; margin-left: 4px; background: var(--surface-var); padding: 1px 5px; border-radius: 8px; }

  .book-list  { display: flex; flex-direction: column; gap: 0; }
  .book-row   { display: flex; align-items: center; gap: 14px; padding: 14px 16px; border-bottom: 1px solid var(--divider); }
  .book-cover { width: 52px; height: 74px; object-fit: cover; border-radius: 6px; flex-shrink: 0; }
  .cover-ph   { width: 52px; height: 74px; background: var(--surface-var); border-radius: 6px;
                display: flex; align-items: center; justify-content: center; font-size: 22px; color: var(--muted); }
  .book-info  { display: flex; flex-direction: column; gap: 3px; }
  .book-title { font-weight: 700; font-size: 15px; color: var(--on-bg); }
  .book-year  { font-size: 12px; color: var(--muted); }
  .book-rating { font-size: 12px; color: var(--primary); }
  .book-counts { font-size: 12px; color: var(--muted); }

  .quote-list  { padding: 12px; display: flex; flex-direction: column; gap: 12px; }
  .quote-item  { background: var(--card); border-radius: 14px; overflow: hidden; }
  .quote-meta  { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-top: 1px solid var(--divider); }
  .quote-user  { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--on-surface); }
  .book-link   { font-size: 12px; color: var(--primary); margin-left: auto; }
  .like-btn    { font-size: 13px; color: var(--muted); margin-left: auto; padding: 4px 8px; border-radius: 12px; }
  .like-btn.liked { color: var(--error); }
  .mini-av     { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }

  .review-list  { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
  .review-card  { display: block; background: var(--card); border-radius: 14px; padding: 14px; }
  .rv-header    { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
  .stars        { color: var(--primary); }
  .book-name    { font-size: 13px; color: var(--muted); }
  .rv-text      { font-size: 14px; line-height: 1.5; color: var(--on-surface); margin-bottom: 8px; }
  .rv-user      { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted); }
</style>
