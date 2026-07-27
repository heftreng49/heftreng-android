// Android Models.kt → CmsPage, CmsCategory, AppConfig karşılığı
export interface CmsPage {
  id:        string;
  slug:      string;
  title:     string;
  body:      string;
  lang:      string;
  published: boolean;
  order:     number;
  updatedAt: unknown;
  updatedBy: string;
}

export interface CmsBanner {
  id:        string;
  title:     string;
  subtitle:  string;
  imageUrl:  string;
  linkUrl:   string;
  active:    boolean;
  order:     number;
  updatedAt: unknown;
}

export interface CmsAnnouncement {
  id:      string;
  title:   string;
  body:    string;
  type:    string;
  active:  boolean;
  linkUrl: string;
  ts:      unknown;
}

export interface CmsCategory {
  id:     string;
  name:   string;
  nameKu: string;
  slug:   string;
  order:  number;
}

export interface AppConfig {
  feedEnabled:          boolean;
  messagesEnabled:      boolean;
  serialsEnabled:       boolean;
  booksEnabled:         boolean;
  kurdiEnabled:         boolean;
  notificationsEnabled: boolean;
  searchEnabled:        boolean;
  storiesEnabled:       boolean;
  feedShowImages:       boolean;
  feedShowReposts:      boolean;
  feedAllowQuotes:      boolean;
  feedMaxTextLength:    number;
  messagesAllowImages:  boolean;
  messagesAllowVoice:   boolean;
  profileShowXp:        boolean;
  profileShowStreak:    boolean;
  profileShowBadges:    boolean;
  profileShowReadList:  boolean;
  kurdiShowWordOfDay:   boolean;
  maintenanceMode:      boolean;
  maintenanceMessage:   string;
  minVersion:           number;
  feedTitle:            string;
  messagesTitle:        string;
  kurdiTitle:           string;
}
