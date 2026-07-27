// Android Models.kt → Serial, Chapter, Book, BookChapter karşılığı
export interface Serial {
  id:           string;
  uid:          string;
  name:         string;
  photoURL:     string;
  title:        string;
  desc:         string;
  genre:        string;
  coverImg:     string;
  chapterCount: number;
  likes:        number;
  ts:           unknown;
  updatedAt:    unknown;
  // Client-state
  isLikedByMe?: boolean;
}

export interface Chapter {
  id:        string;
  serialId:  string;
  title:     string;
  body:      string;
  order:     number;
  wordCount: number;
  uid:       string;
  likes:     number;
  cmtCount:  number;
  ts:        unknown;
  // Client-state
  isLikedByMe?: boolean;
}

export interface Book {
  id:           string;
  uid:          string;
  name:         string;
  photoURL:     string;
  title:        string;
  desc:         string;
  genre:        string;
  coverImg:     string;
  bg:           string;
  chapterCount: number;
  likes:        number;
  ts:           unknown;
  updatedAt:    unknown;
  type:         string;
  // Client-state
  isLikedByMe?: boolean;
}

export interface BookChapter {
  id:        string;
  bookId:    string;
  serialId:  string;
  title:     string;
  body:      string;
  order:     number;
  wordCount: number;
  uid:       string;
  likes:     number;
  cmtCount:  number;
  ts:        unknown;
  // Client-state
  isLikedByMe?: boolean;
}
