package com.heftreng.app.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
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
    private LinearLayout chipGroupFilter;
    private FeedAdapter adapter;
    private List<FeedPost> posts = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String activeFilter = "all"; // all | following | trending

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db          = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recyclerView   = view.findViewById(R.id.recyclerFeed);
        swipeRefresh   = view.findViewById(R.id.swipeRefresh);
        fabCompose     = view.findViewById(R.id.fabCompose);
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter);

        // Arama butonu
        View btnSearch = view.findViewById(R.id.btnSearch);
        if (btnSearch != null) btnSearch.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.exploreFragment));

        // Mesajlar butonu
        View btnMessages = view.findViewById(R.id.btnMessages);
        if (btnMessages != null) btnMessages.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.messagesFragment));

        // Filtre chip'leri
        buildFilterChips();

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

    private void buildFilterChips() {
        String[][] filters = {
            {"all",       "Tüm Akış"},
            {"following", "Takip"},
            {"trending",  "Trend"}
        };
        for (String[] f : filters) {
            Chip chip = new Chip(requireContext());
            chip.setText(f[1]);
            chip.setCheckable(true);
            chip.setChecked(f[0].equals(activeFilter));
            chip.setChipBackgroundColorResource(R.color.surface);
            chip.setTextColor(getResources().getColor(R.color.text_secondary, null));
            chip.setCheckedIconVisible(false);
            chip.setChipStrokeWidth(1f);
            chip.setChipStrokeColorResource(R.color.border_light);
            chip.setRippleColorResource(R.color.brand_primary);
            chip.setOnClickListener(v -> {
                activeFilter = f[0];
                // Tüm chip'leri güncelle
                for (int i = 0; i < chipGroupFilter.getChildCount(); i++) {
                    View c = chipGroupFilter.getChildAt(i);
                    if (c instanceof Chip) {
                        boolean sel = c == chip;
                        ((Chip) c).setChecked(sel);
                        ((Chip) c).setTextColor(getResources().getColor(
                            sel ? R.color.brand_primary_light : R.color.text_secondary, null));
                        ((Chip) c).setChipStrokeColorResource(
                            sel ? R.color.brand_primary : R.color.border_light);
                    }
                }
                loadFeed();
            });
            android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(6);
            chip.setLayoutParams(lp);
            chipGroupFilter.addView(chip);
        }
    }

    private void loadFeed() {
        swipeRefresh.setRefreshing(true);
        Query query;

        if ("trending".equals(activeFilter)) {
            query = db.collection("feed")
                .orderBy("likes", Query.Direction.DESCENDING)
                .limit(30);
        } else {
            query = db.collection("feed")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(30);
        }

        query.get()
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
                Toast.makeText(getContext(), "Yüklenemedi", Toast.LENGTH_SHORT).show();
            });
    }

    @Override
    public void onComment(FeedPost post) {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Yorum için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(getContext())
            .inflate(R.layout.dialog_comment, null);
        android.widget.EditText etComment = dialogView.findViewById(R.id.etComment);

        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Yorum yap")
            .setView(dialogView)
            .setPositiveButton("Gönder", (dialog, which) -> {
                String text = etComment.getText().toString().trim();
                if (!text.isEmpty()) submitComment(post, text);
            })
            .setNegativeButton("İptal", null)
            .show();
    }

    private void submitComment(FeedPost post, String text) {
        Map<String, Object> comment = new HashMap<>();
        comment.put("uid",      currentUser.getUid());
        comment.put("name",     currentUser.getDisplayName() != null
                                ? currentUser.getDisplayName() : "Kullanıcı");
        comment.put("photoURL", currentUser.getPhotoUrl() != null
                                ? currentUser.getPhotoUrl().toString() : "");
        comment.put("text",     text);
        comment.put("ts",       Timestamp.now());

        db.collection("feed").document(post.id)
            .collection("comments").add(comment)
            .addOnSuccessListener(ref -> {
                db.collection("feed").document(post.id)
                    .update("cmtCount", FieldValue.increment(1));
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
        Navigation.findNavController(requireView()).navigate(R.id.profileFragment, args);
    }
}
