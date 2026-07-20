"use client"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { Toaster } from "react-hot-toast"
import { useEffect, useState } from "react"
import { useThemeStore, applyTheme } from "@/lib/store/themeStore"
import { useAuthInit } from "@/lib/hooks/useAuth"

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 1000 * 60 } },
})

function ThemeInit() {
  const { variant, mode } = useThemeStore()
  useAuthInit()

  useEffect(() => {
    applyTheme(variant, mode)

    if (mode === "system") {
      const mq = window.matchMedia("(prefers-color-scheme: dark)")
      const handler = () => applyTheme(variant, "system")
      mq.addEventListener("change", handler)
      return () => mq.removeEventListener("change", handler)
    }
  }, [variant, mode])

  return null
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])

  return (
    <QueryClientProvider client={queryClient}>
      {mounted && <ThemeInit />}
      {children}
      <Toaster
        position="top-center"
        toastOptions={{
          style: {
            background: "var(--surface)",
            color:      "var(--on-bg)",
            border:     "1px solid var(--divider)",
            borderRadius: "12px",
            fontSize:   "14px",
          },
        }}
      />
    </QueryClientProvider>
  )
}
