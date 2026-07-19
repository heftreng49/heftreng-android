import { doc, getDoc, collection, query, where, getDocs } from "firebase/firestore"
import { db }                  from "@/lib/firebase/config"
import { MarkdownRenderer }    from "@/components/ui/MarkdownRenderer"
import { notFound }            from "next/navigation"
import type { Metadata }       from "next"
import type { CmsPage }        from "@/lib/types"

interface Props { params: Promise<{ slug: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params
  const snap = await getDocs(
    query(collection(db, "cms_pages"), where("slug", "==", slug), where("active", "==", true))
  )
  if (snap.empty) return { title: "Sayfa bulunamadı" }
  const page = snap.docs[0].data() as CmsPage
  return { title: `${page.title} — Heftreng`, description: page.body.slice(0, 160) }
}

export default async function CmsPageRoute({ params }: Props) {
  const { slug } = await params
  const snap = await getDocs(
    query(collection(db, "cms_pages"), where("slug", "==", slug), where("active", "==", true))
  )
  if (snap.empty) notFound()

  const page = { id: snap.docs[0].id, ...snap.docs[0].data() } as CmsPage

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1
        className="text-2xl font-bold mb-6"
        style={{ color: "var(--on-bg)", fontFamily: "Playfair Display, serif" }}
      >
        {page.title}
      </h1>
      <MarkdownRenderer content={page.body} />
    </div>
  )
}
