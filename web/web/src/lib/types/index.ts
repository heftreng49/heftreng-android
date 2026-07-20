// ─────────────────────────────────────────────────────────────────────────────
// Heftreng Web — TypeScript Tipleri
// Android Models.kt karşılığı
// ─────────────────────────────────────────────────────────────────────────────

export interface User {
  uid:            string
  username:       string
  displayName:    string
  photoURL:       string
  bio:            string
  language:       "tr" | "ku"
  themeVariant:   string
  themeMode:      "dark" | "light" | "system"
  followersCount: number
  followingCount: number
  postCount:      number
  isAdmin:        boolean
  isVerified:     boolean
  createdAt:      number
}

export interface Post {
  id:              string
  uid:             string
  username:        string
  displayName:     string
  photoURL:        string
  body:            string
  imageUrls:       string[]
  likeCount:       number
  commentCount:    number
  repostCount:     number
  repostType:      string
  repostSourceId:  string
  repostSourceUid: string
  isAnonymous:     boolean
  isPinned:        boolean
  ts:              number
}

export interface Comment {
  id:          string
  postId:      string
  uid:         string
  username:    string
  displayName: string
  photoURL:    string
  body:        string
  likeCount:   number
  ts:          number
  replyTo?:    ReplyTo
}

export interface ReplyTo {
  commentId:   string
  username:    string
  displayName: string
}

export interface Notification {
  id:          string
  uid:         string
  type:        string
  fromUid:     string
  fromName:    string
  fromPhoto:   string
  postId?:     string
  commentId?:  string
  message:     string
  read:        boolean
  ts:          number
}

export interface Message {
  id:        string
  convId:    string
  uid:       string
  text:      string
  imageUrl?: string
  read:      boolean
  ts:        number
}

export interface Conversation {
  id:           string
  participants: string[]
  lastMessage:  string
  lastTs:       number
  unreadCount:  number
}

export interface Serial {
  id:           string
  uid:          string
  title:        string
  description:  string
  coverUrl:     string
  genre:        string
  language:     string
  chapterCount: number
  likeCount:    number
  active:       boolean
  ts:           number
}

export interface Chapter {
  id:       string
  serialId: string
  title:    string
  content:  string
  order:    number
  ts:       number
}

export interface Book {
  id:          string
  title:       string
  author:      string
  description: string
  coverUrl:    string
  genre:       string
  pageCount:   number
  ts:          number
}

export interface BookChapter {
  id:      string
  bookId:  string
  title:   string
  content: string
  order:   number
}

export interface ReadingListEntry {
  id:       string
  uid:      string
  bookId:   string
  serialId: string
  progress: number
  addedAt:  number
}

export interface KurdiLesson {
  id:        string
  unitId:    string
  title:     string
  titleTr:   string
  content:   string   // Markdown
  contentTr: string   // Markdown
  order:     number
  type:      string   // "lesson" | "grammar" | "vocab" | "exercise"
  active:    boolean
}

export interface CmsPage {
  id:     string
  slug:   string
  title:  string
  body:   string    // Markdown
  active: boolean
}

export interface CmsBanner {
  id:        string
  title:     string
  subtitle:  string
  imageUrl:  string
  linkUrl:   string
  active:    boolean
  order:     number
}

export interface CmsAnnouncement {
  id:      string
  title:   string
  body:    string
  type:    "info" | "warning" | "error"
  active:  boolean
  linkUrl: string
}

export interface AppConfig {
  maintenanceMode:      boolean
  minVersion:           number
  feedEnabled:          boolean
  messagesEnabled:      boolean
  serialsEnabled:       boolean
  booksEnabled:         boolean
  kurdiEnabled:         boolean
  notificationsEnabled: boolean
  searchEnabled:        boolean
  feedAllowQuotes:      boolean
  feedShowImages:       boolean
  feedShowReposts:      boolean
  feedMaxTextLength:    number
  feedTitle:            string
  messagesTitle:        string
  kurdiTitle:           string
}

export interface Author {
  id:        string
  name:      string
  bio:       string
  photoUrl:  string
  bookCount: number
}

export interface BookQuote {
  id:       string
  bookId:   string
  authorId: string
  text:     string
  page:     number
  likeCount:number
}

// Tema
export type ThemeVariant =
  | "charcoal" | "book" | "forest"
  | "ocean"    | "sunset" | "mono"

export type ThemeMode = "dark" | "light" | "system"

export interface ThemePreference {
  variant: ThemeVariant
  mode:    ThemeMode
}
