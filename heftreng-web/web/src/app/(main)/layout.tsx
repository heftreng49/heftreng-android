import { Navbar }    from "@/components/layout/Navbar"
import { Sidebar }   from "@/components/layout/Sidebar"
import { BottomNav } from "@/components/layout/BottomNav"

export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen" style={{ background: "var(--bg)" }}>
      <Navbar />
      <div className="flex max-w-6xl mx-auto pt-14">
        {/* Sol sidebar — masaüstü */}
        <aside className="hidden lg:block w-64 shrink-0 sticky top-14 h-[calc(100vh-3.5rem)] overflow-y-auto">
          <Sidebar />
        </aside>
        {/* Ana içerik */}
        <main className="flex-1 min-w-0 px-0 sm:px-4 pb-20 lg:pb-0">
          {children}
        </main>
      </div>
      {/* Alt navigasyon — mobil */}
      <BottomNav />
    </div>
  )
}
