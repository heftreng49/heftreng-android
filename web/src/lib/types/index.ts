export interface Post {
  id:          string
  uid:         string
  username:    string
  displayName: string
  photoURL:    string
  body:        string
  imageUrls:   string[]
  likeCount:   number
  commentCount:number
  repostCount: number
  isAnonymous: boolean
  isPinned:    boolean
  ts:          number
}

export interface User {
  uid:          string
  username:     string
  displayName:  string
  photoURL:     string
  bio:          string
  isAdmin:      boolean
  isVerified:   boolean
}

export type ThemeVariant = 'charcoal' | 'book' | 'forest' | 'ocean' | 'sunset' | 'mono'
export type ThemeMode    = 'dark' | 'light' | 'system'
