"use client"
import { useState } from "react"
import { Image as ImageIcon, Send } from "lucide-react"
import { Avatar } from "@/components/ui/Avatar"
import { useAuthStore } from "@/lib/store/authStore"

interface Props {
  onPost: (body: string, imageUrls?: string[]) => Promise<void>
  maxLength?: number
}

export function PostComposer({ onPost, maxLength = 1000 }: Props) {
  const { user }                = useAuthStore()
  const [body, setBody]         = useState("")
  const [loading, setLoading]   = useState(false)

  if (!user) return null

  const remaining = maxLength - body.length
  const canPost   = body.trim().length > 0 && remaining >= 0 && !loading

  const handlePost = async () => {
    if (!canPost) return
    setLoading(true)
    await onPost(body.trim())
    setBody("")
    setLoading(false)
  }

  return (
    <div
      className="border-b px-4 py-4"
      style={{ borderColor: "var(--divider)" }}
    >
      <div className="flex gap-3">
        <Avatar src={user.photoURL} name={user.displayName} size={42} />

        <div className="flex-1">
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Ne düşünüyorsun?"
            rows={3}
            maxLength={maxLength + 10}
            className="w-full resize-none bg-transparent text-sm outline-none placeholder:text-[var(--muted)]"
            style={{ color: "var(--on-bg)" }}
          />

          <div className="flex items-center justify-between mt-2">
            <div className="flex items-center gap-2">
              <button
                className="p-1.5 rounded-lg transition-colors hover:bg-[var(--surface-var)]"
                style={{ color: "var(--muted)" }}
              >
                <ImageIcon size={18} />
              </button>
            </div>

            <div className="flex items-center gap-3">
              {body.length > 0 && (
                <span
                  className="text-xs"
                  style={{ color: remaining < 50 ? "var(--error)" : "var(--muted)" }}
                >
                  {remaining}
                </span>
              )}
              <button
                onClick={handlePost}
                disabled={!canPost}
                className="flex items-center gap-1.5 px-4 py-1.5 rounded-xl text-sm font-medium transition-all disabled:opacity-40"
                style={{ background: "var(--primary)", color: "var(--bg)" }}
              >
                {loading ? (
                  <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
                ) : (
                  <Send size={14} />
                )}
                Gönder
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
