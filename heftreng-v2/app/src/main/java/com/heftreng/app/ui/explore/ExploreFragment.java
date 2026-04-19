package com.heftreng.app.ui.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.heftreng.app.R;
import com.heftreng.app.adapter.FeedAdapter;
import com.heftreng.app.model.FeedPost;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExploreFragment extends Fragment implements FeedAdapter.OnPostActionListener {

    private RecyclerView recyclerExplore;
    private FeedAdapter adapter;
    private List<FeedPost> posts = new ArrayList<>();
    private FirebaseFirestore db;
    private String activeCategory = null;

    private static final List<String> CATEGORIES = Arrays.asList(
        "Tümü", "Teknoloji", "Kültür", "Sanat", "Spor", "Bilim", "Yaşam", "Politika"
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        recyclerExplore = view.findViewById(R.id.recyclerExplore);
        adapter = new FeedAdapter(posts, null, db, this);
        recyclerExplore.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerExplore.setAdapter(adapter);

        // Kategori chip'leri
        LinearLayout chipGroup = view.findViewById(R.id.chipGroup);
        for (String cat : CATEGORIES) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.surface2);
            chip.setTextColor(getResources().getColor(R.color.white, null));
            chip.setOnClickListener(v -> {
                activeCategory = cat.equals("Tümü") ? null : cat;
                loadPosts();
            });
            chipGroup.addView(chip);
        }

        // Arama
        EditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String q = etSearch.getText().toString().trim();
            if (!q.isEmpty()) searchPosts(q);
            return true;
        });

        loadPosts();
    }

    private void loadPosts() {
        // Tema ile uyumlu: "ts" field (createdAt değil)
        Query query = db.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50);
        if (activeCategory != null) {
            query = db.collection("feed")
                .whereEqualTo("category", activeCategory)
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(50);
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
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Yüklenemedi", Toast.LENGTH_SHORT).show());
    }

    private void searchPosts(String query) {
        // Tema: text field üzerinde arama
        db.collection("feed")
            .orderBy("text")
            .startAt(query)
            .endAt(query + "\uf8ff")
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
            });
    }

    @Override
    public void onComment(FeedPost post) {
        Toast.makeText(getContext(), "Yorum için yazıya git", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthorClick(String uid) {
        Bundle args = new Bundle();
        args.putString("uid", uid);
        Navigation.findNavController(requireView()).navigate(R.id.profileFragment, args);
    }
}
