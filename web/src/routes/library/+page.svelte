<!--
  library/+page.svelte — sadece tab yönetimi.
  Veri ve UI tamamen alt bileşenlerde (_Quotes, _Reviews, _Authors, _Books).
  Önceki: 694 satır → Şimdi: ~70 satır
-->
<script lang="ts">
  import TabBar        from '$lib/components/TabBar.svelte';
  import PullToRefresh from '$lib/components/PullToRefresh.svelte';
  import Quotes        from './_Quotes.svelte';
  import Reviews       from './_Reviews.svelte';
  import Authors       from './_Authors.svelte';
  import Books         from './_Books.svelte';

  const TABS = ['Alıntılar', 'İncelemeler', 'Yazarlar', 'Kitaplar'] as const;
  let activeTab = $state(0);

  // Her sekme bileşenine refresh tetikleyebilmek için referans
  let quotesRef  = $state<{ refresh: () => Promise<void> } | undefined>(undefined);
  let reviewsRef = $state<{ refresh: () => Promise<void> } | undefined>(undefined);
  let authorsRef = $state<{ refresh: () => Promise<void> } | undefined>(undefined);
  let booksRef   = $state<{ refresh: () => Promise<void> } | undefined>(undefined);

  async function handleRefresh() {
    await Promise.all([
      quotesRef?.refresh(),
      reviewsRef?.refresh(),
      authorsRef?.refresh(),
      booksRef?.refresh(),
    ]);
  }
</script>

<svelte:head><title>Kütüphane — Heftreng</title></svelte:head>

<div class="page">
  <header class="lib-header">
    <h1 class="lib-title">Kütüphane</h1>
  </header>

  <TabBar tabs={[...TABS]} bind:active={activeTab} urlParam="tab" stickyTop={52} />

  <PullToRefresh onRefresh={handleRefresh}>
    {#if activeTab === 0}
      <Quotes   bind:this={quotesRef}  />
    {:else if activeTab === 1}
      <Reviews  bind:this={reviewsRef} />
    {:else if activeTab === 2}
      <Authors  bind:this={authorsRef} />
    {:else}
      <Books    bind:this={booksRef}   />
    {/if}
  </PullToRefresh>
</div>

<style>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding-bottom: 80px;
  background: var(--bg);
  min-height: 100vh;
}
.lib-header {
  padding: 16px 16px 8px;
}
.lib-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--primary);
  margin: 0;
}
</style>
