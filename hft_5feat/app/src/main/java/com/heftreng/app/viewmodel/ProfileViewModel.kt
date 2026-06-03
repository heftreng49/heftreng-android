package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Post
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    private val _followersCount = MutableStateFlow(0)
    val followersCount = _followersCount.asStateFlow()

    private val _followingCount = MutableStateFlow(0)
    val followingCount = _followingCount.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _savedPosts = MutableStateFlow<List<Post>>(emptyList())
    val savedPosts = _savedPosts.asStateFlow()

    private val _savedLoading = MutableStateFlow(false)
    val savedLoading = _savedLoading.asStateFlow()

    val myUid get() = auth.currentUser?.uid ?: ""

    fun load(uid: String, preloadedUser: User? = null) {
        val targetUid = if (uid == "me") myUid else uid

        // ── 1. Eğer önceki ekrandan User verisi geldiyse anında göster ──────
        if (preloadedUser != null && _user.value == null) {
            _user.value = preloadedUser
        }

        viewModelScope.launch {
            // Loading'i sadece liste boşsa göster — ikinci açılışta çark gözükmez
            _loading.value = _posts.value.isEmpty()
            try {
                // ── 2. Paralel fetch: user + follow durumu aynı anda ──────────
                val userDocDeferred = viewModelScope.async {
                    firestore.collection("users").document(targetUid).get().await()
                }
                val followDocDeferred = viewModelScope.async {
                    if (targetUid != myUid && myUid.isNotEmpty())
                        firestore.collection("follows").document("${myUid}_$targetUid").get().await()
                    else null
                }
                val followersDeferred = viewModelScope.async {
                    firestore.collection("follows").whereEqualTo("targetUid", targetUid).get().await()
                }
                val followingDeferred = viewModelScope.async {
                    firestore.collection("follows").whereEqualTo("fromUid", targetUid).get().await()
                }

                val userDoc    = userDocDeferred.await()
                val followDoc  = followDocDeferred.await()
                val followersSnap = followersDeferred.await()
                val followingSnap = followingDeferred.await()

                val d = userDoc.data ?: return@launch
                _user.value = User(
                    uid         = d["uid"] as? String ?: targetUid,
                    displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                    name        = d["name"] as? String ?: "",
                    username    = d["username"] as? String ?: (d["email"] as? String)?.substringBefore("@") ?: "",
                    email       = d["email"] as? String ?: "",
                    photoURL    = d["photoURL"] as? String ?: "",
                    coverPhoto  = d["coverPhoto"] as? String ?: "",
                    bio         = d["bio"] as? String ?: "",
                    website     = d["website"] as? String ?: "",
                    level       = (d["level"] as? Long)?.toInt() ?: 1,
                    xp          = (d["xp"] as? Long)?.toInt() ?: 0,
                    streak      = (d["streak"] as? Long)?.toInt() ?: 0,
                    isPrivate          = d["private"]           as? Boolean ?: false,
                    messagePermission  = d["messagePermission"] as? String  ?: "everyone",
                )

                if (followDoc != null) _isFollowing.value = followDoc.exists()
                _followersCount.value = followersSnap.size()
                _followingCount.value = followingSnap.size()

                val isOwnProfile  = (targetUid == myUid)
                val isPrivate     = _user.value?.isPrivate ?: false
                val canSeeContent = isOwnProfile || !isPrivate || _isFollowing.value

                if (!canSeeContent) { _posts.value = emptyList(); return@launch }

                // ── 3. Gönderileri çek — her post için ayrı likeDoc await() YOK ──
                val snap = firestore.collection("feed")
                    .whereEqualTo("uid", targetUid)
                    .limit(50).get().await()

                // Beğenilen ID'leri tek toplu sorguyla al
                val postIds = snap.documents.map { it.id }
                val likedIds = if (myUid.isNotEmpty() && postIds.isNotEmpty()) {
                    val likeKeys = postIds.map { "${it}_$myUid" }
                    likeKeys.chunked(10).flatMap { chunk ->
                        try {
                            firestore.collection("feedLikes")
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                .get().await().documents.map { it.id.substringBefore("_$myUid") }
                        } catch (_: Exception) { emptyList() }
                    }.toSet()
                } else emptySet()

                val rawPosts = snap.documents.mapNotNull { doc ->
                    val fd = doc.data ?: return@mapNotNull null
                    val postText = fd["text"] as? String ?: ""
                    val imageURL = fd["imageURL"] as? String ?: fd["imgUrl"] as? String ?: ""
                    val quoteObj = fd["quote"] as? Map<*, *>
                    val quoteText  = (quoteObj?.get("text")   as? String)?.takeIf { it.isNotBlank() } ?: fd["quoteText"]  as? String ?: ""
                    val bookName   = (quoteObj?.get("book")   as? String)?.takeIf { it.isNotBlank() } ?: fd["bookName"]   as? String ?: ""
                    val authorName = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() } ?: fd["authorName"] as? String ?: ""
                    val repostOf   = fd["repostOf"] as? String ?: fd["repostType"] as? String ?: ""
                    if (postText.isBlank() && imageURL.isBlank() && quoteText.isBlank() && repostOf.isBlank()) return@mapNotNull null

                    Post(
                        id            = doc.id,
                        uid           = fd["uid"]         as? String ?: "",
                        displayName   = (fd["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: fd["name"] as? String ?: "",
                        username      = fd["username"]    as? String ?: "",
                        photoURL      = fd["photoURL"]    as? String ?: "",
                        text          = postText,
                        imageURL      = imageURL,
                        quoteText     = quoteText,
                        bookName      = bookName,
                        authorName    = authorName,
                        likesCount    = (fd["likes"]    as? Long)?.toInt() ?: 0,
                        commentsCount = (fd["cmtCount"] as? Long)?.toInt() ?: 0,
                        repostsCount  = (fd["reposts"]  as? Long)?.toInt() ?: 0,
                        isLikedByMe   = doc.id in likedIds,
                        ts            = fd["ts"] as? Timestamp,
                    )
                }.sortedByDescending { it.ts?.seconds ?: 0L }

                // ── 4. Denormalize veriyle anında ekrana bas ──────────────────
                _posts.value = rawPosts
                _loading.value = false

                // ── 5. Arka planda güncel avatar/isim ile sessizce güncelle ──
                enrichPostsInBackground(rawPosts)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    private fun enrichPostsInBackground(
        posts: List<Post>,
        target: MutableStateFlow<List<Post>> = _posts,
    ) {
        if (posts.isEmpty()) return
        viewModelScope.launch {
            val uids = posts.map { it.uid }.filter { it.isNotBlank() }.distinct()
            val userMap = mutableMapOf<String, Pair<String, String>>()
            uids.chunked(10).forEach { chunk ->
                try {
                    val snap = firestore.collection("users")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()
                    snap.documents.forEach { doc ->
                        val name  = (doc.getString("displayName") ?: doc.getString("name") ?: "").takeIf { it.isNotBlank() } ?: ""
                        val photo = doc.getString("photoURL") ?: ""
                        userMap[doc.id] = name to photo
                    }
                } catch (_: Exception) {}
            }
            if (userMap.isEmpty()) return@launch
            val updated = target.value.map { post ->
                val (freshName, freshPhoto) = userMap[post.uid] ?: return@map post
                post.copy(
                    displayName = freshName.ifBlank { post.displayName },
                    photoURL    = freshPhoto.ifBlank { post.photoURL },
                )
            }
            target.value = updated
        }
    }

    fun toggleFollow(targetUid: String) {
        viewModelScope.launch {
            try {
                val followDoc = firestore.collection("follows").document("${myUid}_$targetUid")
                if (_isFollowing.value) {
                    followDoc.delete().await()
                    _isFollowing.value = false
                    _followersCount.value = (_followersCount.value - 1).coerceAtLeast(0)
                } else {
                    // XML: follows/{fromUid_targetUid} şeması —
                    // fromUid, fromName, fromPhoto, targetUid, targetName, targetPhoto, ts
                    val myDoc       = firestore.collection("users").document(myUid).get().await()
                    val fromName    = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                    val fromPhoto   = myDoc.getString("photoURL") ?: ""
                    val targetDoc   = firestore.collection("users").document(targetUid).get().await()
                    val targetName  = targetDoc.getString("displayName") ?: targetDoc.getString("name") ?: ""
                    val targetPhoto = targetDoc.getString("photoURL") ?: ""
                    followDoc.set(mapOf(
                        "fromUid"     to myUid,
                        "fromName"    to fromName,
                        "fromPhoto"   to fromPhoto,
                        "targetUid"   to targetUid,
                        "targetName"  to targetName,
                        "targetPhoto" to targetPhoto,
                        "ts"          to Timestamp.now(),
                    )).await()
                    _isFollowing.value = true
                    _followersCount.value += 1
                    // Bildirim
                    firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                        "fromUid"   to myUid,
                        "fromName"  to fromName,
                        "fromPhoto" to fromPhoto,
                        "type"      to "follow",
                        "feedId"    to "",          // tema: feedId (follow'da boş)
                        "postId"    to "",          // Android uyumu
                        "title"     to "$fromName seni takip etmeye başladı",
                        "sub"       to "",
                        "ico"       to "person_add",
                        "message"   to "$fromName seni takip etmeye başladı",
                        "url"       to "",
                        "read"      to false,
                        "ts"        to Timestamp.now(),
                    )).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateProfile(displayName: String, bio: String, website: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(myUid).update(mapOf(
                    "displayName" to displayName,
                    "name"        to displayName,
                    "bio"         to bio,
                    "website"     to website,
                )).await()
                _user.value = _user.value?.copy(displayName = displayName, bio = bio, website = website)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    // ── Profil gönderilerinde beğeni / silme / düzenleme ─────────────────────
    fun toggleLikePost(post: com.heftreng.app.data.model.Post) {
        if (myUid.isEmpty()) return
        val nowLiked = !post.isLikedByMe
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = it.likesCount + if (nowLiked) 1 else -1
            ) else it
        }
        viewModelScope.launch {
            try {
                val ref  = firestore.collection("feedLikes").document("${post.id}_$myUid")
                val pRef = firestore.collection("feed").document(post.id)
                if (nowLiked) {
                    val me     = firestore.collection("users").document(myUid).get().await()
                    val myName = me.getString("displayName") ?: me.getString("name") ?: ""
                    val myPhoto = me.getString("photoURL") ?: ""
                    ref.set(mapOf(
                        "uid"      to myUid,
                        "feedId"   to post.id,
                        "name"     to myName,
                        "photoURL" to myPhoto,
                        "ts"       to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )).await()
                    pRef.update("likes", com.google.firebase.firestore.FieldValue.increment(1)).await()
                    // Bildirim — kendi gönderisini beğenirse gönderme
                    if (post.uid.isNotEmpty() && post.uid != myUid) {
                        firestore.collection("userNotifs").document(post.uid).collection("msgs").add(mapOf(
                            "fromUid"   to myUid,
                            "fromName"  to myName,
                            "fromPhoto" to myPhoto,
                            "type"      to "like",
                            "feedId"    to post.id,
                            "postId"    to post.id,
                            "title"     to "$myName gönderini beğendi",
                            "sub"       to post.text.take(60),
                            "ico"       to "favorite",
                            "ts"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        )).await()
                    }
                } else {
                    ref.delete().await()
                    pRef.update("likes", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteOwnPost(postId: String) {
        if (myUid.isEmpty()) return
        _posts.value = _posts.value.filter { it.id != postId }
        viewModelScope.launch {
            try {
                // Güvenlik: post sahibi mi kontrol et
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != myUid) return@launch
                firestore.collection("feed").document(postId).delete().await()
            }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editOwnPost(postId: String, newText: String) {
        if (myUid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                // Güvenlik: post sahibi mi kontrol et
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != myUid) return@launch
                firestore.collection("feed").document(postId).update("text", newText).await()
                _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText) else it }
            }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kaydedilen gönderiler (Beğendikleri tab) ─────────────────────────────
    fun loadSavedPosts() {
        val uid = myUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _savedLoading.value = true
            try {
                // feedSaves/{postId_uid} — site ile aynı koleksiyon
                val savesSnap = firestore.collection("feedSaves")
                    .whereEqualTo("uid", uid)
                    .limit(30).get().await()

                val postIds = savesSnap.documents.mapNotNull { it.getString("feedId") }
                    .filter { it.isNotBlank() }.distinct()

                if (postIds.isEmpty()) { _savedPosts.value = emptyList(); return@launch }

                // Batch: 10'ar 10'ar (Firestore in-query limiti)
                val posts = mutableListOf<Post>()
                postIds.chunked(10).forEach { chunk ->
                    val snap = firestore.collection("feed")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()
                    snap.documents.forEach { doc ->
                        val fd = doc.data ?: return@forEach
                        val quoteObj   = fd["quote"] as? Map<*, *>
                        posts.add(Post(
                            id            = doc.id,
                            uid           = fd["uid"]      as? String ?: "",
                            displayName   = (fd["name"]    as? String)?.takeIf { it.isNotBlank() } ?: "",
                            username      = fd["username"] as? String ?: "",
                            photoURL      = fd["photoURL"] as? String ?: "",
                            text          = fd["text"]     as? String ?: "",
                            imageURL      = fd["imgUrl"]   as? String ?: fd["imageURL"] as? String ?: "",
                            quoteText     = (quoteObj?.get("text")   as? String) ?: fd["quoteText"]  as? String ?: "",
                            bookName      = (quoteObj?.get("book")   as? String) ?: fd["bookName"]   as? String ?: "",
                            authorName    = (quoteObj?.get("author") as? String) ?: fd["authorName"] as? String ?: "",
                            likesCount    = (fd["likes"]    as? Long)?.toInt() ?: 0,
                            commentsCount = (fd["cmtCount"] as? Long)?.toInt() ?: 0,
                            repostsCount  = (fd["reposts"]  as? Long)?.toInt() ?: 0,
                            ts            = fd["ts"] as? com.google.firebase.Timestamp,
                        ))
                    }
                }
                val sorted = posts.sortedByDescending { it.ts?.seconds ?: 0L }
                _savedPosts.value = sorted
                enrichPostsInBackground(sorted, _savedPosts)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _savedLoading.value = false
            }
        }
    }

    // ── Profil / Kapak fotoğrafı güncelle ────────────────────────────────────
    fun updateProfilePhoto(
        imageUri: android.net.Uri,
        storage : com.google.firebase.storage.FirebaseStorage,
        onDone  : (String) -> Unit = {},
        onError : (String) -> Unit = {},
    ) {
        if (myUid.isEmpty()) { onError("Kullanıcı bulunamadı"); return }
        _loading.value = true
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("profile_photos/${myUid}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                firestore.collection("users").document(myUid).update("photoURL", url).await()
                _user.value = _user.value?.copy(photoURL = url)
                onDone(url)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Fotoğraf yüklenemedi")
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateCoverPhoto(
        imageUri: android.net.Uri,
        storage : com.google.firebase.storage.FirebaseStorage,
        onDone  : (String) -> Unit = {},
        onError : (String) -> Unit = {},
    ) {
        if (myUid.isEmpty()) { onError("Kullanıcı bulunamadı"); return }
        _loading.value = true
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("cover_photos/${myUid}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                firestore.collection("users").document(myUid).update(mapOf(
                    "coverPhoto" to url,
                    "coverURL"   to url,
                )).await()
                _user.value = _user.value?.copy(coverPhoto = url)
                onDone(url)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Kapak fotoğrafı yüklenemedi")
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Username güncelle ─────────────────────────────────────────────────────
    fun updateUsername(newUsername: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (myUid.isEmpty() || newUsername.isBlank()) return
        val handle = newUsername.lowercase().trim()
            .filter { it.isLetterOrDigit() || it == '_' }
            .take(20)
        if (handle.isBlank()) { onError("Geçersiz kullanıcı adı"); return }
        viewModelScope.launch {
            try {
                // Alınmış mı kontrolü
                val takenDoc = firestore.collection("usernames").document(handle).get().await()
                if (takenDoc.exists()) {
                    val ownerUid = takenDoc.getString("uid") ?: ""
                    if (ownerUid != myUid) { onError("Bu kullanıcı adı alınmış"); return@launch }
                }
                val oldHandle = _user.value?.username?.lowercase()?.trim() ?: ""
                // Eski handle sil
                if (oldHandle.isNotBlank() && oldHandle != handle) {
                    try { firestore.collection("usernames").document(oldHandle).delete().await() }
                    catch (_: Exception) {}
                }
                // Yeni handle kaydet
                firestore.collection("usernames").document(handle).set(
                    mapOf("uid" to myUid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                ).await()
                // users dokümanını güncelle
                firestore.collection("users").document(myUid).update(mapOf("username" to handle)).await()
                _user.value = _user.value?.copy(username = handle)
                onSuccess()
            } catch (e: Exception) { onError(e.message ?: "Hata") }
        }
    }


}
