<script lang="ts">
  import { onMount }     from 'svelte';
  import { page }        from '$app/stores';
  import { goto }        from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import Avatar          from '$lib/components/Avatar.svelte';
  import QuoteCard       from '$lib/components/QuoteCard.svelte';
  import {
    fetchAuthorById, fetchAuthorBooks, fetchAuthorReviews,
    fetchAuthorQuotesFromFeed,
    checkAuthorFollow, followAuthor, unfollowAuthor,
  } from '$lib/services/library.service';
  import type { Author, LibraryBook, BookReview } from '$lib/models/library';

  const id = $derived($page.params.id);

  let author:  Author | null    = $state(null);
  let books:   LibraryBook[]    = $state([]);
  let quotes:  any[]            = $state([]);
  let reviews: BookReview[]     = $state([]);
  let loading                   = $state(true);

  let isFollowing   = $state(false);
  let followLoading = $state(false);

  // Android: 3 sekme — Kitapları | Alıntılar | İncelemeler
  const TABS = ['Kitapları', 'Alıntılar', 'İncelemeler'] as const;
  let activeTab = $state(0);

  onMount(async () => {
    loading = true;
    author = await fetchAuthorById(id);
    await Promise.all([
      fetchAuthorBooks(id).then(d => books = d),
      fetchAuthorReviews(id).then(d => reviews = d),
      author ? fetchAuthorQuotesFromFeed(author.name).then(d => quotes = d) : Promise.resolve(),
      $currentUser ? checkAuthorFollow($currentUser.uid, id).then(v => isFollowing = v) : Promise.resolve(),
    ]);
    loading = false;
  });

  async function toggleFollow() {
    if (!$currentUser || followLoading || !author) return;
    followLoading = true;
    try {
      if (isFollowing) {
        await unfollowAuthor($currentUser.uid, id);
        isFollowing = false;
        author = { ...author, followerCount: (author.followerCount ?? 1) - 1 };
      } else {
        await followAuthor($currentUser.uid, id);
        isFollowing = true;
        author = { ...author, followerCount: (author.followerCount ?? 0) + 1 };
      }
    } finally { followLoading = false; }
  }

  function stars(r: number) {
    return '★'.repeat(Math.round(r)) + '☆'.repeat(5 - Math.round(r));
  }
</script>

<svelte:head><title>{author?.name ?? 'Yazar'} — Heftreng</title></svelte:head>

