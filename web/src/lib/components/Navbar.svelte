<script lang="ts">
  import { currentUser } from '$lib/store/auth';
  import { goto } from '$app/navigation';
</script>

<nav class="navbar">
  <a href="/feed" class="logo">Heftreng</a>
  <div class="nav-actions">
    {#if $currentUser}
      <button class="avatar-btn" on:click={() => goto('/profile')}>
        {#if $currentUser.photoURL}
          <img src={$currentUser.photoURL} alt="profil" class="avatar" />
        {:else}
          <div class="avatar-placeholder">{$currentUser.displayName?.[0] ?? '?'}</div>
        {/if}
      </button>
    {:else}
      <a href="/login" class="btn-login">Giriş Yap</a>
    {/if}
  </div>
</nav>

<style>
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--divider);
}
.logo {
  font-family: 'Playfair Display', serif;
  font-size: 22px;
  font-weight: 600;
  color: var(--primary);
}
.btn-login {
  padding: 8px 18px;
  background: var(--primary);
  color: #fff;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar-placeholder {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
</style>
