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
    await setDoc(ref, {
      uid:            cred.user.uid,
      displayName:    cred.user.displayName ?? '',
      email:          cred.user.email ?? '',
      photoURL:       cred.user.photoURL ?? '',
      username:       '',
      bio:            '',
      followersCount: 0,
      followingCount: 0,
      postsCount:     0,
      createdAt:      serverTimestamp(),
    } satisfies Partial<User>);
  }
  return cred;
}

/** Yeni kullanıcı kaydı */
export async function register(email: string, password: string, displayName: string) {
  const cred = await createUserWithEmailAndPassword(auth, email, password);
  // Firebase Auth displayName güncelle
  await updateProfile(cred.user, { displayName });
  // Firestore kullanıcı belgesi
  await setDoc(doc(db, 'users', cred.user.uid), {
    uid:            cred.user.uid,
    displayName,
    email,
    photoURL:       '',
    username:       '',
    bio:            '',
    followersCount: 0,
    followingCount: 0,
    postsCount:     0,
    createdAt:      serverTimestamp(),
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
