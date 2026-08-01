<script lang="ts">
  import { onMount }     from 'svelte';
  import { goto }        from '$app/navigation';
  import { currentUser, userProfile } from '$lib/stores/auth';
  import {
    getTheme, saveTheme, saveLang, getPushEnabled, setPushEnabled,
    togglePrivateAccount, setMessagePermission,
    fetchBlockedUsers, unblockUser, changePassword, deleteAccount,
  } from '$lib/services/settings.service';
  import { signOut } from '$lib/services/auth.service';
  import SettingsRow from '$lib/components/SettingsRow.svelte';
  import Modal       from '$lib/components/Modal.svelte';
  import Avatar      from '$lib/components/Avatar.svelte';

  let theme       = $state(getTheme());
  let pushEnabled = $state(getPushEnabled());
  let isPrivate   = $state(false);
  let msgPerm     = $state<'everyone'|'followers'|'nobody'>('everyone');
  let blocked     = $state<any[]>([]);
  let showBlocked = $state(false);
  let showPwModal = $state(false);
  let showDelModal= $state(false);
  let newPw       = $state(''); let pwError = $state(''); let pwSaving = $state(false);
  let delConfirm  = $state(''); let delError = $state('');

  onMount(async () => {
    if (!$currentUser) { goto('/login'); return; }
    isPrivate = $userProfile?.isPrivate ?? false;
    msgPerm   = ($userProfile?.messagePermission as any) ?? 'everyone';
  });

  async function loadBlocked() {
    if (!$currentUser) return;
    blocked = await fetchBlockedUsers($currentUser.uid);
    showBlocked = true;
  }

  function onThemeMode(mode: string) {
    theme = { ...theme, mode };
    saveTheme(mode, theme.variant);
  }

  function onThemeVariant(variant: string) {
    theme = { ...theme, variant };
    saveTheme(theme.mode, variant);
  }

  const VARIANTS = [
    { key: 'charcoal', label: 'Charcoal', primary: '#4A6FFF', bg: '#F5F5F7' },
    { key: 'book',     label: 'Kitap',    primary: '#8B5E2C', bg: '#FAF3E8' },
    { key: 'forest',   label: 'Orman',    primary: '#2E7D32', bg: '#F2F9F2' },
    { key: 'ocean',    label: 'Okyanus',  primary: '#0077B6', bg: '#F0F8FC' },
    { key: 'sunset',   label: 'Gün Batımı', primary: '#C0305A', bg: '#FDF5F7' },
    { key: 'mono',     label: 'Mono',     primary: '#222222', bg: '#F8F8F8' },
  ];

  function onLang(lang: string) {
    theme = { ...theme, lang };
    saveLang(lang);
  }

  async function onTogglePrivate(val: boolean) {
    isPrivate = val;
    if ($currentUser) await togglePrivateAccount($currentUser.uid, val);
  }

  async function onMsgPerm(p: 'everyone'|'followers'|'nobody') {
    msgPerm = p;
    if ($currentUser) await setMessagePermission($currentUser.uid, p);
  }

  async function savePw() {
    if (!newPw || newPw.length < 6) { pwError = 'En az 6 karakter.'; return; }
    pwSaving = true; pwError = '';
    try { await changePassword(newPw); showPwModal = false; newPw = ''; }
    catch(e: any) { pwError = e.message ?? 'Hata.'; }
    finally { pwSaving = false; }
  }

  async function confirmDelete() {
    if (delConfirm !== 'SİL') { delError = '"SİL" yazın.'; return; }
    try { await deleteAccount($currentUser!.uid); goto('/login'); }
    catch(e: any) { delError = e.message ?? 'Hata.'; }
  }
</script>

<svelte:head><title>Ayarlar — Heftreng</title></svelte:head>

