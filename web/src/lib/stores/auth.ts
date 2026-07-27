// Android ViewModel auth state karşılığı
// Mevcut lib/store/auth.ts'in genişletilmiş hali — import yolu değişti: $lib/stores/auth
import { writable, derived } from 'svelte/store';
import type { User as FirebaseUser } from 'firebase/auth';
import type { User } from '$lib/models/user';

// Firebase auth kullanıcısı (ham)
export const currentUser   = writable<FirebaseUser | null>(null);
export const authLoading   = writable(true);

// Firestore'dan çekilen tam kullanıcı profili
export const userProfile   = writable<User | null>(null);

// Giriş yapılmış mı?
export const isLoggedIn    = derived(
  [currentUser, authLoading],
  ([$u, $l]) => !$l && $u !== null
);
