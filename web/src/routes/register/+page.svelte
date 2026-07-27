<script lang="ts">
  import { goto } from '$app/navigation';
  import { register } from '$lib/services/auth.service';

  let email       = $state('');
  let password    = $state('');
  let displayName = $state('');
  let error       = $state('');
  let loading     = $state(false);

  async function submit() {
    if (!email || !password || !displayName) { error = 'Tüm alanları doldurun.'; return; }
    loading = true; error = '';
    try {
      await register(email, password, displayName);
      goto('/feed');
    } catch(e: any) {
      error =
        e.code === 'auth/email-already-in-use'
          ? 'Bu e-posta zaten kullanımda.'
          : e.code === 'auth/weak-password'
          ? 'Şifre en az 6 karakter olmalı.'
          : 'Kayıt başarısız.';
    } finally { loading = false; }
  }
</script>

<div class="wrap">
  <div class="card">
    <h1 class="logo">Heftreng</h1>
    <p class="sub">Hesap oluştur</p>
    {#if error}<div class="err">{error}</div>{/if}
    <div class="field"><label for="name">Ad Soyad</label><input id="name" type="text" bind:value={displayName} placeholder="Adın Soyadın"/></div>
    <div class="field"><label for="email">E-posta</label><input id="email" type="email" bind:value={email} placeholder="ornek@mail.com"/></div>
    <div class="field"><label for="pass">Şifre</label><input id="pass" type="password" bind:value={password} placeholder="En az 6 karakter"/></div>
    <button class="btn" onclick={register} disabled={loading}>{loading ? 'Kayıt yapılıyor...' : 'Kayıt Ol'}</button>
    <p class="foot">Zaten hesabın var mı? <a href="/login">Giriş Yap</a></p>
  </div>
</div>

<style>
.wrap { min-height:100vh; display:flex; align-items:center; justify-content:center; background:var(--bg); padding:20px; }
.card { background:var(--surface); border-radius:20px; padding:32px 28px; width:100%; max-width:380px; border:1px solid var(--divider); }
.logo { font-family:'Playfair Display',serif; font-size:28px; color:var(--primary); text-align:center; margin-bottom:4px; }
.sub { text-align:center; color:var(--muted); font-size:14px; margin-bottom:24px; }
.field { margin-bottom:16px; }
.field label { display:block; font-size:13px; color:var(--muted); margin-bottom:6px; }
.field input { width:100%; padding:12px 14px; background:var(--surface-var); border:1px solid var(--divider); border-radius:12px; color:var(--on-bg); font-size:15px; outline:none; }
.field input:focus { border-color:var(--primary); }
.btn { width:100%; padding:14px; background:var(--primary); color:#fff; border-radius:12px; font-size:16px; font-weight:600; margin-top:8px; cursor:pointer; border:none; }
.btn:disabled { opacity:0.6; }
.err { background:rgba(248,113,113,0.1); border:1px solid var(--error); color:var(--error); border-radius:10px; padding:10px 14px; font-size:14px; margin-bottom:16px; }
.foot { text-align:center; margin-top:20px; font-size:14px; color:var(--muted); }
.foot a { color:var(--primary); font-weight:600; }
</style>
