<script lang="ts">
  import { onMount }        from 'svelte';
  import { page }           from '$app/stores';
  import { goto }           from '$app/navigation';
  import { fetchGrammarRules, type GrammarRule } from '$lib/services/kurdi.service';

  const id    = $derived($page.params.id);
  let rule    = $state<GrammarRule | null>(null);
  let loading = $state(true);

  onMount(async () => {
    const rules = await fetchGrammarRules();
    rule    = rules.find(r => r.id === id) ?? null;
    loading = false;
  });
</script>

<svelte:head><title>{rule?.titleTr ?? 'Dilbilgisi'} — Kurdî</title></svelte:head>

<div class="grammar-page">
  <div class="grammar-topbar">
    <button class="back-btn" onclick={() => goto('/kurdi?tab=grammar')}>←</button>
    <span class="grammar-topbar-title">Dilbilgisi</span>
  </div>

  {#if loading}
    <div class="grammar-loading">Yükleniyor…</div>
  {:else if !rule}
    <div class="grammar-loading">Kural bulunamadı.</div>
  {:else}
    <div class="grammar-body">
      <h1 class="grammar-title">{rule.titleTr || rule.title}</h1>
      {#if rule.title && rule.titleTr}
        <p class="grammar-title-ku">{rule.title}</p>
      {/if}
      <div class="grammar-divider"></div>
      <!-- Türkçe içerik -->
      {#if rule.contentTr}
        <div class="grammar-content">{@html rule.contentTr.replace(/\n/g,'<br>')}</div>
      {/if}
      <!-- Kürtçe içerik -->
      {#if rule.content && rule.content !== rule.contentTr}
        <div class="grammar-section-label">Kurdî</div>
        <div class="grammar-content ku">{@html rule.content.replace(/\n/g,'<br>')}</div>
      {/if}
    </div>
  {/if}
</div>

<style>
.grammar-page { min-height:100dvh; padding-bottom:80px; }
.grammar-topbar { display:flex;align-items:center;gap:10px;padding:10px 14px;background:var(--surface);border-bottom:1px solid var(--divider);position:sticky;top:0;z-index:10; }
.back-btn { background:none;border:none;cursor:pointer;font-size:1.3rem;color:var(--on-bg);padding:4px; }
.grammar-topbar-title { font-size:.95rem;font-weight:700; }
.grammar-loading { display:flex;align-items:center;justify-content:center;height:160px;color:var(--muted); }
.grammar-body { padding:20px 16px; }
.grammar-title { font-size:1.3rem;font-weight:800;margin:0 0 4px;color:var(--on-bg); }
.grammar-title-ku { font-size:1rem;color:var(--primary);margin:0 0 12px; }
.grammar-divider { height:2px;background:var(--divider);margin-bottom:16px;border-radius:1px; }
.grammar-section-label { font-size:.72rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--muted);margin:20px 0 8px; }
.grammar-content { font-size:.92rem;line-height:1.8;color:var(--on-bg); }
.grammar-content.ku { color:var(--primary); }
.grammar-content :global(strong) { font-weight:700; }
</style>
