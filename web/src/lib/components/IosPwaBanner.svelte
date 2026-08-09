<script lang="ts">
  import { onMount } from 'svelte';

  let show = $state(false);

  onMount(() => {
    const isIos = /iPhone|iPad|iPod/i.test(navigator.userAgent);
    const isStandalone = (window.navigator as any).standalone === true;
    const dismissed = localStorage.getItem('hf_ios_pwa_dismissed') === '1';
    // Sadece iOS Safari'de ve henüz kurulmamışsa göster
    show = isIos && !isStandalone && !dismissed;
  });

  function dismiss() {
    show = false;
    localStorage.setItem('hf_ios_pwa_dismissed', '1');
  }
</script>

{#if show}
<div class="ios-banner">
  <img src="/apple-touch-icon.png" alt="Heftreng" class="icon" />
  <div class="text">
    <strong>Heftreng'i Kur</strong>
    <span>
      Safari'de
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle;margin:0 2px">
        <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/>
        <polyline points="16 6 12 2 8 6"/>
        <line x1="12" y1="2" x2="12" y2="15"/>
      </svg>
      → <b>Ana Ekrana Ekle</b>
    </span>
  </div>
  <button onclick={dismiss} class="close" aria-label="Kapat">✕</button>
</div>
{/if}

<style>
.ios-banner {
  position: fixed; bottom: 0; left: 0; right: 0; z-index: 9999;
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px 28px;
  background: var(--surface, #fff);
  border-top: 1px solid var(--divider, #e0e0e0);
  box-shadow: 0 -2px 16px rgba(0,0,0,.12);
  animation: slideUp .3s ease;
}
.icon { width: 44px; height: 44px; border-radius: 10px; flex-shrink: 0; }
.text { flex: 1; display: flex; flex-direction: column; gap: 3px; font-size: 13px; }
.text strong { font-size: 14px; color: var(--on-bg, #111); }
.text span { color: var(--muted, #666); line-height: 1.4; }
.close {
  background: none; border: none; font-size: 18px;
  color: var(--muted, #666); cursor: pointer; padding: 4px 8px;
  flex-shrink: 0;
}
@keyframes slideUp {
  from { transform: translateY(100%); }
  to   { transform: translateY(0); }
}
</style>
