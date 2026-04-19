package com.heftreng.app.ui.saved;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.heftreng.app.R;
import com.heftreng.app.model.SavedPost;
import java.util.*;

public class SavedFragment extends Fragment {

    private RecyclerView recycler;
    private LinearLayout layoutEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private final List<SavedPost> items = new ArrayList<>();
    private SavedAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i,
            @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_saved, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        db          = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recycler     = view.findViewById(R.id.recyclerSaved);
        layoutEmpty  = view.findViewById(R.id.layoutEmpty);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        adapter = new SavedAdapter(items);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.brand_primary);
            swipeRefresh.setOnRefreshListener(this::loadSaved);
        }

        if (currentUser != null) loadSaved();
        else showEmpty();
    }

    private void loadSaved() {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid())
            .collection("saved")
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(snap -> {
                if (!isAdded()) return;
                items.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    SavedPost sp = doc.toObject(SavedPost.class);
                    sp.id = doc.getId();
                    items.add(sp);
                }
                adapter.notifyDataSetChanged();
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutEmpty != null)
                    layoutEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            })
            .addOnFailureListener(e -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            });
    }

    private void showEmpty() {
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
    }

    // ── Adapter ───────────────────────────────────────────────────────────

    static class SavedAdapter extends RecyclerView.Adapter<SavedAdapter.VH> {
        private final List<SavedPost> list;

        SavedAdapter(List<SavedPost> l) { this.list = l; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_saved, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SavedPost sp = list.get(pos);
            if (h.tvContent != null)
                h.tvContent.setText(sp.postContent != null ? sp.postContent : "");
            if (h.tvAuthor != null)
                h.tvAuthor.setText(sp.postAuthor != null ? sp.postAuthor : "");
            if (h.tvStatus != null)
                h.tvStatus.setText(sp.status != null ? sp.status : "");

            // Görsel
            if (h.ivThumb != null) {
                if (sp.postPhoto != null && !sp.postPhoto.isEmpty()) {
                    h.ivThumb.setVisibility(View.VISIBLE);
                    Glide.with(h.itemView.getContext())
                        .load(sp.postPhoto).centerCrop().into(h.ivThumb);
                } else {
                    h.ivThumb.setVisibility(View.GONE);
                }
            }
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvContent, tvAuthor, tvStatus;
            ImageView ivThumb;
            ImageButton btnRemove;

            VH(View v) {
                super(v);
                tvContent  = v.findViewById(R.id.tvContent);
                tvAuthor   = v.findViewById(R.id.tvAuthor);
                tvStatus   = v.findViewById(R.id.tvStatus);
                ivThumb    = v.findViewById(R.id.ivPostThumb);
                btnRemove  = v.findViewById(R.id.btnRemove);
            }
        }
    }
}
