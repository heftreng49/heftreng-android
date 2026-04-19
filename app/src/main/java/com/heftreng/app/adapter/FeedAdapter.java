package com.heftreng.app.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.heftreng.app.R;
import com.heftreng.app.model.FeedPost;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.FeedVH> {

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
    public FeedVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_feed_post, parent, false);
        return new FeedVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedVH h, int pos) {
        FeedPost post = posts.get(pos);

        // Yazar bilgileri
        h.tvAuthorName.setText(post.name != null ? post.name : "");
        h.tvContent.setText(post.text != null ? post.text : "");
        h.tvLikeCount.setText(String.valueOf(post.likes));
        h.tvCommentCount.setText(String.valueOf(post.cmtCount));

        // Zaman
        if (post.ts != null) {
            h.tvTime.setText(timeAgo(post.ts.toDate().getTime()));
        }

        // Kategori chip
        if (post.category != null && !post.category.isEmpty()) {
            h.chipCategory.setVisibility(View.VISIBLE);
            h.chipCategory.setText(post.category);
        } else {
            h.chipCategory.setVisibility(View.GONE);
        }

        // Profil fotoğrafı
        if (post.photoURL != null && !post.photoURL.isEmpty()) {
            Glide.with(h.itemView.getContext())
                .load(post.photoURL)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(h.ivAuthorPhoto);
        } else {
            h.ivAuthorPhoto.setImageResource(R.drawable.ic_account_circle);
        }

        // Gönderi görseli
        if (post.imgUrl != null && !post.imgUrl.isEmpty()) {
            h.ivPostImage.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                .load(post.imgUrl)
                .centerCrop()
                .into(h.ivPostImage);
        } else {
            h.ivPostImage.setVisibility(View.GONE);
        }

        // Beğeni durumu
        boolean liked = currentUser != null && post.likedBy != null
            && post.likedBy.contains(currentUser.getUid());
        h.btnLike.setImageResource(liked
            ? R.drawable.ic_heart_filled
            : R.drawable.ic_heart_outline);
        h.btnLike.setColorFilter(liked
            ? 0xFFF43F5E
            : 0xFF888888);

        // Tıklamalar
        h.btnLike.setOnClickListener(v -> toggleLike(post, h));
        h.btnComment.setOnClickListener(v -> { if (listener != null) listener.onComment(post); });
        h.btnRepost.setOnClickListener(v -> repost(post, h));
        h.btnSave.setOnClickListener(v -> save(post, h));
        h.btnShare.setOnClickListener(v -> share(post, h));
        h.btnMore.setOnClickListener(v -> showMoreMenu(post, h));
        h.ivAuthorPhoto.setOnClickListener(v -> { if (listener != null) listener.onAuthorClick(post.uid); });
        h.tvAuthorName.setOnClickListener(v -> { if (listener != null) listener.onAuthorClick(post.uid); });
    }

    // ── Beğeni ──────────────────────────────────────────────────────────

    private void toggleLike(FeedPost post, FeedVH h) {
        if (currentUser == null) {
            Toast.makeText(h.itemView.getContext(),
                "Beğenmek için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = currentUser.getUid();
        boolean liked = post.likedBy != null && post.likedBy.contains(uid);

        if (liked) {
            db.collection("feed").document(post.id)
                .update("likedBy", FieldValue.arrayRemove(uid),
                        "likes",   FieldValue.increment(-1));
            if (post.likedBy != null) post.likedBy.remove(uid);
            post.likes = Math.max(0, post.likes - 1);
        } else {
            db.collection("feed").document(post.id)
                .update("likedBy", FieldValue.arrayUnion(uid),
                        "likes",   FieldValue.increment(1));
            if (post.likedBy == null) post.likedBy = new ArrayList<>();
            post.likedBy.add(uid);
            post.likes++;
        }
        h.tvLikeCount.setText(String.valueOf(post.likes));
        boolean nowLiked = !liked;
        h.btnLike.setImageResource(nowLiked
            ? R.drawable.ic_heart_filled
            : R.drawable.ic_heart_outline);
        h.btnLike.setColorFilter(nowLiked ? 0xFFF43F5E : 0xFF888888);
    }

    // ── Repost ──────────────────────────────────────────────────────────

    private void repost(FeedPost post, FeedVH h) {
        if (currentUser == null) {
            Toast.makeText(h.itemView.getContext(),
                "Repost için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        // Repost kaydı feed'e ekle
        java.util.Map<String, Object> rp = new java.util.HashMap<>();
        rp.put("uid",        currentUser.getUid());
        rp.put("name",       currentUser.getDisplayName() != null
                             ? currentUser.getDisplayName() : "Kullanıcı");
        rp.put("photoURL",   currentUser.getPhotoUrl() != null
                             ? currentUser.getPhotoUrl().toString() : "");
        rp.put("text",       "");
        rp.put("repostType", "feed");
        rp.put("repostId",   post.id);
        rp.put("repostText", post.text != null ? post.text : "");
        rp.put("repostName", post.name != null ? post.name : "");
        rp.put("likes",      0);
        rp.put("cmtCount",   0);
        rp.put("ts",         com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("feed").add(rp)
            .addOnSuccessListener(ref -> {
                h.btnRepost.setColorFilter(0xFF7C3AED);
                Toast.makeText(h.itemView.getContext(),
                    "Yeniden paylaşıldı", Toast.LENGTH_SHORT).show();
            });
    }

    // ── Kaydet ──────────────────────────────────────────────────────────

    private void save(FeedPost post, FeedVH h) {
        if (currentUser == null) {
            Toast.makeText(h.itemView.getContext(),
                "Kaydetmek için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        String docId = currentUser.getUid() + "_" + post.id;
        java.util.Map<String, Object> save = new java.util.HashMap<>();
        save.put("uid",    currentUser.getUid());
        save.put("postId", post.id);
        save.put("ts",     com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("feedSaves").document(docId).set(save)
            .addOnSuccessListener(ref -> {
                h.btnSave.setImageResource(R.drawable.ic_bookmark);
                h.btnSave.setColorFilter(0xFF7C3AED);
                Toast.makeText(h.itemView.getContext(),
                    "Kaydedildi", Toast.LENGTH_SHORT).show();
            });
    }

    // ── Paylaş ──────────────────────────────────────────────────────────

    private void share(FeedPost post, FeedVH h) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String shareText = (post.name != null ? post.name + "\n\n" : "")
            + (post.text != null ? post.text : "")
            + "\n\nheft-reng.blogspot.com";
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        h.itemView.getContext().startActivity(
            Intent.createChooser(intent, "Paylaş"));
    }

    // ── Daha Fazla Menü ─────────────────────────────────────────────────

    private void showMoreMenu(FeedPost post, FeedVH h) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(
            h.itemView.getContext(), h.btnMore);
        popup.getMenu().add(0, 1, 0, "Paylaş");
        if (currentUser != null && currentUser.getUid().equals(post.uid)) {
            popup.getMenu().add(0, 2, 0, "Sil");
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                share(post, h);
            } else if (item.getItemId() == 2) {
                db.collection("feed").document(post.id).delete()
                    .addOnSuccessListener(v -> {
                        int idx = posts.indexOf(post);
                        if (idx >= 0) {
                            posts.remove(idx);
                            notifyItemRemoved(idx);
                        }
                    });
            }
            return true;
        });
        popup.show();
    }

    // ── Yardımcı ────────────────────────────────────────────────────────

    private String timeAgo(long millis) {
        long diff    = System.currentTimeMillis() - millis;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours   = TimeUnit.MILLISECONDS.toHours(diff);
        long days    = TimeUnit.MILLISECONDS.toDays(diff);
        if (minutes < 1)  return "az önce";
        if (minutes < 60) return minutes + " dk";
        if (hours   < 24) return hours   + " sa";
        if (days    < 7)  return days    + " gün";
        return new SimpleDateFormat("dd MMM", new Locale("tr"))
            .format(new java.util.Date(millis));
    }

    @Override
    public int getItemCount() { return posts.size(); }

    static class FeedVH extends RecyclerView.ViewHolder {
        CircleImageView ivAuthorPhoto;
        ShapeableImageView ivPostImage;
        TextView tvAuthorName, tvContent, tvTime, tvLikeCount, tvCommentCount;
        ImageButton btnLike, btnComment, btnRepost, btnSave, btnShare, btnMore;
        Chip chipCategory;

        FeedVH(@NonNull View v) {
            super(v);
            ivAuthorPhoto  = v.findViewById(R.id.ivAuthorPhoto);
            ivPostImage    = v.findViewById(R.id.ivPostImage);
            tvAuthorName   = v.findViewById(R.id.tvAuthorName);
            tvContent      = v.findViewById(R.id.tvContent);
            tvTime         = v.findViewById(R.id.tvTime);
            tvLikeCount    = v.findViewById(R.id.tvLikeCount);
            tvCommentCount = v.findViewById(R.id.tvCommentCount);
            btnLike        = v.findViewById(R.id.btnLike);
            btnComment     = v.findViewById(R.id.btnComment);
            btnRepost      = v.findViewById(R.id.btnRepost);
            btnSave        = v.findViewById(R.id.btnSave);
            btnShare       = v.findViewById(R.id.btnShare);
            btnMore        = v.findViewById(R.id.btnMore);
            chipCategory   = v.findViewById(R.id.chipCategory);
        }
    }
}
