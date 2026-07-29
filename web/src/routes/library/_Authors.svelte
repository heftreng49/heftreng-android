<!--
  _Authors.svelte — Kütüphane Yazarlar sekmesi.
-->
<script lang="ts">
  import { onMount }  from 'svelte';
  import EmptyState   from '$lib/components/EmptyState.svelte';
  import Skeleton     from '$lib/components/Skeleton.svelte';
  import { fetchAuthors } from '$lib/services/library.service';
  import type { Author }  from '$lib/models/library';

  let authors: Author[] = $state([]);
  let loading = $state(true);

  export async function refresh() { await load(); }

  onMount(() => load());

  async function load() {
    loading = true;
    authors = await fetchAuthors();
    loading = false;
  }
</script>

{#if loading}
  <div class="skel-list">
    {#each {length: 5} as _}
      <div class="skel-row">
        <Skeleton width="52px" height="52px" radius="50%" />
        <div style="flex:1;display:flex;flex-direction:column;gap:6px">
          <Skeleton width="45%" height="14px" />
          <Skeleton width="30%" height="12px" />
        </div>
      </div>
    {/each}
  </div>

{:else if authors.length === 0}
  <EmptyState icon="✍️" message="Henüz yazar yok." />

{:else}
  <div class="author-list">
    {#each authors as a (a.id)}
      <a href="/library/author/{a.id}" class="author-card">
        <!-- Avatar -->
        <div class="author-av">
          {#if a.photoURL}
            <img src={a.photoURL} alt={a.name} />
          {:else}
            <span>{a.name[0]?.toUpperCase()}</span>
          {/if}
        </div>

        <!-- Bilgi -->
        <div class="author-info">
          <span class="author-name">{a.name}</span>
          {#if a.nationality}
            <span class="author-nat">{a.nationality}</span>
          {/if}
          <div class="chips">
            {#if (a.bookCount ?? 0) > 0}
              <span class="chip">📚 {a.bookCount} kitap</span>
            {/if}
            {#if (a.quoteCount ?? 0) > 0}
              <span class="chip">❝ {a.quoteCount} alıntı</span>
            {/if}
            {#if (a.followerCount ?? 0) > 0}
              <span class="chip">👤 {a.followerCount} takipçi</span>
            {/if}
          </div>
        </div>

        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          width="18" height="18" style="color:var(--muted);flex-shrink:0">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </a>
    {/each}
  </div>
{/if}

<style>
.skel-list { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.skel-row  { display: flex; gap: 12px; align-items: center; padding: 10px; background: var(--card); border-radius: 14px; }

.author-list {
  display: flex; flex-direction: column;
  gap: 8px; padding: 10px 12px;
}
.author-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--card); border-radius: 14px;
  padding: 12px 14px; text-decoration: none;
  border: 0.7px solid var(--divider);
  transition: border-color 0.15s;
}
.author-card:hover {
  border-color: color-mix(in srgb, var(--primary) 30%, var(--divider));
}

.author-av {
  width: 52px; height: 52px; border-radius: 50%;
  background: var(--surface-var); overflow: hidden;
  flex-shrink: 0; display: flex; align-items: center;
  justify-content: center; font-size: 18px;
  font-weight: 700; color: var(--on-bg);
  border: 2px solid color-mix(in srgb, var(--primary) 20%, transparent);
}
.author-av img { width: 100%; height: 100%; object-fit: cover; }

.author-info {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 3px;
}
.author-name {
  font-size: 15px; font-weight: 700; color: var(--on-bg);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.author-nat  { font-size: 12px; color: var(--muted); }

.chips       { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 2px; }
.chip        { font-size: 11px; color: var(--muted); }
</style>
