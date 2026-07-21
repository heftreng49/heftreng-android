import { writable } from 'svelte/store';

function createTheme() {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('heftreng-theme') : null;
  const initial = stored ? JSON.parse(stored) : { variant: 'charcoal', mode: 'light' };
  const { subscribe, update } = writable(initial);
  return {
    subscribe,
    set: (v: any) => update(() => {
      if (typeof localStorage !== 'undefined') localStorage.setItem('heftreng-theme', JSON.stringify(v));
      applyTheme(v.variant, v.mode);
      return v;
    })
  };
}

export function applyTheme(variant: string, mode: string) {
  if (typeof document === 'undefined') return;
  const resolved = mode === 'system'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    : mode;
  document.documentElement.setAttribute('data-theme', variant + '-' + resolved);
}

export const theme = createTheme();
