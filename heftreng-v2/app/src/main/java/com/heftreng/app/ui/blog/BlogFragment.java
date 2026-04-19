package com.heftreng.app.ui.blog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.heftreng.app.R;
import com.heftreng.app.adapter.BlogAdapter;
import com.heftreng.app.model.BlogPost;

import java.util.ArrayList;
import java.util.List;

public class BlogFragment extends Fragment implements BlogAdapter.OnBlogClickListener {

    private RecyclerView recyclerBlog;
    private SwipeRefreshLayout swipeRefresh;
    private BlogAdapter adapter;
    private List<BlogPost> posts = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private boolean showingMine = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_blog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recyclerBlog = view.findViewById(R.id.recyclerBlog);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        adapter = new BlogAdapter(posts, this);
        recyclerBlog.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerBlog.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary);
        swipeRefresh.setOnRefreshListener(this::loadPosts);

        // Yeni yazı gönderme butonu
        MaterialButton btnNew = view.findViewById(R.id.btnNewPost);
        btnNew.setOnClickListener(v -> {
            if (currentUser == null) {
                Toast.makeText(getContext(),
                    "Yazı paylaşmak için giriş yapın", Toast.LENGTH_SHORT).show();
            } else {
                Navigation.findNavController(v).navigate(R.id.blogDetailFragment,
                    new Bundle()); // blogDetailFragment'ı yeni yazı modu için aç
            }
        });

        // Tabs
        TabLayout tabs = view.findViewById(R.id.tabLayout);
        tabs.addTab(tabs.newTab().setText("Tüm Yazılar"));
        tabs.addTab(tabs.newTab().setText("Benim Yazılarım"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showingMine = tab.getPosition() == 1;
                loadPosts();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadPosts();
    }

    private void loadPosts() {
        swipeRefresh.setRefreshing(true);

        Query q;
        if (showingMine && currentUser != null) {
            // Kendi yazıları — tüm durumlar (pending, approved, rejected)
            q = db.collection("pendingPosts")
                .whereEqualTo("authorId", currentUser.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING);
        } else {
            // Onaylanmış tüm yazılar
            q = db.collection("pendingPosts")
                .whereEqualTo("status", "approved")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40);
        }

        q.get()
            .addOnSuccessListener(snap -> {
                posts.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    BlogPost post = doc.toObject(BlogPost.class);
                    post.id = doc.getId();
                    // Yazar fotoğrafını users koleksiyonundan çek (gerekirse)
                    posts.add(post);
                }
                adapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
            })
            .addOnFailureListener(e -> {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Yazılar yüklenemedi", Toast.LENGTH_SHORT).show();
            });
    }

    @Override
    public void onPostClick(BlogPost post) {
        Bundle args = new Bundle();
        args.putString("postId", post.id);
        Navigation.findNavController(requireView()).navigate(R.id.blogDetailFragment, args);
    }

    @Override
    public void onAuthorClick(String uid) {
        Bundle args = new Bundle();
        args.putString("uid", uid);
        Navigation.findNavController(requireView()).navigate(R.id.profileFragment, args);
    }
}
