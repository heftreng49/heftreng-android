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
            ivAvatar       = v.findViewById(R.id.ivAvatar);
            ivPostImage    = v.findViewById(R.id.ivPostImage);
            tvAuthor       = v.findViewById(R.id.tvAuthor);
            tvTime         = v.findViewById(R.id.tvTime);
            tvContent      = v.findViewById(R.id.tvContent);
            tvLikeCount    = v.findViewById(R.id.tvLikeCount);
            tvCommentCount = v.findViewById(R.id.tvCommentCount);
            btnLike        = v.findViewById(R.id.btnLike);
            btnComment     = v.findViewById(R.id.btnComment);
        }

        void bind(FeedPost post) {
            // Web şeması: name, text, photoURL, likes, cmtCount, ts
            tvAuthor.setText(post.name != null ? post.name : "Kullanıcı");
            tvContent.setText(post.text != null ? post.text : "");
            tvLikeCount.setText(String.valueOf(post.likes));
            tvCommentCount.setText(String.valueOf(post.cmtCount));

            // Zaman
            if (post.ts != null) {
                long millis = post.ts.toDate().getTime();
                tvTime.setText(DateUtils.getRelativeTimeSpanString(millis,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            }

            // Avatar
            if (post.photoURL != null && !post.photoURL.isEmpty()) {
                Glide.with(ivAvatar).load(post.photoURL).circleCrop()
                    .placeholder(R.drawable.ic_account_circle).into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_account_circle);
            }

            // Post resmi
            if (post.imgUrl != null && !post.imgUrl.isEmpty()) {
                ivPostImage.setVisibility(View.VISIBLE);
                Glide.with(ivPostImage).load(post.imgUrl)
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
                String myUid = currentUser.getUid();
                boolean isLiked = post.likedBy != null && post.likedBy.contains(myUid);
                if (isLiked) {
                    db.collection("feed").document(post.id).update(
                        "likedBy", FieldValue.arrayRemove(myUid),
                        "likes", Math.max(0, post.likes - 1));
                    post.likes = Math.max(0, post.likes - 1);
                    if (post.likedBy != null) post.likedBy.remove(myUid);
                } else {
                    db.collection("feed").document(post.id).update(
                        "likedBy", FieldValue.arrayUnion(myUid),
                        "likes", post.likes + 1);
                    post.likes++;
                    if (post.likedBy != null) post.likedBy.add(myUid);
                }
                notifyItemChanged(getAdapterPosition());
            });

            btnComment.setOnClickListener(v -> listener.onComment(post));

            ivAvatar.setOnClickListener(v -> {
                if (post.uid != null) listener.onAuthorClick(post.uid);
            });
        }
    }
}
