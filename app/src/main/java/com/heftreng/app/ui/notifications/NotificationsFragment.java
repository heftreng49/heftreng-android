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
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private NotifAdapter adapter;
    private final List<HeftNotification> items = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db          = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        recycler      = view.findViewById(R.id.recyclerNotifications);
        swipeRefresh  = view.findViewById(R.id.swipeRefresh);
        layoutEmpty   = view.findViewById(R.id.layoutEmpty);

        View btnMarkAll = view.findViewById(R.id.btnMarkAll);
        if (btnMarkAll != null) btnMarkAll.setOnClickListener(v -> markAllRead());

        adapter = new NotifAdapter(items);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary);
        swipeRefresh.setOnRefreshListener(this::load);

        if (currentUser == null) showEmpty("Giriş yapın");
        else load();
    }

    private void load() {
        if (currentUser == null) return;
        swipeRefresh.setRefreshing(true);
        db.collection("userNotifs").document(currentUser.getUid())
            .collection("msgs")
            .orderBy("ts", Query.Direction.DESCENDING).limit(50)
            .get()
            .addOnSuccessListener(snap -> {
                items.clear();
                for (QueryDocumentSnapshot d : snap) {
                    HeftNotification n = d.toObject(HeftNotification.class);
                    n.id = d.getId();
                    items.add(n);
                }
                adapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
                if (items.isEmpty()) showEmpty("Hîn agahdarî tune.");
                else { layoutEmpty.setVisibility(View.GONE); recycler.setVisibility(View.VISIBLE); }
            })
            .addOnFailureListener(e -> swipeRefresh.setRefreshing(false));
    }

    private void markAllRead() {
        if (currentUser == null) return;
        WriteBatch batch = db.batch();
        for (HeftNotification n : items) {
            if (!n.read && n.id != null) {
                batch.update(db.collection("userNotifs").document(currentUser.getUid())
                    .collection("msgs").document(n.id), "read", true);
                n.read = true;
            }
        }
        batch.commit().addOnSuccessListener(v -> adapter.notifyDataSetChanged());
    }

    private void showEmpty(String msg) {
        recycler.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        TextView tv = layoutEmpty.findViewWithTag("emptyMsg");
        if (tv != null) tv.setText(msg);
    }
}