<div class="page">

  <!-- Üst bar (geri ok) -->
  <div class="topbar">
    <button class="back" onclick={() => history.back()}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="22" height="22"><polyline points="15 18 9 12 15 6"/></svg>
    </button>
    <span class="topbar-title">{author?.name ?? 'Yazar'}</span>
  </div>

  {#if loading}
    <div class="center"><div class="spinner"></div></div>
  {:else if author}

  <!-- Yazar başlık (Android AuthorHeaderSection) -->
  <div class="author-header">
    <Avatar src={author.photoURL} name={author.name} size={92} />
    <h1 class="author-name">{author.name}</h1>
    {#if author.nationality || author.birthYear > 0}
      <p class="author-meta">
        {[author.nationality, author.birthYear > 0 ? author.birthYear : null].filter(Boolean).join(' · ')}
      </p>
    {/if}
    {#if author.bio}
      <p class="author-bio">{author.bio}</p>
    {/if}

    <!-- İstatistikler (Android stat row) -->
    <div class="stats-row">
      <div class="stat"><span class="stat-val">{author.bookCount || books.length}</span><span class="stat-lbl">Kitap</span></div>
      <div class="stat"><span class="stat-val">{author.quoteCount || quotes.length}</span><span class="stat-lbl">Alıntı</span></div>
      <div class="stat"><span class="stat-val">{author.reviewCount || reviews.length}</span><span class="stat-lbl">İnceleme</span></div>
      <div class="stat"><span class="stat-val">{author.followerCount}</span><span class="stat-lbl">Takipçi</span></div>
    </div>

    <!-- Takip butonu -->
    {#if $currentUser}
      <button
        class="follow-btn"
        class:following={isFollowing}
        onclick={toggleFollow}
        disabled={followLoading}
      >
        {#if isFollowing}
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="22" y1="11" x2="16" y2="11"/></svg>
          Takip Ediliyor
        {:else}
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>
          Takip Et
        {/if}
      </button>
    {/if}
  </div>

  <!-- Sekmeler -->
  <div class="tab-row">
    {#each TABS as t, i}
      <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>{t}</button>
    {/each}
  </div>

  <!-- ── Kitapları ── -->
  {#if activeTab === 0}
    {#if books.length === 0}
      <div class="empty"><p>Henüz kitap eklenmemiş.</p></div>
    {:else}
      <div class="books-grid">
        {#each books as b (b.id)}
          <a href="/library/book/{b.id}" class="book-card">
            {#if b.coverImg}
              <img src={b.coverImg} alt={b.title} class="book-cover"/>
            {:else}
              <div class="book-cover-ph">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" width="28" height="28"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              </div>
            {/if}
            <div class="book-meta">
              <span class="book-title">{b.title}</span>
              {#if b.publishYear > 0}<span class="book-year">{b.publishYear}</span>{/if}
              {#if b.avgRating > 0}<span class="book-rating">{'★'.repeat(Math.round(b.avgRating))}</span>{/if}
            </div>
          </a>
        {/each}
      </div>
    {/if}

  <!-- ── Alıntılar ── -->
  {:else if activeTab === 1}
    {#if quotes.length === 0}
      <div class="empty"><p>Henüz alıntı yok.</p></div>
    {:else}
      <div class="quotes-list">
        {#each quotes as q (q.id)}
          <div class="quote-row" onclick={() => goto(`/post/${q.id}`)} role="button" tabindex="0">
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
    {#if reviews.length === 0}
      <div class="empty"><p>Henüz inceleme yok.</p></div>
    {:else}
      <div class="reviews-list">
        {#each reviews as r (r.id)}
          <div class="review-item">
            <div class="review-head">
              <Avatar src={r.userPhotoURL} name={r.userDisplayName} size={32} />
              <div>
                <span class="rv-name">{r.userDisplayName}</span>
                <span class="rv-stars">{stars(r.rating)}</span>
              </div>
              {#if r.bookTitle}
                <a href="/library/book/{r.bookId}" class="rv-book">{r.bookTitle}</a>
              {/if}
            </div>
            <p class="rv-text">{r.text}</p>
          </div>
        {/each}
      </div>
    {/if}
  {/if}

  {/if}
</div>

<style>
.page { max-width: 700px; margin: 0 auto; padding-bottom: 80px; }
.topbar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid var(--divider); position: sticky; top: 56px; background: var(--surface); z-index: 9; }
.back { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; background: none; border: none; cursor: pointer; color: var(--on-bg); border-radius: 50%; }
.back:hover { background: var(--surface-var); }
.topbar-title { font-size: 17px; font-weight: 700; color: var(--on-bg); }
.center { display: flex; justify-content: center; padding: 60px; }

/* Yazar başlık */
.author-header {
  display: flex; flex-direction: column; align-items: center;
  padding: 24px 20px 20px; gap: 6px;
  background: linear-gradient(to bottom, color-mix(in srgb, var(--primary) 10%, transparent), transparent);
}
.author-name { font-size: 22px; font-weight: 700; color: var(--on-bg); margin: 8px 0 0; }
.author-meta { font-size: 13px; color: var(--muted); margin: 0; }
.author-bio { font-size: 14px; color: var(--on-bg); line-height: 1.6; margin: 6px 0 0; text-align: center; max-width: 480px; }

.stats-row { display: flex; gap: 0; width: 100%; justify-content: space-evenly; margin-top: 12px; }
.stat { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.stat-val { font-size: 18px; font-weight: 700; color: var(--on-bg); }
.stat-lbl { font-size: 11px; color: var(--muted); }

.follow-btn {
  display: flex; align-items: center; gap: 6px; margin-top: 12px;
  padding: 10px 28px; border-radius: 24px; font-size: 14px; font-weight: 600;
  background: var(--primary); color: #fff; border: none; cursor: pointer; font-family: inherit;
  transition: all .15s;
}
.follow-btn.following { background: var(--surface-var); color: var(--on-bg); }
.follow-btn:disabled { opacity: .5; cursor: not-allowed; }

/* Tabs */
.tab-row { display: flex; border-bottom: 1px solid var(--divider); }
.tab { flex: 1; padding: 12px 4px; font-size: 13px; font-weight: 500; color: var(--muted); background: none; border: none; cursor: pointer; border-bottom: 2px solid transparent; transition: all .15s; font-family: inherit; }
.tab.active { color: var(--primary); font-weight: 600; border-bottom-color: var(--primary); }

.empty { padding: 48px 20px; text-align: center; color: var(--muted); font-size: 14px; }

/* Kitaplar grid */
.books-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; padding: 14px 16px; }
.book-card { display: flex; flex-direction: column; border-radius: 12px; background: var(--card); box-shadow: 0 1px 4px rgba(0,0,0,.08); overflow: hidden; text-decoration: none; transition: box-shadow .15s; }
.book-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.12); }
.book-cover { width: 100%; aspect-ratio: 2/3; object-fit: cover; }
.book-cover-ph { width: 100%; aspect-ratio: 2/3; background: color-mix(in srgb, var(--primary) 8%, var(--surface-var)); display: flex; align-items: center; justify-content: center; color: var(--muted); }
.book-meta { padding: 8px 10px; display: flex; flex-direction: column; gap: 2px; }
.book-title { font-size: 12px; font-weight: 600; color: var(--on-bg); line-height: 1.3; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.book-year { font-size: 11px; color: var(--muted); }
.book-rating { font-size: 11px; color: #F59E0B; }

/* Alıntılar */
.quotes-list { display: flex; flex-direction: column; gap: 0; }
.quote-row { padding: 12px 16px; border-bottom: 1px solid var(--divider); cursor: pointer; }
.quote-row:hover { background: var(--surface-var); }

/* İncelemeler */
.reviews-list { display: flex; flex-direction: column; }
.review-item { padding: 14px 16px; border-bottom: 1px solid var(--divider); }
.review-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.rv-name { display: block; font-size: 13px; font-weight: 600; color: var(--on-bg); }
.rv-stars { font-size: 12px; color: #F59E0B; }
.rv-book { font-size: 11px; color: var(--primary); margin-left: auto; text-decoration: none; }
.rv-text { font-size: 14px; color: var(--on-bg); line-height: 1.6; margin: 0; }

.spinner { width: 32px; height: 32px; border: 3px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
