<script lang="ts">
  import { auth } from '$lib/firebase/config';
  import { signInWithEmailAndPassword } from 'firebase/auth';
  import { goto } from '$app/navigation';

  let email = '';
  let password = '';
  let error = '';
  let loading = false;

  async function login() {
    if (!email || !password) return;
    loading = true; error = '';
    try {
      await signInWithEmailAndPassword(auth, email, password);
      goto('/feed');
    } catch (e: any) {
      error = 'E-posta veya şifre hatalı.';
    } finally {
      loading = false;
    }
  }
</script>

<div class="auth-wrap">
  <div class="auth-card">
    <h1 class="logo">Heftreng</h1>
    <p class="subtitle">Kürt edebiyatı ve dil topluluğu</p>

    {#if error}
      <div class="error-box">{error}</div>
    {/if}

    <div class="field">
      <label>E-posta</label>
      <input type="email" bind:value={email} placeholder="ornek@mail.com" />
    </div>

    <div class="field">
      <label>Şifre</label>
      <input type="password" bind:value={password} placeholder="••••••••" />
    </div>

    <button class="btn-primary" on:click={login} disabled={loading}>
      {loading ? 'Giriş yapılıyor...' : 'Giriş Yap'}
    </button>

    <p class="footer-text">
      Hesabın yok mu? <a href="/register">Kayıt Ol</a>
    </p>
  </div>
</div>

<style>
.auth-wrap {
  min-height: 100vh; display: flex;
  align-items: center; justify-content: center;
  background: var(--bg); padding: 20px;
}
.auth-card {
  background: var(--surface); border-radius: 20px;
  padding: 32px 28px; width: 100%; max-width: 380px;
  border: 1px solid var(--divider);
}
.logo {
  font-family: 'Playfair Display', serif;
  font-size: 28px; color: var(--primary);
  text-align: center; margin-bottom: 4px;
}
.subtitle { text-align: center; color: var(--muted); font-size: 14px; margin-bottom: 24px; }
.field { margin-bottom: 16px; }
.field label { display: block; font-size: 13px; color: var(--muted); margin-bottom: 6px; }
.field input {
  width: 100%; padding: 12px 14px;
  background: var(--surface-var); border: 1px solid var(--divider);
  border-radius: 12px; color: var(--on-bg); font-size: 15px;
  outline: none;
}
.field input:focus { border-color: var(--primary); }
.btn-primary {
  width: 100%; padding: 14px;
  background: var(--primary); color: #fff;
  border-radius: 12px; font-size: 16px; font-weight: 600;
  margin-top: 8px; transition: opacity 0.2s;
}
.btn-primary:disabled { opacity: 0.6; }
.error-box {
  background: rgba(248,113,113,0.1); border: 1px solid var(--error);
  color: var(--error); border-radius: 10px; padding: 10px 14px;
  font-size: 14px; margin-bottom: 16px;
}
.footer-text { text-align: center; margin-top: 20px; font-size: 14px; color: var(--muted); }
.footer-text a { color: var(--primary); font-weight: 600; }
</style>
