// Android Models.kt → Notification data class karşılığı
export interface Notification {
  id:        string;
  userId:    string;
  fromUid:   string;
  fromName:  string;
  fromPhoto: string;
  type:      string;
  message:   string;
  sub:       string;
  postId?:   string;
  imageUrl:  string;
  url:       string;
  read:      boolean;
  ts:        unknown;
  status:    string; // 'accepted' | 'declined' | ''
}
