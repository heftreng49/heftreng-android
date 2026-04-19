package com.heftreng.app.ui.kurdi;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.heftreng.app.R;
import com.heftreng.app.model.*;
import java.util.*;

public class KurdiFragment extends Fragment {

    private RecyclerView recyclerUnits;
    private TextView tvXP, tvStreak, tvLevel;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private List<KurdiUnit> units = new ArrayList<>();
    private UnitAdapter adapter;
    private KurdiProgress progress;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_kurdi, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recyclerUnits = view.findViewById(R.id.recyclerUnits);
        tvXP          = view.findViewById(R.id.tvXP);
        tvStreak      = view.findViewById(R.id.tvStreak);
        tvLevel       = view.findViewById(R.id.tvLevel);

        adapter = new UnitAdapter(units, unit -> openUnit(unit));
        recyclerUnits.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerUnits.setAdapter(adapter);

        loadProgress();
        loadUnits();
    }

    private void loadProgress() {
        if (currentUser == null) return;
        db.collection("kurdiProgress").document(currentUser.getUid()).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded()) return;
                progress = doc.exists() ? doc.toObject(KurdiProgress.class) : new KurdiProgress();
                if (progress == null) progress = new KurdiProgress();
                updateProgressUI();
            });
    }

    private void updateProgressUI() {
        if (tvXP     != null) tvXP.setText(progress.xp + " XP");
        if (tvStreak != null) tvStreak.setText("🔥 " + progress.streak + " gün");
        if (tvLevel  != null) {
            String lv = progress.xp < 100 ? "Destpêk" :
                        progress.xp < 300 ? "Navîn" : "Pêşketî";
            tvLevel.setText(lv);
        }
    }

    private void loadUnits() {
        db.collection("kurdiUnits")
            .orderBy("order")
            .get()
            .addOnSuccessListener(snap -> {
                if (!isAdded()) return;
                units.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    KurdiUnit u = doc.toObject(KurdiUnit.class);
                    u.id = doc.getId();
                    units.add(u);
                }
                adapter.notifyDataSetChanged();
            });
    }

    private void openUnit(KurdiUnit unit) {
        Bundle args = new Bundle();
        args.putString("unitId",    unit.id);
        args.putString("unitTitle", unit.title);
        args.putInt   ("unitXP",    unit.xp);
        requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.nav_host_fragment,
                KurdiLessonFragment.newInstance(args))
            .addToBackStack(null)
            .commit();
    }

    // ── UnitAdapter ──────────────────────────────────────────────────────
    static class UnitAdapter extends RecyclerView.Adapter<UnitAdapter.VH> {
        interface OnUnitClick { void onClick(KurdiUnit unit); }
        private final List<KurdiUnit> list;
        private final OnUnitClick listener;
        UnitAdapter(List<KurdiUnit> l, OnUnitClick c) { list = l; listener = c; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_kurdi_unit, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            KurdiUnit u = list.get(pos);
            h.tvTitle.setText(u.title);
            h.tvLevel.setText(u.level != null ? u.level : "");
            h.tvXP.setText(u.xp + " XP");
            h.itemView.setOnClickListener(v -> listener.onClick(u));
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvLevel, tvXP;
            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvUnitTitle);
                tvLevel = v.findViewById(R.id.tvUnitLevel);
                tvXP    = v.findViewById(R.id.tvUnitXP);
            }
        }
    }
}
