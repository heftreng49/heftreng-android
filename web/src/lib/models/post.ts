// Android Models.kt → Post data class karşılığı
// @get:Exclude @set:Exclude alanları → optional client state
export interface Post {
  id:                      string;
  uid:                     string;
  displayName:             string;
  username:                string;
  photoURL:                string;
  title:                   string;
  category:                string;
  text:                    string;
  imgUrl:                  string;
  ytVid:                   string;
  badges:                  string[];
  repostTitle:             string;
  repostUrl:               string;
  repostImg:               string;
  imageURL:                string;
  likesCount:              number;
  commentsCount:           number;
  repostsCount:            number;
  ts:                      unknown; // Firestore Timestamp — tsToMs() ile çevrilmeli
  quoteText:               string;
  bookName:                string;
  authorName:              string;
  repostOf:                string;
  repostUid:               string;
  name:                    string;
  repostType:              string;
  repostId:                string;
  repostText:              string;
  repostAuthor:            string;
  repostAuthorPhoto:       string;
  repostAuthorUid:         string;
  serialTitle:             string;
  serialCover:             string;
  chapterTitle:            string;
  chapterOrder:            number;
  repostLevel:             number;
  repostXp:                number;
  repostStreak:            number;
  repostSerialId:          string;
  repostSerialTitle:       string;
  repostSerialDesc:        string;
  repostSerialCover:       string;
  repostSerialAuthorName:  string;
  repostSerialAuthorUid:   string;
  repostSerialBg:          string;
  repostSerialChCount:     number;
  serialId:                string;
  chapterId:               string;
  libraryBookId:           string;
  libraryAuthorId:         string;
  coverImg:                string;
  type:                    string;
  moderationStatus:        string;
  moderationNote:          string;
  moderationReason:        string;
  visibility:              string;
  mentions:                string[];
  // ── Client-state (@Exclude karşılığı) ──────────────────────────────
  isLikedByMe?:    boolean;
  isSavedByMe?:    boolean;
  isRepostedByMe?: boolean;
  myRepostId?:     string;
}

// Supabase satırları
export interface FeedLikeRow {
  id:          string;
  post_id:     string;
  uid:         string;
  name?:       string;
  photo_url?:  string;
  created_at?: string;
}

export interface FeedSaveRow {
  id:          string;
  post_id:     string;
  uid:         string;
  created_at?: string;
}
