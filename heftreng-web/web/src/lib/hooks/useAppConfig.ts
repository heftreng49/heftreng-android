"use client"
import { useEffect, useState } from "react"
import { doc, onSnapshot } from "firebase/firestore"
import { db } from "@/lib/firebase/config"
import type { AppConfig } from "@/lib/types"

const DEFAULT_CONFIG: AppConfig = {
  maintenanceMode:      false,
  minVersion:           0,
  feedEnabled:          true,
  messagesEnabled:      true,
  serialsEnabled:       true,
  booksEnabled:         true,
  kurdiEnabled:         true,
  notificationsEnabled: true,
  searchEnabled:        true,
  feedAllowQuotes:      true,
  feedShowImages:       true,
  feedShowReposts:      true,
  feedMaxTextLength:    1000,
  feedTitle:            "",
  messagesTitle:        "",
  kurdiTitle:           "",
}

export function useAppConfig() {
  const [config, setConfig]   = useState<AppConfig>(DEFAULT_CONFIG)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const unsub = onSnapshot(doc(db, "app_config", "config"), (snap) => {
      if (snap.exists()) {
        setConfig({ ...DEFAULT_CONFIG, ...snap.data() } as AppConfig)
      }
      setLoading(false)
    })
    return unsub
  }, [])

  return { config, loading }
}
