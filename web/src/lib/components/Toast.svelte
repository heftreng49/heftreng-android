<script lang="ts">
  // ui.store.ts'deki showToast()/toastMsg daha önce yazılmıştı ama hiçbir
  // yerde render edilmiyordu — showToast() çağrıları görünürde hiçbir şey
  // yapmıyordu. Bu bileşen o store'u tüketir; +layout.svelte'e bir kez
  // eklenir, tüm uygulamada showToast(msg) çağrısı buradan görünür olur.
  import { toastMsg } from '$lib/stores/ui.store';
</script>

{#if $toastMsg}
  <div class="hf-toast" role="status">{$toastMsg}</div>
{/if}

<style>
  .hf-toast {
    position: fixed;
    left: 50%;
    bottom: calc(env(safe-area-inset-bottom, 0px) + 76px);
    transform: translateX(-50%);
    background: var(--surface-var, #1f1f1f);
    color: var(--text, #fff);
    border: 1px solid var(--divider, rgba(255,255,255,.12));
    padding: 10px 18px;
    border-radius: 24px;
    font-size: 13.5px;
    font-weight: 600;
    box-shadow: 0 6px 20px rgba(0,0,0,.25);
    z-index: 9999;
    max-width: min(88vw, 420px);
    text-align: center;
    animation: hf-toast-in .18s ease-out;
    pointer-events: none;
  }
  @keyframes hf-toast-in {
    from { opacity: 0; transform: translateX(-50%) translateY(8px); }
    to   { opacity: 1; transform: translateX(-50%) translateY(0); }
  }
</style>
