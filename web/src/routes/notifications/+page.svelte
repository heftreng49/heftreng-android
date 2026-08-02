<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { goto } from '$app/navigation';
  import { currentUser, authLoading } from '$lib/stores/auth';
  import { requireAuth } from '$lib/utils/auth.guard';
  import { listenNotifications, markAsRead, markAllRead } from '$lib/services/notification.service';
  import NotifItem  from '$lib/components/NotifItem.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Skeleton   from '$lib/components/Skeleton.svelte';

  let notifs   = $state<any[]>([]);
  let loading  = $state(true);
  let unsub: (() => void) | null = null;

  // Android: TODAY / WEEK / OLDER grupları
  const now = Date.now();
  const DAY  = 86400_000;
  const WEEK = 7 * DAY;

  const toMs = (ts: any) => ts?.seconds ? ts.seconds * 1000 : Number(ts ?? 0);

  const groups = $derived((() => {
    const today: any[] = [], week: any[] = [], older: any[] = [];
    notifs.forEach(n => {
      const diff = now - toMs(n.ts);
      if (diff < DAY)  today.push(n);
      else if (diff < WEEK) week.push(n);
      else older.push(n);
    });
    return { today, week, older };
  })());

  const unread = $derived(notifs.filter(n => !n.read).length);

  onMount(async () => {
    await requireAuth();
    const uid = $currentUser?.uid;
    if (!uid) return;
    unsub = listenNotifications(uid, (data) => {
      notifs = data;
      loading = false;
    });
    // 2 sn sonra hepsini okundu say
    setTimeout(() => { if (uid) markAllRead(uid); }, 2000);
  });

  onDestroy(() => unsub?.());

  async function handleClick(notif: any) {
    if (!notif.read && $currentUser) {
      markAsRead($currentUser.uid, notif.id);
      notifs = notifs.map(n => n.id === notif.id ? { ...n, read: true } : n);
    }
    if (notif.url) goto(notif.url);
    else if (notif.feedId) goto(`/post/${notif.feedId}`);
    else if (notif.fromUid && (notif.type === 'follow' || notif.type === 'follow_request'))
      goto(`/profile/${notif.fromUid}`);
  }
</script>

<svelte:head><title>Bildirimler — Heftreng</title></svelte:head>

<div class="notif-page">
  <div class="notif-topbar">
    <div class="notif-title-wrap">
      <h2>Bildirimler</h2>
      {#if unread > 0}
        <span class="notif-unread-sub">{unread} okunmamış</span>
      {/if}
    </div>
    {#if unread > 0}
      <button class="mark-all-btn" onclick={() => $currentUser && markAllRead($currentUser.uid)}>
        Tümünü oku
      </button>
    {/if}
  </div>

  {#if loading}
    <div class="notif-list">
      {#each {length: 6} as _}
        <div class="notif-skeleton">
          <Skeleton width="36px" height="36px" radius="50%" />
          <div style="flex:1">
            <Skeleton width="80%" height="13px" />
            <Skeleton width="40%" height="11px" />
          </div>
        </div>
      {/each}
    </div>
  {:else if notifs.length === 0}
    <EmptyState icon="🔔" message="Henüz bildirim yok." />
  {:else}
    <div class="notif-list">
      {#if groups.today.length > 0}
        <p class="group-label">Bugün</p>
        {#each groups.today as n (n.id)}
          <NotifItem notif={n} onClick={handleClick} />
        {/each}
      {/if}
      {#if groups.week.length > 0}
        <p class="group-label">Bu Hafta</p>
        {#each groups.week as n (n.id)}
          <NotifItem notif={n} onClick={handleClick} />
        {/each}
      {/if}
      {#if groups.older.length > 0}
        <p class="group-label">Daha Eski</p>
        {#each groups.older as n (n.id)}
          <NotifItem notif={n} onClick={handleClick} />
        {/each}
      {/if}
    </div>
  {/if}
</div>

<style>
.notif-page { min-height: 100dvh; }
.notif-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 10px;
}
.notif-title-wrap { display: flex; flex-direction: column; gap: 1px; }
.notif-topbar h2 { margin: 0; font-size: 1.1rem; font-weight: 700; }
.notif-unread-sub { font-size: 0.72rem; color: #F59E0B; font-weight: 600; }
.mark-all-btn {
  background: none; border: none; cursor: pointer;
  color: var(--primary); font-size: 0.8rem; font-weight: 600;
}
.notif-list { display: flex; flex-direction: column; }
.group-label {
  font-size: 0.72rem; font-weight: 700; color: var(--muted);
  padding: 10px 16px 4px; margin: 0;
  text-transform: uppercase; letter-spacing: 0.05em;
}
.notif-skeleton { display: flex; gap: 12px; padding: 13px 16px; align-items: center; }
</style>
