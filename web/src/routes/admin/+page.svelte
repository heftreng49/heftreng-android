<script lang="ts">
  import { onMount } from 'svelte';
  import { goto }    from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import {
    checkAdminPerms, fetchStats, fetchUsers, banUser, unbanUser,
    fetchReports, updateReportStatus, moderatePost, restorePost,
    fetchAppeals, resolveAppeal, sendPushNotification,
    fetchStaff, addStaff, removeStaff,
    ROLE_PERMS, type AdminPerms,
  } from '$lib/services/admin.service';
  import TabBar  from '$lib/components/TabBar.svelte';
  import Modal   from '$lib/components/Modal.svelte';
  import Avatar  from '$lib/components/Avatar.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';

  let perms    = $state<AdminPerms | null>(null);
  let loading  = $state(true);
  let notAdmin = $state(false);

  // Sekmeler — sadece izin verilen gösterilir
  const allTabs = [
    { key:'stats',   label:'İstatistik' },
    { key:'users',   label:'Kullanıcılar' },
    { key:'reports', label:'Şikayetler' },
    { key:'appeals', label:'İtirazlar' },
    { key:'push',    label:'Push' },
    { key:'staff',   label:'Personel' },
  ];
  let visibleTabs = $state<{key:string;label:string}[]>([]);
  let activeIdx   = $state(0);
  const activeKey = $derived(visibleTabs[activeIdx]?.key ?? '');

  // Stats
  let stats = $state<any>(null);
  // Users
  let users = $state<any[]>([]); let userSearch = $state(''); let userLoading = $state(false);
  let banTarget = $state<any|null>(null); let banReason = $state(''); let banSaving = $state(false);
  // Reports
  let reports = $state<any[]>([]); let reportsLoading = $state(false);
  let modTarget = $state<any|null>(null); let modStatus = $state('removed'); let modReason = $state('');
  // Appeals
  let appeals = $state<any[]>([]); let appealsLoading = $state(false);
  let appealTarget = $state<any|null>(null); let appealNote = $state('');
  // Push
  let pushTitle = $state(''); let pushBody = $state(''); let pushUrl = $state('');
  let pushResult = $state(''); let pushSending = $state(false);
  // Staff
  let staffList = $state<any[]>([]); let staffLoading = $state(false);
  let newStaffUid = $state(''); let newStaffTitle = $state(''); let newStaffRole = $state('moderator');
  let showAddStaff = $state(false);

  onMount(async () => {
    if (!$currentUser) { goto('/login'); return; }
    const p = await checkAdminPerms($currentUser.uid);
    if (!p) { notAdmin = true; loading = false; return; }
    perms = p;
    visibleTabs = allTabs.filter(t =>
      t.key === 'stats' ? p.can('stats') || p.can('users')
      : t.key === 'staff' ? p.can('staff')
      : p.can(t.key)
    );
    loading = false;
    loadTab(visibleTabs[0]?.key ?? '');
  });

  async function loadTab(key: string) {
    if (key === 'stats' && !stats) stats = await fetchStats();
    if (key === 'users' && !users.length) { userLoading = true; users = await fetchUsers(); userLoading = false; }
    if (key === 'reports' && !reports.length) { reportsLoading = true; reports = await fetchReports() as any[]; reportsLoading = false; }
    if (key === 'appeals' && !appeals.length) { appealsLoading = true; appeals = await fetchAppeals() as any[]; appealsLoading = false; }
    if (key === 'staff' && !staffList.length) { staffLoading = true; staffList = await fetchStaff(); staffLoading = false; }
  }

  $effect(() => { if (visibleTabs.length && activeKey) loadTab(activeKey); });

  async function searchUsers() {
    userLoading = true;
    users = await fetchUsers(userSearch);
    userLoading = false;
  }

  async function doBan() {
    if (!banTarget || !$currentUser) return;
    banSaving = true;
    await banUser(banTarget.uid, banReason, $currentUser.uid);
    users = users.map(u => u.uid === banTarget.uid ? {...u, banned:true} : u);
    banTarget = null; banReason = ''; banSaving = false;
  }

  async function doUnban(uid: string) {
    await unbanUser(uid);
    users = users.map(u => u.uid === uid ? {...u, banned:false} : u);
  }

  async function doModerate(approved: boolean) {
    if (!modTarget) return;
    await moderatePost(modTarget.postId ?? modTarget.id, modTarget.reportedUid ?? '', modTarget.reportedName ?? '', modStatus, modReason, '');
    await updateReportStatus(modTarget.id, approved ? 'resolved' : 'dismissed');
    reports = reports.filter(r => r.id !== modTarget.id);
    modTarget = null; modReason = '';
  }

  async function doResolveAppeal(approved: boolean) {
    if (!appealTarget) return;
    await resolveAppeal(appealTarget, approved, appealNote);
    appeals = appeals.filter(a => a.id !== appealTarget.id);
    appealTarget = null; appealNote = '';
  }

  async function doSendPush() {
    if (!pushTitle.trim() || !pushBody.trim()) return;
    pushSending = true;
    const r = await sendPushNotification(pushTitle, pushBody, pushUrl);
    pushResult = r.message;
    if (r.success) { pushTitle = ''; pushBody = ''; pushUrl = ''; }
    pushSending = false;
  }

  async function doAddStaff() {
    if (!newStaffUid.trim()) return;
    await addStaff(newStaffUid, newStaffTitle || newStaffRole, newStaffRole);
    staffList = await fetchStaff();
    newStaffUid = ''; newStaffTitle = ''; showAddStaff = false;
  }

  async function doRemoveStaff(uid: string) {
    if (!confirm('Bu yetkiliyi kaldır?')) return;
    await removeStaff(uid);
    staffList = staffList.filter(s => s.uid !== uid);
  }
