"use client"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { useAuthStore } from "@/lib/store/authStore"
import { useAppConfig } from "@/lib/hooks/useAppConfig"
import {
  Newspaper, Search, BookOpen, Languages,
  Bell, MessageCircle, Settings, Bookmark,
  LayoutDashboard, User,
} from "lucide-react"
import clsx from "clsx"

export function Sidebar() {
  const { user }    = useAuthStore()
  const { config }  = useAppConfig()
  const pathname    = usePathname()

  const navItems = [
    { href: "/feed",          label: "Gönderi",      icon: Newspaper,      show: true },
    { href: "/search",        label: "Keşfet",       icon: Search,         show: config.searchEnabled },
    { href: "/serials",       label: "Seriler",      icon: BookOpen,       show: config.serialsEnabled || config.booksEnabled },
    { href: "/kurdi",         label: "Kurdî",        icon: Languages,      show: config.kurdiEnabled },
    { href: "/notifications", label: "Bildirimler",  icon: Bell,           show: config.notificationsEnabled && !!user },
    { href: "/messages",      label: "Mesajlar",     icon: MessageCircle,  show: config.messagesEnabled && !!user },
    { href: "/saved",         label: "Kaydedilenler",icon: Bookmark,       show: !!user },
    { href: "/settings",      label: "Ayarlar",      icon: Settings,       show: true },
    { href: "/cms",           label: "CMS",          icon: LayoutDashboard,show: user?.isAdmin },
  ].filter((i) => i.show)

  return (
    <nav className="flex flex-col gap-1 py-4 px-3">
      {user && (
        <Link
          href={`/profile/${user.uid}`}
          className={clsx(
            "flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors mb-2",
            pathname.startsWith("/profile")
              ? "text-[var(--primary)] bg-[color-mix(in_srgb,var(--primary)_12%,transparent)]"
              : "text-[var(--on-surface)] hover:bg-[var(--surface-var)]",
          )}
        >
          <User size={18} />
          Profil
        </Link>
      )}

      {navItems.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(href + "/")
        return (
          <Link
            key={href}
            href={href}
            className={clsx(
              "flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors",
              active
                ? "text-[var(--primary)] bg-[color-mix(in_srgb,var(--primary)_12%,transparent)]"
                : "text-[var(--on-surface)] hover:bg-[var(--surface-var)]",
            )}
          >
            <Icon size={18} />
            {label}
          </Link>
        )
      })}
    </nav>
  )
}
