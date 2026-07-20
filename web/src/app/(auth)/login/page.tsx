"use client"
import { useState } from "react"
import Link         from "next/link"
import { useRouter } from "next/navigation"
import { signIn }   from "@/lib/hooks/useAuth"
import toast        from "react-hot-toast"

export default function LoginPage() {
  const router = useRouter()
  const [email,    setEmail]    = useState("")
  const [password, setPassword] = useState("")
  const [loading,  setLoading]  = useState(false)

  const handleSubmit = async () => {
    if (!email || !password) return
    setLoading(true)
    try {
      await signIn(email, password)
      router.push("/feed")
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Giriş başarısız"
      toast.error(msg.includes("wrong-password") ? "Şifre hatalı" :
                  msg.includes("user-not-found") ? "Kullanıcı bulunamadı" : "Bir hata oluştu")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4" style={{ background: "var(--bg)" }}>
      <div
        className="w-full max-w-sm rounded-2xl p-8 border"
        style={{ background: "var(--surface)", borderColor: "var(--divider)" }}
      >
        <h1
          className="text-2xl font-bold mb-1 grad-text"
          style={{ fontFamily: "Playfair Display, serif" }}
        >
          Heftreng
        </h1>
        <p className="text-sm mb-6" style={{ color: "var(--muted)" }}>Hesabına giriş yap</p>

        <div className="flex flex-col gap-3">
          <input
            type="email"
            placeholder="E-posta"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-4 py-3 rounded-xl text-sm outline-none border transition-colors focus:border-[var(--primary)]"
            style={{
              background:  "var(--surface-var)",
              borderColor: "var(--divider)",
              color:       "var(--on-bg)",
            }}
          />
          <input
            type="password"
            placeholder="Şifre"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
            className="w-full px-4 py-3 rounded-xl text-sm outline-none border transition-colors focus:border-[var(--primary)]"
            style={{
              background:  "var(--surface-var)",
              borderColor: "var(--divider)",
              color:       "var(--on-bg)",
            }}
          />
          <button
            onClick={handleSubmit}
            disabled={loading || !email || !password}
            className="w-full py-3 rounded-xl text-sm font-semibold transition-all disabled:opacity-50 mt-1"
            style={{ background: "var(--primary)", color: "var(--bg)" }}
          >
            {loading ? (
              <span className="inline-block w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
            ) : "Giriş Yap"}
          </button>
        </div>

        <p className="text-center text-sm mt-5" style={{ color: "var(--muted)" }}>
          Hesabın yok mu?{" "}
          <Link href="/register" style={{ color: "var(--primary)" }} className="font-medium hover:underline">
            Kayıt Ol
          </Link>
        </p>
      </div>
    </div>
  )
}
