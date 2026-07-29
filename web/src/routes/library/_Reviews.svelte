<!--
  _Reviews.svelte — Kütüphane İncelemeler sekmesi.
-->
<script lang="ts">
  import { onMount }     from 'svelte';
  import { currentUser } from '$lib/stores/auth';
  import { supabase }    from '$lib/supabase/config';
  import EmptyState      from '$lib/components/EmptyState.svelte';
  import Skeleton        from '$lib/components/Skeleton.svelte';
  import ActionBar       from '$lib/components/ActionBar.svelte';
  import { fetchRecentReviews, toggleLibraryItemLike } from '$lib/services/library.service';
  import type { BookReview } from '$lib/models/library';

  let reviews: BookReview[] = $state([]);
  let loading = $state(true);

  export async function refresh() { await load(); }

  onMount(() => load());

  async function load() {
    loading = true;
    const raw = await fetchRecentReviews();
    // beğeni hydrate
    const uid = $currentUser?.uid ?? null;
    const ids = raw.map(r => r.feedPostId).filter(Boolean);
    if (ids.length && uid) {
      const { data } = await supabase
        .from('feed_likes').select('post_id, uid').in('post_id', ids);
      const counts  = new Map<string, number>();
      const likedBy = new Set<string>();
      for (const r of (data ?? [])) {
        counts.set(r.post_id, (counts.get(r.post_id) ?? 0) + 1);
        if (r.uid === uid) likedBy.add(r.post_id);
      }
      reviews = raw.map(r => ({
        ...r,
        likesCount:  counts.get(r.feedPostId) ?? 0,
        isLikedByMe: likedBy.has(r.feedPostId),
      }));
    } else {
      reviews = raw;
    }
    loading = false;
  }

  async function handleLike(rv: BookReview) {
    const u = $currentUser;
    if (!u) { window.location.href = '/login'; return; }
    const was = rv.isLikedByMe ?? false;
    reviews = reviews.map(r => r.id === rv.id
      ? { ...r, isLikedByMe: !was, likesCount: Math.max(0, r.likesCount + (was ? -1 : 1)) }
      : r
    );
    try {
      const res = await toggleLibraryItemLike(rv.feedPostId || rv.id, u.uid, u.displayName ?? '', u.photoURL ?? '');
      reviews = reviews.map(r => r.id === rv.id
        ? { ...r, likesCount: res.count, isLikedByMe: res.liked } : r);
    } catch {
      reviews = reviews.map(r => r.id === rv.id
        ? { ...r, isLikedByMe: was, likesCount: Math.max(0, r.likesCount + (was ? 1 : -1)) } : r);
    }
  }

  function stars(r: number) {
    return Array.from({length: 5}, (_, i) => i < Math.round(r));
  }
</script>

{#if loading}
  <div class="skel-list">
    {#each {length: 3} as _}
      <div class="skel-card"><Skeleton width="100%" height="120px" radius="14px" /></div>
    {/each}
  </div>

{:else if reviews.length === 0}
  <EmptyState icon="⭐" message="Henüz inceleme yok." />

{:else}
  <div class="review-list">
    {#each reviews as rv (rv.id)}
      <div class="review-card">
        <!-- Kitap başlık satırı -->
        <a href="/library/book/{rv.bookId}" class="review-book-row">
          {#if rv.bookCoverImg}
            <img src={rv.bookCoverImg} alt={rv.bookTitle} class="rv-cover" />
          {:else}
            <div class="rv-cover rv-cover-ph">📖</div>
          {/if}
          <div class="rv-book-info">
            <span class="rv-book-title">{rv.bookTitle}</span>
            <div class="rv-stars">
              {#each stars(rv.rating) as filled}
                <svg viewBox="0 0 24 24" width="13" height="13"
                  fill={filled ? '#F59E0B' : 'none'}
                  stroke="#F59E0B" stroke-width="1.5">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
              {/each}
              <span class="rv-rating-num">{rv.rating.toFixed(1)}</span>
            </div>
          </div>
        </a>

        {#if rv.text}
          <p class="rv-text">{rv.text}</p>
        {/if}

        <!-- Alt satır: kullanıcı + beğeni -->
        <div class="rv-footer">
          <a href="/profile/{rv.uid}" class="rv-user">
            <div class="mini-av">
              {#if rv.userPhotoURL}
                <img src={rv.userPhotoURL} alt={rv.userDisplayName} />
              {:else}
                <span>{(rv.userDisplayName || '?')[0].toUpperCase()}</span>
              {/if}
            </div>
            <span>{rv.userDisplayName}</span>
          </a>
          <ActionBar
            liked={rv.isLikedByMe ?? false}
            likeCount={rv.likesCount}
            onLike={() => handleLike(rv)}
            compact
          />
        </div>
      </div>
    {/each}
  </div>
{/if}

<style>
.skel-list { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.skel-card {}

.review-list { padding: 10px 12px; display: flex; flex-direction: column; gap: 10px; }
.review-card {
  background: var(--card);
  border-radius: 16px;
  overflow: hidden;
  border: 0.7px solid var(--divider);
  padding: 14px;
}

.review-book-row {
  display: flex; gap: 12px; align-items: center;
  margin-bottom: 10px; text-decoration: none;
}
.rv-cover {
  width: 44px; height: 64px;
  border-radius: 5px; object-fit: cover; flex-shrink: 0;
}
.rv-cover-ph {
  background: var(--surface-var);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
}
.rv-book-info { display: flex; flex-direction: column; gap: 5px; }
.rv-book-title { font-size: 14px; font-weight: 700; color: var(--on-bg); }
.rv-stars { display: flex; align-items: center; gap: 2px; }
.rv-rating-num { font-size: 12px; font-weight: 700; color: #F59E0B; margin-left: 4px; }

.rv-text {
  font-size: 14px; color: var(--on-surface);
  line-height: 1.6; margin-bottom: 12px;
}

.rv-footer {
  display: flex; align-items: center; justify-content: space-between;
  border-top: 1px solid var(--divider); padding-top: 8px;
}
.rv-user {
  display: flex; align-items: center; gap: 7px;
  text-decoration: none; color: var(--muted);
  font-size: 13px; font-weight: 500;
}
.mini-av {
  width: 26px; height: 26px; border-radius: 50%;
  background: var(--surface-var); overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: var(--on-bg);
}
.mini-av img { width: 100%; height: 100%; object-fit: cover; }
</style>
