package com.heftreng.app.ui.feed;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.heftreng.app.R;
import com.heftreng.app.adapter.FeedAdapter;
import com.heftreng.app.model.FeedPost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedFragment extends Fragment implements FeedAdapter.OnPostActionListener {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private FloatingActionButton fabCompose;
    private FeedAdapter adapter;
    private List<FeedPost> posts = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recyclerView = view.findViewById(R.id.recyclerFeed);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        fabCompose   = view.findViewById(R.id.fabCompose);

        adapter = new FeedAdapter(posts, currentUser, db, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary);
        swipeRefresh.setOnRefreshListener(this::loadFeed);

        fabCompose.setOnClickListener(v -> {
            if (currentUser == null) {
                Toast.makeText(getContext(), "Paylaşmak için giriş yapın", Toast.LENGTH_SHORT).show();
            } else {
                Navigation.findNavController(v).navigate(R.id.composeFragment);
            }
        });

        loadFeed();
    }

    private void loadFeed() {
        swipeRefresh.setRefreshing(true);
        db.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(30)
            .get()
            .addOnSuccessListener(snap -> {
                posts.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    FeedPost post = doc.toObject(FeedPost.class);
                    post.id = doc.getId();
                    posts.add(post);
                }
                adapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
            })
            .addOnFailureListener(e -> {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Yüklenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    @Override
    public void onComment(FeedPost post) {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Yorum yapmak için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(getContext())
            .inflate(R.layout.dialog_comment, null);
        EditText etComment = dialogView.findViewById(R.id.etComment);

        new AlertDialog.Builder(getContext())
            .setTitle("Yorum yap")
            .setView(dialogView)
            .setPositiveButton("Gönder", (dialog, which) -> {
                String text = etComment.getText().toString().trim();
                if (text.isEmpty()) return;
                submitComment(post, text);
            })
            .setNegativeButton("İptal", null)
            .show();
    }

    private void submitComment(FeedPost post, String text) {
        Map<String, Object> comment = new HashMap<>();
        comment.put("uid",        currentUser.getUid());
        comment.put("name",       currentUser.getDisplayName());
        comment.put("photoURL",   currentUser.getPhotoUrl() != null
            ? currentUser.getPhotoUrl().toString() : "");
        comment.put("text",       text);
        comment.put("ts",         Timestamp.now());

        db.collection("feed").document(post.id).collection("comments").add(comment)
            .addOnSuccessListener(ref -> {
                db.collection("feed").document(post.id)
                    .update("cmtCount", com.google.firebase.firestore.FieldValue.increment(1));
                post.cmtCount++;
                int idx = posts.indexOf(post);
                if (idx >= 0) adapter.notifyItemChanged(idx);
                Toast.makeText(getContext(), "Yorum eklendi", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Yorum gönderilemedi", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAuthorClick(String uid) {
        Bundle args = new Bundle();
        args.putString("uid", uid);
        Navigation.findNavController(requireView())
            .navigate(R.id.profileFragment, args);
    }
}
