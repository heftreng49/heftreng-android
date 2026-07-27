// Global UI state — drawer, modal, theme
import { writable } from 'svelte/store';

export const drawerOpen   = writable(false);
export const theme        = writable<'light' | 'dark'>('light');
export const toastMsg     = writable<string | null>(null);

// Toast yardımcısı
export function showToast(msg: string, ms = 2500) {
  toastMsg.set(msg);
  setTimeout(() => toastMsg.set(null), ms);
}
