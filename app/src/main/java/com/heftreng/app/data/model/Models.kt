package com.heftreng.app.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoURL: String = "",
    val bio: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isVerified: Boolean = false,
)

data class Post(
    val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoURL: String = "",
    val text: String = "",
    val imageURL: String = "",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val repostsCount: Int = 0,
    val ts: Timestamp? = null,
    // Quote post
    val quoteText: String = "",
    val bookName: String = "",
    val authorName: String = "",
    // Local state
    val isLikedByMe: Boolean = false,
    val isSavedByMe: Boolean = false,
)

data class Comment(
    val id: String = "",
    val postId: String = "",
    val uid: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val text: String = "",
    val likesCount: Int = 0,
    val replyTo: ReplyTo? = null,
    val ts: Timestamp? = null,
)

data class ReplyTo(
    val uid: String = "",
    val displayName: String = "",
)

data class Notification(
    val id: String = "",
    val userId: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromPhoto: String = "",
    val type: String = "",   // like, comment, follow, repost
    val message: String = "",
    val url: String = "",
    val read: Boolean = false,
    val ts: Timestamp? = null,
)

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: String = "",
    val read: Boolean = false,
)

data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageAt: String = "",
    val otherUser: User? = null,
    val unreadCount: Int = 0,
)
