package com.heftreng.app.adapter;

import android.content.Context;
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
    private final Context context;

    public FeedAdapter(List<FeedPost> posts, FirebaseUser currentUser,
                       FirebaseFirestore db, OnPostActionListener listener) {
        this.posts = posts;
        this.currentUser = currentUser;
        this.db = db;
        this.listener = listener;
        this.context = null;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_feed_post, parent, false);
        return new PostViewHolder(v, parent.getContext());
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
        Context ctx;

        PostViewHolder(@NonNull View v, Context context) {
            super(v);
            this.ctx = context;
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
            tvAuthor.setText(post.name != null ? post.name : "Kullanıcı");
            tvContent.setText(post.text != null ? post.text : "");
            tvLikeCount.setText(String.valueOf(post.likes));
            tvCommentCount.setText(String.valueOf(post.cmtCount));

            if (post.ts != null) {
                long millis = post.ts.toDate().getTime();
                tvTime.setText(DateUtils.getRelativeTimeSpanString(millis,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            } else {
                tvTime.setText("");
            }

            // Avatar - context'i onCreateViewHolder'dan alıyoruz
            try {
                if (post.photoURL != null && !post.photoURL.isEmpty()) {
                    Glide.with(ctx)
                        .load(post.photoURL)
                        .circleCrop()
                        .placeholder(R.drawable.ic_account_circle)
                        .error(R.drawable.ic_account_circle)
                        .into(ivAvatar);
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_account_circle);
                }
            } catch (Exception e) {
                ivAvatar.setImageResource(R.drawable.ic_account_circle);
            }

            // Post resmi
            try {
                if (post.imgUrl != null && !post.imgUrl.isEmpty()) {
                    ivPostImage.setVisibility(View.VISIBLE);
                    Glide.with(ctx)
                        .load(post.imgUrl)
                        .placeholder(R.drawable.ic_account_circle)
                        .error(R.drawable.ic_account_circle)
                        .into(ivPostImage);
                } else {
                    ivPostImage.setVisibility(View.GONE);
                }
            } catch (Exception e) {
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
