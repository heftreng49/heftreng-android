package com.heftreng.app.ui.component

import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import java.net.URLEncoder

/**
 * ConnectedPostCard — tek kaynak, tüm ekranlarda aynı davranış.
 *
 * Tüm navigation + like/save/repost mantığı burada.
 * Ekranlar sadece şunu yazar:
 *
 *   ConnectedPostCard(post = post, navController = navController, feedVm = vm)
 *
 * Ekrana özgü ek aksiyonlar (onDelete, onSave override, isDetailScreen vb.)
 * isteğe bağlı parametrelerle geçirilir.
 */
@Composable
fun ConnectedPostCard(
    post            : Post,
    navController   : NavController,
    feedVm          : FeedViewModel,
    socialVm        : SocialViewModel? = null,
    language        : String = "tr",
    // Ekrana özgü overrides
    isDetailScreen  : Boolean = false,   // true → onTap null (zaten detaydasın)
    onSaveOverride  : (() -> Unit)? = null, // SavedPostsScreen gibi ekstra mantık için
    onDeleteOverride: (() -> Unit)? = null,
    onEditOverride  : ((title: String, text: String) -> Unit)? = null,
    showReport      : Boolean = false,
    onReport        : (() -> Unit)? = null,
    onBlock         : (() -> Unit)? = null,
) {
    PostCard(
        post     = post,
        language = language,

        // ── Sosyal aksiyonlar ──────────────────────────────────────────────
        onLike   = { feedVm.toggleLike(post) },
        onSave   = onSaveOverride ?: { feedVm.toggleSave(post) },
        onShare  = {
            if (post.isRepostedByMe) feedVm.unrepost(post)
            else feedVm.repost(post)
        },
        onDelete = onDeleteOverride,
        onEdit   = onEditOverride,

        // ── Navigation ────────────────────────────────────────────────────
        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
        onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
        onTap     = if (isDetailScreen) null
                    else { { navController.navigate(Screen.PostDetail.go(post.id)) } },

        // ── Likers ────────────────────────────────────────────────────────
        onShowLikers = socialVm?.let { svm ->
            { svm.loadPostLikers(post.id) }
        },

        // ── Kitap / Yazar — her zaman aynı mantık ─────────────────────────
        onTapBook = { _ ->
            if (post.libraryBookId.isNotBlank())
                navController.navigate("library_book_detail/${post.libraryBookId}")
            else if (post.bookName.isNotBlank())
                navController.navigate("book_quotes/${URLEncoder.encode(post.bookName, "UTF-8")}")
        },
        onTapAuthor = { _ ->
            if (post.libraryAuthorId.isNotBlank())
                navController.navigate("author_detail/${post.libraryAuthorId}")
            else if (post.authorName.isNotBlank())
                navController.navigate("author_quotes/${URLEncoder.encode(post.authorName, "UTF-8")}")
        },

        // ── Repost tipleri — her zaman aynı mantık ────────────────────────
        onTapRepost = { repostId, repostType ->
            when (repostType) {
                "feed"         -> navController.navigate(Screen.PostDetail.go(repostId))
                "serial"       -> navController.navigate("serial/$repostId")
                "chapter"      -> {
                    val sid = post.serialId.ifBlank { "" }
                    val cid = post.chapterId.ifBlank { repostId }
                    if (sid.isNotBlank()) navController.navigate("chapter/$sid/$cid")
                    else navController.navigate("serial/${post.repostId}")
                }
                "book_chapter" -> {
                    val bid = post.serialId.ifBlank { "" }
                    val cid = post.chapterId.ifBlank { repostId }
                    if (bid.isNotBlank()) navController.navigate("book_chapter/$bid/$cid")
                }
                "blog"         -> navController.navigate("blog/$repostId")
                else           -> navController.navigate(Screen.PostDetail.go(repostId))
            }
        },

        onReport = onReport,
        onBlock  = onBlock,
    )
}
