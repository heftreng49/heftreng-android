package com.heftreng.app.adapter;

import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.heftreng.app.R;
import com.heftreng.app.model.FeedPost;
import java.util.*;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.PostViewHolder> {

    public interface OnPostActionListener {
        void onComment(FeedPost post);
        void onAuthorClick(String uid);
    }

    private final List<FeedPost>       posts;
    private final FirebaseUser         currentUser;
    private final FirebaseFirestore    db;
    private final OnPostActionListener listener;

    public FeedAdapter(List<FeedPost> posts, FirebaseUser user,
                       FirebaseFirestore db, OnPostActionListener listener) {
        this.posts = posts; this.currentUser = user;
        this.db = db; this.listener = listener;
    }

    @NonNull @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_feed_post, parent, false);
        return new PostViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder h, int pos) {
        FeedPost post = posts.get(pos);

        h.tvAuthor.setText(post.authorName != null ? post.authorName : "Kullanıcı");
        h.tvContent.setText(post.content != null ? post.content : "");
        h.tvLikeCount.setText(String.valueOf(post.likeCount));
        h.tvCommentCount.setText(String.valueOf(post.commentCount));

        // Zaman
        if (post.createdAt != null) {
            long ms = post.createdAt.toDate().getTime();
            h.tvTime.setText(DateUtils.getRelativeTimeSpanString(ms));
        }

        // Avatar
        if (post.authorPhoto != null && !post.authorPhoto.isEmpty()) {
            Glide.with(h.itemView).load(post.authorPhoto).circleCrop()
                .placeholder(R.drawable.ic_account_circle).into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(R.drawable.ic_account_circle);
        }

        // Resim
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            h.ivPostImage.setVisibility(View.VISIBLE);
            Glide.with(h.itemView).load(post.imageUrl).centerCrop().into(h.ivPostImage);
        } else {
            h.ivPostImage.setVisibility(View.GONE);
        }

        // Beğeni durumu
        boolean liked = currentUser != null && post.likedBy != null
            && post.likedBy.contains(currentUser.getUid());
        h.btnLike.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

        // Beğeni tıklama
        h.btnLike.setOnClickListener(v -> toggleLike(post, h, liked));

        // Yorum
        h.btnComment.setOnClickListener(v -> listener.onComment(post));

        // Yazar tıklama
        h.ivAvatar.setOnClickListener(v -> {
            if (post.authorId != null) listener.onAuthorClick(post.authorId);
        });
        h.tvAuthor.setOnClickListener(v -> {
            if (post.authorId != null) listener.onAuthorClick(post.authorId);
        });

        // Repost
        h.btnRepost.setOnClickListener(v -> repost(post, h));

        // Bookmark
        h.btnBookmark.setOnClickListener(v -> bookmark(post, h));

        // Paylaş
        h.btnShare.setOnClickListener(v -> share(post, h));
    }

    private void toggleLike(FeedPost post, PostViewHolder h, boolean currentlyLiked) {
        if (currentUser == null) return;
        DocumentReference ref = db.collection("feed").document(post.id);
        if (currentlyLiked) {
            ref.update("likedBy", FieldValue.arrayRemove(currentUser.getUid()),
                       "likeCount", FieldValue.increment(-1));
            post.likeCount = Math.max(0, post.likeCount - 1);
            if (post.likedBy != null) post.likedBy.remove(currentUser.getUid());
            h.btnLike.setImageResource(R.drawable.ic_heart_outline);
        } else {
            ref.update("likedBy", FieldValue.arrayUnion(currentUser.getUid()),
                       "likeCount", FieldValue.increment(1));
            post.likeCount++;
            if (post.likedBy == null) post.likedBy = new ArrayList<>();
            post.likedBy.add(currentUser.getUid());
            h.btnLike.setImageResource(R.drawable.ic_heart_filled);
        }
        h.tvLikeCount.setText(String.valueOf(post.likeCount));
    }

    private void repost(FeedPost post, PostViewHolder h) {
        if (currentUser == null) return;
        Map<String, Object> repost = new HashMap<>();
        repost.put("authorId",    currentUser.getUid());
        repost.put("authorName",  currentUser.getDisplayName() != null
                                  ? currentUser.getDisplayName() : "Kullanıcı");
        repost.put("authorPhoto", currentUser.getPhotoUrl() != null
                                  ? currentUser.getPhotoUrl().toString() : "");
        repost.put("content",     post.content);
        repost.put("imageUrl",    post.imageUrl != null ? post.imageUrl : "");
        repost.put("likeCount",   0);
        repost.put("commentCount",0);
        repost.put("likedBy",     new ArrayList<>());
        repost.put("type",        "repost");
        repost.put("originalAuthor", post.authorName);
        repost.put("createdAt",   com.google.firebase.Timestamp.now());
        db.collection("feed").add(repost);
        Toast.makeText(h.itemView.getContext(), "Repost edildi", Toast.LENGTH_SHORT).show();
    }

    private void bookmark(FeedPost post, PostViewHolder h) {
        if (currentUser == null) return;
        Map<String, Object> saved = new HashMap<>();
        saved.put("postId",      post.id);
        saved.put("postContent", post.content != null ? post.content : "");
        saved.put("postAuthor",  post.authorName != null ? post.authorName : "");
        saved.put("postPhoto",   post.imageUrl != null ? post.imageUrl : "");
        saved.put("status",      "okuyacak");
        saved.put("savedAt",     com.google.firebase.Timestamp.now());
        db.collection("users").document(currentUser.getUid())
            .collection("saved").add(saved)
            .addOnSuccessListener(ref ->
                Toast.makeText(h.itemView.getContext(), "Kaydedildi ✓", Toast.LENGTH_SHORT).show());
    }

    private void share(FeedPost post, PostViewHolder h) {
        android.content.Intent intent = new android.content.Intent(
            android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT,
            post.content + "\n\n— Heftreng");
        h.itemView.getContext().startActivity(
            android.content.Intent.createChooser(intent, "Paylaş"));
    }

    @Override public int getItemCount() { return posts.size(); }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivPostImage;
        TextView tvAuthor, tvTime, tvContent, tvLikeCount, tvCommentCount;
        ImageButton btnLike, btnComment, btnRepost, btnBookmark, btnShare;

        PostViewHolder(View v) {
            super(v);
            ivAvatar       = v.findViewById(R.id.ivAvatar);
            ivPostImage    = v.findViewById(R.id.ivPostImage);
            tvAuthor       = v.findViewById(R.id.tvAuthor);
            tvTime         = v.findViewById(R.id.tvTime);
            tvContent      = v.findViewById(R.id.tvContent);
            tvLikeCount    = v.findViewById(R.id.tvLikeCount);
            tvCommentCount = v.findViewById(R.id.tvCommentCount);
            btnLike        = v.findViewById(R.id.btnLike);
            btnComment     = v.findViewById(R.id.btnComment);
            btnRepost      = v.findViewById(R.id.btnRepost);
            btnBookmark    = v.findViewById(R.id.btnBookmark);
            btnShare       = v.findViewById(R.id.btnShare);
        }
    }
}
