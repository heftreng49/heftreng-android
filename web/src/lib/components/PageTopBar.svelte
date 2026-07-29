<script lang="ts">
  import { type Snippet } from 'svelte';

  interface Props {
    title:    string;
    loading?: boolean;
    right?:   Snippet;   // sağ aksiyon alanı (opsiyonel slot)
  }

  let { title = '', loading = false, right }: Props = $props();
</script>

<div class="top-bar">
  <button class="back-btn" onclick={() => history.back()} aria-label="Geri">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="22" height="22">
      <polyline points="15 18 9 12 15 6"/>
    </svg>
  </button>

  <span class="top-title">
    {#if !loading}{title}{/if}
  </span>

  {#if right}
    <div class="right-slot">
      {@render right()}
    </div>
  {:else}
    <div class="right-placeholder"></div>
  {/if}
</div>

<style>
.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px 8px;
  position: sticky;
  top: 0;
  background: var(--bg);
  z-index: 10;
  border-bottom: 1px solid var(--divider);
}

.back-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--surface-var);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;
}
.back-btn:hover { background: var(--divider); }

.top-title {
  flex: 1;
  font-size: 15px;
  font-weight: 700;
  color: var(--on-bg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.right-slot       { flex-shrink: 0; }
.right-placeholder { width: 36px; }
</style>