</script>

<svelte:head><title>Admin — Heftreng</title></svelte:head>

<div class="admin-page">
  <div class="admin-topbar">
    <h2>⚙️ Admin Paneli</h2>
    {#if perms}<span class="role-chip">{perms.title}</span>{/if}
  </div>

  {#if loading}
    <div class="admin-loading">Kontrol ediliyor…</div>
  {:else if notAdmin}
    <EmptyState icon="🚫" message="Bu sayfaya erişim yetkiniz yok." actionLabel="Ana Sayfaya Dön" actionHref="/feed" />
  {:else}
    <TabBar tabs={visibleTabs.map(t=>t.label)} bind:active={activeIdx} stickyTop={52} />

    <div class="admin-body">

      <!-- İSTATİSTİK -->
      {#if activeKey === 'stats'}
        {#if !stats}
          <div class="admin-loading">Yükleniyor…</div>
        {:else}
          <div class="stats-grid">
            <div class="stat-card"><span class="stat-val">{stats.newUsersToday ?? 0}</span><span class="stat-lbl">Bugün Yeni Kullanıcı</span></div>
            <div class="stat-card"><span class="stat-val">{stats.newPostsToday ?? 0}</span><span class="stat-lbl">Aktif Gönderi</span></div>
            <div class="stat-card"><span class="stat-val">{stats.onlineNow ?? 0}</span><span class="stat-lbl">Çevrimiçi</span></div>
            <div class="stat-card"><span class="stat-val">{stats.bannedUsers ?? 0}</span><span class="stat-lbl">Banlı Kullanıcı</span></div>
          </div>
        {/if}

      <!-- KULLANICILAR -->
      {:else if activeKey === 'users'}
        <div class="search-bar">
          <input type="search" placeholder="İsim veya kullanıcı adı ara…" bind:value={userSearch}
            oninput={searchUsers} class="search-input" />
        </div>
        {#if userLoading}
          <div class="admin-loading">Yükleniyor…</div>
        {:else if users.length === 0}
          <EmptyState icon="👥" message="Kullanıcı bulunamadı." />
        {:else}
          <ul class="user-list">
            {#each users as u (u.uid)}
              <li class="user-row">
                <a href="/profile/{u.uid}" class="user-info">
                  <Avatar src={u.photo_url} name={u.display_name} size={40} />
                  <div>
                    <p class="user-name">{u.display_name || u.username}</p>
                    <p class="user-sub">@{u.username} · {u.followers_count ?? 0} takipçi</p>
                  </div>
                </a>
                <div class="user-actions">
                  {#if u.banned}
                    <span class="banned-badge">Banlı</span>
                    <button class="action-btn green" onclick={() => doUnban(u.uid)}>Kaldır</button>
                  {:else}
                    <button class="action-btn red" onclick={() => banTarget = u}>Banla</button>
                  {/if}
                </div>
              </li>
            {/each}
          </ul>
        {/if}

      <!-- ŞİKAYETLER -->
      {:else if activeKey === 'reports'}
        {#if reportsLoading}
          <div class="admin-loading">Yükleniyor…</div>
        {:else if reports.length === 0}
          <EmptyState icon="✅" message="Bekleyen şikayet yok." />
        {:else}
          <ul class="report-list">
            {#each reports as r (r.id)}
              <li class="report-row">
                <div class="report-info">
                  <p class="report-reason"><strong>{r.reason ?? 'Şikayet'}</strong></p>
                  <p class="report-sub">Gönderi: {r.postId ?? r.feedId ?? '-'}</p>
                  <p class="report-sub">Şikayet eden: {r.reporterUid ?? '-'}</p>
                </div>
                <div class="report-actions">
                  <button class="action-btn red" onclick={() => modTarget = r}>Müdahale</button>
                  <button class="action-btn" onclick={async () => { await updateReportStatus(r.id,'dismissed'); reports=reports.filter(x=>x.id!==r.id); }}>Yoksay</button>
                </div>
              </li>
            {/each}
          </ul>
        {/if}

      <!-- İTİRAZLAR -->
      {:else if activeKey === 'appeals'}
        {#if appealsLoading}
          <div class="admin-loading">Yükleniyor…</div>
        {:else if appeals.length === 0}
          <EmptyState icon="✅" message="Bekleyen itiraz yok." />
        {:else}
          <ul class="report-list">
            {#each appeals as a (a.id)}
              <li class="report-row">
                <div class="report-info">
                  <p class="report-reason"><strong>{a.postOwnerName ?? 'Kullanıcı'}</strong> itiraz etti</p>
                  <p class="report-sub">Gönderi: {a.postId ?? '-'}</p>
                  {#if a.message}<p class="report-sub">"{a.message}"</p>{/if}
                </div>
                <div class="report-actions">
                  <button class="action-btn green" onclick={() => { appealTarget=a; appealNote=''; }}>İncele</button>
                </div>
              </li>
            {/each}
          </ul>
        {/if}

      <!-- PUSH -->
      {:else if activeKey === 'push'}
        <div class="push-form">
          <label class="form-label">Başlık</label>
          <input type="text" class="form-input" bind:value={pushTitle} placeholder="Bildirim başlığı" />
          <label class="form-label">Mesaj</label>
          <textarea class="form-input" bind:value={pushBody} rows="3" placeholder="Bildirim metni"></textarea>
          <label class="form-label">URL (opsiyonel)</label>
          <input type="text" class="form-input" bind:value={pushUrl} placeholder="https://…" />
          {#if pushResult}<p class="push-result">{pushResult}</p>{/if}
          <button class="primary-btn" disabled={pushSending || !pushTitle.trim() || !pushBody.trim()} onclick={doSendPush}>
            {pushSending ? 'Gönderiliyor…' : '📣 Bildirim Gönder'}
          </button>
        </div>

      <!-- PERSONEL -->
      {:else if activeKey === 'staff'}
        <div class="staff-header">
          <button class="primary-btn sm" onclick={() => showAddStaff = true}>+ Yetkili Ekle</button>
        </div>
        {#if staffLoading}
          <div class="admin-loading">Yükleniyor…</div>
        {:else if staffList.length === 0}
          <EmptyState icon="👤" message="Henüz yetkili yok." />
        {:else}
          <ul class="user-list">
            {#each staffList as s (s.uid)}
              <li class="user-row">
                <div class="user-info">
                  <Avatar src={s.photoURL} name={s.name} size={40} />
                  <div>
                    <p class="user-name">{s.name}</p>
                    <p class="user-sub">{s.title ?? s.role} · {(s.permissions ?? []).join(', ')}</p>
                  </div>
                </div>
                <button class="action-btn red" onclick={() => doRemoveStaff(s.uid)}>Kaldır</button>
              </li>
            {/each}
          </ul>
        {/if}
      {/if}

    </div>
  {/if}
</div>

<!-- Ban Modal -->
<Modal bind:open={!!banTarget} title="Kullanıcıyı Banla" onclose={() => banTarget = null}>
  {#if banTarget}
    <div class="modal-form">
      <p style="margin:0 0 10px;font-size:.88rem"><strong>{banTarget.display_name}</strong> banlanacak.</p>
      <input type="text" class="form-input" bind:value={banReason} placeholder="Ban sebebi (opsiyonel)" />
      <button class="primary-btn danger" disabled={banSaving} onclick={doBan}>
        {banSaving ? 'İşleniyor…' : 'Banla'}
      </button>
    </div>
  {/if}
</Modal>

<!-- Moderasyon Modal -->
<Modal bind:open={!!modTarget} title="Gönderi Müdahalesi" onclose={() => modTarget = null}>
  {#if modTarget}
    <div class="modal-form">
      <p style="margin:0 0 10px;font-size:.82rem;color:var(--muted)">Gönderi: {modTarget.postId ?? modTarget.id}</p>
      <select class="form-input" bind:value={modStatus}>
        <option value="restricted">Kısıtla</option>
        <option value="suspended">Askıya Al</option>
        <option value="removed">Kaldır</option>
      </select>
      <input type="text" class="form-input" bind:value={modReason} placeholder="Sebep (kullanıcıya bildirilir)" />
      <div style="display:flex;gap:8px">
        <button class="primary-btn danger" onclick={() => doModerate(false)}>Uygula</button>
        <button class="primary-btn" style="background:var(--surface-var);color:var(--on-bg)" onclick={() => modTarget = null}>İptal</button>
      </div>
    </div>
  {/if}
</Modal>

<!-- İtiraz Modal -->
<Modal bind:open={!!appealTarget} title="İtiraz İncele" onclose={() => appealTarget = null}>
  {#if appealTarget}
    <div class="modal-form">
      <p style="margin:0 0 6px;font-size:.88rem"><strong>{appealTarget.postOwnerName}</strong></p>
      {#if appealTarget.message}<p style="font-size:.82rem;color:var(--muted);margin:0 0 10px">"{appealTarget.message}"</p>{/if}
      <input type="text" class="form-input" bind:value={appealNote} placeholder="Admin notu (opsiyonel)" />
      <div style="display:flex;gap:8px">
        <button class="primary-btn green" onclick={() => doResolveAppeal(true)}>✅ Kabul Et</button>
        <button class="primary-btn danger" onclick={() => doResolveAppeal(false)}>❌ Reddet</button>
      </div>
    </div>
  {/if}
</Modal>

<!-- Personel Ekle Modal -->
<Modal bind:open={showAddStaff} title="Yetkili Ekle" onclose={() => showAddStaff = false}>
  <div class="modal-form">
    <input type="text" class="form-input" bind:value={newStaffUid} placeholder="Kullanıcı UID" />
    <input type="text" class="form-input" bind:value={newStaffTitle} placeholder="Unvan (ör: Moderatör)" />
    <select class="form-input" bind:value={newStaffRole}>
      {#each Object.keys(ROLE_PERMS) as role}
        <option value={role}>{role}</option>
      {/each}
    </select>
    <button class="primary-btn" onclick={doAddStaff}>Ekle</button>
  </div>
</Modal>

<style>
.admin-page { min-height:100dvh; padding-bottom:80px; }
.admin-topbar { display:flex; align-items:center; justify-content:space-between; padding:14px 16px 8px; }
.admin-topbar h2 { margin:0; font-size:1.1rem; font-weight:700; }
.role-chip { font-size:.72rem; font-weight:700; background:color-mix(in srgb,var(--primary) 12%,transparent); color:var(--primary); border-radius:10px; padding:3px 10px; }
.admin-loading { display:flex; align-items:center; justify-content:center; height:160px; color:var(--muted); }
.admin-body { padding:12px; }

.stats-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:10px; }
.stat-card { background:var(--surface); border:1px solid var(--divider); border-radius:14px; padding:16px; display:flex; flex-direction:column; gap:4px; }
.stat-val { font-size:1.6rem; font-weight:800; color:var(--on-bg); }
.stat-lbl { font-size:.72rem; color:var(--muted); }

.search-bar { margin-bottom:10px; }
.search-input { width:100%; border:1.5px solid var(--divider); border-radius:12px; padding:10px 14px; font-size:.88rem; font-family:inherit; color:var(--on-bg); background:var(--surface-var); outline:none; box-sizing:border-box; }
.user-list { list-style:none; padding:0; margin:0; }
.user-row { display:flex; align-items:center; gap:10px; padding:10px 0; border-bottom:1px solid var(--divider); }
.user-info { display:flex; align-items:center; gap:10px; flex:1; min-width:0; text-decoration:none; color:inherit; cursor:pointer; }
.user-name { margin:0; font-size:.88rem; font-weight:600; }
.user-sub  { margin:0; font-size:.72rem; color:var(--muted); }
.user-actions { display:flex; align-items:center; gap:6px; flex-shrink:0; }
.banned-badge { font-size:.72rem; font-weight:700; color:#ef4444; background:color-mix(in srgb,#ef4444 10%,transparent); border-radius:8px; padding:2px 8px; }
.action-btn { font-size:.75rem; font-weight:700; border:1.5px solid var(--divider); background:transparent; color:var(--on-bg); border-radius:12px; padding:5px 12px; cursor:pointer; font-family:inherit; }
.action-btn.red   { border-color:#ef4444; color:#ef4444; }
.action-btn.green { border-color:#22c55e; color:#22c55e; }

.report-list { list-style:none; padding:0; margin:0; }
.report-row { display:flex; align-items:flex-start; gap:10px; padding:12px 0; border-bottom:1px solid var(--divider); }
.report-info { flex:1; min-width:0; }
.report-reason { margin:0 0 3px; font-size:.88rem; }
.report-sub    { margin:0; font-size:.75rem; color:var(--muted); }
.report-actions { display:flex; flex-direction:column; gap:5px; flex-shrink:0; }

.push-form { display:flex; flex-direction:column; gap:10px; max-width:480px; }
.form-label { font-size:.78rem; font-weight:700; color:var(--muted); }
.form-input { border:1.5px solid var(--divider); border-radius:10px; padding:10px 12px; font-size:.88rem; font-family:inherit; color:var(--on-bg); background:var(--surface-var); outline:none; width:100%; box-sizing:border-box; }
.push-result { font-size:.82rem; color:var(--primary); margin:0; }

.staff-header { margin-bottom:10px; }
.modal-form { display:flex; flex-direction:column; gap:10px; }
.primary-btn { padding:11px; border-radius:12px; border:none; background:var(--primary); color:#fff; font-weight:700; font-size:.88rem; cursor:pointer; font-family:inherit; }
.primary-btn.sm { padding:7px 16px; font-size:.78rem; width:auto; }
.primary-btn.danger { background:#ef4444; }
.primary-btn.green  { background:#22c55e; }
.primary-btn:disabled { opacity:.4; cursor:default; }
</style>
