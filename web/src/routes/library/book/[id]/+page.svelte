<script lang="ts">
  import { onMount }     from 'svelte';
  import { page }        from '$app/stores';
  import { currentUser } from '$lib/stores/auth';
  import QuoteCard       from '$lib/components/QuoteCard.svelte';
  import Skeleton        from '$lib/components/Skeleton.svelte';
  import {
    fetchBookById, fetchQuotesByBook, fetchReviewsByBook,
    addBookReview, toggleLibraryItemLike, hydrateQuoteLikes,
    toggleBookLike,
  } from '$lib/services/library.service';
  import type { LibraryBook, BookQuote, BookReview } from '$lib/models/library';

  const id = $derived($page.params.id);
  const TABS = ['Alıntılar', 'İncelemeler'] as const;
  let activeTab = $state(0);
  let book:    LibraryBook | null = $state(null);
  let quotes:  BookQuote[]        = $state([]);
  let reviews: BookReview[]       = $state([]);
  let loading  = $state(true);
  let reviewText   = $state('');
  let reviewRating = $state(5);
  let submitting   = $state(false);
  let refreshing   = $state(false);
  let touchStartY  = 0;
  let pullDist     = $state(0);
  const PULL_THRESHOLD = 72;

  onMount(() => loadAll());

  async function loadAll() {
    loading = true;
    try {
      book = await fetchBookById(id);
      if (!book) return;
      const uid = $currentUser?.uid ?? null;
      const [rawQuotes, rawReviews] = await Promise.all([
        fetchQuotesByBook(id, book.title),
        fetchReviewsByBook(id),
      ]);
      quotes  = await hydrateQuoteLikes(rawQuotes, uid);
      reviews = rawReviews;
    } catch(e) { console.error(e); }
    finally { loading = false; }
  }

  async function handleBookLike() {
    const u = $currentUser;
    if (!u || !book) { window.location.href = '/login'; return; }
    const was = book.isLikedByMe ?? false;
    book = { ...book, isLikedByMe: !was, likes: Math.max(0, (book.likes ?? 0) + (was ? -1 : 1)) };
    try { await toggleBookLike(id, u.uid, u.displayName ?? '', u.photoURL ?? '', was); }
    catch { book = { ...book, isLikedByMe: was, likes: Math.max(0, (book.likes ?? 0) + (was ? 1 : -1)) }; }
  }

  async function handleQuoteLike(q: BookQuote) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const was = q.isLikedByMe ?? false;
    quotes = quotes.map(x => x.id === q.id ? { ...x, isLikedByMe: !was, likesCount: Math.max(0, x.likesCount + (was ? -1 : 1)) } : x);
    try {
      const res = await toggleLibraryItemLike(q.feedPostId, u.uid, u.displayName ?? '', u.photoURL ?? '');
      quotes = quotes.map(x => x.id === q.id ? { ...x, likesCount: res.count, isLikedByMe: res.liked } : x);
    } catch { quotes = quotes.map(x => x.id === q.id ? { ...x, isLikedByMe: was, likesCount: Math.max(0, x.likesCount + (was ? 1 : -1)) } : x); }
  }

  async function handleReviewLike(rv: BookReview) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const was = rv.isLikedByMe ?? false;
    reviews = reviews.map(r => r.id === rv.id ? { ...r, isLikedByMe: !was, likesCount: Math.max(0, (r.likesCount ?? 0) + (was ? -1 : 1)) } : r);
    try {
      const res = await toggleLibraryItemLike(rv.id, u.uid, u.displayName ?? '', u.photoURL ?? '');
      reviews = reviews.map(r => r.id === rv.id ? { ...r, likesCount: res.count, isLikedByMe: res.liked } : r);
    } catch { reviews = reviews.map(r => r.id === rv.id ? { ...r, isLikedByMe: was, likesCount: Math.max(0, (r.likesCount ?? 0) + (was ? 1 : -1)) } : r); }
  }

  async function submitReview() {
    const u = $currentUser;
    if (!u || !book || !reviewText.trim()) return;
    submitting = true;
    try {
      const rv = await addBookReview({ bookId: id, authorId: book.authorId, bookTitle: book.title, authorName: book.authorName, text: reviewText.trim(), rating: reviewRating, uid: u.uid, userDisplayName: u.displayName ?? '', userPhotoURL: u.photoURL ?? '' });
      if (rv) { reviews = [rv, ...reviews]; reviewText = ''; reviewRating = 5; }
    } finally { submitting = false; }
  }

  function onTouchStart(e: TouchEvent) { touchStartY = e.touches[0].clientY; }
  function onTouchMove(e: TouchEvent) {
    if (refreshing || loading) return;
    if (document.documentElement.scrollTop > 0) return;
    const dy = e.touches[0].clientY - touchStartY;
    if (dy > 0) pullDist = Math.min(dy * 0.5, PULL_THRESHOLD + 20);
  }
  async function onTouchEnd() {
    if (pullDist >= PULL_THRESHOLD) { refreshing = true; pullDist = 0; await loadAll(); refreshing = false; }
    else { pullDist = 0; }
  }

  function ago(ts: any): string {
    const ms = ts?.seconds ? ts.seconds * 1000 : ts ? new Date(ts).getTime() : 0;
    const d = Math.floor((Date.now() - ms) / 86400000);
    if (d < 1) return 'bugün'; if (d < 7) return d + 'g'; if (d < 30) return Math.floor(d/7) + 'hf';
    return Math.floor(d/30) + 'ay';
  }
