"use client"
import { useEffect } from "react"
import { onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword, signOut as fbSignOut } from "firebase/auth"
import { doc, getDoc, setDoc, serverTimestamp } from "firebase/firestore"
import { auth, db } from "@/lib/firebase/config"
import { useAuthStore } from "@/lib/store/authStore"
import type { User } from "@/lib/types"

export function useAuthInit() {
  const { setUser, setFirebaseUid, setLoading } = useAuthStore()

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (fbUser) => {
      if (fbUser) {
        setFirebaseUid(fbUser.uid)
        const snap = await getDoc(doc(db, "users", fbUser.uid))
        if (snap.exists()) {
          setUser({ uid: fbUser.uid, ...snap.data() } as User)
        }
      } else {
        setUser(null)
        setFirebaseUid(null)
      }
      setLoading(false)
    })
    return unsub
  }, [setUser, setFirebaseUid, setLoading])
}

export async function signIn(email: string, password: string) {
  const cred = await signInWithEmailAndPassword(auth, email, password)
  return cred.user
}

export async function signUp(email: string, password: string, username: string, displayName: string) {
  const cred = await createUserWithEmailAndPassword(auth, email, password)
  const uid  = cred.user.uid
  const newUser: Omit<User, "uid"> = {
    username,
    displayName,
    photoURL:       "",
    bio:            "",
    language:       "tr",
    themeVariant:   "charcoal",
    themeMode:      "dark",
    followersCount: 0,
    followingCount: 0,
    postCount:      0,
    isAdmin:        false,
    isVerified:     false,
    createdAt:      Date.now(),
  }
  await setDoc(doc(db, "users", uid), { ...newUser, createdAt: serverTimestamp() })
  return cred.user
}

export async function signOut() {
  await fbSignOut(auth)
}
