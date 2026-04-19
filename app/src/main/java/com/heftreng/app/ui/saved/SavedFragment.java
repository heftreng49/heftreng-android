package com.heftreng.app.ui.saved;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.heftreng.app.R;
import com.heftreng.app.model.SavedPost;
import java.util.*;

public class SavedFragment extends Fragment {

    private static final String[] STATUSES = {"okuyacak", "okuyor", "okudu", "bekleyen"};
    private static final String[] LABELS   = {"Okuyacaklarım","Okuyorum","Okudum","Bekleyenler"};

    private RecyclerView recycler;
    private TextView tvEmpty;
    private ChipGroup chipGroup;
    private String activeStatus = "okuyacak";
    private final List<SavedPost> items = new ArrayList<>();
    private SavedAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_saved, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recycler  = view.findViewById(R.id.recyclerSaved);
        tvEmpty   = view.findViewById(R.id.tvEmpty);
        chipGroup = view.findViewById(R.id.chipGroupSaved);

        buildChips();

        adapter = new SavedAdapter(items);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        if (currentUser != null) loadSaved();
    }

    private void buildChips() {
        for (int i = 0; i < STATUSES.length; i++) {
            final String status = STATUSES[i];
            Chip chip = new Chip(requireContext());
            chip.setText(LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(status.equals(activeStatus));
            chip.setChipBackgroundColorResource(R.color.surface2);
            chip.setTextColor(getResources().getColor(R.color.white, null));
            chip.setOnClickListener(v -> {
                activeStatus = status;
                loadSaved();
            });
            chipGroup.addView(chip);
        }
    }

    private void loadSaved() {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid())
            .collection("saved")
            .whereEqualTo("status", activeStatus)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .limit(30)
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
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
    }

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
            h.tvContent.setText(sp.postContent != null ? sp.postContent : "");
            h.tvAuthor.setText(sp.postAuthor != null ? sp.postAuthor : "");
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvContent, tvAuthor;
            VH(View v) {
                super(v);
                tvContent = v.findViewById(R.id.tvSavedContent);
                tvAuthor  = v.findViewById(R.id.tvSavedAuthor);
            }
        }
    }
}
