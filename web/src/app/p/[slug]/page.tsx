import CmsPageClient from "./CmsPageClient"

export function generateStaticParams() {
  return []
}

export default async function CmsPageRoute({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  return <CmsPageClient slug={slug} />
}
