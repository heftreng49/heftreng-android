"use client"
import { useEffect, useState } from "react"
import { collection, query, where, getDocs } from "firebase/firestore"
import { db } from "@/lib/firebase/config"
import { X, Info, AlertTriangle, AlertCircle } from "lucide-react"
import type { CmsAnnouncement } from "@/lib/types"
import Link from "next/link"

const DISMISSED_KEY = "heftreng_dismissed_announcements"

export function AnnouncementBanner() {
  const [ann, setAnn]         = useState<CmsAnnouncement | null>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const dismissed = JSON.parse(localStorage.getItem(DISMISSED_KEY) ?? "[]") as string[]

    getDocs(query(collection(db, "cms_announcements"), where("active", "==", true)))
      .then((snap) => {
        const active = snap.docs
          .map((d) => ({ id: d.id, ...d.data() } as CmsAnnouncement))
          .filter((a) => !dismissed.includes(a.id))
          .sort((a, b) => b.id.localeCompare(a.id))[0]
        if (active) { setAnn(active); setVisible(true) }
      })
  }, [])

  const dismiss = () => {
    if (!ann) return
    const dismissed = JSON.parse(localStorage.getItem(DISMISSED_KEY) ?? "[]") as string[]
    localStorage.setItem(DISMISSED_KEY, JSON.stringify([...dismissed, ann.id]))
    setVisible(false)
  }

  if (!visible || !ann) return null

  const colors = {
    info:    { bg: "#1E3A5F", text: "#93C5FD", icon: Info },
    warning: { bg: "#3D2800", text: "#FCD34D", icon: AlertTriangle },
    error:   { bg: "#3D0A0A", text: "#FCA5A5", icon: AlertCircle },
  }
  const c    = colors[ann.type as keyof typeof colors] ?? colors.info
  const Icon = c.icon

  const content = (
    <div
      className="mx-3 my-2 rounded-xl px-4 py-3 flex items-start gap-3"
      style={{ background: c.bg }}
    >
      <Icon size={16} style={{ color: c.text, marginTop: 2, shrink: 0 }} />
      <div className="flex-1 min-w-0">
        {ann.title && (
          <p className="text-sm font-semibold" style={{ color: c.text }}>{ann.title}</p>
        )}
        {ann.body && (
          <p className="text-xs mt-0.5 leading-relaxed" style={{ color: c.text, opacity: 0.85 }}>{ann.body}</p>
        )}
        {ann.linkUrl && (
          <p className="text-xs mt-1 font-semibold underline" style={{ color: c.text }}>{ann.linkUrl}</p>
        )}
      </div>
      <button onClick={dismiss} style={{ color: c.text, opacity: 0.7 }} className="shrink-0">
        <X size={14} />
      </button>
    </div>
  )

  return ann.linkUrl ? (
    <Link href={ann.linkUrl} target="_blank" rel="noopener">{content}</Link>
  ) : content
}
