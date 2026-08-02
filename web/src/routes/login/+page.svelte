<script lang="ts">
  import { goto }                        from '$app/navigation';
  import { signIn, signInWithGoogle }    from '$lib/services/auth.service';
  import { lang, strings as s }          from '$lib/i18n/strings';

  let email    = $state('');
  let password = $state('');
  let error    = $state('');
  let loading  = $state(false);
  let gLoading = $state(false);

  function mapError(code: string): string {
    const map: Record<string, () => string> = {
      'auth/invalid-credential':   () => $lang === 'ku' ? 'E-name an şîfre şaş e.' : 'E-posta veya şifre hatalı.',
      'auth/wrong-password':       () => $lang === 'ku' ? 'E-name an şîfre şaş e.' : 'E-posta veya şifre hatalı.',
      'auth/user-not-found':       () => $lang === 'ku' ? 'Ev e-name qeyd nebûye.' : 'Bu e-posta kayıtlı değil.',
      'auth/too-many-requests':    () => $lang === 'ku' ? 'Pir ceribandin. Hinekî bisekine.' : 'Çok fazla deneme. Lütfen bekleyin.',
      'auth/network-request-failed':()=> $lang === 'ku' ? 'Têkiliya torê şikest.' : 'Ağ bağlantısı hatası.',
    };
    return (map[code] ?? (() => $lang === 'ku' ? 'Ketin têk çû.' : 'Giriş başarısız.'))();
  }

  async function login() {
    if (!email || !password) {
      error = $lang === 'ku' ? 'Hemû xane dagirtin.' : 'Tüm alanları doldurun.';
      return;
    }
    loading = true; error = '';
    try {
      await signIn(email, password);
      goto('/feed');
    } catch(e: any) {
      error = mapError(e.code ?? '');
    } finally { loading = false; }
  }

  async function loginGoogle() {
    gLoading = true; error = '';
    try {
      await signInWithGoogle();
      goto('/feed');
    } catch(e: any) {
      if (e.code !== 'auth/popup-closed-by-user') {
        error = $lang === 'ku' ? 'Google bi ketin têk çû.' : 'Google ile giriş başarısız.';
      }
    } finally { gLoading = false; }
  }
</script>

<div class="wrap">
  <div class="card">
    <h1 class="logo">Heftreng</h1>
    <p class="sub">{$lang === 'ku' ? 'Civaka wêje û zimanê Kurdî' : 'Kürt edebiyatı ve dil topluluğu'}</p>

    {#if error}<div class="err">{error}</div>{/if}

    <!-- Google ile giriş -->
    <button class="google-btn" onclick={loginGoogle} disabled={gLoading}>
      {#if gLoading}
        <span class="spinner"></span>
      {:else}
        <svg width="20" height="20" viewBox="0 0 48 48">
          <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9 3.2l6.7-6.7C35.7 2.4 30.2 0 24 0 14.8 0 6.9 5.4 3 13.3l7.8 6C12.7 13.5 17.9 9.5 24 9.5z"/>
          <path fill="#4285F4" d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v8.5h12.4c-.5 2.8-2.1 5.2-4.5 6.8l7 5.4c4.1-3.8 6.5-9.4 6.5-16.2z"/>
          <path fill="#FBBC05" d="M10.8 28.7A14.5 14.5 0 0 1 9.5 24c0-1.6.3-3.2.8-4.7L2.5 13.3A24 24 0 0 0 0 24c0 3.8.9 7.4 2.5 10.6l8.3-5.9z"/>
          <path fill="#34A853" d="M24 48c6.2 0 11.4-2 15.2-5.5l-7-5.4c-2.1 1.4-4.8 2.2-8.2 2.2-6.1 0-11.3-4-13.2-9.4l-7.8 6C6.9 42.6 14.8 48 24 48z"/>
        </svg>
      {/if}
      {s.googleLogin($lang)}
    </button>

    <div class="divider"><span>{s.orDivider($lang)}</span></div>

    <div class="field">
      <label for="email">{s.email($lang)}</label>
      <input id="email" type="email" bind:value={email}
        placeholder={$lang === 'ku' ? 'nimune@mail.com' : 'ornek@mail.com'}
        onkeydown={(e) => e.key === 'Enter' && login()} />
    </div>
    <div class="field">
      <label for="pass">{s.password($lang)}</label>
      <input id="pass" type="password" bind:value={password}
        placeholder="••••••••"
        onkeydown={(e) => e.key === 'Enter' && login()} />
    </div>

    <a href="/forgot-password" class="forgot">{s.forgotPass($lang)}</a>

    <button class="btn" onclick={login} disabled={loading}>
      {loading
        ? ($lang === 'ku' ? 'Tê ketin...' : 'Giriş yapılıyor...')
        : s.login($lang)}
    </button>

    <p class="foot">{s.noAccount($lang)} → <a href="/register">{s.register($lang)}</a></p>
  </div>
</div>

<style>
.wrap { min-height:100dvh; display:flex; align-items:center; justify-content:center; background:var(--bg); padding:20px; }
.card { background:var(--card); border-radius:20px; padding:32px 24px; width:100%; max-width:380px; border:1px solid var(--divider); box-shadow:0 4px 24px rgba(0,0,0,.08); }
.logo { font-family:'Playfair Display',serif; font-size:28px; color:var(--primary); text-align:center; margin:0 0 4px; }
.sub { text-align:center; color:var(--muted); font-size:13px; margin-bottom:24px; }
.err { background:color-mix(in srgb,var(--error) 10%,transparent); border:1px solid var(--error); color:var(--error); border-radius:10px; padding:10px 14px; font-size:13px; margin-bottom:14px; }
.google-btn {
  width:100%; display:flex; align-items:center; justify-content:center; gap:10px;
  padding:12px; border-radius:12px; border:1.5px solid var(--divider);
  background:var(--surface-var); color:var(--on-bg); font-size:15px; font-weight:600;
  cursor:pointer; font-family:inherit; transition:background .15s;
}
.google-btn:hover { background:var(--surface); }
.google-btn:disabled { opacity:.6; cursor:not-allowed; }
.spinner { width:18px; height:18px; border:2px solid var(--divider); border-top-color:var(--primary); border-radius:50%; animation:spin .7s linear infinite; }
@keyframes spin { to { transform:rotate(360deg); } }
.divider { display:flex; align-items:center; gap:10px; margin:16px 0; }
.divider::before, .divider::after { content:''; flex:1; height:1px; background:var(--divider); }
.divider span { color:var(--muted); font-size:12px; }
.field { margin-bottom:14px; }
.field label { display:block; font-size:12px; font-weight:600; color:var(--muted); margin-bottom:5px; }
.field input { width:100%; padding:12px 14px; background:var(--surface-var); border:1.5px solid var(--divider); border-radius:12px; color:var(--on-bg); font-size:15px; outline:none; box-sizing:border-box; }
.field input:focus { border-color:var(--primary); }
.forgot { display:block; text-align:right; font-size:12px; color:var(--primary); margin:-6px 0 14px; text-decoration:none; }
.btn { width:100%; padding:14px; background:var(--primary); color:#fff; border-radius:12px; font-size:16px; font-weight:700; cursor:pointer; border:none; font-family:inherit; transition:opacity .15s; }
.btn:disabled { opacity:.6; cursor:not-allowed; }
.foot { text-align:center; margin-top:20px; font-size:13px; color:var(--muted); }
.foot a { color:var(--primary); font-weight:600; text-decoration:none; }
</style>
