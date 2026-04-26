package com.heftreng.app.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val displayName: String = "",
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val photoURL: String = "",
    val coverPhoto: String = "",
    val bio: String = "",
    val website: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val level: Int = 1,
    val xp: Int = 0,
    val streak: Int = 0,
)

data class Post(
    val id: String = "",
    val uid: String = "",
    val text: String = "",
    val imageURL: String = "",
    val quoteText: String = "",
    val authorName: String = "",
    val bookName: String = "",
    val ts: Timestamp? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val repostsCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val isSavedByMe: Boolean = false,
    val displayName: String = "",
    val username: String = "",
    val photoURL: String = "",
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
    val type: String = "",       // like | comment | follow | repost
    val message: String = "",
    val postId: String? = null,  // Yönlendirme için
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

data class KurdiLesson(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val type: String = "",
    val xpReward: Int = 10,
    val completed: Boolean = false,
    val order: Int = 0,
)

data class ReadingListEntry(
    val id       : String = "",
    val uid      : String = "",
    val title    : String = "",
    val coverImg : String = "",
    val author   : String = "",
    val addedAt  : com.google.firebase.Timestamp? = null,
)

data class Serial(
    val id           : String = "",
    val uid          : String = "",
    val title        : String = "",
    val desc         : String = "",
    val coverImg     : String = "",
    val genre        : String = "",
    val photoURL     : String = "",
    val name         : String = "",
    val chapterCount : Int    = 0,
    val likes        : Int    = 0,
    val isLikedByMe  : Boolean = false,
    val ts           : com.google.firebase.Timestamp? = null,
)

data class Chapter(
    val id        : String = "",
    val serialId  : String = "",
    val title     : String = "",
    val body      : String = "",
    val order     : Int    = 0,
    val wordCount : Int    = 0,
    val ts        : com.google.firebase.Timestamp? = null,
)
