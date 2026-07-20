import { writable } from 'svelte/store';
import { browser } from '$app/environment';

type ThemeVariant = 'charcoal' | 'book' | 'forest' | 'ocean' | 'sunset' | 'mono';
type ThemeMode = 'dark' | 'light' | 'system';

interface Theme { variant: ThemeVariant; mode: ThemeMode; }

const DEFAULT: Theme = { variant: 'charcoal', mode: 'dark' };

function load(): Theme {
  if (!browser) return DEFAULT;
  try { return JSON.parse(localStorage.getItem('heft-theme') || '') as Theme; }
  catch { return DEFAULT; }
}

export const theme = writable<Theme>(load());

export function applyTheme(variant: ThemeVariant, mode: ThemeMode) {
  if (!browser) return;
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = mode === 'dark' || (mode === 'system' && prefersDark);
  document.body.setAttribute('data-theme', `${variant}-${dark ? 'dark' : 'light'}`);
  localStorage.setItem('heft-theme', JSON.stringify({ variant, mode }));
}

theme.subscribe(({ variant, mode }) => applyTheme(variant, mode));
