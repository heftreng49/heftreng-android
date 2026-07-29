<!--
  PullToRefresh — slot tabanlı pull-to-refresh sarıcı.
  3 sayfada kopyalanmış dokunma mantığının tek kaynağı.

  Kullanım:
    <PullToRefresh onRefresh={loadAll}>
      <!-- sayfa içeriği buraya -->
    </PullToRefresh>

    <!-- refreshing state'ini dışarıdan takip etmek için: -->
    <PullToRefresh onRefresh={loadAll} bind:refreshing>
      ...
    </PullToRefresh>
-->
<script lang="ts">
  import { type Snippet } from 'svelte';

  interface Props {
    onRefresh:   () => Promise<void>;
    threshold?:  number;    // çekme eşiği px, default 72
    disabled?:   boolean;
    refreshing?: boolean;   // bind ile dışarıdan takip
    children:    Snippet;
  }

  let {
    onRefresh,
    threshold  = 72,
    disabled   = false,
    refreshing = $bindable(false),
    children,
  }: Props = $props();

  let pullDist   = $state(0);
  let touchStart = 0;

  function onTouchStart(e: TouchEvent) {
    if (disabled) return;
    touchStart = e.touches[0].clientY;
  }

  function onTouchMove(e: TouchEvent) {
    if (disabled || refreshing) return;
    if (document.documentElement.scrollTop > 0) return;
    const dy = e.touches[0].clientY - touchStart;
    if (dy > 0) pullDist = Math.min(dy * 0.5, threshold + 20);
  }

  async function onTouchEnd() {
    if (pullDist >= threshold) {
      refreshing = true;
      pullDist   = 0;
      try { await onRefresh(); } finally { refreshing = false; }
    } else {
      pullDist = 0;
    }
  }
</script>

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<div
  role="main"
  ontouchstart={onTouchStart}
  ontouchmove={onTouchMove}
  ontouchend={onTouchEnd}
>
  {#if pullDist > 10 || refreshing}
    <div
      class="ptr-bar"
      style="height:{refreshing ? 48 : pullDist}px; opacity:{refreshing ? 1 : pullDist / threshold}"
    >
      <div class="ptr-spin" class:spinning={refreshing}></div>
    </div>
  {/if}

  {@render children()}
</div>

<style>
.ptr-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: height 0.2s, opacity 0.2s;
}
.ptr-spin {
  width: 22px;
  height: 22px;
  border: 2.5px solid color-mix(in srgb, var(--primary) 30%, transparent);
  border-top-color: var(--primary);
  border-radius: 50%;
}
.ptr-spin.spinning {
  animation: ptr-rotate 0.7s linear infinite;
}
@keyframes ptr-rotate {
  to { transform: rotate(360deg); }
}
</style>
