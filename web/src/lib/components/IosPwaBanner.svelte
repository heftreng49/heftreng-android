<script lang="ts">
  import { onMount } from 'svelte';

  let show = $state(false);

  onMount(() => {
    const isIos = /iPhone|iPad|iPod/i.test(navigator.userAgent);
    const isStandalone = (window.navigator as any).standalone === true;
    const dismissed = localStorage.getItem('hf_ios_pwa_dismissed') === '1';
    // iOS Safari'de ve henüz kurulmamışsa göster
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
    <span>Safari'de <b>Paylaş</b> → <b>Ana Ekrana Ekle</b></span>
  </div>
  <button onclick={dismiss} class="close" aria-label="Kapat">✕</button>
</div>
{/if}

<style>
.ios-banner {
  position: fixed; bottom: 0; left: 0; right: 0; z-index: 9999;
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px 20px;
  background: var(--surface, #fff);
  border-top: 1px solid var(--divider, #e0e0e0);
  box-shadow: 0 -2px 12px rgba(0,0,0,.1);
  animation: slideUp .3s ease;
}
.icon { width: 44px; height: 44px; border-radius: 10px; flex-shrink: 0; }
.text { flex: 1; display: flex; flex-direction: column; gap: 2px; font-size: 13px; }
.text strong { font-size: 14px; color: var(--on-bg, #111); }
.text span { color: var(--muted, #666); }
.close {
  background: none; border: none; font-size: 18px;
  color: var(--muted, #666); cursor: pointer; padding: 4px 8px;
}
@keyframes slideUp {
  from { transform: translateY(100%); }
  to   { transform: translateY(0); }
}
</style>
