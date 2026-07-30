<script lang="ts">
  import { onMount }     from 'svelte';
  import { goto }        from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import { supabase }    from '$lib/supabase/config';
  import { fetchFollowers, fetchFollowing, toggleFollow } from '$lib/services/profile.service';
  import TabBar     from '$lib/components/TabBar.svelte';
  import Avatar     from '$lib/components/Avatar.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Skeleton   from '$lib/components/Skeleton.svelte';

  // Android: Takip Edilenler · Takipçiler · Önerilen
  const tabs = ['Takip Edilenler', 'Takipçiler', 'Önerilen'];
  let activeTab  = $state(2);   // Android initialTab = 2 (Önerilen)
  let following  = $state<any[]>([]);
  let followers  = $state<any[]>([]);
  let suggested  = $state<any[]>([]);
  let loading    = $state(true);
  let followingMe = $state(new Set<string>());

  onMount(async () => {
    if (!$currentUser) { goto('/login'); return; }
    await Promise.all([loadFollowing(), loadFollowers(), loadSuggested()]);
    loading = false;
  });

  async function loadFollowing() {
    const res = await fetchFollowing($currentUser!.uid);
    following = res;
    res.forEach(u => followingMe.add(u.uid));
  }
  async function loadFollowers() {
    const res = await fetchFollowers($currentUser!.uid);
    followers = res;
  }
  async function loadSuggested() {
    const { data } = await supabase.from('users')
      .select('uid,display_name,username,photo_url,followers_count')
      .neq('uid', $currentUser!.uid)
      .order('followers_count', { ascending: false })
      .limit(20);
    // Zaten takip edilenleri çıkar
    const followingIds = new Set(following.map(f => f.uid));
    suggested = (data ?? []).filter((u: any) => !followingIds.has(u.uid));
  }

  async function onFollow(uid: string, name: string, photo: string) {
    if (!$currentUser) return;
    const isF = followingMe.has(uid);
    if (isF) followingMe.delete(uid); else followingMe.add(uid);
    followingMe = new Set(followingMe); // reaktif update
    try {
      await toggleFollow(
        $currentUser.uid, $currentUser.displayName ?? '', $currentUser.photoURL ?? '',
        uid, name, photo, isF,
      );
    } catch { if (isF) followingMe.add(uid); else followingMe.delete(uid); followingMe = new Set(followingMe); }
  }

  const currentList = $derived(
    activeTab === 0 ? following : activeTab === 1 ? followers : suggested
  );
</script>

<svelte:head><title>Kişiler — Heftreng</title></svelte:head>

<div class="people-page">
  <div class="people-topbar"><h2>Kişiler</h2></div>

  <TabBar {tabs} bind:active={activeTab} stickyTop={52} />

  {#if loading}
    <div class="user-list">
      {#each {length:6} as _}
        <div class="sk-row">
          <Skeleton width="44px" height="44px" radius="50%" />
          <div style="flex:1"><Skeleton width="45%" height="13px"/><Skeleton width="60%" height="11px"/></div>
        </div>
      {/each}
    </div>
  {:else if currentList.length === 0}
    <EmptyState icon="👥"
      message={activeTab === 0 ? 'Henüz kimseyi takip etmiyorsun.' : activeTab === 1 ? 'Henüz takipçin yok.' : 'Öneri bulunamadı.'}
    />
  {:else}
    <ul class="user-list">
      {#each currentList as u (u.uid)}
        <li class="user-row">
          <a href="/profile/{u.uid}" class="user-info">
            <Avatar src={u.photo ?? u.photo_url ?? ''} name={u.name ?? u.display_name ?? ''} size={44} />
            <div class="user-meta">
              <span class="user-name">{u.name ?? u.display_name ?? ''}</span>
              {#if u.username}<span class="user-username">@{u.username}</span>{/if}
            </div>
          </a>
          {#if u.uid !== $currentUser?.uid}
            <button
              class="follow-btn"
              class:following={followingMe.has(u.uid)}
              onclick={() => onFollow(u.uid, u.name ?? u.display_name ?? '', u.photo ?? u.photo_url ?? '')}
            >
              {followingMe.has(u.uid) ? 'Takip Ediliyor' : 'Takip Et'}
            </button>
          {/if}
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
.people-page { min-height: 100dvh; }
.people-topbar { padding: 14px 16px 8px; }
.people-topbar h2 { margin: 0; font-size: 1.1rem; font-weight: 700; }
.user-list { list-style: none; padding: 0; margin: 0; }
.user-row {
  display: flex; align-items: center; gap: 12px;
  padding: 11px 16px; border-bottom: 1px solid var(--divider);
}
.user-info { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; text-decoration: none; color: inherit; }
.user-meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.user-name { font-size: 0.9rem; font-weight: 600; color: var(--on-bg); }
.user-username { font-size: 0.75rem; color: var(--muted); }
.follow-btn {
  padding: 6px 14px; border-radius: 20px; font-size: 0.78rem; font-weight: 700;
  border: 1.5px solid var(--primary); background: var(--primary); color: #fff;
  cursor: pointer; flex-shrink: 0; font-family: inherit; transition: background 0.15s;
}
.follow-btn.following { background: transparent; color: var(--primary); }
.sk-row { display: flex; gap: 12px; padding: 12px 16px; align-items: center; }
</style>
