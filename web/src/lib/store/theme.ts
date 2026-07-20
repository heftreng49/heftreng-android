import { writable } from 'svelte/store';
import type { ThemeVariant, ThemeMode } from '$lib/types';

function createThemeStore() {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('heftreng-theme') : null;
  const initial = stored ? JSON.parse(stored) : { variant: 'charcoal', mode: 'dark' };

  const { subscribe, set, update } = writable(initial);

  return {
    subscribe,
    setVariant: (variant: ThemeVariant) => update(s => {
      const next = { ...s, variant };
      if (typeof localStorage !== 'undefined') localStorage.setItem('heftreng-theme', JSON.stringify(next));
      applyTheme(next.variant, next.mode);
      return next;
    }),
    setMode: (mode: ThemeMode) => update(s => {
      const next = { ...s, mode };
      if (typeof localStorage !== 'undefined') localStorage.setItem('heftreng-theme', JSON.stringify(next));
      applyTheme(next.variant, next.mode);
      return next;
    }),
  };
}

export function applyTheme(variant: ThemeVariant, mode: ThemeMode) {
  if (typeof document === 'undefined') return;
  const resolved = mode === 'system'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    : mode;
  document.documentElement.setAttribute('data-theme', `${variant}-${resolved}`);
}

export const theme = createThemeStore();
