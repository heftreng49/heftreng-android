"use client"
import Link from "next/link"
import Image from "next/image"
import { formatDistanceToNow } from "date-fns"
import { tr } from "date-fns/locale"
import { Heart, MessageCircle, Repeat2, Share2, MoreHorizontal } from "lucide-react"
import { Avatar } from "@/components/ui/Avatar"
import type { Post } from "@/lib/types"

interface Props {
  post:       Post
  onLike?:    (id: string) => void
  onComment?: (id: string) => void
  onRepost?:  (id: string) => void
  onDelete?:  (id: string) => void
  liked?:     boolean
  currentUid?: string
}

export function PostCard({ post, onLike, onComment, onRepost, liked, currentUid }: Props) {
  const time = formatDistanceToNow(new Date(post.ts), { addSuffix: true, locale: tr })

  return (
    <article
      className="border-b px-4 py-4 transition-colors hover:bg-[var(--surface-var)]"
      style={{ borderColor: "var(--divider)" }}
    >
      <div className="flex gap-3">
        {/* Avatar */}
        <Link href={`/profile/${post.uid}`} className="shrink-0">
          <Avatar src={post.photoURL} name={post.displayName} size={42} />
        </Link>

        <div className="flex-1 min-w-0">
          {/* Header */}
          <div className="flex items-center gap-1.5 flex-wrap">
            <Link
              href={`/profile/${post.uid}`}
              className="font-semibold text-sm hover:underline"
              style={{ color: "var(--on-bg)" }}
            >
              {post.isAnonymous ? "Anonim" : post.displayName}
            </Link>
            {!post.isAnonymous && (
              <span className="text-xs" style={{ color: "var(--muted)" }}>
                @{post.username}
              </span>
            )}
            <span style={{ color: "var(--muted)" }}>·</span>
            <span className="text-xs" style={{ color: "var(--muted)" }}>{time}</span>
          </div>

          {/* Gönderi metni */}
          <Link href={`/post/${post.id}`}>
            <p
              className="mt-1 text-sm leading-relaxed whitespace-pre-wrap break-words"
              style={{ color: "var(--on-bg)" }}
            >
              {post.body}
            </p>
          </Link>

          {/* Resimler */}
          {post.imageUrls?.length > 0 && (
            <div className={`mt-3 grid gap-1 rounded-xl overflow-hidden ${post.imageUrls.length > 1 ? "grid-cols-2" : "grid-cols-1"}`}>
              {post.imageUrls.slice(0, 4).map((url, i) => (
                <div key={i} className="relative aspect-video bg-[var(--shimmer)]">
                  <Image src={url} alt="" fill className="object-cover" />
                </div>
              ))}
            </div>
          )}

          {/* Aksiyonlar */}
          <div className="flex items-center gap-5 mt-3">
            <button
              onClick={() => onComment?.(post.id)}
              className="flex items-center gap-1.5 text-xs transition-colors hover:text-[var(--primary)]"
              style={{ color: "var(--muted)" }}
            >
              <MessageCircle size={16} />
              {post.commentCount > 0 && post.commentCount}
            </button>

            <button
              onClick={() => onRepost?.(post.id)}
              className="flex items-center gap-1.5 text-xs transition-colors hover:text-[var(--success)]"
              style={{ color: "var(--muted)" }}
            >
              <Repeat2 size={16} />
              {post.repostCount > 0 && post.repostCount}
            </button>

            <button
              onClick={() => onLike?.(post.id)}
              className="flex items-center gap-1.5 text-xs transition-colors"
              style={{ color: liked ? "var(--error)" : "var(--muted)" }}
            >
              <Heart size={16} fill={liked ? "currentColor" : "none"} />
              {post.likeCount > 0 && post.likeCount}
            </button>

            <button
              className="flex items-center gap-1.5 text-xs transition-colors hover:text-[var(--primary)]"
              style={{ color: "var(--muted)" }}
            >
              <Share2 size={16} />
            </button>
          </div>
        </div>
      </div>
    </article>
  )
}
