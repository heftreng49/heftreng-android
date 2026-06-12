package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.CommentLikeRow
import com.heftreng.app.data.model.FeedLikeRow
import com.heftreng.app.data.model.FollowEntry
import com.heftreng.app.data.model.FollowRow
import com.heftreng.app.data.model.LikeEntry
import com.heftreng.app.data.model.SerialLikeRow
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase : SupabaseClient,
) : ViewModel() {

    private val _followers        = MutableStateFlow<List<FollowEntry>>(emptyList())
    val followers = _followers.asStateFlow()

    private val _following        = MutableStateFlow<List<FollowEntry>>(emptyList())
    val following = _following.asStateFlow()

    private val _likers           = MutableStateFlow<List<LikeEntry>>(emptyList())
    val likers = _likers.asStateFlow()

    private val _loading          = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _followersLoading = MutableStateFlow(false)
    val followersLoading = _followersLoading.asStateFlow()

    private val _followingLoading = MutableStateFlow(false)
    val followingLoading = _followingLoading.asStateFlow()

    private val _hasMoreFollowers = MutableStateFlow(false)
    val hasMoreFollowers = _hasMoreFollowers.asStateFlow()

    private val _hasMoreFollowing = MutableStateFlow(false)
    val hasMoreFollowing = _hasMoreFollowing.asStateFlow()

    private val PAGE = 20
    private var followersOffset = 0
    private var followingOffset = 0
    private var followersTargetUid = ""
    private var followingTargetUid = ""

    private val userCache = mutableMapOf<String, Pair<String, String>>()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Beğenenler ────────────────────────────────────────────────────────────

    fun loadPostLikers(postId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val rows = supabase.postgrest["feed_likes"]
                    .select { filter { eq("post_id", postId) }; limit(50); order("created_at", Order.DESCENDING) }
                    .decodeList<FeedLikeRow>()
                _likers.value = enrichLikes(rows.map { LikeEntry(uid = it.uid, name = it.name, photoURL = it.photoUrl) })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadPostLikers: ${e.message}")
            } finally { _loading.value = false }
        }
    }

    fun loadCommentLikers(commentId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val rows = supabase.postgrest["comment_likes"]
                    .select { filter { eq("comment_id", commentId) }; limit(50) }
                    .decodeList<CommentLikeRow>()
                _likers.value = enrichLikes(rows.map { LikeEntry(uid = it.uid, name = it.name, photoURL = it.photoUrl) })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadCommentLikers: ${e.message}")
            } finally { _loading.value = false }
        }
    }

    fun loadSerialLikers(serialId: String) {
        viewModelScope.launch {
            _loading.value = true
            _likers.value  = emptyList()
            try {
                val rows = supabase.postgrest["serial_likes"]
                    .select { filter { eq("serial_id", serialId) }; limit(50) }
                    .decodeList<SerialLikeRow>()
                _likers.value = enrichLikes(rows.map { LikeEntry(uid = it.uid, name = it.name, photoURL = it.photoUrl) })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadSerialLikers: ${e.message}")
            } finally { _loading.value = false }
        }
    }

    // ── Takipçiler ────────────────────────────────────────────────────────────

    fun loadFollowers(targetUid: String) {
        if (followersTargetUid == targetUid && _followers.value.isNotEmpty()) return
        followersTargetUid = targetUid
        followersOffset    = 0
        viewModelScope.launch {
            _followersLoading.value = true
            _followers.value = emptyList()
            try {
                val rows = supabase.postgrest["follows"]
                    .select {
                        filter { eq("target_uid", targetUid) }
                        order("created_at", Order.DESCENDING)
                        range(0L, (PAGE - 1).toLong())
                    }
                    .decodeList<FollowRow>()
                followersOffset = rows.size
                _hasMoreFollowers.value = rows.size >= PAGE
                _followers.value = enrichFollows(rows.map {
                    FollowEntry(uid = it.fromUid, name = it.fromName, photoURL = it.fromPhoto)
                })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadFollowers: ${e.message}")
            } finally { _followersLoading.value = false }
        }
    }

    fun loadMoreFollowers() {
        if (_followersLoading.value || !_hasMoreFollowers.value) return
        viewModelScope.launch {
            _followersLoading.value = true
            try {
                val from = followersOffset.toLong()
                val to   = (followersOffset + PAGE - 1).toLong()
                val rows = supabase.postgrest["follows"]
                    .select {
                        filter { eq("target_uid", followersTargetUid) }
                        order("created_at", Order.DESCENDING)
                        range(from, to)
                    }
                    .decodeList<FollowRow>()
                followersOffset += rows.size
                _hasMoreFollowers.value = rows.size >= PAGE
                _followers.value = _followers.value + enrichFollows(rows.map {
                    FollowEntry(uid = it.fromUid, name = it.fromName, photoURL = it.fromPhoto)
                })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadMoreFollowers: ${e.message}")
            } finally { _followersLoading.value = false }
        }
    }

    // ── Takip edilenler ───────────────────────────────────────────────────────

    fun loadFollowing(targetUid: String) {
        if (followingTargetUid == targetUid && _following.value.isNotEmpty()) return
        followingTargetUid = targetUid
        followingOffset    = 0
        viewModelScope.launch {
            _followingLoading.value = true
            _following.value = emptyList()
            try {
                val rows = supabase.postgrest["follows"]
                    .select {
                        filter { eq("from_uid", targetUid) }
                        order("created_at", Order.DESCENDING)
                        range(0L, (PAGE - 1).toLong())
                    }
                    .decodeList<FollowRow>()
                followingOffset = rows.size
                _hasMoreFollowing.value = rows.size >= PAGE
                _following.value = enrichFollows(rows.map {
                    FollowEntry(uid = it.targetUid, name = it.targetName, photoURL = it.targetPhoto)
                })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadFollowing: ${e.message}")
            } finally { _followingLoading.value = false }
        }
    }

    fun loadMoreFollowing() {
        if (_followingLoading.value || !_hasMoreFollowing.value) return
        viewModelScope.launch {
            _followingLoading.value = true
            try {
                val from = followingOffset.toLong()
                val to   = (followingOffset + PAGE - 1).toLong()
                val rows = supabase.postgrest["follows"]
                    .select {
                        filter { eq("from_uid", followingTargetUid) }
                        order("created_at", Order.DESCENDING)
                        range(from, to)
                    }
                    .decodeList<FollowRow>()
                followingOffset += rows.size
                _hasMoreFollowing.value = rows.size >= PAGE
                _following.value = _following.value + enrichFollows(rows.map {
                    FollowEntry(uid = it.targetUid, name = it.targetName, photoURL = it.targetPhoto)
                })
            } catch (e: Exception) {
                android.util.Log.e("SocialVM", "loadMoreFollowing: ${e.message}")
            } finally { _followingLoading.value = false }
        }
    }

    fun clearLikers()    { _likers.value    = emptyList() }
    fun clearFollowers() { _followers.value = emptyList(); followersTargetUid = ""; followersOffset = 0; _hasMoreFollowers.value = false }
    fun clearFollowing() { _following.value = emptyList(); followingTargetUid = ""; followingOffset = 0; _hasMoreFollowing.value = false }

    // ── Enrich ───────────────────────────────────────────────────────────────

    private suspend fun enrichLikes(entries: List<LikeEntry>): List<LikeEntry> {
        fetchUsersToCache(entries.filter { it.name.isBlank() && it.uid !in userCache }.map { it.uid }.distinct())
        return entries.map { e ->
            val c = userCache[e.uid] ?: return@map e
            e.copy(name = c.first.takeIf { it.isNotBlank() } ?: e.name, photoURL = c.second.takeIf { it.isNotBlank() } ?: e.photoURL)
        }
    }

    private suspend fun enrichFollows(entries: List<FollowEntry>): List<FollowEntry> {
        fetchUsersToCache(entries.filter { it.name.isBlank() && it.uid !in userCache }.map { it.uid }.distinct())
        return entries.map { e ->
            val c = userCache[e.uid] ?: return@map e
            e.copy(name = c.first.takeIf { it.isNotBlank() } ?: e.name, photoURL = c.second.takeIf { it.isNotBlank() } ?: e.photoURL)
        }
    }

    private suspend fun fetchUsersToCache(uids: List<String>) {
        if (uids.isEmpty()) return
        uids.chunked(10).forEach { chunk ->
            try {
                firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await().documents.forEach { doc ->
                        val d = doc.data ?: return@forEach
                        userCache[doc.id] = Pair(
                            (d["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: (d["name"] as? String) ?: "",
                            (d["photoURL"] as? String) ?: "",
                        )
                    }
            } catch (_: Exception) {}
        }
    }
}
