import { writable } from 'svelte/store';

// localStorage key'leri — settings.service ile aynı
const KEY_MODE    = 'hf_theme_mode';
const KEY_VARIANT = 'hf_theme_variant';

export function applyTheme(variant: string, mode: string) {
  if (typeof document === 'undefined') return;
  const resolved = mode === 'system'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    : mode;
  document.documentElement.setAttribute('data-theme', `${variant}-${resolved}`);
  // System mod için değişim dinleyicisi
  if (mode === 'system') {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => {
      document.documentElement.setAttribute('data-theme', `${variant}-${e.matches ? 'dark' : 'light'}`);
    };
    mq.removeEventListener('change', handler);
    mq.addEventListener('change', handler);
  }
}

function createTheme() {
  const getInitial = () => {
    if (typeof localStorage === 'undefined') return { variant: 'charcoal', mode: 'system' };
    return {
      variant: localStorage.getItem(KEY_VARIANT) ?? 'charcoal',
      mode:    localStorage.getItem(KEY_MODE)    ?? 'system',
    };
  };

  const { subscribe, set, update } = writable(getInitial());

  return {
    subscribe,
    update,
    set: (v: { variant: string; mode: string }) => {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(KEY_VARIANT, v.variant);
        localStorage.setItem(KEY_MODE,    v.mode);
      }
      applyTheme(v.variant, v.mode);
      set(v);
    },
  };
}

export const theme = createTheme();
