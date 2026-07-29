<!--
  _Books.svelte — Kütüphane Kitaplar sekmesi.
-->
<script lang="ts">
  import { onMount }  from 'svelte';
  import EmptyState   from '$lib/components/EmptyState.svelte';
  import Skeleton     from '$lib/components/Skeleton.svelte';
  import { fetchBooks }      from '$lib/services/library.service';
  import type { LibraryBook } from '$lib/models/library';

  let books:   LibraryBook[] = $state([]);
  let loading  = $state(true);

  export async function refresh() { await load(); }

  onMount(() => load());

  async function load() {
    loading = true;
    books   = await fetchBooks();
    loading = false;
  }
</script>

{#if loading}
  <div class="book-grid">
    {#each {length: 6} as _}
      <div class="skel-book">
        <Skeleton width="100%" height="0" radius="10px" style="padding-bottom:150%" />
        <Skeleton width="70%" height="13px" />
        <Skeleton width="50%" height="11px" />
      </div>
    {/each}
  </div>

{:else if books.length === 0}
  <EmptyState icon="📚" message="Henüz kitap yok." />

{:else}
  <div class="book-grid">
    {#each books as b (b.id)}
      <a href="/library/book/{b.id}" class="book-card">
        {#if b.coverImg}
          <img src={b.coverImg} alt={b.title} class="book-cover" />
        {:else}
          <div class="book-cover book-cover-ph">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="1.5" width="32" height="32">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
        {/if}
        <div class="book-info">
          <span class="book-title">{b.title}</span>
          <span class="book-author">{b.authorName}</span>
          {#if (b.avgRating ?? 0) > 0}
            <span class="book-rating">
              <svg viewBox="0 0 24 24" fill="#F59E0B" width="11" height="11">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
              </svg>
              {b.avgRating.toFixed(1)}
            </span>
          {/if}
        </div>
      </a>
    {/each}
  </div>
{/if}

<style>
.book-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 12px;
}
.skel-book { display: flex; flex-direction: column; gap: 6px; }

.book-card  { display: flex; flex-direction: column; gap: 7px; text-decoration: none; }
.book-cover {
  width: 100%; aspect-ratio: 2/3; object-fit: cover;
  border-radius: 10px; display: block;
  box-shadow: 0 3px 10px rgba(0,0,0,0.15);
}
.book-cover-ph {
  background: var(--surface-var);
  display: flex; align-items: center; justify-content: center;
  color: var(--muted);
}
.book-info   { display: flex; flex-direction: column; gap: 2px; padding: 0 2px; }
.book-title  { font-size: 13px; font-weight: 700; color: var(--on-bg); line-height: 1.3; }
.book-author { font-size: 12px; color: var(--muted); }
.book-rating {
  display: flex; align-items: center; gap: 3px;
  font-size: 12px; color: #F59E0B; font-weight: 600;
}
</style>
