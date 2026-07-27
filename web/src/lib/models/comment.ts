// Android Models.kt → Comment + ReplyTo karşılığı
export interface ReplyTo {
  commentId:   string;
  uid:         string;
  displayName: string;
}

export interface Comment {
  id:          string;
  postId:      string;
  uid:         string;
  displayName: string;
  photoURL:    string;
  text:        string;
  likesCount:  number;
  replyTo?:    ReplyTo;
  ts:          unknown;
  mentions:    string[];
  // Client-state
  isLikedByMe?: boolean;
}

// Supabase feed_comments tablosu (FeedCommentRow)
export interface FeedCommentRow {
  id:               string;
  post_id:          string;
  uid:              string;
  name?:            string;
  photo_url?:       string;
  text:             string;
  likes_count:      number;
  reply_to_cmt_id?: string;
  mentions?:        string[];
  created_at?:      string;
}

// INSERT payload
export interface FeedCommentInsert {
  post_id:          string;
  uid:              string;
  name:             string;
  photo_url:        string;
  text:             string;
  reply_to_cmt_id?: string;
  mentions?:        string[];
}

export interface CommentLikeRow {
  id:          string;
  comment_id:  string;
  uid:         string;
  name:        string;
  photo_url:   string;
  created_at:  string;
}