<div class="settings-page">
  <!-- Profil özeti -->
  {#if $currentUser}
    <a href="/profile/{$currentUser.uid}" class="profile-summary">
      <Avatar src={$currentUser.photoURL ?? ''} name={$currentUser.displayName ?? ''} size={52} />
      <div>
        <p class="ps-name">{$currentUser.displayName}</p>
        <p class="ps-email">{$currentUser.email}</p>
      </div>
      <span style="color:var(--muted);font-size:1.2rem">›</span>
    </a>
  {/if}

  <!-- Görünüm -->
  <p class="section-title">Görünüm</p>
  <div class="section">
    <!-- Mod: Açık / Koyu / Sistem -->
    <div class="theme-row">
      {#each [['system','Sistem'],['light','Açık'],['dark','Koyu']] as [val, label]}
        <button class="theme-btn" class:active={theme.mode === val} onclick={() => onThemeMode(val)}>{label}</button>
      {/each}
    </div>
    <!-- Varyant -->
    <p class="sub-label">Renk Teması</p>
    <div class="variant-grid">
      {#each VARIANTS as v}
        <button
          class="variant-btn"
          class:active={theme.variant === v.key}
          onclick={() => onThemeVariant(v.key)}
        >
          <span class="variant-dot" style="background:{v.primary}"></span>
          <span class="variant-name">{v.label}</span>
          {#if theme.variant === v.key}
            <span class="variant-check">✓</span>
          {/if}
        </button>
      {/each}
    </div>
  </div>

  <!-- Dil -->
  <p class="section-title">Dil</p>
  <div class="section">
    <div class="theme-row">
      {#each [['tr','Türkçe'],['ku','Kurdî']] as [val, label]}
        <button class="theme-btn" class:active={theme.lang === val} onclick={() => onLang(val)}>{label}</button>
      {/each}
    </div>
  </div>

  <!-- Hesap -->
  <p class="section-title">Hesap</p>
  <div class="section">
    <SettingsRow icon="✏️" label="Profili Düzenle" href="/profile/{$currentUser?.uid}?edit=1" />
    <SettingsRow icon="🔑" label="Şifre Değiştir" onClick={() => showPwModal = true} />
  </div>

  <!-- Bildirimler -->
  <p class="section-title">Bildirimler</p>
  <div class="section">
    <SettingsRow icon="🔔" label="Push Bildirimleri" toggle
      bind:checked={pushEnabled}
      onToggle={(v) => setPushEnabled(v)}
    />
  </div>

  <!-- Gizlilik -->
  <p class="section-title">Gizlilik</p>
  <div class="section">
    <SettingsRow icon="🔒" label="Gizli Hesap" sub="Sadece takipçilerin gönderilerini görsün" toggle
      bind:checked={isPrivate} onToggle={onTogglePrivate}
    />
    <p class="perm-label">Mesaj İzni</p>
    <div class="perm-row">
      {#each [['everyone','Herkes'],['followers','Takipçiler'],['nobody','Kimse']] as [val, lbl]}
        <button class="perm-btn" class:active={msgPerm === val} onclick={() => onMsgPerm(val as any)}>{lbl}</button>
      {/each}
    </div>
    <SettingsRow icon="🚫" label="Engellenen Kullanıcılar" onClick={loadBlocked} />
  </div>

  <!-- Diğer -->
  <p class="section-title">Diğer</p>
  <div class="section">
    <SettingsRow icon="📋" label="Hakkında"          href="/blog" />
    <SettingsRow icon="📄" label="Kullanım Koşulları" href="/terms" />
    <SettingsRow icon="🔏" label="Gizlilik Politikası" href="/privacy" />
    <SettingsRow icon="🚪" label="Çıkış Yap" onClick={async () => { await signOut(); goto('/login'); }} />
    <SettingsRow icon="🗑️" label="Hesabı Sil" danger onClick={() => showDelModal = true} />
  </div>
</div>

<!-- Şifre Modal -->
<Modal bind:open={showPwModal} title="Şifre Değiştir" maxWidth="380px">
  <div class="modal-form">
    <input type="password" placeholder="Yeni şifre (min 6 karakter)" bind:value={newPw} class="modal-input" />
    {#if pwError}<p class="modal-error">{pwError}</p>{/if}
    <button class="modal-btn" disabled={pwSaving} onclick={savePw}>
      {pwSaving ? 'Kaydediliyor…' : 'Kaydet'}
    </button>
  </div>
</Modal>

<!-- Hesap Sil Modal -->
<Modal bind:open={showDelModal} title="Hesabı Sil" maxWidth="380px">
  <div class="modal-form">
    <p style="font-size:.85rem;color:var(--muted);margin:0 0 12px">Bu işlem geri alınamaz. Onaylamak için <strong>SİL</strong> yazın.</p>
    <input type="text" placeholder='SİL' bind:value={delConfirm} class="modal-input" />
    {#if delError}<p class="modal-error">{delError}</p>{/if}
    <button class="modal-btn danger" onclick={confirmDelete}>Hesabı Kalıcı Sil</button>
  </div>
</Modal>

<!-- Engellenenler Modal -->
<Modal bind:open={showBlocked} title="Engellenen Kullanıcılar" maxWidth="420px">
  {#if blocked.length === 0}
    <p style="text-align:center;color:var(--muted);padding:16px 0">Engellenen kimse yok.</p>
  {:else}
    <ul class="blocked-list">
      {#each blocked as u (u.uid)}
        <li class="blocked-row">
          <Avatar src={u.photoURL} name={u.displayName} size={36} />
          <span class="blocked-name">{u.displayName}</span>
          <button class="unblock-btn" onclick={async () => {
            await unblockUser($currentUser!.uid, u.uid);
            blocked = blocked.filter(b => b.uid !== u.uid);
          }}>Engeli Kaldır</button>
        </li>
      {/each}
    </ul>
  {/if}
</Modal>

<style>
.settings-page { min-height: 100dvh; padding-bottom: 80px; }
.profile-summary {
  display: flex; align-items: center; gap: 14px;
  padding: 16px; border-bottom: 1px solid var(--divider);
  text-decoration: none; color: inherit; transition: background .12s;
}
.profile-summary:hover { background: var(--surface-var); }
.ps-name { margin: 0; font-weight: 700; font-size: .95rem; }
.ps-email { margin: 0; font-size: .75rem; color: var(--muted); }
.section-title { font-size: .72rem; font-weight: 700; color: var(--muted);
  text-transform: uppercase; letter-spacing: .05em; padding: 14px 16px 4px; margin: 0; }
.section { background: var(--surface); border-top: 1px solid var(--divider); border-bottom: 1px solid var(--divider); }
.theme-row { display: flex; padding: 10px 16px; gap: 8px; }
.theme-btn {
  flex: 1; padding: 8px 4px; border-radius: 10px; font-size: .82rem; font-weight: 600;
  border: 1.5px solid var(--divider); background: transparent; color: var(--muted);
  cursor: pointer; font-family: inherit; transition: all .15s;
}
.theme-btn.active { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 10%, transparent); color: var(--primary); }

.sub-label { font-size: .72rem; color: var(--muted); padding: 8px 16px 4px; margin: 0; font-weight: 600; }
.variant-grid {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 8px; padding: 4px 16px 12px;
}
.variant-btn {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 12px;
  border: 1.5px solid var(--divider); background: var(--surface-var);
  cursor: pointer; font-family: inherit; transition: all .15s;
  position: relative;
}
.variant-btn.active { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 8%, transparent); }
.variant-dot { width: 22px; height: 22px; border-radius: 50%; flex-shrink: 0; }
.variant-name { font-size: .82rem; font-weight: 600; color: var(--on-bg); flex: 1; text-align: left; }
.variant-check { font-size: .75rem; color: var(--primary); font-weight: 700; }
.perm-label { font-size: .78rem; color: var(--muted); padding: 10px 16px 4px; margin: 0; }
.perm-row { display: flex; gap: 8px; padding: 0 16px 10px; }
.perm-btn { flex: 1; padding: 7px 4px; border-radius: 10px; font-size: .78rem; font-weight: 600;
  border: 1.5px solid var(--divider); background: transparent; color: var(--muted); cursor: pointer; font-family: inherit; }
.perm-btn.active { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 10%, transparent); color: var(--primary); }
.modal-form { display: flex; flex-direction: column; gap: 12px; }
.modal-input { border: 1.5px solid var(--divider); border-radius: 10px; padding: 10px 14px;
  font-size: .9rem; font-family: inherit; color: var(--on-bg); background: var(--surface-var); outline: none; }
.modal-error { margin: 0; font-size: .8rem; color: #ef4444; }
.modal-btn { padding: 11px; border-radius: 12px; border: none; background: var(--primary);
  color: #fff; font-weight: 700; font-size: .9rem; cursor: pointer; font-family: inherit; }
.modal-btn.danger { background: #ef4444; }
.modal-btn:disabled { opacity: .5; }
.blocked-list { list-style: none; padding: 0; margin: 0; }
.blocked-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--divider); }
.blocked-name { flex: 1; font-size: .9rem; font-weight: 600; }
.unblock-btn { font-size: .78rem; font-weight: 700; border: 1.5px solid var(--primary);
  background: transparent; color: var(--primary); border-radius: 16px; padding: 5px 12px;
  cursor: pointer; font-family: inherit; }
</style>
