<script lang="ts">
  import { shareContent } from '$lib/deeplink';

  interface Props {
    title: string;
    text?: string;
    path: string;         // örn. "/post/abc123"
    label?: string;
    compact?: boolean;    // sadece ikon, etiket yok
  }
  const { title, text, path, label = 'Paylaş', compact = false }: Props = $props();

  let status = $state<'idle' | 'copied'>('idle');

  async function handleShare() {
    const result = await shareContent({ title, text, path });
    if (result === 'copied') {
      status = 'copied';
      setTimeout(() => (status = 'idle'), 2000);
    }
  }
</script>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<button class="share-btn" class:compact onclick={handleShare} title={label}>
  {#if status === 'copied'}
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <polyline points="20 6 9 17 4 12"/>
    </svg>
    {#if !compact}<span>Kopyalandı</span>{/if}
  {:else}
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
      <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
      <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
    </svg>
    {#if !compact}<span>{label}</span>{/if}
  {/if}
</button>

<style>
  .share-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border: none;
    border-radius: 20px;
    background: transparent;
    color: var(--color-on-surface-variant, #888);
    cursor: pointer;
    font-size: 0.85rem;
    transition: background 0.15s, color 0.15s;
  }
  .share-btn:hover {
    background: var(--color-surface-variant, #f0f0f0);
    color: var(--color-on-surface, #222);
  }
  .share-btn.compact {
    padding: 6px;
  }
</style>
