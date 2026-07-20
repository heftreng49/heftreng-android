"use client"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { useAuthStore } from "@/lib/store/authStore"
import { useAppConfig } from "@/lib/hooks/useAppConfig"
import { Newspaper, Search, BookOpen, MessageCircle, User } from "lucide-react"
import clsx from "clsx"

export function BottomNav() {
  const { user }   = useAuthStore()
  const { config } = useAppConfig()
  const pathname   = usePathname()

  const items = [
    { href: "/feed",     icon: Newspaper,      show: true },
    { href: "/search",   icon: Search,         show: config.searchEnabled },
    { href: "/serials",  icon: BookOpen,       show: config.serialsEnabled || config.booksEnabled },
    { href: "/messages", icon: MessageCircle,  show: config.messagesEnabled && !!user },
    { href: user ? `/profile/${user.uid}` : "/login", icon: User, show: true },
  ].filter((i) => i.show)

  return (
    <nav
      className="lg:hidden fixed bottom-0 left-0 right-0 z-50 glass border-t flex"
      style={{ borderColor: "var(--divider)" }}
    >
      {items.map(({ href, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(href + "/")
        return (
          <Link
            key={href}
            href={href}
            className={clsx(
              "flex-1 flex flex-col items-center justify-center py-3 transition-colors",
              active ? "text-[var(--primary)]" : "text-[var(--muted)]",
            )}
          >
            <Icon size={22} strokeWidth={active ? 2.2 : 1.8} />
          </Link>
        )
      })}
    </nav>
  )
}
