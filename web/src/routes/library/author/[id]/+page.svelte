<script lang="ts">
  import { onMount }     from 'svelte';
  import { page }        from '$app/stores';
  import { currentUser } from '$lib/stores/auth';
  import QuoteCard       from '$lib/components/QuoteCard.svelte';
  import Skeleton        from '$lib/components/Skeleton.svelte';
  import {
    fetchAuthorById, fetchAuthorBooks, fetchQuotesByAuthor, fetchReviewsByAuthor,
    checkAuthorFollow, followAuthor, unfollowAuthor,
    toggleLibraryItemLike, hydrateQuoteLikes,
  } from '$lib/services/library.service';
  import type { Author, LibraryBook, BookQuote, BookReview } from '$lib/models/library';

  const id = $derived($page.params.id);
  const TABS = ['Kitaplar', 'Alıntılar', 'İncelemeler'] as const;
  let activeTab     = $state(0);
  let author: Author | null = $state(null);
  let books:  LibraryBook[] = $state([]);
  let quotes: BookQuote[]   = $state([]);
  let reviews: BookReview[] = $state([]);
  let loading       = $state(true);
  let following     = $state(false);
  let followLoading = $state(false);
  let refreshing    = $state(false);
  let touchStartY   = 0;
  let pullDist      = $state(0);
  const PULL_THRESHOLD = 72;

  onMount(() => loadAll());

  async function loadAll() {
    loading = true;
    try {
      author = await fetchAuthorById(id);
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
    } catch(e) { console.error(e); }
    finally { loading = false; }
  }

  async function toggleFollow() {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    followLoading = true;
    const was = following;
    following = !was;
    if (author) author = { ...author, followerCount: Math.max(0, author.followerCount + (was ? -1 : 1)) };
    try {
      if (was) await unfollowAuthor(u.uid, id);
      else     await followAuthor(u.uid, id);
    } catch {
      following = was;
      if (author) author = { ...author, followerCount: Math.max(0, author.followerCount + (was ? 1 : -1)) };
    }
    followLoading = false;
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
    if (d < 1) return 'bugün'; if (d < 7) return d + 'g';
    if (d < 30) return Math.floor(d/7) + 'hf'; return Math.floor(d/30) + 'ay';
  }
</script>

