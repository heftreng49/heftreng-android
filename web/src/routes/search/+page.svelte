<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import { search, fetchSuggestions } from '$lib/services/search.service';
  import TabBar   from '$lib/components/TabBar.svelte';
  import Skeleton from '$lib/components/Skeleton.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Avatar   from '$lib/components/Avatar.svelte';

  // 6 sekme — Android SearchScreen tabs
  const tabs = ['Hepsi', 'Kişi', 'Gönderi', 'Seri', 'Kitap', 'Alıntı'];
  const typeMap: Record<number, string[]> = {
    0: [],
    1: ['user'],
    2: ['post'],
    3: ['serial'],
    4: ['library_book'],
    5: ['book_quote'],
  };

  let activeTab    = $state(0);
  let queryText    = $state('');
  let results      = $state<any[]>([]);
  let suggestions  = $state<any[]>([]);
  let loading      = $state(false);
  let timer: any;

  const filtered = $derived(
    activeTab === 0 ? results : results.filter(r => typeMap[activeTab]?.includes(r.type))
  );

  onMount(async () => {
    suggestions = await fetchSuggestions();
  });

  function onInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    queryText = val;
    clearTimeout(timer);
    if (val.trim().length < 2) { results = []; return; }
    timer = setTimeout(async () => {
      loading = true;
      results = await search(val.trim());
      loading = false;
    }, 280);
  }

  function clear() { queryText = ''; results = []; }
</script>

<svelte:head><title>Ara — Heftreng</title></svelte:head>

<div class="search-page">
  <!-- Arama kutusu -->
  <div class="search-bar">
    <svg class="search-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="18" height="18">
      <circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/>
    </svg>
    <input
      type="search" placeholder="Ara…" autocomplete="off"
      value={queryText} oninput={onInput} class="search-input"
    />
    {#if queryText}
      <button class="clear-btn" onclick={clear}>✕</button>
    {/if}
  </div>

  {#if queryText.length >= 2}
    <TabBar {tabs} bind:active={activeTab} stickyTop={52} />

    {#if loading}
      <div class="result-list">
        {#each {length: 5} as _}
          <div class="result-skeleton">
            <Skeleton width="40px" height="40px" radius="50%" />
            <div style="flex:1"><Skeleton width="50%" height="13px" /><Skeleton width="70%" height="11px" /></div>
          </div>
        {/each}
      </div>
    {:else if filtered.length === 0}
      <EmptyState icon="🔍" message="Sonuç bulunamadı." hint='"{queryText}" için eşleşme yok.' />
    {:else}
      <ul class="result-list">
        {#each filtered as r (r.id + r.type)}
          <li>
            <a href={r.href} class="result-item">
              {#if r.type === 'user' || r.type === 'library_author'}
                <Avatar src={r.imageUrl} name={r.title} size={40} />
              {:else if r.imageUrl}
                <img src={r.imageUrl} alt={r.title} class="result-thumb" />
              {:else}
                <div class="result-thumb-ph">
                  {#if r.type === 'post'}📄{:else if r.type === 'library_book'}📖{:else if r.type === 'book_quote'}""{:else}📋{/if}
                </div>
              {/if}
              <div class="result-info">
                <span class="result-title">{r.title}</span>
                {#if r.subtitle}<span class="result-sub">{r.subtitle}</span>{/if}
                <span class="result-type-badge">{r.type === 'user' ? 'Kişi' : r.type === 'post' ? 'Gönderi' : r.type === 'library_book' ? 'Kitap' : r.type === 'book_quote' ? 'Alıntı' : r.type === 'library_author' ? 'Yazar' : r.type}</span>
              </div>
            </a>
          </li>
        {/each}
      </ul>
    {/if}

  {:else}
    <!-- Önerilen kişiler -->
    <p class="section-label">Önerilen Kişiler</p>
    <ul class="result-list">
      {#each suggestions as s (s.id)}
        <li>
          <a href={s.href} class="result-item">
            <Avatar src={s.imageUrl} name={s.title} size={40} />
            <div class="result-info">
              <span class="result-title">{s.title}</span>
              {#if s.subtitle}<span class="result-sub">{s.subtitle}</span>{/if}
            </div>
          </a>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
.search-page { min-height: 100dvh; }
.search-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: var(--surface);
  border-bottom: 1px solid var(--divider);
  position: sticky; top: 52px; z-index: 9;
}
.search-ico { flex-shrink: 0; color: var(--muted); }
.search-input {
  flex: 1; border: none; background: var(--surface-var);
  border-radius: 20px; padding: 9px 14px;
  font-size: 0.9rem; font-family: inherit; color: var(--on-bg);
  outline: none;
}
.clear-btn { background: none; border: none; cursor: pointer; color: var(--muted); font-size: 0.9rem; }

.section-label { font-size: 0.78rem; font-weight: 700; color: var(--muted); padding: 14px 16px 4px; margin: 0; }
.result-list { list-style: none; padding: 0; margin: 0; }
.result-item {
  display: flex; align-items: center; gap: 12px;
  padding: 11px 16px; border-bottom: 1px solid var(--divider);
  text-decoration: none; color: inherit; transition: background 0.12s;
}
.result-item:hover { background: var(--surface-var); }
.result-thumb { width: 40px; height: 40px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.result-thumb-ph {
  width: 40px; height: 40px; border-radius: 8px; flex-shrink: 0;
  background: var(--surface-var); display: flex; align-items: center;
  justify-content: center; font-size: 1.2rem;
}
.result-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.result-title { font-size: 0.9rem; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.result-sub   { font-size: 0.75rem; color: var(--muted); }
.result-type-badge {
  display: inline-block; font-size: 10px; font-weight: 600;
  color: var(--primary); background: color-mix(in srgb, var(--primary) 12%, transparent);
  border-radius: 4px; padding: 1px 6px; width: fit-content;
}
.result-skeleton { display: flex; gap: 10px; padding: 12px 16px; align-items: center; }
</style>
