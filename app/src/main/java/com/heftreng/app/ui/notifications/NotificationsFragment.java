package com.heftreng.app.ui.notifications;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.heftreng.app.R;
import com.heftreng.app.model.HeftNotification;
import java.util.*;

public class NotificationsFragment extends Fragment {

    private RecyclerView recycler;
    private LinearLayout layoutEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private final List<HeftNotification> notifs = new ArrayList<>();
    private NotifAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i,
            @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_notifications, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        db          = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recycler     = view.findViewById(R.id.recyclerNotifications);
        layoutEmpty  = view.findViewById(R.id.layoutEmpty);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        // btnClearAll opsiyonel — layout'ta yoksa null gelir
        View btnClearAll = view.findViewById(R.id.btnClearAll);
        if (btnClearAll != null) btnClearAll.setOnClickListener(v -> clearAll());

        adapter = new NotifAdapter(notifs);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.brand_primary);
            swipeRefresh.setOnRefreshListener(this::loadNotifs);
        }

        if (currentUser != null) loadNotifs();
        else showEmpty();
    }

    private void loadNotifs() {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid())
            .collection("notifications")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener((snap, e) -> {
                if (!isAdded() || snap == null) return;
                notifs.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    HeftNotification n = doc.toObject(HeftNotification.class);
                    n.id = doc.getId();
                    notifs.add(n);
                    if (!Boolean.TRUE.equals(n.read))
                        doc.getReference().update("read", true);
                }
                adapter.notifyDataSetChanged();
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutEmpty != null)
                    layoutEmpty.setVisibility(notifs.isEmpty() ? View.VISIBLE : View.GONE);
            });
    }

    private void clearAll() {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid())
            .collection("notifications").get()
            .addOnSuccessListener(snap -> {
                WriteBatch batch = db.batch();
                for (QueryDocumentSnapshot doc : snap) batch.delete(doc.getReference());
                batch.commit().addOnSuccessListener(v -> {
                    notifs.clear();
                    adapter.notifyDataSetChanged();
                    if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                });
            });
    }

    private void showEmpty() {
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
    }

    // ── İç Adapter ──────────────────────────────────────
    static class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {
        private final List<HeftNotification> list;
        NotifAdapter(List<HeftNotification> l) { this.list = l; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_notification, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HeftNotification n = list.get(pos);
            h.tvText.setText(n.text != null ? n.text : "");
            h.tvTime.setText(n.ts != null
                ? android.text.format.DateUtils
                    .getRelativeTimeSpanString(n.ts.toDate().getTime()).toString()
                : "");
            h.itemView.setAlpha(Boolean.TRUE.equals(n.read) ? 0.6f : 1f);
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvText, tvTime;
            VH(View v) {
                super(v);
                tvText = v.findViewById(R.id.tvNotifText);
                tvTime = v.findViewById(R.id.tvNotifTime);
            }
        }
    }
}
