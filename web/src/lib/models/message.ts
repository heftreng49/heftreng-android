// Android Models.kt → Message + Conversation karşılığı
import type { User } from './user';

export interface Message {
  id:             string;
  conversationId: string;
  senderId:       string;
  text:           string;
  imageUrl:       string;
  audioUrl:       string;
  createdAt:      unknown;
  read:           boolean;
  readAt:         unknown;
  deleted:        boolean;
  edited:         boolean;
  replyToId:      string;
  replyToText:    string;
  replyToName:    string;
  isLikedByMe:    boolean;
  likesCount:     number;
  mentions:       string[];
}

export interface Conversation {
  id:             string;
  participantIds: string[];
  lastMessage:    string;
  lastMessageAt:  unknown;
  unreadCount:    number;
  // Client-state (@Exclude karşılığı)
  otherUser?:     User;
}
