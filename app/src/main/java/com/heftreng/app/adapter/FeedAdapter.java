package com.heftreng.app.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.heftreng.app.R;
import com.heftreng.app.model.FeedPost;

import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.PostViewHolder> {

    public interface OnPostActionListener {
        void onComment(FeedPost post);
        void onAuthorClick(String uid);
    }

    private final List<FeedPost> posts;
    private final FirebaseUser currentUser;
    private final FirebaseFirestore db;
    private final OnPostActionListener listener;

    public FeedAdapter(List<FeedPost> posts, FirebaseUser currentUser,
                       FirebaseFirestore db, OnPostActionListener listener) {
        this.posts = posts;
        this.currentUser = currentUser;
        this.db = db;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_feed_post, parent, false);
        return new PostViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() { return posts.size(); }

    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivPostImage;
        TextView tvAuthor, tvTime, tvContent, tvLikeCount, tvCommentCount;
        ImageButton btnLike, btnComment;

        PostViewHolder(@NonNull View v) {
            super(v);
            ivAvatar      = v.findViewById(R.id.ivAvatar);
            ivPostImage   = v.findViewById(R.id.ivPostImage);
            tvAuthor      = v.findViewById(R.id.tvAuthor);
            tvTime        = v.findViewById(R.id.tvTime);
            tvContent     = v.findViewById(R.id.tvContent);
            tvLikeCount   = v.findViewById(R.id.tvLikeCount);
            tvCommentCount = v.findViewById(R.id.tvCommentCount);
            btnLike       = v.findViewById(R.id.btnLike);
            btnComment    = v.findViewById(R.id.btnComment);
        }

        void bind(FeedPost post) {
            tvAuthor.setText(post.authorName != null ? post.authorName : "Kullanıcı");
            tvContent.setText(post.content);
            tvLikeCount.setText(String.valueOf(post.likeCount));
            tvCommentCount.setText(String.valueOf(post.commentCount));

            // Zaman
            if (post.createdAt != null) {
                long millis = post.createdAt.toDate().getTime();
                tvTime.setText(DateUtils.getRelativeTimeSpanString(millis,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            }

            // Avatar
            if (post.authorPhoto != null && !post.authorPhoto.isEmpty()) {
                Glide.with(itemView).load(post.authorPhoto).circleCrop()
                    .placeholder(R.drawable.ic_account_circle).into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_account_circle);
            }

            // Post resmi
            if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
                ivPostImage.setVisibility(View.VISIBLE);
                Glide.with(itemView).load(post.imageUrl)
                    .placeholder(R.drawable.ic_account_circle).into(ivPostImage);
            } else {
                ivPostImage.setVisibility(View.GONE);
            }

            // Beğeni durumu
            boolean liked = currentUser != null && post.likedBy != null
                && post.likedBy.contains(currentUser.getUid());
            btnLike.setImageResource(liked
                ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

            btnLike.setOnClickListener(v -> {
                if (currentUser == null) return;
                String uid = currentUser.getUid();
                boolean isLiked = post.likedBy != null && post.likedBy.contains(uid);
                if (isLiked) {
                    db.collection("feed").document(post.id).update(
                        "likedBy", FieldValue.arrayRemove(uid),
                        "likeCount", Math.max(0, post.likeCount - 1));
                    post.likeCount = Math.max(0, post.likeCount - 1);
                    if (post.likedBy != null) post.likedBy.remove(uid);
                } else {
                    db.collection("feed").document(post.id).update(
                        "likedBy", FieldValue.arrayUnion(uid),
                        "likeCount", post.likeCount + 1);
                    post.likeCount++;
                    if (post.likedBy != null) post.likedBy.add(uid);
                }
                notifyItemChanged(getAdapterPosition());
            });

            // Yorum
            btnComment.setOnClickListener(v -> listener.onComment(post));

            // Yazar profili
            ivAvatar.setOnClickListener(v -> {
                if (post.authorId != null) listener.onAuthorClick(post.authorId);
            });
        }
    }
}