<svelte:head><title>{author?.name ?? 'Yazar'} — Heftreng</title></svelte:head>

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
    <span class="top-title">{loading ? '' : (author?.name ?? 'Yazar')}</span>
  </div>

  {#if loading}
    <div class="skel-header">
      <Skeleton width="80px" height="80px" radius="50%" />
      <div style="flex:1;display:flex;flex-direction:column;gap:8px">
        <Skeleton width="50%" height="18px" /><Skeleton width="35%" height="12px" /><Skeleton width="70%" height="12px" />
      </div>
    </div>
  {:else if !author}
    <div class="empty-page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="56" height="56"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg><p>Yazar bulunamadı.</p></div>
  {:else}
    <!-- Android LibraryAuthorRow + ProfileHeader karışımı -->
    <div class="author-header">
      <div class="author-av-wrap">
        {#if author.photoURL}
          <img src={author.photoURL} alt={author.name} class="author-av" />
        {:else}
          <div class="author-av author-av-ph">{author.name[0]?.toUpperCase()}</div>
        {/if}
      </div>
      <div class="author-info">
        <h1 class="author-name">{author.name}</h1>
        {#if author.nationality}
          <span class="author-nat">{author.nationality}{author.birthYear ? ' · ' + author.birthYear : ''}</span>
        {/if}
        <!-- Android StatChip'ler -->
        <div class="stat-chips">
          {#if author.bookCount > 0}
            <span class="stat-chip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              {author.bookCount} kitap
            </span>
          {/if}
          {#if author.quoteCount > 0}
            <span class="stat-chip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/></svg>
              {author.quoteCount} alıntı
            </span>
          {/if}
          {#if author.followerCount > 0}
            <span class="stat-chip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
              {author.followerCount} takipçi
            </span>
          {/if}
        </div>
        {#if author.bio}<p class="author-bio">{author.bio}</p>{/if}
        <button class="follow-btn" class:following onclick={toggleFollow} disabled={followLoading}>
          {following ? 'Takip Ediliyor' : 'Takip Et'}
        </button>
      </div>
    </div>

    <!-- Sekmeler -->
    <div class="tabs">
      {#each TABS as tab, i}
        <button class="tab" class:active={activeTab === i} onclick={() => activeTab = i}>
          {tab}<span class="tab-count">{i === 0 ? books.length : i === 1 ? quotes.length : reviews.length}</span>
        </button>
      {/each}
      <div class="tab-line" style="transform:translateX({activeTab * 100}%)"></div>
    </div>

    <!-- Kitaplar (Android LibraryBooksTab — 2 kolon grid) -->
    {#if activeTab === 0}
      {#if books.length === 0}
        <div class="empty-tab"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg><p>Bu yazara ait kitap yok.</p></div>
      {:else}
        <div class="book-grid">
          {#each books as b (b.id)}
            <a href="/library/book/{b.id}" class="book-card">
              {#if b.coverImg}
                <img src={b.coverImg} alt={b.title} class="book-cover" />
              {:else}
                <div class="book-cover book-cover-ph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg></div>
              {/if}
              <div class="book-info">
                <span class="book-title">{b.title}</span>
                {#if (b.avgRating ?? 0) > 0}
                  <span class="book-rating"><svg viewBox="0 0 24 24" fill="#F59E0B" width="11" height="11"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>{b.avgRating.toFixed(1)}</span>
                {/if}
                <span class="book-counts">{b.quoteCount} alıntı · {b.reviewCount} inceleme</span>
              </div>
            </a>
          {/each}
        </div>
      {/if}

    <!-- Alıntılar (Android LibraryQuotesTab — ConnectedPostCard gibi) -->
    {:else if activeTab === 1}
      {#if quotes.length === 0}
        <div class="empty-tab"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg><p>Bu yazara ait alıntı yok.</p></div>
      {:else}
        <div class="content-list">
          {#each quotes as q (q.id)}
            <div class="quote-item">
              <div class="item-user-header">
                <a href="/profile/{q.uid}" class="item-av">
                  {#if q.userPhotoURL}<img src={q.userPhotoURL} alt={q.userDisplayName} />{:else}<span>{(q.userDisplayName||'?')[0].toUpperCase()}</span>{/if}
                </a>
                <div class="item-meta">
                  <a href="/profile/{q.uid}" class="item-name">{q.userDisplayName}</a>
                  {#if q.createdAt}<span class="item-time">{ago(q.createdAt)}</span>{/if}
                </div>
                {#if q.bookId}
                  <a href="/library/book/{q.bookId}" class="book-chip">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="11" height="11"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                    {q.bookTitle}
                  </a>
                {/if}
              </div>
              <div class="quote-card-wrap">
                <QuoteCard quoteText={q.text} bookName={q.bookTitle} authorName={q.authorName} coverImg={q.coverImg} bookId={q.bookId || ''} authorId={id} />
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

    <!-- İncelemeler -->
    {:else}
      {#if reviews.length === 0}
        <div class="empty-tab"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg><p>Bu yazara ait inceleme yok.</p></div>
      {:else}
        <div class="content-list">
          {#each reviews as rv (rv.id)}
            <a href="/library/book/{rv.bookId}" class="review-card">
              <div class="rv-top">
                {#if rv.bookCoverImg}<img src={rv.bookCoverImg} alt={rv.bookTitle} class="rv-cover" />{:else}<div class="rv-cover rv-cover-ph">📖</div>{/if}
                <div>
                  <span class="rv-book-title">{rv.bookTitle}</span>
                  <div class="rv-stars">
                    {#each {length:5} as _, i}<svg viewBox="0 0 24 24" width="13" height="13" fill={i < Math.round(rv.rating) ? '#F59E0B' : 'none'} stroke="#F59E0B" stroke-width="1.5"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>{/each}
                    <span class="rv-rating-num">{rv.rating.toFixed(1)}</span>
                  </div>
                </div>
              </div>
              {#if rv.text}<p class="rv-text">{rv.text}</p>{/if}
              <div class="rv-user-row">
                <div class="mini-av">{#if rv.userPhotoURL}<img src={rv.userPhotoURL} alt={rv.userDisplayName} />{:else}<span>{(rv.userDisplayName||'?')[0].toUpperCase()}</span>{/if}</div>
                <span>{rv.userDisplayName}</span>
              </div>
            </a>
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
.empty-page,.empty-tab{display:flex;flex-direction:column;align-items:center;padding:60px 20px;gap:12px;color:var(--muted);text-align:center}
.empty-page svg,.empty-tab svg{opacity:.4}.empty-tab p,.empty-page p{font-size:14px}
.skel-header{display:flex;gap:12px;padding:16px}
/* Author header */
.author-header{display:flex;gap:14px;padding:16px;background:var(--card);border-bottom:1px solid var(--divider);align-items:flex-start}
.author-av-wrap{flex-shrink:0}
.author-av{width:78px;height:78px;border-radius:50%;object-fit:cover;display:block;border:2px solid color-mix(in srgb,var(--primary) 30%,transparent)}
.author-av-ph{background:color-mix(in srgb,var(--primary) 15%,transparent);display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:700;color:var(--primary)}
.author-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.author-name{font-size:20px;font-weight:800;color:var(--on-bg);margin:0}
.author-nat{font-size:13px;color:var(--muted)}
.stat-chips{display:flex;gap:10px;flex-wrap:wrap;margin-top:2px}
.stat-chip{display:flex;align-items:center;gap:3px;font-size:12px;color:var(--muted)}
.author-bio{font-size:13px;line-height:1.55;color:var(--on-surface);margin:4px 0}
.follow-btn{align-self:flex-start;padding:7px 18px;border-radius:20px;font-size:13px;font-weight:700;border:2px solid var(--primary);background:none;color:var(--primary);cursor:pointer;font-family:inherit;margin-top:4px;transition:background .15s,color .15s}
.follow-btn.following{background:var(--primary);color:#fff}
.follow-btn:disabled{opacity:.6}
/* Tabs */
.tabs{position:sticky;top:52px;z-index:9;display:flex;background:var(--surface);border-bottom:1px solid var(--divider);overflow:hidden}
.tab{flex:1;padding:12px 4px;font-size:13px;font-weight:500;color:var(--muted);background:none;border:none;cursor:pointer;font-family:inherit;transition:color .2s}
.tab.active{color:var(--on-bg);font-weight:700}
.tab-count{font-size:11px;background:var(--surface-var);border-radius:99px;padding:1px 6px;margin-left:4px}
.tab-line{position:absolute;bottom:0;left:0;width:33.33%;height:2.5px;background:var(--primary);border-radius:2px 2px 0 0;transition:transform .25s cubic-bezier(.4,0,.2,1);pointer-events:none}
/* Books grid */
.book-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;padding:12px}
.book-card{display:flex;flex-direction:column;gap:6px;text-decoration:none}
.book-cover{width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:10px;box-shadow:0 3px 10px rgba(0,0,0,.15)}
.book-cover-ph{background:var(--surface-var);display:flex;align-items:center;justify-content:center;color:var(--muted)}
.book-info{display:flex;flex-direction:column;gap:2px;padding:0 2px}
.book-title{font-size:13px;font-weight:700;color:var(--on-bg);line-height:1.3}
.book-rating{display:flex;align-items:center;gap:3px;font-size:12px;color:#F59E0B;font-weight:600}
.book-counts{font-size:11px;color:var(--muted)}
/* Content list */
.content-list{padding:10px 12px;display:flex;flex-direction:column;gap:10px}
.quote-item{background:var(--card);border-radius:16px;overflow:hidden;border:.7px solid var(--divider)}
.item-user-header{display:flex;align-items:center;gap:9px;padding:11px 12px 0}
.item-av{width:34px;height:34px;border-radius:50%;background:var(--surface-var);overflow:hidden;flex-shrink:0;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;color:var(--on-bg);text-decoration:none}
.item-av img{width:100%;height:100%;object-fit:cover}
.item-meta{flex:1;display:flex;flex-direction:column;gap:1px}
.item-name{font-size:13px;font-weight:700;color:var(--on-bg);text-decoration:none}
.item-name:hover{text-decoration:underline}
.item-time{font-size:11px;color:var(--muted)}
.book-chip{display:flex;align-items:center;gap:3px;font-size:11px;color:var(--primary);background:color-mix(in srgb,var(--primary) 10%,transparent);border-radius:99px;padding:3px 8px;text-decoration:none;white-space:nowrap;max-width:100px;overflow:hidden;text-overflow:ellipsis;flex-shrink:0}
.quote-card-wrap{padding:10px 12px 0}
.item-actions{display:flex;align-items:center;gap:4px;padding:6px 12px 10px;border-top:1px solid var(--divider);margin-top:8px}
.act-like{display:flex;align-items:center;gap:4px;background:none;border:none;cursor:pointer;color:var(--muted);padding:5px 8px;border-radius:20px;font-size:12px;font-family:inherit;transition:background .15s}
.act-like:hover{background:var(--surface-var)}.act-like.liked{color:#FF3A5C}
.act-comment{display:flex;align-items:center;color:var(--muted);padding:5px 8px;border-radius:20px;text-decoration:none;transition:background .15s}
.act-comment:hover{background:var(--surface-var)}
/* Reviews */
.review-card{display:block;background:var(--card);border-radius:14px;padding:14px;border:.7px solid var(--divider);text-decoration:none}
.rv-top{display:flex;align-items:center;gap:12px;margin-bottom:8px}
.rv-cover{width:40px;height:58px;object-fit:cover;border-radius:5px;flex-shrink:0}
.rv-cover-ph{background:var(--surface-var);display:flex;align-items:center;justify-content:center;font-size:20px}
.rv-book-title{display:block;font-size:14px;font-weight:700;color:var(--on-bg);margin-bottom:4px}
.rv-stars{display:flex;align-items:center;gap:2px}
.rv-rating-num{font-size:12px;font-weight:700;color:#F59E0B;margin-left:4px}
.rv-text{font-size:14px;line-height:1.6;color:var(--on-surface);margin-bottom:10px}
.rv-user-row{display:flex;align-items:center;gap:6px;font-size:12px;color:var(--muted)}
.mini-av{width:22px;height:22px;border-radius:50%;background:var(--surface-var);overflow:hidden;display:flex;align-items:center;justify-content:center;font-size:10px;font-weight:700;color:var(--on-bg);flex-shrink:0}
.mini-av img{width:100%;height:100%;object-fit:cover}
</style>
