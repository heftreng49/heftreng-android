package com.heftreng.app.ui.blog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.heftreng.app.R;
import com.heftreng.app.model.BlogPost;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlogDetailFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String postId;
    private BlogPost currentPost;
    private boolean isLiked = false;

    private ImageView ivCover, ivAuthorPhoto;
    private TextView tvTitle, tvCategory, tvAuthorName, tvDate, tvReadTime,
                     tvContent, tvLikeCount, tvCommentCount, tvStatus;
    private ImageButton btnLike, btnBack, btnShare, btnSendComment;
    private EditText etComment;
    private RecyclerView recyclerComments;

    private List<Map<String, Object>> comments = new ArrayList<>();
    private CommentAdapter commentAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_blog_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        postId = getArguments() != null ? getArguments().getString("postId") : null;

        initViews(view);
        if (postId != null && !postId.isEmpty()) {
            loadPost();
        }
    }

    private void initViews(View v) {
        ivCover        = v.findViewById(R.id.ivCover);
        ivAuthorPhoto  = v.findViewById(R.id.ivAuthorPhoto);
        tvTitle        = v.findViewById(R.id.tvTitle);
        tvCategory     = v.findViewById(R.id.tvCategory);
        tvAuthorName   = v.findViewById(R.id.tvAuthorName);
        tvDate         = v.findViewById(R.id.tvDate);
        tvReadTime     = v.findViewById(R.id.tvReadTime);
        tvContent      = v.findViewById(R.id.tvContent);
        tvLikeCount    = v.findViewById(R.id.tvLikeCount);
        tvCommentCount = v.findViewById(R.id.tvCommentCount);
        tvStatus       = v.findViewById(R.id.tvStatus);
        btnLike        = v.findViewById(R.id.btnLike);
        btnBack        = v.findViewById(R.id.btnBack);
        btnShare       = v.findViewById(R.id.btnShare);
        btnSendComment = v.findViewById(R.id.btnSendComment);
        etComment      = v.findViewById(R.id.etComment);
        recyclerComments = v.findViewById(R.id.recyclerComments);

        commentAdapter = new CommentAdapter(comments);
        recyclerComments.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerComments.setAdapter(commentAdapter);

        btnBack.setOnClickListener(x -> requireActivity().onBackPressed());
        btnLike.setOnClickListener(x -> toggleLike());
        btnSendComment.setOnClickListener(x -> sendComment());
        if (btnShare != null)
            btnShare.setOnClickListener(x -> sharePost());
    }

    private void loadPost() {
        // pendingPosts koleksiyonundan çek
        db.collection("pendingPosts").document(postId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    currentPost = doc.toObject(BlogPost.class);
                    if (currentPost != null) {
                        currentPost.id = doc.getId();
                        bindPost();
                        checkLiked();
                        loadComments();
                    }
                }
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Yazı yüklenemedi", Toast.LENGTH_SHORT).show());
    }

    private void bindPost() {
        tvTitle.setText(currentPost.title != null ? currentPost.title : "");
        tvContent.setText(currentPost.content != null ? currentPost.content : "");
        tvCategory.setText(currentPost.category != null ? currentPost.category : "Genel");
        tvAuthorName.setText(currentPost.authorName != null ? currentPost.authorName : "");

        if (currentPost.readTimeMinutes > 0)
            tvReadTime.setText(currentPost.readTimeMinutes + " dk okuma");

        tvLikeCount.setText(String.valueOf(currentPost.likeCount));
        tvCommentCount.setText(currentPost.commentCount + " yorum");

        if (currentPost.createdAt != null) {
            String date = new SimpleDateFormat("dd MMMM yyyy", new Locale("tr"))
                .format(currentPost.createdAt.toDate());
            if (tvDate != null) tvDate.setText(date);
        }

        // Onay durumu
        if (tvStatus != null) {
            if ("approved".equals(currentPost.status)) {
                tvStatus.setVisibility(View.GONE);
            } else if ("rejected".equals(currentPost.status)) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("Reddedildi"
                    + (currentPost.adminNote != null && !currentPost.adminNote.isEmpty()
                       ? ": " + currentPost.adminNote : ""));
                tvStatus.setTextColor(0xFFEF4444);
            } else {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("Yazı inceleniyor, onay bekleniyor.");
                tvStatus.setTextColor(0xFFF59E0B);
            }
        }

        // Kapak görseli — tema: cover (coverUrl değil)
        String cover = currentPost.cover != null ? currentPost.cover : "";
        if (!cover.isEmpty()) {
            ivCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(cover).centerCrop().into(ivCover);
        } else {
            ivCover.setVisibility(View.GONE);
        }

        // Yazar fotoğrafını users koleksiyonundan çek
        if (currentPost.authorId != null) {
            db.collection("users").document(currentPost.authorId).get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String photoURL = userDoc.getString("photoURL");
                        if (photoURL != null && !photoURL.isEmpty()) {
                            Glide.with(this).load(photoURL).circleCrop()
                                .placeholder(R.drawable.ic_account_circle)
                                .into(ivAuthorPhoto);
                        }
                    }
                });
        }
    }

    private void checkLiked() {
        if (currentUser == null || currentPost.likedBy == null) return;
        isLiked = currentPost.likedBy.contains(currentUser.getUid());
        updateLikeButton();
    }

    private void toggleLike() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Beğenmek için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = currentUser.getUid();
        if (isLiked) {
            db.collection("pendingPosts").document(postId)
                .update("likedBy",   FieldValue.arrayRemove(uid),
                        "likeCount", FieldValue.increment(-1));
            isLiked = false;
            currentPost.likeCount = Math.max(0, currentPost.likeCount - 1);
        } else {
            db.collection("pendingPosts").document(postId)
                .update("likedBy",   FieldValue.arrayUnion(uid),
                        "likeCount", FieldValue.increment(1));
            isLiked = true;
            currentPost.likeCount++;
        }
        tvLikeCount.setText(String.valueOf(currentPost.likeCount));
        updateLikeButton();
    }

    private void updateLikeButton() {
        btnLike.setImageResource(isLiked
            ? R.drawable.ic_heart_filled
            : R.drawable.ic_heart_outline);
    }

    private void loadComments() {
        // Tema: comments/{postId}/msgs subcollection, field: uid, name, photoURL, text, ts
        db.collection("comments").document(postId)
            .collection("msgs")
            .orderBy("ts", Query.Direction.ASCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(snap -> {
                comments.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    comments.add(doc.getData());
                }
                commentAdapter.notifyDataSetChanged();
                tvCommentCount.setText(comments.size() + " yorum");
            });
    }

    private void sendComment() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Yorum yapmak için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etComment.getText().toString().trim();
        if (text.isEmpty()) return;

        // Tema: comments/{postId}/msgs subcollection, field: uid, name, photoURL, text, ts
        Map<String, Object> comment = new HashMap<>();
        comment.put("uid",      currentUser.getUid());
        comment.put("name",     currentUser.getDisplayName() != null
                                ? currentUser.getDisplayName() : "Kullanıcı");
        comment.put("photoURL", currentUser.getPhotoUrl() != null
                                ? currentUser.getPhotoUrl().toString() : "");
        comment.put("text",     text);
        comment.put("likes",    0);
        comment.put("ts",       Timestamp.now());

        db.collection("comments").document(postId)
            .collection("msgs").add(comment)
            .addOnSuccessListener(ref -> {
                db.collection("pendingPosts").document(postId)
                    .update("commentCount", FieldValue.increment(1));
                etComment.setText("");
                comments.add(comment);
                commentAdapter.notifyItemInserted(comments.size() - 1);
                recyclerComments.scrollToPosition(comments.size() - 1);
                tvCommentCount.setText(comments.size() + " yorum");
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Yorum gönderilemedi", Toast.LENGTH_SHORT).show());
    }

    private void sharePost() {
        if (currentPost == null) return;
        String shareUrl = currentPost.bloggerPostUrl != null && !currentPost.bloggerPostUrl.isEmpty()
            ? currentPost.bloggerPostUrl
            : "heftreng.blogspot.com";
        android.content.Intent shareIntent = new android.content.Intent(
            android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
            (currentPost.title != null ? currentPost.title : "") + "\n\n" + shareUrl);
        startActivity(android.content.Intent.createChooser(shareIntent, "Paylaş"));
    }

    // ── İç sınıf: Yorum Adapter ──────────────────────────────────────────

    static class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentVH> {
        private final List<Map<String, Object>> list;
        CommentAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public CommentVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.two_line_list_item, parent, false);
            return new CommentVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentVH h, int pos) {
            Map<String, Object> c = list.get(pos);
            // Tema field: name, text
            h.tvName.setText(String.valueOf(c.getOrDefault("name", "Kullanıcı")));
            h.tvContent.setText(String.valueOf(c.getOrDefault("text", "")));
            h.tvName.setTextColor(0xFFCCCCCC);
            h.tvContent.setTextColor(0xFF888888);
        }

        @Override public int getItemCount() { return list.size(); }

        static class CommentVH extends RecyclerView.ViewHolder {
            TextView tvName, tvContent;
            CommentVH(@NonNull View v) {
                super(v);
                tvName    = v.findViewById(android.R.id.text1);
                tvContent = v.findViewById(android.R.id.text2);
            }
        }
    }
}
