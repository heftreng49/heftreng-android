import CmsPageClient from "./CmsPageClient"

export function generateStaticParams() {
  return []
}

export default function CmsPageRoute({ params }: { params: { slug: string } }) {
  return <CmsPageClient slug={params.slug} />
}
