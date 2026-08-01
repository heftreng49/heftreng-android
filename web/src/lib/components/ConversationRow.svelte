<!-- Android ConversationsScreen conv-item karşılığı -->
<script lang="ts">
  import { ago } from '$lib/utils/time';

  interface Props {
    conv: any;
    currentUid: string;
  }
  let { conv, currentUid }: Props = $props();

  const isMine = $derived(conv.lastSenderUid === currentUid);
</script>

<a href="/messages/{conv.id}" class="conv-row" class:unread={conv.unreadCount > 0}>
  <div class="conv-av-wrap">
    {#if conv.otherPhoto}
      <img src={conv.otherPhoto} alt={conv.otherName} class="conv-av" />
    {:else}
      <div class="conv-av-ph">{(conv.otherName?.[0] ?? '?').toUpperCase()}</div>
    {/if}
    {#if conv.otherOnline}
      <span class="online-dot"></span>
    {/if}
  </div>
  <div class="conv-info">
    <div class="conv-top">
      <span class="conv-name" class:unread-name={conv.unreadCount > 0}>{conv.otherName}</span>
      <span class="conv-time">{ago(conv.lastMsgTs)}</span>
    </div>
    <div class="conv-bottom">
      <p class="conv-last" class:unread-last={conv.unreadCount > 0}>{isMine ? 'Sen: ' : ''}{conv.lastMsg || '…'}</p>
      {#if conv.unreadCount > 0}
        <span class="unread-badge">{conv.unreadCount}</span>
      {/if}
    </div>
  </div>
</a>

<style>
.conv-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-bottom: 1px solid var(--divider);
  text-decoration: none; color: inherit; transition: background 0.12s;
  border-left: 3px solid transparent;
}
.conv-row:hover { background: var(--surface-var); }
.conv-row.unread {
  background: color-mix(in srgb, var(--primary) 5%, transparent);
  border-left-color: var(--primary);
}
.conv-av-wrap { position: relative; flex-shrink: 0; }
.conv-av { width: 48px; height: 48px; border-radius: 50%; object-fit: cover; }
.conv-av-ph {
  width: 48px; height: 48px; border-radius: 50%;
  background: color-mix(in srgb, var(--primary) 20%, transparent);
  color: var(--primary); display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 1.1rem;
}
.online-dot {
  position: absolute; bottom: 2px; right: 2px;
  width: 11px; height: 11px; border-radius: 50%;
  background: #22c55e; border: 2px solid var(--surface);
}
.conv-info { flex: 1; min-width: 0; }
.conv-top { display: flex; justify-content: space-between; align-items: baseline; gap: 6px; margin-bottom: 3px; }
.conv-name { font-weight: 700; font-size: 0.9rem; color: var(--on-bg); }
.conv-name.unread-name { font-weight: 800; }
.conv-time { font-size: 0.72rem; color: var(--muted); flex-shrink: 0; }
.conv-bottom { display: flex; justify-content: space-between; align-items: center; gap: 6px; }
.conv-last {
  font-size: 0.82rem; color: var(--muted); margin: 0;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1;
}
.conv-last.unread-last { color: var(--on-bg); font-weight: 600; }
.unread-badge {
  min-width: 18px; height: 18px; border-radius: 9px;
  background: var(--primary); color: #fff;
  font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  padding: 0 5px; flex-shrink: 0;
}
</style>
