package com.heftreng.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Supabase DTO'ları — tüm ViewModel'ler buradan import eder
// ══════════════════════════════════════════════════════════════

@Serializable
data class FollowRow(
    val id           : String = "",
    @SerialName("from_uid")     val fromUid    : String = "",
    @SerialName("from_name")    val fromName   : String = "",
    @SerialName("from_photo")   val fromPhoto  : String = "",
    @SerialName("target_uid")   val targetUid  : String = "",
    @SerialName("target_name")  val targetName : String = "",
    @SerialName("target_photo") val targetPhoto: String = "",
    @SerialName("created_at")   val createdAt  : String = "",
)

@Serializable
data class FeedLikeRow(
    val id        : String = "",
    @SerialName("post_id")    val postId   : String = "",
    val uid       : String = "",
    val name      : String = "",
    @SerialName("photo_url")  val photoUrl : String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class FeedSaveRow(
    val id        : String = "",
    @SerialName("post_id")    val postId   : String = "",
    val uid       : String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CommentLikeRow(
    val id         : String = "",
    @SerialName("comment_id") val commentId: String = "",
    val uid        : String = "",
    val name       : String = "",
    @SerialName("photo_url")  val photoUrl : String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SerialLikeRow(
    val id        : String = "",
    @SerialName("serial_id")  val serialId : String = "",
    val uid       : String = "",
    val name      : String = "",
    @SerialName("photo_url")  val photoUrl : String = "",
    @SerialName("created_at") val createdAt: String = "",
)