</script>

<svelte:head><title>{book?.title ?? 'Kitap'} — Heftreng</title></svelte:head>

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<div class="page" role="main" ontouchstart={onTouchStart} ontouchmove={onTouchMove} ontouchend={onTouchEnd}>

  {#if pullDist > 10 || refreshing}
    <div class="ptr" style="height:{refreshing ? 48 : pullDist}px;opacity:{refreshing ? 1 : pullDist/PULL_THRESHOLD}">
      <div class="ptr-spin" class:spin={refreshing}></div>
    </div>
  {/if}

  <div class="top-bar">
    <button class="back-btn" onclick={() => history.back()} aria-label="Geri">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="22" height="22"><polyline points="15 18 9 12 15 6"/></svg>
    </button>
    <span class="top-title">{loading ? '' : (book?.title ?? 'Kitap')}</span>
  </div>

  {#if loading}
    <div class="hero-skel"></div>
    <div class="skel-meta">
      <Skeleton width="60%" height="22px" /><Skeleton width="40%" height="14px" /><Skeleton width="80%" height="14px" />
    </div>
  {:else if !book}
    <div class="empty-page">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="56" height="56"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
      <p>Kitap bulunamadı.</p>
    </div>
  {:else}
    <!-- Android BookDetailHeader: bulanık arka plan + merkez kapak + tür rozeti -->
    <div class="hero-wrap">
      <div class="hero-bg" style={book.coverImg ? "background-image:url(" + book.coverImg + ")" : ''}></div>
      <div class="hero-cover-wrap">
        {#if book.coverImg}
          <img src={book.coverImg} alt={book.title} class="hero-cover" />
        {:else}
          <div class="hero-cover hero-cover-ph">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="40" height="40"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          </div>
        {/if}
      </div>
      <div class="type-badge" class:serial={(book as any).type === 'serial'}>
        {(book as any).type === 'serial' ? 'Seri' : 'Kitap'}
      </div>
    </div>

    <div class="book-meta">
      <h1 class="book-title">{book.title}</h1>
      {#if book.authorName}
        <a href="/library/author/{book.authorId}" class="author-row">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          <span class="author-name-link">{book.authorName}</span>
        </a>
      {/if}
      {#if book.genre}<div class="genre-chip">{book.genre}</div>{/if}
      {#if book.synopsis}<p class="synopsis">{book.synopsis}</p>{/if}
      <div class="stats-row">
        {#if (book.avgRating ?? 0) > 0}
          <div class="stat-chip">
            <svg viewBox="0 0 24 24" fill="#F59E0B" width="13" height="13"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            <span>{book.avgRating.toFixed(1)}</span>
          </div>
        {/if}
        {#if (book.reviewCount ?? 0) > 0}<div class="stat-chip"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg><span>{book.reviewCount} inceleme</span></div>{/if}
        {#if (book.quoteCount ?? 0) > 0}<div class="stat-chip"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/></svg><span>{book.quoteCount} alıntı</span></div>{/if}
      </div>
      <div class="like-row">
        <button class="like-btn" class:liked={book.isLikedByMe} onclick={handleBookLike} aria-label="Beğen">
          {#if book.isLikedByMe}<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
          {:else}<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>{/if}
          <span>{book.likes ?? 0}</span>
        </button>
      </div>
    </div>

    <div class="divider"></div>

    <div class="tabs">
      {#each TABS as tab, i}
        <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>
          {tab}<span class="tab-count">{i === 0 ? quotes.length : reviews.length}</span>
        </button>
      {/each}
      <div class="tab-line" style="transform:translateX({activeTab * 100}%)"></div>
    </div>

    {#if activeTab === 0}
      {#if quotes.length === 0}
        <div class="empty-tab">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
          <p>Bu kitap için henüz alıntı yok.</p>
          {#if $currentUser}<a href="/compose?type=quote" class="add-cta">İlk alıntıyı sen ekle →</a>{/if}
        </div>
      {:else}
        <div class="content-list">
          {#each quotes as q (q.id)}
            <div class="quote-item">
              <div class="item-user-header">
                <a href="/profile/{q.uid}" class="item-av">
                  {#if q.userPhotoURL}<img src={q.userPhotoURL} alt={q.userDisplayName} />{:else}<span>{(q.userDisplayName || '?')[0].toUpperCase()}</span>{/if}
                </a>
                <div class="item-meta">
                  <a href="/profile/{q.uid}" class="item-name">{q.userDisplayName}</a>
                  {#if q.createdAt}<span class="item-time">{ago(q.createdAt)}</span>{/if}
                </div>
              </div>
              <div class="quote-card-wrap">
                <QuoteCard quoteText={q.text} bookName={q.bookTitle} authorName={q.authorName} coverImg={q.coverImg || book?.coverImg || ''} bookId={q.bookId || id} authorId={q.authorId || book?.authorId || ''} />
              </div>
              <div class="item-actions">
                <button class="act-like" class:liked={q.isLikedByMe} onclick={() => handleQuoteLike(q)} aria-label="Beğen">
                  {#if q.isLikedByMe}<svg viewBox="0 0 24 24" fill="currentColor" width="17" height="17"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {:else}<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="17" height="17"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>{/if}
                  {#if q.likesCount > 0}<span>{q.likesCount}</span>{/if}
                </button>
                <a href="/post/{q.feedPostId}" class="act-comment" aria-label="Yorumlar">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="17" height="17"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                </a>
              </div>
            </div>
          {/each}
        </div>
      {/if}
    {:else}
      {#if $currentUser}
        <div class="review-form">
          <div class="star-row">
            {#each [1,2,3,4,5] as s}
              <button class="star-btn" onclick={() => reviewRating = s} aria-label="{s} yıldız">
                <svg viewBox="0 0 24 24" fill={s <= reviewRating ? '#F59E0B' : 'none'} stroke="#F59E0B" stroke-width="1.5" width="28" height="28"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              </button>
            {/each}
            <span class="star-label">{reviewRating}/5</span>
          </div>
          <textarea bind:value={reviewText} placeholder="İncelemenizi yazın…" rows="3" class="review-input"></textarea>
          <button class="submit-btn" disabled={submitting || !reviewText.trim()} onclick={submitReview}>
            {submitting ? 'Gönderiliyor…' : 'İnceleme Ekle'}
          </button>
        </div>
      {/if}
      {#if reviews.length === 0}
        <div class="empty-tab">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          <p>Henüz inceleme yok. İlk siz yazın!</p>
        </div>
      {:else}
        <div class="content-list">
          {#each reviews as rv (rv.id)}
            <div class="review-card">
              <div class="rv-top">
                {#if rv.bookCoverImg}<img src={rv.bookCoverImg} alt={rv.bookTitle} class="rv-cover" />{:else}<div class="rv-cover rv-cover-ph">📖</div>{/if}
                <div class="rv-stars">
                  {#each {length:5} as _, i}<svg viewBox="0 0 24 24" width="14" height="14" fill={i < Math.round(rv.rating) ? '#F59E0B' : 'none'} stroke="#F59E0B" stroke-width="1.5"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>{/each}
                  <span class="rv-rating-num">{rv.rating.toFixed(1)}</span>
                </div>
              </div>
              {#if rv.text}<p class="rv-text">{rv.text}</p>{/if}
              <div class="rv-footer">
                <a href="/profile/{rv.uid}" class="rv-user">
                  <div class="mini-av">{#if rv.userPhotoURL}<img src={rv.userPhotoURL} alt={rv.userDisplayName} />{:else}<span>{(rv.userDisplayName||'?')[0].toUpperCase()}</span>{/if}</div>
                  <span>{rv.userDisplayName}</span>
                </a>
                <button class="act-like sm" class:liked={rv.isLikedByMe} onclick={() => handleReviewLike(rv)} aria-label="Beğen">
                  {#if rv.isLikedByMe}<svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {:else}<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>{/if}
                  {#if (rv.likesCount ?? 0) > 0}<span>{rv.likesCount}</span>{/if}
                </button>
              </div>
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  {/if}
  <div style="height:80px"></div>
</div>

<style>
.page{max-width:720px;margin:0 auto;padding-bottom:80px}
.ptr{display:flex;align-items:center;justify-content:center;overflow:hidden;transition:height .2s,opacity .2s}
.ptr-spin{width:22px;height:22px;border:2.5px solid color-mix(in srgb,var(--primary) 30%,transparent);border-top-color:var(--primary);border-radius:50%}
.ptr-spin.spin{animation:spin .7s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
.top-bar{display:flex;align-items:center;gap:10px;padding:12px 14px 8px;position:sticky;top:0;background:var(--bg);z-index:10;border-bottom:1px solid var(--divider)}
.back-btn{width:36px;height:36px;border-radius:50%;border:none;background:var(--surface-var);color:var(--primary);display:flex;align-items:center;justify-content:center;cursor:pointer}
.top-title{font-size:15px;font-weight:700;color:var(--on-bg);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.hero-wrap{position:relative;width:100%;height:180px;overflow:hidden;background:var(--surface-var)}
.hero-bg{position:absolute;inset:0;background-size:cover;background-position:center;filter:blur(12px) brightness(0.45);transform:scale(1.1)}
.hero-cover-wrap{position:absolute;inset:0;display:flex;align-items:center;justify-content:center}
.hero-cover{width:100px;height:140px;object-fit:cover;border-radius:8px;box-shadow:0 4px 20px rgba(0,0,0,.4)}
.hero-cover-ph{background:var(--surface-var);display:flex;align-items:center;justify-content:center;color:var(--muted)}
.type-badge{position:absolute;top:10px;right:10px;padding:4px 10px;border-radius:6px;font-size:10px;font-weight:700;color:#fff;background:#F59E0B}
.type-badge.serial{background:var(--primary)}
.book-meta{padding:16px 16px 12px}
.book-title{font-size:20px;font-weight:800;color:var(--on-bg);margin:0 0 8px;line-height:1.3}
.author-row{display:flex;align-items:center;gap:6px;text-decoration:none;margin-bottom:8px;color:var(--muted)}
.author-name-link{font-size:14px;color:var(--muted)}
.author-row:hover .author-name-link{color:var(--primary)}
.genre-chip{display:inline-block;background:var(--surface-var);color:var(--primary);font-size:11px;font-weight:600;padding:4px 10px;border-radius:8px;margin-bottom:8px}
.synopsis{font-size:14px;line-height:1.6;color:var(--on-surface);margin:8px 0}
.stats-row{display:flex;gap:12px;flex-wrap:wrap;margin:8px 0 12px}
.stat-chip{display:flex;align-items:center;gap:4px;font-size:12px;color:var(--muted)}
.like-row{display:flex;align-items:center;gap:12px}
.like-btn{display:flex;align-items:center;gap:6px;padding:8px 14px;background:var(--surface-var);border:none;border-radius:10px;cursor:pointer;font-size:13px;font-weight:600;color:var(--muted);transition:background .15s}
.like-btn.liked{color:#FF3A5C;background:color-mix(in srgb,#FF3A5C 10%,transparent)}
.divider{height:1px;background:var(--divider)}
.tabs{position:sticky;top:52px;z-index:9;display:flex;background:var(--surface);border-bottom:1px solid var(--divider);overflow:hidden}
.tab{flex:1;padding:12px 4px;font-size:13px;font-weight:500;color:var(--muted);background:none;border:none;cursor:pointer;font-family:inherit;transition:color .2s}
.tab.active{color:var(--on-bg);font-weight:700}
.tab-count{font-size:11px;background:var(--surface-var);border-radius:99px;padding:1px 6px;margin-left:4px}
.tab-line{position:absolute;bottom:0;left:0;width:50%;height:2.5px;background:var(--primary);border-radius:2px 2px 0 0;transition:transform .25s cubic-bezier(.4,0,.2,1);pointer-events:none}
.empty-page,.empty-tab{display:flex;flex-direction:column;align-items:center;padding:60px 20px;gap:12px;color:var(--muted);text-align:center}
.empty-page svg,.empty-tab svg{opacity:.4}
.empty-tab p,.empty-page p{font-size:14px}
.add-cta{color:var(--primary);font-weight:600;font-size:13px;text-decoration:none;margin-top:4px}
.hero-skel{height:180px;background:var(--shimmer);animation:shimmer 1.4s ease-in-out infinite}
.skel-meta{padding:16px;display:flex;flex-direction:column;gap:8px}
@keyframes shimmer{0%,100%{opacity:1}50%{opacity:.5}}
.content-list{padding:10px 12px;display:flex;flex-direction:column;gap:10px}
.quote-item{background:var(--card);border-radius:16px;overflow:hidden;border:.7px solid var(--divider)}
.item-user-header{display:flex;align-items:center;gap:9px;padding:11px 12px 0}
.item-av{width:34px;height:34px;border-radius:50%;background:var(--surface-var);overflow:hidden;flex-shrink:0;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;color:var(--on-bg);text-decoration:none}
.item-av img{width:100%;height:100%;object-fit:cover}
.item-meta{flex:1;display:flex;flex-direction:column;gap:1px}
.item-name{font-size:13px;font-weight:700;color:var(--on-bg);text-decoration:none}
.item-name:hover{text-decoration:underline}
.item-time{font-size:11px;color:var(--muted)}
.quote-card-wrap{padding:10px 12px 0}
.item-actions{display:flex;align-items:center;gap:4px;padding:6px 12px 10px;border-top:1px solid var(--divider);margin-top:8px}
.act-like{display:flex;align-items:center;gap:4px;background:none;border:none;cursor:pointer;color:var(--muted);padding:5px 8px;border-radius:20px;font-size:12px;font-family:inherit;transition:background .15s}
.act-like:hover{background:var(--surface-var)}
.act-like.liked{color:#FF3A5C}
.act-like.sm{font-size:11px;padding:3px 6px}
.act-comment{display:flex;align-items:center;color:var(--muted);padding:5px 8px;border-radius:20px;text-decoration:none;transition:background .15s}
.act-comment:hover{background:var(--surface-var)}
.review-form{padding:14px 14px 16px;border-bottom:1px solid var(--divider);display:flex;flex-direction:column;gap:12px}
.star-row{display:flex;align-items:center;gap:4px}
.star-btn{background:none;border:none;cursor:pointer;padding:2px}
.star-label{font-size:13px;color:var(--muted);margin-left:6px;font-weight:600}
.review-input{width:100%;padding:12px;border:1.5px solid var(--divider);border-radius:12px;background:var(--surface-var);color:var(--on-bg);font-size:14px;font-family:inherit;resize:vertical;outline:none;box-sizing:border-box;line-height:1.6}
.review-input:focus{border-color:var(--primary)}
.submit-btn{align-self:flex-end;padding:9px 20px;background:var(--primary);color:#fff;border:none;border-radius:20px;font-size:13px;font-weight:700;cursor:pointer;font-family:inherit}
.submit-btn:disabled{opacity:.5;cursor:not-allowed}
.review-card{background:var(--card);border-radius:14px;padding:14px;border:.7px solid var(--divider)}
.rv-top{display:flex;align-items:center;gap:12px;margin-bottom:10px}
.rv-cover{width:42px;height:60px;object-fit:cover;border-radius:5px;flex-shrink:0}
.rv-cover-ph{background:var(--surface-var);display:flex;align-items:center;justify-content:center;font-size:20px}
.rv-stars{display:flex;align-items:center;gap:2px}
.rv-rating-num{font-size:12px;font-weight:700;color:#F59E0B;margin-left:4px}
.rv-text{font-size:14px;line-height:1.6;color:var(--on-surface);margin-bottom:10px}
.rv-footer{display:flex;align-items:center;justify-content:space-between;border-top:1px solid var(--divider);padding-top:8px}
.rv-user{display:flex;align-items:center;gap:6px;text-decoration:none;color:var(--muted);font-size:12px}
.mini-av{width:24px;height:24px;border-radius:50%;background:var(--surface-var);overflow:hidden;display:flex;align-items:center;justify-content:center;font-size:10px;font-weight:700;color:var(--on-bg);flex-shrink:0}
.mini-av img{width:100%;height:100%;object-fit:cover}
</style>
