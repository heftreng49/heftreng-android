<!-- Android MessageDetailScreen mesaj baloncuğu karşılığı -->
<script lang="ts">
  import { ago } from '$lib/utils/time';

  interface Props {
    msg:        any;
    isMine:     boolean;
    onDelete?:  (msg: any) => void;
  }
  let { msg, isMine, onDelete }: Props = $props();
  let showMenu = $state(false);
</script>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="bubble-row" class:mine={isMine} onclick={() => isMine && (showMenu = !showMenu)}>
  {#if !isMine}
    {#if msg.photoURL}
      <img src={msg.photoURL} alt={msg.name} class="bubble-av" />
    {:else}
      <div class="bubble-av-ph">{(msg.name?.[0] ?? '?').toUpperCase()}</div>
    {/if}
  {/if}
  <div class="bubble-wrap">
    {#if !isMine}
      <span class="bubble-sender">{msg.name}</span>
    {/if}
    {#if msg.deleted}
      <div class="bubble bubble-deleted">🚫 Mesaj silindi</div>
    {:else}
      <div class="bubble" class:mine={isMine}>{msg.text}</div>
    {/if}
    <span class="bubble-time">{ago(msg.ts)}</span>
    {#if showMenu && isMine && !msg.deleted}
      <!-- svelte-ignore a11y_click_events_have_key_events -->
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div class="bubble-menu" onclick={e => e.stopPropagation()}>
        <button onclick={() => { onDelete?.(msg); showMenu = false; }}>Sil</button>
      </div>
    {/if}
  </div>
</div>

<style>
.bubble-row {
  display: flex; align-items: flex-end; gap: 8px;
  margin-bottom: 8px; padding: 0 12px;
}
.bubble-row.mine { flex-direction: row-reverse; }
.bubble-av { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.bubble-av-ph {
  width: 28px; height: 28px; border-radius: 50%;
  background: color-mix(in srgb, var(--primary) 20%, transparent);
  color: var(--primary); display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 700; flex-shrink: 0;
}
.bubble-wrap { max-width: 72%; display: flex; flex-direction: column; gap: 3px; position: relative; }
.bubble-sender { font-size: 0.7rem; color: var(--muted); padding: 0 4px; }
.bubble {
  padding: 9px 13px; border-radius: 18px 18px 18px 4px;
  background: var(--surface-var); color: var(--on-bg);
  font-size: 0.88rem; line-height: 1.5; word-break: break-word;
}
.bubble.mine { background: var(--primary); color: #fff; border-radius: 18px 18px 4px 18px; }
.bubble-deleted { color: var(--muted); font-style: italic; background: transparent; border: 1px solid var(--divider); }
.bubble-time { font-size: 0.68rem; color: var(--muted); padding: 0 4px; }
.bubble-menu {
  position: absolute; bottom: calc(100% + 4px); right: 0;
  background: var(--surface); border: 1px solid var(--divider);
  border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,.12);
  z-index: 10;
}
.bubble-menu button {
  display: block; width: 100%; padding: 10px 20px;
  background: none; border: none; cursor: pointer;
  color: #ef4444; font-size: 0.88rem; font-family: inherit;
}
</style>
