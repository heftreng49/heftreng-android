import { create } from "zustand"
import type { User } from "@/lib/types"

interface AuthStore {
  user:       User | null
  firebaseUid: string | null
  loading:    boolean
  setUser:    (u: User | null)    => void
  setFirebaseUid: (uid: string | null) => void
  setLoading: (v: boolean)        => void
}

export const useAuthStore = create<AuthStore>((set) => ({
  user:        null,
  firebaseUid: null,
  loading:     true,
  setUser:     (user)        => set({ user }),
  setFirebaseUid: (uid)      => set({ firebaseUid: uid }),
  setLoading:  (loading)     => set({ loading }),
}))
