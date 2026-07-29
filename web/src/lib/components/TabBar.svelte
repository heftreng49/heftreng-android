<!--
  TabBar — sticky sekme çubuğu + URL sync + animasyonlu underline.
  4 sayfada kopyalanmış tab kalıbının tek kaynağı.

  Kullanım:
    <TabBar tabs={['Alıntılar','İncelemeler']} bind:active={activeTab} />
    <TabBar tabs={['Kitaplar','Alıntılar']} bind:active={tab}
            counts={[books.length, quotes.length]} urlParam="tab" stickyTop={52} />
-->
<script lang="ts">
  import { page }         from '$app/stores';
  import { replaceState } from '$app/navigation';
  import { onMount }      from 'svelte';

  interface Props {
    tabs:       string[];
    active:     number;
    counts?:    number[];       // sekme yanında sayı badge
    urlParam?:  string;         // default: 'tab'
    stickyTop?: number;         // default: 52  (global header yüksekliği)
  }

  let {
    tabs,
    active    = $bindable(0),
    counts    = [],
    urlParam  = 'tab',
    stickyTop = 52,
  }: Props = $props();

  // İlk yüklemede URL'den oku
  onMount(() => {
    const t = $page.url.searchParams.get(urlParam);
    if (t !== null) {
      const idx = parseInt(t);
      if (!isNaN(idx) && idx >= 0 && idx < tabs.length) active = idx;
    }
  });

  function select(i: number) {
    active = i;
    // replaceState — history'e ekleme, geri tuşu sekme değiştirmez
    const url = new URL($page.url);
    url.searchParams.set(urlParam, String(i));
    replaceState(url, {});
  }
</script>

<div class="tabs" style="top:{stickyTop}px" role="tablist">
  {#each tabs as tab, i}
    <button
      class="tab"
      class:active={active === i}
      onclick={() => select(i)}
      role="tab"
      aria-selected={active === i}
    >
      {tab}
      {#if counts[i] !== undefined && counts[i] > 0}
        <span class="tab-badge">{counts[i]}</span>
      {/if}
    </button>
  {/each}

  <!-- Kayan underline — Android TabRow indicator birebir karşılığı -->
  <div
    class="tab-line"
    style="
      width: {100 / tabs.length}%;
      transform: translateX({active * 100}%);
    "
  ></div>
</div>

<style>
.tabs {
  position: sticky;
  z-index: 9;
  display: flex;
  background: var(--surface);
  border-bottom: 1px solid var(--divider);
  overflow: hidden;
}
.tab {
  flex: 1;
  padding: 12px 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  background: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  transition: color 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
.tab.active {
  color: var(--on-bg);
  font-weight: 700;
}
.tab-badge {
  font-size: 11px;
  background: var(--surface-var);
  border-radius: 99px;
  padding: 1px 6px;
  color: var(--muted);
}
/* Kayan underline */
.tab-line {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2.5px;
  background: var(--primary);
  border-radius: 2px 2px 0 0;
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
}
</style>
