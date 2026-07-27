// Android ProfileRepository karşılığı — Firestore users koleksiyonu
import { doc, getDoc, updateDoc, setDoc } from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import type { User } from '$lib/models/user';

// ── Profil getir ─────────────────────────────────────────────────────────────
export async function fetchProfile(uid: string): Promise<User | null> {
  const snap = await getDoc(doc(db, 'users', uid));
  if (!snap.exists()) return null;
  return { uid: snap.id, ...snap.data() } as User;
}

// ── Profil güncelle ──────────────────────────────────────────────────────────
export async function updateProfile(uid: string, data: Partial<User>): Promise<void> {
  await updateDoc(doc(db, 'users', uid), data as Record<string, unknown>);
}

// ── Yeni kullanıcı belgesi oluştur ───────────────────────────────────────────
export async function createUserDoc(uid: string, data: Partial<User>): Promise<void> {
  await setDoc(doc(db, 'users', uid), {
    uid,
    displayName:       '',
    name:              '',
    username:          '',
    email:             '',
    photoURL:          '',
    coverPhoto:        '',
    bio:               '',
    website:           '',
    followersCount:    0,
    followingCount:    0,
    postsCount:        0,
    level:             1,
    xp:                0,
    streak:            0,
    booksRead:         0,
    quotesShared:      0,
    banned:            false,
    emailVerified:     false,
    isPrivate:         false,
    messagePermission: 'everyone',
    createdAt:         Date.now(),
    ...data,
  });
}
