// Android AppModule / AuthRepository karşılığı
// Firebase Auth işlemlerini merkezi yönetir
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut,
  onAuthStateChanged,
  type User as FirebaseUser,
} from 'firebase/auth';
import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { currentUser, authLoading, userProfile } from '$lib/stores/auth';
import type { User } from '$lib/models/user';

const auth = getAuth();

/** Firebase Auth state'ini dinler; store'ları günceller */
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

/** Yeni kullanıcı kaydı */
export async function register(email: string, password: string, displayName: string) {
  const cred = await createUserWithEmailAndPassword(auth, email, password);
  await setDoc(doc(db, 'users', cred.user.uid), {
    uid: cred.user.uid,
    displayName,
    email,
    photoURL: '',
    username: '',
    bio: '',
    followersCount: 0,
    followingCount: 0,
    postsCount: 0,
    createdAt: serverTimestamp(),
  } satisfies Partial<User>);
  return cred;
}

/** Çıkış */
export async function signOut() {
  await firebaseSignOut(auth);
}
