import { create } from "zustand"
import { persist } from "zustand/middleware"
import type { ThemeVariant, ThemeMode } from "@/lib/types"

interface ThemeStore {
  variant: ThemeVariant
  mode:    ThemeMode
  setVariant: (v: ThemeVariant) => void
  setMode:    (m: ThemeMode)    => void
  resolvedTheme: () => string   // "charcoal-dark" gibi
}

export const useThemeStore = create<ThemeStore>()(
  persist(
    (set, get) => ({
      variant: "charcoal",
      mode:    "dark",

      setVariant: (variant) => {
        set({ variant })
        applyTheme(variant, get().mode)
      },

      setMode: (mode) => {
        set({ mode })
        applyTheme(get().variant, mode)
      },

      resolvedTheme: () => {
        const { variant, mode } = get()
        const resolved = mode === "system"
          ? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
          : mode
        return `${variant}-${resolved}`
      },
    }),
    { name: "heftreng-theme" },
  ),
)

export function applyTheme(variant: ThemeVariant, mode: ThemeMode) {
  const resolved = mode === "system"
    ? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
    : mode
  document.documentElement.setAttribute("data-theme", `${variant}-${resolved}`)
}
