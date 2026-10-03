<script lang="ts">
  // Android BookScreens (type=serial) karşılığı
  // Seri kitapları (serial) listeler; bölümlere ve okuma sayfasına gider.
  import { onMount }    from 'svelte';
  import { goto }       from '$app/navigation';
  import { supabase }   from '$lib/supabase/config';
  import { currentUser } from '$lib/stores/auth';
  import PageTopBar  from '$lib/components/PageTopBar.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import EmptyState  from '$lib/components/EmptyState.svelte';
  import Avatar      from '$lib/components/Avatar.svelte';

  type Serial = {
    id: string; title: string; description: string; cover_url: string;
    author_name: string; author_uid: string; chapter_count: number;
    category: string; language: string; created_at: string;
  };

  let serials    = $state<Serial[]>([]);
  let loading    = $state(true);
  let search     = $state('');
  let activeFilter = $state('all');

  const filters = [
    { key: 'all', label: 'Tümü' },
    { key: 'kurdi', label: '🇹🇷 Kürtçe' },
    { key: 'roman', label: '📖 Roman' },
    { key: 'hikaye', label: '📝 Hikâye' },
    { key: 'siir', label: '🎭 Şiir' },
  ];

  onMount(load);

  async function load() {
    loading = true;
    try {
      let q = supabase
        .from('serials')
        .select('id,title,description,cover_url,author_name,author_uid,chapter_count,category,language,created_at')
        .order('created_at', { ascending: false })
        .limit(60);

      if (activeFilter !== 'all') q = q.eq('category', activeFilter);

      const { data } = await q;
      serials = data ?? [];
    } catch(e) { console.error(e); }
    finally { loading = false; }
  }

  $effect(() => { activeFilter; load(); });

  const filtered = $derived(
    search.trim()
      ? serials.filter(s =>
          s.title.toLowerCase().includes(search.toLowerCase()) ||
          s.author_name.toLowerCase().includes(search.toLowerCase())
        )
      : serials
  );
</script>

<PageTopBar title="Seriler" onBack={() => goto('/library')} />

<div class="page">
  <!-- Arama -->
  <div class="search-wrap">
    <input
      class="search-input"
      placeholder="Seri veya yazar ara…"
      bind:value={search}
    />
  </div>

  <!-- Filtre -->
  <div class="filter-row">
    {#each filters as f}
      <button
        class="filter-chip"
        class:active={activeFilter === f.key}
        onclick={() => activeFilter = f.key}
      >{f.label}</button>
    {/each}
  </div>

  {#if loading}
    <div class="grid">
      {#each Array(6) as _}<Skeleton height={200} />{/each}
    </div>
  {:else if filtered.length === 0}
    <EmptyState icon="📚" title="Seri bulunamadı" subtitle="Farklı bir arama dene." />
  {:else}
    <div class="grid">
      {#each filtered as s (s.id)}
        <!-- svelte-ignore a11y_click_events_have_key_events -->
        <div
          class="serial-card"
          onclick={() => goto('/library/book/' + s.id)}
          role="button"
          tabindex="0"
        >
          {#if s.cover_url}
            <img src={s.cover_url} alt={s.title} class="serial-cover" />
          {:else}
            <div class="serial-cover-placeholder">📖</div>
          {/if}
          <div class="serial-info">
            {#if s.category}
              <span class="serial-cat">{s.category}</span>
            {/if}
            <h3 class="serial-title">{s.title}</h3>
            <p class="serial-author">✍️ {s.author_name}</p>
            <p class="serial-ch">{s.chapter_count ?? 0} bölüm</p>
            {#if s.description}
              <p class="serial-desc">{s.description.slice(0, 80)}{s.description.length > 80 ? '…' : ''}</p>
            {/if}
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .page { max-width: 700px; margin: 0 auto; padding: 12px; }

  .search-wrap { margin-bottom: 10px; }
  .search-input {
    width: 100%; padding: 10px 14px; border: 1.5px solid var(--divider);
    border-radius: 12px; font-size: 14px; background: var(--surface-var);
    color: var(--on-bg); outline: none; font-family: inherit; box-sizing: border-box;
  }
  .search-input:focus { border-color: var(--primary); }

  .filter-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
  .filter-chip {
    padding: 5px 14px; border-radius: 20px; border: 1.5px solid var(--divider);
    background: none; font-size: 13px; cursor: pointer; font-family: inherit;
    color: var(--on-bg); transition: all .15s;
  }
  .filter-chip.active {
    background: var(--primary); color: #fff; border-color: var(--primary);
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }
  .serial-card {
    background: var(--surface); border-radius: 14px; overflow: hidden;
    box-shadow: 0 2px 8px rgba(0,0,0,.07); cursor: pointer;
    transition: transform .15s, box-shadow .15s;
  }
  .serial-card:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(0,0,0,.12); }
  .serial-cover {
    width: 100%; aspect-ratio: 2/3; object-fit: cover;
  }
  .serial-cover-placeholder {
    width: 100%; aspect-ratio: 2/3; display: flex; align-items: center;
    justify-content: center; font-size: 40px;
    background: color-mix(in srgb, var(--primary) 8%, transparent);
  }
  .serial-info { padding: 10px; }
  .serial-cat { font-size: 10px; font-weight: 700; color: var(--primary); text-transform: uppercase; }
  .serial-title { font-size: 13px; font-weight: 700; margin: 3px 0 2px; line-height: 1.3; }
  .serial-author { font-size: 11px; color: var(--muted); margin: 0 0 2px; }
  .serial-ch { font-size: 11px; color: var(--primary); font-weight: 600; margin: 0 0 4px; }
  .serial-desc { font-size: 11px; color: var(--muted); margin: 0; line-height: 1.4; }
</style>
