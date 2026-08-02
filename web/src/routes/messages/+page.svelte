<script lang="ts">
  import { onMount, onDestroy }              from 'svelte';
  import { get }                             from 'svelte/store';
  import { currentUser }                     from '$lib/stores/auth';
  import { requireAuth }                     from '$lib/utils/auth.guard';
  import { listenConversations, setPresence } from '$lib/services/message.service';
  import { lang, strings as s }             from '$lib/i18n/strings';
  import ConversationRow                     from '$lib/components/ConversationRow.svelte';
  import Skeleton                            from '$lib/components/Skeleton.svelte';

  let convs      = $state<any[]>([]);
  let loading    = $state(true);
  let error      = $state('');
  let search     = $state('');

  const filtered = $derived(
    search.trim()
      ? convs.filter(c => c.otherName?.toLowerCase().includes(search.toLowerCase()))
      : convs
  );

  let unsub: (() => void) | null = null;

  onMount(async () => {
    const ok = await requireAuth();
    if (!ok) return;

    const uid = get(currentUser)?.uid;
    if (!uid) { loading = false; return; }

    setPresence(uid, true);

    // fetch yerine listener — Android gibi realtime, hata da yakalanır
    unsub = listenConversations(
      uid,
      (data) => {
        convs   = data;
        loading = false;
        error   = '';
      },
      (err) => {
        console.error('listenConversations hata:', err);
        error   = $lang === 'ku' ? 'Peyam neyên barkirin.' : 'Mesajlar yüklenemedi.';
        loading = false;
      },
    );
  });

  onDestroy(() => {
    unsub?.();
    const uid = get(currentUser)?.uid;
    if (uid) setPresence(uid, false);
  });
</script>

<svelte:head><title>{s.messages($lang)} — Heftreng</title></svelte:head>

<div class="msg-page">
  <div class="msg-topbar">
    <h2>{s.messages($lang)}</h2>
    <a href="/search" class="new-conv-btn" title={s.newConv($lang)}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="20" height="20">
        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
      </svg>
    </a>
  </div>

  <div class="msg-search">
    <input
      type="search"
      placeholder={s.msgSearchHint($lang)}
      bind:value={search}
      class="msg-search-input"
    />
  </div>

  {#if loading}
    <div class="conv-list">
      {#each {length: 5} as _}
        <div class="conv-skeleton">
          <Skeleton width="48px" height="48px" radius="50%" />
          <div style="flex:1;display:flex;flex-direction:column;gap:6px">
            <Skeleton width="40%" height="13px" />
            <Skeleton width="70%" height="11px" />
          </div>
        </div>
      {/each}
    </div>

  {:else if error}
    <div class="msg-error">
      <p>{error}</p>
      <button onclick={() => { loading = true; error = ''; unsub?.(); const uid = get(currentUser)?.uid; if (uid) unsub = listenConversations(uid, d => { convs=d; loading=false; error=''; }, e => { error='Hata'; loading=false; }); }} class="retry-btn">{s.retry($lang)}</button>
    </div>

  {:else if filtered.length === 0}
    <div class="msg-empty">
      <span class="empty-icon">✉️</span>
      <p>{s.msgEmpty($lang)}</p>
      <p class="empty-hint">{s.msgEmptyDesc($lang)}</p>
    </div>

  {:else}
    <div class="conv-list">
      {#each filtered as conv (conv.id)}
        <ConversationRow {conv} currentUid={get(currentUser)?.uid ?? ''} />
      {/each}
    </div>
  {/if}
</div>

<style>
.msg-page { min-height: 100dvh; background: var(--bg); }
.msg-topbar {
  padding: 14px 16px 8px;
  display: flex; align-items: center; justify-content: space-between;
}
.msg-topbar h2 { margin: 0; font-size: 1.1rem; font-weight: 700; color: var(--on-bg); }
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
.msg-empty {
  display: flex; flex-direction: column; align-items: center;
  padding: 60px 24px; gap: 8px; text-align: center;
}
.empty-icon { font-size: 3rem; }
.msg-empty p { margin: 0; color: var(--muted); font-size: 0.95rem; }
.empty-hint { font-size: 0.82rem !important; }
.msg-error {
  display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 40px 24px; color: var(--muted);
}
.retry-btn {
  padding: 8px 20px; border-radius: 20px; border: 1.5px solid var(--primary);
  background: none; color: var(--primary); font-weight: 600;
  cursor: pointer; font-family: inherit;
}
</style>
