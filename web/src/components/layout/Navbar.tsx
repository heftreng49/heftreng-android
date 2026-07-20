"use client"
import Link from "next/link"
import { useAuthStore } from "@/lib/store/authStore"
import { signOut }      from "@/lib/hooks/useAuth"
import { Avatar }       from "@/components/ui/Avatar"
import { Bell, Search, LogOut, Settings } from "lucide-react"
import { useState } from "react"

export function Navbar() {
  const { user } = useAuthStore()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header
      className="fixed top-0 left-0 right-0 z-50 h-14 glass border-b"
      style={{ borderColor: "var(--divider)" }}
    >
      <div className="max-w-6xl mx-auto h-full flex items-center justify-between px-4">
        {/* Logo */}
        <Link href="/feed" className="flex items-center gap-2">
          <span
            className="text-xl font-bold grad-text"
            style={{ fontFamily: "Playfair Display, serif" }}
          >
            Heftreng
          </span>
        </Link>

        {/* Sağ aksiyonlar */}
        <div className="flex items-center gap-2">
          <Link
            href="/search"
            className="p-2 rounded-xl transition-colors"
            style={{ color: "var(--muted)" }}
          >
            <Search size={20} />
          </Link>

          {user ? (
            <>
              <Link
                href="/notifications"
                className="p-2 rounded-xl transition-colors"
                style={{ color: "var(--muted)" }}
              >
                <Bell size={20} />
              </Link>

              <div className="relative">
                <button
                  onClick={() => setMenuOpen((v) => !v)}
                  className="rounded-full overflow-hidden"
                >
                  <Avatar src={user.photoURL} name={user.displayName} size={32} />
                </button>

                {menuOpen && (
                  <div
                    className="absolute right-0 top-10 w-48 rounded-xl shadow-xl border z-50 overflow-hidden"
                    style={{ background: "var(--surface)", borderColor: "var(--divider)" }}
                  >
                    <Link
                      href={`/profile/${user.uid}`}
                      className="flex items-center gap-3 px-4 py-3 text-sm transition-colors hover:bg-[var(--surface-var)]"
                      style={{ color: "var(--on-bg)" }}
                      onClick={() => setMenuOpen(false)}
                    >
                      <Avatar src={user.photoURL} name={user.displayName} size={24} />
                      <div>
                        <p className="font-medium">{user.displayName}</p>
                        <p className="text-xs" style={{ color: "var(--muted)" }}>@{user.username}</p>
                      </div>
                    </Link>
                    <div style={{ borderTop: "1px solid var(--divider)" }} />
                    <Link
                      href="/settings"
                      className="flex items-center gap-3 px-4 py-3 text-sm transition-colors hover:bg-[var(--surface-var)]"
                      style={{ color: "var(--on-surface)" }}
                      onClick={() => setMenuOpen(false)}
                    >
                      <Settings size={16} />
                      Ayarlar
                    </Link>
                    <button
                      className="flex w-full items-center gap-3 px-4 py-3 text-sm transition-colors hover:bg-[var(--surface-var)]"
                      style={{ color: "var(--error)" }}
                      onClick={() => { signOut(); setMenuOpen(false) }}
                    >
                      <LogOut size={16} />
                      Çıkış Yap
                    </button>
                  </div>
                )}
              </div>
            </>
          ) : (
            <Link
              href="/login"
              className="px-4 py-1.5 rounded-xl text-sm font-medium"
              style={{ background: "var(--primary)", color: "var(--bg)" }}
            >
              Giriş Yap
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
