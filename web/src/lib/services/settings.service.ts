// Android SettingsViewModel karşılığı
import { doc, updateDoc, collection, getDocs, deleteDoc, setDoc, serverTimestamp, deleteField } from 'firebase/firestore';
import { updatePassword, deleteUser, getAuth } from 'firebase/auth';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';

// ── Tema & Dil (localStorage → ui.store.ts ile senkron) ─────────────────────
export function getTheme(): { mode: string; variant: string; lang: string } {
  return {
    mode:    localStorage.getItem('hf_theme_mode')    ?? 'system',
    variant: localStorage.getItem('hf_theme_variant') ?? 'default',
    lang:    localStorage.getItem('hf_lang')          ?? 'tr',
  };
}

export function saveTheme(mode: string, variant: string) {
  localStorage.setItem('hf_theme_mode',    mode);
  localStorage.setItem('hf_theme_variant', variant);
  // DOM'a uygula
  document.documentElement.setAttribute('data-theme', mode === 'system'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    : mode);
  document.documentElement.setAttribute('data-variant', variant);
}

export function saveLang(lang: string) {
  localStorage.setItem('hf_lang', lang);
}

// ── Push bildirim ─────────────────────────────────────────────────────────────
export function getPushEnabled(): boolean {
  return localStorage.getItem('hf_push') !== 'false';
}
export function setPushEnabled(val: boolean) {
  localStorage.setItem('hf_push', String(val));
}

// ── Gizlilik ──────────────────────────────────────────────────────────────────
export async function togglePrivateAccount(uid: string, isPrivate: boolean): Promise<void> {
  await updateDoc(doc(db, 'users', uid), { isPrivate });
}

export async function setMessagePermission(uid: string, perm: 'everyone' | 'followers' | 'nobody'): Promise<void> {
  await updateDoc(doc(db, 'users', uid), { messagePermission: perm });
}

// ── Engellenen kullanıcılar ───────────────────────────────────────────────────
export async function fetchBlockedUsers(uid: string): Promise<{ uid: string; displayName: string; photoURL: string }[]> {
  const snap = await getDocs(collection(db, 'users', uid, 'blocked'));
  return snap.docs.map(d => ({ uid: d.id, ...d.data() as any }));
}

export async function unblockUser(myUid: string, targetUid: string): Promise<void> {
  await deleteDoc(doc(db, 'users', myUid, 'blocked', targetUid));
}

export async function blockUser(myUid: string, targetUid: string, name: string, photo: string): Promise<void> {
  await setDoc(doc(db, 'users', myUid, 'blocked', targetUid), {
    displayName: name, photoURL: photo, blockedAt: serverTimestamp(),
  });
}

// ── Şifre değiştir ────────────────────────────────────────────────────────────
export async function changePassword(newPassword: string): Promise<void> {
  const user = getAuth().currentUser;
  if (!user) throw new Error('Giriş yapılmamış');
  await updatePassword(user, newPassword);
}

// ── Hesabı sil ────────────────────────────────────────────────────────────────
export async function deleteAccount(uid: string): Promise<void> {
  const user = getAuth().currentUser;
  if (!user) throw new Error('Giriş yapılmamış');
  // Supabase kaydını sil
  await supabase.from('users').delete().eq('uid', uid);
  // Firebase hesabını sil
  await deleteUser(user);
}
