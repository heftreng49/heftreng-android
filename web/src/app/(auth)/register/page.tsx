"use client"
import { useState }   from "react"
import Link            from "next/link"
import { useRouter }   from "next/navigation"
import { signUp }      from "@/lib/hooks/useAuth"
import toast           from "react-hot-toast"

export default function RegisterPage() {
  const router = useRouter()
  const [displayName, setDisplayName] = useState("")
  const [username,    setUsername]    = useState("")
  const [email,       setEmail]       = useState("")
  const [password,    setPassword]    = useState("")
  const [loading,     setLoading]     = useState(false)

  const handleSubmit = async () => {
    if (!displayName || !username || !email || !password) return
    if (password.length < 6) { toast.error("Şifre en az 6 karakter olmalı"); return }
    setLoading(true)
    try {
      await signUp(email, password, username, displayName)
      router.push("/feed")
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : ""
      toast.error(msg.includes("email-already-in-use") ? "Bu e-posta zaten kullanımda" : "Kayıt başarısız")
    } finally {
      setLoading(false)
    }
  }

  const fields = [
    { label: "Ad Soyad", value: displayName, set: setDisplayName, type: "text",     placeholder: "Adın Soyadın" },
    { label: "Kullanıcı Adı", value: username, set: setUsername,  type: "text",     placeholder: "kullanici_adi" },
    { label: "E-posta",   value: email,       set: setEmail,       type: "email",    placeholder: "ornek@mail.com" },
    { label: "Şifre",     value: password,    set: setPassword,    type: "password", placeholder: "En az 6 karakter" },
  ]

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
        <p className="text-sm mb-6" style={{ color: "var(--muted)" }}>Yeni hesap oluştur</p>

        <div className="flex flex-col gap-3">
          {fields.map(({ label, value, set, type, placeholder }) => (
            <input
              key={label}
              type={type}
              placeholder={placeholder}
              value={value}
              onChange={(e) => set(e.target.value)}
              className="w-full px-4 py-3 rounded-xl text-sm outline-none border transition-colors focus:border-[var(--primary)]"
              style={{ background: "var(--surface-var)", borderColor: "var(--divider)", color: "var(--on-bg)" }}
            />
          ))}

          <button
            onClick={handleSubmit}
            disabled={loading || !displayName || !username || !email || !password}
            className="w-full py-3 rounded-xl text-sm font-semibold transition-all disabled:opacity-50 mt-1"
            style={{ background: "var(--primary)", color: "var(--bg)" }}
          >
            {loading ? (
              <span className="inline-block w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
            ) : "Kayıt Ol"}
          </button>
        </div>

        <p className="text-center text-sm mt-5" style={{ color: "var(--muted)" }}>
          Hesabın var mı?{" "}
          <Link href="/login" style={{ color: "var(--primary)" }} className="font-medium hover:underline">
            Giriş Yap
          </Link>
        </p>
      </div>
    </div>
  )
}
