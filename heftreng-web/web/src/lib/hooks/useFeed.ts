"use client"
import { useState, useEffect, useCallback } from "react"
import {
  collection, query, orderBy, limit,
  startAfter, getDocs, addDoc, updateDoc,
  deleteDoc, doc, serverTimestamp,
  onSnapshot, DocumentSnapshot,
} from "firebase/firestore"
import { db } from "@/lib/firebase/config"
import { useAuthStore } from "@/lib/store/authStore"
import type { Post } from "@/lib/types"

const PAGE_SIZE = 20

export function useFeed() {
  const [posts,   setPosts]   = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [hasMore,  setHasMore]  = useState(true)
  const [lastDoc,  setLastDoc]  = useState<DocumentSnapshot | null>(null)
  const { user } = useAuthStore()

  const fetchPosts = useCallback(async (after?: DocumentSnapshot) => {
    setLoading(true)
    const q = after
      ? query(collection(db, "posts"), orderBy("ts", "desc"), startAfter(after), limit(PAGE_SIZE))
      : query(collection(db, "posts"), orderBy("ts", "desc"), limit(PAGE_SIZE))

    const snap = await getDocs(q)
    const newPosts = snap.docs.map((d) => ({ id: d.id, ...d.data() } as Post))

    setPosts((prev) => after ? [...prev, ...newPosts] : newPosts)
    setLastDoc(snap.docs[snap.docs.length - 1] ?? null)
    setHasMore(snap.docs.length === PAGE_SIZE)
    setLoading(false)
  }, [])

  useEffect(() => { fetchPosts() }, [fetchPosts])

  const loadMore = () => { if (lastDoc) fetchPosts(lastDoc) }

  const createPost = async (body: string, imageUrls: string[] = []) => {
    if (!user) return
    await addDoc(collection(db, "posts"), {
      uid:         user.uid,
      username:    user.username,
      displayName: user.displayName,
      photoURL:    user.photoURL,
      body,
      imageUrls,
      likeCount:   0,
      commentCount:0,
      repostCount: 0,
      repostType:  "",
      repostSourceId:  "",
      repostSourceUid: "",
      isAnonymous: false,
      isPinned:    false,
      ts:          serverTimestamp(),
    })
    fetchPosts()
  }

  const deletePost = async (postId: string) => {
    await deleteDoc(doc(db, "posts", postId))
    setPosts((prev) => prev.filter((p) => p.id !== postId))
  }

  return { posts, loading, hasMore, loadMore, createPost, deletePost, refresh: () => fetchPosts() }
}
