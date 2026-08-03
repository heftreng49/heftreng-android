import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut,
  onAuthStateChanged,
  GoogleAuthProvider,
  signInWithPopup,
  sendPasswordResetEmail,
  updateProfile,
  type User as FirebaseUser,
} from 'firebase/auth';
import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore';
import { auth, db } from '$lib/firebase/config'; // config.ts'den tek instance
import { currentUser, authLoading, userProfile } from '$lib/stores/auth';
import type { User } from '$lib/models/user';
import { collection, query, where, limit, getDocs } from 'firebase/firestore';

/** displayName'den benzersiz username üretir — Firestore'da usernames koleksiyonunu kontrol eder */
async function generateUniqueUsername(displayName: string, uid: string): Promise<string> {
  const base = displayName.toLowerCase()
    .replace(/[^a-z0-9_]/g, '')
    .slice(0, 20) || 'user';

  let handle = base;
  for (let attempt = 0; attempt < 8; attempt++) {
    const snap = await getDoc(doc(db, 'usernames', handle));
    if (!snap.exists()) {
      // Rezerve et
      await setDoc(doc(db, 'usernames', handle), { uid });
      return handle;
    }
    // Alınmış — rastgele sayı ekle
    handle = base.slice(0, 16) + Math.floor(1000 + Math.random() * 9000);
  }
  // Son çare: uid sonunu ekle
  const fallback = base.slice(0, 12) + '_' + uid.slice(-6);
  await setDoc(doc(db, 'usernames', fallback), { uid });
  return fallback;
}

/** Firebase Auth state dinleyici */
export function initAuthListener() {
  return onAuthStateChanged(auth, async (fbUser: FirebaseUser | null) => {
    currentUser.set(fbUser);
    if (fbUser) {
      const snap = await getDoc(doc(db, 'users', fbUser.uid));
      userProfile.set(snap.exists() ? (snap.data() as User) : null);
    } else {
      userProfile.set(null);
    }
    authLoading.set(false);
  });
}

/** E-posta / şifre ile giriş */
export async function signIn(email: string, password: string) {
  return signInWithEmailAndPassword(auth, email, password);
}

/** Google ile giriş */
export async function signInWithGoogle() {
  const provider = new GoogleAuthProvider();
  const cred = await signInWithPopup(auth, provider);
  // Firestore'da kullanıcı yoksa oluştur
  const ref = doc(db, 'users', cred.user.uid);
  const snap = await getDoc(ref);
  if (!snap.exists()) {
    const username = await generateUniqueUsername(
      cred.user.displayName ?? '', cred.user.uid
    );
    await setDoc(ref, {
      uid:            cred.user.uid,
      displayName:    cred.user.displayName ?? '',
      email:          cred.user.email ?? '',
      photoURL:       cred.user.photoURL ?? '',
      username,
      usernameLower:  username.toLowerCase(),
      bio:            '',
      followersCount: 0,
      followingCount: 0,
      postsCount:     0,
      createdAt:      serverTimestamp(),
      platform:       'web',
    } satisfies Partial<User>);
  }
  return cred;
}

/** Yeni kullanıcı kaydı */
export async function register(email: string, password: string, displayName: string) {
  const cred = await createUserWithEmailAndPassword(auth, email, password);
  // Firebase Auth displayName güncelle
  await updateProfile(cred.user, { displayName });
  // Benzersiz username üret
  const username = await generateUniqueUsername(displayName, cred.user.uid);
  // Firestore kullanıcı belgesi
  await setDoc(doc(db, 'users', cred.user.uid), {
    uid:            cred.user.uid,
    displayName,
    email,
    photoURL:       '',
    username,
    usernameLower:  username.toLowerCase(),
    bio:            '',
    followersCount: 0,
    followingCount: 0,
    postsCount:     0,
    createdAt:      serverTimestamp(),
    platform:       'web',
  } satisfies Partial<User>);
  return cred;
}

/** Şifre sıfırlama maili */
export async function sendPasswordReset(email: string) {
  return sendPasswordResetEmail(auth, email);
}

/** Çıkış */
export async function signOut() {
  await firebaseSignOut(auth);
}
