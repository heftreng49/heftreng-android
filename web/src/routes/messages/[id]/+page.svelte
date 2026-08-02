<script lang="ts">
  import { onMount, onDestroy, tick } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { currentUser, authLoading } from '$lib/stores/auth';
  import { requireAuth } from '$lib/utils/auth.guard';
  import {
    listenMessages, listenConversations,
    sendMessage, deleteMessage, markConversationRead, setPresence,
  } from '$lib/services/message.service';
  import MessageBubble from '$lib/components/MessageBubble.svelte';
  import EmptyState    from '$lib/components/EmptyState.svelte';

  const convId  = $derived($page.params.id);

  let msgs      = $state<any[]>([]);
  let conv      = $state<any>(null);
  let text      = $state('');
  let sending   = $state(false);
  let loading   = $state(true);
  let msgEnd = $state<HTMLDivElement | undefined>(undefined);

  let unsubMsgs: (() => void) | null = null;
  let unsubConvs: (() => void) | null = null;

  onMount(async () => {
    await requireAuth();
    const uid = $currentUser?.uid;
    if (!uid) return;
    setPresence(uid, true);
    // Konuşma bilgisini al
    unsubConvs = listenConversations(uid, convs => {
      conv = convs.find(c => c.id === convId) ?? null;
    });
    // Mesajları dinle
    unsubMsgs = listenMessages(convId, data => {
      msgs = data; loading = false;
      tick().then(() => msgEnd?.scrollIntoView({ behavior: 'smooth' }));
    });
    markConversationRead(convId, uid);
  });

  onDestroy(() => {
    unsubMsgs?.(); unsubConvs?.();
    if ($currentUser?.uid) setPresence($currentUser.uid, false);
  });

  async function submit() {
    const uid = $currentUser?.uid;
    if (!uid || !text.trim() || sending) return;
    sending = true;
    try {
      await sendMessage(convId, uid, $currentUser.displayName ?? '', $currentUser.photoURL ?? '', text.trim());
      text = '';
      tick().then(() => msgEnd?.scrollIntoView({ behavior: 'smooth' }));
    } finally { sending = false; }
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); }
  }

  async function handleDelete(msg: any) {
    if (msg.uid !== $currentUser?.uid) return;
    await deleteMessage(convId, msg.id);
  }
</script>

<svelte:head><title>{conv?.otherName ?? 'Mesaj'} — Heftreng</title></svelte:head>

<div class="chat-page">
  <!-- Header -->
  <div class="chat-header">
    <button class="back-btn" onclick={() => goto('/messages')}>←</button>
    {#if conv?.otherPhoto}
      <img src={conv.otherPhoto} alt={conv.otherName} class="chat-av" />
    {:else}
      <div class="chat-av-ph">{(conv?.otherName?.[0] ?? '?').toUpperCase()}</div>
    {/if}
    <div class="chat-header-info">
      <span class="chat-name">{conv?.otherName ?? '…'}</span>
      {#if conv?.otherOnline}<span class="chat-online">● Çevrimiçi</span>{/if}
    </div>
    {#if conv?.otherUid}
      <a href="/profile/{conv.otherUid}" class="profile-link">Profil</a>
    {/if}
  </div>

  <!-- Mesajlar -->
  <div class="chat-body">
    {#if loading}
      <div class="chat-loading">Yükleniyor…</div>
    {:else if msgs.length === 0}
      <EmptyState icon="💬" message="Henüz mesaj yok. İlk mesajı gönder!" />
    {:else}
      {#each msgs as msg (msg.id)}
        <MessageBubble
          {msg}
          isMine={msg.uid === $currentUser?.uid}
          onDelete={handleDelete}
        />
      {/each}
      <div bind:this={msgEnd}></div>
    {/if}
  </div>

  <!-- Giriş alanı -->
  <div class="chat-input-bar">
    <textarea
      bind:value={text}
      onkeydown={onKeydown}
      placeholder="Mesaj yaz…"
      rows="1"
      class="chat-textarea"
    ></textarea>
    <button class="send-btn" disabled={!text.trim() || sending} onclick={submit}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="20" height="20">
        <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
      </svg>
    </button>
  </div>
</div>

<style>
.chat-page { display: flex; flex-direction: column; height: calc(100dvh - 52px); }
.chat-header {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: var(--surface);
  border-bottom: 1px solid var(--divider);
  position: sticky; top: 52px; z-index: 10; flex-shrink: 0;
}
.back-btn { background: none; border: none; cursor: pointer; font-size: 1.3rem; color: var(--on-bg); padding: 4px; }
.chat-av { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.chat-av-ph {
  width: 36px; height: 36px; border-radius: 50%;
  background: color-mix(in srgb, var(--primary) 20%, transparent);
  color: var(--primary); display: flex; align-items: center; justify-content: center;
  font-weight: 700;
}
.chat-header-info { flex: 1; min-width: 0; }
.chat-name { font-weight: 700; font-size: 0.95rem; display: block; }
.chat-online { font-size: 0.72rem; color: #22c55e; }
.profile-link { font-size: 0.78rem; color: var(--primary); text-decoration: none; }
.chat-body { flex: 1; overflow-y: auto; padding: 12px 0 8px; }
.chat-loading { display: flex; align-items: center; justify-content: center; height: 120px; color: var(--muted); }
.chat-input-bar {
  display: flex; align-items: flex-end; gap: 10px;
  padding: 10px 14px; padding-bottom: max(10px, env(safe-area-inset-bottom));
  background: var(--surface); border-top: 1px solid var(--divider);
  flex-shrink: 0;
}
.chat-textarea {
  flex: 1; border: 1.5px solid var(--divider); background: var(--surface-var);
  border-radius: 20px; padding: 9px 14px; font-size: 0.9rem;
  font-family: inherit; color: var(--on-bg); outline: none;
  resize: none; max-height: 120px; line-height: 1.5;
}
.send-btn {
  width: 40px; height: 40px; border-radius: 50%; border: none;
  background: var(--primary); color: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.send-btn:disabled { opacity: 0.5; cursor: default; }
</style>
