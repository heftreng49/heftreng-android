import type { Metadata } from "next"
import "@/styles/globals.css"
import { Providers } from "./providers"

export const metadata: Metadata = {
  title:       "Heftreng",
  description: "Kürtçe ve Türkçe içerik platformu",
  icons:       { icon: "/icons/icon.png" },
  openGraph: {
    title:       "Heftreng",
    description: "Kürtçe ve Türkçe içerik platformu",
    type:        "website",
  },
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="tr" data-theme="charcoal-dark" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
