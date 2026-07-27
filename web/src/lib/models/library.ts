// Android Models.kt → Author, LibraryBook, BookQuote, BookReview karşılığı
export interface Author {
  id:            string;
  name:          string;
  bio:           string;
  photoURL:      string;
  birthYear:     number;
  nationality:   string;
  bookCount:     number;
  quoteCount:    number;
  reviewCount:   number;
  followerCount: number;
  // Client-state
  isFollowedByMe?: boolean;
}

export interface LibraryBook {
  id:          string;
  title:       string;
  authorId:    string;
  authorName:  string;
  coverImg:    string;
  genre:       string;
  publishYear: number;
  synopsis:    string;
  pageCount:   number;
  quoteCount:  number;
  reviewCount: number;
  avgRating:   number;
  likesCount:  number;
  ts:          unknown;
}

export interface BookQuote {
  id:              string;
  bookId:          string;
  authorId:        string;
  bookTitle:       string;
  authorName:      string;
  coverImg:        string;
  text:            string;
  uid:             string;
  userDisplayName: string;
  userPhotoURL:    string;
  feedPostId:      string;
  visibility:      string;
  likesCount:      number;
  ts:              unknown;
  // Client-state
  isLikedByMe?: boolean;
}

export interface BookReview {
  id:              string;
  bookId:          string;
  authorId:        string;
  bookTitle:       string;
  authorName:      string;
  text:            string;
  rating:          number;
  uid:             string;
  userDisplayName: string;
  userPhotoURL:    string;
  feedPostId:      string;
  likesCount:      number;
  ts:              unknown;
  // Client-state
  isLikedByMe?: boolean;
}

export interface ReadingListEntry {
  sid:         string;
  title:       string;
  coverImg:    string;
  bg:          string;
  status:      string;
  updatedAt:   unknown;
  source:      string;
  authorName:  string;
  currentPage: number;
}
