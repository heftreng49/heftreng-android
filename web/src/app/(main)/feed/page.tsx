"use client"
import { useFeed }        from "@/lib/hooks/useFeed"
import { useAppConfig }   from "@/lib/hooks/useAppConfig"
import { PostCard }       from "@/components/feed/PostCard"
import { PostComposer }   from "@/components/feed/PostComposer"
import { Skeleton }       from "@/components/ui/Skeleton"
import { AnnouncementBanner } from "@/components/feed/AnnouncementBanner"

export default function FeedPage() {
  const { posts, loading, hasMore, loadMore, createPost } = useFeed()
  const { config } = useAppConfig()

  return (
    <div
      className="min-h-screen border-x"
      style={{ borderColor: "var(--divider)" }}
    >
      {/* Duyuru */}
      <AnnouncementBanner />

      {/* Yazı oluşturucu */}
      {config.feedEnabled && (
        <PostComposer
          onPost={createPost}
          maxLength={config.feedMaxTextLength || 1000}
        />
      )}

      {/* Gönderi listesi */}
      {loading && posts.length === 0 ? (
        <div className="divide-y" style={{ borderColor: "var(--divider)" }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <PostSkeleton key={i} />
          ))}
        </div>
      ) : posts.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
          <p className="text-lg font-semibold" style={{ color: "var(--on-bg)" }}>
            Henüz gönderi yok
          </p>
          <p className="text-sm" style={{ color: "var(--muted)" }}>
            İlk gönderiyi sen yaz!
          </p>
        </div>
      ) : (
        <>
          {posts.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}

          {hasMore && (
            <div className="py-6 flex justify-center">
              <button
                onClick={loadMore}
                disabled={loading}
                className="px-6 py-2 rounded-xl text-sm font-medium border transition-colors"
                style={{
                  borderColor: "var(--divider)",
                  color: "var(--on-surface)",
                }}
              >
                {loading ? "Yükleniyor..." : "Daha Fazla"}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function PostSkeleton() {
  return (
    <div className="px-4 py-4 flex gap-3" style={{ borderBottom: "1px solid var(--divider)" }}>
      <Skeleton className="w-10 h-10 rounded-full shrink-0" />
      <div className="flex-1 flex flex-col gap-2">
        <Skeleton className="h-4 w-32 rounded" />
        <Skeleton className="h-3 w-full rounded" />
        <Skeleton className="h-3 w-3/4 rounded" />
      </div>
    </div>
  )
}
