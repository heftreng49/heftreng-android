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

    val myUid get() = auth.currentUser?.uid ?: ""

    fun load(uid: String) {
        val targetUid = if (uid == "me") myUid else uid
        viewModelScope.launch {
            _loading.value = true
            try {
                // Kullanıcı belgesi
                val userDoc = firestore.collection("users").document(targetUid).get().await()
                val d = userDoc.data ?: return@launch
                _user.value = User(
                    uid        = d["uid"] as? String ?: targetUid,
                    displayName= d["displayName"] as? String ?: d["name"] as? String ?: "",
                    name       = d["name"] as? String ?: "",
                    username   = d["username"] as? String ?: (d["email"] as? String)?.substringBefore("@") ?: "",
                    email      = d["email"] as? String ?: "",
                    photoURL   = d["photoURL"] as? String ?: "",
                    coverPhoto = d["coverPhoto"] as? String ?: "",
                    bio        = d["bio"] as? String ?: "",
                    website    = d["website"] as? String ?: "",
                    level      = (d["level"] as? Long)?.toInt() ?: 1,
                    xp         = (d["xp"] as? Long)?.toInt() ?: 0,
                    streak     = (d["streak"] as? Long)?.toInt() ?: 0,
                )

                // Takipçi sayıları — follows koleksiyonundan say
                val followersSnap = firestore.collection("follows")
                    .whereEqualTo("targetUid", targetUid).get().await()
                val followingSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", targetUid).get().await()
                _followersCount.value = followersSnap.size()
                _followingCount.value = followingSnap.size()

                // Gönderiler
                // orderBy kaldırıldı — composite index gerektirmez
                // XML temasıyla aynı: where + limit, sonra client-side sort
                val snap = firestore.collection("feed")
                    .whereEqualTo("uid", targetUid)
                    .limit(50).get().await()
                _posts.value = snap.documents.mapNotNull { doc ->
                    val fd = doc.data ?: return@mapNotNull null
                    val postText  = fd["text"]     as? String ?: ""
                    val imageURL  = fd["imageURL"] as? String ?: fd["imgUrl"] as? String ?: ""

                    // quote alanları — tema: d.quote objesi veya düz alanlar
                    val quoteObj   = fd["quote"] as? Map<*, *>
                    val quoteText  = (quoteObj?.get("text")   as? String)?.takeIf { it.isNotBlank() }
                        ?: fd["quoteText"]  as? String ?: ""
                    val bookName   = (quoteObj?.get("book")   as? String)?.takeIf { it.isNotBlank() }
                        ?: fd["bookName"]   as? String ?: ""
                    val authorName = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                        ?: fd["authorName"] as? String ?: ""

                    // Gerçekten boş gönderiyi atla (text, resim, alıntı, repost yok)
                    val repostOf = fd["repostOf"] as? String ?: fd["repostType"] as? String ?: ""
                    if (postText.isBlank() && imageURL.isBlank() && quoteText.isBlank() && repostOf.isBlank())
                        return@mapNotNull null

                    val dName = (fd["displayName"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (fd["name"] as? String)?.takeIf { it.isNotBlank() } ?: ""

                    Post(
                        id            = doc.id,
                        uid           = fd["uid"]      as? String ?: "",
                        displayName   = dName,
                        username      = fd["username"] as? String ?: "",
                        photoURL      = fd["photoURL"] as? String ?: "",
                        text          = postText,
                        imageURL      = imageURL,
                        quoteText     = quoteText,
                        bookName      = bookName,
                        authorName    = authorName,
                        likesCount    = (fd["likes"]    as? Long)?.toInt() ?: 0,
                        commentsCount = (fd["cmtCount"] as? Long)?.toInt() ?: 0,
                        repostsCount  = (fd["reposts"]  as? Long)?.toInt() ?: 0,
                        ts            = fd["ts"] as? Timestamp,
                    )
                }.sortedByDescending { it.ts?.seconds ?: 0L }

                // Takip durumu
                if (targetUid != myUid) {
                    val followDoc = firestore.collection("follows")
                        .document("${myUid}_$targetUid").get().await()
                    _isFollowing.value = followDoc.exists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
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
                    val me = firestore.collection("users").document(myUid).get().await()
                    ref.set(mapOf(
                        "uid"     to myUid,
                        "feedId"  to post.id,
                        "name"    to (me.getString("name") ?: ""),
                        "photoURL" to (me.getString("photoURL") ?: ""),
                        "ts"      to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )).await()
                    pRef.update("likes", com.google.firebase.firestore.FieldValue.increment(1)).await()
                } else {
                    ref.delete().await()
                    pRef.update("likes", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteOwnPost(postId: String) {
        _posts.value = _posts.value.filter { it.id != postId }
        viewModelScope.launch {
            try { firestore.collection("feed").document(postId).delete().await() }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editOwnPost(postId: String, newText: String) {
        _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText) else it }
        viewModelScope.launch {
            try { firestore.collection("feed").document(postId).update("text", newText).await() }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Profil / Kapak fotoğrafı güncelle ────────────────────────────────────
    fun updateProfilePhoto(imageUri: android.net.Uri, storage: com.google.firebase.storage.FirebaseStorage, onDone: (String) -> Unit = {}) {
        if (myUid.isEmpty()) return
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("profile_photos/${myUid}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                firestore.collection("users").document(myUid).update("photoURL", url).await()
                _user.value = _user.value?.copy(photoURL = url)
                onDone(url)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateCoverPhoto(imageUri: android.net.Uri, storage: com.google.firebase.storage.FirebaseStorage, onDone: (String) -> Unit = {}) {
        if (myUid.isEmpty()) return
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("cover_photos/${myUid}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                // Tema: hem coverPhoto hem coverURL yaz (her iki alan kullanılıyor)
                firestore.collection("users").document(myUid).update(mapOf(
                    "coverPhoto" to url,
                    "coverURL"   to url,
                )).await()
                _user.value = _user.value?.copy(coverPhoto = url)
                onDone(url)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Username güncelle ─────────────────────────────────────────────────────
    fun updateUsername(newUsername: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (myUid.isEmpty() || newUsername.isBlank()) return
        val handle = newUsername.lowercase().trim()
        viewModelScope.launch {
            try {
                val taken = firestore.collection("usernames").document(handle).get().await().exists()
                if (taken) { onError("Bu kullanıcı adı alınmış"); return@launch }
                val batch = firestore.batch()
                val old   = _user.value?.username ?: ""
                if (old.isNotBlank()) batch.delete(firestore.collection("usernames").document(old))
                batch.set(firestore.collection("usernames").document(handle),
                    mapOf("uid" to myUid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                batch.update(firestore.collection("users").document(myUid), mapOf("username" to handle))
                batch.commit().await()
                _user.value = _user.value?.copy(username = handle)
                onSuccess()
            } catch (e: Exception) { onError(e.message ?: "Hata") }
        }
    }

}
