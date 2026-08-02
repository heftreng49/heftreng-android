<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { goto } from '$app/navigation';
  import { currentUser, authLoading } from '$lib/stores/auth';
  import { requireAuth } from '$lib/utils/auth.guard';
  import { listenConversations, setPresence } from '$lib/services/message.service';
  import ConversationRow from '$lib/components/ConversationRow.svelte';
  import EmptyState      from '$lib/components/EmptyState.svelte';
  import Skeleton        from '$lib/components/Skeleton.svelte';

  let convs    = $state<any[]>([]);
  let loading  = $state(true);
  let search   = $state('');
  let unsub: (() => void) | null = null;

  const filtered = $derived(
    search.trim()
      ? convs.filter(c => c.otherName?.toLowerCase().includes(search.toLowerCase()))
      : convs
  );

  onMount(async () => {
    await requireAuth();
    const uid = $currentUser?.uid;
    if (!uid) return;
    setPresence(uid, true);

    // 5 saniye içinde cevap gelmezse loading'i kapat
    const timeout = setTimeout(() => { loading = false; }, 5000);

    unsub = listenConversations(uid, data => {
      clearTimeout(timeout);
      convs = data;
      loading = false;
    });
  });
  onDestroy(() => {
    unsub?.();
    if ($currentUser?.uid) setPresence($currentUser.uid, false);
  });
</script>

<svelte:head><title>Mesajlar — Heftreng</title></svelte:head>

<div class="msg-page">
  <div class="msg-topbar">
    <h2>Mesajlar</h2>
    <a href="/search" class="new-conv-btn" title="Yeni konuşma">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="20" height="20">
        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
      </svg>
    </a>
  </div>

  <!-- Arama -->
  <div class="msg-search">
    <input type="search" placeholder="Konuşma ara…" bind:value={search} class="msg-search-input" />
  </div>

  {#if loading}
    <div class="conv-list">
      {#each {length: 5} as _}
        <div class="conv-skeleton">
          <Skeleton width="48px" height="48px" radius="50%" />
          <div style="flex:1">
            <Skeleton width="40%" height="13px" />
            <Skeleton width="70%" height="11px" />
          </div>
        </div>
      {/each}
    </div>
  {:else if filtered.length === 0}
    <EmptyState icon="✉️" message="Henüz mesaj yok." hint="Bir kullanıcının profilinden mesaj gönder." />
  {:else}
    <div class="conv-list">
      {#each filtered as conv (conv.id)}
        <ConversationRow {conv} currentUid={$currentUser?.uid ?? ''} />
      {/each}
    </div>
  {/if}
</div>

<style>
.msg-page { min-height: 100dvh; }
.msg-topbar {
  padding: 14px 16px 8px;
  display: flex; align-items: center; justify-content: space-between;
}
.msg-topbar h2 { margin: 0; font-size: 1.1rem; font-weight: 700; }
.new-conv-btn {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: color-mix(in srgb, var(--primary) 12%, transparent);
  color: var(--primary); transition: background 0.15s;
}
.new-conv-btn:hover { background: color-mix(in srgb, var(--primary) 20%, transparent); }
.msg-search { padding: 0 14px 10px; }
.msg-search-input {
  width: 100%; border: none; background: var(--surface-var);
  border-radius: 20px; padding: 9px 14px;
  font-size: 0.88rem; font-family: inherit; color: var(--on-bg);
  outline: none; box-sizing: border-box;
}
.conv-list { display: flex; flex-direction: column; }
.conv-skeleton { display: flex; gap: 12px; padding: 12px 16px; align-items: center; }
</style>
