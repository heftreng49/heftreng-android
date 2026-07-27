<script lang="ts">
  import { goto } from '$app/navigation';
  import { signIn } from '$lib/services/auth.service';

  let email    = $state('');
  let password = $state('');
  let error    = $state('');
  let loading  = $state(false);

  async function login() {
    if (!email || !password) return;
    loading = true; error = '';
    try {
      await signIn(email, password);
      goto('/feed');
    } catch(e: any) {
      error =
        e.code === 'auth/invalid-credential' || e.code === 'auth/wrong-password'
          ? 'E-posta veya şifre hatalı.'
          : e.code === 'auth/user-not-found'
          ? 'Bu e-posta kayıtlı değil.'
          : e.code === 'auth/too-many-requests'
          ? 'Çok fazla deneme. Lütfen bekleyin.'
          : (e.message ?? 'Giriş başarısız.');
    } finally { loading = false; }
  }
</script>
</script>

<div class="wrap">
  <div class="card">
    <h1 class="logo">Heftreng</h1>
    <p class="sub">Kürt edebiyatı ve dil topluluğu</p>
    {#if error}<div class="err">{error}</div>{/if}
    <div class="field"><label for="email">E-posta</label><input id="email" type="email" bind:value={email} placeholder="ornek@mail.com"/></div>
    <div class="field"><label for="pass">Şifre</label><input id="pass" type="password" bind:value={password} placeholder="••••••••"/></div>
    <button class="btn" onclick={login} disabled={loading}>{loading ? 'Giriş yapılıyor...' : 'Giriş Yap'}</button>
    <p class="foot">Hesabın yok mu? <a href="/register">Kayıt Ol</a></p>
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
