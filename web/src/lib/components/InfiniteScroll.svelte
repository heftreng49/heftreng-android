<!-- Android LazyColumn + paging karşılığı — Intersection Observer ile -->
<script lang="ts">
  import { onMount, onDestroy } from 'svelte';

  interface Props {
    /** Yüklenecek daha fazla içerik var mı */
    hasMore:   boolean;
    /** Yükleme devam ediyor mu */
    loading:   boolean;
    /** Tetiklendiğinde çağrılır */
    onLoadMore: () => void;
    /** Alt kenar ne kadar yaklaşınca tetiklensin (px) */
    threshold?: number;
  }

  let {
    hasMore,
    loading,
    onLoadMore,
    threshold = 200,
  }: Props = $props();

  let sentinel: HTMLDivElement;
  let observer: IntersectionObserver;

  onMount(() => {
    observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && hasMore && !loading) {
          onLoadMore();
        }
      },
      { rootMargin: `${threshold}px` }
    );
    if (sentinel) observer.observe(sentinel);
  });

  onDestroy(() => observer?.disconnect());

  // hasMore veya loading değişince yeniden değerlendir
  $effect(() => {
    if (sentinel && observer) {
      observer.unobserve(sentinel);
      if (hasMore && !loading) observer.observe(sentinel);
    }
  });
</script>

<div bind:this={sentinel} class="sentinel" aria-hidden="true"></div>

{#if loading}
  <div class="load-indicator">
    <div class="spinner"></div>
  </div>
{/if}

{#if !hasMore && !loading}
  <p class="end-msg">— Tüm gönderiler yüklendi —</p>
{/if}

<style>
.sentinel { height: 1px; }
.load-indicator {
  display: flex;
  justify-content: center;
  padding: 24px;
}
.spinner {
  width: 28px; height: 28px;
  border: 3px solid #eee;
  border-top-color: #888;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.end-msg {
  text-align: center;
  color: #bbb;
  font-size: 0.8rem;
  padding: 16px 0 24px;
  margin: 0;
}
</style>
