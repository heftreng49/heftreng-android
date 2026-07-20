<script lang="ts">
  import { currentUser } from '$lib/store/auth';
</script>

<nav>
  <a href="/feed" class="logo">Heftreng</a>
  <div>
    {#if $currentUser}
      <span class="user">{$currentUser.displayName ?? $currentUser.email}</span>
    {:else}
      <a href="/login" class="btn">Giriş Yap</a>
    {/if}
  </div>
</nav>

<style>
nav { display:flex; align-items:center; justify-content:space-between; padding:12px 16px; background:var(--surface); border-bottom:1px solid var(--divider); position:sticky; top:0; z-index:100; }
.logo { font-family:'Playfair Display',serif; font-size:22px; font-weight:600; color:var(--primary); }
.btn { padding:8px 18px; background:var(--primary); color:#fff; border-radius:20px; font-size:14px; font-weight:600; }
.user { font-size:14px; color:var(--muted); }
</style>
