// Android Models.kt → User data class karşılığı
export interface User {
  uid:               string;
  displayName:       string;
  name:              string;
  username:          string;
  email:             string;
  photoURL:          string;
  coverPhoto:        string;
  bio:               string;
  website:           string;
  followersCount:    number;
  followingCount:    number;
  postsCount:        number;
  level:             number;
  xp:                number;
  streak:            number;
  booksRead:         number;
  quotesShared:      number;
  banned:            boolean;
  emailVerified:     boolean;
  isPrivate:         boolean;
  messagePermission: 'everyone' | 'followers' | 'nobody';
  createdAt:         number; // epoch millis
}

// Supabase users tablosu karşılığı (UserRow)
export interface UserRow {
  uid:           string;
  display_name:  string;
  photo_url:     string;
  bio:           string;
  banned:        boolean;
  created_at:    string;
  username:      string;
  username_lower:string;
}

// Takip ilişkisi (Supabase follows tablosu)
export interface FollowRow {
  id:          string;
  from_uid:    string;
  from_name:   string;
  from_photo:  string;
  target_uid:  string;
  target_name: string;
  target_photo:string;
  created_at:  string;
}
