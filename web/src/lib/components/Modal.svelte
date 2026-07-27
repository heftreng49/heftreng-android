<!-- Genel amaçlı modal — Android BottomSheet / AlertDialog karşılığı -->
<script lang="ts">
  import { onMount } from 'svelte';

  interface Props {
    open:       boolean;
    title?:     string;
    maxWidth?:  string;
    onclose?:   () => void;
    children?:  import('svelte').Snippet;
  }

  let {
    open     = $bindable(false),
    title    = '',
    maxWidth = '480px',
    onclose,
    children,
  }: Props = $props();

  function close() {
    open = false;
    onclose?.();
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') close();
  }

  onMount(() => {
    document.addEventListener('keydown', onKeydown);
    return () => document.removeEventListener('keydown', onKeydown);
  });
</script>

{#if open}
<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="overlay" onclick={close}>
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div
    class="modal"
    style:max-width={maxWidth}
    onclick={(e) => e.stopPropagation()}
    role="dialog"
    aria-modal="true"
    aria-label={title}
  >
    {#if title}
    <div class="modal-header">
      <span class="modal-title">{title}</span>
      <button class="close-btn" onclick={close} aria-label="Kapat">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    {/if}
    <div class="modal-body">
      {@render children?.()}
    </div>
  </div>
</div>
{/if}

<style>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.modal {
  background: var(--color-surface, #fff);
  border-radius: 18px;
  width: 100%;
  box-shadow: 0 8px 40px rgba(0,0,0,0.18);
  overflow: hidden;
  animation: pop 0.18s ease;
}
@keyframes pop {
  from { transform: scale(0.93); opacity: 0; }
  to   { transform: scale(1);    opacity: 1; }
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 12px;
  border-bottom: 1px solid rgba(0,0,0,0.07);
}
.modal-title {
  font-weight: 700;
  font-size: 1rem;
}
.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #888;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
}
.close-btn:hover { color: #333; }
.modal-body {
  padding: 16px 18px 20px;
}
</style>
