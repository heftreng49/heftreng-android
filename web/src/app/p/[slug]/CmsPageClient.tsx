"use client"
import { useEffect, useState } from "react"
import { collection, query, where, getDocs } from "firebase/firestore"
import { db } from "@/lib/firebase/config"
import { MarkdownRenderer } from "@/components/ui/MarkdownRenderer"
import type { CmsPage } from "@/lib/types"

export default function CmsPageClient({ slug }: { slug: string }) {
  const [page, setPage] = useState<CmsPage | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getDocs(query(collection(db, "cms_pages"), where("slug", "==", slug), where("active", "==", true)))
      .then((snap) => {
        if (!snap.empty) {
          setPage({ id: snap.docs[0].id, ...snap.docs[0].data() } as CmsPage)
        }
        setLoading(false)
      })
  }, [slug])

  if (loading) return <div className="p-8 text-center" style={{ color: "var(--muted)" }}>Yükleniyor...</div>
  if (!page) return <div className="p-8 text-center" style={{ color: "var(--muted)" }}>Sayfa bulunamadı</div>

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-6" style={{ color: "var(--on-bg)", fontFamily: "Playfair Display, serif" }}>
        {page.title}
      </h1>
      <MarkdownRenderer content={page.body} />
    </div>
  )
}
